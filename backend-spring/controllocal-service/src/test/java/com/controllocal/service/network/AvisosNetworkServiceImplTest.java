package com.controllocal.service.network;

import com.controllocal.persistence.repositorio.AvisosNetworkRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AvisosNetworkServiceImplTest {
    private final AvisosNetworkRepository repo=mock(AvisosNetworkRepository.class);
    private final Alcances alcances=mock(Alcances.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final AvisosNetworkService servicio=new AvisosNetworkServiceImpl(repo,alcances,contextos,new Documentos(new ObjectMapper().findAndRegisterModules()));
    private final Actor agente=new Actor(1,10,100,Actor.AGENTE),broker=new Actor(1,11,101,Actor.BROKER),admin=new Actor(1,12,102,Actor.TENANT_ADMIN);
    private final Instant ahora=Instant.parse("2026-10-29T12:00:00Z");
    @BeforeEach void montar() {
        when(contextos.exigirContexto(anyString(),anyLong(),any())).thenAnswer(i->acceso(i.getArgument(2)));
        when(alcances.de(any())).thenReturn(new Alcances.Alcance(1,false,List.of(100L)));
        when(repo.pagina(anyLong(),anyLong(),anyCollection(),anyInt(),anyLong())).thenReturn(List.of(fila(5)));
        when(repo.revalidar(anyLong(),anyLong(),anyCollection(),anyCollection())).thenReturn(List.of(fila(5)));
        when(repo.contar(anyLong(),anyLong(),anyCollection())).thenReturn(1L);
    }
    @Test void lectorDevuelveMetadataAutorizadaYRevalidaPaginaConteoYSesionSinNMasUno() {
        var p=servicio.consultar(null,null,agente,sesion(agente));
        assertEquals(1,p.total());var aviso=p.items().getFirst();assertEquals(5,aviso.idEvento());
        assertEquals("ENC-9",aviso.codigoEncargo());assertEquals("Revisar el precio autorizado",aviso.motivo());
        assertEquals(Set.of("CAMBIO_DATOS"),aviso.causas());assertEquals(ahora,aviso.ocurridoEn());
        verify(repo).pagina(1,100,List.of(100L),20,0);verify(repo).revalidar(1,100,List.of(100L),List.of(5L));
        verify(repo).contar(1,100,List.of(100L));verify(alcances,times(2)).de(agente);
        verify(contextos,times(2)).exigirContexto("sesion-10",2,agente);
    }
    @Test void antiguoBrokerSinSupervisionActualNoConservaAvisosHistoricos() {
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(1,false,List.of(100L)),new Alcances.Alcance(1,false,List.of()));
        when(repo.revalidar(1,101,List.of(-1L),List.of(5L))).thenReturn(List.of());
        when(repo.contar(1,101,List.of(-1L))).thenReturn(0L);
        var p=servicio.consultar(1,20,broker,sesion(broker));assertTrue(p.items().isEmpty());assertEquals(0,p.total());
        verify(repo).revalidar(1,101,List.of(-1L),List.of(5L));
    }
    @Test void antiguoAgenteNoRecibePorAutoriaSiLaRevalidacionRetiroSuAcceso() {
        when(repo.revalidar(1,100,List.of(100L),List.of(5L))).thenReturn(List.of());when(repo.contar(1,100,List.of(100L))).thenReturn(0L);
        var p=servicio.consultar(1,20,agente,sesion(agente));assertTrue(p.items().isEmpty());assertEquals(0,p.total());
    }
    @Test void sesionRevocadaDespuesDeLasConsultasNoEntregaLaPagina() {
        when(contextos.exigirContexto("sesion-10",2,agente)).thenReturn(acceso(agente)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(1,20,agente,sesion(agente)));
        verify(repo).contar(1,100,List.of(100L));
    }
    @Test void gobiernoYSidAusenteNoSeConviertenEnBandejaVacia() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(1,20,admin,sesion(admin)));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(1,20,agente,null));verifyNoInteractions(repo);
    }
    @Test void sidOContextoManipuladoSeRechazaAntesDeConsultar() {
        var s=sesion(agente);var alterada=new ContextoSesion("otra",s.expiraEnEpochSeconds(),s.referenciaSesion(),s.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(1,20,agente,alterada));verifyNoInteractions(repo);
    }
    @Test void paginacionAcotaCincuentaYConservaDesplazamientoSinCargarTodo() {
        servicio.consultar(3,999,agente,sesion(agente));verify(repo).pagina(1,100,List.of(100L),50,100);
    }
    @Test void falloTecnicoDeConsultaODeRevalidacionNoSeRepresentaComoVacio() {
        when(repo.pagina(anyLong(),anyLong(),anyCollection(),anyInt(),anyLong())).thenThrow(new IllegalStateException("SQL no disponible"));
        assertThrows(IllegalStateException.class,()->servicio.consultar(1,20,agente,sesion(agente)));
        verify(repo,never()).contar(anyLong(),anyLong(),anyCollection());
    }
    @Test void revalidacionNoPuedeIntroducirOtroEventoNiUnConteoInvalido() {
        when(repo.revalidar(anyLong(),anyLong(),anyCollection(),anyCollection())).thenReturn(List.of(fila(6)));
        assertThrows(IllegalStateException.class,()->servicio.consultar(1,20,agente,sesion(agente)));
        when(repo.revalidar(anyLong(),anyLong(),anyCollection(),anyCollection())).thenReturn(List.of(fila(5)));
        when(repo.contar(anyLong(),anyLong(),anyCollection())).thenReturn(-1L);
        assertThrows(IllegalStateException.class,()->servicio.consultar(1,20,agente,sesion(agente)));
    }
    @Test void paginaVaciaEsResultadoNormalYNoEjecutaInVacio() {
        when(repo.pagina(anyLong(),anyLong(),anyCollection(),anyInt(),anyLong())).thenReturn(List.of());
        when(repo.contar(anyLong(),anyLong(),anyCollection())).thenReturn(0L);
        var p=servicio.consultar(1,20,agente,sesion(agente));assertTrue(p.items().isEmpty());assertEquals(0,p.total());
        verify(repo,never()).revalidar(anyLong(),anyLong(),anyCollection(),anyCollection());
    }
    private ContextoSesion sesion(Actor a){return ContextoSesion.de(acceso(a));}
    private ContextosService.SesionAccesoDTO acceso(Actor a) {
        return new ContextosService.SesionAccesoDTO("sesion-"+a.idPersona(),2,new ContextosService.IdentidadAutenticada(77,"fixture","fixture@example.test","Fixture"),
                new ContextosService.Contexto("contexto",a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),"EMPRESARIAL","Fixture"),
                ahora,ahora.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
    }
    private AvisosNetworkRepository.Fila fila(long id) {
        Map<String,Object> m=new HashMap<>();m.put("getIdEvento",id);m.put("getTipo","NETWORK_SUSPENDIDA");m.put("getOcurridoEn",ahora);
        m.put("getIdEncargo",9L);m.put("getCodigoEncargo","ENC-9");m.put("getParticipacion","69bf8091-e006-4b98-815e-0d3ad27f7859");
        m.put("getEstadoParticipacion","S");m.put("getVersionProyeccion",1L);m.put("getMotivo","Revisar el precio autorizado");m.put("getCausas","[\"CAMBIO_DATOS\"]");
        return (AvisosNetworkRepository.Fila)Proxy.newProxyInstance(AvisosNetworkRepository.Fila.class.getClassLoader(),new Class<?>[]{AvisosNetworkRepository.Fila.class},
                (p,method,args)->method.getName().equals("toString")?m.toString():m.get(method.getName()));
    }
}
