-- C1/K1, CAT-E32/E33: el selector público identifica un vínculo y una versión
-- binaria concretos. No es la clave del proveedor ni concede acceso por posesión.
-- Conserva columnas, objetos y rutas históricas; no mueve ni borra bytes.
ALTER TABLE documento_solicitud ADD COLUMN archivo_ref uuid;
ALTER TABLE foto_propiedad ADD COLUMN archivo_ref uuid;
ALTER TABLE persona ADD COLUMN foto_ref uuid;

-- E33 y cierre del titular: retirar de la galería no destruye el original ni
-- su referencia histórica. No se inventa autor para las fotos anteriores.
ALTER TABLE foto_propiedad ADD COLUMN estado varchar(1) NOT NULL DEFAULT 'A';
ALTER TABLE foto_propiedad ADD COLUMN fecha_retiro timestamptz;
ALTER TABLE foto_propiedad ADD COLUMN id_persona_registro bigint;
ALTER TABLE foto_propiedad ADD COLUMN id_rol_registro bigint;
ALTER TABLE foto_propiedad ADD COLUMN rol_registro varchar(20);
ALTER TABLE foto_propiedad ADD CONSTRAINT ck_foto_estado CHECK (estado IN ('A','I'));
ALTER TABLE foto_propiedad ADD CONSTRAINT ck_foto_retiro
    CHECK ((estado = 'A' AND fecha_retiro IS NULL) OR (estado = 'I' AND fecha_retiro IS NOT NULL));
ALTER TABLE foto_propiedad ADD CONSTRAINT ck_foto_autor_conocido CHECK (
    (id_persona_registro IS NULL AND id_rol_registro IS NULL AND rol_registro IS NULL) OR
    (id_persona_registro IS NOT NULL AND id_rol_registro IS NOT NULL AND rol_registro IS NOT NULL));
ALTER TABLE foto_propiedad ADD CONSTRAINT fk_foto_persona_registro
    FOREIGN KEY (organizacion_id,id_persona_registro) REFERENCES persona(organizacion_id,id_persona);
ALTER TABLE foto_propiedad ADD CONSTRAINT fk_foto_rol_registro
    FOREIGN KEY (organizacion_id,id_rol_registro) REFERENCES persona_rol(organizacion_id,id_persona_rol);
INSERT INTO entidad_tipo(codigo,descripcion,auditable,referenciable)
    VALUES ('FOTO_PROPIEDAD','Original de foto del inmueble y retirada activa',true,false);

UPDATE documento_solicitud SET archivo_ref = gen_random_uuid()
WHERE ruta_archivo IS NOT NULL AND btrim(ruta_archivo) <> '';
UPDATE foto_propiedad SET archivo_ref = gen_random_uuid();
UPDATE persona SET foto_ref = gen_random_uuid()
WHERE foto_clave IS NOT NULL AND btrim(foto_clave) <> '';

ALTER TABLE foto_propiedad ALTER COLUMN archivo_ref SET NOT NULL;
ALTER TABLE documento_solicitud ADD CONSTRAINT ck_documento_referencia_binaria
CHECK ((ruta_archivo IS NULL OR btrim(ruta_archivo) = '') = (archivo_ref IS NULL));
ALTER TABLE persona ADD CONSTRAINT ck_persona_referencia_binaria
CHECK ((foto_clave IS NULL OR btrim(foto_clave) = '') = (foto_ref IS NULL));

CREATE UNIQUE INDEX ux_documento_archivo_ref ON documento_solicitud (archivo_ref)
WHERE archivo_ref IS NOT NULL;
CREATE UNIQUE INDEX ux_foto_archivo_ref ON foto_propiedad (archivo_ref);
CREATE UNIQUE INDEX ux_persona_foto_ref ON persona (foto_ref) WHERE foto_ref IS NOT NULL;

-- Red de integridad para escritores SQL/fixtures además del modelo JPA:
-- otra clave binaria recibe otra referencia; vaciarla no deja un alias vigente.
-- Si la aplicación ya generó la referencia nueva se conserva, no se genera otra.
CREATE FUNCTION mantener_referencia_binaria() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    clave_nueva text := to_jsonb(NEW) ->> TG_ARGV[0];
    referencia_nueva text := to_jsonb(NEW) ->> TG_ARGV[1];
    clave_anterior text;
    referencia_anterior text;
BEGIN
    IF clave_nueva IS NULL OR btrim(clave_nueva) = '' THEN
        referencia_nueva := NULL;
    ELSIF TG_OP = 'INSERT' THEN
        referencia_nueva := coalesce(referencia_nueva, gen_random_uuid()::text);
    ELSE
        clave_anterior := to_jsonb(OLD) ->> TG_ARGV[0];
        referencia_anterior := to_jsonb(OLD) ->> TG_ARGV[1];
        IF referencia_nueva IS NULL OR
                (clave_nueva IS DISTINCT FROM clave_anterior AND
                 referencia_nueva IS NOT DISTINCT FROM referencia_anterior) THEN
            referencia_nueva := gen_random_uuid()::text;
        END IF;
    END IF;
    NEW := jsonb_populate_record(NEW, jsonb_build_object(TG_ARGV[1], referencia_nueva));
    RETURN NEW;
END;
$$;

CREATE TRIGGER documento_referencia_binaria BEFORE INSERT OR UPDATE OF ruta_archivo, archivo_ref
ON documento_solicitud FOR EACH ROW
EXECUTE FUNCTION mantener_referencia_binaria('ruta_archivo', 'archivo_ref');
CREATE TRIGGER foto_referencia_binaria BEFORE INSERT OR UPDATE OF clave, archivo_ref
ON foto_propiedad FOR EACH ROW
EXECUTE FUNCTION mantener_referencia_binaria('clave', 'archivo_ref');
CREATE TRIGGER perfil_referencia_binaria BEFORE INSERT OR UPDATE OF foto_clave, foto_ref
ON persona FOR EACH ROW
EXECUTE FUNCTION mantener_referencia_binaria('foto_clave', 'foto_ref');

-- Una revisión nueva es otra fila. La ordenación activa puede cambiar; el
-- original, su fecha, vínculo, versión y procedencia conocida no se sobrescriben.
CREATE FUNCTION preservar_original_foto() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.organizacion_id,NEW.id_propiedad,NEW.clave,NEW.archivo_ref,NEW.nombre_archivo,
           NEW.fecha_registro,NEW.id_persona_registro,NEW.id_rol_registro,NEW.rol_registro)
        IS DISTINCT FROM
       ROW(OLD.organizacion_id,OLD.id_propiedad,OLD.clave,OLD.archivo_ref,OLD.nombre_archivo,
           OLD.fecha_registro,OLD.id_persona_registro,OLD.id_rol_registro,OLD.rol_registro) THEN
        RAISE EXCEPTION 'El original de foto es inmutable; anexe otra revisión';
    END IF;
    IF OLD.estado = 'I' AND
       ROW(NEW.estado,NEW.fecha_retiro,NEW.orden) IS DISTINCT FROM ROW(OLD.estado,OLD.fecha_retiro,OLD.orden) THEN
        RAISE EXCEPTION 'La retirada de la foto conserva su antecedente';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER foto_original_inmutable BEFORE UPDATE ON foto_propiedad
FOR EACH ROW EXECUTE FUNCTION preservar_original_foto();
