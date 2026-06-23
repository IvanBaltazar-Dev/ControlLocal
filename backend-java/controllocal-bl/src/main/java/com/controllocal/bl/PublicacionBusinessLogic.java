package com.controllocal.bl;

import java.util.List;

import com.controllocal.model.comercial.Publicacion;
import com.controllocal.model.inmueble.LocalComercial;

public interface PublicacionBusinessLogic {

    // Codigo del estado de publicacion del inmueble (BORRADOR si no tiene).
    String codigoEstadoPublicacion(Long idLocal);

    // Crea/actualiza la publicacion web del local segun el estado solicitado.
    void sincronizar(LocalComercial local, String codigoEstado);

    // Publicaciones del local, mas recientes primero.
    List<Publicacion> listarPorInmueble(Long idLocal);
}
