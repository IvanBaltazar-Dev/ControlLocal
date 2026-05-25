package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.InteraccionComercial;

public interface InteraccionComercialBusinessLogic {

    public Long registrar(InteraccionComercial interaccion);
    public Optional<InteraccionComercial> buscarPorId(Long idInteraccion);
    public List<InteraccionComercial> listarTodos();
    public boolean actualizar(InteraccionComercial interaccion);
    public boolean eliminar(Long idInteraccion);
}

