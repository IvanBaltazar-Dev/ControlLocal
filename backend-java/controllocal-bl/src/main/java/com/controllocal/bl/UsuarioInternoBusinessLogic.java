package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.UsuarioInterno;

public interface UsuarioInternoBusinessLogic {

    public Long registrar(UsuarioInterno usuario);
    public Optional<UsuarioInterno> buscarPorId(Long idUsuario);
    public Optional<UsuarioInterno> buscarPorNombreUsuario(String nombreUsuario);
    public List<UsuarioInterno> listarTodos();
    public boolean actualizar(UsuarioInterno usuario);
    public boolean desactivar(Long idUsuario);
}

