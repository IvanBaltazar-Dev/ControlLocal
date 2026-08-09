package com.controllocal.service.impl;

import com.controllocal.domain.persona.Persona;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerfilServiceImplTest {

    private static final long ORG = 1L;
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final PerfilServiceImpl service = new PerfilServiceImpl(personas);
    private final Actor actor = new Actor(ORG, 7L, 20L, "BROKER");

    @Test
    void obtieneElPerfilDeLaPersonaDelToken() {
        when(personas.findByOrganizacionIdAndId(ORG, 7L))
                .thenReturn(Optional.of(persona()));

        var ficha = service.obtener(actor);

        assertEquals("Ricardo Salas", ficha.nombre());
        assertEquals("999888777", ficha.telefono());
        assertEquals("perfil/actual.png", ficha.fotoClave());
    }

    @Test
    void unActorSinPersonaRespondeUsuarioNoEncontrado() {
        when(personas.findByOrganizacionIdAndId(ORG, 7L))
                .thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.obtener(actor));
    }

    @Test
    void elPatchRecortaYGuardaElTelefono() {
        Persona persona = persona();
        when(personas.findByOrganizacionIdAndId(ORG, 7L))
                .thenReturn(Optional.of(persona));

        var ficha = service.actualizarTelefono("  +51 999-111-222  ", actor);

        assertEquals("+51 999-111-222", ficha.telefono());
        verify(personas).save(persona);
    }

    @Test
    void elPatchSinTelefonoNoMutaNada() {
        Persona persona = persona();
        when(personas.findByOrganizacionIdAndId(ORG, 7L))
                .thenReturn(Optional.of(persona));

        assertEquals("999888777",
                service.actualizarTelefono(null, actor).telefono());
    }

    @Test
    void elTelefonoExigeEntre6Y15Digitos() {
        when(personas.findByOrganizacionIdAndId(ORG, 7L))
                .thenReturn(Optional.of(persona()));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.actualizarTelefono("123", actor));

        assertEquals("Ingresa un telefono valido de entre 6 y 15 digitos.",
                error.getMessage());
    }

    @Test
    void laFotoSeGuardaEnPersonaNoEnLaCredencial() {
        Persona persona = persona();
        when(personas.findByOrganizacionIdAndId(ORG, 7L))
                .thenReturn(Optional.of(persona));

        assertEquals("perfiles/nueva.png",
                service.actualizarFoto("perfiles/nueva.png", actor));
        assertEquals("perfiles/nueva.png", persona.getFotoClave());
        verify(personas).save(persona);
    }

    private static Persona persona() {
        Persona persona = new Persona();
        persona.setOrganizacionId(ORG);
        persona.setTipoPersona("N");
        persona.setTipoDocumento("D");
        persona.setNumeroDocumento("12345678");
        persona.setNombresORazonSocial("Ricardo Salas");
        persona.setCorreo("rsalas@test.pe");
        persona.setTelefono("999888777");
        persona.setFotoClave("perfil/actual.png");
        persona.setEstado("A");
        ReflectionTestUtils.setField(persona, "id", 7L);
        return persona;
    }
}
