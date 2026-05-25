USE controllocal;

-- Datos iniciales idempotentes del sistema.
-- Crea el broker administrador base sin duplicarlo si ya existe.

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
    'broker@controllocal.com',
    'A'
WHERE NOT EXISTS (
    SELECT 1
    FROM persona
    WHERE numero_documento = '00000000'
       OR correo = 'broker@controllocal.com'
);

SET @id_persona_broker_admin = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '00000000'
       OR correo = 'broker@controllocal.com'
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
    'brokeradmin',
    'HASH_TEMPORAL',
    'A',
    'B'
WHERE @id_persona_broker_admin IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM usuario_interno
      WHERE id_persona = @id_persona_broker_admin
         OR nombre_usuario = 'brokeradmin'
  );

SET @id_usuario_broker_admin = (
    SELECT id_usuario
    FROM usuario_interno
    WHERE id_persona = @id_persona_broker_admin
       OR nombre_usuario = 'brokeradmin'
    ORDER BY id_usuario
    LIMIT 1
);

INSERT INTO broker (
    id_usuario,
    codigo_broker,
    fecha_designacion,
    es_administrador
)
SELECT
    @id_usuario_broker_admin,
    'BRK-ADM-001',
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
