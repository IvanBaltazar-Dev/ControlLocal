package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.persona.Persona;

public interface PersonaBusinessLogic {

    public Long registrar(Persona persona);
    public Optional<Persona> buscarPorId(Long idPersona);
    public List<Persona> listarTodos();
    public boolean actualizar(Persona persona);
    public boolean desactivar(Long idPersona);
}

