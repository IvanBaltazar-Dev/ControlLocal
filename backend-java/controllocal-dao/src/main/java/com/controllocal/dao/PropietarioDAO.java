package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.controllocal.model.persona.Propietario;

public interface PropietarioDAO extends CrudDAO<Propietario> {
    Long crear(Propietario propietario);

    Optional<Propietario> buscarPorId(Long id);

    List<Propietario> listarTodos();

    // Carga en bloque solo los propietarios pedidos (paginar el alcance del broker sin traer todo).
    List<Propietario> listarPorIds(Collection<Long> ids);

    // Conteo de locales "en seguimiento" (en captacion o prospeccion dentro del alcance) por
    // propietario, restringido a los propietarios dados. idsAgente == null => admin (sin filtro);
    // idsCaptacionSupervisadas => captaciones supervisadas por el broker. Evita escanear tablas.
    Map<Long, Integer> contarLocalesEnSeguimiento(
            Collection<Long> idsPropietario, Collection<Long> idsAgente, Collection<Long> idsCaptacionSupervisadas);

    boolean actualizar(Propietario propietario);

    boolean eliminar(Long id);
}
