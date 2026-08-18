-- =====================================================================
-- V62 - Retirar las seis columnas espejo de `propiedad`.
--
-- Es el paso 9 de D-E4-3, y llega DESPUES de que el paso 8 comprobara que no
-- queda ni un lector ni un escritor en el codigo. Antes de esto:
--
--   V60     declaro la autoridad (destino + campo_estructural)
--   V61     consolido los valores: metraje al campo canonico, los seis al atributo
--   paso 7  migro los TRES lectores (listado, ficha, matcher) y el escritor de
--           /locales, que seguia rellenando estas columnas en cada PUT
--
-- POR QUE SE BORRAN Y NO SE DEJAN "por si acaso"
-- Una columna que nadie lee ni escribe no es inofensiva: es la segunda verdad
-- esperando a que alguien la crea. Mientras exista, el siguiente que abra la
-- tabla vera `ambientes` y la usara, y en dos meses habra dos valores otra vez.
-- Dejarla vacia es peor: parece un dato que falta.
--
-- LO QUE ESTA MIGRACION NO PODIA HACER A LA LIGERA
-- Estas seis columnas no eran solo columnas: cuatro llevaban un CHECK de rango
-- desde V4 (ambientes > 0, y >= 0 en antiguedad, estacionamientos y cuota de
-- mantenimiento; frente tambien). Un DROP COLUMN se los lleva por delante sin
-- decir nada, y `atributo_propiedad` no tenia con que sustituirlos: su trigger
-- valida el TIPO del valor, no su rango. Borrar sin mas habria cambiado
-- "ambientes > 0" por "ambientes cualquier cosa" en silencio -- la misma clase
-- de perdida callada que esta tanda vino a cerrar, solo que en el invariante en
-- vez de en el dato. Por eso el paso 2 se los lleva consigo ANTES del DROP.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1 - La guarda que puede parar la migracion.
--
-- Se vuelve a medir aunque V61 ya lo midiera: entre las dos ha pasado tiempo, y
-- un escritor no migrado habria seguido rellenando columnas en cada PUT. Si
-- algun valor vive SOLO en la columna, esto falla y no borra nada.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    huerfanos bigint;
    detalle   text;
BEGIN
    SELECT count(*), string_agg(DISTINCT format('%s(id=%s)', v.clave, p.id_propiedad), ', ')
      INTO huerfanos, detalle
      FROM propiedad p
      CROSS JOIN LATERAL (VALUES
            ('ambientes',            p.ambientes::numeric),
            ('frente',               p.frente),
            ('cuota_mantenimiento',  p.cuota_mantenimiento),
            ('estacionamientos',     p.numero_estacionamientos::numeric),
            ('antiguedad_anios',     p.antiguedad_anios::numeric)
           ) AS v(clave, valor)
     WHERE v.valor IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_propiedad = p.id_propiedad AND a.clave = v.clave);

    IF huerfanos > 0 THEN
        RAISE EXCEPTION
            'V62: % valores viven SOLO en su columna espejo (%). Borrarlas ahora los '
            'perderia: algo siguio escribiendo la columna despues de V61.',
            huerfanos, left(detalle, 400);
    END IF;

    SELECT count(*) INTO huerfanos
      FROM propiedad p
     WHERE p.zonificacion IS NOT NULL AND btrim(p.zonificacion) <> ''
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_propiedad = p.id_propiedad AND a.clave = 'zonificacion');
    IF huerfanos > 0 THEN
        RAISE EXCEPTION 'V62: % zonificaciones viven solo en su columna espejo.', huerfanos;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2 - Los cinco invariantes de rango, mudados con su dato.
--
-- El minimo se declara en el CATALOGO y no en el codigo por la misma razon que
-- el tipo de dato: la clave la puede anadir un tenant, y su rango es parte de lo
-- que la define. NULL significa "sin minimo", que es el caso de la mayoria: una
-- zonificacion no tiene minimo, un frente si.
-- ---------------------------------------------------------------------
ALTER TABLE catalogo_atributo
    ADD COLUMN valor_minimo NUMERIC(14, 4);

COMMENT ON COLUMN catalogo_atributo.valor_minimo IS
    'Minimo admisible del valor numerico; NULL = sin minimo. Hereda los CHECK de rango que V4 tenia sobre las columnas espejo retiradas en V62 (D-E4-3).';

UPDATE catalogo_atributo SET valor_minimo = 1
 WHERE organizacion_id IS NULL AND clave = 'ambientes';
UPDATE catalogo_atributo SET valor_minimo = 0
 WHERE organizacion_id IS NULL
   AND clave IN ('antiguedad_anios', 'estacionamientos', 'cuota_mantenimiento', 'frente');

-- El trigger de V48, ampliado. Se reescribe la funcion ENTERA -- CREATE OR
-- REPLACE -- porque PL/pgSQL no admite parches: es la leccion de V44, donde una
-- condicion dentro de un cuerpo de funcion se quedo sin actualizar y ni javac ni
-- Hibernate podian verlo.
CREATE OR REPLACE FUNCTION exigir_atributo_gobernado() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    cat       record;
    tipo_prop varchar(1);
BEGIN
    SELECT * INTO cat
      FROM catalogo_atributo c
     WHERE c.clave = NEW.clave
       AND c.activo = true
       AND (c.organizacion_id = NEW.organizacion_id OR c.organizacion_id IS NULL)
     ORDER BY c.organizacion_id NULLS LAST
     LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El atributo "%" no esta en el catalogo', NEW.clave
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    SELECT tipo_inmueble INTO tipo_prop FROM propiedad WHERE id_propiedad = NEW.id_propiedad;

    IF NOT cat.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = cat.id_catalogo_atributo
                          AND t.tipo_propiedad = tipo_prop) THEN
        RAISE EXCEPTION 'El atributo "%" no aplica a una propiedad de tipo %', NEW.clave, tipo_prop
            USING ERRCODE = 'check_violation';
    END IF;

    -- El valor tiene que estar en la columna que corresponde a su tipo.
    IF cat.tipo_dato IN ('ENTERO', 'DECIMAL') AND NEW.valor_numero IS NULL THEN
        RAISE EXCEPTION 'El atributo "%" es numerico y llego sin valor_numero', NEW.clave
            USING ERRCODE = 'check_violation';
    ELSIF cat.tipo_dato = 'BOOLEANO' AND NEW.valor_booleano IS NULL THEN
        RAISE EXCEPTION 'El atributo "%" es booleano y llego sin valor_booleano', NEW.clave
            USING ERRCODE = 'check_violation';
    ELSIF cat.tipo_dato IN ('TEXTO', 'LISTA') AND NEW.valor_texto IS NULL THEN
        RAISE EXCEPTION 'El atributo "%" es de texto y llego sin valor_texto', NEW.clave
            USING ERRCODE = 'check_violation';
    END IF;

    -- V62: el rango que antes vivia en un CHECK de la columna espejo.
    IF cat.valor_minimo IS NOT NULL AND NEW.valor_numero IS NOT NULL
       AND NEW.valor_numero < cat.valor_minimo THEN
        RAISE EXCEPTION 'El atributo "%" no puede ser menor que % y llego %',
            NEW.clave, cat.valor_minimo, NEW.valor_numero
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

-- Lo que ya estaba escrito NO se toca: el trigger es BEFORE INSERT OR UPDATE y
-- no reexamina filas viejas. Si alguna entro por el camino universal -- que
-- nunca tuvo este rango -- se informa y se deja, porque corregir un valor ajeno
-- es inventarlo. Que aparezca en el log es lo que permite ir a mirarlo.
DO $$
DECLARE
    fuera_de_rango bigint;
BEGIN
    SELECT count(*) INTO fuera_de_rango
      FROM atributo_propiedad a
      JOIN catalogo_atributo c
        ON c.clave = a.clave
       AND (c.organizacion_id = a.organizacion_id OR c.organizacion_id IS NULL)
     WHERE c.valor_minimo IS NOT NULL
       AND a.valor_numero IS NOT NULL
       AND a.valor_numero < c.valor_minimo;

    IF fuera_de_rango > 0 THEN
        RAISE WARNING 'V62: % valores ya escritos quedan por debajo de su minimo. Entraron por el camino universal, que no tenia este rango; se dejan como estan.',
            fuera_de_rango;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 3 - Fuera las columnas.
--
-- `metraje` NO esta en esta lista: es el unico de los siete que quedo
-- clasificado como estructural y su columna ES la autoridad (D-E4-3 seccion 2),
-- con su `ck_propiedad_metraje` intacto.
--
-- Los cuatro CHECK de rango se van con sus columnas -- PostgreSQL borra el CHECK
-- al borrar la unica columna de la que depende -- y por eso el paso 2 tenia que
-- ir antes: asi el invariante no deja de existir en ningun momento.
-- ---------------------------------------------------------------------
ALTER TABLE propiedad
    DROP COLUMN ambientes,
    DROP COLUMN antiguedad_anios,
    DROP COLUMN frente,
    DROP COLUMN zonificacion,
    DROP COLUMN numero_estacionamientos,
    DROP COLUMN cuota_mantenimiento;

-- ---------------------------------------------------------------------
-- 4 - Evidencia.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    quedan  bigint;
    minimos bigint;
BEGIN
    SELECT count(*) INTO quedan
      FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'propiedad'
       AND column_name IN ('ambientes', 'antiguedad_anios', 'frente', 'zonificacion',
                           'numero_estacionamientos', 'cuota_mantenimiento');
    IF quedan > 0 THEN
        RAISE EXCEPTION 'V62: quedan % columnas espejo en propiedad', quedan;
    END IF;

    SELECT count(*) INTO minimos FROM catalogo_atributo WHERE valor_minimo IS NOT NULL;
    IF minimos < 5 THEN
        RAISE EXCEPTION 'V62: se esperaban 5 minimos heredados de V4 y hay %', minimos;
    END IF;

    RAISE NOTICE 'V62: seis columnas espejo retiradas, % rangos mudados al catalogo; una sola autoridad por clave',
        minimos;
END $$;
