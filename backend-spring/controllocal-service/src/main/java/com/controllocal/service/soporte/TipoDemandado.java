package com.controllocal.service.soporte;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * <b>El tipo de inmueble que pide un cliente, en el vocabulario de la
 * propiedad</b> (WP-B, D-M-2).
 *
 * <h2>El defecto que cierra</h2>
 * La demanda y la oferta hablaban idiomas distintos:
 *
 * <pre>
 *   requerimiento   LOCAL_COMERCIAL  OFICINA  DEPOSITO_ALMACEN  STAND_MODULO  TERRENO_COMERCIAL  OTRO
 *   propiedad       L  O  D  C  T  A  E  X
 * </pre>
 *
 * y el traductor del matcher —{@code mapTipo}— resolvia <b>tres</b> de los seis
 * y devolvia {@code null} para los demas, con lo que el criterio se caia de los
 * aplicables. Un cliente que buscaba un {@code DEPOSITO_ALMACEN} puntuaba
 * exactamente igual contra un almacen que contra una casa: ALMACEN y
 * {@code DEPOSITO_ALMACEN} son el mismo concepto y estaban declarados no
 * comparables.
 *
 * <h2>Por que vive aqui y no en {@code AtributosGobernados}</h2>
 * Alli esta el vocabulario de la <b>propiedad</b> —los ocho codigos y los
 * nombres con los que se escriben—, y esta clase no lo repite: se lo pregunta.
 * Lo que anade son los <b>alias de la demanda</b>, que son historia de la
 * demanda y de nadie mas: {@code DEPOSITO_ALMACEN}, {@code TERRENO_COMERCIAL} y
 * {@code STAND_MODULO} nunca fueron tipos de propiedad.
 *
 * <p>Es la misma traduccion que la migración universal aplicó a las filas ya escritas. Las
 * dos existen a proposito y no se pueden fundir —una traduce una peticion HTTP,
 * la otra tradujo un {@code UPDATE} una sola vez—, asi que <b>la tabla se
 * escribe en las dos y se comprueba en el mismo test de integracion</b>: si una
 * cambia sin la otra, la traduccion del alta deja de coincidir con la del
 * historico.
 */
public final class TipoDemandado {

    private TipoDemandado() {
    }

    /**
     * Los ocho codigos que admite {@code requerimiento_tipo_inmueble}, que son
     * los mismos de {@code propiedad.tipo_inmueble}.
     */
    public static final Set<String> CODIGOS = AtributosGobernados.nombresDeTipos().stream()
            .map(tipo -> AtributosGobernados.codigoDelTipo(tipo).orElseThrow())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * El codigo de un tipo pedido, venga como codigo, como nombre de propiedad
     * o como nombre del enum viejo de la demanda.
     *
     * <p>La traduccion del legado, con su porque:
     * <pre>
     *   LOCAL_COMERCIAL    -&gt; L   mismo concepto
     *   OFICINA            -&gt; O   mismo concepto
     *   DEPOSITO_ALMACEN   -&gt; A   mismo concepto, distinto nombre. ESTE estaba roto
     *   TERRENO_COMERCIAL  -&gt; T   el "comercial" describia el uso, no el tipo
     *   OTRO               -&gt; X   mismo concepto
     *   STAND_MODULO       -&gt; X   un stand no es ninguno de los ocho tipos
     * </pre>
     *
     * <p><b>{@code STAND_MODULO} cae en {@code X} y no estrena otro tipo.</b>
     * Anadir uno tocaria el CHECK de {@code propiedad}, el catalogo
     * de atributos, la aplicabilidad de las claves y el motor de captura —y
     * un stand de galeria se describe con lo mismo que un local—. Lo que se
     * pierde con la traduccion es la palabra «stand», y por eso
     * {@code requerimiento_cliente.tipo_inmueble} se conserva como linaje: sin
     * ella, un {@code STAND_MODULO} y un {@code OTRO} quedarian
     * indistinguibles.
     *
     * @return {@code empty()} si el texto no nombra ningun tipo conocido. No es
     *         un error de esta clase decidir que hacer con eso
     */
    public static Optional<String> codigo(String pedido) {
        if (pedido == null || pedido.isBlank()) {
            return Optional.empty();
        }
        String limpio = pedido.trim().toUpperCase(Locale.ROOT);
        return switch (limpio) {
            case "DEPOSITO_ALMACEN", "DEPÓSITO_ALMACEN" -> Optional.of("A");
            case "TERRENO_COMERCIAL" -> Optional.of("T");
            case "STAND_MODULO", "STAND_MÓDULO" -> Optional.of("X");
            // Todo lo demas es vocabulario de la PROPIEDAD y lo resuelve su
            // duena. Repetirlo aqui seria la segunda lista de tipos que
            // `Propiedad` retiro a proposito en V71.
            default -> AtributosGobernados.codigoDelTipo(limpio);
        };
    }

    /**
     * Los codigos de una lista de tipos pedidos, sin repetidos y en el orden en
     * que llegaron.
     *
     * @throws IllegalArgumentException si alguno no nombra un tipo conocido. Se
     *         rechaza en vez de descartarse en silencio: un tipo mal escrito
     *         que se ignora convierte «busco un almacen» en «me vale
     *         cualquiera», y el cliente no se entera
     */
    public static Set<String> codigos(List<String> pedidos) {
        Set<String> codigos = new LinkedHashSet<>();
        if (pedidos == null) {
            return codigos;
        }
        for (String pedido : pedidos) {
            if (pedido == null || pedido.isBlank()) {
                continue;
            }
            codigos.add(codigo(pedido).orElseThrow(() -> new IllegalArgumentException(
                    "Tipo de inmueble desconocido: \"" + pedido + "\". Son "
                            + String.join(", ", AtributosGobernados.nombresDeTipos())
                            + " (o sus codigos " + String.join(" ", CODIGOS) + ").")));
        }
        return codigos;
    }

    /**
     * <b>El nombre que el modelo viejo daba a un tipo</b>, para que la respuesta
     * siga llevando el campo {@code tipoInmueble} que el SPA lee.
     *
     * <p>Es la vuelta de la traduccion y no es completa, a proposito:
     * {@code D} y {@code C} <b>no tenian nombre</b> en el vocabulario de la
     * demanda —el modelo viejo no sabia buscar una vivienda—, asi que devuelven
     * {@code null} en vez de inventarles uno. Un requerimiento de departamento
     * viaja con {@code tipoInmueble} vacio y con {@code tiposInmueble = ["D"]},
     * que es exactamente la verdad: el campo viejo no puede decirlo.
     *
     * <p>{@code X} vuelve como {@code OTRO} y nunca como {@code STAND_MODULO}:
     * la traduccion perdio esa distincion y reconstruirla adivinando seria
     * inventar procedencia. Quien la necesite la tiene en la columna de linaje.
     */
    public static String nombreLegado(String codigo) {
        return switch (codigo == null ? "" : codigo) {
            case "L" -> "LOCAL_COMERCIAL";
            case "O" -> "OFICINA";
            case "E" -> "ESTACIONAMIENTO";
            case "A" -> "DEPOSITO_ALMACEN";
            case "T" -> "TERRENO_COMERCIAL";
            case "X" -> "OTRO";
            default -> null;
        };
    }

    /** Como se lee un tipo pedido. El rotulo es el de la propiedad: es el mismo concepto. */
    public static String rotulo(String codigo) {
        return AtributosGobernados.rotuloDelTipo(codigo);
    }
}
