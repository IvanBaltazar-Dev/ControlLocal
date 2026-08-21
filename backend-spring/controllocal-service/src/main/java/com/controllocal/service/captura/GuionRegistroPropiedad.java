package com.controllocal.service.captura;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.captura.MotorDeCaptura.Pregunta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Que hace falta para registrar una propiedad, y en que orden se pregunta.</b>
 *
 * <h2>Dos clases de dato, y solo una esta escrita aqui</h2>
 * <ul>
 *   <li><b>Estructural</b>: el tipo, la operacion, el importe, el titular, la
 *       direccion. Son las columnas del modelo y las mismas para los siete
 *       tipos de propiedad, asi que estan declaradas en esta clase.</li>
 *   <li><b>De catalogo</b>: dormitorios, metraje, zonificacion... Esas
 *       <b>no</b> estan aqui. Salen de {@code catalogo_atributo} segun el tipo,
 *       y por eso anadir "Almacen" no toca este fichero.</li>
 * </ul>
 *
 * <h2>El orden no es estetico</h2>
 * {@link #TIPO_PROPIEDAD} va primero porque <b>decide todo lo demas</b>: hasta
 * saber si es un terreno o un departamento, el catalogo no puede decir si toca
 * preguntar dormitorios o zonificacion. Detras va la operacion, porque decide
 * si el importe que viene despues es un precio de venta o una renta mensual —
 * y si se pregunta el importe antes que la operacion, hay un numero guardado
 * que no significa nada.
 *
 * <p>Despues, lo que identifica: quien es el titular y donde esta. Y al final
 * los atributos del catalogo, que son los que mas varian y los que menos
 * bloquean.
 */
public final class GuionRegistroPropiedad {

    /** LOCAL, OFICINA, DEPARTAMENTO, CASA, TERRENO, ALMACEN, OTRO. */
    public static final String TIPO_PROPIEDAD = "tipoPropiedad";
    /**
     * <b>Que se va a encargar sobre la propiedad</b>: {@code "VENTA"},
     * {@code "ALQUILER"} o {@code "VENTA,ALQUILER"}. Nunca se infiere.
     *
     * <p>Esta en plural porque una propiedad puede ofrecerse para las dos cosas,
     * y entonces se abren <b>dos encargos independientes</b> sobre una sola
     * propiedad. Lo que no existe es una operacion combinada: la lista dice
     * cuantos encargos hay, no inventa un tercer valor
     * ({@link com.controllocal.domain.inmueble.OperacionInmobiliaria#desdeLista}).
     */
    public static final String OPERACIONES = "operaciones";
    /**
     * <b>Base</b> de la clave del importe: precio de venta o renta mensual.
     *
     * <p>Nunca se usa suelta. Cada encargo tiene el suyo, asi que la clave real
     * viaja calificada por la operacion — {@code importe:VENTA},
     * {@code importe:ALQUILER}—, que es lo que permite registrar los dos sin
     * que el segundo pise al primero. Se construye con {@link #para}.
     */
    public static final String IMPORTE = "importe";
    /** Base de la clave de la moneda. Calificada por operacion, como el importe. */
    public static final String MONEDA = "moneda";
    /**
     * Los titulares, como {@code idRol} o {@code idRol:cuota} separados por
     * comas: {@code "12:60,13:40"}. Se identifican por id y no por nombre a
     * proposito — resolver "Ana Torres" es trabajo del cliente, que tiene el
     * buscador de propietarios, y hacerlo aqui obligaria a este motor a
     * desambiguar homonimos sin ver a la persona.
     */
    public static final String TITULARES = "titulares";
    public static final String DIRECCION = "direccion";
    public static final String DISTRITO = "distrito";

    /** Opcionales: mejoran la ficha, no bloquean el alta. */
    public static final String USO = "uso";
    public static final String CODIGO = "codigo";
    public static final String DESCRIPCION = "descripcion";
    public static final String LATITUD = "latitud";
    public static final String LONGITUD = "longitud";
    public static final String ZONA = "zonaUrbanizacion";
    public static final String INTERIOR = "interiorUnidad";
    /**
     * <b>Retirada como pregunta en V67.</b> Nombraba el mismo concepto que la
     * clave de catalogo {@code piso} y escribia la misma columna, asi que el
     * alta universal lo preguntaba dos veces. La autoridad quedo en {@code piso}
     * (ESTRUCTURAL, concepto PISO); la constante se conserva porque
     * {@code Ubicacion} sigue transportando el valor hacia el agregado.
     */
    public static final String PISO_UNIDAD = "pisoUnidad";
    public static final String REFERENCIA = "referenciaInterna";
    public static final String EDIFICIO = "nombreEdificioGaleria";
    public static final String EXCLUSIVIDAD = "exclusividad";

    /**
     * Lo estructural obligatorio que <b>no depende de cuantos encargos</b> haya,
     * en el orden en que se pregunta.
     *
     * <p>El importe y la moneda no estan aqui: son de cada encargo y por tanto
     * hay tantos como operaciones declaradas. La lista completa y ordenada la
     * da {@link #obligatorias(List)}.
     */
    private static final List<String> OBLIGATORIAS_SIN_OPERACION = List.of(
            TIPO_PROPIEDAD, TITULARES, DIRECCION, DISTRITO);

    /** Lo obligatorio de <b>cada</b> encargo. Una copia por operacion declarada. */
    private static final List<String> OBLIGATORIAS_DE_CADA_ENCARGO = List.of(IMPORTE, MONEDA);

    /**
     * <b>Lo estructural obligatorio, en el orden en que se pregunta</b>, ya
     * desplegado sobre las operaciones que se han declarado.
     *
     * <p>El orden no es estetico y se conserva: primero el tipo, que decide que
     * mas hay que preguntar; despues las operaciones, que deciden si el importe
     * que viene detras es un precio de venta o una renta; despues <b>la
     * condicion economica de cada encargo</b>; y al final lo que identifica —de
     * quien es y donde esta—.
     *
     * <p>Con las operaciones todavia sin declarar, la lista llega hasta
     * {@link #OPERACIONES} y para: no se puede preguntar un importe sin saber
     * de que es.
     *
     * <h2>La operacion se PREGUNTA, pero ya no BLOQUEA</h2>
     * Sigue estando en la lista y en este orden —decide si el importe que viene
     * detras es un precio de venta o una renta—, pero salio de
     * {@link #OBLIGATORIAS_SIN_OPERACION} en V75: una propiedad puede
     * registrarse para PROSPECTARLA, y entonces todavia no hay ninguna
     * operacion que declarar. El encargo nace despues, cuando el propietario
     * acepta.
     *
     * <p>Lo que no se afloja: en cuanto se declara una operacion, su importe y
     * su moneda vuelven a ser obligatorios. Cero operaciones es una respuesta;
     * una operacion a medias no.
     */
    public static List<String> obligatorias(List<OperacionInmobiliaria> operaciones) {
        List<String> ordenadas = new java.util.ArrayList<>();
        ordenadas.add(TIPO_PROPIEDAD);
        ordenadas.add(OPERACIONES);
        for (OperacionInmobiliaria operacion : operaciones == null ? List.<OperacionInmobiliaria>of() : operaciones) {
            for (String base : OBLIGATORIAS_DE_CADA_ENCARGO) {
                ordenadas.add(para(base, operacion));
            }
        }
        ordenadas.add(TITULARES);
        ordenadas.add(DIRECCION);
        ordenadas.add(DISTRITO);
        return List.copyOf(ordenadas);
    }

    /** Todo lo estructural que el motor reconoce. Lo demas se busca en el catalogo. */
    public static final List<String> ESTRUCTURALES = List.of(
            TIPO_PROPIEDAD, OPERACIONES, IMPORTE, MONEDA, TITULARES, DIRECCION, DISTRITO,
            USO, CODIGO, DESCRIPCION, LATITUD, LONGITUD, ZONA, INTERIOR,
            REFERENCIA, EDIFICIO, EXCLUSIVIDAD);

    // ------------------------------------------------------------------
    // Claves calificadas por operacion
    //
    // El importe, la moneda y la exclusividad son de un ENCARGO, no de la
    // propiedad. Mientras solo hubo alquiler se podian llamar `importe` a
    // secas; con venta y alquiler vivos a la vez, esa clave tendria dos duenos
    // y el segundo pisaria al primero -- que es exactamente el error que el
    // modelo universal existe para impedir (D-E4-1).
    //
    // Se califican con `:` porque ninguna clave del catalogo lo lleva, asi que
    // no puede colisionar con `dormitorios` ni con nada que alguien anada.
    // ------------------------------------------------------------------

    /** Separa una clave economica de la operacion a la que pertenece. */
    public static final String DE = ":";

    /** {@code importe} + {@code VENTA} -> {@code importe:VENTA}. */
    public static String para(String base, OperacionInmobiliaria operacion) {
        return base + DE + operacion.name();
    }

    /** {@code importe:VENTA} -> {@code importe}. Una clave sin calificar se devuelve igual. */
    public static String claveBase(String clave) {
        int corte = clave.indexOf(DE);
        return corte < 0 ? clave : clave.substring(0, corte);
    }

    /**
     * La operacion de una clave calificada, o {@code null} si no lleva ninguna.
     *
     * <p>Devuelve {@code null} —y no una excepcion— para un sufijo ilegible: a
     * quien tiene que quejarse es al motor, que sabe que operaciones se han
     * declarado y puede decir cual esperaba.
     */
    public static OperacionInmobiliaria operacionDe(String clave) {
        int corte = clave.indexOf(DE);
        if (corte < 0) {
            return null;
        }
        try {
            return OperacionInmobiliaria.desde(clave.substring(corte + DE.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** ¿Es la clave de un dato que pertenece a un encargo y no a la propiedad? */
    public static boolean esDeLaOperacion(String clave) {
        return DE_LA_OPERACION.contains(claveBase(clave));
    }

    // ------------------------------------------------------------------
    // Las TRES familias de datos.
    //
    // Estaban mezcladas, y esa mezcla es la deuda de fondo: la direccion de una
    // propiedad y su carga electrica vivian en la misma lista, asi que la
    // pantalla no tenia forma de saber cual preguntar a un terreno. Separarlas
    // es lo que permite que la interfaz salga del TIPO + OPERACION y no de
    // condiciones escritas a mano.
    // ------------------------------------------------------------------

    /**
     * <b>Comunes a cualquier propiedad.</b> Identidad, ubicacion y titularidad:
     * las tiene un terreno igual que un departamento.
     */
    public static final List<String> COMUNES = List.of(
            TITULARES, DIRECCION, DISTRITO, ZONA, REFERENCIA, LATITUD, LONGITUD,
            CODIGO, DESCRIPCION, USO);

    /**
     * <b>Estructurales que dependen del TIPO fisico.</b> Un terreno no tiene
     * interior, ni piso, ni edificio: preguntarselo no es un campo de mas, es
     * una pregunta sin sentido que hace dudar de si el sistema entiende lo que
     * se esta registrando.
     *
     * <p>El resto de lo que depende del tipo NO esta aqui: sale del catalogo
     * ({@code catalogo_atributo}, V48), que es donde tiene que estar para que
     * anadir un tipo sea una fila y no un despliegue.
     */
    private static final Map<String, Set<String>> ESTRUCTURALES_POR_TIPO = Map.of(
            INTERIOR, Set.of("L", "O", "D"),
            EDIFICIO, Set.of("L", "O", "D"));

    /**
     * <b>Dependen de la OPERACION comercial.</b> El importe existe siempre,
     * pero no significa lo mismo —precio de venta o renta mensual— y por eso su
     * rotulo se calcula, no se escribe.
     */
    public static final List<String> DE_LA_OPERACION = List.of(IMPORTE, MONEDA, EXCLUSIVIDAD);

    /** ¿Esta clave estructural aplica a este tipo de propiedad? */
    public static boolean aplicaAlTipo(String clave, String codigoTipo) {
        Set<String> tipos = ESTRUCTURALES_POR_TIPO.get(clave);
        return tipos == null || tipos.contains(codigoTipo);
    }

    private static final Map<String, Pregunta> PREGUNTAS = new LinkedHashMap<>();

    static {
        declarar(TIPO_PROPIEDAD, "Tipo de propiedad", "LISTA", null,
                valores("LOCAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO", "ALMACEN", "OTRO"),
                "Decide qué más hay que preguntar: un terreno no tiene dormitorios.");
        declarar(OPERACIONES, "Operación", "LISTA_MULTIPLE", null, valores("VENTA", "ALQUILER"),
                "Se puede elegir más de una: la propiedad se registra una vez y se abre un "
                        + "encargo por cada operación, cada uno con su precio y su histórico.");
        // Sin ayuda: el rotulo ya la lleva. Un campo que se titula «Precio de
        // venta» y debajo dice «segun la operacion declarada» repite lo que
        // acaba de decir, y en una conversacion KAIROS lo leeria en voz alta.
        declarar(IMPORTE, "Importe", "DECIMAL", "moneda", null, null);
        declarar(MONEDA, "Moneda", "LISTA", null, valores("PEN", "USD"), null);
        // Tipo de dato propio y no TEXTO: el valor no es una frase, es «quien es
        // el dueno y en que cuota». Declararlo aqui es lo que permite al cliente
        // pintar el buscador de propietarios sin reconocer la clave `titulares`
        // -- que seria conocer el negocio, no representar la pregunta (D-A-1).
        declarar(TITULARES, "Titulares", "TITULARES", null, null,
                "Busca a la persona antes de crearla. Con varios titulares, las cuotas tienen que sumar 100 %.");
        declarar(DIRECCION, "Dirección", "TEXTO", null, null, null);
        declarar(DISTRITO, "Distrito", "TEXTO", null, null, null);
        declarar(USO, "Uso", "LISTA", null, valores("COMERCIAL", "VIVIENDA", "INDUSTRIAL", "MIXTO"),
                "Si no se declara, se deduce del tipo: una casa es vivienda.");
        declarar(CODIGO, "Código interno", "TEXTO", null, null, "Si no se declara, lo genera BROX.");
        declarar(DESCRIPCION, "Descripción", "TEXTO", null, null, null);
        declarar(LATITUD, "Latitud", "DECIMAL", "grados", null, null);
        declarar(LONGITUD, "Longitud", "DECIMAL", "grados", null, null);
        declarar(ZONA, "Zona o urbanización", "TEXTO", null, null, null);
        declarar(INTERIOR, "Interior o unidad", "TEXTO", null, null, null);
        declarar(REFERENCIA, "Referencia interna", "TEXTO", null, null, null);
        declarar(EDIFICIO, "Edificio o galería", "TEXTO", null, null, null);
        declarar(EXCLUSIVIDAD, "Encargo en exclusiva", "BOOLEANO", null, null, null);
    }

    private GuionRegistroPropiedad() {
    }

    /**
     * La pregunta de una clave estructural, o {@code null} si no es de aqui.
     *
     * <p>Admite la clave calificada de un encargo ({@code importe:VENTA}) y
     * entonces devuelve la pregunta <b>ya rotulada para esa operacion</b>:
     * «Precio de venta» o «Renta mensual», nunca un «Importe» generico. 180 000
     * y 2 900 no se distinguen por magnitud —se distinguen por como se llaman—
     * y quien responde tiene que poder verlo sin deducirlo.
     */
    public static Pregunta pregunta(String clave) {
        Pregunta declarada = PREGUNTAS.get(claveBase(clave));
        if (declarada == null) {
            return null;
        }
        OperacionInmobiliaria operacion = operacionDe(clave);
        String rotulo = declarada.rotulo();
        if (operacion != null && IMPORTE.equals(claveBase(clave))) {
            rotulo = mayuscula(operacion.nombreDelImporte());
        }
        return new Pregunta(clave, rotulo, declarada.tipoDato(), declarada.unidad(),
                declarada.opciones(), esObligatoria(clave), declarada.ayuda());
    }

    /**
     * ¿Sin este dato no se puede registrar? Para las claves de encargo la
     * respuesta no depende de la operacion concreta —todo encargo necesita
     * importe y moneda—, asi que se mira la base.
     */
    private static boolean esObligatoria(String clave) {
        String base = claveBase(clave);
        return OBLIGATORIAS_SIN_OPERACION.contains(clave)
                || (operacionDe(clave) != null && OBLIGATORIAS_DE_CADA_ENCARGO.contains(base));
    }

    private static String mayuscula(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    public static boolean esEstructural(String clave) {
        return PREGUNTAS.containsKey(claveBase(clave));
    }

    /**
     * Un vocabulario del GUION, con valor y rotulo iguales.
     *
     * <p>Iguales a proposito y no por descuido: estos codigos ya viajaban asi y
     * el cliente los pinta tal cual. Ponerles rotulo de verdad --"Local
     * comercial" en vez de "LOCAL"-- es cambiar la presentacion, y eso no entra
     * de contrabando en un corte que solo amplia capacidades.
     */
    private static List<MotorDeCaptura.Opcion> valores(String... codigos) {
        return java.util.Arrays.stream(codigos)
                .map(codigo -> new MotorDeCaptura.Opcion(codigo, codigo))
                .toList();
    }

    private static void declarar(String clave, String rotulo, String tipoDato, String unidad,
                                 List<MotorDeCaptura.Opcion> opciones, String ayuda) {
        PREGUNTAS.put(clave, new Pregunta(clave, rotulo, tipoDato, unidad, opciones, false, ayuda));
    }
}
