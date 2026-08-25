package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <b>Los anuncios de un ENCARGO</b> (V70).
 *
 * <p>Una publicacion anuncia que esta propiedad se ofrece en ESTA operacion a
 * ESTE precio, asi que pertenece al encargo. Los dos metodos que siguen
 * preguntando por inmueble --{@link #listarPorInmueble} y
 * {@link #codigoEstadoPublicacion}-- sirven al listado heredado, que todavia
 * razona en locales; los dos <b>leen</b>, y ninguno crea.
 *
 * <p>El tercero que preguntaba por inmueble, {@code sincronizar}, servia al
 * formulario de la v1 y <b>si creaba</b>. Se retiro el 2026-08-24 junto con
 * {@code crear(idPropiedad, ...)}: ver la nota al final de esta interfaz.
 *
 * <p>La publicacion mas reciente es la "principal": su estado es el
 * {@code estadoPublicacion} del cable heredado ('B' si no hay ninguna).
 */
public interface PublicacionService {

    /**
     * Lo que se manda para crear o editar un anuncio.
     *
     * <p>{@code importePublicado} se llamaba {@code rentaPublicada}: en una
     * publicacion de venta ese nombre era falso y viajaba hasta la pantalla.
     */
    record DatosPublicacion(String canal, String urlPublicacion, BigDecimal importePublicado,
                            String moneda, String tituloAnuncio, String codigoOrigen, String estado) {
    }

    /**
     * Un anuncio, listo para leerse.
     *
     * @param idEncargo      de que encargo es. Nunca se pierde: es lo que
     *                       impide que el anuncio de la venta se lea como el
     *                       del alquiler
     * @param importeRotulo  «precio de venta» o «renta mensual», segun la
     *                       operacion del encargo. Lo dice el dominio; el
     *                       cliente no lo deduce (D-A-1 §5)
     * @param estadoRotulo   «Publicada», «Pausada»… junto al codigo
     */
    record FichaPublicacion(Long id, Long idEncargo, String canal, String tituloAnuncio,
                            BigDecimal importePublicado, String moneda, String importeRotulo,
                            String estado, String estadoRotulo, LocalDateTime fechaPublicacion,
                            LocalDateTime fechaBaja, String urlPublicacion, String codigoOrigen) {
    }

    /** Los anuncios de un inmueble. Lo consume el detalle heredado. */
    List<FichaPublicacion> listarPorInmueble(long idPropiedad);

    /**
     * <b>Los anuncios de un encargo</b>, del mas reciente al mas antiguo.
     *
     * <p>Comprueba que el encargo sea del tenant del actor antes de responder:
     * un id de otro tenant es un 404, no una lista vacia.
     */
    List<FichaPublicacion> listarDeEncargo(long idEncargo, Actor actor);

    /**
     * Crea un anuncio <b>de un encargo</b>.
     *
     * <p>Exige que el encargo este VIVO: publicar un encargo cerrado pondria en
     * el mercado algo que ya no se ofrece. Es una regla de negocio y por eso
     * vive aqui, no en la pantalla que dibuja el boton.
     */
    FichaPublicacion crearEnEncargo(long idEncargo, DatosPublicacion datos, Actor actor);

    String codigoEstadoPublicacion(long idPropiedad);

    /** Estado de publicacion por local, en lote (para las listas sin N+1). */
    Map<Long, String> codigosEstadoPublicacion(Collection<Long> idsPropiedad);

    /** Edita un anuncio del tenant del actor. */
    FichaPublicacion actualizar(long idPublicacion, DatosPublicacion datos, Actor actor);

    /** Publica, pausa o cierra un anuncio del tenant del actor. */
    FichaPublicacion cambiarEstado(long idPublicacion, String estado, Actor actor);

    // ------------------------------------------------------------------
    // NO HAY MAS PUERTAS DE CREACION, Y ESO ES LA GARANTIA
    //
    // Aqui vivian `crear(idPropiedad, ...)` y
    // `sincronizar(idPropiedad, ...)`. Las dos CREABAN una publicacion --
    // `sincronizar` ademas la dejaba en PUBLICADO y escribia el hito `P` --
    // <b>sin pasar por `exigirPublicable`</b>. Ninguna estaba expuesta por un
    // controlador y ninguna tenia un solo consumidor de produccion: `sincronizar`
    // era residuo del formulario de la v1, borrada el 2026-08-08.
    //
    // Se retiraron el 2026-08-24 en vez de hacerlas delegar, y la razon es que
    // una via que delega SIGUE EXISTIENDO y puede desincronizarse en el proximo
    // cambio. Una via que no existe no puede eludir nada.
    //
    // Lo que queda: `crearEnEncargo` crea y `cambiarEstado` publica, y las dos
    // llaman a `exigirPublicable`. `actualizar` edita y no toca el estado, asi
    // que no puede publicar un borrador.
    //
    // `PuertasDePublicacionTest` lo comprueba sobre esta interfaz: si alguien
    // anade un metodo que cree o publique sin la validacion canonica, rompe.
    // ------------------------------------------------------------------
}
