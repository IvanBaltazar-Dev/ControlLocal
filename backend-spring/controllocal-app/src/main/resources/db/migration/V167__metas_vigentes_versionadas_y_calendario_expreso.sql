-- Catálogo20.10 aprobado10/11/2026. Reutiliza meta_comercial/meta_revision sin convertir C/P/S/F.
ALTER TABLE meta_comercial ALTER COLUMN kpi TYPE VARCHAR(64);
ALTER TABLE meta_revision ALTER COLUMN kpi TYPE VARCHAR(64);
ALTER TABLE meta_comercial ADD COLUMN definicion VARCHAR(64),ADD COLUMN operacion VARCHAR(16),
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0,ADD COLUMN calendario JSONB;
ALTER TABLE meta_revision ADD COLUMN definicion VARCHAR(64),ADD COLUMN operacion VARCHAR(16),
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0,ADD COLUMN version_meta BIGINT,
 ADD COLUMN version_resultado BIGINT,ADD COLUMN id_meta BIGINT,ADD COLUMN calendario JSONB,
 ADD COLUMN valor_aprobado INT,ADD COLUMN reemplazada_por BIGINT;
ALTER TABLE meta_comercial ADD CONSTRAINT uq_meta_org_id UNIQUE(organizacion_id,id_meta);
ALTER TABLE meta_revision ADD CONSTRAINT uq_meta_revision_org_id UNIQUE(organizacion_id,id_revision);
ALTER TABLE meta_revision ADD CONSTRAINT fk_revision_meta_contextual FOREIGN KEY(organizacion_id,id_meta)
 REFERENCES meta_comercial(organizacion_id,id_meta) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE meta_revision ADD CONSTRAINT fk_revision_reemplazo_contextual FOREIGN KEY(organizacion_id,reemplazada_por)
 REFERENCES meta_revision(organizacion_id,id_revision) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE meta_comercial DROP CONSTRAINT uq_meta_agente_kpi_mes;
CREATE UNIQUE INDEX uq_meta_clave_vigente ON meta_comercial(organizacion_id,id_rol_agente,kpi,coalesce(operacion,''),anio,mes);
DROP INDEX uq_revision_propuesta_viva;
CREATE UNIQUE INDEX uq_revision_propuesta_viva ON meta_revision(organizacion_id,id_rol_agente,kpi,coalesce(operacion,''),anio,mes)
 WHERE estado='E' AND reemplazada_por IS NULL;
CREATE FUNCTION meta_calendario_valido(c JSONB,a INT,m INT) RETURNS BOOLEAN LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE n INT;u INT;d TEXT;
BEGIN
 IF c IS NULL THEN RETURN TRUE;END IF;
 IF jsonb_typeof(c)<>'array' THEN RETURN FALSE;END IF;
 SELECT count(*),count(DISTINCT x) INTO n,u FROM jsonb_array_elements_text(c) x;
 IF n<>u THEN RETURN FALSE;END IF;
 FOR d IN SELECT jsonb_array_elements_text(c) LOOP
  IF d !~ '^\d{4}-\d{2}-\d{2}$' OR extract(year from d::date)<>a OR extract(month from d::date)<>m THEN RETURN FALSE;END IF;
 END LOOP;
 RETURN TRUE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END $$;
ALTER TABLE meta_comercial DROP CONSTRAINT ck_meta_kpi;
ALTER TABLE meta_revision DROP CONSTRAINT ck_revision_kpi;
ALTER TABLE meta_comercial ADD CONSTRAINT ck_meta_definicion CHECK (coalesce(
 (kpi IN('C','P','S','F') AND definicion IS NULL AND operacion IS NULL AND version=0 AND calendario IS NULL)
 OR(definicion='METAS-C7-20261110-1' AND version>0 AND
  ((kpi='PROSPECCION_EFECTIVA_SALIENTE' AND operacion='GENERAL') OR
   (kpi IN('ENCARGO_PRIMERA_HABILITACION','OPORTUNIDAD_APERTURA','CIERRE_CONTRACTUAL_FAVORABLE') AND operacion IN('VENTA','ALQUILER')))
  AND meta_calendario_valido(calendario,anio,mes)),FALSE));
ALTER TABLE meta_revision DROP CONSTRAINT ck_revision_estado;
ALTER TABLE meta_revision ADD CONSTRAINT ck_revision_estado CHECK(estado IN('A','E','R','O'));
ALTER TABLE meta_revision ADD CONSTRAINT ck_revision_definicion CHECK(coalesce(
 (kpi IN('C','P','S','F') AND definicion IS NULL AND operacion IS NULL AND version=0 AND version_meta IS NULL
  AND version_resultado IS NULL AND calendario IS NULL AND id_meta IS NULL AND valor_aprobado IS NULL AND reemplazada_por IS NULL AND estado IN('A','E','R'))
 OR(definicion='METAS-C7-20261110-1' AND version>0 AND version_meta>=0 AND
  ((kpi='PROSPECCION_EFECTIVA_SALIENTE' AND operacion='GENERAL') OR
   (kpi IN('ENCARGO_PRIMERA_HABILITACION','OPORTUNIDAD_APERTURA','CIERRE_CONTRACTUAL_FAVORABLE') AND operacion IN('VENTA','ALQUILER')))
  AND meta_calendario_valido(calendario,anio,mes)
  AND ((estado='A' AND id_meta IS NOT NULL AND version_resultado=version_meta+1 AND valor_aprobado>=0)
    OR(estado<>'A' AND version_resultado IS NULL AND valor_aprobado IS NULL))
  AND (reemplazada_por IS NULL OR(estado='E' AND reemplazada_por<>id_revision))),FALSE));
CREATE FUNCTION conservar_meta_versionada() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'La meta y su historia se conservan';END IF;
 IF OLD.definicion IS NULL THEN RAISE EXCEPTION 'La meta histórica no se convierte ni modifica';END IF;
 IF ROW(NEW.organizacion_id,NEW.id_meta,NEW.id_rol_agente,NEW.kpi,NEW.operacion,NEW.anio,NEW.mes,NEW.definicion,NEW.fecha_creacion)
  IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.id_meta,OLD.id_rol_agente,OLD.kpi,OLD.operacion,OLD.anio,OLD.mes,OLD.definicion,OLD.fecha_creacion)
  OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Identidad/versión de meta inválida';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_meta_conservar BEFORE UPDATE OR DELETE ON meta_comercial FOR EACH ROW EXECUTE FUNCTION conservar_meta_versionada();
CREATE FUNCTION conservar_revision_meta() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'La revisión de meta se conserva';END IF;
 IF OLD.definicion IS NULL OR OLD.estado<>'E' OR OLD.reemplazada_por IS NOT NULL THEN RAISE EXCEPTION 'La revisión anterior es inmutable';END IF;
 IF (to_jsonb(NEW)-ARRAY['version','estado','id_rol_decisor','fecha_decision','motivo_decision','valor_aprobado','version_resultado','id_meta','reemplazada_por'])
   IS DISTINCT FROM(to_jsonb(OLD)-ARRAY['version','estado','id_rol_decisor','fecha_decision','motivo_decision','valor_aprobado','version_resultado','id_meta','reemplazada_por'])
   OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'El contenido original de la propuesta es inmutable';END IF;
 IF NEW.reemplazada_por IS NOT NULL THEN
  IF NEW.estado<>'E' OR ROW(NEW.id_rol_decisor,NEW.fecha_decision,NEW.motivo_decision,NEW.valor_aprobado,NEW.version_resultado,NEW.id_meta)
   IS DISTINCT FROM ROW(OLD.id_rol_decisor,OLD.fecha_decision,OLD.motivo_decision,OLD.valor_aprobado,OLD.version_resultado,OLD.id_meta) THEN RAISE EXCEPTION 'Sustituir no es rechazar ni aprobar';END IF;
 ELSIF NEW.estado NOT IN('A','R','O') OR NEW.id_rol_decisor IS NULL OR NEW.fecha_decision IS NULL
   OR length(btrim(NEW.motivo_decision))<10 THEN RAISE EXCEPTION 'Falta decisión competente con motivo';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_revision_meta_conservar BEFORE UPDATE OR DELETE ON meta_revision FOR EACH ROW EXECUTE FUNCTION conservar_revision_meta();
CREATE FUNCTION verificar_meta_con_historia() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.definicion IS NOT NULL AND NOT EXISTS(SELECT 1 FROM meta_revision r WHERE r.organizacion_id=NEW.organizacion_id
   AND r.id_meta=NEW.id_meta AND r.id_rol_agente=NEW.id_rol_agente AND r.kpi=NEW.kpi AND r.operacion=NEW.operacion
   AND r.anio=NEW.anio AND r.mes=NEW.mes AND r.estado='A' AND r.version_resultado=NEW.version
   AND r.valor_aprobado=NEW.valor AND r.calendario IS NOT DISTINCT FROM NEW.calendario)
  THEN RAISE EXCEPTION 'La meta vigente necesita su revisión aplicada exacta';END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER tg_meta_historia_exacta AFTER INSERT OR UPDATE ON meta_comercial
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verificar_meta_con_historia();
CREATE FUNCTION no_truncar_metas() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Las metas y sus antecedentes se conservan';END $$;
CREATE TRIGGER tg_meta_no_truncate BEFORE TRUNCATE ON meta_comercial EXECUTE FUNCTION no_truncar_metas();
CREATE TRIGGER tg_revision_meta_no_truncate BEFORE TRUNCATE ON meta_revision EXECUTE FUNCTION no_truncar_metas();

-- RF075 añade sólo las dos actuaciones de metas a las listas cerradas, sin retirar guardas.
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
 'finalizar_difusion_anuncio','obtener_pieza_para_difusion','rectificar_comunicacion_reporte','establecer_meta','decidir_propuesta_meta'));

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
 'finalizar_difusion_anuncio','obtener_pieza_para_difusion','rectificar_comunicacion_reporte','establecer_meta','decidir_propuesta_meta') AND id_borrador IS NULL));

ALTER TABLE meta_comercial ADD CONSTRAINT fk_meta_profesional_org FOREIGN KEY(organizacion_id,id_rol_agente) REFERENCES detalle_agente(organizacion_id,id_persona_rol);
ALTER TABLE meta_revision ADD CONSTRAINT fk_revision_profesional_org FOREIGN KEY(organizacion_id,id_rol_agente) REFERENCES detalle_agente(organizacion_id,id_persona_rol);
CREATE UNIQUE INDEX uq_meta_revision_aplicada ON meta_revision(organizacion_id,id_meta,version_resultado) WHERE definicion IS NOT NULL AND estado='A';
ALTER TABLE meta_comercial ADD CONSTRAINT fk_meta_autor_org FOREIGN KEY(organizacion_id,id_rol_autor) REFERENCES persona_rol(organizacion_id,id_persona_rol);
ALTER TABLE meta_revision ADD CONSTRAINT fk_revision_autor_org FOREIGN KEY(organizacion_id,id_rol_autor) REFERENCES persona_rol(organizacion_id,id_persona_rol);
ALTER TABLE meta_revision ADD CONSTRAINT fk_revision_decisor_org FOREIGN KEY(organizacion_id,id_rol_decisor) REFERENCES persona_rol(organizacion_id,id_persona_rol);
CREATE FUNCTION verificar_vinculos_revision_meta() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE m meta_comercial;r meta_revision;hay_ciclo BOOLEAN;
BEGIN
 IF NEW.definicion IS NULL THEN RETURN NEW;END IF;
 IF NEW.id_meta IS NOT NULL THEN
  SELECT * INTO m FROM meta_comercial WHERE organizacion_id=NEW.organizacion_id AND id_meta=NEW.id_meta;
  IF NOT FOUND OR ROW(m.id_rol_agente,m.kpi,m.operacion,m.definicion,m.anio,m.mes)
    IS DISTINCT FROM ROW(NEW.id_rol_agente,NEW.kpi,NEW.operacion,NEW.definicion,NEW.anio,NEW.mes)
    OR(NEW.estado='A' AND NEW.version_resultado>m.version) THEN RAISE EXCEPTION 'La revisión no corresponde a la identidad/version de la meta';END IF;
 END IF;
 IF NEW.reemplazada_por IS NOT NULL THEN
  SELECT * INTO r FROM meta_revision WHERE organizacion_id=NEW.organizacion_id AND id_revision=NEW.reemplazada_por;
  IF NOT FOUND OR r.origen<>'P' OR ROW(r.id_rol_agente,r.kpi,r.operacion,r.definicion,r.anio,r.mes)
    IS DISTINCT FROM ROW(NEW.id_rol_agente,NEW.kpi,NEW.operacion,NEW.definicion,NEW.anio,NEW.mes) THEN RAISE EXCEPTION 'La sustitución debe pertenecer a la misma propuesta contextual';END IF;
  WITH RECURSIVE cadena(id) AS(SELECT NEW.reemplazada_por UNION SELECT x.reemplazada_por FROM meta_revision x JOIN cadena c ON c.id=x.id_revision WHERE x.organizacion_id=NEW.organizacion_id AND x.reemplazada_por IS NOT NULL)
   SELECT EXISTS(SELECT 1 FROM cadena WHERE id=NEW.id_revision) INTO hay_ciclo;
  IF hay_ciclo THEN RAISE EXCEPTION 'La sustitución no puede contener ciclos';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER tg_revision_meta_vinculos AFTER INSERT OR UPDATE ON meta_revision
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verificar_vinculos_revision_meta();
