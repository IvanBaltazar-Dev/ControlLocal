-- =====================================================================
-- V89 - Una PUBLICACION siempre pertenece a un ENCARGO
-- =====================================================================
--
-- QUE ARREGLA. `V70` movio la publicacion de la propiedad al encargo y
-- dejo `publicacion.id_captacion` NULLABLE a proposito: en aquel momento
-- habia anuncios anteriores cuya propiedad tenia varios encargos
-- candidatos, y elegir uno habria sido inventar de cual era. Esa columna
-- nullable es hoy la unica puerta por la que puede entrar un anuncio del
-- que nadie puede decir quien responde por el: `PublicacionServiceImpl`
-- exige el encargo al crear, pero el ESQUEMA no, y un INSERT directo, un
-- backfill o un productor futuro pueden volver a abrirla.
--
-- LA DECISION (D-P0-11, CONTROL, 2026-09-01). Una publicacion siempre
-- pertenece a un encargo. Un anuncio no anuncia "una propiedad": anuncia
-- que esta propiedad se ofrece en ESTA operacion a ESTE precio. Sin
-- encargo no hay operacion, no hay importe con nombre, no hay agente
-- responsable y no hay autoridad que decida quien lo puede tocar --
-- exactamente los cuatro huecos que P0 vino a cerrar.
--
-- POR QUE AHORA. Es la unica de las columnas de autoridad del bloque que
-- seguia siendo nullable "por si acaso". `PublicacionServiceImpl` ya
-- rechaza en `exigirEncargoPropio` toda publicacion sin encargo resuelto
-- (P0-4), asi que el caso que la nulabilidad protegia YA NO SE PUEDE
-- OPERAR: una fila con `id_captacion IS NULL` no se puede editar, ni
-- cambiar de estado, ni publicar. Mantenerla nullable no conserva un
-- dato: conserva un agujero. Se cierra en el esquema, que es donde
-- ningun productor lo puede saltar.
--
-- MEDICION PREVIA (2026-09-02, antes de escribir una linea):
--
--   base                       publicacion   id_captacion IS NULL
--   controllocal_dev                    12                      0
--   controllocal_repositorios         1081                      0
--
-- El backfill demostrable de `V70` (sus dos pasadas: encargo unico de la
-- propiedad, y encargo unico ya referenciado por la propia publicacion)
-- dejo la tabla entera resuelta en las dos bases. No queda ninguna fila
-- ambigua que esta migracion tenga que decidir, y por eso puede exigir la
-- invariante sin tocar un solo dato.
--
-- ADITIVA. No toca ninguna migracion aplicada -- `V70` incluida -- ni
-- ninguna fila de `publicacion`. No rellena, no borra y no reinterpreta
-- nada: si apareciera una fila sin encargo, esta migracion ABORTA y deja
-- la base como estaba, porque a que encargo pertenece un anuncio no se
-- deduce, se sabe o no se sabe.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Primero se comprueba, y si no se cumple NO se sigue.
--
-- El `SET NOT NULL` de abajo ya fallaria por si solo, pero con el mensaje
-- de Postgres ("column contains null values"), que no dice CUANTAS filas
-- ni que hacer con ellas. Aqui se para antes y se dice el recuento, para
-- que quien la aplique sepa el tamano de lo que tiene delante.
--
-- Y se para a proposito: la alternativa -- rellenar el hueco con "el
-- encargo mas probable de esa propiedad" -- es inventar procedencia. Un
-- anuncio adjudicado al encargo equivocado falsea su importe publicado,
-- su operacion y su responsable a la vez. Lo desconocido se queda
-- FALTANTE y esta migracion no entra hasta que alguien lo resuelva con
-- conocimiento, no con estadistica.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    huerfanas BIGINT;
BEGIN
    SELECT count(*) INTO huerfanas
      FROM publicacion
     WHERE id_captacion IS NULL;

    IF huerfanas > 0 THEN
        RAISE EXCEPTION
            'V89 no se aplica: hay % publicacion(es) sin id_captacion. '
            'A que encargo pertenece un anuncio NO se deduce. Resuelvelas una '
            'a una (o retiralas) y vuelve a aplicar la migracion.', huerfanas;
    END IF;
END
$$;


-- ---------------------------------------------------------------------
-- 2. La invariante, en el esquema.
--
-- A partir de aqui el vinculo es estructural: ningun INSERT -- venga del
-- servicio, de una prueba, de un script de carga o de psql -- puede dejar
-- un anuncio sin dueno. La FK a `captacion` ya existe desde `V70`; lo que
-- faltaba era que fuera obligatoria.
-- ---------------------------------------------------------------------
ALTER TABLE publicacion
    ALTER COLUMN id_captacion SET NOT NULL;


COMMENT ON COLUMN publicacion.id_captacion IS
    'El ENCARGO que este anuncio publica. OBLIGATORIO desde V89 (D-P0-11): '
    'es lo que dice si el anuncio publica la venta o el alquiler, con que '
    'importe y bajo la autoridad de que agente. Era nullable en V70 por las '
    'publicaciones anteriores a esa migracion; el backfill demostrable de V70 '
    'las resolvio todas (medido 2026-09-02: 0 nulos en dev y en pruebas) y el '
    'hueco se cierra aqui. No se deduce de id_propiedad: una propiedad con '
    'venta y alquiler vivos a la vez tiene dos encargos candidatos.';
