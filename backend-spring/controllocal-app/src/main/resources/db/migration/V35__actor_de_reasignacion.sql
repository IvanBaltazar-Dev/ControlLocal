-- =====================================================================
-- V35: la reasignacion de captacion registra QUIEN la hizo, y ese quien
--      ya no es necesariamente un broker (D-S0-17, fila 6).
--
-- Bloque 5. La fila 6 conserva la reasignacion para los DOS roles con
-- alcances distintos: dentro del equipo es supervision del BROKER; entre
-- equipos es organigrama, y eso es gobierno del TENANT_ADMIN.
--
-- El obstaculo era de esquema, no de permisos: `id_rol_broker` es
-- NOT NULL y apunta a `detalle_broker`, asi que un administrador **sin**
-- detalle de broker —que es exactamente lo que el Bloque 5 hace posible—
-- no puede aparecer como autor. Sin esta migracion, la fila 6 seria una
-- declaracion sin efecto: el gate dejaria pasar al TENANT_ADMIN y la
-- operacion moriria con "Broker no encontrado".
--
-- No se toca el contrato: `idBroker` y `brokerNombre` siguen saliendo en
-- la respuesta cuando el autor es un broker, y el JSON omite nulos, asi
-- que una reasignacion de gobierno simplemente no los trae.
-- =====================================================================

ALTER TABLE reasignacion_captacion ALTER COLUMN id_rol_broker DROP NOT NULL;

ALTER TABLE reasignacion_captacion
    ADD COLUMN id_persona_actor BIGINT REFERENCES persona (id_persona),
    ADD COLUMN tipo_rol_actor   VARCHAR(20);

COMMENT ON COLUMN reasignacion_captacion.id_rol_broker IS
    'Broker que reasigno, cuando el autor ES un broker. NULL si la hizo el '
    'gobierno del tenant, que no tiene detalle_broker. Se conserva para no '
    'romper el cable congelado, que lo expone como idBroker.';

COMMENT ON COLUMN reasignacion_captacion.tipo_rol_actor IS
    'Banda del autor: BROKER o TENANT_ADMIN. Es lo que hace legible el rastro '
    'cuando la misma persona puede gobernar y operar (D-S0-7).';

-- Backfill: todo lo reasignado hasta hoy lo hizo un broker, por definicion
-- —era el unico que podia—.
UPDATE reasignacion_captacion r
   SET id_persona_actor = pr.id_persona,
       tipo_rol_actor   = 'BROKER'
  FROM persona_rol pr
 WHERE pr.id_persona_rol = r.id_rol_broker
   AND r.id_persona_actor IS NULL;

-- Un evento de auditoria sin autor no es un evento de auditoria. Se admiten
-- las dos formas de atribuirlo, pero no la ausencia de ambas.
ALTER TABLE reasignacion_captacion
    ADD CONSTRAINT ck_reasignacion_tiene_autor
    CHECK (id_persona_actor IS NOT NULL OR id_rol_broker IS NOT NULL);
