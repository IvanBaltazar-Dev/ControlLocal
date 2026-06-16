USE controllocal;

-- Usuarios iniciales idempotentes.
-- Crea exactamente un usuario de referencia por perfil operativo:
-- broker administrador, broker y agente inmobiliario.
--
-- Antes de ejecutar este archivo, define en la misma sesion valores privados:
-- SET @seed_admin_user = 'your_admin_user';
-- SET @seed_admin_password_hash = 'your_admin_pbkdf2_hash';
-- SET @seed_broker_user = 'your_broker_user';
-- SET @seed_broker_password_hash = 'your_broker_pbkdf2_hash';
-- SET @seed_agent_user = 'your_agent_user';
-- SET @seed_agent_password_hash = 'your_agent_pbkdf2_hash';

DROP PROCEDURE IF EXISTS validar_credenciales_iniciales;

DELIMITER //
CREATE PROCEDURE validar_credenciales_iniciales()
BEGIN
    IF COALESCE(TRIM(@seed_admin_user), '') = ''
       OR COALESCE(TRIM(@seed_admin_password_hash), '') = ''
       OR COALESCE(TRIM(@seed_broker_user), '') = ''
       OR COALESCE(TRIM(@seed_broker_password_hash), '') = ''
       OR COALESCE(TRIM(@seed_agent_user), '') = ''
       OR COALESCE(TRIM(@seed_agent_password_hash), '') = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Define las variables privadas de usuarios y hashes antes de ejecutar el script.';
    END IF;
END//
DELIMITER ;

CALL validar_credenciales_iniciales();
DROP PROCEDURE validar_credenciales_iniciales;

INSERT INTO persona (
    tipo_persona,
    tipo_documento,
    numero_documento,
    nombres_o_razon_social,
    telefono,
    correo,
    estado
)
SELECT
    'N',
    'D',
    '00000000',
    'Broker Principal',
    '999999999',
    @seed_admin_user,
    'A'
WHERE NOT EXISTS (
    SELECT 1
    FROM persona
    WHERE numero_documento = '00000000'
       OR correo = @seed_admin_user
);

SET @id_persona_broker_admin = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '00000000'
       OR correo = @seed_admin_user
    ORDER BY id_persona
    LIMIT 1
);

INSERT INTO usuario_interno (
    id_persona,
    nombre_usuario,
    contrasena_hash,
    estado_administrativo,
    rol
)
SELECT
    @id_persona_broker_admin,
    @seed_admin_user,
    @seed_admin_password_hash,
    'A',
    'B'
WHERE @id_persona_broker_admin IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM usuario_interno
      WHERE id_persona = @id_persona_broker_admin
         OR nombre_usuario = @seed_admin_user
  );

UPDATE persona
SET correo = @seed_admin_user
WHERE id_persona = @id_persona_broker_admin;

UPDATE usuario_interno
SET nombre_usuario = @seed_admin_user,
    contrasena_hash = @seed_admin_password_hash,
    estado_administrativo = 'A',
    rol = 'B'
WHERE id_persona = @id_persona_broker_admin;

SET @id_usuario_broker_admin = (
    SELECT id_usuario
    FROM usuario_interno
    WHERE id_persona = @id_persona_broker_admin
       OR nombre_usuario = @seed_admin_user
    ORDER BY id_usuario
    LIMIT 1
);

INSERT INTO broker (
    id_usuario,
    codigo_broker,
    zona,
    fecha_designacion,
    es_administrador
)
SELECT
    @id_usuario_broker_admin,
    'BRK-ADM-001',
    'Sede central',
    CURRENT_DATE,
    TRUE
WHERE @id_usuario_broker_admin IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM broker
      WHERE codigo_broker = 'BRK-ADM-001'
         OR id_usuario = @id_usuario_broker_admin
         OR es_administrador = TRUE
  );

INSERT INTO persona (
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, estado
)
SELECT
    'N', 'D', '08412991',
    'Broker de referencia', '999999998', 'broker.profile@example.invalid', 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM persona
    WHERE numero_documento = '08412991'
       OR correo = 'broker.profile@example.invalid'
);

SET @id_persona_broker_demo = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '08412991'
       OR correo = 'broker.profile@example.invalid'
    ORDER BY id_persona
    LIMIT 1
);

INSERT INTO usuario_interno (
    id_persona, nombre_usuario, contrasena_hash,
    estado_administrativo, rol
)
SELECT
    @id_persona_broker_demo,
    @seed_broker_user,
    @seed_broker_password_hash,
    'A',
    'B'
WHERE @id_persona_broker_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM usuario_interno
      WHERE id_persona = @id_persona_broker_demo
         OR nombre_usuario = @seed_broker_user
  );

UPDATE usuario_interno
SET nombre_usuario = @seed_broker_user,
    contrasena_hash = @seed_broker_password_hash,
    estado_administrativo = 'A',
    rol = 'B'
WHERE id_persona = @id_persona_broker_demo;

SET @id_usuario_broker_demo = (
    SELECT id_usuario
    FROM usuario_interno
    WHERE id_persona = @id_persona_broker_demo
    LIMIT 1
);

INSERT INTO broker (
    id_usuario, codigo_broker, zona,
    fecha_designacion, es_administrador
)
SELECT
    @id_usuario_broker_demo,
    'BRK-001',
    'Lima Centro / Sur',
    CURRENT_DATE,
    FALSE
WHERE @id_usuario_broker_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM broker
      WHERE id_usuario = @id_usuario_broker_demo
         OR codigo_broker = 'BRK-001'
  );

SET @id_broker_demo = (
    SELECT id_broker
    FROM broker
    WHERE id_usuario = @id_usuario_broker_demo
       OR codigo_broker = 'BRK-001'
    ORDER BY id_broker
    LIMIT 1
);

INSERT INTO persona (
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, estado
)
SELECT
    'N', 'D', '45893211',
    'Agente de referencia', '999999997', 'agent.profile@example.invalid', 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM persona
    WHERE numero_documento = '45893211'
       OR correo = 'agent.profile@example.invalid'
);

SET @id_persona_agente_demo = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '45893211'
       OR correo = 'agent.profile@example.invalid'
    ORDER BY id_persona
    LIMIT 1
);

INSERT INTO usuario_interno (
    id_persona, nombre_usuario, contrasena_hash,
    estado_administrativo, rol
)
SELECT
    @id_persona_agente_demo,
    @seed_agent_user,
    @seed_agent_password_hash,
    'A',
    'A'
WHERE @id_persona_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM usuario_interno
      WHERE id_persona = @id_persona_agente_demo
         OR nombre_usuario = @seed_agent_user
  );

UPDATE usuario_interno
SET nombre_usuario = @seed_agent_user,
    contrasena_hash = @seed_agent_password_hash,
    estado_administrativo = 'A',
    rol = 'A'
WHERE id_persona = @id_persona_agente_demo;

SET @id_usuario_agente_demo = (
    SELECT id_usuario
    FROM usuario_interno
    WHERE id_persona = @id_persona_agente_demo
    LIMIT 1
);

INSERT INTO agente_inmobiliario (
    id_usuario, codigo_agente, zona_asignada,
    fecha_ingreso, estado_operativo
)
SELECT
    @id_usuario_agente_demo,
    'AGE-001',
    'Lima Centro',
    CURRENT_DATE,
    'D'
WHERE @id_usuario_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM agente_inmobiliario
      WHERE id_usuario = @id_usuario_agente_demo
         OR codigo_agente = 'AGE-001'
  );

SET @id_agente_demo = (
    SELECT id_agente
    FROM agente_inmobiliario
    WHERE id_usuario = @id_usuario_agente_demo
       OR codigo_agente = 'AGE-001'
    ORDER BY id_agente
    LIMIT 1
);

INSERT INTO broker_agente (
    id_broker, id_agente, fecha_asignacion,
    fecha_fin, motivo, estado
)
SELECT
    @id_broker_demo,
    @id_agente_demo,
    CURRENT_DATE,
    NULL,
    'Asignacion inicial del perfil demo',
    'A'
WHERE @id_broker_demo IS NOT NULL
  AND @id_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM broker_agente
      WHERE id_agente = @id_agente_demo
        AND estado = 'A'
  );
