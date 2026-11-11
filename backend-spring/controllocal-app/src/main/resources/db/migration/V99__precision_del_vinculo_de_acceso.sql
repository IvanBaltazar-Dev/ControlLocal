-- UC-K-16 y contrato de autenticación: iat/exp se expresan en segundos.
-- V98 ya puede estar aplicada: conservarla y corregir sólo la frontera temporal.
-- No reescribe identidad, contexto, referencias o efectos ya registrados.
ALTER TABLE vinculo_interaccion_segura DROP CONSTRAINT ck_vinculo_contexto_atomico;
ALTER TABLE vinculo_interaccion_segura ADD CONSTRAINT ck_vinculo_contexto_atomico CHECK (
    (completado_en IS NULL AND organizacion_id IS NULL AND id_persona IS NULL
        AND id_rol_operativo IS NULL AND rol_efectivo IS NULL AND contexto_sesion IS NULL
        AND sesion_emitida_en IS NULL AND sesion_expira_en IS NULL AND hash_retorno IS NULL)
    OR (completado_en IS NOT NULL AND organizacion_id IS NOT NULL AND id_persona IS NOT NULL
        AND id_rol_operativo IS NOT NULL AND rol_efectivo IS NOT NULL AND contexto_sesion IS NOT NULL
        AND sesion_emitida_en IS NOT NULL AND sesion_expira_en IS NOT NULL AND hash_retorno IS NOT NULL
        AND sesion_emitida_en >= date_trunc('second', creado_en) AND sesion_expira_en > completado_en
        AND vence_en <= sesion_expira_en)
);
