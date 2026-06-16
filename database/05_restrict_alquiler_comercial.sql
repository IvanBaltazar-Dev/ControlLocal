USE controllocal;

-- Normaliza instalaciones existentes al alcance actual: alquiler comercial.
UPDATE captacion
SET motivo_operacion = 'A'
WHERE motivo_operacion IS NULL OR motivo_operacion <> 'A';

-- Conserva documentos ya cargados: primero los reasigna al tipo equivalente
-- de alquiler y, si no existe equivalencia, al tipo generico "Otro".
UPDATE documento_solicitud d
INNER JOIN tipo_documento_requerido origen
    ON origen.id_tipo_documento_requerido = d.id_tipo_documento_requerido
   AND origen.tipo_operacion <> 'A'
INNER JOIN tipo_documento_requerido destino
    ON destino.tipo_operacion = 'A'
   AND destino.tipo_documento = origen.tipo_documento
SET d.id_tipo_documento_requerido = destino.id_tipo_documento_requerido;

UPDATE documento_solicitud d
INNER JOIN tipo_documento_requerido origen
    ON origen.id_tipo_documento_requerido = d.id_tipo_documento_requerido
   AND origen.tipo_operacion <> 'A'
INNER JOIN tipo_documento_requerido destino
    ON destino.tipo_operacion = 'A'
   AND destino.tipo_documento = 'Otro'
SET d.id_tipo_documento_requerido = destino.id_tipo_documento_requerido;

DELETE FROM tipo_documento_requerido
WHERE tipo_operacion <> 'A';

ALTER TABLE captacion
    DROP CHECK ck_captacion_motivo_operacion,
    MODIFY motivo_operacion CHAR(1) NOT NULL DEFAULT 'A',
    ADD CONSTRAINT ck_captacion_motivo_operacion CHECK (motivo_operacion = 'A');

ALTER TABLE tipo_documento_requerido
    DROP CHECK ck_tipo_documento_operacion,
    ADD CONSTRAINT ck_tipo_documento_operacion CHECK (tipo_operacion = 'A');
