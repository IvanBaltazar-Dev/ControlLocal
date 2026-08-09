package com.controllocal.service.impl;

import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.OrganizacionService;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutenticacionServiceImplTest {

    @Test
    void soloBuscaCredencialesEnLaOrganizacionActual() {
        CredencialUsuarioRepository credenciales =
                mock(CredencialUsuarioRepository.class);
        OrganizacionService organizaciones = mock(OrganizacionService.class);
        when(organizaciones.idOrganizacionActual()).thenReturn(7L);
        when(credenciales.buscarActivaPorNombreUsuario(7L, "usuario"))
                .thenReturn(Optional.empty());

        AutenticacionServiceImpl service = new AutenticacionServiceImpl(
                credenciales,
                mock(PersonaRolRepository.class),
                mock(DetalleBrokerRepository.class),
                mock(DetalleAgenteRepository.class),
                mock(UsuarioOrganizacionRepository.class),
                organizaciones);

        assertThrows(CredencialesInvalidasException.class,
                () -> service.autenticar("  usuario  ", "secreto".toCharArray()));
        verify(credenciales).buscarActivaPorNombreUsuario(7L, "usuario");
    }
}
