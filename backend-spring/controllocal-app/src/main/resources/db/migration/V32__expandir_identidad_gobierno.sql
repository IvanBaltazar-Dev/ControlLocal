-- =====================================================================
-- V32: expandir la identidad para separar GOBIERNO de OPERACION (D-S0-6).
--
-- Bloque 5 — "Roles y gobierno". Se ejecuta con la matriz D-S0-17 ya
-- aprobada: de las 18 operaciones que hoy comparten BROKER y ADMIN, ocho
-- cambian de dueno. La regla que lo ordena es *gobernar no es operar*.
--
-- Esta migracion solo EXPANDE (patron expandir -> convivir -> contraer):
-- no mueve un solo dato y no rompe nada de lo que hay. El backfill va en
-- V33 y el invariante en V34.
--
-- POR QUE persona_rol NECESITA 'ADMIN' (R2 del Plan S0): el token lleva
-- `idDominio` y exige que sea > 0; hoy sale del persona_rol operativo. Un
-- administrador que NO sea broker no tendria ninguno. Con el tipo 'ADMIN'
-- como rol de verdad, un administrador existe sin necesitar un
-- detalle_broker — que es justo la herencia que este bloque viene a
-- cortar.
--
-- CORRECCION AL PLAN S0. El plan pedia tambien "ampliar el CHECK de
-- usuario_organizacion.rol". Ese CHECK NO EXISTE: V6 solo restringe
-- `estado` (ck_usuario_org_estado). La columna `rol` nacio como
-- VARCHAR(20) libre. No hay nada que relajar aqui; la restriccion se
-- ANADE en V33, cuando el backfill ya deja el vocabulario final y la
-- columna pasa a ser fuente de verdad.
-- =====================================================================

-- ---------------------------------------------------------------------
-- V32.1  persona_rol admite el tipo ADMIN
-- ---------------------------------------------------------------------
ALTER TABLE persona_rol DROP CONSTRAINT ck_persona_rol_tipo;

ALTER TABLE persona_rol ADD CONSTRAINT ck_persona_rol_tipo CHECK (
    tipo_rol IN ('PROPIETARIO', 'CLIENTE', 'USUARIO_INTERNO', 'BROKER', 'AGENTE', 'ADMIN')
);

COMMENT ON COLUMN persona_rol.tipo_rol IS
    'Rol acumulable de la persona (Party-Role). ADMIN (V32) es el rol de '
    'GOBIERNO del tenant: da el `idDominio` del token sin obligar a que su '
    'titular sea broker. Una persona que gobierna Y opera lleva DOS roles '
    'explicitos, y la auditoria dice cual uso.';

-- ---------------------------------------------------------------------
-- V32.2  Indice por banda dentro del tenant
--
-- Lo pide el invariante de V34 ("una organizacion nunca sin
-- administrador"), que cuenta TENANT_ADMIN activos por organizacion en
-- cada baja o degradacion. Sin el, ese recuento es un scan de la tabla en
-- una operacion que se ejecuta dentro de una transaccion de escritura.
-- ---------------------------------------------------------------------
CREATE INDEX ix_usuario_org_rol ON usuario_organizacion (organizacion_id, rol);
