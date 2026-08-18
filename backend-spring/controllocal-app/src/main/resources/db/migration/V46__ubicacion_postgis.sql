-- =====================================================================
-- V46 - M0 del modelo universal: la ubicacion pasa a ser geografia.
--
-- QUE PROBLEMA CIERRA
-- `geo_lat` y `geo_long` son dos numeros sueltos. Con eso se puede pintar un
-- pin, pero no se puede preguntar lo unico que el negocio pregunta de verdad:
-- "que propiedades hay a menos de 2 km de aqui". Hacerlo con aritmetica sobre
-- dos numeric es un calculo a mano en cada consulta, no usa indice y da mal el
-- resultado cerca de los polos.
--
-- QUE SE GANA
--   - `ST_DWithin(ubicacion, punto, metros)` con indice GiST: la busqueda por
--     cercania deja de ser un escaneo completo.
--   - `ST_Distance` en METROS, sin convertir grados a nada.
--   - La puerta abierta a poligonos de zona (distrito, urbanizacion) sin
--     cambiar el modelo otra vez.
--
-- POR QUE `geography` Y NO `geometry`
-- `geography(Point,4326)` calcula sobre el elipsoide y devuelve metros. Con
-- `geometry` habria que proyectar a UTM para que una distancia signifique algo,
-- y Lima cae en dos husos. Para puntos de una ciudad, geography es correcto y
-- no obliga a elegir proyeccion.
--
-- NO SE BORRA NADA
-- `geo_lat` y `geo_long` SE CONSERVAN. El cable v2 las publica hoy y la entidad
-- `Propiedad` las mapea; quitarlas rompería `ddl-auto: validate` en el arranque.
-- Mientras existan las tres columnas, un trigger las mantiene en sincronia en
-- los dos sentidos, asi que ningun productor actual tiene que cambiar para que
-- la busqueda geografica funcione.
--
-- INFRAESTRUCTURA
-- Exige una imagen con PostGIS. `docker-compose.yml` pasa de `postgres:17-alpine`
-- a `postgis/postgis:17-3.5-alpine`, que esta construida sobre la anterior: el
-- volumen de datos se monta sin migracion (mismo major, mismo datadir).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE propiedad
    ADD COLUMN IF NOT EXISTS ubicacion geography(Point, 4326);

COMMENT ON COLUMN propiedad.ubicacion IS
    'Punto WGS84. Derivado de geo_lat/geo_long por trigger mientras las dos sigan en el cable.';

-- Poblar lo que ya existe. 4326 = WGS84, el sistema de un GPS y de un mapa web.
UPDATE propiedad
   SET ubicacion = ST_SetSRID(ST_MakePoint(geo_long::double precision,
                                           geo_lat::double precision), 4326)::geography
 WHERE geo_lat IS NOT NULL
   AND geo_long IS NOT NULL
   AND ubicacion IS NULL;

-- El indice que hace barata la pregunta "que hay cerca de aqui".
CREATE INDEX IF NOT EXISTS ix_propiedad_ubicacion
    ON propiedad USING GIST (ubicacion);

-- Y el mismo indice acotado por organizacion, que es como se consulta siempre:
-- ninguna busqueda cruza tenants.
--
-- `btree_gist` es imprescindible aqui: un GiST no sabe indexar un bigint por su
-- cuenta -- "data type bigint has no default operator class for access method
-- gist" -- y sin la extension este indice no se puede crear. Es contrib
-- estandar y viene en la imagen.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE INDEX IF NOT EXISTS ix_propiedad_org_ubicacion
    ON propiedad USING GIST (organizacion_id, ubicacion);

-- ---------------------------------------------------------------------
-- Sincronia en los dos sentidos.
--
-- Quien escribe hoy son los productores v2, que ponen geo_lat/geo_long. Quien
-- escribira manana es el modelo universal, que pondra `ubicacion`. El trigger
-- acepta las dos entradas y completa la que falte, asi que la migracion del
-- codigo puede ir modulo a modulo sin que la busqueda se rompa a medias.
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION sincronizar_ubicacion_propiedad()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- Caso 1: llegan las coordenadas sueltas -> se construye el punto.
    IF NEW.geo_lat IS NOT NULL AND NEW.geo_long IS NOT NULL
       AND (TG_OP = 'INSERT'
            OR NEW.geo_lat IS DISTINCT FROM OLD.geo_lat
            OR NEW.geo_long IS DISTINCT FROM OLD.geo_long) THEN
        NEW.ubicacion := ST_SetSRID(ST_MakePoint(NEW.geo_long::double precision,
                                                 NEW.geo_lat::double precision), 4326)::geography;

    -- Caso 2: llega el punto -> se descomponen las coordenadas, para que el
    -- cable congelado siga publicando lo mismo que publicaba.
    ELSIF NEW.ubicacion IS NOT NULL
          AND (TG_OP = 'INSERT' OR NEW.ubicacion IS DISTINCT FROM OLD.ubicacion) THEN
        NEW.geo_lat  := ROUND(ST_Y(NEW.ubicacion::geometry)::numeric, 7);
        NEW.geo_long := ROUND(ST_X(NEW.ubicacion::geometry)::numeric, 7);

    -- Caso 3: se borran las coordenadas -> se borra el punto.
    ELSIF TG_OP = 'UPDATE' AND NEW.geo_lat IS NULL AND NEW.geo_long IS NULL THEN
        NEW.ubicacion := NULL;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tg_propiedad_ubicacion ON propiedad;
CREATE TRIGGER tg_propiedad_ubicacion
    BEFORE INSERT OR UPDATE ON propiedad
    FOR EACH ROW
    EXECUTE FUNCTION sincronizar_ubicacion_propiedad();

-- ---------------------------------------------------------------------
-- Evidencia en el log de la migracion.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    con_punto  bigint;
    sin_punto  bigint;
BEGIN
    SELECT count(*) FILTER (WHERE ubicacion IS NOT NULL),
           count(*) FILTER (WHERE ubicacion IS NULL)
      INTO con_punto, sin_punto
      FROM propiedad;
    RAISE NOTICE 'V46: % propiedades con ubicacion, % sin coordenadas todavia', con_punto, sin_punto;
END $$;
