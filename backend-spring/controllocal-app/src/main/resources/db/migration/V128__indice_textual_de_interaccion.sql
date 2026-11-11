-- C4: proyeccion de busqueda de los mismos cinco textos ya consultables.
-- No autoriza recursos/campos ni cambia los originales o sus versiones.
CREATE FUNCTION texto_busqueda_interaccion(p_observaciones TEXT, p_contexto TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
PARALLEL SAFE
CALLED ON NULL INPUT
SET search_path = pg_catalog
AS $$
DECLARE
    v_datos jsonb;
    v_declaraciones TEXT;
    v_separador CONSTANT TEXT := chr(31);
BEGIN
    v_datos := p_contexto::jsonb -> 'datos';
    SELECT coalesce(string_agg(lower(coalesce(d.valor ->> 'texto', '')),
                               v_separador ORDER BY d.ordinal), '')
      INTO v_declaraciones
      FROM jsonb_array_elements(
          coalesce(nullif(v_datos -> 'declaraciones', 'null'::jsonb), '[]'::jsonb)
      ) WITH ORDINALITY AS d(valor, ordinal);
    RETURN lower(coalesce(p_observaciones, '')) || v_separador
        || lower(coalesce(v_datos ->> 'resumen', '')) || v_separador
        || lower(coalesce(v_datos -> 'destinatario' ->> 'nombre', '')) || v_separador
        || lower(coalesce(v_datos -> 'interlocutor' ->> 'nombre', '')) || v_separador
        || v_declaraciones;
END
$$;

ALTER TABLE interaccion_comercial ADD COLUMN texto_busqueda TEXT
    GENERATED ALWAYS AS (texto_busqueda_interaccion(observaciones, contexto_declarado_json)) STORED;

CREATE INDEX ix_interaccion_texto_busqueda_trgm
    ON interaccion_comercial USING gin (texto_busqueda gin_trgm_ops);

-- El lector unificado incluye las observaciones legadas. Ningun consumidor
-- vigente conserva la consulta aislada que necesitaba este indice sustituido.
DROP INDEX ix_interaccion_observaciones_trgm;
