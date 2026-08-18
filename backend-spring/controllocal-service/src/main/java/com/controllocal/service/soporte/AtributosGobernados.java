package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.AtributoPropiedad;
import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.persistence.repositorio.AtributoPropiedadRepository;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
     * Comprueba que la clave existe, que aplica a ese tipo y que el valor
     * encaja con su tipo de dato. Devuelve el atributo listo para guardar.
     *
     * <p>Se hace en este orden a proposito: "esa clave no existe" y "esa clave
     * no aplica a un terreno" son errores distintos y se arreglan distinto.
     */
    public AtributoPropiedad convertir(long idOrganizacion, long idPropiedad, String tipoPropiedad,
                                       String clave, String valor) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, tipoPropiedad);
        String limpio = exigirValor(clave, valor);

        return switch (definicion.getTipoDato()) {
            case CatalogoAtributo.ENTERO -> AtributoPropiedad.deNumero(
                    idOrganizacion, idPropiedad, clave, entero(clave, limpio));
            case CatalogoAtributo.DECIMAL -> AtributoPropiedad.deNumero(
                    idOrganizacion, idPropiedad, clave, decimal(clave, limpio));
            case CatalogoAtributo.BOOLEANO -> AtributoPropiedad.deBooleano(
                    idOrganizacion, idPropiedad, clave, booleano(clave, limpio));
            default -> AtributoPropiedad.deTexto(idOrganizacion, idPropiedad, clave, limpio);
        };
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
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        String limpio = exigirValor(clave, valor);
        switch (definicion.getTipoDato()) {
            case CatalogoAtributo.ENTERO -> entero(clave, limpio);
            case CatalogoAtributo.DECIMAL -> decimal(clave, limpio);
            case CatalogoAtributo.BOOLEANO -> booleano(clave, limpio);
            default -> { }
        }
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
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" llego sin valor. Un atributo sin responder se "
                            + "omite, no se guarda vacio: el matcher tiene que poder distinguir "
                            + "\"no aplica\" de \"no lo se\".");
        }
        return valor.trim();
    }

    /**
     * Cambia el valor de un atributo que ya existe, respetando su tipo. Se
     * separa de {@link #convertir} porque actualizar y crear no son lo mismo
     * para JPA: reemplazar la fila perderia {@code fecha_creacion}, que es el
     * dato que dice desde cuando se sabe eso de la propiedad.
     */
    public void actualizar(long idOrganizacion, AtributoPropiedad existente, String valor) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, existente.getClave());
        String clave = existente.getClave();
        String limpio = valor == null ? null : valor.trim();
        if (limpio == null || limpio.isEmpty()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" llego sin valor. Para quitarlo, borralo; "
                            + "no se guarda vacio.");
        }
        switch (definicion.getTipoDato()) {
            case CatalogoAtributo.ENTERO -> existente.cambiarANumero(entero(clave, limpio));
            case CatalogoAtributo.DECIMAL -> existente.cambiarANumero(decimal(clave, limpio));
            case CatalogoAtributo.BOOLEANO -> existente.cambiarABooleano(booleano(clave, limpio));
            default -> existente.cambiarATexto(limpio);
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
                                               String clave, String valor) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, propiedad.getTipoInmueble());

        if (definicion.esEstructural()) {
            EscritorEstructural.aplicar(propiedad, definicion.getCampoEstructural(),
                    exigirValor(clave, valor), clave);
            // Y NO se guarda como atributo: su autoridad es el campo canonico.
            return Optional.empty();
        }
        return Optional.of(convertir(idOrganizacion, propiedad.getId(),
                propiedad.getTipoInmueble(), clave, valor));
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
                                                      AtributoPropiedad existente) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        exigirQueAplique(definicion, propiedad.getTipoInmueble());

        if (definicion.esEstructural()) {
            EscritorEstructural.aplicar(propiedad, definicion.getCampoEstructural(),
                    exigirValor(clave, valor), clave);
            return Optional.empty();
        }
        if (existente != null) {
            actualizar(idOrganizacion, existente, valor);
            return Optional.of(existente);
        }
        return Optional.of(convertir(idOrganizacion, propiedad.getId(),
                propiedad.getTipoInmueble(), clave, valor));
    }

    /**
     * <b>Fija el valor de una clave, incluido el caso de que llegue vacio.</b>
     *
     * <p>Existe por los endpoints que mandan el objeto ENTERO — {@code PUT
     * /locales} es el caso — donde un campo ausente no significa "no lo toques"
     * sino <b>"ya no lo se"</b>. Esa semantica no la puede inventar el caso de
     * uso, porque borrar un valor es distinto segun su autoridad: en un atributo
     * gobernado se retira la fila, en un campo canonico habria que definir que
     * significa vaciarlo, y hoy no esta definido para ninguno.
     *
     * <p>Sin esto, migrar los lectores de {@code /locales} sin migrar su
     * escritor dejaria cada PUT escribiendo en una columna que ya nadie lee:
     * el mismo fallo que se acaba de arreglar, con el espejo puesto.
     */
    public void fijar(long idOrganizacion, Propiedad propiedad, String clave, String valor) {
        CatalogoAtributo definicion = definicionDe(idOrganizacion, clave);
        if (valor != null && !valor.isBlank()) {
            AtributoPropiedad existente = definicion.esEstructural() ? null
                    : valores.findByIdPropiedadAndClave(propiedad.getId(), clave).orElse(null);
            enrutarEdicion(idOrganizacion, propiedad, clave, valor, existente)
                    .ifPresent(valores::save);
            return;
        }
        if (definicion.esEstructural()) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es estructural y no se puede dejar en blanco por "
                            + "esta via: vaciar un campo canonico es una operacion del agregado, y "
                            + "todavia no esta definida para \"" + definicion.getCampoEstructural() + "\".");
        }
        valores.deleteByIdPropiedadAndClave(propiedad.getId(), clave);
    }

    /** La definicion de una clave: la de la organizacion gana sobre la del sistema. */
    public CatalogoAtributo definicionDe(long idOrganizacion, String clave) {
        return catalogo.porClave(idOrganizacion, clave)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El atributo \"" + clave + "\" no esta en el catalogo. Una clave existe "
                                + "antes que su valor: si no, dos propiedades dicen lo mismo con "
                                + "nombres distintos y dejan de poder compararse."));
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

    private static BigDecimal entero(String clave, String valor) {
        BigDecimal numero = decimal(clave, valor);
        if (numero.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es un numero entero y llego \"" + valor + "\".");
        }
        return numero;
    }

    private static BigDecimal decimal(String clave, String valor) {
        try {
            return new BigDecimal(valor.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es numerico y llego \"" + valor + "\".");
        }
    }

    private static Boolean booleano(String clave, String valor) {
        String normalizado = valor.toLowerCase(Locale.ROOT);
        return switch (normalizado) {
            case "true", "si", "sí", "1", "s", "y", "yes" -> Boolean.TRUE;
            case "false", "no", "0", "n" -> Boolean.FALSE;
            default -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es de si/no y llego \"" + valor + "\".");
        };
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
