-- =====================================================================
-- V29: invalidacion de sesiones (D-S0-12).
--
-- Bloque "Seguridad de sesiones, auditoria y bloqueo de accesos".
--
-- El hallazgo que hace barata esta pieza: el JWT YA lleva `iat` (instante de
-- emision) y lo emiten los DOS backends. Basta comparar ese `iat` contra una
-- marca por credencial para matar todas las sesiones vivas de una cuenta
-- SIN tocar el formato del token, que sigue congelado mientras GlassFish
-- conviva.
--
-- ALCANCE DELIBERADAMENTE ESTRECHO. El Plan S0 agrupaba en V29 tambien la
-- expansion de identidad (persona_rol con ADMIN, usuario_organizacion con
-- TENANT_ADMIN/PLATFORM_ADMIN). Eso pertenece al bloque de roles y gobierno,
-- que esta BLOQUEADO por D-S0-17: escribirlo aqui ataria este bloque a una
-- decision que no esta tomada. Las columnas de contrasenas
-- (debe_cambiar_contrasena, password_actualizada_en, algoritmo_hash) entran
-- con su propio bloque, que es su dueno.
--
-- Esto NO tiene nada que ver con la autorizacion de datos personales (D-27),
-- que queda cerrada como constancia unica del alta y SIN flujo de revocacion.
-- "Revocar" aqui es siempre "invalidar sesiones".
-- =====================================================================

ALTER TABLE credencial_usuario
    ADD COLUMN sesiones_invalidas_desde TIMESTAMPTZ;

COMMENT ON COLUMN credencial_usuario.sesiones_invalidas_desde IS
    'D-S0-12: todo token cuyo `iat` sea ANTERIOR a este instante se rechaza '
    'con 401. NULL = nunca se invalido nada. Lo escribe el logout real y, mas '
    'adelante, el cambio de contrasena, el restablecimiento, la desactivacion '
    'de la cuenta y el cambio de rol o membresia.';
