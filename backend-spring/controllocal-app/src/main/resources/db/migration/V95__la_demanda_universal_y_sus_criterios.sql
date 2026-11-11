-- =====================================================================
-- V95 - La demanda deja de hablar solo alquiler comercial.
--
-- Cierra el Corte 7 del lado del ESQUEMA (D-M-1 .. D-M-4). Lo que el
-- modelo universal declaraba pendiente desde el principio:
--
--     "El requerimiento habla alquiler comercial (renta_min/max, rubro
--      obligatorio, un solo tipo_inmueble)"
--     docs/ai/decision-modelo-universal-propiedad-operacion.md, seccion 2
--
-- Y lo que la auditoria de profundidad midio como sesgo del matcher: el
-- requerimiento y la propiedad hablaban VOCABULARIOS DISTINTOS -- la
-- demanda decia LOCAL_COMERCIAL / DEPOSITO_ALMACEN y la propiedad L / A --,
-- asi que ALMACEN y DEPOSITO_ALMACEN, que son el mismo concepto, se
-- declaraban no comparables.
--
-- QUE HACE, EN UNA LINEA POR PIEZA
--   1. `operacion`      el requerimiento declara VENTA o ALQUILER
--   2. `presupuesto_*`  el importe deja de llamarse renta
--   3. `requerimiento_tipo_inmueble`  varios tipos, en el vocabulario de
--                       UN CARACTER que ya usa la propiedad
--   4. `requerimiento_criterio`       el requerimiento puede pedir
--                       atributos gobernados, con peso INDISPENSABLE o
--                       DESEABLE
--   5. `vigente_hasta` / `fecha_revalidacion`  cuanto vale lo declarado
--
-- QUE NO HACE, Y ES LO MAS IMPORTANTE
-- **No borra nada.** Ni una columna, ni una fila, ni un valor. Las cuatro
-- columnas del modelo viejo -- `rubro`, `tipo_inmueble`, `renta_min`,
-- `renta_max`, `frente_minimo` -- se quedan donde estan con su contenido
-- intacto y pasan a ser LINAJE: nadie las vuelve a escribir desde la
-- aplicacion (el mapeo JPA desaparece), y siguen ahi para poder responder
-- que decia exactamente el requerimiento antes de traducirse.
--
-- Esa es la constancia que exige el North Star: `STAND_MODULO` y `OTRO`
-- se traducen los dos a `X`, y sin la columna original no habria forma de
-- volver a distinguirlos.
--
-- LO QUE NO SE PUEDE TRADUCIR SE QUEDA FALTANTE
-- El `rubro` es texto libre. Cuando el catalogo tiene una clave que lo
-- gobierna (`rubro_permitido`) la traduccion es directa; si algun dia esa
-- clave pasa a LISTA_MULTIPLE con vocabulario cerrado, los rubros que no
-- esten en el vocabulario NO se inventan: se quedan en la columna de
-- linaje y el requerimiento no gana ese criterio. Esta migracion cuenta
-- cuantos traduce y cuantos no, y lo dice en el log de Flyway.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. El requerimiento declara de que operacion habla
--
-- Sin esto, "busco 180.000 USD" y "busco 2.900 USD" son el mismo dato con
-- distinta magnitud, y distinguir venta de alquiler por el tamano del
-- numero es exactamente lo que el modelo economico prohibe.
--
-- El backfill a 'A' no es una suposicion: la tabla entera se llamaba
-- `renta_min`/`renta_max` y su CHECK de tipo solo admitia los seis tipos
-- comerciales del alquiler. No habia forma de escribir una venta ahi.
-- ---------------------------------------------------------------------
ALTER TABLE requerimiento_cliente
    ADD COLUMN operacion          VARCHAR(1),
    ADD COLUMN presupuesto_min    NUMERIC(14, 2),
    ADD COLUMN presupuesto_max    NUMERIC(14, 2),
    ADD COLUMN vigente_hasta      DATE,
    ADD COLUMN fecha_revalidacion TIMESTAMPTZ;

-- La tabla inicial guardaba las palabras completas, pero el vocabulario
-- vigente de estados usa codigos unitarios como el resto de Core. Se migra
-- antes de validar la entidad JPA; no se descarta ninguna fila.
ALTER TABLE requerimiento_cliente
    DROP CONSTRAINT ck_requerimiento_estado,
    ALTER COLUMN estado DROP DEFAULT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM requerimiento_cliente
               WHERE estado NOT IN ('ACTIVO', 'PAUSADO', 'CERRADO', 'A', 'P', 'C')) THEN
        RAISE EXCEPTION 'V95: existe un estado de requerimiento no reconocido';
    END IF;
END $$;

UPDATE requerimiento_cliente
   SET estado = CASE estado
                    WHEN 'ACTIVO' THEN 'A'
                    WHEN 'PAUSADO' THEN 'P'
                    WHEN 'CERRADO' THEN 'C'
                    ELSE estado
                END;

ALTER TABLE requerimiento_cliente
    ALTER COLUMN estado TYPE VARCHAR(1) USING estado,
    ADD CONSTRAINT ck_requerimiento_estado CHECK (estado IN ('A', 'P', 'C'));

DO $$
DECLARE
    traducidos bigint;
    total      bigint;
    sin_importe bigint;
BEGIN
    SELECT count(*) INTO total FROM requerimiento_cliente;

    SELECT count(*) INTO sin_importe
      FROM requerimiento_cliente
     WHERE renta_min IS NULL AND renta_max IS NULL;

    UPDATE requerimiento_cliente
       SET operacion       = 'A',
           presupuesto_min = renta_min,
           presupuesto_max = renta_max
     WHERE operacion IS NULL;
    GET DIAGNOSTICS traducidos = ROW_COUNT;

    IF traducidos <> total THEN
        RAISE EXCEPTION 'V95: habia % requerimientos y se tradujeron %', total, traducidos;
    END IF;

    RAISE NOTICE 'V95 operacion+presupuesto -> traducidos=% (todos ALQUILER) sin_importe_declarado=%',
                 traducidos, sin_importe;
END $$;

ALTER TABLE requerimiento_cliente
    ALTER COLUMN operacion SET NOT NULL,
    -- El rubro deja de ser obligatorio: era el requisito que obligaba a
    -- inventar uno para poder registrar la busqueda de una vivienda.
    ALTER COLUMN rubro DROP NOT NULL,
    ADD CONSTRAINT ck_requerimiento_operacion CHECK (operacion IN ('A', 'V')),
    ADD CONSTRAINT ck_requerimiento_presupuesto CHECK (
        presupuesto_min IS NULL OR presupuesto_max IS NULL
            OR presupuesto_max >= presupuesto_min);

COMMENT ON COLUMN requerimiento_cliente.operacion IS
    'A alquiler, V venta. Sin DEFAULT: un requerimiento sin operacion no se puede cruzar con '
    'ninguna oferta, porque una compra no casa con un alquiler.';
COMMENT ON COLUMN requerimiento_cliente.presupuesto_min IS
    'Lo que el cliente esta dispuesto a pagar: renta mensual si operacion=A, precio si operacion=V.';
COMMENT ON COLUMN requerimiento_cliente.presupuesto_max IS
    'El techo del presupuesto. NULL = no lo declaro; no se infiere ninguno.';
COMMENT ON COLUMN requerimiento_cliente.vigente_hasta IS
    'Hasta cuando vale lo declarado. NULL es FALTANTE legitimo y NO saca al requerimiento del '
    'matcher: quien decide que entra al cruce sigue siendo `estado`.';
COMMENT ON COLUMN requerimiento_cliente.fecha_revalidacion IS
    'Cuando se confirmo por ultima vez que el cliente sigue buscando esto. La escribe '
    'POST /requerimientos/{id}/revalidar y nada mas.';

-- Las cuatro del modelo viejo: se conservan y dejan de escribirse.
COMMENT ON COLUMN requerimiento_cliente.renta_min IS
    'LINAJE (V95): lo que decia el requerimiento cuando la demanda solo sabia alquilar. La '
    'autoridad del importe es `presupuesto_min`; la aplicacion ya no escribe esta columna.';
COMMENT ON COLUMN requerimiento_cliente.renta_max IS
    'LINAJE (V95): ver renta_min. La autoridad es `presupuesto_max`.';
COMMENT ON COLUMN requerimiento_cliente.frente_minimo IS
    'LINAJE (V95): se tradujo a un criterio DESEABLE sobre la clave gobernada `frente`. La '
    'aplicacion ya no escribe esta columna.';
COMMENT ON COLUMN requerimiento_cliente.tipo_inmueble IS
    'LINAJE (V95): el UNICO tipo que admitia el modelo viejo, con el vocabulario viejo. La '
    'autoridad son las filas de `requerimiento_tipo_inmueble`. Se conserva porque STAND_MODULO '
    'y OTRO se traducen los dos a X y sin esto no se podrian volver a distinguir.';
COMMENT ON COLUMN requerimiento_cliente.rubro IS
    'La ETIQUETA del negocio del cliente ("Cafeteria"), que se sigue mostrando. Dejo de ser '
    'criterio de matching en V95: pedir un rubro es un criterio gobernado sobre '
    '`rubro_permitido`, con su peso, y vive en `requerimiento_criterio`.';


-- ---------------------------------------------------------------------
-- 2. Varios tipos, y en el MISMO vocabulario que la propiedad
--
-- El sesgo que esto cierra no es de puntaje sino de idioma: `mapTipo`
-- traducia tres de los seis nombres del enum viejo y devolvia NULL para
-- los otros tres, con lo que el criterio se caia de los aplicables. Un
-- cliente que buscaba un DEPOSITO_ALMACEN puntuaba igual contra un
-- almacen que contra una casa.
--
-- La traduccion, con su porque:
--   LOCAL_COMERCIAL   -> L   mismo concepto
--   OFICINA           -> O   mismo concepto
--   DEPOSITO_ALMACEN  -> A   mismo concepto, distinto nombre. ESTE es el
--                            que estaba roto
--   TERRENO_COMERCIAL -> T   el "comercial" describia el uso, no el tipo
--   OTRO              -> X   mismo concepto
--   STAND_MODULO      -> X   un stand no es ninguno de los siete tipos.
--                            No se inventa un octavo: cae en OTRO y la
--                            columna de linaje conserva que era un stand
-- ---------------------------------------------------------------------
CREATE TABLE requerimiento_tipo_inmueble (
    id_requerimiento BIGINT     NOT NULL REFERENCES requerimiento_cliente (id_requerimiento)
                                ON DELETE CASCADE,
    tipo_inmueble    VARCHAR(1) NOT NULL,
    PRIMARY KEY (id_requerimiento, tipo_inmueble),
    CONSTRAINT ck_requerimiento_tipo_codigo CHECK (
        tipo_inmueble IN ('L', 'O', 'A', 'D', 'C', 'T', 'E', 'X'))
);

COMMENT ON TABLE requerimiento_tipo_inmueble IS
    'Los tipos que el cliente acepta, en el vocabulario de un caracter de propiedad.tipo_inmueble. '
    'Sin tenant propio, igual que requerimiento_distrito: lo aporta el requerimiento.';

DO $$
DECLARE
    convertidos bigint;
    esperados   bigint;
BEGIN
    SELECT count(*) INTO esperados
      FROM requerimiento_cliente WHERE tipo_inmueble IS NOT NULL;

    INSERT INTO requerimiento_tipo_inmueble (id_requerimiento, tipo_inmueble)
    SELECT r.id_requerimiento,
           CASE r.tipo_inmueble
               WHEN 'LOCAL_COMERCIAL'   THEN 'L'
               WHEN 'OFICINA'           THEN 'O'
               WHEN 'DEPOSITO_ALMACEN'  THEN 'A'
               WHEN 'TERRENO_COMERCIAL' THEN 'T'
               WHEN 'STAND_MODULO'      THEN 'X'
               WHEN 'OTRO'              THEN 'X'
           END
      FROM requerimiento_cliente r
     WHERE r.tipo_inmueble IS NOT NULL
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS convertidos = ROW_COUNT;

    IF convertidos <> esperados THEN
        RAISE EXCEPTION 'V95: % requerimientos declaraban tipo y solo se tradujeron %',
                        esperados, convertidos;
    END IF;

    RAISE NOTICE 'V95 tipos -> traducidos=% (los que declaraban tipo)', convertidos;
END $$;


-- ---------------------------------------------------------------------
-- 3. El requerimiento puede pedir un atributo gobernado
--
-- Es la mitad que faltaba de "el requerimiento habla el mismo idioma"
-- (decision del modelo universal, seccion 3.4): si la propiedad dice
-- `carga_electrica_kw` y el requerimiento no puede pedirla, el matcher
-- puntua sobre seis criterios fijos y el resto de la ficha no sirve para
-- nada.
--
-- PESO
--   I  INDISPENSABLE  si no se cumple, DESCARTA
--   D  DESEABLE       si no se cumple, solo baja el puntaje
--
-- OPERADOR
--   I  igual          =           TEXTO, LISTA, numeros y fechas
--   M  minimo         >=          numeros y fechas
--   X  maximo         <=          numeros y fechas
--   E  entre          [a, b]      numeros y fechas
--   N  incluye        interseccion de conjuntos, o contencion de texto
--   V  verdadero      el hecho es cierto (o expresamente falso)
--
-- Sobre `V`: admite tambien el negativo declarado -- valor_booleano =
-- false-- porque "sin cargas ni gravamenes" es un requisito real, y
-- anadir un septimo operador solo para negar duplicaria el mecanismo.
-- ---------------------------------------------------------------------
CREATE TABLE requerimiento_criterio (
    id_criterio        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    organizacion_id    BIGINT       NOT NULL REFERENCES organizacion (id_organizacion),
    id_requerimiento   BIGINT       NOT NULL REFERENCES requerimiento_cliente (id_requerimiento)
                                    ON DELETE CASCADE,
    clave              VARCHAR(60)  NOT NULL,
    operador           VARCHAR(1)   NOT NULL,
    peso               VARCHAR(1)   NOT NULL,
    valor_texto        TEXT,
    valor_numero       NUMERIC(14, 4),
    valor_numero_hasta NUMERIC(14, 4),
    valor_moneda       VARCHAR(3),
    valor_booleano     BOOLEAN,
    valor_fecha        DATE,
    valor_fecha_hasta  DATE,
    fecha_creacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_criterio_operador CHECK (operador IN ('I', 'M', 'X', 'E', 'N', 'V')),
    CONSTRAINT ck_criterio_peso     CHECK (peso IN ('I', 'D')),
    CONSTRAINT ck_criterio_moneda   CHECK (valor_moneda IS NULL OR valor_moneda IN ('PEN', 'USD')),
    -- Un criterio por clave: "de 50 a 80" es ENTRE, no dos filas que el
    -- lector tendria que recomponer -- y recomponer es donde se pierde el
    -- sentido de dos filas contradictorias.
    CONSTRAINT uq_criterio_clave UNIQUE (id_requerimiento, clave),
    CONSTRAINT fk_criterio_requerimiento_org FOREIGN KEY (organizacion_id, id_requerimiento)
        REFERENCES requerimiento_cliente (organizacion_id, id_requerimiento),
    CONSTRAINT ck_criterio_rango CHECK (
        (valor_numero IS NULL OR valor_numero_hasta IS NULL OR valor_numero_hasta >= valor_numero)
        AND (valor_fecha IS NULL OR valor_fecha_hasta IS NULL OR valor_fecha_hasta >= valor_fecha)),
    -- La FORMA del valor depende del operador, y aqui es donde se
    -- comprueba: un ENTRE sin techo no es un rango, es un minimo mal
    -- escrito, y un VERDADERO sin booleano no dice nada.
    --
    -- Se escribe contando los escalares presentes -- y no con una cadena
    -- de `<>` -- porque un `(a IS NOT NULL) <> (b IS NOT NULL)` deja
    -- pasar una tercera columna llena sin decir nada: exige que a y b
    -- discrepen y no mira c. Contar dice exactamente lo que se quiere:
    -- "uno, y solo uno".
    CONSTRAINT ck_criterio_forma CHECK (
        CASE operador
            WHEN 'I' THEN (CASE WHEN valor_texto  IS NOT NULL THEN 1 ELSE 0 END
                         + CASE WHEN valor_numero IS NOT NULL THEN 1 ELSE 0 END
                         + CASE WHEN valor_fecha  IS NOT NULL THEN 1 ELSE 0 END) = 1
                          AND valor_booleano IS NULL
                          AND valor_numero_hasta IS NULL AND valor_fecha_hasta IS NULL
            WHEN 'M' THEN (CASE WHEN valor_numero IS NOT NULL THEN 1 ELSE 0 END
                         + CASE WHEN valor_fecha  IS NOT NULL THEN 1 ELSE 0 END) = 1
                          AND valor_texto IS NULL AND valor_booleano IS NULL
                          AND valor_numero_hasta IS NULL AND valor_fecha_hasta IS NULL
            WHEN 'X' THEN (CASE WHEN valor_numero IS NOT NULL THEN 1 ELSE 0 END
                         + CASE WHEN valor_fecha  IS NOT NULL THEN 1 ELSE 0 END) = 1
                          AND valor_texto IS NULL AND valor_booleano IS NULL
                          AND valor_numero_hasta IS NULL AND valor_fecha_hasta IS NULL
            WHEN 'E' THEN ((valor_numero IS NOT NULL AND valor_numero_hasta IS NOT NULL
                            AND valor_fecha IS NULL AND valor_fecha_hasta IS NULL)
                        OR (valor_fecha IS NOT NULL AND valor_fecha_hasta IS NOT NULL
                            AND valor_numero IS NULL AND valor_numero_hasta IS NULL))
                          AND valor_texto IS NULL AND valor_booleano IS NULL
            -- INCLUYE guarda sus valores en la tabla hija: aqui no hay escalar.
            WHEN 'N' THEN valor_texto IS NULL AND valor_numero IS NULL
                          AND valor_booleano IS NULL AND valor_fecha IS NULL
                          AND valor_numero_hasta IS NULL AND valor_fecha_hasta IS NULL
            WHEN 'V' THEN valor_booleano IS NOT NULL AND valor_texto IS NULL
                          AND valor_numero IS NULL AND valor_fecha IS NULL
                          AND valor_numero_hasta IS NULL AND valor_fecha_hasta IS NULL
            ELSE false
        END)
);

CREATE INDEX ix_criterio_requerimiento ON requerimiento_criterio (id_requerimiento);
CREATE INDEX ix_criterio_organizacion ON requerimiento_criterio (organizacion_id);

COMMENT ON TABLE requerimiento_criterio IS
    'Lo que el cliente PIDE sobre una clave gobernada, con su peso. Es lo que permite decir '
    '"encaja en 4 de 5, falta confirmar la potencia" en vez de un porcentaje sin explicacion.';
COMMENT ON COLUMN requerimiento_criterio.peso IS
    'I INDISPENSABLE (si no se cumple, descarta) o D DESEABLE (solo baja el puntaje).';

-- Los valores de un INCLUYE. Misma forma que catalogo_atributo_opcion y
-- por la misma razon: un valor pedido no tiene vida fuera de su criterio.
CREATE TABLE requerimiento_criterio_valor (
    id_criterio BIGINT       NOT NULL REFERENCES requerimiento_criterio (id_criterio)
                             ON DELETE CASCADE,
    valor       VARCHAR(120) NOT NULL,
    PRIMARY KEY (id_criterio, valor)
);

COMMENT ON TABLE requerimiento_criterio_valor IS
    'Los valores que admite un criterio INCLUYE. Un conjunto y no una cadena separada por comas: '
    '"agua, desague" y "agua desague" serian la misma cadena para cualquier comparacion.';


-- ---------------------------------------------------------------------
-- 3 bis. La clave de un criterio existe en el catalogo, y el operador
--        encaja con su tipo de dato
--
-- La comprobacion vive TAMBIEN aqui, y no solo en Java, por la misma
-- razon que `tg_atributo_gobernado`: la aplicacion no es la unica puerta.
-- Una fila insertada por SQL con una clave inventada convierte el
-- criterio en algo que el matcher no puede evaluar nunca, y el
-- requerimiento pasa a puntuar sobre un denominador que incluye un
-- criterio imposible.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_criterio_gobernado()
RETURNS TRIGGER AS $$
DECLARE
    org        bigint;
    tipo       text;
    compatible boolean;
BEGIN
    SELECT r.organizacion_id INTO org
      FROM requerimiento_cliente r
     WHERE r.id_requerimiento = NEW.id_requerimiento;

    -- `sujeto = PROPIEDAD` no es defensivo: es la mitad de la regla. Una
    -- condicion del ENCARGO --la garantia, el amoblado-- se pacta en cada
    -- comercializacion y no es un hecho del inmueble; un criterio sobre ella
    -- contaria en el denominador del matcher y ninguna propiedad podria
    -- responderlo. Pedir condiciones del encargo es una capacidad aparte, y se
    -- declara fuera del Corte 7 en vez de dejarse entrar a medias.
    SELECT c.tipo_dato INTO tipo
      FROM catalogo_atributo c
     WHERE c.clave = NEW.clave
       AND c.activo = true
       AND c.sujeto = 'PROPIEDAD'
       AND (c.organizacion_id IS NULL OR c.organizacion_id = org)
     ORDER BY c.organizacion_id DESC NULLS LAST
     LIMIT 1;

    IF tipo IS NULL THEN
        RAISE EXCEPTION
            'El criterio "%" no es una clave activa del catalogo con sujeto PROPIEDAD (o se '
            'retiro, o es una condicion del ENCARGO). Una clave existe antes que el criterio que '
            'la pide: si no, el matcher puntua sobre algo que ninguna propiedad puede responder',
            NEW.clave
            USING ERRCODE = '23514';
    END IF;

    compatible := CASE tipo
        WHEN 'TEXTO'           THEN NEW.operador IN ('I', 'N')
        WHEN 'LISTA'           THEN NEW.operador IN ('I', 'N')
        WHEN 'LISTA_MULTIPLE'  THEN NEW.operador = 'N'
        WHEN 'ENTERO'          THEN NEW.operador IN ('I', 'M', 'X', 'E')
        WHEN 'DECIMAL'         THEN NEW.operador IN ('I', 'M', 'X', 'E')
        WHEN 'IMPORTE'         THEN NEW.operador IN ('I', 'M', 'X', 'E')
        WHEN 'BOOLEANO'        THEN NEW.operador = 'V'
        WHEN 'FECHA'           THEN NEW.operador IN ('I', 'M', 'X', 'E')
        ELSE NULL
    END;

    IF compatible IS NULL THEN
        RAISE EXCEPTION 'Tipo de dato desconocido en el catalogo para "%": %', NEW.clave, tipo
            USING ERRCODE = '23514';
    END IF;

    IF NOT compatible THEN
        RAISE EXCEPTION
            'El operador "%" no se puede aplicar a "%", que es de tipo %', NEW.operador,
            NEW.clave, tipo
            USING ERRCODE = '23514';
    END IF;

    -- IMPORTE es monto + moneda. Una moneda sin monto, o un monto sin
    -- moneda, no se puede cruzar; para los otros tipos la moneda sobra.
    IF tipo = 'IMPORTE' THEN
        IF NEW.valor_numero IS NULL OR NEW.valor_moneda IS NULL
                OR NEW.valor_moneda NOT IN ('PEN', 'USD') THEN
            RAISE EXCEPTION 'El criterio de importe "%" requiere monto y moneda PEN o USD', NEW.clave
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.valor_moneda IS NOT NULL THEN
        RAISE EXCEPTION 'La clave "%" no es un importe y no admite moneda', NEW.clave
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_criterio_gobernado
    BEFORE INSERT OR UPDATE ON requerimiento_criterio
    FOR EACH ROW
    EXECUTE FUNCTION exigir_criterio_gobernado();


-- ---------------------------------------------------------------------
-- 4. Lo que ya estaba escrito, traducido a criterios
--
-- `frente_minimo` -> criterio DESEABLE `frente >= n`.
-- `rubro`         -> criterio DESEABLE `rubro_permitido INCLUYE (texto)`,
--                    y SOLO si el catalogo gobierna esa clave.
--
-- Los dos como DESEABLE y no como INDISPENSABLE: el modelo viejo no
-- distinguia peso, asi que promoverlos a indispensable cambiaria el
-- resultado de cruces que hoy existen -- descartaria coincidencias que
-- hasta ayer se mostraban. Subir el peso es una decision del cliente, y
-- se toma pidiendolo, no migrando.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    gobierna_rubro  boolean;
    con_frente      bigint;
    con_rubro       bigint;
    criterios_frente bigint;
    criterios_rubro  bigint;
BEGIN
    SELECT count(*) INTO con_frente
      FROM requerimiento_cliente WHERE frente_minimo IS NOT NULL;
    SELECT count(*) INTO con_rubro
      FROM requerimiento_cliente WHERE nullif(btrim(rubro), '') IS NOT NULL;

    INSERT INTO requerimiento_criterio (organizacion_id, id_requerimiento, clave, operador,
                                        peso, valor_numero)
    SELECT r.organizacion_id, r.id_requerimiento, 'frente', 'M', 'D', r.frente_minimo
      FROM requerimiento_cliente r
     WHERE r.frente_minimo IS NOT NULL
       AND EXISTS (SELECT 1 FROM catalogo_atributo c
                    WHERE c.clave = 'frente' AND c.activo = true
                      AND (c.organizacion_id IS NULL OR c.organizacion_id = r.organizacion_id))
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS criterios_frente = ROW_COUNT;

    SELECT EXISTS (SELECT 1 FROM catalogo_atributo c
                    WHERE c.clave = 'rubro_permitido' AND c.activo = true
                      AND c.tipo_dato IN ('TEXTO', 'LISTA', 'LISTA_MULTIPLE'))
      INTO gobierna_rubro;

    IF gobierna_rubro THEN
        INSERT INTO requerimiento_criterio (organizacion_id, id_requerimiento, clave, operador, peso)
        SELECT r.organizacion_id, r.id_requerimiento, 'rubro_permitido', 'N', 'D'
          FROM requerimiento_cliente r
         WHERE nullif(btrim(r.rubro), '') IS NOT NULL
           AND EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.clave = 'rubro_permitido' AND c.activo = true
                          AND (c.organizacion_id IS NULL
                               OR c.organizacion_id = r.organizacion_id))
        ON CONFLICT DO NOTHING;
        GET DIAGNOSTICS criterios_rubro = ROW_COUNT;

        INSERT INTO requerimiento_criterio_valor (id_criterio, valor)
        SELECT cr.id_criterio, btrim(r.rubro)
          FROM requerimiento_criterio cr
          JOIN requerimiento_cliente r ON r.id_requerimiento = cr.id_requerimiento
         WHERE cr.clave = 'rubro_permitido'
           AND nullif(btrim(r.rubro), '') IS NOT NULL
        ON CONFLICT DO NOTHING;
    ELSE
        criterios_rubro := 0;
    END IF;

    RAISE NOTICE 'V95 criterios traducidos -> frente: % de % ; rubro: % de % (los no traducidos '
                 'se quedan en su columna de linaje y NO se inventan)',
                 criterios_frente, con_frente, criterios_rubro, con_rubro;

    IF criterios_frente <> con_frente THEN
        RAISE EXCEPTION 'V95: % requerimientos pedian frente minimo y solo se tradujeron %',
                        con_frente, criterios_frente;
    END IF;
END $$;


-- ---------------------------------------------------------------------
-- 5. Evidencia: nada quedo a medias
-- ---------------------------------------------------------------------
DO $$
DECLARE
    sin_operacion bigint;
    descuadre     bigint;
    huerfanos     bigint;
BEGIN
    SELECT count(*) INTO sin_operacion
      FROM requerimiento_cliente WHERE operacion IS NULL;
    IF sin_operacion > 0 THEN
        RAISE EXCEPTION 'V95: quedan % requerimientos sin operacion', sin_operacion;
    END IF;

    -- El importe viejo y el nuevo dicen lo mismo, fila a fila.
    SELECT count(*) INTO descuadre
      FROM requerimiento_cliente
     WHERE renta_min IS DISTINCT FROM presupuesto_min
        OR renta_max IS DISTINCT FROM presupuesto_max;
    IF descuadre > 0 THEN
        RAISE EXCEPTION 'V95: % requerimientos tienen presupuesto distinto de su renta de origen',
                        descuadre;
    END IF;

    -- Todo el que declaraba tipo tiene su fila en el vocabulario nuevo.
    SELECT count(*) INTO huerfanos
      FROM requerimiento_cliente r
     WHERE r.tipo_inmueble IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM requerimiento_tipo_inmueble t
                        WHERE t.id_requerimiento = r.id_requerimiento);
    IF huerfanos > 0 THEN
        RAISE EXCEPTION 'V95: % requerimientos declaraban tipo y no tienen fila traducida',
                        huerfanos;
    END IF;

    RAISE NOTICE 'V95 cerrada: la demanda ya puede hablar de venta, de varios tipos y de '
                 'atributos gobernados. Nada se borro.';
END $$;

