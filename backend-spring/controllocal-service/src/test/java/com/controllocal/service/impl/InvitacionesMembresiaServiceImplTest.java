package com.controllocal.service.impl;

import com.controllocal.domain.organizacion.*;
import com.controllocal.domain.persona.*;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvitacionesMembresiaServiceImplTest {
    private static final long ORG=12, CUENTA=90;
    private static final String CORREO="titular@example.test";
    private static final Instant AHORA=Instant.parse("2026-10-27T15:00:00Z");
    private final InvitacionMembresiaRepository repo=mock(InvitacionMembresiaRepository.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final PersonaRolRepository roles=mock(PersonaRolRepository.class);
    private final PersonaRepository personas=mock(PersonaRepository.class);
    private final OrganizacionRepository organizaciones=mock(OrganizacionRepository.class);
    private final DetalleAgenteRepository agentes=mock(DetalleAgenteRepository.class);
    private final DetalleBrokerRepository brokers=mock(DetalleBrokerRepository.class);
    private final SupervisionAgenteRepository supervisiones=mock(SupervisionAgenteRepository.class);
    private final UsuariosInternos usuarios=mock(UsuariosInternos.class);
    private final EventosSeguridad eventos=mock(EventosSeguridad.class);
    private final EntityManager em=mock(EntityManager.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final ComandosIdempotentes comandos=new ComandosIdempotentes(registros);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final Map<String,ComandoIdempotente> ledger=new HashMap<>();
    private final InvitacionesMembresiaServiceImpl servicio=servicioEn(AHORA);
    private final Actor admin=new Actor(ORG,20,30,Actor.TENANT_ADMIN);
    private InvitacionMembresia invitacion;

    @BeforeEach void preparar() {
        when(registros.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString()))
                .thenAnswer(a->Optional.ofNullable(ledger.get(a.getArgument(0)+"/"+a.getArgument(1))));
        when(registros.save(any())).thenAnswer(a->{ComandoIdempotente c=a.getArgument(0);
            ledger.put(c.getOrganizacionId()+"/"+c.getClaveIdempotencia(),c);return c;});
        when(repo.gobiernoVigente(ORG,20,30)).thenReturn(true);
        Organizacion org=mock(Organizacion.class);
        when(org.estaActiva()).thenReturn(true);when(org.getModalidad()).thenReturn("EMPRESARIAL");
        when(organizaciones.findById(ORG)).thenReturn(Optional.of(org));
        when(repo.saveAndFlush(any())).thenAnswer(a->{InvitacionMembresia i=a.getArgument(0);
            ReflectionTestUtils.setField(i,"id",100L);return i;});
        var identidad=new ContextosService.IdentidadAutenticada(CUENTA,"referencia-global",CORREO,null);
        when(contextos.exigirIdentidad("sid",7)).thenReturn(new ContextosService.SesionAccesoDTO(
                "sid",7,identidad,null,AHORA.minusSeconds(20),AHORA.plusSeconds(200),EstadoDeAcceso.SIN_RESTRICCIONES));
        when(contextos.comprobarSesion("sid",7,null,null)).thenReturn(new ContextosService.SesionAccesoDTO(
                "sid",7,identidad,null,AHORA.minusSeconds(20),AHORA.plusSeconds(200),EstadoDeAcceso.SIN_RESTRICCIONES));
        when(miembros.cuentasActivasDePersona(anyLong(),anyLong())).thenAnswer(a->List.of(a.<Long>getArgument(0)==99L?801L:800L));
        when(cuentas.bloquearCambioAcceso(anyLong())).thenAnswer(a->{var c=new com.controllocal.domain.seguridad.CuentaAcceso();c.setId(a.getArgument(0));return Optional.of(c);});
        when(contextos.exigirContexto(eq("sidgov"),eq(8L),any())).thenAnswer(a->sesionGobierno(a.getArgument(2)));
        when(cuentas.vista(CUENTA)).thenReturn(Optional.of(cuenta(CORREO,AHORA.minusSeconds(40))));
        invitacion=new InvitacionMembresia();invitacion.setOrganizacionId(ORG);invitacion.setReferencia(UUID.randomUUID());
        ReflectionTestUtils.setField(invitacion,"id",100L);invitacion.setCorreoCuenta(CORREO);
        invitacion.setRolPrincipal("BROKER");invitacion.setCreadaEn(AHORA.minusSeconds(60));
        invitacion.setVenceEn(AHORA.plusSeconds(300));invitacion.setMotivo("Alta autorizada de equipo.");
        when(repo.bloquearParaAceptar(invitacion.getReferencia())).thenReturn(Optional.of(invitacion));
        var snapshot=mock(InvitacionMembresiaRepository.ContextoInvitacion.class);
        when(snapshot.getOrganizacion()).thenReturn(ORG);when(snapshot.getCorreo()).thenReturn(CORREO);
        when(snapshot.getPrincipal()).thenAnswer(a->invitacion.getRolPrincipal());
        when(snapshot.getSupervisor()).thenAnswer(a->invitacion.getIdBrokerSupervisor());
        when(snapshot.getAceptadaEn()).thenAnswer(a->invitacion.getAceptadaEn());
        when(snapshot.getRetiradaEn()).thenAnswer(a->invitacion.getRetiradaEn());
        when(repo.contextoDe(invitacion.getReferencia())).thenReturn(Optional.of(snapshot));
        when(personas.saveAndFlush(any())).thenAnswer(a->{Persona p=a.getArgument(0);
            ReflectionTestUtils.setField(p,"id",401L);return p;});
        when(usuarios.vincularPertenenciaAceptada(eq(ORG),eq(CUENTA),any(),any(),anyBoolean()))
                .thenAnswer(a->{Persona p=a.getArgument(2);TipoRol principal=a.getArgument(3);
                    PersonaRol r=null;if(principal!=null){r=new PersonaRol();r.setPersona(p);r.setOrganizacionId(ORG);r.setTipoRol(principal);
                        ReflectionTestUtils.setField(r,"id",501L);}
                    UsuarioOrganizacion m=new UsuarioOrganizacion();ReflectionTestUtils.setField(m,"id",701L);
                    return new UsuariosInternos.Vinculacion(r,m);});
        when(repo.consumir(any(),eq(CUENTA),eq(701L),eq(401L),any())).thenReturn(1);
    }

    @Test void unBrokerNoPuedeEmitirPertenenciaAunqueSeSalteHttp() {
        assertThrows(AccesoNoAutorizadoException.class,()->emitir(datos(AHORA.plusSeconds(30)),
                new Actor(ORG,20,30,Actor.BROKER)));
        verifyNoInteractions(contextos,personas,usuarios);
    }
    @Test void vigenciaSeEligeExpresamenteSinTreintaMinutosPorDefecto() {
        assertThrows(ReglaNegocioException.class,()->emitir(datos(null),admin));
        assertThrows(ReglaNegocioException.class,()->emitir(datos(AHORA),admin));
        var r=emitir(datos(AHORA.plusSeconds(97)),admin);
        assertEquals(AHORA.plusSeconds(97),r.venceEn());
    }
    @Test void noEjecutaGobiernoConUnaPruebaDeOtraSesionORevision() {
        var correcta=ContextoSesion.de(sesionGobierno(admin));
        var alterada=new ContextoSesion("otra-identidad",correcta.expiraEnEpochSeconds(),correcta.referenciaSesion(),correcta.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.emitir(datos(AHORA.plusSeconds(30)),admin,alterada,"prueba-alterada"));
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void emitirNoCreaCuentaPersonaNiDatosFicticios() {
        var r=emitir(datos(AHORA.plusSeconds(97)),admin);
        var captor=org.mockito.ArgumentCaptor.forClass(InvitacionMembresia.class);
        verify(repo).saveAndFlush(captor.capture());var i=captor.getValue();
        assertNull(i.getNombre());assertNull(i.getNumeroDocumento());assertEquals(CORREO,i.getCorreoCuenta());
        assertNotNull(UUID.fromString(r.referencia()));
        verifyNoInteractions(personas,usuarios);verify(cuentas,never()).save(any());
    }
    @Test void unEspacioIndividualNoSeConvierteEnEmpresaPorInvitar() {
        var org=organizaciones.findById(ORG).orElseThrow();when(org.getModalidad()).thenReturn("INDIVIDUAL");
        assertThrows(ReglaNegocioException.class,()->emitir(datos(AHORA.plusSeconds(97)),admin));
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void conocerLaReferenciaNoPermiteAceptarConOtroCorreo() {
        when(cuentas.vista(CUENTA)).thenReturn(Optional.of(cuenta("otro@example.test",AHORA.minusSeconds(20))));
        assertThrows(NoEncontradoException.class,()->aceptar());
        verifyNoInteractions(personas,usuarios);
    }
    @Test void cuentaSinCorreoVerificadoNoPuedeAceptar() {
        when(cuentas.vista(CUENTA)).thenReturn(Optional.of(cuenta(CORREO,null)));
        assertThrows(NoEncontradoException.class,()->aceptar());
        verify(repo,never()).bloquearParaAceptar(any());
    }
    @Test void aceptaLaDecisionDeOrgAunqueElEmisorYaNoGobierneSinModificarCuenta() {
        var r=aceptar();
        assertEquals(ORG,r.idOrganizacion());assertEquals(701,r.idMembresia());
        verify(repo,never()).gobiernoVigente(anyLong(),anyLong(),anyLong());
        verify(cuentas,never()).save(any());verify(cuentas,never()).saveAndFlush(any());
        var contexto=org.mockito.ArgumentCaptor.forClass(EventosSeguridad.Contexto.class);
        verify(eventos).registrarEnTransaccion(eq("ACCESO_TENANT_CONCEDIDO"),eq("OK"),contexto.capture(),anyMap());
        assertNull(contexto.getValue().idPersona());assertNull(contexto.getValue().rolEfectivo());
        assertEquals(CUENTA,contexto.getValue().idCuenta());
    }
    @Test void laCuentaConOtraPertenenciaNoRecibeUnaSegunda() {
        when(usuarios.vincularPertenenciaAceptada(eq(ORG),eq(CUENTA),any(),any(),anyBoolean()))
                .thenThrow(new ReglaNegocioException("Ya existe una pertenencia activa."));
        assertThrows(ReglaNegocioException.class,()->aceptar());
        verify(repo,never()).consumir(any(),anyLong(),anyLong(),anyLong(),any());
    }
    @Test void retiradaOExpiradaNoCreaPersona() {
        invitacion.setRetiradaEn(AHORA.minusSeconds(1));
        assertThrows(NoEncontradoException.class,()->aceptar());
        invitacion.setRetiradaEn(null);invitacion.setVenceEn(AHORA);
        assertThrows(NoEncontradoException.class,()->aceptar());verifyNoInteractions(personas,usuarios);
    }
    @Test void repetirLaAceptacionSoloDevuelveSuResultadoSinRevincular() {
        invitacion.setAceptadaEn(AHORA.minusSeconds(1));invitacion.setIdCuentaAceptante(CUENTA);
        invitacion.setIdMembresiaAceptada(701L);invitacion.setIdPersonaAceptada(401L);invitacion.setIdRolAceptado(501L);
        assertEquals(701,aceptar().idMembresia());verifyNoInteractions(usuarios,personas,brokers);
    }
    @Test void elVencimientoFinalDeLaBaseRechazaAntesDeAuditarExito() {
        when(repo.consumir(any(),eq(CUENTA),eq(701L),eq(401L),any())).thenReturn(0);
        assertThrows(NoEncontradoException.class,()->aceptar());verifyNoInteractions(eventos);
    }
    @Test void retirarConservaReferenciaMotivoActorYNoDaDeBajaACuenta() {
        var resultado=retirar(invitacion.getReferencia().toString(),"Retiro expreso.",admin);
        assertEquals(AHORA,resultado.retiradaEn());assertEquals("Retiro expreso.",invitacion.getMotivoRetiro());
        assertEquals(20,invitacion.getIdPersonaRetiro());assertEquals(30,invitacion.getIdRolRetiro());
        verifyNoInteractions(usuarios);verify(cuentas,never()).save(any());
    }
    @Test void gobiernoDeOtraOrgNoPuedeRetirarPorReferencia() {
        Actor ajeno=new Actor(99,40,50,Actor.TENANT_ADMIN);when(repo.gobiernoVigente(99,40,50)).thenReturn(true);
        assertThrows(NoEncontradoException.class,()->retirar(invitacion.getReferencia().toString(),"Motivo",ajeno));
        assertNull(invitacion.getRetiradaEn());
    }
    @Test void retirarUnaInvitacionAceptadaNoSustituyeLaBajaFormal() {
        invitacion.setAceptadaEn(AHORA.minusSeconds(1));
        assertThrows(ConflictoException.class,()->retirar(invitacion.getReferencia().toString(),"Motivo",admin));
        verifyNoInteractions(usuarios);
    }
    @Test void reingresoUsaRelacionCuentaOrgSinFusionarPorDocumento() {
        UsuarioOrganizacion anterior=new UsuarioOrganizacion();anterior.setIdPersona(401L);
        when(miembros.buscarPorCuenta(ORG,CUENTA)).thenReturn(Optional.of(anterior));
        Persona p=new Persona();p.setOrganizacionId(ORG);ReflectionTestUtils.setField(p,"id",401L);
        when(personas.findByOrganizacionIdAndId(ORG,401)).thenReturn(Optional.of(p));
        assertEquals(401,aceptar().idPersona());
        verify(personas,never()).saveAndFlush(any());
        verify(usuarios).vincularPertenenciaAceptada(ORG,CUENTA,p,TipoRol.BROKER,false);
    }
    @Test void unaPertenenciaAdministrativaNoCreaUnBrokerFicticio() {
        invitacion.setRolPrincipal(null);invitacion.setAdministrar(true);
        var resultado=aceptar();assertNull(resultado.idRolOperativo());assertTrue(resultado.administrar());
        verifyNoInteractions(brokers,agentes,supervisiones);
    }
    @Test void unaReferenciaLocalDiferenteNoSustituyeLaPersonaHistorica() {
        UsuarioOrganizacion anterior=new UsuarioOrganizacion();anterior.setIdPersona(401L);
        when(miembros.buscarPorCuenta(ORG,CUENTA)).thenReturn(Optional.of(anterior));invitacion.setIdPersonaLocal(999L);
        assertThrows(ConflictoException.class,()->aceptar());verifyNoInteractions(usuarios);
    }

    private InvitacionesMembresiaService.PertenenciaAceptada aceptar() {
        return servicio.aceptar(invitacion.getReferencia().toString(),"sid",7);
    }
    private InvitacionesMembresiaService.InvitacionEmitida emitir(InvitacionesMembresiaService.DatosInvitacion datos,Actor actor) {
        return servicio.emitir(datos,actor,ContextoSesion.de(sesionGobierno(actor)),"emision-prueba");
    }
    private InvitacionesMembresiaService.InvitacionRetirada retirar(String ref,String motivo,Actor actor) {
        return servicio.retirar(ref,motivo,actor,ContextoSesion.de(sesionGobierno(actor)));
    }
    private static ContextosService.SesionAccesoDTO sesionGobierno(Actor a) {
        long cuenta=a.idOrganizacion()==99L?801L:800L;
        var identidad=new ContextosService.IdentidadAutenticada(cuenta,"gobierno","gobierno@example.test",null);
        return new ContextosService.SesionAccesoDTO("sidgov",8,identidad,new ContextosService.Contexto("ctx",
                a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),"EMPRESARIAL","Empresa"),
                AHORA.minusSeconds(20),AHORA.plusSeconds(200),EstadoDeAcceso.SIN_RESTRICCIONES);
    }
    private static InvitacionesMembresiaService.DatosInvitacion datos(Instant vence) {
        return new InvitacionesMembresiaService.DatosInvitacion("  TITULAR@example.test ",null,"BROKER",false,
                null,null,null,null,vence,"Invitacion de pertenencia autorizada.");
    }
    private static CuentaAccesoFila cuenta(String correo,Instant confirmado) {
        return new CuentaAccesoFila(CUENTA,"referencia-global",correo,confirmado,null,"A",null,false,false);
    }
    private InvitacionesMembresiaServiceImpl servicioEn(Instant ahora) {
        return new InvitacionesMembresiaServiceImpl(repo,contextos,cuentas,miembros,roles,personas,organizaciones,
                agentes,brokers,supervisiones,usuarios,eventos,em,comandos,registros,documentos,Clock.fixed(ahora,ZoneOffset.UTC));
    }

    @Test void emisionRepetidaRestauraElResultadoOriginalAunqueYaVencio() {
        var datos=datos(AHORA.plusSeconds(97));var primero=emitir(datos,admin);
        var repetido=servicioEn(AHORA.plusSeconds(100)).emitir(datos,admin,
                ContextoSesion.de(sesionGobierno(admin)),"emision-prueba");
        assertEquals(primero,repetido);verify(repo,times(1)).saveAndFlush(any());
        verify(eventos,times(1)).registrarEnTransaccion(eq("INVITACION_EMITIDA"),any(),any(),anyMap());
    }
    @Test void mismaClaveConOtroContenidoEsConflictoSinCrearOtraInvitacion() {
        emitir(datos(AHORA.plusSeconds(97)),admin);
        assertThrows(ConflictoException.class,()->emitir(datos(AHORA.plusSeconds(98)),admin));
        verify(repo,times(1)).saveAndFlush(any());
    }
    @Test void laEmisionNoInventaUnaClaveDeComandoSiNoSeAporto() {
        assertThrows(ReglaNegocioException.class,()->servicio.emitir(datos(AHORA.plusSeconds(97)),admin,
                ContextoSesion.de(sesionGobierno(admin)),null));
        verify(repo,never()).saveAndFlush(any());
    }
}
