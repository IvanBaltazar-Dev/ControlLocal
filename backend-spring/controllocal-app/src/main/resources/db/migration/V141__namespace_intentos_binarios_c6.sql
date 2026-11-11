-- RF080: un GET vacío en otro namespace no acredita retiro del objeto original.
-- Las filas anteriores permanecen sin atribución inventada; no participan de recuperación automática.
ALTER TABLE intento_salida_binaria ADD COLUMN almacen_huella varchar(64)
 CHECK(almacen_huella IS NULL OR almacen_huella ~ '^[0-9a-f]{64}$');
CREATE OR REPLACE FUNCTION conservar_identidad_intento_binario_c6() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
   IF NEW.almacen_huella IS NULL THEN RAISE EXCEPTION 'El intento nuevo requiere identidad del almacén'; END IF;
   RETURN NEW;
 END IF;
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Los intentos técnicos conservan su antecedente'; END IF;
 IF ROW(NEW.id_intento,NEW.organizacion_id,NEW.operacion,NEW.numero,NEW.fuente,NEW.clave_proveedor,NEW.extension,
   NEW.content_type,NEW.huella,NEW.bytes,NEW.registrado_en,NEW.almacen_huella)
  IS DISTINCT FROM ROW(OLD.id_intento,OLD.organizacion_id,OLD.operacion,OLD.numero,OLD.fuente,OLD.clave_proveedor,OLD.extension,
   OLD.content_type,OLD.huella,OLD.bytes,OLD.registrado_en,OLD.almacen_huella)
  THEN RAISE EXCEPTION 'Un intento conserva identidad, almacén, fuente y contenido esperado'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER intento_binario_c6_identificar BEFORE INSERT ON intento_salida_binaria
 FOR EACH ROW EXECUTE FUNCTION conservar_identidad_intento_binario_c6();
DROP INDEX ix_intentos_binarios_recuperables;
CREATE INDEX ix_intentos_binarios_almacen_recuperables ON intento_salida_binaria(almacen_huella,id_intento)
 WHERE estado NOT IN('VINCULADO','RETIRADO_VERIFICADO') OR escritura_incierta;
