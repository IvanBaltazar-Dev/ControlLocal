-- =====================================================================
-- V73 - Corte 0C: el ENCARGO pasa a ser sujeto gobernado.
--
-- LA PREGUNTA QUE FALTABA
-- D-E4-3 respondio DONDE vive cada dato. Faltaba lo que va antes: DE QUIEN es.
-- El catalogo presuponia una sola respuesta --`atributo -> Propiedad`-- porque
-- `catalogo_atributo_tipo` solo se mapea contra `tipo_propiedad` y
-- `atributo_propiedad` cuelga de `id_propiedad`. Por construccion, todo
-- atributo gobernado era un hecho de la cosa fisica.
--
-- LO QUE DEMUESTRA QUE ES INSUFICIENTE
-- `amoblado` esta declarado hoy como atributo de la PROPIEDAD. Pero una
-- vivienda puede tener muebles y, con los MISMOS muebles:
--   * venderse sin ellos;
--   * alquilarse amoblada;
--   * tener dos encargos en momentos distintos con condiciones distintas.
-- La tercera historia es irrepresentable con un solo sujeto: el dato se
-- sobrescribe. Y hay una familia entera sin domicilio -- garantia, adelanto,
-- plazo minimo, disponible desde, mascotas aceptadas, se ofrece amoblado --
-- que son condiciones de UNA comercializacion concreta.
--
-- LA REGLA QUE CONGELA
--   clave -> vocabulario -> SUJETO -> autoridad -> mecanismo
--
--   sujeto=PROPIEDAD  ->  catalogo_atributo_tipo       ->  atributo_propiedad
--   sujeto=ENCARGO    ->  catalogo_atributo_operacion  ->  atributo_encargo
--                         nunca en las dos
--
-- SIN FK POLIMORFICA. Nada de `tipo_sujeto` + `id_sujeto`: esa forma parece
-- que ahorra una tabla y lo que hace es renunciar a la integridad referencial.
-- Dos persistencias explicitas, cada una con su FK real, compartiendo UN solo
-- catalogo que declara de quien es cada clave.
--
-- Y EL VALOR CUELGA DEL ENCARGO, NO DE LA OPERACION. Dos alquileres sucesivos
-- de la misma propiedad son DOS episodios: comparten operacion y no comparten
-- nada mas. Colgar el valor de (propiedad, operacion) haria que el alquiler de
-- 2026 heredara la garantia pactada en 2024, en silencio.
--
-- ESTA MIGRACION NO SIEMBRA NINGUNA CLAVE COMERCIAL. Solo abre el sitio. La
-- siembra va detras, igual que 0B fue capacidad antes que vocabulario.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. De quien es cada clave.
--
-- Default PROPIEDAD porque es lo que las 19 existentes son de verdad: el
-- catalogo nacio suponiendolo y ninguna cambia de dueno en esta migracion.
-- Lo que cambia es que a partir de aqui hay que DECLARARLO.
-- ---------------------------------------------------------------------
ALTER TABLE catalogo_atributo
    ADD COLUMN sujeto VARCHAR(10) NOT NULL DEFAULT 'PROPIEDAD';

ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_sujeto CHECK (sujeto IN ('PROPIEDAD', 'ENCARGO'));

COMMENT ON COLUMN catalogo_atributo.sujeto IS
    'De quien es el dato. PROPIEDAD = hecho de la cosa fisica, sobrevive al '
    'encargo. ENCARGO = condicion de una comercializacion concreta, muere con '
    'ella. La regla del reparto: si al firmar el siguiente alquiler el dato '
    'puede cambiar sin que la propiedad haya cambiado, es del ENCARGO.';

-- ---------------------------------------------------------------------
-- 2. La aplicabilidad del encargo, que es por (tipo, operacion).
--
-- No basta el tipo, y esto es lo que la tabla por tipo no podia expresar:
-- `partida_registral` bloquea una VENTA y es irrelevante en un ALQUILER;
-- `garantia_meses` es al reves. Hoy eso obliga a degradar a opcional cosas que
-- en venta son imprescindibles.
--
-- Misma forma exacta que `catalogo_atributo_tipo` (V48): PK compuesta, sin id
-- propio, FK por `id_catalogo_atributo` y no por `clave` --que solo es unica
-- POR ORGANIZACION-- y ON DELETE CASCADE. Una fila de aplicabilidad no tiene
-- identidad: es una fila DE un atributo.
-- ---------------------------------------------------------------------
CREATE TABLE catalogo_atributo_operacion (
    id_catalogo_atributo BIGINT      NOT NULL
        REFERENCES catalogo_atributo (id_catalogo_atributo) ON DELETE CASCADE,
    tipo_propiedad       VARCHAR(1)  NOT NULL,
    tipo_operacion       VARCHAR(1)  NOT NULL,
    exigencia            VARCHAR(3)  NOT NULL DEFAULT 'OPC',
    PRIMARY KEY (id_catalogo_atributo, tipo_propiedad, tipo_operacion),
    CONSTRAINT ck_catalogo_operacion_tipo
        CHECK (tipo_propiedad IN ('L', 'O', 'D', 'C', 'T', 'A', 'X')),
    -- El mismo vocabulario que `captacion.motivo_operacion` (V17): A alquiler,
    -- V venta. Inventar otro aqui seria la segunda lista de operaciones.
    CONSTRAINT ck_catalogo_operacion_operacion
        CHECK (tipo_operacion IN ('A', 'V')),
    CONSTRAINT ck_catalogo_operacion_exigencia
        CHECK (exigencia IN ('ALT', 'PUB', 'OPC'))
);

COMMENT ON TABLE catalogo_atributo_operacion IS
    'A que (tipo de propiedad, operacion) aplica una clave del ENCARGO, y cuanto '
    'hace falta ahi. La aplicabilidad comercial depende de las dos cosas.';

-- ---------------------------------------------------------------------
-- 3. Donde vive el valor de una condicion comercial.
--
-- Cuelga de `id_captacion` -- el encargo CONCRETO -- y no de la operacion. Es
-- la decision central de este corte: `uq_captacion_viva_por_operacion` (V50)
-- prohibe dos encargos VIVOS de la misma operacion, no que hayan EXISTIDO
-- varios. Una propiedad con tres alquileres a lo largo del tiempo tiene tres
-- garantias distintas, y ninguna debe pisar a otra.
--
-- FK compuesta a `uq_captacion_org` (V6) porque el gate del discriminador de
-- tenant lo exige: sin ella un valor podria apuntar al encargo de otra
-- corredora.
--
-- Mismas columnas de valor que `atributo_propiedad` tras V72, y por la misma
-- razon: un tipo de dato significa lo mismo lo lleve quien lo lleve.
-- ---------------------------------------------------------------------
CREATE TABLE atributo_encargo (
    id_atributo_encargo BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    organizacion_id     BIGINT        NOT NULL REFERENCES organizacion (id_organizacion),
    id_captacion        BIGINT        NOT NULL,
    clave               VARCHAR(60)   NOT NULL,
    valor_texto         TEXT,
    valor_numero        NUMERIC(14,4),
    valor_booleano      BOOLEAN,
    valor_fecha         DATE,
    valor_moneda        VARCHAR(3),
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMPTZ,

    CONSTRAINT fk_atributo_encargo_org
        FOREIGN KEY (organizacion_id, id_captacion)
        REFERENCES captacion (organizacion_id, id_captacion),
    CONSTRAINT ck_atributo_encargo_un_valor
        CHECK (num_nonnulls(valor_texto, valor_numero, valor_booleano, valor_fecha) <= 1),
    CONSTRAINT ck_atributo_encargo_moneda_con_monto
        CHECK (valor_moneda IS NULL OR valor_numero IS NOT NULL),
    CONSTRAINT ck_atributo_encargo_moneda
        CHECK (valor_moneda IS NULL OR valor_moneda IN ('PEN', 'USD'))
);

COMMENT ON TABLE atributo_encargo IS
    'Las condiciones de UNA comercializacion concreta. Cuelgan del encargo y no '
    'de la operacion: dos alquileres sucesivos de la misma propiedad son dos '
    'episodios y jamas comparten condiciones.';

CREATE UNIQUE INDEX uq_atributo_encargo_clave ON atributo_encargo (id_captacion, clave);
CREATE INDEX ix_atributo_encargo_organizacion ON atributo_encargo (organizacion_id);
CREATE INDEX ix_atributo_encargo_clave_numero
    ON atributo_encargo (organizacion_id, clave, valor_numero)
 WHERE valor_numero IS NOT NULL;

-- La FK compuesta que necesita la tabla de valores multiples, igual que en
-- `atributo_propiedad` (V72).
ALTER TABLE atributo_encargo
    ADD CONSTRAINT uq_atributo_encargo_org UNIQUE (organizacion_id, id_atributo_encargo);

CREATE TABLE atributo_encargo_opcion (
    organizacion_id     BIGINT      NOT NULL REFERENCES organizacion (id_organizacion),
    id_atributo_encargo BIGINT      NOT NULL,
    valor               VARCHAR(40) NOT NULL,
    PRIMARY KEY (id_atributo_encargo, valor),
    CONSTRAINT fk_atributo_encargo_opcion_org
        FOREIGN KEY (organizacion_id, id_atributo_encargo)
        REFERENCES atributo_encargo (organizacion_id, id_atributo_encargo)
        ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 4. El trigger del encargo.
--
-- Es el gemelo de `exigir_atributo_gobernado`, y tiene que serlo: si el
-- enrutamiento es simetrico, la validacion tambien. Las diferencias son dos y
-- las dos importan:
--
--   * comprueba `sujeto = 'ENCARGO'`, o sea que rechaza guardar aqui un hecho
--     fisico;
--   * la aplicabilidad la mira contra (tipo de la propiedad del encargo,
--     operacion del encargo), no solo contra el tipo.
--
-- Y conserva el CASE con ELSE que lanza, por la misma razon que V72: un tipo
-- de dato nuevo sin regla no puede colarse por una salida por defecto.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_atributo_de_encargo() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    cat       record;
    tipo_prop varchar(1);
    operacion varchar(1);
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

    IF cat.sujeto <> 'ENCARGO' THEN
        RAISE EXCEPTION 'El atributo "%" es de la PROPIEDAD y no puede colgar de un encargo: un hecho fisico sobrevive al encargo', NEW.clave
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT p.tipo_inmueble, cap.motivo_operacion INTO tipo_prop, operacion
      FROM captacion cap JOIN propiedad p ON p.id_propiedad = cap.id_propiedad
     WHERE cap.id_captacion = NEW.id_captacion;

    IF NOT cat.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                        WHERE o.id_catalogo_atributo = cat.id_catalogo_atributo
                          AND o.tipo_propiedad = tipo_prop
                          AND o.tipo_operacion = operacion) THEN
        RAISE EXCEPTION 'El atributo "%" no aplica a un encargo de operacion % sobre una propiedad de tipo %', NEW.clave, operacion, tipo_prop
            USING ERRCODE = 'check_violation';
    END IF;

    escalares := num_nonnulls(NEW.valor_texto, NEW.valor_numero,
                              NEW.valor_booleano, NEW.valor_fecha);

    CASE cat.tipo_dato
        WHEN 'ENTERO', 'DECIMAL' THEN
            IF NEW.valor_numero IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es numerico y llego sin valor_numero', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        WHEN 'IMPORTE' THEN
            IF NEW.valor_numero IS NULL OR NEW.valor_moneda IS NULL THEN
                RAISE EXCEPTION 'El atributo "%" es un importe: sin monto y moneda no es dinero', NEW.clave
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
            IF escalares > 0 THEN
                RAISE EXCEPTION 'El atributo "%" es multivalor: sus valores van en atributo_encargo_opcion', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        ELSE
            RAISE EXCEPTION 'El tipo de dato "%" del atributo "%" no tiene regla de almacenamiento en este trigger.',
                cat.tipo_dato, NEW.clave
                USING ERRCODE = 'check_violation';
    END CASE;

    IF cat.tipo_dato <> 'LISTA_MULTIPLE' AND escalares <> 1 THEN
        RAISE EXCEPTION 'El atributo "%" tiene % valores escalares y tiene que tener exactamente uno', NEW.clave, escalares
            USING ERRCODE = 'check_violation';
    END IF;

    IF cat.tipo_dato = 'LISTA' AND NEW.valor_texto IS NOT NULL
       AND EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                    WHERE o.id_catalogo_atributo = cat.id_catalogo_atributo)
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = cat.id_catalogo_atributo
                          AND o.valor = NEW.valor_texto AND o.activo) THEN
        RAISE EXCEPTION 'El atributo "%" no admite el valor "%": no esta en su vocabulario', NEW.clave, NEW.valor_texto
            USING ERRCODE = 'check_violation';
    END IF;

    IF cat.valor_minimo IS NOT NULL AND NEW.valor_numero IS NOT NULL
       AND NEW.valor_numero < cat.valor_minimo THEN
        RAISE EXCEPTION 'El atributo "%" no puede ser menor que %', NEW.clave, cat.valor_minimo
            USING ERRCODE = 'check_violation';
    END IF;

    IF cat.valor_maximo IS NOT NULL AND NEW.valor_numero IS NOT NULL
       AND NEW.valor_numero > cat.valor_maximo THEN
        RAISE EXCEPTION 'El atributo "%" no puede ser mayor que %', NEW.clave, cat.valor_maximo
            USING ERRCODE = 'check_violation';
    END IF;

    IF cat.longitud_maxima IS NOT NULL AND NEW.valor_texto IS NOT NULL
       AND length(NEW.valor_texto) > cat.longitud_maxima THEN
        RAISE EXCEPTION 'El atributo "%" admite % caracteres y llegaron %',
            NEW.clave, cat.longitud_maxima, length(NEW.valor_texto)
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_atributo_de_encargo
    BEFORE INSERT OR UPDATE ON atributo_encargo
    FOR EACH ROW EXECUTE FUNCTION exigir_atributo_de_encargo();

-- ---------------------------------------------------------------------
-- 5. La direccion contraria, en el trigger que ya existia.
--
-- Sin esto el enrutamiento seria simetrico solo de un lado: `atributo_encargo`
-- rechazaria un hecho fisico, y `atributo_propiedad` seguiria aceptando una
-- condicion comercial sin decir nada. Un dato en el sujeto equivocado no falla:
-- MIENTE, y lo hace hasta que alguien compare dos encargos y no entienda por
-- que dicen lo mismo.
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

    -- V73: la mitad que faltaba de la regla del sujeto.
    IF cat.sujeto <> 'PROPIEDAD' THEN
        RAISE EXCEPTION 'El atributo "%" es del ENCARGO y no puede colgar de la propiedad: una condicion negociada muere con su encargo', NEW.clave
            USING ERRCODE = 'check_violation';
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
            IF escalares > 0 THEN
                RAISE EXCEPTION 'El atributo "%" es multivalor: sus valores van en atributo_propiedad_opcion, no en la fila', NEW.clave
                    USING ERRCODE = 'check_violation';
            END IF;
        ELSE
            RAISE EXCEPTION 'El tipo de dato "%" del atributo "%" no tiene regla de almacenamiento en este trigger. Anadir un tipo al catalogo sin ensenarle aqui donde vive su valor deja pasar cualquier fila.',
                cat.tipo_dato, NEW.clave
                USING ERRCODE = 'check_violation';
    END CASE;

    IF cat.tipo_dato <> 'LISTA_MULTIPLE' AND escalares <> 1 THEN
        RAISE EXCEPTION 'El atributo "%" tiene % valores escalares y tiene que tener exactamente uno', NEW.clave, escalares
            USING ERRCODE = 'check_violation';
    END IF;

    IF cat.tipo_dato <> 'IMPORTE' AND NEW.valor_moneda IS NOT NULL THEN
        RAISE EXCEPTION 'El atributo "%" no es un importe y llego con moneda', NEW.clave
            USING ERRCODE = 'check_violation';
    END IF;

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
-- 6. El vocabulario del multivalor de encargo, tambien vigilado.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_opcion_de_encargo() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    cat record;
BEGIN
    SELECT c.* INTO cat
      FROM atributo_encargo a
      JOIN catalogo_atributo c ON c.clave = a.clave
                              AND c.activo = true
                              AND (c.organizacion_id = a.organizacion_id
                                   OR c.organizacion_id IS NULL)
     WHERE a.id_atributo_encargo = NEW.id_atributo_encargo
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
                      AND o.valor = NEW.valor AND o.activo) THEN
        RAISE EXCEPTION 'El atributo "%" no admite el valor "%": no esta en su vocabulario', cat.clave, NEW.valor
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_opcion_de_encargo
    BEFORE INSERT OR UPDATE ON atributo_encargo_opcion
    FOR EACH ROW EXECUTE FUNCTION exigir_opcion_de_encargo();

-- ---------------------------------------------------------------------
-- 7. La guarda: ninguna clave existente cambia de dueno aqui.
--
-- Las 19 nacieron suponiendo PROPIEDAD y siguen siendo suyas. `amoblado` es el
-- caso que la auditoria usa para explicar por que hace falta un segundo sujeto,
-- pero reclasificarlo es SIEMBRA: exige decidir que pasa con los valores ya
-- escritos, y eso no se hace de paso en la migracion que abre el sitio.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    mudadas INTEGER;
BEGIN
    SELECT count(*) INTO mudadas FROM catalogo_atributo WHERE sujeto <> 'PROPIEDAD';
    IF mudadas > 0 THEN
        RAISE EXCEPTION
            'V73: % clave(s) cambiaron de sujeto en la migracion que solo abre el sitio. Reclasificar exige decidir que pasa con los valores ya escritos.',
            mudadas;
    END IF;
END $$;
