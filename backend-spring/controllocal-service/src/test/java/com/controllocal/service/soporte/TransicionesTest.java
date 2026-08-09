package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.service.Actor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TransicionesTest {

    /** Organizacion de legado: el tenant que el backend resuelve para la sesion (V6). */
    private static final long ORG = 1L;

    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);
    private final Transiciones transiciones = new Transiciones(historial);

    /**
     * Maquina de estados minima para el test. Es {@link EntidadDeOrganizacion}
     * porque toda entidad auditable es privada de un tenant: sin eso ni
     * siquiera compila la llamada a aplicar().
     */
    private static final class Maquina extends EntidadDeOrganizacion implements Transicionable {
        private String estado;

        Maquina() {
            setOrganizacionId(ORG);
        }

        @Override
        public String entidadTipo() {
            return "PROPIEDAD";
        }

        @Override
        public String estadoActual() {
            return estado;
        }

        @Override
        public void transicionarA(String nuevoEstado) {
            estado = nuevoEstado;
        }
    }

    @Test
    void iniciarFijaElEstadoInicialSinAuditar() {
        Maquina entidad = new Maquina();

        transiciones.iniciar(entidad, "A");

        assertEquals("A", entidad.estadoActual());
        verifyNoInteractions(historial);
    }

    @Test
    void iniciarRechazaUnaEntidadQueYaTieneEstado() {
        Maquina entidad = new Maquina();
        transiciones.iniciar(entidad, "A");

        assertThrows(IllegalStateException.class, () -> transiciones.iniciar(entidad, "I"));
    }

    @Test
    void aplicarTransicionaYEmiteHistorialConElActor() {
        Maquina entidad = new Maquina();
        transiciones.iniciar(entidad, "A");

        transiciones.aplicar(entidad, 7L, "I", new Actor(ORG, 3L, 30L, "AGENTE"),
                "Desactivación de local por el agente");

        assertEquals("I", entidad.estadoActual());
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        assertEquals(ORG, evento.getValue().getOrganizacionId());
        assertEquals("PROPIEDAD", evento.getValue().getEntidadTipo());
        assertEquals(7L, evento.getValue().getIdEntidad());
        assertEquals("A", evento.getValue().getEstadoAnterior());
        assertEquals("I", evento.getValue().getEstadoNuevo());
        assertEquals(3L, evento.getValue().getIdActor());
        assertEquals("AGENTE", evento.getValue().getTipoRolActor());
        assertEquals("Desactivación de local por el agente", evento.getValue().getMotivo());
    }

    @Test
    void aplicarConElMismoEstadoNoTransicionaNiAudita() {
        Maquina entidad = new Maquina();
        transiciones.iniciar(entidad, "A");

        transiciones.aplicar(entidad, 7L, "A", new Actor(ORG, 3L, 30L, "AGENTE"), "sin cambio");

        assertEquals("A", entidad.estadoActual());
        verifyNoInteractions(historial);
    }

    /**
     * H-09 cerrado: la auditoria decia BROKER cuando actuaba el administrador,
     * asi que el rastro no distinguia gobierno de operacion. Ya no traduce.
     */
    @Test
    void elAdminAuditaComoTenantAdminYNoComoBroker() {
        Maquina entidad = new Maquina();
        transiciones.iniciar(entidad, "A");

        transiciones.aplicar(entidad, 7L, "I", new Actor(ORG, 1L, 10L, "TENANT_ADMIN"), "revision");

        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        assertEquals("TENANT_ADMIN", evento.getValue().getTipoRolActor());
    }

    @Test
    void sinActorQuedaComoEventoDeSistema() {
        Maquina entidad = new Maquina();
        transiciones.iniciar(entidad, "A");

        transiciones.aplicar(entidad, 7L, "I", null, "job nocturno");

        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        assertNull(evento.getValue().getIdActor());
        assertNull(evento.getValue().getTipoRolActor());
        // Sin actor la fila sigue teniendo tenant: lo aporta la entidad auditada.
        assertEquals(ORG, evento.getValue().getOrganizacionId());
    }
}
