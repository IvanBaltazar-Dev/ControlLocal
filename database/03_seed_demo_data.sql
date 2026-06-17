-- =========================================================
-- ControlLocal - demo operativo amplio
-- Requiere: 02_seed_base_data.sql
--
-- Carga una muestra suficiente para probar:
--   - reasignacion de agente entre brokers;
--   - reasignacion de captaciones entre agentes;
--   - captaciones en varios estados;
--   - clientes, requerimientos, oportunidades, visitas y solicitudes;
--   - documentos, evaluaciones, contratos, tareas y alertas.
--
-- Idempotente: usa codigos demo estables y no duplica filas.
-- =========================================================

USE controllocal;

START TRANSACTION;

-- =========================================================
-- Personas demo: propietarios y clientes
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_persona_demo;

CREATE TEMPORARY TABLE seed_persona_demo (
    perfil VARCHAR(20) NOT NULL,
    tipo_persona CHAR(1) NOT NULL,
    tipo_documento CHAR(1) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL,
    nombres_o_razon_social VARCHAR(150) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    correo VARCHAR(150) NOT NULL,
    rubro_comercial VARCHAR(120) NULL
) ENGINE=MEMORY;

INSERT INTO seed_persona_demo VALUES
    ('PROPIETARIO', 'J', 'R', '20580000001', 'Inversiones Mirador S.A.C.', '945200101', 'administracion@mirador.demo', NULL),
    ('PROPIETARIO', 'N', 'D', '70110001', 'Carmen Vela Arce', '945200102', 'carmen.vela@demo.local', NULL),
    ('PROPIETARIO', 'J', 'R', '20580000027', 'Grupo San Borja S.A.C.', '945200103', 'activos@gruposanborja.demo', NULL),
    ('PROPIETARIO', 'N', 'D', '70110004', 'Luis Paredes Montalvo', '945200104', 'luis.paredes@demo.local', NULL),
    ('PROPIETARIO', 'J', 'R', '20580000051', 'Retail Norte S.A.C.', '945200105', 'inmuebles@retailnorte.demo', NULL),
    ('PROPIETARIO', 'N', 'D', '70110006', 'Milagros Chaname Rios', '945200106', 'milagros.chaname@demo.local', NULL),
    ('CLIENTE', 'J', 'R', '20610000011', 'Mercado Uno S.A.C.', '946100101', 'contacto@mercadouno.demo', 'Minimarket'),
    ('CLIENTE', 'J', 'R', '20610000029', 'Showroom Centro S.A.C.', '946100202', 'gerencia@showroomcentro.demo', 'Moda y exhibicion'),
    ('CLIENTE', 'J', 'R', '20610000037', 'Clinica Dental Sonrisa S.A.C.', '946100303', 'admin@sonrisadental.demo', 'Servicios odontologicos'),
    ('CLIENTE', 'J', 'R', '20610000045', 'Cafeteria Barranco E.I.R.L.', '946100404', 'hola@cafebarranco.demo', 'Cafeteria'),
    ('CLIENTE', 'J', 'R', '20610000053', 'Cowork Andes S.A.C.', '946100505', 'operaciones@coworkandes.demo', 'Coworking'),
    ('CLIENTE', 'J', 'R', '20610000061', 'Farmacia Salud 24 S.A.C.', '946100606', 'expansion@salud24.demo', 'Farmacia'),
    ('CLIENTE', 'N', 'D', '76000001', 'Andrea Huaman Quispe', '946100707', 'andrea.huaman@demo.local', 'Boutique'),
    ('CLIENTE', 'N', 'D', '76000002', 'Diego Castillo Flores', '946100808', 'diego.castillo@demo.local', 'Restaurante');

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
FROM seed_persona_demo
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
FROM seed_persona_demo s
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
FROM seed_persona_demo s
INNER JOIN persona p ON p.numero_documento = s.numero_documento
WHERE s.perfil = 'CLIENTE'
ON DUPLICATE KEY UPDATE
    rubro_comercial = VALUES(rubro_comercial),
    consentimiento_contacto = VALUES(consentimiento_contacto),
    consentimiento_uso_dato = VALUES(consentimiento_uso_dato);

-- =========================================================
-- Reasignacion demo de agente entre brokers
-- AGE-004 nace bajo BRK-001 en el seed base y aqui pasa a BRK-002.
-- =========================================================

SET @id_broker_admin = (SELECT id_broker FROM broker WHERE codigo_broker = 'BRK-ADM-001' LIMIT 1);
SET @id_broker_001 = (SELECT id_broker FROM broker WHERE codigo_broker = 'BRK-001' LIMIT 1);
SET @id_broker_002 = (SELECT id_broker FROM broker WHERE codigo_broker = 'BRK-002' LIMIT 1);
SET @id_agente_004 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-004' LIMIT 1);

UPDATE broker_agente
SET fecha_fin = '2026-05-30',
    estado = 'I',
    motivo = 'Cierre por reasignacion demo de Camila Reyes a Patricia Soto'
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
    'Asignacion vigente despues de reasignacion demo',
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

DROP TEMPORARY TABLE IF EXISTS seed_local_demo;

CREATE TEMPORARY TABLE seed_local_demo (
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

INSERT INTO seed_local_demo VALUES
    ('LC-DEMO-001', '20580000001', 'Av. La Marina 1532, tienda 101', 'San Miguel', 78.50, 6800.00, 'Comercio vecinal', 'Local con frente a avenida y alto flujo peatonal.', 'D', 'L', 2, 8, 'Maranga', -12.0784250, -77.0907310, 7.50, 'CZ', TRUE, 20.00, 1, 350.00),
    ('LC-DEMO-002', '20580000001', 'Jr. Junin 425, segundo nivel', 'Lima', 120.00, 9500.00, 'Showroom y oficina comercial', 'Inmueble con acceso independiente y operacion demo cerrada.', 'N', 'O', 4, 12, 'Centro Historico', -12.0452140, -77.0281220, 9.20, 'ZTE-1', TRUE, 30.00, 0, 480.00),
    ('LC-DEMO-003', '20580000027', 'Av. Aviacion 2450, local 3', 'San Borja', 95.00, 8200.00, 'Salud, estetica y servicios', 'Local en esquina cerca a estacion de tren.', 'D', 'L', 3, 6, 'San Borja Norte', -12.0972100, -77.0046500, 8.10, 'CZ', TRUE, 25.00, 2, 420.00),
    ('LC-DEMO-004', '70110001', 'Av. Alfredo Benavides 3890', 'Santiago de Surco', 62.00, 7200.00, 'Cafeteria y servicios rapidos', 'Local compacto para marca de comida o cafe.', 'D', 'L', 2, 4, 'Higuereta', -12.1287600, -76.9992200, 6.20, 'CZ', TRUE, 18.00, 1, 300.00),
    ('LC-DEMO-005', '70110004', 'Av. Mexico 1201', 'La Victoria', 180.00, 12500.00, 'Almacen ligero y showroom', 'Amplio metraje con acceso para carga liviana.', 'D', 'L', 5, 15, 'Santa Catalina', -12.0745100, -77.0187400, 11.00, 'CM', TRUE, 45.00, 2, 650.00),
    ('LC-DEMO-006', '20580000051', 'Av. Universitaria 5120', 'Los Olivos', 110.00, 7600.00, 'Farmacia y conveniencia', 'Local a pie de avenida con estacionamiento frontal.', 'D', 'L', 3, 7, 'Palmeras', -11.9813200, -77.0731100, 9.00, 'CZ', TRUE, 28.00, 3, 390.00),
    ('LC-DEMO-007', '20580000027', 'Calle Las Begonias 441, piso 2', 'San Isidro', 140.00, 15500.00, 'Oficina comercial y coworking', 'Oficina implementada en zona empresarial.', 'D', 'O', 6, 10, 'Centro Financiero', -12.0931800, -77.0278800, 10.00, 'CZ', TRUE, 35.00, 2, 900.00),
    ('LC-DEMO-008', '70110006', 'Av. Jose Larco 812', 'Miraflores', 85.00, 11000.00, 'Retail especializado', 'Local vitrina en zona de alto transito turistico.', 'N', 'L', 2, 9, 'Larco', -12.1239400, -77.0308800, 7.00, 'CZ', TRUE, 22.00, 0, 520.00),
    ('LC-DEMO-009', '70110004', 'Av. Proceres 640', 'San Juan de Miraflores', 70.00, 4800.00, 'Servicios barriales', 'Local economico para servicios de cercania.', 'D', 'L', 2, 18, 'Ciudad de Dios', -12.1579400, -76.9709100, 5.80, 'CZ', TRUE, 15.00, 0, 180.00),
    ('LC-DEMO-010', '20580000051', 'Av. Primavera 1245', 'Santiago de Surco', 100.00, 9800.00, 'Restaurante y minimarket', 'Local con buena fachada y alto flujo vehicular.', 'D', 'L', 3, 5, 'Monterrico', -12.1098300, -76.9756200, 8.50, 'CZ', TRUE, 32.00, 2, 430.00);

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
FROM seed_local_demo l
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

DROP TEMPORARY TABLE IF EXISTS seed_precio_demo;

CREATE TEMPORARY TABLE seed_precio_demo (
    codigo_local VARCHAR(20) NOT NULL,
    hito CHAR(1) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    fecha DATE NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_precio_demo VALUES
    ('LC-DEMO-001', 'P', 'PEN', 6800.00, '2026-01-15'),
    ('LC-DEMO-002', 'C', 'PEN', 9000.00, '2026-03-20'),
    ('LC-DEMO-003', 'R', 'PEN', 8000.00, '2026-04-02'),
    ('LC-DEMO-004', 'E', 'PEN', 7200.00, '2026-04-10'),
    ('LC-DEMO-006', 'P', 'PEN', 7600.00, '2026-05-03'),
    ('LC-DEMO-007', 'P', 'USD', 4100.00, '2026-05-09'),
    ('LC-DEMO-010', 'O', 'PEN', 9400.00, '2026-05-18');

INSERT INTO precio_local (id_local, hito, moneda, monto, fecha)
SELECT
    l.id_local,
    p.hito,
    p.moneda,
    p.monto,
    p.fecha
FROM seed_precio_demo p
INNER JOIN local_comercial l ON l.codigo_local = p.codigo_local
WHERE NOT EXISTS (
    SELECT 1
    FROM precio_local px
    WHERE px.id_local = l.id_local
      AND px.hito = p.hito
      AND px.fecha = p.fecha
);

DROP TEMPORARY TABLE IF EXISTS seed_publicacion_demo;

CREATE TEMPORARY TABLE seed_publicacion_demo (
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

INSERT INTO seed_publicacion_demo VALUES
    ('PUB-DEMO-001', 'LC-DEMO-001', 'URBANIA', 'Local comercial en avenida principal de San Miguel', 6800.00, 'PEN', 180.00, '2026-01-15 08:00:00', NULL, 'P'),
    ('PUB-DEMO-002', 'LC-DEMO-002', 'WEB_PROPIA', 'Oficina comercial en el Centro de Lima', 9500.00, 'PEN', 0.00, '2026-02-05 08:00:00', '2026-03-20 18:00:00', 'C'),
    ('PUB-DEMO-003', 'LC-DEMO-003', 'ADONDEVIVIR', 'Local para consultorio o servicios en San Borja', 8200.00, 'PEN', 220.00, '2026-04-03 09:00:00', NULL, 'P'),
    ('PUB-DEMO-004', 'LC-DEMO-006', 'FACEBOOK', 'Local con estacionamientos en Los Olivos', 7600.00, 'PEN', 160.00, '2026-05-04 09:00:00', NULL, 'P'),
    ('PUB-DEMO-005', 'LC-DEMO-007', 'WEB_PROPIA', 'Oficina implementada en San Isidro', 4100.00, 'USD', 0.00, '2026-05-10 09:00:00', NULL, 'P'),
    ('PUB-DEMO-006', 'LC-DEMO-010', 'INSTAGRAM', 'Local comercial en Primavera Surco', 9800.00, 'PEN', 190.00, '2026-05-20 09:00:00', NULL, 'P');

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
    CONCAT('https://demo.local/publicaciones/', LOWER(p.codigo_origen)),
    1,
    p.titulo_anuncio,
    p.renta_publicada,
    p.moneda,
    p.inversion_pauta,
    p.codigo_origen,
    p.fecha_publicacion,
    p.fecha_baja,
    p.estado
FROM seed_publicacion_demo p
INNER JOIN local_comercial l ON l.codigo_local = p.codigo_local
WHERE NOT EXISTS (
    SELECT 1
    FROM publicacion px
    WHERE px.codigo_origen = p.codigo_origen
);

-- =========================================================
-- Captaciones y reasignacion de captacion
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_captacion_demo;

CREATE TEMPORARY TABLE seed_captacion_demo (
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

INSERT INTO seed_captacion_demo VALUES
    ('CAP-DEMO-001', 'LC-DEMO-001', 'AGE-001', 'BRK-001', '2026-01-10', '2026-01-10', '2026-12-31', 6800.00, 'Captacion activa de Valentina Mora.', 'A', '2026-01-11 10:00:00', 'Documentacion conforme.', 3, TRUE),
    ('CAP-DEMO-002', 'LC-DEMO-002', 'AGE-001', 'BRK-001', '2026-02-01', '2026-02-01', '2027-01-31', 9000.00, 'Captacion asociada a contrato demo.', 'A', '2026-02-02 09:30:00', 'Aprobada para publicacion.', 4, TRUE),
    ('CAP-DEMO-003', 'LC-DEMO-003', 'AGE-002', 'BRK-001', '2026-04-01', '2026-04-01', '2027-03-31', 8200.00, 'Captacion reasignada de Valentina a Javier.', 'A', '2026-04-02 15:00:00', 'Reasignada por capacidad comercial.', 5, TRUE),
    ('CAP-DEMO-004', 'LC-DEMO-004', 'AGE-003', NULL, '2026-04-10', '2026-04-10', '2027-04-09', 7200.00, 'Pendiente de revision del broker.', 'P', NULL, NULL, 2, FALSE),
    ('CAP-DEMO-005', 'LC-DEMO-005', 'AGE-003', 'BRK-002', '2026-04-15', '2026-04-15', '2027-04-14', 12500.00, 'Observada por fotos incompletas.', 'O', '2026-04-16 11:00:00', 'Completar fotos interiores y zonificacion.', 4, FALSE),
    ('CAP-DEMO-006', 'LC-DEMO-006', 'AGE-004', 'BRK-002', '2026-05-02', '2026-05-02', '2027-05-01', 7600.00, 'Captacion activa de Camila Reyes.', 'A', '2026-05-03 12:00:00', 'Aprobada.', 3, TRUE),
    ('CAP-DEMO-007', 'LC-DEMO-007', 'AGE-002', 'BRK-001', '2026-05-08', '2026-05-08', '2027-05-07', 15500.00, 'Oficina activa para cartera corporativa.', 'A', '2026-05-09 12:00:00', 'Aprobada.', 2, FALSE),
    ('CAP-DEMO-008', 'LC-DEMO-008', 'AGE-004', 'BRK-002', '2026-03-01', '2026-03-01', '2026-05-31', 10500.00, 'Captacion cerrada por retiro del propietario.', 'C', '2026-03-02 09:00:00', 'Cierre solicitado por propietario.', 1, FALSE),
    ('CAP-DEMO-009', 'LC-DEMO-009', 'AGE-001', 'BRK-001', '2025-10-01', '2025-10-01', '2026-03-31', 4800.00, 'Captacion vencida para prueba de alertas.', 'V', '2025-10-02 09:00:00', 'Vigencia vencida.', 1, FALSE),
    ('CAP-DEMO-010', 'LC-DEMO-010', 'AGE-003', 'BRK-002', '2026-05-18', '2026-05-18', '2027-05-17', 9800.00, 'Captacion activa con oportunidad reciente.', 'A', '2026-05-19 14:00:00', 'Aprobada para pauta digital.', 4, TRUE);

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
FROM seed_captacion_demo c
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

SET @id_cap_demo_003 = (SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-DEMO-003' LIMIT 1);
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
    @id_cap_demo_003,
    @id_agente_001,
    @id_agente_002,
    @id_broker_001
WHERE @id_cap_demo_003 IS NOT NULL
  AND @id_agente_001 IS NOT NULL
  AND @id_agente_002 IS NOT NULL
  AND @id_broker_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM reasignacion_captacion
      WHERE id_captacion = @id_cap_demo_003
        AND fecha_cambio = '2026-04-12 10:30:00'
  );

-- =========================================================
-- Prospecciones
-- =========================================================

DROP TEMPORARY TABLE IF EXISTS seed_prospeccion_demo;

CREATE TEMPORARY TABLE seed_prospeccion_demo (
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

INSERT INTO seed_prospeccion_demo VALUES
    ('PRO-DEMO-001', 'LC-DEMO-001', 'AGE-001', 'CAP-DEMO-001', '2026-01-05 09:00:00', 'T', 'A', '2026-01-05', '2026-01-07', '2026-01-09', NULL, 'Prospeccion convertida en captacion.'),
    ('PRO-DEMO-002', 'LC-DEMO-004', 'AGE-003', NULL, '2026-04-06 09:00:00', 'R', NULL, '2026-04-06', '2026-04-08', NULL, NULL, 'Reunion pendiente de propuesta final.'),
    ('PRO-DEMO-003', 'LC-DEMO-006', 'AGE-004', 'CAP-DEMO-006', '2026-04-24 10:00:00', 'T', 'A', '2026-04-24', '2026-04-27', '2026-05-01', NULL, 'Propietario acepto pauta digital.'),
    ('PRO-DEMO-004', 'LC-DEMO-009', 'AGE-001', NULL, '2025-09-20 10:00:00', 'D', 'R', '2025-09-20', '2025-09-23', '2025-09-25', NULL, 'Descartada inicialmente por precio.'),
    ('PRO-DEMO-005', 'LC-DEMO-010', 'AGE-003', 'CAP-DEMO-010', '2026-05-10 10:00:00', 'T', 'A', '2026-05-10', '2026-05-12', '2026-05-17', NULL, 'Prospeccion captada para campana Primavera.');

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
FROM seed_prospeccion_demo p
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

DROP TEMPORARY TABLE IF EXISTS seed_requerimiento_demo;

CREATE TEMPORARY TABLE seed_requerimiento_demo (
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

INSERT INTO seed_requerimiento_demo VALUES
    ('20610000011', 'Minimarket', 'LOCAL_COMERCIAL', 5000.00, 7500.00, 'PEN', 60.00, 100.00, 6.00, 'ACTIVO', 'Busca avenida principal y licencia compatible.', 'San Miguel', 'Pueblo Libre'),
    ('20610000029', 'Moda y exhibicion', 'LOCAL_COMERCIAL', 8000.00, 11000.00, 'PEN', 90.00, 140.00, 7.00, 'CERRADO', 'Operacion cerrada en Centro de Lima.', 'Lima', NULL),
    ('20610000037', 'Clinica dental', 'LOCAL_COMERCIAL', 7500.00, 9500.00, 'PEN', 80.00, 120.00, 6.00, 'ACTIVO', 'Necesita zonificacion para servicios de salud.', 'San Borja', 'Surquillo'),
    ('20610000045', 'Cafeteria', 'LOCAL_COMERCIAL', 6000.00, 7800.00, 'PEN', 50.00, 80.00, 5.00, 'ACTIVO', 'Prioriza flujo peatonal y terraza.', 'Barranco', 'Santiago de Surco'),
    ('20610000053', 'Coworking', 'OFICINA', 12000.00, 18000.00, 'PEN', 120.00, 180.00, 8.00, 'ACTIVO', 'Busca oficina implementada para 25 puestos.', 'San Isidro', 'Miraflores'),
    ('20610000061', 'Farmacia', 'LOCAL_COMERCIAL', 6500.00, 8500.00, 'PEN', 80.00, 130.00, 7.00, 'ACTIVO', 'Necesita alto transito y estacionamiento.', 'Los Olivos', 'Comas'),
    ('76000001', 'Boutique', 'LOCAL_COMERCIAL', 8000.00, 12000.00, 'PEN', 70.00, 110.00, 6.00, 'ACTIVO', 'Busca local vitrina en zona comercial.', 'Santiago de Surco', 'Miraflores'),
    ('76000002', 'Restaurante', 'LOCAL_COMERCIAL', 8500.00, 10500.00, 'PEN', 90.00, 130.00, 7.00, 'PAUSADO', 'Evaluando inversion inicial.', 'Santiago de Surco', NULL);

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
FROM seed_requerimiento_demo r
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
FROM seed_requerimiento_demo r
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
FROM seed_requerimiento_demo r
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

DROP TEMPORARY TABLE IF EXISTS seed_oportunidad_demo;

CREATE TEMPORARY TABLE seed_oportunidad_demo (
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

INSERT INTO seed_oportunidad_demo VALUES
    ('OPO-DEMO-001', '20610000011', 'CAP-DEMO-001', 'AGE-001', 'PUB-DEMO-001', '2026-02-10 10:15:00', 'A', NULL, NULL, 'Cliente activo en etapa de seguimiento.', 'PORTAL', 'PUB-DEMO-001', '2026-02-10 10:15:00', NULL),
    ('OPO-DEMO-002', '20610000029', 'CAP-DEMO-002', 'AGE-001', 'PUB-DEMO-002', '2026-02-12 11:30:00', 'F', '2026-03-20 17:00:00', NULL, 'Operacion cerrada exitosamente.', 'WEB_PROPIA', 'PUB-DEMO-002', '2026-02-12 11:30:00', '2026-03-20 17:00:00'),
    ('OPO-DEMO-003', '20610000037', 'CAP-DEMO-003', 'AGE-002', 'PUB-DEMO-003', '2026-04-14 15:20:00', 'S', '2026-04-22 12:00:00', NULL, 'Solicitud creada y en revision.', 'PORTAL', 'PUB-DEMO-003', '2026-04-14 15:20:00', NULL),
    ('OPO-DEMO-004', '20610000045', 'CAP-DEMO-004', 'AGE-003', NULL, '2026-04-18 13:00:00', 'A', NULL, NULL, 'Cliente quiere visitar antes de oferta.', 'REFERIDO', 'REF-CAFE-01', '2026-04-18 13:00:00', NULL),
    ('OPO-DEMO-005', '20610000053', 'CAP-DEMO-007', 'AGE-002', 'PUB-DEMO-005', '2026-05-12 16:00:00', 'N', '2026-05-18 10:00:00', 'Precio fuera de rango', 'Cliente no continua por presupuesto.', 'WEB_PROPIA', 'PUB-DEMO-005', '2026-05-12 16:00:00', '2026-05-18 10:00:00'),
    ('OPO-DEMO-006', '20610000061', 'CAP-DEMO-006', 'AGE-004', 'PUB-DEMO-004', '2026-05-07 11:10:00', 'S', '2026-05-21 15:00:00', NULL, 'Solicitud registrada con documentos pendientes.', 'REDES_SOCIALES', 'PUB-DEMO-004', '2026-05-07 11:10:00', NULL),
    ('OPO-DEMO-007', '76000001', 'CAP-DEMO-010', 'AGE-003', 'PUB-DEMO-006', '2026-05-22 17:40:00', 'A', NULL, NULL, 'Cliente natural evalua boutique.', 'INSTAGRAM', 'PUB-DEMO-006', '2026-05-22 17:40:00', NULL),
    ('OPO-DEMO-008', '76000002', 'CAP-DEMO-001', 'AGE-001', 'PUB-DEMO-001', '2026-05-28 09:20:00', 'X', '2026-06-02 12:30:00', 'Desistio por inversion inicial', 'Cliente desistio antes de solicitud.', 'WHATSAPP', 'WA-76000002', '2026-05-28 09:20:00', '2026-06-02 12:30:00');

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
FROM seed_oportunidad_demo o
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

DROP TEMPORARY TABLE IF EXISTS seed_interaccion_demo;

CREATE TEMPORARY TABLE seed_interaccion_demo (
    codigo_oportunidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    fecha_hora DATETIME NOT NULL,
    canal_contacto CHAR(1) NOT NULL,
    resultado CHAR(1) NOT NULL,
    observaciones VARCHAR(500) NOT NULL,
    transcripcion_nota VARCHAR(500) NULL
) ENGINE=MEMORY;

INSERT INTO seed_interaccion_demo VALUES
    ('OPO-DEMO-001', 'AGE-001', '2026-02-10 10:20:00', 'W', 'I', 'Se envio ficha comercial y ubicacion.', 'Cliente solicita coordinar una visita.'),
    ('OPO-DEMO-001', 'AGE-001', '2026-02-16 09:30:00', 'L', 'S', 'Cliente evaluara con socio.', 'Llamar nuevamente en tres dias.'),
    ('OPO-DEMO-003', 'AGE-002', '2026-04-15 12:00:00', 'E', 'I', 'Se envio brochure y requisitos.', NULL),
    ('OPO-DEMO-004', 'AGE-003', '2026-04-18 13:30:00', 'R', 'P', 'Referido pide visita antes de propuesta.', NULL),
    ('OPO-DEMO-005', 'AGE-002', '2026-05-18 09:40:00', 'L', 'N', 'Cliente indica que la renta supera presupuesto.', NULL),
    ('OPO-DEMO-006', 'AGE-004', '2026-05-08 10:00:00', 'W', 'I', 'Cliente interesado por local cerca de avenida.', NULL),
    ('OPO-DEMO-007', 'AGE-003', '2026-05-23 11:00:00', 'T', 'S', 'Contacto desde Instagram con dudas de metraje.', NULL),
    ('OPO-DEMO-008', 'AGE-001', '2026-06-02 12:00:00', 'W', 'D', 'Cliente desiste por inversion inicial.', NULL);

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
FROM seed_interaccion_demo i
INNER JOIN oportunidad_comercial o ON o.codigo_oportunidad = i.codigo_oportunidad
INNER JOIN agente_inmobiliario a ON a.codigo_agente = i.codigo_agente
WHERE NOT EXISTS (
    SELECT 1
    FROM interaccion_comercial ix
    WHERE ix.id_oportunidad = o.id_oportunidad
      AND ix.fecha_hora = i.fecha_hora
);

DROP TEMPORARY TABLE IF EXISTS seed_visita_demo;

CREATE TEMPORARY TABLE seed_visita_demo (
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

INSERT INTO seed_visita_demo VALUES
    ('OPO-DEMO-001', 'AGE-001', '2026-02-14', '10:00:00', 'Visita realizada; cliente evalua distribucion.', 'R', 'I', 4, 'E', 'J', 'O'),
    ('OPO-DEMO-003', 'AGE-002', '2026-04-18', '11:00:00', 'Visita realizada; cliente pidio condiciones.', 'R', 'S', 5, 'C', 'J', 'O'),
    ('OPO-DEMO-004', 'AGE-003', '2026-04-21', '09:30:00', 'Visita pendiente de confirmacion.', 'P', NULL, NULL, NULL, NULL, NULL),
    ('OPO-DEMO-005', 'AGE-002', '2026-05-16', '16:00:00', 'Visita realizada; objecion principal precio.', 'R', 'N', 2, 'P', 'A', 'D'),
    ('OPO-DEMO-006', 'AGE-004', '2026-05-11', '10:30:00', 'Visita reprogramada por agenda del cliente.', 'G', NULL, NULL, NULL, NULL, NULL),
    ('OPO-DEMO-007', 'AGE-003', '2026-05-25', '12:00:00', 'Cliente cancelo por viaje.', 'C', NULL, NULL, NULL, NULL, NULL),
    ('OPO-DEMO-008', 'AGE-001', '2026-05-31', '18:00:00', 'No realizada; cliente no asistio.', 'N', NULL, NULL, NULL, NULL, NULL);

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
FROM seed_visita_demo v
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

DROP TEMPORARY TABLE IF EXISTS seed_solicitud_demo;

CREATE TEMPORARY TABLE seed_solicitud_demo (
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

INSERT INTO seed_solicitud_demo VALUES
    ('SOL-DEMO-001', 'OPO-DEMO-002', 'AGE-001', '2026-03-01', 9000.00, '24 meses', 'Oferta aceptada para contrato demo.', 'A', '2026-03-10 16:00:00', '2026-03-15'),
    ('SOL-DEMO-002', 'OPO-DEMO-003', 'AGE-002', '2026-04-22', 8000.00, '36 meses', 'Solicitud en revision documental.', 'E', '2026-04-23 11:00:00', '2026-04-30'),
    ('SOL-DEMO-003', 'OPO-DEMO-006', 'AGE-004', '2026-05-21', 7400.00, '24 meses', 'Documentos pendientes de regularizacion.', 'O', '2026-05-22 10:00:00', '2026-05-29');

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
FROM seed_solicitud_demo s
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

DROP TEMPORARY TABLE IF EXISTS seed_documento_demo;

CREATE TEMPORARY TABLE seed_documento_demo (
    codigo_solicitud VARCHAR(20) NOT NULL,
    id_tipo_documento_requerido BIGINT NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    fecha_entrega DATETIME NOT NULL,
    resultado_revision CHAR(1) NULL,
    observaciones VARCHAR(500) NULL,
    estado CHAR(1) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_documento_demo VALUES
    ('SOL-DEMO-001', 1, 'documento-identidad-sol-demo-001.pdf', '2026-03-02 09:00:00', 'C', 'Documento conforme.', 'V'),
    ('SOL-DEMO-001', 5, 'sustento-economico-sol-demo-001.pdf', '2026-03-02 09:05:00', 'C', 'Solvencia validada.', 'V'),
    ('SOL-DEMO-002', 1, 'documento-identidad-sol-demo-002.pdf', '2026-04-23 09:00:00', 'C', 'Documento conforme.', 'V'),
    ('SOL-DEMO-002', 2, 'ficha-ruc-sol-demo-002.pdf', '2026-04-23 09:15:00', 'P', 'Pendiente de revision.', 'R'),
    ('SOL-DEMO-003', 5, 'sustento-economico-sol-demo-003.pdf', '2026-05-22 09:00:00', 'O', 'Debe adjuntar estados actualizados.', 'O');

INSERT INTO documento_solicitud (
    id_tipo_documento_requerido,
    nombre_archivo,
    ruta_archivo,
    fecha_entrega,
    resultado_revision,
    observaciones,
    estado,
    id_solicitud
)
SELECT
    d.id_tipo_documento_requerido,
    d.nombre_archivo,
    CONCAT('/demo/solicitudes/', d.codigo_solicitud, '/', d.nombre_archivo),
    d.fecha_entrega,
    d.resultado_revision,
    d.observaciones,
    d.estado,
    s.id_solicitud
FROM seed_documento_demo d
INNER JOIN solicitud_alquiler s ON s.codigo_solicitud = d.codigo_solicitud
WHERE NOT EXISTS (
    SELECT 1
    FROM documento_solicitud dx
    WHERE dx.id_solicitud = s.id_solicitud
      AND dx.nombre_archivo = d.nombre_archivo
);

DROP TEMPORARY TABLE IF EXISTS seed_evaluacion_demo;

CREATE TEMPORARY TABLE seed_evaluacion_demo (
    codigo_solicitud VARCHAR(20) NOT NULL,
    codigo_broker VARCHAR(20) NOT NULL,
    fecha_evaluacion DATETIME NOT NULL,
    resultado CHAR(1) NOT NULL,
    tipo_evaluacion CHAR(1) NOT NULL,
    observaciones VARCHAR(500) NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_evaluacion_demo VALUES
    ('SOL-DEMO-001', 'BRK-001', '2026-03-10 16:00:00', 'A', 'F', 'Evaluacion final aprobada.'),
    ('SOL-DEMO-002', 'BRK-001', '2026-04-24 10:00:00', 'O', 'O', 'Falta vigencia de poder actualizada.'),
    ('SOL-DEMO-003', 'BRK-002', '2026-05-22 10:00:00', 'O', 'O', 'Sustento economico observado.');

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
FROM seed_evaluacion_demo e
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

SET @id_opo_demo_005 = (SELECT id_oportunidad FROM oportunidad_comercial WHERE codigo_oportunidad = 'OPO-DEMO-005' LIMIT 1);
SET @id_opo_demo_008 = (SELECT id_oportunidad FROM oportunidad_comercial WHERE codigo_oportunidad = 'OPO-DEMO-008' LIMIT 1);
SET @id_age_demo_001 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-001' LIMIT 1);
SET @id_age_demo_002 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-002' LIMIT 1);
SET @id_interaccion_opo_005 = (
    SELECT i.id_interaccion
    FROM interaccion_comercial i
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = i.id_oportunidad
    WHERE o.codigo_oportunidad = 'OPO-DEMO-005'
      AND i.fecha_hora = '2026-05-18 09:40:00'
    LIMIT 1
);
SET @id_interaccion_opo_008 = (
    SELECT i.id_interaccion
    FROM interaccion_comercial i
    INNER JOIN oportunidad_comercial o ON o.id_oportunidad = i.id_oportunidad
    WHERE o.codigo_oportunidad = 'OPO-DEMO-008'
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
    @id_age_demo_002,
    @id_opo_demo_005,
    @id_interaccion_opo_005,
    NULL,
    NULL
WHERE @id_opo_demo_005 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM motivo_no_continuidad
      WHERE id_oportunidad = @id_opo_demo_005
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
    @id_age_demo_001,
    @id_opo_demo_008,
    @id_interaccion_opo_008,
    NULL,
    NULL
WHERE @id_opo_demo_008 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM motivo_no_continuidad
      WHERE id_oportunidad = @id_opo_demo_008
  );

SET @id_opo_demo_002 = (SELECT id_oportunidad FROM oportunidad_comercial WHERE codigo_oportunidad = 'OPO-DEMO-002' LIMIT 1);
SET @id_sol_demo_001 = (SELECT id_solicitud FROM solicitud_alquiler WHERE codigo_solicitud = 'SOL-DEMO-001' LIMIT 1);

INSERT INTO contrato_alquiler (
    id_oportunidad,
    id_solicitud,
    renta_mensual,
    moneda,
    plazo_contrato_meses,
    fecha_inicio_contrato,
    fecha_fin_contrato,
    meses_garantia,
    monto_garantia,
    meses_adelanto,
    cuota_mantenimiento,
    tipo_reajuste,
    porcentaje_reajuste,
    forma_pago,
    fecha_cierre,
    comision_generada,
    estado_contrato,
    incidencias
)
SELECT
    @id_opo_demo_002,
    @id_sol_demo_001,
    9000.00,
    'PEN',
    24,
    '2026-04-01',
    '2028-03-31',
    2,
    18000.00,
    1,
    480.00,
    'ANUAL_FIJO',
    3.00,
    'TRANSFERENCIA',
    '2026-03-20',
    9000.00,
    'VIGENTE',
    NULL
WHERE @id_opo_demo_002 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM contrato_alquiler
      WHERE id_oportunidad = @id_opo_demo_002
  );

SET @id_contrato_demo = (
    SELECT id_contrato_alquiler
    FROM contrato_alquiler
    WHERE id_oportunidad = @id_opo_demo_002
    LIMIT 1
);

INSERT INTO comision_liquidacion (
    id_contrato_alquiler,
    monto,
    moneda,
    monto_agente,
    monto_empresa,
    fecha_cobro,
    estado
)
SELECT
    @id_contrato_demo,
    9000.00,
    'PEN',
    3600.00,
    5400.00,
    '2026-03-25',
    'COBRADA'
WHERE @id_contrato_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM comision_liquidacion
      WHERE id_contrato_alquiler = @id_contrato_demo
  );

-- =========================================================
-- Reportes, tareas, alertas e historial
-- =========================================================

SET @id_cap_demo_001 = (SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-DEMO-001' LIMIT 1);
SET @id_cap_demo_006 = (SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-DEMO-006' LIMIT 1);
SET @id_age_demo_003 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-003' LIMIT 1);
SET @id_age_demo_004 = (SELECT id_agente FROM agente_inmobiliario WHERE codigo_agente = 'AGE-004' LIMIT 1);

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
    @id_cap_demo_001,
    @id_age_demo_001,
    '2026-02-28',
    '2026-02-01',
    '2026-02-28',
    8,
    2,
    'Distribucion interior y estacionamiento.',
    'Mantener precio y mejorar fotografias.',
    'E'
WHERE @id_cap_demo_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM reporte_propietario
      WHERE id_captacion = @id_cap_demo_001
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
    @id_cap_demo_006,
    @id_age_demo_004,
    '2026-05-31',
    '2026-05-01',
    '2026-05-31',
    11,
    3,
    'Accesibilidad y horario de carga.',
    'Preparar video corto para redes.',
    'W'
WHERE @id_cap_demo_006 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM reporte_propietario
      WHERE id_captacion = @id_cap_demo_006
        AND fecha_reporte = '2026-05-31'
  );

DROP TEMPORARY TABLE IF EXISTS seed_tarea_demo;

CREATE TEMPORARY TABLE seed_tarea_demo (
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

INSERT INTO seed_tarea_demo VALUES
    ('SEGUIMIENTO', 'OPORTUNIDAD', 'OPO-DEMO-001', 'AGE-001', 'Confirmar decision del cliente despues de la visita.', '2026-06-16 09:00:00', '2026-06-16 08:30:00', 'PENDIENTE', 'ALTA'),
    ('ENVIAR_REVISION', 'CAPTACION', 'CAP-DEMO-004', 'AGE-003', 'Enviar captacion pendiente al broker.', '2026-06-17 10:00:00', '2026-06-17 09:30:00', 'PENDIENTE', 'MEDIA'),
    ('SUBIR_DOCUMENTOS', 'SOLICITUD_ALQUILER', 'SOL-DEMO-003', 'AGE-004', 'Subir sustento economico corregido.', '2026-06-18 11:00:00', '2026-06-18 10:30:00', 'EN_PROCESO', 'ALTA'),
    ('REPORTE_PROPIETARIO', 'CAPTACION', 'CAP-DEMO-006', 'AGE-004', 'Enviar reporte semanal al propietario.', '2026-06-19 16:00:00', '2026-06-19 15:30:00', 'PENDIENTE', 'MEDIA');

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
FROM seed_tarea_demo t
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

DROP TEMPORARY TABLE IF EXISTS seed_alerta_demo;

CREATE TEMPORARY TABLE seed_alerta_demo (
    tipo VARCHAR(30) NOT NULL,
    severidad VARCHAR(10) NOT NULL,
    entidad_tipo VARCHAR(30) NOT NULL,
    codigo_entidad VARCHAR(20) NOT NULL,
    codigo_agente VARCHAR(20) NOT NULL,
    mensaje VARCHAR(300) NOT NULL,
    estado VARCHAR(15) NOT NULL,
    fecha_generacion DATETIME NOT NULL
) ENGINE=MEMORY;

INSERT INTO seed_alerta_demo VALUES
    ('SIN_AVANCE', 'MEDIA', 'OPORTUNIDAD', 'OPO-DEMO-001', 'AGE-001', 'La oportunidad de Mercado Uno requiere seguimiento.', 'ACTIVA', '2026-06-13 09:00:00'),
    ('CAPTACION_VENCIDA', 'ALTA', 'CAPTACION', 'CAP-DEMO-009', 'AGE-001', 'La captacion CAP-DEMO-009 esta vencida.', 'ACTIVA', '2026-04-01 09:00:00'),
    ('OFERTA_POR_VENCER', 'MEDIA', 'SOLICITUD_ALQUILER', 'SOL-DEMO-003', 'AGE-004', 'La oferta SOL-DEMO-003 vence pronto.', 'ACTIVA', '2026-05-27 09:00:00'),
    ('VISITA_PROXIMA', 'INFO', 'VISITA', 'OPO-DEMO-004', 'AGE-003', 'Visita pendiente con Cafeteria Barranco.', 'ACTIVA', '2026-04-20 09:00:00');

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
FROM seed_alerta_demo al
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
    @id_contrato_demo,
    'FIRMADO',
    'VIGENTE',
    @id_usuario_age_001,
    '2026-04-01 08:00:00',
    'Inicio de vigencia del contrato demo.'
WHERE @id_contrato_demo IS NOT NULL
  AND @id_usuario_age_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM historial_estado
      WHERE entidad_tipo = 'CONTRATO_ALQUILER'
        AND entidad_id = @id_contrato_demo
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
    @id_cap_demo_003,
    'AGE-001',
    'AGE-002',
    @id_usuario_age_001,
    '2026-04-12 10:30:00',
    'Captacion reasignada a Javier Ruiz.'
WHERE @id_cap_demo_003 IS NOT NULL
  AND @id_usuario_age_001 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM historial_estado
      WHERE entidad_tipo = 'CAPTACION'
        AND entidad_id = @id_cap_demo_003
        AND fecha_evento = '2026-04-12 10:30:00'
  );

DROP TEMPORARY TABLE IF EXISTS seed_persona_demo;
DROP TEMPORARY TABLE IF EXISTS seed_local_demo;
DROP TEMPORARY TABLE IF EXISTS seed_precio_demo;
DROP TEMPORARY TABLE IF EXISTS seed_publicacion_demo;
DROP TEMPORARY TABLE IF EXISTS seed_captacion_demo;
DROP TEMPORARY TABLE IF EXISTS seed_prospeccion_demo;
DROP TEMPORARY TABLE IF EXISTS seed_requerimiento_demo;
DROP TEMPORARY TABLE IF EXISTS seed_oportunidad_demo;
DROP TEMPORARY TABLE IF EXISTS seed_interaccion_demo;
DROP TEMPORARY TABLE IF EXISTS seed_visita_demo;
DROP TEMPORARY TABLE IF EXISTS seed_solicitud_demo;
DROP TEMPORARY TABLE IF EXISTS seed_documento_demo;
DROP TEMPORARY TABLE IF EXISTS seed_evaluacion_demo;
DROP TEMPORARY TABLE IF EXISTS seed_tarea_demo;
DROP TEMPORARY TABLE IF EXISTS seed_alerta_demo;

COMMIT;
