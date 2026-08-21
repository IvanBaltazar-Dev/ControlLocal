package com.kairos.interpretacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Vocabulario;
import com.kairos.conversacion.Accion;
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
 *   <tr><td>los siete tipos</td><td>{@link Vocabulario#tipoPropiedad}, del contrato</td></tr>
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
 *   <li><b>Un numero no es un precio por ser un numero.</b> Se emite como
 *       importe solo si viene <i>anclado</i>: con moneda o con escala ("mil",
 *       "millones"). Sin eso, "un depa de 3 dormitorios" registraria una
 *       propiedad de tres soles.</li>
 *   <li><b>Lo ambiguo se declara, no se resuelve.</b> "120 m2" encaja con tres
 *       atributos del catalogo —metraje total, metraje construido y area de
 *       terreno— asi que no se emite ninguno: se declara en
 *       {@code noEntendido} y el motor lo pregunta con su nombre.</li>
 * </ol>
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

    private static final Map<String, String> MONEDAS = Map.of(
            "s/", "PEN", "soles", "PEN", "sol", "PEN", "pen", "PEN",
            "us$", "USD", "usd", "USD", "dolares", "USD", "dolar", "USD", "$", "USD");

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
            "([\\d.,]+)\\s*" + ESCALA + "?\\s*(dolares|dolar|soles|sol|usd|pen)?");

    /** Un numero pegado a una palabra: "3 dormitorios", "dormitorios 3", "120 m2". */
    private static final Pattern NUMERO_Y_PALABRA = Pattern.compile(
            "([\\d.,]+)\\s*([a-z][a-z0-9_]{1,24})");

    /** Un codigo del sistema: PROP-0001, CAP-00003. */
    private static final Pattern CODIGO = Pattern.compile("\\b((?:prop|cap)-\\d{1,10})\\b");

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
    public Lectura leer(String texto, SesionBrox sesion) {
        if (texto == null || texto.isBlank()) {
            return Lectura.nada(Lectura.SIN_TEXTO);
        }
        String llano = llano(texto);
        Accion accion = accionDe(llano);
        if (accion == null) {
            return Lectura.nada(Lectura.SIN_ACCION);
        }

        Map<String, String> datos = new LinkedHashMap<>();
        List<String> noEntendido = new ArrayList<>();

        switch (accion) {
            case REGISTRAR_PROPIEDAD -> propiedad(llano, sesion, datos, noEntendido);
            case CONTINUAR_BORRADOR -> codigoDeBorrador(llano, datos);
            case CONSULTAR_PROPIEDAD -> quePropiedad(texto, llano, sesion, datos);
            case CONSULTAR_CLIENTE -> aQuien(texto, llano, datos);
            case REGISTRAR_PROPIETARIO -> propietario(texto, llano, datos, noEntendido);
            case REGISTRAR_INTERACCION -> interaccion(texto, llano, datos, noEntendido);
        }
        return new Lectura(accion, datos, List.copyOf(noEntendido), null);
    }

    // ==================================================================
    // Que quiere hacer
    // ==================================================================

    /**
     * Verbo + objeto. Los dos hacen falta: "registra" solo no dice que, y
     * "propiedad" solo no dice si consultarla o crearla. Sin los dos, no hay
     * accion — y no haberla entendido se dice, en vez de elegir la mas comun.
     */
    private static Accion accionDe(String llano) {
        boolean consultar = contieneAlguna(llano, "consulta", "consultar", "busca", "buscar",
                "muestra", "mostrar", "dime", "que sabes", "ver ", "listame", "lista ");
        boolean registrar = contieneAlguna(llano, "registra", "registrar", "da de alta",
                "dar de alta", "anota", "anotar", "apunta", "crear", "crea ", "agrega", "agregar");
        boolean continuar = contieneAlguna(llano, "continua", "continuar", "sigue", "seguir",
                "retoma", "retomar", "termina de", "lo de ayer", "lo que quedo", "a medias",
                "pendiente de ayer");

        boolean deInteraccion = contieneAlguna(llano, "llamada", "llame", "llamé", "visita",
                "correo", "email", "whatsapp", "mensaje", "contacto", "contacte", "interaccion",
                "conversacion con", "hable con", "reunion");
        boolean deCliente = contieneAlguna(llano, "cliente", "clientes", "interesado",
                "interesados", "demanda");
        boolean dePropietario = contieneAlguna(llano, "propietario", "propietaria",
                "propietarios", "dueno", "duena", "duenos", "titular", "titulares");
        boolean dePropiedad = contieneAlguna(llano, "propiedad", "propiedades", "inmueble",
                "inmuebles", "local", "oficina", "departamento", "depa", "depto", "dpto", "casa",
                "terreno", "almacen", "deposito", "lote", "tienda", "flat");

        // El borrador gana a todo: "continua" solo puede significar una cosa, y
        // la frase que lo dice suele nombrar tambien la propiedad que se estaba
        // registrando ("sigue con el depa de Miraflores").
        if (continuar) {
            return Accion.CONTINUAR_BORRADOR;
        }
        if (registrar) {
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

    private void propiedad(String llano, SesionBrox sesion, Map<String, String> datos,
                           List<String> noEntendido) {
        tipoDe(llano).ifPresent(t -> datos.put(Vocabulario.TIPO_PROPIEDAD, t));
        operacionDe(llano).ifPresent(o -> datos.put(Vocabulario.OPERACIONES_DECLARADAS, o));

        precioDe(llano).ifPresent(precio -> {
            datos.put(Vocabulario.IMPORTE, precio.importe().toPlainString());
            if (precio.moneda() != null) {
                datos.put(Vocabulario.MONEDA, precio.moneda());
            }
        });

        distritoDe(llano, sesion).ifPresent(d -> datos.put(Vocabulario.DISTRITO, d));

        // Los atributos solo se buscan cuando ya se sabe el tipo: es el catalogo
        // de ESE tipo el que dice que claves existen, y sin tipo no hay catalogo
        // que consultar. Lo que no se extraiga aqui lo preguntara el motor.
        String tipo = datos.get(Vocabulario.TIPO_PROPIEDAD);
        if (tipo != null) {
            atributosDe(llano, sesion, tipo, datos, noEntendido);
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

    /**
     * La operacion, <b>solo si la frase la dice</b>.
     *
     * <p>Sin coincidencia no se devuelve nada, y eso es una decision y no una
     * carencia: el motor la pondra en {@code faltante} y KAIROS la preguntara.
     * Es la unica forma de que un precio de venta no acabe en la serie de
     * alquiler.
     */
    private static Optional<String> operacionDe(String llano) {
        for (String palabra : llano.split("[^a-z0-9/]+")) {
            String declarada = OPERACIONES.get(palabra);
            if (declarada != null) {
                // Se pasa por el vocabulario del dominio incluso sabiendo que
                // vale: BROX la volvera a validar al anotarla, y si algun dia
                // deja de admitirla, el rechazo vendra con su explicacion.
                return Optional.of(declarada);
            }
        }
        return Optional.empty();
    }

    private record Precio(BigDecimal importe, String moneda) {
    }

    /**
     * Un importe <b>anclado</b>: con moneda o con escala. Un numero suelto no
     * es un precio, y tratarlo como tal registraria "3 dormitorios" como tres
     * soles.
     */
    private static Optional<Precio> precioDe(String llano) {
        Matcher conSimbolo = PRECIO_CON_SIMBOLO.matcher(llano);
        if (conSimbolo.find()) {
            BigDecimal importe = numero(conSimbolo.group(2));
            if (importe != null) {
                return Optional.of(new Precio(escalar(importe, conSimbolo.group(3)),
                        MONEDAS.get(conSimbolo.group(1).replace("s/.", "s/"))));
            }
        }
        Matcher conPalabra = PRECIO_CON_PALABRA.matcher(llano);
        while (conPalabra.find()) {
            String escala = conPalabra.group(2);
            String moneda = conPalabra.group(3);
            if (escala == null && moneda == null) {
                continue;   // un numero pelado: no es un precio
            }
            BigDecimal importe = numero(conPalabra.group(1));
            if (importe != null) {
                return Optional.of(new Precio(escalar(importe, escala),
                        moneda == null ? null : MONEDAS.get(moneda)));
            }
        }
        return Optional.empty();
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
    private Optional<String> distritoDe(String llano, SesionBrox sesion) {
        return brox.distritos(sesion).stream()
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
     * <p>Se reconoce por la <b>clave</b> ({@code dormitorios}) o por el
     * <b>rotulo</b> ({@code Dormitorios}); nunca por la unidad, porque tres
     * atributos comparten {@code m2} y "120 m2" no dice cual de los tres es.
     * Ese caso se declara en {@code noEntendido}: el motor lo preguntara por su
     * nombre, que es lo unico que lo desambigua.
     */
    private void atributosDe(String llano, SesionBrox sesion, String tipoPropiedad,
                             Map<String, String> datos, List<String> noEntendido) {
        Map<String, String> porPalabra = new LinkedHashMap<>();
        Map<String, Integer> unidades = new LinkedHashMap<>();
        for (ClienteBrox.Pregunta definicion : brox.catalogoDe(sesion, tipoPropiedad)) {
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
            Integer cuantos = unidades.get(palabra);
            if (cuantos != null && cuantos > 1) {
                // La unidad no basta: hay mas de un atributo que la usa.
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
    private void quePropiedad(String original, String llano, SesionBrox sesion,
                              Map<String, String> datos) {
        Matcher codigo = CODIGO.matcher(llano);
        if (codigo.find()) {
            datos.put("texto", codigo.group(1).toUpperCase(Locale.ROOT));
            return;
        }
        Optional<String> distrito = distritoDe(llano, sesion);
        if (distrito.isPresent()) {
            datos.put("texto", distrito.get());
            return;
        }
        nombreDe(original, llano).ifPresent(nombre -> datos.put("texto", nombre));
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
            datos.put("tipoDocumento", numero.length() == 8 ? "DNI" : "RUC");
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

    private static void interaccion(String original, String llano, Map<String, String> datos,
                                    List<String> noEntendido) {
        canalDe(llano).ifPresent(canal -> datos.put("canalContacto", canal));

        Matcher sobre = Pattern.compile(
                        "\\b(oportunidad|cliente|captacion|prospeccion)\\s+(\\d{1,9})\\b")
                .matcher(llano);
        if (sobre.find()) {
            datos.put("contexto", sobre.group(1).toUpperCase(Locale.ROOT));
            datos.put("idEntidad", sobre.group(2));
        } else {
            // Una interaccion cuelga de UNA de cuatro entidades —lo garantiza un
            // CHECK de la base—. Sin saber de cual, no se registra: una nota sin
            // expediente no la encuentra despues nadie.
            noEntendido.add("sobre que");
        }

        // El resultado NO se deduce del tono de la frase. Su vocabulario
        // depende del contexto y lo decide el dominio; aqui solo se reconoce si
        // la frase nombra uno literalmente. Si no, el adaptador lo pregunta con
        // la lista exacta que ese contexto admite.
        for (String candidato : llano.split("[^a-z_]+")) {
            if (candidato.length() > 3 && candidato.contains("_")) {
                datos.put("resultado", candidato.toUpperCase(Locale.ROOT));
                break;
            }
        }
        datos.put("observaciones", original.trim());
    }

    /** Un telefono peruano: nueve digitos, o seis a ocho de fijo. */
    private static Optional<String> telefonoDe(String llano) {
        Matcher movil = Pattern.compile("\\b(9\\d{8})\\b").matcher(llano);
        return movil.find() ? Optional.of(movil.group(1)) : Optional.empty();
    }

    /**
     * El canal, con el vocabulario de {@code InteraccionComercial.CANALES}:
     * L llamada, W WhatsApp, E email, P presencial, R reunion, T portal, O otro.
     * Los codigos son los del dominio; lo que esta clase pone son las palabras.
     */
    private static Optional<String> canalDe(String llano) {
        if (contieneAlguna(llano, "whatsapp", "wsp")) {
            return Optional.of("W");
        }
        if (contieneAlguna(llano, "correo", "email", "mail")) {
            return Optional.of("E");
        }
        if (contieneAlguna(llano, "reunion", "reunimos")) {
            return Optional.of("R");
        }
        if (contieneAlguna(llano, "visita", "presencial", "en obra", "en el local")) {
            return Optional.of("P");
        }
        if (contieneAlguna(llano, "llamada", "llame", "telefono", "telefonica", "llamo")) {
            return Optional.of("L");
        }
        if (contieneAlguna(llano, "portal", "aviso")) {
            return Optional.of("T");
        }
        return Optional.empty();
    }

    /**
     * Lo que viene detras de "a", "de" o "sobre", conservando las mayusculas
     * del original: es un nombre propio y va a una busqueda por texto.
     */
    private static Optional<String> nombreDe(String original, String llano) {
        Matcher tras = Pattern.compile("\\b(?:a|de|del|sobre|con|para)\\s+(?:la\\s+|el\\s+|los\\s+"
                        + "|las\\s+|senor\\s+|senora\\s+|sr\\s+|sra\\s+)?([a-z][a-z ]{2,40})")
                .matcher(llano);
        if (!tras.find()) {
            return Optional.empty();
        }
        String encontrado = tras.group(1).trim();
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

    /** Minusculas y sin tildes: "Miraflores" y "miraflores" son el mismo sitio. */
    static String llano(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
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
