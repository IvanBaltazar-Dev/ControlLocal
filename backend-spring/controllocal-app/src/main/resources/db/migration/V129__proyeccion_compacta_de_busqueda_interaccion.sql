-- C4: mismo texto candidato y recheck exacto, sin parsear todo el contexto en lectura.
-- Una proyeccion privada, atomica y derivada; originales y versiones permanecen intactos.
CREATE FUNCTION public.proyeccion_busqueda_interaccion(
    p_observaciones text, p_contexto text
) RETURNS jsonb
LANGUAGE plpgsql
IMMUTABLE
PARALLEL SAFE
CALLED ON NULL INPUT
SET search_path = pg_catalog
AS $productor$
DECLARE
    v_original jsonb;
    v_declaraciones jsonb;
    v_texto_declaraciones text;
    v_compacto jsonb;
    v_texto text;
    v_separador constant text := chr(31);
BEGIN
    -- Un solo parseo del contexto TEXT dentro de este productor.
    v_original := p_contexto::jsonb -> 'datos';

    -- Una sola expansion de declaraciones para ambos atributos derivados.
    -- Preservar el valor JSON; su ->> posterior mantiene el texto de origen.
    SELECT coalesce(jsonb_agg(jsonb_build_object('texto', d.valor -> 'texto')
                              ORDER BY d.ordinal), '[]'::jsonb),
           coalesce(string_agg(lower(coalesce(d.valor ->> 'texto', '')),
                               v_separador ORDER BY d.ordinal), '')
      INTO v_declaraciones, v_texto_declaraciones
      FROM jsonb_array_elements(
          coalesce(nullif(v_original -> 'declaraciones', 'null'::jsonb), '[]'::jsonb)
      ) WITH ORDINALITY AS d(valor, ordinal);

    v_compacto := jsonb_build_object(
        'observaciones', p_observaciones,
        'resumen', v_original -> 'resumen',
        'destinatario', jsonb_build_object('nombre', v_original -> 'destinatario' -> 'nombre'),
        'interlocutor', jsonb_build_object('nombre', v_original -> 'interlocutor' -> 'nombre'),
        'declaraciones', v_declaraciones,
        'tipo', v_original -> 'tipo',
        'resultado', v_original -> 'resultado',
        'canal', v_original -> 'canal'
    );

    v_texto := lower(coalesce(p_observaciones, '')) || v_separador
        || lower(coalesce(v_original ->> 'resumen', '')) || v_separador
        || lower(coalesce(v_original -> 'destinatario' ->> 'nombre', '')) || v_separador
        || lower(coalesce(v_original -> 'interlocutor' ->> 'nombre', '')) || v_separador
        || v_texto_declaraciones;

    RETURN jsonb_build_object('texto', v_texto, 'datos', v_compacto);
END
$productor$;

ALTER TABLE public.interaccion_comercial
    ADD COLUMN busqueda_proyectada jsonb
    GENERATED ALWAYS AS (
        public.proyeccion_busqueda_interaccion(observaciones, contexto_declarado_json)
    ) STORED;

DROP INDEX ix_interaccion_texto_busqueda_trgm;

CREATE INDEX ix_interaccion_texto_busqueda_trgm
    ON public.interaccion_comercial
    USING gin ((busqueda_proyectada ->> 'texto') gin_trgm_ops);


-- Retirar la proyeccion sustituida despues de crear y enlazar su reemplazo.
ALTER TABLE public.interaccion_comercial DROP COLUMN texto_busqueda;
DROP FUNCTION texto_busqueda_interaccion(TEXT,TEXT);
