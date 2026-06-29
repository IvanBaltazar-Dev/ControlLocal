package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;

public interface PublicacionBusinessLogic {

    // Codigo del estado de publicacion del inmueble (BORRADOR si no tiene).
    String codigoEstadoPublicacion(Long idLocal);

    // Crea/actualiza la publicacion web del local segun el estado solicitado.
    void sincronizar(LocalComercial local, String codigoEstado);

    // Publicaciones del local, mas recientes primero.
    List<Publicacion> listarPorInmueble(Long idLocal);

    Optional<Publicacion> buscarPorId(Long idPublicacion);

    // Etapa 7: gestion de publicacion desde el detalle (canal/URL/renta + estados).
    // Crea una publicacion para el local (estado PUBLICADO por defecto, version 1).
    Publicacion crear(Long idLocal, Publicacion datos);

    // Actualiza canal/URL/renta/titulo y autoincrementa la version del anuncio.
    Publicacion actualizar(Long idPublicacion, Publicacion datos);

    // Publicar / pausar / reanudar / cerrar un anuncio.
    Publicacion cambiarEstado(Long idPublicacion, EstadoPublicacion estado);
}
