package com.kairos.interpretacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Traza;
import com.kairos.brox.Vocabulario;
import com.kairos.conversacion.Accion;
import com.kairos.herramientas.Herramientas;
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
            "\\b(cliente|captacion|prospeccion)\\s+((?:cap-)?\\d{1,10})\\b");

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
            case CONSULTAR_PROPIEDAD -> quePropiedad(texto, llano, sesion, traza, datos);
            case CONSULTAR_CLIENTE -> aQuien(texto, llano, datos);
            case REGISTRAR_PROPIETARIO -> propietario(texto, llano, datos, noEntendido);
            case REGISTRAR_INTERACCION -> interaccion(texto, llano, datos, noEntendido);
            case BUSCAR_COINCIDENCIAS -> deQueCoincidencias(llano, datos, noEntendido);
            case EXPLICAR_MATCH -> queCoincidenciaExplicar(llano, datos, noEntendido);
            // El foco no lleva datos: preguntar "que resuelvo hoy" no admite
            // parametros, y aceptarlos —un rol, un agente— seria dejar que la
            // frase eligiera de quien es el foco.
            case QUE_RESOLVER_HOY -> { }
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
            return Accion.CONTINUAR_BORRADOR;
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
        tipoDe(llano).ifPresent(t -> datos.put(Vocabulario.TIPO_PROPIEDAD, t));
        operacionDe(llano).ifPresent(o -> datos.put(Vocabulario.OPERACIONES_DECLARADAS, o));

        precioDe(llano).ifPresent(precio -> {
            datos.put(Vocabulario.IMPORTE, precio.importe().toPlainString());
            if (precio.moneda() != null) {
                datos.put(Vocabulario.MONEDA, precio.moneda());
            }
        });

        distritoDe(llano, sesion, traza).ifPresent(d -> datos.put(Vocabulario.DISTRITO, d));

        // Los atributos solo se buscan cuando ya se sabe el tipo: es el catalogo
        // de ESE tipo el que dice que claves existen, y sin tipo no hay catalogo
        // que consultar. Lo que no se extraiga aqui lo preguntara el motor.
        String tipo = datos.get(Vocabulario.TIPO_PROPIEDAD);
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
     * {@code años} la usa <b>solo</b> {@code antiguedad_anios} en los siete
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
     * m²», «8 m», «30 %», «5 kW»— <b>se perdia en silencio</b>. Medido el
     * 2026-09-05 sobre {@code catalogoDe} (comunes + del tipo) contra el
     * catalogo vivo de los siete tipos:
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
     * De {@link com.kairos.brox.ClienteBrox#catalogoDe}, que devuelve
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
            noEntendido.add("de que cliente, encargo o prospeccion");
        }
    }

    /**
     * Lo mismo, mas <b>cual</b> de las coincidencias explicar.
     *
     * <p>El codigo se busca en lo que queda de la frase <b>despues de quitar el
     * origen</b>. Sin quitarlo, «por que encaja PROP-12 con la captacion
     * CAP-00003» tomaria {@code CAP-00003} como la coincidencia a explicar —el
     * patron de codigos reconoce las dos formas— y KAIROS explicaria el encargo
     * del que parte en vez de la propiedad que se le esta preguntando.
     */
    private static void queCoincidenciaExplicar(String llano, Map<String, String> datos,
                                                List<String> noEntendido) {
        String resto = origenDeMatching(llano, datos);
        if (resto == null) {
            noEntendido.add("de que cliente, encargo o prospeccion");
            resto = llano;
        }
        Matcher codigo = CODIGO.matcher(resto);
        if (codigo.find()) {
            datos.put("codigo", codigo.group(1).toUpperCase(Locale.ROOT));
        }
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
