-- =====================================================================
-- V17: RESTRICCIONES FINALES.
-- Solo llega aqui una base sin ambiguedades pendientes. El mensaje de error
-- identifica el paso manual necesario en vez de elegir una moneda o unidad.
-- =====================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM regularizacion_dato_economico WHERE estado = 'P') THEN
        RAISE EXCEPTION
            'Regularizacion economica pendiente: resuelva la cola regularizacion_dato_economico antes de V17';
    END IF;
END $$;

ALTER TABLE propiedad
    ALTER COLUMN estado_registro SET NOT NULL,
    ALTER COLUMN disponibilidad_comercial SET NOT NULL,
    ALTER COLUMN moneda_referencial SET NOT NULL;

ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_estado_registro CHECK (estado_registro IN ('A', 'I')),
    ADD CONSTRAINT ck_propiedad_disponibilidad CHECK (disponibilidad_comercial IN ('D', 'R', 'A', 'T'));

ALTER TABLE propiedad DROP CONSTRAINT ck_propiedad_estado;
ALTER TABLE propiedad DROP COLUMN estado;
ALTER TABLE propiedad VALIDATE CONSTRAINT ck_propiedad_moneda_referencial;

ALTER TABLE captacion
    ADD CONSTRAINT ck_captacion_encargo CHECK (
        fecha_fin_encargo IS NULL OR fecha_inicio_encargo IS NULL
        OR fecha_fin_encargo > fecha_inicio_encargo),
    ADD CONSTRAINT ck_captacion_activa_completa CHECK (
        estado <> 'A' OR (
            fecha_inicio_encargo IS NOT NULL
            AND fecha_fin_encargo IS NOT NULL
            AND fecha_fin_encargo > fecha_inicio_encargo
            AND exclusividad IS NOT NULL
            AND id_condicion_economica IS NOT NULL)),
    ADD CONSTRAINT ck_captacion_cierre CHECK (
        (estado = 'C' AND fecha_cierre IS NOT NULL AND motivo_cierre IS NOT NULL)
        OR (estado <> 'C' AND motivo_cierre IS NULL));

ALTER TABLE captacion
    DROP CONSTRAINT ck_captacion_fechas,
    DROP CONSTRAINT ck_captacion_comision_porcentaje,
    DROP CONSTRAINT ck_captacion_motivo_operacion,
    ADD CONSTRAINT ck_captacion_tipo_operacion CHECK (motivo_operacion IN ('A', 'V')),
    DROP COLUMN fecha_inicio_vigencia,
    DROP COLUMN fecha_fin_vigencia,
    DROP COLUMN comision_pactada;

ALTER TABLE condicion_economica_captacion
    ADD CONSTRAINT ck_condicion_tipo_operacion CHECK (tipo_operacion IN ('A', 'V')),
    ADD CONSTRAINT ck_condicion_moneda_referencia CHECK (moneda_referencia IN ('PEN', 'USD')),
    ADD CONSTRAINT ck_condicion_tipo_comision CHECK (tipo_comision IN ('E', 'P', 'F')),
    ADD CONSTRAINT ck_condicion_base_calculo CHECK (base_calculo IN ('R', 'V', 'N')),
    ADD CONSTRAINT ck_condicion_moneda_comision CHECK (
        moneda_comision IS NULL OR moneda_comision IN ('PEN', 'USD')),
    ADD CONSTRAINT ck_condicion_igv CHECK (tratamiento_igv IN ('I', 'A', 'N')),
    ADD CONSTRAINT ck_condicion_importes CHECK (
        importe_referencia >= 0 AND valor_comision >= 0),
    ADD CONSTRAINT ck_condicion_tipo_base CHECK (
        (tipo_comision = 'E' AND base_calculo = 'R' AND moneda_comision = moneda_referencia)
        OR (tipo_comision = 'P' AND base_calculo IN ('R', 'V') AND moneda_comision = moneda_referencia)
        OR (tipo_comision = 'F' AND base_calculo = 'N' AND moneda_comision IS NOT NULL)),
    ADD CONSTRAINT ck_condicion_sin_comision CHECK (
        valor_comision <> 0 OR (motivo_sin_comision IS NOT NULL AND btrim(motivo_sin_comision) <> ''));

ALTER TABLE solicitud_alquiler
    ALTER COLUMN moneda SET NOT NULL;
ALTER TABLE solicitud_alquiler VALIDATE CONSTRAINT ck_solicitud_moneda;

ALTER TABLE contrato_alquiler
    DROP CONSTRAINT uq_contrato_oportunidad,
    DROP CONSTRAINT uq_contrato_solicitud,
    ADD CONSTRAINT ck_contrato_moneda CHECK (moneda IS NULL OR moneda IN ('PEN', 'USD')),
    ADD CONSTRAINT ck_contrato_renta CHECK (renta_contractual IS NULL OR renta_contractual > 0),
    ADD CONSTRAINT ck_contrato_vigencia CHECK (
        fecha_fin_contrato IS NULL OR fecha_inicio_contrato IS NULL
        OR fecha_fin_contrato > fecha_inicio_contrato),
    ADD CONSTRAINT ck_contrato_formalizado_completo CHECK (
        estado_contrato NOT IN ('D', 'V') OR (
            fecha_inicio_contrato IS NOT NULL
            AND fecha_fin_contrato IS NOT NULL
            AND fecha_fin_contrato > fecha_inicio_contrato
            AND renta_contractual IS NOT NULL
            AND moneda IS NOT NULL));

CREATE UNIQUE INDEX uq_contrato_raiz_oportunidad
    ON contrato_alquiler (id_oportunidad)
    WHERE id_contrato_anterior IS NULL;
CREATE UNIQUE INDEX uq_contrato_raiz_solicitud
    ON contrato_alquiler (id_solicitud)
    WHERE id_contrato_anterior IS NULL;

ALTER TABLE comision_liquidacion DROP CONSTRAINT ck_comision_estado;
ALTER TABLE comision_liquidacion
    ALTER COLUMN monto_bruto SET NOT NULL,
    ALTER COLUMN moneda TYPE VARCHAR(3),
    ALTER COLUMN estado TYPE VARCHAR(1) USING CASE estado
        WHEN 'PENDIENTE' THEN 'P' WHEN 'PARCIAL' THEN 'R'
        WHEN 'COBRADA' THEN 'C' WHEN 'ANULADA' THEN 'A' ELSE estado END;

ALTER TABLE comision_liquidacion
    DROP CONSTRAINT ck_comision_montos,
    ADD CONSTRAINT ck_comision_estado CHECK (estado IN ('P', 'R', 'C', 'A')),
    ADD CONSTRAINT ck_comision_montos CHECK (
        monto_bruto >= 0
        AND (parte_agente IS NULL OR parte_agente >= 0)
        AND (parte_empresa IS NULL OR parte_empresa >= 0)
        AND (parte_agente IS NULL OR parte_empresa IS NULL
             OR parte_agente + parte_empresa = monto_bruto));

ALTER TABLE comision_liquidacion
    DROP COLUMN monto,
    DROP COLUMN monto_agente,
    DROP COLUMN monto_empresa,
    DROP COLUMN fecha_cobro,
    DROP COLUMN forma_pago;

ALTER TABLE comision_movimiento
    ADD CONSTRAINT ck_movimiento_tipo CHECK (tipo IN ('C', 'P', 'A', 'R')),
    ADD CONSTRAINT ck_movimiento_monto CHECK (monto > 0),
    ADD CONSTRAINT ck_movimiento_moneda CHECK (moneda IN ('PEN', 'USD')),
    ADD CONSTRAINT ck_movimiento_forma_pago CHECK (
        forma_pago IS NULL OR forma_pago IN
        ('TRANSFERENCIA', 'DEPOSITO_BANCARIO', 'EFECTIVO', 'CHEQUE', 'OTRO'));

ALTER TABLE alerta
    DROP CONSTRAINT ck_alerta_estado,
    DROP CONSTRAINT ck_alerta_resolucion;
ALTER TABLE alerta
    ALTER COLUMN estado TYPE VARCHAR(1) USING CASE estado
        WHEN 'ACTIVA' THEN 'A' WHEN 'ATENDIDA' THEN 'T'
        WHEN 'DESCARTADA' THEN 'D' ELSE estado END,
    ADD CONSTRAINT ck_alerta_estado CHECK (estado IN ('A', 'T', 'D')),
    ADD CONSTRAINT ck_alerta_resolucion CHECK (
        (estado = 'A' AND fecha_resolucion IS NULL)
        OR (estado <> 'A' AND fecha_resolucion IS NOT NULL));

DROP INDEX uq_tarea_abierta_por_entidad;
ALTER TABLE tarea DROP CONSTRAINT ck_tarea_estado;
ALTER TABLE tarea DROP CONSTRAINT ck_tarea_completada;
ALTER TABLE tarea
    ALTER COLUMN estado TYPE VARCHAR(1) USING CASE estado
        WHEN 'PENDIENTE' THEN 'P' WHEN 'EN_PROCESO' THEN 'E'
        WHEN 'COMPLETADA' THEN 'C' WHEN 'VENCIDA' THEN 'V'
        WHEN 'CANCELADA' THEN 'A' ELSE estado END,
    ADD CONSTRAINT ck_tarea_estado CHECK (estado IN ('P', 'E', 'C', 'V', 'A')),
    ADD CONSTRAINT ck_tarea_completada CHECK (
        (estado = 'C' AND fecha_completada IS NOT NULL) OR estado <> 'C');

CREATE UNIQUE INDEX uq_tarea_abierta_por_entidad
    ON tarea (organizacion_id, id_rol_agente, entidad_tipo, entidad_id)
    WHERE estado IN ('P', 'E');

ALTER TABLE tarea DROP CONSTRAINT ck_tarea_tipo;
ALTER TABLE tarea ADD CONSTRAINT ck_tarea_tipo CHECK (tipo IN (
    'SEGUIMIENTO', 'LLAMADA', 'VISITA', 'RECONTACTO', 'REPORTE_PROPIETARIO',
    'SUBIR_DOCUMENTOS', 'COMPLETAR_DOCUMENTACION', 'ACTUALIZAR_INFORMACION',
    'PROPONER_OPORTUNIDAD', 'REVISION_INMUEBLE'));

ALTER TABLE regularizacion_dato_economico
    ADD CONSTRAINT ck_regularizacion_estado CHECK (estado IN ('P', 'R', 'D'));
