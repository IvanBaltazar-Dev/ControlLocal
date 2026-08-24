-- =====================================================================
-- V81 - Corte 4: el activo comercial, descrito
--
-- QUE HUECO CIERRA
-- El Corte 3 (V80) le dio a la vivienda 30 claves propias. El activo COMERCIAL
-- -- local (L), oficina (O), almacen/industrial (A) -- seguia describiendose con
-- el fondo comun: metraje, antiguedad, estacionamientos, rubro, carga electrica
-- y altura libre. Con eso, un almacen con anden elevado y 6 t/m2 y una nave sin
-- muelle sobre losa sin tratar son la misma ficha; y dos locales de 40 m2 a
-- S/ 3 000 -- uno a pie de calle en Miraflores, otro en el interior de una
-- galeria de Mesa Redonda -- entran en el mismo precio por m2 como si fueran el
-- mismo mercado.
--
-- Esta migracion siembra TREINTA Y NUEVE claves, sujeto PROPIEDAD, tomadas de
-- las secciones 3.3, 3.4, 3.5 y 3.7 de
-- `docs/ai/auditoria-profundidad-inmobiliaria.md`.
--
-- ESTE CORTE TERMINA TAMBIEN LAS INSTALACIONES DE LA VIVIENDA, y se acepta a
-- proposito. La seccion 3.5 mezcla tipos: `gas` aplica a D,C,L,O,A,T y
-- `agua_caliente` a C,D. El Corte 3 las excluyo expresamente y su encargo esta
-- congelado; no se reabre. Asi que entran aqui, con la vivienda de arrastre.
-- Igual con `gas`, `acceso_vehiculo_maximo` y `via_de_acceso`, que tocan T: su
-- tipo principal es comercial, y un corte se define por el tipo que lo MOTIVA,
-- no por que otros tipos arrastre su aplicabilidad. Los parametros urbanisticos
-- del terreno siguen en el Corte 5. Queda registrado como consecuencia, no como
-- hueco silencioso.
--
-- ------------------------------------------------------------------
-- LA EXIGENCIA: `tipo_acceso` NACE 'ALT' EN 'L'. LAS OTRAS 38, 'OPC'.
-- ------------------------------------------------------------------
-- Es DECISION DEL TITULAR, tomada con el efecto medido delante, y no es
-- cosmetica. `ALT` IMPIDE PUBLICAR, exactamente igual que `PUB`:
-- `AtributoPropiedadRepository.clavesQueImpidenPublicar:100` filtra
-- `a.exigencia in ('ALT', 'PUB')` -- por diseno desde V72. No es "avisar": es
-- HTTP 400 al anunciar.
--
-- Medido contra `controllocal_dev` el 2026-08-24, ANTES de escribir esto:
--
--     L: 21 propiedades, las 21 sin `tipo_acceso` (la clave no existia)
--     publicables hoy: 26 de 26, cero bloqueantes
--
-- => AL APLICAR ESTA MIGRACION, LAS 26 PUBLICABLES PASAN A 5. Los 21 locales
-- quedan fuera del mercado hasta que alguien los visite y registre el dato. Se
-- desbloquean UNO A UNO, segun se vean. ESO ES EL RESULTADO ESPERADO, no un
-- fallo, y la evidencia del corte lleva la lista de los 21 con su codigo para
-- que exista la lista de trabajo de campo: un corte que saca inventario del
-- mercado sin decir cual, lo saca a ciegas.
--
-- POR QUE ES DEFENDIBLE. `tipo_acceso` es el unico dato de este corte que el
-- agente tiene DELANTE cuando capta: esta de pie en el local. No se deduce del
-- distrito ni del metraje. Y sin el, 40 m2 a S/ 3 000 son caros a pie de calle
-- en Miraflores y absurdos en el interior de Mesa Redonda: el precio por m2
-- mezcla dos mercados distintos. Hoy hay 10 filas ALT en todo el sistema
-- (`metraje_total` en los siete tipos, `dormitorios` en C y D, `zonificacion` en
-- T) y NINGUNA exige salir a mirar. Esta es la primera, y por eso la decidio el
-- titular y no CONTROL.
--
-- LO QUE ESTA MIGRACION NO HACE PARA ALIVIARLO, y no por olvido:
--   * NO rellena `tipo_acceso` en los 21 locales. Ni por inferencia, ni por el
--     caso frecuente, ni con `A_PIE_DE_CALLE` "porque casi siempre lo es". Un
--     dato que no se sabe se declara FALTANTE -- regla no negociable del North
--     Star -- y es justo lo que hace el bloqueo aceptable: desbloquea el HECHO
--     VERIFICADO, no el relleno. Sembrar el valor convertiria una exigencia de
--     campo en una casilla ya marcada, que es lo contrario de lo decidido.
--   * NO le pone valor por defecto.
--   * NO toca `exigirPublicable` ni le anade excepcion para propiedades
--     anteriores a V81.
--   * NO sube ninguna otra. Las catorce PUB que propone la auditoria siguen
--     siendo PROPUESTA: hoy el sistema tendria su primera PUB, y PUB no informa
--     de nada -- no hay superficie del cable que reporte una PUB de la
--     PROPIEDAD. Esa promocion es un corte propio.
--
-- Y AL REVES QUE UNA PUB, ESTA ALT SI INFORMA: `atributosQueFaltan` se alimenta
-- de `clavesObligatoriasQueFaltan`, que filtra `a.exigencia = 'ALT'`, asi que la
-- ficha de cada uno de los 21 locales dice que le falta `tipo_acceso`, con su
-- rotulo. El bloqueo viaja con la instruccion de como quitarlo.
--
-- LO QUE ESTA MIGRACION NO HACE, ademas:
--   * `agua_desague` y `energia_electrica` NO entran: son los reemplazos de
--     `servicios_disponibles` y nacen en el Corte 5, junto con su retirada y con
--     la guarda "ninguna LISTA sin vocabulario" extendida a PROPIEDAD.
--     Separarlas antes deja un agujero de captura.
--   * `certificado_itse` NO retira `apto_licencia_funcionamiento`. La auditoria
--     describe la nueva como "el hecho verificable detras" de ese booleano sin
--     procedencia, pero retirar la vieja exige migracion de datos y es un corte
--     propio: el North Star prohibe retirar una captura antes de que su
--     reemplazo exista Y ESTE POBLADO. Las dos conviven, y queda anotado.
--   * `estado_ocupacion`, `lote_minimo_normativo` -> Corte 5.
--     `unidad_relacionada` -> Corte 6.
--   * Ninguna conversion de tipo, ninguna ESTRUCTURAL, ninguna LISTA_MULTIPLE.
--   * `familia` sigue NULL en las 39. El formulario pasa de 55 a 94 campos;
--     agruparlo es decision de presentacion y va con el corte del SPA.
--     REGISTRADO, no silenciado.
--   * El tipo X (OTRO) no entra en ninguna. Sigue sin auditar.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Las treinta y nueve claves.
--
-- `orden` continua donde paro el catalogo de la PROPIEDAD -- el maximo medido el
-- 2026-08-24 es 550 (`mascotas_reglamento`, V80) -- y avanza de diez en diez
-- desde 560 hasta 940, en el orden de lectura de la auditoria. Los huecos de
-- diez existen para que un corte posterior pueda intercalar sin renumerar.
--
-- Todas: destino ATRIBUTO, campo_estructural NULL, del_sistema true,
-- organizacion_id NULL, aplica_todos false, familia NULL. Ninguna es identidad
-- del inmueble, asi que ninguna es ESTRUCTURAL: describen situacion, y su
-- aplicabilidad depende del tipo. Es el criterio de D-E4-3.
--
-- El INSERT va en ASCII a proposito, para que se lea en una terminal sin UTF-8;
-- los acentos de `rotulo`, `ayuda` y `unidad` los repone el bloque 2. Patron de
-- V68, V79 y V80.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                               aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                               valor_minimo, valor_maximo, longitud_maxima,
                               destino, campo_estructural)
VALUES
    -- 1.1 Seccion 3.3 - el hecho que cierra un par huerfano ----------------

    -- El HECHO cuya CONDICION `se_entrega_implementado` existe desde V77. Va
    -- aparte de `estado_conservacion` (V80) a proposito: "en que estado esta" y
    -- "hasta donde llega la implementacion" son dos hechos distintos, y un local
    -- en casco puede estar impecable.
    (NULL, 'nivel_implementacion', 'Nivel de implementacion', 'LISTA', NULL,
     false, true, 560, 'PROPIEDAD', NULL,
     'Hasta donde llega la implementacion del espacio.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- 1.2 Seccion 3.4 - lo que el Corte 3 dejo fuera por ser de O, L o A ----

    (NULL, 'recepcion_edificio', 'Recepcion atendida', 'BOOLEANO', NULL,
     false, true, 570, 'PROPIEDAD', NULL,
     'Si el edificio tiene recepcion atendida por una persona.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- Regla del EDIFICIO, no del encargo: descalifica de golpe un call center o
    -- un cierre contable, y eso no se negocia con el propietario.
    (NULL, 'horario_acceso_edificio', 'Horario de acceso', 'LISTA', NULL,
     false, true, 580, 'PROPIEDAD', NULL,
     'En que horario se puede entrar al edificio.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'fibra_optica', 'Fibra optica en el edificio', 'BOOLEANO', NULL,
     false, true, 590, 'PROPIEDAD', NULL,
     'Si el edificio tiene fibra optica instalada.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'certificacion_sostenible', 'Certificacion sostenible', 'LISTA', NULL,
     false, true, 600, 'PROPIEDAD', NULL,
     'Que certificacion sostenible tiene el edificio.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- 1.3 Seccion 3.5 - instalaciones --------------------------------------

    -- No se supone por distrito: la red de Calidda crece manzana a manzana, y
    -- "hay gas en el distrito" no significa que llegue a esta puerta.
    (NULL, 'gas', 'Suministro de gas', 'LISTA', NULL,
     false, true, 610, 'PROPIEDAD', NULL,
     'Como llega el gas a este inmueble.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'agua_caliente', 'Agua caliente', 'LISTA', NULL,
     false, true, 620, 'PROPIEDAD', NULL,
     'Como se produce el agua caliente.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- No lo sustituye `carga_electrica_kw`: esa dice CUANTA potencia hay, esta
    -- dice DE QUE CLASE es. Un horno trifasico no funciona con 220 monofasico
    -- por muchos kW que diga la ficha.
    (NULL, 'suministro_electrico', 'Tipo de suministro', 'LISTA', NULL,
     false, true, 630, 'PROPIEDAD', NULL,
     'De que clase es el suministro electrico.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'respaldo_electrico', 'Respaldo electrico', 'LISTA', NULL,
     false, true, 640, 'PROPIEDAD', NULL,
     'Si hay grupo electrogeno y hasta donde llega.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'aire_acondicionado', 'Aire acondicionado', 'LISTA', NULL,
     false, true, 650, 'PROPIEDAD', NULL,
     'Que aire acondicionado tiene y de quien depende.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'medidor_servicios', 'Medidor de servicios', 'LISTA', NULL,
     false, true, 660, 'PROPIEDAD', NULL,
     'Si los servicios se miden aparte o se prorratean.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'sistema_contra_incendios', 'Sistema contra incendios', 'LISTA', NULL,
     false, true, 670, 'PROPIEDAD', NULL,
     'Que sistema contra incendios tiene.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- Habilita o descarta de golpe al segmento gastronomico, que es de los que
    -- mas local busca. Sin ducto no hay cocina, y eso se sabe mirando.
    (NULL, 'extraccion_humos', 'Extraccion de humos', 'LISTA', NULL,
     false, true, 680, 'PROPIEDAD', NULL,
     'Si hay ducto de extraccion y hasta donde llega.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- 1.4 Seccion 3.7 - comercial y logistico -------------------------------

    -- LA UNICA 'ALT' DEL CORTE. Ver la cabecera: decision del titular con el
    -- efecto medido delante, y la unica clave de este corte que se sabe estando
    -- de pie en el local.
    (NULL, 'tipo_acceso', 'Tipo de acceso', 'LISTA', NULL,
     false, true, 690, 'PROPIEDAD', NULL,
     'Como se entra al local desde la calle.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'en_esquina', 'Esta en esquina', 'BOOLEANO', NULL,
     false, true, 700, 'PROPIEDAD', NULL,
     'Si el inmueble hace esquina.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'clase_edificio', 'Clase de edificio', 'LISTA', NULL,
     false, true, 710, 'PROPIEDAD', NULL,
     'La clase del edificio de oficinas.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'metraje_arrendable', 'Metraje arrendable', 'DECIMAL', 'm2',
     false, true, 720, 'PROPIEDAD', NULL,
     'Los metros que se cobran, que no siempre son los que se ocupan.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'banos_comunes_piso', 'Banos comunes en el piso', 'BOOLEANO', NULL,
     false, true, 730, 'PROPIEDAD', NULL,
     'Si los banos son comunes del piso y no de la unidad.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'posiciones_trabajo', 'Posiciones de trabajo', 'ENTERO', NULL,
     false, true, 740, 'PROPIEDAD', NULL,
     'Cuantos puestos de trabajo caben o estan instalados.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'salas_reunion', 'Salas de reunion', 'ENTERO', NULL,
     false, true, 750, 'PROPIEDAD', NULL,
     'Cuantas salas de reunion tiene.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'aforo_itse', 'Aforo autorizado (ITSE)', 'ENTERO', 'personas',
     false, true, 760, 'PROPIEDAD', NULL,
     'Cuantas personas autoriza el certificado de Defensa Civil.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    -- El hecho verificable detras de `apto_licencia_funcionamiento`, que es un
    -- booleano sin procedencia. NO la retira: conviven, y la retirada es un
    -- corte propio con su migracion de datos.
    (NULL, 'certificado_itse', 'Certificado ITSE', 'LISTA', NULL,
     false, true, 770, 'PROPIEDAD', NULL,
     'En que estado esta el certificado de Defensa Civil.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'area_libre', 'Area libre / patio de maniobras', 'DECIMAL', 'm2',
     false, true, 780, 'PROPIEDAD', NULL,
     'Cuantos metros cuadrados de patio de maniobras hay.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'profundidad_patio_maniobras', 'Profundidad de patio', 'DECIMAL', 'm',
     false, true, 790, 'PROPIEDAD', NULL,
     'Cuantos metros de fondo tiene el patio de maniobras.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'acceso_vehiculo_maximo', 'Vehiculo maximo que ingresa', 'LISTA', NULL,
     false, true, 800, 'PROPIEDAD', NULL,
     'El vehiculo mas grande que puede entrar y maniobrar.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- `muelles_carga` y `tipo_muelle` son DOS claves a proposito: contar muelles
    -- sin decir de que tipo deja la cifra sin significado -- cuatro a nivel de
    -- piso y cuatro con nivelador son dos operaciones logisticas distintas.
    (NULL, 'muelles_carga', 'Muelles de carga', 'ENTERO', NULL,
     false, true, 810, 'PROPIEDAD', NULL,
     'Cuantos muelles de carga tiene.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'tipo_muelle', 'Tipo de muelle', 'LISTA', NULL,
     false, true, 820, 'PROPIEDAD', NULL,
     'De que tipo son los muelles.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'puertas_ingreso', 'Puertas de ingreso', 'ENTERO', NULL,
     false, true, 830, 'PROPIEDAD', NULL,
     'Cuantas puertas de ingreso tiene la nave.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'ancho_puerta_ingreso', 'Ancho de puerta', 'DECIMAL', 'm',
     false, true, 840, 'PROPIEDAD', NULL,
     'Cuantos metros de ancho tiene la puerta de ingreso.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'alto_puerta_ingreso', 'Alto de puerta', 'DECIMAL', 'm',
     false, true, 850, 'PROPIEDAD', NULL,
     'Cuantos metros de alto tiene la puerta de ingreso.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'capacidad_portante_piso', 'Capacidad portante del piso', 'DECIMAL', 't/m2',
     false, true, 860, 'PROPIEDAD', NULL,
     'Cuanto peso aguanta el piso por metro cuadrado.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'tipo_piso', 'Tipo de piso', 'LISTA', NULL,
     false, true, 870, 'PROPIEDAD', NULL,
     'Como es el piso de la nave.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- TEXTO y no DECIMAL: se declara como una malla ("12 x 24"), y partirla en
    -- dos numeros seria inventar cual es cual.
    (NULL, 'luz_entre_columnas', 'Luz entre columnas', 'TEXTO', 'm',
     false, true, 880, 'PROPIEDAD', NULL,
     'La distancia entre columnas, como la declara la ficha tecnica.',
     NULL, NULL, 40, 'ATRIBUTO', NULL),

    (NULL, 'posiciones_pallet', 'Capacidad en posiciones pallet', 'ENTERO', NULL,
     false, true, 890, 'PROPIEDAD', NULL,
     'Cuantas posiciones pallet caben.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    -- Aparte, porque sin separarla esos metros se cotizan como almacen y son
    -- oficina: el precio por m2 sale mal para las dos partes.
    (NULL, 'area_oficinas', 'Area de oficinas administrativas', 'DECIMAL', 'm2',
     false, true, 900, 'PROPIEDAD', NULL,
     'Cuantos metros cuadrados son oficina dentro de la nave.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'condicion_almacenamiento', 'Condicion de almacenamiento', 'LISTA', NULL,
     false, true, 910, 'PROPIEDAD', NULL,
     'En que condicion se puede almacenar.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'balanza_camionera', 'Balanza camionera', 'BOOLEANO', NULL,
     false, true, 920, 'PROPIEDAD', NULL,
     'Si tiene balanza para camiones.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- No se mezcla con `estacionamientos`: juntar autos y camiones hace el
    -- numero inutil para los dos casos.
    (NULL, 'estacionamientos_camiones', 'Estacionamiento de camiones', 'ENTERO', NULL,
     false, true, 930, 'PROPIEDAD', NULL,
     'Cuantos camiones pueden estacionar.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    -- TEXTO y no LISTA: "Panamericana Sur km 32" no tiene vocabulario cerrado, y
    -- forzarlo a una lista obligaria a inventar categorias que nadie usa.
    (NULL, 'via_de_acceso', 'Via principal de acceso', 'TEXTO', NULL,
     false, true, 940, 'PROPIEDAD', NULL,
     'Por que via principal se llega.',
     NULL, NULL, 120, 'ATRIBUTO', NULL);

-- ---------------------------------------------------------------------
-- 2. Lo que LEE una persona: rotulo, ayuda y unidad, con acentos.
--
-- Dos bloques unicos en vez de treinta y nueve UPDATE sueltos, por la misma
-- razon por la que el INSERT va en ASCII: que se puedan leer enteros y comparar
-- clave a clave con la tabla del encargo.
--
-- Las ayudas dicen el HECHO y para que sirve. Ni metaforas ni lenguaje de
-- manual: es lo que el agente lee mientras habla con el propietario.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo c
   SET rotulo = v.rotulo, ayuda = v.ayuda, unidad = v.unidad
  FROM (VALUES
    ('nivel_implementacion', 'Nivel de implementación',
     'Hasta dónde llega la implementación del espacio. Es otra pregunta que el estado de conservación: un local en casco puede estar impecable.',
     NULL::text),
    ('recepcion_edificio', 'Recepción atendida',
     'Si el edificio tiene recepción atendida por una persona.', NULL),
    ('horario_acceso_edificio', 'Horario de acceso',
     'En qué horario se puede entrar al edificio. Es regla del edificio y no del contrato: descalifica un call center o un cierre contable.', NULL),
    ('fibra_optica', 'Fibra óptica en el edificio',
     'Si el edificio tiene fibra óptica instalada.', NULL),
    ('certificacion_sostenible', 'Certificación sostenible',
     'Qué certificación sostenible tiene el edificio.', NULL),
    ('gas', 'Suministro de gas',
     'Cómo llega el gas a este inmueble. No se supone por el distrito: la red crece manzana a manzana, y que haya gas en la zona no significa que llegue a esta puerta.', NULL),
    ('agua_caliente', 'Agua caliente',
     'Cómo se produce el agua caliente.', NULL),
    ('suministro_electrico', 'Tipo de suministro',
     'De qué clase es el suministro eléctrico. No es lo mismo que la carga en kW: un horno trifásico no funciona con 220 monofásico por mucha potencia que haya.', NULL),
    ('respaldo_electrico', 'Respaldo eléctrico',
     'Si hay grupo electrógeno y hasta dónde llega.', NULL),
    ('aire_acondicionado', 'Aire acondicionado',
     'Qué aire acondicionado tiene y de quién depende.', NULL),
    ('medidor_servicios', 'Medidor de servicios',
     'Si los servicios se miden aparte o se prorratean entre varios.', NULL),
    ('sistema_contra_incendios', 'Sistema contra incendios',
     'Qué sistema contra incendios tiene.', NULL),
    ('extraccion_humos', 'Extracción de humos',
     'Si hay ducto de extracción y hasta dónde llega. Sin ducto no hay cocina, y eso descarta de golpe al segmento gastronómico.', NULL),
    ('tipo_acceso', 'Tipo de acceso',
     'Cómo se entra al local desde la calle. Es obligatorio para poder anunciar un local, y se sabe estando de pie delante: sin este dato, 40 m² a S/ 3 000 son caros a pie de calle y baratos en el interior de una galería, y el precio por m² mezcla dos mercados.', NULL),
    ('en_esquina', 'Está en esquina',
     'Si el inmueble hace esquina.', NULL),
    ('clase_edificio', 'Clase de edificio',
     'La clase del edificio de oficinas.', NULL),
    ('metraje_arrendable', 'Metraje arrendable',
     'Los metros que se cobran, que no siempre son los que se ocupan.', 'm²'),
    ('banos_comunes_piso', 'Baños comunes en el piso',
     'Si los baños son comunes del piso y no de la unidad.', NULL),
    ('posiciones_trabajo', 'Posiciones de trabajo',
     'Cuántos puestos de trabajo caben o están instalados.', NULL),
    ('salas_reunion', 'Salas de reunión',
     'Cuántas salas de reunión tiene.', NULL)
  ) AS v(clave, rotulo, ayuda, unidad)
 WHERE c.organizacion_id IS NULL AND c.clave = v.clave;

UPDATE catalogo_atributo c
   SET rotulo = v.rotulo, ayuda = v.ayuda, unidad = v.unidad
  FROM (VALUES
    ('aforo_itse', 'Aforo autorizado (ITSE)',
     'Cuántas personas autoriza el certificado de Defensa Civil.', 'personas'::text),
    ('certificado_itse', 'Certificado ITSE',
     'En qué estado está el certificado de Defensa Civil. Es el hecho verificable detrás de «Apto para licencia de funcionamiento», que hoy es un sí o un no sin procedencia.', NULL),
    ('area_libre', 'Área libre / patio de maniobras',
     'Cuántos metros cuadrados de patio de maniobras hay.', 'm²'),
    ('profundidad_patio_maniobras', 'Profundidad de patio',
     'Cuántos metros de fondo tiene el patio de maniobras. Es lo que decide qué camión puede girar.', 'm'),
    ('acceso_vehiculo_maximo', 'Vehículo máximo que ingresa',
     'El vehículo más grande que puede entrar y maniobrar.', NULL),
    ('muelles_carga', 'Muelles de carga',
     'Cuántos muelles de carga tiene. El tipo va aparte: cuatro a nivel de piso y cuatro con nivelador no son la misma operación.', NULL),
    ('tipo_muelle', 'Tipo de muelle',
     'De qué tipo son los muelles.', NULL),
    ('puertas_ingreso', 'Puertas de ingreso',
     'Cuántas puertas de ingreso tiene la nave.', NULL),
    ('ancho_puerta_ingreso', 'Ancho de puerta',
     'Cuántos metros de ancho tiene la puerta de ingreso.', 'm'),
    ('alto_puerta_ingreso', 'Alto de puerta',
     'Cuántos metros de alto tiene la puerta de ingreso.', 'm'),
    ('capacidad_portante_piso', 'Capacidad portante del piso',
     'Cuánto peso aguanta el piso por metro cuadrado.', 't/m²'),
    ('tipo_piso', 'Tipo de piso',
     'Cómo es el piso de la nave.', NULL),
    ('luz_entre_columnas', 'Luz entre columnas',
     'La distancia entre columnas, como la declara la ficha técnica. Se escribe tal cual, por ejemplo «12 x 24».', 'm'),
    ('posiciones_pallet', 'Capacidad en posiciones pallet',
     'Cuántas posiciones pallet caben.', NULL),
    ('area_oficinas', 'Área de oficinas administrativas',
     'Cuántos metros cuadrados son oficina dentro de la nave. Van aparte para que no se coticen como almacén.', 'm²'),
    ('condicion_almacenamiento', 'Condición de almacenamiento',
     'En qué condición se puede almacenar.', NULL),
    ('balanza_camionera', 'Balanza camionera',
     'Si tiene balanza para camiones.', NULL),
    ('estacionamientos_camiones', 'Estacionamiento de camiones',
     'Cuántos camiones pueden estacionar. No se mezcla con los estacionamientos de autos: juntarlos hace el número inútil para los dos casos.', NULL),
    ('via_de_acceso', 'Vía principal de acceso',
     'Por qué vía principal se llega.', NULL)
  ) AS v(clave, rotulo, ayuda, unidad)
 WHERE c.organizacion_id IS NULL AND c.clave = v.clave;

-- ---------------------------------------------------------------------
-- 3. A que tipos aplica cada una.
--
-- TREINTA Y OCHO 'OPC' Y UNA 'ALT': `tipo_acceso` en `L`, y solo ahi.
--
-- `requerido` se escribe ademas de `exigencia` porque son columna y espejo desde
-- V72: `true` EXACTAMENTE en la fila de `tipo_acceso`/`L`, `false` en las otras
-- setenta. El guard 2.4 de V78 lo comprueba en TODO el catalogo, no solo en lo
-- nuevo, asi que una fila que escriba solo una de las dos rompe la migracion.
--
-- Van en `catalogo_atributo_tipo` y NUNCA en `catalogo_atributo_operacion`: son
-- del sujeto PROPIEDAD, y la guarda 2.5 de V78 rompe la migracion si una clave
-- declara su aplicabilidad en la tabla del otro sujeto.
--
-- La aplicabilidad de cada clave es la que declara la auditoria en sus secciones
-- 3.3, 3.4, 3.5 y 3.7, contrastada contra la base antes de escribirla. Los
-- comentarios explican el agrupamiento; la fuente de la decision es la
-- auditoria, no ellos.
-- ---------------------------------------------------------------------

-- 3.1 A,L,O -- el activo comercial completo. Es el grupo mayor del corte: lo que
--     se pregunta de cualquier espacio de trabajo o de venta, sea nave, local u
--     oficina.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('L'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('nivel_implementacion', 'horario_acceso_edificio',
                   'suministro_electrico', 'medidor_servicios',
                   'sistema_contra_incendios', 'en_esquina', 'metraje_arrendable',
                   'aforo_itse', 'certificado_itse');

-- 3.2 O -- lo que solo tiene sentido en una oficina.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'O', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('recepcion_edificio', 'certificacion_sostenible',
                   'clase_edificio', 'banos_comunes_piso',
                   'posiciones_trabajo', 'salas_reunion');

-- 3.3 L,O -- lo del edificio urbano de local y oficina.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('fibra_optica', 'aire_acondicionado');

-- 3.4 A,C,D,L,O,T -- `gas` llega a todo lo que tenga acometida posible, incluido
--     el terreno. Es una de las dos claves de vivienda que este corte hereda de
--     la seccion 3.5 (ver cabecera): el Corte 3 las excluyo y no se reabre.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('C'), ('D'), ('L'), ('O'), ('T')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'gas';

-- 3.5 C,D -- `agua_caliente` es la otra heredada: vivienda pura.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('C'), ('D')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'agua_caliente';

-- 3.6 A,D,L,O -- el respaldo electrico llega tambien al departamento, donde el
--     grupo del edificio alimenta areas comunes o la unidad entera.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('D'), ('L'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'respaldo_electrico';

-- 3.7 L -- la extraccion de humos es del local: es lo que decide si ahi cabe una
--     cocina.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'L', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL AND c.clave = 'extraccion_humos';

-- 3.8 LA FILA 'ALT'. Una sola, `tipo_acceso` en `L`, con `requerido = true`.
--
--     Se escribe APARTE de todas las demas y no dentro de un CROSS JOIN a
--     proposito: es la unica fila de este corte que cambia lo que el sistema
--     deja hacer, y tiene que poder leerse sola. Al aplicarse, los 21 locales de
--     la cartera dejan de ser publicables hasta que alguien los visite. Ver la
--     cabecera: decision del titular, con el efecto medido delante.
--
--     `requerido = true` no es adorno: es el espejo exacto de `exigencia='ALT'`
--     que vigila el guard 2.4 de V78 sobre todo el catalogo.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'L', true, 'ALT'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_acceso';

-- 3.9 A,L,T -- que vehiculo entra: importa en la nave, en el local con carga y
--     en el terreno.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('L'), ('T')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'acceso_vehiculo_maximo';

-- 3.10 A,T -- la via principal de acceso: nave y terreno.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('T')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'via_de_acceso';

-- 3.11 A -- el bloque logistico entero. Quince claves que solo tienen sentido en
--      una nave: muelles, puertas, piso, pallets y patio.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'A', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('area_libre', 'profundidad_patio_maniobras', 'muelles_carga',
                   'tipo_muelle', 'puertas_ingreso', 'ancho_puerta_ingreso',
                   'alto_puerta_ingreso', 'capacidad_portante_piso', 'tipo_piso',
                   'luz_entre_columnas', 'posiciones_pallet', 'area_oficinas',
                   'condicion_almacenamiento', 'balanza_camionera',
                   'estacionamientos_camiones');

-- ---------------------------------------------------------------------
-- 4. Los dieciocho vocabularios.
--
-- `catalogo_atributo_opcion` es la UNICA autoridad de estos valores: no hay enum
-- Java, ni Set escrito a mano, ni CHECK enumerativo paralelo, ni lista en
-- Angular. Anadir una opcion el dia que haga falta tiene que ser una fila, no un
-- despliegue.
--
-- CODIGOS `UPPER_SNAKE` ASCII QUE EMPIEZAN POR LETRA -- `^[A-Z][A-Z0-9_]*$`. Por
-- eso el horario 24/7 se codifica `H24_7` y no `24_7`: un codigo que empieza por
-- digito no es un identificador, y en cuanto alguien lo use como nombre de
-- constante, de clase CSS o de columna derivada, deja de serlo en silencio. El
-- rotulo si dice "24/7", que es lo que se lee. La guarda 6.5 lo comprueba con
-- ese regex sobre las 83 opciones, para que no dependa de que alguien se fije.
--
-- `orden` denso desde 1, y los acentos del rotulo repuestos al final del bloque.
-- ---------------------------------------------------------------------

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('CASCO_OBRA_GRIS',        'Casco / obra gris',        1),
        ('PLANTA_LIBRE',           'Planta libre',             2),
        ('IMPLEMENTADO_PARCIAL',   'Implementado parcial',     3),
        ('IMPLEMENTADO_COMPLETO',  'Implementado completo',    4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'nivel_implementacion';

-- H24_7, no 24_7. Ver la nota de arriba.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('H24_7',            '24/7',                      1),
        ('LUN_VIE_OFICINA',  'Lunes a viernes, oficina',  2),
        ('LUN_SAB_OFICINA',  'Lunes a sabado, oficina',   3),
        ('OTRO',             'Otro',                      4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'horario_acceso_edificio';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('NINGUNA',        'Ninguna',        1),
        ('LEED_CERTIFIED', 'LEED Certified', 2),
        ('LEED_SILVER',    'LEED Silver',    3),
        ('LEED_GOLD',      'LEED Gold',      4),
        ('LEED_PLATINUM',  'LEED Platinum',  5),
        ('OTRA',           'Otra',           6)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'certificacion_sostenible';

-- SIN_RED_CERCANA y RED_EN_LA_VIA son dos respuestas distintas y las dos son
-- verificadas: "no hay red" y "la red pasa por la puerta pero no esta conectada"
-- cambian por completo el coste de instalarlo.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('SIN_RED_CERCANA',     'Sin red cercana',        1),
        ('RED_EN_LA_VIA',       'Red en la via',          2),
        ('INSTALADO',           'Instalado',              3),
        ('GLP_TANQUE_EXTERNO',  'GLP con tanque externo', 4),
        ('GLP_BALONES',         'GLP con balones',        5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'gas';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('NO_TIENE',        'No tiene',         1),
        ('TERMA_ELECTRICA', 'Terma electrica',  2),
        ('TERMA_A_GAS',     'Terma a gas',      3),
        ('CENTRALIZADA',    'Centralizada',     4),
        ('SOLAR',           'Solar',            5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'agua_caliente';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('MONOFASICO_220',     'Monofasico 220 V',    1),
        ('TRIFASICO_380',      'Trifasico 380 V',     2),
        ('TRIFASICO_440',      'Trifasico 440 V',     3),
        ('SUBESTACION_PROPIA', 'Subestacion propia',  4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'suministro_electrico';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('NO_TIENE',                          'No tiene',                                1),
        ('GRUPO_ELECTROGENO_AREAS_COMUNES',   'Grupo electrogeno para areas comunes',    2),
        ('GRUPO_ELECTROGENO_TOTAL',           'Grupo electrogeno total',                 3)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'respaldo_electrico';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('NINGUNO',              'Ninguno',                    1),
        ('SPLIT_EN_UNIDAD',      'Split en la unidad',         2),
        ('CENTRAL_DEL_EDIFICIO', 'Central del edificio',       3),
        ('VRV_INDEPENDIENTE',    'VRV independiente',          4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'aire_acondicionado';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('INDEPENDIENTE',         'Independiente',            1),
        ('COMPARTIDO_PRORRATEO',  'Compartido con prorrateo', 2),
        ('SIN_MEDIDOR',           'Sin medidor',              3)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'medidor_servicios';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('NINGUNO',           'Ninguno',            1),
        ('EXTINTORES',        'Extintores',         2),
        ('GABINETES',         'Gabinetes',          3),
        ('ROCIADORES',        'Rociadores',         4),
        ('ROCIADORES_ESFR',   'Rociadores ESFR',    5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'sistema_contra_incendios';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('SIN_DUCTO',          'Sin ducto',            1),
        ('DUCTO_PROYECTADO',   'Ducto proyectado',     2),
        ('DUCTO_A_AZOTEA',     'Ducto a azotea',       3),
        ('CAMPANA_INSTALADA',  'Campana instalada',    4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'extraccion_humos';

-- El vocabulario de la unica ALT del corte. Siete formas de entrar, y ninguna
-- es "no se": para eso esta la ausencia del dato, que es justo lo que bloquea.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('A_PIE_DE_CALLE',       'A pie de calle',        1),
        ('ESQUINA_A_CALLE',      'Esquina a calle',       2),
        ('GALERIA_INTERIOR',     'Galeria interior',      3),
        ('PASAJE_COMERCIAL',     'Pasaje comercial',      4),
        ('CENTRO_COMERCIAL',     'Centro comercial',      5),
        ('INTERIOR_DE_EDIFICIO', 'Interior de edificio',  6),
        ('MERCADO',              'Mercado',               7)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_acceso';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('A_PLUS',    'A+',        1),
        ('A',         'A',         2),
        ('B',         'B',         3),
        ('C',         'C',         4),
        ('NO_APLICA', 'No aplica', 5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'clase_edificio';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('VIGENTE',    'Vigente',     1),
        ('VENCIDO',    'Vencido',     2),
        ('EN_TRAMITE', 'En tramite',  3),
        ('NO_TIENE',   'No tiene',    4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'certificado_itse';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('CAMIONETA',           'Camioneta',             1),
        ('CAMION_2_EJES',       'Camion de 2 ejes',      2),
        ('CAMION_3_EJES',       'Camion de 3 ejes',      3),
        ('TRAILER_T3S3',        'Trailer T3S3',          4),
        ('CONTENEDOR_40_PIES',  'Contenedor de 40 pies', 5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'acceso_vehiculo_maximo';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('SIN_MUELLE',           'Sin muelle',            1),
        ('A_NIVEL_DE_PISO',      'A nivel de piso',       2),
        ('ANDEN_ELEVADO',        'Anden elevado',         3),
        ('ANDEN_CON_NIVELADOR',  'Anden con nivelador',   4),
        ('MIXTO',                'Mixto',                 5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_muelle';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('CONCRETO_PULIDO',      'Concreto pulido',       1),
        ('CONCRETO_ENDURECIDO',  'Concreto endurecido',   2),
        ('LOSA_SIN_TRATAR',      'Losa sin tratar',       3),
        ('AFIRMADO',             'Afirmado',              4),
        ('TIERRA',               'Tierra',                5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_piso';

INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('SECO',                         'Seco',                           1),
        ('REFRIGERADO',                  'Refrigerado',                    2),
        ('CONGELADO',                    'Congelado',                      3),
        ('MATERIALES_PELIGROSOS',        'Materiales peligrosos',          4),
        ('DEPOSITO_TEMPORAL_ADUANERO',   'Deposito temporal aduanero',     5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'condicion_almacenamiento';

-- Los acentos de los rotulos que se leen en el selector. El codigo (`valor`) no
-- se toca nunca: es lo que se compara entre organizaciones.
UPDATE catalogo_atributo_opcion o
   SET rotulo = v.rotulo
  FROM catalogo_atributo c,
       (VALUES
        ('horario_acceso_edificio',  'LUN_SAB_OFICINA',                'Lunes a sábado, oficina'),
        ('gas',                      'RED_EN_LA_VIA',                  'Red en la vía'),
        ('agua_caliente',            'TERMA_ELECTRICA',                'Terma eléctrica'),
        ('suministro_electrico',     'MONOFASICO_220',                 'Monofásico 220 V'),
        ('suministro_electrico',     'TRIFASICO_380',                  'Trifásico 380 V'),
        ('suministro_electrico',     'TRIFASICO_440',                  'Trifásico 440 V'),
        ('suministro_electrico',     'SUBESTACION_PROPIA',             'Subestación propia'),
        ('respaldo_electrico',       'GRUPO_ELECTROGENO_AREAS_COMUNES','Grupo electrógeno para áreas comunes'),
        ('respaldo_electrico',       'GRUPO_ELECTROGENO_TOTAL',        'Grupo electrógeno total'),
        ('tipo_acceso',              'GALERIA_INTERIOR',               'Galería interior'),
        ('acceso_vehiculo_maximo',   'CAMION_2_EJES',                  'Camión de 2 ejes'),
        ('acceso_vehiculo_maximo',   'CAMION_3_EJES',                  'Camión de 3 ejes'),
        ('acceso_vehiculo_maximo',   'TRAILER_T3S3',                   'Tráiler T3S3'),
        ('tipo_muelle',              'ANDEN_ELEVADO',                  'Andén elevado'),
        ('tipo_muelle',              'ANDEN_CON_NIVELADOR',            'Andén con nivelador'),
        ('condicion_almacenamiento', 'DEPOSITO_TEMPORAL_ADUANERO',     'Depósito temporal aduanero')
       ) AS v(clave, valor, rotulo)
 WHERE c.id_catalogo_atributo = o.id_catalogo_atributo
   AND c.organizacion_id IS NULL
   AND c.clave = v.clave
   AND o.valor = v.valor;

-- ---------------------------------------------------------------------
-- 5. La unica correccion de dato del corte: la unidad de
--    `area_minima_arrendable` recupera su acento (D-BASE-4).
--
-- V77 la sembro con `m2` y no le aplico el UPDATE de acentos que si recibieron
-- sus hermanas. Es la ULTIMA clave del catalogo con `unidad = 'm2'`: despues de
-- esto, cero.
--
-- Es clave del ENCARGO y es comercial -- el metraje minimo que el titular acepta
-- arrendar de una nave o un local --, asi que este corte es su sitio.
-- `proteger_catalogo_del_sistema()` no bloquea el UPDATE de `unidad`: solo
-- vigila `clave`, `tipo_dato`, `del_sistema` y `organizacion_id`. No se toca
-- ningun valor escrito: es el rotulo de la unidad, no el dato.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo
   SET unidad = 'm²'
 WHERE organizacion_id IS NULL AND clave = 'area_minima_arrendable';

-- ---------------------------------------------------------------------
-- 6. Las guardas.
--
-- Comprueban invariantes del estado resultante, no cifras escritas a mano donde
-- una invariante sirve. Las cifras que si son literales -- 39 claves, 83
-- opciones, 71 filas -- lo son porque son el CONTENIDO de esta migracion y no el
-- tamano de nada que crezca con el uso.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    claves TEXT[] := ARRAY[
        'nivel_implementacion',
        'recepcion_edificio', 'horario_acceso_edificio', 'fibra_optica',
        'certificacion_sostenible',
        'gas', 'agua_caliente', 'suministro_electrico', 'respaldo_electrico',
        'aire_acondicionado', 'medidor_servicios', 'sistema_contra_incendios',
        'extraccion_humos',
        'tipo_acceso', 'en_esquina', 'clase_edificio', 'metraje_arrendable',
        'banos_comunes_piso', 'posiciones_trabajo', 'salas_reunion', 'aforo_itse',
        'certificado_itse', 'area_libre', 'profundidad_patio_maniobras',
        'acceso_vehiculo_maximo', 'muelles_carga', 'tipo_muelle',
        'puertas_ingreso', 'ancho_puerta_ingreso', 'alto_puerta_ingreso',
        'capacidad_portante_piso', 'tipo_piso', 'luz_entre_columnas',
        'posiciones_pallet', 'area_oficinas', 'condicion_almacenamiento',
        'balanza_camionera', 'estacionamientos_camiones', 'via_de_acceso'];
    faltan            TEXT;
    sin_aplicabilidad TEXT;
    cruce             TEXT;
    lista_sin_vocab   TEXT;
    mal_exigidas      TEXT;
    codigo_malo       TEXT;
    mal_destino       TEXT;
    espejo            TEXT;
    con_valor         BIGINT;
    opciones          INT;
    filas_tipo        INT;
    filas_alt         INT;
    huecos            TEXT;
    unidades_sin_acento INT;
BEGIN
    -- 6.0 Son treinta y nueve. Un typo en el array haria que todo lo demas se
    --     comprobara sobre un conjunto equivocado y saliera verde.
    IF array_length(claves, 1) <> 39 THEN
        RAISE EXCEPTION 'V81: el array de claves tiene % entradas y el corte son 39', array_length(claves, 1);
    END IF;

    -- 6.1 Las treinta y nueve entraron, activas y del sujeto correcto.
    SELECT string_agg(k, ', ') INTO faltan
      FROM unnest(claves) AS k
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.organizacion_id IS NULL AND c.clave = k
                          AND c.activo AND c.del_sistema AND c.sujeto = 'PROPIEDAD');
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'V81: estas claves no llegaron al catalogo de la PROPIEDAD: %', faltan;
    END IF;

    -- 6.2 Ninguna sin decir a que tipos aplica: seria invisible en el alta y en
    --     el editor, y nadie lo notaria hasta echarla en falta.
    SELECT string_agg(c.clave, ', ') INTO sin_aplicabilidad
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves) AND NOT c.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = c.id_catalogo_atributo);
    IF sin_aplicabilidad IS NOT NULL THEN
        RAISE EXCEPTION 'V81: claves sin aplicabilidad declarada: %', sin_aplicabilidad;
    END IF;

    -- 6.3 Y ninguna con la suya en la tabla del otro sujeto. Guarda 2.5 de V78
    --     sobre el estado resultante, en las dos direcciones.
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
        RAISE EXCEPTION 'V81: claves con la aplicabilidad en la tabla del otro sujeto: %', cruce;
    END IF;

    -- 6.4 Toda LISTA nueva tiene vocabulario. Sin el,
    --     `MotorDeCaptura.controlDe` la degrada a TEXTO y el trigger acepta
    --     cualquier cadena: la clave nace muda y nadie lo ve. Acotada a ESTAS
    --     claves a proposito -- la guarda global de V77 solo mira ENCARGO, y
    --     extenderla hoy a toda la PROPIEDAD haria fallar por
    --     `servicios_disponibles`, que este corte no toca (nace su reemplazo en
    --     el Corte 5, y la guarda va con el).
    SELECT string_agg(c.clave, ', ') INTO lista_sin_vocab
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND c.tipo_dato IN ('LISTA', 'LISTA_MULTIPLE')
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo
                          AND o.activo);
    IF lista_sin_vocab IS NOT NULL THEN
        RAISE EXCEPTION 'V81: listas sin vocabulario sembrado: %', lista_sin_vocab;
    END IF;

    -- 6.5 Todo codigo es UPPER_SNAKE Y EMPIEZA POR LETRA. Es la razon de que el
    --     horario 24/7 se codifique `H24_7`: un codigo que empieza por digito no
    --     es un identificador, y deja de serlo en silencio en cuanto alguien lo
    --     use como nombre de constante o de clase CSS.
    SELECT string_agg(c.clave || '/' || o.valor, ', ') INTO codigo_malo
      FROM catalogo_atributo c
      JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND o.valor !~ '^[A-Z][A-Z0-9_]*$';
    IF codigo_malo IS NOT NULL THEN
        RAISE EXCEPTION 'V81: codigos que no son UPPER_SNAKE empezando por letra: %', codigo_malo;
    END IF;

    -- 6.6 LA EXIGENCIA, que es la decision de este corte. EXACTAMENTE UNA fila
    --     ALT -- `tipo_acceso` en L -- y todas las demas OPC. Una ALT de mas
    --     saca del mercado a un tipo entero sin que nadie lo haya decidido.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad || '=' || t.exigencia, ', ')
      INTO mal_exigidas
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND NOT (t.exigencia = 'OPC'
                OR (c.clave = 'tipo_acceso' AND t.tipo_propiedad = 'L' AND t.exigencia = 'ALT'));
    IF mal_exigidas IS NOT NULL THEN
        RAISE EXCEPTION
            'V81: exigencia inesperada en: %. Este corte tiene UNA sola ALT, tipo_acceso/L.',
            mal_exigidas;
    END IF;

    SELECT count(*) INTO filas_alt
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves) AND t.exigencia = 'ALT';
    IF filas_alt <> 1 THEN
        RAISE EXCEPTION 'V81: se esperaba exactamente 1 fila ALT entre las nuevas y hay %', filas_alt;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                     JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
                    WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_acceso'
                      AND t.tipo_propiedad = 'L' AND t.exigencia = 'ALT' AND t.requerido) THEN
        RAISE EXCEPTION 'V81: tipo_acceso/L no quedo ALT con requerido = true.';
    END IF;

    -- 6.7 Y el catalogo ENTERO sigue sin una sola fila PUB, en las dos tablas.
    --     Es el estado que esta migracion se comprometio a no mover: subir algo
    --     a PUB seria estrenar un bloqueo que ademas NO informa.
    IF EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                 JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
                WHERE c.organizacion_id IS NULL AND t.exigencia = 'PUB')
       OR EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                    JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
                   WHERE c.organizacion_id IS NULL AND o.exigencia = 'PUB') THEN
        RAISE EXCEPTION 'V81: aparecieron filas PUB en el catalogo del sistema y esta migracion no promueve ninguna.';
    END IF;

    -- 6.8 `requerido` sigue siendo espejo exacto de `exigencia` en TODO el
    --     catalogo. Guarda 2.4 de V78, vuelta a correr aqui: es la primera vez
    --     que este proyecto escribe un `requerido = true` desde V72, y es justo
    --     donde el espejo se rompe si se escribe una sola de las dos columnas.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO espejo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE t.requerido <> (t.exigencia = 'ALT');
    IF espejo IS NOT NULL THEN
        RAISE EXCEPTION 'V81: requerido y exigencia divergen en: %', espejo;
    END IF;

    -- 6.9 Ninguna es ESTRUCTURAL.
    SELECT string_agg(c.clave || ' -> ' || c.destino, ', ') INTO mal_destino
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND (c.destino <> 'ATRIBUTO' OR c.campo_estructural IS NOT NULL);
    IF mal_destino IS NOT NULL THEN
        RAISE EXCEPTION 'V81: destino equivocado en: %', mal_destino;
    END IF;

    -- 6.10 EL PAR. Ningun hecho existente llega menos lejos que su condicion.
    --      `nivel_implementacion` nace aqui y esta es su prueba: su condicion
    --      `se_entrega_implementado` se pacta en A, L y O, y el guard 2.2 de V78
    --      IGNORA `tipo_operacion` -- compara solo el conjunto de
    --      `tipo_propiedad` --, asi que el hecho debe cubrir los tres o esta
    --      migracion falla aqui, que es lo correcto. Se corre sobre TODOS los
    --      pares declarados, no solo el nuevo.
    FOR huecos IN
        SELECT p.hecho || ' no llega a ' || string_agg(DISTINCT o.tipo_propiedad, ', ')
               || ' y su condicion ' || p.condicion || ' si'
          FROM (VALUES
                ('amoblado',              'se_ofrece_amoblado'),
                ('cuota_mantenimiento',   'mantenimiento_a_cargo_de'),
                ('estacionamientos',      'estacionamientos_incluidos'),
                ('rubro_permitido',       'rubros_excluidos_por_titular'),
                ('mascotas_reglamento',   'mascotas_aceptadas'),
                ('nivel_implementacion',  'se_entrega_implementado'),
                ('estado_ocupacion',      'entrega_desocupado'),
                ('lote_minimo_normativo', 'acepta_venta_fraccionada')
               ) AS p(hecho, condicion)
          JOIN catalogo_atributo h ON h.clave = p.hecho AND h.activo AND NOT h.aplica_todos
          JOIN catalogo_atributo c ON c.clave = p.condicion AND c.activo
          JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
         WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                            WHERE t.id_catalogo_atributo = h.id_catalogo_atributo
                              AND t.tipo_propiedad = o.tipo_propiedad)
         GROUP BY p.hecho, p.condicion
    LOOP
        RAISE EXCEPTION
            'V81: %. Ahi el pacto seria el unico sitio donde cabe el hecho.', huecos;
    END LOOP;

    -- 6.11 Cero valores materializados. NINGUNO, y menos `tipo_acceso`: sembrarlo
    --      en los 21 locales convertiria una exigencia de campo en una casilla ya
    --      marcada, que es lo contrario de lo que decidio el titular. El dato se
    --      desbloquea visitando, no escribiendo.
    SELECT count(*) INTO con_valor FROM atributo_propiedad WHERE clave = ANY (claves);
    IF con_valor > 0 THEN
        RAISE EXCEPTION
            'V81: se escribieron % valores de claves que acaban de nacer. Ninguna se rellena, y `tipo_acceso` menos que ninguna.',
            con_valor;
    END IF;

    -- 6.12 El contenido de esta migracion, contado.
    SELECT count(*) INTO opciones
      FROM catalogo_atributo_opcion o
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves);
    IF opciones <> 83 THEN
        RAISE EXCEPTION 'V81: se esperaban 83 opciones en los 18 vocabularios y hay %', opciones;
    END IF;

    SELECT count(*) INTO filas_tipo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves);
    IF filas_tipo <> 71 THEN
        RAISE EXCEPTION 'V81: se esperaban 71 filas de aplicabilidad y hay %', filas_tipo;
    END IF;

    -- 6.13 D-BASE-4 pagada: ya no queda ninguna clave con la unidad en `m2`.
    SELECT count(*) INTO unidades_sin_acento
      FROM catalogo_atributo WHERE organizacion_id IS NULL AND unidad = 'm2';
    IF unidades_sin_acento > 0 THEN
        RAISE EXCEPTION 'V81: quedan % claves con unidad "m2" sin acento', unidades_sin_acento;
    END IF;

    RAISE NOTICE 'V81: 39 claves comerciales, % filas de aplicabilidad (1 ALT: tipo_acceso/L), % opciones en 18 vocabularios, 0 en PUB, 0 valores materializados.',
        filas_tipo, opciones;
END $$;
