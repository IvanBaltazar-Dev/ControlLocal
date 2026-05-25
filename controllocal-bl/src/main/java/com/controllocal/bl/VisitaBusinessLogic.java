package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Visita;

public interface VisitaBusinessLogic {

    public Long registrar(Visita visita);
    public Optional<Visita> buscarPorId(Long idVisita);
    public List<Visita> listarTodos();
    public boolean actualizar(Visita visita);
    public boolean eliminar(Long idVisita);
}

