package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.EventoSeguridad;
import com.controllocal.persistence.repositorio.EventoSeguridadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Auditoria de seguridad (Plan S0 §6.3), con la <b>regla de higiene</b> como
 * eje: ni contrasenas, ni hashes, ni tokens, ni secretos en
 * {@code detalle_json}. Una auditoria que filtra secretos es un agujero con
 * sello de calidad, y el sello lo pone justamente que exista este test.
 */
class EventosSeguridadTest {

    private static final long ORG = 1L;

    private final EventoSeguridadRepository repositorio = mock(EventoSeguridadRepository.class);
    private final EventosSeguridad eventos = new EventosSeguridad(repositorio);

    // ------------------------------------------------------------------
    // 1. Regla de higiene
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("higiene del detalle")
    class Higiene {

        @Test
        @DisplayName("ninguna clave sospechosa de llevar un secreto llega a la BD")
        void ningunSecretoLlegaALaBase() {
            Map<String, Object> venenoso = new LinkedHashMap<>();
            venenoso.put("contrasena", "Admin2026");
            venenoso.put("contrasenaNueva", "Otra2026");
            venenoso.put("password", "hunter2");
            venenoso.put("passwordHash", "pbkdf2$120000$sal$hash");
            venenoso.put("contrasenaHash", "pbkdf2$...");
            venenoso.put("token", "eyJhbGciOiJIUzI1NiJ9...");
            venenoso.put("jwt", "eyJ...");
            venenoso.put("Authorization", "Bearer eyJ...");
            venenoso.put("secretoTotp", "JBSWY3DPEHPK3PXP");
            venenoso.put("mfaSecret", "JBSWY3DPEHPK3PXP");
            venenoso.put("salt", "c2FsdA==");
            venenoso.put("cookie", "SESSION=abc");
            venenoso.put("claveRecuperacion", "123456");
            venenoso.put("credencialHash", "x");

            String json = EventosSeguridad.serializarSinSecretos(venenoso);

            // Todas cayeron: no queda ni un par, asi que el detalle es null.
            assertNull(json, "ninguna de esas claves puede sobrevivir: " + json);
        }

        @Test
        @DisplayName("lo que NO es secreto sí se conserva: la auditoría tiene que servir")
        void loQueNoEsSecretoSeConserva() {
            String json = EventosSeguridad.serializarSinSecretos(
                    Map.of("fallos", 7));

            assertNotNull(json);
            assertTrue(json.contains("\"fallos\":\"7\""), json);
        }

        @Test
        @DisplayName("la lista negra compara en minusculas y por contencion")
        void laListaNegraEsPorContencionYSinCajas() {
            // 'contrasenaNueva' no esta en la lista; cae por contener
            // 'contrasena'. Y 'PASSWORD' cae aunque venga en mayusculas.
            assertTrue(EventosSeguridad.esProhibida("contrasenaNueva"));
            assertTrue(EventosSeguridad.esProhibida("PASSWORD"));
            assertTrue(EventosSeguridad.esProhibida("miTokenDeAcceso"));
            assertFalse(EventosSeguridad.esProhibida("fallos"));
            assertFalse(EventosSeguridad.esProhibida("dimension"));
        }

        @Test
        @DisplayName("se descarta, no se enmascara")
        void seDescartaNoSeEnmascara() {
            String json = EventosSeguridad.serializarSinSecretos(
                    new LinkedHashMap<>(Map.of("contrasena", "Admin2026", "fallos", 3)));

            // Un "***" confirmaria que el campo existia y no aporta nada.
            assertFalse(json.contains("contrasena"), json);
            assertFalse(json.contains("*"), json);
            assertTrue(json.contains("fallos"), json);
        }

        @Test
        @DisplayName("las comillas y los saltos de linea no rompen el JSON")
        void elJsonNoSeRompeConComillas() {
            String json = EventosSeguridad.serializarSinSecretos(
                    Map.of("motivo", "dijo \"hola\"\ny se fue"));

            assertTrue(json.startsWith("{") && json.endsWith("}"), json);
            assertFalse(json.contains("\n"), json);
        }
    }

    // ------------------------------------------------------------------
    // 2. Lo que se graba
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un login fallido SIN persona ni credencial tambien se registra")
    void unLoginFallidoAnonimoSeRegistra() {
        eventos.registrar(EventoSeguridad.LOGIN_FALLIDO, EventoSeguridad.RESULTADO_FALLO,
                EventosSeguridad.Contexto.anonimo(ORG, "10.0.0.9", "curl/8"));

        ArgumentCaptor<EventoSeguridad> captor = ArgumentCaptor.forClass(EventoSeguridad.class);
        verify(repositorio).save(captor.capture());
        EventoSeguridad evento = captor.getValue();

        // Es justo el caso que mas interesa: alguien probando un usuario que
        // no existe. Si exigieramos persona, no se registraria nada.
        assertEquals(EventoSeguridad.LOGIN_FALLIDO, evento.getTipo());
        assertNull(evento.getIdPersona());
        assertNull(evento.getIdCredencial());
        assertEquals(ORG, evento.getOrganizacionId());
        assertEquals("10.0.0.9", evento.getIp());
        assertNotNull(evento.getFecha());
    }

    @Test
    @DisplayName("el agente de usuario se recorta a lo que cabe en la columna")
    void elAgenteSeRecorta() {
        eventos.registrar(EventoSeguridad.LOGIN_OK, EventoSeguridad.RESULTADO_OK,
                EventosSeguridad.Contexto.anonimo(ORG, "10.0.0.9", "x".repeat(500)));

        ArgumentCaptor<EventoSeguridad> captor = ArgumentCaptor.forClass(EventoSeguridad.class);
        verify(repositorio).save(captor.capture());
        // La cabecera la escribe el cliente: sin recorte, una peticion basta
        // para tumbar la insercion.
        assertEquals(300, captor.getValue().getAgenteUsuario().length());
    }
}
