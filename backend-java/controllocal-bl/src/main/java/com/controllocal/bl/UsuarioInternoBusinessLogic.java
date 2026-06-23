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

    // Perfil del usuario en sesion: foto y telefono de su persona asociada.
    public boolean actualizarFotoPerfil(Long idUsuario, String fotoClave);
    public boolean actualizarTelefono(Long idUsuario, String telefono);
    public Optional<String> obtenerFotoPerfil(Long idUsuario);
}

