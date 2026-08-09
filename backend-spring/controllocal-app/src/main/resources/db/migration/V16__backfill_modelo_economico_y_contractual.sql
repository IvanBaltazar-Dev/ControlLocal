-- =====================================================================
-- V16: BACKFILL controlado del modelo expandido por V15.
--
-- Fuentes de evidencia:
--  * estado legado D/N/I y contratos relacionados para disponibilidad;
--  * columnas de vigencia existentes para el encargo;
--  * semantica documentada de comision_pactada (% de una renta): se pasa a
--    equivalencia de mensualidades dividiendo exactamente entre 100;
--  * moneda declarada en hitos, publicaciones, solicitudes o contratos.
-- Las contradicciones se encolan. No hay conversion por magnitud.
-- =====================================================================

UPDATE propiedad
   SET estado_registro = CASE WHEN estado = 'I' THEN 'I' ELSE 'A' END,
       disponibilidad_comercial = CASE
           WHEN estado = 'D' THEN 'D'
           WHEN estado = 'I' THEN 'T'
           WHEN EXISTS (
               SELECT 1
                 FROM captacion c
                 JOIN oportunidad_comercial o ON o.id_captacion = c.id_captacion
                 JOIN contrato_alquiler ca ON ca.id_oportunidad = o.id_oportunidad
                WHERE c.id_propiedad = propiedad.id_propiedad
                  AND ca.estado_contrato IN ('D', 'V', 'R')
           ) THEN 'A'
           ELSE 'T'
       END
 WHERE estado_registro IS NULL OR disponibilidad_comercial IS NULL;

UPDATE captacion
   SET fecha_inicio_encargo = fecha_inicio_vigencia,
       fecha_fin_encargo = fecha_fin_vigencia
 WHERE fecha_inicio_encargo IS NULL OR fecha_fin_encargo IS NULL;

-- Una captacion cerrada por contrato tiene evidencia directa de fecha/motivo.
UPDATE captacion c
   SET fecha_cierre = ca.fecha_cierre,
       motivo_cierre = 'A',
       detalle_motivo_cierre = NULL
  FROM oportunidad_comercial o
  JOIN contrato_alquiler ca ON ca.id_oportunidad = o.id_oportunidad
 WHERE o.id_captacion = c.id_captacion
   AND c.estado = 'C'
   AND c.fecha_cierre IS NULL;

-- Cierres historicos sin evento de contrato conservan explicitamente que el
-- motivo original no fue registrado; no se inventa "alquiler".
UPDATE captacion
   SET fecha_cierre = COALESCE(fecha_fin_vigencia, fecha_actualizacion::date, fecha_captacion),
       motivo_cierre = 'O',
       detalle_motivo_cierre = 'Motivo historico no registrado'
 WHERE estado = 'C'
   AND fecha_cierre IS NULL;

-- Moneda referencial: solo se completa cuando todas las evidencias del mismo
-- inmueble coinciden. Las filas contradictorias permanecen pendientes.
WITH evidencias AS (
    SELECT p.id_propiedad, min(e.moneda) AS moneda, count(DISTINCT e.moneda) AS cantidad
      FROM propiedad p
      JOIN LATERAL (
          SELECT pp.moneda FROM precio_propiedad pp WHERE pp.id_propiedad = p.id_propiedad
          UNION ALL
          SELECT pu.moneda FROM publicacion pu WHERE pu.id_propiedad = p.id_propiedad
          UNION ALL
          SELECT s.moneda
            FROM captacion c
            JOIN oportunidad_comercial o ON o.id_captacion = c.id_captacion
            JOIN solicitud_alquiler s ON s.id_oportunidad = o.id_oportunidad
           WHERE c.id_propiedad = p.id_propiedad AND s.moneda IS NOT NULL
      ) e ON TRUE
     WHERE p.moneda_referencial IS NULL
     GROUP BY p.id_propiedad
)
UPDATE propiedad p
   SET moneda_referencial = e.moneda
  FROM evidencias e
 WHERE e.id_propiedad = p.id_propiedad
   AND e.cantidad = 1;

INSERT INTO regularizacion_dato_economico
    (organizacion_id, entidad_tipo, entidad_id, campo, valor_origen, motivo)
SELECT p.organizacion_id, 'PROPIEDAD', p.id_propiedad, 'moneda_referencial',
       p.precio_referencial::text,
       'No existe una unica moneda respaldada por hitos, publicaciones o solicitudes.'
  FROM propiedad p
 WHERE p.moneda_referencial IS NULL
ON CONFLICT DO NOTHING;

-- La solicitud hereda moneda solo de la referencia consistente de su local.
UPDATE solicitud_alquiler s
   SET moneda = p.moneda_referencial
  FROM oportunidad_comercial o
  JOIN captacion c ON c.id_captacion = o.id_captacion
  JOIN propiedad p ON p.id_propiedad = c.id_propiedad
 WHERE s.id_oportunidad = o.id_oportunidad
   AND s.moneda IS NULL
   AND p.moneda_referencial IS NOT NULL;

INSERT INTO regularizacion_dato_economico
    (organizacion_id, entidad_tipo, entidad_id, campo, valor_origen, motivo)
SELECT s.organizacion_id, 'SOLICITUD_ALQUILER', s.id_solicitud, 'moneda',
       s.monto_propuesto::text, 'La solicitud no tiene una moneda relacionada demostrable.'
  FROM solicitud_alquiler s
 WHERE s.moneda IS NULL
ON CONFLICT DO NOTHING;

-- La escala anterior estaba documentada como porcentaje sobre una renta:
-- 50/100/150/200 -> 0.50/1.00/1.50/2.00 mensualidades. No se interpreta el
-- valor como importe ni se compara con la renta.
INSERT INTO condicion_economica_captacion
    (id_condicion_economica, organizacion_id, tipo_operacion, importe_referencia,
     moneda_referencia, tipo_comision, base_calculo, valor_comision,
     moneda_comision, tratamiento_igv, motivo_sin_comision)
SELECT c.id_captacion, c.organizacion_id, 'A', p.precio_referencial,
       p.moneda_referencial, 'E', 'R', c.comision_pactada / 100.0,
       p.moneda_referencial, 'N',
       CASE WHEN c.comision_pactada = 0 THEN 'Registro historico sin comision' END
  FROM captacion c
  JOIN propiedad p ON p.id_propiedad = c.id_propiedad
 WHERE p.moneda_referencial IS NOT NULL
   AND c.comision_pactada BETWEEN 0 AND 200
ON CONFLICT (id_condicion_economica) DO NOTHING;

UPDATE captacion c
   SET id_condicion_economica = c.id_captacion
 WHERE c.id_condicion_economica IS NULL
   AND EXISTS (SELECT 1 FROM condicion_economica_captacion ce
                WHERE ce.id_condicion_economica = c.id_captacion);

SELECT setval(pg_get_serial_sequence('condicion_economica_captacion', 'id_condicion_economica'),
              GREATEST((SELECT COALESCE(max(id_condicion_economica), 1)
                          FROM condicion_economica_captacion), 1), true);

-- Valores fuera del dominio declarado quedan en cola sin dividir, redondear
-- ni reinterpretar. V17 impide cerrar la normalizacion mientras existan.
INSERT INTO regularizacion_dato_economico
    (organizacion_id, entidad_tipo, entidad_id, campo, valor_origen, motivo)
SELECT c.organizacion_id, 'CAPTACION', c.id_captacion, 'comision_pactada',
       c.comision_pactada::text,
       'Valor fuera de 0..200; requiere evidencia humana sobre unidad y base.'
  FROM captacion c
 WHERE c.comision_pactada < 0 OR c.comision_pactada > 200
ON CONFLICT DO NOTHING;

-- Snapshot contractual: proviene de la solicitud aprobada que origino el
-- contrato; el fin se deriva del plazo contractual ya persistido.
UPDATE contrato_alquiler ca
   SET fecha_inicio_contrato = s.fecha_inicio_contrato,
       fecha_fin_contrato = CASE
           WHEN s.fecha_inicio_contrato IS NOT NULL AND s.plazo_contrato_meses IS NOT NULL
           THEN s.fecha_inicio_contrato + make_interval(months => s.plazo_contrato_meses)
           ELSE NULL
       END,
       renta_contractual = s.monto_propuesto,
       moneda = s.moneda,
       fecha_efectiva_estado = ca.fecha_cierre
  FROM solicitud_alquiler s
 WHERE ca.id_solicitud = s.id_solicitud;

INSERT INTO regularizacion_dato_economico
    (organizacion_id, entidad_tipo, entidad_id, campo, valor_origen, motivo)
SELECT ca.organizacion_id, 'CONTRATO_ALQUILER', ca.id_contrato_alquiler,
       'vigencia_o_moneda', ca.fecha_cierre::text,
       'El contrato firmado/vigente no tiene inicio, fin, renta o moneda demostrable.'
  FROM contrato_alquiler ca
 WHERE ca.estado_contrato IN ('D', 'V')
   AND (ca.fecha_inicio_contrato IS NULL OR ca.fecha_fin_contrato IS NULL
        OR ca.renta_contractual IS NULL OR ca.moneda IS NULL)
ON CONFLICT DO NOTHING;

UPDATE comision_liquidacion
   SET monto_bruto = monto,
       parte_agente = monto_agente,
       parte_empresa = monto_empresa
 WHERE monto_bruto IS NULL;

-- Un cobro historico marcado como COBRADA se materializa como movimiento,
-- usando exactamente su snapshot, moneda, fecha y forma ya persistidos.
INSERT INTO comision_movimiento
    (organizacion_id, id_comision_liquidacion, tipo, monto, moneda, fecha,
     forma_pago, observacion)
SELECT cl.organizacion_id, cl.id_comision_liquidacion, 'C', cl.monto,
       cl.moneda, cl.fecha_cobro, cl.forma_pago, 'Backfill de cobro historico'
  FROM comision_liquidacion cl
 WHERE cl.estado = 'COBRADA'
   AND cl.fecha_cobro IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM comision_movimiento cm
        WHERE cm.id_comision_liquidacion = cl.id_comision_liquidacion
          AND cm.tipo = 'C'
   );
