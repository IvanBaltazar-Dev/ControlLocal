-- C6: recepción técnica del MISMO objeto antes de QC. Sin bytes, fondos o perfiles.
CREATE TABLE custodia_resultado_material_ia (
 organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,archivo_ref uuid NOT NULL UNIQUE,
 operacion_io uuid NOT NULL,numero_io integer NOT NULL,
 estado varchar(20) NOT NULL CHECK(estado IN('PREPARADA','VERIFICADA','RETIRADA')),
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(organizacion_id,id_operacion),UNIQUE(organizacion_id,id_operacion,archivo_ref),
 UNIQUE(organizacion_id,operacion_io,numero_io),
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES material_ia_operacion(organizacion_id,id_operacion),
 FOREIGN KEY(organizacion_id,operacion_io,numero_io) REFERENCES intento_salida_binaria(organizacion_id,operacion,numero)
);
-- Metadatos ya verificados por V144 al crear su vínculo; no recalibra ni presume bytes presentes hoy.
INSERT INTO custodia_resultado_material_ia(organizacion_id,id_operacion,archivo_ref,operacion_io,numero_io,estado,registrada_en)
 SELECT d.organizacion_id,d.id_operacion,d.archivo_ref,d.operacion_io,d.numero_io,
 CASE WHEN s.estado='RETIRADA' THEN 'RETIRADA' ELSE 'VERIFICADA' END,d.registrada_en
 FROM derivada_material_ia d JOIN slot_derivada_material_ia s USING(organizacion_id,id_operacion);
CREATE FUNCTION verificar_custodia_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j material_ia_operacion%ROWTYPE;i intento_salida_binaria%ROWTYPE;s slot_derivada_material_ia%ROWTYPE;
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La custodia conserva el antecedente técnico' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (ROW(NEW.organizacion_id,NEW.id_operacion,NEW.archivo_ref,NEW.operacion_io,NEW.numero_io,NEW.registrada_en)
   IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.id_operacion,OLD.archivo_ref,OLD.operacion_io,OLD.numero_io,OLD.registrada_en)
   OR NEW.version<>OLD.version+1 OR NOT ((OLD.estado='PREPARADA' AND NEW.estado IN('VERIFICADA','RETIRADA')) OR (OLD.estado='VERIFICADA' AND NEW.estado='RETIRADA')))
 THEN RAISE EXCEPTION 'La custodia conserva identidad y transición exactas' USING ERRCODE='23514'; END IF;
 SELECT * INTO STRICT j FROM material_ia_operacion WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion;
 SELECT * INTO STRICT i FROM intento_salida_binaria WHERE organizacion_id=NEW.organizacion_id AND operacion=NEW.operacion_io AND numero=NEW.numero_io;
 SELECT * INTO STRICT s FROM slot_derivada_material_ia WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion;
 IF j.accion<>'MEJORAR_FOTO' OR j.medicion IS NULL OR j.salida IS NULL
  OR i.huella IS DISTINCT FROM j.salida::jsonb->>'huellaFoto'
  OR i.content_type IS DISTINCT FROM j.salida::jsonb->>'contentType' OR i.bytes<=0
  OR i.almacen_huella IS DISTINCT FROM s.namespace_hash
  OR i.fuente IS DISTINCT FROM 'MATERIAL-IA:'||NEW.organizacion_id::text||':'||NEW.id_operacion::text||':'||j.huella_snapshot||':'||i.huella
 THEN RAISE EXCEPTION 'La custodia exige su claim, salida e intento exactos' USING ERRCODE='23514'; END IF;
 IF NEW.estado='VERIFICADA' AND (i.estado NOT IN('ESCRITO_VERIFICADO','VINCULADO') OR i.escritura_incierta)
 THEN RAISE EXCEPTION 'La custodia no presume una escritura verificada' USING ERRCODE='23514'; END IF;
 IF NEW.estado='RETIRADA' AND NOT EXISTS(SELECT 1 FROM retirada_derivada_material_ia r
   WHERE r.organizacion_id=NEW.organizacion_id AND r.id_operacion=NEW.id_operacion AND r.archivo_ref=NEW.archivo_ref AND r.estado='LIBERADA')
 THEN RAISE EXCEPTION 'La retirada exige ausencia conciliada' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;

-- Misma retirada prescindible; GENERADA sólo si custodia acredita ese objeto exacto.
CREATE OR REPLACE FUNCTION verificar_retiro_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s slot_derivada_material_ia%ROWTYPE;
BEGIN
 SELECT * INTO STRICT s FROM slot_derivada_material_ia WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion;
 IF TG_OP='INSERT' THEN
  IF NEW.estado<>'INTENCION' OR NEW.revision<>s.revision+1 OR s.estado NOT IN('DISPONIBLE','GENERADA')
   OR (s.estado='GENERADA' AND NOT EXISTS(SELECT 1 FROM custodia_resultado_material_ia c JOIN intento_salida_binaria i
    ON i.organizacion_id=c.organizacion_id AND i.operacion=c.operacion_io AND i.numero=c.numero_io
    WHERE c.organizacion_id=NEW.organizacion_id AND c.id_operacion=NEW.id_operacion AND c.archivo_ref=NEW.archivo_ref
     AND c.estado IN('PREPARADA','VERIFICADA') AND i.almacen_huella=s.namespace_hash
     AND NEW.identidad_objeto=i.almacen_huella||':'||i.clave_proveedor AND NEW.bytes=i.bytes AND NEW.huella=i.huella))
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
END; $$;
CREATE TRIGGER custodia_material_ia_verificar BEFORE INSERT OR UPDATE OR DELETE ON custodia_resultado_material_ia FOR EACH ROW EXECUTE FUNCTION verificar_custodia_material_ia();
CREATE TRIGGER custodia_material_ia_truncate BEFORE TRUNCATE ON custodia_resultado_material_ia FOR EACH STATEMENT EXECUTE FUNCTION verificar_custodia_material_ia();

-- Retirar recepción no entregable no requiere fingir un vínculo comercial APROBADO.
DO $$ DECLARE c record;BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='retirada_derivada_material_ia'::regclass
   AND confrelid='derivada_material_ia'::regclass AND contype='f'
 LOOP EXECUTE format('ALTER TABLE retirada_derivada_material_ia DROP CONSTRAINT %I',c.conname);END LOOP;
END; $$;
ALTER TABLE retirada_derivada_material_ia ADD CONSTRAINT fk_retirada_custodia_material_ia
 FOREIGN KEY(organizacion_id,id_operacion,archivo_ref) REFERENCES custodia_resultado_material_ia(organizacion_id,id_operacion,archivo_ref);

-- Importe técnico NATIVO de cada medición, no saldo ni costo liquidado.
-- Conserva decimales nuevos; no reconstruye la precisión ya perdida de filas históricas.
-- Fondo/reserva/créditos/totales liquidados conservan NUMERIC(38,12) y cierre HALF_EVEN.
ALTER TABLE b0_medicion_tecnica ALTER COLUMN costo_usd TYPE numeric;
ALTER TABLE b0_medicion_tecnica ADD CONSTRAINT ck_b0_medicion_costo_nativo_finito
 CHECK(costo_usd>=0 AND costo_usd NOT IN('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric));

-- Claims/resolución técnicos; unidades y dinero permanecen en las tablas B0 existentes.
CREATE TABLE b0_etapa_operacion_ia (
 organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,etapa varchar(80) NOT NULL,
 clave varchar(64) NOT NULL UNIQUE CHECK(clave ~ '^[0-9a-f]{64}$'),proveedor varchar(100) NOT NULL,
 huella_peticion varchar(64) NOT NULL CHECK(huella_peticion ~ '^[0-9a-f]{64}$'),
 estado varchar(20) NOT NULL CHECK(estado IN('PENDIENTE','RECLAMADA','MEDIDA','NO_INVOCADA','INCIERTA')),
 resultado_hash varchar(64),mediciones jsonb,prueba jsonb,
 programada_en timestamptz NOT NULL DEFAULT clock_timestamp(),reclamada_en timestamptz,resuelta_en timestamptz,
 PRIMARY KEY(organizacion_id,id_operacion,etapa),
 UNIQUE(organizacion_id,id_operacion,clave),
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES b0_operacion_ia(organizacion_id,id_operacion),
 CHECK((estado='PENDIENTE' AND reclamada_en IS NULL) OR estado='NO_INVOCADA' OR (estado<>'PENDIENTE' AND reclamada_en IS NOT NULL)),
 CHECK((estado IN('MEDIDA','NO_INVOCADA'))=(resuelta_en IS NOT NULL)),
 CONSTRAINT ck_b0_etapa_ia_medida CHECK((estado<>'MEDIDA' OR
  (resultado_hash IS NOT NULL AND resultado_hash ~ '^[0-9a-f]{64}$' AND mediciones IS NOT NULL
   AND CASE WHEN jsonb_typeof(mediciones)='array' THEN jsonb_array_length(mediciones)>0 ELSE false END)) IS TRUE),
 CONSTRAINT ck_b0_etapa_ia_no_invocada CHECK((estado<>'NO_INVOCADA' OR
  (prueba IS NOT NULL AND jsonb_typeof(prueba)='object' AND prueba<>'{}'::jsonb)) IS TRUE)
);
ALTER TABLE b0_medicion_tecnica ADD COLUMN etapa_clave varchar(64),
 ADD CONSTRAINT fk_b0_medicion_etapa FOREIGN KEY(organizacion_id,id_operacion,etapa_clave)
 REFERENCES b0_etapa_operacion_ia(organizacion_id,id_operacion,clave);
CREATE FUNCTION verificar_origen_medicion_etapa_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.etapa_clave IS NOT NULL AND NOT EXISTS(SELECT 1 FROM b0_etapa_operacion_ia e
  WHERE e.organizacion_id=NEW.organizacion_id AND e.id_operacion=NEW.id_operacion AND e.clave=NEW.etapa_clave
   AND e.proveedor=NEW.proveedor AND e.estado IN('RECLAMADA','INCIERTA')) THEN
  RAISE EXCEPTION 'La medición conserva el claim exacto de su etapa' USING ERRCODE='23514',CONSTRAINT='ck_b0_medicion_origen_etapa';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER medicion_ia_etapa BEFORE INSERT ON b0_medicion_tecnica FOR EACH ROW EXECUTE FUNCTION verificar_origen_medicion_etapa_ia();
CREATE FUNCTION conservar_etapa_operacion_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La etapa conserva su claim y hechos' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.organizacion_id,NEW.id_operacion,NEW.etapa,NEW.clave,NEW.proveedor,NEW.huella_peticion,NEW.programada_en)
   IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.id_operacion,OLD.etapa,OLD.clave,OLD.proveedor,OLD.huella_peticion,OLD.programada_en)
   OR OLD.estado IN('MEDIDA','NO_INVOCADA')
   OR NOT ((OLD.estado='PENDIENTE' AND NEW.estado IN('RECLAMADA','NO_INVOCADA')) OR (OLD.estado='RECLAMADA' AND NEW.estado IN('MEDIDA','NO_INVOCADA','INCIERTA')) OR (OLD.estado='INCIERTA' AND NEW.estado IN('MEDIDA','NO_INVOCADA')))
   OR (OLD.reclamada_en IS NOT NULL AND NEW.reclamada_en IS DISTINCT FROM OLD.reclamada_en)
 THEN RAISE EXCEPTION 'La etapa no admite otra identidad, claim o resolución' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER etapa_operacion_ia_conservar BEFORE UPDATE OR DELETE ON b0_etapa_operacion_ia FOR EACH ROW EXECUTE FUNCTION conservar_etapa_operacion_ia();
CREATE TRIGGER etapa_operacion_ia_truncate BEFORE TRUNCATE ON b0_etapa_operacion_ia FOR EACH STATEMENT EXECUTE FUNCTION conservar_etapa_operacion_ia();
CREATE FUNCTION verificar_mediciones_etapa_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v jsonb;t text;
BEGIN
 IF NEW.estado='MEDIDA' AND jsonb_typeof(NEW.mediciones)='array' THEN
  FOR v IN SELECT value FROM jsonb_array_elements(NEW.mediciones) LOOP
   t=v#>>'{}';
   IF jsonb_typeof(v)<>'string' OR t !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
    RAISE EXCEPTION 'La etapa exige identificadores reales de medición' USING ERRCODE='23514',CONSTRAINT='ck_b0_etapa_ia_vinculo';
   END IF;
   IF NOT EXISTS(SELECT 1 FROM b0_medicion_tecnica m WHERE m.organizacion_id=NEW.organizacion_id
    AND m.id_operacion=NEW.id_operacion AND m.id_medicion=t::uuid AND m.proveedor=NEW.proveedor AND m.etapa_clave=NEW.clave) THEN
    RAISE EXCEPTION 'La medición exige la misma organización, operación y proveedor' USING ERRCODE='23514',CONSTRAINT='ck_b0_etapa_ia_vinculo';
   END IF;
  END LOOP;
  IF jsonb_array_length(NEW.mediciones)<>(SELECT count(DISTINCT value::uuid) FROM jsonb_array_elements_text(NEW.mediciones)) THEN
   RAISE EXCEPTION 'La etapa no duplica mediciones' USING ERRCODE='23514',CONSTRAINT='ck_b0_etapa_ia_vinculo';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER etapa_ia_mediciones BEFORE INSERT OR UPDATE ON b0_etapa_operacion_ia FOR EACH ROW EXECUTE FUNCTION verificar_mediciones_etapa_ia();
CREATE UNIQUE INDEX uq_material_ia_control_claim ON material_ia_evento(organizacion_id,id_operacion) WHERE estado='QC_CLAIM';
CREATE FUNCTION exigir_conjunto_etapas_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o b0_operacion_ia%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='b0_operacion_ia' THEN
  IF NEW.estado IN('LIQUIDADA','CANCELADA') AND EXISTS(SELECT 1 FROM b0_etapa_operacion_ia e
   WHERE e.organizacion_id=NEW.organizacion_id AND e.id_operacion=NEW.id_operacion AND e.estado NOT IN('MEDIDA','NO_INVOCADA'))
  THEN RAISE EXCEPTION 'El conjunto conserva etapas facturables pendientes' USING ERRCODE='23514'; END IF;
 ELSE
  -- Mismo fence de fila que el cierre Core; una etapa no puede confirmar después de ese cierre.
  SELECT * INTO STRICT o FROM b0_operacion_ia WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion FOR NO KEY UPDATE;
  IF o.estado NOT IN('EN_PROCESO','INCIERTA') THEN RAISE EXCEPTION 'La operación cerrada no admite programar o reclamar etapas' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER etapa_ia_conjunto BEFORE INSERT OR UPDATE ON b0_etapa_operacion_ia FOR EACH ROW EXECUTE FUNCTION exigir_conjunto_etapas_ia();
CREATE TRIGGER operacion_ia_etapas BEFORE UPDATE OF estado ON b0_operacion_ia FOR EACH ROW EXECUTE FUNCTION exigir_conjunto_etapas_ia();

-- Mismo slot/7d/fence: una recepción GENERADA también puede retirarse sin fingir DISPONIBLE/QC.
CREATE OR REPLACE FUNCTION verificar_slot_material_ia() RETURNS trigger LANGUAGE plpgsql AS $$
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
        OR (OLD.estado='GENERADA' AND (NEW.estado IN('DISPONIBLE','NO_ALMACENADA') OR (NEW.estado='RETIRADA_PENDIENTE'
             AND EXISTS(SELECT 1 FROM custodia_resultado_material_ia c WHERE c.organizacion_id=NEW.organizacion_id AND c.id_operacion=NEW.id_operacion))))
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
END; $$;
