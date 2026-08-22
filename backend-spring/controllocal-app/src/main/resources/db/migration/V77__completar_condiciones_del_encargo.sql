-- =====================================================================
-- V77 - El lenguaje completo del ENCARGO
--
-- V73 abrio el segundo sujeto y V74 sembro SEIS condiciones para probar que
-- el mecanismo funciona. El catalogo se quedo ahi: de las veintiseis que el
-- documento inventaria, veinte no existian -- y VENTA no tenia NI UNA. Un
-- encargo de venta no podia decir si se entrega desocupado, si el propietario
-- acepta credito hipotecario ni si aceptaria una permuta: tres preguntas que
-- deciden si una operacion avanza o se cae, y ninguna tenia donde vivir.
--
-- Esta migracion es CATALOGO. No toca el mecanismo -- no anade columnas, no
-- cambia triggers, no redefine la aplicabilidad -- porque el mecanismo esta
-- construido y probado. Lo unico que hace es completar el vocabulario y
-- declarar a que (tipo, operacion) aplica cada pieza.
--
-- LA PREGUNTA QUE DECIDIO CADA UNA, y que no se salto ninguna:
--
--     Describe como ES el inmueble, o como se acordo COMERCIALIZARLO en este
--     encargo? Si al abrir otro encargo sobre la misma propiedad el dato
--     puede cambiar sin que la propiedad haya cambiado, es del ENCARGO.
--
-- LAS VEINTISEIS ENTRAN 'OPC', SIN EXCEPCION. Que una garantia sea
-- imprescindible para publicar un alquiler puede ser cierto, pero es una
-- decision del negocio que nadie ha tomado, y tomarla aqui dejaria fichas ya
-- publicadas incompletas de golpe. Subir una a PUB es una linea de SQL el dia
-- que se decida.
--
-- Y NINGUNA LLEVA VALOR POR DEFECTO. La ausencia de una condicion significa
-- "todavia no se sabe", nunca "no". Es lo que permitira a KAIROS preguntar
-- solo lo que falta en vez de heredar supuestos que nadie dijo. Por eso
-- `igv_arrendamiento` NO lleva la opcion `POR_DEFINIR` que el documento
-- proponia: con ella habria dos formas de decir lo mismo -- la ausencia y la
-- opcion -- y esa duplicidad es justo la clase de problema que los cortes
-- anteriores fueron retirando.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Las condiciones de VENTA. Van primero a proposito: es la mitad del
--    modelo que estaba entera sin sembrar, y la que hace que "universal"
--    signifique algo.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                               aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                               valor_minimo, valor_maximo)
VALUES
    -- La gemela comercial de `estado_ocupacion` (hecho, todavia sin sembrar).
    -- Que hoy este ocupado no dice como se entrega: eso se pacta.
    (NULL, 'entrega_desocupado', 'Se entrega desocupado', 'BOOLEANO', NULL,
     false, true, 320, 'ENCARGO', 'condiciones',
     'Si el propietario entrega el inmueble libre de ocupantes al cerrar la venta.',
     NULL, NULL),

    (NULL, 'apto_credito_hipotecario', 'Acepta credito hipotecario', 'BOOLEANO', NULL,
     false, true, 321, 'ENCARGO', 'condiciones',
     'Si el propietario acepta un comprador financiado. Alarga el cierre 45-60 dias, y hay quien solo quiere contado.',
     NULL, NULL),

    (NULL, 'acepta_financiamiento_directo', 'Acepta financiamiento del propietario', 'BOOLEANO', NULL,
     false, true, 322, 'ENCARGO', 'condiciones',
     'Si el propietario financia el saldo. En terrenos de periferia es lo que hace que el aviso funcione.',
     NULL, NULL),

    (NULL, 'acepta_permuta', 'Acepta permuta', 'BOOLEANO', NULL,
     false, true, 323, 'ENCARGO', 'condiciones',
     'Si aceptaria otro inmueble como parte del pago.', NULL, NULL),

    -- La gemela de `lote_minimo_normativo` (hecho, todavia sin sembrar): lo
    -- que la norma permite subdividir no dice si el dueno quiere hacerlo.
    (NULL, 'acepta_venta_fraccionada', 'Acepta vender por partes', 'BOOLEANO', NULL,
     false, true, 324, 'ENCARGO', 'condiciones',
     'Si acepta vender el terreno en lotes en vez de entero.', NULL, NULL),

    (NULL, 'acepta_aporte_a_proyecto', 'Acepta aportar el terreno a un proyecto', 'BOOLEANO', NULL,
     false, true, 325, 'ENCARGO', 'condiciones',
     'Canje de terreno por metros construidos: la salida habitual de un lote bien zonificado.',
     NULL, NULL);

-- ---------------------------------------------------------------------
-- 2. Las condiciones de ALQUILER que faltaban.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo (organizacion_id, clave, rotulo, tipo_dato, unidad,
                               aplica_todos, del_sistema, orden, sujeto, familia, ayuda,
                               valor_minimo, valor_maximo)
VALUES
    -- «S/ 5 000» y «S/ 5 000 mas IGV» son 900 soles al mes de diferencia, y
    -- hoy la ficha no puede distinguirlos.
    (NULL, 'igv_arrendamiento', 'IGV sobre la renta', 'LISTA', NULL,
     false, true, 306, 'ENCARGO', 'condiciones',
     'Si la renta pactada lleva IGV. Sin declararlo, el mismo numero significa dos importes distintos.',
     NULL, NULL),

    -- El error de tres ordenes de magnitud: el agente oye "seis dolares el
    -- metro" y escribe 6 en un campo que el sistema lee como renta mensual.
    (NULL, 'modalidad_precio', 'Modalidad del precio', 'LISTA', NULL,
     false, true, 307, 'ENCARGO', 'condiciones',
     'Si la renta se pacta por el total o por metro cuadrado al mes.', NULL, NULL),

    -- La gemela de `cuota_mantenimiento` (hecho: lo que cobra la junta).
    (NULL, 'mantenimiento_a_cargo_de', 'Mantenimiento a cargo de', 'LISTA', NULL,
     false, true, 308, 'ENCARGO', 'condiciones',
     'Quien paga el mantenimiento en este alquiler. Cuanto cobra la junta es otro dato, y es del inmueble.',
     NULL, NULL),

    -- La gemela de `estacionamientos` (hecho: cuantas tiene el inmueble).
    (NULL, 'estacionamientos_incluidos', 'Cocheras incluidas', 'ENTERO', NULL,
     false, true, 309, 'ENCARGO', 'condiciones',
     'Cuantas cocheras entran en la renta. El inmueble puede tener mas y no incluirlas.', 0, 50),

    (NULL, 'precio_estacionamiento_adicional', 'Precio por cochera adicional', 'IMPORTE', NULL,
     false, true, 310, 'ENCARGO', 'condiciones',
     'Lo que cuesta cada cochera de mas, al mes.', 0, NULL),

    (NULL, 'equipamiento_incluido', 'Equipamiento incluido', 'LISTA_MULTIPLE', NULL,
     false, true, 311, 'ENCARGO', 'condiciones',
     'Que entra con el inmueble. Hoy se negocia por WhatsApp y no queda escrito: ahi nacen las disputas al entregar.',
     NULL, NULL),

    -- Distinta de `uso`, que dice lo que la zonificacion permite: esta dice
    -- lo que el propietario admite.
    (NULL, 'uso_admitido_por_titular', 'Uso admitido por el propietario', 'LISTA', NULL,
     false, true, 312, 'ENCARGO', 'condiciones',
     'Para que acepta el propietario que se use. La zonificacion puede permitir mas.', NULL, NULL),

    -- La gemela de `nivel_implementacion` (hecho, todavia sin sembrar).
    (NULL, 'se_entrega_implementado', 'Se entrega implementado', 'BOOLEANO', NULL,
     false, true, 313, 'ENCARGO', 'condiciones',
     'Si el local se entrega con su implementacion puesta.', NULL, NULL),

    (NULL, 'meses_gracia_implementacion', 'Meses de gracia', 'ENTERO', 'meses',
     false, true, 314, 'ENCARGO', 'condiciones',
     'Meses sin renta para implementar. Sin registrarlo, el historico miente sobre la renta efectiva del primer ano.',
     0, 24),

    (NULL, 'respaldo_exigido', 'Respaldo exigido al inquilino', 'LISTA', NULL,
     false, true, 315, 'ENCARGO', 'condiciones',
     'Que garantia personal o bancaria pide el propietario. Filtra la demanda antes de la visita.',
     NULL, NULL),

    -- La gemela de `rubro_permitido` (hecho: lo que admiten zonificacion y
    -- reglamento). Esta dice lo que el dueno rechaza aunque este permitido.
    (NULL, 'rubros_excluidos_por_titular', 'Rubros que el propietario no acepta', 'TEXTO', NULL,
     false, true, 316, 'ENCARGO', 'condiciones',
     'Lo que el propietario no quiere aunque la zonificacion lo permita.', NULL, NULL),

    (NULL, 'racks_incluidos', 'Se entregan los racks', 'BOOLEANO', NULL,
     false, true, 317, 'ENCARGO', 'condiciones',
     'Si la estanteria industrial entra en el alquiler.', NULL, NULL),

    (NULL, 'arrendamiento_parcial', 'Acepta arrendar por partes', 'BOOLEANO', NULL,
     false, true, 318, 'ENCARGO', 'condiciones',
     'Si acepta dividir el area en vez de arrendar el total.', NULL, NULL),

    (NULL, 'area_minima_arrendable', 'Area minima arrendable', 'DECIMAL', 'm2',
     false, true, 319, 'ENCARGO', 'condiciones',
     'El area mas pequena que acepta arrendar. Solo dice algo si acepta arrendar por partes.',
     1, NULL);

-- ---------------------------------------------------------------------
-- 3. Los vocabularios de las cuatro LISTA.
--
-- Cerrados y con rotulo: el cliente pinta el rotulo y devuelve el valor. Sin
-- opciones sembradas, `controlDe` degrada la clave a TEXTO y la lista deja de
-- serlo -- que es exactamente lo que le pasaba a `servicios_disponibles`.
-- ---------------------------------------------------------------------
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        -- Sin POR_DEFINIR: la ausencia ya dice "no se sabe".
        ('igv_arrendamiento', 'GRAVADO_18', 'Gravado con IGV (18 %)', 1),
        ('igv_arrendamiento', 'NO_GRAVADO', 'No gravado', 2),

        ('modalidad_precio', 'MENSUAL_TOTAL', 'Monto mensual total', 1),
        ('modalidad_precio', 'POR_M2_AL_MES', 'Por metro cuadrado al mes', 2),

        ('mantenimiento_a_cargo_de', 'PROPIETARIO', 'El propietario', 1),
        ('mantenimiento_a_cargo_de', 'INQUILINO', 'El inquilino', 2),

        ('uso_admitido_por_titular', 'VIVIENDA', 'Solo vivienda', 1),
        ('uso_admitido_por_titular', 'VIVIENDA_Y_OFICINA', 'Vivienda y oficina', 2),
        ('uso_admitido_por_titular', 'COMERCIAL', 'Comercial', 3),

        ('respaldo_exigido', 'SOLO_DEPOSITO', 'Solo deposito en garantia', 1),
        ('respaldo_exigido', 'AVAL_PERSONAL', 'Aval personal', 2),
        ('respaldo_exigido', 'AVAL_CON_INMUEBLE', 'Aval con inmueble', 3),
        ('respaldo_exigido', 'CARTA_FIANZA_BANCARIA', 'Carta fianza bancaria', 4)
       ) AS o(clave, valor, rotulo, orden) ON o.clave = c.clave
 WHERE c.organizacion_id IS NULL AND c.sujeto = 'ENCARGO';

-- El equipamiento es multivalor: se marcan varios y cada uno es un hecho.
INSERT INTO catalogo_atributo_opcion (id_catalogo_atributo, valor, rotulo, orden)
SELECT c.id_catalogo_atributo, o.valor, o.rotulo, o.orden
  FROM catalogo_atributo c
  JOIN (VALUES
        ('REFRIGERADORA', 'Refrigeradora', 1),
        ('COCINA', 'Cocina', 2),
        ('LAVADORA', 'Lavadora', 3),
        ('SECADORA', 'Secadora', 4),
        ('AIRE_ACONDICIONADO', 'Aire acondicionado', 5),
        ('MUEBLES_SALA', 'Muebles de sala', 6),
        ('MUEBLES_DORMITORIO', 'Muebles de dormitorio', 7),
        ('CORTINAS', 'Cortinas', 8),
        ('TELEVISOR', 'Televisor', 9)
       ) AS o(valor, rotulo, orden) ON true
 WHERE c.organizacion_id IS NULL AND c.clave = 'equipamiento_incluido';

-- ---------------------------------------------------------------------
-- 4. A que (tipo, operacion) aplica cada una.
--
-- La pregunta NO es "este atributo corresponde a departamentos", sino
-- "corresponde a este tipo cuando existe este tipo de encargo". Por eso hay
-- condiciones que aplican a ALQUILER y no a VENTA, otras al reves, y otras
-- solo a ciertos tipos: la aplicabilidad tiene dos coordenadas y el catalogo
-- las declara las dos. Ninguna combinacion se rellena "por si acaso".
-- ---------------------------------------------------------------------

-- 4.1 VENTA en cualquier tipo: como se entrega y como se paga son preguntas
--     que caben en un terreno igual que en un departamento.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'V', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'),('O'),('D'),('C'),('T'),('A'),('X')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('entrega_desocupado', 'apto_credito_hipotecario', 'acepta_permuta');

-- 4.2 Financiamiento del propietario: terreno, casa y departamento. En un
--     local o una oficina la operacion es de empresa y se financia en banco.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'V', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('T'),('C'),('D')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'acepta_financiamiento_directo';

-- 4.3 Fraccionar y aportar a un proyecto: solo terreno. Un departamento no se
--     vende por partes ni se aporta a un proyecto.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, 'T', 'V', 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('acepta_venta_fraccionada', 'acepta_aporte_a_proyecto');

-- 4.4 IGV de la renta: cualquier tipo, solo ALQUILER. En una venta el impuesto
--     es otro y se pacta distinto; meterlo aqui seria decir una falsedad.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'),('O'),('D'),('C'),('T'),('A'),('X')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'igv_arrendamiento';

-- 4.5 Precio por metro: comercial. Es como se cotiza un local, una oficina y
--     un almacen; una vivienda se cotiza por el total.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'),('O'),('A')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'modalidad_precio';

-- 4.6 Mantenimiento y cocheras incluidas: donde hay areas comunes o cochera.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'),('O'),('L'),('C'),('A')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('mantenimiento_a_cargo_de', 'estacionamientos_incluidos');

-- 4.7 Cochera adicional con precio: donde se alquila de a una.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'),('O'),('L')) AS t(tipo)
 WHERE c.organizacion_id IS NULL AND c.clave = 'precio_estacionamiento_adicional';

-- 4.8 Equipamiento y uso admitido: vivienda.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('D'),('C')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('equipamiento_incluido', 'uso_admitido_por_titular');

-- 4.9 Implementacion, gracia, respaldo y rubros excluidos: comercial.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('L'),('O'),('A')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('se_entrega_implementado', 'meses_gracia_implementacion',
                   'respaldo_exigido', 'rubros_excluidos_por_titular');

-- 4.10 Racks: solo almacen.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, 'A', 'A', 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL AND c.clave = 'racks_incluidos';

-- 4.11 Arrendamiento parcial y area minima: donde hay area que dividir.
INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, t.tipo, 'A', 'OPC'
  FROM catalogo_atributo c
  CROSS JOIN (VALUES ('A'),('L'),('O')) AS t(tipo)
 WHERE c.organizacion_id IS NULL
   AND c.clave IN ('arrendamiento_parcial', 'area_minima_arrendable');

-- ---------------------------------------------------------------------
-- 5. Las guardas.
--
-- Cuentan lo que acaba de entrar y comprueban invariantes, no cifras escritas
-- a mano: V72 aprendio que una cifra literal o aborta una migracion sana o
-- invita a que alguien la "arregle" bajandola.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    sin_aplicabilidad TEXT;
    mal_sujeto        TEXT;
    lista_sin_vocab   TEXT;
    par               RECORD;
    con_defecto       TEXT;
    venta             INT;
BEGIN
    -- 5.1 Ninguna clave del ENCARGO sin decir a que aplica: seria invisible en
    --     todos los guiones y nadie lo notaria hasta echarla en falta.
    SELECT string_agg(c.clave, ', ') INTO sin_aplicabilidad
      FROM catalogo_atributo c
     WHERE c.sujeto = 'ENCARGO' AND c.activo AND NOT c.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo);
    IF sin_aplicabilidad IS NOT NULL THEN
        RAISE EXCEPTION 'Claves del ENCARGO sin aplicabilidad declarada: %', sin_aplicabilidad;
    END IF;

    -- 5.2 Y ninguna con la suya declarada en la tabla del otro sujeto.
    SELECT string_agg(c.clave, ', ') INTO mal_sujeto
      FROM catalogo_atributo c
     WHERE c.sujeto = 'ENCARGO' AND c.activo
       AND EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                    WHERE t.id_catalogo_atributo = c.id_catalogo_atributo);
    IF mal_sujeto IS NOT NULL THEN
        RAISE EXCEPTION 'Claves del ENCARGO con aplicabilidad por tipo: %', mal_sujeto;
    END IF;

    -- 5.3 Una LISTA sin opciones no es una lista: `controlDe` la degrada a
    --     TEXTO y el vocabulario deja de existir sin que nadie avise. Le paso
    --     a `servicios_disponibles` y se descubrio dos cortes despues.
    SELECT string_agg(c.clave, ', ') INTO lista_sin_vocab
      FROM catalogo_atributo c
     WHERE c.sujeto = 'ENCARGO' AND c.activo
       AND c.tipo_dato IN ('LISTA', 'LISTA_MULTIPLE')
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                        WHERE o.id_catalogo_atributo = c.id_catalogo_atributo);
    IF lista_sin_vocab IS NOT NULL THEN
        RAISE EXCEPTION 'Listas del ENCARGO sin vocabulario sembrado: %', lista_sin_vocab;
    END IF;

    -- 5.4 LOS PARES SEMANTICOS, TODOS.
    --
    --     El guard de V74 nombraba un solo par -- `amoblado` -- y eso lo
    --     convertia en una excepcion artesanal. El par es el PATRON: un hecho
    --     del inmueble y la condicion que se pacta sobre el son dos claves, y
    --     tienen que vivir en dos sujetos. Si alguna vez alguien las unifica
    --     "porque es lo mismo", el pacto vuelve a pisar al hecho.
    --
    --     Los pares cuyo lado PROPIEDAD todavia no existe se comprueban igual:
    --     la comprobacion no exige que existan las dos, exige que si existen
    --     NO compartan sujeto. Asi el dia que llegue el hecho, esta guarda ya
    --     esta puesta.
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
        IF EXISTS (
            SELECT 1
              FROM catalogo_atributo h
              JOIN catalogo_atributo c ON c.clave = par.condicion AND c.activo
             WHERE h.clave = par.hecho AND h.activo
               AND h.sujeto = c.sujeto
        ) THEN
            RAISE EXCEPTION
                'El hecho "%" y la condicion "%" acabaron en el mismo sujeto. Un hecho del inmueble sobrevive al encargo; una condicion negociada muere con el.',
                par.hecho, par.condicion;
        END IF;
    END LOOP;

    -- 5.5 Ninguna condicion con valor por defecto. La ausencia significa
    --     "todavia no se sabe", y un defecto la convertiria en una respuesta
    --     que nadie dio.
    SELECT string_agg(column_name, ', ') INTO con_defecto
      FROM information_schema.columns
     WHERE table_name = 'atributo_encargo'
       AND column_name LIKE 'valor%'
       AND column_default IS NOT NULL;
    IF con_defecto IS NOT NULL THEN
        RAISE EXCEPTION 'Columnas de valor con DEFAULT en atributo_encargo: %', con_defecto;
    END IF;

    -- 5.6 Y VENTA deja de estar en cero, que es la brecha que abrio esta
    --     migracion.
    SELECT count(DISTINCT c.id_catalogo_atributo) INTO venta
      FROM catalogo_atributo c
      JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
     WHERE c.sujeto = 'ENCARGO' AND c.del_sistema AND c.activo AND o.tipo_operacion = 'V';
    IF venta < 6 THEN
        RAISE EXCEPTION 'VENTA sigue incompleta: solo % condiciones aplicables.', venta;
    END IF;

    RAISE NOTICE 'V77: % condiciones de encargo (% aplicables a VENTA, % a ALQUILER), % opciones, % filas de aplicabilidad.',
        (SELECT count(*) FROM catalogo_atributo
          WHERE sujeto = 'ENCARGO' AND del_sistema AND activo),
        venta,
        (SELECT count(DISTINCT c.id_catalogo_atributo)
           FROM catalogo_atributo c
           JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
          WHERE c.sujeto = 'ENCARGO' AND c.del_sistema AND c.activo AND o.tipo_operacion = 'A'),
        (SELECT count(*) FROM catalogo_atributo_opcion o
           JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
          WHERE c.sujeto = 'ENCARGO'),
        (SELECT count(*) FROM catalogo_atributo_operacion o
           JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
          WHERE c.del_sistema);
END $$;
