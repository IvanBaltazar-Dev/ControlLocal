package com.controllocal.asistencia.interpretacion;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.asistencia.brox.Vocabulario;
import com.controllocal.asistencia.conversacion.Accion;
import com.controllocal.asistencia.herramientas.Herramientas;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>El interprete sin modelo de lenguaje.</b>
 *
 * <h2>Por que existe, si el objetivo era conversar</h2>
 * Porque el bloque 4 es el <i>adaptador</i> y su contrato, no el modelo. Con
 * esta clase el recorrido entero —comprender, completar, ejecutar, reintentar—
 * se puede probar contra PostgreSQL real, sin red, sin clave de API y sin que
 * una prueba dependa de que un modelo conteste hoy lo mismo que ayer. El dia
 * que entre el LLM, entra por {@link Interprete} y todo lo que hay a su derecha
 * sigue igual — incluidas estas pruebas, que pasan a ser la linea base contra
 * la que se compara.
 *
 * <h2>De donde sale cada palabra que entiende</h2>
 * De ningun sitio inventado aqui, y eso es la mitad del diseno:
 * <table>
 *   <tr><td>los ocho tipos</td><td>{@link Vocabulario#tipoPropiedad}, del contrato</td></tr>
 *   <tr><td>VENTA / ALQUILER</td><td>{@link Vocabulario#OPERACIONES}, del contrato</td></tr>
 *   <tr><td>los distritos</td><td>{@link ClienteBrox#distritos}, del tenant</td></tr>
 *   <tr><td>dormitorios, metraje…</td><td>{@link ClienteBrox#catalogoDe}, por tipo</td></tr>
 * </table>
 * Lo unico que esta clase anade es <b>coloquial</b>: que "depa" es un
 * departamento y que "S/" son soles. Eso es idioma, no negocio — y por eso
 * anadir un tipo de propiedad sigue sin tocar este fichero.
 *
 * <h2>Las tres reglas que lo hacen seguro</h2>
 * <ol>
 *   <li><b>La operacion no se infiere jamas.</b> Solo si la frase la dice. Sin
 *       "vende" o "alquila" explicitos, {@code operacion} no se emite y el
 *       motor la pregunta.</li>
 *   <li><b>Un numero no es un precio por ser un numero.</b> Se considera
 *       importe solo si viene <i>anclado</i>: con moneda o con escala ("mil",
 *       "millones"). Sin eso, "un depa de 3 dormitorios" registraria una
 *       propiedad de tres soles. Y cada importe necesita el sujeto de su
 *       encargo: no se manda un {@code importe} global.</li>
 *   <li><b>Un numero con unidad se declara, nunca se asigna.</b> Ver
 *       {@link #atributosDe}: la unidad dice <i>que se midio</i>, no <i>que
 *       campo es</i>, asi que «500 m²» o «8 m» van a {@code noEntendido} y se
 *       preguntan. Antes se perdian en silencio.</li>
 * </ol>
 *
 * <h2>Lo que cambio con N28/N29, y por que era necesario</h2>
 * Hasta aqui el tokenizador <b>no podia ver</b> las unidades que mas aparecen en
 * una frase inmobiliaria: {@code NUMERO_Y_PALABRA} exigia dos caracteres —y
 * {@code m} mide uno—, y el superindice de {@code m²} no estaba en su clase de
 * caracteres ni lo descomponia {@link #llano}, que normaliza con <b>NFD</b> y
 * NFD no descompone {@code U+00B2}. Resultado medido el 2026-08-29 y confirmado
 * contra el catalogo vivo: «el lote da 8 m de frente» y «500 m²»
 * <b>se descartaban sin dejar constancia</b>, en los siete tipos. Eso choca de
 * frente con el North Star —un dato que no se sabe interpretar se declara, no se
 * descarta— y con la regla 3, que solo se estaba cumpliendo para {@code grados}
 * por accidente: es la unica unidad que el patron alcanzaba a emitir.
 *
 * <p>Ahora {@link #llano} pliega {@code ²}→{@code 2} y {@code ³}→{@code 3}
 * —explicitamente, porque NFD no lo hace— y el patron admite palabras de un
 * caracter, {@code %} y unidades compuestas como {@code t/m²}. Con eso las
 * unidades del catalogo son visibles y las tres frases de arriba se declaran.
 */
@Component
public class InterpreteDeterminista implements Interprete {

    // ------------------------------------------------------------------
    // Lexico. Es lo unico de esta clase que es "idioma" y no dominio.
    // ------------------------------------------------------------------

    /** Formas coloquiales que el vocabulario canonico no tiene por que conocer. */
    private static final Map<String, String> TIPOS_COLOQUIALES = Map.of(
            "depa", "DEPARTAMENTO",
            "depto", "DEPARTAMENTO",
            "dpto", "DEPARTAMENTO",
            "flat", "DEPARTAMENTO",
            "tienda", "LOCAL",
            "deposito", "ALMACEN",
            "lote", "TERRENO");

    /**
     * Palabras que declaran la operacion. <b>Solo estas.</b> "disponible",
     * "en cartera" o "para el cliente" no dicen si se vende o se alquila, y
     * tratarlas como si lo dijeran es exactamente el fallo que
     * {@code PrecioPropiedad} dejo de poder cometer al perder su defecto.
     */
    private static final Map<String, String> OPERACIONES = Map.of(
            "venta", "VENTA", "vende", "VENTA", "vender", "VENTA", "venderla", "VENTA",
            "alquiler", "ALQUILER", "alquila", "ALQUILER", "alquilar", "ALQUILER",
            "arriendo", "ALQUILER", "renta", "ALQUILER");

    private static final Map<String, String> MONEDAS = Map.ofEntries(
            Map.entry("s/", "PEN"), Map.entry("s/.", "PEN"),
            Map.entry("soles", "PEN"), Map.entry("sol", "PEN"), Map.entry("pen", "PEN"),
            Map.entry("sol peruano", "PEN"), Map.entry("soles peruanos", "PEN"),
            Map.entry("us$", "USD"), Map.entry("usd", "USD"),
            Map.entry("dolares", "USD"), Map.entry("dolar", "USD"), Map.entry("$", "USD"),
            Map.entry("dolar americano", "USD"), Map.entry("dolares americanos", "USD"));

    /**
     * La escala. <b>El millon va primero, y no es cosmetico:</b> la alternancia
     * de una expresion regular prueba las ramas en orden y se queda con la
     * primera que encaje, y {@code mil} encaja dentro de {@code millones}. Con
     * el orden al reves, "1.5 millones" se lee como mil quinientos — que es un
     * precio perfectamente creible y por eso nadie lo notaria mirando.
     */
    private static final String ESCALA = "(millon(?:es)?|mil(?:es)?)";

    /** Un numero con moneda delante: "US$ 180,000", "S/ 2500", "$180 mil". */
    private static final Pattern PRECIO_CON_SIMBOLO = Pattern.compile(
            "(us\\$|s/\\.?|\\$|usd|pen)\\s*([\\d.,]+)\\s*" + ESCALA + "?");

    /** Un numero con moneda o escala detras: "180 mil dolares", "2500 soles", "180 mil". */
    private static final Pattern PRECIO_CON_PALABRA = Pattern.compile(
            "([\\d.,]+)\\s*" + ESCALA + "?\\s*(?:de\\s+)?(dolares|dolar|soles|sol|usd|pen)?");

    /** Una palabra que da sujeto al importe que aparece cerca. */
    private static final Pattern MARCADOR_OPERACION = Pattern.compile(
            "\\b(venta|vende|vender|venderla|alquiler|alquila|alquilar|arriendo|renta)\\b");

    /** La cuota es un importe del inmueble con su propia moneda. */
    private static final Pattern MARCADOR_MANTENIMIENTO = Pattern.compile(
            "\\b(?:cuota\\s+(?:de\\s+)?mantenimiento|mantenimiento|cuota)\\b");

    /**
     * Un numero pegado a una palabra: "3 dormitorios", "120 m2", "8 m", "30 %".
     *
     * <h2>Por que la palabra puede medir UN caracter</h2>
     * Porque {@code m} es una unidad del catalogo —la comparten hasta seis
     * claves en un ALMACEN— y con el minimo en dos el tokenizador no podia
     * emitirla: «el lote da 8 m de frente» no producia ningun par y el dato se
     * perdia sin que nadie lo declarara. El precio de bajarlo es que "3 d"
     * tambien produce un par; no pasa nada, porque {@code d} no es clave, ni
     * rotulo, ni unidad de ningun catalogo, y lo que no es ninguna de las tres
     * cosas se ignora igual que antes.
     *
     * <h2>Por que {@code %} y por que la barra</h2>
     * {@code %} es la unidad de {@code area_libre_minima} en un TERRENO y no
     * empieza por letra, asi que va en la primera posicion de la clase.
     * {@code t/m²} —la capacidad portante del piso de un ALMACEN— es la unica
     * unidad compuesta del catalogo, y la barra solo se admite <b>entre</b>
     * partes: un {@code /} suelto al final convertiria "S/" en una unidad.
     *
     * <p>El superindice no aparece en el patron porque no llega hasta aqui:
     * {@link #llano} ya lo ha plegado a {@code 2}.
     */
    private static final Pattern NUMERO_Y_PALABRA = Pattern.compile(
            "([\\d.,]+)\\s*([a-z%][a-z0-9_%]{0,24}(?:/[a-z0-9_%]{1,24})?)");

    /** Un codigo del sistema: PROP-0001, CAP-00003. */
    private static final Pattern CODIGO = Pattern.compile("\\b((?:prop|cap)-\\d{1,10})\\b");

    /**
     * De donde parte un matching: {@code cliente 47}, {@code captacion CAP-00003},
     * {@code prospeccion 12}.
     *
     * <p>La captacion admite id o codigo porque su ruta del Core admite los dos,
     * y una conversacion nombra el codigo mucho mas a menudo que el id.
     */
    private static final Pattern ORIGEN_DE_MATCHING = Pattern.compile(
            "\\b(demanda|encargo|captura|prospeccion|fisico)\\s+(\\d+)\\b");

    /** Ocho a once digitos seguidos: un DNI (8) o un RUC (11). */
    private static final Pattern DOCUMENTO = Pattern.compile("\\b(\\d{8}|\\d{11})\\b");

    /**
     * La unica dependencia, y es la puerta a BROX.
     *
     * <p>Los distritos y el catalogo son <b>datos del tenant</b>: una copia aqui
     * envejeceria en silencio, reconociendo distritos que ya no existen e
     * ignorando los que se dieron de alta ayer.
     */
    private final ClienteBrox brox;

    public InterpreteDeterminista(ClienteBrox brox) {
        this.brox = brox;
    }

    // ==================================================================

    @Override
    public Lectura leer(String texto, SesionBrox sesion, Traza traza) {
        if (texto == null || texto.isBlank()) {
            return Lectura.nada(Lectura.SIN_TEXTO);
        }
        var comercial=accionComercial(llano(texto));
        if(comercial!=null && (comercial!=Accion.REGISTRAR_INTERACCION || texto.contains("{"))) {
            try {
                var argumentos=ArgumentosComerciales.leer(texto,ClienteBrox.OperacionComercial.valueOf(comercial.name()));
                return new Lectura(comercial,Map.of("datosComerciales",ArgumentosComerciales.serializar(argumentos)),List.of(),null);
            } catch(IllegalArgumentException invalido) {
                return new Lectura(comercial,Map.of(),List.of("argumentos estructurados"),null);
            }
        }
        String llano = llano(texto);
        Accion accion = accionDe(llano);
        if (accion == null) {
            return Lectura.nada(Lectura.SIN_ACCION);
        }

        Map<String, String> datos = new LinkedHashMap<>();
        List<String> noEntendido = new ArrayList<>();

        switch (accion) {
            case REGISTRAR_PROPIEDAD -> propiedad(llano, sesion, traza, datos, noEntendido);
            case CONTINUAR_BORRADOR -> codigoDeBorrador(llano, datos);
            case PREPARAR_CAPTURA -> { }
            case CONSULTAR_PROPIEDAD -> quePropiedad(texto, llano, sesion, traza, datos);
            case CONSULTAR_CLIENTE -> aQuien(texto, llano, datos);
            case REGISTRAR_PROPIETARIO -> propietario(texto, llano, datos, noEntendido);
            case REGISTRAR_INTERACCION -> {
                if(contieneAlguna(llano,"reunion cancelada","reunion reprogramada","reunion no realizada",
                        "llamada pendiente","mensaje preparado","nota preparada"))
                    return Lectura.nada(Lectura.SIN_ACCION);
                try { interaccion(texto, llano, datos, sesion, traza); }
                catch(IllegalArgumentException invalido) { noEntendido.add("argumentos estructurados"); }
            }
            case REGISTRAR_RESULTADO_VISITA -> {
                try { resultadoVisitaDeclarado(texto,llano,datos); }
                catch(IllegalArgumentException invalido) { noEntendido.add("argumentos estructurados"); }
            }
            case BUSCAR_COINCIDENCIAS -> deQueCoincidencias(llano, datos, noEntendido);
            case EXPLICAR_MATCH -> referenciasDeMatching(texto, datos, noEntendido, List.of("cursor", "referenciaDemanda", "referencia"));
            case CONTINUAR_COINCIDENCIAS -> referenciasDeMatching(texto, datos, noEntendido, List.of("cursor"));
            // El foco no lleva datos: preguntar "que resuelvo hoy" no admite
            // parametros, y aceptarlos —un rol, un agente— seria dejar que la
            // frase eligiera de quien es el foco.
            case QUE_RESOLVER_HOY -> { }
            default -> throw new IllegalStateException("Acción no traducida: "+accion);
        }
        return new Lectura(accion, datos, List.copyOf(noEntendido), null);
    }

    /**
     * En una captura activa, una respuesta de datos no necesita repetir
     * «registra». La decisión sigue siendo estricta: primero se intenta la
     * lectura normal; sólo una frase que contiene un tipo, un importe o un
     * par numero+campo o una respuesta escalar consulta el borrador para conocer
     * el tipo ya elegido. USD/PEN y si/no solo se asignan al primer faltante
     * activo que devuelve BROX.
     */
    @Override
    public Lectura leerEnCaptura(String texto, Long idBorrador, SesionBrox sesion,
                                 Traza traza) {
        Lectura lectura = leer(texto, sesion, traza);
        if (idBorrador == null || lectura.hayAccion() || texto == null || texto.isBlank()) {
            return lectura;
        }
        String llano = llano(texto);
        if (!pareceDatoDeCaptura(llano)) {
            return lectura;
        }

        ClienteBrox.EstadoCaptura borrador = brox.captura(sesion, idBorrador,
                traza.invocando(Herramientas.CONSULTAR_CAPTURA.nombre()));
        String tipoDelBorrador = tipoDelBorrador(borrador);
        Map<String, String> datos = new LinkedHashMap<>();
        List<String> noEntendido = new ArrayList<>();
        String escalar = escalarDeCaptura(llano, borrador);
        if (escalar != null) {
            datos.put(faltanteActivo(borrador), escalar);
            return new Lectura(Accion.REGISTRAR_PROPIEDAD, datos, List.of(), null);
        }
        propiedad(llano, sesion, traza, datos, noEntendido, tipoDelBorrador);
        if (datos.isEmpty()) {
            return lectura;
        }
        return new Lectura(Accion.REGISTRAR_PROPIEDAD, datos, List.copyOf(noEntendido), null);
    }

    /** No se consulta un borrador por una frase que no contiene ningún dato. */
    private static boolean pareceDatoDeCaptura(String llano) {
        return tipoDe(llano).isPresent()
                || !importesDe(llano).isEmpty()
                || monedaEscalarDe(llano) != null
                || booleanoEscalarDe(llano) != null
                || NUMERO_Y_PALABRA.matcher(llano).find()
                || contieneAlguna(llano, "direccion", "distrito", "avenida", "calle");
    }

    /**
     * Lee una respuesta corta solo contra el hueco que BROX acaba de preguntar.
     * La palabra por si sola no dice si es la moneda de venta, la de alquiler
     * o la de mantenimiento; tampoco dice que interruptor booleano se quiere
     * marcar. El primer faltante activo es la unica autoridad para asignarla.
     */
    private static String escalarDeCaptura(String llano, ClienteBrox.EstadoCaptura borrador) {
        String clave = faltanteActivo(borrador);
        if (clave == null) {
            return null;
        }
        if (clave.regionMatches(true, 0, Vocabulario.MONEDA + ":", 0,
                (Vocabulario.MONEDA + ":").length())) {
            return monedaEscalarDe(llano);
        }
        ClienteBrox.Pregunta siguiente = borrador.siguiente();
        if (siguiente != null && "BOOLEANO".equalsIgnoreCase(siguiente.tipoDato())) {
            return booleanoEscalarDe(llano);
        }
        return null;
    }

    /** El primer hueco es el que se responde; no se adivina otro de la lista. */
    private static String faltanteActivo(ClienteBrox.EstadoCaptura borrador) {
        if (borrador == null) {
            return null;
        }
        if (borrador.faltante() != null) {
            for (String clave : borrador.faltante()) {
                if (clave != null && !clave.isBlank()) {
                    return clave.trim();
                }
            }
        }
        String clave = borrador.siguiente() == null ? null : borrador.siguiente().clave();
        return clave == null || clave.isBlank() ? null : clave.trim();
    }

    /** Monedas admitidas como respuesta escalar, sin aceptar prosa ambigua. */
    private static String monedaEscalarDe(String llano) {
        if (llano == null) {
            return null;
        }
        return MONEDAS.get(llano.trim());
    }

    /** Booleanos admitidos como respuesta escalar, ya normalizados al cable. */
    private static String booleanoEscalarDe(String llano) {
        if (llano == null) {
            return null;
        }
        return switch (llano.trim()) {
            case "si", "s", "yes", "y", "true", "1", "verdadero" -> "true";
            case "no", "n", "false", "0", "falso" -> "false";
            default -> null;
        };
    }

    private static String tipoDelBorrador(ClienteBrox.EstadoCaptura borrador) {
        if (borrador == null || borrador.conocido() == null) {
            return null;
        }
        Object valor = borrador.conocido().get(Vocabulario.TIPO_PROPIEDAD);
        if (valor == null) {
            return null;
        }
        return Vocabulario.tipoPropiedad(String.valueOf(valor)).orElse(null);
    }

    // ==================================================================
    // Que quiere hacer
    // ==================================================================

    /**
     * Verbo + objeto. Los dos hacen falta: "registra" solo no dice que, y
     * "propiedad" solo no dice si consultarla o crearla. Sin los dos, no hay
     * accion — y no haberla entendido se dice, en vez de elegir la mas comun.
     */
    private static Accion accionComercial(String texto) {
        if(texto.startsWith("preparar material ia"))return Accion.PREPARAR_MATERIAL_IA;
        if(texto.startsWith("solicitar material ia"))return Accion.SOLICITAR_MATERIAL_IA;
        if(texto.startsWith("consultar material ia"))return Accion.CONSULTAR_MATERIAL_IA;
        if(texto.startsWith("consultar estado pieza"))return Accion.CONSULTAR_ESTADO_PIEZA;
        if(texto.startsWith("consultar fotos permitidas para pieza"))return Accion.CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA;
        if(texto.startsWith("consultar plantillas y formatos pieza"))return Accion.CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA;
        if(texto.startsWith("preparar pieza comercial"))return Accion.PREPARAR_PIEZA_COMERCIAL;
        if(texto.startsWith("previsualizar pieza comercial"))return Accion.PREVISUALIZAR_PIEZA_COMERCIAL;
        if(texto.startsWith("regenerar pieza comercial"))return Accion.REGENERAR_PIEZA_COMERCIAL;
        if(texto.startsWith("obtener pieza para difusion"))return Accion.OBTENER_PIEZA_PARA_DIFUSION;
        if(texto.startsWith("evaluar foto local"))return Accion.EVALUAR_FOTO_LOCAL;
        if(texto.startsWith("evaluar publicabilidad"))return Accion.EVALUAR_PUBLICABILIDAD;
        if(texto.startsWith("consultar historia publicacion"))return Accion.CONSULTAR_HISTORIA_PUBLICACION;
        if(texto.startsWith("consultar publicaciones"))return Accion.CONSULTAR_PUBLICACIONES;
        if(texto.startsWith("consultar publicacion"))return Accion.CONSULTAR_PUBLICACION;
        if(texto.startsWith("crear borrador anuncio"))return Accion.CREAR_BORRADOR_ANUNCIO;
        if(texto.startsWith("editar borrador anuncio"))return Accion.EDITAR_BORRADOR_ANUNCIO;
        if(texto.startsWith("publicar anuncio"))return Accion.PUBLICAR_ANUNCIO;
        if(texto.startsWith("actualizar anuncio visible"))return Accion.ACTUALIZAR_ANUNCIO_VISIBLE;
        if(texto.startsWith("pausar anuncio"))return Accion.PAUSAR_ANUNCIO;
        if(texto.startsWith("reanudar anuncio"))return Accion.REANUDAR_ANUNCIO;
        if(texto.startsWith("finalizar difusion anuncio"))return Accion.FINALIZAR_DIFUSION_ANUNCIO;
        if(texto.startsWith("consultar seguimientos prospeccion") || texto.startsWith("consultar seguimientos de prospeccion"))return Accion.CONSULTAR_SEGUIMIENTOS_PROSPECCION;
        if(texto.startsWith("consultar compromisos prospeccion"))return Accion.CONSULTAR_COMPROMISOS_PROSPECCION;
        if(texto.startsWith("asumir compromiso prospeccion"))return Accion.ASUMIR_COMPROMISO_PROSPECCION;
        if(texto.startsWith("acreditar compromiso prospeccion"))return Accion.ACREDITAR_COMPROMISO_PROSPECCION;
        if(texto.startsWith("resolver compromiso prospeccion"))return Accion.RESOLVER_COMPROMISO_PROSPECCION;
        if(texto.startsWith("consultar metas comerciales"))return Accion.CONSULTAR_METAS_COMERCIALES;
        if(texto.startsWith("consultar propuestas metas"))return Accion.CONSULTAR_PROPUESTAS_METAS;
        if(texto.startsWith("consultar propuesta meta"))return Accion.CONSULTAR_PROPUESTA_META;
        if(texto.startsWith("decidir propuesta meta"))return Accion.DECIDIR_PROPUESTA_META;
        if(texto.startsWith("consultar avance metas"))return Accion.CONSULTAR_AVANCE_METAS;
        if(texto.startsWith("consultar pulso equipo"))return Accion.CONSULTAR_PULSO_EQUIPO;
        if(texto.startsWith("definicion de metas"))return Accion.DEFINICION_DE_METAS;
        if(texto.startsWith("establecer meta"))return Accion.ESTABLECER_META;
        if(texto.startsWith("proponer meta"))return Accion.PROPONER_META;
        if(texto.startsWith("consultar mediciones individuales"))return Accion.CONSULTAR_MEDICIONES_INDIVIDUALES;
        if(texto.startsWith("consultar derechos informes"))return Accion.CONSULTAR_DERECHOS_INFORMES;
        if(texto.startsWith("consultar reportes propietario"))return Accion.CONSULTAR_REPORTES_PROPIETARIO;
        if(texto.startsWith("consultar avance reporte"))return Accion.CONSULTAR_AVANCE_REPORTE;
        if(texto.startsWith("consultar antecedente reporte"))return Accion.CONSULTAR_ANTECEDENTE_REPORTE;
        if(texto.startsWith("preparar reporte propietario"))return Accion.PREPARAR_REPORTE_PROPIETARIO;
        if(texto.startsWith("previsualizar reporte propietario"))return Accion.PREVISUALIZAR_REPORTE_PROPIETARIO;
        if(texto.startsWith("obtener reporte propietario"))return Accion.OBTENER_REPORTE_PROPIETARIO;
        if(texto.startsWith("consultar comunicaciones reporte"))return Accion.CONSULTAR_COMUNICACIONES_REPORTE;
        if(texto.startsWith("registrar comunicacion reporte realizada"))return Accion.REGISTRAR_COMUNICACION_REPORTE_REALIZADA;
        if(texto.startsWith("rectificar comunicacion reporte"))return Accion.RECTIFICAR_COMUNICACION_REPORTE;
        if(texto.startsWith("preparar informe comercial"))return Accion.PREPARAR_INFORME_COMERCIAL;
        if(texto.startsWith("consultar programas personales"))return Accion.CONSULTAR_PROGRAMAS_PERSONALES;
        if(texto.startsWith("consultar programa personal"))return Accion.CONSULTAR_PROGRAMA_PERSONAL;
        if(texto.startsWith("consultar ocurrencias programa personal"))return Accion.CONSULTAR_OCURRENCIAS_PROGRAMA_PERSONAL;
        if(texto.startsWith("consultar resultado programa personal"))return Accion.CONSULTAR_RESULTADO_PROGRAMA_PERSONAL;
        if(texto.startsWith("planificar seguimiento prospeccion") || texto.startsWith("planificar seguimiento de prospeccion"))return Accion.PLANIFICAR_SEGUIMIENTO_PROSPECCION;
        if(texto.startsWith("posponer seguimiento prospeccion") || texto.startsWith("posponer seguimiento de prospeccion"))return Accion.POSPONER_SEGUIMIENTO_PROSPECCION;
        if(texto.startsWith("vincular interaccion"))return Accion.VINCULAR_INTERACCION;
        if(texto.startsWith("anotar observacion interaccion"))return Accion.ANOTAR_OBSERVACION_INTERACCION;
        // Prioridad por acto explícito, no por la mención incidental de visita/oferta.
        if(texto.startsWith("presentar expediente"))return Accion.PRESENTAR_EXPEDIENTE_OPERACION;
        if(texto.startsWith("evaluar expediente"))return Accion.EVALUAR_EXPEDIENTE_OPERACION;
        if(texto.startsWith("informar hito contractual"))return Accion.INFORMAR_HITO_CONTRACTUAL;
        if(texto.startsWith("confirmar hito contractual"))return Accion.CONFIRMAR_HITO_CONTRACTUAL;
        if(texto.startsWith("rectificar hito contractual"))return Accion.RECTIFICAR_HITO_CONTRACTUAL;
        if(texto.startsWith("registrar cobro"))return Accion.REGISTRAR_COBRO;
        if(texto.startsWith("asignar reparto comision"))return Accion.ASIGNAR_REPARTO_COMISION;
        if(texto.startsWith("autorizar borrador adenda"))return Accion.AUTORIZAR_BORRADOR_ADENDA;
        if(texto.startsWith("reprogramar gestion cobro"))return Accion.REPROGRAMAR_GESTION_COBRO;
        if(texto.startsWith("concluir gestion cobro"))return Accion.CONCLUIR_GESTION_COBRO;
        String[] frases={"abrir oportunidad","rectificar interaccion","registrar no continuidad","consultar agenda","programar visita","cancelar visita","reprogramar visita","registrar resultado visita","registrar contraoferta","registrar aceptacion oferta","registrar rechazo oferta","registrar retirada oferta","presentar oferta","consultar oportunidad"};
        Accion[] acciones={Accion.ABRIR_OPORTUNIDAD,Accion.RECTIFICAR_INTERACCION,Accion.REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD,Accion.CONSULTAR_AGENDA_VISITAS,Accion.PROGRAMAR_VISITA,Accion.CANCELAR_VISITA,Accion.REPROGRAMAR_VISITA,Accion.REGISTRAR_RESULTADO_VISITA,Accion.REGISTRAR_CONTRAOFERTA,Accion.REGISTRAR_ACEPTACION_OFERTA,Accion.REGISTRAR_RECHAZO_OFERTA,Accion.REGISTRAR_RETIRADA_OFERTA,Accion.PRESENTAR_OFERTA,Accion.CONSULTAR_OPORTUNIDAD};
        for(int i=0;i<frases.length;i++)if(texto.startsWith(frases[i]))return acciones[i];
        if(texto.startsWith("registrar interaccion")||texto.startsWith("registra interaccion"))return Accion.REGISTRAR_INTERACCION;
        return null;
    }
    private static Accion accionDe(String llano) {
        // «Confirmar» es una solicitud de revisión, no una prueba humana. La
        // captura debe venir identificada por el contexto estructurado; no se
        // elige un borrador por una afirmación o por una frase más amplia.
        if (llano.matches("(?:confirmar|confirma)(?: (?:la captura|el borrador|el registro))?[.!]?")) {
            return Accion.PREPARAR_CAPTURA;
        }
        boolean consultar = contieneAlguna(llano, "consulta", "consultar", "busca", "buscar",
                "muestra", "mostrar", "dime", "que sabes", "ver ", "listame", "lista ");
        boolean registrar = contieneAlguna(llano, "registra", "registrar", "da de alta",
                "dar de alta", "anota", "anotar", "apunta", "crear", "crea ", "agrega", "agregar");
        boolean continuar = contieneAlguna(llano, "continua", "continuar", "sigue", "seguir",
                "retoma", "retomar", "termina de", "lo de ayer", "lo que quedo", "a medias",
                "pendiente de ayer");

        boolean deInteraccion = contieneAlguna(llano, "llamada", "llame", "llamé",
                "correo", "email", "whatsapp", "mensaje", "contacto", "contacte", "interaccion",
                "conversacion con", "hable con", "reunion");
        boolean deCliente = contieneAlguna(llano, "cliente", "clientes", "interesado",
                "interesados", "demanda");
        boolean dePropietario = contieneAlguna(llano, "propietario", "propietaria",
                "propietarios", "dueno", "duena", "duenos", "titular", "titulares");
        boolean dePropiedad = contieneAlguna(llano, "propiedad", "propiedades", "inmueble",
                "inmuebles", "local", "oficina", "departamento", "depa", "depto", "dpto", "casa",
                "terreno", "almacen", "deposito", "lote", "tienda", "flat", "estacionamiento");

        boolean deCoincidencias = contieneAlguna(llano, "coincidencia", "coincidencias", "encaja",
                "encajan", "matching", "compatible", "compatibles", "calza", "calzan");
        boolean deExplicacion = contieneAlguna(llano, "por que", "porque", "explica", "explicame",
                "criterios", "detalle de", "en que se basa");
        boolean delFoco = contieneAlguna(llano, "que resuelvo", "que resolver", "que atiendo",
                "que hago hoy", "que tengo hoy", "que tengo pendiente", "mis pendientes",
                "que hay pendiente", "prioridades", "en que me enfoco", "mi bandeja",
                "que urge", "foco de hoy", "que resuelvo hoy");

        // El borrador gana a todo: "continua" solo puede significar una cosa, y
        // la frase que lo dice suele nombrar tambien la propiedad que se estaba
        // registrando ("sigue con el depa de Miraflores").
        if (continuar) {
            return deCoincidencias ? Accion.CONTINUAR_COINCIDENCIAS : Accion.CONTINUAR_BORRADOR;
        }
        // El foco va antes que registrar y consultar porque "que tengo pendiente"
        // no nombra ningun objeto: si esperara a las ramas de abajo, se caeria
        // al null y KAIROS diria que no entendio la pregunta mas frecuente del
        // dia.
        if (delFoco) {
            return Accion.QUE_RESOLVER_HOY;
        }
        // Explicar es una forma de mirar coincidencias, no una accion aparte:
        // hace falta que la frase las nombre. Con "explica" bastando por si
        // sola, "explicame la propiedad PROP-12" pediria criterios de un
        // matching que nadie busco.
        if (deCoincidencias) {
            return deExplicacion ? Accion.EXPLICAR_MATCH : Accion.BUSCAR_COINCIDENCIAS;
        }
        if (registrar) {
            if(Pattern.compile("^(?:anota[r]?|registra[r]?|apunta|agrega[r]?)\\s+(?:(?:la|el|una|un)\\s+)?visita\\b")
                    .matcher(llano).find())return Accion.REGISTRAR_RESULTADO_VISITA;
            // La interaccion primero: "anota la llamada al propietario" es una
            // bitacora, no un alta de persona.
            if (deInteraccion) {
                return Accion.REGISTRAR_INTERACCION;
            }
            if (dePropietario) {
                return Accion.REGISTRAR_PROPIETARIO;
            }
            if (dePropiedad) {
                return Accion.REGISTRAR_PROPIEDAD;
            }
            return null;
        }
        if (consultar) {
            if (deCliente) {
                return Accion.CONSULTAR_CLIENTE;
            }
            if (dePropiedad) {
                return Accion.CONSULTAR_PROPIEDAD;
            }
            return null;
        }
        return null;
    }

    // ==================================================================
    // Registrar una propiedad: lo que la frase diga, y solo eso
    // ==================================================================

    private void propiedad(String llano, SesionBrox sesion, Traza traza, Map<String, String> datos,
                           List<String> noEntendido) {
        propiedad(llano, sesion, traza, datos, noEntendido, null);
    }

    private void propiedad(String llano, SesionBrox sesion, Traza traza, Map<String, String> datos,
                           List<String> noEntendido, String tipoDelBorrador) {
        Optional<String> tipoEnLaFrase = tipoDe(llano);
        tipoEnLaFrase.ifPresent(t -> datos.put(Vocabulario.TIPO_PROPIEDAD, t));
        List<String> operaciones = operacionesDe(llano);
        if (!operaciones.isEmpty()) {
            datos.put(Vocabulario.OPERACIONES_DECLARADAS, String.join(",", operaciones));
        }
        importesDe(llano, operaciones, datos, noEntendido);

        distritoDe(llano, sesion, traza).ifPresent(d -> datos.put(Vocabulario.DISTRITO, d));

        // Los atributos solo se buscan cuando ya se sabe el tipo: es el catalogo
        // de ESE tipo el que dice que claves existen, y sin tipo no hay catalogo
        // que consultar. Lo que no se extraiga aqui lo preguntara el motor.
        String tipo = tipoEnLaFrase.orElse(tipoDelBorrador);
        if (tipo != null) {
            atributosDe(llano, sesion, traza, tipo, datos, noEntendido);
        }
    }

    /** El primer tipo que aparezca, canonico o coloquial. */
    private static Optional<String> tipoDe(String llano) {
        for (String palabra : llano.split("[^a-z0-9_]+")) {
            String coloquial = TIPOS_COLOQUIALES.get(palabra);
            if (coloquial != null) {
                return Optional.of(coloquial);
            }
            Optional<String> canonico = Vocabulario.tipoPropiedad(palabra);
            // Una palabra de una letra no declara un tipo: "a" no es ALMACEN.
            if (canonico.isPresent() && palabra.length() > 1) {
                return canonico;
            }
        }
        return Optional.empty();
    }

    /** Las operaciones, <b>solo si la frase las dice</b>, sin elegir una por defecto. */
    private static List<String> operacionesDe(String llano) {
        List<String> operaciones = new ArrayList<>();
        Matcher marcadores = MARCADOR_OPERACION.matcher(llano);
        while (marcadores.find()) {
            String operacion = OPERACIONES.get(marcadores.group(1));
            if (operacion != null && !operaciones.contains(operacion)) {
                operaciones.add(operacion);
            }
        }
        return List.copyOf(operaciones);
    }

    private record ImporteDetectado(BigDecimal importe, String moneda, int inicio, int fin) {
    }

    private record MarcadorSujeto(String sujeto, int inicio, int fin) {
    }

    /**
     * Extrae todos los importes anclados y conserva el sujeto de cada uno.
     * Cada encargo tiene una clave propia: el segundo importe nunca pisa al
     * primero, y una frase sin sujeto no se reparte entre operaciones por
     * magnitud.
     */
    private static void importesDe(String llano, List<String> operaciones,
                                   Map<String, String> datos, List<String> noEntendido) {
        List<ImporteDetectado> importes = importesDe(llano);
        if (importes.isEmpty()) {
            return;
        }
        List<MarcadorSujeto> sujetos = sujetosDe(llano);

        for (ImporteDetectado importe : importes) {
            MarcadorSujeto sujeto = sujetoMasCercano(importe, sujetos);
            if (sujeto != null && "MANTENIMIENTO".equals(sujeto.sujeto())) {
                // La cuota no pertenece a VENTA ni a ALQUILER. Es un IMPORTE
                // del inmueble y lleva una compañera propia en el cable.
                String clave = Vocabulario.CUOTA_MANTENIMIENTO;
                if (datos.containsKey(clave)) {
                    noEntendido.add("otro importe para " + clave);
                    continue;
                }
                datos.put(clave, importe.importe().toPlainString());
                if (importe.moneda() != null) {
                    datos.put(Vocabulario.MONEDA + ":" + clave, importe.moneda());
                }
                continue;
            }

            if (operaciones.isEmpty()) {
                // Sin sujeto no hay una clave valida: una propiedad puede
                // declararse sin encargo, pero su importe no pertenece a la
                // propiedad. Dejarlo como `importe` global permitiria que el
                // siguiente turno lo archivara en el encargo equivocado.
                noEntendido.add("importe sin operacion");
                continue;
            }

            String operacion = sujeto == null || "MANTENIMIENTO".equals(sujeto.sujeto())
                    ? null : sujeto.sujeto();
            if (operacion == null) {
                noEntendido.add("importe sin operacion");
                continue;
            }
            String claveImporte = Vocabulario.IMPORTE + ":" + operacion;
            if (datos.containsKey(claveImporte)) {
                noEntendido.add("otro importe para " + operacion);
                continue;
            }
            datos.put(claveImporte, importe.importe().toPlainString());
            if (importe.moneda() != null) {
                datos.put(Vocabulario.MONEDA + ":" + operacion, importe.moneda());
            }
        }
    }

    /** Todos los importes anclados, ordenados como aparecen en la frase. */
    private static List<ImporteDetectado> importesDe(String llano) {
        List<ImporteDetectado> conSimbolo = new ArrayList<>();
        Matcher simbolo = PRECIO_CON_SIMBOLO.matcher(llano);
        while (simbolo.find()) {
            BigDecimal importe = numero(simbolo.group(2));
            if (importe != null) {
                conSimbolo.add(new ImporteDetectado(escalar(importe, simbolo.group(3)),
                        MONEDAS.get(simbolo.group(1).replace("s/.", "s/")), simbolo.start(),
                        simbolo.end()));
            }
        }

        List<ImporteDetectado> todos = new ArrayList<>(conSimbolo);
        Matcher palabra = PRECIO_CON_PALABRA.matcher(llano);
        while (palabra.find()) {
            String escala = palabra.group(2);
            String moneda = palabra.group(3);
            if (escala == null && moneda == null) {
                continue; // un numero pelado no es un precio
            }
            BigDecimal importe = numero(palabra.group(1));
            if (importe == null || seSolapa(palabra.start(), palabra.end(), todos)) {
                continue;
            }
            todos.add(new ImporteDetectado(escalar(importe, escala),
                    moneda == null ? null : MONEDAS.get(moneda), palabra.start(), palabra.end()));
        }
        todos.sort((a, b) -> Integer.compare(a.inicio(), b.inicio()));
        return List.copyOf(todos);
    }

    private static boolean seSolapa(int inicio, int fin, List<ImporteDetectado> importes) {
        return importes.stream().anyMatch(otro -> inicio < otro.fin() && fin > otro.inicio());
    }

    private static List<MarcadorSujeto> sujetosDe(String llano) {
        List<MarcadorSujeto> sujetos = new ArrayList<>();
        Matcher operaciones = MARCADOR_OPERACION.matcher(llano);
        while (operaciones.find()) {
            String sujeto = OPERACIONES.get(operaciones.group(1));
            if (sujeto != null) {
                sujetos.add(new MarcadorSujeto(sujeto, operaciones.start(), operaciones.end()));
            }
        }
        Matcher mantenimiento = MARCADOR_MANTENIMIENTO.matcher(llano);
        while (mantenimiento.find()) {
            sujetos.add(new MarcadorSujeto("MANTENIMIENTO", mantenimiento.start(),
                    mantenimiento.end()));
        }
        return sujetos;
    }

    private static MarcadorSujeto sujetoMasCercano(ImporteDetectado importe,
                                                    List<MarcadorSujeto> sujetos) {
        MarcadorSujeto cercano = null;
        int distanciaMinima = Integer.MAX_VALUE;
        for (MarcadorSujeto sujeto : sujetos) {
            int distancia = importe.inicio() >= sujeto.fin()
                    ? importe.inicio() - sujeto.fin()
                    : sujeto.inicio() >= importe.fin()
                    ? sujeto.inicio() - importe.fin() : 0;
            if (distancia < distanciaMinima) {
                cercano = sujeto;
                distanciaMinima = distancia;
            }
        }
        return cercano;
    }

    /**
     * "mil" multiplica por mil y "millones" por un millon.
     *
     * <p>El millon se comprueba <b>primero</b> y no al reves: {@code "millones"}
     * empieza por {@code "mil"}, asi que preguntar antes por los miles convierte
     * "1.5 millones" en mil quinientos. Lo encontro su prueba, no una revision.
     */
    private static BigDecimal escalar(BigDecimal importe, String escala) {
        if (escala == null) {
            return importe.stripTrailingZeros();
        }
        // El millon se comprueba primero por la misma razon que en ESCALA:
        // "millones" empieza por "mil".
        //
        // Y se quitan los ceros de la derecha porque "1.5 millones" da
        // 1500000.0, que es el mismo numero escrito de una forma que ningun
        // agente reconoceria en la pantalla de confirmacion.
        return (escala.startsWith("millon")
                ? importe.multiply(BigDecimal.valueOf(1_000_000))
                : importe.multiply(BigDecimal.valueOf(1_000))).stripTrailingZeros();
    }

    /**
     * {@code 180,000} y {@code 180.000} son ciento ochenta mil; {@code 180.5}
     * es ciento ochenta y medio. Se distingue por la forma —grupos de tres— y
     * no por el separador, porque el separador de miles se escribe de las dos
     * maneras en el mismo pais.
     */
    static BigDecimal numero(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String limpio = bruto.trim();
        try {
            if (limpio.matches("\\d{1,3}([.,]\\d{3})+")) {
                return new BigDecimal(limpio.replaceAll("[.,]", ""));
            }
            return new BigDecimal(limpio.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * El distrito sale de la tabla del tenant, no de una lista escrita aqui.
     * Uno que no este dado de alta no se emite: el alta lo rechazaria despues,
     * y es mejor preguntarlo que fallar al final.
     */
    private Optional<String> distritoDe(String llano, SesionBrox sesion, Traza traza) {
        Traza deLosDistritos = traza.invocando(Herramientas.CATALOGO_DE_CAPTURA.nombre());
        return brox.distritos(sesion, deLosDistritos).stream()
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .filter(nombre -> contienePalabra(llano, llano(nombre)))
                // El nombre mas largo gana: "San Isidro" antes que "San Juan"
                // si la frase dice los dos, y "Santiago de Surco" antes que
                // "Surco" siempre.
                .max((a, b) -> Integer.compare(a.length(), b.length()));
    }

    /**
     * Los atributos que la frase declare, contra el catalogo del tipo.
     *
     * <h2>Se reconoce por la clave o por el rotulo. NUNCA por la unidad</h2>
     * {@code «120 dormitorios»} escribe {@code dormitorios} porque la frase
     * <b>nombra el campo</b>. {@code «120 m²»} no escribe nada, y no es una
     * carencia: <b>la unidad dice que se midio, no que campo es</b>. Un
     * departamento tiene cinco campos en metros cuadrados y una casa seis;
     * elegir uno seria decidir por el usuario cual midio, y el error no lo
     * cazaria ningun CHECK porque los seis admiten el mismo numero.
     *
     * <h2>Y tampoco cuando la unidad la usa un solo campo</h2>
     * Esta es la decision que N28 dejo abierta, y va hacia el lado estricto.
     * {@code años} la usa <b>solo</b> {@code antiguedad_anios} en los ocho
     * tipos, asi que la tentacion es asignarla; pero <i>«lo compre hace 8
     * años»</i> escribiria una antiguedad que la frase no dice. Una unidad no
     * identifica un campo ni siendo unica: identifica una <b>magnitud</b>.
     * Asi que el criterio es uno solo y no tiene excepciones —<b>un numero con
     * unidad del catalogo se declara y se pregunta</b>—, que ademas es el unico
     * que se puede sostener sin mirar cuantas claves comparten la unidad hoy.
     *
     * <h2>Lo que cambia respecto de antes</h2>
     * Antes solo se declaraba cuando la unidad estaba compartida por mas de una
     * clave, y en la practica <b>solo ocurria con {@code grados}</b>: era la
     * unica unidad que el tokenizador alcanzaba a emitir. Todo lo demas —«500
     * m²», «8 m», «30 %», «5 kW»— <b>se perdia en silencio</b>. Medido en el
     * snapshot del 2026-09-05, anterior a {@code ESTACIONAMIENTO}, sobre
     * {@code catalogoDe} (comunes + del tipo) contra el catalogo vivo de los
     * siete tipos que entonces existian:
     * <pre>
     *   m²      12 claves en la union (7 en ALMACEN, 6 en CASA, 1 en OTRO)
     *   m        8 claves en la union (6 en ALMACEN, 0 en DEPARTAMENTO)
     *   grados   2 claves, en los SIETE: latitud y longitud
     *   años     1 clave,  en los SIETE: antiguedad_anios
     *   %        1 clave,  solo TERRENO: area_libre_minima
     *   kW       1 clave:  carga_electrica_kw
     *   t/m²     1 clave:  capacidad_portante_piso
     * </pre>
     * Con {@code m²} en un solo campo en OTRO y en doce en la union, una regla
     * que dependiera del recuento habria hecho que la <b>misma frase</b> se
     * escribiera en un tipo y se preguntara en otro.
     *
     * <h2>De donde se mide, que es lo que hizo falso el analisis anterior</h2>
     * De {@link com.controllocal.asistencia.brox.ClienteBrox#catalogoDe}, que devuelve
     * {@code comunes + delTipo}. <b>{@code comunes} no sale de la tabla</b>
     * {@code catalogo_atributo}: sale de {@code GuionRegistroPropiedad.COMUNES},
     * Java escrito a mano, y es quien aporta {@code latitud} y {@code longitud}
     * con {@code unidad = "grados"}. Toda afirmacion sobre lo que KAIROS ve se
     * mide desde aqui; ninguna consulta SQL habria visto esas dos.
     */
    private void atributosDe(String llano, SesionBrox sesion, Traza traza, String tipoPropiedad,
                             Map<String, String> datos, List<String> noEntendido) {
        Map<String, String> porPalabra = new LinkedHashMap<>();
        Map<String, Integer> unidades = new LinkedHashMap<>();
        Traza deLaDefinicion = traza.invocando(Herramientas.CATALOGO_DE_CAPTURA.nombre());
        List<ClienteBrox.Pregunta> catalogo = brox.catalogoDe(sesion, tipoPropiedad,
                deLaDefinicion);
        for (ClienteBrox.Pregunta definicion : catalogo) {
            porPalabra.put(llano(definicion.clave()), definicion.clave());
            if (definicion.rotulo() != null) {
                porPalabra.put(llano(definicion.rotulo()), definicion.clave());
            }
            if (definicion.unidad() != null && !definicion.unidad().isBlank()) {
                unidades.merge(llano(definicion.unidad()), 1, Integer::sum);
            }
        }

        Matcher pares = NUMERO_Y_PALABRA.matcher(llano);
        while (pares.find()) {
            String valor = pares.group(1);
            String palabra = pares.group(2);
            String clave = porPalabra.get(palabra);
            if (clave != null) {
                BigDecimal cifra = numero(valor);
                if (cifra != null) {
                    datos.put(clave, cifra.toPlainString());
                }
                continue;
            }
            if (unidades.containsKey(palabra)) {
                // Es una unidad del catalogo de ESTE tipo: hay una medida y no
                // se sabe de que campo. Se declara para poder preguntarla.
                //
                // Lo que NO se declara es una palabra que no es ni clave, ni
                // rotulo, ni unidad —"180 mil", "3 d"—: eso no es una medida
                // perdida, es que la frase decia otra cosa, y declararlo todo
                // convertiria noEntendido en ruido.
                noEntendido.add(pares.group());
            }
        }
    }

    // ==================================================================
    // Las otras cuatro acciones
    // ==================================================================

    /** El {@code CAP-00003} que se quiere retomar, si la frase lo nombra. */
    private static void codigoDeBorrador(String llano, Map<String, String> datos) {
        Matcher codigo = CODIGO.matcher(llano);
        if (codigo.find()) {
            datos.put("codigo", codigo.group(1).toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Por que texto buscar la propiedad. Sale una sola clave —{@code texto}—
     * porque la busqueda de cartera que ya existe recibe eso: un texto que
     * cruza codigo, direccion, distrito, rubro y nombre del propietario. El
     * adaptador no elige el campo, y por eso no puede equivocarse de campo.
     */
    private void quePropiedad(String original, String llano, SesionBrox sesion, Traza traza,
                              Map<String, String> datos) {
        Matcher codigo = CODIGO.matcher(llano);
        if (codigo.find()) {
            datos.put("texto", codigo.group(1).toUpperCase(Locale.ROOT));
            return;
        }
        Optional<String> distrito = distritoDe(llano, sesion, traza);
        if (distrito.isPresent()) {
            datos.put("texto", distrito.get());
            return;
        }
        nombreDe(original, llano).ifPresent(nombre -> datos.put("texto", nombre));
    }

    // ==================================================================
    // Matching: de que se parte, y cual de las coincidencias
    // ==================================================================

    /**
     * De donde parte el matching. <b>Sin origen no se busca.</b>
     *
     * <p>«las coincidencias» a secas no es una consulta: son las de alguien.
     * Elegir por la persona —«las del ultimo cliente que miraste»— seria
     * inventar un criterio que nadie declaro, asi que se declara la falta y el
     * adaptador la pregunta.
     */
    private static void deQueCoincidencias(String llano, Map<String, String> datos,
                                           List<String> noEntendido) {
        if (origenDeMatching(llano, datos) == null) {
            noEntendido.add("origen y versión de demanda, encargo, captura, prospección o inmueble físico");
        }
        for(String clave:List.of("versionOrigen","tipoVersion","modalidad","capas","categoria","tamano","modoExploracion","monedaExploracion")) {
            Matcher m=Pattern.compile("(?i:\\b"+clave+"\\s+)"+"([A-Za-z0-9_,]+)").matcher(llano);
            if(m.find())datos.put(clave,clave.equals("versionOrigen")||clave.equals("tamano")?m.group(1):m.group(1).toUpperCase(Locale.ROOT));
        }
        Matcher limite=Pattern.compile("(?i:\\blimiteExploracion\\s+)([^\\s]+)").matcher(llano);
        if(limite.find()) {
            BigDecimal valor=numero(limite.group(1));
            datos.put("limiteExploracion",valor==null?limite.group(1):valor.toPlainString());
        }
    }

    /** Referencias explícitas opacas, conservando mayúsculas; nunca un código físico como candidato. */
    private static void referenciasDeMatching(String texto,Map<String,String> datos,List<String> noEntendido,List<String> requeridas) {
        for(String clave:List.of("cursor","referenciaDemanda","referencia")) {
            Matcher m=Pattern.compile("(?i:\\b"+clave+"\\s+)"+"([A-Za-z0-9_-]+)").matcher(texto);
            if(m.find())datos.put(clave,m.group(1));
        }
        for(String clave:requeridas)if(!datos.containsKey(clave))noEntendido.add(clave);
    }

    /**
     * Anota el origen y devuelve la frase <b>sin</b> el, o {@code null} si no lo
     * habia.
     */
    private static String origenDeMatching(String llano, Map<String, String> datos) {
        Matcher origen = ORIGEN_DE_MATCHING.matcher(llano);
        if (!origen.find()) {
            return null;
        }
        datos.put("origen", origen.group(1).toUpperCase(Locale.ROOT));
        datos.put("idOrigen", origen.group(2).toUpperCase(Locale.ROOT));
        return llano.substring(0, origen.start()) + " " + llano.substring(origen.end());
    }

    /** A quien se busca: lo que venga detras de "de", "sobre" o "a". */
    private static void aQuien(String original, String llano, Map<String, String> datos) {
        nombreDe(original, llano).ifPresent(nombre -> datos.put("texto", nombre));
        Matcher documento = DOCUMENTO.matcher(llano);
        if (documento.find()) {
            datos.put("texto", documento.group(1));
        }
    }

    private static void propietario(String original, String llano, Map<String, String> datos,
                                    List<String> noEntendido) {
        nombreDe(original, llano).ifPresent(nombre -> datos.put("nombre", nombre));
        Matcher documento = DOCUMENTO.matcher(llano);
        if (documento.find()) {
            String numero = documento.group(1);
            datos.put("numeroDocumento", numero);
            // Ocho digitos es DNI y once es RUC: lo dice la longitud, no una
            // suposicion. Un RUC obliga ademas a persona juridica, igual que en
            // el alta de la pantalla (D-E2-3 §3.1.2).
            // El puerto conserva los códigos Core de Persona: D=DNI, R=RUC.
            datos.put("tipoDocumento", numero.length() == 8 ? "D" : "R");
            if (numero.length() == 11) {
                datos.put("tipoPersona", "J");
            }
        }
        telefonoDe(llano).ifPresent(telefono -> datos.put("telefono", telefono));
        if (!datos.containsKey("numeroDocumento")) {
            // Se declara: sin documento no hay con que descartar un duplicado,
            // y un propietario repetido ensucia la busqueda de toda captacion
            // futura (D-E2-3 §3.1.2).
            noEntendido.add("documento");
        }
    }

    private void interaccion(String original, String llano, Map<String, String> datos,
                             SesionBrox sesion, Traza traza) {
        Map<String,Object> definicion=brox.definicionDeInteracciones(sesion,
                traza.invocando(Herramientas.DEFINICION_DE_INTERACCIONES.nombre()));
        if(definicion==null || !(definicion.get("version") instanceof String version) || version.isBlank())
            throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        Map<String,Object> conocidos=new LinkedHashMap<>(ArgumentosComerciales.leer(
                original,ClienteBrox.OperacionComercial.REGISTRAR_INTERACCION));
        conocidos.putIfAbsent("resumen",original.trim());
        List<Map<String,Object>> vinculos=new ArrayList<>();
        Matcher sobre=Pattern.compile("\\b(propiedad|inmueble|oportunidad|cliente|encargo|captacion|prospeccion)\\s+(\\d+)\\b")
                .matcher(llano);
        while(sobre.find()) {
            String tipo=sobre.group(1).toUpperCase(Locale.ROOT);
            if("CAPTACION".equals(tipo))tipo="ENCARGO";
            if("INMUEBLE".equals(tipo))tipo="PROPIEDAD";
            try { vinculos.add(Map.of("tipo",tipo,"referencia",Long.valueOf(sobre.group(2)))); }
            catch(NumberFormatException invalido) { throw new IllegalArgumentException("Referencia fuera de rango",invalido); }
        }
        if(!vinculos.isEmpty())conocidos.put("vinculos",vinculos);
        opcionDeclarada(definicion,"tipos",llano,false).ifPresent(v->conocidos.putIfAbsent("tipo",v));
        String medios=llano.replaceAll("\\bwsp\\b","whatsapp")
                .replaceAll("\\b(?:email|mail)\\b","correo electronico")
                .replaceAll("\\bcorreo\\b(?!\\s+electronico)","correo electronico")
                .replaceAll("\\bportal\\b(?!\\s+inmobiliario)","portal inmobiliario");
        opcionDeclarada(definicion,"canales",medios,false).ifPresent(v->conocidos.putIfAbsent("canal",v));
        opcionDeclarada(definicion,"resultados",llano,true).ifPresent(v->conocidos.putIfAbsent("resultado",v));
        List<Map<String,String>> eventos=new ArrayList<>();
        for(String evento:opcionesDeclaradas(definicion,"eventos",llano,false))
            eventos.add(Map.of("evento",evento));
        if(!eventos.isEmpty())conocidos.put("eventos",eventos);
        datos.put("datosComerciales",ArgumentosComerciales.serializar(conocidos));
    }

    private static void resultadoVisitaDeclarado(String original,String llano,Map<String,String> datos) {
        Map<String,Object> conocidos=new LinkedHashMap<>();
        conocidos.put("resultadoVisita",Map.of("observaciones",original.trim()));
        Matcher visita=Pattern.compile("\\bvisita\\s+(\\d+)\\b").matcher(llano);
        if(visita.find())conocidos.put("idVisita",Long.valueOf(visita.group(1)));
        Matcher contexto=Pattern.compile("\\b(oportunidad|cliente|captacion|encargo|prospeccion|propiedad)\\s+(\\d+)\\b").matcher(llano);
        if(contexto.find())datos.put("contextoVisitaDeclarado",contexto.group());
        datos.put("datosComerciales",ArgumentosComerciales.serializar(conocidos));
    }

    private static Optional<String> opcionDeclarada(Map<String,Object> definicion,String campo,
                                                     String texto,boolean resultado) {
        List<String> valores=opcionesDeclaradas(definicion,campo,texto,resultado);
        return valores.size()==1?Optional.of(valores.getFirst()):Optional.empty();
    }

    private static List<String> opcionesDeclaradas(Map<String,Object> definicion,String campo,
                                                   String texto,boolean resultado) {
        if(!(definicion.get(campo) instanceof List<?> opciones))
            throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
        var valores=new java.util.LinkedHashSet<String>();
        for(Object elemento:opciones) {
            if(!(elemento instanceof Map<?,?> opcion) || !(opcion.get("valor") instanceof String valor)
                    || !(opcion.get("rotulo") instanceof String rotulo))
                throw com.controllocal.asistencia.brox.ErrorBrox.contratoInvalido();
            String contexto=texto;
            if(resultado) {
                Matcher m=Pattern.compile("\\bresultado\\s*(?:=|:)?\\s*(.*)").matcher(texto);
                contexto=m.find()?m.group(1):"";
            }
            boolean literal=resultado?palabra(contexto,llano(valor))
                    :!"canales".equals(campo) && valor.contains("_") && palabra(texto,llano(valor));
            String etiqueta=llano(rotulo).replace('_',' '), codigo=llano(valor).replace('_',' ');
            boolean nombrado="canales".equals(campo)
                    ? medioDeclarado(contexto,etiqueta)||medioDeclarado(contexto,codigo)
                    : palabra(contexto,etiqueta)||palabra(contexto,codigo);
            if(literal || nombrado)valores.add(valor);
        }
        return List.copyOf(valores);
    }

    private static boolean palabra(String texto,String valor) {
        return !valor.isBlank() && Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(valor)
                +"(?![\\p{L}\\p{N}_])").matcher(texto).find();
    }

    private static boolean medioDeclarado(String texto,String medio) {
        String inicio="(?:\\b(?:por|via|canal\\s*[=:]?)\\s+|^(?:anota[r]?|registra[r]?|apunta)\\s+)";
        return Pattern.compile(inicio+"(?:(?:el|la|un|una)\\s+)?"+Pattern.quote(medio)
                +"(?![\\p{L}\\p{N}_])").matcher(texto).find();
    }

    /** Un telefono peruano: nueve digitos, o seis a ocho de fijo. */
    private static Optional<String> telefonoDe(String llano) {
        Matcher movil = Pattern.compile("\\b(9\\d{8})\\b").matcher(llano);
        return movil.find() ? Optional.of(movil.group(1)) : Optional.empty();
    }

    /**
     * Lo que viene detras de "a", "de" o "sobre", conservando las mayusculas
     * del original: es un nombre propio y va a una busqueda por texto.
     */
    private static Optional<String> nombreDe(String original, String llano) {
        // El nombre que sigue a una persona nombrada tiene prioridad frente a metadatos
        // posteriores: «al propietario Ana Cobre con DNI ...» no nombra «DNI».
        Matcher tras = Pattern.compile("\\b(?:cliente|propietari[oa]|duen[oa]|senor|senora|sr|sra)"
                + "\\s+([a-z][a-z ]{2,80})").matcher(llano);
        if (!tras.find()) {
            tras = Pattern.compile("\\b(?:a|al|de|del|sobre|con|para)\\s+(?:la\\s+|el\\s+|los\\s+"
                            + "|las\\s+|senor\\s+|senora\\s+|sr\\s+|sra\\s+)?([a-z][a-z ]{2,40})")
                    .matcher(llano);
            if (!tras.find()) return Optional.empty();
        }
        String encontrado = tras.group(1).trim();
        if (encontrado.matches("^(?:con|dni|ruc|documento)(?:\\s.*)?$")) return Optional.empty();
        // Se recorta en la primera palabra funcional: "torres en miraflores" es
        // "torres", no la frase entera.
        for (String corte : new String[]{" en ", " con ", " por ", " que ", " de ", " para "}) {
            int donde = (" " + encontrado + " ").indexOf(corte);
            if (donde >= 0) {
                encontrado = encontrado.substring(0, donde).trim();
            }
        }
        if (encontrado.isBlank()) {
            return Optional.empty();
        }
        // Se devuelve el trozo del ORIGINAL, con sus tildes y mayusculas: es lo
        // que se va a buscar contra nombres de personas.
        int donde = llano.indexOf(encontrado);
        return donde < 0 || donde + encontrado.length() > original.length()
                ? Optional.of(encontrado)
                : Optional.of(original.substring(donde, donde + encontrado.length()).trim());
    }

    // ==================================================================

    /**
     * Minusculas, sin tildes y con los superindices plegados: "Miraflores" y
     * "miraflores" son el mismo sitio, y {@code m²} y {@code m2} son la misma
     * unidad.
     *
     * <h2>Por que los superindices se pliegan a mano</h2>
     * Porque <b>NFD no los descompone</b>. La forma de descomposicion canonica
     * deja {@code U+00B2} tal cual —quien lo convierte en {@code 2} es NFKD, la
     * de <i>compatibilidad</i>—, asi que {@code llano("m²")} valia {@code "m²"}
     * y no coincidia con lo que cualquier persona escribe en un teclado:
     * {@code "m2"}. El catalogo publica la unidad con superindice y las frases
     * la traen sin el, de modo que las dos formas del mismo metro cuadrado no
     * se encontraban nunca.
     *
     * <p>Se pliega aqui y no con NFKD porque NFKD ademas rompe cosas que aqui
     * importan: convierte ligaduras, comillas tipograficas y espacios duros, y
     * cambiaria el texto que despues se devuelve como nombre propio.
     */
    static String llano(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.replace('²', '2').replace('³', '3')
                .toLowerCase(Locale.ROOT).trim();
    }

    private static boolean contieneAlguna(String llano, String... candidatas) {
        for (String candidata : candidatas) {
            if (llano.contains(candidata)) {
                return true;
            }
        }
        return false;
    }

    /** Contiene la palabra, no el trozo: "surco" no esta dentro de "surcos". */
    private static boolean contienePalabra(String llano, String palabra) {
        return Pattern.compile("\\b" + Pattern.quote(palabra) + "\\b").matcher(llano).find();
    }
}

