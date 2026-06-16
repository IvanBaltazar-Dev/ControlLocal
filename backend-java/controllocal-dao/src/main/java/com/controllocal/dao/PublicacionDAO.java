package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.Publicacion;

public interface PublicacionDAO extends CrudDAO<Publicacion> {
    List<Publicacion> listarPorInmueble(Long idLocal);
}
