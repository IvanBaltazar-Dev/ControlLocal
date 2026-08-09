-- =====================================================================
-- V3: seed de identidad con PARIDAD respecto de database/02_seed_base_data.sql:
-- 1 broker administrador (BRK-ADM-001) + 5 brokers (BRK-001..005) + 15 agentes (AGE-001..015).
-- Credenciales dev: admin Admin2026 / brokers Broker2026 / agentes Agente2026.
-- Los hashes PBKDF2 (pbkdf2$iteraciones$sal$hash) se reutilizan tal cual de la v1:
-- el formato es portable entre el PasswordHasher Jakarta y el de Spring.
-- =====================================================================

CREATE TEMPORARY TABLE seed_usuario (
    tipo_usuario      VARCHAR(10)  NOT NULL, -- BROKER | AGENTE (coincide con tipo_rol)
    tipo_persona      VARCHAR(1)      NOT NULL,
    tipo_documento    VARCHAR(1)      NOT NULL,
    numero_documento  VARCHAR(30)  NOT NULL,
    nombres           VARCHAR(150) NOT NULL,
    telefono          VARCHAR(20)  NOT NULL,
    correo            VARCHAR(150) NOT NULL,
    nombre_usuario    VARCHAR(60)  NOT NULL,
    contrasena_hash   VARCHAR(255) NOT NULL,
    codigo_operativo  VARCHAR(20)  NOT NULL,
    zona              VARCHAR(100) NOT NULL,
    fecha_alta        DATE         NOT NULL,
    es_administrador  BOOLEAN      NOT NULL,
    broker_supervisor VARCHAR(20)
) ON COMMIT DROP;

INSERT INTO seed_usuario VALUES
    ('BROKER', 'N', 'D', '00000000', 'Broker Administrador ControlLocal', '999999999', 'admin@controllocal.test', 'admin@controllocal.test',
        'pbkdf2$100000$uy2GnOLWMudcyeMG7pKhjA==$3twwP9cAqG+ykRGAx5BmI8ZTAPa3w2dcwviW8dqvDdE=', 'BRK-ADM-001', 'Sede central', DATE '2024-01-02', TRUE, NULL),
    ('BROKER', 'N', 'D', '08412991', 'Ricardo Salas', '998110220', 'rsalas@controllocal.pe', 'rsalas',
        'pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=', 'BRK-001', 'Lima Centro / Sur', DATE '2024-01-11', FALSE, NULL),
    ('BROKER', 'N', 'D', '09644120', 'Patricia Soto', '998110221', 'psoto@controllocal.pe', 'psoto',
        'pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=', 'BRK-002', 'Lima Norte / Este', DATE '2024-02-05', FALSE, NULL),
    ('BROKER', 'N', 'D', '09711233', 'Gabriela Nunez', '998110223', 'gnunez@controllocal.pe', 'gnunez',
        'pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=', 'BRK-003', 'Lima Sur', DATE '2024-05-06', FALSE, NULL),
    ('BROKER', 'N', 'D', '10522344', 'Martin Aguirre', '998110224', 'maguirre@controllocal.pe', 'maguirre',
        'pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=', 'BRK-004', 'Lima Este', DATE '2024-06-10', FALSE, NULL),
    ('BROKER', 'N', 'D', '11633455', 'Sofia Ramirez', '998110225', 'sramirez@controllocal.pe', 'sramirez',
        'pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=', 'BRK-005', 'Lima Oeste', DATE '2024-07-15', FALSE, NULL),
    ('AGENTE', 'N', 'D', '45893211', 'Valentina Mora', '998110311', 'vmora@controllocal.pe', 'vmora',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-001', 'Lima Centro', DATE '2024-02-14', FALSE, 'BRK-001'),
    ('AGENTE', 'N', 'D', '46778122', 'Javier Ruiz', '998110312', 'jruiz@controllocal.pe', 'jruiz',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-002', 'Lima Moderna', DATE '2024-03-04', FALSE, 'BRK-001'),
    ('AGENTE', 'N', 'D', '47220933', 'Lucia Torres', '998110313', 'ltorres@controllocal.pe', 'ltorres',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-003', 'Lima Norte', DATE '2024-03-18', FALSE, 'BRK-002'),
    ('AGENTE', 'N', 'D', '48111544', 'Camila Reyes', '998110314', 'creyes@controllocal.pe', 'creyes',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-004', 'Lima Este', DATE '2024-04-01', FALSE, 'BRK-001'),
    ('AGENTE', 'N', 'D', '45100005', 'Pedro Quispe', '998110315', 'pquispe@controllocal.pe', 'pquispe',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-005', 'Lima Centro', DATE '2024-05-20', FALSE, 'BRK-001'),
    ('AGENTE', 'N', 'D', '45100006', 'Rosa Mendoza', '998110316', 'rmendoza@controllocal.pe', 'rmendoza',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-006', 'Lima Norte', DATE '2024-06-03', FALSE, 'BRK-002'),
    ('AGENTE', 'N', 'D', '45100007', 'Carlos Vargas', '998110317', 'cvargas@controllocal.pe', 'cvargas',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-007', 'Lima Sur', DATE '2024-06-17', FALSE, 'BRK-003'),
    ('AGENTE', 'N', 'D', '45100008', 'Elena Flores', '998110318', 'eflores@controllocal.pe', 'eflores',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-008', 'Lima Sur', DATE '2024-07-01', FALSE, 'BRK-003'),
    ('AGENTE', 'N', 'D', '45100009', 'Jorge Diaz', '998110319', 'jdiaz@controllocal.pe', 'jdiaz',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-009', 'Lima Sur', DATE '2024-07-22', FALSE, 'BRK-003'),
    ('AGENTE', 'N', 'D', '45100010', 'Ana Salazar', '998110320', 'asalazar@controllocal.pe', 'asalazar',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-010', 'Lima Este', DATE '2024-08-05', FALSE, 'BRK-004'),
    ('AGENTE', 'N', 'D', '45100011', 'Luis Campos', '998110321', 'lcampos@controllocal.pe', 'lcampos',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-011', 'Lima Este', DATE '2024-08-19', FALSE, 'BRK-004'),
    ('AGENTE', 'N', 'D', '45100012', 'Maria Rojas', '998110322', 'mrojas@controllocal.pe', 'mrojas',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-012', 'Lima Este', DATE '2024-09-02', FALSE, 'BRK-004'),
    ('AGENTE', 'N', 'D', '45100013', 'Fernando Leon', '998110323', 'fleon@controllocal.pe', 'fleon',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-013', 'Lima Oeste', DATE '2024-09-16', FALSE, 'BRK-005'),
    ('AGENTE', 'N', 'D', '45100014', 'Patricia Vega', '998110324', 'pvega@controllocal.pe', 'pvega',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-014', 'Lima Oeste', DATE '2024-10-07', FALSE, 'BRK-005'),
    ('AGENTE', 'N', 'D', '45100015', 'Ricardo Gomez', '998110325', 'rgomez@controllocal.pe', 'rgomez',
        'pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=', 'AGE-015', 'Lima Oeste', DATE '2024-10-21', FALSE, 'BRK-005');

-- 1) Party: una persona por usuario del seed.
INSERT INTO persona (tipo_persona, tipo_documento, numero_documento, nombres_o_razon_social,
                     telefono, correo, estado, consentimiento_uso_dato)
SELECT tipo_persona, tipo_documento, numero_documento, nombres, telefono, correo, 'A', TRUE
FROM seed_usuario;

-- 2) Rol USUARIO_INTERNO (acceso al sistema) + credenciales.
INSERT INTO persona_rol (id_persona, tipo_rol, vigencia_desde)
SELECT p.id_persona, 'USUARIO_INTERNO', s.fecha_alta
FROM seed_usuario s
JOIN persona p ON p.tipo_documento = s.tipo_documento AND p.numero_documento = s.numero_documento;

INSERT INTO credencial_usuario (id_persona_rol, nombre_usuario, contrasena_hash, estado_administrativo)
SELECT r.id_persona_rol, s.nombre_usuario, s.contrasena_hash, 'A'
FROM seed_usuario s
JOIN persona p ON p.tipo_documento = s.tipo_documento AND p.numero_documento = s.numero_documento
JOIN persona_rol r ON r.id_persona = p.id_persona AND r.tipo_rol = 'USUARIO_INTERNO';

-- 3) Rol operativo (BROKER o AGENTE) + detalle por rol.
INSERT INTO persona_rol (id_persona, tipo_rol, vigencia_desde)
SELECT p.id_persona, s.tipo_usuario, s.fecha_alta
FROM seed_usuario s
JOIN persona p ON p.tipo_documento = s.tipo_documento AND p.numero_documento = s.numero_documento;

INSERT INTO detalle_broker (id_persona_rol, codigo_broker, zona, fecha_designacion, es_administrador)
SELECT r.id_persona_rol, s.codigo_operativo, s.zona, s.fecha_alta, s.es_administrador
FROM seed_usuario s
JOIN persona p ON p.tipo_documento = s.tipo_documento AND p.numero_documento = s.numero_documento
JOIN persona_rol r ON r.id_persona = p.id_persona AND r.tipo_rol = 'BROKER'
WHERE s.tipo_usuario = 'BROKER';

INSERT INTO detalle_agente (id_persona_rol, codigo_agente, zona_asignada, fecha_ingreso, estado_operativo)
SELECT r.id_persona_rol, s.codigo_operativo, s.zona, s.fecha_alta, 'D'
FROM seed_usuario s
JOIN persona p ON p.tipo_documento = s.tipo_documento AND p.numero_documento = s.numero_documento
JOIN persona_rol r ON r.id_persona = p.id_persona AND r.tipo_rol = 'AGENTE'
WHERE s.tipo_usuario = 'AGENTE';

-- 4) Supervision inicial broker->agente (paridad con broker_agente de la v1).
INSERT INTO supervision_agente (id_rol_broker, id_rol_agente, fecha_asignacion, motivo)
SELECT db.id_persona_rol, da.id_persona_rol, s.fecha_alta, 'Asignacion inicial de ' || s.nombres
FROM seed_usuario s
JOIN detalle_agente da ON da.codigo_agente = s.codigo_operativo
JOIN detalle_broker db ON db.codigo_broker = s.broker_supervisor
WHERE s.tipo_usuario = 'AGENTE';
