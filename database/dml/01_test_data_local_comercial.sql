USE controllocal;

-- Datos de prueba minimos para validar manualmente la entidad LocalComercial.
-- El script es idempotente respecto a los identificadores y codigos usados aqui.

INSERT INTO propietario (
    id_propietario,
    tipo_persona,
    tipo_documento,
    numero_documento,
    nombres_o_razon_social,
    telefono,
    correo,
    estado
)
SELECT
    9001,
    'NATURAL',
    'DNI',
    '70000001',
    'Carlos Alberto Mendoza Rojas',
    '987654321',
    'carlos.mendoza.test@controllocal.pe',
    'ACTIVO'
WHERE NOT EXISTS (
    SELECT 1
    FROM propietario
    WHERE id_propietario = 9001
       OR numero_documento = '70000001'
);

INSERT INTO local_comercial (
    codigo_local,
    direccion,
    distrito,
    metraje,
    precio_referencial,
    rubro_permitido,
    descripcion,
    estado,
    id_propietario
)
SELECT
    'LCTEST0001',
    'Av. La Marina 1532, tienda 101',
    'San Miguel',
    78.50,
    6800.00,
    'Minimarket y tienda de conveniencia',
    'Local comercial de prueba con frente a avenida y alto flujo peatonal.',
    'DISPONIBLE',
    9001
WHERE NOT EXISTS (
    SELECT 1
    FROM local_comercial
    WHERE codigo_local = 'LCTEST0001'
);

INSERT INTO local_comercial (
    codigo_local,
    direccion,
    distrito,
    metraje,
    precio_referencial,
    rubro_permitido,
    descripcion,
    estado,
    id_propietario
)
SELECT
    'LCTEST0002',
    'Jr. Junin 425, segundo nivel',
    'Cercado de Lima',
    120.00,
    9500.00,
    'Boutique, accesorios y showroom',
    'Local de prueba con dos ambientes y acceso independiente.',
    'NO_DISPONIBLE',
    9001
WHERE NOT EXISTS (
    SELECT 1
    FROM local_comercial
    WHERE codigo_local = 'LCTEST0002'
);



--reasignación_captacion, motivo_no_continuidad y interaccion_comercial

INSERT INTO interaccion_comercial (
    fecha_hora,
    canal_contacto,
    observaciones,
    resultado,
    id_cliente,
    id_captacion,
    id_agente
)
SELECT
    NOW(),
    'LLAMADA',
    'Cliente solicita información del local',
    'INTERESADO',
    c.id_cliente,
    cap.id_captacion,
    a.id_agente
FROM cliente_interesado c
JOIN captacion cap ON cap.id_local = (
    SELECT id_local FROM local_comercial WHERE codigo_local = 'LCTEST0001'
)
JOIN agente_inmobiliario a ON a.id_agente = cap.id_agente
WHERE c.numero_documento = '70000002'
AND NOT EXISTS (
    SELECT 1 FROM interaccion_comercial i
    WHERE i.id_cliente = c.id_cliente
      AND i.observaciones = 'Cliente solicita información del local'
);


-- NO CONTINUIDAD (por interacción)
INSERT INTO motivo_no_continuidad (
    fecha_hora,
    razon_principal,
    observaciones,
    id_agente,
    id_interaccion
)
SELECT
    NOW(),
    'Precio elevado',
    'Cliente considera el precio fuera de presupuesto',
    i.id_agente,
    i.id_interaccion
FROM interaccion_comercial i
WHERE i.resultado = 'NO_INTERESADO'
AND NOT EXISTS (
    SELECT 1 FROM motivo_no_continuidad m
    WHERE m.id_interaccion = i.id_interaccion
);

INSERT INTO reasignacion_captacion (
    fecha_cambio,
    motivo,
    id_captacion,
    id_agente_anterior,
    id_agente_nuevo,
    id_broker
)
SELECT
    NOW(),
    'Redistribución de carga de trabajo',
    cap.id_captacion,
    cap.id_agente,
    (
        SELECT a2.id_agente
        FROM agente_inmobiliario a2
        WHERE a2.id_agente <> cap.id_agente
        LIMIT 1
    ),
    (
        SELECT b.id_broker
        FROM broker b
        LIMIT 1
    )
FROM captacion cap
LIMIT 1;
