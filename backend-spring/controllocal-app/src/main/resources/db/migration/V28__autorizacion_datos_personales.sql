-- =====================================================================
-- V28: autorizacion de datos personales (D-27).
--
-- REUTILIZA las estructuras que V6 ya creo y que nadie usaba
-- (finalidad_tratamiento, aviso_privacidad_version, evidencia_autorizacion,
-- autorizacion_tratamiento_evento). NO se crea ninguna tabla nueva y NO se
-- edita ninguna migracion aplicada: V1-V27 son historia.
--
-- Tres cosas:
--   1. Una sola finalidad ACTIVA, que cubre los cinco ambitos de operacion.
--   2. Dos columnas que faltaban: quien registro y por que se revoco.
--   3. Vigencia atada al aviso: un cambio MATERIAL caduca lo autorizado
--      contra versiones anteriores.
-- =====================================================================

-- ---------------------------------------------------------------------
-- V28.1  Una sola autorizacion, cinco ambitos
--
-- Se reutiliza OPERACION_SERVICIO en vez de inventar un codigo nuevo. V6 la
-- sembro como NECESARIA/no-revocable pensando en "lo imprescindible del
-- servicio"; D-27 la convierte en la UNICA autorizacion que se pide, y por
-- tanto pasa a exigir consentimiento y a admitir revocacion.
-- ---------------------------------------------------------------------
UPDATE finalidad_tratamiento
   SET nombre      = 'Operacion de BROX',
       descripcion = 'Gestion comercial e inmobiliaria; comunicaciones y seguimiento; '
                  || 'documentos, contratos y pagos; seguridad, auditoria y cumplimiento '
                  || 'legal; y automatizaciones internas necesarias para operar el servicio.',
       requiere_consentimiento = TRUE,
       permite_revocacion      = TRUE,
       nivel                   = 'NECESARIA',
       estado                  = 'A'
 WHERE codigo = 'OPERACION_SERVICIO';

-- Las cuatro opcionales quedan INACTIVAS: siguen en el catalogo como destino
-- (analitica, mejora de modelos, red colaborativa y prospeccion), pero ninguna
-- pantalla las ofrece y ningun flujo las escribe. Entrenar modelos con datos
-- identificables exigiria una autorizacion independiente y separada.
UPDATE finalidad_tratamiento
   SET estado = 'I'
 WHERE codigo IN ('ANALITICA_AGREGADA', 'MEJORA_MODELOS',
                  'RED_COLABORATIVA', 'PROSPECCION_COMERCIAL');

-- ---------------------------------------------------------------------
-- V28.2  Lo que faltaba en el evento: quien lo registro y por que se revoco
-- ---------------------------------------------------------------------
ALTER TABLE autorizacion_tratamiento_evento
    ADD COLUMN registrada_por    BIGINT REFERENCES persona_rol (id_persona_rol),
    ADD COLUMN motivo_revocacion VARCHAR(300);

COMMENT ON COLUMN autorizacion_tratamiento_evento.registrada_por IS
    'persona_rol del usuario interno que registro el evento. NULL cuando lo '
    'produce el propio titular (canje de un enlace) o un proceso del sistema.';

-- Un motivo solo tiene sentido cuando se retira la autorizacion.
ALTER TABLE autorizacion_tratamiento_evento
    ADD CONSTRAINT ck_autorizacion_motivo_solo_al_revocar
        CHECK (motivo_revocacion IS NULL OR evento = 'REVOCADO');

-- ---------------------------------------------------------------------
-- V28.3  Vigencia atada al aviso
--
-- Un cambio MATERIAL del aviso caduca las autorizaciones otorgadas contra
-- versiones anteriores. No se borra ni se modifica ningun evento (el registro
-- es append-only): lo que cambia es la PROYECCION de vigencia.
-- Una correccion de redaccion se publica con cambio_material = FALSE y no
-- molesta a nadie; sin esta bandera, cualquier retoque del texto obligaria a
-- repreguntar a toda la cartera.
-- ---------------------------------------------------------------------
ALTER TABLE aviso_privacidad_version
    ADD COLUMN cambio_material BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN aviso_privacidad_version.cambio_material IS
    'TRUE si esta version cambia materialmente el tratamiento. Caduca las '
    'autorizaciones otorgadas contra versiones anteriores (D-27 §3.4).';

-- Primera version vigente: sin ella no hay contra que registrar una
-- autorizacion, y el alta fallaria en la primera pantalla.
-- El contenido definitivo lo redacta la pagina publica de privacidad; aqui va
-- el minimo con las cinco finalidades, para que la version exista y sea citable.
INSERT INTO aviso_privacidad_version (version, contenido_hash, contenido, cambio_material)
SELECT '1.0',
       encode(sha256(CAST(
           'Aviso de privacidad BROX v1.0. Finalidades: (1) gestion comercial e inmobiliaria; '
        || '(2) comunicaciones y seguimiento; (3) documentos, contratos y pagos; '
        || '(4) seguridad, auditoria y cumplimiento legal; (5) automatizaciones internas '
        || 'necesarias para operar el servicio. El titular puede revocar su autorizacion; '
        || 'la revocacion no afecta a los tratamientos sostenidos en relacion contractual u '
        || 'obligacion legal.' AS bytea)), 'hex'),
       'Aviso de privacidad BROX v1.0. Finalidades: (1) gestion comercial e inmobiliaria; '
        || '(2) comunicaciones y seguimiento; (3) documentos, contratos y pagos; '
        || '(4) seguridad, auditoria y cumplimiento legal; (5) automatizaciones internas '
        || 'necesarias para operar el servicio. El titular puede revocar su autorizacion; '
        || 'la revocacion no afecta a los tratamientos sostenidos en relacion contractual u '
        || 'obligacion legal.',
       FALSE
 WHERE NOT EXISTS (SELECT 1 FROM aviso_privacidad_version);

-- Una sola version vigente a la vez: es la que se cita al registrar.
CREATE UNIQUE INDEX uq_aviso_vigente
    ON aviso_privacidad_version ((TRUE))
 WHERE vigente_hasta IS NULL;

-- ---------------------------------------------------------------------
-- V28.4  Indice de la proyeccion de vigencia
--
-- La consulta caliente es "el ultimo evento de esta persona para esta
-- finalidad", una vez por alta y una vez por comprobacion.
-- ---------------------------------------------------------------------
CREATE INDEX ix_autorizacion_vigencia
    ON autorizacion_tratamiento_evento (organizacion_id, id_persona, finalidad_codigo, id_evento DESC);
