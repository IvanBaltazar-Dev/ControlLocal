-- Aprobacion titular03/11/2026. Venta manual Web; Culqi Posterior. Sin caducidad comercial.
-- Fragmento para integrar por Root en la NUEVA V152. No aplicado ni ejecutado.
-- Autoridad: APROBACION.md y CANAL-OFICIAL.md del 03/11/2026; CAT-R25/E38.
-- Misma atribución: evaluación existente XOR colección explícita de compras manuales.
ALTER TABLE b0_atribucion_plataforma
 ADD COLUMN recurso_compra varchar(40),
 ALTER COLUMN plan DROP NOT NULL,
 ALTER COLUMN version_plan DROP NOT NULL;

-- El CHECK financiero de V149 carece de nombre explícito. Localizarlo por sus
-- columnas dependientes; no adivinar el nombre generado ni retirar otros controles.
DO $$
DECLARE regla record; cantidad integer;
BEGIN
 SELECT count(*) INTO cantidad FROM pg_constraint c
 WHERE c.conrelid='b0_atribucion_plataforma'::regclass AND c.contype='c'
  AND c.conkey @> ARRAY[
   (SELECT a.attnum FROM pg_attribute a WHERE a.attrelid=c.conrelid AND a.attname='atribucion'),
   (SELECT a.attnum FROM pg_attribute a WHERE a.attrelid=c.conrelid AND a.attname='fondo')
  ]::smallint[];
 IF cantidad<>1 THEN RAISE EXCEPTION 'No se localizó el único control financiero esperado de atribuciones'; END IF;
 FOR regla IN SELECT c.conname FROM pg_constraint c
  WHERE c.conrelid='b0_atribucion_plataforma'::regclass AND c.contype='c'
   AND c.conkey @> ARRAY[
    (SELECT a.attnum FROM pg_attribute a WHERE a.attrelid=c.conrelid AND a.attname='atribucion'),
    (SELECT a.attnum FROM pg_attribute a WHERE a.attrelid=c.conrelid AND a.attname='fondo')
   ]::smallint[]
 LOOP
  EXECUTE format('ALTER TABLE b0_atribucion_plataforma DROP CONSTRAINT %I',regla.conname);
 END LOOP;
END;
$$;

ALTER TABLE b0_atribucion_plataforma
 ADD CONSTRAINT ck_b0_atribucion_recurso_compra CHECK(
  recurso_compra IS NULL OR recurso_compra='COMPRAS_MANUALES_WEB_MVP'),
 ADD CONSTRAINT ck_b0_atribucion_ambito CHECK(
  (recurso_compra IS NULL AND plan IS NOT NULL AND version_plan IS NOT NULL
   AND (atribucion='FINANCIERO_PLATFORM')=(fondo IS NOT NULL))
  OR
  (recurso_compra IS NOT NULL AND recurso_compra='COMPRAS_MANUALES_WEB_MVP'
   AND atribucion='FINANCIERO_PLATFORM' AND plan IS NULL AND version_plan IS NULL
   AND caso IS NULL AND fondo IS NULL AND finalidad='GESTION_COMPRAS_MANUALES_BROX')),
 ADD CONSTRAINT ck_b0_atribucion_acciones_compra CHECK(
  recurso_compra IS NULL OR
  (acciones <@ '["CONSULTAR_COMPRA","INICIAR_VERIFICACION","VERIFICAR_FONDOS","REGISTRAR_INCIDENCIA"]'::jsonb
   AND campos <@ '["ESTADO","PEDIDO_COMERCIAL","DATOS_PAGO_VERIFICADO"]'::jsonb)),
 ADD CONSTRAINT uq_b0_atribucion_compra UNIQUE(id,cuenta,recurso_compra);

CREATE INDEX ix_b0_atribucion_compras_cuenta ON b0_atribucion_plataforma(cuenta,hasta,id)
 WHERE recurso_compra='COMPRAS_MANUALES_WEB_MVP' AND revocada_en IS NULL;

CREATE OR REPLACE FUNCTION conservar_atribucion_plataforma_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La atribución conserva su antecedente';END IF;
 IF ROW(NEW.id,NEW.otorgante,NEW.cuenta,NEW.atribucion,NEW.plan,NEW.version_plan,NEW.caso,NEW.fondo,
  NEW.finalidad,NEW.acciones,NEW.campos,NEW.desde,NEW.hasta,NEW.evidencia,NEW.huella,NEW.decision,NEW.registrada_en,NEW.recurso_compra)
 IS DISTINCT FROM ROW(OLD.id,OLD.otorgante,OLD.cuenta,OLD.atribucion,OLD.plan,OLD.version_plan,OLD.caso,OLD.fondo,
  OLD.finalidad,OLD.acciones,OLD.campos,OLD.desde,OLD.hasta,OLD.evidencia,OLD.huella,OLD.decision,OLD.registrada_en,OLD.recurso_compra)
 OR OLD.revocada_en IS NOT NULL OR NEW.revocada_en IS NULL
 THEN RAISE EXCEPTION 'Sólo se añade la revocación acreditada';END IF;
 RETURN NEW;
END;$$;

-- Fragmento propietario Root, integrar con extensión R25 en V152; no aplicar separado.
CREATE TABLE b0_compra_manual (
    id_compra uuid PRIMARY KEY,
    organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
    creada_por bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
    solicitud_huella varchar(64) NOT NULL CHECK(solicitud_huella ~ '^[0-9a-f]{64}$'),
    canal text NOT NULL DEFAULT 'MANUAL_WHATSAPP_WEB_MVP' CHECK(canal='MANUAL_WHATSAPP_WEB_MVP'),
    terminos jsonb NOT NULL CHECK(jsonb_typeof(terminos)='object'),
    creada_en timestamptz NOT NULL,
    consentida_por bigint REFERENCES cuenta_acceso(id_cuenta_acceso),
    consentida_en timestamptz,
    huella_consentimiento varchar(64),
    estado text NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN('PENDIENTE','EN_VERIFICACION','ACREDITADA','CON_INCIDENCIA')),
    version bigint NOT NULL DEFAULT 1 CHECK(version>0),
    pago_informado jsonb CHECK(jsonb_typeof(pago_informado)='object'),
    fondos jsonb CHECK(jsonb_typeof(fondos)='object'),
    incidencia text,
    CONSTRAINT b0_compra_manual_consentimiento_completo CHECK((consentida_por IS NULL AND consentida_en IS NULL AND huella_consentimiento IS NULL)
       OR(consentida_por IS NOT NULL AND consentida_en IS NOT NULL AND consentida_en>=creada_en AND huella_consentimiento IS NOT NULL AND huella_consentimiento ~ '^[0-9a-f]{64}$')),
    CHECK(fondos IS NULL OR consentida_en IS NOT NULL),
    CHECK(estado<>'ACREDITADA' OR fondos IS NOT NULL),
    CONSTRAINT b0_compra_manual_incidencia_explicita CHECK(estado<>'CON_INCIDENCIA' OR (incidencia IS NOT NULL AND length(incidencia)>0))
);
CREATE INDEX b0_compra_manual_organizacion ON b0_compra_manual(organizacion_id,creada_en DESC,id_compra);

CREATE TABLE b0_pago_manual_identidad (
    origen text NOT NULL CHECK(length(origen) BETWEEN 1 AND 80 AND origen=upper(btrim(origen))),
    referencia text NOT NULL CHECK(length(referencia) BETWEEN 1 AND 200 AND referencia=upper(btrim(referencia))),
    id_compra uuid NOT NULL UNIQUE REFERENCES b0_compra_manual(id_compra),
    PRIMARY KEY(origen,referencia)
);
CREATE TABLE b0_compra_manual_evento (
    id_compra uuid NOT NULL REFERENCES b0_compra_manual(id_compra),
    id_comando uuid NOT NULL,
    cuenta bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
    concesion uuid,
    recurso_compra text GENERATED ALWAYS AS (CASE WHEN concesion IS NULL THEN NULL ELSE 'COMPRAS_MANUALES_WEB_MVP' END) STORED,
    tipo text NOT NULL CHECK(tipo IN('PREPARADA','CONSENTIDA','VERIFICACION_INICIADA','FONDOS_VERIFICADOS','ACREDITADA','INCIDENCIA','ACREDITACION_PENDIENTE')),
    huella varchar(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'),
    contenido jsonb NOT NULL CHECK(jsonb_typeof(contenido)='object'),
    version_compra bigint NOT NULL CHECK(version_compra>0),
    estado text NOT NULL CHECK(estado IN('PENDIENTE','EN_VERIFICACION','ACREDITADA','CON_INCIDENCIA')),
    registrada_en timestamptz NOT NULL,
    PRIMARY KEY(id_compra,id_comando),
    UNIQUE(id_compra,version_compra),
    CHECK((tipo IN('PREPARADA','CONSENTIDA'))=(concesion IS NULL)),
    FOREIGN KEY(concesion,cuenta,recurso_compra) REFERENCES b0_atribucion_plataforma(id,cuenta,recurso_compra)
);

CREATE FUNCTION proteger_compra_manual_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Se conserva la compra y su historia' USING ERRCODE='23514',CONSTRAINT='b0_compra_manual_conservacion'; END IF;
    IF (NEW.id_compra,NEW.organizacion_id,NEW.creada_por,NEW.solicitud_huella,NEW.canal,NEW.terminos,NEW.creada_en)
            IS DISTINCT FROM (OLD.id_compra,OLD.organizacion_id,OLD.creada_por,OLD.solicitud_huella,OLD.canal,OLD.terminos,OLD.creada_en)
        OR OLD.consentida_en IS NOT NULL AND (NEW.consentida_por,NEW.consentida_en,NEW.huella_consentimiento) IS DISTINCT FROM (OLD.consentida_por,OLD.consentida_en,OLD.huella_consentimiento)
        OR OLD.fondos IS NOT NULL AND NEW.fondos IS DISTINCT FROM OLD.fondos
        OR OLD.estado='ACREDITADA' AND NEW IS DISTINCT FROM OLD
        OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Términos, consentimiento, fondos e historia no se sobrescriben' USING ERRCODE='23514',CONSTRAINT='b0_compra_manual_inmutabilidad';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER b0_compra_manual_conservar BEFORE UPDATE OR DELETE ON b0_compra_manual FOR EACH ROW EXECUTE FUNCTION proteger_compra_manual_b0();
CREATE FUNCTION conservar_hecho_compra_manual_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Se conserva el hecho de compra manual' USING ERRCODE='23514',CONSTRAINT='b0_compra_manual_hecho_inmutable';
END $$;
CREATE TRIGGER b0_pago_manual_identidad_conservar BEFORE UPDATE OR DELETE ON b0_pago_manual_identidad FOR EACH ROW EXECUTE FUNCTION conservar_hecho_compra_manual_b0();
CREATE TRIGGER b0_compra_manual_evento_conservar BEFORE UPDATE OR DELETE ON b0_compra_manual_evento FOR EACH ROW EXECUTE FUNCTION conservar_hecho_compra_manual_b0();
CREATE TRIGGER b0_compra_manual_no_truncar BEFORE TRUNCATE ON b0_compra_manual FOR EACH STATEMENT EXECUTE FUNCTION conservar_hecho_compra_manual_b0();
CREATE TRIGGER b0_pago_manual_no_truncar BEFORE TRUNCATE ON b0_pago_manual_identidad FOR EACH STATEMENT EXECUTE FUNCTION conservar_hecho_compra_manual_b0();
CREATE TRIGGER b0_compra_manual_evento_no_truncar BEFORE TRUNCATE ON b0_compra_manual_evento FOR EACH STATEMENT EXECUTE FUNCTION conservar_hecho_compra_manual_b0();
