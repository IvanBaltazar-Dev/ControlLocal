-- =========================================================
-- Catalogos obligatorios de ControlLocal
-- Idempotente: puede ejecutarse nuevamente sin duplicar filas.
-- =========================================================

USE controllocal;

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
