-- Auditoría C3: estado técnico D4-33 y conservación de contexto D1/D3. Sin datos privados nuevos.
-- D4-33/35: una oferta cambiada sale de ESE recorrido, también si luego vuelve A→B→A.
-- Se conserva intacta la metadata original de V114 y su capacidad de retirar metadata vencida.
CREATE TABLE matching_referencia_retirada (
    organizacion_id BIGINT NOT NULL,
    id_recorrido UUID NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    retirada_en TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organizacion_id,id_recorrido,ordinal),
    CONSTRAINT fk_matching_retirada_original FOREIGN KEY (organizacion_id,id_recorrido,ordinal)
        REFERENCES matching_referencia(organizacion_id,id_recorrido,ordinal) ON DELETE CASCADE
);

CREATE TRIGGER tg_matching_retirada_original BEFORE UPDATE ON matching_referencia_retirada
    FOR EACH ROW EXECUTE FUNCTION impedir_reescritura_matching();

-- Quitar únicamente la marca reintroduciría una referencia retirada en un recorrido vivo.
-- El cascade del original sigue admitido: al ejecutarse ya no existe la fila original.
CREATE FUNCTION impedir_reactivacion_referencia_matching() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM matching_referencia r WHERE r.organizacion_id=OLD.organizacion_id
            AND r.id_recorrido=OLD.id_recorrido AND r.ordinal=OLD.ordinal) THEN
        RAISE EXCEPTION 'La referencia retirada no se reactiva dentro del mismo recorrido' USING ERRCODE='23514';
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER tg_matching_retirada_no_reactivable BEFORE DELETE ON matching_referencia_retirada
    FOR EACH ROW EXECUTE FUNCTION impedir_reactivacion_referencia_matching();

COMMENT ON TABLE matching_referencia_retirada IS 'Marca monotónica de retirada técnica D4-33; sólo referencia/instante, sin atributos ni resultados ajenos.';

CREATE TRIGGER tg_matching_retirada_no_truncate BEFORE TRUNCATE ON matching_referencia_retirada
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_reescritura_matching();

-- Conservar un criterio literal no autoriza cambiar su operación o tipos sin
-- validar otra vez su contexto. La copia histórica sigue permitida si es el mismo.
CREATE OR REPLACE FUNCTION exigir_criterio_demanda_canonico() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
        AND anterior.tipo_dato IS NOT DISTINCT FROM NEW.tipo_dato
        AND EXISTS (
            SELECT 1 FROM requerimiento_version previa
            WHERE previa.organizacion_id=NEW.organizacion_id
              AND previa.id_requerimiento=NEW.id_requerimiento
              AND previa.version=NEW.version_demanda-1
              AND previa.snapshot_json::jsonb->>'operacion' IS NOT DISTINCT FROM
                  CASE r.operacion WHEN 'A' THEN 'ALQUILER' WHEN 'V' THEN 'VENTA' END
              AND coalesce((SELECT jsonb_agg(x ORDER BY x)
                  FROM jsonb_array_elements_text(previa.snapshot_json::jsonb->'tiposInmueble') x),'[]'::jsonb)
                  IS NOT DISTINCT FROM coalesce((SELECT jsonb_agg(t.tipo_inmueble ORDER BY t.tipo_inmueble)
                  FROM requerimiento_tipo_inmueble t WHERE t.id_requerimiento=r.id_requerimiento),'[]'::jsonb)
        ) THEN RETURN NEW; END IF;
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
