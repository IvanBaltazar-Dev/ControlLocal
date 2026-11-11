-- Especificación C4/C5 30/10 §§6/9: los nueve casos C4 usan la misma
-- revisión durable. Mantener las capacidades C2/C3 y la distinción de recurso;
-- no fabricar un borrador de captura para una interacción/visita/negociación.
ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_capacidad;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_capacidad CHECK (capacidad IN (
    'confirmar_captura','registrar_origen_fisico_unidad','retirar_finalizar_demanda',
    'solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
    'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network',
    'abrir_oportunidad','rectificar_interaccion','registrar_no_continuidad_oportunidad','cancelar_visita',
    'presentar_oferta','registrar_contraoferta','registrar_aceptacion_oferta',
    'registrar_rechazo_oferta','registrar_retirada_oferta'));

ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_recurso_concreto;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_recurso_concreto CHECK (
    (capacidad='confirmar_captura' AND id_borrador IS NOT NULL)
    OR (capacidad IN ('registrar_origen_fisico_unidad','retirar_finalizar_demanda',
        'solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
        'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network',
        'abrir_oportunidad','rectificar_interaccion','registrar_no_continuidad_oportunidad','cancelar_visita',
        'presentar_oferta','registrar_contraoferta','registrar_aceptacion_oferta',
        'registrar_rechazo_oferta','registrar_retirada_oferta') AND id_borrador IS NULL));
