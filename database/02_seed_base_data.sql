-- =========================================================
-- ControlLocal - datos de la base (catalogos, usuarios y cartera operativa)
-- Requiere: 00_recreate_database_controllocal.sql y 01_create_schema_controllocal.sql
--
-- Archivo unico de carga de datos. Incluye:
--   - catalogos obligatorios (distritos, tipos de documento requerido);
--   - usuarios internos: 6 brokers (1 admin BRK-ADM-001 + BRK-001..005) y
--     15 agentes (AGE-001..015);
--   - propietarios, locales, publicaciones y precios;
--   - captaciones (12), prospecciones, requerimientos y oportunidades;
--   - interacciones, visitas, solicitudes (10 + 6 extra de alquiler) y evaluaciones;
--   - contratos, comisiones, reportes, tareas, alertas, documentos e historial.
--   - cartera masiva CAR-2026: 520 propietarios, 760 clientes, 620 locales,
--     560 prospecciones, 480 captaciones, 900 oportunidades, 620 solicitudes,
--     190 contratos, comisiones, documentos, reportes, tareas, alertas e historiales.
--
-- Tambien siembra documentos de solicitud referenciales para revisar estados,
-- observaciones y tiempos de respuesta sin depender del almacen de archivos.
--
-- Credenciales de acceso:
--   admin@controllocal.test / Admin2026
--   brokers (rsalas, psoto, gnunez, maguirre, sramirez) / Broker2026
--   agentes (vmora, jruiz, ltorres, creyes, pquispe ... rgomez) / Agente2026
--
-- Idempotente: usa codigos estables (ON DUPLICATE KEY / NOT EXISTS).
-- =========================================================

USE controllocal;

START TRANSACTION;

-- =========================================================
-- Catalogos: distritos y tipos de documento requerido
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
-- Usuarios internos: brokers y agentes
-- 6 brokers (1 administrador BRK-ADM-001 + BRK-001..005) y 15 agentes (AGE-001..015).
-- Credenciales de acceso: admin Admin2026, brokers Broker2026, agentes Agente2026.
-- =========================================================

SET @hash_admin_2026 = 'pbkdf2$100000$uy2GnOLWMudcyeMG7pKhjA==$3twwP9cAqG+ykRGAx5BmI8ZTAPa3w2dcwviW8dqvDdE=';
SET @hash_broker_2026 = 'pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=';
SET @hash_agente_2026 = 'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=';

DROP TEMPORARY TABLE IF EXISTS seed_usuario_operativo;

CREATE TEMPORARY TABLE seed_usuario_operativo (
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

INSERT INTO seed_usuario_operativo (
    tipo_usuario, tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, nombre_usuario,
    contrasena_hash, rol, codigo_operativo, zona, fecha_alta,
    es_administrador, broker_supervisor_codigo, estado_operativo
) VALUES
    ('BROKER', 'N', 'D', '00000000', 'Broker Administrador ControlLocal', '999999999', 'admin@controllocal.test', 'admin@controllocal.test',
        @hash_admin_2026, 'B', 'BRK-ADM-001', 'Sede central', '2024-01-02', TRUE, NULL, NULL),
    ('BROKER', 'N', 'D', '08412991', 'Ricardo Salas', '998110220', 'rsalas@controllocal.pe', 'rsalas',
        @hash_broker_2026, 'B', 'BRK-001', 'Lima Centro / Sur', '2024-01-11', FALSE, NULL, NULL),
    ('BROKER', 'N', 'D', '09644120', 'Patricia Soto', '998110221', 'psoto@controllocal.pe', 'psoto',
        @hash_broker_2026, 'B', 'BRK-002', 'Lima Norte / Este', '2024-02-05', FALSE, NULL, NULL),
    ('BROKER', 'N', 'D', '09711233', 'Gabriela Nunez', '998110223', 'gnunez@controllocal.pe', 'gnunez',
        @hash_broker_2026, 'B', 'BRK-003', 'Lima Sur', '2024-05-06', FALSE, NULL, NULL),
    ('BROKER', 'N', 'D', '10522344', 'Martin Aguirre', '998110224', 'maguirre@controllocal.pe', 'maguirre',
        @hash_broker_2026, 'B', 'BRK-004', 'Lima Este', '2024-06-10', FALSE, NULL, NULL),
    ('BROKER', 'N', 'D', '11633455', 'Sofia Ramirez', '998110225', 'sramirez@controllocal.pe', 'sramirez',
        @hash_broker_2026, 'B', 'BRK-005', 'Lima Oeste', '2024-07-15', FALSE, NULL, NULL),
    ('AGENTE', 'N', 'D', '45893211', 'Valentina Mora', '998110311', 'vmora@controllocal.pe', 'vmora',
        @hash_agente_2026, 'A', 'AGE-001', 'Lima Centro', '2024-02-14', FALSE, 'BRK-001', 'D'),
    ('AGENTE', 'N', 'D', '46778122', 'Javier Ruiz', '998110312', 'jruiz@controllocal.pe', 'jruiz',
        @hash_agente_2026, 'A', 'AGE-002', 'Lima Moderna', '2024-03-04', FALSE, 'BRK-001', 'D'),
    ('AGENTE', 'N', 'D', '47220933', 'Lucia Torres', '998110313', 'ltorres@controllocal.pe', 'ltorres',
        @hash_agente_2026, 'A', 'AGE-003', 'Lima Norte', '2024-03-18', FALSE, 'BRK-002', 'D'),
    ('AGENTE', 'N', 'D', '48111544', 'Camila Reyes', '998110314', 'creyes@controllocal.pe', 'creyes',
        @hash_agente_2026, 'A', 'AGE-004', 'Lima Este', '2024-04-01', FALSE, 'BRK-001', 'D'),
    ('AGENTE', 'N', 'D', '45100005', 'Pedro Quispe', '998110315', 'pquispe@controllocal.pe', 'pquispe',
        @hash_agente_2026, 'A', 'AGE-005', 'Lima Centro', '2024-05-20', FALSE, 'BRK-001', 'D'),
    ('AGENTE', 'N', 'D', '45100006', 'Rosa Mendoza', '998110316', 'rmendoza@controllocal.pe', 'rmendoza',
        @hash_agente_2026, 'A', 'AGE-006', 'Lima Norte', '2024-06-03', FALSE, 'BRK-002', 'D'),
    ('AGENTE', 'N', 'D', '45100007', 'Carlos Vargas', '998110317', 'cvargas@controllocal.pe', 'cvargas',
        @hash_agente_2026, 'A', 'AGE-007', 'Lima Sur', '2024-06-17', FALSE, 'BRK-003', 'D'),
    ('AGENTE', 'N', 'D', '45100008', 'Elena Flores', '998110318', 'eflores@controllocal.pe', 'eflores',
        @hash_agente_2026, 'A', 'AGE-008', 'Lima Sur', '2024-07-01', FALSE, 'BRK-003', 'D'),
    ('AGENTE', 'N', 'D', '45100009', 'Jorge Diaz', '998110319', 'jdiaz@controllocal.pe', 'jdiaz',
        @hash_agente_2026, 'A', 'AGE-009', 'Lima Sur', '2024-07-22', FALSE, 'BRK-003', 'D'),
    ('AGENTE', 'N', 'D', '45100010', 'Ana Salazar', '998110320', 'asalazar@controllocal.pe', 'asalazar',
        @hash_agente_2026, 'A', 'AGE-010', 'Lima Este', '2024-08-05', FALSE, 'BRK-004', 'D'),
    ('AGENTE', 'N', 'D', '45100011', 'Luis Campos', '998110321', 'lcampos@controllocal.pe', 'lcampos',
        @hash_agente_2026, 'A', 'AGE-011', 'Lima Este', '2024-08-19', FALSE, 'BRK-004', 'D'),
    ('AGENTE', 'N', 'D', '45100012', 'Maria Rojas', '998110322', 'mrojas@controllocal.pe', 'mrojas',
        @hash_agente_2026, 'A', 'AGE-012', 'Lima Este', '2024-09-02', FALSE, 'BRK-004', 'D'),
    ('AGENTE', 'N', 'D', '45100013', 'Fernando Leon', '998110323', 'fleon@controllocal.pe', 'fleon',
        @hash_agente_2026, 'A', 'AGE-013', 'Lima Oeste', '2024-09-16', FALSE, 'BRK-005', 'D'),
    ('AGENTE', 'N', 'D', '45100014', 'Patricia Vega', '998110324', 'pvega@controllocal.pe', 'pvega',
        @hash_agente_2026, 'A', 'AGE-014', 'Lima Oeste', '2024-10-07', FALSE, 'BRK-005', 'D'),
    ('AGENTE', 'N', 'D', '45100015', 'Ricardo Gomez', '998110325', 'rgomez@controllocal.pe', 'rgomez',
        @hash_agente_2026, 'A', 'AGE-015', 'Lima Oeste', '2024-10-21', FALSE, 'BRK-005', 'D');

INSERT INTO persona (
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, estado, consentimiento_uso_dato
)
SELECT
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, 'A', TRUE
FROM seed_usuario_operativo
ON DUPLICATE KEY UPDATE
    tipo_persona = VALUES(tipo_persona),
    tipo_documento = VALUES(tipo_documento),
    nombres_o_razon_social = VALUES(nombres_o_razon_social),
    telefono = VALUES(telefono),
    correo = VALUES(correo),
    estado = VALUES(estado),
    consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

INSERT INTO usuario_interno (
    id_persona, nombre_usuario, contrasena_hash, estado_administrativo, rol
)
SELECT
    p.id_persona, s.nombre_usuario, s.contrasena_hash, 'A', s.rol
FROM seed_usuario_operativo s
INNER JOIN persona p ON p.numero_documento = s.numero_documento
ON DUPLICATE KEY UPDATE
    nombre_usuario = VALUES(nombre_usuario),
    contrasena_hash = VALUES(contrasena_hash),
    estado_administrativo = VALUES(estado_administrativo),
    rol = VALUES(rol);

INSERT INTO broker (id_usuario, codigo_broker, zona, fecha_designacion, es_administrador)
SELECT
    u.id_usuario, s.codigo_operativo, s.zona, s.fecha_alta, s.es_administrador
FROM seed_usuario_operativo s
INNER JOIN usuario_interno u ON u.nombre_usuario = s.nombre_usuario
WHERE s.tipo_usuario = 'BROKER'
ON DUPLICATE KEY UPDATE
    zona = VALUES(zona),
    fecha_designacion = VALUES(fecha_designacion),
    es_administrador = VALUES(es_administrador);

INSERT INTO agente_inmobiliario (id_usuario, codigo_agente, zona_asignada, fecha_ingreso, estado_operativo)
SELECT
    u.id_usuario, s.codigo_operativo, s.zona, s.fecha_alta, COALESCE(s.estado_operativo, 'D')
FROM seed_usuario_operativo s
INNER JOIN usuario_interno u ON u.nombre_usuario = s.nombre_usuario
WHERE s.tipo_usuario = 'AGENTE'
ON DUPLICATE KEY UPDATE
    zona_asignada = VALUES(zona_asignada),
    fecha_ingreso = VALUES(fecha_ingreso),
    estado_operativo = VALUES(estado_operativo);

INSERT INTO broker_agente (id_broker, id_agente, fecha_asignacion, fecha_fin, motivo, estado)
SELECT
    b.id_broker, a.id_agente, s.fecha_alta, NULL,
    CONCAT('Asignacion inicial de ', s.nombres_o_razon_social), 'A'
FROM seed_usuario_operativo s
INNER JOIN broker b ON b.codigo_broker = s.broker_supervisor_codigo
INNER JOIN agente_inmobiliario a ON a.codigo_agente = s.codigo_operativo
WHERE s.tipo_usuario = 'AGENTE'
  AND NOT EXISTS (
      SELECT 1 FROM broker_agente ba
      WHERE ba.id_agente = a.id_agente AND ba.estado = 'A'
  );

DROP TEMPORARY TABLE IF EXISTS seed_usuario_operativo;

-- =========================================================
-- Personas base: propietarios y clientes
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_persona_operativo;

CREATE TEMPORARY TABLE seed_persona_operativo (
    perfil VARCHAR(20) NOT NULL,
    tipo_persona CHAR(1) NOT NULL,
    tipo_documento CHAR(1) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL,
    nombres_o_razon_social VARCHAR(150) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    correo VARCHAR(150) NOT NULL,
    rubro_comercial VARCHAR(120) NULL
) ENGINE=MEMORY;

INSERT INTO seed_persona_operativo VALUES
    ('PROPIETARIO', 'J', 'R', '20580000001', 'Inversiones Mirador S.A.C.', '945200101', 'administracion@mirador.pe', NULL),
    ('PROPIETARIO', 'N', 'D', '70110001', 'Carmen Vela Arce', '945200102', 'carmen.vela@clientes.controllocal.pe', NULL),
    ('PROPIETARIO', 'J', 'R', '20580000027', 'Grupo San Borja S.A.C.', '945200103', 'activos@gruposanborja.pe', NULL),
    ('PROPIETARIO', 'N', 'D', '70110004', 'Luis Paredes Montalvo', '945200104', 'luis.paredes@clientes.controllocal.pe', NULL),
    ('PROPIETARIO', 'J', 'R', '20580000051', 'Retail Norte S.A.C.', '945200105', 'inmuebles@retailnorte.pe', NULL),
    ('PROPIETARIO', 'N', 'D', '70110006', 'Milagros Chaname Rios', '945200106', 'milagros.chaname@clientes.controllocal.pe', NULL),
    ('CLIENTE', 'J', 'R', '20610000011', 'Mercado Uno S.A.C.', '946100101', 'contacto@mercadouno.pe', 'Minimarket'),
    ('CLIENTE', 'J', 'R', '20610000029', 'Showroom Centro S.A.C.', '946100202', 'gerencia@showroomcentro.pe', 'Moda y exhibicion'),
    ('CLIENTE', 'J', 'R', '20610000037', 'Clinica Dental Sonrisa S.A.C.', '946100303', 'admin@sonrisadental.pe', 'Servicios odontologicos'),
    ('CLIENTE', 'J', 'R', '20610000045', 'Cafeteria Barranco E.I.R.L.', '946100404', 'hola@cafebarranco.pe', 'Cafeteria'),
    ('CLIENTE', 'J', 'R', '20610000053', 'Cowork Andes S.A.C.', '946100505', 'operaciones@coworkandes.pe', 'Coworking'),
    ('CLIENTE', 'J', 'R', '20610000061', 'Farmacia Salud 24 S.A.C.', '946100606', 'expansion@salud24.pe', 'Farmacia'),
    ('CLIENTE', 'N', 'D', '76000001', 'Andrea Huaman Quispe', '946100707', 'andrea.huaman@clientes.controllocal.pe', 'Boutique'),
    ('CLIENTE', 'N', 'D', '76000002', 'Diego Castillo Flores', '946100808', 'diego.castillo@clientes.controllocal.pe', 'Restaurante'),
    -- Propietarios adicionales
    ('PROPIETARIO', 'J', 'R', '20580000078', 'Activos Lima Norte S.A.C.', '945200107', 'activos@limanorte.pe', NULL),
    ('PROPIETARIO', 'N', 'D', '70110008', 'Rosa Linares Tello', '945200108', 'rosa.linares@clientes.controllocal.pe', NULL),
    ('PROPIETARIO', 'J', 'R', '20580000086', 'Inmobiliaria El Sol S.A.C.', '945200109', 'contacto@elsol.pe', NULL),
    ('PROPIETARIO', 'N', 'D', '70110010', 'Hugo Bravo Salinas', '945200110', 'hugo.bravo@clientes.controllocal.pe', NULL),
    ('PROPIETARIO', 'J', 'R', '20580000094', 'Patrimonio Surco S.A.C.', '945200111', 'inmuebles@patrimoniosurco.pe', NULL),
    ('PROPIETARIO', 'N', 'D', '70110012', 'Teresa Campos Nunez', '945200112', 'teresa.campos@clientes.controllocal.pe', NULL),
    -- Clientes adicionales
    ('CLIENTE', 'J', 'R', '20610000079', 'Gimnasio Fuerza Total S.A.C.', '946100909', 'gerencia@fuerzatotal.pe', 'Gimnasio'),
    ('CLIENTE', 'J', 'R', '20610000087', 'Libreria Saber S.A.C.', '946101010', 'ventas@libreriasaber.pe', 'Libreria'),
    ('CLIENTE', 'J', 'R', '20610000095', 'Optica Vision Clara S.A.C.', '946101111', 'contacto@visionclara.pe', 'Optica'),
    ('CLIENTE', 'J', 'R', '20610000102', 'Pet Shop Huellas S.A.C.', '946101212', 'hola@petshuellas.pe', 'Veterinaria'),
    ('CLIENTE', 'J', 'R', '20610000118', 'Panaderia Trigo de Oro S.A.C.', '946101313', 'pedidos@trigodeoro.pe', 'Panaderia'),
    ('CLIENTE', 'J', 'R', '20610000126', 'Academia Preuniversitaria Norte S.A.C.', '946101414', 'informes@academianorte.pe', 'Educacion'),
    ('CLIENTE', 'N', 'D', '76000003', 'Sandra Quiroz Pena', '946101515', 'sandra.quiroz@clientes.controllocal.pe', 'Salon de belleza'),
    ('CLIENTE', 'N', 'D', '76000004', 'Marco Ramos Diaz', '946101616', 'marco.ramos@clientes.controllocal.pe', 'Ferreteria'),
    ('CLIENTE', 'N', 'D', '76000005', 'Lucia Ferrer Campos', '946101717', 'lucia.ferrer@clientes.controllocal.pe', 'Floreria'),
    ('CLIENTE', 'N', 'D', '76000006', 'Oscar Medina Rios', '946101818', 'oscar.medina@clientes.controllocal.pe', 'Heladeria'),
    ('CLIENTE', 'N', 'D', '76000007', 'Karina Solano Vega', '946101919', 'karina.solano@clientes.controllocal.pe', 'Lavanderia'),
    ('CLIENTE', 'N', 'D', '76000008', 'Bruno Castro Lazo', '946102020', 'bruno.castro@clientes.controllocal.pe', 'Jugueteria');

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
FROM seed_persona_operativo
ON DUPLICATE KEY UPDATE
    tipo_persona = VALUES(tipo_persona),
    tipo_documento = VALUES(tipo_documento),
    nombres_o_razon_social = VALUES(nombres_o_razon_social),
    telefono = VALUES(telefono),
    correo = VALUES(correo),
    estado = VALUES(estado),
    consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

INSERT INTO propietario (id_persona)
SELECT p.id_persona
FROM seed_persona_operativo s
INNER JOIN persona p ON p.numero_documento = s.numero_documento
WHERE s.perfil = 'PROPIETARIO'
ON DUPLICATE KEY UPDATE id_persona = VALUES(id_persona);

INSERT INTO cliente_interesado (
    id_persona,
    rubro_comercial,
    consentimiento_contacto,
    consentimiento_uso_dato
)
SELECT
    p.id_persona,
    s.rubro_comercial,
    TRUE,
    TRUE
FROM seed_persona_operativo s
INNER JOIN persona p ON p.numero_documento = s.numero_documento
WHERE s.perfil = 'CLIENTE'
ON DUPLICATE KEY UPDATE
    rubro_comercial = VALUES(rubro_comercial),
    consentimiento_contacto = VALUES(consentimiento_contacto),
    consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

-- =========================================================
-- Reasignacion operativa de agente entre brokers
-- AGE-004 nace bajo BRK-001 (seccion de usuarios) y aqui pasa a BRK-002.
-- =========================================================

SET @id_broker_admin = (SELECT id_broker FROM broker WHERE codigo_broker = 'BRK-ADM-001' LIMIT 1);
SET @id_broker_001 = (SELECT id_broker FROM broker WHERE codigo_broker = 'BRK-001' LIMIT 1);
SET @id_broker_002 = (SELECT id_broker FROM broker WHERE codigo_broker = 'BRK-002' LIMIT 1);
SET @id_agente_004 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-004' LIMIT 1);

UPDATE broker_agente
SET fecha_fin = '2026-05-30',
    estado = 'I',
    motivo = 'Cierre por reasignacion operativa de Camila Reyes a Patricia Soto'
WHERE id_agente = @id_agente_004
  AND estado = 'A'
  AND id_broker <> @id_broker_002;

INSERT INTO broker_agente (
    id_broker,
    id_agente,
    fecha_asignacion,
    fecha_fin,
    motivo,
    estado
)
SELECT
    @id_broker_002,
    @id_agente_004,
    '2026-06-01',
    NULL,
    'Asignacion vigente despues de reasignacion operativa',
    'A'
WHERE @id_broker_002 IS NOT NULL
  AND @id_agente_004 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM broker_agente
      WHERE id_agente = @id_agente_004
        AND estado = 'A'
  );

INSERT INTO reasignacion_agente_broker (
    fecha_cambio,
    motivo,
    id_agente,
    id_broker_anterior,
    id_broker_nuevo,
    id_broker_administrador
)
SELECT
    '2026-06-01 09:00:00',
    'Balance de cartera para campana norte/este',
    @id_agente_004,
    @id_broker_001,
    @id_broker_002,
    @id_broker_admin
WHERE @id_agente_004 IS NOT NULL
  AND @id_broker_001 IS NOT NULL
  AND @id_broker_002 IS NOT NULL
  AND @id_broker_admin IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM reasignacion_agente_broker
      WHERE id_agente = @id_agente_004
        AND fecha_cambio = '2026-06-01 09:00:00'
  );

-- =========================================================
-- Locales comerciales
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_local_operativo;

CREATE TEMPORARY TABLE seed_local_operativo (
    codigo_local VARCHAR(20) NOT NULL,
    propietario_doc VARCHAR(30) NOT NULL,
    direccion VARCHAR(200) NOT NULL,
    distrito_nombre VARCHAR(100) NOT NULL,
    metraje DECIMAL(10,2) NOT NULL,
    precio_referencial DECIMAL(12,2) NOT NULL,
    rubro_permitido VARCHAR(120) NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    estado CHAR(1) NOT NULL,
    tipo_inmueble CHAR(1) NOT NULL,
    ambientes INT NOT NULL,
    antiguedad_anios INT NOT NULL,
    zona_urbanizacion VARCHAR(150) NOT NULL,
    geo_lat DECIMAL(10,7) NULL,
    geo_long DECIMAL(10,7) NULL,
    frente DECIMAL(8,2) NULL,
    zonificacion VARCHAR(40) NOT NULL,
    apto_licencia_funcionamiento BOOLEAN NOT NULL,
    carga_electrica_kw DECIMAL(8,2) NULL,
    numero_estacionamientos INT NOT NULL,
    cuota_mantenimiento DECIMAL(10,2) NULL
) ENGINE=MEMORY;

INSERT INTO seed_local_operativo VALUES
    ('LOC-LIM-2026-001', '20580000001', 'Av. La Marina 1532, tienda 101', 'San Miguel', 78.50, 6800.00, 'Comercio vecinal', 'Local con frente a avenida y alto flujo peatonal.', 'D', 'L', 2, 8, 'Maranga', -12.0784250, -77.0907310, 7.50, 'CZ', TRUE, 20.00, 1, 350.00),
    ('LOC-LIM-2026-002', '20580000001', 'Jr. Junin 425, segundo nivel', 'Lima', 120.00, 9500.00, 'Showroom y oficina comercial', 'Inmueble con acceso independiente y operacion cerrada.', 'N', 'O', 4, 12, 'Centro Historico', -12.0452140, -77.0281220, 9.20, 'ZTE-1', TRUE, 30.00, 0, 480.00),
    ('LOC-LIM-2026-003', '20580000027', 'Av. Aviacion 2450, local 3', 'San Borja', 95.00, 8200.00, 'Salud, estetica y servicios', 'Local en esquina cerca a estacion de tren.', 'D', 'L', 3, 6, 'San Borja Norte', -12.0972100, -77.0046500, 8.10, 'CZ', TRUE, 25.00, 2, 420.00),
    ('LOC-LIM-2026-004', '70110001', 'Av. Alfredo Benavides 3890', 'Santiago de Surco', 62.00, 7200.00, 'Cafeteria y servicios rapidos', 'Local compacto para marca de comida o cafe.', 'D', 'L', 2, 4, 'Higuereta', -12.1287600, -76.9992200, 6.20, 'CZ', TRUE, 18.00, 1, 300.00),
    ('LOC-LIM-2026-005', '70110004', 'Av. Mexico 1201', 'La Victoria', 180.00, 12500.00, 'Almacen ligero y showroom', 'Amplio metraje con acceso para carga liviana.', 'D', 'L', 5, 15, 'Santa Catalina', -12.0745100, -77.0187400, 11.00, 'CM', TRUE, 45.00, 2, 650.00),
    ('LOC-LIM-2026-006', '20580000051', 'Av. Universitaria 5120', 'Los Olivos', 110.00, 7600.00, 'Farmacia y conveniencia', 'Local a pie de avenida con estacionamiento frontal.', 'D', 'L', 3, 7, 'Palmeras', -11.9813200, -77.0731100, 9.00, 'CZ', TRUE, 28.00, 3, 390.00),
    ('LOC-LIM-2026-007', '20580000027', 'Calle Las Begonias 441, piso 2', 'San Isidro', 140.00, 15500.00, 'Oficina comercial y coworking', 'Oficina implementada en zona empresarial.', 'D', 'O', 6, 10, 'Centro Financiero', -12.0931800, -77.0278800, 10.00, 'CZ', TRUE, 35.00, 2, 900.00),
    ('LOC-LIM-2026-008', '70110006', 'Av. Jose Larco 812', 'Miraflores', 85.00, 11000.00, 'Retail especializado', 'Local vitrina en zona de alto transito turistico.', 'N', 'L', 2, 9, 'Larco', -12.1239400, -77.0308800, 7.00, 'CZ', TRUE, 22.00, 0, 520.00),
    ('LOC-LIM-2026-009', '70110004', 'Av. Proceres 640', 'San Juan de Miraflores', 70.00, 4800.00, 'Servicios barriales', 'Local economico para servicios de cercania.', 'D', 'L', 2, 18, 'Ciudad de Dios', -12.1579400, -76.9709100, 5.80, 'CZ', TRUE, 15.00, 0, 180.00),
    ('LOC-LIM-2026-010', '20580000051', 'Av. Primavera 1245', 'Santiago de Surco', 100.00, 9800.00, 'Restaurante y minimarket', 'Local con buena fachada y alto flujo vehicular.', 'D', 'L', 3, 5, 'Monterrico', -12.1098300, -76.9756200, 8.50, 'CZ', TRUE, 32.00, 2, 430.00),
    ('LOC-LIM-2026-011', '20580000078', 'Av. Salaverry 2055', 'Jesus Maria', 88.00, 8600.00, 'Servicios y educacion', 'Local en avenida con vereda amplia y buena visibilidad.', 'D', 'L', 3, 6, 'San Felipe', -12.0855400, -77.0492600, 8.00, 'CZ', TRUE, 26.00, 1, 360.00),
    ('LOC-LIM-2026-012', '70110008', 'Av. Arequipa 2310, oficina 201', 'Lince', 130.00, 10800.00, 'Oficina comercial', 'Oficina implementada en eje corporativo de Arequipa.', 'D', 'O', 5, 9, 'Lince Centro', -12.0876200, -77.0361500, 9.50, 'CZ', TRUE, 33.00, 2, 700.00);

INSERT INTO local_comercial (
    codigo_local,
    direccion,
    distrito,
    metraje,
    precio_referencial,
    rubro_permitido,
    descripcion,
    estado,
    id_propietario,
    tipo_inmueble,
    uso,
    ambientes,
    antiguedad_anios,
    zona_urbanizacion,
    geo_lat,
    geo_long,
    frente,
    zonificacion,
    apto_licencia_funcionamiento,
    carga_electrica_kw,
    numero_estacionamientos,
    cuota_mantenimiento,
    id_distrito
)
SELECT
    l.codigo_local,
    l.direccion,
    l.distrito_nombre,
    l.metraje,
    l.precio_referencial,
    l.rubro_permitido,
    l.descripcion,
    l.estado,
    pr.id_propietario,
    l.tipo_inmueble,
    'C',
    l.ambientes,
    l.antiguedad_anios,
    l.zona_urbanizacion,
    l.geo_lat,
    l.geo_long,
    l.frente,
    l.zonificacion,
    l.apto_licencia_funcionamiento,
    l.carga_electrica_kw,
    l.numero_estacionamientos,
    l.cuota_mantenimiento,
    d.id_distrito
FROM seed_local_operativo l
INNER JOIN persona pp ON pp.numero_documento = l.propietario_doc
INNER JOIN propietario pr ON pr.id_persona = pp.id_persona
LEFT JOIN distrito d ON d.nombre = l.distrito_nombre
ON DUPLICATE KEY UPDATE
    direccion = VALUES(direccion),
    distrito = VALUES(distrito),
    metraje = VALUES(metraje),
    precio_referencial = VALUES(precio_referencial),
    rubro_permitido = VALUES(rubro_permitido),
    descripcion = VALUES(descripcion),
    estado = VALUES(estado),
    id_propietario = VALUES(id_propietario),
    tipo_inmueble = VALUES(tipo_inmueble),
    uso = VALUES(uso),
    ambientes = VALUES(ambientes),
    antiguedad_anios = VALUES(antiguedad_anios),
    zona_urbanizacion = VALUES(zona_urbanizacion),
    geo_lat = VALUES(geo_lat),
    geo_long = VALUES(geo_long),
    frente = VALUES(frente),
    zonificacion = VALUES(zonificacion),
    apto_licencia_funcionamiento = VALUES(apto_licencia_funcionamiento),
    carga_electrica_kw = VALUES(carga_electrica_kw),
    numero_estacionamientos = VALUES(numero_estacionamientos),
    cuota_mantenimiento = VALUES(cuota_mantenimiento),
    id_distrito = VALUES(id_distrito);

-- =========================================================
-- Precios de referencia y publicaciones
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_precio_operativo;

CREATE TEMPORARY TABLE seed_precio_operativo (
    codigo_local VARCHAR(20) NOT NULL,
    hito CHAR(1) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    fecha DATE NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_precio_operativo VALUES
    ('LOC-LIM-2026-001', 'P', 'PEN', 6800.00, '2026-01-15'),
    ('LOC-LIM-2026-002', 'C', 'PEN', 9000.00, '2026-03-20'),
    ('LOC-LIM-2026-003', 'R', 'PEN', 8000.00, '2026-04-02'),
    ('LOC-LIM-2026-004', 'E', 'PEN', 7200.00, '2026-04-10'),
    ('LOC-LIM-2026-006', 'P', 'PEN', 7600.00, '2026-05-03'),
    ('LOC-LIM-2026-007', 'P', 'USD', 4100.00, '2026-05-09'),
    ('LOC-LIM-2026-010', 'O', 'PEN', 9400.00, '2026-05-18'),
    ('LOC-LIM-2026-005', 'P', 'PEN', 12500.00, '2026-04-15'),
    ('LOC-LIM-2026-008', 'P', 'PEN', 11000.00, '2026-03-12'),
    ('LOC-LIM-2026-009', 'R', 'PEN', 4600.00, '2025-10-01'),
    ('LOC-LIM-2026-011', 'P', 'PEN', 8600.00, '2026-05-25'),
    ('LOC-LIM-2026-012', 'P', 'PEN', 10800.00, '2026-05-28');

INSERT INTO precio_local (id_local, hito, moneda, monto, fecha)
SELECT
    l.id_local,
    p.hito,
    p.moneda,
    p.monto,
    p.fecha
FROM seed_precio_operativo p
INNER JOIN local_comercial l ON l.codigo_local = p.codigo_local
WHERE NOT EXISTS (
    SELECT 1
    FROM precio_local px
    WHERE px.id_local = l.id_local
      AND px.hito = p.hito
      AND px.fecha = p.fecha
);

DROP TEMPORARY TABLE IF EXISTS seed_publicacion_operativo;

CREATE TEMPORARY TABLE seed_publicacion_operativo (
    codigo_origen VARCHAR(50) NOT NULL,
    codigo_local VARCHAR(20) NOT NULL,
    canal VARCHAR(30) NOT NULL,
    titulo_anuncio VARCHAR(200) NOT NULL,
    renta_publicada DECIMAL(12,2) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    inversion_pauta DECIMAL(12,2) NOT NULL,
    fecha_publicacion DATETIME NOT NULL,
    fecha_baja DATETIME NULL,
    estado CHAR(1) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_publicacion_operativo VALUES
    ('PUB-LIM-2026-001', 'LOC-LIM-2026-001', 'URBANIA', 'Local comercial en avenida principal de San Miguel', 6800.00, 'PEN', 180.00, '2026-01-15 08:00:00', NULL, 'P'),
    ('PUB-LIM-2026-002', 'LOC-LIM-2026-002', 'WEB_PROPIA', 'Oficina comercial en el Centro de Lima', 9500.00, 'PEN', 0.00, '2026-02-05 08:00:00', '2026-03-20 18:00:00', 'C'),
    ('PUB-LIM-2026-003', 'LOC-LIM-2026-003', 'ADONDEVIVIR', 'Local para consultorio o servicios en San Borja', 8200.00, 'PEN', 220.00, '2026-04-03 09:00:00', NULL, 'P'),
    ('PUB-LIM-2026-004', 'LOC-LIM-2026-006', 'FACEBOOK', 'Local con estacionamientos en Los Olivos', 7600.00, 'PEN', 160.00, '2026-05-04 09:00:00', NULL, 'P'),
    ('PUB-LIM-2026-005', 'LOC-LIM-2026-007', 'WEB_PROPIA', 'Oficina implementada en San Isidro', 4100.00, 'USD', 0.00, '2026-05-10 09:00:00', NULL, 'P'),
    ('PUB-LIM-2026-006', 'LOC-LIM-2026-010', 'INSTAGRAM', 'Local comercial en Primavera Surco', 9800.00, 'PEN', 190.00, '2026-05-20 09:00:00', NULL, 'P'),
    ('PUB-LIM-2026-007', 'LOC-LIM-2026-004', 'URBANIA', 'Local para cafeteria en Benavides Surco', 7200.00, 'PEN', 150.00, '2026-04-11 09:00:00', NULL, 'P'),
    ('PUB-LIM-2026-008', 'LOC-LIM-2026-005', 'PROPERATI', 'Almacen y showroom en La Victoria', 12500.00, 'PEN', 200.00, '2026-04-16 09:00:00', NULL, 'P'),
    ('PUB-LIM-2026-009', 'LOC-LIM-2026-008', 'ADONDEVIVIR', 'Local vitrina en Larco Miraflores', 11000.00, 'PEN', 210.00, '2026-03-13 09:00:00', '2026-05-31 18:00:00', 'B'),
    ('PUB-LIM-2026-010', 'LOC-LIM-2026-009', 'MARKETPLACE', 'Local economico en San Juan de Miraflores', 4800.00, 'PEN', 80.00, '2025-09-26 09:00:00', '2026-03-31 18:00:00', 'C'),
    ('PUB-LIM-2026-011', 'LOC-LIM-2026-011', 'FACEBOOK', 'Local en Salaverry Jesus Maria', 8600.00, 'PEN', 170.00, '2026-05-26 09:00:00', NULL, 'P'),
    ('PUB-LIM-2026-012', 'LOC-LIM-2026-012', 'WEB_PROPIA', 'Oficina comercial en Arequipa Lince', 10800.00, 'PEN', 0.00, '2026-05-29 09:00:00', NULL, 'P');

INSERT INTO publicacion (
    id_local,
    canal,
    url_publicacion,
    version_anuncio,
    titulo_anuncio,
    renta_publicada,
    moneda,
    inversion_pauta,
    codigo_origen,
    fecha_publicacion,
    fecha_baja,
    estado
)
SELECT
    l.id_local,
    p.canal,
    CONCAT('https://cartera.controllocal.pe/publicaciones/', LOWER(p.codigo_origen)),
    1,
    p.titulo_anuncio,
    p.renta_publicada,
    p.moneda,
    p.inversion_pauta,
    p.codigo_origen,
    p.fecha_publicacion,
    p.fecha_baja,
    p.estado
FROM seed_publicacion_operativo p
INNER JOIN local_comercial l ON l.codigo_local = p.codigo_local
WHERE NOT EXISTS (
    SELECT 1
    FROM publicacion px
    WHERE px.codigo_origen = p.codigo_origen
);

-- =========================================================
-- Captaciones y reasignacion de captacion
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_captacion_operativo;

CREATE TEMPORARY TABLE seed_captacion_operativo (
    codigo_captacion VARCHAR(20) NOT NULL,
    codigo_local VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    codigo_broker_revisor VARCHAR(20) NULL,
    fecha_captacion DATE NOT NULL,
    fecha_inicio_vigencia DATE NULL,
    fecha_fin_vigencia DATE NULL,
    comision_pactada DECIMAL(10,2) NOT NULL,
    observaciones VARCHAR(500) NOT NULL,
    estado CHAR(1) NOT NULL,
    fecha_revision DATETIME NULL,
    observacion_revision VARCHAR(500) NULL,
    urgencia INT NULL,
    exclusividad BOOLEAN NULL
) ENGINE=MEMORY;

-- comision_pactada es un PORCENTAJE (ej. 5.00 = 5%); la app la muestra como "%"
-- y el contrato calcula comision = renta * %comision_pactada.
INSERT INTO seed_captacion_operativo VALUES
    ('CAP-LIM-2026-001', 'LOC-LIM-2026-001', 'AGE-001', 'BRK-001', '2026-01-10', '2026-01-10', '2026-12-31', 5.00, 'Captacion activa de Valentina Mora.', 'A', '2026-01-11 10:00:00', 'Documentacion conforme.', 3, TRUE),
    ('CAP-LIM-2026-002', 'LOC-LIM-2026-002', 'AGE-001', 'BRK-001', '2026-02-01', '2026-02-01', '2027-01-31', 5.00, 'Captacion asociada a contrato cerrado.', 'A', '2026-02-02 09:30:00', 'Aprobada para publicacion.', 4, TRUE),
    ('CAP-LIM-2026-003', 'LOC-LIM-2026-003', 'AGE-002', 'BRK-001', '2026-04-01', '2026-04-01', '2027-03-31', 4.50, 'Captacion reasignada de Valentina a Javier.', 'A', '2026-04-02 15:00:00', 'Reasignada por capacidad comercial.', 5, TRUE),
    ('CAP-LIM-2026-004', 'LOC-LIM-2026-004', 'AGE-003', NULL, '2026-04-10', '2026-04-10', '2027-04-09', 5.00, 'Pendiente de revision del broker.', 'P', NULL, NULL, 2, FALSE),
    ('CAP-LIM-2026-005', 'LOC-LIM-2026-005', 'AGE-003', 'BRK-002', '2026-04-15', '2026-04-15', '2027-04-14', 4.00, 'Observada por fotos incompletas.', 'O', '2026-04-16 11:00:00', 'Completar fotos interiores y zonificacion.', 4, FALSE),
    ('CAP-LIM-2026-006', 'LOC-LIM-2026-006', 'AGE-004', 'BRK-002', '2026-05-02', '2026-05-02', '2027-05-01', 5.00, 'Captacion activa de Camila Reyes.', 'A', '2026-05-03 12:00:00', 'Aprobada.', 3, TRUE),
    ('CAP-LIM-2026-007', 'LOC-LIM-2026-007', 'AGE-002', 'BRK-001', '2026-05-08', '2026-05-08', '2027-05-07', 4.00, 'Oficina activa para cartera corporativa.', 'A', '2026-05-09 12:00:00', 'Aprobada.', 2, FALSE),
    ('CAP-LIM-2026-008', 'LOC-LIM-2026-008', 'AGE-004', 'BRK-002', '2026-03-01', '2026-03-01', '2026-05-31', 5.00, 'Captacion cerrada por retiro del propietario.', 'C', '2026-03-02 09:00:00', 'Cierre solicitado por propietario.', 1, FALSE),
    ('CAP-LIM-2026-009', 'LOC-LIM-2026-009', 'AGE-001', 'BRK-001', '2025-10-01', '2025-10-01', '2026-03-31', 6.00, 'Captacion vencida para validar alertas.', 'V', '2025-10-02 09:00:00', 'Vigencia vencida.', 1, FALSE),
    ('CAP-LIM-2026-010', 'LOC-LIM-2026-010', 'AGE-003', 'BRK-002', '2026-05-18', '2026-05-18', '2027-05-17', 4.50, 'Captacion activa con oportunidad reciente.', 'A', '2026-05-19 14:00:00', 'Aprobada para pauta digital.', 4, TRUE),
    ('CAP-LIM-2026-011', 'LOC-LIM-2026-011', 'AGE-005', 'BRK-001', '2026-05-25', '2026-05-25', '2027-05-24', 5.00, 'Captacion activa de Pedro Quispe.', 'A', '2026-05-26 10:00:00', 'Aprobada.', 3, TRUE),
    ('CAP-LIM-2026-012', 'LOC-LIM-2026-012', 'AGE-007', 'BRK-003', '2026-05-28', '2026-05-28', '2027-05-27', 4.00, 'Oficina captada para cartera corporativa.', 'A', '2026-05-29 11:00:00', 'Aprobada para publicacion.', 2, FALSE);

INSERT INTO captacion (
    codigo_captacion,
    fecha_captacion,
    fecha_inicio_vigencia,
    fecha_fin_vigencia,
    comision_pactada,
    observaciones,
    estado,
    fecha_revision,
    observacion_revision,
    id_local,
    id_agente,
    id_broker_revisor,
    motivo_operacion,
    urgencia,
    exclusividad
)
SELECT
    c.codigo_captacion,
    c.fecha_captacion,
    c.fecha_inicio_vigencia,
    c.fecha_fin_vigencia,
    c.comision_pactada,
    c.observaciones,
    c.estado,
    c.fecha_revision,
    c.observacion_revision,
    l.id_local,
    a.id_agente,
    b.id_broker,
    'A',
    c.urgencia,
    c.exclusividad
FROM seed_captacion_operativo c
INNER JOIN local_comercial l ON l.codigo_local = c.codigo_local
INNER JOIN agente_inmobiliario a ON a.codigo_agente = c.codigo_agente
LEFT JOIN broker b ON b.codigo_broker = c.codigo_broker_revisor
ON DUPLICATE KEY UPDATE
    fecha_captacion = VALUES(fecha_captacion),
    fecha_inicio_vigencia = VALUES(fecha_inicio_vigencia),
    fecha_fin_vigencia = VALUES(fecha_fin_vigencia),
    comision_pactada = VALUES(comision_pactada),
    observaciones = VALUES(observaciones),
    estado = VALUES(estado),
    fecha_revision = VALUES(fecha_revision),
    observacion_revision = VALUES(observacion_revision),
    id_local = VALUES(id_local),
    id_agente = VALUES(id_agente),
    id_broker_revisor = VALUES(id_broker_revisor),
    motivo_operacion = VALUES(motivo_operacion),
    urgencia = VALUES(urgencia),
    exclusividad = VALUES(exclusividad);

SET @id_cap_operativo_003 = (SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-LIM-2026-003' LIMIT 1);
SET @id_agente_001 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-001' LIMIT 1);
SET @id_agente_002 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-002' LIMIT 1);
SET @id_broker_001 = (SELECT id_broker FROM broker WHERE codigo_broker = 'BRK-001' LIMIT 1);

INSERT INTO reasignacion_captacion (
    fecha_cambio,
    motivo,
    id_captacion,
    id_agente_anterior,
    id_agente_nuevo,
    id_broker
)
SELECT
    '2026-04-12 10:30:00',
    'Javier asumio seguimiento por especialidad en salud y servicios.',
    @id_cap_operativo_003,
    @id_agente_001,
    @id_agente_002,
    @id_broker_001
WHERE @id_cap_operativo_003 IS NOT NULL
  AND @id_agente_001 IS NOT NULL
  AND @id_agente_002 IS NOT NULL
  AND @id_broker_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM reasignacion_captacion
      WHERE id_captacion = @id_cap_operativo_003
        AND fecha_cambio = '2026-04-12 10:30:00'
  );

-- =========================================================
-- Prospecciones
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_prospeccion_operativo;

CREATE TEMPORARY TABLE seed_prospeccion_operativo (
    codigo_prospeccion VARCHAR(20) NOT NULL,
    codigo_local VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    codigo_captacion VARCHAR(20) NULL,
    fecha_registro DATETIME NOT NULL,
    estado CHAR(1) NOT NULL,
    resultado_propuesta CHAR(1) NULL,
    fecha_contacto DATE NULL,
    fecha_reunion DATE NULL,
    fecha_propuesta DATE NULL,
    fecha_recontacto DATE NULL,
    observaciones VARCHAR(500) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_prospeccion_operativo VALUES
    ('PRO-LIM-2026-001', 'LOC-LIM-2026-001', 'AGE-001', 'CAP-LIM-2026-001', '2026-01-05 09:00:00', 'T', 'A', '2026-01-05', '2026-01-07', '2026-01-09', NULL, 'Prospeccion convertida en captacion.'),
    ('PRO-LIM-2026-002', 'LOC-LIM-2026-004', 'AGE-003', NULL, '2026-04-06 09:00:00', 'R', NULL, '2026-04-06', '2026-04-08', NULL, NULL, 'Reunion pendiente de propuesta final.'),
    ('PRO-LIM-2026-003', 'LOC-LIM-2026-006', 'AGE-004', 'CAP-LIM-2026-006', '2026-04-24 10:00:00', 'T', 'A', '2026-04-24', '2026-04-27', '2026-05-01', NULL, 'Propietario acepto pauta digital.'),
    ('PRO-LIM-2026-004', 'LOC-LIM-2026-009', 'AGE-001', NULL, '2025-09-20 10:00:00', 'D', 'R', '2025-09-20', '2025-09-23', '2025-09-25', NULL, 'Descartada inicialmente por precio.'),
    ('PRO-LIM-2026-005', 'LOC-LIM-2026-010', 'AGE-003', 'CAP-LIM-2026-010', '2026-05-10 10:00:00', 'T', 'A', '2026-05-10', '2026-05-12', '2026-05-17', NULL, 'Prospeccion captada para campana Primavera.'),
    ('PRO-LIM-2026-006', 'LOC-LIM-2026-007', 'AGE-002', 'CAP-LIM-2026-007', '2026-05-02 09:00:00', 'T', 'A', '2026-05-02', '2026-05-05', '2026-05-07', NULL, 'Oficina captada para cartera corporativa.'),
    ('PRO-LIM-2026-007', 'LOC-LIM-2026-011', 'AGE-005', 'CAP-LIM-2026-011', '2026-05-20 09:00:00', 'T', 'A', '2026-05-20', '2026-05-22', '2026-05-24', NULL, 'Propietario acepto captacion en Jesus Maria.'),
    ('PRO-LIM-2026-008', 'LOC-LIM-2026-012', 'AGE-007', 'CAP-LIM-2026-012', '2026-05-23 09:00:00', 'T', 'A', '2026-05-23', '2026-05-26', '2026-05-27', NULL, 'Oficina de Lince captada para corporativo.'),
    ('PRO-LIM-2026-009', 'LOC-LIM-2026-005', 'AGE-003', NULL, '2026-04-10 09:00:00', 'E', 'P', '2026-04-10', '2026-04-12', '2026-04-14', '2026-04-14', 'Propuesta enviada al propietario; sin nueva accion: recontacto vencido.'),
    ('PRO-LIM-2026-010', 'LOC-LIM-2026-008', 'AGE-004', NULL, '2026-06-01 09:00:00', 'S', 'S', '2026-06-01', '2026-06-03', NULL, '2026-06-22', 'Ultimo seguimiento reciente con el propietario; recontacto al dia.'),
    ('PRO-LIM-2026-011', 'LOC-LIM-2026-002', 'AGE-001', 'CAP-LIM-2026-002', '2026-01-26 09:00:00', 'T', 'A', '2026-01-26', '2026-01-29', '2026-01-31', NULL, 'Captacion cerrada para showroom del Centro.'),
    ('PRO-LIM-2026-012', 'LOC-LIM-2026-003', 'AGE-002', 'CAP-LIM-2026-003', '2026-03-25 09:00:00', 'T', 'A', '2026-03-25', '2026-03-28', '2026-03-31', NULL, 'Prospeccion de San Borja convertida en captacion.');

INSERT INTO prospeccion (
    codigo_prospeccion,
    fecha_registro,
    estado,
    resultado_propuesta,
    fecha_contacto,
    fecha_reunion,
    fecha_propuesta,
    fecha_recontacto,
    observaciones,
    id_local,
    id_agente,
    id_captacion
)
SELECT
    p.codigo_prospeccion,
    p.fecha_registro,
    p.estado,
    p.resultado_propuesta,
    p.fecha_contacto,
    p.fecha_reunion,
    p.fecha_propuesta,
    p.fecha_recontacto,
    p.observaciones,
    l.id_local,
    a.id_agente,
    c.id_captacion
FROM seed_prospeccion_operativo p
INNER JOIN local_comercial l ON l.codigo_local = p.codigo_local
INNER JOIN agente_inmobiliario a ON a.codigo_agente = p.codigo_agente
LEFT JOIN captacion c ON c.codigo_captacion = p.codigo_captacion
ON DUPLICATE KEY UPDATE
    fecha_registro = VALUES(fecha_registro),
    estado = VALUES(estado),
    resultado_propuesta = VALUES(resultado_propuesta),
    fecha_contacto = VALUES(fecha_contacto),
    fecha_reunion = VALUES(fecha_reunion),
    fecha_propuesta = VALUES(fecha_propuesta),
    fecha_recontacto = VALUES(fecha_recontacto),
    observaciones = VALUES(observaciones),
    id_local = VALUES(id_local),
    id_agente = VALUES(id_agente),
    id_captacion = VALUES(id_captacion);

-- =========================================================
-- Requerimientos de clientes
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_requerimiento_operativo;

CREATE TEMPORARY TABLE seed_requerimiento_operativo (
    cliente_doc VARCHAR(30) NOT NULL,
    rubro VARCHAR(80) NOT NULL,
    tipo_inmueble VARCHAR(30) NOT NULL,
    renta_min DECIMAL(12,2) NULL,
    renta_max DECIMAL(12,2) NULL,
    moneda VARCHAR(10) NOT NULL,
    metraje_min DECIMAL(10,2) NULL,
    metraje_max DECIMAL(10,2) NULL,
    frente_minimo DECIMAL(8,2) NULL,
    estado VARCHAR(20) NOT NULL,
    observaciones VARCHAR(500) NOT NULL,
    distrito_1 VARCHAR(100) NULL,
    distrito_2 VARCHAR(100) NULL
) ENGINE=MEMORY;

INSERT INTO seed_requerimiento_operativo VALUES
    ('20610000011', 'Minimarket', 'LOCAL_COMERCIAL', 5000.00, 7500.00, 'PEN', 60.00, 100.00, 6.00, 'ACTIVO', 'Busca avenida principal y licencia compatible.', 'San Miguel', 'Pueblo Libre'),
    ('20610000029', 'Moda y exhibicion', 'LOCAL_COMERCIAL', 8000.00, 11000.00, 'PEN', 90.00, 140.00, 7.00, 'CERRADO', 'Operacion cerrada en Centro de Lima.', 'Lima', NULL),
    ('20610000037', 'Clinica dental', 'LOCAL_COMERCIAL', 7500.00, 9500.00, 'PEN', 80.00, 120.00, 6.00, 'ACTIVO', 'Necesita zonificacion para servicios de salud.', 'San Borja', 'Surquillo'),
    ('20610000045', 'Cafeteria', 'LOCAL_COMERCIAL', 6000.00, 7800.00, 'PEN', 50.00, 80.00, 5.00, 'ACTIVO', 'Prioriza flujo peatonal y terraza.', 'Barranco', 'Santiago de Surco'),
    ('20610000053', 'Coworking', 'OFICINA', 12000.00, 18000.00, 'PEN', 120.00, 180.00, 8.00, 'ACTIVO', 'Busca oficina implementada para 25 puestos.', 'San Isidro', 'Miraflores'),
    ('20610000061', 'Farmacia', 'LOCAL_COMERCIAL', 6500.00, 8500.00, 'PEN', 80.00, 130.00, 7.00, 'ACTIVO', 'Necesita alto transito y estacionamiento.', 'Los Olivos', 'Comas'),
    ('76000001', 'Boutique', 'LOCAL_COMERCIAL', 8000.00, 12000.00, 'PEN', 70.00, 110.00, 6.00, 'ACTIVO', 'Busca local vitrina en zona comercial.', 'Santiago de Surco', 'Miraflores'),
    ('76000002', 'Restaurante', 'LOCAL_COMERCIAL', 8500.00, 10500.00, 'PEN', 90.00, 130.00, 7.00, 'PAUSADO', 'Evaluando inversion inicial.', 'Santiago de Surco', NULL),
    ('20610000079', 'Gimnasio', 'LOCAL_COMERCIAL', 9000.00, 13000.00, 'PEN', 150.00, 220.00, 8.00, 'ACTIVO', 'Requiere altura libre y ducha.', 'Surquillo', 'Lince'),
    ('20610000087', 'Libreria', 'LOCAL_COMERCIAL', 4500.00, 6500.00, 'PEN', 50.00, 90.00, 5.00, 'ACTIVO', 'Cercania a colegios y universidades.', 'Jesus Maria', 'Pueblo Libre'),
    ('20610000095', 'Optica', 'LOCAL_COMERCIAL', 6000.00, 8000.00, 'PEN', 45.00, 75.00, 5.00, 'ACTIVO', 'Vitrina a calle y buena iluminacion.', 'Miraflores', 'San Isidro'),
    ('76000003', 'Salon de belleza', 'LOCAL_COMERCIAL', 4000.00, 6000.00, 'PEN', 40.00, 70.00, 4.50, 'ACTIVO', 'Zona residencial con estacionamiento.', 'Santiago de Surco', 'Surquillo'),
    ('76000004', 'Ferreteria', 'LOCAL_COMERCIAL', 5500.00, 8500.00, 'PEN', 80.00, 140.00, 7.00, 'ACTIVO', 'Acceso para carga y deposito.', 'Los Olivos', 'Comas');

INSERT INTO requerimiento_cliente (
    id_cliente,
    rubro,
    tipo_inmueble,
    renta_min,
    renta_max,
    moneda,
    metraje_min,
    metraje_max,
    frente_minimo,
    estado,
    observaciones
)
SELECT
    ci.id_cliente,
    r.rubro,
    r.tipo_inmueble,
    r.renta_min,
    r.renta_max,
    r.moneda,
    r.metraje_min,
    r.metraje_max,
    r.frente_minimo,
    r.estado,
    r.observaciones
FROM seed_requerimiento_operativo r
INNER JOIN persona p ON p.numero_documento = r.cliente_doc
INNER JOIN cliente_interesado ci ON ci.id_persona = p.id_persona
WHERE NOT EXISTS (
    SELECT 1
    FROM requerimiento_cliente rc
    WHERE rc.id_cliente = ci.id_cliente
      AND rc.rubro = r.rubro
      AND rc.estado = r.estado
);

INSERT INTO requerimiento_distrito (id_requerimiento, id_distrito)
SELECT rc.id_requerimiento, d.id_distrito
FROM seed_requerimiento_operativo r
INNER JOIN persona p ON p.numero_documento = r.cliente_doc
INNER JOIN cliente_interesado ci ON ci.id_persona = p.id_persona
INNER JOIN requerimiento_cliente rc
    ON rc.id_cliente = ci.id_cliente
   AND rc.rubro = r.rubro
   AND rc.estado = r.estado
INNER JOIN distrito d ON d.nombre = r.distrito_1
WHERE r.distrito_1 IS NOT NULL
ON DUPLICATE KEY UPDATE id_distrito = VALUES(id_distrito);

INSERT INTO requerimiento_distrito (id_requerimiento, id_distrito)
SELECT rc.id_requerimiento, d.id_distrito
FROM seed_requerimiento_operativo r
INNER JOIN persona p ON p.numero_documento = r.cliente_doc
INNER JOIN cliente_interesado ci ON ci.id_persona = p.id_persona
INNER JOIN requerimiento_cliente rc
    ON rc.id_cliente = ci.id_cliente
   AND rc.rubro = r.rubro
   AND rc.estado = r.estado
INNER JOIN distrito d ON d.nombre = r.distrito_2
WHERE r.distrito_2 IS NOT NULL
ON DUPLICATE KEY UPDATE id_distrito = VALUES(id_distrito);

-- =========================================================
-- Oportunidades
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_oportunidad_operativo;

CREATE TEMPORARY TABLE seed_oportunidad_operativo (
    codigo_oportunidad VARCHAR(20) NOT NULL,
    cliente_doc VARCHAR(30) NOT NULL,
    codigo_captacion VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    codigo_publicacion VARCHAR(50) NULL,
    fecha_registro DATETIME NOT NULL,
    estado CHAR(1) NOT NULL,
    fecha_actualizacion_estado DATETIME NULL,
    motivo_cierre VARCHAR(150) NULL,
    observaciones VARCHAR(500) NOT NULL,
    fuente_origen VARCHAR(30) NOT NULL,
    codigo_origen_capturado VARCHAR(50) NULL,
    fecha_primera_consulta DATETIME NOT NULL,
    fecha_cierre DATETIME NULL
) ENGINE=MEMORY;

INSERT INTO seed_oportunidad_operativo VALUES
    ('OPO-LIM-2026-001', '20610000011', 'CAP-LIM-2026-001', 'AGE-001', 'PUB-LIM-2026-001', '2026-02-10 10:15:00', 'A', NULL, NULL, 'Cliente activo en etapa de seguimiento.', 'PORTAL', 'PUB-LIM-2026-001', '2026-02-10 10:15:00', NULL),
    ('OPO-LIM-2026-002', '20610000029', 'CAP-LIM-2026-002', 'AGE-001', 'PUB-LIM-2026-002', '2026-02-12 11:30:00', 'F', '2026-03-20 17:00:00', NULL, 'Operacion cerrada exitosamente.', 'WEB_PROPIA', 'PUB-LIM-2026-002', '2026-02-12 11:30:00', '2026-03-20 17:00:00'),
    ('OPO-LIM-2026-003', '20610000037', 'CAP-LIM-2026-003', 'AGE-002', 'PUB-LIM-2026-003', '2026-04-14 15:20:00', 'S', '2026-04-22 12:00:00', NULL, 'Solicitud creada y en revision.', 'PORTAL', 'PUB-LIM-2026-003', '2026-04-14 15:20:00', NULL),
    ('OPO-LIM-2026-004', '20610000045', 'CAP-LIM-2026-004', 'AGE-003', NULL, '2026-04-18 13:00:00', 'A', NULL, NULL, 'Cliente quiere visitar antes de oferta.', 'REFERIDO', 'REF-CAFE-01', '2026-04-18 13:00:00', NULL),
    ('OPO-LIM-2026-005', '20610000053', 'CAP-LIM-2026-007', 'AGE-002', 'PUB-LIM-2026-005', '2026-05-12 16:00:00', 'N', '2026-05-18 10:00:00', 'Precio fuera de rango', 'Cliente no continua por presupuesto.', 'WEB_PROPIA', 'PUB-LIM-2026-005', '2026-05-12 16:00:00', '2026-05-18 10:00:00'),
    ('OPO-LIM-2026-006', '20610000061', 'CAP-LIM-2026-006', 'AGE-004', 'PUB-LIM-2026-004', '2026-05-07 11:10:00', 'S', '2026-05-21 15:00:00', NULL, 'Solicitud registrada con documentos pendientes.', 'REDES_SOCIALES', 'PUB-LIM-2026-004', '2026-05-07 11:10:00', NULL),
    ('OPO-LIM-2026-007', '76000001', 'CAP-LIM-2026-010', 'AGE-003', 'PUB-LIM-2026-006', '2026-05-22 17:40:00', 'A', NULL, NULL, 'Cliente natural evalua boutique.', 'REDES_SOCIALES', 'PUB-LIM-2026-006', '2026-05-22 17:40:00', NULL),
    ('OPO-LIM-2026-008', '76000002', 'CAP-LIM-2026-001', 'AGE-001', 'PUB-LIM-2026-001', '2026-05-28 09:20:00', 'X', '2026-06-02 12:30:00', 'Desistio por inversion inicial', 'Cliente desistio antes de solicitud.', 'WHATSAPP', 'WA-76000002', '2026-05-28 09:20:00', '2026-06-02 12:30:00'),
    ('OPO-LIM-2026-009', '20610000079', 'CAP-LIM-2026-007', 'AGE-002', 'PUB-LIM-2026-005', '2026-05-14 10:00:00', 'S', '2026-05-26 12:00:00', NULL, 'Gimnasio con solicitud en evaluacion.', 'PORTAL', 'PUB-LIM-2026-005', '2026-05-14 10:00:00', NULL),
    ('OPO-LIM-2026-010', '20610000087', 'CAP-LIM-2026-010', 'AGE-003', 'PUB-LIM-2026-006', '2026-05-23 11:00:00', 'S', '2026-05-30 12:00:00', NULL, 'Libreria con solicitud generada.', 'PORTAL', 'PUB-LIM-2026-006', '2026-05-23 11:00:00', NULL),
    ('OPO-LIM-2026-011', '20610000095', 'CAP-LIM-2026-011', 'AGE-005', 'PUB-LIM-2026-011', '2026-05-27 09:30:00', 'S', '2026-06-01 12:00:00', NULL, 'Optica con solicitud en revision.', 'REDES_SOCIALES', 'PUB-LIM-2026-011', '2026-05-27 09:30:00', NULL),
    ('OPO-LIM-2026-012', '20610000102', 'CAP-LIM-2026-012', 'AGE-007', 'PUB-LIM-2026-012', '2026-05-30 10:00:00', 'S', '2026-06-03 12:00:00', NULL, 'Pet shop interesado en oficina de Lince.', 'WEB_PROPIA', 'PUB-LIM-2026-012', '2026-05-30 10:00:00', NULL),
    ('OPO-LIM-2026-013', '20610000118', 'CAP-LIM-2026-006', 'AGE-004', 'PUB-LIM-2026-004', '2026-05-15 11:00:00', 'S', '2026-05-24 12:00:00', NULL, 'Panaderia con solicitud aprobada.', 'WHATSAPP', 'WA-20610000118', '2026-05-15 11:00:00', NULL),
    ('OPO-LIM-2026-014', '20610000126', 'CAP-LIM-2026-003', 'AGE-002', 'PUB-LIM-2026-003', '2026-05-05 16:00:00', 'S', '2026-05-12 12:00:00', NULL, 'Academia con solicitud rechazada.', 'REFERIDO', 'REF-ACAD-01', '2026-05-05 16:00:00', NULL),
    ('OPO-LIM-2026-015', '76000003', 'CAP-LIM-2026-011', 'AGE-005', 'PUB-LIM-2026-011', '2026-06-02 09:00:00', 'S', '2026-06-05 12:00:00', NULL, 'Salon de belleza con solicitud desistida.', 'LLAMADA_DIRECTA', 'LL-76000003', '2026-06-02 09:00:00', NULL);

INSERT INTO oportunidad_comercial (
    codigo_oportunidad,
    fecha_registro,
    estado,
    fecha_actualizacion_estado,
    motivo_cierre,
    observaciones,
    id_cliente,
    id_captacion,
    id_agente,
    id_publicacion_origen,
    fuente_origen,
    codigo_origen_capturado,
    fecha_primera_consulta,
    fecha_cierre
)
SELECT
    o.codigo_oportunidad,
    o.fecha_registro,
    o.estado,
    o.fecha_actualizacion_estado,
    o.motivo_cierre,
    o.observaciones,
    ci.id_cliente,
    c.id_captacion,
    a.id_agente,
    p.id_publicacion,
    o.fuente_origen,
    o.codigo_origen_capturado,
    o.fecha_primera_consulta,
    o.fecha_cierre
FROM seed_oportunidad_operativo o
INNER JOIN persona pe ON pe.numero_documento = o.cliente_doc
INNER JOIN cliente_interesado ci ON ci.id_persona = pe.id_persona
INNER JOIN captacion c ON c.codigo_captacion = o.codigo_captacion
INNER JOIN agente_inmobiliario a ON a.codigo_agente = o.codigo_agente
LEFT JOIN publicacion p ON p.codigo_origen = o.codigo_publicacion
ON DUPLICATE KEY UPDATE
    fecha_registro = VALUES(fecha_registro),
    estado = VALUES(estado),
    fecha_actualizacion_estado = VALUES(fecha_actualizacion_estado),
    motivo_cierre = VALUES(motivo_cierre),
    observaciones = VALUES(observaciones),
    id_cliente = VALUES(id_cliente),
    id_captacion = VALUES(id_captacion),
    id_agente = VALUES(id_agente),
    id_publicacion_origen = VALUES(id_publicacion_origen),
    fuente_origen = VALUES(fuente_origen),
    codigo_origen_capturado = VALUES(codigo_origen_capturado),
    fecha_primera_consulta = VALUES(fecha_primera_consulta),
    fecha_cierre = VALUES(fecha_cierre);

-- =========================================================
-- Interacciones y visitas
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_interaccion_operativo;

CREATE TEMPORARY TABLE seed_interaccion_operativo (
    codigo_oportunidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    fecha_hora DATETIME NOT NULL,
    canal_contacto CHAR(1) NOT NULL,
    resultado VARCHAR(30) NOT NULL,
    observaciones VARCHAR(500) NOT NULL,
    transcripcion_nota VARCHAR(500) NULL
) ENGINE=MEMORY;

INSERT INTO seed_interaccion_operativo VALUES
    ('OPO-LIM-2026-001', 'AGE-001', '2026-02-10 10:20:00', 'W', 'INTERESADO', 'Se envio ficha comercial y ubicacion.', 'Cliente solicita coordinar una visita.'),
    ('OPO-LIM-2026-001', 'AGE-001', '2026-02-16 09:30:00', 'L', 'NEGOCIANDO', 'Cliente evaluara con socio.', 'Llamar nuevamente en tres dias.'),
    ('OPO-LIM-2026-003', 'AGE-002', '2026-04-15 12:00:00', 'E', 'INTERESADO', 'Se envio brochure y requisitos.', NULL),
    ('OPO-LIM-2026-004', 'AGE-003', '2026-04-18 13:30:00', 'R', 'VISITA_AGENDADA', 'Referido pide visita antes de propuesta.', NULL),
    ('OPO-LIM-2026-005', 'AGE-002', '2026-05-18 09:40:00', 'L', 'NO_INTERESADO', 'Cliente indica que la renta supera presupuesto.', NULL),
    ('OPO-LIM-2026-006', 'AGE-004', '2026-05-08 10:00:00', 'W', 'INTERESADO', 'Cliente interesado por local cerca de avenida.', NULL),
    ('OPO-LIM-2026-007', 'AGE-003', '2026-05-23 11:00:00', 'T', 'NEGOCIANDO', 'Contacto desde Instagram con dudas de metraje.', NULL),
    ('OPO-LIM-2026-008', 'AGE-001', '2026-06-02 12:00:00', 'W', 'DESCARTADO', 'Cliente desiste por inversion inicial.', NULL),
    ('OPO-LIM-2026-009', 'AGE-002', '2026-05-14 10:30:00', 'E', 'INTERESADO', 'Se envio propuesta comercial al gimnasio.', 'Solicita visita tecnica del local.'),
    ('OPO-LIM-2026-010', 'AGE-003', '2026-05-23 11:30:00', 'W', 'INTERESADO', 'Libreria pide ficha y condiciones de pago.', NULL),
    ('OPO-LIM-2026-011', 'AGE-005', '2026-05-27 10:00:00', 'L', 'NEGOCIANDO', 'Optica evalua metraje y vitrina.', 'Coordinar segunda llamada.'),
    ('OPO-LIM-2026-012', 'AGE-007', '2026-05-30 10:30:00', 'E', 'INTERESADO', 'Pet shop solicita planos de la oficina.', NULL),
    ('OPO-LIM-2026-013', 'AGE-004', '2026-05-15 11:30:00', 'W', 'OFERTA_SOLICITADA', 'Panaderia confirma interes y pide oferta.', NULL),
    ('OPO-LIM-2026-014', 'AGE-002', '2026-05-05 16:30:00', 'R', 'VISITA_AGENDADA', 'Referido de academia consulta disponibilidad.', NULL);

INSERT INTO interaccion_comercial (
    fecha_hora,
    canal_contacto,
    observaciones,
    resultado,
    id_oportunidad,
    id_agente,
    transcripcion_nota
)
SELECT
    i.fecha_hora,
    i.canal_contacto,
    i.observaciones,
    i.resultado,
    o.id_oportunidad,
    a.id_agente,
    i.transcripcion_nota
FROM seed_interaccion_operativo i
INNER JOIN oportunidad_comercial o ON o.codigo_oportunidad = i.codigo_oportunidad
INNER JOIN agente_inmobiliario a ON a.codigo_agente = i.codigo_agente
WHERE NOT EXISTS (
    SELECT 1
    FROM interaccion_comercial ix
    WHERE ix.id_oportunidad = o.id_oportunidad
      AND ix.fecha_hora = i.fecha_hora
);

DROP TEMPORARY TABLE IF EXISTS seed_visita_operativo;

CREATE TEMPORARY TABLE seed_visita_operativo (
    codigo_oportunidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    fecha_visita DATE NOT NULL,
    hora_visita TIME NOT NULL,
    observaciones VARCHAR(500) NOT NULL,
    estado CHAR(1) NOT NULL,
    resultado CHAR(1) NULL,
    nivel_interes INT NULL,
    objecion_principal CHAR(1) NULL,
    opinion_precio CHAR(1) NULL,
    proxima_accion CHAR(1) NULL
) ENGINE=MEMORY;

INSERT INTO seed_visita_operativo VALUES
    ('OPO-LIM-2026-001', 'AGE-001', '2026-02-14', '10:00:00', 'Visita realizada; cliente evalua distribucion.', 'R', 'I', 4, 'E', 'J', 'O'),
    ('OPO-LIM-2026-003', 'AGE-002', '2026-04-18', '11:00:00', 'Visita realizada; cliente pidio condiciones.', 'R', 'S', 5, 'C', 'J', 'O'),
    ('OPO-LIM-2026-004', 'AGE-003', '2026-04-21', '09:30:00', 'Visita pendiente de confirmacion.', 'P', NULL, NULL, NULL, NULL, NULL),
    ('OPO-LIM-2026-005', 'AGE-002', '2026-05-16', '16:00:00', 'Visita realizada; objecion principal precio.', 'R', 'N', 2, 'P', 'A', 'D'),
    ('OPO-LIM-2026-006', 'AGE-004', '2026-05-11', '10:30:00', 'Visita reprogramada por agenda del cliente.', 'G', NULL, NULL, NULL, NULL, NULL),
    ('OPO-LIM-2026-007', 'AGE-003', '2026-05-25', '12:00:00', 'Cliente cancelo por viaje.', 'C', NULL, NULL, NULL, NULL, NULL),
    ('OPO-LIM-2026-008', 'AGE-001', '2026-05-31', '18:00:00', 'No realizada; cliente no asistio.', 'N', NULL, NULL, NULL, NULL, NULL),
    ('OPO-LIM-2026-009', 'AGE-002', '2026-05-19', '10:00:00', 'Visita realizada; gimnasio evalua altura libre.', 'R', 'I', 4, 'E', 'J', 'O'),
    ('OPO-LIM-2026-010', 'AGE-003', '2026-05-27', '11:00:00', 'Visita realizada; libreria interesada.', 'R', 'S', 5, 'C', 'J', 'O'),
    ('OPO-LIM-2026-011', 'AGE-005', '2026-05-30', '09:30:00', 'Visita realizada; optica revisa vitrina.', 'R', 'I', 4, 'U', 'A', 'O'),
    ('OPO-LIM-2026-012', 'AGE-007', '2026-06-02', '11:00:00', 'Visita programada para la oficina de Lince.', 'P', NULL, NULL, NULL, NULL, NULL),
    ('OPO-LIM-2026-013', 'AGE-004', '2026-05-20', '10:00:00', 'Visita realizada; panaderia conforme.', 'R', 'S', 5, 'C', 'A', 'O');

INSERT INTO visita (
    fecha_visita,
    hora_visita,
    observaciones,
    estado,
    resultado,
    id_oportunidad,
    id_agente,
    nivel_interes,
    objecion_principal,
    opinion_precio,
    proxima_accion
)
SELECT
    v.fecha_visita,
    v.hora_visita,
    v.observaciones,
    v.estado,
    v.resultado,
    o.id_oportunidad,
    a.id_agente,
    v.nivel_interes,
    v.objecion_principal,
    v.opinion_precio,
    v.proxima_accion
FROM seed_visita_operativo v
INNER JOIN oportunidad_comercial o ON o.codigo_oportunidad = v.codigo_oportunidad
INNER JOIN agente_inmobiliario a ON a.codigo_agente = v.codigo_agente
WHERE NOT EXISTS (
    SELECT 1
    FROM visita vx
    WHERE vx.id_oportunidad = o.id_oportunidad
      AND vx.fecha_visita = v.fecha_visita
      AND vx.hora_visita = v.hora_visita
);

-- =========================================================
-- Solicitudes, documentos y evaluaciones
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_solicitud_operativo;

CREATE TEMPORARY TABLE seed_solicitud_operativo (
    codigo_solicitud VARCHAR(20) NOT NULL,
    codigo_oportunidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    fecha_registro DATE NOT NULL,
    monto_propuesto DECIMAL(12,2) NOT NULL,
    plazo_tentativo VARCHAR(50) NOT NULL,
    observaciones VARCHAR(500) NOT NULL,
    estado CHAR(1) NOT NULL,
    fecha_actualizacion_estado DATETIME NULL,
    fecha_vigencia_oferta DATE NULL
) ENGINE=MEMORY;

INSERT INTO seed_solicitud_operativo VALUES
    ('SOL-LIM-2026-001', 'OPO-LIM-2026-002', 'AGE-001', '2026-03-01', 9000.00, '24 meses', 'Oferta aceptada para contrato de alquiler.', 'A', '2026-03-10 16:00:00', '2026-03-15'),
    ('SOL-LIM-2026-002', 'OPO-LIM-2026-003', 'AGE-002', '2026-04-22', 8000.00, '36 meses', 'Solicitud en revision documental.', 'E', '2026-04-23 11:00:00', '2026-04-30'),
    ('SOL-LIM-2026-003', 'OPO-LIM-2026-006', 'AGE-004', '2026-05-21', 7400.00, '24 meses', 'Documentos pendientes de regularizacion.', 'O', '2026-05-22 10:00:00', '2026-05-29'),
    ('SOL-LIM-2026-004', 'OPO-LIM-2026-009', 'AGE-002', '2026-05-25', 14800.00, '36 meses', 'Solicitud de gimnasio en evaluacion documental.', 'E', '2026-05-26 12:00:00', '2026-06-05'),
    ('SOL-LIM-2026-005', 'OPO-LIM-2026-010', 'AGE-003', '2026-05-29', 9400.00, '24 meses', 'Solicitud de libreria en preparacion.', 'G', NULL, '2026-06-10'),
    ('SOL-LIM-2026-006', 'OPO-LIM-2026-011', 'AGE-005', '2026-05-31', 8300.00, '24 meses', 'Solicitud de optica en revision.', 'E', '2026-06-01 12:00:00', '2026-06-10'),
    ('SOL-LIM-2026-007', 'OPO-LIM-2026-012', 'AGE-007', '2026-06-02', 10500.00, '36 meses', 'Solicitud de oficina aprobada para contrato.', 'A', '2026-06-04 12:00:00', '2026-06-12'),
    ('SOL-LIM-2026-008', 'OPO-LIM-2026-013', 'AGE-004', '2026-05-22', 7400.00, '24 meses', 'Solicitud de panaderia aprobada.', 'A', '2026-05-24 12:00:00', '2026-06-01'),
    ('SOL-LIM-2026-009', 'OPO-LIM-2026-014', 'AGE-002', '2026-05-08', 8000.00, '24 meses', 'Solicitud de academia rechazada por evaluacion.', 'R', '2026-05-12 12:00:00', '2026-05-20'),
    ('SOL-LIM-2026-010', 'OPO-LIM-2026-015', 'AGE-005', '2026-06-03', 8200.00, '24 meses', 'Solicitud de salon desistida por el cliente.', 'D', '2026-06-05 12:00:00', '2026-06-13');

INSERT INTO solicitud_alquiler (
    codigo_solicitud,
    fecha_registro,
    monto_propuesto,
    plazo_tentativo,
    observaciones,
    estado,
    fecha_actualizacion_estado,
    fecha_vigencia_oferta,
    id_oportunidad,
    id_agente
)
SELECT
    s.codigo_solicitud,
    s.fecha_registro,
    s.monto_propuesto,
    s.plazo_tentativo,
    s.observaciones,
    s.estado,
    s.fecha_actualizacion_estado,
    s.fecha_vigencia_oferta,
    o.id_oportunidad,
    a.id_agente
FROM seed_solicitud_operativo s
INNER JOIN oportunidad_comercial o ON o.codigo_oportunidad = s.codigo_oportunidad
INNER JOIN agente_inmobiliario a ON a.codigo_agente = s.codigo_agente
ON DUPLICATE KEY UPDATE
    fecha_registro = VALUES(fecha_registro),
    monto_propuesto = VALUES(monto_propuesto),
    plazo_tentativo = VALUES(plazo_tentativo),
    observaciones = VALUES(observaciones),
    estado = VALUES(estado),
    fecha_actualizacion_estado = VALUES(fecha_actualizacion_estado),
    fecha_vigencia_oferta = VALUES(fecha_vigencia_oferta),
    id_oportunidad = VALUES(id_oportunidad),
    id_agente = VALUES(id_agente);

-- Condiciones del trato (plazo/fecha inicio/forma de pago/garantia/adelanto) capturadas en
-- la solicitud para las aprobadas; el contrato las hereda al cerrar (no se duplican en contrato).
UPDATE solicitud_alquiler SET plazo_contrato_meses = 24, fecha_inicio_contrato = '2026-04-01',
       forma_pago = 'TRANSFERENCIA', meses_garantia = 2, meses_adelanto = 1
WHERE codigo_solicitud = 'SOL-LIM-2026-001';
UPDATE solicitud_alquiler SET plazo_contrato_meses = 36, fecha_inicio_contrato = '2026-06-15',
       forma_pago = 'TRANSFERENCIA', meses_garantia = 2, meses_adelanto = 1
WHERE codigo_solicitud = 'SOL-LIM-2026-007';
UPDATE solicitud_alquiler SET plazo_contrato_meses = 24, fecha_inicio_contrato = '2026-06-05',
       forma_pago = 'TRANSFERENCIA', meses_garantia = 1, meses_adelanto = 1
WHERE codigo_solicitud = 'SOL-LIM-2026-008';

-- La carga inicial deja estas solicitudes sin archivos; la cartera masiva posterior
-- incluye documentos referenciales para revisar estados observados y validados.
-- (frontend -> backend -> almacen S3/disco) y la clave queda en ruta_archivo. Sembrar
-- metadatos con ruta_archivo NULL dejaba documentos "fantasma" que el visor no podia
-- abrir, asi que se omiten en este bloque inicial.

DROP TEMPORARY TABLE IF EXISTS seed_evaluacion_operativo;

CREATE TEMPORARY TABLE seed_evaluacion_operativo (
    codigo_solicitud VARCHAR(20) NOT NULL,
    codigo_broker VARCHAR(20) NOT NULL,
    fecha_evaluacion DATETIME NOT NULL,
    resultado CHAR(1) NOT NULL,
    tipo_evaluacion CHAR(1) NOT NULL,
    observaciones VARCHAR(500) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_evaluacion_operativo VALUES
    ('SOL-LIM-2026-001', 'BRK-001', '2026-03-10 16:00:00', 'A', 'F', 'Evaluacion final aprobada.'),
    ('SOL-LIM-2026-002', 'BRK-001', '2026-04-24 10:00:00', 'O', 'O', 'Falta vigencia de poder actualizada.'),
    ('SOL-LIM-2026-003', 'BRK-002', '2026-05-22 10:00:00', 'O', 'O', 'Sustento economico observado.'),
    ('SOL-LIM-2026-001', 'BRK-001', '2026-03-08 10:00:00', 'A', 'P', 'Evaluacion preliminar favorable.'),
    ('SOL-LIM-2026-002', 'BRK-001', '2026-04-23 11:30:00', 'O', 'P', 'Preliminar: validar documentacion del representante.'),
    ('SOL-LIM-2026-004', 'BRK-001', '2026-05-26 12:30:00', 'A', 'P', 'Preliminar favorable para gimnasio.'),
    ('SOL-LIM-2026-006', 'BRK-001', '2026-06-01 12:30:00', 'A', 'P', 'Preliminar en revision para optica.'),
    ('SOL-LIM-2026-007', 'BRK-003', '2026-06-03 10:00:00', 'A', 'P', 'Preliminar favorable para oficina de Lince.'),
    ('SOL-LIM-2026-007', 'BRK-003', '2026-06-04 12:00:00', 'A', 'F', 'Evaluacion final aprobada.'),
    ('SOL-LIM-2026-008', 'BRK-002', '2026-05-24 12:00:00', 'A', 'F', 'Evaluacion final aprobada para panaderia.'),
    ('SOL-LIM-2026-009', 'BRK-001', '2026-05-10 11:00:00', 'O', 'O', 'Observacion: ingresos insuficientes.'),
    ('SOL-LIM-2026-009', 'BRK-001', '2026-05-12 12:00:00', 'R', 'F', 'Evaluacion final rechazada.');

INSERT INTO evaluacion_solicitud (
    fecha_evaluacion,
    resultado,
    observaciones,
    responsable_evaluacion,
    tipo_evaluacion,
    id_solicitud
)
SELECT
    e.fecha_evaluacion,
    e.resultado,
    e.observaciones,
    b.id_broker,
    e.tipo_evaluacion,
    s.id_solicitud
FROM seed_evaluacion_operativo e
INNER JOIN solicitud_alquiler s ON s.codigo_solicitud = e.codigo_solicitud
INNER JOIN broker b ON b.codigo_broker = e.codigo_broker
WHERE NOT EXISTS (
    SELECT 1
    FROM evaluacion_solicitud ex
    WHERE ex.id_solicitud = s.id_solicitud
      AND ex.tipo_evaluacion = e.tipo_evaluacion
      AND ex.fecha_evaluacion = e.fecha_evaluacion
);

-- =========================================================
-- Motivos de no continuidad, contrato y comision
-- =========================================================

SET @id_opo_operativo_005 = (SELECT id_oportunidad FROM oportunidad_comercial WHERE codigo_oportunidad = 'OPO-LIM-2026-005' LIMIT 1);
SET @id_opo_operativo_008 = (SELECT id_oportunidad FROM oportunidad_comercial WHERE codigo_oportunidad = 'OPO-LIM-2026-008' LIMIT 1);
SET @id_age_operativo_001 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-001' LIMIT 1);
SET @id_age_operativo_002 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-002' LIMIT 1);
SET @id_interaccion_opo_005 = (
    SELECT i.id_interaccion
    FROM interaccion_comercial i
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = i.id_oportunidad
    WHERE o.codigo_oportunidad = 'OPO-LIM-2026-005'
      AND i.fecha_hora = '2026-05-18 09:40:00'
    LIMIT 1
);
SET @id_interaccion_opo_008 = (
    SELECT i.id_interaccion
    FROM interaccion_comercial i
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = i.id_oportunidad
    WHERE o.codigo_oportunidad = 'OPO-LIM-2026-008'
      AND i.fecha_hora = '2026-06-02 12:00:00'
    LIMIT 1
);

INSERT INTO motivo_no_continuidad (
    fecha_hora,
    razon_principal,
    observaciones,
    id_agente,
    id_oportunidad,
    id_interaccion,
    id_visita,
    id_solicitud
)
SELECT
    '2026-05-18 10:00:00',
    'P',
    'Cliente no continua por presupuesto.',
    @id_age_operativo_002,
    @id_opo_operativo_005,
    @id_interaccion_opo_005,
    NULL,
    NULL
WHERE @id_opo_operativo_005 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM motivo_no_continuidad
      WHERE id_oportunidad = @id_opo_operativo_005
  );

INSERT INTO motivo_no_continuidad (
    fecha_hora,
    razon_principal,
    observaciones,
    id_agente,
    id_oportunidad,
    id_interaccion,
    id_visita,
    id_solicitud
)
SELECT
    '2026-06-02 12:30:00',
    'O',
    'Desistio por inversion inicial.',
    @id_age_operativo_001,
    @id_opo_operativo_008,
    @id_interaccion_opo_008,
    NULL,
    NULL
WHERE @id_opo_operativo_008 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM motivo_no_continuidad
      WHERE id_oportunidad = @id_opo_operativo_008
  );

SET @id_opo_operativo_002 = (SELECT id_oportunidad FROM oportunidad_comercial WHERE codigo_oportunidad = 'OPO-LIM-2026-002' LIMIT 1);
SET @id_sol_operativo_001 = (SELECT id_solicitud FROM solicitud_alquiler WHERE codigo_solicitud = 'SOL-LIM-2026-001' LIMIT 1);

INSERT INTO contrato_alquiler (
    id_oportunidad,
    id_solicitud,
    fecha_cierre,
    estado_contrato,
    incidencias
)
SELECT
    @id_opo_operativo_002,
    @id_sol_operativo_001,
    '2026-03-20',
    'V',
    NULL
WHERE @id_opo_operativo_002 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM contrato_alquiler
      WHERE id_oportunidad = @id_opo_operativo_002
  );

SET @id_contrato_operativo = (
    SELECT id_contrato_alquiler
    FROM contrato_alquiler
    WHERE id_oportunidad = @id_opo_operativo_002
    LIMIT 1
);

INSERT INTO comision_liquidacion (
    id_contrato_alquiler,
    monto,
    moneda,
    monto_agente,
    monto_empresa,
    fecha_cobro,
    forma_pago,
    estado
)
SELECT
    @id_contrato_operativo,
    450.00,
    'PEN',
    382.50,
    67.50,
    '2026-03-25',
    'TRANSFERENCIA',
    'COBRADA'
WHERE @id_contrato_operativo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM comision_liquidacion
      WHERE id_contrato_alquiler = @id_contrato_operativo
  );

-- =========================================================
-- Reportes, tareas, alertas e historial
-- =========================================================

SET @id_cap_operativo_001 = (SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-LIM-2026-001' LIMIT 1);
SET @id_cap_operativo_006 = (SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-LIM-2026-006' LIMIT 1);
SET @id_age_operativo_003 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-003' LIMIT 1);
SET @id_age_operativo_004 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-004' LIMIT 1);

INSERT INTO reporte_propietario (
    id_captacion,
    id_agente,
    fecha_reporte,
    periodo_inicio,
    periodo_fin,
    consultas_reportadas,
    visitas_reportadas,
    objeciones_frecuentes,
    ajustes_recomendados,
    canal_envio
)
SELECT
    @id_cap_operativo_001,
    @id_age_operativo_001,
    '2026-02-28',
    '2026-02-01',
    '2026-02-28',
    8,
    2,
    'Distribucion interior y estacionamiento.',
    'Mantener precio y mejorar fotografias.',
    'E'
WHERE @id_cap_operativo_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM reporte_propietario
      WHERE id_captacion = @id_cap_operativo_001
        AND fecha_reporte = '2026-02-28'
  );

INSERT INTO reporte_propietario (
    id_captacion,
    id_agente,
    fecha_reporte,
    periodo_inicio,
    periodo_fin,
    consultas_reportadas,
    visitas_reportadas,
    objeciones_frecuentes,
    ajustes_recomendados,
    canal_envio
)
SELECT
    @id_cap_operativo_006,
    @id_age_operativo_004,
    '2026-05-31',
    '2026-05-01',
    '2026-05-31',
    11,
    3,
    'Accesibilidad y horario de carga.',
    'Preparar video corto para redes.',
    'W'
WHERE @id_cap_operativo_006 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM reporte_propietario
      WHERE id_captacion = @id_cap_operativo_006
        AND fecha_reporte = '2026-05-31'
  );

DROP TEMPORARY TABLE IF EXISTS seed_tarea_operativo;

CREATE TEMPORARY TABLE seed_tarea_operativo (
    tipo VARCHAR(30) NOT NULL,
    entidad_tipo VARCHAR(30) NOT NULL,
    codigo_entidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    fecha_programada DATETIME NOT NULL,
    fecha_recordatorio DATETIME NULL,
    estado VARCHAR(20) NOT NULL,
    prioridad VARCHAR(10) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_tarea_operativo VALUES
    ('SEGUIMIENTO', 'OPORTUNIDAD', 'OPO-LIM-2026-001', 'AGE-001', 'Confirmar decision del cliente despues de la visita.', '2026-06-16 09:00:00', '2026-06-16 08:30:00', 'PENDIENTE', 'ALTA'),
    ('ENVIAR_REVISION', 'CAPTACION', 'CAP-LIM-2026-004', 'AGE-003', 'Enviar captacion pendiente al broker.', '2026-06-17 10:00:00', '2026-06-17 09:30:00', 'PENDIENTE', 'MEDIA'),
    ('SUBIR_DOCUMENTOS', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-003', 'AGE-004', 'Subir sustento economico corregido.', '2026-06-18 11:00:00', '2026-06-18 10:30:00', 'EN_PROCESO', 'ALTA'),
    ('REPORTE_PROPIETARIO', 'CAPTACION', 'CAP-LIM-2026-006', 'AGE-004', 'Enviar reporte semanal al propietario.', '2026-06-19 16:00:00', '2026-06-19 15:30:00', 'PENDIENTE', 'MEDIA'),
    ('SEGUIMIENTO', 'OPORTUNIDAD', 'OPO-LIM-2026-007', 'AGE-003', 'Dar seguimiento a la boutique interesada.', '2026-06-16 10:00:00', '2026-06-16 09:30:00', 'PENDIENTE', 'ALTA'),
    ('LLAMADA', 'OPORTUNIDAD', 'OPO-LIM-2026-009', 'AGE-002', 'Llamar al gimnasio para coordinar visita tecnica.', '2026-06-17 09:00:00', '2026-06-17 08:30:00', 'PENDIENTE', 'MEDIA'),
    ('VISITA', 'OPORTUNIDAD', 'OPO-LIM-2026-012', 'AGE-007', 'Realizar visita a la oficina de Lince.', '2026-06-18 11:00:00', '2026-06-18 10:30:00', 'PENDIENTE', 'ALTA'),
    ('ENVIAR_REVISION', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-005', 'AGE-003', 'Enviar solicitud de libreria a evaluacion.', '2026-06-16 12:00:00', '2026-06-16 11:30:00', 'PENDIENTE', 'ALTA'),
    ('SUBIR_DOCUMENTOS', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-006', 'AGE-005', 'Completar documentos de la optica.', '2026-06-17 15:00:00', '2026-06-17 14:30:00', 'EN_PROCESO', 'ALTA'),
    ('ENVIO_INFO', 'OPORTUNIDAD', 'OPO-LIM-2026-010', 'AGE-003', 'Enviar ficha y condiciones a la libreria.', '2026-06-15 09:00:00', NULL, 'COMPLETADA', 'MEDIA'),
    ('RECONTACTO', 'OPORTUNIDAD', 'OPO-LIM-2026-005', 'AGE-002', 'Recontacto cancelado: cliente no continua.', '2026-05-25 09:00:00', NULL, 'CANCELADA', 'BAJA'),
    ('REGISTRAR_CAPTACION', 'CAPTACION', 'CAP-LIM-2026-012', 'AGE-007', 'Registrar captacion de la oficina de Lince.', '2026-05-28 09:00:00', NULL, 'COMPLETADA', 'MEDIA');

INSERT INTO tarea (
    tipo,
    entidad_tipo,
    entidad_id,
    id_agente,
    descripcion,
    fecha_programada,
    fecha_recordatorio,
    estado,
    prioridad
)
SELECT
    t.tipo,
    t.entidad_tipo,
    CASE
        WHEN t.entidad_tipo = 'OPORTUNIDAD' THEN o.id_oportunidad
        WHEN t.entidad_tipo = 'CAPTACION' THEN c.id_captacion
        WHEN t.entidad_tipo = 'SOLICITUD_ALQUILER' THEN s.id_solicitud
    END,
    a.id_agente,
    t.descripcion,
    t.fecha_programada,
    t.fecha_recordatorio,
    t.estado,
    t.prioridad
FROM seed_tarea_operativo t
INNER JOIN agente_inmobiliario a ON a.codigo_agente = t.codigo_agente
LEFT JOIN oportunidad_comercial o ON o.codigo_oportunidad = t.codigo_entidad
LEFT JOIN captacion c ON c.codigo_captacion = t.codigo_entidad
LEFT JOIN solicitud_alquiler s ON s.codigo_solicitud = t.codigo_entidad
WHERE CASE
        WHEN t.entidad_tipo = 'OPORTUNIDAD' THEN o.id_oportunidad
        WHEN t.entidad_tipo = 'CAPTACION' THEN c.id_captacion
        WHEN t.entidad_tipo = 'SOLICITUD_ALQUILER' THEN s.id_solicitud
      END IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM tarea tx
      WHERE tx.tipo = t.tipo
        AND tx.entidad_tipo = t.entidad_tipo
        AND tx.entidad_id = CASE
            WHEN t.entidad_tipo = 'OPORTUNIDAD' THEN o.id_oportunidad
            WHEN t.entidad_tipo = 'CAPTACION' THEN c.id_captacion
            WHEN t.entidad_tipo = 'SOLICITUD_ALQUILER' THEN s.id_solicitud
        END
        AND tx.estado = t.estado
  );

DROP TEMPORARY TABLE IF EXISTS seed_alerta_operativo;

CREATE TEMPORARY TABLE seed_alerta_operativo (
    tipo VARCHAR(30) NOT NULL,
    severidad VARCHAR(10) NOT NULL,
    entidad_tipo VARCHAR(30) NOT NULL,
    codigo_entidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    mensaje VARCHAR(300) NOT NULL,
    estado VARCHAR(15) NOT NULL,
    fecha_generacion DATETIME NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_alerta_operativo VALUES
    ('SIN_AVANCE', 'MEDIA', 'OPORTUNIDAD', 'OPO-LIM-2026-001', 'AGE-001', 'La oportunidad de Mercado Uno requiere seguimiento.', 'ACTIVA', '2026-06-13 09:00:00'),
    ('CAPTACION_VENCIDA', 'ALTA', 'CAPTACION', 'CAP-LIM-2026-009', 'AGE-001', 'La captacion CAP-LIM-2026-009 esta vencida.', 'ACTIVA', '2026-04-01 09:00:00'),
    ('OFERTA_POR_VENCER', 'MEDIA', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-003', 'AGE-004', 'La oferta SOL-LIM-2026-003 vence pronto.', 'ACTIVA', '2026-05-27 09:00:00'),
    ('VISITA_PROXIMA', 'INFO', 'VISITA', 'OPO-LIM-2026-004', 'AGE-003', 'Visita pendiente con Cafeteria Barranco.', 'ACTIVA', '2026-04-20 09:00:00'),
    ('SIN_RESPUESTA', 'MEDIA', 'OPORTUNIDAD', 'OPO-LIM-2026-007', 'AGE-003', 'La boutique no responde desde la ultima consulta.', 'ACTIVA', '2026-06-12 09:00:00'),
    ('SOLICITUD_EVALUADA', 'INFO', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-007', 'AGE-007', 'La solicitud SOL-LIM-2026-007 fue aprobada.', 'ACTIVA', '2026-06-04 12:30:00'),
    ('SOLICITUD_EVALUADA', 'ALTA', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-009', 'AGE-002', 'La solicitud SOL-LIM-2026-009 fue rechazada.', 'ACTIVA', '2026-05-12 12:30:00'),
    ('OFERTA_POR_VENCER', 'MEDIA', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-004', 'AGE-002', 'La oferta SOL-LIM-2026-004 vence pronto.', 'ACTIVA', '2026-06-03 09:00:00'),
    ('SIN_AVANCE', 'MEDIA', 'OPORTUNIDAD', 'OPO-LIM-2026-010', 'AGE-003', 'La oportunidad de la libreria requiere avance.', 'ACTIVA', '2026-06-10 09:00:00'),
    ('VISITA_PROXIMA', 'INFO', 'VISITA', 'OPO-LIM-2026-012', 'AGE-007', 'Visita programada para la oficina de Lince.', 'ACTIVA', '2026-06-01 09:00:00'),
    ('SOLICITUD_REENVIADA', 'INFO', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-006', 'AGE-005', 'La solicitud SOL-LIM-2026-006 fue reenviada a evaluacion.', 'ACTIVA', '2026-06-01 12:30:00'),
    ('SOLICITUD_REENVIADA', 'INFO', 'SOLICITUD_ALQUILER', 'SOL-LIM-2026-003', 'AGE-004', 'La solicitud SOL-LIM-2026-003 fue observada y reenviada.', 'ATENDIDA', '2026-05-22 12:30:00');

INSERT INTO alerta (
    tipo,
    severidad,
    entidad_tipo,
    entidad_id,
    id_agente,
    mensaje,
    estado,
    fecha_generacion
)
SELECT
    al.tipo,
    al.severidad,
    al.entidad_tipo,
    CASE
        WHEN al.entidad_tipo = 'OPORTUNIDAD' THEN o.id_oportunidad
        WHEN al.entidad_tipo = 'CAPTACION' THEN c.id_captacion
        WHEN al.entidad_tipo = 'SOLICITUD_ALQUILER' THEN s.id_solicitud
        WHEN al.entidad_tipo = 'VISITA' THEN v.id_visita
    END,
    a.id_agente,
    al.mensaje,
    al.estado,
    al.fecha_generacion
FROM seed_alerta_operativo al
INNER JOIN agente_inmobiliario a ON a.codigo_agente = al.codigo_agente
LEFT JOIN oportunidad_comercial o ON o.codigo_oportunidad = al.codigo_entidad
LEFT JOIN captacion c ON c.codigo_captacion = al.codigo_entidad
LEFT JOIN solicitud_alquiler s ON s.codigo_solicitud = al.codigo_entidad
LEFT JOIN oportunidad_comercial ov ON ov.codigo_oportunidad = al.codigo_entidad
LEFT JOIN visita v ON v.id_oportunidad = ov.id_oportunidad
WHERE CASE
        WHEN al.entidad_tipo = 'OPORTUNIDAD' THEN o.id_oportunidad
        WHEN al.entidad_tipo = 'CAPTACION' THEN c.id_captacion
        WHEN al.entidad_tipo = 'SOLICITUD_ALQUILER' THEN s.id_solicitud
        WHEN al.entidad_tipo = 'VISITA' THEN v.id_visita
      END IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM alerta ax
      WHERE ax.tipo = al.tipo
        AND ax.entidad_tipo = al.entidad_tipo
        AND ax.entidad_id = CASE
            WHEN al.entidad_tipo = 'OPORTUNIDAD' THEN o.id_oportunidad
            WHEN al.entidad_tipo = 'CAPTACION' THEN c.id_captacion
            WHEN al.entidad_tipo = 'SOLICITUD_ALQUILER' THEN s.id_solicitud
            WHEN al.entidad_tipo = 'VISITA' THEN v.id_visita
        END
        AND ax.estado = al.estado
  );

SET @id_usuario_age_001 = (
    SELECT u.id_usuario
    FROM usuario_interno u
    INNER JOIN agente_inmobiliario a ON a.id_usuario = u.id_usuario
    WHERE a.codigo_agente = 'AGE-001'
    LIMIT 1
);
SET @id_usuario_age_004 = (
    SELECT u.id_usuario
    FROM usuario_interno u
    INNER JOIN agente_inmobiliario a ON a.id_usuario = u.id_usuario
    WHERE a.codigo_agente = 'AGE-004'
    LIMIT 1
);

INSERT INTO historial_estado (
    entidad_tipo,
    entidad_id,
    estado_anterior,
    estado_nuevo,
    id_usuario,
    fecha_evento,
    observacion
)
SELECT
    'CONTRATO_ALQUILER',
    @id_contrato_operativo,
    'FIRMADO',
    'VIGENTE',
    @id_usuario_age_001,
    '2026-04-01 08:00:00',
    'Inicio de vigencia del contrato de alquiler.'
WHERE @id_contrato_operativo IS NOT NULL
  AND @id_usuario_age_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM historial_estado
      WHERE entidad_tipo = 'CONTRATO_ALQUILER'
        AND entidad_id = @id_contrato_operativo
        AND estado_nuevo = 'VIGENTE'
  );

INSERT INTO historial_estado (
    entidad_tipo,
    entidad_id,
    estado_anterior,
    estado_nuevo,
    id_usuario,
    fecha_evento,
    observacion
)
SELECT
    'CAPTACION',
    @id_cap_operativo_003,
    'AGE-001',
    'AGE-002',
    @id_usuario_age_001,
    '2026-04-12 10:30:00',
    'Captacion reasignada a Javier Ruiz.'
WHERE @id_cap_operativo_003 IS NOT NULL
  AND @id_usuario_age_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM historial_estado
      WHERE entidad_tipo = 'CAPTACION'
        AND entidad_id = @id_cap_operativo_003
        AND fecha_evento = '2026-04-12 10:30:00'
  );

DROP TEMPORARY TABLE IF EXISTS seed_persona_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_local_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_precio_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_publicacion_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_captacion_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_prospeccion_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_requerimiento_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_oportunidad_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_interaccion_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_visita_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_solicitud_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_evaluacion_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_tarea_operativo;
DROP TEMPORARY TABLE IF EXISTS seed_alerta_operativo;

-- =========================================================
-- Solicitudes adicionales del flujo de alquiler
-- Varias para la MISMA propiedad (LOC-LIM-2026-001 y LOC-LIM-2026-003) y para otras
-- existentes (LOC-LIM-2026-010, LOC-LIM-2026-011). Ofertas por debajo / iguales / por
-- encima del precio referencial, para lucir la comparacion del resumen.
-- Las oportunidades nacen en estado 'S' (Solicitud creada) para no chocar con
-- la unicidad de oportunidad abierta por cliente/captacion. Cuatro quedan
-- Aprobadas (probar "Marcar como alquilada"); SOL-LIM-2026-106 va sin plazo.
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_oportunidad_extra;
CREATE TEMPORARY TABLE seed_oportunidad_extra (
    codigo_oportunidad VARCHAR(20) NOT NULL,
    cliente_doc VARCHAR(30) NOT NULL,
    codigo_captacion VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    fecha_registro DATETIME NOT NULL,
    observaciones VARCHAR(500) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_oportunidad_extra VALUES
    ('OPO-LIM-2026-101', '76000004', 'CAP-LIM-2026-001', 'AGE-001', '2026-06-05 10:00:00', 'Ferreteria interesada en el local de San Miguel.'),
    ('OPO-LIM-2026-102', '76000005', 'CAP-LIM-2026-001', 'AGE-001', '2026-06-06 11:00:00', 'Floreria evalua el mismo local de San Miguel.'),
    ('OPO-LIM-2026-103', '76000006', 'CAP-LIM-2026-003', 'AGE-002', '2026-06-05 12:00:00', 'Heladeria interesada en el local de San Borja.'),
    ('OPO-LIM-2026-104', '20610000011', 'CAP-LIM-2026-003', 'AGE-002', '2026-06-07 09:30:00', 'Minimarket evalua el mismo local de San Borja.'),
    ('OPO-LIM-2026-105', '76000007', 'CAP-LIM-2026-010', 'AGE-003', '2026-06-06 15:00:00', 'Lavanderia interesada en el local de Surco.'),
    ('OPO-LIM-2026-106', '76000008', 'CAP-LIM-2026-011', 'AGE-005', '2026-06-07 16:00:00', 'Jugueteria interesada en el local de Jesus Maria.');

INSERT INTO oportunidad_comercial (
    codigo_oportunidad,
    fecha_registro,
    estado,
    fecha_actualizacion_estado,
    motivo_cierre,
    observaciones,
    id_cliente,
    id_captacion,
    id_agente,
    id_publicacion_origen,
    fuente_origen,
    codigo_origen_capturado,
    fecha_primera_consulta,
    fecha_cierre
)
SELECT
    o.codigo_oportunidad,
    o.fecha_registro,
    'S',
    o.fecha_registro,
    NULL,
    o.observaciones,
    ci.id_cliente,
    c.id_captacion,
    a.id_agente,
    NULL,
    'OTRO',
    NULL,
    o.fecha_registro,
    NULL
FROM seed_oportunidad_extra o
INNER JOIN persona pe ON pe.numero_documento = o.cliente_doc
INNER JOIN cliente_interesado ci ON ci.id_persona = pe.id_persona
INNER JOIN captacion c ON c.codigo_captacion = o.codigo_captacion
INNER JOIN agente_inmobiliario a ON a.codigo_agente = o.codigo_agente
ON DUPLICATE KEY UPDATE
    estado = VALUES(estado),
    observaciones = VALUES(observaciones),
    id_cliente = VALUES(id_cliente),
    id_captacion = VALUES(id_captacion),
    id_agente = VALUES(id_agente);

DROP TEMPORARY TABLE IF EXISTS seed_solicitud_extra;
CREATE TEMPORARY TABLE seed_solicitud_extra (
    codigo_solicitud VARCHAR(20) NOT NULL,
    codigo_oportunidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    fecha_registro DATE NOT NULL,
    monto_propuesto DECIMAL(12,2) NOT NULL,
    plazo_tentativo VARCHAR(50) NOT NULL,
    observaciones VARCHAR(500) NOT NULL,
    estado CHAR(1) NOT NULL,
    fecha_actualizacion_estado DATETIME NULL,
    fecha_vigencia_oferta DATE NULL
) ENGINE=MEMORY;

INSERT INTO seed_solicitud_extra VALUES
    ('SOL-LIM-2026-101', 'OPO-LIM-2026-101', 'AGE-001', '2026-06-08', 6500.00, '24 meses', 'Oferta por debajo del precio pedido (mismo local LOC-LIM-2026-001).', 'A', '2026-06-10 12:00:00', '2026-06-20'),
    ('SOL-LIM-2026-102', 'OPO-LIM-2026-102', 'AGE-001', '2026-06-09', 6800.00, '18 meses', 'Oferta al precio pedido (mismo local LOC-LIM-2026-001).', 'E', '2026-06-10 12:00:00', '2026-06-22'),
    ('SOL-LIM-2026-103', 'OPO-LIM-2026-103', 'AGE-002', '2026-06-08', 8500.00, '36 meses', 'Oferta por encima del precio pedido (local LOC-LIM-2026-003).', 'A', '2026-06-10 12:00:00', '2026-06-20'),
    ('SOL-LIM-2026-104', 'OPO-LIM-2026-104', 'AGE-002', '2026-06-09', 7800.00, '24 meses', 'Oferta por debajo del precio pedido (mismo local LOC-LIM-2026-003).', 'E', '2026-06-11 12:00:00', '2026-06-23'),
    ('SOL-LIM-2026-105', 'OPO-LIM-2026-105', 'AGE-003', '2026-06-09', 9300.00, '24 meses', 'Oferta por debajo del precio pedido (local LOC-LIM-2026-010).', 'A', '2026-06-11 12:00:00', '2026-06-21'),
    ('SOL-LIM-2026-106', 'OPO-LIM-2026-106', 'AGE-005', '2026-06-10', 8600.00, '', 'Oferta al precio pedido, sin plazo definido (local LOC-LIM-2026-011).', 'A', '2026-06-12 12:00:00', '2026-06-24');

INSERT INTO solicitud_alquiler (
    codigo_solicitud,
    fecha_registro,
    monto_propuesto,
    plazo_tentativo,
    observaciones,
    estado,
    fecha_actualizacion_estado,
    fecha_vigencia_oferta,
    id_oportunidad,
    id_agente
)
SELECT
    s.codigo_solicitud,
    s.fecha_registro,
    s.monto_propuesto,
    s.plazo_tentativo,
    s.observaciones,
    s.estado,
    s.fecha_actualizacion_estado,
    s.fecha_vigencia_oferta,
    o.id_oportunidad,
    a.id_agente
FROM seed_solicitud_extra s
INNER JOIN oportunidad_comercial o ON o.codigo_oportunidad = s.codigo_oportunidad
INNER JOIN agente_inmobiliario a ON a.codigo_agente = s.codigo_agente
ON DUPLICATE KEY UPDATE
    monto_propuesto = VALUES(monto_propuesto),
    plazo_tentativo = VALUES(plazo_tentativo),
    observaciones = VALUES(observaciones),
    estado = VALUES(estado),
    fecha_actualizacion_estado = VALUES(fecha_actualizacion_estado),
    fecha_vigencia_oferta = VALUES(fecha_vigencia_oferta),
    id_oportunidad = VALUES(id_oportunidad),
    id_agente = VALUES(id_agente);

-- Evaluacion final aprobada para las solicitudes extra en estado Aprobada,
-- a cargo del broker revisor de la captacion (consistencia con el flujo).
DROP TEMPORARY TABLE IF EXISTS seed_evaluacion_extra;
CREATE TEMPORARY TABLE seed_evaluacion_extra (
    codigo_solicitud VARCHAR(20) NOT NULL,
    codigo_broker VARCHAR(20) NOT NULL,
    fecha_evaluacion DATETIME NOT NULL,
    resultado CHAR(1) NOT NULL,
    tipo_evaluacion CHAR(1) NOT NULL,
    observaciones VARCHAR(500) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_evaluacion_extra VALUES
    ('SOL-LIM-2026-101', 'BRK-001', '2026-06-10 12:00:00', 'A', 'F', 'Evaluacion final aprobada.'),
    ('SOL-LIM-2026-103', 'BRK-001', '2026-06-10 12:00:00', 'A', 'F', 'Evaluacion final aprobada.'),
    ('SOL-LIM-2026-105', 'BRK-002', '2026-06-11 12:00:00', 'A', 'F', 'Evaluacion final aprobada.'),
    ('SOL-LIM-2026-106', 'BRK-001', '2026-06-12 12:00:00', 'A', 'F', 'Evaluacion final aprobada.');

INSERT INTO evaluacion_solicitud (
    fecha_evaluacion,
    resultado,
    observaciones,
    responsable_evaluacion,
    tipo_evaluacion,
    id_solicitud
)
SELECT
    e.fecha_evaluacion,
    e.resultado,
    e.observaciones,
    b.id_broker,
    e.tipo_evaluacion,
    s.id_solicitud
FROM seed_evaluacion_extra e
INNER JOIN solicitud_alquiler s ON s.codigo_solicitud = e.codigo_solicitud
INNER JOIN broker b ON b.codigo_broker = e.codigo_broker
WHERE NOT EXISTS (
    SELECT 1
    FROM evaluacion_solicitud ex
    WHERE ex.id_solicitud = s.id_solicitud
      AND ex.tipo_evaluacion = e.tipo_evaluacion
      AND ex.fecha_evaluacion = e.fecha_evaluacion
);

DROP TEMPORARY TABLE IF EXISTS seed_oportunidad_extra;
DROP TEMPORARY TABLE IF EXISTS seed_solicitud_extra;
DROP TEMPORARY TABLE IF EXISTS seed_evaluacion_extra;

COMMIT;

-- =========================================================
-- Carga masiva de cartera operativa para reportes y tableros
-- Fecha de referencia: 2026-06-28.
-- Centrada en vmora (AGE-001), con volumen relevante para todos los agentes.
-- =========================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS seed_cartera_operativa_masiva$$

CREATE PROCEDURE seed_cartera_operativa_masiva()
BEGIN
    DECLARE v_seed_today DATE DEFAULT '2026-06-28';
    DECLARE v_seed_now DATETIME DEFAULT '2026-06-28 10:00:00';
    DECLARE v_i INT DEFAULT 1;
    DECLARE v_j INT DEFAULT 1;
    DECLARE v_owner_count INT DEFAULT 520;
    DECLARE v_client_count INT DEFAULT 760;
    DECLARE v_local_count INT DEFAULT 620;
    DECLARE v_captacion_count INT DEFAULT 480;
    DECLARE v_prospeccion_count INT DEFAULT 560;
    DECLARE v_oportunidad_count INT DEFAULT 900;
    DECLARE v_solicitud_count INT DEFAULT 620;
    DECLARE v_contrato_count INT DEFAULT 190;
    DECLARE v_doc VARCHAR(30);
    DECLARE v_codigo VARCHAR(30);
    DECLARE v_nombre VARCHAR(180);
    DECLARE v_email VARCHAR(150);
    DECLARE v_telefono VARCHAR(20);
    DECLARE v_distrito_nombre VARCHAR(100);
    DECLARE v_zona VARCHAR(150);
    DECLARE v_rubro VARCHAR(120);
    DECLARE v_tipo_inmueble_char CHAR(1);
    DECLARE v_tipo_inmueble_req VARCHAR(30);
    DECLARE v_estado CHAR(1);
    DECLARE v_cap_estado CHAR(1);
    DECLARE v_opp_estado CHAR(1);
    DECLARE v_sol_estado CHAR(1);
    DECLARE v_publicacion_estado VARCHAR(20);
    DECLARE v_resultado VARCHAR(30);
    DECLARE v_fecha DATE;
    DECLARE v_fecha_fin DATE;
    DECLARE v_fecha_dt DATETIME;
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_amount DECIMAL(12,2);
    DECLARE v_commission_rate DECIMAL(6,2);
    DECLARE v_agent_share DECIMAL(12,2);
    DECLARE v_company_share DECIMAL(12,2);
    DECLARE v_area DECIMAL(10,2);
    DECLARE v_front DECIMAL(8,2);
    DECLARE v_propietario_id BIGINT;
    DECLARE v_persona_id BIGINT;
    DECLARE v_cliente_id BIGINT;
    DECLARE v_local_id BIGINT;
    DECLARE v_captacion_id BIGINT;
    DECLARE v_prospeccion_id BIGINT;
    DECLARE v_oportunidad_id BIGINT;
    DECLARE v_solicitud_id BIGINT;
    DECLARE v_contrato_id BIGINT;
    DECLARE v_publicacion_id BIGINT;
    DECLARE v_requerimiento_id BIGINT;
    DECLARE v_distrito_id BIGINT;
    DECLARE v_agente_id BIGINT;
    DECLARE v_agente_anterior_id BIGINT;
    DECLARE v_agente_codigo VARCHAR(20);
    DECLARE v_broker_id BIGINT;
    DECLARE v_admin_broker_id BIGINT;
    DECLARE v_usuario_id BIGINT;
    DECLARE v_tipo_doc_1 BIGINT;
    DECLARE v_tipo_doc_2 BIGINT;
    DECLARE v_tipo_doc_3 BIGINT;
    DECLARE v_cap_idx INT;
    DECLARE v_client_idx INT;
    DECLARE v_local_idx INT;
    DECLARE v_entity_id BIGINT;
    DECLARE v_entity_tipo VARCHAR(30);
    DECLARE v_tarea_tipo VARCHAR(30);
    DECLARE v_tarea_estado VARCHAR(20);
    DECLARE v_alerta_tipo VARCHAR(30);
    DECLARE v_old_sql_safe_updates INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET SQL_SAFE_UPDATES = v_old_sql_safe_updates;
        RESIGNAL;
    END;

    SET v_old_sql_safe_updates = @@SQL_SAFE_UPDATES;
    SET SQL_SAFE_UPDATES = 0;

    SELECT COALESCE(MAX(id_broker), 1)
    INTO v_admin_broker_id
    FROM broker
    WHERE codigo_broker = 'BRK-ADM-001';

    SELECT MIN(id_tipo_documento_requerido)
    INTO v_tipo_doc_1
    FROM tipo_documento_requerido
    WHERE activo = TRUE;

    SELECT MIN(id_tipo_documento_requerido)
    INTO v_tipo_doc_2
    FROM tipo_documento_requerido
    WHERE activo = TRUE
      AND id_tipo_documento_requerido > v_tipo_doc_1;

    SELECT MIN(id_tipo_documento_requerido)
    INTO v_tipo_doc_3
    FROM tipo_documento_requerido
    WHERE activo = TRUE
      AND id_tipo_documento_requerido > COALESCE(v_tipo_doc_2, v_tipo_doc_1);

    SET v_tipo_doc_2 = COALESCE(v_tipo_doc_2, v_tipo_doc_1);
    SET v_tipo_doc_3 = COALESCE(v_tipo_doc_3, v_tipo_doc_2, v_tipo_doc_1);

    DELETE FROM historial_estado
    WHERE observacion LIKE '%CAR-2026%';

    DELETE FROM alerta
    WHERE mensaje LIKE '%CAR-2026%';

    DELETE FROM tarea
    WHERE descripcion LIKE '%CAR-2026%';

    DELETE rp
    FROM reporte_propietario rp
    INNER JOIN captacion c ON c.id_captacion = rp.id_captacion
    WHERE c.codigo_captacion LIKE 'CAP-CAR-2026-%';

    DELETE cl
    FROM comision_liquidacion cl
    INNER JOIN contrato_alquiler ca ON ca.id_contrato_alquiler = cl.id_contrato_alquiler
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = ca.id_oportunidad
    WHERE o.codigo_oportunidad LIKE 'OPO-CAR-2026-%';

    DELETE ca
    FROM contrato_alquiler ca
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = ca.id_oportunidad
    WHERE o.codigo_oportunidad LIKE 'OPO-CAR-2026-%';

    DELETE ev
    FROM evaluacion_solicitud ev
    INNER JOIN solicitud_alquiler s ON s.id_solicitud = ev.id_solicitud
    WHERE s.codigo_solicitud LIKE 'SOL-CAR-2026-%';

    DELETE ds
    FROM documento_solicitud ds
    INNER JOIN solicitud_alquiler s ON s.id_solicitud = ds.id_solicitud
    WHERE s.codigo_solicitud LIKE 'SOL-CAR-2026-%';

    DELETE mn
    FROM motivo_no_continuidad mn
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = mn.id_oportunidad
    WHERE o.codigo_oportunidad LIKE 'OPO-CAR-2026-%';

    DELETE v
    FROM visita v
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = v.id_oportunidad
    WHERE o.codigo_oportunidad LIKE 'OPO-CAR-2026-%';

    DELETE ic
    FROM interaccion_comercial ic
    LEFT JOIN oportunidad_comercial o ON o.id_oportunidad = ic.id_oportunidad
    LEFT JOIN prospeccion pr ON pr.id_prospeccion = ic.id_prospeccion
    LEFT JOIN captacion c ON c.id_captacion = ic.id_captacion
    LEFT JOIN cliente_interesado ci ON ci.id_cliente = ic.id_cliente
    LEFT JOIN persona pcli ON pcli.id_persona = ci.id_persona
    WHERE o.codigo_oportunidad LIKE 'OPO-CAR-2026-%'
       OR pr.codigo_prospeccion LIKE 'PRO-CAR-2026-%'
       OR c.codigo_captacion LIKE 'CAP-CAR-2026-%'
       OR pcli.correo LIKE 'expansion%@marcaslima.pe';

    DELETE FROM solicitud_alquiler
    WHERE codigo_solicitud LIKE 'SOL-CAR-2026-%';

    DELETE FROM oportunidad_comercial
    WHERE codigo_oportunidad LIKE 'OPO-CAR-2026-%';

    DELETE FROM publicacion
    WHERE codigo_origen LIKE 'PUB-CAR-2026-%';

    DELETE rc
    FROM reasignacion_captacion rc
    INNER JOIN captacion c ON c.id_captacion = rc.id_captacion
    WHERE c.codigo_captacion LIKE 'CAP-CAR-2026-%';

    DELETE FROM prospeccion
    WHERE codigo_prospeccion LIKE 'PRO-CAR-2026-%';

    DELETE FROM captacion
    WHERE codigo_captacion LIKE 'CAP-CAR-2026-%';

    DELETE fl
    FROM foto_local fl
    INNER JOIN local_comercial l ON l.id_local = fl.id_local
    WHERE l.codigo_local LIKE 'LOC-CAR-2026-%';

    DELETE pl
    FROM precio_local pl
    INNER JOIN local_comercial l ON l.id_local = pl.id_local
    WHERE l.codigo_local LIKE 'LOC-CAR-2026-%';

    DELETE FROM local_comercial
    WHERE codigo_local LIKE 'LOC-CAR-2026-%';

    DELETE rd
    FROM requerimiento_distrito rd
    INNER JOIN requerimiento_cliente rq ON rq.id_requerimiento = rd.id_requerimiento
    INNER JOIN cliente_interesado ci ON ci.id_cliente = rq.id_cliente
    INNER JOIN persona p ON p.id_persona = ci.id_persona
    WHERE p.correo LIKE 'expansion%@marcaslima.pe';

    DELETE rq
    FROM requerimiento_cliente rq
    INNER JOIN cliente_interesado ci ON ci.id_cliente = rq.id_cliente
    INNER JOIN persona p ON p.id_persona = ci.id_persona
    WHERE p.correo LIKE 'expansion%@marcaslima.pe';

    DELETE ci
    FROM cliente_interesado ci
    INNER JOIN persona p ON p.id_persona = ci.id_persona
    WHERE p.correo LIKE 'expansion%@marcaslima.pe';

    DELETE pr
    FROM propietario pr
    INNER JOIN persona p ON p.id_persona = pr.id_persona
    WHERE p.correo LIKE 'inmuebles%@grupoalameda.pe';

    DELETE FROM persona
    WHERE correo LIKE 'inmuebles%@grupoalameda.pe'
       OR correo LIKE 'expansion%@marcaslima.pe';

    DELETE FROM reasignacion_agente_broker
    WHERE motivo LIKE 'Rotacion comercial por cobertura CAR-2026%';

    SET v_i = 1;
    WHILE v_i <= v_owner_count DO
        SET v_doc = CONCAT('208', LPAD(v_i, 8, '0'));
        SET v_telefono = CONCAT('94', LPAD(MOD(v_i * 37, 10000000), 7, '0'));
        SET v_email = CONCAT('inmuebles', LPAD(v_i, 4, '0'), '@grupoalameda.pe');
        SET v_distrito_nombre = CASE MOD(v_i, 12)
            WHEN 0 THEN 'Miraflores'
            WHEN 1 THEN 'San Isidro'
            WHEN 2 THEN 'Santiago de Surco'
            WHEN 3 THEN 'San Borja'
            WHEN 4 THEN 'Lince'
            WHEN 5 THEN 'Jesus Maria'
            WHEN 6 THEN 'La Victoria'
            WHEN 7 THEN 'Los Olivos'
            WHEN 8 THEN 'San Miguel'
            WHEN 9 THEN 'Surquillo'
            WHEN 10 THEN 'Ate'
            ELSE 'Barranco'
        END;
        SET v_nombre = CONCAT(CASE MOD(v_i, 8)
            WHEN 0 THEN 'Inversiones Alameda'
            WHEN 1 THEN 'Patrimonio Comercial'
            WHEN 2 THEN 'Activos Urbanos'
            WHEN 3 THEN 'Grupo Inmobiliario'
            WHEN 4 THEN 'Rentas Metropolitanas'
            WHEN 5 THEN 'Fondo Familiar'
            WHEN 6 THEN 'Desarrollos Prime'
            ELSE 'Corporacion Locales'
        END, ' ', v_distrito_nombre, ' ', LPAD(v_i, 4, '0'), ' S.A.C.');

        INSERT INTO persona (
            tipo_persona, tipo_documento, numero_documento,
            nombres_o_razon_social, telefono, correo, estado, consentimiento_uso_dato,
            fecha_creacion, fecha_actualizacion
        ) VALUES (
            'J', 'R', v_doc, v_nombre, v_telefono, v_email, 'A', TRUE,
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 11, 365) DAY),
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 3, 45) DAY)
        )
        ON DUPLICATE KEY UPDATE
            nombres_o_razon_social = VALUES(nombres_o_razon_social),
            telefono = VALUES(telefono),
            correo = VALUES(correo),
            estado = VALUES(estado),
            consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

        SELECT id_persona INTO v_persona_id
        FROM persona
        WHERE numero_documento = v_doc
        LIMIT 1;

        INSERT INTO propietario (id_persona)
        VALUES (v_persona_id)
        ON DUPLICATE KEY UPDATE id_persona = VALUES(id_persona);

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_client_count DO
        SET v_doc = CONCAT('207', LPAD(v_i, 8, '0'));
        SET v_telefono = CONCAT('95', LPAD(MOD(v_i * 53, 10000000), 7, '0'));
        SET v_email = CONCAT('expansion', LPAD(v_i, 4, '0'), '@marcaslima.pe');
        SET v_rubro = CASE MOD(v_i, 12)
            WHEN 0 THEN 'Restaurante'
            WHEN 1 THEN 'Minimarket'
            WHEN 2 THEN 'Farmacia'
            WHEN 3 THEN 'Moda'
            WHEN 4 THEN 'Servicios odontologicos'
            WHEN 5 THEN 'Cafeteria'
            WHEN 6 THEN 'Gimnasio'
            WHEN 7 THEN 'Veterinaria'
            WHEN 8 THEN 'Ferreteria'
            WHEN 9 THEN 'Belleza'
            WHEN 10 THEN 'Educacion'
            ELSE 'Oficina comercial'
        END;
        SET v_nombre = CONCAT(CASE MOD(v_i, 10)
            WHEN 0 THEN 'Mercado Selecto'
            WHEN 1 THEN 'Boticas Salud Norte'
            WHEN 2 THEN 'Cafes del Parque'
            WHEN 3 THEN 'Clinicas Sonria'
            WHEN 4 THEN 'Moda Avenida'
            WHEN 5 THEN 'Gimnasios Activa'
            WHEN 6 THEN 'Veterinaria Huella Urbana'
            WHEN 7 THEN 'Ferreteria Progreso'
            WHEN 8 THEN 'Instituto Saber'
            ELSE 'Servicios Comerciales Delta'
        END, ' ', LPAD(v_i, 4, '0'), ' S.A.C.');

        INSERT INTO persona (
            tipo_persona, tipo_documento, numero_documento,
            nombres_o_razon_social, telefono, correo, estado, consentimiento_uso_dato,
            fecha_creacion, fecha_actualizacion
        ) VALUES (
            'J', 'R', v_doc, v_nombre, v_telefono, v_email, 'A', TRUE,
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 7, 365) DAY),
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 2, 60) DAY)
        )
        ON DUPLICATE KEY UPDATE
            nombres_o_razon_social = VALUES(nombres_o_razon_social),
            telefono = VALUES(telefono),
            correo = VALUES(correo),
            estado = VALUES(estado),
            consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

        SELECT id_persona INTO v_persona_id
        FROM persona
        WHERE numero_documento = v_doc
        LIMIT 1;

        INSERT INTO cliente_interesado (
            id_persona, rubro_comercial, consentimiento_contacto, consentimiento_uso_dato
        ) VALUES (
            v_persona_id, v_rubro, TRUE, TRUE
        )
        ON DUPLICATE KEY UPDATE
            rubro_comercial = VALUES(rubro_comercial),
            consentimiento_contacto = VALUES(consentimiento_contacto),
            consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_local_count DO
        SET v_doc = CONCAT('208', LPAD(1 + MOD(v_i - 1, v_owner_count), 8, '0'));
        SET v_codigo = CONCAT('LOC-CAR-2026-', LPAD(v_i, 4, '0'));
        SET v_distrito_nombre = CASE MOD(v_i, 16)
            WHEN 0 THEN 'Miraflores'
            WHEN 1 THEN 'San Isidro'
            WHEN 2 THEN 'Santiago de Surco'
            WHEN 3 THEN 'San Borja'
            WHEN 4 THEN 'Lince'
            WHEN 5 THEN 'Jesus Maria'
            WHEN 6 THEN 'La Victoria'
            WHEN 7 THEN 'Los Olivos'
            WHEN 8 THEN 'San Miguel'
            WHEN 9 THEN 'Surquillo'
            WHEN 10 THEN 'Ate'
            WHEN 11 THEN 'Barranco'
            WHEN 12 THEN 'Santa Anita'
            WHEN 13 THEN 'Chorrillos'
            WHEN 14 THEN 'Pueblo Libre'
            ELSE 'Magdalena del Mar'
        END;
        SELECT id_distrito INTO v_distrito_id
        FROM distrito
        WHERE nombre = v_distrito_nombre
        LIMIT 1;

        SELECT pr.id_propietario INTO v_propietario_id
        FROM propietario pr
        INNER JOIN persona p ON p.id_persona = pr.id_persona
        WHERE p.numero_documento = v_doc
        LIMIT 1;

        SET v_tipo_inmueble_char = CASE MOD(v_i, 6)
            WHEN 0 THEN 'L'
            WHEN 1 THEN 'L'
            WHEN 2 THEN 'O'
            WHEN 3 THEN 'D'
            WHEN 4 THEN 'C'
            ELSE 'T'
        END;
        SET v_rubro = CASE MOD(v_i, 12)
            WHEN 0 THEN 'Restaurante'
            WHEN 1 THEN 'Minimarket'
            WHEN 2 THEN 'Farmacia'
            WHEN 3 THEN 'Moda'
            WHEN 4 THEN 'Servicios odontologicos'
            WHEN 5 THEN 'Cafeteria'
            WHEN 6 THEN 'Gimnasio'
            WHEN 7 THEN 'Veterinaria'
            WHEN 8 THEN 'Ferreteria'
            WHEN 9 THEN 'Belleza'
            WHEN 10 THEN 'Educacion'
            ELSE 'Oficina comercial'
        END;
        SET v_area = 45 + MOD(v_i * 17, 260);
        SET v_front = 4.5 + MOD(v_i * 3, 16);
        SET v_price = 3200 + MOD(v_i * 271, 21000);
        SET v_estado = CASE
            WHEN MOD(v_i, 31) = 0 THEN 'I'
            WHEN MOD(v_i, 17) = 0 THEN 'N'
            ELSE 'D'
        END;
        SET v_fecha_dt = DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 5, 365) DAY);
        SET v_zona = CASE MOD(v_i, 8)
            WHEN 0 THEN 'Eje comercial'
            WHEN 1 THEN 'Zona financiera'
            WHEN 2 THEN 'Residencial consolidado'
            WHEN 3 THEN 'Avenida principal'
            WHEN 4 THEN 'Centro urbano'
            WHEN 5 THEN 'Zona de servicios'
            WHEN 6 THEN 'Corredor gastronomico'
            ELSE 'Polo educativo'
        END;

        INSERT INTO local_comercial (
            codigo_local, direccion, distrito, metraje, precio_referencial,
            rubro_permitido, descripcion, estado, id_propietario, tipo_inmueble,
            uso, ambientes, antiguedad_anios, zona_urbanizacion, geo_lat, geo_long,
            frente, zonificacion, apto_licencia_funcionamiento, carga_electrica_kw,
            numero_estacionamientos, cuota_mantenimiento, id_distrito, fecha_registro
        ) VALUES (
            v_codigo,
            CONCAT(CASE MOD(v_i, 6)
                WHEN 0 THEN 'Av. Primavera'
                WHEN 1 THEN 'Av. Arequipa'
                WHEN 2 THEN 'Av. Javier Prado'
                WHEN 3 THEN 'Av. La Marina'
                WHEN 4 THEN 'Av. Universitaria'
                ELSE 'Av. Benavides'
            END, ' ', 400 + v_i, ', local ', 1 + MOD(v_i, 48)),
            v_distrito_nombre,
            v_area,
            v_price,
            v_rubro,
            CONCAT('Local de cartera CAR-2026 para ', LOWER(v_rubro), ' en ', v_distrito_nombre, '.'),
            v_estado,
            v_propietario_id,
            v_tipo_inmueble_char,
            'C',
            1 + MOD(v_i, 8),
            MOD(v_i * 2, 32),
            v_zona,
            -12.2000000 + (MOD(v_i, 900) / 10000),
            -77.1200000 + (MOD(v_i * 2, 900) / 10000),
            v_front,
            CASE WHEN MOD(v_i, 5) = 0 THEN 'CM' ELSE 'CZ' END,
            MOD(v_i, 9) <> 0,
            12 + MOD(v_i * 4, 85),
            MOD(v_i, 6),
            180 + MOD(v_i * 19, 1100),
            v_distrito_id,
            v_fecha_dt
        )
        ON DUPLICATE KEY UPDATE
            direccion = VALUES(direccion),
            distrito = VALUES(distrito),
            metraje = VALUES(metraje),
            precio_referencial = VALUES(precio_referencial),
            rubro_permitido = VALUES(rubro_permitido),
            descripcion = VALUES(descripcion),
            estado = VALUES(estado),
            id_propietario = VALUES(id_propietario),
            tipo_inmueble = VALUES(tipo_inmueble),
            uso = VALUES(uso),
            ambientes = VALUES(ambientes),
            antiguedad_anios = VALUES(antiguedad_anios),
            zona_urbanizacion = VALUES(zona_urbanizacion),
            geo_lat = VALUES(geo_lat),
            geo_long = VALUES(geo_long),
            frente = VALUES(frente),
            zonificacion = VALUES(zonificacion),
            apto_licencia_funcionamiento = VALUES(apto_licencia_funcionamiento),
            carga_electrica_kw = VALUES(carga_electrica_kw),
            numero_estacionamientos = VALUES(numero_estacionamientos),
            cuota_mantenimiento = VALUES(cuota_mantenimiento),
            id_distrito = VALUES(id_distrito);

        SELECT id_local INTO v_local_id
        FROM local_comercial
        WHERE codigo_local = v_codigo
        LIMIT 1;

        INSERT INTO precio_local (id_local, hito, moneda, monto, fecha)
        VALUES (
            v_local_id,
            CASE WHEN MOD(v_i, 11) = 0 THEN 'R' WHEN MOD(v_i, 13) = 0 THEN 'U' ELSE 'P' END,
            'PEN',
            v_price,
            DATE(v_fecha_dt)
        );

        INSERT INTO foto_local (id_local, clave, nombre_archivo, orden, fecha_registro)
        VALUES (
            v_local_id,
            CONCAT('locales/car-2026/', LPAD(v_i, 4, '0'), '/fachada.webp'),
            CONCAT('fachada-', LPAD(v_i, 4, '0'), '.webp'),
            1,
            v_fecha_dt
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_captacion_count DO
        SET v_codigo = CONCAT('CAP-CAR-2026-', LPAD(v_i, 4, '0'));
        SET v_local_idx = v_i;
        SET v_agente_codigo = CASE
            WHEN MOD(v_i, 10) IN (0, 1, 2, 3, 4) THEN 'AGE-001'
            ELSE CONCAT('AGE-', LPAD(2 + MOD(v_i, 14), 3, '0'))
        END;
        SELECT id_agente INTO v_agente_id
        FROM agente_inmobiliario
        WHERE codigo_agente = v_agente_codigo
        LIMIT 1;
        SELECT COALESCE(MAX(id_broker), v_admin_broker_id)
        INTO v_broker_id
        FROM broker_agente
        WHERE id_agente = v_agente_id
          AND estado = 'A';
        SELECT id_local, precio_referencial INTO v_local_id, v_price
        FROM local_comercial
        WHERE codigo_local = CONCAT('LOC-CAR-2026-', LPAD(v_local_idx, 4, '0'))
        LIMIT 1;

        SET v_cap_estado = CASE
            WHEN MOD(v_i, 29) = 0 THEN 'C'
            WHEN MOD(v_i, 23) = 0 THEN 'V'
            WHEN MOD(v_i, 20) = 0 THEN 'P'
            WHEN MOD(v_i, 17) = 0 THEN 'O'
            WHEN MOD(v_i, 31) = 0 THEN 'R'
            ELSE 'A'
        END;
        SET v_fecha = CASE
            WHEN v_cap_estado = 'V' THEN DATE_SUB(v_seed_today, INTERVAL (420 + MOD(v_i * 7, 120)) DAY)
            ELSE DATE_SUB(v_seed_today, INTERVAL MOD(v_i * 7, 365) DAY)
        END;
        SET v_fecha_fin = CASE
            WHEN v_cap_estado = 'V' THEN DATE_SUB(v_seed_today, INTERVAL (1 + MOD(v_i, 60)) DAY)
            ELSE DATE_ADD(v_fecha, INTERVAL 365 DAY)
        END;
        SET v_commission_rate = 82.00 + MOD(v_i, 12);

        INSERT INTO captacion (
            codigo_captacion, fecha_captacion, fecha_inicio_vigencia,
            fecha_fin_vigencia, comision_pactada, observaciones, estado,
            fecha_revision, observacion_revision, id_local, id_agente,
            id_broker_revisor, urgencia, exclusividad, fecha_creacion
        ) VALUES (
            v_codigo,
            v_fecha,
            v_fecha,
            v_fecha_fin,
            v_commission_rate,
            CONCAT('Captacion CAR-2026 con comision pactada de ', v_commission_rate, '%.'),
            v_cap_estado,
            CASE WHEN v_cap_estado = 'P' THEN NULL ELSE DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 1 DAY) END,
            CASE
                WHEN v_cap_estado = 'P' THEN NULL
                WHEN v_cap_estado = 'O' THEN 'Observada: reforzar fotos, zonificacion y ficha de precio.'
                WHEN v_cap_estado = 'R' THEN 'Rechazada por condicion comercial insuficiente.'
                WHEN v_cap_estado = 'C' THEN 'Cerrada por contrato o retiro del propietario.'
                WHEN v_cap_estado = 'V' THEN 'Vigencia vencida pendiente de renovacion.'
                ELSE 'Aprobada para gestion comercial.'
            END,
            v_local_id,
            v_agente_id,
            CASE WHEN v_cap_estado = 'P' THEN NULL ELSE v_broker_id END,
            1 + MOD(v_i, 5),
            MOD(v_i, 3) <> 0,
            DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 9 HOUR)
        );

        SET v_publicacion_estado = CASE
            WHEN v_cap_estado IN ('C', 'V') THEN 'C'
            WHEN v_cap_estado IN ('P', 'O', 'R') THEN 'B'
            WHEN MOD(v_i, 12) = 0 THEN 'S'
            ELSE 'P'
        END;

        INSERT INTO publicacion (
            id_local, canal, url_publicacion, version_anuncio, titulo_anuncio,
            renta_publicada, moneda, inversion_pauta, codigo_origen,
            fecha_publicacion, fecha_baja, estado, fecha_creacion
        ) VALUES (
            v_local_id,
            CASE MOD(v_i, 9)
                WHEN 0 THEN 'URBANIA'
                WHEN 1 THEN 'ADONDEVIVIR'
                WHEN 2 THEN 'MARKETPLACE'
                WHEN 3 THEN 'INSTAGRAM'
                WHEN 4 THEN 'FACEBOOK'
                WHEN 5 THEN 'WEB_PROPIA'
                WHEN 6 THEN 'WHATSAPP'
                WHEN 7 THEN 'REFERIDO'
                ELSE 'NEXO_INMOBILIARIO'
            END,
            CONCAT('https://cartera.controllocal.pe/publicaciones/pub-car-2026-', LPAD(v_i, 4, '0')),
            1 + MOD(v_i, 3),
            CONCAT('Local comercial CAR-2026 ', LPAD(v_i, 4, '0'), ' en ', (SELECT distrito FROM local_comercial WHERE id_local = v_local_id)),
            v_price,
            'PEN',
            CASE WHEN MOD(v_i, 6) = 0 THEN 0 ELSE 120 + MOD(v_i * 13, 420) END,
            CONCAT('PUB-CAR-2026-', LPAD(v_i, 4, '0')),
            DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 10 HOUR),
            CASE WHEN v_publicacion_estado IN ('C', 'S') THEN DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL (45 + MOD(v_i, 90)) DAY) ELSE NULL END,
            v_publicacion_estado,
            DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 10 HOUR)
        );

        IF v_cap_estado IN ('C', 'V') THEN
            UPDATE local_comercial
            SET estado = 'N'
            WHERE id_local = v_local_id;
        END IF;

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_prospeccion_count DO
        SET v_codigo = CONCAT('PRO-CAR-2026-', LPAD(v_i, 4, '0'));
        SET v_local_idx = v_i;
        SET v_agente_codigo = CASE
            WHEN MOD(v_i, 10) IN (0, 1, 2, 3, 4) THEN 'AGE-001'
            ELSE CONCAT('AGE-', LPAD(2 + MOD(v_i, 14), 3, '0'))
        END;
        SELECT id_agente INTO v_agente_id
        FROM agente_inmobiliario
        WHERE codigo_agente = v_agente_codigo
        LIMIT 1;
        SELECT id_local INTO v_local_id
        FROM local_comercial
        WHERE codigo_local = CONCAT('LOC-CAR-2026-', LPAD(v_local_idx, 4, '0'))
        LIMIT 1;
        SET v_captacion_id = NULL;
        IF v_i <= v_captacion_count THEN
            SELECT id_captacion, estado INTO v_captacion_id, v_cap_estado
            FROM captacion
            WHERE codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(v_i, 4, '0'))
            LIMIT 1;
        END IF;
        SET v_fecha_dt = DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 6, 365) DAY);
        IF v_captacion_id IS NOT NULL THEN
            SET v_estado = 'T';
            SET v_resultado = 'A';
        ELSE
            SET v_estado = CASE MOD(v_i, 6)
                WHEN 0 THEN 'C'
                WHEN 1 THEN 'R'
                WHEN 2 THEN 'E'
                WHEN 3 THEN 'S'
                WHEN 4 THEN 'P'
                ELSE 'D'
            END;
            SET v_resultado = CASE
                WHEN v_estado = 'E' THEN 'P'
                WHEN v_estado = 'S' THEN 'S'
                WHEN v_estado = 'D' THEN 'R'
                ELSE NULL
            END;
        END IF;

        INSERT INTO prospeccion (
            codigo_prospeccion, fecha_registro, estado, resultado_propuesta,
            fecha_contacto, fecha_reunion, fecha_propuesta, fecha_recontacto,
            observaciones, id_local, id_agente, id_captacion, fecha_creacion
        ) VALUES (
            v_codigo,
            v_fecha_dt,
            v_estado,
            v_resultado,
            DATE(v_fecha_dt),
            CASE WHEN v_estado IN ('R', 'E', 'S', 'T') THEN DATE_ADD(DATE(v_fecha_dt), INTERVAL 2 DAY) ELSE NULL END,
            CASE WHEN v_estado IN ('E', 'S', 'T') THEN DATE_ADD(DATE(v_fecha_dt), INTERVAL 4 DAY) ELSE NULL END,
            CASE WHEN v_estado IN ('C', 'R', 'E', 'S') THEN DATE_SUB(v_seed_today, INTERVAL MOD(v_i * 3, 28) DAY) ELSE NULL END,
            CONCAT('Prospeccion CAR-2026 con seguimiento de propietario y trazabilidad comercial.'),
            v_local_id,
            v_agente_id,
            v_captacion_id,
            v_fecha_dt
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_client_count DO
        SET v_doc = CONCAT('207', LPAD(v_i, 8, '0'));
        SELECT ci.id_cliente INTO v_cliente_id
        FROM cliente_interesado ci
        INNER JOIN persona p ON p.id_persona = ci.id_persona
        WHERE p.numero_documento = v_doc
        LIMIT 1;
        SET v_rubro = CASE MOD(v_i, 12)
            WHEN 0 THEN 'Restaurante'
            WHEN 1 THEN 'Minimarket'
            WHEN 2 THEN 'Farmacia'
            WHEN 3 THEN 'Moda'
            WHEN 4 THEN 'Servicios odontologicos'
            WHEN 5 THEN 'Cafeteria'
            WHEN 6 THEN 'Gimnasio'
            WHEN 7 THEN 'Veterinaria'
            WHEN 8 THEN 'Ferreteria'
            WHEN 9 THEN 'Belleza'
            WHEN 10 THEN 'Educacion'
            ELSE 'Oficina comercial'
        END;
        SET v_tipo_inmueble_req = CASE MOD(v_i, 6)
            WHEN 0 THEN 'LOCAL_COMERCIAL'
            WHEN 1 THEN 'LOCAL_COMERCIAL'
            WHEN 2 THEN 'OFICINA'
            WHEN 3 THEN 'DEPOSITO_ALMACEN'
            WHEN 4 THEN 'STAND_MODULO'
            ELSE 'TERRENO_COMERCIAL'
        END;
        SET v_price = 2800 + MOD(v_i * 251, 22000);

        INSERT INTO requerimiento_cliente (
            id_cliente, rubro, tipo_inmueble, renta_min, renta_max, moneda,
            metraje_min, metraje_max, frente_minimo, estado, observaciones,
            fecha_creacion
        ) VALUES (
            v_cliente_id,
            v_rubro,
            v_tipo_inmueble_req,
            GREATEST(v_price - 1800, 1200),
            v_price + 4200,
            'PEN',
            35 + MOD(v_i * 3, 80),
            120 + MOD(v_i * 7, 260),
            4 + MOD(v_i, 10),
            CASE WHEN MOD(v_i, 19) = 0 THEN 'CERRADO' WHEN MOD(v_i, 13) = 0 THEN 'PAUSADO' ELSE 'ACTIVO' END,
            CONCAT('Perfil activo CAR-2026 para busqueda de ', LOWER(v_rubro), '.'),
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 4, 365) DAY)
        );

        SET v_requerimiento_id = LAST_INSERT_ID();

        SET v_distrito_nombre = CASE MOD(v_i, 16)
            WHEN 0 THEN 'Miraflores'
            WHEN 1 THEN 'San Isidro'
            WHEN 2 THEN 'Santiago de Surco'
            WHEN 3 THEN 'San Borja'
            WHEN 4 THEN 'Lince'
            WHEN 5 THEN 'Jesus Maria'
            WHEN 6 THEN 'La Victoria'
            WHEN 7 THEN 'Los Olivos'
            WHEN 8 THEN 'San Miguel'
            WHEN 9 THEN 'Surquillo'
            WHEN 10 THEN 'Ate'
            WHEN 11 THEN 'Barranco'
            WHEN 12 THEN 'Santa Anita'
            WHEN 13 THEN 'Chorrillos'
            WHEN 14 THEN 'Pueblo Libre'
            ELSE 'Magdalena del Mar'
        END;
        INSERT IGNORE INTO requerimiento_distrito (id_requerimiento, id_distrito)
        SELECT v_requerimiento_id, id_distrito FROM distrito WHERE nombre = v_distrito_nombre;

        SET v_distrito_nombre = CASE MOD(v_i + 5, 16)
            WHEN 0 THEN 'Miraflores'
            WHEN 1 THEN 'San Isidro'
            WHEN 2 THEN 'Santiago de Surco'
            WHEN 3 THEN 'San Borja'
            WHEN 4 THEN 'Lince'
            WHEN 5 THEN 'Jesus Maria'
            WHEN 6 THEN 'La Victoria'
            WHEN 7 THEN 'Los Olivos'
            WHEN 8 THEN 'San Miguel'
            WHEN 9 THEN 'Surquillo'
            WHEN 10 THEN 'Ate'
            WHEN 11 THEN 'Barranco'
            WHEN 12 THEN 'Santa Anita'
            WHEN 13 THEN 'Chorrillos'
            WHEN 14 THEN 'Pueblo Libre'
            ELSE 'Magdalena del Mar'
        END;
        INSERT IGNORE INTO requerimiento_distrito (id_requerimiento, id_distrito)
        SELECT v_requerimiento_id, id_distrito FROM distrito WHERE nombre = v_distrito_nombre;

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_oportunidad_count DO
        SET v_client_idx = 1 + MOD(v_i * 7, v_client_count);
        SET v_cap_idx = 1 + MOD(v_i * 11, v_captacion_count);
        SET v_doc = CONCAT('207', LPAD(v_client_idx, 8, '0'));
        SELECT ci.id_cliente INTO v_cliente_id
        FROM cliente_interesado ci
        INNER JOIN persona p ON p.id_persona = ci.id_persona
        WHERE p.numero_documento = v_doc
        LIMIT 1;
        SELECT c.id_captacion, c.estado, c.id_agente, p.id_publicacion
        INTO v_captacion_id, v_cap_estado, v_agente_id, v_publicacion_id
        FROM captacion c
        LEFT JOIN publicacion p ON p.codigo_origen = CONCAT('PUB-CAR-2026-', LPAD(v_cap_idx, 4, '0'))
        WHERE c.codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(v_cap_idx, 4, '0'))
        LIMIT 1;

        SET v_fecha_dt = DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 3, 365) DAY);
        SET v_opp_estado = CASE
            WHEN v_i <= 190 THEN 'F'
            WHEN v_i <= v_solicitud_count THEN
                CASE
                    WHEN MOD(v_i, 13) = 0 THEN 'X'
                    WHEN MOD(v_i, 11) = 0 THEN 'N'
                    ELSE 'S'
                END
            WHEN v_cap_estado <> 'A' THEN
                CASE WHEN MOD(v_i, 2) = 0 THEN 'N' ELSE 'X' END
            WHEN MOD(v_i, 9) = 0 THEN 'N'
            WHEN MOD(v_i, 7) = 0 THEN 'X'
            WHEN MOD(v_i, 4) = 0 THEN 'S'
            ELSE 'A'
        END;

        INSERT INTO oportunidad_comercial (
            codigo_oportunidad, fecha_registro, estado, fecha_actualizacion_estado,
            motivo_cierre, observaciones, id_cliente, id_captacion, id_agente,
            id_publicacion_origen, fuente_origen, codigo_origen_capturado,
            fecha_primera_consulta, fecha_cierre, fecha_creacion
        ) VALUES (
            CONCAT('OPO-CAR-2026-', LPAD(v_i, 4, '0')),
            v_fecha_dt,
            v_opp_estado,
            CASE WHEN v_opp_estado IN ('F', 'X', 'N') THEN DATE_ADD(v_fecha_dt, INTERVAL 6 DAY) ELSE NULL END,
            CASE
                WHEN v_opp_estado = 'N' THEN 'Cliente descarto por presupuesto o ubicacion'
                WHEN v_opp_estado = 'X' THEN 'Oportunidad descartada por falta de continuidad'
                ELSE NULL
            END,
            CONCAT('Oportunidad CAR-2026 generada para evaluar reportes, cartera y origen de publicacion.'),
            v_cliente_id,
            v_captacion_id,
            v_agente_id,
            v_publicacion_id,
            CASE MOD(v_i, 8)
                WHEN 0 THEN 'PORTAL'
                WHEN 1 THEN 'REDES_SOCIALES'
                WHEN 2 THEN 'WHATSAPP'
                WHEN 3 THEN 'LLAMADA_DIRECTA'
                WHEN 4 THEN 'REFERIDO'
                WHEN 5 THEN 'CARTERA_PROPIA'
                WHEN 6 THEN 'WEB_PROPIA'
                ELSE 'OTRO'
            END,
            CONCAT('PUB-CAR-2026-', LPAD(v_cap_idx, 4, '0')),
            v_fecha_dt,
            CASE WHEN v_opp_estado = 'F' THEN DATE_ADD(v_fecha_dt, INTERVAL 18 DAY)
                 WHEN v_opp_estado IN ('X', 'N') THEN DATE_ADD(v_fecha_dt, INTERVAL 8 DAY)
                 ELSE NULL
            END,
            v_fecha_dt
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_oportunidad_count DO
        SELECT id_oportunidad, id_agente, estado, fecha_registro
        INTO v_oportunidad_id, v_agente_id, v_opp_estado, v_fecha_dt
        FROM oportunidad_comercial
        WHERE codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        INSERT INTO interaccion_comercial (
            contexto, fecha_hora, canal_contacto, observaciones, resultado,
            id_oportunidad, id_agente, transcripcion_nota
        ) VALUES (
            'OPORTUNIDAD',
            DATE_ADD(v_fecha_dt, INTERVAL 1 DAY),
            CASE MOD(v_i, 5) WHEN 0 THEN 'W' WHEN 1 THEN 'L' WHEN 2 THEN 'E' WHEN 3 THEN 'R' ELSE 'T' END,
            CONCAT('Primer contacto CAR-2026 para oportunidad ', LPAD(v_i, 4, '0'), '.'),
            'INTERESADO',
            v_oportunidad_id,
            v_agente_id,
            'Cliente solicita ficha, condiciones y disponibilidad.'
        );

        INSERT INTO interaccion_comercial (
            contexto, fecha_hora, canal_contacto, observaciones, resultado,
            id_oportunidad, id_agente, transcripcion_nota
        ) VALUES (
            'OPORTUNIDAD',
            DATE_ADD(v_fecha_dt, INTERVAL (3 + MOD(v_i, 5)) DAY),
            CASE MOD(v_i, 4) WHEN 0 THEN 'W' WHEN 1 THEN 'L' WHEN 2 THEN 'E' ELSE 'P' END,
            CONCAT('Seguimiento CAR-2026 con avance comercial registrado.'),
            CASE
                WHEN v_opp_estado = 'F' THEN 'NEGOCIANDO'
                WHEN v_opp_estado = 'X' THEN 'DESCARTADO'
                WHEN v_opp_estado = 'N' THEN 'NO_INTERESADO'
                WHEN v_opp_estado = 'S' THEN 'OFERTA_SOLICITADA'
                ELSE 'VISITA_AGENDADA'
            END,
            v_oportunidad_id,
            v_agente_id,
            NULL
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_prospeccion_count DO
        SELECT id_prospeccion, id_agente, fecha_registro, estado
        INTO v_prospeccion_id, v_agente_id, v_fecha_dt, v_estado
        FROM prospeccion
        WHERE codigo_prospeccion = CONCAT('PRO-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        INSERT INTO interaccion_comercial (
            contexto, fecha_hora, canal_contacto, observaciones, resultado,
            id_prospeccion, id_agente, transcripcion_nota
        ) VALUES (
            'PROSPECCION', DATE_ADD(v_fecha_dt, INTERVAL 1 DAY), 'L',
            CONCAT('Contacto inicial con propietario CAR-2026 ', LPAD(v_i, 4, '0'), '.'),
            'CONTACTADO', v_prospeccion_id, v_agente_id,
            'Se validan datos del local, renta esperada y urgencia.'
        );

        INSERT INTO interaccion_comercial (
            contexto, fecha_hora, canal_contacto, observaciones, resultado,
            id_prospeccion, id_agente, transcripcion_nota
        ) VALUES (
            'PROSPECCION', DATE_ADD(v_fecha_dt, INTERVAL 3 DAY), 'W',
            'Se coordina reunion y se comparten condiciones comerciales.',
            CASE WHEN v_estado IN ('T', 'E', 'S') THEN 'PROPUESTA_ENVIADA' ELSE 'REUNION_AGENDADA' END,
            v_prospeccion_id, v_agente_id, NULL
        );

        INSERT INTO interaccion_comercial (
            contexto, fecha_hora, canal_contacto, observaciones, resultado,
            id_prospeccion, id_agente, transcripcion_nota
        ) VALUES (
            'PROSPECCION', DATE_ADD(v_fecha_dt, INTERVAL 6 DAY), 'E',
            'Seguimiento de decision del propietario y proximo paso.',
            CASE
                WHEN v_estado = 'T' THEN 'ACEPTA_CAPTAR'
                WHEN v_estado = 'D' THEN 'NO_ACEPTA'
                ELSE 'RECONTACTAR'
            END,
            v_prospeccion_id, v_agente_id, NULL
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_captacion_count DO
        SELECT id_captacion, id_agente, estado, fecha_captacion
        INTO v_captacion_id, v_agente_id, v_cap_estado, v_fecha
        FROM captacion
        WHERE codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        INSERT INTO interaccion_comercial (
            contexto, fecha_hora, canal_contacto, observaciones, resultado,
            id_captacion, id_agente, transcripcion_nota
        ) VALUES (
            'CAPTACION',
            DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 2 DAY),
            CASE MOD(v_i, 4) WHEN 0 THEN 'W' WHEN 1 THEN 'E' WHEN 2 THEN 'L' ELSE 'P' END,
            CONCAT('Gestion de expediente CAR-2026 para captacion ', LPAD(v_i, 4, '0'), '.'),
            CASE
                WHEN v_cap_estado = 'O' THEN 'PROPIETARIO_OBSERVA'
                WHEN v_cap_estado IN ('P', 'R') THEN 'DOCS_SOLICITADOS'
                WHEN v_cap_estado = 'C' THEN 'PAUSAR_GESTION'
                WHEN v_cap_estado = 'V' THEN 'CONDICIONES_AJUSTADAS'
                ELSE 'PUBLICACION_COORDINADA'
            END,
            v_captacion_id, v_agente_id, NULL
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_client_count DO
        SET v_doc = CONCAT('207', LPAD(v_i, 8, '0'));
        SELECT ci.id_cliente INTO v_cliente_id
        FROM cliente_interesado ci
        INNER JOIN persona p ON p.id_persona = ci.id_persona
        WHERE p.numero_documento = v_doc
        LIMIT 1;
        SET v_agente_codigo = CASE
            WHEN MOD(v_i, 10) IN (0, 1, 2, 3, 4) THEN 'AGE-001'
            ELSE CONCAT('AGE-', LPAD(2 + MOD(v_i, 14), 3, '0'))
        END;
        SELECT id_agente INTO v_agente_id
        FROM agente_inmobiliario
        WHERE codigo_agente = v_agente_codigo
        LIMIT 1;

        INSERT INTO interaccion_comercial (
            contexto, fecha_hora, canal_contacto, observaciones, resultado,
            id_cliente, id_agente, transcripcion_nota
        ) VALUES (
            'CLIENTE',
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 4, 180) DAY),
            CASE MOD(v_i, 5) WHEN 0 THEN 'W' WHEN 1 THEN 'L' WHEN 2 THEN 'E' WHEN 3 THEN 'R' ELSE 'T' END,
            CONCAT('Levantamiento de requerimiento CAR-2026 para cliente ', LPAD(v_i, 4, '0'), '.'),
            CASE
                WHEN MOD(v_i, 13) = 0 THEN 'NO_RESPONDE'
                WHEN MOD(v_i, 7) = 0 THEN 'PROPUESTA_ENVIADA'
                WHEN MOD(v_i, 5) = 0 THEN 'REQUIERE_OPCIONES'
                ELSE 'BUSQUEDA_LEVANTADA'
            END,
            v_cliente_id,
            v_agente_id,
            'Se registran rubro, renta, frente, area y distritos objetivo.'
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_oportunidad_count DO
        SELECT id_oportunidad, id_agente, fecha_registro
        INTO v_oportunidad_id, v_agente_id, v_fecha_dt
        FROM oportunidad_comercial
        WHERE codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        INSERT INTO visita (
            fecha_visita, hora_visita, observaciones, estado, resultado,
            id_oportunidad, id_agente, nivel_interes, objecion_principal,
            opinion_precio, proxima_accion
        ) VALUES (
            CASE WHEN MOD(v_i, 8) = 0 THEN DATE_ADD(v_seed_today, INTERVAL (1 + MOD(v_i, 14)) DAY)
                 ELSE DATE_ADD(DATE(v_fecha_dt), INTERVAL (4 + MOD(v_i, 18)) DAY)
            END,
            MAKETIME(9 + MOD(v_i, 8), CASE WHEN MOD(v_i, 2) = 0 THEN 0 ELSE 30 END, 0),
            CONCAT('Visita CAR-2026 con seguimiento comercial del interesado.'),
            CASE
                WHEN MOD(v_i, 8) = 0 THEN 'P'
                WHEN MOD(v_i, 17) = 0 THEN 'N'
                WHEN MOD(v_i, 13) = 0 THEN 'C'
                WHEN MOD(v_i, 11) = 0 THEN 'G'
                ELSE 'R'
            END,
            CASE
                WHEN MOD(v_i, 8) = 0 THEN NULL
                WHEN MOD(v_i, 17) = 0 THEN NULL
                WHEN MOD(v_i, 5) = 0 THEN 'S'
                WHEN MOD(v_i, 7) = 0 THEN 'N'
                ELSE 'I'
            END,
            v_oportunidad_id,
            v_agente_id,
            CASE WHEN MOD(v_i, 8) = 0 THEN NULL ELSE 1 + MOD(v_i, 5) END,
            CASE WHEN MOD(v_i, 7) = 0 THEN 'P' WHEN MOD(v_i, 5) = 0 THEN 'C' ELSE 'E' END,
            CASE WHEN MOD(v_i, 6) = 0 THEN 'B' WHEN MOD(v_i, 4) = 0 THEN 'A' ELSE 'J' END,
            CASE WHEN MOD(v_i, 5) = 0 THEN 'O' WHEN MOD(v_i, 7) = 0 THEN 'D' ELSE 'S' END
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_solicitud_count DO
        SELECT o.id_oportunidad, o.id_agente, l.precio_referencial, o.fecha_registro
        INTO v_oportunidad_id, v_agente_id, v_price, v_fecha_dt
        FROM oportunidad_comercial o
        INNER JOIN captacion c ON c.id_captacion = o.id_captacion
        INNER JOIN local_comercial l ON l.id_local = c.id_local
        WHERE o.codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        SET v_sol_estado = CASE
            WHEN v_i <= v_contrato_count THEN 'C'
            WHEN v_i <= v_contrato_count + 60 THEN 'A'
            WHEN MOD(v_i, 14) = 0 THEN 'O'
            WHEN MOD(v_i, 11) = 0 THEN 'R'
            WHEN MOD(v_i, 10) = 0 THEN 'D'
            WHEN MOD(v_i, 7) = 0 THEN 'G'
            ELSE 'E'
        END;
        SET v_amount = ROUND(v_price * (0.88 + (MOD(v_i, 9) / 100.00)), 2);
        SET v_fecha = DATE_ADD(DATE(v_fecha_dt), INTERVAL (4 + MOD(v_i, 10)) DAY);

        INSERT INTO solicitud_alquiler (
            codigo_solicitud, fecha_registro, monto_propuesto, plazo_tentativo,
            observaciones, estado, fecha_actualizacion_estado, fecha_vigencia_oferta,
            plazo_contrato_meses, fecha_inicio_contrato, forma_pago, meses_garantia,
            meses_adelanto, id_oportunidad, id_agente, fecha_creacion
        ) VALUES (
            CONCAT('SOL-CAR-2026-', LPAD(v_i, 4, '0')),
            v_fecha,
            v_amount,
            CONCAT(12 + MOD(v_i, 37), ' meses'),
            CONCAT('Solicitud CAR-2026 con condiciones comerciales completas.'),
            v_sol_estado,
            CASE WHEN v_sol_estado = 'G' THEN NULL ELSE DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 2 DAY) END,
            DATE_ADD(v_fecha, INTERVAL (12 + MOD(v_i, 18)) DAY),
            12 + MOD(v_i, 37),
            CASE WHEN v_sol_estado IN ('A', 'C') THEN DATE_ADD(v_fecha, INTERVAL (20 + MOD(v_i, 25)) DAY) ELSE NULL END,
            CASE MOD(v_i, 5)
                WHEN 0 THEN 'TRANSFERENCIA'
                WHEN 1 THEN 'DEPOSITO_BANCARIO'
                WHEN 2 THEN 'EFECTIVO'
                WHEN 3 THEN 'CHEQUE'
                ELSE 'OTRO'
            END,
            1 + MOD(v_i, 3),
            1 + MOD(v_i, 2),
            v_oportunidad_id,
            v_agente_id,
            CAST(v_fecha AS DATETIME)
        );

        SELECT id_solicitud INTO v_solicitud_id
        FROM solicitud_alquiler
        WHERE codigo_solicitud = CONCAT('SOL-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        UPDATE oportunidad_comercial
        SET estado = CASE
                WHEN v_sol_estado = 'C' THEN 'F'
                WHEN v_sol_estado IN ('R', 'D') THEN 'X'
                ELSE 'S'
            END,
            fecha_actualizacion_estado = DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 2 DAY),
            fecha_cierre = CASE WHEN v_sol_estado = 'C' THEN DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 25 DAY) ELSE NULL END,
            motivo_cierre = CASE WHEN v_sol_estado IN ('R', 'D') THEN 'Solicitud no continuo luego de evaluacion' ELSE NULL END
        WHERE id_oportunidad = v_oportunidad_id;

        INSERT INTO documento_solicitud (
            id_tipo_documento_requerido, nombre_archivo, ruta_archivo, fecha_entrega,
            resultado_revision, observaciones, estado, id_solicitud
        ) VALUES (
            v_tipo_doc_1,
            CONCAT('ruc-sol-car-2026-', LPAD(v_i, 4, '0'), '.pdf'),
            CONCAT('/expedientes/sol-car-2026-', LPAD(v_i, 4, '0'), '/ruc.pdf'),
            DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 1 DAY),
            'C',
            'Documento validado por mesa de control.',
            'V',
            v_solicitud_id
        );

        INSERT INTO documento_solicitud (
            id_tipo_documento_requerido, nombre_archivo, ruta_archivo, fecha_entrega,
            resultado_revision, observaciones, estado, id_solicitud
        ) VALUES (
            v_tipo_doc_2,
            CONCAT('representante-sol-car-2026-', LPAD(v_i, 4, '0'), '.pdf'),
            CONCAT('/expedientes/sol-car-2026-', LPAD(v_i, 4, '0'), '/representante.pdf'),
            DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 1 DAY),
            CASE WHEN v_sol_estado = 'O' THEN 'O' WHEN v_sol_estado IN ('G', 'E') THEN 'P' ELSE 'C' END,
            CASE WHEN v_sol_estado = 'O' THEN 'Documento observado: falta vigencia actualizada.' ELSE 'Documento recibido para revision.' END,
            CASE WHEN v_sol_estado = 'O' THEN 'O' WHEN v_sol_estado IN ('G', 'E') THEN 'R' ELSE 'V' END,
            v_solicitud_id
        );

        INSERT INTO documento_solicitud (
            id_tipo_documento_requerido, nombre_archivo, ruta_archivo, fecha_entrega,
            resultado_revision, observaciones, estado, id_solicitud
        ) VALUES (
            v_tipo_doc_3,
            CONCAT('sustento-sol-car-2026-', LPAD(v_i, 4, '0'), '.pdf'),
            CONCAT('/expedientes/sol-car-2026-', LPAD(v_i, 4, '0'), '/sustento.pdf'),
            DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 2 DAY),
            CASE WHEN v_sol_estado IN ('O', 'R') THEN 'O' WHEN v_sol_estado IN ('G', 'E') THEN 'P' ELSE 'C' END,
            CASE WHEN v_sol_estado IN ('O', 'R') THEN 'Sustento observado por inconsistencia de ingresos.' ELSE 'Sustento economico registrado.' END,
            CASE WHEN v_sol_estado IN ('O', 'R') THEN 'O' WHEN v_sol_estado IN ('G', 'E') THEN 'R' ELSE 'V' END,
            v_solicitud_id
        );

        SELECT COALESCE(MAX(id_broker), v_admin_broker_id)
        INTO v_broker_id
        FROM broker_agente
        WHERE id_agente = v_agente_id
          AND estado = 'A';

        IF v_sol_estado <> 'G' THEN
            INSERT INTO evaluacion_solicitud (
                fecha_evaluacion, resultado, observaciones,
                responsable_evaluacion, tipo_evaluacion, id_solicitud
            ) VALUES (
                DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 2 DAY),
                CASE WHEN v_sol_estado IN ('O', 'R') THEN 'O' ELSE 'A' END,
                'Evaluacion preliminar CAR-2026 del expediente comercial.',
                v_broker_id,
                'P',
                v_solicitud_id
            );
        END IF;

        IF v_sol_estado = 'O' THEN
            INSERT INTO evaluacion_solicitud (
                fecha_evaluacion, resultado, observaciones,
                responsable_evaluacion, tipo_evaluacion, id_solicitud
            ) VALUES (
                DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 4 DAY),
                'O',
                'Observacion documental: corregir sustento y poderes.',
                v_broker_id,
                'O',
                v_solicitud_id
            );
        END IF;

        IF v_sol_estado IN ('A', 'C', 'R') THEN
            INSERT INTO evaluacion_solicitud (
                fecha_evaluacion, resultado, observaciones,
                responsable_evaluacion, tipo_evaluacion, id_solicitud
            ) VALUES (
                DATE_ADD(CAST(v_fecha AS DATETIME), INTERVAL 5 DAY),
                CASE WHEN v_sol_estado = 'R' THEN 'R' ELSE 'A' END,
                CASE WHEN v_sol_estado = 'R' THEN 'Evaluacion final rechazada.' ELSE 'Evaluacion final aprobada.' END,
                v_broker_id,
                'F',
                v_solicitud_id
            );
        END IF;

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_contrato_count DO
        SELECT s.id_solicitud, s.id_oportunidad, s.monto_propuesto, o.id_agente
        INTO v_solicitud_id, v_oportunidad_id, v_price, v_agente_id
        FROM solicitud_alquiler s
        INNER JOIN oportunidad_comercial o ON o.id_oportunidad = s.id_oportunidad
        WHERE s.codigo_solicitud = CONCAT('SOL-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        SET v_fecha = DATE_SUB(v_seed_today, INTERVAL MOD(v_i * 4, 360) DAY);

        INSERT INTO contrato_alquiler (
            id_oportunidad, id_solicitud, fecha_cierre, estado_contrato, incidencias
        ) VALUES (
            v_oportunidad_id,
            v_solicitud_id,
            v_fecha,
            CASE MOD(v_i, 7)
                WHEN 0 THEN 'P'
                WHEN 1 THEN 'D'
                WHEN 2 THEN 'V'
                WHEN 3 THEN 'R'
                WHEN 4 THEN 'F'
                WHEN 5 THEN 'S'
                ELSE 'A'
            END,
            CONCAT('Cierre CAR-2026 con contrato y seguimiento de liquidacion.')
        );

        SET v_contrato_id = LAST_INSERT_ID();
        SET v_commission_rate = 82.00 + MOD(v_i, 10);
        SET v_amount = ROUND(v_price * (v_commission_rate / 100.00), 2);
        SET v_agent_share = ROUND(v_amount * (0.82 + (MOD(v_i, 9) / 100.00)), 2);
        SET v_company_share = v_amount - v_agent_share;

        INSERT INTO comision_liquidacion (
            id_contrato_alquiler, monto, moneda, monto_agente, monto_empresa,
            fecha_cobro, forma_pago, estado
        ) VALUES (
            v_contrato_id,
            v_amount,
            'PEN',
            v_agent_share,
            v_company_share,
            CASE WHEN MOD(v_i, 7) = 0 THEN NULL ELSE DATE_ADD(v_fecha, INTERVAL (7 + MOD(v_i, 20)) DAY) END,
            CASE WHEN MOD(v_i, 7) = 0 THEN NULL
                 WHEN MOD(v_i, 5) = 0 THEN 'DEPOSITO_BANCARIO'
                 ELSE 'TRANSFERENCIA'
            END,
            CASE WHEN MOD(v_i, 7) = 0 THEN 'PENDIENTE'
                 WHEN MOD(v_i, 11) = 0 THEN 'PARCIAL'
                 ELSE 'COBRADA'
            END
        );

        UPDATE publicacion p
        INNER JOIN captacion c ON c.id_local = p.id_local
        INNER JOIN oportunidad_comercial o ON o.id_captacion = c.id_captacion
        SET p.estado = 'C',
            p.fecha_baja = CAST(v_fecha AS DATETIME)
        WHERE o.id_oportunidad = v_oportunidad_id
          AND p.estado IN ('P', 'S', 'B');

        UPDATE local_comercial l
        INNER JOIN captacion c ON c.id_local = l.id_local
        INNER JOIN oportunidad_comercial o ON o.id_captacion = c.id_captacion
        SET l.estado = 'N'
        WHERE o.id_oportunidad = v_oportunidad_id;

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_oportunidad_count DO
        SELECT id_oportunidad, id_agente, estado
        INTO v_oportunidad_id, v_agente_id, v_opp_estado
        FROM oportunidad_comercial
        WHERE codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        IF v_opp_estado IN ('N', 'X') THEN
            INSERT INTO motivo_no_continuidad (
                fecha_hora, razon_principal, observaciones, id_agente,
                id_oportunidad, id_interaccion, id_visita, id_solicitud
            ) VALUES (
                DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 5, 180) DAY),
                CASE MOD(v_i, 7)
                    WHEN 0 THEN 'P'
                    WHEN 1 THEN 'U'
                    WHEN 2 THEN 'C'
                    WHEN 3 THEN 'L'
                    WHEN 4 THEN 'N'
                    WHEN 5 THEN 'E'
                    ELSE 'O'
                END,
                'Cierre de no continuidad CAR-2026 registrado para analisis de conversion.',
                v_agente_id,
                v_oportunidad_id,
                NULL,
                NULL,
                NULL
            );
        END IF;

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_captacion_count DO
        SELECT id_captacion, id_agente, estado, fecha_captacion
        INTO v_captacion_id, v_agente_id, v_cap_estado, v_fecha
        FROM captacion
        WHERE codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        IF v_cap_estado = 'A' THEN
            SET v_j = 1;
            WHILE v_j <= 4 DO
                SET v_fecha_fin = DATE_SUB(v_seed_today, INTERVAL MOD(v_i * v_j * 3, 360) DAY);
                INSERT INTO reporte_propietario (
                    id_captacion, id_agente, fecha_reporte, periodo_inicio, periodo_fin,
                    consultas_reportadas, visitas_reportadas, objeciones_frecuentes,
                    ajustes_recomendados, canal_envio
                ) VALUES (
                    v_captacion_id,
                    v_agente_id,
                    v_fecha_fin,
                    DATE_SUB(v_fecha_fin, INTERVAL (CASE v_j WHEN 1 THEN 6 WHEN 2 THEN 14 WHEN 3 THEN 29 ELSE 89 END) DAY),
                    v_fecha_fin,
                    MOD(v_i * v_j, 28),
                    MOD(v_i + v_j, 11),
                    CASE MOD(v_i + v_j, 5)
                        WHEN 0 THEN 'Precio por encima del rango de clientes activos'
                        WHEN 1 THEN 'Ubicacion requiere mejor material fotografico'
                        WHEN 2 THEN 'Metraje no calza con rubros de alta demanda'
                        WHEN 3 THEN 'Condiciones de adelanto generan friccion'
                        ELSE 'Sin objeciones criticas'
                    END,
                    CASE MOD(v_i + v_j, 5)
                        WHEN 0 THEN 'Ajustar renta publicada y reforzar pauta'
                        WHEN 1 THEN 'Actualizar fotos de fachada y mapa comercial'
                        WHEN 2 THEN 'Proponer rubros alternativos compatibles'
                        WHEN 3 THEN 'Flexibilizar adelanto para acelerar cierre'
                        ELSE 'Mantener estrategia actual'
                    END,
                    CASE MOD(v_j, 4) WHEN 0 THEN 'W' WHEN 1 THEN 'E' WHEN 2 THEN 'L' ELSE 'P' END
                );
                SET v_j = v_j + 1;
            END WHILE;
        END IF;

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= 140 DO
        SELECT c.id_captacion, c.id_agente
        INTO v_captacion_id, v_agente_id
        FROM captacion c
        WHERE c.codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        IF v_agente_id = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-001' LIMIT 1) THEN
            SELECT id_agente INTO v_agente_anterior_id FROM agente_inmobiliario WHERE codigo_agente = 'AGE-003' LIMIT 1;
        ELSE
            SELECT id_agente INTO v_agente_anterior_id FROM agente_inmobiliario WHERE codigo_agente = 'AGE-001' LIMIT 1;
        END IF;

        SELECT COALESCE(MAX(id_broker), v_admin_broker_id)
        INTO v_broker_id
        FROM broker_agente
        WHERE id_agente = v_agente_id
          AND estado = 'A';

        INSERT INTO reasignacion_captacion (
            fecha_cambio, motivo, id_captacion, id_agente_anterior,
            id_agente_nuevo, id_broker
        ) VALUES (
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 9, 300) DAY),
            CONCAT('Reasignacion CAR-2026 por especializacion de cartera y carga operativa.'),
            v_captacion_id,
            v_agente_anterior_id,
            v_agente_id,
            v_broker_id
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= 15 DO
        SELECT id_agente INTO v_agente_id
        FROM agente_inmobiliario
        WHERE codigo_agente = CONCAT('AGE-', LPAD(v_i, 3, '0'))
        LIMIT 1;

        INSERT INTO reasignacion_agente_broker (
            fecha_cambio, motivo, id_agente, id_broker_anterior,
            id_broker_nuevo, id_broker_administrador
        )
        SELECT
            DATE_SUB(v_seed_now, INTERVAL (180 + MOD(v_i * 11, 150)) DAY),
            'Rotacion comercial por cobertura CAR-2026 y balance de zonas.',
            v_agente_id,
            b1.id_broker,
            b2.id_broker,
            v_admin_broker_id
        FROM broker b1
        INNER JOIN broker b2 ON b2.codigo_broker = CASE
            WHEN b1.codigo_broker = 'BRK-001' THEN 'BRK-002'
            ELSE 'BRK-001'
        END
        WHERE b1.codigo_broker = CASE WHEN MOD(v_i, 2) = 0 THEN 'BRK-001' ELSE 'BRK-002' END
        LIMIT 1;

        INSERT INTO reasignacion_agente_broker (
            fecha_cambio, motivo, id_agente, id_broker_anterior,
            id_broker_nuevo, id_broker_administrador
        )
        SELECT
            DATE_SUB(v_seed_now, INTERVAL (35 + MOD(v_i * 7, 90)) DAY),
            'Rotacion comercial por cobertura CAR-2026 y especializacion sectorial.',
            v_agente_id,
            b1.id_broker,
            b2.id_broker,
            v_admin_broker_id
        FROM broker b1
        INNER JOIN broker b2 ON b2.codigo_broker = CASE
            WHEN b1.codigo_broker = 'BRK-003' THEN 'BRK-004'
            ELSE 'BRK-003'
        END
        WHERE b1.codigo_broker = CASE WHEN MOD(v_i, 2) = 0 THEN 'BRK-003' ELSE 'BRK-004' END
        LIMIT 1;

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= 920 DO
        SET v_cap_idx = 1 + MOD(v_i, v_captacion_count);
        SET v_client_idx = 1 + MOD(v_i, v_client_count);
        SET v_tarea_tipo = CASE MOD(v_i, 6)
            WHEN 0 THEN 'RECONTACTO'
            WHEN 1 THEN 'ENVIAR_REVISION'
            WHEN 2 THEN 'SUBIR_DOCUMENTOS'
            WHEN 3 THEN 'REPORTE_PROPIETARIO'
            WHEN 4 THEN 'PROPONER_OPORTUNIDAD'
            ELSE 'SEGUIMIENTO'
        END;

        IF v_tarea_tipo = 'RECONTACTO' THEN
            SET v_entity_tipo = 'PROSPECCION';
            SELECT id_prospeccion, id_agente INTO v_entity_id, v_agente_id
            FROM prospeccion
            WHERE codigo_prospeccion = CONCAT('PRO-CAR-2026-', LPAD(1 + MOD(v_i, v_prospeccion_count), 4, '0'))
            LIMIT 1;
        ELSEIF v_tarea_tipo IN ('ENVIAR_REVISION', 'REPORTE_PROPIETARIO') THEN
            SET v_entity_tipo = 'CAPTACION';
            SELECT id_captacion, id_agente INTO v_entity_id, v_agente_id
            FROM captacion
            WHERE codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(v_cap_idx, 4, '0'))
            LIMIT 1;
        ELSEIF v_tarea_tipo = 'SUBIR_DOCUMENTOS' THEN
            SET v_entity_tipo = 'SOLICITUD_ALQUILER';
            SELECT id_solicitud, id_agente INTO v_entity_id, v_agente_id
            FROM solicitud_alquiler
            WHERE codigo_solicitud = CONCAT('SOL-CAR-2026-', LPAD(1 + MOD(v_i, v_solicitud_count), 4, '0'))
            LIMIT 1;
        ELSEIF v_tarea_tipo = 'PROPONER_OPORTUNIDAD' THEN
            SET v_entity_tipo = 'REQUERIMIENTO';
            SELECT rq.id_requerimiento INTO v_entity_id
            FROM requerimiento_cliente rq
            INNER JOIN cliente_interesado ci ON ci.id_cliente = rq.id_cliente
            INNER JOIN persona p ON p.id_persona = ci.id_persona
            WHERE p.numero_documento = CONCAT('207', LPAD(v_client_idx, 8, '0'))
            LIMIT 1;
            SELECT id_agente INTO v_agente_id
            FROM agente_inmobiliario
            WHERE codigo_agente = CASE WHEN MOD(v_i, 10) IN (0, 1, 2, 3, 4) THEN 'AGE-001' ELSE CONCAT('AGE-', LPAD(2 + MOD(v_i, 14), 3, '0')) END
            LIMIT 1;
        ELSE
            SET v_entity_tipo = 'OPORTUNIDAD';
            SELECT id_oportunidad, id_agente INTO v_entity_id, v_agente_id
            FROM oportunidad_comercial
            WHERE codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(1 + MOD(v_i, v_oportunidad_count), 4, '0'))
            LIMIT 1;
        END IF;

        SET v_tarea_estado = CASE
            WHEN MOD(v_i, 10) = 0 THEN 'VENCIDA'
            WHEN MOD(v_i, 9) = 0 THEN 'COMPLETADA'
            WHEN MOD(v_i, 7) = 0 THEN 'EN_PROCESO'
            ELSE 'PENDIENTE'
        END;

        INSERT INTO tarea (
            tipo, entidad_tipo, entidad_id, id_agente, descripcion,
            fecha_programada, fecha_recordatorio, fecha_completada,
            estado, prioridad
        ) VALUES (
            v_tarea_tipo,
            v_entity_tipo,
            v_entity_id,
            v_agente_id,
            CONCAT('Accion CAR-2026 ', v_tarea_tipo, ' para ', v_entity_tipo, ' ', v_entity_id, '.'),
            CASE
                WHEN MOD(v_i, 10) = 0 THEN DATE_SUB(v_seed_now, INTERVAL (1 + MOD(v_i, 18)) DAY)
                WHEN MOD(v_i, 8) = 0 THEN DATE_ADD(CAST(v_seed_today AS DATETIME), INTERVAL (1 + MOD(v_i, 14)) HOUR)
                ELSE DATE_ADD(v_seed_now, INTERVAL MOD(v_i, 21) DAY)
            END,
            CASE
                WHEN MOD(v_i, 9) = 0 THEN NULL
                ELSE DATE_SUB(
                    CASE
                        WHEN MOD(v_i, 10) = 0 THEN DATE_SUB(v_seed_now, INTERVAL (1 + MOD(v_i, 18)) DAY)
                        WHEN MOD(v_i, 8) = 0 THEN DATE_ADD(CAST(v_seed_today AS DATETIME), INTERVAL (1 + MOD(v_i, 14)) HOUR)
                        ELSE DATE_ADD(v_seed_now, INTERVAL MOD(v_i, 21) DAY)
                    END,
                    INTERVAL 30 MINUTE
                )
            END,
            CASE WHEN v_tarea_estado = 'COMPLETADA' THEN DATE_SUB(v_seed_now, INTERVAL MOD(v_i, 20) DAY) ELSE NULL END,
            v_tarea_estado,
            CASE WHEN MOD(v_i, 5) = 0 THEN 'ALTA' WHEN MOD(v_i, 3) = 0 THEN 'MEDIA' ELSE 'BAJA' END
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= 820 DO
        SET v_alerta_tipo = CASE MOD(v_i, 8)
            WHEN 0 THEN 'SIN_RESPUESTA'
            WHEN 1 THEN 'SIN_AVANCE'
            WHEN 2 THEN 'OFERTA_POR_VENCER'
            WHEN 3 THEN 'CAPTACION_VENCIDA'
            WHEN 4 THEN 'SOLICITUD_DOCUMENTO'
            WHEN 5 THEN 'SOLICITUD_EVALUADA'
            WHEN 6 THEN 'COMISION_ASIGNADA'
            ELSE 'VISITA_PROXIMA'
        END;

        IF v_alerta_tipo IN ('SIN_RESPUESTA', 'SIN_AVANCE') THEN
            SET v_entity_tipo = 'OPORTUNIDAD';
            SELECT id_oportunidad, id_agente INTO v_entity_id, v_agente_id
            FROM oportunidad_comercial
            WHERE codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(1 + MOD(v_i, v_oportunidad_count), 4, '0'))
            LIMIT 1;
        ELSEIF v_alerta_tipo IN ('OFERTA_POR_VENCER', 'SOLICITUD_DOCUMENTO', 'SOLICITUD_EVALUADA') THEN
            SET v_entity_tipo = 'SOLICITUD_ALQUILER';
            SELECT id_solicitud, id_agente INTO v_entity_id, v_agente_id
            FROM solicitud_alquiler
            WHERE codigo_solicitud = CONCAT('SOL-CAR-2026-', LPAD(1 + MOD(v_i, v_solicitud_count), 4, '0'))
            LIMIT 1;
        ELSEIF v_alerta_tipo = 'COMISION_ASIGNADA' THEN
            SET v_entity_tipo = 'CONTRATO_ALQUILER';
            SELECT ca.id_contrato_alquiler, o.id_agente INTO v_entity_id, v_agente_id
            FROM contrato_alquiler ca
            INNER JOIN oportunidad_comercial o ON o.id_oportunidad = ca.id_oportunidad
            WHERE o.codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(1 + MOD(v_i, v_contrato_count), 4, '0'))
            LIMIT 1;
        ELSEIF v_alerta_tipo = 'VISITA_PROXIMA' THEN
            SET v_entity_tipo = 'VISITA';
            SELECT vta.id_visita, vta.id_agente INTO v_entity_id, v_agente_id
            FROM visita vta
            INNER JOIN oportunidad_comercial o ON o.id_oportunidad = vta.id_oportunidad
            WHERE o.codigo_oportunidad = CONCAT('OPO-CAR-2026-', LPAD(1 + MOD(v_i, v_oportunidad_count), 4, '0'))
            LIMIT 1;
        ELSE
            SET v_entity_tipo = 'CAPTACION';
            SELECT id_captacion, id_agente INTO v_entity_id, v_agente_id
            FROM captacion
            WHERE codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(1 + MOD(v_i, v_captacion_count), 4, '0'))
            LIMIT 1;
        END IF;

        INSERT INTO alerta (
            tipo, severidad, entidad_tipo, entidad_id, id_agente,
            mensaje, estado, fecha_generacion, fecha_resolucion
        ) VALUES (
            v_alerta_tipo,
            CASE WHEN MOD(v_i, 5) = 0 THEN 'ALTA' WHEN MOD(v_i, 3) = 0 THEN 'MEDIA' ELSE 'INFO' END,
            v_entity_tipo,
            v_entity_id,
            v_agente_id,
            CONCAT('Alerta CAR-2026 ', v_alerta_tipo, ' para ', v_entity_tipo, ' ', v_entity_id, '.'),
            CASE WHEN MOD(v_i, 6) = 0 THEN 'ATENDIDA' WHEN MOD(v_i, 17) = 0 THEN 'DESCARTADA' ELSE 'ACTIVA' END,
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 2, 120) DAY),
            CASE WHEN MOD(v_i, 6) = 0 THEN DATE_SUB(v_seed_now, INTERVAL MOD(v_i, 60) DAY) ELSE NULL END
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_captacion_count DO
        SELECT c.id_captacion, c.id_agente, c.estado, a.id_usuario
        INTO v_entity_id, v_agente_id, v_cap_estado, v_usuario_id
        FROM captacion c
        INNER JOIN agente_inmobiliario a ON a.id_agente = c.id_agente
        WHERE c.codigo_captacion = CONCAT('CAP-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        INSERT INTO historial_estado (
            entidad_tipo, entidad_id, estado_anterior, estado_nuevo,
            id_usuario, fecha_evento, observacion
        ) VALUES (
            'CAPTACION',
            v_entity_id,
            'P',
            v_cap_estado,
            v_usuario_id,
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 4, 365) DAY),
            'Cambio de estado CAR-2026 para trazabilidad de revision de captacion.'
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET v_i = 1;
    WHILE v_i <= v_solicitud_count DO
        SELECT s.id_solicitud, s.estado, a.id_usuario
        INTO v_entity_id, v_sol_estado, v_usuario_id
        FROM solicitud_alquiler s
        INNER JOIN agente_inmobiliario a ON a.id_agente = s.id_agente
        WHERE s.codigo_solicitud = CONCAT('SOL-CAR-2026-', LPAD(v_i, 4, '0'))
        LIMIT 1;

        INSERT INTO historial_estado (
            entidad_tipo, entidad_id, estado_anterior, estado_nuevo,
            id_usuario, fecha_evento, observacion
        ) VALUES (
            'SOLICITUD_ALQUILER',
            v_entity_id,
            'G',
            v_sol_estado,
            v_usuario_id,
            DATE_SUB(v_seed_now, INTERVAL MOD(v_i * 3, 365) DAY),
            'Cambio de estado CAR-2026 para control de solicitudes.'
        );

        SET v_i = v_i + 1;
    END WHILE;

    SET SQL_SAFE_UPDATES = v_old_sql_safe_updates;
END$$

START TRANSACTION$$
CALL seed_cartera_operativa_masiva()$$
COMMIT$$

DROP PROCEDURE IF EXISTS seed_cartera_operativa_masiva$$

DELIMITER ;
