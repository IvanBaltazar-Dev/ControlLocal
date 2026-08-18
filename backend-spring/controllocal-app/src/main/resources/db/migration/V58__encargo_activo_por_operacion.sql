-- =====================================================================
-- V58 - Un encargo ACTIVO por operacion, no uno por propiedad.
--
-- QUE PROBLEMA CIERRA, Y POR QUE NO LO CERRO V50
-- V50 anadio `uq_captacion_viva_por_operacion` sobre
-- `(id_propiedad, motivo_operacion) WHERE estado IN ('P','O','A')` y con eso
-- dio por admitida la venta y el alquiler vivos a la vez sobre la misma
-- propiedad. Pero dejo en pie el indice heredado de la v1:
--
--     uq_captacion_activa_por_local
--     UNIQUE (organizacion_id, id_propiedad) WHERE estado = 'A'
--
-- Ese indice no distingue operacion. Mientras los dos encargos estan
-- PENDIENTES todo funciona; en cuanto el broker aprueba el segundo, la base
-- lo rechaza. Es decir: el modelo universal se rompia justo en el paso que lo
-- hace util, y el gate de V50 no lo vio porque sus 47 comprobaciones se
-- hicieron con encargos en 'P'.
--
-- POR QUE EL INDICE VIEJO SOBRA
-- `uq_captacion_viva_por_operacion` es ESTRICTAMENTE MAS FUERTE para cada
-- operacion: no solo impide dos activas, impide dos vivas en cualquier
-- combinacion de P, O y A. Lo unico que el indice viejo anadia era la
-- prohibicion de cruzar operaciones, que es precisamente lo que el modelo
-- universal viene a permitir.
--
-- Dicho de otro modo: la invariante de la v1 era "una propiedad, un encargo"
-- porque una propiedad solo podia alquilarse. La invariante de v2 es "una
-- propiedad, un encargo POR OPERACION", y esa la impone V50.
--
-- La guarda de Java (`CaptacionServiceImpl`) se estrecha igual, para seguir
-- dando el mensaje antes de que hable PostgreSQL.
-- =====================================================================

DROP INDEX IF EXISTS uq_captacion_activa_por_local;

COMMENT ON INDEX uq_captacion_viva_por_operacion IS
    'La invariante vigente: un encargo vivo por (propiedad, operacion). Sustituye a '
    'uq_captacion_activa_por_local, que prohibia venta y alquiler activos a la vez (V58).';

-- ---------------------------------------------------------------------
-- Comprobacion: la invariante nueva sigue en pie y la vieja ya no bloquea.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    viejo_existe    boolean;
    nuevo_existe    boolean;
    dobles_activas  bigint;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_captacion_activa_por_local')
      INTO viejo_existe;
    SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_captacion_viva_por_operacion')
      INTO nuevo_existe;

    IF viejo_existe THEN
        RAISE EXCEPTION 'V58: uq_captacion_activa_por_local sigue ahi y bloquearia venta + alquiler';
    END IF;
    IF NOT nuevo_existe THEN
        RAISE EXCEPTION 'V58: falta uq_captacion_viva_por_operacion; sin el no queda ninguna invariante';
    END IF;

    -- Y que nadie haya colado ya dos vivas de la misma operacion aprovechando
    -- el hueco. Si las hubiera, el indice de V50 no existiria; se comprueba
    -- igualmente porque este es el sitio donde se mira.
    SELECT count(*) INTO dobles_activas FROM (
        SELECT id_propiedad, motivo_operacion
          FROM captacion
         WHERE estado IN ('P', 'O', 'A')
         GROUP BY id_propiedad, motivo_operacion
        HAVING count(*) > 1) d;

    IF dobles_activas > 0 THEN
        RAISE EXCEPTION 'V58: % propiedades con dos encargos vivos de la misma operacion', dobles_activas;
    END IF;

    RAISE NOTICE 'V58: la invariante pasa a ser un encargo vivo por (propiedad, operacion)';
END $$;
