-- =====================================================================
-- V36: gemela de V35, sobre el OTRO evento de reasignación.
--
-- `reasignacion_agente_broker` (V10) registra quién movió a un agente de
-- equipo, y lo hacía con `id_rol_broker_administrador NOT NULL` contra
-- `detalle_broker`. Es la misma trampa que V35 arregló en
-- `reasignacion_captacion`: desde el Bloque 5 un `TENANT_ADMIN` puede no
-- tener detalle de broker, así que el evento no lo admitía y las cuatro
-- operaciones de `/asignaciones` —que son **suyas**, gobierno puro— morían
-- con "Broker no encontrado".
--
-- Aparecieron cuatro sitios con este mismo acoplamiento (`/agentes`,
-- `validarAdministrador` de brokers, la reasignación de captación y esta).
-- El patrón que los une: **usar `actor.idRolOperativo()` para buscar en
-- `detalle_broker`**. Compila siempre y falla en ejecución.
-- =====================================================================

ALTER TABLE reasignacion_agente_broker
    ALTER COLUMN id_rol_broker_administrador DROP NOT NULL;

ALTER TABLE reasignacion_agente_broker
    ADD COLUMN id_persona_actor BIGINT REFERENCES persona (id_persona),
    ADD COLUMN tipo_rol_actor   VARCHAR(20);

COMMENT ON COLUMN reasignacion_agente_broker.id_rol_broker_administrador IS
    'Broker administrador que reasignó, cuando el autor tenía detalle de broker. '
    'NULL desde el Bloque 5: administrar dejó de ser una variedad de broker. Se '
    'conserva por el cable congelado, que lo expone.';

COMMENT ON COLUMN reasignacion_agente_broker.tipo_rol_actor IS
    'Banda del autor. Hoy siempre TENANT_ADMIN — el organigrama es gobierno —, '
    'pero se guarda explícito para que el rastro no dependa de esa suposición.';

UPDATE reasignacion_agente_broker r
   SET id_persona_actor = pr.id_persona,
       tipo_rol_actor   = 'TENANT_ADMIN'
  FROM persona_rol pr
 WHERE pr.id_persona_rol = r.id_rol_broker_administrador
   AND r.id_persona_actor IS NULL;

ALTER TABLE reasignacion_agente_broker
    ADD CONSTRAINT ck_reasignacion_agente_tiene_autor
    CHECK (id_persona_actor IS NOT NULL OR id_rol_broker_administrador IS NOT NULL);
