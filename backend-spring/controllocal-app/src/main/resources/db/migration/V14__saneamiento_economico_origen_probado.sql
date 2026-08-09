-- Saneamiento separado: SOLO filas cuyo origen e intencion estan probados
-- por migrations/seeds o por identificadores literales de los scripts E2E.
-- No hay conversion por rango, cercania al alquiler ni otra heuristica.

-- V4 creo estos dos locales y, en el mismo seed, sus hitos E/publicacion en PEN.
UPDATE propiedad p
   SET moneda_referencial = 'PEN'
 WHERE p.codigo IN ('LOC-0001', 'LOC-0002')
   AND p.moneda_referencial IS NULL
   AND EXISTS (
       SELECT 1
         FROM precio_propiedad pp
        WHERE pp.id_propiedad = p.id_propiedad
          AND pp.hito = 'E'
          AND pp.moneda = 'PEN'
          AND pp.monto = p.precio_referencial
   );

-- V8 creo la solicitud demo desde OP-0001/CAP-0001/LOC-0001, cuya renta
-- referencial y primer hito son PEN por el seed anterior.
UPDATE solicitud_alquiler s
   SET moneda = 'PEN'
  FROM oportunidad_comercial op,
       captacion c,
       propiedad p
 WHERE s.id_oportunidad = op.id_oportunidad
   AND op.id_captacion = c.id_captacion
   AND c.id_propiedad = p.id_propiedad
   AND s.codigo_solicitud = 'SOL-260715103000'
   AND op.codigo_oportunidad = 'OP-0001'
   AND c.codigo_captacion = 'CAP-0001'
   AND p.codigo = 'LOC-0001'
   AND s.monto_propuesto = 9000.00
   AND s.moneda IS NULL;

-- E2: firma completa del script (codigo, valor anterior y observacion).
UPDATE captacion
   SET comision_pactada = 100.00
 WHERE codigo_captacion ~ '^CAP-E2-[0-9]{6}$'
   AND comision_pactada = 3000.00
   AND observaciones ~ '^Fixture reportes E2 [0-9]{6}$';

-- E3: firma completa del script de ficha comercial.
UPDATE captacion
   SET comision_pactada = 100.00
 WHERE codigo_captacion ~ '^CAP-E3-[0-9]{6}$'
   AND comision_pactada = 3600.00
   AND observaciones ~ '^Fixture ficha E3 [0-9]{6}$';

-- F3 y V6 usaban captacion generada, por eso se prueba el codigo del local
-- que el propio script construye y el valor literal equivocado.
UPDATE captacion c
   SET comision_pactada = 100.00
  FROM propiedad p
 WHERE c.id_propiedad = p.id_propiedad
   AND c.comision_pactada = 5500.00
   AND (p.codigo ~ '^LOC-F3[0-9]{6}$' OR p.codigo ~ '^LOC-V6[0-9]+$');

-- E4 limpia siempre su fixture, pero se incluye la firma por si una corrida
-- antigua fue interrumpida. No toca ninguna captacion sin esa observacion.
UPDATE captacion
   SET comision_pactada = 100.00
 WHERE comision_pactada IN (2400.00, 3900.00)
   AND observaciones ~ '^Fixture (pendiente|activo) E4 [0-9]{6}$';

-- Un V6 persistido declara PEN de forma explicita en el hito O del mismo
-- local; esa evidencia permite completar la moneda referencial, nada mas.
UPDATE propiedad p
   SET moneda_referencial = 'PEN'
 WHERE p.codigo ~ '^LOC-V6[0-9]+$'
   AND p.moneda_referencial IS NULL
   AND EXISTS (
       SELECT 1 FROM precio_propiedad pp
        WHERE pp.id_propiedad = p.id_propiedad
          AND pp.hito = 'O'
          AND pp.moneda = 'PEN'
   );
