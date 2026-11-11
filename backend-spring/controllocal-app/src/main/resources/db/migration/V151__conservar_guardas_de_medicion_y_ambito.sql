-- B0/C6, 03/11/2026. Revisada por Root tras PG365.
-- Conserva guardas y diagnosticos aprobados; no cambia estados ni importes.
-- BASE V149 SHA-256:
-- 5bbdbfcb847ffd228a1b624918883cd067fffc233dc861ac951b12fb55fc6dac
-- BASE V150 SHA-256:
-- 9c8872a063911aa7952605837d9adf2b0d5f6e9f78b1f854358bd0bf6a69d56e
-- Restituye validacion/diagnosticos V148 con el padre tecnico de V149.
-- No modifica tablas, filas, estados, permisos, triggers, CHECKs o FKs.

CREATE OR REPLACE FUNCTION verificar_origen_medicion_etapa_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.etapa_clave IS NOT NULL AND NOT EXISTS(SELECT 1 FROM b0_etapa_operacion_ia e
  WHERE e.id_operacion_b0=NEW.id_operacion_b0 AND e.clave=NEW.etapa_clave
   AND e.organizacion_id IS NOT DISTINCT FROM NEW.organizacion_id
   AND e.id_caso_evaluacion IS NOT DISTINCT FROM NEW.id_caso_evaluacion
   AND e.id_operacion=NEW.id_operacion
   AND e.proveedor=NEW.proveedor AND e.estado IN('RECLAMADA','INCIERTA')) THEN
  RAISE EXCEPTION 'La medición conserva el claim exacto de su etapa'
   USING ERRCODE='23514',CONSTRAINT='ck_b0_medicion_origen_etapa';
 END IF;
 RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION verificar_mediciones_etapa_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v jsonb;t text;
BEGIN
 -- La CHECK ck_b0_etapa_ia_medida de V148 conserva SQL NULL, JSON null,
 -- objetos, arrays vacios y resultado_hash ausente/invalido. El trigger
 -- valida elementos solamente cuando el contenedor es un array.
 IF NEW.estado='MEDIDA' AND jsonb_typeof(NEW.mediciones)='array' THEN
  FOR v IN SELECT value FROM jsonb_array_elements(NEW.mediciones) LOOP
   t=v#>>'{}';
   IF jsonb_typeof(v)<>'string' OR t !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
    RAISE EXCEPTION 'La etapa exige identificadores reales de medición'
     USING ERRCODE='23514',CONSTRAINT='ck_b0_etapa_ia_vinculo';
   END IF;
   IF NOT EXISTS(SELECT 1 FROM b0_medicion_tecnica m
    WHERE m.id_operacion_b0=NEW.id_operacion_b0 AND m.id_medicion=t::uuid
     AND m.organizacion_id IS NOT DISTINCT FROM NEW.organizacion_id
     AND m.id_caso_evaluacion IS NOT DISTINCT FROM NEW.id_caso_evaluacion
     AND m.id_operacion=NEW.id_operacion
     AND m.proveedor=NEW.proveedor AND m.etapa_clave=NEW.clave) THEN
    RAISE EXCEPTION 'La medición exige la misma organización, operación y proveedor'
     USING ERRCODE='23514',CONSTRAINT='ck_b0_etapa_ia_vinculo';
   END IF;
  END LOOP;
  -- El UUID normalizado es la identidad: mayusculas no crean otra medicion.
  IF jsonb_array_length(NEW.mediciones)<>(SELECT count(DISTINCT value::uuid) FROM jsonb_array_elements_text(NEW.mediciones)) THEN
   RAISE EXCEPTION 'La etapa no duplica mediciones'
    USING ERRCODE='23514',CONSTRAINT='ck_b0_etapa_ia_vinculo';
  END IF;
 END IF;
 RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION exigir_conjunto_etapas_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o b0_operacion_ia%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='b0_operacion_ia' THEN
  IF NEW.estado IN('LIQUIDADA','CANCELADA') AND EXISTS(SELECT 1 FROM b0_etapa_operacion_ia e
   WHERE e.id_operacion_b0=NEW.id_operacion_b0 AND e.estado NOT IN('MEDIDA','NO_INVOCADA')) THEN
   RAISE EXCEPTION 'El conjunto conserva etapas facturables pendientes' USING ERRCODE='23514';
  END IF;
 ELSE
  -- Mismo fence de fila que el cierre Core. La PK tecnica identifica tanto
  -- COMERCIAL como EVALUACION; las ternas/FKs V149 mantienen su procedencia.
  SELECT * INTO STRICT o FROM b0_operacion_ia WHERE id_operacion_b0=NEW.id_operacion_b0 FOR NO KEY UPDATE;
  IF o.estado NOT IN('EN_PROCESO','INCIERTA') THEN
   RAISE EXCEPTION 'La operación cerrada no admite programar o reclamar etapas' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
