package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.persona.Persona;

public interface PersonaDAO extends CrudDAO<Persona> {
    Long crear(Persona persona);
    Optional<Persona> buscarPorId(Long id);
    List<Persona> listarTodos();
    boolean actualizar(Persona persona);
    boolean eliminar(Long id);
}
