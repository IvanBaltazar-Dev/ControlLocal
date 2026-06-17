-- =========================================================
-- ControlLocal - garantia minima de solicitudes demo
--
-- Proposito:
--   Asegurar que las 3 solicitudes demo (SOL-DEMO-001/002/003)
--   esten cargadas en la base independientemente del estado en
--   que haya quedado 03_seed_demo_data.sql. Esto es util si el
--   seed principal se interrumpio a la mitad por algun error de
--   CHECK constraint y la transaccion completa hizo rollback.
--
-- Pre-requisitos:
--   - 01_create_schema_controllocal.sql ejecutado.
--   - 02_seed_base_data.sql ejecutado (debe existir AGE-001,
--     AGE-002, AGE-004 en agente_inmobiliario).
--   - 03_seed_demo_data.sql ejecutado al menos hasta la seccion
--     de oportunidades (debe existir OPO-DEMO-002, OPO-DEMO-003
--     y OPO-DEMO-006 en oportunidad_comercial).
--
-- Idempotente:
--   Usa ON DUPLICATE KEY UPDATE sobre codigo_solicitud, por lo
--   que se puede ejecutar varias veces sin duplicar filas.
-- =========================================================

USE controllocal;

START TRANSACTION;

INSERT INTO solicitud_alquiler (
    codigo_solicitud, fecha_registro, monto_propuesto, plazo_tentativo,
    observaciones, estado, fecha_actualizacion_estado, fecha_vigencia_oferta,
    id_oportunidad, id_agente
)
SELECT 'SOL-DEMO-001', '2026-03-01', 9000.00, '24 meses',
       'Oferta aceptada para contrato demo.', 'A',
       '2026-03-10 16:00:00', '2026-03-15',
       o.id_oportunidad, a.id_agente
FROM oportunidad_comercial o
JOIN agente_inmobiliario a ON a.codigo_agente = 'AGE-001'
WHERE o.codigo_oportunidad = 'OPO-DEMO-002'
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

INSERT INTO solicitud_alquiler (
    codigo_solicitud, fecha_registro, monto_propuesto, plazo_tentativo,
    observaciones, estado, fecha_actualizacion_estado, fecha_vigencia_oferta,
    id_oportunidad, id_agente
)
SELECT 'SOL-DEMO-002', '2026-04-22', 8000.00, '36 meses',
       'Solicitud en revision documental.', 'E',
       '2026-04-23 11:00:00', '2026-04-30',
       o.id_oportunidad, a.id_agente
FROM oportunidad_comercial o
JOIN agente_inmobiliario a ON a.codigo_agente = 'AGE-002'
WHERE o.codigo_oportunidad = 'OPO-DEMO-003'
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

INSERT INTO solicitud_alquiler (
    codigo_solicitud, fecha_registro, monto_propuesto, plazo_tentativo,
    observaciones, estado, fecha_actualizacion_estado, fecha_vigencia_oferta,
    id_oportunidad, id_agente
)
SELECT 'SOL-DEMO-003', '2026-05-21', 7400.00, '24 meses',
       'Documentos pendientes de regularizacion.', 'O',
       '2026-05-22 10:00:00', '2026-05-29',
       o.id_oportunidad, a.id_agente
FROM oportunidad_comercial o
JOIN agente_inmobiliario a ON a.codigo_agente = 'AGE-004'
WHERE o.codigo_oportunidad = 'OPO-DEMO-006'
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

COMMIT;

-- Verificacion rapida (debe devolver al menos 3 filas):
SELECT codigo_solicitud, estado, id_oportunidad, id_agente
FROM solicitud_alquiler
WHERE codigo_solicitud LIKE 'SOL-DEMO-%'
ORDER BY codigo_solicitud;
