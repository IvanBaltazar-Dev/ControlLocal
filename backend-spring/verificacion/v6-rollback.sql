-- =====================================================================
-- Rollback de V6 (criterio #8 del gate de docs/ai/plan-migracion-v6-tenancy.md).
-- Deshace el nucleo multi-tenant y devuelve el esquema a la forma V5:
-- sin organizacion_id, con las unicidades globales de siempre.
--
-- Los DATOS de negocio NO se tocan: solo se pierde la marca de tenant,
-- que es exactamente lo que significa volver a mono-tenant. Las filas
-- creadas despues de V6 sobreviven.
--
-- Deshace en ORDEN INVERSO (V6.6 -> V6.1) porque las FK compuestas y las
-- unicidades dependen de la columna que se elimina al final.
--
-- Uso (sobre una COPIA; ver el README de esta carpeta):
--   docker exec -i controllocal-postgres-v2 psql -U controllocal \
--     -d controllocal_rollback_test -v ON_ERROR_STOP=1 \
--     < backend-spring/verificacion/v6-rollback.sql
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;

-- --- V6.6  consentimiento -------------------------------------------
DROP TABLE IF EXISTS autorizacion_tratamiento_evento;
DROP TABLE IF EXISTS evidencia_autorizacion;
DROP TABLE IF EXISTS aviso_privacidad_version;
DROP TABLE IF EXISTS finalidad_tratamiento;

-- --- V6.5  membresia -------------------------------------------------
DROP TABLE IF EXISTS usuario_organizacion;

-- --- V6.4  FK compuestas con tenant + unicos (organizacion_id, pk) ---
ALTER TABLE reasignacion_captacion DROP CONSTRAINT IF EXISTS fk_reasignacion_captacion_org;
ALTER TABLE prospeccion            DROP CONSTRAINT IF EXISTS fk_prospeccion_agente_org;
ALTER TABLE prospeccion            DROP CONSTRAINT IF EXISTS fk_prospeccion_propiedad_org;
ALTER TABLE captacion              DROP CONSTRAINT IF EXISTS fk_captacion_agente_org;
ALTER TABLE captacion              DROP CONSTRAINT IF EXISTS fk_captacion_propiedad_org;
ALTER TABLE propiedad              DROP CONSTRAINT IF EXISTS fk_propiedad_propietario_org;
ALTER TABLE persona_rol            DROP CONSTRAINT IF EXISTS fk_persona_rol_persona_org;

ALTER TABLE detalle_agente DROP CONSTRAINT IF EXISTS uq_detalle_agente_org;
ALTER TABLE captacion      DROP CONSTRAINT IF EXISTS uq_captacion_org;
ALTER TABLE propiedad      DROP CONSTRAINT IF EXISTS uq_propiedad_org;
ALTER TABLE persona_rol    DROP CONSTRAINT IF EXISTS uq_persona_rol_org;
ALTER TABLE persona        DROP CONSTRAINT IF EXISTS uq_persona_org;

-- --- V6.3  unicidades por organizacion -> globales -------------------
DROP INDEX IF EXISTS uq_captacion_activa_por_local;
CREATE UNIQUE INDEX uq_captacion_activa_por_local ON captacion (id_propiedad) WHERE estado = 'A';

ALTER TABLE prospeccion DROP CONSTRAINT IF EXISTS uq_prospeccion_codigo;
ALTER TABLE prospeccion ADD  CONSTRAINT prospeccion_codigo_prospeccion_key UNIQUE (codigo_prospeccion);
ALTER TABLE captacion   DROP CONSTRAINT IF EXISTS uq_captacion_codigo;
ALTER TABLE captacion   ADD  CONSTRAINT captacion_codigo_captacion_key UNIQUE (codigo_captacion);
ALTER TABLE propiedad   DROP CONSTRAINT IF EXISTS uq_propiedad_codigo;
ALTER TABLE propiedad   ADD  CONSTRAINT propiedad_codigo_key UNIQUE (codigo);

DROP INDEX IF EXISTS uq_broker_admin_unico;
CREATE UNIQUE INDEX uq_broker_admin_unico ON detalle_broker ((es_administrador)) WHERE es_administrador;

ALTER TABLE detalle_agente     DROP CONSTRAINT IF EXISTS uq_agente_codigo;
ALTER TABLE detalle_agente     ADD  CONSTRAINT detalle_agente_codigo_agente_key UNIQUE (codigo_agente);
ALTER TABLE detalle_broker     DROP CONSTRAINT IF EXISTS uq_broker_codigo;
ALTER TABLE detalle_broker     ADD  CONSTRAINT detalle_broker_codigo_broker_key UNIQUE (codigo_broker);
ALTER TABLE credencial_usuario DROP CONSTRAINT IF EXISTS uq_credencial_nombre_usuario;
ALTER TABLE credencial_usuario ADD  CONSTRAINT credencial_usuario_nombre_usuario_key UNIQUE (nombre_usuario);

ALTER TABLE persona DROP CONSTRAINT IF EXISTS uq_persona_correo;
ALTER TABLE persona ADD  CONSTRAINT uq_persona_correo UNIQUE (correo);
ALTER TABLE persona DROP CONSTRAINT IF EXISTS uq_persona_documento;
ALTER TABLE persona ADD  CONSTRAINT uq_persona_documento UNIQUE (tipo_documento, numero_documento);

-- --- V6.2  columna organizacion_id (sus indices caen con ella) -------
DO $$
DECLARE
    privada  TEXT;
    privadas TEXT[] := ARRAY[
        'persona', 'persona_rol', 'credencial_usuario', 'detalle_broker',
        'detalle_agente', 'supervision_agente', 'historial_estado',
        'propiedad', 'detalle_local_comercial', 'foto_propiedad',
        'precio_propiedad', 'publicacion', 'captacion', 'prospeccion',
        'reasignacion_captacion'
    ];
BEGIN
    FOREACH privada IN ARRAY privadas LOOP
        EXECUTE format('ALTER TABLE %I DROP COLUMN IF EXISTS organizacion_id', privada);
    END LOOP;
END $$;

-- --- V6.1  organizacion ---------------------------------------------
DROP TABLE IF EXISTS organizacion;

-- Flyway debe volver a creer que el esquema esta en V5.
DELETE FROM flyway_schema_history WHERE version = '6';

COMMIT;
