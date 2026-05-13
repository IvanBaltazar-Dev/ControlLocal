package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.model.usuario.UsuarioInterno;

import java.util.List;
import java.util.Optional;

public class UsuarioInternoBusinessLogic {

    private final UsuarioInternoDAO usuarioDAO;

    public UsuarioInternoBusinessLogic() {
        this(new UsuarioInternoDAOImpl());
    }

    public UsuarioInternoBusinessLogic(UsuarioInternoDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    public Long registrar(UsuarioInterno usuario) {
        return TransactionRunner.write(() -> {
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
        return TransactionRunner.write(() -> {
            BusinessValidations.id(usuario != null ? usuario.getIdUsuarioInterno() : null, "El id de usuario interno");
            BusinessValidations.usuarioInterno(usuario);
            return usuarioDAO.actualizar(usuario);
        });
    }

    public boolean desactivar(Long idUsuario) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idUsuario, "El id de usuario interno");
            return usuarioDAO.eliminar(idUsuario);
        });
    }
}
