-- CAT20.9: misma máquina humana; sin retirar capacidades ni guardas.
ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_capacidad;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_capacidad CHECK (capacidad IN (
 'confirmar_captura','registrar_origen_fisico_unidad','retirar_finalizar_demanda',
 'invitar_colaboracion_encargo','solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
 'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network',
 'abrir_oportunidad','rectificar_interaccion','registrar_no_continuidad_oportunidad','cancelar_visita',
 'presentar_oferta','registrar_contraoferta','registrar_aceptacion_oferta',
 'registrar_rechazo_oferta','registrar_retirada_oferta','decidir_captacion',
 'presentar_expediente_operacion','evaluar_expediente_operacion','informar_hito_contractual',
 'confirmar_hito_contractual','rectificar_hito_contractual','registrar_cobro','asignar_reparto_comision',
 'autorizar_borrador_adenda','reprogramar_gestion_cobro','concluir_gestion_cobro',
 'publicar_anuncio','actualizar_anuncio_visible','pausar_anuncio','reanudar_anuncio',
 'finalizar_difusion_anuncio','obtener_pieza_para_difusion'));

ALTER TABLE operacion_herramienta DROP CONSTRAINT ck_operacion_recurso_concreto;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_recurso_concreto CHECK (
 (capacidad='confirmar_captura' AND id_borrador IS NOT NULL)
 OR (capacidad IN (
 'registrar_origen_fisico_unidad','retirar_finalizar_demanda',
 'invitar_colaboracion_encargo','solicitar_colaboracion','decidir_colaboracion','revocar_colaboracion','declarar_canal_colaboracion',
 'solicitar_participacion_network','decidir_participacion_network','desactivar_participacion_network',
 'abrir_oportunidad','rectificar_interaccion','registrar_no_continuidad_oportunidad','cancelar_visita',
 'presentar_oferta','registrar_contraoferta','registrar_aceptacion_oferta',
 'registrar_rechazo_oferta','registrar_retirada_oferta','decidir_captacion',
 'presentar_expediente_operacion','evaluar_expediente_operacion','informar_hito_contractual',
 'confirmar_hito_contractual','rectificar_hito_contractual','registrar_cobro','asignar_reparto_comision',
 'autorizar_borrador_adenda','reprogramar_gestion_cobro','concluir_gestion_cobro',
 'publicar_anuncio','actualizar_anuncio_visible','pausar_anuncio','reanudar_anuncio',
 'finalizar_difusion_anuncio','obtener_pieza_para_difusion') AND id_borrador IS NULL));
