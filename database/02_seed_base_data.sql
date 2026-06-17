-- =========================================================
-- ControlLocal - datos base para desarrollo y pruebas
-- Requiere: 00_recreate_database_controllocal.sql y 01_create_schema_controllocal.sql
--
-- Incluye catalogos obligatorios y usuarios internos de prueba.
-- Los hashes PBKDF2 corresponden a estas credenciales demo:
--
--   admin@controllocal.test / Admin2026
--   rsalas                  / Broker2026
--   psoto                   / Broker2026
--   vmora                   / Agente2026
--   jruiz                   / Agente2026
--   ltorres                 / Agente2026
--   creyes                  / Agente2026
--
-- Idempotente: puede ejecutarse nuevamente sin duplicar filas.
-- =========================================================

USE controllocal;

START TRANSACTION;

-- =========================================================
-- Catalogos
-- =========================================================

INSERT INTO distrito (nombre, provincia, activo) VALUES
    ('Lima', 'Lima', TRUE),
    ('Ancon', 'Lima', TRUE),
    ('Ate', 'Lima', TRUE),
    ('Barranco', 'Lima', TRUE),
    ('Brena', 'Lima', TRUE),
    ('Carabayllo', 'Lima', TRUE),
    ('Chaclacayo', 'Lima', TRUE),
    ('Chorrillos', 'Lima', TRUE),
    ('Cieneguilla', 'Lima', TRUE),
    ('Comas', 'Lima', TRUE),
    ('El Agustino', 'Lima', TRUE),
    ('Independencia', 'Lima', TRUE),
    ('Jesus Maria', 'Lima', TRUE),
    ('La Molina', 'Lima', TRUE),
    ('La Victoria', 'Lima', TRUE),
    ('Lince', 'Lima', TRUE),
    ('Los Olivos', 'Lima', TRUE),
    ('Lurigancho-Chosica', 'Lima', TRUE),
    ('Lurin', 'Lima', TRUE),
    ('Magdalena del Mar', 'Lima', TRUE),
    ('Miraflores', 'Lima', TRUE),
    ('Pachacamac', 'Lima', TRUE),
    ('Pucusana', 'Lima', TRUE),
    ('Pueblo Libre', 'Lima', TRUE),
    ('Puente Piedra', 'Lima', TRUE),
    ('Punta Hermosa', 'Lima', TRUE),
    ('Punta Negra', 'Lima', TRUE),
    ('Rimac', 'Lima', TRUE),
    ('San Bartolo', 'Lima', TRUE),
    ('San Borja', 'Lima', TRUE),
    ('San Isidro', 'Lima', TRUE),
    ('San Juan de Lurigancho', 'Lima', TRUE),
    ('San Juan de Miraflores', 'Lima', TRUE),
    ('San Luis', 'Lima', TRUE),
    ('San Martin de Porres', 'Lima', TRUE),
    ('San Miguel', 'Lima', TRUE),
    ('Santa Anita', 'Lima', TRUE),
    ('Santa Maria del Mar', 'Lima', TRUE),
    ('Santa Rosa', 'Lima', TRUE),
    ('Santiago de Surco', 'Lima', TRUE),
    ('Surquillo', 'Lima', TRUE),
    ('Villa El Salvador', 'Lima', TRUE),
    ('Villa Maria del Triunfo', 'Lima', TRUE)
ON DUPLICATE KEY UPDATE
    provincia = VALUES(provincia),
    activo = VALUES(activo);

INSERT INTO tipo_documento_requerido (
    id_tipo_documento_requerido,
    tipo_operacion,
    tipo_documento,
    obligatorio,
    activo,
    descripcion
) VALUES
    (1, 'A', 'Documento de identidad', TRUE, TRUE, 'Documento de identidad del solicitante'),
    (2, 'A', 'Ficha o constancia RUC', TRUE, TRUE, 'Ficha RUC vigente para persona juridica'),
    (3, 'A', 'Vigencia de poder', TRUE, TRUE, 'Vigencia de poder del representante'),
    (4, 'A', 'Poder de representacion', FALSE, TRUE, 'Poder que autoriza la representacion'),
    (5, 'A', 'Sustento economico', TRUE, TRUE, 'Documentos de solvencia o ingresos'),
    (6, 'A', 'Documento de garantia', FALSE, TRUE, 'Documento asociado a la garantia'),
    (7, 'A', 'Declaracion jurada', FALSE, TRUE, 'Declaracion jurada complementaria'),
    (8, 'A', 'Otro', FALSE, TRUE, 'Otro documento solicitado')
ON DUPLICATE KEY UPDATE
    tipo_operacion = VALUES(tipo_operacion),
    tipo_documento = VALUES(tipo_documento),
    obligatorio = VALUES(obligatorio),
    activo = VALUES(activo),
    descripcion = VALUES(descripcion);

-- =========================================================
-- Usuarios internos de prueba
-- =========================================================

SET @hash_admin_2026 = 'pbkdf2$100000$uy2GnOLWMudcyeMG7pKhjA==$3twwP9cAqG+ykRGAx5BmI8ZTAPa3w2dcwviW8dqvDdE=';
SET @hash_broker_2026 = 'pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=';
SET @hash_agente_2026 = 'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=';

DROP TEMPORARY TABLE IF EXISTS seed_usuario_base;

CREATE TEMPORARY TABLE seed_usuario_base (
    tipo_usuario VARCHAR(10) NOT NULL,
    tipo_persona CHAR(1) NOT NULL,
    tipo_documento CHAR(1) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL,
    nombres_o_razon_social VARCHAR(150) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    correo VARCHAR(150) NOT NULL,
    nombre_usuario VARCHAR(60) NOT NULL,
    contrasena_hash VARCHAR(255) NOT NULL,
    rol CHAR(1) NOT NULL,
    codigo_operativo VARCHAR(20) NOT NULL,
    zona VARCHAR(100) NOT NULL,
    fecha_alta DATE NOT NULL,
    es_administrador BOOLEAN NOT NULL,
    broker_supervisor_codigo VARCHAR(20) NULL,
    estado_operativo CHAR(1) NULL
) ENGINE=MEMORY;

INSERT INTO seed_usuario_base (
    tipo_usuario, tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, nombre_usuario,
    contrasena_hash, rol, codigo_operativo, zona, fecha_alta,
    es_administrador, broker_supervisor_codigo, estado_operativo
) VALUES
    ('BROKER', 'N', 'D', '00000000', 'Broker Administrador ControlLocal',
        '999999999', 'admin@controllocal.test', 'admin@controllocal.test',
        @hash_admin_2026, 'B', 'BRK-ADM-001', 'Sede central',
        '2024-01-02', TRUE, NULL, NULL),
    ('BROKER', 'N', 'D', '08412991', 'Ricardo Salas',
        '998110220', 'rsalas@controllocal.pe', 'rsalas',
        @hash_broker_2026, 'B', 'BRK-001', 'Lima Centro / Sur',
        '2024-01-11', FALSE, NULL, NULL),
    ('BROKER', 'N', 'D', '09644120', 'Patricia Soto',
        '998110221', 'psoto@controllocal.pe', 'psoto',
        @hash_broker_2026, 'B', 'BRK-002', 'Lima Norte / Este',
        '2024-02-05', FALSE, NULL, NULL),
    ('AGENTE', 'N', 'D', '45893211', 'Valentina Mora',
        '998110311', 'vmora@controllocal.pe', 'vmora',
        @hash_agente_2026, 'A', 'AGE-001', 'Lima Centro',
        '2024-02-14', FALSE, 'BRK-001', 'D'),
    ('AGENTE', 'N', 'D', '46778122', 'Javier Ruiz',
        '998110312', 'jruiz@controllocal.pe', 'jruiz',
        @hash_agente_2026, 'A', 'AGE-002', 'Lima Moderna',
        '2024-03-04', FALSE, 'BRK-001', 'D'),
    ('AGENTE', 'N', 'D', '47220933', 'Lucia Torres',
        '998110313', 'ltorres@controllocal.pe', 'ltorres',
        @hash_agente_2026, 'A', 'AGE-003', 'Lima Norte',
        '2024-03-18', FALSE, 'BRK-002', 'D'),
    ('AGENTE', 'N', 'D', '48111544', 'Camila Reyes',
        '998110314', 'creyes@controllocal.pe', 'creyes',
        @hash_agente_2026, 'A', 'AGE-004', 'Lima Este',
        '2024-04-01', FALSE, 'BRK-001', 'D');

INSERT INTO persona (
    tipo_persona,
    tipo_documento,
    numero_documento,
    nombres_o_razon_social,
    telefono,
    correo,
    estado,
    consentimiento_uso_dato
)
SELECT
    tipo_persona,
    tipo_documento,
    numero_documento,
    nombres_o_razon_social,
    telefono,
    correo,
    'A',
    TRUE
FROM seed_usuario_base
ON DUPLICATE KEY UPDATE
    tipo_persona = VALUES(tipo_persona),
    tipo_documento = VALUES(tipo_documento),
    nombres_o_razon_social = VALUES(nombres_o_razon_social),
    telefono = VALUES(telefono),
    correo = VALUES(correo),
    estado = VALUES(estado),
    consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

INSERT INTO usuario_interno (
    id_persona,
    nombre_usuario,
    contrasena_hash,
    estado_administrativo,
    rol
)
SELECT
    p.id_persona,
    s.nombre_usuario,
    s.contrasena_hash,
    'A',
    s.rol
FROM seed_usuario_base s
INNER JOIN persona p ON p.numero_documento = s.numero_documento
ON DUPLICATE KEY UPDATE
    nombre_usuario = VALUES(nombre_usuario),
    contrasena_hash = VALUES(contrasena_hash),
    estado_administrativo = VALUES(estado_administrativo),
    rol = VALUES(rol);

INSERT INTO broker (
    id_usuario,
    codigo_broker,
    zona,
    fecha_designacion,
    es_administrador
)
SELECT
    u.id_usuario,
    s.codigo_operativo,
    s.zona,
    s.fecha_alta,
    s.es_administrador
FROM seed_usuario_base s
INNER JOIN usuario_interno u ON u.nombre_usuario = s.nombre_usuario
WHERE s.tipo_usuario = 'BROKER'
ON DUPLICATE KEY UPDATE
    zona = VALUES(zona),
    fecha_designacion = VALUES(fecha_designacion),
    es_administrador = VALUES(es_administrador);

INSERT INTO agente_inmobiliario (
    id_usuario,
    codigo_agente,
    zona_asignada,
    fecha_ingreso,
    estado_operativo
)
SELECT
    u.id_usuario,
    s.codigo_operativo,
    s.zona,
    s.fecha_alta,
    COALESCE(s.estado_operativo, 'D')
FROM seed_usuario_base s
INNER JOIN usuario_interno u ON u.nombre_usuario = s.nombre_usuario
WHERE s.tipo_usuario = 'AGENTE'
ON DUPLICATE KEY UPDATE
    zona_asignada = VALUES(zona_asignada),
    fecha_ingreso = VALUES(fecha_ingreso),
    estado_operativo = VALUES(estado_operativo);

INSERT INTO broker_agente (
    id_broker,
    id_agente,
    fecha_asignacion,
    fecha_fin,
    motivo,
    estado
)
SELECT
    b.id_broker,
    a.id_agente,
    s.fecha_alta,
    NULL,
    CONCAT('Asignacion base de ', s.nombres_o_razon_social),
    'A'
FROM seed_usuario_base s
INNER JOIN broker b ON b.codigo_broker = s.broker_supervisor_codigo
INNER JOIN agente_inmobiliario a ON a.codigo_agente = s.codigo_operativo
WHERE s.tipo_usuario = 'AGENTE'
  AND NOT EXISTS (
      SELECT 1
      FROM broker_agente ba
      WHERE ba.id_agente = a.id_agente
        AND ba.estado = 'A'
  );

DROP TEMPORARY TABLE IF EXISTS seed_usuario_base;

COMMIT;
