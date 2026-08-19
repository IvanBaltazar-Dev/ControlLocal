-- V65 - La meta comercial pasa a existir
--
-- QUE FALTABA
-- -----------
-- Medido el 2026-08-19: las metas no estaban en NINGUN sitio. Ni columna, ni
-- tabla, ni constante de backend, ni fixture del SPA, ni seed. Cero productores.
-- El unico lugar del repositorio donde habia metas era la maqueta
-- (docs/ai/prototipos/indicadores.html:1093), como literal.
--
-- Sin meta no hay metaPeriodo, ni metaEsperadaAHoy, ni porcentajeMeta, ni
-- faltante, ni semaforo: los cinco campos que D-E2-2 §2 exige nacen de aqui o
-- no nacen.
--
-- LAS DECISIONES QUE ESTA TABLA MATERIALIZA
-- -----------------------------------------
-- 1. Una sola fuente persistente. No hay meta en configuracion, ni en el SPA,
--    ni derivada de la produccion del mes pasado.
-- 2. Mensual y por agente. El mes es la unidad porque es la unidad del ritmo
--    (V64 y PeriodoCalendario): un periodo de calendario con inicio, fin y dias
--    transcurridos.
-- 3. LA META DEL EQUIPO NO SE GUARDA. Es la suma de las de sus agentes. Guardar
--    ademas una meta de equipo editable a mano crea dos verdades que divergen
--    en cuanto alguien edita una sola: el broker abriria "Equipo 56" y
--    encontraria agentes que suman 48. D-E2-2 §5 lo dice al reves -la meta del
--    equipo ES la suma- y aqui se hace imposible contradecirlo.
-- 4. Si falta la meta de un agente del alcance, el ritmo del equipo se declara
--    SIN_BASE por cobertura incompleta. No se compara contra una meta parcial:
--    la brecha saldria siempre a favor.
--
-- POR QUE NO SE SIEMBRA
-- ---------------------
-- La maqueta trae ocho agentes con cuatro metas cada uno. Son numeros de
-- ilustracion y sembrarlos los convertiria en el objetivo comercial real de una
-- corredora sin que nadie lo haya decidido. La tabla nace VACIA y el sistema
-- dice "sin meta" hasta que alguien la fije, que es la verdad.
--
-- EL CODIGO DEL KPI
-- -----------------
-- Unitario, como el resto del vocabulario persistido (la leccion de V40):
--   C  Propietarios contactados
--   P  Propiedades captadas
--   S  Solicitudes ingresadas
--   F  Contratos firmados
-- El codigo es estable; el rotulo puede cambiar sin migrar una fila.

CREATE TABLE meta_comercial (
    id_meta             BIGSERIAL     PRIMARY KEY,
    organizacion_id     BIGINT        NOT NULL REFERENCES organizacion (id_organizacion),
    id_rol_agente       BIGINT        NOT NULL REFERENCES persona_rol (id_persona_rol),
    kpi                 VARCHAR(1)    NOT NULL,
    anio                INT           NOT NULL,
    mes                 INT           NOT NULL,
    valor               INT           NOT NULL,
    id_rol_autor        BIGINT        REFERENCES persona_rol (id_persona_rol),
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMPTZ,

    -- Los cuatro KPI canonicos y ninguno mas. Un quinto se decide en D-E2-2,
    -- no se cuela por una fila.
    CONSTRAINT ck_meta_kpi       CHECK (kpi IN ('C', 'P', 'S', 'F')),
    CONSTRAINT ck_meta_mes       CHECK (mes BETWEEN 1 AND 12),
    CONSTRAINT ck_meta_anio      CHECK (anio BETWEEN 2020 AND 2100),
    -- Cero es una meta legitima: "este mes no se te pide captar". Negativo no.
    CONSTRAINT ck_meta_valor     CHECK (valor >= 0),

    -- Una meta por agente, KPI y mes. Sin esto, dos filas discrepantes y la
    -- suma del equipo depende de cual lea la consulta.
    CONSTRAINT uq_meta_agente_kpi_mes UNIQUE (organizacion_id, id_rol_agente, kpi, anio, mes)
);

-- La lectura del tablero es siempre "las metas de estos agentes en este mes".
CREATE INDEX ix_meta_org_periodo ON meta_comercial (organizacion_id, anio, mes);
CREATE INDEX ix_meta_agente      ON meta_comercial (id_rol_agente, anio, mes);

COMMENT ON TABLE  meta_comercial IS
    'Meta mensual por agente y KPI canonico. La meta del equipo NO se guarda: es la suma '
    'de las de sus agentes (D-E2-2 §5). Nace vacia a proposito.';
COMMENT ON COLUMN meta_comercial.kpi IS
    'Codigo unitario del KPI canonico: C contactados, P captadas, S solicitudes, F contratos.';
COMMENT ON COLUMN meta_comercial.valor IS
    'Meta del mes. Cero es legitimo y significa que este mes no se pide ese resultado.';
COMMENT ON COLUMN meta_comercial.id_rol_autor IS
    'Quien la fijo. Nullable para las cargadas por proceso, no por una persona.';
