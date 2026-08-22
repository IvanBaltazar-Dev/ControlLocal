-- =====================================================================
-- V76 - La Propiedad como activo de dato.
--
-- LA FRASE QUE CONGELA
--   "Una Propiedad representa un inmueble CONOCIDO por BROX, no
--    necesariamente una oferta GESTIONADA por BROX. Su existencia,
--    procedencia e historia observada son independientes de Prospecciones y
--    Encargos. Los hechos comerciales solo nacen cuando existe la relacion
--    comercial que los autoriza."
--
--   "BROX nunca convierte una observacion de mercado en un hecho comercial ni
--    inventa una relacion para poder conservar conocimiento."
--
-- V75 permitio que una propiedad exista sin encargo. Esto termina el trabajo:
-- una propiedad puede EXISTIR sin que se sepa de quien es, tiene que decir COMO
-- se conocio, y puede acumular lo que se ve del mercado sin que nada de eso se
-- confunda con un hecho comercial.
--
-- LO QUE NO SE HACE, Y ES LA MITAD DE LA DECISION
--
-- No se crea un estado NO_OFRECIDA. La situacion comercial se DERIVA de las
-- relaciones -cero encargos vivos = no hay oferta gestionada por BROX- y
-- guardarla ademas en una columna crearia dos autoridades para la misma verdad:
-- manana habria filas con `NO_OFRECIDA` y un encargo de VENTA vivo. Este
-- proyecto lleva cortes enteros retirando exactamente esa clase de problema.
--
-- No se crea un estado MAESTRA. Bien modelada, TODA Propiedad ya es el registro
-- canonico del inmueble. Una misma propiedad puede empezar observada, entrar en
-- prospeccion seis meses despues y acabar con un encargo, y no debe convertirse
-- en otra propiedad ni dejar de ser maestra por el camino: ese historial
-- completo, sobre una sola identidad, es justamente lo valioso.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Se puede conocer un inmueble sin saber de quien es.
--
-- Esta es una deuda del modelo original: D-E4-1 decia "toda propiedad tiene al
-- menos un titular vigente", y eso vale para una propiedad que se GESTIONA. Se
-- puede conocer legitimamente un departamento de 90 m2, tres dormitorios,
-- ofrecido a 180 000 USD, sin saber todavia quien es el dueno. Obligar a
-- declararlo obligaria a inventarlo, y esa es la regla que el producto no
-- rompe: lo que no se sabe se declara FALTANTE.
--
-- La tabla `titularidad_propiedad` NO se toca: su trigger `exigir_cuotas_completas`
-- (V47) ya declara que cero titulares vigentes es un estado legitimo. Todo el
-- bloqueo estaba en la columna espejo del agregado.
--
-- `tipo_rol_propietario` cae con ella porque forman la FK compuesta: dejar el
-- discriminador con su DEFAULT sobre un id nulo diria "es un PROPIETARIO" de
-- nadie.
-- ---------------------------------------------------------------------
ALTER TABLE propiedad ALTER COLUMN id_rol_propietario   DROP NOT NULL;
ALTER TABLE propiedad ALTER COLUMN tipo_rol_propietario DROP NOT NULL;
-- El DEFAULT se queda -- la entidad no mapea esa columna, la rellena la base --
-- pero deja de aplicarse cuando no hay a quien discriminar: sin esto, una
-- propiedad sin titular nacia diciendo el tipo de rol de un rol que no existe,
-- y el CHECK de abajo la rechazaria justo en el caso que este corte permite.
CREATE OR REPLACE FUNCTION limpiar_tipo_rol_sin_propietario()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.id_rol_propietario IS NULL THEN
        NEW.tipo_rol_propietario := NULL;
    ELSIF NEW.tipo_rol_propietario IS NULL THEN
        NEW.tipo_rol_propietario := 'PROPIETARIO';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_propiedad_tipo_rol
    BEFORE INSERT OR UPDATE ON propiedad
    FOR EACH ROW EXECUTE FUNCTION limpiar_tipo_rol_sin_propietario();

ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_titular_completo
    CHECK ((id_rol_propietario IS NULL     AND tipo_rol_propietario IS NULL)
        OR (id_rol_propietario IS NOT NULL AND tipo_rol_propietario IS NOT NULL));

COMMENT ON COLUMN propiedad.id_rol_propietario IS
    'Proyeccion del titular representante. NULL = inmueble conocido del que '
    'todavia no se sabe quien es el dueno. La titularidad de verdad vive en '
    'titularidad_propiedad; el ENCARGO si exige que haya al menos una vigente.';

-- ---------------------------------------------------------------------
-- 2. Por que BROX conoce este inmueble.
--
-- Sin esto, una propiedad de referencia es un rumor: el producto exige
-- procedencia, vigencia y evidencia ANTES que inferencia.
--
-- Es PROCEDENCIA, no ESTADO. No dice en que situacion esta la propiedad -eso se
-- deriva de sus relaciones- sino como entro, y no cambia con el tiempo: una
-- propiedad conocida observando el mercado que seis meses despues se capta
-- siguio conociendose observando el mercado.
--
-- EL VOCABULARIO SALE DEL INVENTARIO DE PRODUCTORES REALES, no de una lista
-- imaginada. Hoy una fila de `propiedad` nace por tres caminos y cada valor
-- tiene el suyo:
--
--   OPERACION    el alta universal, que ejecuta un agente
--   OBSERVACION  el registro de conocimiento de mercado (sin encargo)
--   SEMILLA      las migraciones de arranque y los fixtures
--
-- No hay IMPORTACION porque no hay ninguna importacion. Un vocabulario con
-- valores que nadie produce deja de poder auditarse: nunca se sabe si la lista
-- esta incompleta o si el productor se perdio.
--
-- Y NO se confunde con `Procedencia` (D-K-1), que responde otra cosa: por donde
-- entro la PETICION -pantalla, WhatsApp, que conversacion, que turno-. Son dos
-- ejes y los dos importan.
-- ---------------------------------------------------------------------
ALTER TABLE propiedad
    ADD COLUMN origen_incorporacion VARCHAR(12),
    ADD COLUMN id_rol_incorporo     BIGINT;

-- El backfill NO adivina. Las dos propiedades que sembro V4 son SEMILLA porque
-- las escribio una migracion; el resto entro por el alta, que solo ejecuta un
-- agente. Ninguna es OBSERVACION: esa via no existia hasta hoy.
UPDATE propiedad SET origen_incorporacion = 'SEMILLA'
 WHERE codigo IN ('LOC-0001', 'LOC-0002');
UPDATE propiedad SET origen_incorporacion = 'OPERACION'
 WHERE origen_incorporacion IS NULL;

-- El DEFAULT no es un descuido ni una comodidad: es la respuesta correcta para
-- quien lo va a usar. Seis guiones de verificacion y las semillas Flyway
-- insertan en `propiedad` con SQL directo, saltandose el dominio; una fila que
-- entra asi ES, por definicion, sembrada. El caso de uso declara siempre el
-- suyo, asi que el DEFAULT solo alcanza a quien no pasa por el.
ALTER TABLE propiedad ALTER COLUMN origen_incorporacion SET DEFAULT 'SEMILLA';
ALTER TABLE propiedad ALTER COLUMN origen_incorporacion SET NOT NULL;
ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_origen_incorporacion
    CHECK (origen_incorporacion IN ('OPERACION', 'OBSERVACION', 'SEMILLA'));

-- Quien la incorporo. FK compuesta con el tenant, igual que el propietario:
-- sin ella un rol de otra corredora podria firmar una incorporacion ajena.
-- Anulable porque las filas historicas no lo saben, y no se inventa.
ALTER TABLE propiedad
    ADD CONSTRAINT fk_propiedad_incorporo
    FOREIGN KEY (organizacion_id, id_rol_incorporo)
    REFERENCES persona_rol (organizacion_id, id_persona_rol);

CREATE INDEX ix_propiedad_origen ON propiedad (organizacion_id, origen_incorporacion);

COMMENT ON COLUMN propiedad.origen_incorporacion IS
    'Como llego BROX a conocer este inmueble: OPERACION (la registro un agente), '
    'OBSERVACION (se vio en el mercado, sin gestionarla) o SEMILLA (migracion o '
    'fixture). Es procedencia, no estado: no cambia porque despues se capte.';

COMMENT ON COLUMN propiedad.id_rol_incorporo IS
    'Quien la incorporo. NULL en las filas anteriores a V76, que no lo saben.';

-- ---------------------------------------------------------------------
-- 3. Lo que se ve del mercado, en su propia serie.
--
-- `precio_propiedad` guarda hechos de un ENCARGO: el importe autorizado (U), el
-- publicado (P), el ofrecido (O), el de cierre (C). Los cuatro existen porque
-- hubo una relacion comercial que los autoriza.
--
-- "Lo vi anunciado a 190 000 dolares" no es ninguno de esos. BROX no lo
-- autorizo, no lo publico y no lo negocio: lo OBSERVO. Meterlo en la misma
-- serie convertiria una observacion en un hecho comercial y falsearia cualquier
-- comparable que se construya despues, que es justamente para lo que este dato
-- existe.
--
-- APPEND-ONLY, y no por costumbre: lo impide un trigger. Una observacion es un
-- hecho fechado; corregirla borraria la muestra que la hace util. Cuando el
-- precio cambia se observa OTRA VEZ, y las dos filas juntas son las que dicen
-- como se movio.
-- ---------------------------------------------------------------------
CREATE TABLE observacion_mercado (
    id_observacion  BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    organizacion_id BIGINT        NOT NULL REFERENCES organizacion (id_organizacion),
    id_propiedad    BIGINT        NOT NULL,
    fecha_observada DATE          NOT NULL,
    operacion       VARCHAR(1)    NOT NULL,
    importe         NUMERIC(14,2) NOT NULL,
    moneda          VARCHAR(3)    NOT NULL,
    fuente          VARCHAR(30)   NOT NULL,
    detalle         VARCHAR(300),
    id_rol_actor    BIGINT        NOT NULL,
    fecha_creacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- FK compuestas con el tenant, como en atributo_propiedad y atributo_encargo:
    -- sin ellas una observacion podria colgar del inmueble de otra corredora.
    CONSTRAINT fk_observacion_propiedad
        FOREIGN KEY (organizacion_id, id_propiedad)
        REFERENCES propiedad (organizacion_id, id_propiedad),
    CONSTRAINT fk_observacion_actor
        FOREIGN KEY (organizacion_id, id_rol_actor)
        REFERENCES persona_rol (organizacion_id, id_persona_rol),

    -- El mismo vocabulario de operacion que el resto del dominio. Inventar otro
    -- aqui seria la tercera lista de operaciones.
    CONSTRAINT ck_observacion_operacion CHECK (operacion IN ('A', 'V')),
    CONSTRAINT ck_observacion_moneda    CHECK (moneda IN ('PEN', 'USD')),
    CONSTRAINT ck_observacion_importe   CHECK (importe >= 0),
    -- No se observa lo que no ha pasado. Una fecha futura no es una observacion:
    -- es una expectativa, y esa no es evidencia.
    CONSTRAINT ck_observacion_fecha     CHECK (fecha_observada <= CURRENT_DATE)
);

COMMENT ON TABLE observacion_mercado IS
    'Lo que se VIO del mercado sobre un inmueble. No es un hecho comercial de '
    'BROX: no lo autorizo, no lo publico y no lo negocio. Append-only.';

COMMENT ON COLUMN observacion_mercado.fecha_observada IS
    'Cuando se vio, no cuando se anoto: un aviso de hace tres meses registrado '
    'hoy vale por su fecha.';

COMMENT ON COLUMN observacion_mercado.fuente IS
    'De donde salio. Obligatoria: sin fuente es un rumor. Vocabulario todavia '
    'ABIERTO a proposito -- las fuentes reales son un hecho del campo y nadie '
    'las ha inventariado; un CHECK hoy seria arbitrario u obligaria a mentir.';

CREATE INDEX ix_observacion_propiedad
    ON observacion_mercado (id_propiedad, fecha_observada DESC, id_observacion DESC);
CREATE INDEX ix_observacion_organizacion ON observacion_mercado (organizacion_id);

-- ---------------------------------------------------------------------
-- 4. El append-only, dicho por la base.
--
-- Una regla que solo vive en el servicio se rodea con un psql. Y aqui rodearla
-- no rompe una fila: destruye la muestra que hace comparable a la serie entera.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION proteger_observacion_mercado()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Una observacion de mercado no se % : es un hecho fechado. Si el precio '
        'cambio, observa otra vez -- las dos filas juntas son las que dicen como '
        'se movio.', lower(TG_OP)
        USING ERRCODE = 'check_violation';
END $$;

CREATE TRIGGER tg_observacion_append_only
    BEFORE UPDATE OR DELETE ON observacion_mercado
    FOR EACH ROW EXECUTE FUNCTION proteger_observacion_mercado();

-- ---------------------------------------------------------------------
-- 5. La frontera, dicha al reves: ningun hito NUEVO nace huerfano.
--
-- Un precio autorizado, publicado o negociado nace de una relacion comercial.
-- Sin ella no es un hecho: es una observacion, y esa vive en la tabla de arriba.
-- Hasta hoy el trigger de V49 salia sin comprobar nada cuando `id_captacion`
-- era NULL, y por ahi entraron hitos huerfanos que ningun lector sabe
-- interpretar -- la ficha universal los filtra y `GET /locales/{id}/precios` los
-- ensena: el mismo dato con dos verdades.
--
-- Las filas historicas SE QUEDAN. Borrarlas seria inventar que nunca
-- existieron; lo que se cierra es la puerta, no el pasado.
--
-- Los cinco escritores vivos atan ya el hito a su encargo: el alta universal,
-- `captar` desde la prospeccion, la publicacion -que ademas prefiere NO
-- escribir el dato antes que atribuirlo mal-, `POST /locales/{id}/precios` y el
-- CIERRE del contrato. El quinto lo destapo este trigger: `cerrarLocal`
-- escribia el hito 'C' sin decir de que encargo era, asi que dos alquileres
-- sucesivos del mismo inmueble mezclaban sus cierres en una sola linea.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_encargo_del_hito()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.id_captacion IS NULL THEN
        RAISE EXCEPTION
            'Un hito economico nace de un ENCARGO, y este no declara ninguno. Si lo que se '
            'quiere guardar es lo que se VIO del mercado, va en observacion_mercado: BROX no '
            'convierte una observacion en un hecho comercial.'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_precio_exige_encargo
    BEFORE INSERT ON precio_propiedad
    FOR EACH ROW EXECUTE FUNCTION exigir_encargo_del_hito();

-- ---------------------------------------------------------------------
-- 6. Las guardas. Cuentan el antes contra el despues en vez de comprobar una
--    cifra escrita a mano: V72 aprendio por que.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    sin_origen      BIGINT;
    perdio_titular  BIGINT;
    semillas        BIGINT;
BEGIN
    SELECT count(*) INTO sin_origen FROM propiedad WHERE origen_incorporacion IS NULL;
    IF sin_origen > 0 THEN
        RAISE EXCEPTION 'V76: % propiedades quedaron sin declarar su procedencia.', sin_origen;
    END IF;

    -- Aflojar un NOT NULL no puede vaciar nada.
    SELECT count(*) INTO perdio_titular FROM propiedad WHERE id_rol_propietario IS NULL;
    IF perdio_titular > 0 THEN
        RAISE EXCEPTION 'V76 aflojo el NOT NULL del titular y ya hay % propiedades sin el. '
            'Esta migracion solo abre la puerta; no debe vaciar nada.', perdio_titular;
    END IF;

    SELECT count(*) INTO semillas FROM propiedad WHERE origen_incorporacion = 'SEMILLA';
    RAISE NOTICE 'V76: % propiedades declaran procedencia (% de semilla); '
        'observacion_mercado lista y append-only.',
        (SELECT count(*) FROM propiedad), semillas;
END $$;
