package com.controllocal.service.colaboracion;

import com.controllocal.domain.auditoria.*;
import com.controllocal.domain.colaboracion.*;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.controllocal.domain.colaboracion.LadoColaboracion.*;
import static com.controllocal.service.colaboracion.CanalesColaboracionService.*;

class CanalesColaboracionServiceImplTest {
    private final SolicitudColaboracionRepository solicitudes=mock(SolicitudColaboracionRepository.class);
    private final CanalColaboracionRepository canales=mock(CanalColaboracionRepository.class);
    private final ProyeccionNetwork network=mock(ProyeccionNetwork.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final EntityManager em=mock(EntityManager.class);
    private final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    private final Actor agente=new Actor(1,10,100,Actor.AGENTE),broker=new Actor(1,11,101,Actor.BROKER),otro=new Actor(2,20,200,Actor.AGENTE);
    private static final Instant AHORA=Instant.parse("2026-10-29T12:00:00Z");
    private final Map<Long,CanalColaboracion> declaraciones=new LinkedHashMap<>();
    private final Map<String,ComandoIdempotente> recibos=new HashMap<>();
    private final List<EventoDominio> outbox=new ArrayList<>();
    private SolicitudColaboracion solicitud;
    private CanalesColaboracionServiceImpl servicio;
    @BeforeEach void montar() {
        var alcance=new AlcanceColaboracion(Set.of(DEMANDA,ENCARGO),Set.of("CONSULTAR"),
                Map.of(DEMANDA,Set.of("canalComercial","operacion"),ENCARGO,Set.of("ENCARGO:operacion")),
                "RED_COLABORATIVA",AHORA.minusSeconds(1),AHORA.plusSeconds(3600));
        solicitud=SolicitudColaboracion.nueva(1,2,4,9,100,200,100,3,6,5,"oferta",json.objetoCanonico(alcance),Idempotencia.huellaExacta("alcance"),AHORA);
        ReflectionTestUtils.setField(solicitud,"id",7L);solicitud.transicionarA("P");
        servicio=new CanalesColaboracionServiceImpl(solicitudes,canales,network,contextos,cuentas,miembros,registros,
                new ComandosIdempotentes(registros),json,eventos,em,Clock.fixed(AHORA,ZoneOffset.UTC));
        when(solicitudes.leer(any(),anyLong())).thenReturn(Optional.of(solicitud));when(solicitudes.bloquear(any(),anyLong())).thenReturn(Optional.of(solicitud));
        when(solicitudes.saveAndFlush(any())).thenAnswer(i->{ReflectionTestUtils.setField(solicitud,"version",solicitud.getVersion()+1);return solicitud;});
        when(canales.saveAndFlush(any())).thenAnswer(i->{CanalColaboracion c=i.getArgument(0);ReflectionTestUtils.setField(c,"id",(long)declaraciones.size()+1);declaraciones.put(c.getId(),c);return c;});
        when(canales.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(declaraciones.get(i.getArgument(1))).filter(c->c.getOrganizacionId()==(long)i.getArgument(0)));
        when(network.exigirPartePropia(any(),any(),any(),any())).thenAnswer(i->{
            LadoColaboracion lado=i.getArgument(1);Actor a=i.getArgument(3);long org=lado==DEMANDA?1:2;
            if(a.idOrganizacion()!=org)throw new AccesoNoAutorizadoException();
            return new ProyeccionNetwork.ParteActual(org,lado==DEMANDA?100:200,lado==DEMANDA?3:6,false,lado);
        });
        when(miembros.cuentasActivasDePersona(anyLong(),anyLong())).thenReturn(List.of(77L));when(cuentas.bloquearCambioAcceso(anyLong())).thenReturn(Optional.of(mock(CuentaAcceso.class)));
        when(contextos.exigirContexto(anyString(),anyLong(),any())).thenAnswer(i->acceso(i.getArgument(2)));
        when(registros.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString())).thenAnswer(i->Optional.ofNullable(recibos.get(i.getArgument(0)+"/"+i.getArgument(1))));
        when(registros.save(any())).thenAnswer(i->{ComandoIdempotente c=i.getArgument(0);recibos.put(c.getOrganizacionId()+"/"+c.getClaveIdempotencia(),c);return c;});
        when(eventos.saveAndFlush(any())).thenAnswer(i->{EventoDominio e=i.getArgument(0);outbox.add(e);return e;});
    }
    @Test void titularDeclaraValorExactoParaEsteAlcanceConVersionReciboYAntecedente() {
        var d=datos("  Canal comercial: oficina 987654321  ");var normal=servicio.validarDeclaracion(d,agente);
        assertEquals("Canal comercial: oficina 987654321",normal.canalComercial());
        var r=declarar(normal,"c1");assertEquals(1,r.versionSolicitud());assertEquals(normal.canalComercial(),r.canalComercial());
        assertEquals(1,declaraciones.size());assertEquals(1,outbox.size());assertEquals(100L,declaraciones.get(1L).getDeclarante());
        assertFalse(outbox.getFirst().getCargaUtil().contains("987654321"));assertEquals(r,servicio.consultarResultado("c1",agente).orElseThrow());
    }
    @Test void replayMismaClaveConservaDeclaracionOriginalSinReemitir() {
        var d=datos("Mesa comercial explícita");var r=declarar(d,"c1");assertEquals(r,declarar(d,"c1"));
        assertEquals(1,declaraciones.size());assertEquals(1,outbox.size());assertEquals(1,solicitud.getVersion());
    }
    @Test void otraEntradaConMismaClaveSeRechazaSinCambiarCanal() {
        declarar(datos("Canal A"),"c1");assertThrows(ConflictoException.class,()->declarar(datos("Canal B"),"c1"));assertEquals(1,declaraciones.size());
    }
    @Test void brokerNoDeclaraPorElTitularNiContrapartePorElOtroLado() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.declarar(datos("Canal"),broker,ContextoSesion.de(acceso(broker)),"b",Procedencia.deCabecera("API")));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.declarar(datos("Canal"),otro,ContextoSesion.de(acceso(otro)),"e",Procedencia.deCabecera("API")));
        assertTrue(declaraciones.isEmpty());assertTrue(outbox.isEmpty());
    }
    @Test void noHayCanalImportadoNiVetoDeLeerOtrosCamposPorAusencia() {
        assertTrue(servicio.consultarDeclaracionPropia(solicitud.getReferencia().toString(),DEMANDA,agente).isEmpty());
        assertThrows(ReglaNegocioException.class,()->servicio.validarDeclaracion(datos(null),agente));
        assertThrows(ReglaNegocioException.class,()->servicio.validarDeclaracion(datos(" "),agente));assertTrue(declaraciones.isEmpty());
    }
    @Test void canalDeUnaDecisionNoSeReescribeNiSeBorraElAntecedente() {
        declarar(datos("Canal A"),"c1");solicitud.registrarDecision(DEMANDA,88);
        assertThrows(ConflictoException.class,()->declarar(datos("Canal B"),"c2"));
        assertEquals("Canal A",declaraciones.get(1L).getCanalComercial());assertEquals(1L,solicitud.canalDe(DEMANDA));
        assertEquals(1,declaraciones.size());assertEquals(1,outbox.size());
    }
    @Test void cambiosPreviosAnexanDeclaracionYVersionanSolicitudSinDecisionFicticia() {
        declarar(datos("Canal A"),"c1");declarar(datos("Canal B"),"c2");
        assertEquals(2,declaraciones.size());assertEquals("Canal A",declaraciones.get(1L).getCanalComercial());
        assertEquals(2L,solicitud.canalDe(DEMANDA));assertEquals(2,solicitud.getVersion());assertNull(solicitud.getDecisionDemanda());
    }
    @Test void casAusenteOAntiguoNoSeConvierteEnVersionCeroImplicita() {
        var d=datos("Canal");assertThrows(ReglaNegocioException.class,()->servicio.validarDeclaracion(new DatosDeclaracion(d.solicitud(),d.lado(),null,0L,3L,d.canalComercial()),agente));
        declarar(d,"c1");assertThrows(ConflictoException.class,()->declarar(d,"c2"));assertEquals(1,declaraciones.size());
    }
    @Test void consultaPropiaBrokerPermitidaYContraparteNoVeElCanalAntesDeBilateral() {
        declarar(datos("Canal declarado"),"c1");assertEquals("Canal declarado",servicio.consultarDeclaracionPropia(solicitud.getReferencia().toString(),DEMANDA,broker).orElseThrow().canalComercial());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarDeclaracionPropia(solicitud.getReferencia().toString(),DEMANDA,otro));
    }
    @Test void fallaTecnicaNoDevuelveCanalVacioNiResultadoExitoso() {
        doThrow(new IllegalStateException("Disco SQL")).when(canales).saveAndFlush(any());
        assertThrows(IllegalStateException.class,()->declarar(datos("Canal"),"c1"));assertTrue(recibos.isEmpty());assertTrue(outbox.isEmpty());
    }
    private DatosDeclaracion datos(String canal){return new DatosDeclaracion(solicitud.getReferencia().toString(),DEMANDA,solicitud.getVersion(),0L,3L,canal);}
    private Declaracion declarar(DatosDeclaracion d,String clave){return servicio.declarar(d,agente,ContextoSesion.de(acceso(agente)),clave,Procedencia.deCabecera("API"));}
    private ContextosService.SesionAccesoDTO acceso(Actor a){return new ContextosService.SesionAccesoDTO("sesion-"+a.idPersona(),1,new ContextosService.IdentidadAutenticada(77,"fixture","fixture@example.test","Fixture"),new ContextosService.Contexto("ctx",a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),"EMPRESARIAL","Fixture"),AHORA,AHORA.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);}
}
