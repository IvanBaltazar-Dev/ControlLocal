-- CAT-E41 / UC-K-16: correlación previa a sesión, sin persona o tenant supuestos.
-- No autentica, no conserva secretos y no autoriza una actuación comercial.
CREATE TABLE vinculo_interaccion_segura (
    hash_flujo VARCHAR(64) PRIMARY KEY CHECK (hash_flujo ~ '^[a-f0-9]{64}$'),
    proposito VARCHAR(12) NOT NULL CHECK (proposito = 'ACCESO'),
    creado_en TIMESTAMPTZ NOT NULL,
    vence_en TIMESTAMPTZ NOT NULL,
    completado_en TIMESTAMPTZ,
    usado_en TIMESTAMPTZ,
    organizacion_id BIGINT REFERENCES organizacion(id_organizacion),
    id_persona BIGINT REFERENCES persona(id_persona),
    id_rol_operativo BIGINT,
    rol_efectivo VARCHAR(24),
    contexto_sesion VARCHAR(64),
    sesion_emitida_en TIMESTAMPTZ,
    sesion_expira_en TIMESTAMPTZ,
    hash_retorno VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_vinculo_rol_del_tenant FOREIGN KEY (organizacion_id, id_rol_operativo)
        REFERENCES persona_rol(organizacion_id, id_persona_rol),
    CONSTRAINT ck_vinculo_vigencia CHECK (vence_en > creado_en),
    CONSTRAINT ck_vinculo_contexto_atomico CHECK (
        (completado_en IS NULL AND organizacion_id IS NULL AND id_persona IS NULL
            AND id_rol_operativo IS NULL AND rol_efectivo IS NULL AND contexto_sesion IS NULL
            AND sesion_emitida_en IS NULL AND sesion_expira_en IS NULL AND hash_retorno IS NULL)
        OR (completado_en IS NOT NULL AND organizacion_id IS NOT NULL AND id_persona IS NOT NULL
            AND id_rol_operativo IS NOT NULL AND rol_efectivo IS NOT NULL AND contexto_sesion IS NOT NULL
            AND sesion_emitida_en IS NOT NULL AND sesion_expira_en IS NOT NULL AND hash_retorno IS NOT NULL
            AND sesion_emitida_en > creado_en AND sesion_expira_en > completado_en
            AND vence_en <= sesion_expira_en)),
    CONSTRAINT ck_vinculo_uso CHECK (usado_en IS NULL OR (completado_en IS NOT NULL AND usado_en >= completado_en))
);
