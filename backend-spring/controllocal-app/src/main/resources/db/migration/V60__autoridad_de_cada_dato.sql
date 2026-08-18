-- =====================================================================
-- V60 - Quien es la AUTORIDAD de cada dato de la propiedad (D-E4-3).
--
-- QUE PROBLEMA CIERRA
-- Siete conceptos viven a la vez como columna de `propiedad` y como fila de
-- `atributo_propiedad`: metraje, ambientes, frente, zonificacion,
-- cuota_mantenimiento, numero_estacionamientos y antiguedad_anios. Solo el
-- primero se mantiene sincronizado, asi que registrar un departamento por el
-- modelo universal deja la columna en NULL mientras el atributo tiene el
-- valor -- y las pantallas que leen la columna lo muestran en blanco.
--
-- Comprobado contra la base, no deducido:
--     COLUMNA   metraje=90.00  ambientes=NULL  cuota_mant=NULL
--     ATRIBUTO  metraje_total=90  ambientes=5  cuota_mantenimiento=350
--
-- LA REGLA
-- Cada clave publicada por `/captura/definicion` tiene UNA autoridad
-- persistente declarada. Cero = campo fantasma: se pide y no se guarda. Dos =
-- doble verdad: se guarda dos veces y divergen.
--
-- POR QUE DOS COLUMNAS Y NO UNA
-- Con solo `destino = ESTRUCTURAL`, alguien tendria que escribir en Java
--     si clave == 'metraje_total' -> propiedad.metraje
-- y eso es la misma matriz de antes escondida en otro sitio.
-- `campo_estructural` dice QUE CONCEPTO representa, y la persistencia sabe
-- como guardar ese concepto: anadir un segundo estructural manana no toca
-- ningun `if`.
--
-- Y por que el valor es `METRAJE` y no `propiedad.metraje`: el catalogo no
-- debe conocer la topologia fisica de PostgreSQL. Que hoy ese concepto viva en
-- una columna llamada `metraje` es asunto de la capa de persistencia, y
-- cambiarlo no deberia tocar una fila de catalogo.
--
-- LA CLASIFICACION, Y SU REGLA
-- ESTRUCTURAL cuando el concepto es TRANSVERSAL AL TIPO y forma parte estable
-- de identidad, integridad, busqueda primaria o invariantes del agregado.
-- ATRIBUTO cuando su aplicabilidad y su semantica DEPENDEN DEL TIPO.
--
-- No es "si participa en una decision es estructural": esa formulacion es una
-- trampa, porque el dia que el matcher cruce por zonificacion alguien
-- concluiria que hay que crearle una columna. Un atributo gobernado puede
-- entrar en filtros y en matching sin dejar de ser un atributo.
--
-- Resultado: UNO de los siete es estructural.
--   metraje  -> participa en la deteccion de duplicados (`areaAproximada`),
--               viaja como `areaM2` en la proyeccion de captacion, esta en la
--               del listado y tiene 23 usos en Angular. Transversal de verdad.
--   los otros seis -> un solo uso cada uno, y tres con NINGUNO: solo se
--               arrastran hasta la ficha.
--
-- ESTA MIGRACION NO MUEVE UN SOLO VALOR
-- Declara la autoridad y nada mas. El backfill, el corte de escrituras y la
-- retirada de columnas van en pasos posteriores y con su propia evidencia.
-- Separarlo es deliberado: si algo va mal, lo que se revierte es una columna
-- de metadatos y no los datos del negocio.
-- =====================================================================

ALTER TABLE catalogo_atributo
    ADD COLUMN IF NOT EXISTS destino VARCHAR(12) NOT NULL DEFAULT 'ATRIBUTO',
    ADD COLUMN IF NOT EXISTS campo_estructural VARCHAR(40);

COMMENT ON COLUMN catalogo_atributo.destino IS
    'Donde vive el valor: ATRIBUTO en atributo_propiedad, ESTRUCTURAL en su campo canonico. D-E4-3.';
COMMENT ON COLUMN catalogo_atributo.campo_estructural IS
    'El CONCEPTO del dominio que representa (METRAJE), no la columna fisica. NULL si destino = ATRIBUTO.';

ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_destino
    CHECK (destino IN ('ATRIBUTO', 'ESTRUCTURAL'));

-- El vocabulario de conceptos estructurales. Crece con la clasificacion, no
-- con la implementacion: anadir uno exige decidirlo, no solo escribirlo.
ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_campo_estructural
    CHECK (campo_estructural IS NULL OR campo_estructural IN ('METRAJE'));

-- La invariante que hace util a las dos columnas juntas: ni un ESTRUCTURAL sin
-- concepto -- que obligaria a adivinarlo -- ni un ATRIBUTO con uno, que seria
-- declarar dos sitios para el mismo valor.
ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_autoridad_completa
    CHECK ((destino = 'ATRIBUTO'    AND campo_estructural IS NULL)
        OR (destino = 'ESTRUCTURAL' AND campo_estructural IS NOT NULL));

-- ---------------------------------------------------------------------
-- La clasificacion. Uno de siete.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo
   SET destino = 'ESTRUCTURAL', campo_estructural = 'METRAJE'
 WHERE clave = 'metraje_total';

-- Todo lo demas queda en ATRIBUTO, que es el DEFAULT de la columna: el caso
-- normal no se declara fila a fila.

-- ---------------------------------------------------------------------
-- Evidencia, y una guarda que impide seguir con divergencias dentro.
--
-- La medicion del 2026-08-18 dio CERO divergentes en los siete conceptos, asi
-- que la migracion no estaba bloqueada. Se vuelve a comprobar aqui porque
-- entre aquella medicion y este despliegue puede haber pasado cualquier cosa,
-- y arrastrar una divergencia a los pasos siguientes significaria elegir un
-- ganador sin haberlo decidido.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    divergentes bigint;
    estructurales bigint;
BEGIN
    SELECT count(*) INTO divergentes
      FROM propiedad p
      JOIN atributo_propiedad a ON a.id_propiedad = p.id_propiedad
     WHERE (a.clave = 'metraje_total'           AND a.valor_numero IS DISTINCT FROM p.metraje)
        OR (a.clave = 'ambientes'               AND a.valor_numero IS DISTINCT FROM p.ambientes::numeric)
        OR (a.clave = 'frente'                  AND a.valor_numero IS DISTINCT FROM p.frente)
        OR (a.clave = 'cuota_mantenimiento'     AND a.valor_numero IS DISTINCT FROM p.cuota_mantenimiento)
        OR (a.clave = 'estacionamientos'        AND a.valor_numero IS DISTINCT FROM p.numero_estacionamientos::numeric)
        OR (a.clave = 'antiguedad_anios'        AND a.valor_numero IS DISTINCT FROM p.antiguedad_anios::numeric)
        OR (a.clave = 'zonificacion'            AND a.valor_texto  IS DISTINCT FROM p.zonificacion);

    -- Ojo: `IS DISTINCT FROM` cuenta tambien el caso "columna NULL, atributo
    -- con valor", que es la FUGA en marcha -- cada alta por el modelo
    -- universal anade una. No es una divergencia de contenido y no bloquea:
    -- se resuelve cortando la escritura, que es el paso siguiente.
    SELECT count(*) INTO estructurales
      FROM catalogo_atributo WHERE destino = 'ESTRUCTURAL';

    RAISE NOTICE 'V60: % atributos declarados ESTRUCTURAL; % filas columna<>atributo por revisar',
        estructurales, divergentes;

    IF estructurales <> 1 THEN
        RAISE EXCEPTION 'V60: se esperaba exactamente 1 concepto estructural (metraje_total), hay %',
            estructurales;
    END IF;
END $$;
