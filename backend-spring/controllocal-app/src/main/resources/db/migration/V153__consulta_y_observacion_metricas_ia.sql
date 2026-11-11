-- CAT-R25/directriz8.9-8.10 y decision titular04/11. Sin cuentas, fondos o gasto.
-- La consulta agregada no pertenece a un expediente ni inventa una organizacion.
CREATE TABLE b0_consulta_metricas_ia (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 concesion uuid NOT NULL,
 cuenta bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 plan uuid NOT NULL,version_plan varchar(80) NOT NULL,
 caso uuid,fondo uuid REFERENCES b0_fondo_tecnico(id_fondo),
 clase varchar(12) NOT NULL CHECK(clase IN('TECNICA','FINANCIERA')),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,
 operacion uuid,registrada_en timestamptz NOT NULL,
 FOREIGN KEY(concesion,cuenta,plan,version_plan)
  REFERENCES b0_atribucion_plataforma(id,cuenta,plan,version_plan),
 FOREIGN KEY(caso,plan,version_plan) REFERENCES b0_evaluacion_caso(id,plan,version_plan),
 CHECK(hasta>desde),
 CHECK((clase='TECNICA' AND operacion IS NULL AND fondo IS NULL)
    OR(clase='FINANCIERA' AND operacion IS NOT NULL AND caso IS NOT NULL AND fondo IS NOT NULL))
);
CREATE FUNCTION verificar_consulta_metricas_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE g b0_atribucion_plataforma%ROWTYPE;
BEGIN
 SELECT * INTO g FROM b0_atribucion_plataforma WHERE id=NEW.concesion FOR SHARE;
 IF NOT FOUND OR g.cuenta<>NEW.cuenta OR g.plan<>NEW.plan OR g.version_plan<>NEW.version_plan
   OR g.recurso_compra IS NOT NULL OR g.revocada_en IS NOT NULL
   OR clock_timestamp()<g.desde OR clock_timestamp()>=g.hasta
   OR (g.caso IS NOT NULL AND g.caso IS DISTINCT FROM NEW.caso)
   OR (NEW.clase='TECNICA' AND (g.atribucion<>'OPERACION_IA_PLATFORM'
       OR NOT g.acciones ? 'CONSULTAR_ESTADO' OR NOT g.campos ? 'METRICA_TECNICA'))
   OR (NEW.clase='FINANCIERA' AND (g.atribucion<>'FINANCIERO_PLATFORM'
       OR g.fondo IS DISTINCT FROM NEW.fondo
       OR NOT g.acciones ? 'LEER_LEDGER' OR NOT g.campos ? 'LEDGER'))
   OR NOT EXISTS(SELECT 1 FROM b0_evaluacion_plan p
       WHERE p.id=NEW.plan AND p.version=NEW.version_plan AND p.finalidad=g.finalidad)
 THEN RAISE EXCEPTION 'La consulta conserva la atribucion y su ambito' USING ERRCODE='23514'; END IF;
 IF NEW.clase='FINANCIERA' AND NOT EXISTS(SELECT 1 FROM b0_operacion_ia o
     WHERE o.id_caso_evaluacion=NEW.caso AND o.id_operacion=NEW.operacion
       AND o.perfil=NEW.plan::text AND o.version_perfil=NEW.version_plan
       AND o.id_fondo=NEW.fondo AND o.finalidad=g.finalidad)
 THEN RAISE EXCEPTION 'La consulta conserva la operacion financiera' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER verificar_consulta_metricas_ia BEFORE INSERT ON b0_consulta_metricas_ia
 FOR EACH ROW EXECUTE FUNCTION verificar_consulta_metricas_ia();
CREATE TRIGGER conservar_consulta_metricas_ia BEFORE UPDATE OR DELETE ON b0_consulta_metricas_ia
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER conservar_consulta_metricas_ia_truncate BEFORE TRUNCATE ON b0_consulta_metricas_ia
 FOR EACH STATEMENT EXECUTE FUNCTION b0_conservar_antecedente();

-- Una observacion del mismo intento no es otra llamada, medicion ni cargo.
CREATE UNIQUE INDEX uq_b0_observacion_http_intento
 ON b0_antecedente(id_operacion_b0,(contenido->>'claveIntento'))
 WHERE hecho='OBSERVACION_HTTP_IA';
