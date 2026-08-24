package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.AtributoPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.AtributoPropiedadRepository;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * <b>El catalogo decide que se pregunta; esto lo aplica.</b>
 *
 * <h2>La regla que sostiene el motor de captura (D-E4-2)</h2>
 * Ninguna pantalla tiene una lista de campos escrita a mano. Lo que se pregunta
 * de un terreno sale de {@code catalogo_atributo}, igual que lo que se pregunta
 * de un departamento; anadir "Almacen" no anade un formulario, anade filas.
 *
 * <pre>
 *   TERRENO      -> area de terreno, frente, zonificacion   (no dormitorios)
 *   DEPARTAMENTO -> area, dormitorios, banos, piso, mantenimiento
 * </pre>
 *
 * <p>Angular <b>representa</b> la pregunta; no la decide. KAIROS hara
 * exactamente lo mismo. Si la regla viviera en el cliente habria dos copias
 * divergiendo desde el primer dia, y la tercera copia llegaria con el canal de
 * WhatsApp.
 *
 * <h2>Que hace con los valores</h2>
 * Los valores llegan como texto —de un formulario, de un JSON o de una frase
 * dictada— y el catalogo dice de que tipo son. La conversion se hace <b>aqui</b>
 * y no en el cliente: es la misma razon por la que el tipo esta gobernado.
 *
 * <p>Un valor que no encaja con su tipo se rechaza con el nombre del atributo
 * delante. El trigger {@code tg_atributo_gobernado} (V48) lo rechazaria
 * igualmente, pero con un mensaje de PostgreSQL a mitad de una transaccion, y
 * eso no se le puede ensenar a nadie.
 */
@Component
public class AtributosGobernados {

    private final CatalogoAtributoRepository catalogo;
    private final AtributoPropiedadRepository valores;

    public AtributosGobernados(CatalogoAtributoRepository catalogo,
                               AtributoPropiedadRepository valores) {
        this.catalogo = catalogo;
        this.valores = valores;
    }

    /** Lo que se pregunta para un tipo de propiedad, en orden de presentacion. */
    public List<CatalogoAtributo> aplicablesA(long idOrganizacion, String tipoPropiedad) {
        return catalogo.aplicablesA(idOrganizacion, tipoPropiedad);
    }

    /**
     * Las claves obligatorias de ese tipo. Es lo que el motor de captura
     * compara con lo que ya sabe para decir que falta, <b>antes</b> de intentar
     * guardar.
     */
    public List<String> obligatoriasDe(long idOrganizacion, String tipoPropiedad) {
        return aplicablesA(idOrganizacion, tipoPropiedad).stream()
                .filter(atributo -> atributo.esRequeridoPara(tipoPropiedad))
                .map(CatalogoAtributo::getClave)
                .toList();
    }

    /** Lo que le falta a una propiedad ya escrita. Consulta de V48, sobre su indice. */
    public List<String> obligatoriasQueFaltan(long idOrganizacion, long idPropiedad,
                                              String tipoPropiedad) {
        return valores.clavesObligatoriasQueFaltan(idOrganizacion, idPropiedad, tipoPropiedad);
    }

    /**
     * Lo que le falta, mirando <b>las dos autoridades</b>.
     *
     * <p>La consulta de arriba solo ve las claves gobernadas: una declarada
     * ESTRUCTURAL no deja fila en {@code atributo_propiedad}, asi que buscarla
     * alli la daria por faltante en todas las propiedades. Aqui se anaden las
     * estructurales cuyo campo canonico este vacio, que es donde de verdad
     * viven (D-E4-3).
     */
    public List<String> obligatoriasQueFaltan(long idOrganizacion, Propiedad propiedad) {
        String tipo = propiedad.getTipoInmueble();
        List<String> faltan = new ArrayList<>(
                valores.clavesObligatoriasQueFaltan(idOrganizacion, propiedad.getId(), tipo));

        for (CatalogoAtributo definicion : aplicablesA(idOrganizacion, tipo)) {
            if (definicion.esEstructural()
                    && definicion.esRequeridoPara(tipo)
                    && !EscritorEstructural.tieneValor(propiedad, definicion.getCampoEstructural())) {
                faltan.add(definicion.getClave());
            }
        }
        return List.copyOf(faltan);
    }

    /**
     * <b>Lo que le falta a una propiedad para poder ANUNCIARSE</b> (V72).
     *
     * <p>ALT y PUB, mirando las dos autoridades igual que
     * {@link #obligatoriasQueFaltan(long, Propiedad)}. Es la unica pregunta que
     * debe hacer el caso de uso de publicacion: si cada controlador o cada
     * pantalla volviera a interpretar los tres niveles, en dos cortes habria
     * tres interpretaciones -- y una regla con tres duenos no es una regla.
     *
     * <p>Que devuelva una lista vacia significa «se puede publicar». Que
     * devuelva claves significa que faltan, <b>con su nombre</b>, para que el
     * mensaje diga que hacer en vez de que no se puede.
     */
    public List<String> faltantesDePropiedadParaPublicar(long idOrganizacion, Propiedad propiedad) {
        String tipo = propiedad.getTipoInmueble();
        List<String> faltan = new ArrayList<>(
                valores.clavesQueImpidenPublicar(idOrganizacion, propiedad.getId(), tipo));

        for (CatalogoAtributo definicion : aplicablesA(idOrganizacion, tipo)) {
            if (definicion.esEstructural()
                    && definicion.bloqueaPublicacionPara(tipo)
                    && !EscritorEstructural.tieneValor(propiedad, definicion.getCampoEstructural())) {
                faltan.add(definicion.getClave());
            }
        }
        return List.copyOf(faltan);
    }

    /** Los rotulos de esas claves, para poder decirlo en palabras y no en claves. */
    public List<String> rotulosDe(long idOrganizacion, String tipoPropiedad, List<String> claves) {
        if (claves.isEmpty()) {
            return List.of();
        }
        Map<String, CatalogoAtributo> porClave = definicionesDe(idOrganizacion, tipoPropiedad);
        return claves.stream()
                .map(clave -> porClave.containsKey(clave) ? porClave.get(clave).getRotulo() : clave)
                .toList();
    }

    /**
     * Comprueba que la clave existe, que aplica a ese tipo y que el valor
     * encaja con su tipo de dato. Devuelve el atributo listo para guardar.
     *
     * <p>Se hace en este orden a proposito: "esa clave no existe" y "esa clave
     * no aplica a un terreno" son errores distintos y se arreglan distinto.
     */
    public AtributoPropiedad convertir(long idOrganizacion, long idPropiedad, String tipoPropiedad,
                                       String clave, String valor, String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, tipoPropiedad);
        String limpio = exigirValor(clave, valor);

        return switch (definicion.tipo()) {
            case ENTERO -> AtributoPropiedad.deNumero(
                    idOrganizacion, idPropiedad, clave, enRango(definicion, entero(clave, limpio)));
            case DECIMAL -> AtributoPropiedad.deNumero(
                    idOrganizacion, idPropiedad, clave, enRango(definicion, decimal(clave, limpio)));
            case IMPORTE -> AtributoPropiedad.deImporte(
                    idOrganizacion, idPropiedad, clave, enRango(definicion, decimal(clave, limpio)),
                    exigirMoneda(clave, moneda));
            case BOOLEANO -> AtributoPropiedad.deBooleano(
                    idOrganizacion, idPropiedad, clave, booleano(clave, limpio));
            case FECHA -> AtributoPropiedad.deFecha(
                    idOrganizacion, idPropiedad, clave, fecha(clave, limpio));
            case TEXTO, LISTA -> AtributoPropiedad.deTexto(
                    idOrganizacion, idPropiedad, clave, enLongitud(definicion, limpio));
            // Un multivalor no se construye con un valor suelto: su fila es un
            // ancla y sus valores viven aparte. Quien llegue aqui con uno esta
            // usando la puerta equivocada, y decirselo es mas util que guardar
            // el primero y perder los demas.
            case LISTA_MULTIPLE -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" admite varios valores: se guarda con la via de "
                            + "multivalor, no con un valor suelto.");
        };
    }

    /**
     * El ancla de un multivalor, con sus valores.
     *
     * <p>Va aparte de {@link #convertir} porque no es el mismo acto: alli se
     * escribe UN valor, aqui se sustituye un CONJUNTO. Y sustituir es lo
     * correcto -- anadir dejaria sin forma de quitar una opcion, que es la
     * mitad de lo que significa editar una lista.
     */
    public AtributoPropiedad convertirMultivalor(long idOrganizacion, long idPropiedad,
                                                 String tipoPropiedad, String clave) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, tipoPropiedad);
        if (!definicion.tipo().esMultivalor()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" no admite varios valores.");
        }
        return AtributoPropiedad.anclaDeMultivalor(idOrganizacion, idPropiedad, clave);
    }

    /**
     * Solo comprueba que el VALOR encaja con el tipo de dato de su clave, sin
     * mirar a qué tipo de propiedad aplica.
     *
     * <p>Existe por el motor de captura: alguien puede dictar
     * <i>«tres dormitorios»</i> <b>antes</b> de decir que es un departamento.
     * Ahí no se puede comprobar la aplicabilidad —todavía no se sabe de qué tipo
     * es la propiedad— y rechazarlo con «no aplica a una propiedad de tipo OTRO»
     * sería un mensaje falso sobre un dato correcto. La aplicabilidad se
     * comprueba en cuanto el tipo se conoce, y otra vez al guardar.
     */
    public void exigirValorCompatible(long idOrganizacion, String clave, String valor) {
        ConversionDeValores.exigirCompatible(definicionDe(idOrganizacion, clave), valor);
    }

    // ------------------------------------------------------------------
    // Convertir un texto en un valor tipado NO depende del sujeto: un entero es
    // un entero lo lleve una propiedad o un encargo, y las monedas que existen
    // son las mismas. Eso vive entero en ConversionDeValores desde V73.
    //
    // Copiarlo en el enrutador del encargo habria creado dos definiciones de la
    // misma regla, y habrian divergido en el primer arreglo hecho en una sola.
    // Aqui quedan los atajos para que el codigo de enrutamiento --que si
    // depende del sujeto-- se lea seguido.
    // ------------------------------------------------------------------

    private static LocalDate fecha(String clave, String valor) {
        return ConversionDeValores.fecha(clave, valor);
    }

    private static String exigirMoneda(String clave, String moneda) {
        return ConversionDeValores.exigirMoneda(clave, moneda);
    }

    private static String enLongitud(CatalogoAtributo definicion, String valor) {
        return ConversionDeValores.enLongitud(definicion, valor);
    }

    private static void exigirQueAplique(CatalogoAtributo definicion, String tipoPropiedad) {
        if (!definicion.aplicaA(tipoPropiedad)) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" no aplica a una propiedad de tipo "
                            + nombreDelTipo(tipoPropiedad) + ". Los atributos de cada tipo salen del "
                            + "catalogo, no del formulario.");
        }
    }

    private static String exigirValor(String clave, String valor) {
        return ConversionDeValores.exigirValor(clave, valor);
    }

    /**
     * <b>Lo que se le exige a un valor ANTES de escribirlo en su campo
     * canonico</b> (V79).
     *
     * <p>Hasta este corte el camino estructural solo comprobaba que el valor no
     * llegara vacio, y la conversion la hacia {@code EscritorEstructural} al
     * asignarlo. Con dos conceptos estructurales numericos y de texto libre eso
     * bastaba; con el primero de tipo LISTA deja de bastar, porque la
     * pertenencia al vocabulario vivia <b>solo</b> dentro del trigger de
     * {@code atributo_propiedad}, por donde un valor estructural no pasa.
     *
     * <p>Se apoya en {@link ConversionDeValores}, que es exactamente la mitad
     * que <b>no depende del sujeto</b>: un entero es un entero y un vocabulario
     * es un vocabulario, lo lleve una fila o una columna. Asi el camino
     * estructural y el gobernado exigen lo mismo, que es lo unico que hace
     * cierta la promesa de D-E4-3 — <i>la autoridad fisica cambia, el contrato
     * logico no</i>.
     */
    private static String valorEstructural(CatalogoAtributo definicion, String valor) {
        String limpio = exigirValor(definicion.getClave(), valor);
        ConversionDeValores.exigirCompatible(definicion, limpio);
        return ConversionDeValores.exigirDelVocabulario(definicion, limpio);
    }

    /**
     * Cambia el valor de un atributo que ya existe, respetando su tipo. Se
     * separa de {@link #convertir} porque actualizar y crear no son lo mismo
     * para JPA: reemplazar la fila perderia {@code fecha_creacion}, que es el
     * dato que dice desde cuando se sabe eso de la propiedad.
     */
    public void actualizar(long idOrganizacion, AtributoPropiedad existente, String valor,
                           String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, existente.getClave());
        String clave = existente.getClave();
        String limpio = valor == null ? null : valor.trim();
        if (limpio == null || limpio.isEmpty()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" llego sin valor. Para quitarlo, borralo; "
                            + "no se guarda vacio.");
        }
        switch (definicion.tipo()) {
            case ENTERO ->
                    existente.cambiarANumero(enRango(definicion, entero(clave, limpio)));
            case DECIMAL ->
                    existente.cambiarANumero(enRango(definicion, decimal(clave, limpio)));
            case IMPORTE -> existente.cambiarAImporte(
                    enRango(definicion, decimal(clave, limpio)), exigirMoneda(clave, moneda));
            case BOOLEANO -> existente.cambiarABooleano(booleano(clave, limpio));
            case FECHA -> existente.cambiarAFecha(fecha(clave, limpio));
            case TEXTO, LISTA -> existente.cambiarATexto(enLongitud(definicion, limpio));
            case LISTA_MULTIPLE -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" admite varios valores: se edita con la via de "
                            + "multivalor, no con un valor suelto.");
        }
    }

    /**
     * <b>Escribe un valor donde su AUTORIDAD diga</b> (D-E4-3, paso 4).
     *
     * <p>Este metodo es el enrutador, y es lo que sustituye al
     * {@code if ("metraje_total".equals(clave))} que vivia en el caso de uso:
     *
     * <pre>
     *   CampoCaptura
     *     +- destino = ATRIBUTO     -> AtributoPropiedad
     *     +- destino = ESTRUCTURAL  -> campoEstructural -> EscritorEstructural
     * </pre>
     *
     * <p>Los dos caminos son <b>mutuamente excluyentes</b>: un valor no se
     * escribe nunca en los dos sitios. Escribirlo en ambos era exactamente la
     * doble verdad que D-E4-3 vino a cerrar.
     *
     * @return el atributo a guardar, o {@code empty()} si el valor era
     *         estructural y ya se aplico sobre la propiedad
     */
    public Optional<AtributoPropiedad> enrutar(long idOrganizacion, Propiedad propiedad,
                                               String clave, String valor, String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, propiedad.getTipoInmueble());

        if (definicion.esEstructural()) {
            EscritorEstructural.aplicar(propiedad, definicion.getCampoEstructural(),
                    valorEstructural(definicion, valor), clave);
            // Y NO se guarda como atributo: su autoridad es el campo canonico.
            return Optional.empty();
        }
        return Optional.of(convertir(idOrganizacion, propiedad.getId(),
                propiedad.getTipoInmueble(), clave, valor, moneda));
    }

    /**
     * Lo mismo para una edicion: si es estructural aplica sobre la propiedad;
     * si es atributo, actualiza el existente o crea el que falte.
     *
     * <p>Va aqui y no en el caso de uso por la misma razon: <b>alta y edicion
     * tienen que enrutar igual</b>. Arreglar solo el alta deja la fuga abierta
     * en cuanto alguien modifique una propiedad, que es la operacion mas
     * frecuente de las dos.
     */
    public Optional<AtributoPropiedad> enrutarEdicion(long idOrganizacion, Propiedad propiedad,
                                                      String clave, String valor,
                                                      AtributoPropiedad existente, String moneda) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, propiedad.getTipoInmueble());

        if (definicion.esEstructural()) {
            EscritorEstructural.aplicar(propiedad, definicion.getCampoEstructural(),
                    valorEstructural(definicion, valor), clave);
            return Optional.empty();
        }
        if (existente != null) {
            actualizar(idOrganizacion, existente, valor, moneda);
            return Optional.of(existente);
        }
        return Optional.of(convertir(idOrganizacion, propiedad.getId(),
                propiedad.getTipoInmueble(), clave, valor, moneda));
    }

    /**
     * <b>Retira el valor de una clave logica, enrutando por su autoridad.</b>
     *
     * <p>Quien llama dice «quiero quitar el piso» y nada mas. No sabe —ni tiene
     * por que saber— si esa clave se guarda hoy como fila de
     * {@code atributo_propiedad} o como columna del agregado, ni si manana
     * cambia de sitio. La regla del trazado se completa aqui:
     *
     * <pre>
     *   clave  →  autoridad  →  leer / escribir / <b>borrar</b>
     * </pre>
     *
     * <p>Es distinto de mandar el valor en blanco, y a proposito: en blanco es
     * un valor que llego mal, y retirar es una <b>intencion declarada</b>. Este
     * corte no le da a {@code ""} ningun significado nuevo.
     *
     * @return {@code false} si la clave no esta en el catalogo, para que el
     *         llamante pueda probar el otro espacio de nombres antes de
     *         rechazarla. Lanza si esta y su autoridad no admite quedarse vacia
     */
    public boolean retirar(long idOrganizacion, Propiedad propiedad, String clave) {
        Optional<CatalogoAtributo> definicion = catalogo.porClave(idOrganizacion, clave);
        if (definicion.isEmpty()) {
            return false;
        }
        // Borrar enruta por sujeto igual que leer y escribir. Sin esto, pedir
        // que se retire una condicion del encargo saldria como "no esta en el
        // catalogo" -- un mensaje falso sobre una clave que existe.
        exigirQueSeaDePropiedad(definicion.get());
        if (definicion.get().esEstructural()) {
            EscritorEstructural.vaciar(propiedad, definicion.get().getCampoEstructural(), clave);
            return true;
        }
        valores.deleteByIdPropiedadAndClave(propiedad.getId(), clave);
        return true;
    }

    /** La definicion de una clave: la de la organizacion gana sobre la del sistema. */
    public CatalogoAtributo definicionDe(long idOrganizacion, String clave) {
        CatalogoAtributo definicion = catalogo.porClave(idOrganizacion, clave)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El atributo \"" + clave + "\" no esta en el catalogo. Una clave existe "
                                + "antes que su valor: si no, dos propiedades dicen lo mismo con "
                                + "nombres distintos y dejan de poder compararse."));
        exigirQueSeaDePropiedad(definicion);
        return definicion;
    }

    /**
     * La mitad Java de la regla que el trigger {@code tg_atributo_gobernado}
     * garantiza en la base desde V73.
     *
     * <p>Es la direccion contraria de la que vigila {@link AtributosDeEncargo},
     * y ninguna es simetrica de la otra. Guardar una condicion negociada como
     * hecho del inmueble no falla: <b>miente</b>, y ademas la pierde -- porque
     * `uq_atributo_propiedad_clave` deja un valor por propiedad, asi que el
     * segundo encargo pisa al primero sin dejar rastro.
     */
    private static void exigirQueSeaDePropiedad(CatalogoAtributo definicion) {
        if (definicion.esDeEncargo()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" es una condicion del ENCARGO "
                            + "y se intento tratar como un hecho de la propiedad. Se pacta en "
                            + "cada comercializacion: guardarlo en el inmueble haria que el "
                            + "siguiente encargo heredara lo pactado en el anterior.");
        }
    }

    /** Las definiciones de un lote de claves, por clave. Evita el N+1 de la ficha. */
    public Map<String, CatalogoAtributo> definicionesDe(long idOrganizacion, String tipoPropiedad) {
        Map<String, CatalogoAtributo> porClave = new LinkedHashMap<>();
        for (CatalogoAtributo atributo : aplicablesA(idOrganizacion, tipoPropiedad)) {
            porClave.putIfAbsent(atributo.getClave(), atributo);
        }
        return porClave;
    }

    /** El valor de un atributo tal como se muestra: sin ceros de mas ni "true". */
    public static String comoTexto(AtributoPropiedad atributo) {
        if (atributo.getValorTexto() != null) {
            return atributo.getValorTexto();
        }
        if (atributo.getValorNumero() != null) {
            return atributo.getValorNumero().stripTrailingZeros().toPlainString();
        }
        return atributo.getValorBooleano() == null ? null : atributo.getValorBooleano().toString();
    }

    /**
     * Que claves de las obligatorias no estan en lo que se conoce. El motor de
     * captura lo usa <b>antes</b> de que exista la propiedad, cuando todavia no
     * hay nada que consultar en la base.
     */
    public List<String> faltantesEntre(long idOrganizacion, String tipoPropiedad,
                                       Iterable<String> clavesConocidas) {
        List<String> conocidas = new ArrayList<>();
        clavesConocidas.forEach(conocidas::add);
        return obligatoriasDe(idOrganizacion, tipoPropiedad).stream()
                .filter(clave -> !conocidas.contains(clave))
                .toList();
    }

    // ------------------------------------------------------------------

    /**
     * El rango que declara el catalogo, comprobado <b>aqui</b> y no solo en el
     * trigger.
     *
     * <p>El trigger lo rechazaria igualmente, pero con un mensaje de PostgreSQL
     * a mitad de una transaccion, y eso no se le puede ensenar a nadie: la base
     * es la garantia, esto es el mensaje.
     */
    private static BigDecimal enRango(CatalogoAtributo definicion, BigDecimal valor) {
        return ConversionDeValores.enRango(definicion, valor);
    }

    private static BigDecimal entero(String clave, String valor) {
        return ConversionDeValores.entero(clave, valor);
    }

    private static BigDecimal decimal(String clave, String valor) {
        return ConversionDeValores.decimal(clave, valor);
    }

    private static Boolean booleano(String clave, String valor) {
        return ConversionDeValores.booleano(clave, valor);
    }

    /** El nombre del tipo, para que el error no diga "tipo T". */
    public static String nombreDelTipo(String tipoPropiedad) {
        return switch (tipoPropiedad) {
            case "L" -> "LOCAL";
            case "O" -> "OFICINA";
            case "D" -> "DEPARTAMENTO";
            case "C" -> "CASA";
            case "T" -> "TERRENO";
            case "A" -> "ALMACEN";
            case "X" -> "OTRO";
            default -> tipoPropiedad;
        };
    }

    /** El codigo de un tipo escrito con palabras. Acepta las dos formas. */
    /**
     * <b>Como se llama un tipo cuando lo lee una persona.</b>
     *
     * <p>Va aparte de {@link #nombreDelTipo}: aquel es el nombre del VALOR
     * —{@code LOCAL}, lo que viaja por el cable y lo que el cliente devuelve al
     * responder— y esto es el ROTULO, que lleva acentos y minusculas porque se
     * pinta en una tabla.
     *
     * <p>Existe para que el cliente no traduzca. Un {@code switch} en Angular
     * que convierta {@code "L"} en «Local comercial» seria la matriz «tipo →
     * texto» viviendo en la interfaz, y con dos interfaces habria dos (D-A-1).
     */
    public static String rotuloDelTipo(String tipoPropiedad) {
        return switch (tipoPropiedad == null ? "" : tipoPropiedad) {
            case "L" -> "Local comercial";
            case "O" -> "Oficina";
            case "D" -> "Departamento";
            case "C" -> "Casa";
            case "T" -> "Terreno";
            case "A" -> "Almacén";
            case "X" -> "Otro";
            default -> tipoPropiedad;
        };
    }

    /**
     * <b>Como se llama el uso cuando lo lee una persona.</b>
     *
     * <p>Gemelo de {@link #rotuloDelTipo} y por el mismo motivo: el uso viaja
     * como una letra --{@code "C"}, {@code "V"}-- y una ficha que quiera
     * escribir «Comercial» tendria que traducirla. El catalogo de usos ademas
     * crecio con el modelo universal --antes solo existia el comercial, porque
     * solo se alquilaban locales--, asi que la tabla que el SPA heredo esta
     * incompleta: convierte {@code C} y deja pasar {@code V}, {@code I} y
     * {@code M} en crudo.
     */
    public static String rotuloDelUso(String uso) {
        return switch (uso == null ? "" : uso) {
            case "C" -> "Comercial";
            case "V" -> "Vivienda";
            case "I" -> "Industrial";
            case "M" -> "Mixto";
            default -> uso;
        };
    }
    public static Optional<String> codigoDelTipo(String tipoPropiedad) {
        if (tipoPropiedad == null || tipoPropiedad.isBlank()) {
            return Optional.empty();
        }
        String limpio = tipoPropiedad.trim().toUpperCase(Locale.ROOT);
        return switch (limpio) {
            case "L", "LOCAL", "LOCAL_COMERCIAL" -> Optional.of("L");
            case "O", "OFICINA" -> Optional.of("O");
            case "D", "DEPARTAMENTO", "DEPTO" -> Optional.of("D");
            case "C", "CASA" -> Optional.of("C");
            case "T", "TERRENO" -> Optional.of("T");
            case "A", "ALMACEN", "ALMACÉN", "DEPOSITO", "DEPÓSITO" -> Optional.of("A");
            case "X", "OTRO" -> Optional.of("X");
            default -> Optional.empty();
        };
    }
}
