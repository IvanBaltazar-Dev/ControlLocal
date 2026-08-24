-- =====================================================================
-- V79 - Corte 2: la identidad registral pertenece a la PROPIEDAD
--
-- QUE HUECO CIERRA
-- La partida registral existe hoy en UN SOLO SITIO de toda la base:
-- `condicion_compraventa.partida_registral`, colgada de una solicitud de venta,
-- y con CERO filas. Un inmueble que nunca se puso en venta no tiene partida en
-- ninguna parte -- asi que el broker no puede verificar titular ni cargas antes
-- de firmar un encargo de ALQUILER, que es la operacion mas frecuente.
--
-- La identidad registral no es una condicion que se pacte: es lo que el
-- inmueble ES ante el registro. Sobrevive al encargo, no cambia porque se
-- vuelva a alquilar y no depende de la operacion. Sujeto PROPIEDAD, por la
-- regla de V73: "si al firmar el siguiente encargo el dato puede cambiar sin
-- que la propiedad haya cambiado, es del ENCARGO" -- y aqui no puede.
--
-- LAS DOS DEL NUMERO SON ESTRUCTURALES, LAS CUATRO RESTANTES GOBERNADAS
-- `partida_registral` y `oficina_registral` son identidad: participan en
-- integridad y en la deteccion de duplicados igual que `metraje`, y su
-- aplicabilidad no depende del tipo -- toda propiedad inscribible tiene una
-- partida. Eso es exactamente el criterio ESTRUCTURAL de D-E4-3 (V60). Las
-- otras cuatro describen situacion, no identidad, y su aplicabilidad SI depende
-- del tipo: son atributos gobernados.
--
-- LAS SEIS ENTRAN 'OPC', SIN EXCEPCION -- y esto lo decidio CONTROL despues del
-- precheck, no es una rebaja de paso. El encargo original las queria PUB
-- creyendo que PUB solo informaba. Lo medido es lo contrario: `PUB` cuelga de
-- `exigirPublicable`, que LANZA (`PublicacionServiceImpl:186`, HTTP 400 por
-- `ManejadorErroresApi:45`), y no existe ninguna superficie del cable que
-- reporte una PUB de la PROPIEDAD. Sembrarlas PUB habria dejado sin poder
-- anunciarse a las 26 propiedades reales de `controllocal_dev` -- las 26 pasan
-- hoy el gate -- y habria tumbado dos de las cinco suites del cierre
-- (`e2e-f4-solicitud:144` y `e2e-estabilizacion-alquiler:136`), que publican
-- sobre un LOCAL creado con `metraje_total` y `rubro_permitido` y nada mas.
-- La promocion OPC -> PUB es una linea de SQL el dia que el negocio la decida,
-- y entonces habra que decidir tambien si aplica a venta y no a alquiler, que
-- hoy el sujeto PROPIEDAD no sabe expresar. Queda registrado, no resuelto.
--
-- NINGUNA LLEVA VALOR POR DEFECTO. Ni siquiera `cargas_gravamenes = NINGUNA`:
-- "no tiene cargas" es una afirmacion verificada contra el registro, no el
-- estado inicial de un dato que nadie ha mirado. La ausencia sigue
-- significando "todavia no se sabe".
--
-- LO QUE ESTA MIGRACION NO HACE, y no por olvido:
--   * No promueve ninguna clave a PUB, ni las seis nuevas ni las que ya habia.
--   * No toca `PublicacionServiceImpl` ni la semantica de ALT/PUB/OPC.
--   * No escribe, no fecha y no rellena `condicion_compraventa.partida_registral`.
--     Solo deja escrito que dejo de ser el domicilio del dato. El expediente de
--     compraventa es el bloque 6.
--   * No extiende a la PROPIEDAD la guarda "ninguna LISTA sin vocabulario" de
--     V77. `servicios_disponibles` sigue siendo una LISTA muda de sujeto
--     PROPIEDAD y sus reemplazos nacen en el Corte 5; la guarda va con ellos.
--     Lo que si se vigila aqui es el vocabulario de lo que ESTA migracion
--     introduce, que es otra cosa.
--   * No incluye el tipo X (OTRO) en ninguna de las seis. X sigue con tres
--     claves aplicables y sigue sin auditar (pendientes-brox §2.6).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Las dos columnas de identidad.
--
-- Anulables las dos, y es la regla del 3g: un inmueble puede conocerse antes
-- de tener su partida a la vista. NOT NULL obligaria a inventarla, que es lo
-- que este modelo existe para impedir.
--
-- VARCHAR(40) en las dos: la de oficina tiene que caber en el mismo ancho que
-- `catalogo_atributo_opcion.valor`, que es de donde salen sus valores.
-- ---------------------------------------------------------------------
ALTER TABLE propiedad
    ADD COLUMN partida_registral VARCHAR(40),
    ADD COLUMN oficina_registral VARCHAR(40);

COMMENT ON COLUMN propiedad.partida_registral IS
    'Autoridad unica del concepto PARTIDA_REGISTRAL desde V79. Se escribe por la '
    'clave de catalogo `partida_registral`. NULL significa "todavia no se sabe", '
    'nunca "no tiene": un inmueble sin partida a la vista no es un inmueble sin partida.';

COMMENT ON COLUMN propiedad.oficina_registral IS
    'Autoridad unica del concepto OFICINA_REGISTRAL desde V79. Su vocabulario vive '
    'en catalogo_atributo_opcion, NO en un CHECK de esta columna: duplicarlo aqui '
    'daria dos listas de oficinas y divergirian en la primera que se anada.';

-- ---------------------------------------------------------------------
-- 2. El catalogo admite dos conceptos estructurales mas.
--
-- Mismo movimiento que V67 hizo con PISO. El vocabulario de conceptos crece
-- con la clasificacion, no con la implementacion: anadir uno exige decidirlo.
-- ---------------------------------------------------------------------
ALTER TABLE catalogo_atributo
    DROP CONSTRAINT IF EXISTS ck_catalogo_campo_estructural;

ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_campo_estructural
    CHECK (campo_estructural IS NULL
           OR campo_estructural IN ('METRAJE', 'PISO',
                                    'PARTIDA_REGISTRAL', 'OFICINA_REGISTRAL'));

-- ---------------------------------------------------------------------
-- 3. Las seis claves.
--
-- `familia` queda NULL como en las otras diecinueve de la PROPIEDAD. Estrenar
-- aqui la primera familia tematica del sujeto PROPIEDAD cambiaria como se
-- agrupa el alta, y eso no lo pidio nadie.
--
-- `orden` continua donde acabo el catalogo fisico (190, servicios_disponibles):
-- la identidad registral se pregunta despues de describir la cosa, que es como
-- ocurre en la conversacion real.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                               aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                               valor_minimo, valor_maximo, longitud_maxima,
                               destino, campo_estructural)
VALUES
    (NULL, 'partida_registral', 'Partida registral', 'TEXTO', NULL,
     false, true, 200, 'PROPIEDAD', NULL,
     'El numero de partida en el registro de predios. Es lo que identifica al inmueble y permite verificar titular y cargas antes de firmar.',
     NULL, NULL, 40, 'ESTRUCTURAL', 'PARTIDA_REGISTRAL'),

    -- La misma numeracion de partida existe en varias oficinas, asi que el
    -- numero solo no identifica nada. Por eso viajan juntas y por eso esta es
    -- una LISTA: sin vocabulario cerrado, "Lima" y "LIMA" serian dos oficinas.
    (NULL, 'oficina_registral', 'Oficina registral', 'LISTA', NULL,
     false, true, 210, 'PROPIEDAD', NULL,
     'La oficina registral donde esta inscrita la partida. El mismo numero de partida existe en varias oficinas.',
     NULL, NULL, NULL, 'ESTRUCTURAL', 'OFICINA_REGISTRAL'),

    (NULL, 'independizado', 'Independizado', 'BOOLEANO', NULL,
     false, true, 220, 'PROPIEDAD', NULL,
     'Si la unidad tiene partida propia. Sin independizar no se puede vender ni hipotecar por separado.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'declaratoria_fabrica', 'Declaratoria de fabrica', 'BOOLEANO', NULL,
     false, true, 230, 'PROPIEDAD', NULL,
     'Si la edificacion esta inscrita. Sin declaratoria el registro dice que el terreno esta vacio, y ahi el banco no financia.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'area_segun_partida', 'Area segun partida', 'DECIMAL', 'm2',
     false, true, 240, 'PROPIEDAD', NULL,
     'El area que dice la partida registral. Puede no coincidir con la medida en obra, y en una venta manda la inscrita.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- Multivalor porque un inmueble puede tener hipoteca Y estar en sucesion a
    -- la vez. Con una LISTA simple habria que elegir cual de las dos se cuenta.
    (NULL, 'cargas_gravamenes', 'Cargas y gravamenes', 'LISTA_MULTIPLE', NULL,
     false, true, 250, 'PROPIEDAD', NULL,
     'Lo que consta inscrito sobre el inmueble. Una hipoteca o un embargo cambian el plazo, y a veces impiden la operacion.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL);

-- Los rotulos y la ayuda son lo que LEE una persona (V68): se corrigen aparte
-- para que el INSERT de arriba quede legible en una terminal sin UTF-8.
UPDATE catalogo_atributo SET rotulo = 'Declaratoria de fábrica',
       ayuda = 'Si la edificación está inscrita. Sin declaratoria el registro dice que el terreno está vacío, y ahí el banco no financia.'
 WHERE organizacion_id IS NULL AND clave = 'declaratoria_fabrica';
UPDATE catalogo_atributo SET rotulo = 'Área según partida', unidad = 'm²',
       ayuda = 'El área que dice la partida registral. Puede no coincidir con la medida en obra, y en una venta manda la inscrita.'
 WHERE organizacion_id IS NULL AND clave = 'area_segun_partida';
UPDATE catalogo_atributo SET rotulo = 'Cargas y gravámenes',
       ayuda = 'Lo que consta inscrito sobre el inmueble. Una hipoteca o un embargo cambian el plazo, y a veces impiden la operación.'
 WHERE organizacion_id IS NULL AND clave = 'cargas_gravamenes';
UPDATE catalogo_atributo SET
       ayuda = 'El número de partida en el registro de predios. Es lo que identifica al inmueble y permite verificar titular y cargas antes de firmar.'
 WHERE organizacion_id IS NULL AND clave = 'partida_registral';
UPDATE catalogo_atributo SET
       ayuda = 'La oficina registral donde está inscrita la partida. El mismo número de partida existe en varias oficinas.'
 WHERE organizacion_id IS NULL AND clave = 'oficina_registral';
UPDATE catalogo_atributo SET
       ayuda = 'Si la unidad tiene partida propia. Sin independizar no se puede vender ni hipotecar por separado.'
 WHERE organizacion_id IS NULL AND clave = 'independizado';

-- ---------------------------------------------------------------------
-- 4. Los dos vocabularios.
--
-- `catalogo_atributo_opcion` es la UNICA autoridad de estos valores. No hay
-- enum Java, ni Set escrito a mano, ni CHECK enumerativo paralelo, ni lista en
-- Angular: anadir la oficina de Ica el dia que haga falta tiene que ser una
-- fila, no un despliegue.
--
-- Las seis oficinas son las del departamento de Lima y Callao, que es donde
-- opera la cartera. No se siembran las 60 y pico del pais "por si acaso": un
-- vocabulario que nadie usa no se puede depurar despues sin decidir que se
-- hace con lo escrito.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('LIMA',     'Lima',     1),
        ('CALLAO',   'Callao',   2),
        ('HUAURA',   'Huaura',   3),
        ('CANETE',   'Cañete',   4),
        ('HUARAL',   'Huaral',   5),
        ('BARRANCA', 'Barranca', 6)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'oficina_registral';

-- NINGUNA es una opcion legitima y no un defecto: significa "se miro el
-- registro y no hay nada inscrito". Lo que no existe es una forma de decirlo
-- sin haberlo mirado -- para eso esta la ausencia de la clave.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('NINGUNA',                 'Ninguna',                      1),
        ('HIPOTECA',                'Hipoteca',                     2),
        ('EMBARGO',                 'Embargo',                      3),
        ('SERVIDUMBRE',             'Servidumbre',                  4),
        ('COPROPIEDAD_SIN_DIVIDIR', 'Copropiedad sin dividir',      5),
        ('SUCESION_PENDIENTE',      'Sucesion pendiente',           6),
        ('LITIGIO',                 'Litigio',                      7)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'cargas_gravamenes';

UPDATE catalogo_atributo_opcion o SET rotulo = 'Sucesión pendiente'
  FROM catalogo_atributo c
 WHERE c.id_catalogo_atributo = o.id_catalogo_atributo
   AND c.organizacion_id IS NULL AND c.clave = 'cargas_gravamenes'
   AND o.valor = 'SUCESION_PENDIENTE';

-- ---------------------------------------------------------------------
-- 5. A que tipos aplica cada una, TODAS 'OPC'.
--
-- `requerido` se escribe ademas de `exigencia` porque son columna y espejo
-- desde V72 y hoy son coherentes al 100 %: una fila que escriba solo una de
-- las dos rompe esa coherencia en silencio (leccion de V78).
--
-- Van en `catalogo_atributo_tipo` y NO en `catalogo_atributo_operacion`: son
-- del sujeto PROPIEDAD, y la guarda 2.5 de V78 rompe la migracion si una clave
-- declara su aplicabilidad en la tabla del otro sujeto.
-- ---------------------------------------------------------------------

-- 5.1 Identidad y cargas: los seis tipos inscribibles. Un terreno tiene
--     partida igual que un departamento, y las cargas se inscriben sobre
--     cualquiera de ellos.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'), ('O'), ('D'), ('C'), ('T'), ('A')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('partida_registral', 'oficina_registral', 'cargas_gravamenes');

-- 5.2 Independizado: solo donde la unidad forma parte de algo mayor. Una casa
--     o un terreno no se independizan de nada; un departamento, una oficina,
--     un local de galeria y un almacen de parque logistico si.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'), ('O'), ('L'), ('A')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'independizado';

-- 5.3 Area segun partida: donde el area inscrita y la medida en obra se
--     separan de verdad -- casa, terreno y almacen. En un departamento manda
--     el area de la declaratoria y no hay dos cifras que reconciliar.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('C'), ('T'), ('A')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'area_segun_partida';

-- 5.4 Declaratoria de fabrica: casa y departamento. La exigencia FUTURA es
--     distinta entre las dos -- PUB en C, OPC en D -- y esa asimetria es
--     legitima porque `catalogo_atributo_tipo` la guarda por fila. Hoy las dos
--     entran OPC; la promocion es de otro corte.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('C'), ('D')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'declaratoria_fabrica';

-- ---------------------------------------------------------------------
-- 6. La guarda que faltaba: un valor ESTRUCTURAL tambien pertenece a su
--    vocabulario.
--
-- POR QUE HACE FALTA. La comprobacion de vocabulario de V72 vive dentro de
-- `exigir_atributo_gobernado`, que es un trigger de `atributo_propiedad`. Un
-- valor ESTRUCTURAL no pasa por ahi -- va a su columna canonica -- asi que
-- `oficina_registral` seria la primera LISTA con vocabulario declarado y sin
-- nadie que lo comprobara. Comprobado en el codigo: la capa Java tampoco lo
-- hacia, `ConversionDeValores` acota tipo, rango y longitud y nunca pertenencia.
--
-- COMO SE HACE SIN DUPLICAR EL VOCABULARIO. El trigger no lleva escrita ni una
-- oficina: SELECCIONA las opciones vigentes de la clave que declara ese
-- concepto. La unica autoridad sigue siendo `catalogo_atributo_opcion`.
--
-- POR QUE NO ROMPE `servicios_disponibles`. El bucle solo mira claves con
-- `destino = 'ESTRUCTURAL'`; `servicios_disponibles` es ATRIBUTO y ni se toca.
-- Y ademas exige que la clave TENGA vocabulario sembrado, que es la misma
-- tolerancia que V72 dejo puesta.
--
-- EL `ELSE` QUE GRITA, otra vez. Igual que en V72: un concepto estructural de
-- tipo LISTA que se anada al catalogo sin ensenarle aqui de que columna sale
-- no puede caer en una salida por defecto y quedarse sin comprobar.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_vocabulario_estructural() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    cat record;
    -- NO se llama `valor`: `catalogo_atributo_opcion.valor` existe, y en
    -- `WHERE o.valor = valor` PL/pgSQL no puede decidir si el lado derecho es
    -- la variable o la columna. Falla con "column reference is ambiguous" en la
    -- PRIMERA escritura, no al crear la funcion -- que es como se cuela.
    valor_declarado text;
BEGIN
    FOR cat IN
        SELECT c.id_catalogo_atributo, c.clave, c.campo_estructural
          FROM catalogo_atributo c
         WHERE c.destino = 'ESTRUCTURAL'
           AND c.activo
           AND c.tipo_dato IN ('LISTA', 'LISTA_MULTIPLE')
           AND EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo)
    LOOP
        -- El unico sitio del esquema donde un concepto del dominio se traduce a
        -- una columna fisica. Es la capa de persistencia, que es de quien es
        -- ese conocimiento (D-E4-3 seccion 3).
        CASE cat.campo_estructural
            WHEN 'OFICINA_REGISTRAL' THEN valor_declarado := NEW.oficina_registral;
            ELSE
                RAISE EXCEPTION
                    'El concepto estructural "%" es de tipo lista y este trigger no sabe de que columna de propiedad sale su valor. Declararlo en el catalogo sin ensenarlo aqui deja el vocabulario sin comprobar.',
                    cat.campo_estructural
                    USING ERRCODE = 'check_violation';
        END CASE;

        IF valor_declarado IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                            WHERE o.id_catalogo_atributo = cat.id_catalogo_atributo
                              AND o.valor = valor_declarado
                              AND o.activo) THEN
            RAISE EXCEPTION 'El atributo "%" no admite el valor "%": no esta en su vocabulario',
                cat.clave, valor_declarado
                USING ERRCODE = 'check_violation';
        END IF;
    END LOOP;

    RETURN NEW;
END;
$$;

-- EL `WHEN`, Y POR QUE NO ES UN ATAJO GRATIS.
--
-- Sin el, la funcion se ejecuta en CADA escritura de propiedad, tenga o no
-- identidad registral. Medido sobre 20 000 filas en `controllocal_repositorios`:
--
--     sin trigger              7,1 s
--     trigger sin WHEN        16,9 s     (+0,49 ms por fila)
--     trigger con WHEN         7,0 s     (indistinguible de no tenerlo)
--
-- El coste no es la consulta al catalogo --un indice parcial solo lo bajo a
-- 15,8 s-- sino invocar PL/pgSQL una vez por fila. Y hay tres suites E2E que
-- cargan 100 000 propiedades de golpe: serian ~49 s mas cada una, en las mismas
-- suites que miden p95 y ya son fragiles frente al reloj.
--
-- LO QUE EL `WHEN` ROMPERIA SI NADIE LO VIGILARA: un concepto estructural de
-- tipo lista que se anada manana y cuya columna no se nombre aqui deja de
-- comprobarse EN SILENCIO -- el `ELSE` de la funcion no llega a ejecutarse
-- porque la funcion no se llama. Por eso viaja con su prueba:
-- `CatalogoQueHablaIntegrationTest.laBaseDefiendeElVocabularioDeTodoEstructural`
-- recorre los conceptos que declara el catalogo, escribe en cada uno un valor
-- inventado por su columna real --enrutando con `EscritorEstructural`, que es
-- donde vive esa correspondencia-- y exige que la base lo rechace. Olvidar el
-- `WHEN` pone esa prueba en rojo.
CREATE TRIGGER tg_vocabulario_estructural
    BEFORE INSERT OR UPDATE ON propiedad
    FOR EACH ROW WHEN (NEW.oficina_registral IS NOT NULL)
    EXECUTE FUNCTION exigir_vocabulario_estructural();

-- ---------------------------------------------------------------------
-- 7. La resignificacion de la columna que habia.
--
-- No se borra, no se rellena y no se le anade fecha. Lo unico que cambia es
-- que deja de ser el domicilio del dato: hoy tiene 0 filas y quien la lea sin
-- esto escrito la volvera a tratar como el origen de la partida.
--
-- Que llegue a ser una copia FECHADA de verdad --con su fecha y su escritor--
-- es trabajo del expediente de compraventa (bloque 6), no de aqui.
-- ---------------------------------------------------------------------
COMMENT ON COLUMN condicion_compraventa.partida_registral IS
    'COPIA de la partida vigente en ESA venta, no la autoridad del dato. Desde '
    'V79 la identidad registral del inmueble vive en propiedad.partida_registral; '
    'esta columna conserva lo que se declaro en aquel episodio y NO se reescribe '
    'cuando la partida del inmueble cambia. V79 no la escribe ni la migra: tiene '
    '0 filas y su escritor nace con el expediente de compraventa.';

-- ---------------------------------------------------------------------
-- 8. Las guardas.
--
-- Comprueban invariantes del estado resultante, no cifras escritas a mano.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    claves            TEXT[] := ARRAY['partida_registral', 'oficina_registral',
                                      'independizado', 'cargas_gravamenes',
                                      'area_segun_partida', 'declaratoria_fabrica'];
    faltan            TEXT;
    sin_aplicabilidad TEXT;
    cruce             TEXT;
    lista_sin_vocab   TEXT;
    con_pub           TEXT;
    mal_destino       TEXT;
    con_valor         BIGINT;
    par               RECORD;
    oficinas          INT;
    cargas            INT;
BEGIN
    -- 8.1 Las seis entraron. Un INSERT ... SELECT que no encuentra su clave no
    --     inserta nada y la migracion termina "bien".
    SELECT string_agg(k, ', ') INTO faltan
      FROM unnest(claves) AS k
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.organizacion_id IS NULL AND c.clave = k AND c.activo);
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'V79: estas claves no llegaron al catalogo: %', faltan;
    END IF;

    -- 8.2 Ninguna sin decir a que tipos aplica: seria invisible en todos los
    --     guiones y nadie lo notaria hasta echarla en falta.
    SELECT string_agg(c.clave, ', ') INTO sin_aplicabilidad
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves) AND NOT c.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = c.id_catalogo_atributo);
    IF sin_aplicabilidad IS NOT NULL THEN
        RAISE EXCEPTION 'V79: claves sin aplicabilidad declarada: %', sin_aplicabilidad;
    END IF;

    -- 8.3 Y ninguna con la suya en la tabla del otro sujeto. Es la guarda 2.5
    --     de V78, vuelta a correr sobre el estado que deja esta migracion.
    SELECT string_agg(c.clave, ', ') INTO cruce
      FROM catalogo_atributo c
     WHERE c.activo
       AND ((c.sujeto = 'ENCARGO'
             AND EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                          WHERE t.id_catalogo_atributo = c.id_catalogo_atributo))
         OR (c.sujeto = 'PROPIEDAD'
             AND EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                          WHERE o.id_catalogo_atributo = c.id_catalogo_atributo)));
    IF cruce IS NOT NULL THEN
        RAISE EXCEPTION 'V79: claves con la aplicabilidad en la tabla del otro sujeto: %', cruce;
    END IF;

    -- 8.4 Las dos listas que introduce esta migracion tienen vocabulario.
    --     Se comprueban ESTAS, no todas las de la PROPIEDAD: extender la
    --     comprobacion a `servicios_disponibles` la haria fallar, y esa clave
    --     no se toca en este corte.
    SELECT string_agg(c.clave, ', ') INTO lista_sin_vocab
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND c.tipo_dato IN ('LISTA', 'LISTA_MULTIPLE')
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo);
    IF lista_sin_vocab IS NOT NULL THEN
        RAISE EXCEPTION 'V79: listas sin vocabulario sembrado: %', lista_sin_vocab;
    END IF;

    -- 8.5 NINGUNA entra bloqueando. Es la enmienda de CONTROL, y se comprueba
    --     aqui porque una promocion accidental a PUB dejaria sin publicar a
    --     toda la cartera y el sitio donde se veria seria una suite E2E.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO con_pub
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND t.exigencia <> 'OPC';
    IF con_pub IS NOT NULL THEN
        RAISE EXCEPTION
            'V79: estas filas no entraron OPC: %. Las seis capacidades de este corte no bloquean nada; promover a PUB es otro corte.',
            con_pub;
    END IF;

    -- 8.6 Y el catalogo entero sigue sin una sola fila PUB, que es el estado
    --     que esta migracion se comprometio a no mover.
    IF EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                 JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
                WHERE c.organizacion_id IS NULL AND t.exigencia = 'PUB') THEN
        RAISE EXCEPTION 'V79: aparecieron filas PUB en el catalogo del sistema y esta migracion no promueve ninguna.';
    END IF;

    -- 8.7 La autoridad de cada una, declarada y coherente. `ck_catalogo_autoridad_completa`
    --     ya impide el estado a medias; esto comprueba que la clasificacion es
    --     la que se decidio y no la contraria.
    SELECT string_agg(c.clave || ' -> ' || c.destino, ', ') INTO mal_destino
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL
       AND ((c.clave IN ('partida_registral', 'oficina_registral')
             AND c.destino <> 'ESTRUCTURAL')
         OR (c.clave IN ('independizado', 'cargas_gravamenes',
                         'area_segun_partida', 'declaratoria_fabrica')
             AND c.destino <> 'ATRIBUTO'));
    IF mal_destino IS NOT NULL THEN
        RAISE EXCEPTION 'V79: destino equivocado en: %', mal_destino;
    END IF;

    -- 8.8 Cero valores materializados. La ausencia significa "todavia no se
    --     sabe", y sembrar un defecto seria una respuesta que nadie dio.
    SELECT count(*) INTO con_valor
      FROM atributo_propiedad WHERE clave = ANY (claves);
    IF con_valor > 0 THEN
        RAISE EXCEPTION 'V79: se escribieron % valores de claves que acaban de nacer.', con_valor;
    END IF;

    IF EXISTS (SELECT 1 FROM propiedad
                WHERE partida_registral IS NOT NULL OR oficina_registral IS NOT NULL) THEN
        RAISE EXCEPTION 'V79: alguna propiedad quedo con identidad registral que nadie declaro.';
    END IF;

    -- 8.9 Ninguna de las seis es mitad de un par hecho/condicion declarado. Si
    --     alguna lo fuera tendria que nacer cubriendo la aplicabilidad de su
    --     condicion, y el gate de V78 lo diria. Se comprueba en vez de
    --     suponerlo.
    FOR par IN
        SELECT * FROM (VALUES
            ('amoblado',              'se_ofrece_amoblado'),
            ('cuota_mantenimiento',   'mantenimiento_a_cargo_de'),
            ('estacionamientos',      'estacionamientos_incluidos'),
            ('rubro_permitido',       'rubros_excluidos_por_titular'),
            ('mascotas_reglamento',   'mascotas_aceptadas'),
            ('nivel_implementacion',  'se_entrega_implementado'),
            ('estado_ocupacion',      'entrega_desocupado'),
            ('lote_minimo_normativo', 'acepta_venta_fraccionada')
        ) AS p(hecho, condicion)
    LOOP
        IF par.hecho = ANY (claves) OR par.condicion = ANY (claves) THEN
            RAISE EXCEPTION
                'V79: la clave "%"/"%" es mitad de un par declarado y esta migracion la trata como suelta.',
                par.hecho, par.condicion;
        END IF;
    END LOOP;

    -- 8.10 Los dos vocabularios, completos.
    SELECT count(*) INTO oficinas FROM catalogo_atributo_opcion o
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = 'oficina_registral';
    SELECT count(*) INTO cargas FROM catalogo_atributo_opcion o
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = 'cargas_gravamenes';
    IF oficinas <> 6 OR cargas <> 7 THEN
        RAISE EXCEPTION 'V79: se esperaban 6 oficinas y 7 cargas, y hay % y %', oficinas, cargas;
    END IF;

    RAISE NOTICE 'V79: 6 claves registrales (2 estructurales, 4 gobernadas), % filas de aplicabilidad, % opciones, 0 en PUB, 0 valores materializados.',
        (SELECT count(*) FROM catalogo_atributo_tipo t
           JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
          WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)),
        oficinas + cargas;
END $$;
