package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.ClienteInteresadoDAO;
import com.controllocal.dao.impl.ClienteInteresadoDAOImpl;
import com.controllocal.model.persona.ClienteInteresado;

import java.util.List;
import java.util.Optional;

public class ClienteInteresadoBusinessLogic {

    private final ClienteInteresadoDAO clienteDAO;

    public ClienteInteresadoBusinessLogic() {
        this(new ClienteInteresadoDAOImpl());
    }

    public ClienteInteresadoBusinessLogic(ClienteInteresadoDAO clienteDAO) {
        this.clienteDAO = clienteDAO;
    }

    public Long registrar(ClienteInteresado cliente) {
        return TransactionRunner.write(() -> {
            BusinessValidations.cliente(cliente);
            return clienteDAO.crear(cliente);
        });
    }

    public Optional<ClienteInteresado> buscarPorId(Long idCliente) {
        BusinessValidations.id(idCliente, "El id de cliente interesado");
        return clienteDAO.buscarPorId(idCliente);
    }

    public List<ClienteInteresado> listarTodos() {
        return clienteDAO.listarTodos();
    }

    public boolean actualizar(ClienteInteresado cliente) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(cliente != null ? cliente.getIdCliente() : null, "El id de cliente interesado");
            BusinessValidations.cliente(cliente);
            return clienteDAO.actualizar(cliente);
        });
    }

    public boolean desactivar(Long idCliente) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idCliente, "El id de cliente interesado");
            return clienteDAO.eliminar(idCliente);
        });
    }
}
