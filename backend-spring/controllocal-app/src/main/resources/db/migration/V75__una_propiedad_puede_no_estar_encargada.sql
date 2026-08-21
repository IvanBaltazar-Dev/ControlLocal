-- =====================================================================
-- V75 - Convergencia del Corte 0C: registrar no es encargar.
--
-- LA CONTRADICCION QUE CIERRA
-- El modelo tenia congelado que la Propiedad es la cosa fisica y que la
-- operacion pertenece al Encargo (D-E4-1). Pero el alta exigia al menos una
-- operacion, asi que toda propiedad nacia con un encargo VIVO. Y el embudo
-- comercial de BROX dice lo contrario:
--
--     propietario -> PROSPECCION -> ENCARGO -> PUBLICACION
--                    (existe para conseguir el encargo)
--
-- Si la prospeccion existe para conseguir el encargo, el encargo no puede tener
-- que existir antes de prospectar. Lo destapo la corrida de cierre del Corte 0C:
-- al retirar `POST /locales` -que registraba el inmueble Y abria una
-- prospeccion- no quedo ninguna entrada para una propiedad que solo se esta
-- prospectando, y `uq_captacion_viva_por_operacion` rechazaba el encargo que
-- `captar` intentaba crear encima del que el alta ya habia abierto.
--
-- LA DISTINCION QUE CONGELA
--     Propiedad registrada != propiedad comercialmente encargada.
--
-- Una propiedad sin encargos esta en el registro maestro y puede prospectarse
-- -acumulando identidad, ubicacion, titularidad, atributos, duplicados e
-- interacciones mientras se intenta captar-. Lo que NO es: ofrecida.
--
-- QUE CAMBIA AQUI, Y POR QUE SOLO ESTO
-- Tres columnas de `propiedad` describen la OFERTA y no la cosa, y las tres eran
-- NOT NULL. Esa es toda la razon por la que el alta no podia dejar de crear un
-- encargo: sin encargo no habia de donde sacarlas.
--
--   precio_referencial  \  proyeccion del importe del encargo. Sin encargo no
--   moneda_referencial  /  hay precio AUTORIZADO, y rellenarlo con cero seria
--                          inventar un dato: un local "de 0 soles" entra en
--                          cualquier busqueda por precio maximo.
--   disponibilidad_comercial  "disponible" estampado en el alta era una
--                          DEDUCCION: nada decia lo contrario, asi que se
--                          afirmaba. Una propiedad que solo se prospecta no
--                          esta ofrecida.
--
-- NULL y no un codigo nuevo. Se penso en anadir un quinto valor
-- -NO_OFRECIDA- y se descarto: `DISPONIBILIDAD_PROPIEDAD` es una maquina de
-- estados con transiciones, rotulos y filtros, y "todavia no ha entrado en la
-- maquina" no es un estado de la maquina. Es su ausencia. Ademas el CHECK
-- `ck_propiedad_disponibilidad` ya tolera NULL -en SQL, `NULL = ANY(...)` es
-- UNKNOWN y un CHECK solo rechaza lo FALSO-, asi que no hay que tocarlo, y el
-- listado comercial, que filtra por esta columna, deja de ensenar la propiedad
-- sin que haya que ensenarle una excepcion.
--
-- `estado_registro` NO se toca y sigue NOT NULL: la propiedad SI esta
-- registrada y activa. Es justo la distincion que este corte establece.
-- =====================================================================

ALTER TABLE propiedad ALTER COLUMN precio_referencial       DROP NOT NULL;
ALTER TABLE propiedad ALTER COLUMN moneda_referencial       DROP NOT NULL;
ALTER TABLE propiedad ALTER COLUMN disponibilidad_comercial DROP NOT NULL;

-- La moneda tenia DOS candados, y quitar solo el NOT NULL no habria bastado:
-- `ck_propiedad_moneda_referencial` (V13:24, validado en V17:26) lleva el
-- IS NOT NULL DENTRO de la expresion, asi que el INSERT habria seguido fallando
-- con 23514 en vez de 23502 -- el mismo bloqueo con otro numero. Se reescribe
-- conservando el vocabulario: lo que se afloja es la obligatoriedad, no el
-- catalogo de monedas.
ALTER TABLE propiedad DROP CONSTRAINT ck_propiedad_moneda_referencial;
ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_moneda_referencial
    CHECK (moneda_referencial IS NULL OR moneda_referencial IN ('PEN', 'USD'));

-- `ck_propiedad_precio` (V4:64) es `precio_referencial >= 0` y NO lleva
-- IS NOT NULL, asi que con NULL evalua a UNKNOWN y se acepta. No se toca:
-- borrarlo seria perder gratis la garantia de no-negatividad.
--
-- `ck_propiedad_disponibilidad` (V17:22) es un catalogo D/R/A/T sin IS NOT NULL:
-- tambien tolera NULL tal como esta.

COMMENT ON COLUMN propiedad.precio_referencial IS
    'Proyeccion del importe del encargo de referencia. NULL = la propiedad no '
    'esta encargada: no hay precio autorizado. El precio de verdad vive en '
    'condicion_economica_captacion y en precio_propiedad, por encargo.';

COMMENT ON COLUMN propiedad.moneda_referencial IS
    'La moneda de precio_referencial. NULL cuando no hay precio: una moneda sin '
    'importe no significa nada.';

COMMENT ON COLUMN propiedad.disponibilidad_comercial IS
    'Como esta la OFERTA: D disponible, R reservado, A alquilado, T retirado. '
    'NULL = no hay oferta todavia (propiedad registrada sin encargo). No es un '
    'quinto estado: es no haber entrado aun en la maquina.';

-- ---------------------------------------------------------------------
-- La guarda: aflojar un NOT NULL no puede perder ni un dato.
--
-- Se cuenta el ANTES contra el DESPUES en vez de comprobar una cifra escrita a
-- mano. V72 aprendio por que: su auditoria decia "cuatro filas requeridas" y en
-- la base viva eran diez.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    sin_precio        BIGINT;
    sin_disponibilidad BIGINT;
BEGIN
    SELECT count(*) INTO sin_precio FROM propiedad WHERE precio_referencial IS NULL;
    SELECT count(*) INTO sin_disponibilidad FROM propiedad WHERE disponibilidad_comercial IS NULL;

    IF sin_precio > 0 OR sin_disponibilidad > 0 THEN
        RAISE EXCEPTION 'V75 aflojo un NOT NULL y ya hay filas vacias (% sin precio, % sin '
            'disponibilidad). Esta migracion solo abre la puerta; no debe vaciar nada.',
            sin_precio, sin_disponibilidad;
    END IF;

    RAISE NOTICE 'V75: % propiedades conservan su precio y su disponibilidad; la puerta queda abierta.',
        (SELECT count(*) FROM propiedad);
END $$;
