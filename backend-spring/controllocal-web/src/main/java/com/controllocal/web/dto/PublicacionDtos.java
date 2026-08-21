package com.controllocal.web.dto;

import com.controllocal.service.PublicacionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <b>El cable de la publicación por encargo</b> (V70).
 *
 * <p>Van juntos porque son un solo contrato: el cuerpo de alta/edición y el
 * anuncio que devuelven las tres operaciones.
 *
 * <p>Sustituyen a {@code PublicacionRequest}/{@code PublicacionResponse}, que
 * hablaban de {@code rentaPublicada} — un nombre correcto mientras lo único que
 * existía eran locales en alquiler, y falso en el momento en que se publica una
 * venta. Aquí el importe se llama importe, y <b>cómo se lee</b> lo dice el
 * backend en {@code importeRotulo}.
 */
public final class PublicacionDtos {

    private PublicacionDtos() {
    }

    /** Lo que se manda para crear o editar un anuncio. */
    public record PublicacionRequest(String canal, String urlPublicacion,
                                     BigDecimal importePublicado, String moneda,
                                     String tituloAnuncio, String codigoOrigen, String estado) {

        public PublicacionService.DatosPublicacion aDatos() {
            return new PublicacionService.DatosPublicacion(canal, urlPublicacion, importePublicado,
                    moneda, tituloAnuncio, codigoOrigen, estado);
        }
    }

    /**
     * Un anuncio.
     *
     * @param idEncargo     de qué encargo es. Nunca se pierde: es lo que impide
     *                      que el anuncio de la venta se lea como el del
     *                      alquiler
     * @param importeRotulo «precio de venta» o «renta mensual», según la
     *                      operación del encargo. Lo dice el dominio
     * @param estadoRotulo  «Publicada», «Pausada»… junto al código
     */
    public record PublicacionResponse(Long id, Long idEncargo, String canal, String tituloAnuncio,
                                      BigDecimal importePublicado, String moneda,
                                      String importeRotulo, String estado, String estadoRotulo,
                                      LocalDateTime fechaPublicacion, LocalDateTime fechaBaja,
                                      String urlPublicacion, String codigoOrigen) {

        public static PublicacionResponse desde(PublicacionService.FichaPublicacion f) {
            return new PublicacionResponse(f.id(), f.idEncargo(), f.canal(), f.tituloAnuncio(),
                    f.importePublicado(), f.moneda(), f.importeRotulo(), f.estado(),
                    f.estadoRotulo(), f.fechaPublicacion(), f.fechaBaja(), f.urlPublicacion(),
                    f.codigoOrigen());
        }
    }
}
