-- La auditoria conserva los mismos codigos unitarios del agregado. El unico
-- vocabulario historico largo posible era la liquidacion previa a V17.
UPDATE historial_estado
SET estado_anterior = CASE estado_anterior
        WHEN 'PENDIENTE' THEN 'P'
        WHEN 'PARCIAL' THEN 'R'
        WHEN 'COBRADA' THEN 'C'
        WHEN 'ANULADA' THEN 'A'
        ELSE estado_anterior
    END,
    estado_nuevo = CASE estado_nuevo
        WHEN 'PENDIENTE' THEN 'P'
        WHEN 'PARCIAL' THEN 'R'
        WHEN 'COBRADA' THEN 'C'
        WHEN 'ANULADA' THEN 'A'
        ELSE estado_nuevo
    END
WHERE entidad_tipo = 'COMISION_LIQUIDACION';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM historial_estado
        WHERE (estado_anterior IS NOT NULL AND length(estado_anterior) <> 1)
           OR length(estado_nuevo) <> 1
    ) THEN
        RAISE EXCEPTION
            'Historial con estado no unitario: regularice el codigo antes de V19';
    END IF;
END $$;

ALTER TABLE historial_estado
    ALTER COLUMN estado_anterior TYPE VARCHAR(1),
    ALTER COLUMN estado_nuevo TYPE VARCHAR(1);
