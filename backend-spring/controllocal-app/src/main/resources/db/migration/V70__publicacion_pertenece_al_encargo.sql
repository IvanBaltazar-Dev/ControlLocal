-- V70 · La publicación pertenece al ENCARGO, no a la propiedad
--
-- Es la última pieza del modelo universal que seguía colgando del inmueble. Un
-- anuncio no anuncia «una propiedad»: anuncia que ESTA propiedad se ofrece en
-- ESTA operación a ESTE precio. Con venta y alquiler simultáneos, una
-- publicación atada a `id_propiedad` no puede decir cuál de las dos publica.
--
-- El código ya lo había anticipado. En `PublicacionServiceImpl` había escrito:
--
--     hito.setOperacion(OperacionInmobiliaria.ALQUILER);
--     // "la publicacion de una venta llegara con el encargo de venta y su
--     //  propio importe, y entonces esta linea dejara de ser una constante"
--
-- Esto es ese momento.
--
-- ------------------------------------------------------------------
-- 1. El vínculo
-- ------------------------------------------------------------------
-- NULLABLE, y no por comodidad: hay publicaciones antiguas cuya propiedad tiene
-- más de un encargo candidato, y elegir uno sería inventar de cuál era. Se
-- rellena lo que se puede demostrar y el resto se queda en NULL, que es lo que
-- de verdad se sabe. El servicio exige el encargo al CREAR; lo que ya existía
-- no se falsea hacia atrás.

ALTER TABLE publicacion
    ADD COLUMN id_captacion BIGINT REFERENCES captacion (id_captacion);

-- ------------------------------------------------------------------
-- 2. El importe deja de llamarse renta
-- ------------------------------------------------------------------
-- `renta_publicada` nombraba bien lo único que existía cuando se creó: locales
-- en alquiler. En una publicación de venta, «renta» es sencillamente falso, y
-- el nombre viajaba hasta la pantalla. Se renombra donde vive, para que no haya
-- que traducirlo en cada consumidor.

ALTER TABLE publicacion RENAME COLUMN renta_publicada TO importe_publicado;

-- ------------------------------------------------------------------
-- 3. Backfill demostrable
-- ------------------------------------------------------------------
-- Toda publicación existente es de alquiler: la columna se llamaba `renta` y el
-- único alta que las crea (`sincronizar`, desde el local heredado) siempre lo
-- fue. Así que el candidato es el encargo de ALQUILER de su propiedad.
--
-- La condición `count(*) = 1` es la que hace esto honesto: sólo se rellena
-- cuando hay UN único encargo de alquiler y por tanto no hay nada que elegir.

UPDATE publicacion p
   SET id_captacion = (
        SELECT c.id_captacion
          FROM captacion c
         WHERE c.id_propiedad = p.id_propiedad
           AND c.organizacion_id = p.organizacion_id
           AND c.motivo_operacion = 'A'
       )
 WHERE p.id_captacion IS NULL
   AND (SELECT count(*)
          FROM captacion c
         WHERE c.id_propiedad = p.id_propiedad
           AND c.organizacion_id = p.organizacion_id
           AND c.motivo_operacion = 'A') = 1;

-- ------------------------------------------------------------------
-- 4. Los hitos 'P' huérfanos
-- ------------------------------------------------------------------
-- `registrarRentaPublicada` escribía el hito de precio publicado SIN encargo,
-- porque la publicación no sabía de cuál era. Resultado: los hitos `P` existen
-- en la base y **no aparecen en ninguna ficha** — ni en el histórico del
-- encargo ni en la historia del inmueble, que filtran por `id_captacion`.
--
-- Se adopta el mismo criterio: se rellena sólo cuando el encargo de esa
-- operación es único, y se deja NULL cuando no.

UPDATE precio_propiedad pp
   SET id_captacion = (
        SELECT c.id_captacion
          FROM captacion c
         WHERE c.id_propiedad = pp.id_propiedad
           AND c.organizacion_id = pp.organizacion_id
           AND c.motivo_operacion = pp.operacion
       )
 WHERE pp.id_captacion IS NULL
   AND (SELECT count(*)
          FROM captacion c
         WHERE c.id_propiedad = pp.id_propiedad
           AND c.organizacion_id = pp.organizacion_id
           AND c.motivo_operacion = pp.operacion) = 1;

-- ------------------------------------------------------------------
-- 5. El acceso por encargo
-- ------------------------------------------------------------------
-- Es la lectura nueva: «las publicaciones de ESTE encargo, la más reciente
-- primero». Mismo orden que el índice por propiedad, que se conserva porque el
-- listado heredado y el estado de publicación siguen preguntando por inmueble.

CREATE INDEX ix_publicacion_encargo
    ON publicacion (id_captacion, fecha_publicacion DESC, id_publicacion DESC);
