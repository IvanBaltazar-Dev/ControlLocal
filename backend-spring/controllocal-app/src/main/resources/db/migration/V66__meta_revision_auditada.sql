-- V66 - La meta deja de sobrescribirse en silencio
--
-- QUE FALTABA EN V65
-- ------------------
-- V65 dejo UNA fila mutable por (agente, KPI, mes). Un PUT sobrescribia el
-- valor y solo quedaba `fecha_actualizacion`: dentro de tres meses la base
-- diria que la meta SIEMPRE fue 6, y el grafico historico de cumplimiento
-- mentiria sin que nadie pudiera notarlo.
--
-- Es el mismo defecto que E0 corrigio con los precios -un campo que se
-- sobrescribe frente a una serie- y no se arregla con un `updated_at`: hace
-- falta saber DE CUANTO A CUANTO, CUANDO, QUIEN y POR QUE.
--
--     Meta inicial 8 -> revisada a 6 el 18/08 -> motivo: agente incorporado tarde
--
-- LA POLITICA QUE ESTA TABLA MATERIALIZA
-- --------------------------------------
-- Un agente NO baja su meta porque va perdiendo: eso convertiria el indicador
-- en algo manipulable -voy al 60 %, bajo la meta y vuelvo a verde-. Pero una
-- meta inmutable tampoco sirve: hay vacaciones, altas a mitad de mes, cambios
-- de cartera, bajas por enfermedad.
--
--   AGENTE  propone un ajuste, con motivo obligatorio.
--   BROKER  fija y decide: acepta, rechaza o fija otro valor.
--
-- Las dos cosas viven en la MISMA serie append-only, porque las dos son
-- revisiones de la meta y separarlas daria dos historias que hay que cruzar
-- para reconstruir una.
--
-- POR QUE meta_comercial SIGUE EXISTIENDO
-- ---------------------------------------
-- Es el valor VIGENTE, y el tablero lo lee en cada carga. Derivarlo de la
-- serie en cada lectura serian cuatro KPI x N agentes de ventanas por consulta
-- para responder algo que cambia una vez al mes. La serie es la verdad
-- historica; la fila es la respuesta rapida, y V66 hace imposible que
-- diverjan: toda escritura de meta_comercial deja su revision.

CREATE TABLE meta_revision (
    id_revision         BIGSERIAL     PRIMARY KEY,
    organizacion_id     BIGINT        NOT NULL REFERENCES organizacion (id_organizacion),

    -- A quien y de que, repetido aqui y no solo en id_meta: una propuesta puede
    -- existir ANTES de que haya meta que revisar.
    id_rol_agente       BIGINT        NOT NULL REFERENCES persona_rol (id_persona_rol),
    kpi                 VARCHAR(1)    NOT NULL,
    anio                INT           NOT NULL,
    mes                 INT           NOT NULL,

    -- 'B' la fija el broker · 'P' la propone el agente
    origen              VARCHAR(1)    NOT NULL,
    -- 'A' aplicada · 'E' en espera de decision · 'R' rechazada
    estado              VARCHAR(1)    NOT NULL,

    -- NULL cuando es la primera vez que se fija: no habia de donde venir.
    valor_anterior      INT,
    valor_propuesto     INT           NOT NULL,

    -- Obligatorio SIEMPRE. Una revision sin motivo no se puede leer dentro de
    -- seis meses, que es justo para lo que existe esta tabla.
    motivo              VARCHAR(300)  NOT NULL,

    id_rol_autor        BIGINT        NOT NULL REFERENCES persona_rol (id_persona_rol),
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Solo cuando alguien decide sobre una propuesta.
    id_rol_decisor      BIGINT        REFERENCES persona_rol (id_persona_rol),
    fecha_decision      TIMESTAMPTZ,
    motivo_decision     VARCHAR(300),

    CONSTRAINT ck_revision_kpi    CHECK (kpi IN ('C', 'P', 'S', 'F')),
    CONSTRAINT ck_revision_mes    CHECK (mes BETWEEN 1 AND 12),
    CONSTRAINT ck_revision_anio   CHECK (anio BETWEEN 2020 AND 2100),
    CONSTRAINT ck_revision_origen CHECK (origen IN ('B', 'P')),
    CONSTRAINT ck_revision_estado CHECK (estado IN ('A', 'E', 'R')),
    CONSTRAINT ck_revision_valor  CHECK (valor_propuesto >= 0
                                         AND (valor_anterior IS NULL OR valor_anterior >= 0)),
    CONSTRAINT ck_revision_motivo CHECK (length(btrim(motivo)) >= 10),

    -- Lo que el broker fija se aplica al escribirlo: no hay una fase de espera
    -- para su propia decision. Y una propuesta del agente NACE en espera: si
    -- pudiera nacer aplicada, el agente estaria fijando su meta.
    CONSTRAINT ck_revision_broker_aplica CHECK (origen <> 'B' OR estado = 'A'),

    -- Decidir deja rastro completo o no deja ninguno: media decision registrada
    -- es peor que ninguna, porque parece que alguien reviso.
    CONSTRAINT ck_revision_decision CHECK (
        (estado = 'E' AND id_rol_decisor IS NULL AND fecha_decision IS NULL)
        OR (estado <> 'E' AND (origen = 'B'
                               OR (id_rol_decisor IS NOT NULL AND fecha_decision IS NOT NULL))))
);

-- La lectura del historial es siempre "esta meta, en orden".
CREATE INDEX ix_revision_meta
    ON meta_revision (organizacion_id, id_rol_agente, kpi, anio, mes, id_revision);

-- Y la del broker es "que tengo pendiente de decidir".
CREATE INDEX ix_revision_en_espera
    ON meta_revision (organizacion_id, estado)
    WHERE estado = 'E';

-- Una sola propuesta viva por agente, KPI y mes. Sin esto, un agente insiste
-- diez veces y el broker recibe diez avisos de lo mismo.
CREATE UNIQUE INDEX uq_revision_propuesta_viva
    ON meta_revision (organizacion_id, id_rol_agente, kpi, anio, mes)
    WHERE estado = 'E';

COMMENT ON TABLE meta_revision IS
    'Serie append-only de la meta: quien la fijo o la propuso, de cuanto a cuanto, cuando y '
    'por que. meta_comercial guarda el valor vigente; esta tabla, como se llego a el.';
COMMENT ON COLUMN meta_revision.origen IS
    'B la fija el broker (se aplica al escribirla) · P la propone el agente (nace en espera).';
COMMENT ON COLUMN meta_revision.motivo IS
    'Obligatorio y de 10 caracteres minimo: un "ok" no explica nada dentro de seis meses.';

-- ---------------------------------------------------------------------------
-- Las metas que ya existen se incorporan a la serie
-- ---------------------------------------------------------------------------
--
-- Sin esto, las filas de V65 quedarian sin origen: el tablero mostraria una meta
-- que nadie fijo. Se registran como lo que fueron -fijadas por quien consta en
-- id_rol_autor- y el motivo lo dice en vez de inventar uno.

INSERT INTO meta_revision (organizacion_id, id_rol_agente, kpi, anio, mes,
                           origen, estado, valor_anterior, valor_propuesto,
                           motivo, id_rol_autor, fecha_creacion)
SELECT m.organizacion_id, m.id_rol_agente, m.kpi, m.anio, m.mes,
       'B', 'A', NULL, m.valor,
       'Meta inicial, anterior al registro de revisiones (V66).',
       COALESCE(m.id_rol_autor, m.id_rol_agente), m.fecha_creacion
  FROM meta_comercial m;
