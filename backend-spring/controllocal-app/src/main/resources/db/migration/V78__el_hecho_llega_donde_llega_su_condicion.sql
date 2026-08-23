-- =====================================================================
-- V78 - El hecho llega donde llega su condicion
--
-- El Corte 0C partio el catalogo en dos sujetos y V77 completo el idioma del
-- ENCARGO. Con las dos mitades sembradas se pudo hacer por primera vez una
-- pregunta que antes no tenia sentido:
--
--     Hay algun tipo de propiedad donde la CONDICION se pueda pactar y el
--     HECHO no se pueda escribir?
--
-- Donde eso ocurre, la separacion de sujetos existe sobre el papel y no en la
-- practica: si el hecho no cabe en ninguna parte, la unica casilla disponible
-- para escribirlo es la de su condicion. El pacto vuelve a hacer de hecho, que
-- es exactamente lo que V73 vino a impedir.
--
-- La pregunta se corrio como SQL sobre los ocho pares deliberados y devolvio
-- TRES filas, ni una mas:
--
--     amoblado             / se_ofrece_amoblado       / O
--     cuota_mantenimiento  / mantenimiento_a_cargo_de / A
--     cuota_mantenimiento  / mantenimiento_a_cargo_de / C
--
-- NINGUNO DE LOS TRES ES UN DESCUIDO DE V77. El de `amoblado` lo abrio V74 a
-- proposito -- "una oficina amoblada es un producto real y se anuncia como
-- tal" -- ampliando la condicion a O sin ampliar el hecho. Los de
-- `cuota_mantenimiento` son el mismo movimiento: `mantenimiento_a_cargo_de`
-- llega a A y C porque un almacen en parque logistico y una casa en condominio
-- pagan mantenimiento, y ahi la cuota no tenia donde registrarse.
--
-- ESTA MIGRACION SOLO AMPLIA APLICABILIDAD. No crea claves, no cambia ningun
-- `tipo_dato`, no toca ningun trigger y no mueve ningun valor. Las tres
-- combinaciones se midieron en las dos bases -- `controllocal_dev` y
-- `controllocal_repositorios` -- y tienen CERO valores: `amoblado` esta en C
-- (175) y D (337) y en ninguna oficina; `cuota_mantenimiento` en D (302),
-- L (386) y O (96) y en ninguna casa ni almacen. No hay nada que reinterpretar
-- ni nada que quede huerfano.
--
-- LAS TRES ENTRAN 'OPC'. `exigencia` es NOT NULL sin defecto: hay que elegirla
-- en el mismo INSERT. Entrar como PUB dejaria de golpe sin poder publicarse a
-- toda casa, todo almacen y toda oficina que no tenga el dato -- que son
-- todas, porque el dato acaba de nacer. Subirlas es una linea de SQL el dia
-- que el negocio lo decida.
--
-- Y ESCRIBEN `requerido` ADEMAS DE `exigencia`. Son columna y espejo desde
-- V72, hoy coherentes al 100 %, y una fila que escriba solo una de las dos
-- rompe esa coherencia en silencio.
--
-- LO QUE ESTA MIGRACION NO HACE, y no por olvido:
--   * `cuota_mantenimiento` DECIMAL -> IMPORTE. Bloqueado dos veces: por
--     `tg_catalogo_sistema_inmutable` (el tipo de una clave del sistema no
--     cambia) y por el dato (784 filas sin moneda, y ninguna fuente de la que
--     deducirla sin inventarla).
--   * `rubro_permitido` -> LISTA_MULTIPLE, `zonificacion` -> LISTA,
--     `banos` -> ENTERO. El mismo trigger, y cada una necesita un vocabulario
--     que todavia no existe.
--   * `servicios_disponibles`. Es un hecho de la PROPIEDAD y esta bien
--     colocado; lo que le falta es vocabulario. Declarada LISTA y sin una sola
--     opcion sembrada, `MotorDeCaptura.controlDe` la degrada a TEXTO y el
--     trigger acepta cualquier cadena. Es deuda declarada, no un hallazgo de
--     esta migracion, y se resuelve en el corte que evolucione el catalogo:
--     aqui no se le inventa un vocabulario ni se le cambia el tipo.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. La foto de lo que ya hay.
--
-- La guarda 2.3 la usa para afirmar que esta migracion NO PERDIO ninguna
-- aplicabilidad. Sin la foto, "no se perdio nada" seria una suposicion: un
-- conteo final coincide igual si se borra una fila y se anaden dos.
--
-- Se retira al final con un DROP explicito, no con ON COMMIT DROP: asi la
-- foto sobrevive tanto si Flyway envuelve la migracion en una transaccion
-- como si no, y la guarda no puede fallar por no encontrar su propia tabla.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE aplicabilidad_antes AS
SELECT c.clave, t.tipo_propiedad, t.exigencia, t.requerido
  FROM catalogo_atributo_tipo t
  JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo;

-- ---------------------------------------------------------------------
-- 1. Las tres filas.
--
-- Una oficina amoblada: el hecho fisico de que tiene muebles. Que este
-- alquiler los incluya o no lo sigue diciendo `se_ofrece_amoblado`, que es
-- otra pregunta y vive en el otro sujeto.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'O', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL AND c.clave = 'amoblado';

-- La cuota que cobra la junta de propietarios o la administracion del parque
-- logistico. Quien la paga en este encargo lo sigue diciendo
-- `mantenimiento_a_cargo_de`.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'),('C')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'cuota_mantenimiento';

-- ---------------------------------------------------------------------
-- 2. Las guardas.
--
-- Comprueban invariantes sobre el estado resultante, no cifras escritas a
-- mano: una cifra literal o aborta una migracion sana o invita a que alguien
-- la "arregle" bajandola.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    huecos   TEXT;
    perdidas TEXT;
    espejo   TEXT;
    cruce    TEXT;
    entraron INT;
    par      RECORD;
BEGIN
    -- 2.1 Las tres filas entraron. Si una clave no estuviera en el catalogo el
    --     SELECT no habria insertado nada y la migracion terminaria "bien".
    SELECT count(*) INTO entraron
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL
       AND ((c.clave = 'amoblado' AND t.tipo_propiedad = 'O')
         OR (c.clave = 'cuota_mantenimiento' AND t.tipo_propiedad IN ('A','C')));
    IF entraron <> 3 THEN
        RAISE EXCEPTION
            'Se esperaban 3 aplicabilidades (amoblado/O, cuota_mantenimiento/A y /C) y hay %',
            entraron;
    END IF;

    -- 2.2 LA COMPROBACION QUE DA SENTIDO AL CORTE. Para cada par deliberado
    --     cuyo lado PROPIEDAD existe, no puede quedar ningun tipo donde la
    --     condicion se pacte y el hecho no se pueda escribir.
    --
    --     Los pares cuyo hecho todavia no existe no participan: no se le exige
    --     cobertura a algo que no ha nacido. Lo que se prohibe es que un hecho
    --     EXISTENTE llegue menos lejos que su condicion.
    FOR par IN
        SELECT * FROM (VALUES
            ('amoblado',              'se_ofrece_amoblado'),
            ('cuota_mantenimiento',   'mantenimiento_a_cargo_de'),
            ('estacionamientos',      'estacionamientos_incluidos'),
            ('rubro_permitido',       'rubros_excluidos_por_titular'),
            ('mascotas_reglamento',   'mascotas_aceptadas'),
            ('nivel_implementacion',  'se_entrega_implementado'),
            ('estado_ocupacion',      'entrega_desocupado'),
            ('lote_minimo_normativo', 'acepta_venta_fraccionada')
        ) AS p(hecho, condicion)
    LOOP
        SELECT string_agg(DISTINCT o.tipo_propiedad, ', ') INTO huecos
          FROM catalogo_atributo h
          JOIN catalogo_atributo c ON c.clave = par.condicion AND c.activo
          JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
         WHERE h.clave = par.hecho AND h.activo AND NOT h.aplica_todos
           AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                            WHERE t.id_catalogo_atributo = h.id_catalogo_atributo
                              AND t.tipo_propiedad = o.tipo_propiedad);
        IF huecos IS NOT NULL THEN
            RAISE EXCEPTION
                'El hecho "%" no llega a % y su condicion "%" si: ahi el pacto seria el unico sitio donde cabe el hecho.',
                par.hecho, huecos, par.condicion;
        END IF;
    END LOOP;

    -- 2.3 Nada de lo que habia se perdio. Ampliar no es reescribir.
    SELECT string_agg(a.clave || '/' || a.tipo_propiedad, ', ') INTO perdidas
      FROM aplicabilidad_antes a
     WHERE NOT EXISTS (
            SELECT 1 FROM catalogo_atributo_tipo t
              JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
             WHERE c.clave = a.clave AND t.tipo_propiedad = a.tipo_propiedad
               AND t.exigencia = a.exigencia AND t.requerido = a.requerido);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION 'Esta migracion perdio o cambio aplicabilidad que ya existia: %', perdidas;
    END IF;

    -- 2.4 `requerido` sigue siendo espejo exacto de `exigencia`.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO espejo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE t.requerido <> (t.exigencia = 'ALT');
    IF espejo IS NOT NULL THEN
        RAISE EXCEPTION 'requerido y exigencia divergen en: %', espejo;
    END IF;

    -- 2.5 Y el enrutamiento por sujeto sigue intacto en las dos direcciones:
    --     esta migracion escribe en la tabla de la PROPIEDAD y tiene que
    --     hacerlo solo para claves de la PROPIEDAD.
    SELECT string_agg(c.clave, ', ') INTO cruce
      FROM catalogo_atributo c
     WHERE c.activo
       AND ((c.sujeto = 'ENCARGO'
             AND EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                          WHERE t.id_catalogo_atributo = c.id_catalogo_atributo))
         OR (c.sujeto = 'PROPIEDAD'
             AND EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                          WHERE o.id_catalogo_atributo = c.id_catalogo_atributo)));
    IF cruce IS NOT NULL THEN
        RAISE EXCEPTION 'Claves con la aplicabilidad declarada en la tabla del otro sujeto: %', cruce;
    END IF;
END $$;

DROP TABLE aplicabilidad_antes;
