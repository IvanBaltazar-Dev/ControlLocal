-- =====================================================================
-- V87 - El RESPONSABLE de la propiedad, explicito y traspasable
-- =====================================================================
--
-- QUE ARREGLA. Hasta hoy no existia ninguna autoridad de escritura sobre
-- la propiedad. `PUT /propiedades/{id}` cargaba la fila por
-- (organizacion, id) y escribia: cualquier AGENTE del tenant editaba la
-- ficha de cualquier propiedad, y de paso el importe, la exclusividad y la
-- vigencia de un ENCARGO que no era suyo -- con su hito `U` en la serie
-- economica ajena. Lo mas parecido a una regla que habia,
-- `LocalComercialServiceImpl.exigirPertenencia`, es un OR de tres
-- condiciones con UN solo llamador (`DELETE /locales/{id}`), y con dos
-- encargos de agentes distintos da verdadero para los dos.
--
-- LA DECISION (P0-1..P0-4). Una propiedad la modifica su AGENTE
-- RESPONSABLE, y nadie mas. Ver no concede editar. BROKER y TENANT_ADMIN
-- conservan supervision y gobierno pero no escriben hechos de la
-- propiedad por alcance de tenant. El responsable se declara en una
-- columna propia y se cambia por un TRASPASO explicito que deja rastro.
--
-- POR QUE UNA COLUMNA Y NO UNA DERIVACION DEL ENCARGO. Porque el encargo
-- no puede responder la pregunta: `uq_captacion_viva_por_operacion` es
-- unico por (id_propiedad, motivo_operacion), asi que una misma propiedad
-- admite una VENTA y un ALQUILER vivos a la vez y ninguna restriccion los
-- obliga a ser del mismo agente. Con dos vivos habria que elegir uno, y
-- una fecha comercial no puede ser la autoridad sobre la verdad fisica.
-- `controllocal_dev` ya tiene la propiedad 3259 con los dos encargos
-- vivos.
--
-- POR QUE NO SE CONSAGRA `id_rol_incorporo`. Su comentario de V76 y su
-- javadoc la declaran PROCEDENCIA INMUTABLE -- "se escribe UNA vez, al
-- nacer", "no cambia porque despues se capte". Convertirla en permiso le
-- cambiaria el significado a un dato historico ya escrito. Conserva
-- exclusivamente su significado historico y esta migracion no la toca.
--
-- ADITIVA. No modifica ninguna migracion aplicada, ni ninguna columna
-- existente, ni ningun dato existente.
--
-- ---------------------------------------------------------------------
-- SIN BACKFILL, Y ESO ES LA DECISION P0-3, NO UN OLVIDO
-- ---------------------------------------------------------------------
-- Medido contra las dos bases el 2026-08-30, antes de escribir una linea:
--
--   controllocal_dev            26 propiedades. 2 de origen SEMILLA y 24
--                               de origen OPERACION (residuo de corridas
--                               E2E, no cartera declarada). `id_rol_
--                               incorporo` NULL en 26 de 26: son
--                               anteriores a V76. 12 no tienen ninguna
--                               captacion viva. Las 17 que tienen encargo
--                               lo tienen del MISMO agente (rol 28).
--   controllocal_repositorios   13.070 propiedades, 9.622 con encargo
--                               vivo. Base de pruebas: su contenido es
--                               residuo acumulado de la suite.
--
-- La semilla (V4/V5) NO declara un agente responsable de la propiedad.
-- Declara una captacion de AGE-001 para LOC-0001 y deja LOC-0002 sin
-- ninguna. Escribir el responsable desde ahi seria derivarlo del encargo
-- -- exactamente lo que P0-2 descarto -- y para LOC-0002 seria inventarlo.
-- Y de las 24 restantes no hay ninguna fuente que lo declare.
--
-- Asi que la columna nace NULL en las 26 y en las 13.070, y NULL
-- significa FALTANTE: la propiedad queda VISIBLE y NO EDITABLE, con
-- motivo explicito, hasta que un BROKER asigne responsable por el
-- traspaso trazable. Es la regla del North Star -- un dato que no se sabe
-- se declara FALTANTE y no se rellena con el caso frecuente -- y es la
-- razon de que la columna sea NULLABLE y no lleve DEFAULT.
--
-- FALTANTE bloquea la escritura de la PROPIEDAD. No bloquea nada mas: ni
-- la operacion del encargo de quien lo tenga, ni la publicacion, ni el
-- matching, ni la lectura.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. La columna.
--
-- FK COMPUESTA por organizacion, igual que las seis `fk_*_org` que ya
-- existen (y que `fk_propiedad_incorporo`): sin la organizacion en la
-- clave, un id de rol de otra corredora entraria por la puerta de al
-- lado. NULLABLE a proposito -- ver el bloque de arriba.
-- ---------------------------------------------------------------------
ALTER TABLE propiedad
    ADD COLUMN id_rol_responsable BIGINT;

ALTER TABLE propiedad
    ADD CONSTRAINT fk_propiedad_responsable_org
    FOREIGN KEY (organizacion_id, id_rol_responsable)
    REFERENCES persona_rol (organizacion_id, id_persona_rol);

-- "Que propiedades responde este agente" es la consulta de la bandeja y
-- del traspaso, y va siempre acotada por tenant.
CREATE INDEX ix_propiedad_responsable
    ON propiedad (organizacion_id, id_rol_responsable);

COMMENT ON COLUMN propiedad.id_rol_responsable IS
    'AGENTE que responde HOY por los hechos de esta propiedad y el unico '
    'que puede escribirlos (P0-1). NULL = FALTANTE: nadie edita hasta que '
    'un BROKER asigne por asignacion_responsable_propiedad. Es autoridad '
    'ACTUAL y mutable; no confundir con id_rol_incorporo, que es '
    'procedencia historica inmutable. Es independiente de los ENCARGOS: '
    'no se deriva de captacion.id_rol_agente ni la reasignacion de un '
    'encargo la cambia. Dato de gobierno INTERNO: no sale de la frontera '
    'de la operacion interna.';


-- ---------------------------------------------------------------------
-- 2. El traspaso, con su rastro.
--
-- Misma forma que `reasignacion_captacion` -- de quien a quien, quien lo
-- autorizo, cuando y por que -- porque es el mismo tipo de hecho un nivel
-- mas arriba. Dos diferencias deliberadas:
--
--   * `id_rol_responsable_anterior` es NULLABLE, y aqui esta la primera
--     asignacion de una propiedad FALTANTE: no hay "de quien". En
--     `reasignacion_captacion` el anterior es NOT NULL porque una
--     captacion nunca existio sin agente.
--   * el anterior y el nuevo pueden compararse pero NO se exige que
--     difieran cuando el anterior es NULL.
--
-- Append-only por construccion: no hay UPDATE ni DELETE en el servicio.
-- ---------------------------------------------------------------------
CREATE TABLE asignacion_responsable_propiedad (
    id_asignacion                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    organizacion_id              BIGINT      NOT NULL,
    id_propiedad                 BIGINT      NOT NULL,
    id_rol_responsable_anterior  BIGINT,
    id_rol_responsable_nuevo     BIGINT      NOT NULL,
    -- Quien lo autorizo: la persona y la banda con la que actuo. Las dos,
    -- porque una persona puede gobernar Y operar y el rastro tiene que
    -- decir cual uso (misma leccion que H-09 en reasignacion_captacion).
    id_persona_actor             BIGINT      NOT NULL,
    tipo_rol_actor               VARCHAR(20) NOT NULL,
    motivo                       TEXT        NOT NULL,
    fecha_asignacion             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_asignacion_resp_organizacion
        FOREIGN KEY (organizacion_id) REFERENCES organizacion (id_organizacion),
    CONSTRAINT fk_asignacion_resp_propiedad_org
        FOREIGN KEY (organizacion_id, id_propiedad)
        REFERENCES propiedad (organizacion_id, id_propiedad),
    CONSTRAINT fk_asignacion_resp_anterior_org
        FOREIGN KEY (organizacion_id, id_rol_responsable_anterior)
        REFERENCES persona_rol (organizacion_id, id_persona_rol),
    CONSTRAINT fk_asignacion_resp_nuevo_org
        FOREIGN KEY (organizacion_id, id_rol_responsable_nuevo)
        REFERENCES persona_rol (organizacion_id, id_persona_rol),
    CONSTRAINT fk_asignacion_resp_actor
        FOREIGN KEY (id_persona_actor) REFERENCES persona (id_persona),

    -- Un traspaso traspasa. Reasignar al mismo no es un hecho, es ruido
    -- en el expediente -- y ademas dejaria "de A a A" en la historia.
    CONSTRAINT ck_asignacion_resp_cambia
        CHECK (id_rol_responsable_anterior IS NULL
               OR id_rol_responsable_anterior <> id_rol_responsable_nuevo),
    -- El motivo es del hecho, no del formulario: sin el, el expediente
    -- dice que la propiedad cambio de manos y no dice por que.
    CONSTRAINT ck_asignacion_resp_motivo
        CHECK (length(btrim(motivo)) > 0),
    CONSTRAINT ck_asignacion_resp_banda
        CHECK (tipo_rol_actor IN ('BROKER', 'TENANT_ADMIN'))
);

CREATE INDEX ix_asignacion_resp_propiedad
    ON asignacion_responsable_propiedad (id_propiedad, fecha_asignacion DESC);

CREATE INDEX ix_asignacion_resp_organizacion
    ON asignacion_responsable_propiedad (organizacion_id);

COMMENT ON TABLE asignacion_responsable_propiedad IS
    'Traspaso del responsable de una propiedad (P0-2). Append-only. '
    'La asignacion NO modifica ningun atributo inmobiliario y NO reasigna '
    'ningun ENCARGO: son autoridades distintas.';

COMMENT ON COLUMN asignacion_responsable_propiedad.id_rol_responsable_anterior IS
    'NULL en la primera asignacion de una propiedad que estaba FALTANTE. '
    'No se rellena con el agente de ningun encargo: no habia responsable.';
