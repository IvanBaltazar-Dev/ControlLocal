-- =====================================================================
-- V54 - El septimo tipo de propiedad: ALMACEN.
--
-- QUE PROBLEMA CIERRA
-- V48 declaro el catalogo de atributos para SIETE tipos de propiedad
-- (`ck_catalogo_atributo_tipo`: L O D C T A X) y le colgo atributos propios
-- al almacen -- `altura_libre`, `carga_electrica_kw`, `frente`,
-- `area_terreno`. Pero `ck_propiedad_tipo` seguia admitiendo SEIS (sin `A`):
-- el catalogo sabia preguntar por un almacen y la tabla no dejaba registrarlo.
--
-- No lo vio ninguna prueba porque nadie habia intentado todavia dar de alta
-- una propiedad que no fuera un local o una oficina: el modelo universal es
-- el primero que lo hace.
--
-- POR QUE `A` Y NO OTRA LETRA
-- Es la que ya eligio V48 y la que usa `catalogo_atributo_tipo` en sus 4
-- filas de almacen. Cambiarla ahora obligaria a reescribir esas filas y a
-- tocar `tipo_documento_requerido.tipo_propiedad`, que hereda el mismo
-- vocabulario. La letra no se discute: se completa donde falta.
--
-- OJO CON LA COLISION APARENTE
-- `A` significa ALMACEN en `tipo_inmueble` y ALQUILADO en
-- `disponibilidad_comercial`. Son columnas distintas con vocabularios
-- distintos y ningun CHECK las mezcla; se anota aqui porque quien lea un
-- volcado de `propiedad` va a ver las dos.
-- =====================================================================

ALTER TABLE propiedad DROP CONSTRAINT IF EXISTS ck_propiedad_tipo;

ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_tipo
    CHECK (tipo_inmueble IN ('L', 'O', 'D', 'C', 'T', 'A', 'X'));

COMMENT ON COLUMN propiedad.tipo_inmueble IS
    'L local, O oficina, D departamento, C casa, T terreno, A almacen, X otro. '
    'Mismo vocabulario que catalogo_atributo_tipo.tipo_propiedad (V48) y que '
    'tipo_documento_requerido.tipo_propiedad (V51).';

-- ---------------------------------------------------------------------
-- El vocabulario tiene que ser el MISMO en las tres tablas que lo usan.
-- Si alguien anade un tipo en una y se olvida de las otras, esto lo dice
-- aqui y no tres tandas despues, cuando el alta falle en produccion.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    def_propiedad text;
    def_catalogo  text;
    tipo          text;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO def_propiedad
      FROM pg_constraint WHERE conname = 'ck_propiedad_tipo';
    SELECT pg_get_constraintdef(oid) INTO def_catalogo
      FROM pg_constraint WHERE conname = 'ck_catalogo_atributo_tipo';

    FOREACH tipo IN ARRAY ARRAY['L', 'O', 'D', 'C', 'T', 'A', 'X'] LOOP
        IF position('''' || tipo || '''' in def_propiedad) = 0 THEN
            RAISE EXCEPTION 'V54: el tipo % no quedo admitido en propiedad', tipo;
        END IF;
        IF position('''' || tipo || '''' in def_catalogo) = 0 THEN
            RAISE EXCEPTION
                'V54: el tipo % existe en propiedad y no en catalogo_atributo_tipo', tipo;
        END IF;
    END LOOP;

    RAISE NOTICE 'V54: propiedad admite los siete tipos del catalogo';
END $$;
