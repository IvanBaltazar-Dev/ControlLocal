package com.controllocal.app;

import com.controllocal.dao.PersonaDAO;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.dao.impl.PersonaDAOImpl;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.TipoPersona;
import com.controllocal.model.usuario.RolUsuarioInterno;
import com.controllocal.model.usuario.UsuarioInterno;

final class ManualTestSupport {

    private ManualTestSupport() {
    }

    static Persona crearPersona(String numeroDocumento, String nombre, String telefono, String correo) {
        PersonaDAO dao = new PersonaDAOImpl();
        Persona persona = new Persona();
        persona.setTipoPersona(TipoPersona.NATURAL);
        persona.setTipoDocumento("DNI");
        persona.setNumeroDocumento(numeroDocumento);
        persona.setNombresORazonSocial(nombre);
        persona.setTelefono(telefono);
        persona.setCorreo(correo);
        persona.setEstado(EstadoActivoInactivo.ACTIVO);
        persona.setIdPersona(dao.crear(persona));
        return persona;
    }

    static UsuarioInterno crearUsuarioInterno(
            String numeroDocumento,
            String nombrePersona,
            String telefono,
            String correo,
            String nombreUsuario,
            String contrasenaHash,
            RolUsuarioInterno rol
    ) {
        UsuarioInternoDAO dao = new UsuarioInternoDAOImpl();
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setPersona(crearPersona(numeroDocumento, nombrePersona, telefono, correo));
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setContrasenaHash(contrasenaHash);
        usuario.setEstadoAdministrativo(EstadoActivoInactivo.ACTIVO);
        usuario.setRol(rol);
        usuario.setIdUsuarioInterno(dao.crear(usuario));
        return usuario;
    }
}
