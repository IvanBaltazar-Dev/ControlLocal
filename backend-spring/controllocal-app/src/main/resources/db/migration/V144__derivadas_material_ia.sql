-- CAT-E33/R14: vínculo por job al resultado/QC142 y al binario139/141 existentes.
-- No guarda bytes, costos, otro QC o saldo. Retención7d/slots/selección aprobada01Nov.
-- Metadata mínima inmutable; no obliga a conservar todos los binarios.
CREATE TABLE derivada_material_ia (
 organizacion_id bigint NOT NULL,
 id_operacion uuid NOT NULL,
 id_propiedad bigint NOT NULL,
 id_foto bigint NOT NULL,
 original_ref uuid NOT NULL,
 archivo_ref uuid NOT NULL UNIQUE,
 operacion_io uuid NOT NULL,
 numero_io integer NOT NULL,
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(organizacion_id,id_operacion),
 UNIQUE(organizacion_id,id_operacion,archivo_ref),
 UNIQUE(organizacion_id,operacion_io,numero_io),
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES material_ia_operacion(organizacion_id,id_operacion),
 FOREIGN KEY(organizacion_id,id_propiedad) REFERENCES propiedad(organizacion_id,id_propiedad),
 FOREIGN KEY(organizacion_id,id_foto,id_propiedad,original_ref)
  REFERENCES foto_propiedad(organizacion_id,id_foto,id_propiedad,archivo_ref),
 FOREIGN KEY(organizacion_id,operacion_io,numero_io)
  REFERENCES intento_salida_binaria(organizacion_id,operacion,numero)
);
CREATE FUNCTION verificar_derivada_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j material_ia_operacion%ROWTYPE; i intento_salida_binaria%ROWTYPE;
BEGIN
 SELECT * INTO STRICT j FROM material_ia_operacion
  WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion;
 SELECT * INTO STRICT i FROM intento_salida_binaria
  WHERE organizacion_id=NEW.organizacion_id AND operacion=NEW.operacion_io AND numero=NEW.numero_io;
 IF j.accion<>'MEJORAR_FOTO' OR j.estado NOT IN('CONTROLADA','FALLO_RESULTADO')
  OR j.medicion IS NULL OR j.control_automatico IS NULL
  OR j.control_automatico::jsonb->>'decision' IS DISTINCT FROM 'APROBADA'
  OR j.solicitud::jsonb#>>'{recurso,tipo}' IS DISTINCT FROM 'PROPIEDAD'
  OR j.solicitud::jsonb#>>'{recurso,referencia}' IS DISTINCT FROM NEW.id_propiedad::text
  OR j.solicitud::jsonb->>'fotoOriginal' IS DISTINCT FROM NEW.original_ref::text
  OR i.estado NOT IN('ESCRITO_VERIFICADO','VINCULADO') OR i.escritura_incierta
  OR i.huella IS DISTINCT FROM j.salida::jsonb->>'huellaFoto'
  OR i.content_type IS DISTINCT FROM j.salida::jsonb->>'contentType'
  OR i.bytes<=0
  OR i.fuente IS DISTINCT FROM 'MATERIAL-IA:'||NEW.organizacion_id::text||':'||NEW.id_operacion::text||':'||j.huella_snapshot||':'||i.huella
 THEN RAISE EXCEPTION 'La derivada exige su job, original, control y binario verificados' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER derivada_material_ia_verificar BEFORE INSERT ON derivada_material_ia
 FOR EACH ROW EXECUTE FUNCTION verificar_derivada_material_ia();
CREATE FUNCTION conservar_derivada_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'La derivada conserva vínculo y linaje; otra variante corresponde a otro job' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER derivada_material_ia_conservar BEFORE UPDATE OR DELETE ON derivada_material_ia
 FOR EACH ROW EXECUTE FUNCTION conservar_derivada_material_ia();
CREATE TRIGGER derivada_material_ia_conservar_truncate BEFORE TRUNCATE ON derivada_material_ia
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_derivada_material_ia();

-- Política aprobada01Nov: selección única por original, slots2 y temporales7d computables en B0.
CREATE TABLE seleccion_material_ia (
 organizacion_id bigint NOT NULL, original_ref uuid NOT NULL, id_propiedad bigint NOT NULL,id_foto bigint NOT NULL,
 revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0), revision_material bigint NOT NULL DEFAULT 0 CHECK(revision_material>=0),
 id_operacion uuid,archivo_ref uuid,actualizada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(organizacion_id,original_ref),
 FOREIGN KEY(organizacion_id,id_foto,id_propiedad,original_ref) REFERENCES foto_propiedad(organizacion_id,id_foto,id_propiedad,archivo_ref),
 FOREIGN KEY(organizacion_id,id_operacion,archivo_ref) REFERENCES derivada_material_ia(organizacion_id,id_operacion,archivo_ref),
 CHECK((id_operacion IS NULL)=(archivo_ref IS NULL)),CHECK(revision>0 OR id_operacion IS NULL)
);
CREATE TABLE slot_derivada_material_ia (
 organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,original_ref uuid NOT NULL,
 revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),
 estado varchar(30) NOT NULL CHECK(estado IN('RESERVADA','GENERADA','DISPONIBLE','RETIRADA_PENDIENTE','RETIRADA','NO_ALMACENADA')),
 guardada boolean NOT NULL DEFAULT false,namespace_hash varchar(64) NOT NULL CHECK(namespace_hash ~ '^[0-9a-f]{64}$'),
 evento_generacion bigint REFERENCES material_ia_evento(id_evento),generada_en timestamptz,
 procedencia_fecha varchar(30),temporal_desde timestamptz,vence_en timestamptz,revision_inicio bigint,
 ultimo_motivo varchar(60) NOT NULL,actualizada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(organizacion_id,id_operacion),UNIQUE(organizacion_id,id_operacion,original_ref),
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES material_ia_operacion(organizacion_id,id_operacion),
 FOREIGN KEY(organizacion_id,original_ref) REFERENCES seleccion_material_ia(organizacion_id,original_ref),
 CHECK((evento_generacion IS NULL)=(generada_en IS NULL)),
 CHECK((generada_en IS NULL AND procedencia_fecha IS NULL) OR (generada_en IS NOT NULL AND procedencia_fecha='OBSERVADA_CORE')),
 CHECK((temporal_desde IS NULL)=(vence_en IS NULL)),CHECK(vence_en IS NULL OR vence_en=temporal_desde+interval '7 days')
);
CREATE TABLE seleccion_material_ia_evento (
 id_evento bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,organizacion_id bigint NOT NULL,original_ref uuid NOT NULL,
 revision bigint NOT NULL CHECK(revision>0),id_operacion uuid,archivo_ref uuid,id_operacion_anterior uuid,archivo_anterior_ref uuid,
 id_persona bigint NOT NULL,id_rol bigint NOT NULL,rol_efectivo varchar(30) NOT NULL,
 sesion_contexto varchar(64) NOT NULL,revision_contexto bigint NOT NULL,registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(organizacion_id,original_ref,revision),
 FOREIGN KEY(organizacion_id,original_ref) REFERENCES seleccion_material_ia(organizacion_id,original_ref),
 FOREIGN KEY(organizacion_id,id_operacion,archivo_ref) REFERENCES derivada_material_ia(organizacion_id,id_operacion,archivo_ref),
 FOREIGN KEY(organizacion_id,id_persona) REFERENCES persona(organizacion_id,id_persona),
 FOREIGN KEY(organizacion_id,id_rol) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CHECK((id_operacion IS NULL)=(archivo_ref IS NULL))
);
ALTER TABLE slot_derivada_material_ia ADD CONSTRAINT fk_slot_episodio_seleccion
 FOREIGN KEY(organizacion_id,original_ref,revision_inicio) REFERENCES seleccion_material_ia_evento(organizacion_id,original_ref,revision);
CREATE TABLE retencion_material_ia_evento (
 id_evento bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,
 revision bigint NOT NULL CHECK(revision>=0),causa varchar(80) NOT NULL,
 datos text NOT NULL CHECK(jsonb_typeof(datos::jsonb)='object'),huella varchar(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES slot_derivada_material_ia(organizacion_id,id_operacion)
);
CREATE TABLE gestion_material_ia (
 organizacion_id bigint NOT NULL,clave_huella varchar(64) NOT NULL CHECK(clave_huella ~ '^[0-9a-f]{64}$'),
 huella_peticion varchar(64) NOT NULL CHECK(huella_peticion ~ '^[0-9a-f]{64}$'),
 respuesta text NOT NULL CHECK(jsonb_typeof(respuesta::jsonb)='object'),
 id_persona bigint NOT NULL,id_rol bigint NOT NULL,registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(organizacion_id,clave_huella),FOREIGN KEY(organizacion_id,id_persona) REFERENCES persona(organizacion_id,id_persona),
 FOREIGN KEY(organizacion_id,id_rol) REFERENCES persona_rol(organizacion_id,id_persona_rol)
);
-- Traza mínima de causa: sin destinatarios, flags, prompts o costos. El contenido visible se autoriza al entregar.
ALTER TABLE retencion_material_ia_evento ADD CONSTRAINT uq_evento_retencion_144 UNIQUE(organizacion_id,id_operacion,id_evento);
CREATE TABLE causa_caducidad_material_ia (
 organizacion_id bigint NOT NULL,original_ref uuid NOT NULL,huella varchar(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(organizacion_id,original_ref,huella),
 FOREIGN KEY(organizacion_id,original_ref) REFERENCES seleccion_material_ia(organizacion_id,original_ref)
);
CREATE TABLE miembro_causa_caducidad_material_ia (
 organizacion_id bigint NOT NULL,original_ref uuid NOT NULL,huella varchar(64) NOT NULL,id_operacion uuid NOT NULL,
 revision bigint NOT NULL CHECK(revision>0),evento_retencion bigint NOT NULL,temporal_desde timestamptz NOT NULL,vence_en timestamptz NOT NULL,
 PRIMARY KEY(organizacion_id,original_ref,huella,id_operacion),
 FOREIGN KEY(organizacion_id,original_ref,huella) REFERENCES causa_caducidad_material_ia(organizacion_id,original_ref,huella),
 FOREIGN KEY(organizacion_id,id_operacion,original_ref) REFERENCES slot_derivada_material_ia(organizacion_id,id_operacion,original_ref),
 FOREIGN KEY(organizacion_id,id_operacion,evento_retencion) REFERENCES retencion_material_ia_evento(organizacion_id,id_operacion,id_evento),
 CHECK(vence_en=temporal_desde+interval '7 days')
);
CREATE FUNCTION verificar_miembro_causa_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE d jsonb; r bigint; desde timestamptz; vence timestamptz;
BEGIN
 SELECT datos::jsonb INTO d FROM retencion_material_ia_evento
  WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion AND id_evento=NEW.evento_retencion;
 IF d IS NULL THEN RAISE EXCEPTION 'La causa requiere un evento de retención del mismo job.' USING ERRCODE='23514'; END IF;
 BEGIN
  r:=(d->>'revision')::bigint;desde:=(d->>'temporalDesde')::timestamptz;vence:=(d->>'venceEn')::timestamptz;
 EXCEPTION WHEN invalid_text_representation OR invalid_datetime_format OR datetime_field_overflow THEN
  RAISE EXCEPTION 'La causa requiere fechas y revisión de retención íntegras.' USING ERRCODE='23514';
 END;
 IF r IS DISTINCT FROM NEW.revision OR desde IS DISTINCT FROM NEW.temporal_desde OR vence IS DISTINCT FROM NEW.vence_en THEN
  RAISE EXCEPTION 'La causa no conserva el episodio exacto de retención.' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER miembro_causa_material_ia_verificar BEFORE INSERT ON miembro_causa_caducidad_material_ia
 FOR EACH ROW EXECUTE FUNCTION verificar_miembro_causa_material_ia();
CREATE TABLE proteccion_derivada_material_ia (
 organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,archivo_ref uuid NOT NULL,
 pieza_ref uuid NOT NULL,version_pieza bigint NOT NULL CHECK(version_pieza>0),
 revision_seleccion bigint NOT NULL CHECK(revision_seleccion>0),registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(organizacion_id,id_operacion,pieza_ref),
 FOREIGN KEY(organizacion_id,id_operacion,archivo_ref) REFERENCES derivada_material_ia(organizacion_id,id_operacion,archivo_ref),
 FOREIGN KEY(pieza_ref) REFERENCES pieza_comercial(referencia)
);
CREATE TABLE retirada_derivada_material_ia (
 id_retiro uuid PRIMARY KEY,organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,archivo_ref uuid NOT NULL,
 revision bigint NOT NULL CHECK(revision>0),causa varchar(30) NOT NULL CHECK(causa IN('DESCARTE_EXPLICITO','CADUCIDAD','RETIRADA_ALTERNATIVA')),
 estado varchar(30) NOT NULL CHECK(estado IN('INTENCION','AUSENCIA_COMPROBADA','LIBERADA')),
 identidad_objeto text NOT NULL,bytes bigint NOT NULL CHECK(bytes>0),huella varchar(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'),
 referencia_comprobacion text,comprobada_en timestamptz,creada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(organizacion_id,id_operacion),
 FOREIGN KEY(organizacion_id,id_operacion,archivo_ref) REFERENCES derivada_material_ia(organizacion_id,id_operacion,archivo_ref),
 CHECK((estado='INTENCION' AND comprobada_en IS NULL AND referencia_comprobacion IS NULL)
  OR (estado<>'INTENCION' AND comprobada_en IS NOT NULL AND referencia_comprobacion IS NOT NULL))
);
CREATE FUNCTION verificar_slot_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j material_ia_operacion%ROWTYPE;e material_ia_evento%ROWTYPE;
BEGIN
 SELECT * INTO STRICT j FROM material_ia_operacion WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion;
 IF j.accion<>'MEJORAR_FOTO' OR j.solicitud::jsonb->>'fotoOriginal' IS DISTINCT FROM NEW.original_ref::text
  THEN RAISE EXCEPTION 'El slot exige el job y original exactos' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.organizacion_id,NEW.id_operacion,NEW.original_ref,NEW.namespace_hash) IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.id_operacion,OLD.original_ref,OLD.namespace_hash)
   OR NEW.revision<>OLD.revision+1
   OR (OLD.generada_en IS NOT NULL AND ROW(NEW.generada_en,NEW.evento_generacion,NEW.procedencia_fecha) IS DISTINCT FROM ROW(OLD.generada_en,OLD.evento_generacion,OLD.procedencia_fecha))
   OR (OLD.temporal_desde IS NOT NULL AND NEW.temporal_desde IS DISTINCT FROM OLD.temporal_desde AND
       (NEW.ultimo_motivo<>'CAMBIO_SELECCION' OR NOT EXISTS(SELECT 1 FROM seleccion_material_ia_evento c
        WHERE c.organizacion_id=NEW.organizacion_id AND c.original_ref=NEW.original_ref AND c.revision=NEW.revision_inicio
         AND c.id_operacion_anterior=NEW.id_operacion AND c.id_operacion IS DISTINCT FROM NEW.id_operacion AND c.registrada_en=NEW.temporal_desde)))
   OR OLD.estado IN('RETIRADA','NO_ALMACENADA')
   OR NOT ((OLD.estado='RESERVADA' AND NEW.estado IN('GENERADA','NO_ALMACENADA'))
        OR (OLD.estado='GENERADA' AND NEW.estado IN('DISPONIBLE','NO_ALMACENADA'))
        OR (OLD.estado='DISPONIBLE' AND NEW.estado IN('DISPONIBLE','RETIRADA_PENDIENTE'))
        OR (OLD.estado='RETIRADA_PENDIENTE' AND NEW.estado='RETIRADA'))
   OR (OLD.estado='RETIRADA_PENDIENTE' AND NEW.estado NOT IN('RETIRADA_PENDIENTE','RETIRADA'))
   THEN RAISE EXCEPTION 'El slot conserva identidad, fecha observada, revisión y fence' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.generada_en IS NOT NULL THEN
  SELECT * INTO STRICT e FROM material_ia_evento WHERE id_evento=NEW.evento_generacion;
  IF e.organizacion_id<>NEW.organizacion_id OR e.id_operacion<>NEW.id_operacion OR e.estado<>'MEDIDA' OR e.registrada_en<>NEW.generada_en
   OR e.id_evento<>(SELECT id_evento FROM material_ia_evento WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion AND estado='MEDIDA' ORDER BY id_evento LIMIT 1)
   THEN RAISE EXCEPTION 'La generación exige su primera observación Core MEDIDA' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER slot_material_ia_verificar BEFORE INSERT OR UPDATE ON slot_derivada_material_ia FOR EACH ROW EXECUTE FUNCTION verificar_slot_material_ia();
CREATE FUNCTION verificar_proteccion_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p pieza_comercial%ROWTYPE;s slot_derivada_material_ia%ROWTYPE;
BEGIN
 SELECT * INTO STRICT p FROM pieza_comercial WHERE referencia=NEW.pieza_ref;
 SELECT * INTO STRICT s FROM slot_derivada_material_ia WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion;
 IF p.organizacion_id<>NEW.organizacion_id OR p.version_contenido<>NEW.version_pieza OR p.archivo_difusion_ref IS NULL OR p.autorizada_en IS NULL
  OR s.estado<>'DISPONIBLE' OR NOT EXISTS(SELECT 1 FROM seleccion_material_ia o WHERE o.organizacion_id=NEW.organizacion_id AND o.original_ref=s.original_ref
   AND o.id_operacion=NEW.id_operacion AND o.archivo_ref=NEW.archivo_ref AND o.revision=NEW.revision_seleccion)
  THEN RAISE EXCEPTION 'La protección exige emisión autorizada real y selección exacta' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER proteccion_material_ia_verificar BEFORE INSERT ON proteccion_derivada_material_ia FOR EACH ROW EXECUTE FUNCTION verificar_proteccion_material_ia();
CREATE FUNCTION verificar_temporales_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE n integer;o uuid;
BEGIN
 o=NEW.original_ref;
 SELECT count(*) INTO n FROM slot_derivada_material_ia s WHERE s.organizacion_id=NEW.organizacion_id AND s.original_ref=o
  AND s.estado NOT IN('RETIRADA','NO_ALMACENADA') AND NOT s.guardada
  AND NOT EXISTS(SELECT 1 FROM seleccion_material_ia a WHERE a.organizacion_id=s.organizacion_id AND a.original_ref=s.original_ref AND a.id_operacion=s.id_operacion)
  AND NOT EXISTS(SELECT 1 FROM proteccion_derivada_material_ia p WHERE p.organizacion_id=s.organizacion_id AND p.id_operacion=s.id_operacion);
 IF n>2 THEN RAISE EXCEPTION 'Gestiona qué versión guardar o descartar antes de superar dos temporales' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END;
$$;
CREATE TRIGGER slot_material_ia_maximo AFTER INSERT OR UPDATE ON slot_derivada_material_ia FOR EACH ROW EXECUTE FUNCTION verificar_temporales_material_ia();
CREATE TRIGGER seleccion_material_ia_maximo AFTER INSERT OR UPDATE ON seleccion_material_ia FOR EACH ROW EXECUTE FUNCTION verificar_temporales_material_ia();
CREATE FUNCTION verificar_seleccion_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.revision<>0 OR NEW.revision_material<>0 OR NEW.id_operacion IS NOT NULL THEN
   RAISE EXCEPTION 'El original inicial no fabrica una decisión humana' USING ERRCODE='23514';END IF;
 ELSE
  IF ROW(NEW.organizacion_id,NEW.original_ref,NEW.id_propiedad,NEW.id_foto) IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.original_ref,OLD.id_propiedad,OLD.id_foto)
   OR NEW.revision_material<>OLD.revision_material+1 OR
   (ROW(NEW.id_operacion,NEW.archivo_ref) IS DISTINCT FROM ROW(OLD.id_operacion,OLD.archivo_ref) AND NEW.revision<>OLD.revision+1) OR
   (ROW(NEW.id_operacion,NEW.archivo_ref) IS NOT DISTINCT FROM ROW(OLD.id_operacion,OLD.archivo_ref) AND NEW.revision<>OLD.revision)
   THEN RAISE EXCEPTION 'La selección conserva original y revisiones separadas' USING ERRCODE='23514';END IF;
 END IF;
 IF NEW.id_operacion IS NOT NULL AND NOT EXISTS(SELECT 1 FROM slot_derivada_material_ia s
  JOIN derivada_material_ia d ON d.organizacion_id=s.organizacion_id AND d.id_operacion=s.id_operacion
  JOIN material_ia_operacion j ON j.organizacion_id=s.organizacion_id AND j.id_operacion=s.id_operacion
  WHERE s.organizacion_id=NEW.organizacion_id AND s.original_ref=NEW.original_ref AND s.id_operacion=NEW.id_operacion
   AND d.archivo_ref=NEW.archivo_ref AND s.estado='DISPONIBLE' AND j.estado='DISPONIBLE'
   AND (s.guardada OR s.vence_en>clock_timestamp() OR EXISTS(SELECT 1 FROM proteccion_derivada_material_ia p WHERE p.organizacion_id=s.organizacion_id AND p.id_operacion=s.id_operacion)
        OR EXISTS(SELECT 1 FROM seleccion_material_ia o WHERE o.organizacion_id=s.organizacion_id AND o.id_operacion=s.id_operacion))
   AND j.control_automatico::jsonb->>'decision'='APROBADA')
 THEN RAISE EXCEPTION 'La selección exige artefacto disponible del mismo original y QC aprobado' USING ERRCODE='23514';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER seleccion_material_ia_verificar BEFORE INSERT OR UPDATE ON seleccion_material_ia FOR EACH ROW EXECUTE FUNCTION verificar_seleccion_material_ia();
CREATE FUNCTION verificar_retiro_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s slot_derivada_material_ia%ROWTYPE;
BEGIN
 SELECT * INTO STRICT s FROM slot_derivada_material_ia WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion;
 IF TG_OP='INSERT' THEN
  IF NEW.estado<>'INTENCION' OR NEW.revision<>s.revision+1 OR s.estado<>'DISPONIBLE'
   OR (s.guardada AND NEW.causa<>'RETIRADA_ALTERNATIVA') OR (NOT s.guardada AND NEW.causa='RETIRADA_ALTERNATIVA')
   OR EXISTS(SELECT 1 FROM seleccion_material_ia o WHERE o.organizacion_id=s.organizacion_id AND o.id_operacion=s.id_operacion)
   OR EXISTS(SELECT 1 FROM proteccion_derivada_material_ia p WHERE p.organizacion_id=s.organizacion_id AND p.id_operacion=s.id_operacion)
   OR (NEW.causa='CADUCIDAD' AND (s.vence_en IS NULL OR clock_timestamp()<s.vence_en))
   THEN RAISE EXCEPTION 'La retirada exige una versión prescindible y causa vigente' USING ERRCODE='23514';END IF;
 ELSE
  IF ROW(NEW.id_retiro,NEW.organizacion_id,NEW.id_operacion,NEW.archivo_ref,NEW.revision,NEW.causa,NEW.identidad_objeto,NEW.bytes,NEW.huella,NEW.creada_en)
    IS DISTINCT FROM ROW(OLD.id_retiro,OLD.organizacion_id,OLD.id_operacion,OLD.archivo_ref,OLD.revision,OLD.causa,OLD.identidad_objeto,OLD.bytes,OLD.huella,OLD.creada_en)
   OR (OLD.estado='LIBERADA') OR (OLD.estado='INTENCION' AND NEW.estado<>'AUSENCIA_COMPROBADA')
   OR (OLD.estado='AUSENCIA_COMPROBADA' AND (NEW.estado<>'LIBERADA' OR ROW(NEW.referencia_comprobacion,NEW.comprobada_en) IS DISTINCT FROM ROW(OLD.referencia_comprobacion,OLD.comprobada_en)))
   THEN RAISE EXCEPTION 'La retirada conserva intención, comprobación y estado irreversible' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER retiro_material_ia_verificar BEFORE INSERT OR UPDATE ON retirada_derivada_material_ia FOR EACH ROW EXECUTE FUNCTION verificar_retiro_material_ia();
CREATE FUNCTION conservar_metadata_material_ia_144() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'La derivada conserva vínculo y linaje; la evidencia mínima es de sólo anexión' USING ERRCODE='23514';END;
$$;
CREATE TRIGGER seleccion_material_ia_no_borrar BEFORE DELETE OR TRUNCATE ON seleccion_material_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER slot_material_ia_no_borrar BEFORE DELETE OR TRUNCATE ON slot_derivada_material_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER seleccion_evento_144_inmutable BEFORE UPDATE OR DELETE OR TRUNCATE ON seleccion_material_ia_evento FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER retencion_evento_144_inmutable BEFORE UPDATE OR DELETE OR TRUNCATE ON retencion_material_ia_evento FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER gestion_144_inmutable BEFORE UPDATE OR DELETE OR TRUNCATE ON gestion_material_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER proteccion_144_inmutable BEFORE UPDATE OR DELETE OR TRUNCATE ON proteccion_derivada_material_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER retirada_144_no_borrar BEFORE DELETE OR TRUNCATE ON retirada_derivada_material_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER causa_144_inmutable BEFORE UPDATE OR DELETE OR TRUNCATE ON causa_caducidad_material_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
CREATE TRIGGER miembro_causa_144_inmutable BEFORE UPDATE OR DELETE OR TRUNCATE ON miembro_causa_caducidad_material_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_metadata_material_ia_144();
