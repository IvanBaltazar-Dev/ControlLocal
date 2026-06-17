package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.persona.Propietario;

public interface PropietarioBusinessLogic {

    public Long registrar(Propietario propietario);
    public Optional<Propietario> buscarPorId(Long idPropietario);
    public List<Propietario> listarTodos();
    public List<Propietario> listarPagina(int limite, int desplazamiento);
    public long contar();
    public boolean actualizar(Propietario propietario);
    public boolean desactivar(Long idPropietario);
}

