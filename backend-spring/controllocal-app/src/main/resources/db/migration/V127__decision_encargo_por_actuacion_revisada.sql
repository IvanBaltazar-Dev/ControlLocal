-- BROX RF015/016 y4.6.2, precisión humana30/10/2026:
-- decidir el Encargo comparte la máquina de revisión/confirmación existente.
-- Se conservan las19capacidades anteriores y su recurso concreto; no se
-- habilita una decisión por booleano, otro motor o un borrador ficticio.
ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_capacidad;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_capacidad CHECK (capacidad IN (
    'confirmar_captura','registrar_origen_fisico_unidad','retirar_finalizar_demanda',
    'solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
    'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network',
    'abrir_oportunidad','rectificar_interaccion','registrar_no_continuidad_oportunidad','cancelar_visita',
    'presentar_oferta','registrar_contraoferta','registrar_aceptacion_oferta',
    'registrar_rechazo_oferta','registrar_retirada_oferta','decidir_captacion'));

ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_recurso_concreto;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_recurso_concreto CHECK (
    (capacidad='confirmar_captura' AND id_borrador IS NOT NULL)
    OR (capacidad IN ('registrar_origen_fisico_unidad','retirar_finalizar_demanda',
        'solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
        'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network',
        'abrir_oportunidad','rectificar_interaccion','registrar_no_continuidad_oportunidad','cancelar_visita',
        'presentar_oferta','registrar_contraoferta','registrar_aceptacion_oferta',
        'registrar_rechazo_oferta','registrar_retirada_oferta','decidir_captacion') AND id_borrador IS NULL));
