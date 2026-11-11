package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.*;
import com.controllocal.domain.persona.*;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.query.EncargoParaReasignacion;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** C2 conserva lectura/aislamiento y prueba el único CAS de gobierno; el ciclo se prueba en EncargoService. */
class CaptacionServiceImplTest {
    private static final long ORG=1;
    private final Actor broker=new Actor(ORG,2,23,Actor.BROKER);
    private final Actor agente=new Actor(ORG,3,30,Actor.AGENTE);
    private final Actor admin=new Actor(ORG,4,40,Actor.TENANT_ADMIN);
    private final CaptacionRepository caps=mock(CaptacionRepository.class);
    private final ReasignacionCaptacionRepository cambios=mock(ReasignacionCaptacionRepository.class);
    private final DetalleAgenteRepository agentes=mock(DetalleAgenteRepository.class);
    private final DetalleBrokerRepository brokers=mock(DetalleBrokerRepository.class);
    private final FotoPropiedadRepository fotos=mock(FotoPropiedadRepository.class);
    private final SupervisionAgenteRepository supervisiones=mock(SupervisionAgenteRepository.class);
    private final Alcances alcances=new Alcances(supervisiones);
    private final DocumentoEncargoRepository documentos=mock(DocumentoEncargoRepository.class);
    private final ReglasDeResponsableEncargo reglas=new ReglasDeResponsableEncargo(documentos);
    private final OrganizacionRepository organizaciones=mock(OrganizacionRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final AccesoEncargo acceso=new AccesoEncargo(caps,alcances,organizaciones,miembros,cuentas,contextos);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final ComandosIdempotentes comandos=new ComandosIdempotentes(registros);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final LectorPorAutoridad lector=mock(LectorPorAutoridad.class);
    private final EntityManager em=mock(EntityManager.class);
    private final org.springframework.context.ApplicationEventPublisher publicadorCambios=mock(org.springframework.context.ApplicationEventPublisher.class);
    private final Documentos json=new Documentos(JsonMapper.builder().findAndAddModules().build());
    private final Map<String,ComandoIdempotente> resultados=new HashMap<>();
    private final Map<Long,DetalleAgente> equipo=new HashMap<>();
    private final CaptacionServiceImpl servicio=new CaptacionServiceImpl(caps,cambios,agentes,brokers,fotos,
            alcances,lector,new ElegibilidadDeResponsable(agentes),acceso,reglas,comandos,registros,eventos,json,em,publicadorCambios);
    private Captacion cap;

    @BeforeEach void preparar() {
        equipo.put(30L,agente(30));equipo.put(31L,agente(31));
        when(supervisiones.agentesSupervisados(ORG,23)).thenReturn(List.of(30L,31L));
        when(miembros.cuentasActivasDePersona(anyLong(),anyLong())).thenAnswer(i->List.of(100L+i.getArgument(1,Long.class)));
        when(cuentas.bloquearCambioAcceso(anyLong())).thenReturn(Optional.of(new CuentaAcceso()));
        when(contextos.exigirContexto(anyString(),anyLong(),any())).thenAnswer(i->sesion(i.getArgument(2)));
        when(agentes.findById(anyLong())).thenAnswer(i->Optional.ofNullable(equipo.get(i.getArgument(0))));
        when(agentes.bloquearParaGobierno(eq(ORG),anyLong())).thenAnswer(i->Optional.ofNullable(equipo.get(i.getArgument(1))));
        when(agentes.esCandidatoAResponsable(anyLong(),anyBoolean(),anyLong(),anyLong(),any(),anyLong())).thenReturn(true);
        when(caps.cuentaDelRol(eq(ORG),anyLong())).thenAnswer(i->Optional.of(100L+i.getArgument(1,Long.class)));
        when(registros.findByOrganizacionIdAndClaveIdempotencia(eq(ORG),anyString())).thenAnswer(i->Optional.ofNullable(resultados.get(i.getArgument(1))));
        when(registros.save(any())).thenAnswer(i->{ComandoIdempotente c=i.getArgument(0);resultados.put(c.getClaveIdempotencia(),c);return c;});
        when(lector.de(anyLong(),any())).thenReturn(ValoresGobernados.vacio());
        when(lector.deVarias(anyLong(),any())).thenReturn(Map.of());
        when(lector.gobernadosDeVarias(any())).thenReturn(Map.of());
        when(fotos.deLaPropiedad(anyLong(),anyLong())).thenReturn(List.of());
        var p=new Propiedad();p.setOrganizacionId(ORG);p.setCodigo("P-C2");p.setDireccion("Calle de prueba 123");
        p.setDistrito("Surco");p.setMetraje(new BigDecimal("80"));ReflectionTestUtils.setField(p,"id",7L);
        cap=new Captacion();cap.setOrganizacionId(ORG);cap.setCodigoCaptacion("C2-LECTURA");
        cap.setFechaCaptacion(LocalDate.of(2026,10,28));cap.setMotivoOperacion("A");cap.setPropiedad(p);cap.setAgente(equipo.get(30L));
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(cap,Captacion.EN_PREPARACION);
        ReflectionTestUtils.setField(cap,"id",9L);
        when(caps.buscarFicha(ORG,9)).thenReturn(Optional.of(cap));
        when(caps.bloquearParaEscritura(ORG,9)).thenReturn(Optional.of(cap));
        var actual=mock(CaptacionRepository.ContextoActualEncargo.class);
        when(actual.getResponsable()).thenAnswer(i->cap.getAgente().getId());
        when(actual.getCuenta()).thenAnswer(i->100L+cap.getAgente().getId());
        when(actual.getVersion()).thenAnswer(i->cap.getVersion());
        when(caps.contextoActual(ORG,9)).thenReturn(Optional.of(actual));
        when(caps.cambiarAgenteSi(eq(ORG),eq(9L),anyLong(),anyLong(),anyLong())).thenAnswer(i->{
            cap.setAgente(equipo.get(i.getArgument(3)));ReflectionTestUtils.setField(cap,"version",cap.getVersion()+1);return 1;
        });
        when(cambios.saveAndFlush(any())).thenAnswer(i->{var c=i.getArgument(0,ReasignacionCaptacion.class);ReflectionTestUtils.setField(c,"id",55L);return c;});
        var b=new DetalleBroker();ReflectionTestUtils.setField(b,"id",23L);when(brokers.findById(23L)).thenReturn(Optional.of(b));
    }
    @Test void portadaEntregaUuidYRechazaUnaFotoSinReferencia() {
        var foto=new FotoPropiedad();foto.setClave("privado/portada.jpg");when(fotos.deLaPropiedad(ORG,7)).thenReturn(List.of(foto));
        assertEquals(foto.getArchivoRef().toString(),servicio.obtener(9,agente).fotoPortadaClave());
        ReflectionTestUtils.setField(foto,"archivoRef",null);
        assertThrows(NullPointerException.class,()->servicio.obtener(9,agente));
    }
    @Test void gobiernoNoConsultaFichaComercialAntesDeDenegar() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.obtener(9,admin));
        verify(caps,never()).buscarFicha(anyLong(),anyLong());verifyNoInteractions(fotos,lector);
    }
    @Test void listadosPaginanYFiltranAntesDeHidratar() {
        when(caps.buscar(anyLong(),anyBoolean(),anyCollection(),any(),any(),any(),any())).thenReturn(Page.empty());
        servicio.listar(new CaptacionService.FiltrosCaptacion("A",30L,"Surco",2,25),broker);
        var pagina=org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(caps).buscar(eq(ORG),eq(false),eq(List.of(30L,31L)),eq("A"),eq(30L),eq("Surco"),pagina.capture());
        assertEquals(1,pagina.getValue().getPageNumber());assertEquals(25,pagina.getValue().getPageSize());
    }
    @Test void pendientesUsaEstadoDePeticionYPaginaDeclarados() {
        when(caps.pendientes(anyLong(),anyBoolean(),anyCollection(),any(),any(),any(),any())).thenReturn(Page.empty());
        servicio.pendientes(new CaptacionService.FiltrosPendientes("O",30L,"revisión",3,15),broker);
        verify(caps).pendientes(eq(ORG),eq(false),eq(List.of(30L,31L)),eq("O"),eq(30L),eq("revisión"),any());
    }
    @Test void gobiernoObtieneProyeccionMinimaConVersionSinPrecioNiFicha() {
        var p=mock(EncargoParaReasignacion.class);when(p.getId()).thenReturn(9L);when(p.getEstado()).thenReturn("P");
        when(p.getIdAgente()).thenReturn(30L);when(p.getVersion()).thenReturn(4L);
        when(caps.proyeccionDeReasignacion(ORG,9)).thenReturn(Optional.of(p));
        assertEquals(4,servicio.consultarReasignable(9,admin).version());
        verify(caps,never()).buscarFicha(anyLong(),anyLong());verifyNoInteractions(fotos,lector);
    }
    @Test void reasignarEsUnEventoVersionadoSinCambiarEstadoNiOtrosEncargos() {
        var r=reasignar(broker,0,30,"asignacion-c2");
        assertEquals(30,r.idAgenteAnterior());assertEquals(31,r.idAgente());assertEquals(1,r.version());
        assertEquals(Captacion.EN_PREPARACION,cap.estadoActual());verify(cambios).saveAndFlush(any());
        verify(caps).cambiarAgenteSi(ORG,9,30,31,0);verify(em).refresh(cap);verify(eventos).save(any());
    }
    @Test void repetirDevuelveSnapshotOriginalYNoVuelveAReasignar() {
        var uno=reasignar(admin,0,30,"reintento-c2");var dos=reasignar(admin,0,30,"reintento-c2");
        assertEquals(uno,dos);verify(caps,times(1)).cambiarAgenteSi(anyLong(),anyLong(),anyLong(),anyLong(),anyLong());
        verify(cambios,times(1)).saveAndFlush(any());
    }
    @Test void mismaClaveConOtraEntradaNoReinterpretaLaDecision() {
        reasignar(admin,0,30,"clave-c2");
        assertThrows(ConflictoException.class,()->reasignar(admin,1,31,"clave-c2"));
        verify(cambios,times(1)).saveAndFlush(any());
    }
    @Test void perderSupervisionDeniegaInclusoLaConsultaDelResultadoPrevio() {
        reasignar(broker,0,30,"alcance-c2");when(supervisiones.agentesSupervisados(ORG,23)).thenReturn(List.of());
        assertThrows(AccesoNoAutorizadoException.class,()->reasignar(broker,0,30,"alcance-c2"));
    }
    @Test void agenteYBandaDesconocidaNoReasignanNiEscribenRastro() {
        assertThrows(AccesoNoAutorizadoException.class,()->reasignar(agente,0,30,"agente-c2"));
        var identidad=new Actor(ORG,3,30,"IDENTIDAD");
        assertThrows(AccesoNoAutorizadoException.class,()->reasignar(identidad,0,30,"identidad-c2"));
        verifyNoInteractions(cambios,eventos);
    }
    @Test void versionOResponsableObsoletosSonConflictoAntesDelCas() {
        assertThrows(ConflictoException.class,()->reasignar(admin,1,30,"version-c2"));
        assertThrows(ConflictoException.class,()->reasignar(admin,0,99,"observado-c2"));
        verify(caps,never()).cambiarAgenteSi(anyLong(),anyLong(),anyLong(),anyLong(),anyLong());
    }
    @Test void perderCasNoProduceHistorialNiResultadoDurable() {
        when(caps.cambiarAgenteSi(ORG,9,30,31,0)).thenReturn(0);
        assertThrows(ConflictoException.class,()->reasignar(admin,0,30,"cas-c2"));
        verifyNoInteractions(cambios,eventos);assertTrue(resultados.isEmpty());
    }
    @Test void destinoInelegibleNoEscribeNadaConGuardaReal() {
        when(agentes.esCandidatoAResponsable(anyLong(),anyBoolean(),anyLong(),anyLong(),any(),eq(31L))).thenReturn(false);
        assertThrows(AccesoNoAutorizadoException.class,()->reasignar(admin,0,30,"elegible-c2"));
        verifyNoInteractions(cambios,eventos);assertTrue(resultados.isEmpty());
    }
    @Test void profesionalQueIntegraLoFirmadoExigeAdenda() {
        ReflectionTestUtils.setField(cap,"terminosAceptados",true);ReflectionTestUtils.setField(cap,"idDocumentoActual",8L);
        var cobertura=mock(DocumentoEncargoRepository.CoberturaResponsable.class);when(cobertura.getDefinida()).thenReturn(true);
        when(cobertura.getResponsableFirmado()).thenReturn(30L);when(documentos.coberturaResponsable(ORG,8,9)).thenReturn(Optional.of(cobertura));
        assertThrows(ReglaNegocioException.class,()->reasignar(admin,0,30,"firmado-c2"));
        verifyNoInteractions(cambios,eventos);
    }
    private CaptacionService.ResultadoReasignacion reasignar(Actor actor,long version,long observado,String clave) {
        return servicio.reasignar(9,new CaptacionService.DatosReasignacion(31,observado,version,"Reasignación explícita del fixture C2"),
                actor,ContextoSesion.de(sesion(actor)),clave,Procedencia.deCabecera("API"));
    }
    private static DetalleAgente agente(long id) {
        var p=new Persona();p.setOrganizacionId(ORG);p.setNombresORazonSocial("Profesional "+id);ReflectionTestUtils.setField(p,"id",id+100);
        var r=new PersonaRol();r.setOrganizacionId(ORG);r.setPersona(p);r.setTipoRol(TipoRol.AGENTE);ReflectionTestUtils.setField(r,"id",id);
        var a=new DetalleAgente();a.setOrganizacionId(ORG);a.setRol(r);a.setCodigoAgente("AGT-"+id);ReflectionTestUtils.setField(a,"id",id);return a;
    }
    private static ContextosService.SesionAccesoDTO sesion(Actor actor) {
        var c=new ContextosService.Contexto("ctx-c2",ORG,actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),"EMPRESARIAL","Organización fixture");
        return new ContextosService.SesionAccesoDTO("sesion-"+actor.idPersona(),1,
                new ContextosService.IdentidadAutenticada(100+actor.idPersona(),"fixture","fixture@example.test","Fixture"),c,
                Instant.parse("2026-10-28T00:00:00Z"),Instant.parse("2026-10-29T00:00:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
    }
}
