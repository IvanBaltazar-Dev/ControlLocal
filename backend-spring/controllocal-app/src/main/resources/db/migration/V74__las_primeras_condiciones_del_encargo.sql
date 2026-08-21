-- =====================================================================
-- V74 - Las primeras condiciones del Encargo (cierre del Corte 0C)
--
-- V73 abrio el segundo sujeto: dijo que un dato gobernado puede ser de la
-- PROPIEDAD o del ENCARGO, y construyo el enrutamiento entero -- catalogo,
-- valores, triggers, aplicabilidad por (tipo, operacion). No sembro ni una
-- clave. Esta lo hace, y va aparte por la misma razon por la que V72 separo
-- capacidad de siembra: son dos decisiones distintas y se auditan distinto.
--   * si el enrutamiento esta mal, se arregla aqui y se prueba con `zz_*`;
--   * si una clave esta mal repartida, es una decision de negocio y se
--     discute mirando la clave, no el mecanismo.
--
-- LA REGLA DEL REPARTO, que es lo unico que decide esta migracion:
--
--     Si al firmar el siguiente encargo el dato puede cambiar sin que la
--     propiedad haya cambiado, es del ENCARGO.
--
-- `amoblado` es el caso que lo prueba, y por eso las dos claves conviven a
-- proposito:
--
--     amoblado            PROPIEDAD  -> el inmueble TIENE muebles (hecho)
--     se_ofrece_amoblado  ENCARGO    -> este alquiler los INCLUYE (pacto)
--
-- La misma vivienda con los mismos muebles puede venderse sin ellos y
-- alquilarse con ellos, y dos alquileres sucesivos pueden decidirlo distinto.
-- Con un solo sujeto la tercera historia era irrepresentable: el valor se
-- sobrescribia y nadie se enteraba.
--
-- NINGUNA SE SIEMBRA COMO OBLIGATORIA. Las seis entran OPC.
-- Que una garantia sea imprescindible para publicar un alquiler puede ser
-- cierto, pero es una decision del negocio que nadie ha tomado todavia, y
-- tomarla de contrabando en la migracion que introduce la clave dejaria
-- fichas ya publicadas incompletas de golpe. Subirla a PUB es una linea de
-- SQL el dia que se decida.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Las claves.
--
-- `del_sistema` y `organizacion_id` NULL: son vocabulario comun, como las
-- diecinueve fisicas. Una corredora puede anadir las suyas encima, no
-- redefinir estas.
--
-- `aplica_todos = false` en todas, sin excepcion: una condicion comercial
-- que aplicara "a todo" se saltaria la tabla de aplicabilidad y con ella la
-- distincion entre venta y alquiler, que es justamente lo que este corte
-- vino a poder representar.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                               aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                               valor_minimo, valor_maximo)
VALUES
    (NULL, 'garantia_meses', 'Garantia', 'ENTERO', 'meses',
     false, true, 300, 'ENCARGO', 'condiciones',
     'Cuantas rentas se entregan en garantia al firmar.', 0, 24),

    (NULL, 'adelanto_meses', 'Adelanto', 'ENTERO', 'meses',
     false, true, 301, 'ENCARGO', 'condiciones',
     'Cuantas rentas se pagan por adelantado al firmar.', 0, 12),

    (NULL, 'plazo_minimo_meses', 'Plazo minimo', 'ENTERO', 'meses',
     false, true, 302, 'ENCARGO', 'condiciones',
     'Duracion minima que acepta el propietario para este alquiler.', 1, 120),

    (NULL, 'disponible_desde', 'Disponible desde', 'FECHA', NULL,
     false, true, 303, 'ENCARGO', 'condiciones',
     'Desde cuando se puede entregar. Distinto de la fecha del encargo.',
     NULL, NULL),

    (NULL, 'mascotas_aceptadas', 'Acepta mascotas', 'BOOLEANO', NULL,
     false, true, 304, 'ENCARGO', 'condiciones',
     'Si el propietario acepta mascotas en este alquiler.', NULL, NULL),

    -- La gemela comercial de `amoblado`. El rotulo dice "Se ofrece" y no
    -- "Amoblado" a proposito: en la ficha las dos se ven a la vez y tienen
    -- que poder distinguirse leyendolas.
    (NULL, 'se_ofrece_amoblado', 'Se ofrece amoblado', 'BOOLEANO', NULL,
     false, true, 305, 'ENCARGO', 'condiciones',
     'Si este alquiler incluye los muebles. El inmueble puede tenerlos y no incluirlos.',
     NULL, NULL);

-- ---------------------------------------------------------------------
-- 2. A que (tipo, operacion) aplica cada una.
--
-- Las cuatro primeras son condiciones del ALQUILER en cualquier tipo: un
-- terreno tambien se alquila con garantia y con plazo minimo.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'),('O'),('D'),('C'),('T'),('A'),('X')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('garantia_meses', 'adelanto_meses', 'plazo_minimo_meses');

-- `disponible_desde` es de las dos operaciones. Un piso vendido tambien se
-- entrega en una fecha, y esa fecha no es la del encargo ni la del contrato.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, o.op, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'),('O'),('D'),('C'),('T'),('A'),('X')) AS t(tipo)
  CROSS JOIN (VALUES ('A'),('V')) AS o(op)
 WHERE c.organizacion_id IS NULL
   AND c.clave = 'disponible_desde';

-- Mascotas: donde vive alguien. Preguntarlo en el alquiler de un almacen no
-- es inofensivo -- una pregunta que no viene a cuento ensena a ignorar el
-- formulario, y un formulario que se ignora deja de capturar el dato bueno.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'),('C')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave = 'mascotas_aceptadas';

-- Amoblado: vivienda y oficina. Una oficina amoblada es un producto real y
-- se anuncia como tal; un terreno amoblado no significa nada.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'),('C'),('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave = 'se_ofrece_amoblado';

-- ---------------------------------------------------------------------
-- 3. Las guardas.
--
-- Cuentan lo que acaba de entrar en vez de comprobar un numero escrito a
-- mano. V72 aprendio por que: su auditoria decia "cuatro filas requeridas" y
-- en la base viva eran diez, y con la cifra literal la migracion habria
-- abortado -- o peor, alguien la habria "arreglado" bajando el numero.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    sin_aplicabilidad TEXT;
    mal_sujeto        TEXT;
    duplicadas        TEXT;
BEGIN
    -- Ninguna clave del ENCARGO puede quedarse sin decir a que aplica: seria
    -- invisible en todos los guiones y nadie lo notaria hasta echarla en falta.
    SELECT string_agg(c.clave, ', ') INTO sin_aplicabilidad
      FROM catalogo_atributo c
     WHERE c.sujeto = 'ENCARGO' AND c.activo AND NOT c.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo);
    IF sin_aplicabilidad IS NOT NULL THEN
        RAISE EXCEPTION 'Claves del ENCARGO sin aplicabilidad declarada: %',
            sin_aplicabilidad;
    END IF;

    -- Y ninguna puede haber declarado la suya en la tabla del otro sujeto.
    SELECT string_agg(c.clave, ', ') INTO mal_sujeto
      FROM catalogo_atributo c
     WHERE c.sujeto = 'ENCARGO' AND c.activo
       AND EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                    WHERE t.id_catalogo_atributo = c.id_catalogo_atributo);
    IF mal_sujeto IS NOT NULL THEN
        RAISE EXCEPTION 'Claves del ENCARGO con aplicabilidad por tipo: %', mal_sujeto;
    END IF;

    -- `se_ofrece_amoblado` y `amoblado` tienen que ser DOS claves distintas.
    -- Si alguien las unificara "porque es lo mismo", el pacto volveria a
    -- pisar al hecho y este corte no habria servido de nada.
    SELECT string_agg(clave, ', ') INTO duplicadas
      FROM catalogo_atributo
     WHERE clave IN ('amoblado', 'se_ofrece_amoblado') AND activo
     GROUP BY sujeto HAVING count(*) > 1;
    IF duplicadas IS NOT NULL THEN
        RAISE EXCEPTION 'El hecho y el pacto acabaron en el mismo sujeto: %', duplicadas;
    END IF;

    RAISE NOTICE 'V74: % condiciones de encargo sembradas, % filas de aplicabilidad.',
        (SELECT count(*) FROM catalogo_atributo
          WHERE sujeto = 'ENCARGO' AND del_sistema AND activo),
        (SELECT count(*) FROM catalogo_atributo_operacion o
           JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
          WHERE c.del_sistema);
END $$;
