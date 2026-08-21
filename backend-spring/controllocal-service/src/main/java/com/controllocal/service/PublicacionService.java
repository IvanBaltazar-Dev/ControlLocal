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
 * ESTE precio, asi que pertenece al encargo. Los metodos que siguen
 * preguntando por inmueble --{@link #listarPorInmueble},
 * {@link #codigoEstadoPublicacion}, {@link #sincronizar}-- sirven al listado y
 * al formulario heredados, que todavia razonan en locales.
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

    /** El {@code actor} aporta la organizacion que se estampa en la publicacion nueva (D-20). */
    FichaPublicacion crear(long idPropiedad, DatosPublicacion datos, Actor actor);

    /** Edita un anuncio del tenant del actor. */
    FichaPublicacion actualizar(long idPublicacion, DatosPublicacion datos, Actor actor);

    /** Publica, pausa o cierra un anuncio del tenant del actor. */
    FichaPublicacion cambiarEstado(long idPublicacion, String estado, Actor actor);

    /**
     * Mantiene la publicacion principal alineada con el local (alta/edicion):
     * crea la publicacion web propia si no existe, o actualiza estado, renta
     * y titulo. Con estado en blanco no hace nada; 'B' sin publicacion previa
     * tampoco crea nada (paridad v1).
     */
    void sincronizar(long idPropiedad, String codigoLocal, BigDecimal precioReferencial,
                     String monedaReferencial, String codigoEstado, Actor actor);
}
