-- =====================================================================
-- CORRECCION de una regresion introducida por V40.
--
-- QUE PASO. V40 convirtio `factor_autenticacion.estado` de la palabra completa
-- al codigo unitario ('ACTIVO' -> 'A'), y recreo los dos indices que dependian
-- del texto. Lo que NO se convirtio fue el TRIGGER: la funcion
-- `exigir_administrador_operativo()` que V37 dejo definida sigue comparando
--
--     AND fa.estado = 'ACTIVO'
--
-- y despues de V40 esa condicion no la cumple ninguna fila. El trigger
-- concluye que la organizacion se quedo sin administrador operativo y aborta
-- el enrolamiento con `integrity_constraint_violation`, que sale por el cable
-- como un 409.
--
-- EFECTO OBSERVADO: `POST /perfil/mfa/confirmar` respondia 409 en cualquier
-- base recien migrada. Como el helper de MFA lo comparten casi todas las
-- suites E2E, tumbaba `s0-mfa` y `f4-solicitud` en el primer login.
--
-- POR QUE NO LO VIO NADIE ANTES. El reactor no ejercita el enrolamiento contra
-- PostgreSQL —ningun test de integracion confirma un factor—, y una funcion
-- PL/pgSQL no la compila el javac ni la valida Hibernate: su cuerpo es texto
-- hasta que se ejecuta. Solo el E2E pasa por ahi.
--
-- LECCION, que vale para la proxima conversion de vocabulario: cambiar el
-- dominio de una columna obliga a revisar CHECK, indices parciales, valores
-- por defecto Y EL CUERPO DE LAS FUNCIONES que la leen. Los tres primeros los
-- delata `pg_constraint`/`pg_indexes`; el cuarto solo aparece buscando en
-- `pg_proc.prosrc`.
--
-- Se reemplaza la funcion entera (CREATE OR REPLACE conserva el trigger que la
-- usa). V37 no se toca: ya esta aplicada.
-- =====================================================================

CREATE OR REPLACE FUNCTION exigir_administrador_operativo() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    org BIGINT := COALESCE(NEW.organizacion_id, OLD.organizacion_id);
    exige_mfa BOOLEAN;
BEGIN
    IF org IS NULL THEN
        RETURN NULL;
    END IF;

    -- Una organizacion SIN cuentas activas no necesita gobierno: o esta
    -- naciendo o se esta desmontando entera.
    IF NOT EXISTS (
        SELECT 1 FROM usuario_organizacion
         WHERE organizacion_id = org AND estado = 'A'
    ) THEN
        RETURN NULL;
    END IF;

    SELECT o.mfa_gobierno_exigido INTO exige_mfa
      FROM organizacion o WHERE o.id_organizacion = org;

    IF NOT COALESCE(exige_mfa, FALSE) THEN
        -- Antes del primer enrolamiento rige la regla de V34: basta con que
        -- exista gobierno, aunque todavia no tenga segundo factor.
        IF NOT EXISTS (
            SELECT 1 FROM usuario_organizacion
             WHERE organizacion_id = org AND estado = 'A' AND rol = 'TENANT_ADMIN'
        ) THEN
            RAISE EXCEPTION 'Una organizacion no puede quedarse sin administrador '
                            '(organizacion %)', org
                  USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM usuario_organizacion uo
          JOIN credencial_usuario cu
            ON cu.id_persona_rol = uo.id_usuario
           AND cu.organizacion_id = uo.organizacion_id
          JOIN factor_autenticacion fa
            ON fa.id_credencial = cu.id_persona_rol
           -- AQUI ESTABA EL FALLO: 'ACTIVO' -> 'A' (V40).
           AND fa.estado = 'A'
         WHERE uo.organizacion_id = org
           AND uo.estado = 'A'
           AND uo.rol = 'TENANT_ADMIN'
           AND cu.estado_administrativo = 'A'
           AND NOT cu.debe_cambiar_contrasena
           AND NOT cu.debe_enrolar_mfa
    ) THEN
        RAISE EXCEPTION 'Una organizacion no puede quedarse sin administrador '
                        'operativo (organizacion %)', org
              USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NULL;
END $$;
