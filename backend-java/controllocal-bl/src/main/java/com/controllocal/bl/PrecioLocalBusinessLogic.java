package com.controllocal.bl;

import java.util.List;

import com.controllocal.model.inmueble.PrecioLocal;

/**
 * Logica de negocio del historico de precios de un local (tabla precio_local).
 * Da funcion de lectura/registro al DAO ya existente.
 */
public interface PrecioLocalBusinessLogic {

    // Historico de precios del local, ordenado por fecha.
    List<PrecioLocal> listarPorLocal(Long idLocal);

    // Registra un hito de precio (devuelve el id generado).
    Long registrar(PrecioLocal precio);
}
