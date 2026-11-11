package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ConteoPorBroker;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.BrokerService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.soporte.UsuariosInternos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class BrokerServiceImplTest {

    private static final long ORG = 1L;
    private static final long ADMIN = 10L;

    private final DetalleBrokerRepository brokers = mock(DetalleBrokerRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final SupervisionAgenteRepository supervisiones =
            mock(SupervisionAgenteRepository.class);
    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final UsuariosInternos usuarios = mock(UsuariosInternos.class);
    private final BrokerServiceImpl service = new BrokerServiceImpl(
            brokers, agentes, supervisiones, personas, usuarios);

    private final Actor admin = new Actor(ORG, 1L, ADMIN, "TENANT_ADMIN");
    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");
    private final Map<Long,String> estadosLocales=new HashMap<>();

    @BeforeEach void membresias() {
        when(usuarios.estadoLocalDelRol(anyLong(),anyLong(),any())).thenAnswer(i->estadosLocales.getOrDefault(i.getArgument(1),"A"));
        when(usuarios.estadosLocalesDelRol(anyLong(),anyCollection(),any())).thenAnswer(i->{
            Map<Long,String> r=new HashMap<>();for(Long id:(Collection<Long>)i.getArgument(1))r.put(id,estadosLocales.getOrDefault(id,"A"));return r;
        });
    }

    @Test
    void listaPaginadaConCredencialYConteoEnLote() {
        DetalleBroker broker = broker(20L, 2L, false);
        CuentaAcceso credencial = credencial(broker.getRol().getPersona(), "rsalas", "A");
        when(brokers.pagina(eq(ORG), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(broker)));
        when(usuarios.cuentasPorPersona(ORG, List.of(2L)))
                .thenReturn(Map.of(2L, credencial));
        when(supervisiones.contarActivasPorBrokers(ORG, List.of(20L)))
                .thenReturn(List.of(conteo(20L, 3)));

        var pagina = service.listar(1, 50, agente);

        assertEquals(1, pagina.total());
        assertEquals("rsalas", pagina.items().getFirst().usuario());
        assertEquals(3, pagina.items().getFirst().agentesACargo());
    }

    @Test
    void cualquierSesionPuedeObtenerUnBroker() {
        DetalleBroker broker = broker(20L, 2L, false);
        when(brokers.buscarFicha(ORG, 20L)).thenReturn(Optional.of(broker));
        when(usuarios.cuenta(ORG, 2L))
                .thenReturn(credencial(broker.getRol().getPersona(), "rsalas", "A"));
        when(supervisiones.contarActivasPorBrokers(ORG, List.of(20L)))
                .thenReturn(List.of());

        assertEquals(20L, service.obtener(20L, agente).id());
    }

    @Test
    void laCapacidadAdministrativaSeLeeDeLaPertenenciaLocal() {
        DetalleBroker broker=broker(20L,2L,false);
        when(brokers.buscarFicha(ORG,20L)).thenReturn(Optional.of(broker));
        when(usuarios.cuenta(ORG,2L)).thenReturn(credencial(broker.getRol().getPersona(),"rsalas","A"));
        when(usuarios.administracionLocal(ORG,2L)).thenReturn(true);

        assertTrue(service.obtener(20L,admin).esAdministrador());
        verify(usuarios).administracionLocal(ORG,2L);
    }

    @Test
    void obtenerUnoInexistenteResponde404() {
        when(brokers.buscarFicha(ORG, 999L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class,
                () -> service.obtener(999L, agente));
    }





    /**
     * §2.5: una organizacion puede tener los administradores que necesite. La
     * regla anterior —"solo debe existir un broker administrador"— convertia un
     * olvido de contrasena en una caida de gobierno (H-04).
     *
     * <p>Lo que sigue siendo unico es el BOOLEANO heredado que lee GlassFish
     * (uq_broker_admin_unico, vivo hasta V36): el segundo administrador gobierna
     * por su membresia y no carga la marca.
     */


    @Test
    void elPutSoloTocaLosCamposQueElCablePermite() {
        prepararAdmin();
        DetalleBroker objetivo = broker(20L, 2L, false);
        CuentaAcceso credencial = credencial(
                objetivo.getRol().getPersona(), "rsalas", "A");
        when(brokers.buscarFicha(ORG, 20L)).thenReturn(Optional.of(objetivo));
        when(usuarios.cuenta(ORG, 2L)).thenReturn(credencial);
        when(supervisiones.contarActivasPorBrokers(ORG, List.of(20L)))
                .thenReturn(List.of());

        var ficha = service.actualizar(20L, new BrokerService.DatosBroker(
                "Ricardo Editado", "999111222", "nuevo@test.pe", "Nueva zona"), admin);

        assertEquals("Ricardo Editado", ficha.nombre());
        assertEquals("999111222", ficha.telefono());
        assertEquals("Nueva zona", ficha.zona());
        assertEquals("A", ficha.estadoAdministrativo());
        assertEquals("D", ficha.tipoDocumento());
        assertEquals("11111111", ficha.numeroDocumento());
        assertEquals("rsalas", ficha.usuario());
        assertEquals("BRK-020", ficha.codigoBroker());
        assertFalse(ficha.esAdministrador());
        verify(personas).save(objetivo.getRol().getPersona());
        assertEquals("A",credencial.getEstado());
    }

    @Test
    void agentesDelBrokerSalenSinContadoresComerciales() {
        DetalleBroker broker = broker(20L, 2L, false);
        when(brokers.buscarFicha(ORG, 20L)).thenReturn(Optional.of(broker));
        when(usuarios.cuenta(ORG, 2L))
                .thenReturn(credencial(broker.getRol().getPersona(), "rsalas", "A"));
        when(supervisiones.agentesSupervisados(ORG, 20L))
                .thenReturn(List.of(30L));
        DetalleAgente agente = agente(30L, 3L);
        when(agentes.buscarFichas(ORG, List.of(30L))).thenReturn(List.of(agente));
        when(usuarios.cuentasPorPersona(ORG, List.of(3L)))
                .thenReturn(Map.of(3L,
                        credencial(agente.getRol().getPersona(), "vmora", "A")));

        var ficha = service.agentes(20L, this.agente).getFirst();

        assertEquals("vmora", ficha.usuario());
        assertNull(ficha.captacionesActivas());
        assertNull(ficha.operacionesActivas());
    }



    private void prepararAdmin() {
        DetalleBroker admin = broker(ADMIN, 1L, true);
        when(brokers.buscarFicha(ORG, ADMIN)).thenReturn(Optional.of(admin));
        when(usuarios.cuenta(ORG, 1L))
                .thenReturn(credencial(admin.getRol().getPersona(),
                        "admin@controllocal.test", "A"));
    }

    @Test
    void editarPertenenciaNoPermiteAlBrokerSuplantarGobierno() {
        assertThrows(AccesoNoAutorizadoException.class, () -> service.actualizar(20L, null, agente));
    }

    private static DetalleBroker broker(long id, long idPersona, boolean esAdmin) {
        DetalleBroker broker = new DetalleBroker();
        broker.setOrganizacionId(ORG);
        broker.setRol(rol(id, persona(idPersona,
                esAdmin ? "Administrador" : "Ricardo Salas"), TipoRol.BROKER));
        broker.setCodigoBroker(esAdmin ? "BRK-ADM-001" : "BRK-020");
        broker.setZona("Lima");
        broker.setFechaDesignacion(LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(broker, "id", id);
        return broker;
    }

    private static DetalleAgente agente(long id, long idPersona) {
        DetalleAgente agente = new DetalleAgente();
        agente.setOrganizacionId(ORG);
        agente.setRol(rol(id, persona(idPersona, "Valentina Mora"), TipoRol.AGENTE));
        agente.setCodigoAgente("AGE-001");
        agente.setZonaAsignada("Lima");
        agente.setFechaIngreso(LocalDate.of(2024, 2, 1));
        agente.setEstadoOperativo("D");
        ReflectionTestUtils.setField(agente, "id", id);
        return agente;
    }

    private static Persona persona(long id, String nombre) {
        Persona persona = new Persona();
        persona.setOrganizacionId(ORG);
        persona.setTipoPersona("N");
        persona.setTipoDocumento("D");
        persona.setNumeroDocumento("11111111");
        persona.setNombresORazonSocial(nombre);
        persona.setEstado("A");
        ReflectionTestUtils.setField(persona, "id", id);
        return persona;
    }

    private static PersonaRol rol(long id, Persona persona, TipoRol tipo) {
        PersonaRol rol = new PersonaRol();
        rol.setOrganizacionId(ORG);
        rol.setPersona(persona);
        rol.setTipoRol(tipo);
        rol.setVigenciaDesde(LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(rol, "id", id);
        return rol;
    }


    private static CuentaAcceso credencial(
            Persona persona, String usuario, String estado) {
        CuentaAcceso credencial = new CuentaAcceso();
        credencial.setId(90L);
        credencial.setCorreoPropio(usuario + "@cuenta.example");
        credencial.setCorreoConfirmadoEn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        credencial.setNombreUsuario(usuario);
        credencial.setContrasenaHash("pbkdf2$100000$x$x");
        credencial.setEstado(estado);
        return credencial;
    }

    private static ConteoPorBroker conteo(long id, long total) {
        return new ConteoPorBroker() {
            @Override
            public Long getIdBroker() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
