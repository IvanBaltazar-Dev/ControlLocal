-- Misma máquina durable UC-K06/07; casos C3 tipados, sin otra puerta de aprobación.
-- No altera migraciones aplicadas ni los estados/proof/recibos del contrato anterior.
ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_capacidad;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_capacidad CHECK (capacidad IN (
    'confirmar_captura','registrar_origen_fisico_unidad','retirar_finalizar_demanda',
    'solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
    'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network'));

ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_recurso_concreto;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_recurso_concreto CHECK (
    (capacidad='confirmar_captura' AND id_borrador IS NOT NULL)
    OR (capacidad IN ('registrar_origen_fisico_unidad','retirar_finalizar_demanda',
        'solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
        'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network')
        AND id_borrador IS NULL));
