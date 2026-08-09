-- V21 - El periodo del encargo deja de ser opcional.
--
-- Que estaba mal. Las bandejas mostraban CAP-0001 con el periodo en blanco.
-- El API nunca acepto ese hueco —CaptacionServiceImpl.validarEncargo() corta el
-- alta y la edicion con "El inicio y fin del encargo son obligatorios." (400) y
-- el formulario Angular marca ambos campos como obligatorios—, pero si lo
-- aceptaban dos caminos que no pasan por ahi:
--
--   1. La semilla V5, que sembro CAP-0001 sin fechas.
--   2. POST /prospecciones/{id}/captar, calcado de la v1
--      (ProspeccionBusinessLogicImpl.captar), que creaba el borrador PENDIENTE
--      sin periodo. El SPA ya no lo usa —"Crear captacion" abre el formulario
--      completo—, pero el endpoint sigue publicado.
--
-- Decision de equipo (2026-08-01): el periodo del encargo es obligatorio SIEMPRE,
-- no solo para activar. `captar` pasa a completarlo con el defecto de la casa
-- (fecha de captacion + 6 meses, el mismo que propone el formulario) y el esquema
-- lo blinda con NOT NULL, para que ninguna semilla ni fixture pueda volver a
-- saltarselo. Es una divergencia de DATOS con la v1, no de contrato: ni el
-- request, ni la respuesta, ni los codigos de estado cambian.
--
-- Hasta hoy la unica invariante era `ck_captacion_activa_completa`, que exige el
-- periodo para ACTIVAR (estado 'A'). Sigue vigente: no la reemplaza este NOT
-- NULL, porque ademas cubre exclusividad y condicion economica.
--
-- El relleno usa la fecha de captacion como inicio y seis meses de plazo. Los
-- tres UPDATE cubren tambien las filas a medias (una fecha si y otra no), que
-- ninguna via conocida produce pero que el NOT NULL rechazaria igual; el primero
-- respeta `ck_captacion_encargo` (fin > inicio) cuando ya hay un fin cargado.
--
-- No se sella `fecha_actualizacion`: nadie edito estas captaciones, las completa
-- la migracion. Falsear la marca de edicion enturbiaria la trazabilidad.

-- 1) Solo falta el inicio: se toma la fecha de captacion sin invadir el fin.
UPDATE captacion
   SET fecha_inicio_encargo = LEAST(fecha_captacion, fecha_fin_encargo - 1)
 WHERE fecha_inicio_encargo IS NULL
   AND fecha_fin_encargo IS NOT NULL;

-- 2) Faltan las dos: el encargo arranca el dia en que se capto.
UPDATE captacion
   SET fecha_inicio_encargo = fecha_captacion
 WHERE fecha_inicio_encargo IS NULL;

-- 3) Falta el fin: seis meses de plazo, el defecto de la casa.
UPDATE captacion
   SET fecha_fin_encargo = (fecha_inicio_encargo + INTERVAL '6 months')::date
 WHERE fecha_fin_encargo IS NULL;

ALTER TABLE captacion
    ALTER COLUMN fecha_inicio_encargo SET NOT NULL,
    ALTER COLUMN fecha_fin_encargo    SET NOT NULL;
