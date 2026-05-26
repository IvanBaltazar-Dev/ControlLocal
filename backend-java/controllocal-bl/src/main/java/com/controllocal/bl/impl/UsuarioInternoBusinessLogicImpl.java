package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.bl.UsuarioInternoBusinessLogic;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.model.usuario.UsuarioInterno;

public class UsuarioInternoBusinessLogicImpl implements UsuarioInternoBusinessLogic {

    private final UsuarioInternoDAO usuarioDAO;

    public UsuarioInternoBusinessLogicImpl() {
        this(new UsuarioInternoDAOImpl());
    }

    public UsuarioInternoBusinessLogicImpl(UsuarioInternoDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    public Long registrar(UsuarioInterno usuario) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.usuarioInterno(usuario);
            return usuarioDAO.crear(usuario);
        });
    }

    public Optional<UsuarioInterno> buscarPorId(Long idUsuario) {
        BusinessValidations.id(idUsuario, "El id de usuario interno");
        return usuarioDAO.buscarPorId(idUsuario);
    }

    public List<UsuarioInterno> listarTodos() {
        return usuarioDAO.listarTodos();
    }

    public boolean actualizar(UsuarioInterno usuario) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(usuario != null ? usuario.getIdUsuarioInterno() : null, "El id de usuario interno");
            BusinessValidations.usuarioInterno(usuario);
            return usuarioDAO.actualizar(usuario);
        });
    }

    public boolean desactivar(Long idUsuario) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idUsuario, "El id de usuario interno");
            return usuarioDAO.eliminar(idUsuario);
        });
    }
}

