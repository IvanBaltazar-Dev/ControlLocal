package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.bl.UsuarioInternoBusinessLogic;
import com.controllocal.dao.impl.PersonaDAOImpl;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.dao.PersonaDAO;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.model.usuario.UsuarioInterno;

public class UsuarioInternoBusinessLogicImpl implements UsuarioInternoBusinessLogic {

    private final UsuarioInternoDAO usuarioDAO;
    private final PersonaDAO personaDAO;

    public UsuarioInternoBusinessLogicImpl() {
        this(new UsuarioInternoDAOImpl(), new PersonaDAOImpl());
    }

    public UsuarioInternoBusinessLogicImpl(UsuarioInternoDAO usuarioDAO) {
        this(usuarioDAO, new PersonaDAOImpl());
    }

    public UsuarioInternoBusinessLogicImpl(UsuarioInternoDAO usuarioDAO, PersonaDAO personaDAO) {
        this.usuarioDAO = usuarioDAO;
        this.personaDAO = personaDAO;
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

    public Optional<UsuarioInterno> buscarPorNombreUsuario(String nombreUsuario) {
        BusinessValidations.texto(nombreUsuario, "El nombre de usuario");
        return usuarioDAO.buscarPorNombreUsuario(nombreUsuario);
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

    public boolean actualizarFotoPerfil(Long idUsuario, String fotoClave) {
        return TransactionRunner.write(conn -> {
            Long idPersona = idPersonaDe(idUsuario);
            return personaDAO.actualizarFoto(idPersona, fotoClave);
        });
    }

    public boolean actualizarTelefono(Long idUsuario, String telefono) {
        return TransactionRunner.write(conn -> {
            Long idPersona = idPersonaDe(idUsuario);
            return personaDAO.actualizarTelefono(idPersona, telefono);
        });
    }

    public Optional<String> obtenerFotoPerfil(Long idUsuario) {
        BusinessValidations.id(idUsuario, "El id de usuario interno");
        return personaDAO.buscarFoto(idPersonaDe(idUsuario));
    }

    private Long idPersonaDe(Long idUsuario) {
        BusinessValidations.id(idUsuario, "El id de usuario interno");
        UsuarioInterno usuario = usuarioDAO.buscarPorId(idUsuario)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado."));
        if (usuario.getPersona() == null || usuario.getPersona().getIdPersona() == null) {
            throw new BusinessException("El usuario no tiene una persona asociada.");
        }
        return usuario.getPersona().getIdPersona();
    }
}

