package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.OportunidadComercial;

public interface OportunidadComercialDAO extends CrudDAO<OportunidadComercial> {
    Long crear(OportunidadComercial oportunidad);
    Optional<OportunidadComercial> buscarPorId(Long id);
    List<OportunidadComercial> listarTodos();
    boolean actualizar(OportunidadComercial oportunidad);
    boolean eliminar(Long id);
}
