package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.InteraccionComercial;

public interface InteraccionComercialDAO extends CrudDAO<InteraccionComercial> {
    Long crear(InteraccionComercial interaccion);
    Optional<InteraccionComercial> buscarPorId(Long id);
    List<InteraccionComercial> listarTodos();
    boolean actualizar(InteraccionComercial interaccion);
    boolean eliminar(Long id);
}
