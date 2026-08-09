package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Publicaciones del local (anuncios por canal). La publicacion mas
 * reciente es la "principal": su estado es el estadoPublicacion del cable
 * congelado ('B' si el local no tiene ninguna).
 */
public interface PublicacionService {

    /** Espejo de PublicacionRequest (cable congelado). */
    record DatosPublicacion(String canal, String urlPublicacion, BigDecimal rentaPublicada,
                            String moneda, String tituloAnuncio, String codigoOrigen, String estado) {
    }

    /** Espejo de PublicacionResponse (cable congelado). */
    record FichaPublicacion(Long id, String canal, String tituloAnuncio, BigDecimal rentaPublicada,
                            String moneda, String estado, LocalDateTime fechaPublicacion,
                            LocalDateTime fechaBaja, String urlPublicacion, String codigoOrigen) {
    }

    List<FichaPublicacion> listarPorInmueble(long idPropiedad);

    String codigoEstadoPublicacion(long idPropiedad);

    /** Estado de publicacion por local, en lote (para las listas sin N+1). */
    Map<Long, String> codigosEstadoPublicacion(Collection<Long> idsPropiedad);

    /** El {@code actor} aporta la organizacion que se estampa en la publicacion nueva (D-20). */
    FichaPublicacion crear(long idPropiedad, DatosPublicacion datos, Actor actor);

    FichaPublicacion actualizar(long idPublicacion, DatosPublicacion datos);

    FichaPublicacion cambiarEstado(long idPublicacion, String estado);

    /**
     * Mantiene la publicacion principal alineada con el local (alta/edicion):
     * crea la publicacion web propia si no existe, o actualiza estado, renta
     * y titulo. Con estado en blanco no hace nada; 'B' sin publicacion previa
     * tampoco crea nada (paridad v1).
     */
    void sincronizar(long idPropiedad, String codigoLocal, BigDecimal precioReferencial,
                     String monedaReferencial, String codigoEstado, Actor actor);
}
