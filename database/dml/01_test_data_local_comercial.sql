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
