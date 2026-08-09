-- =====================================================================
-- Criterio #7 del gate de V6 (docs/ai/plan-migracion-v6-tenancy.md):
-- dos organizaciones tecnicas conviven con los MISMOS codigos comerciales
-- sin colisionar, y ninguna relacion puede cruzar la frontera de tenant.
--
-- No activa multi-tenancy comercial: solo demuestra que el ESQUEMA lo
-- soporta. Corre entero dentro de una transaccion que termina en ROLLBACK,
-- asi que se puede lanzar contra la BD de desarrollo sin ensuciarla.
--
-- Uso:
--   docker exec -i controllocal-postgres-v2 psql -U controllocal \
--     -d controllocal_e2e_<run_id> -v ON_ERROR_STOP=1 \
--     < backend-spring/verificacion/v6-dos-organizaciones.sql
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;

-- ---------------------------------------------------------------------
-- Dos tenants tecnicos.
-- ---------------------------------------------------------------------
INSERT INTO organizacion (codigo, nombre) VALUES
    ('TEST_ORG_A', 'Corredora de prueba A'),
    ('TEST_ORG_B', 'Corredora de prueba B');

CREATE TEMP TABLE t AS
SELECT (SELECT id_organizacion FROM organizacion WHERE codigo = 'TEST_ORG_A') AS a,
       (SELECT id_organizacion FROM organizacion WHERE codigo = 'TEST_ORG_B') AS b;

-- ---------------------------------------------------------------------
-- Mismo documento, mismo correo y mismos codigos operativos en ambas.
-- Antes de V6 cualquiera de estos INSERT reventaba por unicidad global.
-- ---------------------------------------------------------------------
INSERT INTO persona (organizacion_id, tipo_persona, tipo_documento, numero_documento,
                     nombres_o_razon_social, correo)
SELECT o, 'N', 'D', '70707070', 'Agente Homonimo', 'homonimo@corredora.test'
FROM (SELECT a AS o FROM t UNION ALL SELECT b FROM t) x;

INSERT INTO persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
SELECT p.organizacion_id, p.id_persona, 'AGENTE', current_date
FROM persona p WHERE p.numero_documento = '70707070';

INSERT INTO detalle_agente (organizacion_id, id_persona_rol, codigo_agente, fecha_ingreso)
SELECT pr.organizacion_id, pr.id_persona_rol, 'AGE-001', current_date
FROM persona_rol pr JOIN persona p ON p.id_persona = pr.id_persona
WHERE p.numero_documento = '70707070' AND pr.tipo_rol = 'AGENTE';

-- Un login puede repetirse entre corredoras (alias durante la convivencia).
-- La credencial cuelga del rol USUARIO_INTERNO (FK compuesta con tipo_rol),
-- no del rol operativo.
INSERT INTO persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
SELECT p.organizacion_id, p.id_persona, 'USUARIO_INTERNO', current_date
FROM persona p WHERE p.numero_documento = '70707070';

INSERT INTO credencial_usuario (organizacion_id, id_persona_rol, nombre_usuario, contrasena_hash)
SELECT pr.organizacion_id, pr.id_persona_rol, 'jperez', 'pbkdf2$1$x$y'
FROM persona_rol pr JOIN persona p ON p.id_persona = pr.id_persona
WHERE p.numero_documento = '70707070' AND pr.tipo_rol = 'USUARIO_INTERNO';

-- Propietario homonimo + local con el MISMO codigo en las dos.
INSERT INTO persona (organizacion_id, tipo_persona, tipo_documento, numero_documento,
                     nombres_o_razon_social)
SELECT o, 'N', 'D', '80808080', 'Propietario Homonimo'
FROM (SELECT a AS o FROM t UNION ALL SELECT b FROM t) x;

INSERT INTO persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
SELECT p.organizacion_id, p.id_persona, 'PROPIETARIO', current_date
FROM persona p WHERE p.numero_documento = '80808080';

INSERT INTO propiedad (organizacion_id, codigo, direccion, distrito, metraje,
                       precio_referencial, moneda_referencial, estado_registro,
                       disponibilidad_comercial, id_rol_propietario)
SELECT pr.organizacion_id, 'LOC-0001', 'Av. Homonima 100', 'Miraflores', 100, 5000, 'PEN', 'A', 'D',
       pr.id_persona_rol
FROM persona_rol pr JOIN persona p ON p.id_persona = pr.id_persona
WHERE p.numero_documento = '80808080' AND pr.tipo_rol = 'PROPIETARIO';

-- Mismo CAP-0001 y mismo PRO-0001 en ambas corredoras.
INSERT INTO captacion (organizacion_id, codigo_captacion, fecha_captacion,
                       fecha_inicio_encargo, fecha_fin_encargo,
                       estado, id_propiedad, id_rol_agente)
SELECT prop.organizacion_id, 'CAP-0001', current_date,
       current_date, current_date + 180, 'P', prop.id_propiedad, ag.id_persona_rol
FROM propiedad prop
JOIN detalle_agente ag ON ag.organizacion_id = prop.organizacion_id
WHERE prop.codigo = 'LOC-0001' AND prop.organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t);

INSERT INTO prospeccion (organizacion_id, codigo_prospeccion, estado, id_propiedad, id_rol_agente)
SELECT prop.organizacion_id, 'PRO-0001', 'P', prop.id_propiedad, ag.id_persona_rol
FROM propiedad prop
JOIN detalle_agente ag ON ag.organizacion_id = prop.organizacion_id
WHERE prop.codigo = 'LOC-0001' AND prop.organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t);

-- Un administrador POR organizacion (antes era uno global).
INSERT INTO persona (organizacion_id, tipo_persona, tipo_documento, numero_documento,
                     nombres_o_razon_social)
SELECT o, 'N', 'D', '90909090', 'Admin Homonimo'
FROM (SELECT a AS o FROM t UNION ALL SELECT b FROM t) x;

INSERT INTO persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
SELECT p.organizacion_id, p.id_persona, 'BROKER', current_date
FROM persona p WHERE p.numero_documento = '90909090';

INSERT INTO detalle_broker (organizacion_id, id_persona_rol, codigo_broker,
                            fecha_designacion, es_administrador)
SELECT pr.organizacion_id, pr.id_persona_rol, 'BRK-001', current_date, TRUE
FROM persona_rol pr JOIN persona p ON p.id_persona = pr.id_persona
WHERE p.numero_documento = '90909090';

-- ---------------------------------------------------------------------
-- Comprobacion 1: los codigos repetidos entraron, uno por organizacion.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    faltan TEXT := '';
BEGIN
    IF (SELECT count(*) FROM detalle_agente WHERE codigo_agente = 'AGE-001'
        AND organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t)) <> 2 THEN
        faltan := faltan || ' codigo_agente';
    END IF;
    IF (SELECT count(*) FROM detalle_broker WHERE codigo_broker = 'BRK-001'
        AND organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t)) <> 2 THEN
        faltan := faltan || ' codigo_broker';
    END IF;
    IF (SELECT count(*) FROM propiedad WHERE codigo = 'LOC-0001'
        AND organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t)) <> 2 THEN
        faltan := faltan || ' codigo_propiedad';
    END IF;
    IF (SELECT count(*) FROM captacion WHERE codigo_captacion = 'CAP-0001'
        AND organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t)) <> 2 THEN
        faltan := faltan || ' codigo_captacion';
    END IF;
    IF (SELECT count(*) FROM prospeccion WHERE codigo_prospeccion = 'PRO-0001'
        AND organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t)) <> 2 THEN
        faltan := faltan || ' codigo_prospeccion';
    END IF;
    IF (SELECT count(*) FROM credencial_usuario WHERE nombre_usuario = 'jperez'
        AND organizacion_id IN (SELECT a FROM t UNION ALL SELECT b FROM t)) <> 2 THEN
        faltan := faltan || ' nombre_usuario';
    END IF;
    IF faltan <> '' THEN
        RAISE EXCEPTION 'GATE #7 FALLA: no se pudieron repetir por organizacion:%', faltan;
    END IF;
    RAISE NOTICE 'OK  los 6 codigos comerciales se repiten en las dos organizaciones';
END $$;

-- ---------------------------------------------------------------------
-- Comprobacion 2: DENTRO de una organizacion la unicidad sigue vigente.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    duplicado_permitido BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO propiedad (organizacion_id, codigo, direccion, distrito, metraje,
                               precio_referencial, moneda_referencial, estado_registro,
                               disponibilidad_comercial, id_rol_propietario)
        SELECT prop.organizacion_id, 'LOC-0001', 'Duplicado', 'Miraflores', 10, 10, 'PEN', 'A', 'D',
               prop.id_rol_propietario
        FROM propiedad prop WHERE prop.codigo = 'LOC-0001'
          AND prop.organizacion_id = (SELECT a FROM t);
        duplicado_permitido := TRUE;
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'OK  el codigo de local sigue siendo unico DENTRO de la organizacion';
    END;
    IF duplicado_permitido THEN
        RAISE EXCEPTION 'GATE #7 FALLA: se permitio duplicar LOC-0001 en la misma organizacion';
    END IF;
END $$;

DO $$
DECLARE
    segundo_admin_permitido BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO persona (organizacion_id, tipo_persona, tipo_documento, numero_documento,
                             nombres_o_razon_social)
        SELECT a, 'N', 'D', '91919191', 'Segundo Admin' FROM t;
        INSERT INTO persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
        SELECT p.organizacion_id, p.id_persona, 'BROKER', current_date
        FROM persona p WHERE p.numero_documento = '91919191';
        INSERT INTO detalle_broker (organizacion_id, id_persona_rol, codigo_broker,
                                    fecha_designacion, es_administrador)
        SELECT pr.organizacion_id, pr.id_persona_rol, 'BRK-002', current_date, TRUE
        FROM persona_rol pr JOIN persona p ON p.id_persona = pr.id_persona
        WHERE p.numero_documento = '91919191';
        segundo_admin_permitido := TRUE;
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'OK  sigue habiendo un solo administrador por organizacion';
    END;
    IF segundo_admin_permitido THEN
        RAISE EXCEPTION 'GATE #7 FALLA: la organizacion admitio dos administradores';
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Comprobacion 3 (criterio #4): las FK compuestas impiden cruzar tenants.
-- Se intenta colgar una captacion de la organizacion A sobre el local de B.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    cruce_permitido BOOLEAN := FALSE;
BEGIN
    BEGIN
        -- El periodo del encargo viaja completo a proposito: desde V21 es NOT
        -- NULL, y sin el la insercion moriria por not_null_violation antes de
        -- llegar a la FK compuesta, que es lo que este gate mide.
        INSERT INTO captacion (organizacion_id, codigo_captacion, fecha_captacion,
                               fecha_inicio_encargo, fecha_fin_encargo,
                               estado, id_propiedad, id_rol_agente)
        SELECT (SELECT a FROM t), 'CAP-CRUCE', current_date,
               current_date, current_date + 180, 'P',
               (SELECT id_propiedad FROM propiedad
                 WHERE codigo = 'LOC-0001' AND organizacion_id = (SELECT b FROM t)),
               (SELECT id_persona_rol FROM detalle_agente
                 WHERE organizacion_id = (SELECT a FROM t));
        cruce_permitido := TRUE;
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK  la FK compuesta rechaza una captacion sobre el local de otro tenant';
    END;
    IF cruce_permitido THEN
        RAISE EXCEPTION 'GATE #4/#7 FALLA: se pudo cruzar la frontera de organizacion';
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- Nada de esto queda en la base: la prueba no ensucia el tenant de legado.
-- ---------------------------------------------------------------------
ROLLBACK;

DO $$
BEGIN
    IF (SELECT count(*) FROM organizacion WHERE codigo LIKE 'TEST_ORG_%') <> 0 THEN
        RAISE EXCEPTION 'La prueba dejo organizaciones de test en la base';
    END IF;
    RAISE NOTICE 'OK  el ROLLBACK dejo la base como estaba (solo el tenant de legado)';
END $$;
