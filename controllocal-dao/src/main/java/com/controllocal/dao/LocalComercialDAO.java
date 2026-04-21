package com.controllocal.dao;

import com.controllocal.model.inmueble.LocalComercial;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para la entidad LocalComercial.
 */
public interface LocalComercialDAO {

    Long crear(LocalComercial local);

    Optional<LocalComercial> buscarPorId(Long id);

    List<LocalComercial> listarTodos();

    boolean actualizar(LocalComercial local);

    boolean eliminar(Long id);
}
