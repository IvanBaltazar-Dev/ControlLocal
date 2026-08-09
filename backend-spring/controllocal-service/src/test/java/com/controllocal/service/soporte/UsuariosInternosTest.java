package com.controllocal.service.soporte;

import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuariosInternosTest {

    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final PersonaRolRepository roles = mock(PersonaRolRepository.class);
    private final CredencialUsuarioRepository credenciales =
            mock(CredencialUsuarioRepository.class);
    private final UsuarioOrganizacionRepository membresias =
            mock(UsuarioOrganizacionRepository.class);
    private final UsuariosInternos service =
            new UsuariosInternos(personas, roles, credenciales, membresias);

    @Test
    void elAltaCreaPersonaCredencialYRolOperativoEnEseOrden() {
        when(personas.save(any(Persona.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roles.save(any(PersonaRol.class))).thenAnswer(inv -> inv.getArgument(0));
        when(credenciales.save(any(CredencialUsuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UsuariosInternos.Alta alta = service.registrar(7L, TipoRol.BROKER,
                "N", "D", "12345678", "Broker Nuevo", "999888777",
                "broker@test.pe", "bnuevo", "Temporal2026", "A");

        assertEquals(7L, alta.persona().getOrganizacionId());
        assertEquals(TipoRol.BROKER, alta.rolOperativo().getTipoRol());
        assertEquals(TipoRol.USUARIO_INTERNO, alta.credencial().getRol().getTipoRol());
        assertEquals("bnuevo", alta.credencial().getNombreUsuario());
        assertTrue(PasswordHasher.verificar("Temporal2026".toCharArray(),
                alta.credencial().getContrasenaHash()));

        InOrder orden = inOrder(personas, roles, credenciales);
        orden.verify(personas).save(any(Persona.class));
        orden.verify(roles).save(any(PersonaRol.class));
        orden.verify(credenciales).save(any(CredencialUsuario.class));
        orden.verify(roles).save(any(PersonaRol.class));
    }
}
