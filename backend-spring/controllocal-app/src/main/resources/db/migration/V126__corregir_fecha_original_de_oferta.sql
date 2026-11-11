-- Reparación reproducida PG54. V90/Oferta usan fecha_creacion, no el nombre del DTO fechaRegistro.
-- Mantiene los18términos, desenlaces terminales, trigger y SQLSTATE; V122 no se modifica.
CREATE OR REPLACE FUNCTION conservar_terminos_oferta_c4()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.id_oferta,NEW.fecha_creacion,NEW.organizacion_id,NEW.id_oportunidad,NEW.id_captacion,NEW.ronda,NEW.presentada_por,NEW.monto,NEW.moneda,NEW.condiciones,NEW.fecha,NEW.id_rol_registra,NEW.origen,NEW.hito_referencia,NEW.id_precio_referencia,NEW.monto_referencia,NEW.moneda_referencia,NEW.id_oferta_anterior)
 IS DISTINCT FROM ROW(OLD.id_oferta,OLD.fecha_creacion,OLD.organizacion_id,OLD.id_oportunidad,OLD.id_captacion,OLD.ronda,OLD.presentada_por,OLD.monto,OLD.moneda,OLD.condiciones,OLD.fecha,OLD.id_rol_registra,OLD.origen,OLD.hito_referencia,OLD.id_precio_referencia,OLD.monto_referencia,OLD.moneda_referencia,OLD.id_oferta_anterior)
 OR (OLD.estado<>'P' AND NEW.estado IS DISTINCT FROM OLD.estado) THEN
 RAISE EXCEPTION 'Se conserva la propuesta original y los desenlaces terminales' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
