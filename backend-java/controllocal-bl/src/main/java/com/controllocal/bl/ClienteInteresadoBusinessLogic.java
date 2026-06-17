package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.persona.ClienteInteresado;

public interface ClienteInteresadoBusinessLogic {

    public Long registrar(ClienteInteresado cliente);
    public Optional<ClienteInteresado> buscarPorId(Long idCliente);
    public List<ClienteInteresado> listarTodos();
    public List<ClienteInteresado> listarPagina(int limite, int desplazamiento);
    public long contar();
    public boolean actualizar(ClienteInteresado cliente);
    public boolean desactivar(Long idCliente);
}

