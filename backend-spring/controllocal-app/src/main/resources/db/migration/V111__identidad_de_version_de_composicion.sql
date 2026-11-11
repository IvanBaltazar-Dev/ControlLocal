-- RF019 / CAT-E13 / AC076-077: una versión material identifica un solo
-- corte de composición dentro de su Encargo y organización. No se elige
-- arbitrariamente entre dos snapshots ni se reescribe un antecedente.
ALTER TABLE composicion_encargo
    ADD CONSTRAINT uq_composicion_encargo_version_material
    UNIQUE (organizacion_id, id_captacion, version_contenido);
