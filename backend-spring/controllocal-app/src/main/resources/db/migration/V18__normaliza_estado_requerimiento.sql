-- Requerimiento era el ultimo agregado con nombres completos en la columna
-- estado. Se migra por separado para no alterar el checksum de V17.
ALTER TABLE requerimiento_cliente DROP CONSTRAINT ck_requerimiento_estado;
ALTER TABLE requerimiento_cliente
    ALTER COLUMN estado TYPE VARCHAR(1) USING CASE estado
        WHEN 'ACTIVO' THEN 'A'
        WHEN 'PAUSADO' THEN 'P'
        WHEN 'CERRADO' THEN 'C'
        ELSE estado
    END,
    ADD CONSTRAINT ck_requerimiento_estado CHECK (estado IN ('A', 'P', 'C'));
