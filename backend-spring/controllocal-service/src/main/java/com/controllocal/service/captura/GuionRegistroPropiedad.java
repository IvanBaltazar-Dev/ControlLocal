package com.controllocal.service.captura;

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
    /** VENTA o ALQUILER. Nunca se infiere. */
    public static final String OPERACION = "operacion";
    /** Precio de venta o renta mensual, segun la operacion. */
    public static final String IMPORTE = "importe";
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
    public static final String PISO_UNIDAD = "pisoUnidad";
    public static final String REFERENCIA = "referenciaInterna";
    public static final String EDIFICIO = "nombreEdificioGaleria";
    public static final String EXCLUSIVIDAD = "exclusividad";

    /** Lo estructural obligatorio, en el orden en que se pregunta. */
    public static final List<String> OBLIGATORIAS = List.of(
            TIPO_PROPIEDAD, OPERACION, IMPORTE, MONEDA, TITULARES, DIRECCION, DISTRITO);

    /** Todo lo estructural que el motor reconoce. Lo demas se busca en el catalogo. */
    public static final List<String> ESTRUCTURALES = List.of(
            TIPO_PROPIEDAD, OPERACION, IMPORTE, MONEDA, TITULARES, DIRECCION, DISTRITO,
            USO, CODIGO, DESCRIPCION, LATITUD, LONGITUD, ZONA, INTERIOR, PISO_UNIDAD,
            REFERENCIA, EDIFICIO, EXCLUSIVIDAD);

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
            INTERIOR, Set.of("L", "O", "D", "A"),
            PISO_UNIDAD, Set.of("L", "O", "D"),
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
                List.of("LOCAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO", "ALMACEN", "OTRO"),
                "Decide que mas hay que preguntar: un terreno no tiene dormitorios.");
        declarar(OPERACION, "Operacion", "LISTA", null, List.of("VENTA", "ALQUILER"),
                "Si la propiedad se ofrece para las dos cosas, se registran dos encargos.");
        declarar(IMPORTE, "Importe", "DECIMAL", "moneda", null,
                "Precio de venta o renta mensual, segun la operacion declarada.");
        declarar(MONEDA, "Moneda", "LISTA", null, List.of("PEN", "USD"), null);
        declarar(TITULARES, "Titulares", "TEXTO", null, null,
                "Ids de propietario separados por comas. Con cuotas: 12:60,13:40 (tienen que sumar 100).");
        declarar(DIRECCION, "Direccion", "TEXTO", null, null, null);
        declarar(DISTRITO, "Distrito", "TEXTO", null, null, null);
        declarar(USO, "Uso", "LISTA", null, List.of("COMERCIAL", "VIVIENDA", "INDUSTRIAL", "MIXTO"),
                "Si no se declara, se deduce del tipo: una casa es vivienda.");
        declarar(CODIGO, "Codigo interno", "TEXTO", null, null, "Si no se declara, se genera PROP-####.");
        declarar(DESCRIPCION, "Descripcion", "TEXTO", null, null, null);
        declarar(LATITUD, "Latitud", "DECIMAL", "grados", null, null);
        declarar(LONGITUD, "Longitud", "DECIMAL", "grados", null, null);
        declarar(ZONA, "Zona o urbanizacion", "TEXTO", null, null, null);
        declarar(INTERIOR, "Interior o unidad", "TEXTO", null, null, null);
        declarar(PISO_UNIDAD, "Piso", "TEXTO", null, null, null);
        declarar(REFERENCIA, "Referencia interna", "TEXTO", null, null, null);
        declarar(EDIFICIO, "Edificio o galeria", "TEXTO", null, null, null);
        declarar(EXCLUSIVIDAD, "Encargo en exclusiva", "BOOLEANO", null, null, null);
    }

    private GuionRegistroPropiedad() {
    }

    /** La pregunta de una clave estructural, o {@code null} si no es de aqui. */
    public static Pregunta pregunta(String clave) {
        Pregunta declarada = PREGUNTAS.get(clave);
        if (declarada == null) {
            return null;
        }
        return new Pregunta(declarada.clave(), declarada.rotulo(), declarada.tipoDato(),
                declarada.unidad(), declarada.opciones(), OBLIGATORIAS.contains(clave),
                declarada.ayuda());
    }

    public static boolean esEstructural(String clave) {
        return PREGUNTAS.containsKey(clave);
    }

    private static void declarar(String clave, String rotulo, String tipoDato, String unidad,
                                 List<String> opciones, String ayuda) {
        PREGUNTAS.put(clave, new Pregunta(clave, rotulo, tipoDato, unidad, opciones, false, ayuda));
    }
}
