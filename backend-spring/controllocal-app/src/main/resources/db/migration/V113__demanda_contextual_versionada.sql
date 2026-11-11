-- RF003/RF028-030, CAT-E18, D1: mismo agregado, versiones y criterios conservados.
-- V1-112 aplicadas no se modifican. No existen clientes reales que migrar.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM requerimiento_cliente WHERE estado IN ('P','C')) THEN
        RAISE EXCEPTION 'V113: reconciliar el fixture P/C declarado antes de cambiar a A/R/F; no inferir tipo, fecha o motivo de fin';
    END IF;
END $$;

ALTER TABLE requerimiento_cliente
    DROP CONSTRAINT ck_requerimiento_estado,
    ALTER COLUMN estado DROP DEFAULT,
    ALTER COLUMN id_rol_cliente DROP NOT NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN id_rol_responsable BIGINT,
    ADD COLUMN id_persona_interesada BIGINT,
    ADD COLUMN interesado_contexto_json TEXT,
    ADD COLUMN finalidad TEXT,
    ADD COLUMN vigente_desde DATE,
    ADD COLUMN procedencia_json TEXT,
    ADD COLUMN concepto_presupuesto TEXT,
    ADD COLUMN periodo_presupuesto TEXT,
    ADD COLUMN fin_tipo VARCHAR(1),
    ADD COLUMN fin_fecha DATE,
    ADD COLUMN fin_motivo TEXT,
    ADD COLUMN fin_registrado_en TIMESTAMPTZ,
    ADD CONSTRAINT ck_requerimiento_estado CHECK (estado IN ('A','R','F')),
    ADD CONSTRAINT ck_demanda_version CHECK (version>=0),
    ADD CONSTRAINT ck_demanda_vigencia CHECK (vigente_desde IS NULL OR vigente_hasta IS NULL OR vigente_hasta>=vigente_desde),
    ADD CONSTRAINT ck_demanda_contexto CHECK (interesado_contexto_json IS NULL OR jsonb_typeof(interesado_contexto_json::jsonb)='object'),
    ADD CONSTRAINT ck_demanda_procedencia CHECK (procedencia_json IS NULL OR jsonb_typeof(procedencia_json::jsonb)='object'),
    ADD CONSTRAINT ck_demanda_fin CHECK (
        (estado='A' AND fin_tipo IS NULL AND fin_fecha IS NULL AND fin_motivo IS NULL AND fin_registrado_en IS NULL)
        OR (estado IN ('R','F') AND fin_tipo IS NOT NULL AND fin_tipo=estado AND fin_fecha IS NOT NULL
            AND fin_motivo IS NOT NULL AND length(btrim(fin_motivo))>0
            AND fin_motivo ~ '[^[:space:]]' AND fin_registrado_en IS NOT NULL)),
    ADD CONSTRAINT fk_demanda_responsable_org FOREIGN KEY(organizacion_id,id_rol_responsable)
        REFERENCES persona_rol(organizacion_id,id_persona_rol),
    ADD CONSTRAINT fk_demanda_persona_org FOREIGN KEY(organizacion_id,id_persona_interesada)
        REFERENCES persona(organizacion_id,id_persona);

-- Única conciliación de identidad: la FK explícita de CLIENTE ya existente.
UPDATE requerimiento_cliente r SET id_persona_interesada=pr.id_persona
  FROM persona_rol pr WHERE pr.id_persona_rol=r.id_rol_cliente AND pr.organizacion_id=r.organizacion_id;
-- No se rellena responsable/procedencia/fin ni se atribuye autoría comercial.
CREATE INDEX ix_demanda_responsable ON requerimiento_cliente(organizacion_id,id_rol_responsable,id_requerimiento);
CREATE INDEX ix_demanda_persona ON requerimiento_cliente(organizacion_id,id_persona_interesada,id_requerimiento);

CREATE TABLE requerimiento_version (
    organizacion_id BIGINT NOT NULL,
    id_requerimiento BIGINT NOT NULL,
    version BIGINT NOT NULL CHECK(version>=0),
    id_rol_actor BIGINT,
    registrado_en TIMESTAMPTZ NOT NULL,
    snapshot_json TEXT NOT NULL CHECK(jsonb_typeof(snapshot_json::jsonb)='object'),
    procedencia_json TEXT CHECK(procedencia_json IS NULL OR jsonb_typeof(procedencia_json::jsonb)='object'),
    PRIMARY KEY(organizacion_id,id_requerimiento,version),
    CONSTRAINT fk_demanda_version_padre FOREIGN KEY(organizacion_id,id_requerimiento)
        REFERENCES requerimiento_cliente(organizacion_id,id_requerimiento),
    CONSTRAINT fk_demanda_version_actor FOREIGN KEY(organizacion_id,id_rol_actor)
        REFERENCES persona_rol(organizacion_id,id_persona_rol)
);
CREATE INDEX ix_demanda_version_corte ON requerimiento_version(organizacion_id,id_requerimiento,registrado_en,version);

-- Baseline técnico explícito para las FK históricas, no Ficha comercial ni acto
-- humano. Actor/procedencia NULL; ownerNULL impide lectura/edición de producto.
-- Los campos de la fila y sus criterios/valores originales permanecen intactos.
INSERT INTO requerimiento_version(organizacion_id,id_requerimiento,version,id_rol_actor,registrado_en,snapshot_json,procedencia_json)
SELECT r.organizacion_id,r.id_requerimiento,0,NULL,clock_timestamp(),
    jsonb_build_object('antecedenteTecnico',true,'datosOriginales',to_jsonb(r))::text,NULL
FROM requerimiento_cliente r;

ALTER TABLE requerimiento_criterio
    ADD COLUMN version_demanda BIGINT NOT NULL DEFAULT 0 CHECK(version_demanda>=0),
    ADD COLUMN criterio_json TEXT CHECK(criterio_json IS NULL OR jsonb_typeof(criterio_json::jsonb)='object'),
    ADD COLUMN rotulo TEXT,
    ADD COLUMN unidad TEXT,
    ADD COLUMN tipo_dato VARCHAR(30),
    ALTER COLUMN operador DROP NOT NULL,
    ALTER COLUMN peso DROP NOT NULL,
    DROP CONSTRAINT uq_criterio_clave,
    ADD CONSTRAINT uq_criterio_version_clave UNIQUE(organizacion_id,id_requerimiento,version_demanda,clave),
    ADD CONSTRAINT fk_criterio_version_demanda FOREIGN KEY(organizacion_id,id_requerimiento,version_demanda)
        REFERENCES requerimiento_version(organizacion_id,id_requerimiento,version) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT ck_criterio_canonico_exclusivo CHECK (criterio_json IS NULL OR
        (operador IS NULL AND peso IS NULL AND valor_texto IS NULL AND valor_numero IS NULL
        AND valor_numero_hasta IS NULL AND valor_moneda IS NULL AND valor_booleano IS NULL
        AND valor_fecha IS NULL AND valor_fecha_hasta IS NULL));
CREATE INDEX ix_criterio_version ON requerimiento_criterio(organizacion_id,id_requerimiento,version_demanda);

-- Preservar literalmente las expresiones V95 para antecedentes escalares. La rama
-- JSON tiene su propia validación completa; no elimina controles del formato previo.
DO $$ DECLARE n TEXT; e TEXT; BEGIN
    FOREACH n IN ARRAY ARRAY['ck_criterio_operador','ck_criterio_peso','ck_criterio_moneda','ck_criterio_rango','ck_criterio_forma'] LOOP
        SELECT pg_get_expr(conbin,conrelid) INTO STRICT e FROM pg_constraint
          WHERE conrelid='requerimiento_criterio'::regclass AND conname=n;
        EXECUTE format('ALTER TABLE requerimiento_criterio DROP CONSTRAINT %I',n);
        EXECUTE format('ALTER TABLE requerimiento_criterio ADD CONSTRAINT %I CHECK ((criterio_json IS NULL AND (%s)) OR criterio_json IS NOT NULL)',n,e);
    END LOOP;
END $$;

CREATE FUNCTION exigir_demanda_contextual() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p BIGINT; ctx JSONB; origen requerimiento_cliente%ROWTYPE;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'La demanda conserva su antecedente; no se borra' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.id_rol_responsable IS NULL THEN
            RAISE EXCEPTION 'El antecedente técnico sin responsable no concede autoridad ni se acredita por backfill' USING ERRCODE='23514';
        END IF;
        IF NEW.id_requerimiento<>OLD.id_requerimiento OR NEW.organizacion_id<>OLD.organizacion_id
            OR NEW.id_rol_responsable IS DISTINCT FROM OLD.id_rol_responsable
            OR NEW.version<>OLD.version+1 OR OLD.estado<>'A' THEN
            RAISE EXCEPTION 'Demanda exige propietario conservado, CAS y estado abierto; no reasigna ni reabre' USING ERRCODE='23514';
        END IF;
        IF NEW.renta_min IS DISTINCT FROM OLD.renta_min OR NEW.renta_max IS DISTINCT FROM OLD.renta_max
            OR NEW.frente_minimo IS DISTINCT FROM OLD.frente_minimo OR NEW.tipo_inmueble IS DISTINCT FROM OLD.tipo_inmueble THEN
            RAISE EXCEPTION 'Las columnas anteriores son sólo antecedente, no otro formato de escritura' USING ERRCODE='23514'; END IF;
    ELSE
        IF NEW.version<>0 OR NEW.estado<>'A' THEN RAISE EXCEPTION 'El alta de demanda comienza ABIERTA en versión0' USING ERRCODE='23514'; END IF;
        IF NEW.renta_min IS NOT NULL OR NEW.renta_max IS NOT NULL OR NEW.frente_minimo IS NOT NULL OR NEW.tipo_inmueble IS NOT NULL THEN
            RAISE EXCEPTION 'El alta nueva escribe presupuesto/tipos/criterios canónicos, no columnas anteriores' USING ERRCODE='23514'; END IF;
    END IF;
    IF NEW.id_rol_responsable IS NULL OR NEW.id_persona_interesada IS NULL
        OR NEW.interesado_contexto_json IS NULL OR NEW.procedencia_json IS NULL THEN
        RAISE EXCEPTION 'La nueva versión requiere responsable actual, interesado contextual y procedencia explícitos' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM persona_rol pr JOIN persona pe ON pe.id_persona=pr.id_persona AND pe.organizacion_id=pr.organizacion_id
        JOIN usuario_organizacion u ON u.id_persona=pe.id_persona AND u.organizacion_id=pr.organizacion_id
        JOIN cuenta_acceso ca ON ca.id_cuenta_acceso=u.id_usuario
        JOIN organizacion o ON o.id_organizacion=pr.organizacion_id
        WHERE pr.id_persona_rol=NEW.id_rol_responsable AND pr.organizacion_id=NEW.organizacion_id
          AND pr.tipo_rol='AGENTE' AND pr.vigencia_hasta IS NULL
          AND pr.vigencia_desde<=(clock_timestamp() AT TIME ZONE 'America/Lima')::date
          AND pe.estado='A' AND u.estado='A' AND u.rol='AGENTE' AND ca.estado='A'
          AND ca.correo_confirmado_en IS NOT NULL AND o.estado='A') THEN
        RAISE EXCEPTION 'El responsable no acredita una pertenencia AGENTE actual de este tenant' USING ERRCODE='23514';
    END IF;
    ctx:=NEW.interesado_contexto_json::jsonb;
    -- Un origen conservado no se vuelve a declarar ni rejuvenece su versión/fuente.
    -- La Persona puede adquirir después otro rol legítimo sin alterar aquel hecho.
    IF TG_OP='UPDATE' AND NEW.interesado_contexto_json IS NOT DISTINCT FROM OLD.interesado_contexto_json
        AND NEW.id_persona_interesada IS NOT DISTINCT FROM OLD.id_persona_interesada
        AND NEW.id_rol_cliente IS NOT DISTINCT FROM OLD.id_rol_cliente THEN
        NULL;
    ELSIF ctx->>'tipo'='CLIENTE' THEN
        SELECT pr.id_persona INTO p FROM persona_rol pr JOIN detalle_cliente dc ON dc.id_persona_rol=pr.id_persona_rol
          AND dc.organizacion_id=pr.organizacion_id WHERE pr.id_persona_rol=NEW.id_rol_cliente AND pr.organizacion_id=NEW.organizacion_id AND pr.tipo_rol='CLIENTE';
        IF p IS DISTINCT FROM NEW.id_persona_interesada OR (ctx->>'idCliente')::bigint IS DISTINCT FROM NEW.id_rol_cliente THEN
            RAISE EXCEPTION 'CLIENTE debe conservar su referencia explícita de rol a Persona' USING ERRCODE='23514'; END IF;
    ELSIF ctx->>'tipo'='CONTACTO_NUEVO' THEN
        IF NEW.id_rol_cliente IS NOT NULL OR (ctx->>'idPersona')::bigint IS DISTINCT FROM NEW.id_persona_interesada
            OR (ctx->>'declaradoPor')::bigint IS DISTINCT FROM NEW.id_rol_responsable
            OR EXISTS(SELECT 1 FROM persona_rol pr WHERE pr.organizacion_id=NEW.organizacion_id AND pr.id_persona=NEW.id_persona_interesada) THEN
            RAISE EXCEPTION 'El contacto contextual no fabrica un rol CLIENTE ni reutiliza una identidad desnuda' USING ERRCODE='23514'; END IF;
    ELSIF ctx->>'tipo'='DEMANDA_ORIGEN' THEN
        SELECT * INTO origen FROM requerimiento_cliente r WHERE r.organizacion_id=NEW.organizacion_id AND r.id_requerimiento=(ctx->>'demandaOrigen')::bigint;
        IF NOT FOUND OR NEW.id_rol_cliente IS NOT NULL OR origen.id_rol_responsable IS DISTINCT FROM NEW.id_rol_responsable
            OR origen.id_persona_interesada IS DISTINCT FROM NEW.id_persona_interesada
            OR origen.version IS DISTINCT FROM (ctx->>'versionOrigen')::bigint THEN
            RAISE EXCEPTION 'Persona existente requiere relación de origen propia y versión legítima' USING ERRCODE='23514'; END IF;
    ELSE RAISE EXCEPTION 'No se admite PersonaId sin relación contextual legítima' USING ERRCODE='23514'; END IF;
    IF NEW.estado IN ('R','F') AND NEW.fin_fecha>(clock_timestamp() AT TIME ZONE 'America/Lima')::date THEN
        RAISE EXCEPTION 'RF030 registra un hecho ya efectivo, no programa un fin futuro' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_demanda_contextual BEFORE INSERT OR UPDATE OR DELETE ON requerimiento_cliente
    FOR EACH ROW EXECUTE FUNCTION exigir_demanda_contextual();

-- Mismo guard para el snapshot al insertarlo y al verificar su TX: no se puede
-- inventar un pasado y luego avanzar el padre para eludir la comparación diferida.
CREATE FUNCTION snapshot_demanda_conserva_fila(j JSONB,r requerimiento_cliente) RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT coalesce(jsonb_typeof(j)='object'
        AND (j->>'id')::bigint IS NOT DISTINCT FROM (r).id_requerimiento
        AND (j->>'version')::bigint IS NOT DISTINCT FROM (r).version
        AND (j->>'idResponsable')::bigint IS NOT DISTINCT FROM (r).id_rol_responsable
        AND (j->>'idPersonaInteresada')::bigint IS NOT DISTINCT FROM (r).id_persona_interesada
        AND (j->>'idCliente')::bigint IS NOT DISTINCT FROM (r).id_rol_cliente
        AND j->>'estado' IS NOT DISTINCT FROM (r).estado
        AND j->>'operacion' IS NOT DISTINCT FROM CASE (r).operacion WHEN 'A' THEN 'ALQUILER' WHEN 'V' THEN 'VENTA' END
        AND j->>'rubro' IS NOT DISTINCT FROM (r).rubro AND j->>'finalidad' IS NOT DISTINCT FROM (r).finalidad
        AND j->>'observaciones' IS NOT DISTINCT FROM (r).observaciones
        AND j->>'moneda' IS NOT DISTINCT FROM (r).moneda
        AND j->>'conceptoPresupuesto' IS NOT DISTINCT FROM (r).concepto_presupuesto
        AND j->>'periodoPresupuesto' IS NOT DISTINCT FROM (r).periodo_presupuesto
        AND coalesce(j->'presupuestoMin','null'::jsonb) IS NOT DISTINCT FROM coalesce(to_jsonb((r).presupuesto_min),'null'::jsonb)
        AND coalesce(j->'presupuestoMax','null'::jsonb) IS NOT DISTINCT FROM coalesce(to_jsonb((r).presupuesto_max),'null'::jsonb)
        AND coalesce(j->'metrajeMin','null'::jsonb) IS NOT DISTINCT FROM coalesce(to_jsonb((r).metraje_min),'null'::jsonb)
        AND coalesce(j->'metrajeMax','null'::jsonb) IS NOT DISTINCT FROM coalesce(to_jsonb((r).metraje_max),'null'::jsonb)
        AND (j->>'vigenteDesde')::date IS NOT DISTINCT FROM (r).vigente_desde
        AND (j->>'vigenteHasta')::date IS NOT DISTINCT FROM (r).vigente_hasta
        AND j->'contextoInteresado' IS NOT DISTINCT FROM (r).interesado_contexto_json::jsonb
        AND CASE WHEN (r).fin_tipo IS NULL THEN coalesce(j->'fin','null'::jsonb)='null'::jsonb ELSE
            jsonb_typeof(j->'fin')='object' AND j#>>'{fin,tipo}' IS NOT DISTINCT FROM (r).fin_tipo
            AND (j#>>'{fin,fechaDelHecho}')::date IS NOT DISTINCT FROM (r).fin_fecha
            AND j#>>'{fin,motivo}' IS NOT DISTINCT FROM (r).fin_motivo
            AND (j#>>'{fin,registradoEn}')::timestamptz IS NOT DISTINCT FROM (r).fin_registrado_en END,false)
$$;

CREATE FUNCTION exigir_nueva_version_demanda() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE r requerimiento_cliente%ROWTYPE;
BEGIN
    SELECT * INTO r FROM requerimiento_cliente WHERE organizacion_id=NEW.organizacion_id AND id_requerimiento=NEW.id_requerimiento;
    IF NOT FOUND OR r.id_rol_responsable IS NULL OR NEW.version<>r.version
        OR NEW.id_rol_actor IS DISTINCT FROM r.id_rol_responsable
        OR NEW.procedencia_json IS DISTINCT FROM r.procedencia_json
        OR NOT snapshot_demanda_conserva_fila(NEW.snapshot_json::jsonb,r) THEN
        RAISE EXCEPTION 'El snapshot nuevo corresponde sólo a la versión actual y actor/procedencia reales' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_nueva_version_demanda BEFORE INSERT ON requerimiento_version
    FOR EACH ROW EXECUTE FUNCTION exigir_nueva_version_demanda();

CREATE FUNCTION forma_valor_criterio_demanda(v JSONB,tipo TEXT) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
    SELECT coalesce(jsonb_typeof(v)='object'
        AND (SELECT count(*) FROM jsonb_each(CASE WHEN jsonb_typeof(v)='object' THEN v ELSE '{}'::jsonb END) x
            WHERE x.key IN ('texto','numero','booleano','fecha','valores') AND x.value<>'null'::jsonb)=1
        AND CASE tipo
            WHEN 'TEXTO' THEN jsonb_typeof(v->'texto')='string' AND length(btrim(v->>'texto'))>0
            WHEN 'LISTA' THEN jsonb_typeof(v->'texto')='string' OR jsonb_typeof(v->'valores')='array'
            WHEN 'LISTA_MULTIPLE' THEN jsonb_typeof(v->'valores')='array'
            WHEN 'BOOLEANO' THEN jsonb_typeof(v->'booleano')='boolean'
            WHEN 'FECHA' THEN jsonb_typeof(v->'fecha')='string'
            WHEN 'ENTERO' THEN jsonb_typeof(v->'numero')='number' AND (v->>'numero')::numeric=trunc((v->>'numero')::numeric)
            WHEN 'DECIMAL' THEN jsonb_typeof(v->'numero')='number'
            WHEN 'IMPORTE' THEN jsonb_typeof(v->'numero')='number' AND v->>'moneda' IN ('PEN','USD')
            ELSE false END,false)
$$;

CREATE FUNCTION estructura_valor_criterio_demanda(v JSONB) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
    SELECT v IS NULL OR v='null'::jsonb OR coalesce(jsonb_typeof(v)='object'
        AND NOT EXISTS(SELECT 1 FROM jsonb_each(CASE WHEN jsonb_typeof(v)='object' THEN v ELSE '{}'::jsonb END) x
            WHERE x.key NOT IN ('texto','numero','booleano','fecha','moneda','valores')
                OR x.value<>'null'::jsonb AND jsonb_typeof(x.value)<>CASE x.key WHEN 'numero' THEN 'number' WHEN 'booleano' THEN 'boolean' WHEN 'valores' THEN 'array' ELSE 'string' END)
        AND NOT EXISTS(SELECT 1 FROM jsonb_array_elements(CASE WHEN jsonb_typeof(v->'valores')='array' THEN v->'valores' ELSE '[]'::jsonb END) x WHERE jsonb_typeof(x)<>'string'),false)
$$;

CREATE FUNCTION exigir_criterio_demanda_canonico() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE r requerimiento_cliente%ROWTYPE; anterior requerimiento_criterio%ROWTYPE;
    j JSONB; a JSONB; m JSONB; v JSONB; h JSONB; contexto JSONB; d catalogo_atributo%ROWTYPE;
    perfil perfil_catalogo_opciones%ROWTYPE;
    estado TEXT; op TEXT; tipo TEXT; selector TEXT; cantidad INT; key TEXT;
BEGIN
    SELECT * INTO r FROM requerimiento_cliente WHERE organizacion_id=NEW.organizacion_id AND id_requerimiento=NEW.id_requerimiento;
    IF NOT FOUND THEN RAISE EXCEPTION 'Demanda no perteneciente al tenant del criterio' USING ERRCODE='23503'; END IF;
    IF r.id_rol_responsable IS NULL OR NEW.version_demanda<>r.version OR NEW.criterio_json IS NULL THEN
        RAISE EXCEPTION 'Criterio nuevo requiere padre autorizado, versión actual y JSON canónico' USING ERRCODE='23514'; END IF;
    -- Copia idéntica de la versión inmediata: conserva definición/fuente/fecha retirada,
    -- no permite restaurar una versión antigua ni actualizar su evidencia.
    SELECT * INTO anterior FROM requerimiento_criterio c WHERE c.organizacion_id=NEW.organizacion_id
        AND c.id_requerimiento=NEW.id_requerimiento AND c.version_demanda=NEW.version_demanda-1 AND c.clave=NEW.clave;
    IF FOUND AND anterior.criterio_json IS NOT NULL AND anterior.criterio_json=NEW.criterio_json
        AND anterior.rotulo IS NOT DISTINCT FROM NEW.rotulo AND anterior.unidad IS NOT DISTINCT FROM NEW.unidad
        AND anterior.tipo_dato IS NOT DISTINCT FROM NEW.tipo_dato THEN RETURN NEW; END IF;
    j:=NEW.criterio_json::jsonb;
    IF EXISTS(SELECT 1 FROM jsonb_object_keys(j) x WHERE x NOT IN ('referencia','clave','exigencia','prioridad','alternativas')) THEN
        RAISE EXCEPTION 'El objeto criterio no admite campos fuera del contrato compartido' USING ERRCODE='23514'; END IF;
    IF j->>'clave' IS DISTINCT FROM NEW.clave OR coalesce(length(btrim(j->>'referencia')),0)=0
        OR coalesce(j->>'exigencia','') NOT IN ('OBLIGATORIO','PREFERENCIA')
        OR (j->>'exigencia'='OBLIGATORIO' AND j->>'prioridad' IS NOT NULL)
        OR (j->>'exigencia'='PREFERENCIA' AND coalesce(j->>'prioridad','') NOT IN ('IMPORTANTE','NORMAL','COMPLEMENTARIA'))
        OR jsonb_typeof(j->'alternativas') IS DISTINCT FROM 'array' OR jsonb_array_length(j->'alternativas')=0 THEN
        RAISE EXCEPTION 'Criterio lógico incompleto: clave, exigencia, prioridad y alternativas explícitas' USING ERRCODE='23514'; END IF;
    FOR a IN SELECT value FROM jsonb_array_elements(j->'alternativas') LOOP
        m:=a->'metadatos';v:=a->'valor';h:=a->'hasta';contexto:=a->'contexto';
        IF jsonb_typeof(a) IS DISTINCT FROM 'object' OR jsonb_typeof(m) IS DISTINCT FROM 'object'
            OR jsonb_typeof(contexto) IS DISTINCT FROM 'object' OR jsonb_typeof(a->'negada') IS DISTINCT FROM 'boolean' THEN
            RAISE EXCEPTION 'Alternativa tipada con contexto y metadatos explícitos requerida' USING ERRCODE='23514'; END IF;
        IF EXISTS(SELECT 1 FROM jsonb_object_keys(a) x WHERE x NOT IN ('claveDato','tipo','operador','valor','hasta','negada','contexto','metadatos'))
            OR EXISTS(SELECT 1 FROM jsonb_object_keys(m) x WHERE x NOT IN ('estado','causa','referenciaDefinicion','versionDefinicion','evidenciaRef','fuente','observadoEn','vigenteDesde','vigenteHasta'))
            OR EXISTS(SELECT 1 FROM jsonb_each(contexto) x WHERE x.key NOT IN ('concepto','unidad','moneda','periodo','operacion','configuracion','jurisdiccion','referenciaCatalogo','versionFuente') OR x.value<>'null'::jsonb AND jsonb_typeof(x.value)<>'string')
            OR EXISTS(SELECT 1 FROM jsonb_each(m) x WHERE x.key IN ('evidenciaRef','fuente','observadoEn','vigenteDesde','vigenteHasta') AND x.value<>'null'::jsonb AND jsonb_typeof(x.value)<>'string') THEN
            RAISE EXCEPTION 'Contexto/metadatos deben respetar los tipos y campos del contrato' USING ERRCODE='23514'; END IF;
        estado:=m->>'estado';op:=a->>'operador';tipo:=a->>'tipo';selector:=a->>'claveDato';
        IF NOT estructura_valor_criterio_demanda(v) OR NOT estructura_valor_criterio_demanda(h) THEN
            RAISE EXCEPTION 'Los operandos son objetos tipados o ausentes, nunca una cadena/0 sustituto' USING ERRCODE='23514'; END IF;
        IF v->>'fecha' IS NOT NULL THEN PERFORM (v->>'fecha')::date; END IF;
        IF h->>'fecha' IS NOT NULL THEN PERFORM (h->>'fecha')::date; END IF;
        IF m->>'observadoEn' IS NOT NULL THEN PERFORM (m->>'observadoEn')::date; END IF;
        IF m->>'vigenteDesde' IS NOT NULL THEN PERFORM (m->>'vigenteDesde')::timestamptz; END IF;
        IF m->>'vigenteHasta' IS NOT NULL THEN PERFORM (m->>'vigenteHasta')::timestamptz; END IF;
        IF coalesce(estado,'') NOT IN ('CONOCIDO','FALTANTE','AUSENCIA_DECLARADA','NO_APLICABLE')
            OR coalesce(op,'') NOT IN ('IGUAL','MINIMO','MAXIMO','ENTRE','INCLUYE','VERDADERO') THEN
            RAISE EXCEPTION 'Estado u operador de criterio no reconocido; desconocido no se convierte en0/falso' USING ERRCODE='23514'; END IF;
        IF m->>'causa' IS NOT NULL AND m->>'causa' NOT IN ('DATO_FALTANTE','CONFLICTO','VALOR_INVALIDO','VIGENCIA_NO_ACREDITADA','NO_APLICABLE','AUSENCIA_SIN_VALOR_COMPARABLE','CONCEPTO_INCOMPATIBLE','UNIDAD_INCOMPATIBLE','MONEDA_NO_COMPARABLE','PERIODO_INCOMPATIBLE','CONFIGURACION_INCOMPATIBLE','OPERACION_INCOMPATIBLE','LIMITES_INSUFICIENTES','CRITERIO_NO_INTERPRETABLE','SIN_CONFIRMACION_DISPONIBILIDAD','CONFIRMACION_NO_VIGENTE','CONTEXTO_TERRITORIAL_INCOMPATIBLE') THEN
            RAISE EXCEPTION 'Causa del metadato no reconocida' USING ERRCODE='23514'; END IF;
        SELECT * INTO d FROM catalogo_atributo c WHERE c.clave=NEW.clave AND c.activo
            AND (c.organizacion_id IS NULL OR c.organizacion_id=NEW.organizacion_id)
            AND selector=c.sujeto||':'||c.clave ORDER BY c.organizacion_id DESC NULLS LAST LIMIT 1 FOR SHARE;
        IF NOT FOUND OR tipo IS DISTINCT FROM d.tipo_dato OR NEW.tipo_dato IS DISTINCT FROM d.tipo_dato
            OR NEW.rotulo IS DISTINCT FROM d.rotulo OR NEW.unidad IS DISTINCT FROM d.unidad
            OR m->>'referenciaDefinicion' IS DISTINCT FROM 'CAT:D:'||d.id_catalogo_atributo||':'||d.version_definicion
            OR jsonb_typeof(m->'versionDefinicion') IS DISTINCT FROM 'number'
            OR (m->>'versionDefinicion')::numeric<>trunc((m->>'versionDefinicion')::numeric)
            OR (m->>'versionDefinicion')::bigint IS DISTINCT FROM d.version_definicion THEN
            RAISE EXCEPTION 'Definición, tenant, tipo o versión observada del criterio no son actuales' USING ERRCODE='23514'; END IF;
        IF contexto->>'operacion' IS NOT NULL AND contexto->>'operacion'<>(CASE r.operacion WHEN 'A' THEN 'ALQUILER' WHEN 'V' THEN 'VENTA' END) THEN
            RAISE EXCEPTION 'Criterio de otra operación no se mezcla con esta demanda' USING ERRCODE='23514'; END IF;
        IF EXISTS(SELECT 1 FROM requerimiento_tipo_inmueble t WHERE t.id_requerimiento=r.id_requerimiento)
            AND NOT EXISTS(SELECT 1 FROM requerimiento_tipo_inmueble t WHERE t.id_requerimiento=r.id_requerimiento AND (
                d.sujeto='PROPIEDAD' AND EXISTS(SELECT 1 FROM catalogo_atributo_tipo ct WHERE ct.id_catalogo_atributo=d.id_catalogo_atributo AND ct.tipo_propiedad=t.tipo_inmueble)
                OR d.sujeto='ENCARGO' AND EXISTS(SELECT 1 FROM catalogo_atributo_operacion co WHERE co.id_catalogo_atributo=d.id_catalogo_atributo AND co.tipo_propiedad=t.tipo_inmueble AND co.tipo_operacion=r.operacion))) THEN
            RAISE EXCEPTION 'Criterio no aplicable a los tipos/operación declarados' USING ERRCODE='23514'; END IF;
        IF NOT (CASE tipo WHEN 'TEXTO' THEN op IN ('IGUAL','INCLUYE') WHEN 'LISTA' THEN op IN ('IGUAL','INCLUYE')
            WHEN 'LISTA_MULTIPLE' THEN op='INCLUYE' WHEN 'BOOLEANO' THEN op='VERDADERO'
            WHEN 'ENTERO' THEN op IN ('IGUAL','MINIMO','MAXIMO','ENTRE') WHEN 'DECIMAL' THEN op IN ('IGUAL','MINIMO','MAXIMO','ENTRE')
            WHEN 'IMPORTE' THEN op IN ('IGUAL','MINIMO','MAXIMO','ENTRE') WHEN 'FECHA' THEN op IN ('IGUAL','MINIMO','MAXIMO','ENTRE') ELSE false END) THEN
            RAISE EXCEPTION 'El operador no corresponde al tipo de la definición' USING ERRCODE='23514'; END IF;
        IF m->>'vigenteDesde' IS NOT NULL AND m->>'vigenteHasta' IS NOT NULL AND (m->>'vigenteHasta')::timestamptz<(m->>'vigenteDesde')::timestamptz THEN
            RAISE EXCEPTION 'Vigencia de metadatos invertida' USING ERRCODE='23514'; END IF;
        IF estado='CONOCIDO' THEN
            IF jsonb_typeof(v) IS DISTINCT FROM 'object' THEN RAISE EXCEPTION 'CONOCIDO requiere operando, no un0 fabricado' USING ERRCODE='23514'; END IF;
            SELECT count(*) INTO cantidad FROM jsonb_each(v) x WHERE x.key IN ('texto','numero','booleano','fecha','valores') AND x.value<>'null'::jsonb;
            IF cantidad<>1 OR NOT forma_valor_criterio_demanda(v,tipo) THEN
                RAISE EXCEPTION 'El operando conocido no tiene exactamente la forma tipada declarada' USING ERRCODE='23514'; END IF;
            IF tipo='ENTERO' AND (v->>'numero')::numeric<>trunc((v->>'numero')::numeric) THEN RAISE EXCEPTION 'Cantidad entera requerida' USING ERRCODE='23514'; END IF;
            IF tipo='IMPORTE' AND coalesce(v->>'moneda','') NOT IN ('PEN','USD') THEN RAISE EXCEPTION 'Importe conocido requiere moneda explícita' USING ERRCODE='23514'; END IF;
            IF tipo='IMPORTE' AND contexto->>'moneda' IS NOT NULL AND contexto->>'moneda' IS DISTINCT FROM v->>'moneda' THEN RAISE EXCEPTION 'Moneda de contexto y operando discrepan' USING ERRCODE='23514'; END IF;
            IF tipo<>'IMPORTE' AND v->>'moneda' IS NOT NULL THEN RAISE EXCEPTION 'Una cantidad no monetaria no cambia de concepto por una moneda' USING ERRCODE='23514'; END IF;
            IF d.sujeto='PROPIEDAD' AND d.clave='zonificacion' THEN
                IF coalesce(length(btrim(contexto->>'jurisdiccion')),0)=0
                    OR coalesce(length(btrim(contexto->>'referenciaCatalogo')),0)=0
                    OR coalesce(length(btrim(contexto->>'versionFuente')),0)=0 THEN
                    RAISE EXCEPTION 'Zonificación conocida exige contexto territorial acreditado, no un distrito inferido' USING ERRCODE='23514'; END IF;
                SELECT * INTO perfil FROM perfil_catalogo_opciones p
                    WHERE p.organizacion_id=NEW.organizacion_id AND p.id_catalogo_atributo=d.id_catalogo_atributo
                      AND p.jurisdiccion=contexto->>'jurisdiccion' AND p.activo FOR SHARE;
                IF NOT FOUND OR contexto->>'referenciaCatalogo' IS DISTINCT FROM 'CAT:P:'||perfil.id_perfil||':'||perfil.version_definicion
                    OR contexto->>'versionFuente' IS DISTINCT FROM perfil.version_fuente
                    OR coalesce(length(btrim(perfil.fuente_definicion)),0)=0 THEN
                    RAISE EXCEPTION 'Perfil territorial, tenant o versión de fuente no vigentes' USING ERRCODE='23514'; END IF;
                FOR key IN SELECT jsonb_array_elements_text(CASE WHEN jsonb_typeof(v->'valores')='array' THEN v->'valores' ELSE jsonb_build_array(v->>'texto') END) LOOP
                    IF NOT EXISTS(SELECT 1 FROM perfil_catalogo_opcion o WHERE o.id_perfil=perfil.id_perfil AND o.activo AND o.valor=key) THEN
                        RAISE EXCEPTION 'Opción fuera del catálogo territorial cerrado vigente' USING ERRCODE='23514'; END IF;
                END LOOP;
            ELSIF tipo IN ('LISTA','LISTA_MULTIPLE') THEN
                FOR key IN SELECT jsonb_array_elements_text(CASE WHEN jsonb_typeof(v->'valores')='array' THEN v->'valores' ELSE jsonb_build_array(v->>'texto') END) LOOP
                    IF NOT EXISTS(SELECT 1 FROM catalogo_atributo_opcion o WHERE o.id_catalogo_atributo=d.id_catalogo_atributo AND o.activo AND o.valor=key) THEN
                        RAISE EXCEPTION 'Opción fuera del vocabulario cerrado de la definición' USING ERRCODE='23514'; END IF;
                END LOOP;
            END IF;
            IF op='INCLUYE' AND jsonb_typeof(v->'valores')='array' AND jsonb_array_length(v->'valores')=0 THEN
                RAISE EXCEPTION 'Grupo explícito vacío no acredita una alternativa' USING ERRCODE='23514'; END IF;
            IF op='ENTRE' THEN
                IF NOT forma_valor_criterio_demanda(h,tipo) THEN RAISE EXCEPTION 'ENTRE conocido requiere ambos límites tipados' USING ERRCODE='23514'; END IF;
                IF tipo IN ('ENTERO','DECIMAL','IMPORTE') THEN
                    IF jsonb_typeof(h->'numero') IS DISTINCT FROM 'number' OR (h->>'numero')::numeric<(v->>'numero')::numeric
                        OR h->>'moneda' IS DISTINCT FROM v->>'moneda' THEN RAISE EXCEPTION 'Límites numéricos/moneda incompatibles' USING ERRCODE='23514'; END IF;
                ELSIF tipo='FECHA' AND (h->>'fecha')::date<(v->>'fecha')::date THEN RAISE EXCEPTION 'Límites de fecha invertidos' USING ERRCODE='23514'; END IF;
            ELSIF h IS NOT NULL AND h<>'null'::jsonb THEN RAISE EXCEPTION 'Sólo ENTRE tiene segundo límite' USING ERRCODE='23514'; END IF;
        END IF;
    END LOOP;
    RETURN NEW;
END $$;

DROP TRIGGER tg_criterio_gobernado ON requerimiento_criterio;
CREATE TRIGGER tg_criterio_gobernado BEFORE INSERT OR UPDATE ON requerimiento_criterio
    FOR EACH ROW WHEN(NEW.criterio_json IS NULL) EXECUTE FUNCTION exigir_criterio_gobernado();
CREATE TRIGGER tg_criterio_demanda_canonico BEFORE INSERT ON requerimiento_criterio
    FOR EACH ROW EXECUTE FUNCTION exigir_criterio_demanda_canonico();

CREATE FUNCTION impedir_mutacion_antecedente_demanda() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
    RAISE EXCEPTION 'El antecedente de Demanda es append-only; no UPDATE/DELETE/TRUNCATE' USING ERRCODE='23514';
END $$;
CREATE TRIGGER tg_demanda_version_appendonly BEFORE UPDATE OR DELETE ON requerimiento_version FOR EACH ROW EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_criterio_demanda_appendonly BEFORE UPDATE OR DELETE ON requerimiento_criterio FOR EACH ROW EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_criterio_valor_antecedente BEFORE INSERT OR UPDATE OR DELETE ON requerimiento_criterio_valor FOR EACH ROW EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_demanda_no_truncate BEFORE TRUNCATE ON requerimiento_cliente FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_demanda_version_no_truncate BEFORE TRUNCATE ON requerimiento_version FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_criterio_demanda_no_truncate BEFORE TRUNCATE ON requerimiento_criterio FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_criterio_valor_no_truncate BEFORE TRUNCATE ON requerimiento_criterio_valor FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_demanda_zonas_no_truncate BEFORE TRUNCATE ON requerimiento_distrito FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();
CREATE TRIGGER tg_demanda_tipos_no_truncate BEFORE TRUNCATE ON requerimiento_tipo_inmueble FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacion_antecedente_demanda();

CREATE FUNCTION validar_snapshot_demanda_completo(org BIGINT,id BIGINT,ver BIGINT) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE r requerimiento_cliente%ROWTYPE; s requerimiento_version%ROWTYPE; j JSONB;
BEGIN
    SELECT * INTO r FROM requerimiento_cliente WHERE organizacion_id=org AND id_requerimiento=id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Demanda no perteneciente al tenant del snapshot' USING ERRCODE='23503'; END IF;
    SELECT * INTO s FROM requerimiento_version WHERE organizacion_id=org AND id_requerimiento=id AND version=ver;
    IF NOT FOUND OR s.id_rol_actor IS DISTINCT FROM r.id_rol_responsable OR s.procedencia_json IS NULL THEN
        RAISE EXCEPTION 'Cada versión nueva exige snapshot/actor/procedencia en la misma TX' USING ERRCODE='23514'; END IF;
    j:=s.snapshot_json::jsonb;
    IF (j->>'id')::bigint IS DISTINCT FROM id OR (j->>'version')::bigint IS DISTINCT FROM ver
        OR (j->>'idResponsable')::bigint IS DISTINCT FROM r.id_rol_responsable
        OR jsonb_typeof(j->'criterios') IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'El snapshot no corresponde a identidad, responsable y versión del agregado' USING ERRCODE='23514'; END IF;
    IF ver=r.version AND (NOT snapshot_demanda_conserva_fila(j,r) OR s.procedencia_json IS DISTINCT FROM r.procedencia_json) THEN
        RAISE EXCEPTION 'El snapshot actual no conserva el estado declarado del agregado' USING ERRCODE='23514'; END IF;
    IF ver=r.version AND (
        jsonb_typeof(j->'tiposInmueble') IS DISTINCT FROM 'array' OR jsonb_typeof(j->'distritos') IS DISTINCT FROM 'array'
        OR coalesce((SELECT jsonb_agg(x ORDER BY x) FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof(j->'tiposInmueble')='array' THEN j->'tiposInmueble' ELSE '[]'::jsonb END) x),'[]'::jsonb)
            IS DISTINCT FROM coalesce((SELECT jsonb_agg(t.tipo_inmueble ORDER BY t.tipo_inmueble) FROM requerimiento_tipo_inmueble t WHERE t.id_requerimiento=id),'[]'::jsonb)
        OR coalesce((SELECT jsonb_agg(x ORDER BY x) FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof(j->'distritos')='array' THEN j->'distritos' ELSE '[]'::jsonb END) x),'[]'::jsonb)
            IS DISTINCT FROM coalesce((SELECT jsonb_agg(d.nombre ORDER BY d.nombre) FROM requerimiento_distrito rd JOIN distrito d ON d.id_distrito=rd.id_distrito WHERE rd.id_requerimiento=id),'[]'::jsonb)) THEN
        RAISE EXCEPTION 'Tipos y zonas no se modifican fuera de la versión conservada' USING ERRCODE='23514'; END IF;
    IF jsonb_array_length(j->'criterios')<>(SELECT count(*) FROM requerimiento_criterio c WHERE c.organizacion_id=org AND c.id_requerimiento=id AND c.version_demanda=ver)
        OR EXISTS(SELECT 1 FROM requerimiento_criterio c WHERE c.organizacion_id=org AND c.id_requerimiento=id AND c.version_demanda=ver
            AND NOT EXISTS(SELECT 1 FROM jsonb_array_elements(j->'criterios') x WHERE x->'normalizado'=c.criterio_json::jsonb
                AND x->>'clave'=c.clave AND x->>'rotulo' IS NOT DISTINCT FROM c.rotulo AND x->>'unidad' IS NOT DISTINCT FROM c.unidad AND x->>'tipoDato' IS NOT DISTINCT FROM c.tipo_dato)) THEN
        RAISE EXCEPTION 'Snapshot y criterios de la versión deben conservar exactamente el mismo contenido' USING ERRCODE='23514'; END IF;
    RETURN;
END $$;
CREATE FUNCTION exigir_snapshot_demanda_completo() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE org BIGINT; id BIGINT; ver BIGINT;
BEGIN
    IF TG_TABLE_NAME IN ('requerimiento_distrito','requerimiento_tipo_inmueble') THEN
        IF TG_OP IN ('UPDATE','DELETE') THEN
            SELECT r.organizacion_id,r.version INTO org,ver FROM requerimiento_cliente r WHERE r.id_requerimiento=OLD.id_requerimiento;
            PERFORM validar_snapshot_demanda_completo(org,OLD.id_requerimiento,ver);
        END IF;
        IF TG_OP IN ('UPDATE','INSERT') THEN
            SELECT r.organizacion_id,r.version INTO org,ver FROM requerimiento_cliente r WHERE r.id_requerimiento=NEW.id_requerimiento;
            PERFORM validar_snapshot_demanda_completo(org,NEW.id_requerimiento,ver);
        END IF;
    ELSE
        org:=NEW.organizacion_id;id:=NEW.id_requerimiento;
        IF TG_TABLE_NAME='requerimiento_criterio' THEN ver:=NEW.version_demanda; ELSE ver:=NEW.version; END IF;
        PERFORM validar_snapshot_demanda_completo(org,id,ver);
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ct_demanda_snapshot_completo AFTER INSERT OR UPDATE ON requerimiento_cliente
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION exigir_snapshot_demanda_completo();
CREATE CONSTRAINT TRIGGER ct_demanda_version_completa AFTER INSERT ON requerimiento_version
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION exigir_snapshot_demanda_completo();
CREATE CONSTRAINT TRIGGER ct_criterio_version_completa AFTER INSERT ON requerimiento_criterio
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION exigir_snapshot_demanda_completo();
CREATE CONSTRAINT TRIGGER ct_demanda_zonas_versionadas AFTER INSERT OR UPDATE OR DELETE ON requerimiento_distrito
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION exigir_snapshot_demanda_completo();
CREATE CONSTRAINT TRIGGER ct_demanda_tipos_versionados AFTER INSERT OR UPDATE OR DELETE ON requerimiento_tipo_inmueble
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION exigir_snapshot_demanda_completo();

COMMENT ON COLUMN requerimiento_cliente.version IS 'Contador CAS del mismo agregado;0 técnico no inventa historia comercial';
COMMENT ON COLUMN requerimiento_cliente.id_rol_responsable IS 'AGENTE actual; NULL sólo antecedente técnico sin autoridad';
COMMENT ON COLUMN requerimiento_criterio.criterio_json IS 'Única fuente C3 de alternativas y metadatos; escalares previos son antecedente0';
COMMENT ON TABLE requerimiento_version IS 'Versiones append-only; lectura siempre reautoriza responsable actual y proyección permitida';
