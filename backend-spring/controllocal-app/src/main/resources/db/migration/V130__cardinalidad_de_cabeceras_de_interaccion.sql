-- Estadistica del GROUP BY real de cabeceras; no indice ni limite nuevo de captura.
-- Las expresiones COLLATE C corresponden a la agrupacion literal del lector.
CREATE STATISTICS public.interaccion_cabeceras_ndistinct (ndistinct)
    ON id_rol_agente,
       (autorizacion_json COLLATE "C"),
       (version_diccionario COLLATE "C"),
       id_oportunidad, id_prospeccion, id_captacion, id_rol_cliente
    FROM public.interaccion_comercial;

ANALYZE public.interaccion_comercial;
