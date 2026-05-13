package com.controllocal.dao;

import com.controllocal.model.comercial.InteraccionComercial;

import java.util.List;
import java.util.Optional;

public interface InteraccionComercialDAO {
    Long crear(InteraccionComercial interaccion);
    Optional<InteraccionComercial> buscarPorId(Long id);
    List<InteraccionComercial> listarTodos();
    boolean actualizar(InteraccionComercial interaccion);
    boolean eliminar(Long id);
}
