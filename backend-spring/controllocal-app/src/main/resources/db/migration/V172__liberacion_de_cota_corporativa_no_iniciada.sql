-- R24/B0: el retiro conocido antes de asignación libera capacidad corporativa,
-- sin tarifa cero, cargo, revisión inventada o alteración de trabajos iniciados.
CREATE OR REPLACE FUNCTION cota_corporativa_i0() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p i0_politica_mercado%ROWTYPE;
BEGIN
 IF NEW.organizacion_id IS NOT NULL THEN RETURN NEW;END IF;
 SELECT * INTO p FROM i0_politica_mercado WHERE id=NEW.politica AND version=NEW.version_politica;
 IF TG_OP='INSERT' THEN
  UPDATE b0_regla_trabajo_manual SET reservadas=reservadas+NEW.rondas_previstas
   WHERE id=p.regla AND version=p.version_regla AND financiacion='MANTENIMIENTO_BROX'
    AND reservadas+realizadas+NEW.rondas_previstas<=cota_rondas;
  IF NOT FOUND THEN RAISE EXCEPTION 'No hay cota corporativa de investigación disponible';END IF;
 ELSIF OLD.estado='PENDIENTE' AND NEW.estado='RETIRADA' AND OLD.operador IS NULL AND NEW.operador IS NULL
   AND OLD.entrega IS NULL AND NEW.entrega IS NULL AND OLD.revision IS NULL AND NEW.revision IS NULL AND OLD.liquidada_en IS NULL AND NEW.liquidada_en IS NULL THEN
  UPDATE b0_regla_trabajo_manual SET reservadas=reservadas-NEW.rondas_previstas
   WHERE id=p.regla AND version=p.version_regla AND financiacion='MANTENIMIENTO_BROX' AND reservadas>=NEW.rondas_previstas;
  IF NOT FOUND THEN RAISE EXCEPTION 'El trabajo no iniciado conserva su reserva corporativa';END IF;
 ELSIF OLD.liquidada_en IS NULL AND NEW.liquidada_en IS NOT NULL THEN
  UPDATE b0_regla_trabajo_manual SET reservadas=reservadas-NEW.rondas_previstas,realizadas=realizadas+NEW.rondas_liquidadas
   WHERE id=p.regla AND version=p.version_regla AND reservadas>=NEW.rondas_previstas;
  IF NOT FOUND THEN RAISE EXCEPTION 'La investigación corporativa no conserva su reserva';END IF;
 END IF;RETURN NEW;
END;$$;
