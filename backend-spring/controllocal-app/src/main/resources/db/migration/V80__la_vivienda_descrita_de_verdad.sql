-- =====================================================================
-- V80 - Corte 3: la vivienda descrita de verdad
--
-- QUE HUECO CIERRA
-- Hasta hoy un departamento y una casa se describian con el mismo puñado de
-- claves que un terreno: metraje, antiguedad, estacionamientos, ambientes,
-- dormitorios, baños y poco mas. Con eso, dos departamentos que en el mercado
-- son productos distintos -- un flat interior de estreno y un duplex con
-- terraza para remodelar -- salen en la misma ficha y en el mismo resultado de
-- busqueda. El agente lo sabe, el sistema no, y lo que el sistema no sabe no
-- entra en el moat.
--
-- Esta migracion siembra TREINTA claves de vivienda, sujeto PROPIEDAD, tomadas
-- de las secciones 3.3, 3.4, 3.6 y 3.8 de
-- `docs/ai/auditoria-profundidad-inmobiliaria.md`, acotadas a las que aplican a
-- D (departamento) o C (casa). Las que solo aplican a O, L o A esperan al
-- Corte 4; las de terreno, al Corte 5.
--
-- LAS TREINTA SON DE LA PROPIEDAD, NO DEL ENCARGO. Regla de V73: "si al firmar
-- el siguiente encargo el dato puede cambiar sin que la propiedad haya
-- cambiado, es del ENCARGO". Ninguna de estas puede: que la unidad sea un
-- duplex, que el edificio tenga dos ascensores o que el reglamento permita
-- mascotas no depende de con quien se firme. Por eso van todas en
-- `catalogo_atributo_tipo` y ni una en `catalogo_atributo_operacion` -- la
-- guarda 2.5 de V78 rompe la migracion si se cruzan.
--
-- LAS TREINTA ENTRAN 'OPC'. SIN EXCEPCION, Y NO ES UNA REBAJA DE PASO.
-- `PUB` no informa: bloquea. Cuelga de `exigirPublicable`, que LANZA
-- (`PublicacionServiceImpl:186`, HTTP 400 por `ManejadorErroresApi:45`), y no
-- existe ninguna superficie del cable que reporte una PUB de la PROPIEDAD --
-- `PropiedadResponse.atributosQueFaltan` lleva solo ALT. Sembrar PUB aqui
-- dejaria sin poder anunciarse a las 26 propiedades reales de
-- `controllocal_dev`, que es exactamente el dato que acaba de nacer y que
-- todavia nadie ha respondido. Medido el 2026-08-24: el catalogo del sistema
-- tiene CERO filas PUB, tampoco las seis de V79. La columna "nivel" de la
-- auditoria es una propuesta, no un estado; la promocion OPC -> PUB es un corte
-- propio, con su medicion sobre corpus real, y no es este.
--
-- NINGUNA LLEVA VALOR POR DEFECTO. Ni siquiera `vigilancia = NO_TIENE` ni
-- `etapa_entrega = ENTREGA_INMEDIATA`, que son los dos valores frecuentes.
-- "No tiene vigilancia" y "se entrega ya" son afirmaciones verificadas, no el
-- estado inicial de un dato que nadie ha mirado. La ausencia sigue
-- significando "todavia no se sabe", y por eso `NO_TIENE` existe como OPCION:
-- para poder decirlo cuando se ha comprobado.
--
-- Y NADA SE RELLENA HACIA ATRAS. Las 26 propiedades reales se quedan con estas
-- treinta claves FALTANTES. No se deduce `FLAT` de que el tipo sea D ni
-- `niveles_internos = 1` de que sea lo normal: seria una respuesta que nadie
-- dio, escrita con la misma autoridad que las que si se dieron.
--
-- POR QUE `torre_bloque` ESTA AQUI Y NO EN EL CORTE 5. La auditoria la redacta
-- dentro de §3.8 (Terreno y parametros urbanisticos), pero su `aplica_a` es `D`
-- y su justificacion es de vivienda: "el 501 existe en la Torre A y en la B".
-- Esta en esa seccion por arrastre de redaccion, no por pertenencia. Un corte
-- se define por TIPO, no por numero de seccion; llevarla al Corte 5 sembraria
-- una clave de departamentos en la tanda del terreno. Queda anotado en la
-- auditoria.
--
-- LA CLAVE 30 ES MITAD DE UN PAR, Y SU APLICABILIDAD ESTA MEDIDA.
-- `mascotas_reglamento` es el HECHO cuya CONDICION `mascotas_aceptadas` existe
-- desde V74. El guard 2.2 de V78 exige que el hecho no llegue menos lejos que
-- su condicion, y hay espejo en Java (`SujetoDelDatoIntegrationTest`,
-- PARES_DELIBERADOS). Medido el 2026-08-24 contra `controllocal_dev`:
--
--     mascotas_aceptadas  BOOLEANO  sujeto=ENCARGO
--       catalogo_atributo_operacion:  C / A / OPC
--                                     D / A / OPC
--
-- La auditoria §3.6 dice "D"; la medicion dice C y D, y manda la medicion. Si
-- naciera solo en D esta migracion fallaria en su propia guarda, que es lo
-- correcto. Se corrige el documento, nunca el codigo.
--
-- LO QUE ESTA MIGRACION NO HACE, y no por olvido:
--   * No promueve ninguna clave a PUB ni a ALT, ni las nuevas ni las que habia,
--     ni las seis de V79.
--   * No cambia la aplicabilidad de ninguna clave existente.
--   * No estrecha `banos` de DECIMAL a ENTERO -- `tg_catalogo_sistema_inmutable`
--     lo prohibe por diseño y exige clave nueva + migracion de datos + retirada
--     de la vieja, que es un corte propio. Lo que si hace es PUBLICAR su
--     convencion en la `ayuda`, que es la precondicion documental de ese
--     estrechamiento: hay que escribirla antes de aplicarla, no despues.
--     `medios_banos` nace aqui y es exactamente lo que lo habilita.
--   * No toca las otras tres conversiones de tipo (`cuota_mantenimiento` ->
--     IMPORTE, `rubro_permitido` -> LISTA_MULTIPLE, `zonificacion` -> LISTA):
--     misma invariante y cada una con su propio bloqueo de dato.
--   * No siembra `estacionamiento_independizado`, que §3.6 marca provisional y
--     que §5 de la auditoria cierra de verdad con `unidad_relacionada`
--     (Corte 6). Sembrar hoy una clave que ya sabemos que sera sustituida es
--     crear deuda de retirada a cambio de nada.
--   * No siembra `nivel_implementacion` (Corte 4, es el hecho de
--     `se_entrega_implementado`), ni `estado_ocupacion` ni
--     `lote_minimo_normativo` (Corte 5). Los tres son huerfanos declarados y
--     ninguno es vivienda.
--   * No extiende a la PROPIEDAD la guarda global "ninguna LISTA sin
--     vocabulario" de V77. `servicios_disponibles` sigue siendo una LISTA muda
--     y sus reemplazos nacen en el Corte 5; la guarda va con ellos, porque
--     retirarla antes deja un agujero de captura. Lo que si se vigila aqui es
--     el vocabulario de lo que ESTA migracion introduce.
--   * No incluye el tipo X (OTRO) en ninguna de las treinta. X sigue sin
--     auditar (pendientes-brox §2.6).
--   * No añade endpoints, no toca Angular y no estrena `familia`. Las treinta
--     nacen con `familia = NULL` como las 25 de la PROPIEDAD que ya habia: el
--     alta y el editor las derivan del catalogo por `cl-campo-gobernado` y
--     tienen que aparecer SOLAS. Agrupar un formulario que pasa de 25 a 55
--     campos es una decision de presentacion y va con el corte del SPA. Queda
--     registrado como consecuencia de este corte, no como hueco silencioso.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Las treinta claves.
--
-- `orden` continua donde paro el catalogo de la PROPIEDAD -- el maximo medido
-- el 2026-08-24 es 250 (`cargas_gravamenes`, V79) -- y avanza de diez en diez
-- desde 260, en el orden de lectura de la auditoria: primero el estado del
-- activo, luego el edificio, luego el interior de la unidad. Los huecos de diez
-- existen para que un corte posterior pueda intercalar sin renumerar.
--
-- Todas: destino ATRIBUTO, campo_estructural NULL, del_sistema true,
-- organizacion_id NULL, aplica_todos false, familia NULL. Ninguna es identidad
-- del inmueble, asi que ninguna es ESTRUCTURAL: describen situacion, y su
-- aplicabilidad depende del tipo. Es el criterio de D-E4-3.
--
-- El INSERT va en ASCII a proposito, para que se lea en una terminal sin UTF-8;
-- los acentos de `rotulo`, `ayuda` y `unidad` los repone el bloque 2. Es el
-- patron de V68 y V79.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                               aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                               valor_minimo, valor_maximo, longitud_maxima,
                               destino, campo_estructural)
VALUES
    -- 1.1 §3.3 - Estado y condicion del activo -----------------------------

    -- No lo sustituye `antiguedad_anios`, y esa es la razon de que exista:
    -- veinte años remodelado y veinte años sin tocar son hoy la misma fila.
    (NULL, 'estado_conservacion', 'Estado de conservacion', 'LISTA', NULL,
     false, true, 260, 'PROPIEDAD', NULL,
     'En que estado esta hoy el inmueble.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- Sin defecto: no se pone ENTREGA_INMEDIATA por ser lo frecuente.
    (NULL, 'etapa_entrega', 'Etapa de entrega', 'LISTA', NULL,
     false, true, 270, 'PROPIEDAD', NULL,
     'Si se entrega ya, esta en obra o todavia esta en planos.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- 1.2 §3.4 - Edificio y servicios comunes ------------------------------

    -- Minimo 0 y no 1: "el edificio no tiene ascensor" es una respuesta, y es
    -- justo la que decide una visita a un quinto piso.
    (NULL, 'ascensores', 'Ascensores', 'ENTERO', NULL,
     false, true, 280, 'PROPIEDAD', NULL,
     'Cuantos ascensores tiene el edificio.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    -- Multivalor porque una caseta 24 horas y camaras no son alternativas: un
    -- edificio puede tener las dos, y con una LISTA simple habria que elegir.
    (NULL, 'vigilancia', 'Vigilancia y control de acceso', 'LISTA_MULTIPLE', NULL,
     false, true, 290, 'PROPIEDAD', NULL,
     'Que vigilancia y que control de acceso tiene.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'areas_comunes', 'Areas comunes', 'LISTA_MULTIPLE', NULL,
     false, true, 300, 'PROPIEDAD', NULL,
     'Que areas comunes tiene el edificio o el condominio.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- Minimo 1: un piso con cero unidades no existe.
    (NULL, 'unidades_por_piso', 'Unidades por piso', 'ENTERO', NULL,
     false, true, 310, 'PROPIEDAD', NULL,
     'Cuantas unidades hay por piso.',
     1, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'en_condominio', 'En condominio cerrado', 'BOOLEANO', NULL,
     false, true, 320, 'PROPIEDAD', NULL,
     'Si esta dentro de un condominio cerrado.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'restriccion_reglamento_interno', 'Restricciones del reglamento interno', 'TEXTO', NULL,
     false, true, 330, 'PROPIEDAD', NULL,
     'Lo que el reglamento interno prohibe o limita.',
     NULL, NULL, 500, 'ATRIBUTO', NULL),

    (NULL, 'accesibilidad_movilidad_reducida', 'Accesible para movilidad reducida', 'BOOLEANO', NULL,
     false, true, 340, 'PROPIEDAD', NULL,
     'Si se puede llegar a la unidad sin escaleras.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- 1.3 §3.6 - Distribucion interior de la vivienda ----------------------

    (NULL, 'tipologia', 'Tipologia', 'LISTA', NULL,
     false, true, 350, 'PROPIEDAD', NULL,
     'La forma de la unidad.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'niveles_internos', 'Niveles de la unidad', 'ENTERO', NULL,
     false, true, 360, 'PROPIEDAD', NULL,
     'Cuantos niveles tiene la unidad por dentro.',
     1, NULL, NULL, 'ATRIBUTO', NULL),

    -- La mitad util del par con `banos`: sin esta, medio baño o se pierde o se
    -- escribe como 0.5 en un DECIMAL cuya convencion nadie habia publicado.
    (NULL, 'medios_banos', 'Medios banos', 'ENTERO', NULL,
     false, true, 370, 'PROPIEDAD', NULL,
     'Cuantos medios banos hay, es decir banos sin ducha.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'cuarto_servicio', 'Cuartos de servicio', 'ENTERO', NULL,
     false, true, 380, 'PROPIEDAD', NULL,
     'Cuantos cuartos de servicio tiene.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'bano_servicio', 'Bano de servicio', 'BOOLEANO', NULL,
     false, true, 390, 'PROPIEDAD', NULL,
     'Si la unidad tiene bano de servicio.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'tipo_cocina', 'Tipo de cocina', 'LISTA', NULL,
     false, true, 400, 'PROPIEDAD', NULL,
     'Como es la cocina.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'lavanderia', 'Lavanderia', 'LISTA', NULL,
     false, true, 410, 'PROPIEDAD', NULL,
     'Donde esta la lavanderia.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- Existe porque hoy o se cuenta como dormitorio -- falseando un campo ALT y
    -- de matching -- o se pierde.
    (NULL, 'estudio', 'Ambiente de estudio', 'BOOLEANO', NULL,
     false, true, 420, 'PROPIEDAD', NULL,
     'Si hay un ambiente de estudio.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'vista', 'Vista', 'LISTA', NULL,
     false, true, 430, 'PROPIEDAD', NULL,
     'A donde da la unidad.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- `terraza` y `area_terraza` son dos claves a proposito: la presencia se
    -- sabe en la visita y el metraje no. Una `area_terraza` vacia NO significa
    -- que no haya terraza.
    (NULL, 'terraza', 'Tiene terraza', 'BOOLEANO', NULL,
     false, true, 440, 'PROPIEDAD', NULL,
     'Si tiene terraza.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'area_terraza', 'Area de terraza', 'DECIMAL', 'm2',
     false, true, 450, 'PROPIEDAD', NULL,
     'Cuantos metros cuadrados tiene la terraza.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'balcon', 'Tiene balcon', 'BOOLEANO', NULL,
     false, true, 460, 'PROPIEDAD', NULL,
     'Si tiene balcon.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- Jardin y patio no son lo mismo: quien busca jardin no debe visitar
    -- patios. Por eso son dos preguntas y una sola area.
    (NULL, 'jardin', 'Tiene jardin', 'BOOLEANO', NULL,
     false, true, 470, 'PROPIEDAD', NULL,
     'Si tiene jardin.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'patio', 'Tiene patio', 'BOOLEANO', NULL,
     false, true, 480, 'PROPIEDAD', NULL,
     'Si tiene patio.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'area_jardin_patio', 'Area de uso exclusivo', 'DECIMAL', 'm2',
     false, true, 490, 'PROPIEDAD', NULL,
     'Cuantos metros cuadrados de uso exclusivo hay en jardin o patio.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    -- Solo C: la piscina de un edificio no es de la unidad, se marca en
    -- `areas_comunes`.
    (NULL, 'piscina', 'Piscina', 'BOOLEANO', NULL,
     false, true, 500, 'PROPIEDAD', NULL,
     'Si la casa tiene piscina propia.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'depositos', 'Depositos', 'ENTERO', NULL,
     false, true, 510, 'PROPIEDAD', NULL,
     'Cuantos depositos incluye la unidad.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'deposito_area', 'Area de deposito', 'DECIMAL', 'm2',
     false, true, 520, 'PROPIEDAD', NULL,
     'Cuantos metros cuadrados suma el deposito.',
     0, NULL, NULL, 'ATRIBUTO', NULL),

    (NULL, 'tipo_estacionamiento', 'Tipo de estacionamiento', 'LISTA', NULL,
     false, true, 530, 'PROPIEDAD', NULL,
     'Como es el estacionamiento.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL),

    -- §3.8 por redaccion, vivienda por pertenencia. Ver la cabecera.
    (NULL, 'torre_bloque', 'Torre o bloque', 'TEXTO', NULL,
     false, true, 540, 'PROPIEDAD', NULL,
     'En que torre o bloque esta la unidad.',
     NULL, NULL, 40, 'ATRIBUTO', NULL),

    -- El hecho del par cuya condicion es `mascotas_aceptadas`. Ver la cabecera.
    (NULL, 'mascotas_reglamento', 'El reglamento permite mascotas', 'BOOLEANO', NULL,
     false, true, 550, 'PROPIEDAD', NULL,
     'Si el reglamento del edificio o del condominio permite mascotas.',
     NULL, NULL, NULL, 'ATRIBUTO', NULL);

-- ---------------------------------------------------------------------
-- 2. Lo que LEE una persona: rotulo, ayuda y unidad, con acentos.
--
-- Un bloque unico en vez de treinta UPDATE sueltos, por la misma razon por la
-- que el INSERT va en ASCII: que se pueda leer entero de una vez y comparar
-- clave a clave con la tabla del encargo.
--
-- Las ayudas dicen el HECHO y para que sirve, con su cifra si la tiene. Ni
-- metaforas ni lenguaje de manual: es lo que el agente lee mientras habla con
-- el propietario.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo c
   SET rotulo = v.rotulo, ayuda = v.ayuda, unidad = v.unidad
  FROM (VALUES
    ('estado_conservacion', 'Estado de conservación',
     'En qué estado está hoy el inmueble. No lo dice la antigüedad: veinte años remodelado y veinte años sin tocar son dos productos distintos y hoy tienen la misma ficha.',
     NULL::text),
    ('etapa_entrega', 'Etapa de entrega',
     'Si se entrega ya, está en obra o todavía está en planos. Cambia la fecha en que el cliente puede mudarse y quién puede financiarlo.',
     NULL),
    ('ascensores', 'Ascensores',
     'Cuántos ascensores tiene el edificio. Cero es una respuesta válida, y es la que decide una visita a un quinto piso.',
     NULL),
    ('vigilancia', 'Vigilancia y control de acceso',
     'Qué vigilancia y qué control de acceso tiene. Se marcan todas las que haya: una caseta 24 horas y unas cámaras no son lo mismo.',
     NULL),
    ('areas_comunes', 'Áreas comunes',
     'Qué áreas comunes tiene el edificio o el condominio. Se marcan todas las que haya.',
     NULL),
    ('unidades_por_piso', 'Unidades por piso',
     'Cuántas unidades hay por piso. Dos por piso y ocho por piso son dos productos distintos aunque el departamento sea igual.',
     NULL),
    ('en_condominio', 'En condominio cerrado',
     'Si está dentro de un condominio cerrado. Cambia el gasto mensual y las reglas de acceso.',
     NULL),
    ('restriccion_reglamento_interno', 'Restricciones del reglamento interno',
     'Lo que el reglamento interno prohíbe o limita: horarios de mudanza, obras, uso de las áreas comunes. Se escribe lo que dice el reglamento, no lo que se supone.',
     NULL),
    ('accesibilidad_movilidad_reducida', 'Accesible para movilidad reducida',
     'Si se puede llegar a la unidad sin escaleras, por rampa o por ascensor desde la calle.',
     NULL),
    ('tipologia', 'Tipología',
     'La forma de la unidad. Un dúplex y un flat del mismo metraje se buscan y se pagan distinto.',
     NULL),
    ('niveles_internos', 'Niveles de la unidad',
     'Cuántos niveles tiene la unidad por dentro. Uno es lo corriente; dos o tres es un dúplex o un tríplex.',
     NULL),
    ('medios_banos', 'Medios baños',
     'Cuántos medios baños hay, es decir baños sin ducha. Van aparte de «Baños» para que el conteo de baños completos no dependa de una convención.',
     NULL),
    ('cuarto_servicio', 'Cuartos de servicio',
     'Cuántos cuartos de servicio tiene. No se cuentan como dormitorios.',
     NULL),
    ('bano_servicio', 'Baño de servicio',
     'Si la unidad tiene baño de servicio.',
     NULL),
    ('tipo_cocina', 'Tipo de cocina',
     'Cómo es la cocina. Abierta a la sala y cerrada son dos decisiones distintas para el cliente.',
     NULL),
    ('lavanderia', 'Lavandería',
     'Dónde está la lavandería. «No tiene» es una respuesta verificada, no el valor inicial.',
     NULL),
    ('estudio', 'Ambiente de estudio',
     'Si hay un ambiente de estudio. Se pregunta aparte para no contarlo como dormitorio: eso falsea el dato con el que se busca.',
     NULL),
    ('vista', 'Vista',
     'A dónde da la unidad. Dos departamentos iguales del mismo edificio se pagan distinto según la vista.',
     NULL),
    ('terraza', 'Tiene terraza',
     'Si tiene terraza. El metraje va aparte, en «Área de terraza»: la presencia se sabe en la visita y el metraje no siempre.',
     NULL),
    ('area_terraza', 'Área de terraza',
     'Cuántos metros cuadrados tiene la terraza. Vacío no significa que no haya terraza: eso lo dice «Tiene terraza».',
     'm²'),
    ('balcon', 'Tiene balcón',
     'Si tiene balcón. Un balcón no es una terraza, y quien busca terraza no debe visitar balcones.',
     NULL),
    ('jardin', 'Tiene jardín',
     'Si tiene jardín. No es lo mismo que un patio: quien busca jardín no debe visitar patios.',
     NULL),
    ('patio', 'Tiene patio',
     'Si tiene patio. No es lo mismo que un jardín, y por eso se preguntan por separado.',
     NULL),
    ('area_jardin_patio', 'Área de uso exclusivo',
     'Cuántos metros cuadrados de uso exclusivo hay en jardín o patio. Vacío no significa que no los haya.',
     'm²'),
    ('piscina', 'Piscina',
     'Si la casa tiene piscina propia. La piscina del edificio o del condominio se marca en «Áreas comunes».',
     NULL),
    ('depositos', 'Depósitos',
     'Cuántos depósitos incluye la unidad. Cero es una respuesta válida.',
     NULL),
    ('deposito_area', 'Área de depósito',
     'Cuántos metros cuadrados suma el depósito. Vacío no significa que no haya depósito.',
     'm²'),
    ('tipo_estacionamiento', 'Tipo de estacionamiento',
     'Cómo es el estacionamiento. Un doble paralelo obliga a mover un auto para sacar el otro, y eso decide compras.',
     NULL),
    ('torre_bloque', 'Torre o bloque',
     'En qué torre o bloque está la unidad. El 501 existe en la Torre A y en la B, y sin esto son la misma dirección.',
     NULL),
    ('mascotas_reglamento', 'El reglamento permite mascotas',
     'Si el reglamento del edificio o del condominio permite mascotas. Es un hecho del inmueble; que este alquiler concreto las acepte lo dice la condición del encargo.',
     NULL)
  ) AS v(clave, rotulo, ayuda, unidad)
 WHERE c.organizacion_id IS NULL AND c.clave = v.clave;

-- ---------------------------------------------------------------------
-- 3. Los nueve vocabularios.
--
-- `catalogo_atributo_opcion` es la UNICA autoridad de estos valores: no hay
-- enum Java, ni Set escrito a mano, ni CHECK enumerativo paralelo, ni lista en
-- Angular. Añadir una opcion el dia que haga falta tiene que ser una fila, no
-- un despliegue.
--
-- Codigos UPPER_SNAKE en ASCII, `orden` denso desde 1, y los acentos del
-- `rotulo` repuestos al final del bloque -- que es lo que se lee.
-- ---------------------------------------------------------------------

-- 3.1 Estado de conservacion. Seis escalones, del estreno a la demolicion. No
--     se solapan y no hay "otro": si no se sabe, la clave se deja vacia.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('ESTRENO',         'Estreno',          1),
        ('MUY_BUENO',       'Muy bueno',        2),
        ('BUENO',           'Bueno',            3),
        ('REGULAR',         'Regular',          4),
        ('PARA_REMODELAR',  'Para remodelar',   5),
        ('PARA_DEMOLER',    'Para demoler',     6)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'estado_conservacion';

-- 3.2 Etapa de entrega.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('EN_PLANOS',         'En planos',         1),
        ('EN_CONSTRUCCION',   'En construccion',   2),
        ('ENTREGA_INMEDIATA', 'Entrega inmediata', 3)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'etapa_entrega';

-- 3.3 Vigilancia. NO_TIENE es una opcion legitima -- "se preguntó y no hay" --
--     y no un defecto: lo que no existe es una forma de decirlo sin haberlo
--     preguntado, y para eso esta la ausencia de la clave.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('NO_TIENE',          'No tiene',            1),
        ('PORTERO_DIURNO',    'Portero diurno',      2),
        ('CASETA_24H',        'Caseta 24 horas',     3),
        ('CAMARAS_CCTV',      'Camaras CCTV',        4),
        ('CONTROL_DE_ACCESO', 'Control de acceso',   5),
        ('CERCO_PERIMETRICO', 'Cerco perimetrico',   6)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'vigilancia';

-- 3.4 Areas comunes.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('GIMNASIO',          'Gimnasio',                1),
        ('PISCINA',           'Piscina',                 2),
        ('SUM',               'Sala de usos multiples',  3),
        ('PARRILLAS',         'Parrillas',               4),
        ('COWORKING',         'Coworking',               5),
        ('SALA_DE_NINOS',     'Sala de ninos',           6),
        ('AZOTEA',            'Azotea',                  7),
        ('LAVANDERIA_COMUN',  'Lavanderia comun',        8),
        ('JUEGOS_INFANTILES', 'Juegos infantiles',       9),
        ('SALA_DE_CINE',      'Sala de cine',           10)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'areas_comunes';

-- 3.5 Tipologia.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('MONOAMBIENTE', 'Monoambiente', 1),
        ('FLAT',         'Flat',         2),
        ('DUPLEX',       'Duplex',       3),
        ('TRIPLEX',      'Triplex',      4),
        ('PENTHOUSE',    'Penthouse',    5),
        ('LOFT',         'Loft',         6)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'tipologia';

-- 3.6 Tipo de cocina.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('CERRADA',        'Cerrada',           1),
        ('ABIERTA_A_SALA', 'Abierta a la sala', 2),
        ('KITCHENETTE',    'Kitchenette',       3),
        ('BARRA',          'Barra',             4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_cocina';

-- 3.7 Lavanderia. Igual que en vigilancia, NO_TIENE es respuesta y no defecto.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('INDEPENDIENTE',       'Independiente',       1),
        ('EN_COCINA',           'En la cocina',        2),
        ('EN_TERRAZA',          'En la terraza',       3),
        ('COMUN_DEL_EDIFICIO',  'Comun del edificio',  4),
        ('NO_TIENE',            'No tiene',            5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'lavanderia';

-- 3.8 Vista.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('INTERIOR',              'Interior',             1),
        ('EXTERIOR_A_CALLE',      'Exterior, a la calle', 2),
        ('VISTA_A_PARQUE',        'A parque',             3),
        ('VISTA_AL_MAR',          'Al mar',               4),
        ('VISTA_A_AREAS_COMUNES', 'A areas comunes',      5)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'vista';

-- 3.9 Tipo de estacionamiento.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('SIMPLE',          'Simple',          1),
        ('DOBLE_LINEAL',    'Doble lineal',    2),
        ('DOBLE_PARALELO',  'Doble paralelo',  3),
        ('MOTO',            'Moto',            4)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_estacionamiento';

-- Los acentos de los rotulos que se leen en el selector.
UPDATE catalogo_atributo_opcion o
   SET rotulo = v.rotulo
  FROM catalogo_atributo c,
       (VALUES
        ('etapa_entrega',        'EN_CONSTRUCCION',       'En construcción'),
        ('vigilancia',           'CAMARAS_CCTV',          'Cámaras CCTV'),
        ('vigilancia',           'CERCO_PERIMETRICO',     'Cerco perimétrico'),
        ('areas_comunes',        'SUM',                   'Sala de usos múltiples'),
        ('areas_comunes',        'SALA_DE_NINOS',         'Sala de niños'),
        ('areas_comunes',        'LAVANDERIA_COMUN',      'Lavandería común'),
        ('tipologia',            'DUPLEX',                'Dúplex'),
        ('tipologia',            'TRIPLEX',               'Tríplex'),
        ('lavanderia',           'COMUN_DEL_EDIFICIO',    'Común del edificio'),
        ('vista',                'VISTA_A_AREAS_COMUNES', 'A áreas comunes')
       ) AS v(clave, valor, rotulo)
 WHERE c.id_catalogo_atributo = o.id_catalogo_atributo
   AND c.organizacion_id IS NULL
   AND c.clave = v.clave
   AND o.valor = v.valor;

-- ---------------------------------------------------------------------
-- 4. A que tipos aplica cada una. TODAS 'OPC'.
--
-- `requerido` se escribe ademas de `exigencia` porque son columna y espejo
-- desde V72 y hoy son coherentes al 100 %: una fila que escriba solo una de las
-- dos rompe esa coherencia en silencio. Es la leccion de V78, cuya guarda 2.4
-- lo comprueba sobre TODO el catalogo y no solo sobre lo nuevo.
--
-- Van en `catalogo_atributo_tipo` y NUNCA en `catalogo_atributo_operacion`: son
-- del sujeto PROPIEDAD, y la guarda 2.5 de V78 rompe la migracion si una clave
-- declara su aplicabilidad en la tabla del otro sujeto.
-- ---------------------------------------------------------------------

-- LA APLICABILIDAD DE CADA CLAVE ES LA QUE DECLARA LA AUDITORIA en §3.3, §3.4,
-- §3.6 y §3.8, transcrita literalmente y sin ampliarla ni recortarla -- con la
-- unica excepcion de `mascotas_reglamento`, donde manda la medicion (4.7). Los
-- comentarios de abajo explican el agrupamiento; la fuente de la decision es la
-- auditoria, no ellos.

-- 4.1 A,C,D,L,O -- todo lo edificado. El estado de conservacion y la vigilancia
--     se preguntan de cualquier cosa que este construida.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('C'), ('D'), ('L'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('estado_conservacion', 'vigilancia');

-- 4.2 A,D,L,O -- lo mismo menos la casa, que es como lo declara §3.3.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('D'), ('L'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'etapa_entrega';

-- 4.3 D,L,O -- lo que describe el EDIFICIO en el que esta la unidad, y los
--     niveles de la propia unidad dentro de el.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'), ('L'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('ascensores', 'restriccion_reglamento_interno',
                   'accesibilidad_movilidad_reducida', 'niveles_internos');

-- 4.4 C,D,O -- areas comunes y tipo de estacionamiento: donde hay comunidad,
--     sea edificio de departamentos, de oficinas o condominio de casas.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('C'), ('D'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('areas_comunes', 'tipo_estacionamiento');

-- 4.5 D,O -- unidad dentro de un edificio con varias por planta.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'), ('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('unidades_por_piso', 'vista', 'depositos', 'deposito_area');

-- 4.6 A,C -- los tipos que ocupan un lote propio y pueden estar, o no, dentro
--     de un recinto cerrado. Un departamento siempre esta en un edificio, y eso
--     ya lo describen las claves de 4.3 y 4.4.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'), ('C')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'en_condominio';

-- 4.7 C,D -- el interior de la vivienda. Es el nucleo del corte.
--     `mascotas_reglamento` entra por aqui, y su aplicabilidad NO es una
--     eleccion: es la de su condicion `mascotas_aceptadas`, medida en
--     `catalogo_atributo_operacion` (C/A y D/A). La guarda 5.9 lo comprueba
--     contra la tabla, no contra este comentario.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, false, 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('C'), ('D')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('medios_banos', 'cuarto_servicio', 'bano_servicio', 'estudio',
                   'terraza', 'area_terraza', 'jardin', 'patio', 'area_jardin_patio',
                   'mascotas_reglamento');

-- 4.8 D -- la forma de la unidad, la cocina, la lavanderia, el balcon y la
--     torre: preguntas de edificio de viviendas.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'D', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('tipologia', 'tipo_cocina', 'lavanderia', 'balcon', 'torre_bloque');

-- 4.9 C -- la piscina propia. La del edificio se marca en `areas_comunes`.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'C', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL AND c.clave = 'piscina';

-- ---------------------------------------------------------------------
-- 5. La convencion de `banos`, publicada.
--
-- `banos` sigue siendo DECIMAL y este corte NO la estrecha: cambiar el
-- `tipo_dato` de una clave del sistema lo prohibe `tg_catalogo_sistema_inmutable`
-- por diseño, y hacerlo bien exige clave nueva, migracion de datos y retirada de
-- la vieja. Eso es un corte propio.
--
-- Lo que si se puede hacer hoy, y es la precondicion de aquel, es DECIR que
-- significa un 2.5. Hasta ahora la convencion vivia en la cabeza del agente:
-- `banos` tenia la `ayuda` vacia. `proteger_catalogo_del_sistema()` no bloquea
-- el UPDATE de `ayuda` -- solo vigila `clave`, `tipo_dato`, `del_sistema` y
-- `organizacion_id` --, asi que esto entra sin rodear ninguna guarda.
--
-- No se reinterpreta ni se reescribe ningun valor ya escrito.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo
   SET ayuda = 'Cuántos baños tiene. Un baño completo cuenta 1 y un medio baño —sin ducha— cuenta 0.5. El medio baño se registra además, y por separado, en «Medios baños».'
 WHERE organizacion_id IS NULL AND clave = 'banos';

-- ---------------------------------------------------------------------
-- 6. Las guardas.
--
-- Comprueban invariantes del estado resultante, no cifras escritas a mano donde
-- una invariante sirve. Las dos cifras que si son literales -- 30 claves y 49
-- opciones -- lo son porque son el contenido de ESTA migracion y no el tamaño
-- de nada que crezca con el uso.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    claves TEXT[] := ARRAY[
        'estado_conservacion', 'etapa_entrega',
        'ascensores', 'vigilancia', 'areas_comunes', 'unidades_por_piso',
        'en_condominio', 'restriccion_reglamento_interno',
        'accesibilidad_movilidad_reducida',
        'tipologia', 'niveles_internos', 'medios_banos', 'cuarto_servicio',
        'bano_servicio', 'tipo_cocina', 'lavanderia', 'estudio', 'vista',
        'terraza', 'area_terraza', 'balcon', 'jardin', 'patio',
        'area_jardin_patio', 'piscina', 'depositos', 'deposito_area',
        'tipo_estacionamiento', 'torre_bloque', 'mascotas_reglamento'];
    faltan            TEXT;
    sin_aplicabilidad TEXT;
    cruce             TEXT;
    lista_sin_vocab   TEXT;
    no_opc            TEXT;
    mal_destino       TEXT;
    espejo            TEXT;
    con_valor         BIGINT;
    opciones          INT;
    filas_tipo        INT;
    huecos            TEXT;
BEGIN
    -- 6.0 Son treinta, ni 29 ni 31. Un typo en el array haria que todo lo
    --     demas se comprobara sobre un conjunto equivocado y saliera verde.
    IF array_length(claves, 1) <> 30 THEN
        RAISE EXCEPTION 'V80: el array de claves tiene % entradas y el corte son 30', array_length(claves, 1);
    END IF;

    -- 6.1 Las treinta entraron y estan activas. Un INSERT ... SELECT que no
    --     encuentra su clave no inserta nada y la migracion termina "bien".
    SELECT string_agg(k, ', ') INTO faltan
      FROM unnest(claves) AS k
     WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                        WHERE c.organizacion_id IS NULL AND c.clave = k
                          AND c.activo AND c.del_sistema AND c.sujeto = 'PROPIEDAD');
    IF faltan IS NOT NULL THEN
        RAISE EXCEPTION 'V80: estas claves no llegaron al catalogo de la PROPIEDAD: %', faltan;
    END IF;

    -- 6.2 Ninguna sin decir a que tipos aplica: seria invisible en el alta y en
    --     el editor, y nadie lo notaria hasta echarla en falta.
    SELECT string_agg(c.clave, ', ') INTO sin_aplicabilidad
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves) AND NOT c.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = c.id_catalogo_atributo);
    IF sin_aplicabilidad IS NOT NULL THEN
        RAISE EXCEPTION 'V80: claves sin aplicabilidad declarada: %', sin_aplicabilidad;
    END IF;

    -- 6.3 Y ninguna con la suya en la tabla del otro sujeto -- ni las nuevas ni
    --     las que ya habia. Es la guarda 2.5 de V78 sobre el estado resultante.
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
        RAISE EXCEPTION 'V80: claves con la aplicabilidad en la tabla del otro sujeto: %', cruce;
    END IF;

    -- 6.4 Toda LISTA o LISTA_MULTIPLE nueva tiene vocabulario. Sin el,
    --     `MotorDeCaptura.controlDe` la degrada a TEXTO y el trigger acepta
    --     cualquier cadena: la clave nace muda y nadie lo ve.
    --
    --     Acotada a ESTAS claves a proposito: la guarda global de V77 solo mira
    --     `sujeto = 'ENCARGO'`, y extenderla hoy a toda la PROPIEDAD haria
    --     fallar por `servicios_disponibles`, que este corte no toca.
    SELECT string_agg(c.clave, ', ') INTO lista_sin_vocab
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND c.tipo_dato IN ('LISTA', 'LISTA_MULTIPLE')
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo
                          AND o.activo);
    IF lista_sin_vocab IS NOT NULL THEN
        RAISE EXCEPTION 'V80: listas sin vocabulario sembrado: %', lista_sin_vocab;
    END IF;

    -- 6.5 NINGUNA entra bloqueando. Una promocion accidental a PUB dejaria sin
    --     publicar a toda la cartera y el sitio donde se veria seria una suite
    --     E2E, no aqui.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad || '=' || t.exigencia, ', ') INTO no_opc
      FROM catalogo_atributo c
      JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND t.exigencia <> 'OPC';
    IF no_opc IS NOT NULL THEN
        RAISE EXCEPTION
            'V80: estas filas no entraron OPC: %. Las treinta claves de este corte no bloquean nada; promover es otro corte.',
            no_opc;
    END IF;

    -- 6.6 Y el catalogo ENTERO sigue sin una sola fila PUB, en las dos tablas de
    --     aplicabilidad. Es el estado que esta migracion se comprometio a no
    --     mover.
    IF EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                 JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
                WHERE c.organizacion_id IS NULL AND t.exigencia = 'PUB')
       OR EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                    JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
                   WHERE c.organizacion_id IS NULL AND o.exigencia = 'PUB') THEN
        RAISE EXCEPTION 'V80: aparecieron filas PUB en el catalogo del sistema y esta migracion no promueve ninguna.';
    END IF;

    -- 6.7 Ninguna es ESTRUCTURAL. `ck_catalogo_autoridad_completa` ya impide el
    --     estado a medias; esto comprueba que la clasificacion es la decidida.
    SELECT string_agg(c.clave || ' -> ' || c.destino, ', ') INTO mal_destino
      FROM catalogo_atributo c
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves)
       AND (c.destino <> 'ATRIBUTO' OR c.campo_estructural IS NOT NULL);
    IF mal_destino IS NOT NULL THEN
        RAISE EXCEPTION 'V80: destino equivocado en: %', mal_destino;
    END IF;

    -- 6.8 `requerido` sigue siendo espejo exacto de `exigencia` en TODO el
    --     catalogo. Guarda 2.4 de V78, vuelta a correr aqui.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO espejo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE t.requerido <> (t.exigencia = 'ALT');
    IF espejo IS NOT NULL THEN
        RAISE EXCEPTION 'V80: requerido y exigencia divergen en: %', espejo;
    END IF;

    -- 6.9 EL PAR. Ningun hecho existente llega menos lejos que su condicion.
    --     `mascotas_reglamento` nace aqui y esta es su prueba: si hubiera
    --     entrado solo en D -- que es lo que dice el plan -- esta migracion
    --     fallaria, porque la condicion se pacta tambien en C. Se corre sobre
    --     TODOS los pares declarados, no solo el nuevo.
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
            'V80: %. Ahi el pacto seria el unico sitio donde cabe el hecho.', huecos;
    END LOOP;

    -- 6.10 Cero valores materializados. La ausencia significa "todavia no se
    --      sabe", y sembrar un defecto seria una respuesta que nadie dio.
    SELECT count(*) INTO con_valor FROM atributo_propiedad WHERE clave = ANY (claves);
    IF con_valor > 0 THEN
        RAISE EXCEPTION 'V80: se escribieron % valores de claves que acaban de nacer.', con_valor;
    END IF;

    -- 6.11 El contenido de esta migracion, contado.
    SELECT count(*) INTO opciones
      FROM catalogo_atributo_opcion o
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves);
    IF opciones <> 49 THEN
        RAISE EXCEPTION 'V80: se esperaban 49 opciones en los nueve vocabularios y hay %', opciones;
    END IF;

    SELECT count(*) INTO filas_tipo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = ANY (claves);
    IF filas_tipo <> 68 THEN
        RAISE EXCEPTION 'V80: se esperaban 68 filas de aplicabilidad y hay %', filas_tipo;
    END IF;

    -- 6.12 Y la convencion de `banos` quedo publicada, que es lo que habilita
    --      su estrechamiento futuro.
    IF NOT EXISTS (SELECT 1 FROM catalogo_atributo
                    WHERE organizacion_id IS NULL AND clave = 'banos'
                      AND ayuda IS NOT NULL AND ayuda LIKE '%0.5%') THEN
        RAISE EXCEPTION 'V80: la convencion de `banos` no quedo escrita en su ayuda.';
    END IF;

    RAISE NOTICE 'V80: 30 claves de vivienda, % filas de aplicabilidad, % opciones en 9 vocabularios, 0 en PUB, 0 valores materializados.',
        filas_tipo, opciones;
END $$;
