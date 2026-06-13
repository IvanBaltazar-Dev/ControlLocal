package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.inmueble.Distrito;

/**
 * Contrato de lectura del catalogo de distritos.
 */
public interface DistritoDAO {

    List<Distrito> listarActivos();

    Optional<Distrito> buscarPorNombre(String nombre);
}
