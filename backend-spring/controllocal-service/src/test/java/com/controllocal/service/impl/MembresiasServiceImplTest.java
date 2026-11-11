package com.controllocal.service.impl;

import com.controllocal.domain.organizacion.*;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.service.soporte.GobiernoOperativo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MembresiasServiceImplTest {
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final SesionAccesoRepository sesiones=mock(SesionAccesoRepository.class);
    private final PersonaRolRepository roles=mock(PersonaRolRepository.class);
    private final GobiernoOperativo gobierno=mock(GobiernoOperativo.class);
    private final EventosSeguridad eventos=mock(EventosSeguridad.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final EntityManager entidades=mock(EntityManager.class);
    private final Clock reloj=Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"),ZoneOffset.UTC);
    private final MembresiasServiceImpl servicio=new MembresiasServiceImpl(miembros,cuentas,sesiones,roles,gobierno,eventos,contextos,entidades,reloj);
    private final Actor actor=new Actor(7,101,301,Actor.TENANT_ADMIN);
    private final ContextosService.SesionAccesoDTO autenticada=new ContextosService.SesionAccesoDTO("a".repeat(43),1,
            new ContextosService.IdentidadAutenticada(10,"gobierno","propio@example.invalid",null),
            new ContextosService.Contexto("b".repeat(64),7,101,301,Actor.TENANT_ADMIN,"EMPRESARIAL","Org"),
            reloj.instant(),reloj.instant().plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
    private final ContextoSesion sesion=ContextoSesion.de(autenticada);
    private UsuarioOrganizacion miembro;private Organizacion org;

    @BeforeEach void preparar() {
        miembro=new UsuarioOrganizacion();ReflectionTestUtils.setField(miembro,"id",42L);ReflectionTestUtils.setField(miembro,"version",2L);
        miembro.setOrganizacionId(7L);miembro.setIdUsuario(20L);miembro.setIdPersona(102L);miembro.setRol("AGENTE");
        miembro.setAltaRealEn(OffsetDateTime.parse("2026-10-20T18:00:00Z"));
        org=new Organizacion();org.setModalidad("EMPRESARIAL");
        var gov=new UsuarioOrganizacion();ReflectionTestUtils.setField(gov,"id",11L);
        when(miembros.paraBaja(7,42)).thenAnswer(i->Optional.of(fila()));
        when(miembros.cuentaDeGobiernoActual(7,101,301,LocalDate.of(2026,10,27))).thenReturn(Optional.of(10L));
        when(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor)).thenReturn(autenticada);
        when(cuentas.bloquearCambioAcceso(anyLong())).thenReturn(Optional.of(new CuentaAcceso()));
        when(entidades.find(Organizacion.class,7L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(org);
        when(miembros.buscarPorCuenta(7,10)).thenReturn(Optional.of(gov));
        when(miembros.bloquearParaContexto(11,10)).thenReturn(Optional.of(gov));
        when(roles.bloquearParaContexto(301)).thenReturn(Optional.of(new PersonaRol()));
        when(miembros.bloquearParaBaja(7,42)).thenReturn(Optional.of(miembro));
        when(miembros.otrosBrokersVigentes(7,42,LocalDate.of(2026,10,27))).thenReturn(1L);
        when(miembros.otrosAdministradoresVigentes(7,42,LocalDate.of(2026,10,27))).thenReturn(1L);
        when(miembros.saveAndFlush(any())).thenAnswer(i->{ReflectionTestUtils.setField(miembro,"version",miembro.getVersion()+1);return miembro;});
    }
    private PertenenciaDeBajaFila fila() {return new PertenenciaDeBajaFila(42,20,7,102L,miembro.getEstado(),miembro.getRol(),miembro.isAdministrar(),miembro.getVersion(),miembro.getAltaRealEn(),miembro.getBajaRealEn());}
    @Test void bajaPersisteFechaRealYMotivoSinModificarCuentaPersonal() {
        var baja=servicio.darBaja(42,2," Fin de pertenencia autorizado ",actor,sesion);
        assertEquals(3,baja.version());assertEquals(reloj.instant(),baja.bajaRealEn());assertEquals("I",miembro.getEstado());
        var orden=inOrder(cuentas,contextos,sesiones,entidades,miembros);
        orden.verify(cuentas).bloquearCambioAcceso(10);orden.verify(cuentas).bloquearCambioAcceso(20);
        orden.verify(contextos).exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
        orden.verify(sesiones).revocarDeMembresia(42);orden.verify(entidades).find(Organizacion.class,7L,LockModeType.PESSIMISTIC_WRITE);
        verify(cuentas,never()).save(any());verify(cuentas,never()).saveAndFlush(any());
        verify(eventos).registrarEnTransaccion(eq("MEMBRESIA_BAJA_FORMAL"),eq("OK"),
                eq(new EventosSeguridad.Contexto(7L,101L,10L,Actor.TENANT_ADMIN,null,null)),
                eq("Fin de pertenencia autorizado"),eq(102L),argThat(d->d.get("version").equals(3L)&&d.get("estadoNuevo").equals("I")));
    }
    @Test void repetirVersionOriginalDevuelveLaMismaBajaSinOtroEvento() {
        miembro.setEstado("I");miembro.setBajaRealEn(OffsetDateTime.now(reloj));ReflectionTestUtils.setField(miembro,"version",3L);
        var primera=servicio.darBaja(42,2,"Baja ya aplicada",actor,sesion);var segunda=servicio.darBaja(42,2,"Baja ya aplicada",actor,sesion);
        assertEquals(primera,segunda);verify(miembros,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }
    @Test void versionAntiguaNoDaBajaAUnReingreso() {
        ReflectionTestUtils.setField(miembro,"version",4L);
        assertThrows(ConflictoException.class,()->servicio.darBaja(42,2,"Solicitud anterior",actor,sesion));
        assertEquals("A",miembro.getEstado());verify(miembros,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }
    @Test void sesionQueCambioDeRevisionNoAutorizaElEfecto() {
        var cambiada=new ContextosService.SesionAccesoDTO(autenticada.referencia(),2,autenticada.identidad(),autenticada.activo(),autenticada.emitidaEn(),autenticada.venceEn(),autenticada.restricciones());
        when(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor)).thenReturn(cambiada);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.darBaja(42,2,"Solicitud",actor,sesion));
        verifyNoInteractions(sesiones,eventos);verify(miembros,never()).saveAndFlush(any());
    }
    @Test void actorBrokerNoAdquiereGobiernoPorOperarComercialmente() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.darBaja(42,2,"Solicitud",new Actor(7,101,201,Actor.BROKER),sesion));
        verifyNoInteractions(miembros,cuentas,sesiones,entidades,eventos);
    }
    @Test void gobiernoRetiradoNoSeAdmitePorElNombreDelRol() {
        when(miembros.cuentaDeGobiernoActual(7,101,301,LocalDate.of(2026,10,27))).thenReturn(Optional.empty());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.darBaja(42,2,"Solicitud",actor,sesion));
        verifyNoInteractions(cuentas,sesiones,eventos);
    }
    @Test void otroTenantNoRevelaNiDaBajaAPertenencia() {
        when(miembros.paraBaja(7,42)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->servicio.darBaja(42,2,"Solicitud",actor,sesion));
        verifyNoInteractions(cuentas,sesiones,eventos);
    }
    @Test void motivoNoSeInventaNiSeAceptaVacio() {
        assertThrows(ReglaNegocioException.class,()->servicio.darBaja(42,2," ",actor,sesion));
        verifyNoInteractions(miembros,cuentas,sesiones,eventos);
    }
    @Test void ultimoBrokerNoSeDaDeBaja() {
        miembro.setRol("BROKER");when(miembros.otrosBrokersVigentes(7,42,LocalDate.of(2026,10,27))).thenReturn(0L);
        assertThrows(ReglaNegocioException.class,()->servicio.darBaja(42,2,"Solicitud",actor,sesion));
        assertEquals("A",miembro.getEstado());verify(miembros,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }
    @Test void ultimoAdministradorNoSeDaDeBaja() {
        miembro.setAdministrar(true);when(miembros.otrosAdministradoresVigentes(7,42,LocalDate.of(2026,10,27))).thenReturn(0L);
        assertThrows(ReglaNegocioException.class,()->servicio.darBaja(42,2,"Solicitud",actor,sesion));
        assertEquals("A",miembro.getEstado());verify(miembros,never()).saveAndFlush(any());
    }
    @Test void brokerConAgentesActivosRequiereReasignacionPrevia() {
        miembro.setRol("BROKER");when(miembros.agentesActivosDeBroker(7,102)).thenReturn(2L);
        assertThrows(ReglaNegocioException.class,()->servicio.darBaja(42,2,"Solicitud",actor,sesion));
        verify(miembros,never()).saveAndFlush(any());verifyNoInteractions(eventos);
    }
}
