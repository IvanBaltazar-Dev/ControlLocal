-- D1-09/D3-07–21: productor del total mensual de la configuración, dentro del pacto.
-- No dato supuesto sobre filas anteriores ni ampliación de la proyección L1/L2.
ALTER TABLE condicion_economica_captacion ADD COLUMN composicion_mensual TEXT;

CREATE FUNCTION declaracion_mensual_encargo_valida(valor TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE j JSONB; d JSONB;
BEGIN
    IF valor IS NULL THEN RETURN FALSE; END IF;
    j:=valor::jsonb;
    IF jsonb_typeof(j) IS DISTINCT FROM 'object'
        OR j-ARRAY['versionEsquema','configuracion','huellaBaseEconomica','declaracion']::text[]<>'{}'::jsonb
        OR jsonb_typeof(j->'versionEsquema') IS DISTINCT FROM 'number'
        OR (j->>'versionEsquema')::numeric<>1
        OR jsonb_typeof(j->'configuracion') IS DISTINCT FROM 'string'
        OR j->>'configuracion' !~ '^(UNIDAD|COMPOSICION):[1-9][0-9]*$'
        OR jsonb_typeof(j->'huellaBaseEconomica') IS DISTINCT FROM 'string'
        OR j->>'huellaBaseEconomica' !~ '^[a-f0-9]{64}$'
        OR jsonb_typeof(j->'declaracion') IS DISTINCT FROM 'object' THEN RETURN FALSE; END IF;
    d:=j->'declaracion';
    RETURN d-ARRAY['completa','fuente','componentes']::text[]='{}'::jsonb
        AND (d->>'completa' IS NULL OR jsonb_typeof(d->'completa')='boolean')
        AND (d->>'fuente' IS NULL OR jsonb_typeof(d->'fuente')='string')
        AND jsonb_typeof(d->'componentes')='array';
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN FALSE;
END;
$$;

ALTER TABLE condicion_economica_captacion ADD CONSTRAINT ck_condicion_declaracion_mensual
    CHECK (composicion_mensual IS NULL OR declaracion_mensual_encargo_valida(composicion_mensual) IS TRUE);

-- Core vincula la declaración antes de guardar la NUEVA condición. Editar crea otra
-- condición y conserva la anterior; no hay permiso para reescribir el antecedente.
CREATE FUNCTION conservar_declaracion_mensual_encargo() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.composicion_mensual IS NOT NULL THEN
            RAISE EXCEPTION 'La declaración mensual conserva su antecedente de pacto' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.composicion_mensual IS DISTINCT FROM OLD.composicion_mensual THEN
        RAISE EXCEPTION 'Una nueva declaración mensual requiere otra condición y revisión de Encargo' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_conservar_declaracion_mensual BEFORE UPDATE OR DELETE ON condicion_economica_captacion
    FOR EACH ROW EXECUTE FUNCTION conservar_declaracion_mensual_encargo();

COMMENT ON COLUMN condicion_economica_captacion.composicion_mensual IS
    'Declaración privada D3 del mismo pacto/configuración/base económica; original conservado. NULL es desconocido, no0 ni permiso L1/L2.';
