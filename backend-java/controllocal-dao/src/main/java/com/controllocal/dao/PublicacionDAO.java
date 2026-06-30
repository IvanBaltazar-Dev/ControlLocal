package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.controllocal.model.comercial.Publicacion;

public interface PublicacionDAO extends CrudDAO<Publicacion> {
    List<Publicacion> listarPorInmueble(Long idLocal);
    Map<Long, String> codigosEstadoPorLocales(Collection<Long> idsLocal);
}
