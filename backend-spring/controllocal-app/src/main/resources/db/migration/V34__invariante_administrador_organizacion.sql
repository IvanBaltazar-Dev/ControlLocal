-- =====================================================================
-- V34: una organizacion nunca se queda sin administrador (D-S0-9).
--
-- Bloque 5. Va DESPUES de V33: un invariante solo se puede imponer
-- cuando ya se cumple, y V33.4 comprueba justo eso antes de llegar aqui.
--
-- POR QUE UN TRIGGER Y NO UN INDICE. "Al menos uno" no se puede expresar
-- con un UNIQUE: los indices unicos prohiben repetir, no exigen que algo
-- exista. Hace falta contar, y contar despues del cambio.
--
-- POR QUE DEFERRABLE INITIALLY DEFERRED. El relevo de administrador
-- (alta del nuevo + baja del viejo) pasa por un instante intermedio en
-- el que el recuento podria ser 0 segun el orden de las sentencias.
-- Diferido al COMMIT, la transaccion se juzga por su RESULTADO y el
-- relevo es legal en cualquier orden. Inmediato, obligaria a recordar
-- "primero el alta" — una regla no escrita que se rompe sola.
--
-- ESTA CAPA NO SUSTITUYE A LA GUARDA DE APLICACION, la respalda. La
-- guarda da el mensaje que el usuario entiende y el 409; el trigger es
-- la garantia de que la regla se cumple aunque alguien escriba por SQL
-- —o aunque manana aparezca un camino de codigo que nadie reviso—.
-- Ninguna de las dos sobra.
-- =====================================================================

CREATE FUNCTION exigir_administrador_de_organizacion() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    org BIGINT := COALESCE(NEW.organizacion_id, OLD.organizacion_id);
BEGIN
    -- Una organizacion SIN cuentas activas no necesita gobierno: o esta
    -- naciendo (todavia no se inserto a nadie) o se esta desmontando
    -- entera. Exigirle administrador impediria las dos cosas, y en
    -- particular haria imposible retirar un tenant de prueba.
    IF NOT EXISTS (
        SELECT 1 FROM usuario_organizacion
        WHERE organizacion_id = org AND estado = 'A'
    ) THEN
        RETURN NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM usuario_organizacion
        WHERE organizacion_id = org AND estado = 'A' AND rol = 'TENANT_ADMIN'
    ) THEN
        RAISE EXCEPTION 'Una organizacion no puede quedarse sin administrador '
                        '(organizacion %)', org
              USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NULL;
END $$;

COMMENT ON FUNCTION exigir_administrador_de_organizacion() IS
    'D-S0-9: red de seguridad del invariante ">= 1 TENANT_ADMIN activo". Se '
    'evalua al COMMIT, asi que un relevo de administrador es valido en '
    'cualquier orden mientras el resultado final tenga gobierno.';

CREATE CONSTRAINT TRIGGER tg_usuario_org_exige_administrador
    AFTER INSERT OR UPDATE OR DELETE ON usuario_organizacion
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION exigir_administrador_de_organizacion();
