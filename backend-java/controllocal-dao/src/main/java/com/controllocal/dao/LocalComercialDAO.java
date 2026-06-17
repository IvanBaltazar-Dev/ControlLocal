package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.inmueble.LocalComercial;

/**
 * Contrato de persistencia para la entidad LocalComercial.
 */
public interface LocalComercialDAO extends CrudDAO<LocalComercial> {

    Long crear(LocalComercial local);

    Optional<LocalComercial> buscarPorId(Long id);

    List<LocalComercial> listarTodos();

    boolean actualizar(LocalComercial local);

    boolean eliminar(Long id);
    List<LocalComercial> listarPorAgente(long idAgente, int limite, int desplazamiento);
}
