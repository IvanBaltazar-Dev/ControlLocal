-- CAT-R25/directriz8.9: confirmación completa y aplicación recuperable del costo técnico.
-- Integrada por Root desde propuesta156 transferida, después de155; sólo base de pruebas autorizada.
-- CAT-R25 / directriz8.9. Ninguna semilla, cuenta, grant, financiación o cambio de migración aplicada.
CREATE TABLE b0_costo_operacion_ia(
 id_operacion_b0 uuid PRIMARY KEY REFERENCES b0_operacion_ia(id_operacion_b0),
 costo_efectivo_usd numeric NOT NULL,costo_imputado_usd numeric NOT NULL,
 pendiente_positivo_usd numeric NOT NULL,conjunto_hash varchar(64) NOT NULL,version bigint NOT NULL,
 CONSTRAINT ck_b0_costo_proyeccion_importes CHECK(
  costo_efectivo_usd>=0 AND costo_efectivo_usd<1e26 AND costo_efectivo_usd=trunc(costo_efectivo_usd,12)
  AND costo_imputado_usd>=0 AND costo_imputado_usd<1e26 AND costo_imputado_usd=trunc(costo_imputado_usd,12)
  AND pendiente_positivo_usd>=0 AND pendiente_positivo_usd<1e26 AND pendiente_positivo_usd=trunc(pendiente_positivo_usd,12)
  AND conjunto_hash ~ '^[0-9a-f]{64}$' AND version>=0)
);
-- No backfill que redondee/sume fragmentos enSQL: Core reconstruye nativos en lote bajo fondo y fijaQ12.
CREATE FUNCTION verificar_proyeccion_costo_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o b0_operacion_ia%ROWTYPE;
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La proyección conserva su operación y hechos' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_proyeccion_conservada';END IF;
 SELECT * INTO o FROM b0_operacion_ia WHERE id_operacion_b0=NEW.id_operacion_b0;
 IF NOT FOUND OR (TG_OP='INSERT' AND NEW.version<>0)
  OR (TG_OP='UPDATE' AND (NEW.id_operacion_b0<>OLD.id_operacion_b0 OR NEW.version<>OLD.version+1))
  OR NEW.pendiente_positivo_usd IS DISTINCT FROM greatest(NEW.costo_efectivo_usd-
    CASE WHEN o.estado IN('LIQUIDADA','CANCELADA') THEN NEW.costo_imputado_usd ELSE o.maximo_tecnico_usd END,0)
 THEN RAISE EXCEPTION 'La proyección conserva identidad, CAS y provisión' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_proyeccion_cas';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER tg_b0_costo_proyeccion BEFORE INSERT OR UPDATE OR DELETE ON b0_costo_operacion_ia FOR EACH ROW EXECUTE FUNCTION verificar_proyeccion_costo_ia();
CREATE TRIGGER tg_b0_costo_proyeccion_truncate BEFORE TRUNCATE ON b0_costo_operacion_ia FOR EACH STATEMENT EXECUTE FUNCTION verificar_proyeccion_costo_ia();

CREATE UNIQUE INDEX uq_b0_fuente_costo_dueno ON b0_antecedente((contenido->>'fuenteNaturalHash')) WHERE hecho='FUENTE_COSTO_IA';
CREATE UNIQUE INDEX uq_b0_confirmacion_costo_comando ON b0_antecedente((contenido->>'confirmacion')) WHERE hecho='CONFIRMACION_COSTO_IA';
CREATE UNIQUE INDEX uq_b0_confirmacion_costo_fuente ON b0_antecedente((contenido->>'fuenteHash')) WHERE hecho='CONFIRMACION_COSTO_IA';
CREATE UNIQUE INDEX uq_b0_confirmacion_costo_cadena ON b0_antecedente(id_operacion_b0,(contenido->>'medicion'),(coalesce(contenido->>'anterior','INICIAL'))) WHERE hecho='CONFIRMACION_COSTO_IA';
CREATE INDEX ix_b0_confirmacion_costo_medicion ON b0_antecedente(id_operacion_b0,(contenido->>'medicion'),id_antecedente DESC) WHERE hecho='CONFIRMACION_COSTO_IA';
CREATE UNIQUE INDEX uq_b0_aplicacion_costo_revision ON b0_antecedente(id_operacion_b0,((contenido->>'revision')::bigint)) WHERE hecho='APLICACION_COSTO_IA';

CREATE FUNCTION verificar_hecho_costo_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j jsonb;g b0_atribucion_plataforma%ROWTYPE;o b0_operacion_ia%ROWTYPE;
 c b0_evaluacion_caso%ROWTYPE;p b0_evaluacion_plan%ROWTYPE;m b0_medicion_tecnica%ROWTYPE;
 pro b0_costo_operacion_ia%ROWTYPE;prev uuid;fuente text;sid text;rev bigint;actor bigint;
BEGIN
 IF NEW.hecho NOT IN('FUENTE_COSTO_IA','CONFIRMACION_COSTO_IA','APLICACION_COSTO_IA') THEN RETURN NEW;END IF;
 j:=NEW.contenido;
 IF jsonb_typeof(j) IS DISTINCT FROM 'object' OR NOT(j ?& ARRAY['esquema','confirmacion','cuenta','concesion','sidHash','revisionSesion'])
  OR EXISTS(SELECT 1 FROM unnest(ARRAY['esquema','confirmacion','concesion','sidHash']) k WHERE jsonb_typeof(j->k) IS DISTINCT FROM 'string')
  OR jsonb_typeof(j->'cuenta') IS DISTINCT FROM 'number' OR jsonb_typeof(j->'revisionSesion') IS DISTINCT FROM 'number'
  OR (j->>'confirmacion') !~ '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$'
  OR (j->>'concesion') !~ '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$'
  OR (j->>'sidHash') !~ '^[0-9a-f]{64}$'
 THEN RAISE EXCEPTION 'El hecho financiero requiere contrato tipado' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_hecho_forma';END IF;
 actor:=(j->>'cuenta')::bigint;sid:=j->>'sidHash';rev:=(j->>'revisionSesion')::bigint;
 -- Orden de identidad/grant anterior al padre. Revocación bloqueada, vencimiento revalidado al final.
 PERFORM 1 FROM sesion_acceso WHERE referencia_hash=sid AND id_cuenta_acceso=actor AND revision=rev AND NOT revocada AND vence_en>clock_timestamp() FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'El hecho requiere SID vigente' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_hecho_autoridad';END IF;
 SELECT * INTO g FROM b0_atribucion_plataforma WHERE id=(j->>'concesion')::uuid FOR SHARE;
 IF NOT FOUND OR g.cuenta<>actor OR g.atribucion<>'FINANCIERO_PLATFORM' OR g.recurso_compra IS NOT NULL
  OR NOT(g.acciones @> '["AJUSTAR_COSTO"]'::jsonb) OR NOT(g.campos @> '["LEDGER"]'::jsonb)
  OR g.revocada_en IS NOT NULL OR clock_timestamp()<g.desde OR clock_timestamp()>=g.hasta
 THEN RAISE EXCEPTION 'El hecho requiere ajuste financiero expreso' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_hecho_autoridad';END IF;
 SELECT * INTO o FROM b0_operacion_ia WHERE id_operacion_b0=NEW.id_operacion_b0 FOR NO KEY UPDATE;
 IF NOT FOUND OR o.organizacion_id IS NOT NULL OR NEW.organizacion_id IS NOT NULL
  OR NEW.id_caso_evaluacion IS DISTINCT FROM o.id_caso_evaluacion OR NEW.id_operacion IS DISTINCT FROM o.id_operacion
  OR o.id_fondo IS DISTINCT FROM g.fondo OR (g.caso IS NOT NULL AND g.caso<>o.id_caso_evaluacion)
 THEN RAISE EXCEPTION 'El hecho conserva operación y fondo exactos' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_hecho_ambito';END IF;
 SELECT * INTO c FROM b0_evaluacion_caso WHERE id=o.id_caso_evaluacion;
 SELECT * INTO p FROM b0_evaluacion_plan WHERE id=c.plan AND version=c.version_plan;
 IF NOT FOUND OR g.plan IS DISTINCT FROM c.plan OR g.version_plan IS DISTINCT FROM c.version_plan
  OR g.finalidad IS DISTINCT FROM p.finalidad OR o.finalidad IS DISTINCT FROM p.finalidad
 THEN RAISE EXCEPTION 'El hecho conserva plan, versión y finalidad' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_hecho_ambito';END IF;
 IF NEW.hecho='FUENTE_COSTO_IA' THEN
  IF j->>'esquema' IS DISTINCT FROM 'FUENTE-COSTO-IA-1'
   OR NOT(j ?& ARRAY['fuenteNaturalHash','operacionB0','medicion','cuentaProveedor','emisorFacturacion','documento','linea','registradaEn'])
   OR EXISTS(SELECT 1 FROM unnest(ARRAY['fuenteNaturalHash','operacionB0','medicion','cuentaProveedor','emisorFacturacion','documento','linea','registradaEn']) k
      WHERE jsonb_typeof(j->k) IS DISTINCT FROM 'string' OR length(trim(j->>k))=0 OR j->>k IS DISTINCT FROM trim(j->>k))
   OR (j->>'operacionB0')::uuid IS DISTINCT FROM o.id_operacion_b0
  THEN RAISE EXCEPTION 'La fuente conserva identidad canónica y dueño' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_fuente_dueno';END IF;
  PERFORM 1 FROM b0_medicion_tecnica WHERE id_operacion_b0=o.id_operacion_b0 AND id_medicion=(j->>'medicion')::uuid;
  fuente:=encode(sha256(convert_to(j->>'cuentaProveedor','UTF8')||decode('00','hex')||convert_to(j->>'documento','UTF8')||decode('00','hex')||convert_to(j->>'linea','UTF8')),'hex');
  IF NOT FOUND OR fuente IS DISTINCT FROM j->>'fuenteNaturalHash'
  THEN RAISE EXCEPTION 'La fuente pertenece a su medición y cuenta canónica' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_fuente_dueno';END IF;
 ELSIF NEW.hecho='CONFIRMACION_COSTO_IA' THEN
  IF j->>'esquema' IS DISTINCT FROM 'COSTO-IA-1'
   OR NOT(j ?& ARRAY['operacionB0','medicion','proveedor','huellaMedicion','fuenteHash','fuenteNaturalHash','costoTotalUsd','solicitudHash','solicitud','registradaEn'])
   OR EXISTS(SELECT 1 FROM unnest(ARRAY['operacionB0','medicion','proveedor','huellaMedicion','fuenteHash','fuenteNaturalHash','solicitudHash','registradaEn']) k WHERE jsonb_typeof(j->k) IS DISTINCT FROM 'string')
   OR (j->>'operacionB0')::uuid IS DISTINCT FROM o.id_operacion_b0
   OR jsonb_typeof(j->'solicitud') IS DISTINCT FROM 'object' OR jsonb_typeof(j->'costoTotalUsd') IS DISTINCT FROM 'number'
   OR (j->>'costoTotalUsd')::numeric<0 OR (j->>'costoTotalUsd')::numeric>=1e26
   OR j->'costoTotalUsd' IS DISTINCT FROM j->'solicitud'->'costoTotalUsd'
   OR j->>'confirmacion' IS DISTINCT FROM j->'solicitud'->>'confirmacion'
   OR j->>'medicion' IS DISTINCT FROM j->'solicitud'->>'medicion'
   OR j->>'anterior' IS DISTINCT FROM j->'solicitud'->>'anterior'
   OR j->>'huellaMedicion' IS DISTINCT FROM j->'solicitud'->>'huellaMedicion'
   OR (j->>'huellaMedicion') !~ '^[0-9a-f]{64}$' OR (j->>'solicitudHash') !~ '^[0-9a-f]{64}$'
   OR (j->>'fuenteHash') !~ '^[0-9a-f]{64}$'
   OR NOT(j->'solicitud' ?& ARRAY['cuentaProveedor','emisorFacturacion','documento','linea','revisionFuente','fechaFuente','evidencia','huellaEvidencia','causa'])
   OR (j->'solicitud'->>'huellaEvidencia') !~ '^[0-9a-f]{64}$'
   OR EXISTS(SELECT 1 FROM unnest(ARRAY['cuentaProveedor','emisorFacturacion','documento','linea','revisionFuente','fechaFuente','evidencia','causa']) k
     WHERE jsonb_typeof(j->'solicitud'->k) IS DISTINCT FROM 'string' OR length(trim(j->'solicitud'->>k))=0
       OR j->'solicitud'->>k IS DISTINCT FROM trim(j->'solicitud'->>k))
  THEN RAISE EXCEPTION 'La confirmación conserva identidad e importe nativo completos' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_confirmacion_forma';END IF;
  IF j->'solicitud'->>'fechaFuente' ~ '^\d{4}-\d{2}-\d{2}$' THEN
   PERFORM (j->'solicitud'->>'fechaFuente')::date;
  ELSIF j->'solicitud'->>'fechaFuente' ~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$' THEN
   PERFORM (j->'solicitud'->>'fechaFuente')::timestamptz;
  ELSE RAISE EXCEPTION 'La fuente conserva fecha tipada' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_confirmacion_forma';END IF;
  SELECT * INTO m FROM b0_medicion_tecnica WHERE id_operacion_b0=o.id_operacion_b0 AND id_medicion=(j->>'medicion')::uuid;
  IF NOT FOUND OR m.huella IS DISTINCT FROM j->>'huellaMedicion' OR m.proveedor IS DISTINCT FROM j->>'proveedor'
  THEN RAISE EXCEPTION 'La confirmación pertenece a su medición original' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_confirmacion_medicion';END IF;
  SELECT (contenido->>'confirmacion')::uuid INTO prev FROM b0_antecedente WHERE id_operacion_b0=o.id_operacion_b0
   AND hecho='CONFIRMACION_COSTO_IA' AND contenido->>'medicion'=m.id_medicion::text ORDER BY id_antecedente DESC LIMIT 1;
  IF prev IS DISTINCT FROM (j->>'anterior')::uuid THEN RAISE EXCEPTION 'La confirmación requiere la cabeza actual' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_confirmacion_cadena';END IF;
  fuente:=encode(sha256(convert_to(j->'solicitud'->>'cuentaProveedor','UTF8')||decode('00','hex')
   ||convert_to(j->'solicitud'->>'documento','UTF8')||decode('00','hex')||convert_to(j->'solicitud'->>'linea','UTF8')),'hex');
  IF fuente IS DISTINCT FROM j->>'fuenteNaturalHash'
   OR encode(sha256(convert_to(fuente,'UTF8')||decode('00','hex')||convert_to(j->'solicitud'->>'revisionFuente','UTF8')),'hex') IS DISTINCT FROM j->>'fuenteHash'
   OR NOT EXISTS(SELECT 1 FROM b0_antecedente a WHERE a.hecho='FUENTE_COSTO_IA' AND a.contenido->>'fuenteNaturalHash'=fuente
     AND a.id_operacion_b0=o.id_operacion_b0 AND a.contenido->>'medicion'=m.id_medicion::text
     AND a.contenido->>'emisorFacturacion'=j->'solicitud'->>'emisorFacturacion')
  THEN RAISE EXCEPTION 'La fuente conserva dueño natural entre revisiones y adaptadores' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_confirmacion_fuente';END IF;
 ELSE
  SELECT * INTO pro FROM b0_costo_operacion_ia WHERE id_operacion_b0=o.id_operacion_b0;
  IF NOT FOUND OR j->>'esquema' IS DISTINCT FROM 'APLICACION-COSTO-IA-1'
   OR o.estado NOT IN('LIQUIDADA','CANCELADA')
   OR NOT(j ?& ARRAY['revisionAnterior','revision','conjunto','diferenciaUsd','efectivoUsd','imputadoUsd'])
   OR (j->>'revision')::bigint IS DISTINCT FROM pro.version OR (j->>'revisionAnterior')::bigint<>pro.version-1
   OR (j->>'efectivoUsd')::numeric IS DISTINCT FROM pro.costo_efectivo_usd
   OR (j->>'imputadoUsd')::numeric IS DISTINCT FROM pro.costo_imputado_usd
   OR pro.costo_efectivo_usd<>pro.costo_imputado_usd OR j->>'conjunto' IS DISTINCT FROM pro.conjunto_hash
   OR NOT EXISTS(SELECT 1 FROM b0_antecedente a WHERE a.id_operacion_b0=o.id_operacion_b0 AND a.hecho='CONFIRMACION_COSTO_IA' AND a.contenido->>'confirmacion'=j->>'confirmacion')
  THEN RAISE EXCEPTION 'La aplicación conserva su proyección y confirmación' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_aplicacion';END IF;
 END IF;
 IF clock_timestamp()<g.desde OR clock_timestamp()>=g.hasta OR NOT EXISTS(SELECT 1 FROM sesion_acceso WHERE referencia_hash=sid AND NOT revocada AND vence_en>clock_timestamp())
 THEN RAISE EXCEPTION 'El hecho revalida vigencia tras esperar' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_hecho_autoridad';END IF;
 RETURN NEW;
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR invalid_datetime_format OR datetime_field_overflow THEN
 RAISE EXCEPTION 'El hecho requiere identidades e importes tipados' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_hecho_forma';
END;$$;
CREATE TRIGGER tg_b0_hecho_costo BEFORE INSERT ON b0_antecedente FOR EACH ROW EXECUTE FUNCTION verificar_hecho_costo_ia();

-- El dueño no se puede reservar sin conservar su confirmación en la misma transacción.
CREATE FUNCTION verificar_fuente_costo_comprometida() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM b0_antecedente a WHERE a.hecho='CONFIRMACION_COSTO_IA'
  AND a.id_operacion_b0=NEW.id_operacion_b0 AND a.contenido->>'confirmacion'=NEW.contenido->>'confirmacion'
  AND a.contenido->>'fuenteNaturalHash'=NEW.contenido->>'fuenteNaturalHash'
  AND a.contenido->>'medicion'=NEW.contenido->>'medicion')
 THEN RAISE EXCEPTION 'El dueño conserva el hecho financiero comprometido' USING ERRCODE='23514',CONSTRAINT='ck_b0_costo_fuente_comprometida';END IF;
 RETURN NEW;
END;$$;
CREATE CONSTRAINT TRIGGER tg_b0_fuente_costo_comprometida AFTER INSERT ON b0_antecedente
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN(NEW.hecho='FUENTE_COSTO_IA') EXECUTE FUNCTION verificar_fuente_costo_comprometida();
