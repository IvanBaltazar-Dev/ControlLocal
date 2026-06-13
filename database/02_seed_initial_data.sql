USE controllocal;

-- Datos iniciales idempotentes del sistema.
-- Crea el broker administrador base sin duplicarlo si ya existe.
--
-- Usuario demo:
-- usuario: admin@controllocal.test
-- contrasena: Admin123*
-- Hash generado con PBKDF2-HMAC-SHA256, 100000 iteraciones y salt.

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
    'admin@controllocal.test',
    'A'
WHERE NOT EXISTS (
    SELECT 1
    FROM persona
    WHERE numero_documento = '00000000'
       OR correo = 'admin@controllocal.test'
);

SET @id_persona_broker_admin = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '00000000'
       OR correo = 'admin@controllocal.test'
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
    'admin@controllocal.test',
    'pbkdf2$100000$Q29udHJvbExvY2FsMjAyNg==$Dsnkk3849fOs8l7u/0XAkCpMzK7XlUIfhawJG6KKtu4=',
    'A',
    'B'
WHERE @id_persona_broker_admin IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM usuario_interno
      WHERE id_persona = @id_persona_broker_admin
         OR nombre_usuario = 'admin@controllocal.test'
  );

UPDATE persona
SET correo = 'admin@controllocal.test'
WHERE id_persona = @id_persona_broker_admin;

UPDATE usuario_interno
SET nombre_usuario = 'admin@controllocal.test',
    contrasena_hash = 'pbkdf2$100000$Q29udHJvbExvY2FsMjAyNg==$Dsnkk3849fOs8l7u/0XAkCpMzK7XlUIfhawJG6KKtu4=',
    estado_administrativo = 'A',
    rol = 'B'
WHERE id_persona = @id_persona_broker_admin;

SET @id_usuario_broker_admin = (
    SELECT id_usuario
    FROM usuario_interno
    WHERE id_persona = @id_persona_broker_admin
       OR nombre_usuario = 'admin@controllocal.test'
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

-- =========================================================
-- Perfiles demo para probar autorizacion y navegacion
-- Broker: rsalas / Broker2026
-- Agente: vmora / Agente2026
-- =========================================================

INSERT INTO persona (
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, estado
)
SELECT
    'N', 'D', '08412991',
    'Ricardo Salas', '998110220', 'rsalas@controllocal.pe', 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM persona
    WHERE numero_documento = '08412991'
       OR correo = 'rsalas@controllocal.pe'
);

SET @id_persona_broker_demo = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '08412991'
       OR correo = 'rsalas@controllocal.pe'
    ORDER BY id_persona
    LIMIT 1
);

INSERT INTO usuario_interno (
    id_persona, nombre_usuario, contrasena_hash,
    estado_administrativo, rol
)
SELECT
    @id_persona_broker_demo,
    'rsalas',
    'pbkdf2$100000$Qc8f+w62/Ea1Y4HvtUxWeg==$IW5Cj8M+m6MNBX7JXhRpTVrdrhNcDHtjwqyrYOIICqM=',
    'A',
    'B'
WHERE @id_persona_broker_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM usuario_interno
      WHERE id_persona = @id_persona_broker_demo
         OR nombre_usuario = 'rsalas'
  );

UPDATE usuario_interno
SET nombre_usuario = 'rsalas',
    contrasena_hash = 'pbkdf2$100000$Qc8f+w62/Ea1Y4HvtUxWeg==$IW5Cj8M+m6MNBX7JXhRpTVrdrhNcDHtjwqyrYOIICqM=',
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
    'Valentina Mora', '998110311', 'vmora@controllocal.pe', 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM persona
    WHERE numero_documento = '45893211'
       OR correo = 'vmora@controllocal.pe'
);

SET @id_persona_agente_demo = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '45893211'
       OR correo = 'vmora@controllocal.pe'
    ORDER BY id_persona
    LIMIT 1
);

INSERT INTO usuario_interno (
    id_persona, nombre_usuario, contrasena_hash,
    estado_administrativo, rol
)
SELECT
    @id_persona_agente_demo,
    'vmora',
    'pbkdf2$100000$Kkq+kPkIbXKml6MTc9VseQ==$+YC4SbPJmSjGNDZ8qOUILQE8+Z5LYAMPnub87Xe/2lA=',
    'A',
    'A'
WHERE @id_persona_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM usuario_interno
      WHERE id_persona = @id_persona_agente_demo
         OR nombre_usuario = 'vmora'
  );

UPDATE usuario_interno
SET nombre_usuario = 'vmora',
    contrasena_hash = 'pbkdf2$100000$Kkq+kPkIbXKml6MTc9VseQ==$+YC4SbPJmSjGNDZ8qOUILQE8+Z5LYAMPnub87Xe/2lA=',
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
