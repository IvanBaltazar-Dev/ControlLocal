-- RF014 / CAT-E07 / AC056-057. Aprobación expresa del titular 28/10/2026.
-- Origen explícito; no fusiona identidades ni expedientes o paquetes comerciales.
-- V1-111 se conservan. A/I/C no se convierten retrospectivamente en origen.

ALTER TABLE unidad_relacionada DROP CONSTRAINT ck_unidad_relacionada_tipo;
ALTER TABLE unidad_relacionada ADD CONSTRAINT ck_unidad_relacionada_tipo
    CHECK (tipo_relacion IN ('A','I','C','O'));
ALTER TABLE unidad_relacionada
    ADD COLUMN situacion_registral TEXT,
    ADD COLUMN fuente TEXT,
    ADD COLUMN procedencia TEXT,
    ADD COLUMN version_matriz_observada BIGINT,
    ADD COLUMN version_unidad_observada BIGINT,
    ADD COLUMN id_rol_actor BIGINT,
    ADD COLUMN registrado_en TIMESTAMPTZ;

-- Sin defaults ni backfill que atribuyan evidencia/fechas inexistentes a A/I/C.
ALTER TABLE unidad_relacionada ADD CONSTRAINT ck_origen_fisico_trazabilidad
    CHECK (tipo_relacion <> 'O' OR (
        fuente IS NOT NULL AND btrim(fuente) <> ''
        AND procedencia IS NOT NULL AND jsonb_typeof(procedencia::jsonb) = 'object'
        AND version_matriz_observada IS NOT NULL AND version_matriz_observada >= 0
        AND version_unidad_observada IS NOT NULL AND version_unidad_observada >= 0
        AND id_rol_actor IS NOT NULL AND registrado_en IS NOT NULL
        AND (situacion_registral IS NULL OR btrim(situacion_registral) <> '')
    ));
ALTER TABLE unidad_relacionada ADD CONSTRAINT fk_origen_fisico_actor
    FOREIGN KEY (organizacion_id,id_rol_actor)
    REFERENCES persona_rol(organizacion_id,id_persona_rol);

COMMENT ON COLUMN unidad_relacionada.tipo_relacion IS
    'A/I/C conservan asociación anterior; O es origen explícito matriz a unidad. No implica independización.';
COMMENT ON COLUMN unidad_relacionada.situacion_registral IS
    'Texto declarado con fuente; NULL significa FALTANTE. No acredita una situación jurídica.';
COMMENT ON COLUMN unidad_relacionada.registrado_en IS
    'Instante de registro, distinto de vigente_desde/vigente_hasta; sin fecha retrospectiva inventada.';

-- La asociación genérica simétrica conserva su guarda y no bloquea un hecho O distinto.
DROP INDEX uq_unidad_relacionada_vigente;
CREATE UNIQUE INDEX uq_unidad_relacionada_vigente ON unidad_relacionada (
    organizacion_id,LEAST(id_propiedad_principal,id_propiedad_relacionada),
    GREATEST(id_propiedad_principal,id_propiedad_relacionada)
) WHERE tipo_relacion <> 'O' AND vigente_hasta IS NULL;
CREATE UNIQUE INDEX uq_origen_fisico_abierto ON unidad_relacionada (
    organizacion_id,id_propiedad_principal,id_propiedad_relacionada
) WHERE tipo_relacion='O' AND vigente_hasta IS NULL;
CREATE INDEX ix_origen_fisico_par ON unidad_relacionada (
    organizacion_id,id_propiedad_principal,id_propiedad_relacionada,vigente_desde,vigente_hasta
) WHERE tipo_relacion='O';

-- Barrera técnica por organización: no es una propiedad ni una entidad comercial.
-- La escritura real de revisión obliga a abortar una instantánea REPEATABLE READ
-- obsoleta, también si dos INSERT SQL directos esperaron el mismo advisory lock.
CREATE TABLE coordinacion_origen_fisico (
    organizacion_id BIGINT PRIMARY KEY REFERENCES organizacion(id_organizacion),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);

CREATE FUNCTION bloquear_grafo_origen(p_organizacion BIGINT)
RETURNS integer LANGUAGE plpgsql VOLATILE AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('BROX:ORIGEN:' || p_organizacion::text,0));
    RETURN 0;
END $$;

CREATE FUNCTION proteger_origen_fisico()
RETURNS trigger LANGUAGE plpgsql VOLATILE AS $$
DECLARE v_ciclo boolean;
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.tipo_relacion='O' THEN
            RAISE EXCEPTION 'El antecedente de origen físico no se elimina.' USING ERRCODE='check_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.tipo_relacion='O' OR NEW.tipo_relacion='O' THEN
            RAISE EXCEPTION 'El origen físico conserva su declaración original; no se sobrescribe ni se convierte.'
                USING ERRCODE='check_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.tipo_relacion <> 'O' THEN RETURN NEW; END IF;

    PERFORM bloquear_grafo_origen(NEW.organizacion_id);
    INSERT INTO coordinacion_origen_fisico(organizacion_id,revision)
        VALUES(NEW.organizacion_id,1)
        ON CONFLICT(organizacion_id) DO UPDATE
        SET revision=coordinacion_origen_fisico.revision+1;

    IF NEW.id_propiedad_principal=NEW.id_propiedad_relacionada THEN
        RAISE EXCEPTION 'Un inmueble no puede ser su propia unidad de origen.' USING ERRCODE='check_violation';
    END IF;
    IF NEW.vigente_desde IS NULL OR (NEW.vigente_hasta IS NOT NULL AND NEW.vigente_hasta < NEW.vigente_desde) THEN
        RAISE EXCEPTION 'El origen requiere una vigencia declarada válida.' USING ERRCODE='check_violation';
    END IF;
    IF EXISTS (
        SELECT 1 FROM unidad_relacionada u
        WHERE u.organizacion_id=NEW.organizacion_id AND u.tipo_relacion='O'
          AND u.id_propiedad_principal=NEW.id_propiedad_principal
          AND u.id_propiedad_relacionada=NEW.id_propiedad_relacionada
          AND daterange(u.vigente_desde,u.vigente_hasta,'[]')
              && daterange(NEW.vigente_desde,NEW.vigente_hasta,'[]')
    ) THEN
        RAISE EXCEPTION 'El origen de este par ya está registrado para una vigencia solapada.'
            USING ERRCODE='unique_violation';
    END IF;

    -- La procedencia física preservada no admite que un inmueble se vuelva
    -- su propio ancestro. UNION termina incluso ante un grafo previo inválido.
    WITH RECURSIVE descendientes(id) AS (
        SELECT NEW.id_propiedad_relacionada
        UNION
        SELECT u.id_propiedad_relacionada FROM unidad_relacionada u
          JOIN descendientes d ON d.id=u.id_propiedad_principal
         WHERE u.organizacion_id=NEW.organizacion_id AND u.tipo_relacion='O'
    ) SELECT EXISTS(SELECT 1 FROM descendientes WHERE id=NEW.id_propiedad_principal) INTO v_ciclo;
    IF v_ciclo THEN
        RAISE EXCEPTION 'El origen propuesto crearía una relación circular entre inmuebles.'
            USING ERRCODE='check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_origen_fisico_conservacion
    BEFORE INSERT OR UPDATE OR DELETE ON unidad_relacionada
    FOR EACH ROW EXECUTE FUNCTION proteger_origen_fisico();

-- Misma máquina de revisión/confirmación durable, sin borrador de captura ficticio.
ALTER TABLE operacion_herramienta ALTER COLUMN id_borrador DROP NOT NULL;
ALTER TABLE operacion_herramienta DROP CONSTRAINT operacion_herramienta_capacidad_check;
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_capacidad
    CHECK (capacidad IN ('confirmar_captura','registrar_origen_fisico_unidad'));
ALTER TABLE operacion_herramienta ADD CONSTRAINT ck_operacion_recurso_concreto
    CHECK ((capacidad='confirmar_captura' AND id_borrador IS NOT NULL)
        OR (capacidad='registrar_origen_fisico_unidad' AND id_borrador IS NULL));
