-- =====================================================================
-- V49 - M3 del modelo universal: el historico de precios sabe de que encargo es.
--
-- QUE PROBLEMA CIERRA
-- `precio_propiedad` cuelga de la PROPIEDAD. Mientras una propiedad solo podia
-- estar en alquiler, eso bastaba. Con venta y alquiler simultaneos (D-E4-1) las
-- dos series se mezclarian en una sola linea: un hito de 180.000 y otro de
-- 2.900 uno detras de otro, sin forma de saber cual es cual salvo por magnitud
-- — que es justo lo que el modelo economico prohibe deducir.
--
-- QUE SE ANADE
--   `id_captacion`  el encargo al que pertenece la serie
--   `operacion`     A/V denormalizada, para leer la serie sin join
--
-- POR QUE `operacion` DENORMALIZADA
-- La consulta natural es "la serie de VENTA de esta propiedad". Con solo
-- `id_captacion` habria que unir contra captacion en cada lectura del historico,
-- que es la tabla que mas se lee al pintar una ficha. Un trigger la mantiene
-- coherente con su encargo, asi que no puede desincronizarse a mano.
--
-- LOS HUERFANOS NO SE INVENTAN
-- Un hito anterior a este cambio puede no tener encargo identificable (la
-- propiedad tuvo dos captaciones, o ninguna). Esos quedan con `id_captacion`
-- NULL y `operacion` 'A': todo lo que existe hoy en el sistema es alquiler, y
-- eso no es una suposicion sino el estado del negocio antes de la venta.
-- =====================================================================

ALTER TABLE precio_propiedad
    ADD COLUMN IF NOT EXISTS id_captacion BIGINT REFERENCES captacion (id_captacion),
    ADD COLUMN IF NOT EXISTS operacion    VARCHAR(1);

COMMENT ON COLUMN precio_propiedad.id_captacion IS
    'Encargo dueno de la serie. NULL solo en hitos anteriores a V49. D-E4-1 M3.';
COMMENT ON COLUMN precio_propiedad.operacion IS
    'A alquiler, V venta. Denormalizada del encargo y mantenida por trigger.';

-- ---------------------------------------------------------------------
-- Backfill. Se ata un hito a un encargo SOLO cuando no hay ambiguedad: la
-- propiedad tiene exactamente una captacion. Con dos o mas no se adivina.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    atados      bigint;
    ambiguos    bigint;
    sin_encargo bigint;
BEGIN
    UPDATE precio_propiedad pp
       SET id_captacion = c.id_captacion,
           operacion    = c.motivo_operacion
      FROM (SELECT id_propiedad, min(id_captacion) AS id_captacion,
                   min(motivo_operacion) AS motivo_operacion, count(*) AS cuantas
              FROM captacion
             GROUP BY id_propiedad) c
     WHERE c.id_propiedad = pp.id_propiedad
       AND c.cuantas = 1
       AND pp.id_captacion IS NULL;

    GET DIAGNOSTICS atados = ROW_COUNT;

    SELECT count(*) INTO ambiguos
      FROM precio_propiedad pp
     WHERE pp.id_captacion IS NULL
       AND (SELECT count(*) FROM captacion c WHERE c.id_propiedad = pp.id_propiedad) > 1;

    SELECT count(*) INTO sin_encargo
      FROM precio_propiedad pp
     WHERE pp.id_captacion IS NULL
       AND NOT EXISTS (SELECT 1 FROM captacion c WHERE c.id_propiedad = pp.id_propiedad);

    -- Todo lo que queda suelto es alquiler: es lo unico que el sistema ha
    -- sabido hacer hasta esta tanda.
    UPDATE precio_propiedad SET operacion = 'A' WHERE operacion IS NULL;

    RAISE NOTICE 'V49: % hitos atados a su encargo; % ambiguos y % sin encargo quedan como alquiler',
        atados, ambiguos, sin_encargo;
END $$;

ALTER TABLE precio_propiedad
    ALTER COLUMN operacion SET NOT NULL;

ALTER TABLE precio_propiedad
    ADD CONSTRAINT ck_precio_operacion CHECK (operacion IN ('A', 'V'));

-- La serie que de verdad se consulta: por propiedad, por operacion y en orden.
CREATE INDEX IF NOT EXISTS ix_precio_propiedad_operacion
    ON precio_propiedad (id_propiedad, operacion, fecha);

-- ---------------------------------------------------------------------
-- Coherencia: si el hito declara encargo, su operacion es la del encargo.
-- Y si no lo declara, se rellena la operacion del encargo cuando venga.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_operacion_del_encargo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    op_encargo varchar(1);
BEGIN
    IF NEW.id_captacion IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT motivo_operacion INTO op_encargo
      FROM captacion WHERE id_captacion = NEW.id_captacion;

    IF NEW.operacion IS NULL THEN
        NEW.operacion := op_encargo;
    ELSIF NEW.operacion <> op_encargo THEN
        RAISE EXCEPTION
            'El hito declara operacion % pero su encargo % es de operacion %',
            NEW.operacion, NEW.id_captacion, op_encargo
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_precio_operacion_encargo
    BEFORE INSERT OR UPDATE ON precio_propiedad
    FOR EACH ROW
    EXECUTE FUNCTION exigir_operacion_del_encargo();
