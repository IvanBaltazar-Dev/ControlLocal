-- Representación expresa opcional; no interpreta ni reescribe cláusulas históricas.
ALTER TABLE condicion_economica_captacion ADD COLUMN ejecucion_pacto TEXT;

ALTER TABLE condicion_economica_captacion
    ADD CONSTRAINT ck_condicion_ejecucion_pacto
    CHECK (ejecucion_pacto IS NULL OR jsonb_typeof(ejecucion_pacto::jsonb) = 'object');

-- RF058/064 reutilizan el vínculo A04 existente, sin dar finalidad de firma o de fin.
ALTER TABLE archivo_encargo DROP CONSTRAINT archivo_encargo_finalidad_check;
ALTER TABLE archivo_encargo ADD CONSTRAINT archivo_encargo_finalidad_check
    CHECK (finalidad IN ('PDF_ENCARGO','ACEPTACION_ENCARGO','TITULARIDAD_ENCARGO',
        'REPRESENTACION_ENCARGO','FIN_ENCARGO','HITO_CONTRACTUAL','COBRO_COMISION'));

ALTER TABLE carga_archivo_contextual DROP CONSTRAINT carga_archivo_contextual_finalidad_check;
ALTER TABLE carga_archivo_contextual ADD CONSTRAINT carga_archivo_contextual_finalidad_check
    CHECK (finalidad IN ('ACEPTACION_ENCARGO','TITULARIDAD_ENCARGO','REPRESENTACION_ENCARGO',
        'FIN_ENCARGO','HITO_CONTRACTUAL','COBRO_COMISION'));
