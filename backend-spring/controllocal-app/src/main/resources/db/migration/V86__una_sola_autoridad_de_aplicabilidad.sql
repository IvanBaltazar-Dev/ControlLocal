-- =====================================================================
-- V86 - `catalogo_atributo_tipo` es la UNICA autoridad de aplicabilidad
-- =====================================================================
--
-- QUE ARREGLA. Hasta hoy "a que tipos aplica esta clave" tenia DOS
-- autoridades: la tabla de filas por tipo y el campo `catalogo_atributo
-- .aplica_todos`, que cortocircuitaba la consulta antes de mirarlas. Dos
-- sitios donde vive la misma verdad divergen -- y aqui divergen en la
-- direccion peor: `aplica_todos = true` hace que la clave aplique a un tipo
-- que nadie declaro, asi que retirarla de UNO exige cambiarle la forma.
--
-- LA DECISION. La autoridad pasa a ser la tabla, y SOLO la tabla. El campo
-- se queda mientras algun contrato lo necesite, pero deja de decidir: se
-- DERIVA de las filas y no se puede mantener por su cuenta.
--
-- POR QUE ES SEGURO. Medido contra las dos bases el 2026-08-30, antes de
-- escribir una linea:
--
--   controllocal_dev            3 claves con `aplica_todos`, las tres del
--                               sistema (antiguedad_anios, estacionamientos,
--                               metraje_total) y las tres CON sus siete filas
--                               por tipo. Ninguna depende del campo.
--   controllocal_repositorios   las mismas tres, y ademas 1.757 claves de
--                               tenant que son residuo de pruebas; 70 de
--                               ellas SI dependen exclusivamente del campo
--                               (cero filas por tipo).
--
-- Esas 70 son el motivo del paso 1: quitarle la autoridad al campo sin
-- respaldarlo antes con filas cambiaria su respuesta de "aplica a los siete"
-- a "no aplica a ninguno". El dato no se retira antes de tener su reemplazo
-- activo, aunque el dato sea residuo.
--
-- ADITIVA. No toca ninguna migracion aplicada. Reescribe TRES cuerpos
-- PL/pgSQL que consultaban el campo -- y lo hace tomando su definicion viva
-- y sustituyendo el fragmento, no copiandola aqui: una copia de 240 lineas
-- en esta migracion seria la segunda autoridad otra vez, un nivel mas abajo.
-- La leccion es la de V40/V44: una conversion de vocabulario que no llega al
-- cuerpo de una funcion deja el gate verde y la aplicacion rota.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 0. Los siete tipos, en UN sitio.
--
-- Ya estaban repetidos a mano en media docena de migraciones. Aqui hacen
-- falta en tres sitios distintos (respaldo, guarda y evidencia) y repetirlos
-- otras tres veces seria sembrar la misma enfermedad que esta migracion
-- viene a curar.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION tipos_de_propiedad()
RETURNS SETOF varchar(1)
LANGUAGE sql
IMMUTABLE
AS $$
    VALUES ('L'::varchar(1)), ('O'), ('D'), ('C'), ('T'), ('A'), ('X');
$$;

COMMENT ON FUNCTION tipos_de_propiedad() IS
    'Los siete codigos de tipo de propiedad. Tiene que coincidir con ck_catalogo_atributo_tipo.';

-- Control de que la lista de arriba no envejece sola: si alguien anade un
-- octavo tipo al CHECK y no lo anade aqui, la guarda de esta migracion
-- dejaria de exigirlo en silencio y `aplica_todos` volveria a significar
-- "aplica a los siete de 2026".
DO $$
DECLARE
    definicion text;
    codigo     varchar(1);
    fuera      text;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO definicion
      FROM pg_constraint WHERE conname = 'ck_catalogo_atributo_tipo';
    IF definicion IS NULL THEN
        RAISE EXCEPTION 'V86: no existe ck_catalogo_atributo_tipo, que es donde vive el vocabulario';
    END IF;

    FOR codigo IN SELECT * FROM tipos_de_propiedad() LOOP
        IF position('''' || codigo || '''' IN definicion) = 0 THEN
            RAISE EXCEPTION 'V86: el tipo % no esta en ck_catalogo_atributo_tipo: %', codigo, definicion;
        END IF;
    END LOOP;

    SELECT string_agg(DISTINCT t.tipo_propiedad, ', ') INTO fuera
      FROM catalogo_atributo_tipo t
     WHERE t.tipo_propiedad NOT IN (SELECT * FROM tipos_de_propiedad());
    IF fuera IS NOT NULL THEN
        RAISE EXCEPTION 'V86: hay aplicabilidad declarada para tipos que tipos_de_propiedad() no conoce: %', fuera;
    END IF;

    SELECT string_agg(DISTINCT p.tipo_inmueble, ', ') INTO fuera
      FROM propiedad p
     WHERE p.tipo_inmueble NOT IN (SELECT * FROM tipos_de_propiedad());
    IF fuera IS NOT NULL THEN
        RAISE EXCEPTION 'V86: hay propiedades de tipos que tipos_de_propiedad() no conoce: %', fuera;
    END IF;
END $$;


-- ---------------------------------------------------------------------
-- 1. CONSERVACION - respaldar con filas lo que hoy solo decia el campo.
--
-- Se escribe `OPC` y `requerido = false` porque es EXACTAMENTE lo que estas
-- claves responden hoy: `exigenciaPara(tipo)` nunca miro `aplica_todos`, asi
-- que una clave sin fila para un tipo ya era OPC en el. Subirla seria
-- inventar una exigencia que nadie declaro; bajarla, imposible.
--
-- Solo el sujeto PROPIEDAD. Una clave de ENCARGO con `aplica_todos` y sin
-- filas no se puede respaldar sin decidir a que OPERACIONES aplica, y eso no
-- esta escrito en ningun sitio: se rechaza abajo en vez de adivinarlo.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    ciegas bigint;
BEGIN
    SELECT count(*) INTO ciegas
      FROM catalogo_atributo c
     WHERE c.aplica_todos AND c.sujeto = 'ENCARGO'
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo);
    IF ciegas > 0 THEN
        RAISE EXCEPTION 'V86: % claves de ENCARGO aplican solo por `aplica_todos`. Su (tipo, operacion) no esta escrito en ningun sitio y no se inventa: declaralas en catalogo_atributo_operacion antes de migrar.', ciegas;
    END IF;
END $$;

INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN tipos_de_propiedad() AS t(tipo)
 WHERE c.aplica_todos
   AND c.sujeto = 'PROPIEDAD'
   AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo x
                    WHERE x.id_catalogo_atributo = c.id_catalogo_atributo
                      AND x.tipo_propiedad = t.tipo);


-- ---------------------------------------------------------------------
-- 2. Retirarle la autoridad a los TRES cuerpos PL/pgSQL que la tenian.
--
-- `exigir_atributo_gobernado` y `exigir_atributo_de_encargo` son los
-- triggers de ESCRITURA: son los que dicen "este atributo no aplica a una
-- propiedad de tipo X". Con el cortocircuito, una clave marcada
-- `aplica_todos` aceptaba valor en un tipo que su catalogo no declaraba.
--
-- No se pega su texto aqui. Se lee su definicion VIVA, se le quita el
-- fragmento y se vuelve a crear; y si el fragmento no aparece, la migracion
-- ABORTA en vez de dejar la funcion como estaba y el resto en verde. Es la
-- misma exigencia que un barrido: un cero que no se ha comprobado contra un
-- control positivo no es un cero.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    nombre    text;
    tabla     text;
    definicion text;
    reescrita  text;
    viejo      text;
BEGIN
    FOREACH nombre IN ARRAY ARRAY['exigir_atributo_gobernado', 'exigir_atributo_de_encargo'] LOOP
        tabla := CASE nombre WHEN 'exigir_atributo_gobernado'
                             THEN 'catalogo_atributo_tipo' ELSE 'catalogo_atributo_operacion' END;

        SELECT pg_get_functiondef(p.oid) INTO definicion
          FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
         WHERE n.nspname = 'public' AND p.proname = nombre;
        IF definicion IS NULL THEN
            RAISE EXCEPTION 'V86: no existe la funcion %()', nombre;
        END IF;

        viejo := 'IF NOT cat.aplica_todos' || E'\n' ||
                 '       AND NOT EXISTS (SELECT 1 FROM ' || tabla;
        IF position(viejo IN definicion) = 0 THEN
            RAISE EXCEPTION 'V86: %() ya no contiene el cortocircuito de `aplica_todos` en la forma esperada. Alguien la reescribio: revisa su cuerpo antes de seguir, porque esta migracion no ha cambiado nada.', nombre;
        END IF;

        reescrita := replace(definicion, viejo,
                             'IF NOT EXISTS (SELECT 1 FROM ' || tabla);
        EXECUTE reescrita;

        SELECT p.prosrc INTO definicion
          FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
         WHERE n.nspname = 'public' AND p.proname = nombre;
        IF position('aplica_todos' IN definicion) > 0 THEN
            RAISE EXCEPTION 'V86: %() sigue consultando `aplica_todos` despues de reescribirla', nombre;
        END IF;
    END LOOP;
END $$;

-- La tercera es de tres lineas y su cuerpo entero es esta regla, asi que se
-- reescribe completa. Cambia de sentido: "sin ambito" pasa a significar "no
-- declara filas", que es lo unico que decide desde hoy.
CREATE OR REPLACE FUNCTION atributo_sin_ambito(id_atributo bigint)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = c.id_catalogo_atributo)
      FROM catalogo_atributo c
     WHERE c.id_catalogo_atributo = id_atributo
       AND c.sujeto = 'PROPIEDAD';
$$;

COMMENT ON FUNCTION atributo_sin_ambito(bigint) IS
    'true si el atributo de PROPIEDAD no declara ninguna fila en catalogo_atributo_tipo: existe y no se pregunta nunca. Desde V86 no mira `aplica_todos`, que dejo de ser autoridad.';


-- ---------------------------------------------------------------------
-- 3. La guarda - el campo no se puede mantener por su cuenta.
--
-- Invariante: `aplica_todos = true` EXIGE una fila por cada uno de los siete
-- tipos. En las dos direcciones, que es la mitad que se olvida:
--
--   poner el campo sin las filas          -> rechazado (trigger en catalogo_atributo)
--   quitar las filas dejando el campo     -> rechazado (trigger en catalogo_atributo_tipo)
--
-- DEFERRABLE INITIALLY DEFERRED porque la fila del catalogo se inserta ANTES
-- que sus filas por tipo: una guarda inmediata rechazaria toda alta legitima.
-- Se comprueba al COMMIT, cuando la transaccion ya dijo todo lo que tenia que
-- decir.
--
-- Lo que esta guarda NO hace: obligar a `aplica_todos = true` a toda clave
-- que declare los siete tipos. `estado_ocupacion` aplica a los siete con
-- filas explicitas y `aplica_todos = false`, y asi lo quiso D-C5-1: son dos
-- cosas distintas en el esquema y la de las filas es la que manda.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_que_las_filas_respalden_aplica_todos()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    id     bigint;
    fila   record;
    faltan text;
BEGIN
    id := CASE TG_OP WHEN 'DELETE' THEN OLD.id_catalogo_atributo
                     ELSE NEW.id_catalogo_atributo END;

    SELECT c.clave, c.sujeto, c.aplica_todos INTO fila
      FROM catalogo_atributo c WHERE c.id_catalogo_atributo = id;
    -- La clave desaparecio en la misma transaccion: no hay nada que respaldar.
    IF NOT FOUND OR NOT fila.aplica_todos THEN
        RETURN NULL;
    END IF;

    IF fila.sujeto <> 'PROPIEDAD' THEN
        RAISE EXCEPTION 'La clave "%" es del ENCARGO y lleva `aplica_todos`: su aplicabilidad vive en catalogo_atributo_operacion, que es la unica que puede respaldarla.', fila.clave
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT string_agg(t.tipo, ', ' ORDER BY t.tipo) INTO faltan
      FROM tipos_de_propiedad() AS t(tipo)
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo x
                        WHERE x.id_catalogo_atributo = id
                          AND x.tipo_propiedad = t.tipo);

    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION '`aplica_todos` de la clave "%" no esta respaldado: faltan filas en catalogo_atributo_tipo para %. Desde V86 la aplicabilidad la decide esa tabla y el campo solo la resume, asi que un campo sin sus filas seria una segunda autoridad diciendo lo contrario.', fila.clave, faltan
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER tg_aplica_todos_respaldado
    AFTER INSERT OR UPDATE ON catalogo_atributo
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION exigir_que_las_filas_respalden_aplica_todos();

CREATE CONSTRAINT TRIGGER tg_aplica_todos_respaldado_al_retirar_filas
    AFTER DELETE OR UPDATE ON catalogo_atributo_tipo
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION exigir_que_las_filas_respalden_aplica_todos();


-- ---------------------------------------------------------------------
-- 4. Evidencia - se mide lo que acaba de quedar, no lo que se esperaba.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    con_campo  bigint;
    respaldado bigint;
    sin_filas  bigint;
    cuerpos    bigint;
BEGIN
    SELECT count(*) INTO con_campo FROM catalogo_atributo WHERE aplica_todos;

    SELECT count(*) INTO respaldado
      FROM catalogo_atributo c
     WHERE c.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM tipos_de_propiedad() AS t(tipo)
                        WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo x
                                           WHERE x.id_catalogo_atributo = c.id_catalogo_atributo
                                             AND x.tipo_propiedad = t.tipo));

    IF con_campo <> respaldado THEN
        RAISE EXCEPTION 'V86: quedaron % claves con `aplica_todos` sin sus siete filas', con_campo - respaldado;
    END IF;

    SELECT count(*) INTO sin_filas
      FROM catalogo_atributo c
     WHERE c.activo AND c.sujeto = 'PROPIEDAD'
       AND atributo_sin_ambito(c.id_catalogo_atributo);

    SELECT count(*) INTO cuerpos
      FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'public' AND p.prosrc LIKE '%aplica_todos%'
       AND p.proname <> 'exigir_que_las_filas_respalden_aplica_todos';
    IF cuerpos > 0 THEN
        RAISE EXCEPTION 'V86: % funciones siguen consultando `aplica_todos` como autoridad', cuerpos;
    END IF;

    RAISE NOTICE 'V86: % claves con `aplica_todos`, las % respaldadas por sus siete filas; % claves activas de PROPIEDAD sin aplicabilidad declarada; 0 cuerpos PL/pgSQL con autoridad sobre el campo',
        con_campo, respaldado, sin_filas;
END $$;
