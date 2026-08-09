-- V27 - El contrato recuerda A QUIEN se le atribuye el alquiler cerrado.
--
-- Que estaba mal. `contrato_alquiler` guardaba el vinculo (oportunidad +
-- solicitud) y el snapshot economico (fechas, renta, moneda), pero NO guardaba
-- a quien se le atribuye el cierre. El agente, la captacion, el inmueble y el
-- cliente se resolvian LEYENDO LA CADENA VIGENTE en cada consulta:
--
--     contrato -> solicitud -> agente
--     contrato -> oportunidad -> captacion -> agente / propiedad
--     contrato -> oportunidad -> cliente
--
-- Consecuencia: la historia se reescribe sola. Si un agente cambia de equipo
-- (`supervision_agente` cierra una fila y abre otra) o una captacion se
-- reasigna, el alquiler cerrado hace meses pasa a atribuirse segun la
-- estructura de HOY, y con el el alcance del BROKER —que en contratos va por
-- captacion supervisada— y los importes de comision que cuelgan de esa lectura.
-- Un cierre es un HECHO consumado: quien lo cerro no cambia porque despues
-- cambie el organigrama.
--
-- Esto NO es una decision funcional nueva ni cambia ninguna regla: la
-- atribucion que se congela es exactamente la que hoy se calcula al cerrar.
-- Tampoco cambia el contrato REST: `agenteId`, `agenteNombre`, `codigoCaptacion`
-- y los datos del inmueble siguen viajando igual, solo que leidos del snapshot
-- en vez de la cadena. La diferencia se ve el dia que alguien reasigna.
--
-- Lo que este cambio NO hace, a proposito:
--   - no reactiva el inmueble al terminar o rescindir el contrato. Eso esta
--     decidido y documentado en `docs/ai/matriz-operacion-rol.md` (finalizar y
--     rescindir dejan TAREA de revision, no reactivan), y tocarlo si seria una
--     decision funcional;
--   - no toca comision generada / cobrada / pagada, que ya estan separadas
--     desde V15 (`comision_liquidacion.monto_bruto` + `comision_movimiento`
--     C/P/A/R) y se exponen en la respuesta.

ALTER TABLE contrato_alquiler
    ADD COLUMN id_rol_agente_cierre BIGINT,
    ADD COLUMN id_rol_broker_cierre BIGINT,
    ADD COLUMN id_captacion         BIGINT,
    ADD COLUMN id_propiedad         BIGINT,
    ADD COLUMN id_rol_cliente       BIGINT;

COMMENT ON COLUMN contrato_alquiler.id_rol_agente_cierre IS
    'Agente al que se atribuye el cierre. Congelado al registrar; no sigue reasignaciones.';
COMMENT ON COLUMN contrato_alquiler.id_rol_broker_cierre IS
    'Broker que supervisaba al agente EN LA FECHA DE CIERRE. Nulo si no tenia supervisor.';

-- 1) Agente: el de la solicitud y, en su defecto, el de la oportunidad. Es la
--    misma precedencia que ya aplican la ficha y E4.
--    Se resuelve con subconsultas correlacionadas y no con un LEFT JOIN en el
--    FROM: la solicitud es opcional y el JOIN no puede referenciar la fila que
--    se actualiza dentro de su propio ON.
UPDATE contrato_alquiler c
   SET id_rol_agente_cierre = COALESCE(
        (SELECT s.id_rol_agente FROM solicitud_alquiler s
          WHERE s.id_solicitud = c.id_solicitud),
        (SELECT o.id_rol_agente FROM oportunidad_comercial o
          WHERE o.id_oportunidad = c.id_oportunidad))
 WHERE c.id_rol_agente_cierre IS NULL;

-- 2) Captacion, inmueble y cliente: por la oportunidad, que es NOT NULL.
UPDATE contrato_alquiler c
   SET id_captacion   = o.id_captacion,
       id_rol_cliente = o.id_rol_cliente
  FROM oportunidad_comercial o
 WHERE o.id_oportunidad = c.id_oportunidad
   AND c.id_captacion IS NULL;

UPDATE contrato_alquiler c
   SET id_propiedad = cap.id_propiedad
  FROM captacion cap
 WHERE cap.id_captacion = c.id_captacion
   AND c.id_propiedad IS NULL;

-- 3) Broker: el supervisor VIGENTE A LA FECHA DE CIERRE, no el de hoy. Si la
--    supervision de entonces ya se cerro, `fecha_fin` la delimita; si sigue
--    abierta, cubre igual. Sin fila que cubra esa fecha queda NULL: es
--    informacion que no existe, y no se inventa con la supervision actual.
UPDATE contrato_alquiler c
   SET id_rol_broker_cierre = sup.id_rol_broker
  FROM supervision_agente sup
 WHERE sup.id_rol_agente = c.id_rol_agente_cierre
   AND sup.organizacion_id = c.organizacion_id
   AND sup.fecha_asignacion <= c.fecha_cierre
   AND (sup.fecha_fin IS NULL OR sup.fecha_fin >= c.fecha_cierre)
   AND c.id_rol_broker_cierre IS NULL;

-- Las cuatro derivables son obligatorias: la cadena que las produce es NOT NULL
-- de punta a punta, asi que un hueco solo puede venir de una fila corrupta.
ALTER TABLE contrato_alquiler
    ALTER COLUMN id_rol_agente_cierre SET NOT NULL,
    ALTER COLUMN id_captacion         SET NOT NULL,
    ALTER COLUMN id_propiedad         SET NOT NULL,
    ALTER COLUMN id_rol_cliente       SET NOT NULL;

ALTER TABLE contrato_alquiler
    ADD CONSTRAINT fk_contrato_agente_cierre_org
        FOREIGN KEY (organizacion_id, id_rol_agente_cierre)
        REFERENCES detalle_agente (organizacion_id, id_persona_rol),
    -- `detalle_broker` no tiene clave compuesta por organizacion (V6 solo se la
    -- dio a agente); se referencia por PK, igual que `supervision_agente`.
    ADD CONSTRAINT fk_contrato_broker_cierre
        FOREIGN KEY (id_rol_broker_cierre) REFERENCES detalle_broker (id_persona_rol),
    ADD CONSTRAINT fk_contrato_captacion_org
        FOREIGN KEY (organizacion_id, id_captacion)
        REFERENCES captacion (organizacion_id, id_captacion),
    ADD CONSTRAINT fk_contrato_propiedad_org
        FOREIGN KEY (organizacion_id, id_propiedad)
        REFERENCES propiedad (organizacion_id, id_propiedad),
    ADD CONSTRAINT fk_contrato_cliente_org
        FOREIGN KEY (organizacion_id, id_rol_cliente)
        REFERENCES detalle_cliente (organizacion_id, id_persona_rol);

-- El alcance de contratos se resuelve por agente (AGENTE) o por captacion
-- (BROKER). Con la atribucion congelada los dos caminos salen de esta tabla,
-- asi que el indice cubre el WHERE completo del listado.
CREATE INDEX ix_contrato_atribucion
    ON contrato_alquiler (organizacion_id, id_rol_agente_cierre, id_contrato_alquiler DESC);
CREATE INDEX ix_contrato_captacion
    ON contrato_alquiler (organizacion_id, id_captacion, id_contrato_alquiler DESC);
