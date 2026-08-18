package com.controllocal.service;

import com.controllocal.service.soporte.Procedencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <b>La propiedad universal: alta, lectura y edicion por el modelo nuevo</b>
 * (D-E4-1, D-E4-2).
 *
 * <h2>Que resuelve, y por que en un solo caso de uso</h2>
 * Registrar una propiedad no es escribir una fila. Son nueve cosas que solo
 * valen juntas: la propiedad, su ubicacion, sus titulares con sus cuotas, sus
 * atributos gobernados, uno o dos encargos con su operacion, la condicion
 * economica de cada uno, el primer hito {@code U} de cada serie y el evento de
 * dominio que deja constancia. <b>Todo o nada.</b>
 *
 * <p>Repartirlo en cinco endpoints —crear el inmueble, luego los titulares,
 * luego el encargo...— produce medias propiedades en cuanto una llamada falla
 * o el usuario cierra la pestana, y esas medias propiedades no las arregla
 * nadie: quedan en la cartera sin titular o sin precio, contando en los
 * listados y mintiendo en los indicadores.
 *
 * <h2>La operacion no se infiere jamas</h2>
 * Cada elemento de {@code operaciones} declara <b>VENTA</b> o <b>ALQUILER</b>.
 * Una propiedad disponible para las dos cosas se registra con <b>dos</b>
 * elementos, cada uno con su importe y su condicion — nunca con un valor
 * combinado, que obligaria a decidir a mano que pasa con el alquiler el dia
 * que se venda.
 *
 * <h2>El mismo modelo escribe y lee</h2>
 * {@link #consultar} devuelve lo que {@link #registrar} escribio, leido de las
 * estructuras nuevas: titularidades, atributos gobernados y encargos. No es un
 * detalle de implementacion — un POST que escribe en el modelo universal y un
 * GET que lee columnas viejas es una migracion a medias que parece terminada.
 *
 * <h2>Idempotencia</h2>
 * {@code claveIdempotencia} es del cliente y opcional. Cuando viene, un
 * reintento con la misma clave <b>no crea una segunda propiedad</b>: devuelve
 * la que produjo el primer intento, con {@code reintento = true}. Es lo que
 * hace seguro poner un canal conversacional delante, porque un canal
 * conversacional reintenta por diseno.
 */
public interface PropiedadUniversalService {

    // ------------------------------------------------------------------
    // Lo que entra
    // ------------------------------------------------------------------

    /**
     * Un titular y la parte que le corresponde.
     *
     * <p>Las cuotas vigentes de una propiedad tienen que sumar 100 — lo exige
     * un constraint trigger diferido (V47) y lo comprueba el servicio antes,
     * para poder decir "te faltan 30" en vez de dejar que estalle el COMMIT.
     * {@code representante} marca con quien se habla; si nadie lo marca, es el
     * primero.
     */
    record Titular(Long idRolPropietario, BigDecimal cuota, Boolean representante) {
    }

    /**
     * El valor de una caracteristica gobernada, tal cual se escribio. Llega
     * como texto y lo interpreta el catalogo: {@code dormitorios} es ENTERO y
     * {@code amoblado} BOOLEANO, y esa conversion no la decide el cliente.
     */
    record ValorAtributo(String clave, String valor) {
    }

    /** Donde esta. Las coordenadas son opcionales; la direccion y el distrito no. */
    record Ubicacion(String direccion, String distrito, String zonaUrbanizacion,
                     BigDecimal latitud, BigDecimal longitud, String interiorUnidad,
                     String piso, String referenciaInterna, String nombreEdificioGaleria) {
    }

    /**
     * Una relacion comercial: la operacion y lo que se pide por ella.
     *
     * <p>{@code operacion} es VENTA o ALQUILER, obligatoria. {@code importe} es
     * el precio de venta o la renta mensual segun cual sea — el mismo numero
     * significa cosas distintas y por eso la operacion viaja pegada a el.
     */
    record OperacionSolicitada(String operacion, BigDecimal importe, String moneda,
                               String tipoComision, String baseCalculo, BigDecimal valorComision,
                               String tratamientoIgv, Boolean exclusividad,
                               LocalDate inicioEncargo, LocalDate finEncargo) {
    }

    /**
     * El comando completo del alta.
     *
     * @param claveIdempotencia del cliente; {@code null} si no reintenta
     * @param procedencia       de donde salio la peticion: origen y, si vino de
     *                          una conversacion, cual y de que turno (D-K-1 §5)
     * @param idBorrador        el borrador que se estaba completando, si lo hay
     */
    record ComandoRegistro(String claveIdempotencia, Procedencia procedencia, String codigo,
                           String tipoPropiedad, String uso, String descripcion,
                           Ubicacion ubicacion, List<Titular> titulares,
                           List<ValorAtributo> atributos, List<OperacionSolicitada> operaciones,
                           Long idBorrador) {
    }

    /**
     * La edicion. Lo que llega {@code null} <b>no se toca</b>: editar el precio
     * no debe exigir reenviar los titulares, y reenviarlos "para completar"
     * es como se pierden datos que nadie queria cambiar.
     *
     * <p>{@code operaciones} sigue la misma regla que en el alta: cada una
     * declara la suya. Cambiar el importe de una operacion <b>anade</b> un hito
     * al historico; nunca sobrescribe el anterior.
     */
    record ComandoEdicion(String claveIdempotencia, Procedencia procedencia, String descripcion,
                          Ubicacion ubicacion, List<Titular> titulares,
                          List<ValorAtributo> atributos, List<OperacionSolicitada> operaciones) {
    }

    // ------------------------------------------------------------------
    // Lo que sale
    // ------------------------------------------------------------------

    record TitularFicha(Long idRolPropietario, String nombre, BigDecimal cuota,
                        boolean representante, LocalDate desde) {
    }

    /**
     * Un atributo con su valor y su etiqueta. {@code rotulo}, {@code unidad} y
     * {@code tipoDato} vienen del catalogo para que la pantalla no tenga que
     * saberse la tabla de memoria.
     */
    record AtributoFicha(String clave, String rotulo, String tipoDato, String unidad,
                         String valor) {
    }

    /** Un hito de la serie economica de una operacion. */
    record HitoFicha(String hito, BigDecimal monto, String moneda, LocalDate fecha) {
    }

    /**
     * Una relacion comercial viva, con su historico propio. Dos encargos de la
     * misma propiedad no comparten ni precio ni serie: es lo que hace real la
     * universalidad.
     */
    record EncargoFicha(Long idEncargo, String codigo, String operacion, String estado,
                        BigDecimal importe, String moneda, Boolean exclusividad,
                        LocalDate inicio, LocalDate fin, List<HitoFicha> historico) {
    }

    /**
     * La propiedad leida por el modelo universal.
     *
     * <p>{@code atributosQueFaltan} son las claves obligatorias para su tipo
     * que todavia no tiene. No es un error: es lo que permite a la ficha decir
     * "no se puede publicar sin el metraje" y al motor de captura preguntar por
     * ello.
     */
    record FichaPropiedadUniversal(Long id, String codigo, String tipoPropiedad, String uso,
                                   String descripcion, String estadoRegistro,
                                   String disponibilidadComercial, Ubicacion ubicacion,
                                   List<TitularFicha> titulares, List<AtributoFicha> atributos,
                                   List<EncargoFicha> encargos, List<String> atributosQueFaltan,
                                   LocalDateTime fechaRegistro) {
    }

    /** Que produjo el alta. {@code reintento} avisa de que ya existia. */
    record ResultadoRegistro(Long idPropiedad, String codigo, List<Long> idsEncargos,
                             boolean reintento) {
    }

    /**
     * Una caracteristica que <b>aplica</b> a un tipo de propiedad, derivada del
     * catalogo. Es lo que hace que registrar un terreno no pida dormitorios sin
     * que ninguna pantalla lo sepa.
     */
    record PreguntaCatalogo(String clave, String rotulo, String tipoDato, String unidad,
                            boolean obligatoria, int orden) {
    }

    // ------------------------------------------------------------------

    /** Alta universal, en una sola transaccion. Todo o nada. */
    ResultadoRegistro registrar(ComandoRegistro comando, Actor actor);

    /**
     * Que se pregunta para un tipo de propiedad. Lo consulta el cliente para
     * pintar el formulario; la lista <b>no</b> se escribe en el cliente.
     */
    List<PreguntaCatalogo> catalogoDe(String tipoPropiedad, Actor actor);

    /** La propiedad tal como la escribio el modelo universal. */
    FichaPropiedadUniversal consultar(long idPropiedad, Actor actor);

    /** Edicion parcial: lo que llega {@code null} no se toca. */
    FichaPropiedadUniversal editar(long idPropiedad, ComandoEdicion comando, Actor actor);
}
