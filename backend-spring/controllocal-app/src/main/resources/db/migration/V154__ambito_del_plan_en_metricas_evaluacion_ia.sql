-- R25/B0: evaluacion neutra usa caso/plan, no perfil comercial.
-- Reparacion tecnica436; V153 aplicada se conserva inmutable. Sin backfill o usuarios ficticios.
CREATE OR REPLACE FUNCTION verificar_consulta_metricas_ia() RETURNS trigger LANGUAGE plpgsql AS $$
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
     JOIN b0_evaluacion_caso c ON c.id=o.id_caso_evaluacion
     WHERE c.id=NEW.caso AND o.id_operacion=NEW.operacion
       AND c.plan=NEW.plan AND c.version_plan=NEW.version_plan
       AND o.organizacion_id IS NULL AND o.id_fondo=NEW.fondo AND o.finalidad=g.finalidad)
 THEN RAISE EXCEPTION 'La consulta conserva la operacion financiera' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
