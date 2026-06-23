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

    // Actualizaciones puntuales de perfil. Tocan solo la columna indicada para no exigir
    // la entidad completa ni alterar el mapeo compartido de persona.
    boolean actualizarFoto(Long idPersona, String fotoClave);
    boolean actualizarTelefono(Long idPersona, String telefono);
    Optional<String> buscarFoto(Long idPersona);
}
