-- =========================================================
-- 07) Migracion: tipos de alerta del flujo de solicitud
-- Amplia el CHECK de alerta.tipo para admitir las notificaciones
-- del flujo "reenviar a evaluacion" (al broker) y "evaluacion"
-- (al agente). Idempotente para entornos ya creados con el script 01.
-- =========================================================
USE controllocal;

ALTER TABLE alerta DROP CONSTRAINT ck_alerta_tipo;

ALTER TABLE alerta ADD CONSTRAINT ck_alerta_tipo CHECK (
    tipo IN ('SIN_RESPUESTA', 'SIN_AVANCE', 'OFERTA_POR_VENCER',
             'CONTRATO_POR_VENCER', 'VISITA_PROXIMA', 'CAPTACION_VENCIDA',
             'SOLICITUD_REENVIADA', 'SOLICITUD_EVALUADA')
);
