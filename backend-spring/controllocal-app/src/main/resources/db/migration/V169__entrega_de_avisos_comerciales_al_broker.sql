-- CAT-R28/NB: misma campana; el outbox conserva el hecho y Alerta conserva la entrega/atención.
ALTER TABLE alerta ADD COLUMN id_rol_broker bigint,
 ADD COLUMN nb_evento bigint REFERENCES evento_dominio(id_evento),ADD COLUMN nb_codigo varchar(5);
ALTER TABLE alerta ALTER COLUMN id_rol_agente DROP NOT NULL;
ALTER TABLE alerta ADD CONSTRAINT fk_alerta_broker_contextual FOREIGN KEY(organizacion_id,id_rol_broker)
 REFERENCES detalle_broker(organizacion_id,id_persona_rol);
ALTER TABLE alerta ADD CONSTRAINT ck_alerta_destinatario CHECK(
 (nb_evento IS NULL AND nb_codigo IS NULL AND id_rol_agente IS NOT NULL AND id_rol_broker IS NULL)
 OR (nb_evento IS NOT NULL AND nb_codigo IS NOT NULL AND nb_codigo IN('NB-01','NB-02','NB-03','NB-04','NB-05','NB-06','NB-07','NB-08','NB-09','NB-10','NB-11','NB-12','NB-13','NB-14','NB-15')
     AND tipo='AVISO_BROKER' AND id_rol_agente IS NULL AND id_rol_broker IS NOT NULL AND huella_causa_c7 IS NULL AND marca_causa IS NULL));
ALTER TABLE alerta DROP CONSTRAINT ck_alerta_tipo;
ALTER TABLE alerta ADD CONSTRAINT ck_alerta_tipo CHECK(tipo IN('SIN_RESPUESTA','SIN_AVANCE','OFERTA_POR_VENCER','CONTRATO_POR_VENCER','VISITA_PROXIMA','CAPTACION_VENCIDA','SOLICITUD_REENVIADA','SOLICITUD_EVALUADA','SOLICITUD_DOCUMENTO','SOLICITUD_DOCUMENTO_REVISADO','CAPTACION_CREADA','CAPTACION_REVISADA','CAPTACION_CERRADA','OPORTUNIDAD_CERRADA','COMISION_ASIGNADA','COMISION_COBRADA','AVISO_BROKER'));
CREATE UNIQUE INDEX uq_alerta_nb_entrega ON alerta(organizacion_id,nb_evento,nb_codigo,id_rol_broker) WHERE nb_evento IS NOT NULL;
CREATE INDEX ix_outbox_nb ON evento_dominio(organizacion_id,id_evento DESC) WHERE carga_util::jsonb ? 'avisoBroker';
ALTER TABLE alerta DROP CONSTRAINT ck_alerta_entidad_tipo;
ALTER TABLE alerta ADD CONSTRAINT ck_alerta_entidad_tipo CHECK(entidad_tipo IN('PROSPECCION','CAPTACION','OPORTUNIDAD','INTERACCION','VISITA','SOLICITUD_ALQUILER','INMUEBLE','PUBLICACION','CONTRATO_ALQUILER','CLIENTE_INTERESADO','PROPIETARIO','REQUERIMIENTO','SOLICITUD_COLABORACION'));
CREATE FUNCTION conservar_entrega_nb() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' AND OLD.nb_evento IS NOT NULL THEN RAISE EXCEPTION 'El aviso conserva su historia';END IF;
 IF TG_OP='DELETE' THEN RETURN OLD;END IF;
 IF NEW.nb_evento IS NOT NULL AND NOT EXISTS(select 1 from evento_dominio e where e.id_evento=NEW.nb_evento and e.organizacion_id=NEW.organizacion_id
   and e.carga_util::jsonb#>>'{avisoBroker,codigo}'=NEW.nb_codigo
   and e.carga_util::jsonb#>>'{avisoBroker,referencia}'=NEW.entidad_id::text
   and (case e.carga_util::jsonb#>>'{avisoBroker,recurso}' when 'ENCARGO' then 'CAPTACION' when 'DEMANDA' then 'REQUERIMIENTO' when 'COLABORACION' then 'SOLICITUD_COLABORACION' end)=NEW.entidad_tipo)
   THEN RAISE EXCEPTION 'La entrega requiere el evento de su ámbito';END IF;
 IF TG_OP='UPDATE' AND OLD.nb_evento IS NOT NULL AND ROW(OLD.organizacion_id,OLD.nb_evento,OLD.nb_codigo,OLD.id_rol_broker,OLD.entidad_tipo,OLD.entidad_id,OLD.fecha_generacion)
   IS DISTINCT FROM ROW(NEW.organizacion_id,NEW.nb_evento,NEW.nb_codigo,NEW.id_rol_broker,NEW.entidad_tipo,NEW.entidad_id,NEW.fecha_generacion)
   THEN RAISE EXCEPTION 'La entrega no sustituye su hecho o destinatario';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER conservar_entrega_nb BEFORE INSERT OR UPDATE OR DELETE ON alerta FOR EACH ROW EXECUTE FUNCTION conservar_entrega_nb();
CREATE FUNCTION conservar_hecho_outbox_nb() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.carga_util::jsonb ? 'avisoBroker' OR NEW.carga_util::jsonb ? 'avisoBroker' THEN
  IF ROW(OLD.organizacion_id,OLD.tipo,OLD.entidad_tipo,OLD.entidad_id,OLD.ocurrido_en,OLD.id_persona_rol,OLD.carga_util)
    IS DISTINCT FROM ROW(NEW.organizacion_id,NEW.tipo,NEW.entidad_tipo,NEW.entidad_id,NEW.ocurrido_en,NEW.id_persona_rol,NEW.carga_util)
    THEN RAISE EXCEPTION 'El hecho del aviso es inmutable; no se reetiqueta historia';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER conservar_hecho_outbox_nb BEFORE UPDATE ON evento_dominio FOR EACH ROW EXECUTE FUNCTION conservar_hecho_outbox_nb();
