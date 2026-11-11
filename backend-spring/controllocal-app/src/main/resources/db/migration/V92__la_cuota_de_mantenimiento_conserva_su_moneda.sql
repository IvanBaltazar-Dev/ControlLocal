-- =====================================================================
-- V92 - La cuota de mantenimiento es un IMPORTE con moneda propia
-- =====================================================================
--
-- D-E4-3 (vigente 2026-09-06) cierra la conversion que V78 dejo pendiente:
-- `cuota_mantenimiento` deja de ser un DECIMAL generico y pasa a IMPORTE.
-- Cada monto lleva su propia moneda; no existe una moneda global de la
-- propiedad, no se elige PEN/USD por defecto y no se hace conversion FX.
--
-- Los valores historicos ya escritos se conservan exactamente. Las filas sin
-- moneda quedan con `valor_moneda = NULL`: el monto sigue visible y la moneda
-- se declara FALTANTE. No se rellena una moneda retrospectiva que la fuente
-- historica no contiene. Las altas y ediciones nuevas pasan desde aqui por la
-- guarda IMPORTE de V73 y necesitan monto + PEN/USD.
--
-- La clave es del sistema y V55 protege su tipo_dato. El cambio de contrato es
-- deliberado y esta autorizado por la decision vigente, asi que el trigger se
-- deshabilita solo durante este UPDATE dentro de la transaccion de Flyway y se
-- vuelve a habilitar inmediatamente. La comprobacion posterior demuestra que
-- el unico cambio de datos fue el tipo/unidad del catalogo.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Foto de todos los valores historicos de la clave.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE cuota_mantenimiento_antes ON COMMIT DROP AS
SELECT id_atributo_propiedad,
       organizacion_id,
       id_propiedad,
       clave,
       valor_texto,
       valor_numero,
       valor_booleano,
       valor_fecha,
       valor_moneda
  FROM atributo_propiedad
 WHERE clave = 'cuota_mantenimiento';

DO $$
DECLARE
    filas_catalogo INTEGER;
    tipo_anterior  TEXT;
BEGIN
    SELECT count(*), min(tipo_dato)
      INTO filas_catalogo, tipo_anterior
      FROM catalogo_atributo
     WHERE organizacion_id IS NULL
       AND clave = 'cuota_mantenimiento'
       AND del_sistema;

    IF filas_catalogo <> 1 THEN
        RAISE EXCEPTION
            'Se esperaba una sola cuota_mantenimiento del sistema y hay %',
            filas_catalogo;
    END IF;
    IF tipo_anterior <> 'DECIMAL' THEN
        RAISE EXCEPTION
            'La cuota_mantenimiento esperaba tipo DECIMAL antes de V92 y tiene %',
            tipo_anterior;
    END IF;
END $$;

-- V55 protege el contrato del catalogo. Este es el unico UPDATE autorizado a
-- cruzar esa guarda: no cambia la clave, su aplicabilidad ni los valores.
ALTER TABLE catalogo_atributo
    DISABLE TRIGGER tg_catalogo_sistema_inmutable;

UPDATE catalogo_atributo
   SET tipo_dato = 'IMPORTE',
       unidad = NULL
 WHERE organizacion_id IS NULL
   AND clave = 'cuota_mantenimiento'
   AND del_sistema;

-- V86 difiere la comprobacion de aplicabilidad. Hay que resolver ese evento
-- antes del ALTER que reactiva la guarda; no se omite la comprobacion.
SET CONSTRAINTS tg_aplica_todos_respaldado IMMEDIATE;

ALTER TABLE catalogo_atributo
    ENABLE TRIGGER tg_catalogo_sistema_inmutable;

SET CONSTRAINTS tg_aplica_todos_respaldado DEFERRED;

-- ---------------------------------------------------------------------
-- 1. Guardas de conversion y conservacion.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    filas_catalogo INTEGER;
    tipo_actual    TEXT;
    unidad_actual  TEXT;
    diferencias    BIGINT;
BEGIN
    SELECT count(*), min(tipo_dato), min(unidad)
      INTO filas_catalogo, tipo_actual, unidad_actual
      FROM catalogo_atributo
     WHERE organizacion_id IS NULL
       AND clave = 'cuota_mantenimiento'
       AND del_sistema;

    IF filas_catalogo <> 1 OR tipo_actual <> 'IMPORTE' OR unidad_actual IS NOT NULL THEN
        RAISE EXCEPTION
            'V92 no dejo la cuota_mantenimiento como IMPORTE sin unidad: filas %, tipo %, unidad %',
            filas_catalogo, tipo_actual, unidad_actual;
    END IF;

    -- FULL JOIN por identidad: detecta filas creadas, borradas o cuyo monto,
    -- moneda, propiedad o tenant haya cambiado. IS DISTINCT FROM conserva los
    -- NULL como valores reales y evita convertirlos en ceros o en PEN/USD.
    SELECT count(*)
      INTO diferencias
      FROM cuota_mantenimiento_antes antes
      FULL JOIN (SELECT * FROM atributo_propiedad WHERE clave = 'cuota_mantenimiento') despues
        ON despues.id_atributo_propiedad = antes.id_atributo_propiedad
     WHERE despues.id_atributo_propiedad IS NULL
        OR antes.id_atributo_propiedad IS NULL
        OR antes.organizacion_id IS DISTINCT FROM despues.organizacion_id
        OR antes.id_propiedad IS DISTINCT FROM despues.id_propiedad
        OR antes.clave IS DISTINCT FROM despues.clave
        OR antes.valor_texto IS DISTINCT FROM despues.valor_texto
        OR antes.valor_numero IS DISTINCT FROM despues.valor_numero
        OR antes.valor_booleano IS DISTINCT FROM despues.valor_booleano
        OR antes.valor_fecha IS DISTINCT FROM despues.valor_fecha
        OR antes.valor_moneda IS DISTINCT FROM despues.valor_moneda;

    IF diferencias <> 0 THEN
        RAISE EXCEPTION
            'V92 cambio % valores historicos de cuota_mantenimiento', diferencias;
    END IF;
END $$;
