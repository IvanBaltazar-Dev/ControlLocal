USE controllocal;

-- Datos de prueba idempotentes para propietario y local_comercial.

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
    '70000001',
    'Carlos Alberto Mendoza Rojas',
    '987654321',
    'carlos.mendoza.test@controllocal.pe',
    'A'
WHERE NOT EXISTS (
    SELECT 1
    FROM persona
    WHERE numero_documento = '70000001'
       OR correo = 'carlos.mendoza.test@controllocal.pe'
);

SET @id_persona_propietario_test = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '70000001'
       OR correo = 'carlos.mendoza.test@controllocal.pe'
    ORDER BY id_persona
    LIMIT 1
);

INSERT INTO propietario (id_persona)
SELECT @id_persona_propietario_test
WHERE @id_persona_propietario_test IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM propietario
      WHERE id_persona = @id_persona_propietario_test
  );

SET @id_propietario_test = (
    SELECT id_propietario
    FROM propietario
    WHERE id_persona = @id_persona_propietario_test
    ORDER BY id_propietario
    LIMIT 1
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
    'D',
    @id_propietario_test
WHERE @id_propietario_test IS NOT NULL
  AND NOT EXISTS (
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
    'N',
    @id_propietario_test
WHERE @id_propietario_test IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM local_comercial
      WHERE codigo_local = 'LCTEST0002'
  );
