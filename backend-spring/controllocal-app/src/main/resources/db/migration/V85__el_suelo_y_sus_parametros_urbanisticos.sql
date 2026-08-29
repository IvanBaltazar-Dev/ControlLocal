-- =====================================================================
-- V85 - Corte 5, subtanda 5B: el suelo, y lo que la norma deja hacer con el
--
-- Encargo congelado: `docs/ai/encargo-corte-5-terreno.md`, decisiones D-1..D-7
-- del titular (2026-08-25). Diseno de las claves: `docs/ai/auditoria-profundidad
-- -inmobiliaria.md` §3.8. Subtanda 5A cerrada en `67d3a56` (`V84`).
--
-- ---------------------------------------------------------------------
-- QUE HUECO CIERRA
--
-- Hasta hoy BROX describe un TERRENO con DIECISEIS caracteristicas, y NINGUNA
-- habla del suelo como suelo. Medidas contra `controllocal_dev` el 2026-08-29,
-- en su orden de catalogo:
--
--     metraje_total, antiguedad_anios, estacionamientos, frente, zonificacion,
--     area_terreno, partida_registral, oficina_registral, area_segun_partida,
--     cargas_gravamenes, gas, agua_desague, energia_electrica,
--     acceso_vehiculo_maximo, via_de_acceso, estado_ocupacion
--
-- Cinco son medidas o identidad que valen para cualquier activo, cuatro son
-- registrales, cuatro son servicios, una es la via como texto libre, una la
-- ocupacion -- y `area_terreno` repite la superficie que ya esta en
-- `metraje_total`. Con eso, dos terrenos de 500 m2 en el mismo distrito son la
-- MISMA FILA: uno puede ser urbano habilitado con 8 pisos de altura normativa y
-- el otro un eriazo en proceso, con CIRA pendiente y una trocha por delante. La
-- diferencia de precio entre esos dos no es un matiz: es el negocio entero.
--
-- (El prototipo `docs/ai/modelo/motor-captura.js` imprime NUEVE para el terreno.
--  Esa cifra es del contrato-dato, que declara 22 de las 123 claves del Core --
--  la deriva esta anotada como N18 en `pendientes-brox.md` --, y NO es el
--  catalogo. Se dice aqui porque las dos cifras conviven en el repositorio y la
--  primera version de esta cabecera cito la equivocada.)
--
-- Lo que falta no es "mas campos". Son TRES preguntas que un comprador de suelo
-- hace siempre y BROX no sabe registrar:
--
--   1. QUE ES este suelo             condicion_terreno, situacion_registral,
--                                    zona_de_riesgo, restriccion_arqueologica
--   2. COMO ES                       fondo, posicion_en_manzana, topografia,
--                                    tipo_via_acceso, estado_via,
--                                    edificacion_existente, cercado
--   3. QUE DEJA HACER LA NORMA       certificado_parametros_vigente,
--                                    altura_normativa_pisos,
--                                    coeficiente_edificacion, area_libre_minima,
--                                    retiro_municipal, lote_minimo_normativo,
--                                    usos_compatibles
--
-- El bloque 3 es el que convierte un terreno en un producto comparable: area x
-- coeficiente de edificacion = area vendible, y con eso una inmobiliaria decide
-- en dos minutos. Hoy esa cuenta no se puede hacer con lo que BROX guarda.
--
-- ---------------------------------------------------------------------
-- LA EXIGENCIA, Y POR QUE SOLO UNA `PUB` (D-1 y D-3)
--
-- La columna `nivel` de §3.8 propone `PUB` para cinco de estas claves
-- --`situacion_registral`, `fondo`, `posicion_en_manzana`,
-- `altura_normativa_pisos` y `tipo_via_acceso`--. ESA COLUMNA ES PROPUESTA, NO
-- AUTORIDAD, y aqui se sigue lo que el titular decidio:
--
--   * D-1: "No se eleva nada mas a PUB en este corte."
--   * D-3: `condicion_terreno` es `PUB` --no `ALT`--, enunciada literalmente por
--          el titular el 2026-08-25.
--
-- => `condicion_terreno` en `T` es la UNICA `PUB` nueva. Las otras diecisiete
--    nacen `OPC`, y ninguna es `ALT`, asi que las 18 llevan `requerido = false`.
--
-- EL PRECEDENTE QUE LO SOSTIENE, MEDIDO: en el Corte 4 la auditoria proponia
-- `PUB` para OCHO claves --`clase_edificio`, `metraje_arrendable`, `aforo_itse`,
-- `certificado_itse`, `area_libre`, `capacidad_portante_piso`,
-- `acceso_vehiculo_maximo` y `muelles_carga`-- y LAS OCHO NACIERON `OPC`. En el
-- catalogo aplicado no hay ni una sola `PUB` sin decision explicita del titular:
-- `tipo_acceso`/L (V82), y `agua_desague`/T y `energia_electrica`/T (V84, D-1).
-- Esta es la cuarta, y tambien la enuncio el titular.
--
-- `PUB` no bloquea el alta -- eso solo lo hace `ALT` -- y si bloquea PUBLICAR:
-- `AtributoPropiedadRepository.clavesQueImpidenPublicar` filtra
-- `exigencia in ('ALT','PUB')`. Y el bloqueo INFORMA: desde `35cf09c` la
-- PROPIEDAD reporta su deuda de publicacion en
-- `PropiedadResponse.faltanParaPublicar`, con el ROTULO de cada clave.
--
-- EFECTO MEDIDO EN LA CARTERA, ANTES DE APLICAR ESTO (controllocal_dev,
-- 2026-08-29): 26 propiedades, L=21, O=2, C=1, D=1 y UN solo terreno,
-- `PROP-0024` (id 3261), que lleva `metraje = 1200` y `zonificacion = RDM` y
-- NADA MAS -- ni un atributo mas, medido fila a fila.
--
--   => TRAS 5A ESTABA BLOQUEADO POR DOS CLAVES `PUB` (agua y luz).
--      TRAS 5B LO ESTA POR TRES: se le suma `condicion_terreno`.
--
-- ESO ES EL RESULTADO BUSCADO, no un fallo. Un anuncio de terreno que no dice si
-- el suelo esta habilitado no es una oferta: 500 m2 habilitados en Surco y
-- 500 m2 rusticos en Pachacamac son hoy la misma ficha, y no valen lo mismo ni
-- de lejos. Lo que D-3 evita es la otra mitad --que la clave impida REGISTRAR--,
-- porque un agente puede conocer un terreno antes de poder confirmar su
-- condicion.
--
-- ---------------------------------------------------------------------
-- LAS SIETE `LISTA` NACEN CON SU VOCABULARIO, EN LA MISMA SENTENCIA
--
-- Es la leccion de `servicios_disponibles`, que 5A acaba de cerrar: una LISTA
-- sin opciones NO ES UNA LISTA. `MotorDeCaptura.controlDe` la degrada a TEXTO y
-- `exigir_atributo_gobernado` acepta cualquier cadena, porque su comprobacion de
-- vocabulario esta condicionada a que haya opciones. Aquella clave estuvo muda
-- cuatro cortes y produjo texto libre que hubo que declarar AMBIGUO.
--
-- Ademas ya no seria posible dejarlo pasar: 5A dejo puesta la guarda "ninguna
-- LISTA activa sin vocabulario" para los DOS sujetos y los DOS ambitos, dentro
-- de `V84` y en `gate-modelo-universal.sql`. Si estas siete nacieran mudas, la
-- migracion abortaria contra su propia guarda.
--
-- ---------------------------------------------------------------------
-- D-7: `area_terreno` SE RETIRA DE `T`, Y SOLO DE `T`
--
-- `metraje_total` es la superficie canonica de un terreno: es `ESTRUCTURAL`
-- --columna `propiedad.metraje`, medida NOT NULL-- y es `ALT` en los SIETE
-- tipos, asi que todo terreno registrado la tiene por fuerza. En un TERRENO las
-- dos claves nombran la MISMA VERDAD --no hay superficie techada distinta del
-- suelo cuando no hay nada techado-- y dos claves para una verdad no comparan
-- nada: un agente escribe 500 en una, otro en la otra, y el buscador no puede
-- ordenar por superficie.
--
-- DICHO CON LOS ROTULOS QUE HAY HOY, que es de donde viene la confusion:
-- `area_terreno` se llama "Área de terreno" (V68) y `metraje_total` se llama
-- "Metraje total" -- medido, no supuesto. La auditoria propone renombrarlo a
-- "Área techada" (§3.1), pero eso es del Corte 1 y esta APLAZADO, asi que hoy
-- el formulario de un terreno pregunta "Metraje total" y "Área de terreno" una
-- debajo de otra sin nada que diga en que se diferencian. No se diferencian.
--
-- En `A` y `C` NO es la misma verdad y por eso NO SE TOCAN: una casa se tasa por
-- el PAR (terreno, construida), y una nave tiene patio ademas de techo.
--
--     ANTES   area_terreno -> A=OPC, C=OPC, T=OPC
--     DESPUES area_terreno -> A=OPC, C=OPC
--
-- LA REGLA DEL DATO, ESCRITA COMO INVARIANTE Y NUNCA COMO LA CIFRA QUE HOY DA:
--
--   NINGUN valor de `area_terreno` sobre una propiedad `T` se pierde sin
--   COINCIDIR con `metraje_total`, o sin quedar declarado FALTANTE y CONTADO.
--
-- El clasificador del bloque 4 es una comparacion NUMERICA contra la columna
-- canonica, no un acta de cadenas como la de V84, y por eso es TOTAL: toda fila
-- cae en uno de los cuatro veredictos y no existe el bucket "no inventariado".
--
--     COINCIDE      valor_numero = propiedad.metraje. El dato esta ENTERO en su
--                   sitio canonico, que ademas es ALT y NOT NULL: retirar la
--                   fila no pierde nada, porque su reemplazo ya esta activo Y
--                   YA CONTIENE EL MISMO NUMERO. Se retira dejando LINAJE
--                   (`RETIRADA`, con el valor en `hallado_numero`), que es la
--                   ultima vez que ese dato existe como fila.
--     DISCREPANTE   las dos superficies no coinciden. NO SE SABE cual es la
--                   correcta, asi que NO se pisa `metraje_total` --seria
--                   reinterpretar-- y NO se borra la fila --seria perder el
--                   unico rastro del desacuerdo--. Se CONSERVA entera, se CUENTA
--                   y se NOMBRA en el NOTICE final.
--     SIN_VALOR     una fila de `area_terreno` sin numero. No deberia existir
--                   --el trigger lo impide-- y si existe se conserva y se cuenta.
--     SIN_CANONICA  una propiedad sin `metraje`. `propiedad.metraje` es NOT NULL,
--                   asi que la asercion 8.9 lo trata como imposible y ABORTA.
--
-- POR QUE LO DISCREPANTE NO ABORTA LA MIGRACION, y si lo hacia el caso de V84:
-- alli el defecto era del ACTA --una cadena que nadie habia inventariado--, y lo
-- correcto era parar para que un humano la clasificara. Aqui no hay acta que
-- completar: la clasificacion es total. Un desacuerdo entre dos superficies es
-- un estado LEGITIMO del dato --alguien escribio dos numeros distintos-- y
-- bloquear el modelo por el dejaria el esquema rehen de una fila mal tecleada.
-- Se conserva, se cuenta y se nombra, que es lo que el North Star pide.
--
-- LO QUE SE MIDIO ANTES DE ESCRIBIR ESTO (2026-08-29):
--
--     controllocal_dev            0 filas de `area_terreno`, de ningun tipo
--     controllocal_repositorios   1089 filas: A=307, C=475, T=307
--                                 las 307 de `T` con valor_numero = 500.0000
--                                 contra metraje = 500.00  ->  0 discrepantes
--
-- ESO ES RESIDUO DE HOY, NO INVARIANTE, y se sabe DE DONDE sale: el fixture
-- TERRENO de `ConservacionDeLaEdicionIntegrationTest` escribia
-- `metraje_total = 500.00` y `area_terreno = 500.00` en la misma alta, asi que
-- coincidian POR CONSTRUCCION. Una asercion "0 discrepantes" mediria ese fixture
-- y no el modelo, y mentiria en cuanto alguien registrara un terreno a mano.
-- Es exactamente la trampa que 5A documento con `servicios_disponibles`.
--
-- ---------------------------------------------------------------------
-- EL ORDEN DE ESTA MIGRACION ES PARTE DEL ENCARGO
--
--     0  foto del estado previo, en tablas TEMP con DROP explicito al final y
--        NUNCA `ON COMMIT DROP` -- no sobrevive a como Flyway envuelve la
--        transaccion (leccion de V78, repetida en V82 y V84)
--     1  nacen las 18 claves, las siete LISTA **con** su vocabulario
--     2  sus filas de aplicabilidad, con la exigencia de D-1/D-3
--     3  rotulos y ayudas con acentos
--     4  **solo entonces** el dato de `area_terreno` en `T`, con linaje
--     5  **solo entonces** la retirada de la fila de aplicabilidad `T`
--     6  aserciones
--     7  comparacion contra la foto, por CONJUNTOS y no por totales
--
-- QUE PASA SI SE INVIERTEN 4 Y 5, MEDIDO CONTRA ESTA IMPLEMENTACION Y NO
-- SUPUESTO -- misma disciplina que obligo a corregir la cabecera de V84:
-- HOY SERIA INOCUO. El bloque 4 solo hace DELETE sobre `atributo_propiedad` e
-- INSERT sobre `rastro_valor_gobernado`, y ninguno de los dos comprueba
-- aplicabilidad: `tg_atributo_gobernado` es `BEFORE INSERT OR UPDATE`
-- --verificado con `pg_get_triggerdef`, no lleva DELETE-- y
-- `rastro_valor_gobernado` no tiene ninguna guarda de catalogo.
--
-- El orden se respeta igual, y por la razon que SI se sostiene: el dia que el
-- tratamiento del dato tenga que ESCRIBIR algo --mover un valor, completar una
-- columna, sembrar un reemplazo--, la aplicabilidad ya retirada lo rechazaria
-- con `check_violation` y el legado se quedaria sin destino. El orden es lo
-- unico que lo impide, y ese dia nadie va a estar leyendo esta cabecera.
--
-- Lo que NO es inocuo es invertir 1 y 2 con 4: sin las claves nacidas, el
-- tratamiento del dato mira un modelo incompleto.
--
-- ---------------------------------------------------------------------
-- LO QUE ESTA MIGRACION **NO** HACE, y no por olvido
--
--   * NO crea `manzana_lote`. Esta FUERA del Corte 5 por D-6, y expresamente
--     "no se crea como atributo provisional".
--   * NO toca `torre_bloque` (ya existe en `D` desde V80), ni `latitud`/
--     `longitud` (estructurales, ya existen), ni anade `C` a `frente` (eso es
--     del Corte 1, aplazado), ni toca `via_de_acceso` -- que es TEXTO y sigue
--     viva en A,T desde V81. `via_de_acceso` y `tipo_via_acceso` CONVIVEN: la
--     primera dice CUAL es la via ("Panamericana Sur km 32"), la segunda de que
--     CLASE es (avenida, pasaje, trocha). Una no sustituye a la otra, y por eso
--     nacen contiguas en el `orden` -- 940, 942, 944 -- para que un agente las
--     lea como la misma conversacion y no rellene una creyendo que es la otra.
--   * NO sube `area_terreno` en `C` a `ALT`. §3.1 lo propone y lo atribuye a
--     "5B", pero el alcance congelado de 5B dice literalmente que "A y C NO se
--     tocan", y elevar una clave a `ALT` cambia quien puede REGISTRAR. Queda
--     como propuesta viva de la auditoria, sin aplicar.
--   * NO toca `aplica_todos` de ninguna clave (D-5), ni extiende `gas` a `X`
--     (D-2), ni nada de 5A -- `estado_ocupacion`, `agua_desague`,
--     `energia_electrica`, `servicios_disponibles` --, que esta CERRADO.
--   * NO borra ni un solo valor DISCREPANTE, ni pisa ningun `metraje_total`.
--   * NO rellena `condicion_terreno` en `PROP-0024`. Se desbloquea el HECHO
--     VERIFICADO, no el relleno: el dato se recupera visitando o pidiendo el
--     certificado, no traduciendo.
--   * NO toca Angular. El SPA no conoce claves: `MotorDeCaptura.controlDe`
--     deriva el control del vocabulario y el formulario sale del catalogo. Y
--     KAIROS tampoco: `InterpreteDeterminista` pide el catalogo del tipo a
--     `ClienteBrox.catalogoDe` y no lleva ninguna lista propia -- Web y KAIROS
--     reciben la MISMA definicion del Core, que es justo lo que esto demuestra.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. LA FOTO DEL ESTADO PREVIO.
--
-- Sin ella "no se movio nada mas" y "no se perdio nada" serian suposiciones: un
-- recuento final cuadra igual si una fila desaparece y otra aparece. Se compara
-- el CONJUNTO, no el total (leccion de V82).
-- ---------------------------------------------------------------------
CREATE TEMP TABLE v85_claves_antes AS
SELECT c.id_catalogo_atributo, c.organizacion_id, c.clave, c.tipo_dato, c.unidad, c.sujeto,
       c.destino, c.campo_estructural, c.activo, c.aplica_todos, c.del_sistema, c.orden
  FROM catalogo_atributo c;

CREATE TEMP TABLE v85_tipo_antes AS
SELECT t.id_catalogo_atributo, c.clave, c.organizacion_id,
       t.tipo_propiedad, t.exigencia, t.requerido
  FROM catalogo_atributo_tipo t
  JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo;

CREATE TEMP TABLE v85_opciones_antes AS
SELECT o.id_catalogo_atributo, c.clave, c.organizacion_id, o.valor, o.rotulo, o.orden, o.activo
  FROM catalogo_atributo_opcion o
  JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo;

-- Los VALORES escritos, enteros, con el TIPO de su inmueble y el metraje
-- canonico al lado: es la foto que sostiene a la vez "nada de lo que habia se
-- perdio" y "lo unico que se fue coincidia con su reemplazo".
CREATE TEMP TABLE v85_valores_antes AS
SELECT a.id_atributo_propiedad, a.organizacion_id, a.id_propiedad, a.clave,
       a.valor_texto, a.valor_numero, a.valor_booleano, a.valor_fecha, a.valor_moneda,
       p.tipo_inmueble, p.metraje
  FROM atributo_propiedad a
  JOIN propiedad p ON p.id_propiedad = a.id_propiedad;

-- ---------------------------------------------------------------------
-- 1. NACEN LAS 18 CLAVES, Y LAS SIETE `LISTA` **CON** SU VOCABULARIO.
--
-- En UNA SOLA SENTENCIA por grupo, con la CTE que devuelve los ids reales -- que
-- la secuencia genera y no son los mismos en dev, en pruebas y en produccion --
-- y siembra el vocabulario con ellos. Patron de V80, V81 y V84.
--
-- `orden`: las dos claves de la VIA se INTERCALAN detras de `via_de_acceso`
-- (940) en 942 y 944, porque son la misma conversacion y separarlas las pondria
-- en dos pantallas. Las otras dieciseis continuan desde 960 -- 950 lo ocupa
-- `estado_ocupacion` desde V84 -- en cuatro grupos de diez que siguen el orden
-- en que se recorre un terreno: QUE ES, COMO ES, QUE HAY ENCIMA, QUE DEJA HACER
-- LA NORMA. Los huecos de diez existen para que un corte posterior pueda
-- intercalar sin renumerar.
--
-- Los INSERT van en ASCII a proposito, para que se lean en una terminal sin
-- UTF-8; los acentos de `rotulo` y `ayuda` los repone el bloque 3. Patron de
-- V68, V79, V80, V81 y V84.
--
-- `valor_minimo`, `valor_maximo` y `longitud_maxima` siguen la convencion
-- MEDIDA del catalogo (2026-08-29): las DECIMAL de superficie y longitud llevan
-- minimo 0 y ningun maximo; las ENTERO de recuento llevan minimo 0 cuando el
-- cero significa algo; las TEXTO llevan longitud explicita.
--
--   * `altura_normativa_pisos` lleva minimo 0 y no 1: un suelo puede estar en
--     una zona no edificable, y ahi CERO PISOS es la respuesta correcta. Poner 1
--     obligaria a mentir o a callar. Es la misma regla que `estacionamientos`:
--     0 = no tiene, nulo = no se sabe.
--   * `area_libre_minima` lleva maximo 100 porque su UNIDAD es el por ciento del
--     lote, y un area libre minima superior al 100 % del lote no existe. No es
--     una cota de frecuencia --de esas no se ponen--: es la definicion de la
--     unidad. Su `ayuda` dice "en por ciento" para que nadie escriba 0,30.
--   * `coeficiente_edificacion` NO lleva maximo: 1,5 o 12 son igual de reales
--     segun la zona, y cualquier techo aqui seria inventado.
-- ---------------------------------------------------------------------

-- 1.a QUE ES este suelo. Las dos LISTA de identidad, con su vocabulario.
WITH nacen AS (
    INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                                   aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                                   valor_minimo, valor_maximo, longitud_maxima,
                                   destino, campo_estructural)
    VALUES
        (NULL, 'condicion_terreno', 'Condicion del terreno', 'LISTA', NULL,
         false, true, 960, 'PROPIEDAD', NULL,
         'En que situacion urbanistica esta el suelo.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL),
        (NULL, 'situacion_registral', 'Situacion registral', 'LISTA', NULL,
         false, true, 970, 'PROPIEDAD', NULL,
         'Como esta inscrito el inmueble.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL)
    RETURNING id_catalogo_atributo, clave
)
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT n.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM nacen n
  JOIN (VALUES
        ('condicion_terreno',  'URBANO_HABILITADO',            'Urbano habilitado',            1),
        ('condicion_terreno',  'EN_PROCESO_DE_HABILITACION',   'En proceso de habilitacion',   2),
        ('condicion_terreno',  'RUSTICO_ERIAZO',               'Rustico o eriazo',             3),
        ('condicion_terreno',  'ZONA_INFORMAL_SIN_HABILITAR',  'Zona informal sin habilitar',  4),
        ('situacion_registral', 'INSCRITO_EN_SUNARP',          'Inscrito en SUNARP',           1),
        ('situacion_registral', 'EN_SANEAMIENTO',              'En saneamiento',               2),
        ('situacion_registral', 'NO_INSCRITO_SOLO_POSESION',   'No inscrito, solo posesion',   3)
       ) AS o(clave, valor, rotulo, orden) ON o.clave = n.clave;

-- 1.b El riesgo declarado y la restriccion arqueologica. `zona_de_riesgo` es
--     BOOLEANO y llega tambien a `C`: una casa en zona de riesgo declarada es
--     el mismo hecho. `restriccion_arqueologica` NO se infiere del distrito --
--     depende del poligono -- y por eso tiene un estado para "requerido y no
--     iniciado", que es distinto de "no aplica".
WITH nace AS (
    INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                                   aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                                   valor_minimo, valor_maximo, longitud_maxima,
                                   destino, campo_estructural)
    VALUES
        (NULL, 'restriccion_arqueologica', 'Restriccion arqueologica (CIRA)', 'LISTA', NULL,
         false, true, 990, 'PROPIEDAD', NULL,
         'Si el suelo necesita CIRA y en que estado esta.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL)
    RETURNING id_catalogo_atributo, clave
)
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT n.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM nace n
  CROSS JOIN (VALUES
        ('NO_APLICA',             'No aplica',              1),
        ('CIRA_OBTENIDO',         'CIRA obtenido',          2),
        ('EN_TRAMITE',            'En tramite',             3),
        ('REQUERIDO_NO_INICIADO', 'Requerido, no iniciado', 4)
       ) AS o(valor, rotulo, orden);

-- 1.c COMO ES: las tres LISTA de forma y via, con su vocabulario.
WITH nacen AS (
    INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                                   aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                                   valor_minimo, valor_maximo, longitud_maxima,
                                   destino, campo_estructural)
    VALUES
        (NULL, 'tipo_via_acceso', 'Tipo de via del frente', 'LISTA', NULL,
         false, true, 942, 'PROPIEDAD', NULL,
         'De que clase es la via a la que da el frente.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL),
        (NULL, 'estado_via', 'Estado de la via', 'LISTA', NULL,
         false, true, 944, 'PROPIEDAD', NULL,
         'Como esta la superficie de esa via.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL),
        (NULL, 'posicion_en_manzana', 'Posicion en la manzana', 'LISTA', NULL,
         false, true, 1010, 'PROPIEDAD', NULL,
         'Cuantos frentes tiene el lote.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL),
        (NULL, 'topografia', 'Topografia', 'LISTA', NULL,
         false, true, 1020, 'PROPIEDAD', NULL,
         'El relieve del terreno respecto de la via.',
         NULL, NULL, NULL, 'ATRIBUTO', NULL)
    RETURNING id_catalogo_atributo, clave
)
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT n.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM nacen n
  JOIN (VALUES
        ('tipo_via_acceso',     'AVENIDA',                'Avenida',                 1),
        ('tipo_via_acceso',     'CALLE_O_JIRON',          'Calle o jiron',           2),
        ('tipo_via_acceso',     'PASAJE',                 'Pasaje',                  3),
        ('tipo_via_acceso',     'CARRETERA',              'Carretera',               4),
        ('tipo_via_acceso',     'TROCHA_O_SIN_VIA',       'Trocha o sin via',        5),
        ('estado_via',          'ASFALTADA',              'Asfaltada',               1),
        ('estado_via',          'AFIRMADA',               'Afirmada',                2),
        ('estado_via',          'SIN_AFIRMAR',            'Sin afirmar',             3),
        ('posicion_en_manzana', 'UN_FRENTE',              'Un frente',               1),
        ('posicion_en_manzana', 'DOS_FRENTES',            'Dos frentes',             2),
        ('posicion_en_manzana', 'TRES_FRENTES',           'Tres frentes',            3),
        ('posicion_en_manzana', 'CUATRO_FRENTES',         'Cuatro frentes',          4),
        ('posicion_en_manzana', 'ESQUINA',                'Esquina',                 5),
        ('topografia',          'PLANO',                  'Plano',                   1),
        ('topografia',          'PENDIENTE_LEVE',         'Pendiente leve',          2),
        ('topografia',          'PENDIENTE_PRONUNCIADA',  'Pendiente pronunciada',   3),
        ('topografia',          'BAJO_NIVEL_DE_VIA',      'Bajo el nivel de la via', 4),
        ('topografia',          'ACCIDENTADO',            'Accidentado',             5)
       ) AS o(clave, valor, rotulo, orden) ON o.clave = n.clave;

-- 1.d Lo que NO es lista: las medidas, los booleanos y el texto. Once claves.
--
--     `edificacion_existente` es DECIMAL y no BOOLEANO a proposito: declarar 0
--     m2 construidos NO es lo mismo que no saberlo, y un booleano no sabe decir
--     "hay 80 m2 de casa vieja que habra que demoler". Es ademas donde vive lo
--     que `CON_EDIFICACION_A_DEMOLER` queria decir y que D-C5-1 saco de
--     `estado_ocupacion` (V84).
INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                               aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                               valor_minimo, valor_maximo, longitud_maxima,
                               destino, campo_estructural)
VALUES
    (NULL, 'zona_de_riesgo', 'Zona de riesgo declarada', 'BOOLEANO', NULL,
     false, true, 980, 'PROPIEDAD', NULL,
     'Si una autoridad declaro el suelo en zona de riesgo.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'fondo', 'Fondo', 'DECIMAL', 'm',
     false, true, 1000, 'PROPIEDAD', NULL,
     'La medida del lote en profundidad, desde el frente.',
     0, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'edificacion_existente', 'Edificacion existente', 'DECIMAL', 'm2',
     false, true, 1030, 'PROPIEDAD', NULL,
     'Metros construidos que hoy hay sobre el terreno.',
     0, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'cercado', 'Cercado o amurallado', 'BOOLEANO', NULL,
     false, true, 1040, 'PROPIEDAD', NULL,
     'Si el lote tiene cerco perimetrico.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'certificado_parametros_vigente', 'Certificado de parametros', 'BOOLEANO', NULL,
     false, true, 1050, 'PROPIEDAD', NULL,
     'Si hay certificado de parametros urbanisticos vigente.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'altura_normativa_pisos', 'Altura normativa', 'ENTERO', 'pisos',
     false, true, 1060, 'PROPIEDAD', NULL,
     'Cuantos pisos permite la norma en este lote.',
     0, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'coeficiente_edificacion', 'Coeficiente de edificacion', 'DECIMAL', NULL,
     false, true, 1070, 'PROPIEDAD', NULL,
     'Cuantas veces el area del lote se puede construir.',
     0, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'area_libre_minima', 'Area libre minima', 'DECIMAL', '%',
     false, true, 1080, 'PROPIEDAD', NULL,
     'Porcentaje del lote que la norma obliga a dejar sin techar.',
     0, 100, NULL, 'ATRIBUTO', NULL),
    (NULL, 'retiro_municipal', 'Retiro municipal', 'DECIMAL', 'm',
     false, true, 1090, 'PROPIEDAD', NULL,
     'Metros que hay que dejar libres desde el limite de propiedad.',
     0, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'lote_minimo_normativo', 'Lote minimo normativo', 'DECIMAL', 'm2',
     false, true, 1100, 'PROPIEDAD', NULL,
     'Superficie minima que la norma admite al subdividir.',
     0, NULL, NULL, 'ATRIBUTO', NULL),
    (NULL, 'usos_compatibles', 'Usos compatibles', 'TEXTO', NULL,
     false, true, 1110, 'PROPIEDAD', NULL,
     'Los usos que el certificado de parametros admite ademas del principal.',
     NULL, NULL, 500, 'ATRIBUTO', NULL);

-- ---------------------------------------------------------------------
-- 2. A QUE TIPOS APLICAN, Y CON QUE EXIGENCIA (D-1, D-3).
--
-- `requerido` se escribe ADEMAS de `exigencia` porque son columna y espejo desde
-- V72: `requerido = (exigencia = 'ALT')`. Aqui no hay ninguna `ALT`, asi que las
-- 26 filas van a `false`. El guard 2.4 de V78 y la guarda permanente `gate:883`
-- lo comprueban sobre TODO el catalogo.
--
-- Van en `catalogo_atributo_tipo` y NUNCA en `catalogo_atributo_operacion`: son
-- del sujeto PROPIEDAD (guarda 2.5 de V78).
--
-- LAS APLICABILIDADES QUE NO SON SOLO `T`, y por que:
--   situacion_registral    T,C   una casa que se vende por su terreno tambien
--                                se compra por su partida
--   fondo                  T,C   idem: se cotiza por frente y fondo
--   posicion_en_manzana    T,C
--   altura_normativa_pisos T,C   decide si la casa se puede demoler y crecer
--   zona_de_riesgo         T,C
--   tipo_via_acceso        T,L,A avenida o pasaje es el doble de precio para el
--                                mismo metraje, y eso vale igual para un local
--   estado_via             T,A   una nave a la que se llega por trocha no la
--                                usa un trailer
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, v.tipo, false, v.exigencia
  FROM catalogo_atributo c
  JOIN (VALUES
        -- La UNICA PUB nueva del corte (D-3).
        ('condicion_terreno',             'T', 'PUB'),
        ('situacion_registral',           'T', 'OPC'),
        ('situacion_registral',           'C', 'OPC'),
        ('fondo',                         'T', 'OPC'),
        ('fondo',                         'C', 'OPC'),
        ('posicion_en_manzana',           'T', 'OPC'),
        ('posicion_en_manzana',           'C', 'OPC'),
        ('topografia',                    'T', 'OPC'),
        ('altura_normativa_pisos',        'T', 'OPC'),
        ('altura_normativa_pisos',        'C', 'OPC'),
        ('coeficiente_edificacion',       'T', 'OPC'),
        ('area_libre_minima',             'T', 'OPC'),
        ('retiro_municipal',              'T', 'OPC'),
        ('usos_compatibles',              'T', 'OPC'),
        ('certificado_parametros_vigente','T', 'OPC'),
        ('lote_minimo_normativo',         'T', 'OPC'),
        ('tipo_via_acceso',               'T', 'OPC'),
        ('tipo_via_acceso',               'L', 'OPC'),
        ('tipo_via_acceso',               'A', 'OPC'),
        ('estado_via',                    'T', 'OPC'),
        ('estado_via',                    'A', 'OPC'),
        ('edificacion_existente',         'T', 'OPC'),
        ('cercado',                       'T', 'OPC'),
        ('restriccion_arqueologica',      'T', 'OPC'),
        ('zona_de_riesgo',                'T', 'OPC'),
        ('zona_de_riesgo',                'C', 'OPC')
       ) AS v(clave, tipo, exigencia) ON v.clave = c.clave
 WHERE c.organizacion_id IS NULL;

-- ---------------------------------------------------------------------
-- 3. LO QUE LEE UNA PERSONA: rotulos y ayudas, con acentos.
--
-- Las ayudas dicen el HECHO y la distincion que la clave existe para capturar.
-- Tres de ellas cargan con una confusion previsible y la deshacen:
--
--   * `tipo_via_acceso` frente a `via_de_acceso`: la segunda dice CUAL es la
--     via, esta dice de que CLASE es. Conviven a proposito.
--   * `area_libre_minima` en POR CIENTO, no en metros ni en fraccion. Sin
--     decirlo, un agente escribe 0,30 y otro 30, y los dos creen tener razon.
--   * `edificacion_existente` en 0 es una MEDIDA -- "esta vacio" -- y dejarlo en
--     blanco es "no consta". No son lo mismo y la ayuda lo dice.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo c
   SET rotulo = v.rotulo, ayuda = v.ayuda
  FROM (VALUES
    ('condicion_terreno', 'Condición del terreno',
     'En qué situación urbanística está el suelo. No se deduce mirándolo: un lote puede parecer urbano y estar todavía en proceso de habilitación.'),
    ('situacion_registral', 'Situación registral',
     'Cómo está inscrito el inmueble. «En saneamiento» es un trámite abierto, distinto de no estar inscrito.'),
    ('fondo', 'Fondo',
     'La medida del lote en profundidad, desde el frente. 200 m² de 8 × 25 y de 20 × 10 sirven para cosas distintas.'),
    ('posicion_en_manzana', 'Posición en la manzana',
     'Cuántos frentes tiene el lote. «Esquina» es la posición, no el número de frentes.'),
    ('topografia', 'Topografía',
     'El relieve del terreno respecto de la vía. Una pendiente pronunciada o un lote bajo el nivel de la vía encarecen la obra antes de empezar.'),
    ('altura_normativa_pisos', 'Altura normativa',
     'Cuántos pisos permite la norma en este lote. No sale de la zonificación sola: depende también de la vía. Cero pisos es una respuesta válida en suelo no edificable; dejarlo en blanco es «no consta».'),
    ('coeficiente_edificacion', 'Coeficiente de edificación',
     'Cuántas veces el área del lote se puede construir. Multiplicado por el área da el área vendible.'),
    ('area_libre_minima', 'Área libre mínima',
     'Porcentaje del lote que la norma obliga a dejar sin techar. Se escribe en por ciento: 30, no 0,30.'),
    ('retiro_municipal', 'Retiro municipal',
     'Metros que hay que dejar libres desde el límite de propiedad. Reduce el área construible aunque el lote sea grande.'),
    ('usos_compatibles', 'Usos compatibles',
     'Los usos que el certificado de parámetros admite además del principal. Es una línea distinta de la zonificación en ese mismo certificado.'),
    ('certificado_parametros_vigente', 'Certificado de parámetros',
     'Si hay certificado de parámetros urbanísticos vigente. Distingue lo que dijo el propietario de lo que está certificado.'),
    ('lote_minimo_normativo', 'Lote mínimo normativo',
     'Superficie mínima que la norma admite al subdividir. Es el hecho sobre el que se pacta si el titular acepta vender fraccionado.'),
    ('tipo_via_acceso', 'Tipo de vía del frente',
     'De qué clase es la vía a la que da el frente. No es lo mismo que «Vía principal de acceso», que dice cuál es: aquí va avenida, calle, pasaje, carretera o trocha.'),
    ('estado_via', 'Estado de la vía',
     'Cómo está la superficie de esa vía. Una nave a la que se llega por trocha no la usa un tráiler.'),
    ('edificacion_existente', 'Edificación existente',
     'Metros construidos que hoy hay sobre el terreno. Declarar 0 es una medida —está vacío—; dejarlo en blanco es «no consta».'),
    ('cercado', 'Cercado o amurallado',
     'Si el lote tiene cerco perimétrico.'),
    ('restriccion_arqueologica', 'Restricción arqueológica (CIRA)',
     'Si el suelo necesita CIRA y en qué estado está. No se deduce del distrito: depende del polígono. «Requerido, no iniciado» no es «no aplica».'),
    ('zona_de_riesgo', 'Zona de riesgo declarada',
     'Si una autoridad declaró el suelo en zona de riesgo. Es una declaración, no una impresión del agente.')
  ) AS v(clave, rotulo, ayuda)
 WHERE c.organizacion_id IS NULL AND c.clave = v.clave;

UPDATE catalogo_atributo_opcion o
   SET rotulo = v.rotulo
  FROM catalogo_atributo c,
       (VALUES
        ('condicion_terreno',        'EN_PROCESO_DE_HABILITACION',  'En proceso de habilitación'),
        ('condicion_terreno',        'RUSTICO_ERIAZO',              'Rústico o eriazo'),
        ('situacion_registral',      'NO_INSCRITO_SOLO_POSESION',   'No inscrito, sólo posesión'),
        ('restriccion_arqueologica', 'EN_TRAMITE',                  'En trámite'),
        ('tipo_via_acceso',          'CALLE_O_JIRON',               'Calle o jirón'),
        ('tipo_via_acceso',          'TROCHA_O_SIN_VIA',            'Trocha o sin vía'),
        ('topografia',               'BAJO_NIVEL_DE_VIA',           'Bajo el nivel de la vía')
       ) AS v(clave, valor, rotulo)
 WHERE c.id_catalogo_atributo = o.id_catalogo_atributo
   AND c.organizacion_id IS NULL
   AND c.clave = v.clave
   AND o.valor = v.valor;

-- Y la unidad de las dos superficies, con su acento. `m²` es la convencion
-- MEDIDA del catalogo -- ONCE claves la usan antes de este corte y CERO usan
-- `m2`; con estas dos quedan TRECE -- y `V68` ya lo
-- hizo asi para las que existian.
UPDATE catalogo_atributo
   SET unidad = 'm²'
 WHERE organizacion_id IS NULL
   AND clave IN ('edificacion_existente', 'lote_minimo_normativo');

-- ---------------------------------------------------------------------
-- 4. **SOLO AHORA**: EL DATO DE `area_terreno` EN `T` (D-7).
--
-- Las 18 claves ya existen, ya tienen vocabulario y ya son aplicables. Es el
-- primer instante en que se puede mirar el legado sabiendo cual es el modelo
-- definitivo.
--
-- EL CLASIFICADOR ES UNA COMPARACION NUMERICA, NO UN ACTA DE CADENAS. La de V84
-- lo era porque `servicios_disponibles` era texto libre de facto y no habia
-- forma de decidir sin inventar. Aqui la pregunta tiene respuesta exacta: o el
-- numero coincide con la columna canonica, o no. Por eso el clasificador es
-- TOTAL y no existe el veredicto "no inventariado".
-- ---------------------------------------------------------------------
CREATE TEMP TABLE v85_area_en_terreno AS
SELECT a.id_atributo_propiedad,
       a.organizacion_id,
       a.id_propiedad,
       a.valor_numero,
       p.metraje,
       CASE
           WHEN a.valor_numero IS NULL THEN 'SIN_VALOR'
           WHEN p.metraje IS NULL      THEN 'SIN_CANONICA'
           WHEN a.valor_numero = p.metraje THEN 'COINCIDE'
           ELSE 'DISCREPANTE'
       END AS veredicto
  FROM atributo_propiedad a
  JOIN propiedad p ON p.id_propiedad = a.id_propiedad
 WHERE a.clave = 'area_terreno'
   AND p.tipo_inmueble = 'T';

-- EL LINAJE VA ANTES DEL BORRADO, y no es estilo: leerlo despues seria leer lo
-- que ya no existe. Es la misma regla que `AtributosGobernados.retirar` aplica
-- en el servicio desde 4.P -- "el borrado es fisico, asi que ese es el ultimo
-- instante en que ese dato existe: se lee, se anota, y la clave queda con
-- historia y sin valor".
--
-- `verbo = 'RETIRADA'` y el valor va en `hallado_numero`, NUNCA en
-- `valor_numero`: `ck_rastro_forma_del_valor` exige que una RETIRADA no traiga
-- valor nuevo, porque despues de ella no hay ninguno. `naturaleza` queda AUSENTE
-- --NULL significa "no consta", no una cuarta clase de evidencia-- y el `canal`
-- es `SISTEMA` porque lo escribio esta migracion. La procedencia OPERACIONAL es
-- demostrable; COMO se conocio el hecho, no.
INSERT INTO rastro_valor_gobernado
    (organizacion_id, sujeto, id_agregado, clave, verbo,
     hallado_numero, canal, registrado_en, evidencia_ref)
SELECT r.organizacion_id, 'PROPIEDAD', r.id_propiedad, 'area_terreno', 'RETIRADA',
       r.valor_numero, 'SISTEMA', now(),
       'V85 D-7 area_terreno se retira de T; el valor coincidia con metraje_total'
  FROM v85_area_en_terreno r
 WHERE r.veredicto = 'COINCIDE';

-- Y AHORA SI. Solo lo que COINCIDE: su reemplazo esta activo, es ALT, es NOT
-- NULL y contiene EL MISMO NUMERO, asi que la fila no llevaba ninguna
-- informacion que no estuviera ya en su sitio canonico.
--
-- Lo DISCREPANTE NO se toca -- ni la fila, ni `metraje_total` --, se cuenta y se
-- nombra en el NOTICE. Queda como una fila que la ficha SIGUE LEYENDO con su
-- rotulo y su tipo --`LectorPorAutoridad` lee por presencia de fila, no por
-- aplicabilidad, y `area_terreno` sigue siendo una clave ACTIVA-- y que el
-- editor ya no ofrece. Es exactamente lo que se quiere: el desacuerdo se VE, no
-- se resuelve por decreto, y se puede quitar por `atributosABorrar` --
-- `AtributosGobernados.retirar` no exige aplicabilidad, a diferencia de
-- `escribirEnEdicion`, asi que un huerfano nunca queda atrapado.
DELETE FROM atributo_propiedad a
 USING v85_area_en_terreno r
 WHERE a.id_atributo_propiedad = r.id_atributo_propiedad
   AND r.veredicto = 'COINCIDE';

-- ---------------------------------------------------------------------
-- 5. **SOLO AHORA**: LA RETIRADA DE LA APLICABILIDAD `area_terreno` / `T`.
--
-- Se borra UNA fila de `catalogo_atributo_tipo`, y solo esa. La CLAVE no se
-- toca: sigue activa, del sistema, con su rotulo y su tipo, y sigue aplicando a
-- `A` y a `C`.
--
-- Desde aqui, `AtributosGobernados.exigirQueAplique` rechaza `area_terreno`
-- sobre un TERRENO tanto en el ALTA como en la EDICION -- las dos puertas, que
-- es la simetria que 4.P tuvo que reparar cuando una CASA se podia registrar con
-- un `piso` que despues no se podia corregir nunca.
-- ---------------------------------------------------------------------
DELETE FROM catalogo_atributo_tipo t
 USING catalogo_atributo c
 WHERE c.id_catalogo_atributo = t.id_catalogo_atributo
   AND c.organizacion_id IS NULL
   AND c.clave = 'area_terreno'
   AND t.tipo_propiedad = 'T';

-- ---------------------------------------------------------------------
-- 6. LAS ASERCIONES DEL ESTADO RESULTANTE.
--
-- Invariantes, no cifras escritas a mano donde una invariante sirve. Las cifras
-- literales que si aparecen -- 18 claves, 26 filas de aplicabilidad, 29
-- opciones -- lo son porque son el CONTENIDO de esta migracion y no el tamano de
-- nada que crezca con el uso.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    nuevas         TEXT[] := ARRAY[
        'condicion_terreno', 'situacion_registral', 'fondo', 'posicion_en_manzana',
        'topografia', 'altura_normativa_pisos', 'coeficiente_edificacion',
        'area_libre_minima', 'retiro_municipal', 'usos_compatibles',
        'certificado_parametros_vigente', 'lote_minimo_normativo', 'tipo_via_acceso',
        'estado_via', 'edificacion_existente', 'cercado', 'restriccion_arqueologica',
        'zona_de_riesgo'];
    faltan         TEXT;
    mal_forma      TEXT;
    duplicadas     TEXT;
    formas         TEXT;
    exigencias     TEXT;
    pub_nuevas     TEXT;
    pub_antes      TEXT;
    pub_ahora      TEXT;
    vocab          TEXT;
    n_opciones     BIGINT;
    codigo_malo    TEXT;
    mudas          TEXT;
    sin_hecho      TEXT;
    cubiertos      INT;
    area_tipos     TEXT;
    total_legado   BIGINT;
    clasificado    BIGINT;
    coinciden      BIGINT;
    discrepan      BIGINT;
    sin_valor      BIGINT;
    sin_canonica   BIGINT;
    con_linaje     BIGINT;
    borradas       BIGINT;
    detalle        TEXT;
    espejo         TEXT;
    cruce          TEXT;
    activas        BIGINT;
    perdidas       TEXT;
    movidas        TEXT;
    opciones_ida   TEXT;
    valores_ida    TEXT;
BEGIN
    -- 6.1 LAS 18 NACIERON, con la forma que manda el encargo: activas, del
    --     sistema, del sujeto PROPIEDAD, destino ATRIBUTO, sin campo
    --     estructural y con aplicabilidad EXPLICITA por tipo (nunca
    --     `aplica_todos`, que es la doble autoridad que D-5 deja anotada).
    SELECT string_agg(k, ', ') INTO faltan
      FROM unnest(nuevas) AS k
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.organizacion_id IS NULL AND c.clave = k
                          AND c.activo AND c.del_sistema AND c.sujeto = 'PROPIEDAD');
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'V85: estas claves no llegaron al catalogo de la PROPIEDAD: %', faltan;
    END IF;
    IF array_length(nuevas, 1) <> 18 THEN
        RAISE EXCEPTION 'V85: el corte declara 18 claves y esta lista tiene %',
            array_length(nuevas, 1);
    END IF;

    SELECT string_agg(c.clave || ' -> ' || c.destino
                      || '/aplica_todos=' || c.aplica_todos
                      || coalesce('/estructural=' || c.campo_estructural, ''), ', ')
      INTO mal_forma
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas)
       AND (c.destino <> 'ATRIBUTO' OR c.campo_estructural IS NOT NULL OR c.aplica_todos);
    IF mal_forma IS NOT NULL THEN
        RAISE EXCEPTION 'V85: forma equivocada en: %. Las 18 son ATRIBUTO y con aplicabilidad '
                        'explicita por tipo.', mal_forma;
    END IF;

    -- Exactamente una definicion del sistema por clave. `uq_catalogo_atributo_clave`
    -- (V48) ya lo impide sobre `(COALESCE(organizacion_id,0), clave)`; se
    -- comprueba igual porque el indice es lo que hace CIERTA esta afirmacion y
    -- una migracion futura podria cambiarlo sin que nadie relea esta linea.
    SELECT string_agg(x.clave || ' x' || x.veces, ', ') INTO duplicadas
      FROM (SELECT c.clave, count(*) AS veces
              FROM catalogo_atributo c
             WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas)
             GROUP BY c.clave HAVING count(*) > 1) AS x;
    IF duplicadas IS NOT NULL THEN
        RAISE EXCEPTION 'V85: hay claves del sistema definidas dos veces: %', duplicadas;
    END IF;

    -- 6.2 LA FORMA EXACTA DE CADA UNA: tipo de dato y unidad. Se compara el
    --     CONJUNTO completo, porque una clave con el tipo cambiado no falla al
    --     nacer -- falla el dia que alguien intenta escribirle un valor.
    SELECT string_agg(c.clave || '=' || c.tipo_dato || coalesce(' ' || c.unidad, ''),
                      ', ' ORDER BY c.clave) INTO formas
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas);
    IF formas IS DISTINCT FROM
       'altura_normativa_pisos=ENTERO pisos, '
       'area_libre_minima=DECIMAL %, '
       'cercado=BOOLEANO, '
       'certificado_parametros_vigente=BOOLEANO, '
       'coeficiente_edificacion=DECIMAL, '
       'condicion_terreno=LISTA, '
       'edificacion_existente=DECIMAL m², '
       'estado_via=LISTA, '
       'fondo=DECIMAL m, '
       'lote_minimo_normativo=DECIMAL m², '
       'posicion_en_manzana=LISTA, '
       'restriccion_arqueologica=LISTA, '
       'retiro_municipal=DECIMAL m, '
       'situacion_registral=LISTA, '
       'tipo_via_acceso=LISTA, '
       'topografia=LISTA, '
       'usos_compatibles=TEXTO, '
       'zona_de_riesgo=BOOLEANO' THEN
        RAISE EXCEPTION 'V85: la forma de las claves nuevas quedo en [%]', formas;
    END IF;

    -- 6.3 LA APLICABILIDAD Y LA EXIGENCIA EXACTAS (D-1, D-3). Se comprueba el
    --     CONJUNTO completo: una fila de mas, una de menos o una exigencia
    --     distinta cambia quien puede registrar o publicar.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad || '=' || t.exigencia
                      || '/req=' || t.requerido, ', ' ORDER BY c.clave, t.tipo_propiedad)
      INTO exigencias
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas);
    IF exigencias IS DISTINCT FROM
       'altura_normativa_pisos/C=OPC/req=false, altura_normativa_pisos/T=OPC/req=false, '
       'area_libre_minima/T=OPC/req=false, '
       'cercado/T=OPC/req=false, '
       'certificado_parametros_vigente/T=OPC/req=false, '
       'coeficiente_edificacion/T=OPC/req=false, '
       'condicion_terreno/T=PUB/req=false, '
       'edificacion_existente/T=OPC/req=false, '
       'estado_via/A=OPC/req=false, estado_via/T=OPC/req=false, '
       'fondo/C=OPC/req=false, fondo/T=OPC/req=false, '
       'lote_minimo_normativo/T=OPC/req=false, '
       'posicion_en_manzana/C=OPC/req=false, posicion_en_manzana/T=OPC/req=false, '
       'restriccion_arqueologica/T=OPC/req=false, '
       'retiro_municipal/T=OPC/req=false, '
       'situacion_registral/C=OPC/req=false, situacion_registral/T=OPC/req=false, '
       'tipo_via_acceso/A=OPC/req=false, tipo_via_acceso/L=OPC/req=false, '
       'tipo_via_acceso/T=OPC/req=false, '
       'topografia/T=OPC/req=false, '
       'usos_compatibles/T=OPC/req=false, '
       'zona_de_riesgo/C=OPC/req=false, zona_de_riesgo/T=OPC/req=false' THEN
        RAISE EXCEPTION 'V85: la exigencia de las claves nuevas quedo en [%]', exigencias;
    END IF;

    -- 6.4 `condicion_terreno` ES LA UNICA `PUB` NUEVA (D-1). Se comprueba por
    --     partida doble:
    --       (a) entre las 18, el conjunto de filas PUB es exactamente esa;
    --       (b) en TODO el catalogo, el conjunto de PUB despues es el de antes
    --           MAS esa y nada mas. Sin (b), promover una clave VIEJA a PUB
    --           dentro de esta migracion pasaria sin ruido, y eso es
    --           exactamente lo que D-1 prohibe.
    SELECT coalesce(string_agg(c.clave || '/' || t.tipo_propiedad, ', '
                               ORDER BY c.clave, t.tipo_propiedad), '(ninguna)')
      INTO pub_nuevas
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas) AND t.exigencia = 'PUB';
    IF pub_nuevas IS DISTINCT FROM 'condicion_terreno/T' THEN
        RAISE EXCEPTION
            'V85: las PUB nuevas son [%] y D-1 solo autoriza `condicion_terreno` en T. '
            'La columna `nivel` de la auditoria propone cinco mas: es PROPUESTA, no autoridad.',
            pub_nuevas;
    END IF;

    -- El conjunto ESPERADO se construye con la foto: las PUB que ya habia, MAS
    -- `condicion_terreno/T`. Escribirlo como cadena literal ataria la asercion a
    -- las PUB que hay hoy y la volveria falsa el dia que un corte posterior
    -- promueva otra, sin decir nada de lo que aqui importa.
    --
    -- SE INFORMA LA **DIFERENCIA**, NO EL CONJUNTO, y no es cosmetica: en
    -- `controllocal_repositorios` el catalogo lleva cientos de claves de tenant
    -- con `PUB` --residuo de las suites--, asi que imprimir el conjunto entero
    -- produce un mensaje de 100 kB en el que la clave culpable no se encuentra.
    -- Un error que no se puede leer no informa de nada.
    SELECT string_agg(x, ', ' ORDER BY x) INTO pub_antes
      FROM (SELECT clave || '/' || tipo_propiedad AS x FROM v85_tipo_antes
             WHERE exigencia = 'PUB'
            EXCEPT
            SELECT c.clave || '/' || t.tipo_propiedad
              FROM catalogo_atributo c
              JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
             WHERE t.exigencia = 'PUB') AS se_fueron(x);
    SELECT string_agg(x, ', ' ORDER BY x) INTO pub_ahora
      FROM (SELECT c.clave || '/' || t.tipo_propiedad AS x
              FROM catalogo_atributo c
              JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
             WHERE t.exigencia = 'PUB'
            EXCEPT
            SELECT clave || '/' || tipo_propiedad FROM v85_tipo_antes
             WHERE exigencia = 'PUB') AS llegaron(x);
    IF pub_ahora IS DISTINCT FROM 'condicion_terreno/T' OR pub_antes IS NOT NULL THEN
        RAISE EXCEPTION
            'V85: el conjunto de PUB del catalogo gano [%] y perdio [%]. Esta migracion solo '
            'puede anadir `condicion_terreno/T`, y no puede quitar ninguna (D-1).',
            coalesce(pub_ahora, '(ninguna)'), coalesce(pub_antes, '(ninguna)');
    END IF;

    -- 6.5 EL VOCABULARIO DE LAS SIETE `LISTA`, codigo a codigo y en orden.
    --     Una LISTA que nace muda es el defecto que 5A acaba de cerrar, asi que
    --     no basta con "tiene opciones": tienen que ser ESTAS y en ESTE orden,
    --     que es el que ve el selector.
    SELECT string_agg(c.clave || ':' || o.valor, ' ' ORDER BY c.clave, o.orden) INTO vocab
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas) AND o.activo;
    IF vocab IS DISTINCT FROM
       'condicion_terreno:URBANO_HABILITADO condicion_terreno:EN_PROCESO_DE_HABILITACION '
       'condicion_terreno:RUSTICO_ERIAZO condicion_terreno:ZONA_INFORMAL_SIN_HABILITAR '
       'estado_via:ASFALTADA estado_via:AFIRMADA estado_via:SIN_AFIRMAR '
       'posicion_en_manzana:UN_FRENTE posicion_en_manzana:DOS_FRENTES '
       'posicion_en_manzana:TRES_FRENTES posicion_en_manzana:CUATRO_FRENTES '
       'posicion_en_manzana:ESQUINA '
       'restriccion_arqueologica:NO_APLICA restriccion_arqueologica:CIRA_OBTENIDO '
       'restriccion_arqueologica:EN_TRAMITE restriccion_arqueologica:REQUERIDO_NO_INICIADO '
       'situacion_registral:INSCRITO_EN_SUNARP situacion_registral:EN_SANEAMIENTO '
       'situacion_registral:NO_INSCRITO_SOLO_POSESION '
       'tipo_via_acceso:AVENIDA tipo_via_acceso:CALLE_O_JIRON tipo_via_acceso:PASAJE '
       'tipo_via_acceso:CARRETERA tipo_via_acceso:TROCHA_O_SIN_VIA '
       'topografia:PLANO topografia:PENDIENTE_LEVE topografia:PENDIENTE_PRONUNCIADA '
       'topografia:BAJO_NIVEL_DE_VIA topografia:ACCIDENTADO' THEN
        RAISE EXCEPTION 'V85: el vocabulario de las claves nuevas quedo en [%]', vocab;
    END IF;

    SELECT count(*) INTO n_opciones
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas);
    IF n_opciones <> 29 THEN
        RAISE EXCEPTION 'V85: las siete LISTA tienen % opciones y el corte siembra 29', n_opciones;
    END IF;

    -- Y NINGUNA de las once que NO son lista gano vocabulario: una opcion sobre
    -- una DECIMAL o una BOOLEANA no la lee nadie y confunde al que la ve.
    SELECT string_agg(DISTINCT c.clave, ', ') INTO detalle
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas)
       AND c.tipo_dato NOT IN ('LISTA', 'LISTA_MULTIPLE');
    IF detalle IS NOT NULL THEN
        RAISE EXCEPTION 'V85: estas claves no son listas y tienen opciones: %', detalle;
    END IF;

    -- 6.6 EL PAR `lote_minimo_normativo` / `acepta_venta_fraccionada`, que es la
    --     guarda 2.2 de V78 y la razon por la que esta clave estaba esperando a
    --     este corte. Se mide en las DOS direcciones para que no salga verde
    --     sobre un universo vacio: primero los tipos DESCUBIERTOS y despues
    --     cuantos quedan CUBIERTOS. Si `acepta_venta_fraccionada` desapareciera
    --     del catalogo, la primera daria cero huecos y la segunda lo caza.
    SELECT string_agg(DISTINCT o.tipo_propiedad, ', ' ORDER BY o.tipo_propiedad) INTO sin_hecho
      FROM catalogo_atributo cond
      JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
      JOIN catalogo_atributo hecho ON hecho.clave = 'lote_minimo_normativo' AND hecho.activo
                                  AND hecho.organizacion_id IS NULL
     WHERE cond.clave = 'acepta_venta_fraccionada' AND cond.activo
       AND cond.organizacion_id IS NULL
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = hecho.id_catalogo_atributo
                          AND t.tipo_propiedad = o.tipo_propiedad);
    IF sin_hecho IS NOT NULL THEN
        RAISE EXCEPTION
            'V85: `acepta_venta_fraccionada` se pacta en % y `lote_minimo_normativo` no llega. '
            'Ahi el pacto seria el unico sitio donde cabe el hecho, y un pacto muere con su '
            'encargo.', sin_hecho;
    END IF;

    SELECT count(DISTINCT o.tipo_propiedad) INTO cubiertos
      FROM catalogo_atributo cond
      JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
      JOIN catalogo_atributo hecho ON hecho.clave = 'lote_minimo_normativo' AND hecho.activo
                                  AND hecho.organizacion_id IS NULL
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                   AND t.tipo_propiedad = o.tipo_propiedad
     WHERE cond.clave = 'acepta_venta_fraccionada' AND cond.activo
       AND cond.organizacion_id IS NULL;
    IF cubiertos < 1 THEN
        RAISE EXCEPTION
            'V85: el par lote_minimo_normativo/acepta_venta_fraccionada esta cubierto en % '
            'tipos. Un cero significaria que la comprobacion de arriba salio verde sobre un '
            'conjunto vacio.', cubiertos;
    END IF;

    -- Y el hecho es de la PROPIEDAD y su condicion del ENCARGO (guarda de V77):
    -- no se juntan aunque hablen de lo mismo.
    IF (SELECT sujeto FROM catalogo_atributo
         WHERE organizacion_id IS NULL AND clave = 'lote_minimo_normativo') <> 'PROPIEDAD'
       OR (SELECT sujeto FROM catalogo_atributo
            WHERE organizacion_id IS NULL AND clave = 'acepta_venta_fraccionada') <> 'ENCARGO' THEN
        RAISE EXCEPTION
            'V85: el hecho y su condicion acabaron en el mismo sujeto. Un lote minimo es un '
            'hecho normativo del suelo; aceptar vender fraccionado es un pacto que muere con '
            'su encargo.';
    END IF;

    -- 6.7 `area_terreno` (D-7): CONSERVA `A` y `C`, PIERDE `T`, y no cambio de
    --     nada mas. La clave sigue ACTIVA -- retirar una aplicabilidad no es
    --     retirar la clave -- y sigue siendo del sistema.
    SELECT coalesce(string_agg(t.tipo_propiedad || '=' || t.exigencia, ',' ORDER BY t.tipo_propiedad),
                    '(ninguna)')
      INTO area_tipos
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = 'area_terreno';
    IF area_tipos IS DISTINCT FROM 'A=OPC,C=OPC' THEN
        RAISE EXCEPTION
            'V85: area_terreno aplica a [%] y tiene que aplicar a [A=OPC,C=OPC]: D-7 retira '
            'SOLO la fila de T, y A y C no se tocan.', area_tipos;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM catalogo_atributo
                    WHERE organizacion_id IS NULL AND clave = 'area_terreno'
                      AND activo AND del_sistema AND tipo_dato = 'DECIMAL') THEN
        RAISE EXCEPTION
            'V85: `area_terreno` tiene que seguir ACTIVA y ser DECIMAL. D-7 retira su '
            'aplicabilidad a T, no la clave: una casa se sigue tasando por el par '
            '(terreno, construida).';
    END IF;

    -- 6.8 EL DATO. La invariante, y NUNCA la cifra que hoy da.
    SELECT count(*) INTO total_legado
      FROM v85_valores_antes WHERE clave = 'area_terreno' AND tipo_inmueble = 'T';
    SELECT count(*),
           count(*) FILTER (WHERE veredicto = 'COINCIDE'),
           count(*) FILTER (WHERE veredicto = 'DISCREPANTE'),
           count(*) FILTER (WHERE veredicto = 'SIN_VALOR'),
           count(*) FILTER (WHERE veredicto = 'SIN_CANONICA')
      INTO clasificado, coinciden, discrepan, sin_valor, sin_canonica
      FROM v85_area_en_terreno;

    -- El acta se pronuncio sobre TODO el universo previo. Compara dos fuentes
    -- distintas --la foto del bloque 0 y el clasificador--, asi que no es una
    -- identidad: caza una fila aparecida o desaparecida entre las dos.
    IF clasificado <> total_legado THEN
        RAISE EXCEPTION
            'V85: la foto previa tiene % valores de area_terreno sobre TERRENOS y el '
            'clasificador se pronuncio sobre %. El universo se movio mientras la migracion '
            'corria.', total_legado, clasificado;
    END IF;
    IF coinciden + discrepan + sin_valor + sin_canonica <> clasificado THEN
        RAISE EXCEPTION 'V85: el clasificador vio % filas y los cuatro veredictos suman %',
            clasificado, coinciden + discrepan + sin_valor + sin_canonica;
    END IF;

    -- `propiedad.metraje` es NOT NULL, asi que `SIN_CANONICA` es imposible. Si
    -- aparece, la premisa que autoriza la retirada --"el reemplazo esta activo y
    -- contiene el dato"-- ha dejado de ser cierta y la migracion PARA.
    IF sin_canonica > 0 THEN
        RAISE EXCEPTION
            'V85: % terrenos tienen `area_terreno` y NO tienen `metraje`. La retirada de D-7 '
            'se apoya en que `metraje_total` es la superficie canonica y siempre esta: si '
            'falta, retirar la otra dejaria al terreno sin ninguna.', sin_canonica;
    END IF;

    -- LO QUE SE FUE ES EXACTAMENTE LO QUE COINCIDIA, Y NADA MAS.
    SELECT count(*) INTO borradas
      FROM v85_valores_antes v
     WHERE v.clave = 'area_terreno' AND v.tipo_inmueble = 'T'
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_atributo_propiedad = v.id_atributo_propiedad);
    IF borradas <> coinciden THEN
        RAISE EXCEPTION
            'V85: coincidian % valores de area_terreno con su metraje y desaparecieron %. '
            'Solo se retira lo que ya esta entero en su columna canonica.', coinciden, borradas;
    END IF;

    -- Y CADA UNO DEJO SU LINAJE. Sin esto la retirada seria un borrado mudo: la
    -- base no podria decir que ese inmueble llego a tener escrita esa medida.
    SELECT count(*) INTO con_linaje
      FROM rastro_valor_gobernado
     WHERE evidencia_ref = 'V85 D-7 area_terreno se retira de T; el valor coincidia con metraje_total';
    IF con_linaje <> coinciden THEN
        RAISE EXCEPTION 'V85: se retiraron % valores y quedo linaje de %', coinciden, con_linaje;
    END IF;

    -- LO DISCREPANTE SIGUE ENTERO, fila a fila y por su valor, no por su numero.
    SELECT string_agg(v.id_atributo_propiedad::text, ', ') INTO perdidas
      FROM v85_valores_antes v
      JOIN v85_area_en_terreno r ON r.id_atributo_propiedad = v.id_atributo_propiedad
     WHERE r.veredicto IN ('DISCREPANTE', 'SIN_VALOR')
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_atributo_propiedad = v.id_atributo_propiedad
                          AND a.valor_numero IS NOT DISTINCT FROM v.valor_numero);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION
            'V85: se perdieron o se reescribieron valores de area_terreno que NO coincidian '
            'con su metraje: %. Lo ambiguo permanece FALTANTE y se conserva: no se sabe cual '
            'de las dos superficies es la correcta, y elegir una seria inventar.', perdidas;
    END IF;

    -- Y NINGUN `metraje` SE MOVIO. Es la otra mitad de "no se reinterpreta": el
    -- camino facil habria sido copiar el area discrepante sobre la columna
    -- canonica y declarar el conflicto resuelto.
    SELECT string_agg(v.id_propiedad::text, ', ') INTO perdidas
      FROM (SELECT DISTINCT id_propiedad, metraje FROM v85_valores_antes) AS v
      JOIN propiedad p ON p.id_propiedad = v.id_propiedad
     WHERE p.metraje IS DISTINCT FROM v.metraje;
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION 'V85: cambio el metraje canonico de estas propiedades: %', perdidas;
    END IF;

    -- Y EL `area_terreno` DE `A` Y `C` NO SE TOCO. Es la parte de D-7 que se
    -- podia romper por exceso de celo: un DELETE sin el filtro por tipo se
    -- habria llevado 782 filas en la base de pruebas sin que nada avisara.
    SELECT string_agg(v.clave || '#' || v.id_atributo_propiedad, ', ') INTO perdidas
      FROM v85_valores_antes v
     WHERE v.clave = 'area_terreno' AND v.tipo_inmueble <> 'T'
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_atributo_propiedad = v.id_atributo_propiedad
                          AND a.valor_numero IS NOT DISTINCT FROM v.valor_numero);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION
            'V85: se perdieron valores de area_terreno fuera de los TERRENOS: %. D-7 retira '
            'SOLO la fila de T.', perdidas;
    END IF;

    -- 6.9 LAS INVARIANTES DE SIEMPRE, sobre TODO el catalogo y no solo sobre lo
    --     nuevo. Son las que se rompen desde fuera del corte.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO espejo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE t.requerido <> (t.exigencia = 'ALT');
    IF espejo IS NOT NULL THEN
        RAISE EXCEPTION 'V85: requerido y exigencia divergen en: %', espejo;
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
        RAISE EXCEPTION 'V85: claves con la aplicabilidad en la tabla del otro sujeto: %', cruce;
    END IF;

    SELECT string_agg(c.clave || '/' || o.valor, ', ') INTO codigo_malo
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND o.valor !~ '^[A-Z][A-Z0-9_]*$';
    IF codigo_malo IS NOT NULL THEN
        RAISE EXCEPTION 'V85: codigos que no son UPPER_SNAKE empezando por letra: %', codigo_malo;
    END IF;

    -- La guarda que 5A dejo puesta, ejecutada tambien aqui: sobre las claves
    -- ACTIVAS de los DOS sujetos y de TODOS los ambitos. Las siete LISTA nuevas
    -- tienen que estar del lado bueno.
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
            'V85: hay LISTAS activas sin vocabulario: %. Una LISTA sin opciones se degrada a '
            'TEXTO en el motor de captura y el trigger acepta cualquier cadena.', mudas;
    END IF;

    -- El suelo del gate del modelo universal (>= 51 claves del sistema activas).
    -- Este corte solo SUMA, asi que no puede bajarlo; se comprueba igual porque
    -- una migracion que deja el gate en rojo se descubre en el cierre y no aqui.
    SELECT count(*) FILTER (WHERE activo) INTO activas
      FROM catalogo_atributo WHERE del_sistema;
    IF activas < 51 THEN
        RAISE EXCEPTION 'V85: quedan % claves del sistema activas y el suelo del gate es 51', activas;
    END IF;

    -- Y ninguna clave del sistema se quedo sin aplicabilidad, que es la otra
    -- comprobacion permanente del gate y la que caza un `DELETE` de mas sobre
    -- `catalogo_atributo_tipo`.
    SELECT string_agg(c.clave, ', ') INTO detalle
      FROM catalogo_atributo c
     WHERE c.del_sistema AND c.activo AND NOT c.aplica_todos
       AND ((c.sujeto = 'PROPIEDAD'
             AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                              WHERE t.id_catalogo_atributo = c.id_catalogo_atributo))
         OR (c.sujeto = 'ENCARGO'
             AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                              WHERE o.id_catalogo_atributo = c.id_catalogo_atributo)));
    IF detalle IS NOT NULL THEN
        RAISE EXCEPTION
            'V85: estas claves del sistema se quedaron sin aplicabilidad: %. Una clave sin '
            'decir a que aplica es invisible en todos los guiones y nadie lo nota hasta '
            'echarla en falta.', detalle;
    END IF;

    -- 6.10 NADA MAS SE MOVIO. Contra la foto, fila a fila, excluyendo lo que
    --      esta migracion viene a cambiar. Un recuento suelto no serviria:
    --      cuadra igual si una baja y otra sube.
    SELECT string_agg(a.clave || ': activo ' || a.activo || ' -> ' || c.activo
                      || ', orden ' || a.orden || ' -> ' || c.orden, ', ')
      INTO movidas
      FROM v85_claves_antes a
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = a.id_catalogo_atributo
     WHERE c.activo IS DISTINCT FROM a.activo
        OR c.orden IS DISTINCT FROM a.orden
        OR c.tipo_dato IS DISTINCT FROM a.tipo_dato
        OR c.unidad IS DISTINCT FROM a.unidad
        OR c.sujeto IS DISTINCT FROM a.sujeto
        OR c.destino IS DISTINCT FROM a.destino
        OR c.aplica_todos IS DISTINCT FROM a.aplica_todos;
    IF movidas IS NOT NULL THEN
        RAISE EXCEPTION 'V85: esta migracion movio claves que no debia tocar: %', movidas;
    END IF;

    -- Ninguna clave del catalogo desaparecio.
    SELECT string_agg(a.clave, ', ') INTO perdidas
      FROM v85_claves_antes a
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.id_catalogo_atributo = a.id_catalogo_atributo);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION 'V85: desaparecieron claves del catalogo: %', perdidas;
    END IF;

    -- Ninguna fila de aplicabilidad desaparecio ni cambio de exigencia, EXCEPTO
    -- la unica que D-7 autoriza a retirar. Se excluye por su nombre exacto para
    -- que un DELETE de mas -- otra clave, otro tipo -- caiga aqui.
    SELECT string_agg(a.clave || '/' || a.tipo_propiedad, ', ') INTO perdidas
      FROM v85_tipo_antes a
     WHERE NOT (a.organizacion_id IS NULL AND a.clave = 'area_terreno' AND a.tipo_propiedad = 'T')
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = a.id_catalogo_atributo
                          AND t.tipo_propiedad = a.tipo_propiedad
                          AND t.exigencia = a.exigencia
                          AND t.requerido = a.requerido);
    IF perdidas IS NOT NULL THEN
        RAISE EXCEPTION 'V85: se perdieron o se movieron filas de aplicabilidad: %', perdidas;
    END IF;

    -- Y la que SI se retira, se retiro de verdad. Sin esto, una migracion que no
    -- hiciera nada pasaria todas las comprobaciones de conservacion.
    IF EXISTS (SELECT 1 FROM v85_tipo_antes a
                WHERE a.organizacion_id IS NULL AND a.clave = 'area_terreno'
                  AND a.tipo_propiedad = 'T')
       AND EXISTS (SELECT 1 FROM catalogo_atributo c
                     JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
                    WHERE c.organizacion_id IS NULL AND c.clave = 'area_terreno'
                      AND t.tipo_propiedad = 'T') THEN
        RAISE EXCEPTION 'V85: la fila area_terreno/T sigue ahi: D-7 no se aplico.';
    END IF;

    -- Ninguna OPCION del catalogo desaparecio, cambio de codigo ni se desactivo.
    SELECT string_agg(a.clave || '/' || a.valor, ', ') INTO opciones_ida
      FROM v85_opciones_antes a
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = a.id_catalogo_atributo
                          AND o.valor = a.valor AND o.activo = a.activo
                          AND o.orden = a.orden);
    IF opciones_ida IS NOT NULL THEN
        RAISE EXCEPTION 'V85: se movieron opciones del catalogo que ya existian: %', opciones_ida;
    END IF;

    -- Y NINGUN VALOR ESCRITO se perdio ni cambio, de ninguna clave, EXCEPTO los
    -- `area_terreno` de terrenos que coincidian con su metraje.
    SELECT string_agg(a.clave || '#' || a.id_atributo_propiedad, ', ') INTO valores_ida
      FROM v85_valores_antes a
     WHERE NOT (a.clave = 'area_terreno' AND a.tipo_inmueble = 'T'
                AND EXISTS (SELECT 1 FROM v85_area_en_terreno r
                             WHERE r.id_atributo_propiedad = a.id_atributo_propiedad
                               AND r.veredicto = 'COINCIDE'))
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad p
                        WHERE p.id_atributo_propiedad = a.id_atributo_propiedad
                          AND p.clave = a.clave
                          AND p.valor_texto    IS NOT DISTINCT FROM a.valor_texto
                          AND p.valor_numero   IS NOT DISTINCT FROM a.valor_numero
                          AND p.valor_booleano IS NOT DISTINCT FROM a.valor_booleano
                          AND p.valor_fecha    IS NOT DISTINCT FROM a.valor_fecha
                          AND p.valor_moneda   IS NOT DISTINCT FROM a.valor_moneda);
    IF valores_ida IS NOT NULL THEN
        RAISE EXCEPTION 'V85: se perdieron o se reescribieron valores del inmueble: %', valores_ida;
    END IF;

    -- Y NINGUNA opcion de multivalor se fue por delante de un borrado. Ninguna
    -- de las filas retiradas podia tener opciones --`area_terreno` es DECIMAL--,
    -- pero `atributo_propiedad_opcion` cuelga de `atributo_propiedad` con
    -- ON DELETE CASCADE, asi que un borrado sobre la clave equivocada se
    -- llevaria un conjunto entero SIN QUE NADA LO DIJERA. Se comprueba por eso.
    SELECT count(*) INTO borradas FROM atributo_propiedad_opcion;
    IF borradas <> (SELECT count(*) FROM v85_valores_antes v
                     JOIN atributo_propiedad_opcion o
                       ON o.id_atributo_propiedad = v.id_atributo_propiedad) THEN
        RAISE EXCEPTION
            'V85: el numero de valores multivalor cambio. Ningun borrado de este corte puede '
            'tocarlos: `area_terreno` es DECIMAL y no tiene opciones.';
    END IF;

    IF discrepan > 0 OR sin_valor > 0 THEN
        SELECT string_agg(r.id_propiedad || ':' || coalesce(r.valor_numero::text, '(sin valor)')
                          || ' vs metraje ' || coalesce(r.metraje::text, '(sin metraje)'),
                          ' | ' ORDER BY r.id_propiedad)
          INTO detalle
          FROM v85_area_en_terreno r WHERE r.veredicto IN ('DISCREPANTE', 'SIN_VALOR');
        RAISE NOTICE
            'V85: % valores de area_terreno sobre TERRENOS no coinciden con su metraje y se '
            'CONSERVAN sin tocar: %. Lo ambiguo permanece FALTANTE para el modelo nuevo: no se '
            'sabe cual de las dos superficies es la correcta y elegir una seria inventar. Se '
            'resuelve midiendo el lote, no migrando.', discrepan + sin_valor, detalle;
    END IF;

    RAISE NOTICE
        'V85: nacen 18 claves del suelo (17 OPC + condicion_terreno PUB en T), las siete LISTA '
        'CON vocabulario (% opciones) y % filas de aplicabilidad; area_terreno se retira de T '
        'conservando A y C, con % valores clasificados (% coincidian con metraje_total y se '
        'retiraron con linaje, % discrepantes conservados, % sin valor); quedan % claves del '
        'sistema activas.',
        n_opciones, (SELECT count(*) FROM catalogo_atributo c
                       JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
                      WHERE c.organizacion_id IS NULL AND c.clave = ANY (nuevas)),
        clasificado, coinciden, discrepan, sin_valor, activas;
END $$;

-- ---------------------------------------------------------------------
-- 7. La foto se retira con DROP explicito. Ver el bloque 0.
-- ---------------------------------------------------------------------
DROP TABLE v85_area_en_terreno;
DROP TABLE v85_valores_antes;
DROP TABLE v85_opciones_antes;
DROP TABLE v85_tipo_antes;
DROP TABLE v85_claves_antes;
