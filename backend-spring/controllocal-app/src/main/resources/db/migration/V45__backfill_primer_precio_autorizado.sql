-- V45 — rescate del PRIMER precio autorizado ('U') de la base ya existente.
--
-- QUE PROBLEMA CIERRA
-- Hasta E0.1 el alta de una propiedad no dejaba ningun hito de precio. El unico
-- productor automatico era la edicion (`LocalComercialServiceImpl.actualizar`),
-- y esa graba el hito con el precio NUEVO, nunca con el anterior. Resultado: la
-- primera edicion de un local borraba para siempre su precio de SALIDA — que es
-- exactamente el numero contra el que se mide cuanto cedio el propietario hasta
-- el cierre, y el que ninguna otra tabla conserva.
--
-- POR QUE ESTE BACKFILL ES UNA DEDUCCION Y NO UNA ESTIMACION
-- `actualizar` escribe un hito 'U' SIEMPRE que cambia el precio o la moneda.
-- Por lo tanto:
--
--     propiedad con CERO hitos  =>  su precio nunca se edito
--                               =>  precio_referencial vigente ES el de salida
--
-- No se esta adivinando un valor historico: se esta rescatando uno que sigue
-- intacto porque nadie lo ha pisado todavia. Es una ventana que se cierra sola
-- —cada edicion futura elimina una fila recuperable—, y por eso el rescate viaja
-- en la misma tanda que el fix hacia adelante.
--
-- IDEMPOTENCIA
-- El NOT EXISTS deja cualquier reejecucion en 0 filas. Una propiedad que ya
-- tenga un hito (del productor de edicion, del cierre de contrato o del registro
-- manual) no se toca: su historia ya empezo y no nos toca reescribirla.
--
-- MONEDA
-- `moneda_referencial` es anulable en `propiedad` pero NOT NULL en el historico.
-- Las filas sin moneda caen a PEN, que es la moneda de operacion del sistema
-- (PrecioPropiedad.MONEDA_PEN).

DO $$
DECLARE
    candidatos  bigint;
    insertadas  bigint;
    ya_tenian   bigint;
BEGIN
    SELECT count(*) INTO candidatos
      FROM propiedad p
     WHERE p.precio_referencial IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM precio_propiedad pp
                        WHERE pp.id_propiedad = p.id_propiedad);

    SELECT count(*) INTO ya_tenian
      FROM propiedad p
     WHERE EXISTS (SELECT 1 FROM precio_propiedad pp
                    WHERE pp.id_propiedad = p.id_propiedad);

    INSERT INTO precio_propiedad (organizacion_id, id_propiedad, hito, moneda, monto, fecha)
    SELECT p.organizacion_id,
           p.id_propiedad,
           'U',
           COALESCE(p.moneda_referencial, 'PEN'),
           p.precio_referencial,
           COALESCE(p.fecha_registro::date, CURRENT_DATE)
      FROM propiedad p
     WHERE p.precio_referencial IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM precio_propiedad pp
                        WHERE pp.id_propiedad = p.id_propiedad);

    GET DIAGNOSTICS insertadas = ROW_COUNT;

    -- Conciliacion en el propio log de Flyway: candidatos y filas insertadas
    -- deben coincidir. Si no coinciden, algo escribio hitos entre el conteo y
    -- el INSERT y hay que revisarlo antes de dar el gate por cerrado.
    RAISE NOTICE 'V45 backfill primer U -> candidatos=% insertadas=% ya_tenian_historico=%',
                 candidatos, insertadas, ya_tenian;

    IF candidatos <> insertadas THEN
        RAISE EXCEPTION 'V45: descuadre de conciliacion (candidatos=% insertadas=%)',
                        candidatos, insertadas;
    END IF;
END $$;
