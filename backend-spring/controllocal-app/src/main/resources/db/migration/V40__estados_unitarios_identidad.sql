-- =====================================================================
-- Los tres estados del bloque de identidad pasan al vocabulario unitario.
--
-- QUE ESTABA MAL. `token_acceso.estado`, `factor_autenticacion.estado` y
-- `concesion_recuperacion.estado` nacieron en V31/V37/V38 como VARCHAR(10..12)
-- con palabras completas, mientras el resto del esquema persiste estados como
-- un solo caracter. `RepositorioEstadosIntegrationTest` exige ese invariante
-- para TODA columna `estado`/`estado_*`, y por eso el reactor estaba en rojo.
--
-- POR QUE NO SE VIO ANTES. Ese gate se salta EN SILENCIO cuando falta
-- `TEST_DB_URL` (`@EnabledIfEnvironmentVariable`), asi que las tres
-- migraciones del bloque de seguridad se cerraron sin que llegara a
-- ejecutarse nunca contra ellas.
--
-- V31, V37 y V38 no se tocan: ya estan aplicadas. Esta migracion convierte.
--
-- La letra se interpreta DENTRO de su columna; no hay significado global.
-- Cuando la inicial ya estaba tomada se toma otra letra distintiva del
-- termino, igual que en `Contrato` (FIRMADO=D, RESCINDIDO=S):
--
--   token_acceso           VIGENTE=V CONSUMIDO=C REVOCADO=R AGOTADO=A
--   factor_autenticacion   PENDIENTE=P ACTIVO=A REVOCADO=R
--   concesion_recuperacion PENDIENTE=P VIGENTE=V CERRADA=C CADUCADA=D AGOTADA=A
--
-- CUIDADO CON LOS INDICES PARCIALES. Dos de ellos son invariantes de
-- seguridad, no optimizaciones: `uq_factor_activo_por_credencial` (un solo
-- factor ACTIVO por credencial) y `uq_concesion_viva_por_organizacion` (una
-- sola concesion viva por organizacion). Su predicado compara contra el texto,
-- asi que hay que recrearlos con el codigo; dejarse uno seria retirar una
-- guarda sin que nada lo delate.
-- =====================================================================

-- --------------------------------------------------------------- token_acceso
ALTER TABLE token_acceso DROP CONSTRAINT ck_token_acceso_estado;
ALTER TABLE token_acceso ALTER COLUMN estado DROP DEFAULT;
ALTER TABLE token_acceso
    ALTER COLUMN estado TYPE VARCHAR(1) USING CASE estado
        WHEN 'VIGENTE'   THEN 'V'
        WHEN 'CONSUMIDO' THEN 'C'
        WHEN 'REVOCADO'  THEN 'R'
        WHEN 'AGOTADO'   THEN 'A'
        ELSE estado END;
ALTER TABLE token_acceso ALTER COLUMN estado SET DEFAULT 'V';
ALTER TABLE token_acceso
    ADD CONSTRAINT ck_token_acceso_estado CHECK (estado IN ('V', 'C', 'R', 'A'));

-- ------------------------------------------------------- factor_autenticacion
DROP INDEX uq_factor_activo_por_credencial;
DROP INDEX ix_factor_credencial;
ALTER TABLE factor_autenticacion DROP CONSTRAINT ck_factor_estado;
ALTER TABLE factor_autenticacion ALTER COLUMN estado DROP DEFAULT;
ALTER TABLE factor_autenticacion
    ALTER COLUMN estado TYPE VARCHAR(1) USING CASE estado
        WHEN 'PENDIENTE' THEN 'P'
        WHEN 'ACTIVO'    THEN 'A'
        WHEN 'REVOCADO'  THEN 'R'
        ELSE estado END;
ALTER TABLE factor_autenticacion ALTER COLUMN estado SET DEFAULT 'P';
ALTER TABLE factor_autenticacion
    ADD CONSTRAINT ck_factor_estado CHECK (estado IN ('P', 'A', 'R'));
CREATE UNIQUE INDEX uq_factor_activo_por_credencial
    ON factor_autenticacion (id_credencial) WHERE estado = 'A';
CREATE INDEX ix_factor_credencial ON factor_autenticacion (id_credencial, estado);

-- ----------------------------------------------------- concesion_recuperacion
DROP INDEX uq_concesion_viva_por_organizacion;
DROP INDEX ix_concesion_estado;
ALTER TABLE concesion_recuperacion DROP CONSTRAINT ck_concesion_estado;
ALTER TABLE concesion_recuperacion DROP CONSTRAINT ck_concesion_ventana;
ALTER TABLE concesion_recuperacion ALTER COLUMN estado DROP DEFAULT;
ALTER TABLE concesion_recuperacion
    ALTER COLUMN estado TYPE VARCHAR(1) USING CASE estado
        WHEN 'PENDIENTE' THEN 'P'
        WHEN 'VIGENTE'   THEN 'V'
        WHEN 'CERRADA'   THEN 'C'
        WHEN 'CADUCADA'  THEN 'D'
        WHEN 'AGOTADA'   THEN 'A'
        ELSE estado END;
ALTER TABLE concesion_recuperacion ALTER COLUMN estado SET DEFAULT 'P';
ALTER TABLE concesion_recuperacion
    ADD CONSTRAINT ck_concesion_estado CHECK (estado IN ('P', 'V', 'C', 'D', 'A'));
-- Misma regla de V38: la ventana solo existe cuando la concesion dejo de estar
-- pendiente. Se reescribe porque comparaba contra la palabra.
ALTER TABLE concesion_recuperacion
    ADD CONSTRAINT ck_concesion_ventana CHECK (
        (estado = 'P' AND vigente_desde IS NULL AND expira_en IS NULL)
        OR (estado <> 'P' AND vigente_desde IS NOT NULL AND expira_en IS NOT NULL));
CREATE UNIQUE INDEX uq_concesion_viva_por_organizacion
    ON concesion_recuperacion (organizacion_id) WHERE estado IN ('P', 'V');
CREATE INDEX ix_concesion_estado ON concesion_recuperacion (estado, expira_en);

COMMENT ON COLUMN token_acceso.estado IS
    'V vigente, C consumido, R revocado, A agotado (EstadoTokenAcceso).';
COMMENT ON COLUMN factor_autenticacion.estado IS
    'P pendiente, A activo, R revocado (EstadoFactorAutenticacion).';
COMMENT ON COLUMN concesion_recuperacion.estado IS
    'P pendiente, V vigente, C cerrada, D caducada, A agotada (EstadoConcesionRecuperacion).';
