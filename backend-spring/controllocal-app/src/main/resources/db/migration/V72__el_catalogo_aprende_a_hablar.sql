-- =====================================================================
-- V72 - Corte 0B: el catalogo aprende a hablar.
--
-- QUE PROBLEMA CIERRA
-- El catalogo sabe declarar cinco tipos de dato y ninguna otra cosa. No sabe
-- decir que opciones tiene una LISTA -- y por eso la unica sembrada,
-- `servicios_disponibles`, viaja como texto libre y destruye justamente la
-- combinacion que importa: "agua si, desague no" y "agua no, desague si" son
-- la misma cadena para cualquier comparacion.
--
-- Tampoco sabe decir cuanto mide un texto (se perdio con el VARCHAR(120) del
-- rubro en V71), ni cuanto vale como maximo un numero, ni que un importe lleva
-- moneda, ni que una fecha es una fecha, ni que un dato puede hacer falta para
-- PUBLICAR sin hacer falta para dar de alta.
--
-- ESTA MIGRACION NO SIEMBRA NI UNA CLAVE. Solo anade CAPACIDADES. Sembrar es
-- el corte siguiente, y ese orden es deliberado: sembrar decenas de campos
-- antes de que el catalogo sepa declarar su vocabulario trasladaria el
-- catalogo a Angular, que es justo lo que el gate de D-A-1 rompe.
--
-- TAMPOCO INTRODUCE `sujeto` NI LA APLICABILIDAD POR OPERACION. Eso es 0C.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. La metadata que faltaba en la definicion de una clave.
--
-- `longitud_maxima` cierra la garantia que se perdio en V71: el rubro tenia
-- VARCHAR(120) y `valor_texto` es TEXT. No se pierde un dato, se pierde una
-- regla -- la misma leccion que V62 y que la carga electrica de V71.
--
-- `familia` es la agrupacion TEMATICA que declara la clave ("edificio",
-- "instalaciones"). NO se confunde con la clasificacion estructural del motor
-- --COMUN / APERTURA / TIPO / OPERACION--, que dice de que lista salio la
-- pregunta y pasa a llamarse `seccion` en el cable. Eran dos conceptos con el
-- mismo nombre y el cliente recibia los dos en el mismo objeto.
-- ---------------------------------------------------------------------
ALTER TABLE catalogo_atributo
    ADD COLUMN ayuda           TEXT,
    ADD COLUMN familia         VARCHAR(30),
    ADD COLUMN valor_maximo    NUMERIC(14,4),
    ADD COLUMN longitud_maxima INTEGER;

COMMENT ON COLUMN catalogo_atributo.ayuda IS
    'Para que sirve este dato, en palabras del corredor. Lo publica el cable.';
COMMENT ON COLUMN catalogo_atributo.familia IS
    'Agrupacion tematica declarada por la clave. Es la UNICA ramificacion que '
    'la regla de arquitectura le permite a Angular, junto al tipo de control.';
COMMENT ON COLUMN catalogo_atributo.longitud_maxima IS
    'Cuanto mide como mucho un valor de texto. Sustituye al VARCHAR(120) que '
    'se perdio al retirar detalle_local_comercial (V71).';

ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_rango_coherente
        CHECK (valor_minimo IS NULL OR valor_maximo IS NULL OR valor_minimo <= valor_maximo),
    ADD CONSTRAINT ck_catalogo_longitud_positiva
        CHECK (longitud_maxima IS NULL OR longitud_maxima > 0);

-- ---------------------------------------------------------------------
-- 2. Tres tipos de dato mas.
--
-- IMPORTE y no "DECIMAL con una clave de moneda al lado": un importe sin su
-- moneda no es un importe, y dos claves separadas dejarian que retirar una
-- dejase la otra huerfana sin que nada lo detectara.
-- ---------------------------------------------------------------------
-- La columna PRIMERO, y no es un detalle: `tipo_dato` era VARCHAR(10) y
-- 'LISTA_MULTIPLE' mide 14. Ensanchar solo el CHECK habria dejado la migracion
-- aplicando en verde y reventando en la primera siembra que usara el tipo
-- nuevo -- un corte mas alla y lejos de la causa. Un CHECK no valida longitud.
ALTER TABLE catalogo_atributo ALTER COLUMN tipo_dato TYPE VARCHAR(20);

ALTER TABLE catalogo_atributo DROP CONSTRAINT ck_catalogo_atributo_tipo_dato;
ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_atributo_tipo_dato
        CHECK (tipo_dato IN ('TEXTO', 'ENTERO', 'DECIMAL', 'BOOLEANO', 'LISTA',
                             'LISTA_MULTIPLE', 'FECHA', 'IMPORTE'));

-- ---------------------------------------------------------------------
-- 3. El vocabulario de una LISTA, con su rotulo.
--
-- La FK va por `id_catalogo_atributo` y no por `clave`, por la misma razon que
-- `catalogo_atributo_tipo`: `clave` solo es unica POR ORGANIZACION
-- (`uq_catalogo_atributo_clave` sobre COALESCE(organizacion_id,0), clave), asi
-- que no puede ser destino de una FK.
--
-- Sin `organizacion_id` a proposito, y es una decision, no un olvido: el
-- vocabulario de una LISTA comun queda cerrado para todas las corredoras. Es
-- lo que hace comparables dos propiedades de dos carteras distintas, que es
-- exactamente lo que `tg_catalogo_no_sombrea` (V55) existe para defender.
-- Anadir ambito despues es aditivo; quitarlo obligaria a decidir que se hace
-- con los valores ya escritos.
-- ---------------------------------------------------------------------
CREATE TABLE catalogo_atributo_opcion (
    id_catalogo_atributo BIGINT       NOT NULL
        REFERENCES catalogo_atributo (id_catalogo_atributo) ON DELETE CASCADE,
    valor                VARCHAR(40)  NOT NULL,
    rotulo               VARCHAR(120) NOT NULL,
    orden                INTEGER      NOT NULL DEFAULT 100,
    activo               BOOLEAN      NOT NULL DEFAULT true,
    PRIMARY KEY (id_catalogo_atributo, valor)
);

COMMENT ON TABLE catalogo_atributo_opcion IS
    'Los valores que admite una LISTA o una LISTA_MULTIPLE, con su rotulo. Sin '
    'esto una LISTA es texto libre y dos propiedades dejan de poder compararse.';

-- ---------------------------------------------------------------------
-- 4. El valor: fecha, moneda y multivalor.
--
-- `ck_atributo_un_valor` era `= 1` -- EXACTAMENTE uno -- y rompia por los dos
-- lados: con `valor_moneda` dentro del conteo, un importe con cifra y moneda
-- contaria 2 y todos los importes se rechazarian; y una fila ancla de
-- LISTA_MULTIPLE, que no lleva ningun escalar, contaria 0.
--
-- Se relaja a `<= 1` y la garantia de "exactamente uno, salvo multivalor"
-- pasa al trigger, que SI puede consultar el tipo declarado. La moneda queda
-- FUERA del conteo porque no es un valor: es la unidad de otro.
-- ---------------------------------------------------------------------
ALTER TABLE atributo_propiedad
    ADD COLUMN valor_fecha  DATE,
    ADD COLUMN valor_moneda VARCHAR(3);

ALTER TABLE atributo_propiedad DROP CONSTRAINT ck_atributo_un_valor;
ALTER TABLE atributo_propiedad
    ADD CONSTRAINT ck_atributo_un_valor
        CHECK (num_nonnulls(valor_texto, valor_numero, valor_booleano, valor_fecha) <= 1),
    -- Una moneda sin monto es una unidad sin nada que medir.
    ADD CONSTRAINT ck_atributo_moneda_con_monto
        CHECK (valor_moneda IS NULL OR valor_numero IS NOT NULL),
    -- El mismo vocabulario que las otras once columnas de moneda del esquema.
    ADD CONSTRAINT ck_atributo_moneda
        CHECK (valor_moneda IS NULL OR valor_moneda IN ('PEN', 'USD'));

-- La FK compuesta de tenant que `atributo_propiedad` nunca tuvo, y que la
-- tabla hija necesita para no poder apuntar al valor de otra corredora.
ALTER TABLE atributo_propiedad
    ADD CONSTRAINT uq_atributo_propiedad_org UNIQUE (organizacion_id, id_atributo_propiedad);

CREATE TABLE atributo_propiedad_opcion (
    organizacion_id       BIGINT      NOT NULL REFERENCES organizacion (id_organizacion),
    id_atributo_propiedad BIGINT      NOT NULL,
    valor                 VARCHAR(40) NOT NULL,
    PRIMARY KEY (id_atributo_propiedad, valor),
    CONSTRAINT fk_atributo_opcion_org
        FOREIGN KEY (organizacion_id, id_atributo_propiedad)
        REFERENCES atributo_propiedad (organizacion_id, id_atributo_propiedad)
        ON DELETE CASCADE
);

COMMENT ON TABLE atributo_propiedad_opcion IS
    'Los N valores de una LISTA_MULTIPLE. Cuelgan de una fila ancla en '
    'atributo_propiedad --que dice "esta clave esta respondida"-- para no tener '
    'que retirar uq_atributo_propiedad_clave, que es el indice sobre el que V71 '
    'apoyo su propia justificacion al borrar la tabla espejo.';

CREATE INDEX ix_atributo_opcion_valor ON atributo_propiedad_opcion (valor);

-- ---------------------------------------------------------------------
-- 5. Tres niveles de exigencia, no dos.
--
--   ALT  bloquea el alta
--   PUB  puede faltar al registrar, pero bloquea publicar
--   OPC  util, no bloquea
--
-- El booleano `requerido` solo sabia decir "bloquea el alta", y por eso la
-- auditoria acabo degradando a opcional una docena de datos que en realidad
-- son imprescindibles para ANUNCIAR. Obligar al corredor a saberlo todo en la
-- primera conversacion es como se consigue que invente.
-- ---------------------------------------------------------------------
ALTER TABLE catalogo_atributo_tipo ADD COLUMN exigencia VARCHAR(3);

UPDATE catalogo_atributo_tipo
   SET exigencia = CASE WHEN requerido THEN 'ALT' ELSE 'OPC' END;

-- Una clave con `aplica_todos` no tenia filas aqui, asi que no tendria donde
-- declarar su exigencia. Se materializan sus siete: no es sembrar una clave
-- nueva --la aplicabilidad ya existia, implicita en la bandera-- es escribir
-- donde se pueda leer lo que hasta ahora se deducia.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'), ('O'), ('D'), ('C'), ('T'), ('A'), ('X')) AS t(tipo)
 WHERE c.aplica_todos
   AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo x
                    WHERE x.id_catalogo_atributo = c.id_catalogo_atributo
                      AND x.tipo_propiedad = t.tipo);

ALTER TABLE catalogo_atributo_tipo
    ALTER COLUMN exigencia SET NOT NULL,
    ADD CONSTRAINT ck_catalogo_exigencia CHECK (exigencia IN ('ALT', 'PUB', 'OPC'));

-- ---------------------------------------------------------------------
-- 6. La guarda de la conversion.
--
-- Se compara el ANTES contra el DESPUES en vez de contra un numero escrito a
-- mano, y es deliberado: la auditoria afirma que "solo cuatro filas estan
-- marcadas requeridas" y en la base viva son DIEZ. Una guarda con la cifra
-- literal habria abortado esta migracion -- o peor, alguien la habria
-- "arreglado" bajando el numero hasta que pasara.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    obligatorias_antes  INTEGER;
    obligatorias_ahora  INTEGER;
    sin_exigencia       INTEGER;
BEGIN
    SELECT count(*) INTO obligatorias_antes FROM catalogo_atributo_tipo WHERE requerido;
    SELECT count(*) INTO obligatorias_ahora FROM catalogo_atributo_tipo WHERE exigencia = 'ALT';

    IF obligatorias_antes <> obligatorias_ahora THEN
        RAISE EXCEPTION
            'V72: habia % filas requeridas y quedaron % con exigencia ALT. La conversion perdio o invento obligatoriedad.',
            obligatorias_antes, obligatorias_ahora;
    END IF;

    -- Ninguna clave activa puede quedarse sin poder declarar su exigencia.
    SELECT count(*) INTO sin_exigencia
      FROM catalogo_atributo c
     WHERE c.activo
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = c.id_catalogo_atributo);

    IF sin_exigencia > 0 THEN
        RAISE EXCEPTION
            'V72: % clave(s) activas no tienen ninguna fila de aplicabilidad, asi que no pueden declarar exigencia.',
            sin_exigencia;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 7. El trigger, reescrito con un ELSE QUE GRITA.
--
-- LA RAZON, y es la mas importante de esta migracion: la cadena IF/ELSIF que
-- habia cubria ENTERO/DECIMAL, BOOLEANO y TEXTO/LISTA. Un tipo que no
-- apareciera ahi NO caia en un error: caia en el ELSE implicito y la fila se
-- aceptaba con cualquier columna rellena, o con ninguna.
--
-- Ni javac, ni Hibernate, ni ArchUnit, ni `ddl-auto: validate` leen un cuerpo
-- PL/pgSQL. Es exactamente el fallo de V40 que dejo
-- `exigir_administrador_operativo` comparando contra un vocabulario que ya no
-- existia y tumbo el enrolamiento MFA entero con la build en verde.
--
-- Con CASE ... ELSE RAISE, anadir un noveno tipo de dato sin ensenarle al
-- trigger donde vive su valor deja de ser un fallo silencioso y pasa a ser un
-- error en la primera escritura.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_atributo_gobernado() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    cat       record;
    tipo_prop varchar(1);
    escalares integer;
BEGIN
    SELECT * INTO cat
      FROM catalogo_atributo c
     WHERE c.clave = NEW.clave
       AND c.activo = true
       AND (c.organizacion_id = NEW.organizacion_id OR c.organizacion_id IS NULL)
     ORDER BY c.organizacion_id NULLS LAST
     LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El atributo "%" no esta en el catalogo', NEW.clave
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    SELECT tipo_inmueble INTO tipo_prop FROM propiedad WHERE id_propiedad = NEW.id_propiedad;

    IF NOT cat.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = cat.id_catalogo_atributo
                          AND t.tipo_propiedad = tipo_prop) THEN
        RAISE EXCEPTION 'El atributo "%" no aplica a una propiedad de tipo %', NEW.clave, tipo_prop
            USING ERRCODE = 'check_violation';
    END IF;

    escalares := num_nonnulls(NEW.valor_texto, NEW.valor_numero,
                              NEW.valor_booleano, NEW.valor_fecha);

    -- El valor tiene que estar en la columna que corresponde a su tipo, y en
    -- NINGUNA otra. El ELSE es la parte que importa.
    CASE cat.tipo_dato
        WHEN 'ENTERO', 'DECIMAL' THEN
            IF NEW.valor_numero IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es numerico y llego sin valor_numero', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        WHEN 'IMPORTE' THEN
            IF NEW.valor_numero IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es un importe y llego sin monto', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
            IF NEW.valor_moneda IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es un importe y llego sin moneda: un numero sin moneda no es un importe', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        WHEN 'BOOLEANO' THEN
            IF NEW.valor_booleano IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es booleano y llego sin valor_booleano', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        WHEN 'FECHA' THEN
            IF NEW.valor_fecha IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es una fecha y llego sin valor_fecha', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        WHEN 'TEXTO', 'LISTA' THEN
            IF NEW.valor_texto IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es de texto y llego sin valor_texto', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        WHEN 'LISTA_MULTIPLE' THEN
            -- La fila ancla NO lleva escalar: sus valores viven en la tabla
            -- hija. Decir "esta clave esta respondida" es todo su trabajo.
            IF escalares > 0 THEN
                RAISE EXCEPTION 'El atributo "%" es multivalor: sus valores van en atributo_propiedad_opcion, no en la fila', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        ELSE
            RAISE EXCEPTION 'El tipo de dato "%" del atributo "%" no tiene regla de almacenamiento en este trigger. Anadir un tipo al catalogo sin ensenarle aqui donde vive su valor deja pasar cualquier fila.',
                cat.tipo_dato, NEW.clave
                USING ERRCODE = 'check_violation';
    END CASE;

    -- Un solo escalar, salvo el multivalor que no lleva ninguno. El CHECK de
    -- la tabla solo puede decir "como mucho uno"; el "exactamente uno" necesita
    -- saber el tipo declarado, y eso solo se sabe aqui.
    IF cat.tipo_dato <> 'LISTA_MULTIPLE' AND escalares <> 1 THEN
        RAISE EXCEPTION 'El atributo "%" tiene % valores escalares y tiene que tener exactamente uno', NEW.clave, escalares
            USING ERRCODE = 'check_violation';
    END IF;

    -- La moneda solo la lleva un importe.
    IF cat.tipo_dato <> 'IMPORTE' AND NEW.valor_moneda IS NOT NULL THEN
        RAISE EXCEPTION 'El atributo "%" no es un importe y llego con moneda', NEW.clave
            USING ERRCODE = 'check_violation';
    END IF;

    -- Pertenencia al vocabulario declarado. Sin esto una LISTA es texto libre.
    IF cat.tipo_dato = 'LISTA' AND NEW.valor_texto IS NOT NULL
       AND EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                    WHERE o.id_catalogo_atributo = cat.id_catalogo_atributo)
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = cat.id_catalogo_atributo
                          AND o.valor = NEW.valor_texto
                          AND o.activo) THEN
        RAISE EXCEPTION 'El atributo "%" no admite el valor "%": no esta en su vocabulario', NEW.clave, NEW.valor_texto
            USING ERRCODE = 'check_violation';
    END IF;

    -- V62: el rango que antes vivia en un CHECK de la columna espejo.
    IF cat.valor_minimo IS NOT NULL AND NEW.valor_numero IS NOT NULL
       AND NEW.valor_numero < cat.valor_minimo THEN
        RAISE EXCEPTION 'El atributo "%" no puede ser menor que % y llego %',
            NEW.clave, cat.valor_minimo, NEW.valor_numero
            USING ERRCODE = 'check_violation';
    END IF;

    IF cat.valor_maximo IS NOT NULL AND NEW.valor_numero IS NOT NULL
       AND NEW.valor_numero > cat.valor_maximo THEN
        RAISE EXCEPTION 'El atributo "%" no puede ser mayor que % y llego %',
            NEW.clave, cat.valor_maximo, NEW.valor_numero
            USING ERRCODE = 'check_violation';
    END IF;

    -- La garantia que se perdio con el VARCHAR(120) del rubro (V71).
    IF cat.longitud_maxima IS NOT NULL AND NEW.valor_texto IS NOT NULL
       AND length(NEW.valor_texto) > cat.longitud_maxima THEN
        RAISE EXCEPTION 'El atributo "%" admite % caracteres y llegaron %',
            NEW.clave, cat.longitud_maxima, length(NEW.valor_texto)
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------
-- 8. El vocabulario de un valor multiple, tambien vigilado.
--
-- La tabla hija no pasa por el trigger de arriba, asi que necesita el suyo: un
-- valor que no este en el catalogo de opciones es exactamente el texto libre
-- que 0B viene a impedir.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_opcion_gobernada() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    cat record;
BEGIN
    SELECT c.* INTO cat
      FROM atributo_propiedad a
      JOIN catalogo_atributo c ON c.clave = a.clave
                              AND c.activo = true
                              AND (c.organizacion_id = a.organizacion_id
                                   OR c.organizacion_id IS NULL)
     WHERE a.id_atributo_propiedad = NEW.id_atributo_propiedad
     ORDER BY c.organizacion_id NULLS LAST
     LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La opcion cuelga de un atributo que no esta en el catalogo'
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF cat.tipo_dato <> 'LISTA_MULTIPLE' THEN
        RAISE EXCEPTION 'El atributo "%" no es multivalor y no puede tener opciones sueltas', cat.clave
            USING ERRCODE = 'check_violation';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                    WHERE o.id_catalogo_atributo = cat.id_catalogo_atributo
                      AND o.valor = NEW.valor
                      AND o.activo) THEN
        RAISE EXCEPTION 'El atributo "%" no admite el valor "%": no esta en su vocabulario', cat.clave, NEW.valor
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_opcion_gobernada
    BEFORE INSERT OR UPDATE ON atributo_propiedad_opcion
    FOR EACH ROW EXECUTE FUNCTION exigir_opcion_gobernada();

-- ---------------------------------------------------------------------
-- 9. Lo que esta migracion NO hace, dicho para que no se busque.
--
--   * No siembra ninguna clave. Ni una. Eso es el corte siguiente.
--   * No reclasifica ninguna de las 19 existentes: `banos` sigue siendo
--     DECIMAL y `zonificacion` sigue siendo TEXTO. Reclasificar exige tocar
--     `tg_catalogo_sistema_inmutable` (V55), que prohibe cambiar el tipo_dato
--     de una clave del sistema, y eso viaja con la siembra.
--   * No declara `sujeto` ni la aplicabilidad por (tipo, operacion). Es 0C.
--   * No marca ninguna clave como PUB todavia: la capacidad existe, y quien
--     la use llega con su gate de publicacion en el mismo corte.
-- ---------------------------------------------------------------------
