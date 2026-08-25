-- =====================================================================
-- V84 - Corte 5, subtanda 5A: quien ocupa el inmueble, y que servicios llegan
--
-- Encargo congelado: `docs/ai/encargo-corte-5-terreno.md`, decisiones D-1..D-7
-- del titular (2026-08-25). Decision de fondo de la clave transversal:
-- `docs/ai/decision-estado-ocupacion-en-los-siete.md` (D-C5-1).
--
-- ---------------------------------------------------------------------
-- QUE HUECO CIERRA
--
-- (1) BROX no tiene DONDE registrar quien esta dentro de un inmueble. La
--     CONDICION comercial `entrega_desocupado` se pacta en los siete tipos desde
--     V77, y el HECHO sobre el que se pacta NO EXISTE. Mientras siga sin existir,
--     el unico sitio donde cabe "hoy vive el propietario" es el pacto de un
--     encargo -- y un pacto muere con su encargo, mientras que el hecho sobrevive.
--     Es exactamente el defecto que el guard 2.2 de V78 vigila; el par
--     `estado_ocupacion` / `entrega_desocupado` esta declarado alli desde V78 y
--     hoy pasa por el lado facil: el hecho no existe, asi que no puede quedarse
--     corto.
--
-- (2) `servicios_disponibles` es la ULTIMA LISTA MUDA del catalogo. Es LISTA, es
--     del sujeto PROPIEDAD, aplica solo a `T` y tiene CERO opciones -- medido el
--     2026-08-25 en las dos bases del proyecto. Una LISTA sin vocabulario no es
--     una lista: `MotorDeCaptura.controlDe` la degrada a TEXTO y
--     `exigir_atributo_gobernado` acepta cualquier cadena, porque su
--     comprobacion de vocabulario esta condicionada a que haya opciones. Por eso
--     la guarda "ninguna LISTA sin vocabulario" existe desde V77 SOLO para el
--     ENCARGO: extenderla a la PROPIEDAD habria roto la migracion contra esta
--     clave. V79, V80 y V81 lo escribieron cada uno en su cabecera y lo
--     aplazaron. Aqui deja de aplazarse.
--
-- ---------------------------------------------------------------------
-- POR QUE `servicios_disponibles` NO SE PODIA RETIRAR ANTES
--
-- Porque retirar una captura antes de que exista su reemplazo deja un agujero:
-- durante el hueco, el agente que sabe que el terreno tiene agua no tiene donde
-- escribirlo. El North Star lo prohibe por escrito, y la auditoria lo dice en su
-- propia celda: "se retira EN LA MISMA TANDA que deja operativos sus
-- reemplazos". De ahi el orden de esta migracion, que es parte del encargo y no
-- una preferencia de estilo:
--
--     invertir 6 y 7  ->  LA MIGRACION ABORTA. La guarda nueva encuentra la
--                         clave todavia activa y muda, y se cae contra ella.
--                         Comprobado ejecutando el bloque 7 sobre una copia de
--                         la base con `servicios_disponibles` reactivada:
--                         "hay LISTAS activas sin vocabulario:
--                          PROPIEDAD/servicios_disponibles (sistema)".
--
--     invertir 5 y 6  ->  el encargo dice que "pierde el legado", y MEDIDO
--                         CONTRA ESTA IMPLEMENTACION **NO ES ASI**, y se
--                         escribe en vez de repetirlo: la retirada es
--                         `activo = false` y NO BORRA NI UNA FILA, y ningun
--                         paso del bloque 5 mira `catalogo_atributo.activo` de
--                         la clave que reparte -- clasifica leyendo
--                         `atributo_propiedad` y escribe sobre las claves
--                         NUEVAS, que estan activas. Asi que invertirlos hoy
--                         seria inocuo.
--                         El orden se respeta igual, y por una razon que si se
--                         sostiene: la unica forma de que el reparto no pueda
--                         mirar lo que sustituye es que alguien convierta la
--                         retirada en algo mas que una desactivacion. Ese dia el
--                         orden es lo unico que lo impide, y el dia que eso pase
--                         nadie va a estar leyendo esta cabecera.
--
-- ---------------------------------------------------------------------
-- LA EXIGENCIA, QUE ES LA DECISION QUE MUEVE EL MERCADO (D-1)
--
--     estado_ocupacion    OPC en los siete
--     agua_desague        PUB en T · OPC en A
--     energia_electrica   PUB en T
--
-- `PUB` no bloquea el alta -- eso solo lo hace `ALT` -- y si bloquea PUBLICAR:
-- `AtributoPropiedadRepository.clavesQueImpidenPublicar` filtra
-- `exigencia in ('ALT','PUB')`. Son las PUB numero DOS y TRES del catalogo del
-- sistema; la primera fue `tipo_acceso`/`L` en V82.
--
-- EFECTO MEDIDO EN LA CARTERA, ANTES DE APLICAR ESTO (controllocal_dev,
-- 2026-08-25): 26 propiedades, 7 publicables y 19 bloqueadas. El unico TERRENO
-- es `PROP-0024`, que hoy lleva `metraje = 1200` (columna canonica) y
-- `zonificacion = RDM`, y NADA MAS.
--
--   => AL APLICAR ESTA MIGRACION, `PROP-0024` PASA DE PUBLICABLE A BLOQUEADO
--      por dos claves, y la cartera pasa de 7 publicables a 6.
--
-- ESO ES EL RESULTADO BUSCADO, no un fallo. Un terreno anunciado sin decir si
-- tiene agua y luz no es una oferta: es una foto. Y en la periferia se tiene luz
-- y no desague, o al reves, asi que un solo campo agregado -- que es lo que era
-- `servicios_disponibles` -- escondia justamente la combinacion que decide la
-- compra.
--
-- Y COMO EL BLOQUEO SI INFORMA: desde `35cf09c` la PROPIEDAD reporta su propia
-- deuda de publicacion en `PropiedadResponse.faltanParaPublicar`, con el ROTULO
-- de cada clave. El bloqueo viaja con la instruccion de como quitarlo. Sin esa
-- superficie, estrenar una PUB seria estrenar un rechazo mudo -- que es la razon
-- por la que V79 y V81 no promovieron ninguna.
--
-- ---------------------------------------------------------------------
-- `gas` CONSERVA SU CONCEPTO Y GANA UNA OPCION (D-2)
--
-- Tres documentos pedian para `gas` un estado `CON_FACTIBILIDAD_APROBADA` que su
-- vocabulario no tenia. NO era una segunda definicion compitiendo con la
-- primera: era una opcion que faltaba. Asi que `gas` no se migra a otro
-- concepto, no cambia de clave, de `tipo_dato` ni de aplicabilidad, no pierde
-- ningun valor y NO se extiende a `X`. Solo gana la opcion, en el sitio que le
-- corresponde por significado:
--
--     SIN_RED_CERCANA           no hay red             1
--     RED_EN_LA_VIA             hay tuberia en la calle 2   <- infraestructura fisica
--     CON_FACTIBILIDAD_APROBADA la concesionaria lo autorizo 3   <- tramite
--     INSTALADO                 llega a la puerta      4
--     GLP_TANQUE_EXTERNO                               5
--     GLP_BALONES                                      6
--
-- `RED_EN_LA_VIA` y `CON_FACTIBILIDAD_APROBADA` no son sinonimos y por eso hacen
-- falta las dos: la primera es un hecho de la calle que se ve mirando, la
-- segunda es un papel de la concesionaria. Se puede tener red delante y no tener
-- factibilidad, y se puede tener factibilidad aprobada de una red que todavia no
-- pasa. La `ayuda` se reescribe para decirlo, porque si no la lista ofrece dos
-- opciones que un agente leeria como la misma.
--
-- `orden` es una de las columnas que `proteger_catalogo_del_sistema()` SI deja
-- tocar: ese trigger vigila `clave`, `tipo_dato`, `del_sistema` y
-- `organizacion_id`, y nada mas.
--
-- ---------------------------------------------------------------------
-- LO QUE ESTA MIGRACION **NO** HACE, y no por olvido
--
--   * NO borra `servicios_disponibles`. `activo = false`, nunca DELETE. Y no es
--     una formalidad: `proteger_catalogo_del_sistema()` rechaza el DELETE de una
--     clave del sistema con `restrict_violation`, y su propio mensaje dice cual
--     es la via -- "para retirarlo de las preguntas, ponlo activo = false".
--   * NO borra ni reinterpreta ni un solo valor escrito de esa clave. Siguen en
--     `atributo_propiedad`, y siguen LEYENDOSE: `LectorPorAutoridad` lee las
--     filas del inmueble sin preguntar si su clave sigue activa, y
--     `fichaDeAtributo` tolera la definicion ausente (`rotulo = clave`). Lo que
--     se cierra es la ESCRITURA: `exigir_atributo_gobernado` exige
--     `c.activo = true`, asi que una clave retirada no admite valores nuevos.
--     Es lo correcto -- un concepto retirado no sigue capturando -- y queda
--     dicho para que no se descubra como sorpresa.
--   * NO escribe una RETIRADA en `rastro_valor_gobernado`. Ningun valor se
--     retira: lo que se retira es la CLAVE del catalogo. Un rastro de retirada
--     afirmaria que a esos inmuebles se les quito el dato, y es falso.
--   * NO promueve ninguna otra clave. Las catorce PUB que propone la auditoria
--     siguen siendo propuesta, salvo las dos que D-1 autoriza aqui.
--   * NO toca `aplica_todos` de ninguna clave (D-5), ni `manzana_lote` (D-6), ni
--     `condicion_terreno` -- que por D-3 es PUB y no ALT, y va en 5B --, ni
--     ninguna otra clave de 5B, ni la retirada de `area_terreno` en T (D-7).
--   * NO toca Angular. El SPA no conoce claves: `MotorDeCaptura.controlDe`
--     deriva el control del vocabulario y el formulario sale del catalogo.
--   * NO rellena `agua_desague` ni `energia_electrica` en `PROP-0024`. Se
--     desbloquea el HECHO VERIFICADO, no el relleno.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. LA FOTO DEL ESTADO PREVIO.
--
-- Sin ella, "no se movio nada mas" y "no se perdio nada" serian suposiciones: un
-- recuento final cuadra igual si una fila desaparece y otra aparece. Se compara
-- el CONJUNTO, no el total (leccion de V82).
--
-- Tablas TEMP con `DROP` explicito al final y NUNCA `ON COMMIT DROP`: no
-- sobrevive a como Flyway envuelve la migracion (leccion de V78, repetida en
-- V82).
-- ---------------------------------------------------------------------
CREATE TEMP TABLE v84_claves_antes AS
SELECT c.id_catalogo_atributo, c.organizacion_id, c.clave, c.tipo_dato, c.sujeto,
       c.destino, c.campo_estructural, c.activo, c.aplica_todos, c.del_sistema, c.orden
  FROM catalogo_atributo c;

CREATE TEMP TABLE v84_tipo_antes AS
SELECT t.id_catalogo_atributo, c.clave, c.organizacion_id,
       t.tipo_propiedad, t.exigencia, t.requerido
  FROM catalogo_atributo_tipo t
  JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo;

CREATE TEMP TABLE v84_opciones_antes AS
SELECT o.id_catalogo_atributo, c.clave, c.organizacion_id, o.valor, o.rotulo, o.orden, o.activo
  FROM catalogo_atributo_opcion o
  JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo;

-- Los VALORES escritos, enteros. Es la foto que sostiene "nada de lo que habia
-- se perdio": la identidad de la fila y lo que dice, no su numero.
CREATE TEMP TABLE v84_valores_antes AS
SELECT a.id_atributo_propiedad, a.organizacion_id, a.id_propiedad, a.clave,
       a.valor_texto, a.valor_numero, a.valor_booleano, a.valor_fecha, a.valor_moneda
  FROM atributo_propiedad a;

-- ---------------------------------------------------------------------
-- 1. NACEN `agua_desague` Y `energia_electrica`, **CON** SU VOCABULARIO.
--
-- En UNA SOLA SENTENCIA, y es el punto: una clave LISTA que nace sin opciones es
-- una clave muda mientras dure el hueco, y "mientras dure el hueco" ha durado
-- cuatro cortes en el caso de `servicios_disponibles`. La CTE que modifica datos
-- devuelve los ids reales -- que la secuencia genera y no son los mismos en dev,
-- en pruebas y en produccion -- y siembra el vocabulario con ellos.
--
-- `orden`: se INTERCALAN detras de `gas` (610) en vez de continuar en 950. Los
-- huecos de diez que dejo V81 existen exactamente para esto -- su cabecera lo
-- dice: "para que un corte posterior pueda intercalar sin renumerar" -- y las
-- tres claves son la MISMA conversacion con el propietario: que servicios llegan
-- a esta puerta. Separarlas 340 posiciones las pondria en dos pantallas.
--
-- El INSERT va en ASCII a proposito, para que se lea en una terminal sin UTF-8;
-- los acentos de `rotulo` y `ayuda` los repone el bloque 4. Patron de V68, V79,
-- V80 y V81.
-- ---------------------------------------------------------------------
WITH nacen AS (
    INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                                   aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                                   valor_minimo, valor_maximo, longitud_maxima,
                                   destino, campo_estructural)
    VALUES
        (NULL, 'agua_desague', 'Agua y desague', 'LISTA', NULL,
         false, true, 612, 'PROPIEDAD', NULL,
         'Si llegan agua y desague a este inmueble.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL),
        (NULL, 'energia_electrica', 'Energia electrica', 'LISTA', NULL,
         false, true, 614, 'PROPIEDAD', NULL,
         'Si llega suministro electrico a este inmueble.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL)
    RETURNING id_catalogo_atributo, clave
)
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT n.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM nacen n
  CROSS JOIN (VALUES
        ('CONECTADO',                 'Conectado',                 1),
        ('CON_FACTIBILIDAD_APROBADA', 'Con factibilidad aprobada', 2),
        ('SIN_SERVICIO',              'Sin servicio',              3)
       ) AS o(valor, rotulo, orden);

-- ---------------------------------------------------------------------
-- 2. A QUE TIPOS APLICAN, Y CON QUE EXIGENCIA (D-1).
--
-- `requerido` se escribe ADEMAS de `exigencia` porque son columna y espejo desde
-- V72: `requerido = (exigencia = 'ALT')`, y aqui no hay ninguna ALT, asi que las
-- tres filas van a `false`. El guard 2.4 de V78 lo comprueba sobre TODO el
-- catalogo, no solo sobre lo nuevo, y una fila que escriba una sola de las dos
-- columnas rompe la migracion -- que es lo correcto.
--
-- Van en `catalogo_atributo_tipo` y NUNCA en `catalogo_atributo_operacion`: son
-- del sujeto PROPIEDAD (guarda 2.5 de V78).
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, v.tipo, false, v.exigencia
  FROM catalogo_atributo c
  JOIN (VALUES
        ('agua_desague',      'T', 'PUB'),
        ('agua_desague',      'A', 'OPC'),
        ('energia_electrica', 'T', 'PUB')
       ) AS v(clave, tipo, exigencia) ON v.clave = c.clave
 WHERE c.organizacion_id IS NULL;

-- ---------------------------------------------------------------------
-- 3. NACE `estado_ocupacion`, CON SU VOCABULARIO Y EN LOS SIETE (D-C5-1).
--
-- Mismo patron que el bloque 1: clave y vocabulario en la misma sentencia.
--
-- CUATRO OPCIONES, y `CON_EDIFICACION_A_DEMOLER` NO ES UNA DE ELLAS (D-C5-1 §3).
-- Mezcla dos ejes -- quien ocupa y que hay construido -- y obliga a elegir entre
-- dos cosas que pueden ser ciertas a la vez: un terreno puede tener una casa
-- vieja encima Y estar ocupado por terceros. Ademas, extendido tal cual a un
-- departamento, la lista ofreceria "con edificacion a demoler" como estado de un
-- piso 12. Lo que esa opcion queria decir vive en `edificacion_existente`, que
-- es del terreno y va en 5B.
--
-- `orden` = 950 y no intercalado: no pertenece a ningun grupo existente. Es un
-- hecho de SITUACION -- quien esta dentro --, no una instalacion, y colocarlo
-- entre las instalaciones lo esconderia. Reordenar la presentacion de lo que ya
-- existe no es de este corte.
-- ---------------------------------------------------------------------
WITH nace AS (
    INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                                   aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                                   valor_minimo, valor_maximo, longitud_maxima,
                                   destino, campo_estructural)
    VALUES
        (NULL, 'estado_ocupacion', 'Estado de ocupacion', 'LISTA', NULL,
         false, true, 950, 'PROPIEDAD', NULL,
         'Quien esta dentro del inmueble hoy.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL)
    RETURNING id_catalogo_atributo, clave
)
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT n.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM nace n
  CROSS JOIN (VALUES
        ('DESOCUPADO',                      'Desocupado',                      1),
        ('OCUPADO_POR_EL_PROPIETARIO',      'Ocupado por el propietario',      2),
        ('OCUPADO_POR_INQUILINO',           'Ocupado por inquilino',           3),
        ('OCUPADO_POR_TERCEROS_SIN_TITULO', 'Ocupado por terceros sin titulo', 4)
       ) AS o(valor, rotulo, orden);

-- Los SIETE tipos, con filas explicitas y `aplica_todos = false` (D-C5-1).
-- Explicitas y no `aplica_todos = true` porque la aplicabilidad tiene HOY dos
-- autoridades en este esquema y mezclarlas es la deuda que D-5 deja anotada: una
-- clave `aplica_todos` no puede despues excluir un tipo sin cambiar de forma.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('C'), ('D'), ('L'), ('O'), ('T'), ('X')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'estado_ocupacion';

-- `gas` gana su opcion (D-2). Primero se abre el hueco desplazando lo que hay de
-- la posicion 3 en adelante, y despues se inserta: al reves, dos opciones
-- compartirian el `orden` 3 y el selector las pintaria en un orden que decide el
-- planificador. `catalogo_atributo_opcion` tiene PK (id_catalogo_atributo,
-- valor), asi que el `orden` no es unico y nada avisaria.
UPDATE catalogo_atributo_opcion o
   SET orden = o.orden + 1
  FROM catalogo_atributo c
 WHERE c.id_catalogo_atributo = o.id_catalogo_atributo
   AND c.organizacion_id IS NULL AND c.clave = 'gas'
   AND o.orden >= 3;

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, 'CON_FACTIBILIDAD_APROBADA', 'Con factibilidad aprobada', 3
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL AND c.clave = 'gas';

-- ---------------------------------------------------------------------
-- 4. LO QUE LEE UNA PERSONA: rotulos y ayudas, con acentos.
--
-- Las ayudas dicen el HECHO y la distincion que la lista existe para capturar.
-- La de los dos servicios separa `CONECTADO` de `CON_FACTIBILIDAD_APROBADA`
-- porque sin decirlo un agente leeria las dos como "si tiene"; la de `gas`
-- separa la infraestructura de la calle del papel de la concesionaria (D-2); y
-- la de `estado_ocupacion` la separa de la disponibilidad comercial, que es la
-- confusion previsible -- un inmueble disponible para vender puede estar ocupado
-- por su propietario hasta la firma, y `propiedad.disponibilidad_comercial` es
-- otra columna con otro vocabulario.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo c
   SET rotulo = v.rotulo, ayuda = v.ayuda
  FROM (VALUES
    ('estado_ocupacion', 'Estado de ocupación',
     'Quién está dentro del inmueble hoy. No es su disponibilidad comercial: un inmueble disponible para vender puede estar ocupado por su propietario hasta la firma.'),
    ('agua_desague', 'Agua y desagüe',
     'Conectado: el servicio funciona hoy. Con factibilidad aprobada: la concesionaria lo autorizó por escrito y todavía no está conectado.'),
    ('energia_electrica', 'Energía eléctrica',
     'Conectado: hay suministro hoy. Con factibilidad aprobada: la concesionaria lo autorizó por escrito y todavía no hay suministro.'),
    ('gas', 'Suministro de gas',
     'Cómo llega el gas a este inmueble. No se supone por el distrito: la red crece manzana a manzana. «Red en la vía» es la tubería física en la calle, sin trámite; «con factibilidad aprobada» es la autorización escrita de la concesionaria, que puede existir sin que la red pase todavía.')
  ) AS v(clave, rotulo, ayuda)
 WHERE c.organizacion_id IS NULL AND c.clave = v.clave;

UPDATE catalogo_atributo_opcion o
   SET rotulo = v.rotulo
  FROM catalogo_atributo c,
       (VALUES
        ('estado_ocupacion', 'OCUPADO_POR_TERCEROS_SIN_TITULO', 'Ocupado por terceros sin título')
       ) AS v(clave, valor, rotulo)
 WHERE c.id_catalogo_atributo = o.id_catalogo_atributo
   AND c.organizacion_id IS NULL
   AND c.clave = v.clave
   AND o.valor = v.valor;

-- ---------------------------------------------------------------------
-- 5. **SOLO AHORA**: EL LEGADO DE `servicios_disponibles`.
--
-- Los reemplazos ya existen, ya tienen vocabulario y ya son aplicables. Es el
-- primer instante en que se puede mirar el legado sabiendo a donde podria ir.
--
-- ---------------------------------------------------------------------
-- EL ACTA DE CLASIFICACION, y por que se clasifica por TEXTO EXACTO
--
-- `servicios_disponibles` es texto libre de facto: es LISTA sin opciones, y la
-- comprobacion de vocabulario de `exigir_atributo_gobernado` esta condicionada a
-- que existan opciones, asi que ha aceptado cualquier cadena. Escribir aqui un
-- interprete de castellano -- "si contiene 'agua' entonces..." -- seria inferir:
-- acertaria con "agua y luz" y mentiria con "sin agua, con luz", y ninguna de
-- las dos formas esta acotada por nada.
--
-- Asi que se clasifica cadena a cadena, con una lista explicita y corta, igual
-- que el `codigo IN (...)` de V14, V76 y V83. Lo que no este en el acta NO se
-- adivina y TAMPOCO se cuenta en silencio: la asercion 8.8 PARA la migracion y
-- NOMBRA las cadenas. Contarlas como "no inventariadas" y seguir --que es lo
-- que hacia la primera version de este bloque-- convertia la promesa "ningun
-- valor sin destino" en una tautologia: el clasificador siempre clasifica el
-- 100 % porque el veredicto por defecto lo pone el propio `coalesce`.
--
-- ---------------------------------------------------------------------
-- LO QUE SE MIDIO ANTES DE ESCRIBIR ESTO (2026-08-25)
--
--     controllocal_dev            0 filas
--     controllocal_repositorios   322 filas, DOS cadenas distintas:
--                                 "Agua, luz y desague"  283
--                                 "agua y desague"        39
--
-- ---------------------------------------------------------------------
-- Y POR QUE LAS DOS QUEDAN **AMBIGUAS**, que es el resultado y no una excusa
--
-- Las dos afirman que hay agua y desague, y una afirma ademas que hay luz. Lo
-- que NINGUNA dice es lo unico que las claves nuevas existen para capturar: si
-- el servicio ESTA CONECTADO o si solo HAY FACTIBILIDAD APROBADA. La auditoria
-- pidio ese tercer estado precisamente porque el campo viejo no podia
-- distinguirlo; traducir "tiene agua" a `CONECTADO` seria inventar la distincion
-- que el campo viejo era incapaz de hacer, y hacerlo por el caso frecuente.
--
-- `SIN_SERVICIO` tampoco es la traduccion de "no lo menciono": que una cadena
-- hable de agua y calle la luz no dice que no haya luz, dice que no consta.
--
-- => Se conservan integros donde estan --nadie borra una fila-- y se CUENTAN.
-- Lo ambiguo permanece FALTANTE. El dato se recupera visitando, no traduciendo.
--
-- EL TAMANO DEL LEGADO SE ESCRIBE COMO INVARIANTE Y **JAMAS COMO `= 0`**: en
-- `controllocal_dev` hay cero filas y en `controllocal_repositorios` hay 322
-- porque un fixture las escribe en cada corrida. Una asercion "hay 0 filas de
-- legado" pasaria en dev y mentiria en pruebas.
--
-- Lo que SI se escribe como cifra exacta es otra cosa: que ninguna de esas
-- filas --sean 0 o 322-- lleve una cadena fuera del acta (asercion 8.8).
-- ---------------------------------------------------------------------
CREATE TEMP TABLE v84_reparto AS
SELECT a.id_atributo_propiedad,
       a.organizacion_id,
       a.id_propiedad,
       a.valor_texto,
       coalesce(acta.veredicto, 'NO_INVENTARIADO') AS veredicto,
       acta.destino_agua,
       acta.destino_energia,
       coalesce(acta.motivo,
                'cadena no inventariada por V84: no se adivina, queda FALTANTE') AS motivo
  FROM atributo_propiedad a
  LEFT JOIN (VALUES
        -- texto exacto | veredicto | agua_desague | energia_electrica | motivo
        ('Agua, luz y desague', 'AMBIGUO', NULL::text, NULL::text,
         'afirma agua, desague y luz, y no dice si estan CONECTADOS o solo con FACTIBILIDAD APROBADA, que es la distincion que las claves nuevas existen para capturar'),
        ('agua y desague', 'AMBIGUO', NULL, NULL,
         'afirma agua y desague sin decir su estado, y calla sobre la luz: callar no es SIN_SERVICIO')
       ) AS acta(texto, veredicto, destino_agua, destino_energia, motivo)
    ON acta.texto = a.valor_texto
 WHERE a.clave = 'servicios_disponibles';

-- EL REPARTO DE LO RECUPERABLE. Hoy no reparte nada, y se escribe igual -- por
-- la misma razon por la que V83 escribio el INSERT gemelo del ENCARGO sabiendo
-- que `atributo_encargo` tenia cero filas: el mecanismo es lo que impide que
-- manana, cuando el acta resuelva una cadena, alguien lo haga a mano y sin
-- linaje. Se escribe el valor y su procedencia en la misma transaccion.
--
-- `agua_desague` aplica a T y A; `servicios_disponibles` aplica SOLO a T --
-- medido: `catalogo_atributo_tipo` tiene UNA fila para esa clave, `T/OPC`, y la
-- enumeracion de siete tipos que la auditoria le atribuia nunca fue cierta --,
-- asi que todo su legado viene de terrenos y ninguna fila puede caer en un tipo
-- donde el destino no aplique. El `NOT EXISTS` esta igualmente, porque una
-- propiedad puede haber recibido ya el dato nuevo por otra via y
-- `uq_atributo_propiedad_clave` deja una sola fila por (propiedad, clave).
WITH escritos AS (
    INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
    SELECT r.organizacion_id, r.id_propiedad, d.clave, d.valor
      FROM v84_reparto r
      CROSS JOIN LATERAL (VALUES ('agua_desague',      r.destino_agua),
                                 ('energia_electrica', r.destino_energia)) AS d(clave, valor)
     WHERE r.veredicto = 'RECUPERABLE'
       AND d.valor IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad ya
                        WHERE ya.id_propiedad = r.id_propiedad AND ya.clave = d.clave)
    RETURNING organizacion_id, id_propiedad, clave, valor_texto
)
-- El linaje del valor repartido (V83). `verbo = 'ALTA'` porque es la primera vez
-- que esa clave recibe valor en ese inmueble, y `naturaleza` queda AUSENTE: la
-- procedencia OPERACIONAL es demostrable -- la escribio esta migracion -- pero
-- COMO se conocio el hecho no lo es, y NULL significa "no consta", no una cuarta
-- clase de evidencia.
INSERT INTO rastro_valor_gobernado
    (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto,
     canal, registrado_en, evidencia_ref)
SELECT e.organizacion_id, 'PROPIEDAD', e.id_propiedad, e.clave, 'ALTA', e.valor_texto,
       'SISTEMA', now(),
       'V84 reparto de servicios_disponibles (acta de clasificacion)'
  FROM escritos e;

-- ---------------------------------------------------------------------
-- 6. **SOLO AHORA**: `servicios_disponibles` SE RETIRA.
--
-- `activo = false`, nunca DELETE. La clave sigue en el catalogo, su fila de
-- aplicabilidad sigue ahi y sus valores siguen escritos y legibles. Lo que
-- desaparece es la PREGUNTA: `CatalogoAtributoRepository.aplicablesA` filtra
-- `c.activo = true`, asi que deja de salir en el alta y en el editor.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo
   SET activo = false
 WHERE organizacion_id IS NULL AND clave = 'servicios_disponibles';

-- ---------------------------------------------------------------------
-- 7. **SOLO AHORA**: LA GUARDA DE VOCABULARIO, EXTENDIDA A LA PROPIEDAD.
--
-- Desde V77 existe para el ENCARGO, y su comentario dice por que: "una LISTA sin
-- opciones no es una lista: `controlDe` la degrada a TEXTO y el vocabulario deja
-- de existir sin que nadie avise. Le paso a `servicios_disponibles` y se
-- descubrio dos cortes despues." Extenderla a la PROPIEDAD era imposible
-- mientras esa clave siguiera activa -- V79, V80 y V81 lo dejaron escrito cada
-- uno en su cabecera --, y por eso este bloque va DESPUES del 6 y no antes.
--
-- Se comprueba sobre las claves ACTIVAS de los DOS sujetos y de TODOS los
-- ambitos, sistema y tenant. `activas` es la palabra que hace que retirar una
-- clave muda sea una solucion legitima y no un rodeo.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    mudas TEXT;
BEGIN
    SELECT string_agg(c.sujeto || '/' || c.clave
                      || coalesce(' (org ' || c.organizacion_id || ')', ' (sistema)'),
                      ', ' ORDER BY c.sujeto, c.clave)
      INTO mudas
      FROM catalogo_atributo c
     WHERE c.activo
       AND c.tipo_dato IN ('LISTA', 'LISTA_MULTIPLE')
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo
                          AND o.activo);
    IF mudas IS NOT NULL THEN
        RAISE EXCEPTION
            'V84: hay LISTAS activas sin vocabulario: %. Una LISTA sin opciones se degrada a '
            'TEXTO en el motor de captura y el trigger acepta cualquier cadena: la clave nace '
            'muda y nadie lo ve. O se le siembra vocabulario, o se retira con activo = false.',
            mudas;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 8. LAS ASERCIONES DEL ESTADO RESULTANTE.
--
-- Invariantes, no cifras escritas a mano donde una invariante sirve. Las cifras
-- literales que si aparecen -- 3 claves, 10 opciones nuevas, 10 filas de
-- aplicabilidad -- lo son porque son el CONTENIDO de esta migracion y no el
-- tamano de nada que crezca con el uso.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    nuevas          TEXT[] := ARRAY['estado_ocupacion', 'agua_desague', 'energia_electrica'];
    faltan          TEXT;
    mal_forma       TEXT;
    duplicadas      TEXT;
    tipos_ocupacion TEXT;
    sin_hecho       TEXT;
    cubiertos       INT;
    exigencias      TEXT;
    vocab           TEXT;
    gas_orden       TEXT;
    gas_opciones    INT;
    espejo          TEXT;
    cruce           TEXT;
    codigo_malo     TEXT;
    total_legado    BIGINT;
    clasificado     BIGINT;
    recuperables    BIGINT;
    ambiguos        BIGINT;
    no_inventar     BIGINT;
    sin_inventariar TEXT;
    con_linaje      BIGINT;
    previstos       BIGINT;
    escritos        BIGINT;
    activas         BIGINT;
    perdidas        TEXT;
    movidas         TEXT;
    opciones_ida    TEXT;
    valores_ida     TEXT;
BEGIN
    -- 8.1 Las tres nacieron, activas, del sistema, del sujeto y la forma correcta.
    SELECT string_agg(k, ', ') INTO faltan
      FROM unnest(nuevas) AS k
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.organizacion_id IS NULL AND c.clave = k
                          AND c.activo AND c.del_sistema AND c.sujeto = 'PROPIEDAD');
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'V84: estas claves no llegaron al catalogo de la PROPIEDAD: %', faltan;
    END IF;

    SELECT string_agg(c.clave || ' -> ' || c.tipo_dato || '/' || c.destino
                      || '/aplica_todos=' || c.aplica_todos, ', ') INTO mal_forma
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas)
       AND (c.tipo_dato <> 'LISTA' OR c.destino <> 'ATRIBUTO'
            OR c.campo_estructural IS NOT NULL OR c.aplica_todos);
    IF mal_forma IS NOT NULL THEN
        RAISE EXCEPTION 'V84: forma equivocada en: %. Las tres son LISTA, ATRIBUTO y con '
                        'aplicabilidad explicita por tipo.', mal_forma;
    END IF;

    -- Y exactamente una fila por clave: `catalogo_atributo` no tiene unico sobre
    -- (organizacion_id, clave), asi que una segunda definicion del sistema
    -- entraria sin ruido y `porClave` devolveria una de las dos por el orden del
    -- planificador.
    SELECT string_agg(x.clave || ' x' || x.veces, ', ') INTO duplicadas
      FROM (SELECT c.clave, count(*) AS veces
              FROM catalogo_atributo c
             WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas)
             GROUP BY c.clave HAVING count(*) > 1) AS x;
    IF duplicadas IS NOT NULL THEN
        RAISE EXCEPTION 'V84: hay claves del sistema definidas dos veces: %', duplicadas;
    END IF;

    -- 8.2 `estado_ocupacion` cubre EXACTAMENTE los siete tipos, y todas OPC.
    SELECT string_agg(t.tipo_propiedad, ',' ORDER BY t.tipo_propiedad) INTO tipos_ocupacion
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = 'estado_ocupacion';
    IF tipos_ocupacion IS DISTINCT FROM 'A,C,D,L,O,T,X' THEN
        RAISE EXCEPTION 'V84: estado_ocupacion aplica a [%] y tiene que aplicar a [A,C,D,L,O,T,X]',
            coalesce(tipos_ocupacion, '(ninguno)');
    END IF;

    -- 8.3 EL PAR, con asercion propia (D-C5-1 §7): no basta con que la clave
    --     exista. El hecho tiene que llegar a TODOS los tipos donde su condicion
    --     se pacta. Se mide en las dos direcciones para que no pueda salir verde
    --     por el universo vacio: primero los tipos DESCUBIERTOS, y despues
    --     cuantos quedan CUBIERTOS -- si `entrega_desocupado` desapareciera del
    --     catalogo, la primera consulta daria cero huecos y la segunda lo caza.
    --
    --     LAS DOS MITADES MIRAN EL CATALOGO DEL SISTEMA, y eso es una correccion
    --     de la primera version de esta migracion: sin `organizacion_id IS NULL`,
    --     una organizacion que declarase su propia `estado_ocupacion` habria
    --     tapado el hueco de la del sistema -- el par saldria cubierto para todos
    --     leyendo una clave que solo existe en un tenant. La asercion 8.2, que ya
    --     filtraba, quedaba entonces en desacuerdo con esta.
    SELECT string_agg(DISTINCT o.tipo_propiedad, ', ' ORDER BY o.tipo_propiedad) INTO sin_hecho
      FROM catalogo_atributo cond
      JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
      JOIN catalogo_atributo hecho ON hecho.clave = 'estado_ocupacion' AND hecho.activo
                                  AND hecho.organizacion_id IS NULL
     WHERE cond.clave = 'entrega_desocupado' AND cond.activo
       AND cond.organizacion_id IS NULL
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = hecho.id_catalogo_atributo
                          AND t.tipo_propiedad = o.tipo_propiedad);
    IF sin_hecho IS NOT NULL THEN
        RAISE EXCEPTION
            'V84: `entrega_desocupado` se pacta en % y `estado_ocupacion` no llega. Ahi el '
            'pacto seria el unico sitio donde cabe el hecho, y un pacto muere con su encargo.',
            sin_hecho;
    END IF;

    SELECT count(DISTINCT o.tipo_propiedad) INTO cubiertos
      FROM catalogo_atributo cond
      JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
      JOIN catalogo_atributo hecho ON hecho.clave = 'estado_ocupacion' AND hecho.activo
                                  AND hecho.organizacion_id IS NULL
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                   AND t.tipo_propiedad = o.tipo_propiedad
     WHERE cond.clave = 'entrega_desocupado' AND cond.activo
       AND cond.organizacion_id IS NULL;
    IF cubiertos <> 7 THEN
        RAISE EXCEPTION
            'V84: el par estado_ocupacion/entrega_desocupado esta cubierto en % tipos y tienen '
            'que ser 7. Un cero aqui significaria que la comprobacion de arriba salio verde '
            'sobre un conjunto vacio.', cubiertos;
    END IF;

    -- 8.4 LA EXIGENCIA EXACTA QUE DECIDIO EL TITULAR (D-1). Se comprueba el
    --     CONJUNTO completo de filas de las tres claves: una fila de mas o una
    --     exigencia distinta cambia quien puede publicar.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad || '=' || t.exigencia
                      || '/req=' || t.requerido, ', ' ORDER BY c.clave, t.tipo_propiedad)
      INTO exigencias
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas);
    IF exigencias IS DISTINCT FROM
       'agua_desague/A=OPC/req=false, agua_desague/T=PUB/req=false, '
       'energia_electrica/T=PUB/req=false, '
       'estado_ocupacion/A=OPC/req=false, estado_ocupacion/C=OPC/req=false, '
       'estado_ocupacion/D=OPC/req=false, estado_ocupacion/L=OPC/req=false, '
       'estado_ocupacion/O=OPC/req=false, estado_ocupacion/T=OPC/req=false, '
       'estado_ocupacion/X=OPC/req=false' THEN
        RAISE EXCEPTION 'V84: la exigencia de las claves nuevas quedo en [%]', exigencias;
    END IF;

    -- 8.5 EL VOCABULARIO, codigo a codigo y en orden. Una LISTA que nace muda es
    --     el defecto que este corte viene a cerrar, asi que no basta con "tiene
    --     opciones": tienen que ser ESTAS.
    SELECT string_agg(c.clave || ':' || o.valor, ' ' ORDER BY c.clave, o.orden) INTO vocab
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas) AND o.activo;
    IF vocab IS DISTINCT FROM
       'agua_desague:CONECTADO agua_desague:CON_FACTIBILIDAD_APROBADA agua_desague:SIN_SERVICIO '
       'energia_electrica:CONECTADO energia_electrica:CON_FACTIBILIDAD_APROBADA '
       'energia_electrica:SIN_SERVICIO '
       'estado_ocupacion:DESOCUPADO estado_ocupacion:OCUPADO_POR_EL_PROPIETARIO '
       'estado_ocupacion:OCUPADO_POR_INQUILINO estado_ocupacion:OCUPADO_POR_TERCEROS_SIN_TITULO' THEN
        RAISE EXCEPTION 'V84: el vocabulario de las claves nuevas quedo en [%]', vocab;
    END IF;

    -- `CON_EDIFICACION_A_DEMOLER` no pertenece a esta clave (D-C5-1 §3). Se
    --  comprueba en negativo y aparte porque la asercion de arriba ya lo cubre
    --  hoy, y dejaria de cubrirlo el dia que alguien anada una quinta opcion
    --  legitima y actualice la cadena sin releer la decision.
    IF EXISTS (SELECT 1 FROM catalogo_atributo c
                 JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
                WHERE c.organizacion_id IS NULL AND c.clave = 'estado_ocupacion'
                  AND o.valor = 'CON_EDIFICACION_A_DEMOLER') THEN
        RAISE EXCEPTION
            'V84: `CON_EDIFICACION_A_DEMOLER` no es un estado de ocupacion: mezcla quien ocupa '
            'con que hay construido, y las dos cosas pueden ser ciertas a la vez. Ese hecho vive '
            'en `edificacion_existente`, que es del terreno y va en 5B.';
    END IF;

    -- 8.6 `gas`: gana una opcion, y NADA MAS. Seis opciones, orden denso 1..6,
    --     y la nueva en la posicion 3 -- entre la tuberia de la calle y la
    --     instalacion en la puerta.
    SELECT count(*) INTO gas_opciones
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = 'gas';
    IF gas_opciones <> 6 THEN
        RAISE EXCEPTION 'V84: gas tiene % opciones y tiene que tener 6', gas_opciones;
    END IF;

    SELECT string_agg(o.orden || ':' || o.valor, ' ' ORDER BY o.orden) INTO gas_orden
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = 'gas';
    IF gas_orden IS DISTINCT FROM
       '1:SIN_RED_CERCANA 2:RED_EN_LA_VIA 3:CON_FACTIBILIDAD_APROBADA 4:INSTALADO '
       '5:GLP_TANQUE_EXTERNO 6:GLP_BALONES' THEN
        RAISE EXCEPTION 'V84: el vocabulario de gas quedo en [%]', gas_orden;
    END IF;

    -- Su aplicabilidad NO se movio, y en particular NO llego a `X` (D-2). Se
    -- compara contra la foto y no contra una cadena literal: lo que se afirma es
    -- que esta migracion no la toco, sea cual sea.
    IF (SELECT string_agg(tipo_propiedad || '=' || exigencia, ',' ORDER BY tipo_propiedad)
          FROM v84_tipo_antes WHERE organizacion_id IS NULL AND clave = 'gas')
       IS DISTINCT FROM
       (SELECT string_agg(t.tipo_propiedad || '=' || t.exigencia, ',' ORDER BY t.tipo_propiedad)
          FROM catalogo_atributo c
          JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
         WHERE c.organizacion_id IS NULL AND c.clave = 'gas') THEN
        RAISE EXCEPTION 'V84: la aplicabilidad de gas cambio, y D-2 dice que conserva su concepto.';
    END IF;
    IF EXISTS (SELECT 1 FROM catalogo_atributo c
                 JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
                WHERE c.organizacion_id IS NULL AND c.clave = 'gas' AND t.tipo_propiedad = 'X') THEN
        RAISE EXCEPTION 'V84: gas llego a X, y D-2 dice expresamente que no se extiende a X.';
    END IF;

    -- 8.7 `servicios_disponibles`: RETIRADA, no borrada, y con todo lo suyo en pie.
    IF NOT EXISTS (SELECT 1 FROM catalogo_atributo
                    WHERE organizacion_id IS NULL AND clave = 'servicios_disponibles'
                      AND NOT activo AND del_sistema) THEN
        RAISE EXCEPTION
            'V84: `servicios_disponibles` tiene que seguir EXISTIENDO y estar `activo = false`. '
            'Si desaparecio, se borro una clave del sistema; si sigue activa, la retirada no se '
            'aplico y la guarda del bloque 7 no puede haber medido nada.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                     JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
                    WHERE c.organizacion_id IS NULL AND c.clave = 'servicios_disponibles'
                      AND t.tipo_propiedad = 'T') THEN
        RAISE EXCEPTION 'V84: se perdio la aplicabilidad de servicios_disponibles a T.';
    END IF;

    -- 8.8 EL LEGADO: NINGUNA CADENA SE QUEDA SIN INVENTARIAR.
    --
    -- LA PRIMERA VERSION DE ESTE BLOQUE ERA VACUA, y la auditoria lo demostro.
    -- Comparaba `total_legado` con `clasificado`, pero `v84_reparto` se
    -- construye con un LEFT JOIN sobre ESE MISMO conjunto y un
    -- `coalesce(veredicto, 'NO_INVENTARIADO')`: clasifica el 100 % de las filas
    -- por construccion, incluso con el acta vacia. Insertando 'sin agua, con
    -- luz' --el contraejemplo que cita la cabecera del bloque 5-- la igualdad
    -- seguia siendo cierta y la migracion pasaba.
    --
    -- La invariante de verdad se asevera sobre el UNIVERSO PREVIO --la foto del
    -- bloque 0-- y sobre lo que el acta NO cubre: toda cadena que exista en la
    -- base tiene que estar EN EL ACTA. Una que no lo este no se adivina, y
    -- tampoco se deja pasar contada: la migracion PARA y la NOMBRA, para que
    -- quien escriba el acta decida si es recuperable o ambigua. Eso, y no un
    -- recuento, es lo que significa "ningun valor sin destino".
    --
    -- `no_inventar = 0` es una cifra legitima y no contradice la regla de no
    -- escribir `= 0`: lo prohibido era afirmar que hay CERO FILAS DE LEGADO --en
    -- dev seria cierto y en la base de pruebas, con 322 filas, mentira--. Aqui
    -- se afirma otra cosa distinta: que ninguna de las que haya, sean 0 o 322,
    -- cayo fuera del acta.
    SELECT count(*) INTO total_legado
      FROM v84_valores_antes WHERE clave = 'servicios_disponibles';
    SELECT count(*),
           count(*) FILTER (WHERE veredicto = 'RECUPERABLE'),
           count(*) FILTER (WHERE veredicto = 'AMBIGUO'),
           count(*) FILTER (WHERE veredicto = 'NO_INVENTARIADO')
      INTO clasificado, recuperables, ambiguos, no_inventar
      FROM v84_reparto;

    IF no_inventar > 0 THEN
        SELECT string_agg(x.texto, ' | ' ORDER BY x.texto) INTO sin_inventariar
          FROM (SELECT DISTINCT coalesce(quote_literal(valor_texto), '(sin texto)') AS texto
                  FROM v84_reparto WHERE veredicto = 'NO_INVENTARIADO') AS x;
        RAISE EXCEPTION
            'V84: % valores de servicios_disponibles llevan cadenas que el acta no inventaria: '
            '%. No se traducen por parecido ni se cuentan como FALTANTE en silencio: se anaden '
            'al acta del bloque 5 --con su veredicto y su motivo-- o se explica por que no cabe '
            'ninguno. Un valor sin destino es un dato que ni se migro ni se declaro.',
            no_inventar, sin_inventariar;
    END IF;

    -- Y el acta se pronuncio sobre TODO el universo previo, no sobre una parte.
    -- Compara dos fuentes distintas --la foto del bloque 0 y el clasificador--,
    -- asi que ya no es una identidad: caza una fila de legado aparecida o
    -- desaparecida entre la foto y el reparto.
    IF clasificado <> total_legado THEN
        RAISE EXCEPTION
            'V84: la foto previa tiene % valores de servicios_disponibles y el acta se '
            'pronuncio sobre %. El universo se movio mientras la migracion corria.',
            total_legado, clasificado;
    END IF;
    IF recuperables + ambiguos + no_inventar <> clasificado THEN
        RAISE EXCEPTION 'V84: el acta clasifico % filas y los tres veredictos suman %',
            clasificado, recuperables + ambiguos + no_inventar;
    END IF;

    -- Y NINGUNA se perdio por el camino: el legado se conserva ENTERO, incluido
    -- lo ambiguo. Se compara contra la foto del bloque 0, por conjunto.
    SELECT string_agg(v.id_atributo_propiedad::text, ', ') INTO perdidas
      FROM v84_valores_antes v
     WHERE v.clave = 'servicios_disponibles'
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_atributo_propiedad = v.id_atributo_propiedad
                          AND a.valor_texto IS NOT DISTINCT FROM v.valor_texto);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION
            'V84: se perdieron o se reescribieron valores de servicios_disponibles: %. '
            'Retirar la clave no toca ni un valor.', perdidas;
    END IF;

    -- LO QUE SE ESCRIBIO ES EXACTAMENTE LO QUE EL ACTA AUTORIZO, Y NADA MAS.
    --
    -- `escritos` no cuenta intenciones: cuenta las filas de las dos claves
    -- nuevas que NO estaban en la foto, y como las dos claves nacen en esta
    -- misma migracion, esas filas solo pueden haberlas escrito estos bloques.
    -- Por eso esta comparacion SI mide en `controllocal_dev`, donde el acta no
    -- autoriza nada: afirma que la migracion NO RELLENO `PROP-0024`, que es una
    -- de las cosas que se comprometio a no hacer.
    SELECT count(*) INTO previstos
      FROM v84_reparto r
      CROSS JOIN LATERAL (VALUES (r.destino_agua), (r.destino_energia)) AS d(valor)
     WHERE r.veredicto = 'RECUPERABLE' AND d.valor IS NOT NULL;
    SELECT count(*) INTO escritos
      FROM atributo_propiedad a
     WHERE a.clave IN ('agua_desague', 'energia_electrica')
       AND NOT EXISTS (SELECT 1 FROM v84_valores_antes v
                        WHERE v.id_atributo_propiedad = a.id_atributo_propiedad);
    IF escritos <> previstos THEN
        RAISE EXCEPTION
            'V84: el acta autorizo % valores de agua_desague/energia_electrica y hay % escritos. '
            'Ninguna de las dos se rellena por fuera del reparto: el dato se desbloquea '
            'visitando, no escribiendo.', previstos, escritos;
    END IF;

    -- Y todo lo repartido dejo linaje (V83).
    SELECT count(*) INTO con_linaje
      FROM rastro_valor_gobernado
     WHERE evidencia_ref = 'V84 reparto de servicios_disponibles (acta de clasificacion)';
    IF con_linaje <> escritos THEN
        RAISE EXCEPTION 'V84: se repartieron % valores y quedo linaje de %', escritos, con_linaje;
    END IF;

    -- 8.9 LAS INVARIANTES DE SIEMPRE, sobre TODO el catalogo y no solo sobre lo
    --     nuevo. Son las que se rompen desde fuera del corte.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO espejo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE t.requerido <> (t.exigencia = 'ALT');
    IF espejo IS NOT NULL THEN
        RAISE EXCEPTION 'V84: requerido y exigencia divergen en: %', espejo;
    END IF;

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
        RAISE EXCEPTION 'V84: claves con la aplicabilidad en la tabla del otro sujeto: %', cruce;
    END IF;

    SELECT string_agg(c.clave || '/' || o.valor, ', ') INTO codigo_malo
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND o.valor !~ '^[A-Z][A-Z0-9_]*$';
    IF codigo_malo IS NOT NULL THEN
        RAISE EXCEPTION 'V84: codigos que no son UPPER_SNAKE empezando por letra: %', codigo_malo;
    END IF;

    -- El suelo del gate del modelo universal (>= 51 claves del sistema activas).
    -- Retirar una lo baja; se comprueba aqui para que la migracion no deje al
    -- gate en rojo y se descubra en el cierre.
    SELECT count(*) FILTER (WHERE activo) INTO activas
      FROM catalogo_atributo WHERE del_sistema;
    IF activas < 51 THEN
        RAISE EXCEPTION 'V84: quedan % claves del sistema activas y el suelo del gate es 51', activas;
    END IF;

    -- 8.10 NADA MAS SE MOVIO. Contra la foto, fila a fila, excluyendo lo que
    --      esta migracion viene a cambiar. Un recuento suelto no serviria:
    --      cuadra igual si una baja y otra sube.
    SELECT string_agg(a.clave || ': activo ' || a.activo || ' -> ' || c.activo
                      || ', orden ' || a.orden || ' -> ' || c.orden, ', ')
      INTO movidas
      FROM v84_claves_antes a
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = a.id_catalogo_atributo
     WHERE NOT (a.organizacion_id IS NULL AND a.clave = 'servicios_disponibles')
       AND (c.activo IS DISTINCT FROM a.activo
         OR c.orden IS DISTINCT FROM a.orden
         OR c.tipo_dato IS DISTINCT FROM a.tipo_dato
         OR c.sujeto IS DISTINCT FROM a.sujeto
         OR c.destino IS DISTINCT FROM a.destino
         OR c.aplica_todos IS DISTINCT FROM a.aplica_todos);
    IF movidas IS NOT NULL THEN
        RAISE EXCEPTION 'V84: esta migracion movio claves que no debia tocar: %', movidas;
    END IF;

    -- Ninguna clave del catalogo desaparecio.
    SELECT string_agg(a.clave, ', ') INTO perdidas
      FROM v84_claves_antes a
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.id_catalogo_atributo = a.id_catalogo_atributo);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION 'V84: desaparecieron claves del catalogo: %', perdidas;
    END IF;

    -- Ninguna fila de aplicabilidad desaparecio ni cambio de exigencia.
    SELECT string_agg(a.clave || '/' || a.tipo_propiedad, ', ') INTO perdidas
      FROM v84_tipo_antes a
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = a.id_catalogo_atributo
                          AND t.tipo_propiedad = a.tipo_propiedad
                          AND t.exigencia = a.exigencia
                          AND t.requerido = a.requerido);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION 'V84: se perdieron o se movieron filas de aplicabilidad: %', perdidas;
    END IF;

    -- Ninguna OPCION del catalogo desaparecio ni cambio de codigo. `gas` cambia
    -- de `orden` y por eso el `orden` se compara aparte, arriba: aqui se afirma
    -- que el VOCABULARIO --lo que se compara entre organizaciones-- esta entero.
    SELECT string_agg(a.clave || '/' || a.valor, ', ') INTO opciones_ida
      FROM v84_opciones_antes a
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = a.id_catalogo_atributo
                          AND o.valor = a.valor AND o.activo = a.activo);
    IF opciones_ida IS NOT NULL THEN
        RAISE EXCEPTION 'V84: desaparecieron o se desactivaron opciones del catalogo: %', opciones_ida;
    END IF;

    -- Y NINGUN VALOR ESCRITO se perdio ni cambio, de ninguna clave.
    SELECT string_agg(a.clave || '#' || a.id_atributo_propiedad, ', ') INTO valores_ida
      FROM v84_valores_antes a
     WHERE NOT EXISTS (SELECT 1 FROM atributo_propiedad p
                        WHERE p.id_atributo_propiedad = a.id_atributo_propiedad
                          AND p.clave = a.clave
                          AND p.valor_texto    IS NOT DISTINCT FROM a.valor_texto
                          AND p.valor_numero   IS NOT DISTINCT FROM a.valor_numero
                          AND p.valor_booleano IS NOT DISTINCT FROM a.valor_booleano
                          AND p.valor_fecha    IS NOT DISTINCT FROM a.valor_fecha
                          AND p.valor_moneda   IS NOT DISTINCT FROM a.valor_moneda);
    IF valores_ida IS NOT NULL THEN
        RAISE EXCEPTION 'V84: se perdieron o se reescribieron valores del inmueble: %', valores_ida;
    END IF;

    RAISE NOTICE
        'V84: nacen estado_ocupacion (OPC en los 7), agua_desague (PUB en T, OPC en A) y '
        'energia_electrica (PUB en T), las tres CON vocabulario; gas pasa a 6 opciones; '
        'servicios_disponibles queda activo=false conservando sus % valores '
        '(% recuperables, % ambiguos, % no inventariados); quedan % claves del sistema activas.',
        total_legado, recuperables, ambiguos, no_inventar, activas;
END $$;

-- ---------------------------------------------------------------------
-- 9. La foto se retira con DROP explicito. Ver el bloque 0.
-- ---------------------------------------------------------------------
DROP TABLE v84_reparto;
DROP TABLE v84_valores_antes;
DROP TABLE v84_opciones_antes;
DROP TABLE v84_tipo_antes;
DROP TABLE v84_claves_antes;
