-- =====================================================================
-- V59 - Procedencia: por que canal entro, que agente lo pidio, de que
--       conversacion salio y con que mensaje se puede probar.
--
-- EL ERROR DE MODELADO QUE ESTA MIGRACION CORRIGE
-- V52 creo `evento_dominio.origen IN ('UI','KAIROS','API','SISTEMA')`. Esos
-- cuatro valores contestan DOS preguntas distintas metidas en una columna:
--
--     UI, API, SISTEMA  ->  POR DONDE entro la peticion   (canal)
--     KAIROS            ->  QUIEN la formulo              (agente)
--
-- Mientras KAIROS fuera "una parte de BROX que conversa", la confusion no
-- costaba nada. Deja de ser gratis en cuanto KAIROS es un sistema aparte que
-- habla por WhatsApp: entonces **KAIROS no es un canal** —WhatsApp lo es— y un
-- mismo agente puede entrar por WhatsApp hoy y por otro canal manana. Con una
-- sola columna, "una propiedad registrada desde WhatsApp por un agente de IA"
-- no se puede escribir: hay que elegir entre perder el canal o perder el
-- agente.
--
-- LA SEPARACION
--     canal    SPA | WHATSAPP | API | SISTEMA      por donde entro
--     agente   NULL si lo pidio una persona directamente;
--              el nombre del agente automatico si no
--
-- `agente IS NULL` es la lectura importante: significa **una persona lo pidio
-- ella misma**. No es un hueco de datos, es un hecho, y es el que distingue una
-- operacion tecleada de una conversada.
--
-- POR QUE ADEMAS EL MODELO Y SU VERSION
-- Porque un agente automatico no es una cosa fija. El dia que una recomendacion
-- salga rara, la pregunta sera "que modelo la produjo y en que version", y esa
-- respuesta no se puede reconstruir despues: el despliegue de entonces ya no
-- existe. Cuesta dos columnas ahora y es imposible mas tarde.
--
-- LO QUE SE GUARDA Y LO QUE NO
-- Aqui va la procedencia de un HECHO, no la conversacion. `mensaje_id` apunta
-- al mensaje del canal —el audio del que salio "ofrezco 165 mil"— y el audio en
-- si vive en el sistema conversacional, con su politica de conservacion. BROX
-- guarda el puntero y la frase; no guarda el medio.
--
-- Tampoco se guarda "que creyo el agente que le pedian". Eso es interpretacion
-- y pertenece a quien interpreta. BROX registra **la herramienta que se
-- invoco**, que es lo unico que BROX presencio de verdad.
--
-- POR QUE NO HAY CHECK QUE EXIJA CONVERSACION CUANDO HAY AGENTE
-- Porque `canal` y `agente` los declara el cliente, y una etiqueta mentida
-- debe producir un dato pobre, no un 500. La garantia vive donde hay prueba:
-- el servicio que recibe una peticion de agente exige conversacion y turno
-- antes de invocar ningun caso de uso.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. evento_dominio: la procedencia completa
-- ---------------------------------------------------------------------

ALTER TABLE evento_dominio DROP CONSTRAINT ck_evento_origen;
ALTER TABLE evento_dominio RENAME COLUMN origen TO canal;
ALTER TABLE evento_dominio ALTER COLUMN canal TYPE VARCHAR(20);

ALTER TABLE evento_dominio
    -- Que agente automatico lo pidio. NULL = lo pidio una persona directamente.
    ADD COLUMN agente                VARCHAR(30),
    -- Con que modelo y version razono ese agente. Irreconstruible despues.
    ADD COLUMN agente_modelo         VARCHAR(60),
    ADD COLUMN agente_modelo_version VARCHAR(40),
    -- De que conversacion y de que turno exacto salio el hecho.
    ADD COLUMN conversacion_id       VARCHAR(64),
    ADD COLUMN turno_id              VARCHAR(64),
    -- El mensaje del canal que lo origino: la evidencia. El medio (audio,
    -- imagen) NO se guarda aqui; esto es el puntero para poder ir a buscarlo.
    ADD COLUMN mensaje_id            VARCHAR(128),
    -- Lo que la persona escribio o dicto, literal.
    ADD COLUMN peticion              TEXT,
    -- La operacion que se invoco. Con esto se mide que se usa de verdad.
    ADD COLUMN herramienta           VARCHAR(60);

-- El vocabulario viejo, traducido al nuevo. `UI` era el SPA; `KAIROS` no era un
-- canal sino un agente que entraba por la API, asi que se parte en dos.
UPDATE evento_dominio SET canal = 'SPA' WHERE canal = 'UI';
UPDATE evento_dominio SET agente = 'KAIROS', canal = 'API' WHERE canal = 'KAIROS';

ALTER TABLE evento_dominio
    ADD CONSTRAINT ck_evento_canal
        CHECK (canal IN ('SPA', 'WHATSAPP', 'API', 'SISTEMA'));

COMMENT ON COLUMN evento_dominio.canal IS
    'Por donde entro la peticion: SPA, WHATSAPP, API o SISTEMA. No dice quien la formulo.';
COMMENT ON COLUMN evento_dominio.agente IS
    'Que agente automatico la formulo. NULL = la pidio una persona directamente.';
COMMENT ON COLUMN evento_dominio.mensaje_id IS
    'El mensaje del canal del que salio el hecho. Puntero a la evidencia, no la evidencia.';
COMMENT ON COLUMN evento_dominio.peticion IS
    'Lo que la persona escribio o dicto, literal.';
COMMENT ON COLUMN evento_dominio.herramienta IS
    'La operacion invocada. Lo unico que BROX presencio; que se creyo que pedian es del agente.';

-- Reconstruir "que hizo esta conversacion" es la consulta de auditoria que
-- justifica las columnas. Parcial: casi todos los eventos son de pantalla y no
-- tienen conversacion, e indexarlos seria pagar por filas que nadie busca asi.
CREATE INDEX ix_evento_conversacion
    ON evento_dominio (organizacion_id, conversacion_id)
    WHERE conversacion_id IS NOT NULL;

-- "De que mensaje salio esto" y su inversa "que produjo este mensaje". La
-- segunda es la que hace falta para no reprocesar un webhook reenviado.
CREATE INDEX ix_evento_mensaje
    ON evento_dominio (organizacion_id, mensaje_id)
    WHERE mensaje_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. borrador_captura: por donde entro y de que conversacion nacio
-- ---------------------------------------------------------------------

ALTER TABLE borrador_captura DROP CONSTRAINT ck_borrador_canal;
ALTER TABLE borrador_captura RENAME COLUMN canal_origen TO canal;
ALTER TABLE borrador_captura ALTER COLUMN canal TYPE VARCHAR(20);

ALTER TABLE borrador_captura
    ADD COLUMN agente          VARCHAR(30),
    -- Lo que permite retomar diciendo "sigamos con lo de ayer" en vez de con un
    -- id: un canal conversacional no tiene donde guardar un numero entre
    -- sesiones, pero la conversacion es justo lo que si conserva.
    ADD COLUMN conversacion_id VARCHAR(64);

UPDATE borrador_captura SET canal = 'SPA' WHERE canal = 'UI';
UPDATE borrador_captura SET agente = 'KAIROS', canal = 'API' WHERE canal = 'KAIROS';

ALTER TABLE borrador_captura
    ALTER COLUMN canal SET DEFAULT 'SPA',
    ADD CONSTRAINT ck_borrador_canal
        CHECK (canal IN ('SPA', 'WHATSAPP', 'API', 'SISTEMA'));

-- Una conversacion tiene como mucho un borrador vivo por intencion: con dos,
-- "continua lo que empezamos" seria ambiguo y habria que preguntar cual, que es
-- justo la friccion que el borrador vino a quitar. Parcial sobre los EN CURSO:
-- los ejecutados y descartados son historia y pueden repetirse.
CREATE UNIQUE INDEX uq_borrador_vivo_por_conversacion
    ON borrador_captura (organizacion_id, conversacion_id, intencion)
    WHERE conversacion_id IS NOT NULL AND estado = 'E';

-- ---------------------------------------------------------------------
-- 3. comando_idempotente: mismo vocabulario, y el mensaje que lo disparo
-- ---------------------------------------------------------------------
--
-- `mensaje_id` aqui no es decorativo: un webhook reenviado trae el MISMO
-- identificador de mensaje, asi que es la clave natural de idempotencia de un
-- canal conversacional. No sustituye a `idempotency_key` —que sigue siendo la
-- restriccion— pero deja escrito de que mensaje salio cada comando.

ALTER TABLE comando_idempotente DROP CONSTRAINT ck_comando_origen;
ALTER TABLE comando_idempotente RENAME COLUMN origen TO canal;
ALTER TABLE comando_idempotente ALTER COLUMN canal TYPE VARCHAR(20);

ALTER TABLE comando_idempotente
    ADD COLUMN agente     VARCHAR(30),
    ADD COLUMN mensaje_id VARCHAR(128);

UPDATE comando_idempotente SET canal = 'SPA' WHERE canal = 'UI';
UPDATE comando_idempotente SET agente = 'KAIROS', canal = 'API' WHERE canal = 'KAIROS';

ALTER TABLE comando_idempotente
    ALTER COLUMN canal SET DEFAULT 'SPA',
    ADD CONSTRAINT ck_comando_canal
        CHECK (canal IN ('SPA', 'WHATSAPP', 'API', 'SISTEMA'));

DO $$
BEGIN
    RAISE NOTICE 'V59: canal separado de agente; conversacion, mensaje y modelo registrados';
END $$;
