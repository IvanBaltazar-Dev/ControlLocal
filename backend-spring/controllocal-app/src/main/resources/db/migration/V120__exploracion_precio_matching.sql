-- Catálogo11.8.1, aprobación expresa30/10: límite auxiliar; nunca modifica la Demanda.
-- Sólo metadata técnica del recorrido. No guarda presupuesto, precios o fichas ajenas.
ALTER TABLE matching_recorrido ADD COLUMN contexto_exploracion TEXT;
ALTER TABLE matching_recorrido ADD COLUMN huella_escalones VARCHAR(64);

CREATE FUNCTION contexto_exploracion_matching_valido(valor TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE j JSONB;
BEGIN
    IF valor IS NULL THEN RETURN FALSE; END IF;
    j:=valor::jsonb;
    IF jsonb_typeof(j) IS DISTINCT FROM 'object'
       OR j-ARRAY['modo','limite','moneda']::text[]<>'{}'::jsonb
       OR jsonb_typeof(j->'modo') IS DISTINCT FROM 'string' THEN RETURN FALSE; END IF;
    IF j->>'modo'='DESCUBRIR' THEN
        RETURN j->>'limite' IS NULL AND j->>'moneda' IS NULL;
    ELSIF j->>'modo'='EXPLORAR' THEN
        RETURN jsonb_typeof(j->'limite')='number' AND (j->>'limite')::numeric>0
           AND jsonb_typeof(j->'moneda')='string' AND length(btrim(j->>'moneda'))>0;
    END IF;
    RETURN FALSE;
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN FALSE;
END;
$$;

ALTER TABLE matching_recorrido ADD CONSTRAINT ck_matching_exploracion CHECK (
    CASE WHEN contexto_exploracion IS NULL THEN huella_escalones IS NULL
    ELSE tipo_origen='DEMANDA' AND modalidad='COMERCIAL' AND categoria='INCOMPATIBLE'
        AND version_regla='D1-D4-20261018-C3-R3'
        AND contexto_exploracion_matching_valido(contexto_exploracion) IS TRUE
        AND CASE WHEN contexto_exploracion::jsonb->>'modo'='DESCUBRIR' AND capas_no_disponibles=''
            THEN huella_escalones IS NOT NULL AND huella_escalones~'^[a-f0-9]{64}$'
            ELSE huella_escalones IS NULL END
    END
);

-- La entidad/caso actual ya conserva la referencia exacta entregada. Se reconcilia
-- su columna pendiente sin reconstruir configuraciones históricas ni tocar V114.
-- R1/R2 anteriores quedan originales; R3 no interpreta aquellos recorridos.
ALTER TABLE matching_referencia ADD COLUMN referencia_candidato VARCHAR(64);
ALTER TABLE matching_referencia ADD CONSTRAINT ck_matching_referencia_opaca CHECK (
    referencia_candidato IS NULL OR referencia_candidato~'^[a-f0-9]{64}$'
);
CREATE UNIQUE INDEX uq_matching_referencia_par ON matching_referencia
    (organizacion_id,id_recorrido,id_demanda,referencia_candidato)
    WHERE referencia_candidato IS NOT NULL;

CREATE FUNCTION exigir_referencia_matching_r3() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.referencia_candidato IS NULL AND EXISTS (
        SELECT 1 FROM matching_recorrido r WHERE r.organizacion_id=NEW.organizacion_id
        AND r.id_recorrido=NEW.id_recorrido AND r.version_regla='D1-D4-20261018-C3-R3'
    ) THEN
        RAISE EXCEPTION 'El recorrido R3 conserva la referencia exacta entregada' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_matching_referencia_r3 BEFORE INSERT ON matching_referencia
    FOR EACH ROW EXECUTE FUNCTION exigir_referencia_matching_r3();

COMMENT ON COLUMN matching_recorrido.contexto_exploracion IS 'Modo y límite elegido para esta consulta propia; no cambia el máximo de Demanda ni concede acceso.';
COMMENT ON COLUMN matching_recorrido.huella_escalones IS 'Huella de los escalones originales; cambiar la propuesta exige actualizar el alcance, sin guardar atributos ajenos.';
COMMENT ON COLUMN matching_referencia.referencia_candidato IS 'Par exacto demanda/oferta entregado; no reconstruye ni fusiona identidad física.';
-- Los triggers originales BEFORE UPDATE de V114 y retirada monotónicaV119 siguen activos.
