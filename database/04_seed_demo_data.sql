-- =========================================================
-- Datos demo operativos de ControlLocal
-- Requiere haber ejecutado 01, 02 y 03.
-- Idempotente: usa codigos estables y no duplica la muestra.
-- =========================================================

USE controllocal;

START TRANSACTION;

SET @id_agente_demo = (
    SELECT a.id_agente
    FROM agente_inmobiliario a
    WHERE a.codigo_agente = 'AGE-001'
    LIMIT 1
);

SET @id_usuario_agente_demo = (
    SELECT id_usuario
    FROM agente_inmobiliario
    WHERE codigo_agente = 'AGE-001'
    LIMIT 1
);

SET @id_broker_demo = (
    SELECT id_broker
    FROM broker
    WHERE codigo_broker = 'BRK-001'
    LIMIT 1
);

SET @id_distrito_san_miguel = (
    SELECT id_distrito FROM distrito WHERE nombre = 'San Miguel' LIMIT 1
);

SET @id_distrito_lima = (
    SELECT id_distrito FROM distrito WHERE nombre = 'Lima' LIMIT 1
);

-- Propietario de los dos inmuebles demo.
INSERT INTO persona (
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, estado,
    consentimiento_uso_dato
)
SELECT
    'N', 'D', '70000001',
    'Carlos Alberto Mendoza Rojas', '987654321',
    'carlos.mendoza.demo@controllocal.pe', 'A', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM persona
    WHERE numero_documento = '70000001'
       OR correo = 'carlos.mendoza.demo@controllocal.pe'
);

SET @id_persona_propietario = (
    SELECT id_persona
    FROM persona
    WHERE numero_documento = '70000001'
       OR correo = 'carlos.mendoza.demo@controllocal.pe'
    ORDER BY id_persona
    LIMIT 1
);

INSERT INTO propietario (id_persona)
SELECT @id_persona_propietario
WHERE @id_persona_propietario IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM propietario
      WHERE id_persona = @id_persona_propietario
  );

SET @id_propietario_demo = (
    SELECT id_propietario
    FROM propietario
    WHERE id_persona = @id_persona_propietario
    LIMIT 1
);

-- Inmueble con oportunidad activa.
INSERT INTO local_comercial (
    codigo_local, direccion, distrito, metraje, precio_referencial,
    rubro_permitido, descripcion, estado, id_propietario,
    tipo_inmueble, uso, ambientes, antiguedad_anios,
    zona_urbanizacion, geo_lat, geo_long, frente, zonificacion,
    apto_licencia_funcionamiento, carga_electrica_kw,
    numero_estacionamientos, cuota_mantenimiento, id_distrito
)
SELECT
    'LC-DEMO-001', 'Av. La Marina 1532, tienda 101', 'San Miguel',
    78.50, 6800.00, 'Comercio vecinal',
    'Local con frente a avenida y alto flujo peatonal.', 'D',
    @id_propietario_demo, 'L', 'C', 2, 8, 'Maranga',
    -12.0784250, -77.0907310, 7.50, 'CZ',
    TRUE, 20.00, 1, 350.00, @id_distrito_san_miguel
WHERE @id_propietario_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM local_comercial WHERE codigo_local = 'LC-DEMO-001'
  );

-- Inmueble con operacion cerrada y contrato vigente.
INSERT INTO local_comercial (
    codigo_local, direccion, distrito, metraje, precio_referencial,
    rubro_permitido, descripcion, estado, id_propietario,
    tipo_inmueble, uso, ambientes, antiguedad_anios,
    zona_urbanizacion, geo_lat, geo_long, frente, zonificacion,
    apto_licencia_funcionamiento, carga_electrica_kw,
    numero_estacionamientos, cuota_mantenimiento, id_distrito
)
SELECT
    'LC-DEMO-002', 'Jr. Junin 425, segundo nivel', 'Lima',
    120.00, 9500.00, 'Showroom y oficina comercial',
    'Inmueble con acceso independiente y operacion demo cerrada.', 'N',
    @id_propietario_demo, 'O', 'C', 4, 12, 'Centro Historico',
    -12.0452140, -77.0281220, 9.20, 'ZTE-1',
    TRUE, 30.00, 0, 480.00, @id_distrito_lima
WHERE @id_propietario_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM local_comercial WHERE codigo_local = 'LC-DEMO-002'
  );

SET @id_local_1 = (
    SELECT id_local FROM local_comercial WHERE codigo_local = 'LC-DEMO-001' LIMIT 1
);
SET @id_local_2 = (
    SELECT id_local FROM local_comercial WHERE codigo_local = 'LC-DEMO-002' LIMIT 1
);

INSERT INTO precio_local (id_local, hito, moneda, monto, fecha)
SELECT @id_local_1, 'P', 'PEN', 6800.00, '2026-01-15'
WHERE @id_local_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM precio_local
      WHERE id_local = @id_local_1 AND hito = 'P' AND fecha = '2026-01-15'
  );

INSERT INTO precio_local (id_local, hito, moneda, monto, fecha)
SELECT @id_local_2, 'C', 'PEN', 9000.00, '2026-03-20'
WHERE @id_local_2 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM precio_local
      WHERE id_local = @id_local_2 AND hito = 'C' AND fecha = '2026-03-20'
  );

INSERT INTO captacion (
    codigo_captacion, fecha_captacion, fecha_inicio_vigencia,
    fecha_fin_vigencia, comision_pactada, observaciones, estado,
    fecha_revision, observacion_revision, id_local, id_agente,
    id_broker_revisor, motivo_operacion, urgencia, exclusividad
)
SELECT
    'CAP-DEMO-001', '2026-01-10', '2026-01-10', '2026-12-31',
    6800.00, 'Captacion demo activa.', 'A', '2026-01-11 10:00:00',
    'Documentacion conforme.', @id_local_1, @id_agente_demo,
    @id_broker_demo, 'A', 3, TRUE
WHERE @id_local_1 IS NOT NULL
  AND @id_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM captacion WHERE codigo_captacion = 'CAP-DEMO-001'
  );

INSERT INTO captacion (
    codigo_captacion, fecha_captacion, fecha_inicio_vigencia,
    fecha_fin_vigencia, comision_pactada, observaciones, estado,
    fecha_revision, observacion_revision, id_local, id_agente,
    id_broker_revisor, motivo_operacion, urgencia, exclusividad
)
SELECT
    'CAP-DEMO-002', '2026-02-01', '2026-02-01', '2027-01-31',
    9000.00, 'Captacion asociada a contrato demo.', 'A',
    '2026-02-02 09:30:00', 'Aprobada para publicacion.',
    @id_local_2, @id_agente_demo, @id_broker_demo, 'A', 4, TRUE
WHERE @id_local_2 IS NOT NULL
  AND @id_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM captacion WHERE codigo_captacion = 'CAP-DEMO-002'
  );

SET @id_captacion_1 = (
    SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-DEMO-001' LIMIT 1
);
SET @id_captacion_2 = (
    SELECT id_captacion FROM captacion WHERE codigo_captacion = 'CAP-DEMO-002' LIMIT 1
);

INSERT INTO prospeccion (
    codigo_prospeccion, fecha_registro, estado, resultado_propuesta,
    fecha_contacto, fecha_reunion, fecha_propuesta, observaciones,
    id_local, id_agente, id_captacion
)
SELECT
    'PRO-DEMO-001', '2026-01-05 09:00:00', 'T', 'A',
    '2026-01-05', '2026-01-07', '2026-01-09',
    'Prospeccion convertida en captacion.', @id_local_1,
    @id_agente_demo, @id_captacion_1
WHERE @id_local_1 IS NOT NULL
  AND @id_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM prospeccion WHERE codigo_prospeccion = 'PRO-DEMO-001'
  );

INSERT INTO publicacion (
    id_local, canal, url_publicacion, version_anuncio,
    titulo_anuncio, renta_publicada, moneda, inversion_pauta,
    codigo_origen, fecha_publicacion, estado
)
SELECT
    @id_local_1, 'URBANIA', 'https://demo.local/publicaciones/lc-demo-001',
    1, 'Local comercial en avenida principal de San Miguel',
    6800.00, 'PEN', 180.00, 'PUB-DEMO-001',
    '2026-01-15 08:00:00', 'P'
WHERE @id_local_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM publicacion WHERE codigo_origen = 'PUB-DEMO-001'
  );

INSERT INTO publicacion (
    id_local, canal, url_publicacion, version_anuncio,
    titulo_anuncio, renta_publicada, moneda, inversion_pauta,
    codigo_origen, fecha_publicacion, fecha_baja, estado
)
SELECT
    @id_local_2, 'WEB_PROPIA', 'https://demo.local/publicaciones/lc-demo-002',
    1, 'Oficina comercial en el Centro de Lima',
    9500.00, 'PEN', 0.00, 'PUB-DEMO-002',
    '2026-02-05 08:00:00', '2026-03-20 18:00:00', 'C'
WHERE @id_local_2 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM publicacion WHERE codigo_origen = 'PUB-DEMO-002'
  );

SET @id_publicacion_1 = (
    SELECT id_publicacion FROM publicacion WHERE codigo_origen = 'PUB-DEMO-001' LIMIT 1
);
SET @id_publicacion_2 = (
    SELECT id_publicacion FROM publicacion WHERE codigo_origen = 'PUB-DEMO-002' LIMIT 1
);

-- Dos clientes: uno en seguimiento y otro con operacion cerrada.
INSERT INTO persona (
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, estado,
    consentimiento_uso_dato
)
SELECT
    'J', 'R', '20600000011', 'Mercado Uno S.A.C.',
    '946100101', 'contacto@mercadouno.demo', 'A', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM persona
    WHERE numero_documento = '20600000011'
       OR correo = 'contacto@mercadouno.demo'
);

INSERT INTO persona (
    tipo_persona, tipo_documento, numero_documento,
    nombres_o_razon_social, telefono, correo, estado,
    consentimiento_uso_dato
)
SELECT
    'J', 'R', '20600000029', 'Showroom Centro S.A.C.',
    '946100202', 'gerencia@showroomcentro.demo', 'A', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM persona
    WHERE numero_documento = '20600000029'
       OR correo = 'gerencia@showroomcentro.demo'
);

SET @id_persona_cliente_1 = (
    SELECT id_persona FROM persona WHERE numero_documento = '20600000011' LIMIT 1
);
SET @id_persona_cliente_2 = (
    SELECT id_persona FROM persona WHERE numero_documento = '20600000029' LIMIT 1
);

INSERT INTO cliente_interesado (
    id_persona, rubro_comercial,
    consentimiento_contacto, consentimiento_uso_dato
)
SELECT @id_persona_cliente_1, 'Minimarket', TRUE, TRUE
WHERE @id_persona_cliente_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM cliente_interesado WHERE id_persona = @id_persona_cliente_1
  );

INSERT INTO cliente_interesado (
    id_persona, rubro_comercial,
    consentimiento_contacto, consentimiento_uso_dato
)
SELECT @id_persona_cliente_2, 'Moda y exhibicion', TRUE, TRUE
WHERE @id_persona_cliente_2 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM cliente_interesado WHERE id_persona = @id_persona_cliente_2
  );

SET @id_cliente_1 = (
    SELECT id_cliente FROM cliente_interesado
    WHERE id_persona = @id_persona_cliente_1 LIMIT 1
);
SET @id_cliente_2 = (
    SELECT id_cliente FROM cliente_interesado
    WHERE id_persona = @id_persona_cliente_2 LIMIT 1
);

INSERT INTO requerimiento_cliente (
    id_cliente, rubro, tipo_inmueble, renta_min, renta_max,
    moneda, metraje_min, metraje_max, frente_minimo,
    estado, observaciones
)
SELECT
    @id_cliente_1, 'Minimarket', 'LOCAL_COMERCIAL',
    5000.00, 7500.00, 'PEN', 60.00, 100.00, 6.00,
    'ACTIVO', 'Busca avenida principal y licencia compatible.'
WHERE @id_cliente_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM requerimiento_cliente
      WHERE id_cliente = @id_cliente_1 AND estado = 'ACTIVO'
  );

SET @id_requerimiento_1 = (
    SELECT id_requerimiento
    FROM requerimiento_cliente
    WHERE id_cliente = @id_cliente_1
    ORDER BY id_requerimiento
    LIMIT 1
);

INSERT INTO requerimiento_distrito (id_requerimiento, id_distrito)
SELECT @id_requerimiento_1, @id_distrito_san_miguel
WHERE @id_requerimiento_1 IS NOT NULL
  AND @id_distrito_san_miguel IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM requerimiento_distrito
      WHERE id_requerimiento = @id_requerimiento_1
        AND id_distrito = @id_distrito_san_miguel
  );

INSERT INTO oportunidad_comercial (
    codigo_oportunidad, fecha_registro, estado,
    observaciones, id_cliente, id_captacion, id_agente,
    id_publicacion_origen, fuente_origen,
    codigo_origen_capturado, fecha_primera_consulta
)
SELECT
    'OPO-DEMO-001', '2026-02-10 10:15:00', 'A',
    'Cliente activo en etapa de seguimiento.', @id_cliente_1,
    @id_captacion_1, @id_agente_demo, @id_publicacion_1,
    'PORTAL', 'PUB-DEMO-001', '2026-02-10 10:15:00'
WHERE @id_cliente_1 IS NOT NULL
  AND @id_captacion_1 IS NOT NULL
  AND @id_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM oportunidad_comercial
      WHERE codigo_oportunidad = 'OPO-DEMO-001'
  );

INSERT INTO oportunidad_comercial (
    codigo_oportunidad, fecha_registro, estado,
    fecha_actualizacion_estado, observaciones,
    id_cliente, id_captacion, id_agente,
    id_publicacion_origen, fuente_origen,
    codigo_origen_capturado, fecha_primera_consulta, fecha_cierre
)
SELECT
    'OPO-DEMO-002', '2026-02-12 11:30:00', 'F',
    '2026-03-20 17:00:00', 'Operacion cerrada exitosamente.',
    @id_cliente_2, @id_captacion_2, @id_agente_demo,
    @id_publicacion_2, 'WEB_PROPIA', 'PUB-DEMO-002',
    '2026-02-12 11:30:00', '2026-03-20 17:00:00'
WHERE @id_cliente_2 IS NOT NULL
  AND @id_captacion_2 IS NOT NULL
  AND @id_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM oportunidad_comercial
      WHERE codigo_oportunidad = 'OPO-DEMO-002'
  );

SET @id_oportunidad_1 = (
    SELECT id_oportunidad FROM oportunidad_comercial
    WHERE codigo_oportunidad = 'OPO-DEMO-001' LIMIT 1
);
SET @id_oportunidad_2 = (
    SELECT id_oportunidad FROM oportunidad_comercial
    WHERE codigo_oportunidad = 'OPO-DEMO-002' LIMIT 1
);

INSERT INTO interaccion_comercial (
    fecha_hora, canal_contacto, observaciones, resultado,
    id_oportunidad, id_agente, transcripcion_nota
)
SELECT
    '2026-02-10 10:20:00', 'W',
    'Se envio ficha comercial y ubicacion.', 'I',
    @id_oportunidad_1, @id_agente_demo,
    'Cliente solicita coordinar una visita.'
WHERE @id_oportunidad_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM interaccion_comercial
      WHERE id_oportunidad = @id_oportunidad_1
        AND fecha_hora = '2026-02-10 10:20:00'
  );

INSERT INTO visita (
    fecha_visita, hora_visita, observaciones, estado, resultado,
    id_oportunidad, id_agente, nivel_interes,
    objecion_principal, opinion_precio, proxima_accion
)
SELECT
    '2026-02-14', '10:00:00',
    'Visita realizada; el cliente evalua distribucion.', 'R', 'I',
    @id_oportunidad_1, @id_agente_demo, 4, 'E', 'J', 'O'
WHERE @id_oportunidad_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM visita
      WHERE id_oportunidad = @id_oportunidad_1
        AND fecha_visita = '2026-02-14'
        AND hora_visita = '10:00:00'
  );

INSERT INTO solicitud_alquiler (
    codigo_solicitud, fecha_registro, monto_propuesto,
    plazo_tentativo, observaciones, estado,
    fecha_actualizacion_estado, fecha_vigencia_oferta,
    id_oportunidad, id_agente
)
SELECT
    'SOL-DEMO-001', '2026-03-01', 9000.00,
    '24 meses', 'Oferta aceptada para contrato demo.', 'A',
    '2026-03-10 16:00:00', '2026-03-15',
    @id_oportunidad_2, @id_agente_demo
WHERE @id_oportunidad_2 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM solicitud_alquiler
      WHERE codigo_solicitud = 'SOL-DEMO-001'
  );

SET @id_solicitud_demo = (
    SELECT id_solicitud FROM solicitud_alquiler
    WHERE codigo_solicitud = 'SOL-DEMO-001' LIMIT 1
);

INSERT INTO documento_solicitud (
    id_tipo_documento_requerido, nombre_archivo, ruta_archivo,
    fecha_entrega, resultado_revision, observaciones,
    estado, id_solicitud
)
SELECT
    1, 'documento-identidad-demo.pdf',
    '/demo/solicitudes/SOL-DEMO-001/documento-identidad.pdf',
    '2026-03-02 09:00:00', 'C', 'Documento conforme.',
    'V', @id_solicitud_demo
WHERE @id_solicitud_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM documento_solicitud
      WHERE id_solicitud = @id_solicitud_demo
        AND nombre_archivo = 'documento-identidad-demo.pdf'
  );

INSERT INTO documento_solicitud (
    id_tipo_documento_requerido, nombre_archivo, ruta_archivo,
    fecha_entrega, resultado_revision, observaciones,
    estado, id_solicitud
)
SELECT
    5, 'sustento-economico-demo.pdf',
    '/demo/solicitudes/SOL-DEMO-001/sustento-economico.pdf',
    '2026-03-02 09:05:00', 'C', 'Solvencia validada.',
    'V', @id_solicitud_demo
WHERE @id_solicitud_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM documento_solicitud
      WHERE id_solicitud = @id_solicitud_demo
        AND nombre_archivo = 'sustento-economico-demo.pdf'
  );

INSERT INTO evaluacion_solicitud (
    fecha_evaluacion, resultado, observaciones,
    responsable_evaluacion, tipo_evaluacion, id_solicitud
)
SELECT
    '2026-03-10 16:00:00', 'A', 'Evaluacion final aprobada.',
    @id_broker_demo, 'F', @id_solicitud_demo
WHERE @id_broker_demo IS NOT NULL
  AND @id_solicitud_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM evaluacion_solicitud
      WHERE id_solicitud = @id_solicitud_demo AND tipo_evaluacion = 'F'
  );

INSERT INTO contrato_alquiler (
    id_oportunidad, id_solicitud, renta_mensual, moneda,
    plazo_contrato_meses, fecha_inicio_contrato, fecha_fin_contrato,
    meses_garantia, monto_garantia, meses_adelanto,
    cuota_mantenimiento, tipo_reajuste, porcentaje_reajuste,
    forma_pago, fecha_cierre, comision_generada,
    estado_contrato, incidencias
)
SELECT
    @id_oportunidad_2, @id_solicitud_demo, 9000.00, 'PEN',
    24, '2026-04-01', '2028-03-31', 2, 18000.00, 1,
    480.00, 'ANUAL_FIJO', 3.00, 'TRANSFERENCIA',
    '2026-03-20', 9000.00, 'VIGENTE', NULL
WHERE @id_oportunidad_2 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM contrato_alquiler
      WHERE id_oportunidad = @id_oportunidad_2
  );

SET @id_contrato_demo = (
    SELECT id_contrato_alquiler FROM contrato_alquiler
    WHERE id_oportunidad = @id_oportunidad_2 LIMIT 1
);

INSERT INTO comision_liquidacion (
    id_contrato_alquiler, monto, moneda,
    monto_agente, monto_empresa, fecha_cobro, estado
)
SELECT
    @id_contrato_demo, 9000.00, 'PEN',
    3600.00, 5400.00, '2026-03-25', 'COBRADA'
WHERE @id_contrato_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM comision_liquidacion
      WHERE id_contrato_alquiler = @id_contrato_demo
  );

INSERT INTO reporte_propietario (
    id_captacion, id_agente, fecha_reporte,
    periodo_inicio, periodo_fin, consultas_reportadas,
    visitas_reportadas, objeciones_frecuentes,
    ajustes_recomendados, canal_envio
)
SELECT
    @id_captacion_1, @id_agente_demo, '2026-02-28',
    '2026-02-01', '2026-02-28', 8, 2,
    'Distribucion interior y estacionamiento.',
    'Mantener precio y mejorar fotografias.', 'E'
WHERE @id_captacion_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM reporte_propietario
      WHERE id_captacion = @id_captacion_1
        AND fecha_reporte = '2026-02-28'
  );

INSERT INTO tarea (
    tipo, entidad_tipo, entidad_id, id_agente,
    descripcion, fecha_programada, fecha_recordatorio,
    estado, prioridad
)
SELECT
    'SEGUIMIENTO', 'OPORTUNIDAD', @id_oportunidad_1, @id_agente_demo,
    'Confirmar decision del cliente despues de la visita.',
    '2026-06-16 09:00:00', '2026-06-16 08:30:00',
    'PENDIENTE', 'ALTA'
WHERE @id_oportunidad_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tarea
      WHERE entidad_tipo = 'OPORTUNIDAD'
        AND entidad_id = @id_oportunidad_1
        AND tipo = 'SEGUIMIENTO'
        AND estado = 'PENDIENTE'
  );

INSERT INTO alerta (
    tipo, severidad, entidad_tipo, entidad_id,
    id_agente, mensaje, estado, fecha_generacion
)
SELECT
    'SIN_AVANCE', 'MEDIA', 'OPORTUNIDAD', @id_oportunidad_1,
    @id_agente_demo, 'La oportunidad demo requiere seguimiento.',
    'ACTIVA', '2026-06-13 09:00:00'
WHERE @id_oportunidad_1 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM alerta
      WHERE entidad_tipo = 'OPORTUNIDAD'
        AND entidad_id = @id_oportunidad_1
        AND tipo = 'SIN_AVANCE'
        AND estado = 'ACTIVA'
  );

INSERT INTO historial_estado (
    entidad_tipo, entidad_id, estado_anterior,
    estado_nuevo, id_usuario, fecha_evento, observacion
)
SELECT
    'CONTRATO_ALQUILER', @id_contrato_demo, 'FIRMADO',
    'VIGENTE', @id_usuario_agente_demo,
    '2026-04-01 08:00:00', 'Inicio de vigencia del contrato demo.'
WHERE @id_contrato_demo IS NOT NULL
  AND @id_usuario_agente_demo IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM historial_estado
      WHERE entidad_tipo = 'CONTRATO_ALQUILER'
        AND entidad_id = @id_contrato_demo
        AND estado_nuevo = 'VIGENTE'
  );

COMMIT;
