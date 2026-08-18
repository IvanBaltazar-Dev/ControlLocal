-- =====================================================================
-- V50 - M4 del modelo universal: la operacion del encargo deja de ser implicita.
--
-- QUE PROBLEMA CIERRA
-- `captacion.motivo_operacion` existe, esta validada contra {A,V} y hasta tiene
-- un CHECK. Pero tiene `DEFAULT 'A'`: un encargo que no declara operacion se
-- convierte en alquiler en silencio. Mientras el sistema solo alquilaba eso era
-- comodo; ahora es un error que no se ve.
--
-- Y falta la invariante que sostiene todo el modelo:
--
--     una propiedad puede tener DOS encargos vivos, uno de venta y uno de
--     alquiler, pero NO dos de la misma operacion.
--
-- Sin ella, "en venta y alquiler a la vez" y "dos encargos duplicados por
-- error" son indistinguibles.
--
-- QUE NO HACE ESTA MIGRACION
-- No mueve `propiedad.precio_referencial` a la condicion economica. Se comprobo
-- antes de escribirla: las 13 captaciones existentes YA tienen condicion
-- economica (`id_condicion_economica IS NOT NULL` en las 13), asi que no hay
-- nada que rescatar. La columna de la propiedad se conserva porque la entidad
-- la mapea y el cable la publica; su retirada es un cambio de codigo, no de
-- esquema, y va cuando el modulo de propiedades migre.
-- =====================================================================

-- Sin default: quien abre un encargo dice que operacion es.
ALTER TABLE captacion
    ALTER COLUMN motivo_operacion DROP DEFAULT;

COMMENT ON COLUMN captacion.motivo_operacion IS
    'A alquiler, V venta. La operacion vive AQUI, no en la propiedad. D-E4-1.';

-- ---------------------------------------------------------------------
-- Un encargo vivo por operacion y propiedad.
--
-- Vivos son P (pendiente), O (observada) y A (activa). R rechazada, C cerrada y
-- V vencida son finales, y por eso una propiedad puede acumular varios encargos
-- cerrados de la misma operacion a lo largo del tiempo: eso es su historia.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uq_captacion_viva_por_operacion
    ON captacion (id_propiedad, motivo_operacion)
    WHERE estado IN ('P', 'O', 'A');

COMMENT ON INDEX uq_captacion_viva_por_operacion IS
    'Dos encargos vivos de la MISMA operacion sobre una propiedad son un error; de operaciones distintas, el caso normal.';

-- ---------------------------------------------------------------------
-- La condicion economica habla de la misma operacion que su encargo.
--
-- Las dos columnas ya existen (V15) y hasta ahora nadie las ataba. Con una sola
-- operacion posible daba igual; con dos, un encargo de venta con una condicion
-- de alquiler colgada es un dato que miente.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_operacion_coherente_encargo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    op_condicion varchar(1);
BEGIN
    IF NEW.id_condicion_economica IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT tipo_operacion INTO op_condicion
      FROM condicion_economica_captacion
     WHERE id_condicion_economica = NEW.id_condicion_economica;

    IF op_condicion IS NOT NULL AND op_condicion <> NEW.motivo_operacion THEN
        RAISE EXCEPTION
            'El encargo es de operacion % y su condicion economica de operacion %',
            NEW.motivo_operacion, op_condicion
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_captacion_operacion_coherente
    BEFORE INSERT OR UPDATE ON captacion
    FOR EACH ROW
    EXECUTE FUNCTION exigir_operacion_coherente_encargo();

-- ---------------------------------------------------------------------
-- Comprobacion de que la base actual cumple ya las dos reglas nuevas.
-- Si no las cumpliera, la migracion tiene que parar aqui y no en produccion.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    duplicados   bigint;
    incoherentes bigint;
BEGIN
    SELECT count(*) INTO duplicados FROM (
        SELECT id_propiedad, motivo_operacion
          FROM captacion
         WHERE estado IN ('P', 'O', 'A')
         GROUP BY id_propiedad, motivo_operacion
        HAVING count(*) > 1) d;

    SELECT count(*) INTO incoherentes
      FROM captacion c
      JOIN condicion_economica_captacion ce
        ON ce.id_condicion_economica = c.id_condicion_economica
     WHERE ce.tipo_operacion <> c.motivo_operacion;

    IF duplicados > 0 THEN
        RAISE EXCEPTION 'V50: % propiedades con dos encargos vivos de la misma operacion', duplicados;
    END IF;
    IF incoherentes > 0 THEN
        RAISE EXCEPTION 'V50: % encargos con condicion economica de otra operacion', incoherentes;
    END IF;

    RAISE NOTICE 'V50: % encargos, todos con operacion explicita y coherente',
        (SELECT count(*) FROM captacion);
END $$;
