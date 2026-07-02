package com.controllocal.bl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.controllocal.model.persona.Propietario;

public interface PropietarioBusinessLogic {

    public Long registrar(Propietario propietario);
    public Optional<Propietario> buscarPorId(Long idPropietario);
    public List<Propietario> listarTodos();
    public List<Propietario> listarPagina(int limite, int desplazamiento);
    // Carga en bloque solo los propietarios pedidos (paginar el alcance del broker sin traer todo).
    public List<Propietario> listarPorIds(Collection<Long> ids);
    // Conteo de locales en seguimiento (captacion/prospeccion en alcance) por propietario, para
    // los propietarios de la pagina. idsAgente == null => admin; idsCaptacionSupervisadas => broker.
    public Map<Long, Integer> contarLocalesEnSeguimiento(
            Collection<Long> idsPropietario, Collection<Long> idsAgente, Collection<Long> idsCaptacionSupervisadas);
    public long contar();
    public boolean actualizar(Propietario propietario);
    public boolean desactivar(Long idPropietario);
}

