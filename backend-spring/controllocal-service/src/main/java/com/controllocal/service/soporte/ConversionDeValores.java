package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * <b>Un valor encaja con su tipo de dato, y eso no depende de quien sea</b> (V73).
 *
 * <h2>Por que se saca aqui en el Corte 0C</h2>
 * Con dos sujetos hay dos enrutadores —{@link AtributosGobernados} para la
 * Propiedad y {@link AtributosDeEncargo} para el Encargo—, y cada uno tiene que
 * convertir texto en valor tipado. Copiar la conversion en el segundo habria
 * creado dos definiciones de «que es un entero» y «que monedas existen», y
 * habrian divergido en el primer arreglo que se hiciera en una sola.
 *
 * <p>La frontera es exacta: <b>aqui vive lo que depende del TIPO DE DATO</b>
 * —convertir, acotar, exigir moneda— y <b>fuera queda lo que depende del
 * SUJETO</b> —a que aplica, donde se guarda, quien lo borra—. Nada de este
 * archivo sabe que existen las propiedades ni los encargos, y por eso puede
 * servir a los dos sin ramificar.
 *
 * <p>Todo lo que rechaza lo rechazaria igualmente el trigger de PostgreSQL.
 * La diferencia es el mensaje: aqui sale con el nombre del atributo delante y
 * <b>sin llegar a intentar la escritura</b>; alli sale en ingles, a mitad de un
 * {@code INSERT}, y no se le puede ensenar a nadie.
 *
 * <p><b>Esta frase decia «antes de abrir la transaccion» y era falsa</b>, medido
 * el 2026-08-26. Todos los llamadores de esta clase estan dentro de un metodo ya
 * transaccional: {@code AtributosGobernados} y {@code AtributosDeEncargo} cuelgan
 * de {@code PropiedadUniversalServiceImpl.registrar} y {@code editar}, y
 * {@code MotorDeCapturaImpl} llama desde {@code avanzar} — las tres
 * {@code @Transactional}. Lo que esta capa se ahorra no es la transaccion: es el
 * {@code INSERT} —y con el, un mensaje que el agente no podria leer—.
 */
public final class ConversionDeValores {

    /** El mismo vocabulario que las once columnas de moneda del esquema. */
    public static final List<String> MONEDAS = List.of("PEN", "USD");

    private ConversionDeValores() {
    }

    /**
     * Un atributo sin responder se OMITE, no se guarda vacio.
     *
     * <p>La distincion no es formal: el matcher tiene que poder separar «no
     * aplica» de «no lo se», y una cadena vacia guardada las funde en la misma
     * cosa. Es la misma regla que sostiene {@code atributosABorrar}: retirar es
     * una intencion declarada, no un valor en blanco.
     */
    public static String exigirValor(String clave, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" llego sin valor. Un atributo sin responder se "
                            + "omite, no se guarda vacio: el matcher tiene que poder distinguir "
                            + "\"no aplica\" de \"no lo se\".");
        }
        return valor.trim();
    }

    public static BigDecimal entero(String clave, String valor) {
        BigDecimal numero = decimal(clave, valor);
        if (numero.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es un numero entero y llego \"" + valor + "\".");
        }
        return numero;
    }

    public static BigDecimal decimal(String clave, String valor) {
        try {
            return new BigDecimal(valor.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es numerico y llego \"" + valor + "\".");
        }
    }

    public static Boolean booleano(String clave, String valor) {
        String normalizado = valor.toLowerCase(Locale.ROOT);
        return switch (normalizado) {
            case "true", "si", "sí", "1", "s", "y", "yes" -> Boolean.TRUE;
            case "false", "no", "0", "n" -> Boolean.FALSE;
            default -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es de si/no y llego \"" + valor + "\".");
        };
    }

    /**
     * La fecha, en ISO y nada mas.
     *
     * <p>Un solo formato a proposito: admitir {@code 03/04/2026} obligaria a
     * decidir si es marzo o abril, y esa decision no la puede tomar el Core sin
     * saber de donde vino el texto. El cliente formatea; el contrato no adivina.
     */
    public static LocalDate fecha(String clave, String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (RuntimeException e) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es una fecha y llego \"" + valor
                            + "\". Se escribe como 2026-08-21.");
        }
    }

    /**
     * La moneda de un importe. Se exige y no se deduce: un monto con la moneda
     * equivocada no falla, <b>miente</b>, y lo hace en el unico sitio donde el
     * error cuesta dinero.
     */
    public static String exigirMoneda(String clave, String moneda) {
        if (moneda == null || moneda.isBlank()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es un importe y llego sin moneda. Un numero sin "
                            + "moneda no es dinero.");
        }
        String limpia = moneda.trim().toUpperCase(Locale.ROOT);
        if (!MONEDAS.contains(limpia)) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" llego con la moneda \"" + moneda
                            + "\", y son " + String.join(" o ", MONEDAS) + ".");
        }
        return limpia;
    }

    /**
     * El rango que declara el catalogo, <b>las dos puntas</b>.
     *
     * <p>Los limites salen de {@code catalogo_atributo}, no de constantes:
     * escribir aqui «ambientes >= 1» seria devolver la regla al codigo el mismo
     * dia que se le dio dueno (D-E4-3), y la clave la puede anadir un tenant.
     */
    /**
     * <b>Solo comprueba que el VALOR encaja con su tipo de dato</b>, sin mirar a
     * que aplica ni de quien es.
     *
     * <p>Existe por el motor de captura: alguien puede dictar «tres dormitorios»
     * o «dos meses de garantia» <b>antes</b> de decir de que tipo es la
     * propiedad. Ahi no se puede comprobar la aplicabilidad, y rechazarlo con
     * «no aplica a una propiedad de tipo OTRO» seria un mensaje falso sobre un
     * dato correcto. La aplicabilidad se mira en cuanto el tipo se conoce, y
     * otra vez al guardar.
     *
     * <p>Vive aqui, y no en un enrutador, porque no depende del sujeto: la
     * pregunta «¿esto es un entero?» tiene la misma respuesta la lleve una
     * propiedad o un encargo.
     */
    public static void exigirCompatible(CatalogoAtributo definicion, String valor) {
        String clave = definicion.getClave();
        String limpio = exigirValor(clave, valor);
        // Exhaustivo sobre el enum y SIN `default`: hasta 0B esto tenia un
        // `default -> { }` que no validaba nada, asi que el motor aceptaba un
        // valor que el trigger rechazaba a mitad de la transaccion. Ahora un
        // noveno tipo de dato no compila hasta que se diga que hacer con el.
        switch (definicion.tipo()) {
            case ENTERO -> enRango(definicion, entero(clave, limpio));
            case DECIMAL, IMPORTE -> enRango(definicion, decimal(clave, limpio));
            case BOOLEANO -> booleano(clave, limpio);
            case FECHA -> fecha(clave, limpio);
            case TEXTO, LISTA -> enLongitud(definicion, limpio);
            // Aqui no se conoce todavia el tipo de propiedad, asi que tampoco
            // se puede consultar el vocabulario: un valor de multivalor se
            // comprueba al guardarlo, contra el catalogo de opciones.
            case LISTA_MULTIPLE -> { }
        }
    }

    public static BigDecimal enRango(CatalogoAtributo definicion, BigDecimal valor) {
        BigDecimal minimo = definicion.getValorMinimo();
        if (minimo != null && valor.compareTo(minimo) < 0) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" no puede ser menor que "
                            + minimo.stripTrailingZeros().toPlainString() + " y llego "
                            + valor.stripTrailingZeros().toPlainString() + ".");
        }
        BigDecimal maximo = definicion.getValorMaximo();
        if (maximo != null && valor.compareTo(maximo) > 0) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" no puede ser mayor que "
                            + maximo.stripTrailingZeros().toPlainString() + " y llego "
                            + valor.stripTrailingZeros().toPlainString() + ".");
        }
        return valor;
    }

    /**
     * <b>Un valor de LISTA pertenece al vocabulario que declara su clave</b>
     * (V79).
     *
     * <h2>Por que hizo falta, y por que solo lo llama el camino estructural</h2>
     * La comprobacion de pertenencia existia en un solo sitio: dentro de
     * {@code exigir_atributo_gobernado}, el trigger de {@code atributo_propiedad}
     * (V72). Un valor cuya autoridad es un campo canonico <b>no pasa por esa
     * tabla</b>, asi que {@code oficina_registral} —la primera LISTA
     * ESTRUCTURAL— habria aceptado cualquier cadena. Esta capa no lo comprobaba:
     * acota tipo, rango y longitud, y nunca pertenencia.
     *
     * <p><b>No hay vocabulario escrito aqui.</b> Las opciones salen de
     * {@code catalogo_atributo_opcion} a traves de
     * {@link CatalogoAtributo#opcionesVigentes()}, que es la unica autoridad.
     * Un {@code Set.of("LIMA", "CALLAO", ...)} en este archivo seria la segunda
     * lista de oficinas, y anadir Ica pasaria a ser un despliegue.
     *
     * <p><b>Sin vocabulario sembrado no comprueba nada</b>, y es la misma
     * tolerancia que V72 dejo puesta en el trigger: una LISTA sin una sola
     * opcion admite cualquier cadena, en vez de rechazarlas todas. Esa
     * tolerancia sigue vigente y es lo que este parrafo documenta.
     *
     * <p>El ejemplo que ilustraba la frase ya <b>no</b> vale, y decirlo importa
     * porque afirmaba lo contrario de lo que hoy pasa. Era
     * {@code servicios_disponibles} —LISTA de la PROPIEDAD sin una sola opcion—
     * «que sigue comportandose exactamente igual que antes de este corte, y
     * cuyos reemplazos son del Corte 5». Las dos mitades caducaron con
     * {@code V84}: los reemplazos —{@code agua_desague} y
     * {@code energia_electrica}, con vocabulario sembrado— <b>ya existen</b>, y
     * la clave quedo <b>retirada</b> ({@code activo = false}). Su valor
     * conservado se sigue LEYENDO; lo que se cerro es la escritura.
     *
     * <p><b>Y no era esta capa la que la paraba, ni antes ni ahora</b> —la
     * primera version de esta correccion lo atribuyo a la retirada, y era falso
     * por dos medidas—. El orden real de las capas es:
     *
     * <ol>
     *   <li>A este metodo <b>solo lo llama el camino ESTRUCTURAL</b>
     *       ({@code AtributosGobernados.valorEstructural}, su unico
     *       llamador), y {@code servicios_disponibles} es
     *       {@code destino = ATRIBUTO} con {@code campo_estructural = NULL}.
     *       Una escritura suya <b>nunca</b> llego hasta aqui, ni antes ni
     *       despues de {@code V84}: la retirada no tiene nada que ver.</li>
     *   <li>En el camino <b>gobernado</b> quien la rechaza es
     *       {@code AtributosGobernados.definicionDe} →
     *       {@code CatalogoAtributoRepository.porClave}, cuyo JPQL lleva
     *       {@code and c.activo = true}: sale una {@code ReglaNegocioException}
     *       («no esta en el catalogo») <b>en Java, sin llegar a intentar la
     *       escritura</b>. Lo unico que se emite es el SELECT del catalogo, y eso
     *       se midio: el 2026-08-26, un {@code PUT /propiedades/1} con la clave
     *       retirada movio {@code catalogo_atributo} en
     *       {@code pg_stat_user_tables} —{@code seq_scan} 5316 → 5317 y
     *       {@code seq_tup_read} +123, la tabla entera— y dejo
     *       {@code atributo_propiedad} igual (950/243/71179 antes y despues).
     *       Al cliente le llega <b>400</b>. La version anterior de esta linea
     *       decia «antes de emitir SQL» y era falsa: no hay cache de segundo
     *       nivel que evite la consulta (0 apariciones de {@code Cacheable} o
     *       {@code use_second_level_cache} en {@code backend-spring}, barrido con
     *       control positivo).</li>
     *   <li>{@code exigir_atributo_gobernado} es la <b>red de atras</b>, para
     *       quien entre por SQL directo: busca la clave con {@code activo = true}
     *       y si no la encuentra levanta SQLSTATE 23503. Por eso el
     *       {@code sembrarLegadoAmbiguo} de {@code OcupacionYServiciosIntegrationTest}
     *       tiene que <b>reactivar</b> la clave para poder saltarselo.</li>
     * </ol>
     *
     * <p>Y hoy <b>ninguna clave la ejerce</b>: medido el 2026-08-25 en las dos
     * bases, no queda una sola LISTA ni LISTA_MULTIPLE activa sin vocabulario,
     * en ninguno de los dos sujetos ni de los dos ambitos. La tolerancia se
     * conserva porque el catalogo es dato y una clave nueva puede nacer muda
     * entre migraciones; lo que la vigila es el gate («5A ninguna LISTA activa
     * se quedo sin vocabulario»), no este metodo.
     *
     * <p>Lo que rechaza lo rechazaria igualmente {@code tg_vocabulario_estructural}.
     * La diferencia es la de siempre: aqui sale con el nombre del atributo
     * delante y <b>sin llegar a tocar la fila</b> —{@code valorEstructural} se
     * evalua como argumento de {@code EscritorEstructural.aplicar}, asi que la
     * entidad ni se muta—.
     *
     * <p><b>Esta linea decia «antes de abrir la transaccion» y era falso</b>,
     * medido el 2026-08-26. Los dos unicos caminos que llegan hasta aqui
     * —{@code AtributosGobernados.aplicarEstructuralesAlAlta} y
     * {@code escribirEnEdicion}— cuelgan de
     * {@code PropiedadUniversalServiceImpl.registrar} y de su {@code editar}, las
     * dos anotadas {@code @Transactional}. Cuando esta comprobacion corre, la
     * transaccion lleva abierta desde el borde del servicio.
     */
    public static String exigirDelVocabulario(CatalogoAtributo definicion, String valor) {
        List<String> admitidos = definicion.opcionesVigentes().stream()
                .map(opcion -> opcion.getValor())
                .toList();
        if (admitidos.isEmpty() || admitidos.contains(valor)) {
            return valor;
        }
        throw new ReglaNegocioException(
                "El atributo \"" + definicion.getClave() + "\" no admite el valor \"" + valor
                        + "\". Los valores posibles son " + String.join(", ", admitidos)
                        + ", y salen del catalogo.");
    }

    /**
     * El techo de longitud que declara la clave.
     *
     * <p>Es la garantia que se perdio en V71 al retirar el {@code VARCHAR(120)}
     * del rubro: {@code valor_texto} es TEXT y no acota nada. Ahora lo acota
     * quien sabe cuanto mide cada concepto, que es el catalogo.
     */
    public static String enLongitud(CatalogoAtributo definicion, String valor) {
        Integer techo = definicion.getLongitudMaxima();
        if (techo != null && valor.length() > techo) {
            throw new ReglaNegocioException(
                    "El atributo \"" + definicion.getClave() + "\" admite " + techo
                            + " caracteres y llegaron " + valor.length() + ".");
        }
        return valor;
    }
}
