package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.ClienteInteresadoBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.ClienteInteresadoDAO;
import com.controllocal.dao.impl.ClienteInteresadoDAOImpl;
import com.controllocal.model.persona.ClienteInteresado;

public class ClienteInteresadoBusinessLogicImpl implements ClienteInteresadoBusinessLogic {

    private final ClienteInteresadoDAO clienteDAO;

    public ClienteInteresadoBusinessLogicImpl() {
        this(new ClienteInteresadoDAOImpl());
    }

    public ClienteInteresadoBusinessLogicImpl(ClienteInteresadoDAO clienteDAO) {
        this.clienteDAO = clienteDAO;
    }

    public Long registrar(ClienteInteresado cliente) {
        return TransactionRunner.write(conn -> {
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

    public List<ClienteInteresado> listarPagina(int limite, int desplazamiento) {
        BusinessValidations.pagina(limite, desplazamiento);
        return clienteDAO.listarPagina(limite, desplazamiento);
    }

    public long contar() {
        return clienteDAO.contar();
    }

    public boolean actualizar(ClienteInteresado cliente) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(cliente != null ? cliente.getIdCliente() : null, "El id de cliente interesado");
            BusinessValidations.cliente(cliente);
            return clienteDAO.actualizar(cliente);
        });
    }

    public boolean desactivar(Long idCliente) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idCliente, "El id de cliente interesado");
            return clienteDAO.eliminar(idCliente);
        });
    }
}

