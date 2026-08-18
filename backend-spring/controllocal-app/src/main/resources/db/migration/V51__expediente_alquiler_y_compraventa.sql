-- =====================================================================
-- V51 - M5 del modelo universal: el cierre admite compraventa.
--
-- QUE PROBLEMA CIERRA
-- `solicitud_alquiler` es el expediente de cierre, y su nombre dice de que
-- operacion. Una compraventa tiene arras, minuta, escritura y bloqueo
-- registral, y no tiene garantia ni adelanto: no cabe ahi sin llenar la tabla
-- de columnas nulas y de `if (esVenta)`.
--
-- POR QUE NO UNA TABLA NUEVA
-- El 80 % de la maquinaria es la misma: documentos, revision, evaluacion del
-- broker, comision, trazabilidad. Duplicarla significaria duplicar tambien
-- `documento_solicitud`, `evaluacion_solicitud` y media docena de consultas.
-- Un expediente CON TIPO, y lo especifico de la compraventa en una tabla 1:1
-- que solo existe cuando hace falta.
--
-- EL TIPO NO SE ELIGE: SE DERIVA
-- Sale de la operacion del encargo del que cuelga la oportunidad. Un trigger lo
-- comprueba, para que nadie pueda abrir un expediente de compraventa sobre un
-- encargo de alquiler.
--
-- LA TABLA NO SE RENOMBRA TODAVIA
-- `solicitud_alquiler` seguira llamandose asi hasta que el modulo de cierre
-- migre: la entidad la mapea y renombrarla ahora rompe el arranque. El nombre
-- del dominio ya es `Expediente` (D-E4-1) y este es el paso de esquema.
-- =====================================================================

-- ---------------------------------------------------------------------
-- El tipo. 'A' para todo lo existente, que es lo que de hecho es.
-- ---------------------------------------------------------------------
ALTER TABLE solicitud_alquiler
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(1) NOT NULL DEFAULT 'A';

ALTER TABLE solicitud_alquiler
    ALTER COLUMN tipo DROP DEFAULT;

ALTER TABLE solicitud_alquiler
    ADD CONSTRAINT ck_expediente_tipo CHECK (tipo IN ('A', 'V'));

COMMENT ON COLUMN solicitud_alquiler.tipo IS
    'A expediente de alquiler, V de compraventa. Se DERIVA de la operacion del encargo. D-E4-1 M5.';

-- Las condiciones de alquiler solo tienen sentido en un expediente de alquiler.
ALTER TABLE solicitud_alquiler
    ADD CONSTRAINT ck_expediente_condiciones_alquiler
    CHECK (tipo = 'A'
           OR (plazo_contrato_meses IS NULL
               AND meses_garantia IS NULL
               AND meses_adelanto IS NULL));

CREATE INDEX IF NOT EXISTS ix_expediente_tipo
    ON solicitud_alquiler (organizacion_id, tipo, estado);

-- ---------------------------------------------------------------------
-- Lo especifico de la compraventa. 1:1 y solo cuando el expediente es 'V'.
-- ---------------------------------------------------------------------
CREATE TABLE condicion_compraventa (
    id_solicitud         BIGINT        PRIMARY KEY REFERENCES solicitud_alquiler (id_solicitud),
    organizacion_id      BIGINT        NOT NULL REFERENCES organizacion (id_organizacion),

    -- Arras: la senal. `tipo_arras` distingue confirmatorias de retractacion,
    -- que tienen consecuencias legales distintas si el trato se cae.
    monto_arras          NUMERIC(14,2),
    moneda_arras         VARCHAR(3),
    tipo_arras           VARCHAR(1),
    fecha_arras          DATE,

    -- Como se paga el saldo.
    forma_pago_saldo     VARCHAR(1)    NOT NULL,
    entidad_financiera   VARCHAR(120),
    monto_inicial        NUMERIC(14,2),
    monto_financiado     NUMERIC(14,2),

    -- Hitos del cierre.
    fecha_minuta         DATE,
    fecha_escritura      DATE,
    partida_registral    VARCHAR(40),
    bloqueo_registral    BOOLEAN       NOT NULL DEFAULT false,

    observaciones        TEXT,
    fecha_creacion       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_actualizacion  TIMESTAMPTZ,

    -- C contado, F financiado con entidad, M mixto.
    CONSTRAINT ck_compraventa_forma_pago
        CHECK (forma_pago_saldo IN ('C', 'F', 'M')),
    -- C confirmatorias, R de retractacion.
    CONSTRAINT ck_compraventa_tipo_arras
        CHECK (tipo_arras IS NULL OR tipo_arras IN ('C', 'R')),
    CONSTRAINT ck_compraventa_arras_completas
        CHECK ((monto_arras IS NULL AND moneda_arras IS NULL AND tipo_arras IS NULL)
               OR (monto_arras IS NOT NULL AND moneda_arras IS NOT NULL AND tipo_arras IS NOT NULL)),
    CONSTRAINT ck_compraventa_moneda
        CHECK (moneda_arras IS NULL OR moneda_arras IN ('PEN', 'USD')),
    CONSTRAINT ck_compraventa_financiacion
        CHECK (forma_pago_saldo <> 'F' OR entidad_financiera IS NOT NULL),
    CONSTRAINT ck_compraventa_escritura
        CHECK (fecha_escritura IS NULL OR fecha_minuta IS NULL OR fecha_escritura >= fecha_minuta)
);

COMMENT ON TABLE condicion_compraventa IS
    'Arras, financiacion y hitos registrales de un expediente de compraventa. D-E4-1 M5.';

CREATE INDEX ix_condicion_compraventa_organizacion
    ON condicion_compraventa (organizacion_id);

-- ---------------------------------------------------------------------
-- El tipo del expediente se deriva de la operacion de su encargo.
--
-- Camino: expediente -> oportunidad -> captacion -> motivo_operacion.
-- Si la oportunidad no cuelga de una captacion, no hay nada que comprobar y se
-- deja pasar: el dato del encargo es la fuente, no una obligacion nueva.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_tipo_expediente_del_encargo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    op_encargo varchar(1);
BEGIN
    SELECT c.motivo_operacion INTO op_encargo
      FROM oportunidad_comercial o
      JOIN captacion c ON c.id_captacion = o.id_captacion
     WHERE o.id_oportunidad = NEW.id_oportunidad;

    IF op_encargo IS NOT NULL AND op_encargo <> NEW.tipo THEN
        RAISE EXCEPTION
            'El expediente es de tipo % pero su encargo es de operacion %',
            NEW.tipo, op_encargo
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_expediente_tipo_del_encargo
    BEFORE INSERT OR UPDATE ON solicitud_alquiler
    FOR EACH ROW
    EXECUTE FUNCTION exigir_tipo_expediente_del_encargo();

-- Y la condicion de compraventa solo cuelga de un expediente de compraventa.
CREATE OR REPLACE FUNCTION exigir_compraventa_en_expediente_de_venta()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    tipo_exp varchar(1);
BEGIN
    SELECT tipo INTO tipo_exp FROM solicitud_alquiler WHERE id_solicitud = NEW.id_solicitud;
    IF tipo_exp <> 'V' THEN
        RAISE EXCEPTION 'Solo un expediente de compraventa admite condiciones de compraventa'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_compraventa_solo_en_venta
    BEFORE INSERT OR UPDATE ON condicion_compraventa
    FOR EACH ROW
    EXECUTE FUNCTION exigir_compraventa_en_expediente_de_venta();

-- ---------------------------------------------------------------------
-- Los documentos exigidos dependen de la operacion Y del tipo de propiedad.
--
-- `tipo_documento_requerido` ya discriminaba por operacion, pero su CHECK la
-- ataba a 'A': el catalogo no podia ni nombrar un documento de venta. Y una
-- compraventa de terreno no pide los mismos papeles que la de un departamento.
-- ---------------------------------------------------------------------
ALTER TABLE tipo_documento_requerido
    DROP CONSTRAINT IF EXISTS ck_tipo_documento_operacion;

ALTER TABLE tipo_documento_requerido
    ADD CONSTRAINT ck_tipo_documento_operacion CHECK (tipo_operacion IN ('A', 'V'));

ALTER TABLE tipo_documento_requerido
    ADD COLUMN IF NOT EXISTS tipo_propiedad VARCHAR(1);

COMMENT ON COLUMN tipo_documento_requerido.tipo_propiedad IS
    'NULL = exigido para cualquier tipo de propiedad. D-E4-1 M5.';

ALTER TABLE tipo_documento_requerido
    ADD CONSTRAINT ck_tipo_documento_propiedad
    CHECK (tipo_propiedad IS NULL OR tipo_propiedad IN ('L', 'O', 'D', 'C', 'T', 'A', 'X'));

-- La unicidad pasa a incluir el tipo de propiedad: el mismo documento puede
-- pedirse para terreno y no para departamento.
ALTER TABLE tipo_documento_requerido
    DROP CONSTRAINT IF EXISTS uq_tipo_documento_operacion;

CREATE UNIQUE INDEX uq_tipo_documento_operacion_propiedad
    ON tipo_documento_requerido (tipo_operacion, tipo_documento, COALESCE(tipo_propiedad, '*'));

-- ---------------------------------------------------------------------
-- Documentos minimos de una compraventa. Son los del expediente del comprador,
-- los mismos para cualquier tipo de inmueble salvo la partida, que en terreno
-- pide ademas el plano perimetrico.
-- ---------------------------------------------------------------------
INSERT INTO tipo_documento_requerido (tipo_operacion, tipo_documento, obligatorio, activo, descripcion, tipo_propiedad)
VALUES
    ('V', 'DOCUMENTO_IDENTIDAD',   true,  true, 'Documento de identidad del comprador',            NULL),
    ('V', 'PARTIDA_REGISTRAL',     true,  true, 'Partida registral vigente del inmueble',          NULL),
    ('V', 'CERTIFICADO_GRAVAMEN',  true,  true, 'Certificado registral de cargas y gravamenes',    NULL),
    ('V', 'HR_PU',                 true,  true, 'Hoja resumen y predio urbano del ano en curso',   NULL),
    ('V', 'DEUDA_MUNICIPAL',       true,  true, 'Constancia de no adeudo de arbitrios e impuesto', NULL),
    ('V', 'MINUTA_COMPRAVENTA',    false, true, 'Minuta firmada por las partes',                   NULL),
    ('V', 'CARTA_APROBACION',      false, true, 'Aprobacion de credito de la entidad financiera',  NULL),
    ('V', 'PLANO_PERIMETRICO',     true,  true, 'Plano perimetrico y de ubicacion visado',         'T')
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    de_venta bigint;
BEGIN
    SELECT count(*) INTO de_venta FROM tipo_documento_requerido WHERE tipo_operacion = 'V';
    RAISE NOTICE 'V51: expediente con tipo; % documentos de compraventa en catalogo', de_venta;
END $$;
