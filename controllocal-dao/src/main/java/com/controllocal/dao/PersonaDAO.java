package com.controllocal.dao;

import com.controllocal.model.persona.Persona;

import java.util.List;
import java.util.Optional;

public interface PersonaDAO {
    Long crear(Persona persona);
    Optional<Persona> buscarPorId(Long id);
    List<Persona> listarTodos();
    boolean actualizar(Persona persona);
    boolean eliminar(Long id);
}
