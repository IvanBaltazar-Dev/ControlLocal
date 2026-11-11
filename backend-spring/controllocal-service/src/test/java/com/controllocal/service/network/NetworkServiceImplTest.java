package com.controllocal.service.network;

import com.controllocal.domain.auditoria.*;
import com.controllocal.domain.comun.Transicionable;
import com.controllocal.domain.network.*;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.network.NetworkService.*;

/** Fuente sintética explícita y legitimada de la prueba; no prueba consentimiento ni entrega en producción. */
class NetworkServiceImplTest {
    private final ParticipacionNetworkRepository partes=mock(ParticipacionNetworkRepository.class);
    private final SolicitudParticipacionNetworkRepository solicitudes=mock(SolicitudParticipacionNetworkRepository.class);
    private final DecisionParticipacionNetworkRepository decisiones=mock(DecisionParticipacionNetworkRepository.class);
    private final VersionParticipacionNetworkRepository versiones=mock(VersionParticipacionNetworkRepository.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final Transiciones transiciones=mock(Transiciones.class);
    private final EntityManager em=mock(EntityManager.class);
    private final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    private final Map<Long,ParticipacionNetwork> pp=new LinkedHashMap<>();
    private final Map<Long,SolicitudParticipacionNetwork> ss=new LinkedHashMap<>();
    private final Map<Long,DecisionParticipacionNetwork> dd=new LinkedHashMap<>();
    private final Map<Long,VersionParticipacionNetwork> vv=new LinkedHashMap<>();
    private final Map<String,ComandoIdempotente> recibos=new HashMap<>();
    private final List<EventoDominio> outbox=new ArrayList<>();
    private final Actor agente=new Actor(1,10,100,Actor.AGENTE),broker=new Actor(1,11,101,Actor.BROKER),
            admin=new Actor(1,12,102,Actor.TENANT_ADMIN),extranjero=new Actor(2,20,200,Actor.AGENTE);
    private static final Instant AHORA=Instant.parse("2026-10-29T12:00:00Z");
    private static final Set<String> CAMPOS=Set.of("ENCARGO:operacion","PROPIEDAD:tipo_propiedad","ENCARGO:precio_pedido");
    private final FuenteFixture fuente=new FuenteFixture();
    private NetworkServiceImpl servicio;

    @BeforeEach void montar() {
        servicio=new NetworkServiceImpl(partes,solicitudes,decisiones,versiones,fuente,contextos,cuentas,miembros,
                registros,new ComandosIdempotentes(registros),json,eventos,transiciones,em,Clock.fixed(AHORA,ZoneOffset.UTC));
        var cuenta=mock(CuentaAcceso.class);
        when(miembros.cuentasActivasDePersona(anyLong(),anyLong())).thenAnswer(i->List.of(1000L+(long)i.getArgument(1)));
        when(cuentas.bloquearCambioAcceso(anyLong())).thenReturn(Optional.of(cuenta));
        when(contextos.exigirContexto(anyString(),anyLong(),any())).thenAnswer(i->sesion(i.getArgument(2)));
        doAnswer(i->{((Transicionable)i.getArgument(0)).transicionarA(i.getArgument(1));return null;}).when(transiciones).iniciar(any(),anyString());
        doAnswer(i->{((Transicionable)i.getArgument(0)).transicionarA(i.getArgument(2));return null;}).when(transiciones).aplicar(any(ParticipacionNetwork.class),anyLong(),anyString(),nullable(Actor.class),anyString(),any(LocalDate.class));
        doAnswer(i->{((Transicionable)i.getArgument(0)).transicionarA(i.getArgument(2));return null;}).when(transiciones).aplicar(any(SolicitudParticipacionNetwork.class),anyLong(),anyString(),nullable(Actor.class),anyString(),any(LocalDate.class));
        when(partes.saveAndFlush(any())).thenAnswer(i->{ParticipacionNetwork p=i.getArgument(0);if(p.getId()==null)id(p,pp.size()+1);else ReflectionTestUtils.setField(p,"version",p.getVersion()+1);pp.put(p.getId(),p);return p;});
        when(partes.findByOrganizacionIdAndEncargo(anyLong(),anyLong())).thenAnswer(i->parte(i.getArgument(0),i.getArgument(1)));
        when(partes.bloquearEncargo(anyLong(),anyLong())).thenAnswer(i->parte(i.getArgument(0),i.getArgument(1)));
        when(partes.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(pp.get(i.getArgument(1))).filter(p->p.getOrganizacionId().equals(i.getArgument(0))));
        when(partes.bloquear(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(pp.get(i.getArgument(1))).filter(p->p.getOrganizacionId().equals(i.getArgument(0))));
        when(solicitudes.saveAndFlush(any())).thenAnswer(i->{SolicitudParticipacionNetwork s=i.getArgument(0);if(s.getId()==null)id(s,ss.size()+1);else ReflectionTestUtils.setField(s,"version",s.getVersion()+1);ss.put(s.getId(),s);return s;});
        when(solicitudes.findByOrganizacionIdAndReferencia(anyLong(),any())).thenAnswer(i->solicitud(i.getArgument(0),i.getArgument(1)));
        when(solicitudes.bloquear(anyLong(),any())).thenAnswer(i->solicitud(i.getArgument(0),i.getArgument(1)));
        when(solicitudes.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(ss.get(i.getArgument(1))));
        when(solicitudes.contexto(anyLong(),any())).thenAnswer(i->solicitud(i.getArgument(0),i.getArgument(1)).map(this::contexto));
        when(solicitudes.contextoId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(ss.get(i.getArgument(1))).map(this::contexto));
        when(solicitudes.referenciaDe(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(ss.get(i.getArgument(1))).map(SolicitudParticipacionNetwork::getReferencia));
        when(decisiones.saveAndFlush(any())).thenAnswer(i->{DecisionParticipacionNetwork d=i.getArgument(0);id(d,dd.size()+1);dd.put(d.getId(),d);return d;});
        when(versiones.saveAndFlush(any())).thenAnswer(i->{VersionParticipacionNetwork v=i.getArgument(0);id(v,vv.size()+1);vv.put(v.getId(),v);return v;});
        when(versiones.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(vv.get(i.getArgument(1))));
        when(versiones.metadata(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(vv.get(i.getArgument(1))).map(v->fila(VersionParticipacionNetworkRepository.MetadataVersion.class,v)));
        when(versiones.candidatasActuales()).thenAnswer(i->actuales().stream().map(v->fila(VersionParticipacionNetworkRepository.EstadoVersion.class,v)).toList());
        when(versiones.candidatasDelEncargo(eq(1L),eq(9L))).thenAnswer(i->actuales().stream().map(v->fila(VersionParticipacionNetworkRepository.EstadoVersion.class,v)).toList());
        when(versiones.candidatasDePropiedad(eq(1L),eq(77L))).thenAnswer(i->actuales().stream().map(v->fila(VersionParticipacionNetworkRepository.EstadoVersion.class,v)).toList());
        when(versiones.candidatasPorReferencia(anyCollection())).thenAnswer(i->actuales().stream().filter(v->((Collection<?>)i.getArgument(0)).contains(pp.get(v.getParticipacion()).getReferencia())).map(v->fila(VersionParticipacionNetworkRepository.EstadoVersion.class,v)).toList());
        when(versiones.salidasActuales(anyCollection())).thenAnswer(i->actuales().stream().filter(v->((Collection<?>)i.getArgument(0)).contains(v.getReferencia())).map(v->fila(VersionParticipacionNetworkRepository.Salida.class,v)).toList());
        when(versiones.referenciasActivas(anyCollection())).thenAnswer(i->{Set<UUID> r=new HashSet<>();actuales().stream().filter(v->"A".equals(pp.get(v.getParticipacion()).estadoActual())).filter(v->((Collection<?>)i.getArgument(0)).contains(v.getReferencia())).forEach(v->r.add(v.getReferencia()));return r;});
        when(registros.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString())).thenAnswer(i->Optional.ofNullable(recibos.get(i.getArgument(0)+"/"+i.getArgument(1))));
        when(registros.save(any())).thenAnswer(i->{ComandoIdempotente c=i.getArgument(0);recibos.put(c.getOrganizacionId()+"/"+c.getClaveIdempotencia(),c);return c;});
        when(eventos.saveAndFlush(any())).thenAnswer(i->{EventoDominio e=i.getArgument(0);outbox.add(e);return e;});
    }
    @Test void agenteSolicitaYBrokerApruebaVersionExactaEnUnaSolaActuacion() {
        var sol=solicitar(Cambio.ACTIVAR,"s");assertEquals("PENDIENTE",sol.estadoSolicitud());assertTrue(vv.isEmpty());
        var datos=decision(Decision.APROBAR);var r=servicio.decidir(datos,broker,prueba(broker),"aprobar",origen());
        assertEquals("ACTIVO_AUTORIZADO",r.estadoParticipacion());assertEquals(1,vv.size());assertEquals(1,dd.size());
        assertEquals(r,servicio.decidir(datos,broker,prueba(broker),"aprobar",origen()));assertEquals(1,vv.size());assertEquals(2,outbox.size());
        var entregada=servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero);
        assertEquals(1,entregada.size());assertEquals(fuente.confirmacion,entregada.getFirst().disponibilidad());
    }
    @Test void agenteCorporativoNoDecideNiUsaDesactivacionDirecta() {
        solicitar(Cambio.ACTIVAR,"s");
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.decidir(decision(Decision.APROBAR),agente,prueba(agente),"ap",origen()));
        assertFalse(servicio.capacidadSobreSolicitud(solicitud().getReferencia().toString(),agente).autoridadDecidir());assertTrue(dd.isEmpty());
        activar();
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.desactivarDirectamente(new DatosDesactivacion(9L,"Baja",part().getVersion()),agente,prueba(agente),"baja",origen()));
    }
    @Test void autoridadSeDistingueDeAusenciaDeSolicitudYMetadataNoLeeProyeccion() {
        int antes=fuente.propias;var a=servicio.capacidadSobre(9,agente);var b=servicio.capacidadSobre(9,broker);
        assertFalse(a.autoridadDecidir());assertFalse(a.decidir());assertTrue(b.autoridadDecidir());assertFalse(b.decidir());
        assertEquals(antes,fuente.propias);verify(versiones,never()).salidasActuales(anyCollection());
    }
    @Test void independienteSolicitaYConfirmaLoPropioSinBrokerNiOtraPuertaDirecta() {
        fuente.individual=true;var r=solicitar(Cambio.ACTIVAR,"s");assertEquals("APROBADA",r.estadoSolicitud());
        assertEquals(1,dd.size());assertEquals(100,dd.values().iterator().next().getDecisor());
        assertFalse(servicio.capacidadSobre(9,agente).autoridadDesactivarDirectamente());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.desactivarDirectamente(new DatosDesactivacion(9L,"Baja",part().getVersion()),agente,prueba(agente),"directa",origen()));
        var retirada=solicitar(Cambio.DESACTIVAR,"retirar");assertEquals("INACTIVO_AUTORIZADO",retirada.estadoParticipacion());assertEquals(1,vv.size());
    }
    @Test void brokerPuedeDesactivarDirectamenteSinInventarSolicitudDeAgente() {
        activar();var r=servicio.desactivarDirectamente(new DatosDesactivacion(9L,"Decisión propia",part().getVersion()),broker,prueba(broker),"baja",origen());
        assertEquals("INACTIVO_AUTORIZADO",r.estadoParticipacion());
        assertEquals(101,ss.values().stream().reduce((a,b)->b).orElseThrow().getSolicitante());
        assertTrue(servicio.resolverReferencia(part().getReferencia().toString(),extranjero).isEmpty());assertEquals(1,vv.size());
    }
    @Test void cambiosCompartidosSuspendenAntesDeLeerDatosYRechazarNoPublicaNuevaVersion() {
        activar();fuente.precio=new BigDecimal("2800.00");clearInvocations(versiones);
        assertTrue(servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero).isEmpty());
        assertEquals("S",part().estadoActual());verify(versiones,never()).salidasActuales(anyCollection());
        solicitar(Cambio.MODIFICAR,"mod");var vista=servicio.consultarSolicitud(solicitudPendiente().getReferencia().toString(),broker);
        assertEquals(1,vista.diferencias().size());assertEquals("ENCARGO:precio_pedido",vista.diferencias().getFirst().campo());
        var rechazada=servicio.decidir(decision(Decision.RECHAZAR),broker,prueba(broker),"rechazar",origen());
        assertEquals("RECHAZADA",rechazada.estadoSolicitud());assertEquals(1,vv.size());assertEquals("S",part().estadoActual());
    }
    @Test void modificacionAprobadaConservaOfertaEstableYEntregaNuevaVersionSinAliasDeVersion() {
        activar();var anterior=version();fuente.precio=new BigDecimal("2800.00");
        servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero);
        solicitar(Cambio.MODIFICAR,"mod");servicio.decidir(decision(Decision.APROBAR),broker,prueba(broker),"ap2",origen());
        assertEquals(2,vv.size());assertNotEquals(anterior.getReferencia(),version().getReferencia());
        assertTrue(servicio.resolverReferencia(anterior.getReferencia().toString(),extranjero).isEmpty());
        var actual=servicio.resolverReferencia(part().getReferencia().toString(),extranjero).orElseThrow();
        assertEquals(part().getReferencia().toString(),actual.ofertaRef());
        assertEquals(version().getReferencia().toString(),actual.referenciaVersion());assertEquals(2,actual.versionProyeccion());
        assertTrue(servicio.resolverReferencia(version().getReferencia().toString(),extranjero).isEmpty());
        var entregada=servicio.revalidarParaEntrega(List.of(actual.ofertaRef()),extranjero);
        assertEquals(actual,entregada.getFirst().referencia());
        assertTrue(anterior.contenido().datos().contains("2500.00"));assertTrue(version().contenido().datos().contains("2800.00"));
    }
    @Test void unaNuevaVersionEntreMetadataYValoresNoSustituyeLaAutorizadaNiSuspendeLaNueva() {
        activar();var oferta=part().getReferencia().toString();var primera=version().getReferencia();
        var cambiar=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(i->{
            if(cambiar.getAndSet(false)) {
                fuente.precio=new BigDecimal("2800.00");solicitar(Cambio.MODIFICAR,"mod-carrera");
                servicio.decidir(decision(Decision.APROBAR),broker,prueba(broker),"ap-carrera",origen());
            }
            return actuales().stream().filter(v->((Collection<?>)i.getArgument(0)).contains(v.getReferencia()))
                    .map(v->fila(VersionParticipacionNetworkRepository.Salida.class,v)).toList();
        }).when(versiones).salidasActuales(anyCollection());
        assertTrue(servicio.revalidarParaEntrega(List.of(oferta),extranjero).isEmpty());
        assertNotEquals(primera,version().getReferencia());assertEquals("A",part().estadoActual());
        assertEquals("[]",part().getCausas());assertEquals(2,vv.size());
        var actual=servicio.revalidarParaEntrega(List.of(oferta),extranjero).getFirst();
        assertEquals(oferta,actual.referencia().ofertaRef());assertEquals(2,actual.referencia().versionProyeccion());
    }
    @Test void inactivoNuncaRetornaPorFrescuraNiPorAprobacionHistorica() {
        activar();servicio.desactivarDirectamente(new DatosDesactivacion(9L,"Baja",part().getVersion()),broker,prueba(broker),"baja",origen());
        assertTrue(servicio.referenciasParaConsulta(extranjero).isEmpty());
        assertThrows(ReglaNegocioException.class,()->servicio.validarSolicitud(datos(Cambio.ACTIVAR),agente));
        solicitar(Cambio.REACTIVAR,"rea");assertEquals("I",part().estadoActual());
        servicio.decidir(decision(Decision.APROBAR),broker,prueba(broker),"ap2",origen());assertEquals(2,vv.size());assertEquals("A",part().estadoActual());
    }
    @Test void frescuraSoloRetornaConLaMismaVersionYAutorizacion() {
        activar();var v=version();fuente.causas=Set.of(FuenteDeNetwork.FRESCURA);
        assertTrue(servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero).isEmpty());assertEquals("S",part().estadoActual());
        fuente.causas=Set.of();fuente.confirmacion=new ConfirmacionDisponibilidad(AHORA.minusSeconds(10),false,"fixture-confirmacion-9-nueva");
        var salida=servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero);
        assertEquals(1,salida.size());assertEquals("A",part().estadoActual());assertEquals(1,vv.size());assertEquals(v.getReferencia(),version().getReferencia());
        assertEquals(fuente.confirmacion,salida.getFirst().disponibilidad());
    }
    @Test void otraCausaDeSeguridadNoAutoReactivaUnaAutorizacionSuspendida() {
        activar();fuente.causas=Set.of("DATO_INCORRECTO");servicio.referenciasParaConsulta(extranjero);
        fuente.causas=Set.of();assertTrue(servicio.referenciasParaConsulta(extranjero).isEmpty());assertEquals("S",part().estadoActual());
    }
    @Test void cambioDeDatosNoSeOlvidaPorPasarLuegoPorFrescura() {
        unaCausaMaterialNoSeReautorizaAlRecuperarFrescura("CAMBIO_DATOS");
    }
    @Test void cambioDeAutoridadNoSeOlvidaPorPasarLuegoPorFrescura() {
        unaCausaMaterialNoSeReautorizaAlRecuperarFrescura("CAMBIO_AUTORIDAD");
    }
    private void unaCausaMaterialNoSeReautorizaAlRecuperarFrescura(String causaMaterial) {
        activar();var aprobada=version();int decisionesAprobadas=dd.size();
        fuente.causas=Set.of(causaMaterial);
        assertTrue(servicio.referenciasParaConsulta(extranjero).isEmpty());
        assertEquals("S",part().estadoActual());assertTrue(json.comoLista(part().getCausas()).contains(causaMaterial));
        fuente.causas=Set.of(FuenteDeNetwork.FRESCURA);
        assertTrue(servicio.referenciasParaConsulta(extranjero).isEmpty());
        assertEquals("S",part().estadoActual());
        fuente.causas=Set.of();
        fuente.confirmacion=new ConfirmacionDisponibilidad(AHORA.minusSeconds(10),false,"fixture-confirmacion-recuperada");
        assertTrue(servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero).isEmpty(),
                "Recuperar frescura no sustituye la nueva decisión requerida por el cambio material anterior");
        assertEquals("S",part().estadoActual());assertEquals(aprobada.getReferencia(),version().getReferencia());
        assertEquals(1,vv.size());assertEquals(decisionesAprobadas,dd.size());
        assertTrue(outbox.stream().noneMatch(e->"NETWORK_FRESCURA_REVALIDADA".equals(e.getTipo())));
        solicitar(Cambio.MODIFICAR,"nueva-solicitud-material");
        servicio.decidir(decision(Decision.APROBAR),broker,prueba(broker),"nueva-decision-material",origen());
        assertEquals("A",part().estadoActual());assertEquals(2,vv.size());assertEquals(decisionesAprobadas+1,dd.size());
        assertEquals(1,servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero).size());
    }
    @Test void cambioPrivadoIrrelevanteNoReiniciaVersionNiExigeAprobacion() {
        activar();long numero=version().getNumero();int avisos=outbox.size();fuente.versionFisica++;
        var salida=servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero);
        assertEquals(1,salida.size());assertEquals(numero,version().getNumero());assertEquals(avisos,outbox.size());assertEquals("A",part().estadoActual());
    }
    @Test void faltaTratamientoConHuellaNulaNoSeFingeComoCambioDeDatos() {
        activar();fuente.legitimada=false;clearInvocations(versiones);
        assertTrue(servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero).isEmpty());
        assertEquals(Set.of("TRATAMIENTO_NETWORK_PENDIENTE_DE_VALIDACION"),new HashSet<>(json.comoLista(part().getCausas())));
        verify(versiones,never()).salidasActuales(anyCollection());
    }
    @Test void cambioEntreRevisionYConfirmacionNoApruebaOtroContenido() {
        solicitar(Cambio.ACTIVAR,"s");var d=decision(Decision.APROBAR);fuente.precio=new BigDecimal("2800.00");
        assertThrows(ConflictoException.class,()->servicio.decidir(d,broker,prueba(broker),"ap",origen()));
        assertTrue(vv.isEmpty());assertTrue(dd.isEmpty());assertEquals("P",solicitud().estadoActual());
    }
    @Test void versionOmitidaYClaveReutilizadaNoCreanOtraSolicitud() {
        var d=datos(Cambio.ACTIVAR);
        assertThrows(ReglaNegocioException.class,()->servicio.validarSolicitud(new DatosSolicitud(9L,Cambio.ACTIVAR,d.campos(),d.finalidad(),d.desde(),d.hasta(),"Alta",null,d.huellaDatos(),d.huellaAutoridad()),agente));
        solicitar(Cambio.ACTIVAR,"s");
        var otra=new DatosSolicitud(9L,Cambio.ACTIVAR,d.campos(),d.finalidad(),d.desde(),d.hasta(),"Otra intención",d.versionParticipacion(),d.huellaDatos(),d.huellaAutoridad());
        assertThrows(ConflictoException.class,()->servicio.solicitar(otra,agente,prueba(agente),"s",origen()));assertEquals(1,ss.size());
    }
    @Test void gobiernoNoConsultaCarteraNiParticipaPorAdministrar() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(9,admin));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.solicitar(datos(Cambio.ACTIVAR),admin,prueba(admin),"s",origen()));
        assertTrue(pp.isEmpty());
    }
    @Test void verificacionTecnicaIncompletaNoProduceVacioNiDatoCompartido() {
        activar();fuente.verificacionIncompleta=true;
        assertThrows(IllegalStateException.class,()->servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero));
        assertEquals("A",part().estadoActual());
    }
    @Test void bajaConcurrenteDespuesDeObtenerDatosSeRevalidaAntesDeEntregar() {
        activar();fuente.alVerificar=()->{if(++fuente.comprobaciones==2)part().transicionarA("I");};
        assertTrue(servicio.revalidarParaEntrega(List.of(part().getReferencia().toString()),extranjero).isEmpty());
    }
    @Test void numeracionPrivadaODecimalEquivalenteNoSePresentaComoModificacion() {
        activar();fuente.precio=new BigDecimal("2500.0");
        assertTrue(servicio.previsualizar(9,CAMPOS,broker).diferencias().isEmpty());
    }
    @Test void cambioFisicoSuspendeConActorRealYEventoCausalSinAbrirCarteraAjena() {
        activar();int consultasPropias=fuente.propias;fuente.precio=new BigDecimal("2800.00");
        Actor editor=new Actor(1,13,103,Actor.AGENTE);var hecho=hecho("PROPIEDAD",77,editor);
        servicio.reconciliarCambioCore(OrigenCambio.PROPIEDAD,77,editor,hecho);
        assertEquals("S",part().estadoActual());assertTrue(part().getCausas().contains("CAMBIO_DATOS"));
        assertEquals(editor,fuente.ultimoVerificador);assertEquals(consultasPropias,fuente.propias);
        var aviso=outbox.getLast();assertEquals("NETWORK_SUSPENDIDA",aviso.getTipo());
        assertEquals(900L,((Number)json.comoMapa(aviso.getCargaUtil()).get("idEventoCausal")).longValue());
        verify(versiones,never()).salidasActuales(anyCollection());
    }
    @Test void cambioCorePrivadoNoSuspendeNiEmiteYLaInelegibilidadDetieneEnElMismoHook() {
        activar();int avisos=outbox.size();fuente.versionFisica++;
        servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,9,agente,hecho("CAPTACION",9,agente));
        assertEquals("A",part().estadoActual());assertEquals(avisos,outbox.size());
        fuente.causas=Set.of("SIN_VIGENCIA");
        servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,9,agente,hecho("CAPTACION",9,agente));
        assertEquals("S",part().estadoActual());assertEquals(avisos+1,outbox.size());
        assertTrue(part().getCausas().contains("SIN_VIGENCIA"));assertEquals(1,vv.size());
    }
    @Test void hookRechazaHechoNoPersistidoOCausalAjenoYNoConcedeFacultadAlGobierno() {
        activar();int avisos=outbox.size();var h=hecho("CAPTACION",9,agente);
        assertThrows(IllegalStateException.class,()->servicio.reconciliarCambioCore(OrigenCambio.PROPIEDAD,9,agente,h));
        assertThrows(IllegalStateException.class,()->servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,10,agente,h));
        assertThrows(IllegalStateException.class,()->servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,9,broker,h));
        assertThrows(IllegalStateException.class,()->servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,9,extranjero,h));
        ReflectionTestUtils.setField(h,"id",null);
        assertThrows(IllegalStateException.class,()->servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,9,agente,h));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,9,admin,hecho("CAPTACION",9,admin)));
        assertEquals("A",part().estadoActual());assertEquals(avisos,outbox.size());
    }
    @Test void reasignacionRealDeGobiernoSuspendeSinDarCarteraNiSuplantarUnOperador() {
        activar();int consultas=fuente.propias;fuente.causas=Set.of("CAMBIO_AUTORIDAD");
        var evento=EventoDominio.de(1L,"ENCARGO_REASIGNADO","CAPTACION",9L,admin.idRolOperativo(),EventoDominio.CANAL_API);
        id(evento,901);
        servicio.reconciliarCambioCore(OrigenCambio.ENCARGO,9,admin,evento);
        assertEquals("S",part().estadoActual());assertEquals(admin,fuente.ultimoVerificador);
        assertEquals(consultas,fuente.propias);assertTrue(part().getCausas().contains("CAMBIO_AUTORIDAD"));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(9,admin));
        verify(versiones,never()).salidasActuales(anyCollection());
        assertEquals(901L,((Number)json.comoMapa(outbox.getLast().getCargaUtil()).get("idEventoCausal")).longValue());
    }
    @Test void resultadoOriginalEsMinimoYRecuperarloNoReemite() {
        var d=datos(Cambio.ACTIVAR);var r=servicio.solicitar(d,agente,prueba(agente),"s",origen());
        assertEquals(r,servicio.solicitar(d,agente,prueba(agente),"s",origen()));
        assertEquals(r,servicio.consultarResultado("solicitar_participacion_network","s",agente).orElseThrow());
        assertEquals(1,ss.size());assertEquals(1,outbox.size());
    }

    private Resultado solicitar(Cambio cambio,String clave) {
        var d=servicio.validarSolicitud(datos(cambio),agente);
        return servicio.solicitar(d,agente,prueba(agente),clave,origen());
    }
    private void activar() {
        if(ss.isEmpty())solicitar(Cambio.ACTIVAR,"s");
        if(vv.isEmpty())servicio.decidir(decision(Decision.APROBAR),broker,prueba(broker),"ap",origen());
    }
    private DatosSolicitud datos(Cambio c) {
        var f=fuente.consultarPropia(9,CAMPOS,FuenteDeNetwork.Accion.CONSULTAR,agente);
        return new DatosSolicitud(9L,c,CAMPOS,PoliticaProyeccionNetworkV1.FINALIDAD,AHORA.minusSeconds(1),AHORA.plusSeconds(86400),
                "Actuación propia",pp.isEmpty()?0L:part().getVersion(),f.huellaRelevante(),f.huellaAutoridad());
    }
    private DatosDecision decision(Decision d) {
        var s=solicitudPendiente();return new DatosDecision(s.getReferencia().toString(),d,"Decisión real propia",s.getVersion(),
                part().getVersion(),s.contenido().huellaDatos(),s.contenido().huellaAutoridad());
    }
    private ParticipacionNetwork part(){return pp.values().iterator().next();}
    private SolicitudParticipacionNetwork solicitud(){return ss.values().iterator().next();}
    private SolicitudParticipacionNetwork solicitudPendiente(){return ss.get(part().getSolicitudPendiente());}
    private VersionParticipacionNetwork version(){return vv.get(part().getVersionActual());}
    private Optional<ParticipacionNetwork> parte(long org,long encargo){return pp.values().stream().filter(p->p.getOrganizacionId()==org && p.getEncargo()==encargo).findFirst();}
    private Optional<SolicitudParticipacionNetwork> solicitud(long org,UUID ref){return ss.values().stream().filter(s->s.getOrganizacionId()==org && s.getReferencia().equals(ref)).findFirst();}
    private List<VersionParticipacionNetwork> actuales(){return vv.values().stream().filter(v->{var p=pp.get(v.getParticipacion());return Objects.equals(p.getVersionActual(),v.getId()) && Set.of("A","S").contains(p.estadoActual());}).toList();}
    private SolicitudParticipacionNetworkRepository.ContextoSolicitud contexto(SolicitudParticipacionNetwork s) {
        return proxy(SolicitudParticipacionNetworkRepository.ContextoSolicitud.class,Map.of("getId",s.getId(),"getParticipacion",s.getParticipacion(),
                "getEncargo",pp.get(s.getParticipacion()).getEncargo(),"getVersion",s.getVersion(),"getEstado",s.estadoActual(),"getAccion",s.getAccion()));
    }
    private <T> T fila(Class<T> tipo,VersionParticipacionNetwork v) {
        var p=pp.get(v.getParticipacion());var c=v.contenido();Map<String,Object> m=new HashMap<>();
        m.put("getId",v.getId());m.put("getOfertaRef",p.getReferencia());m.put("getReferencia",v.getReferencia());m.put("getNumero",v.getNumero());m.put("getParticipacion",v.getParticipacion());
        m.put("getOrganizacion",v.getOrganizacionId());m.put("getEncargo",p.getEncargo());m.put("getResponsable",c.responsable());m.put("getBroker",c.broker());
        m.put("getIndividual",c.individual());m.put("getPoliticaVersion",c.politicaVersion());m.put("getFinalidad",c.finalidad());m.put("getCampos",c.campos());
        m.put("getHuellaDatos",c.huellaDatos());m.put("getHuellaAutoridad",c.huellaAutoridad());m.put("getDesde",c.desde());m.put("getHasta",c.hasta());
        m.put("getEstadoParticipacion",p.estadoActual());m.put("getVersionParticipacion",p.getVersion());m.put("getCausasParticipacion",p.getCausas());m.put("getDatos",c.datos());
        return proxy(tipo,m);
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> tipo,Map<String,Object> valores) {
        return (T)Proxy.newProxyInstance(tipo.getClassLoader(),new Class<?>[]{tipo},(p,m,args)->m.getName().equals("toString")?valores.toString():valores.get(m.getName()));
    }
    private static void id(Object o,long id){ReflectionTestUtils.setField(o,"id",id);}
    private EventoDominio hecho(String tipo,long recurso,Actor actor) {
        var e=EventoDominio.de(actor.idOrganizacion(),"HECHO_CORE_DE_PRUEBA",tipo,recurso,actor.idRolOperativo(),EventoDominio.CANAL_API);
        id(e,900L);return e;
    }
    private static Procedencia origen(){return Procedencia.deCabecera("API");}
    private ContextoSesion prueba(Actor a){return ContextoSesion.de(sesion(a));}
    private ContextosService.SesionAccesoDTO sesion(Actor a){return new ContextosService.SesionAccesoDTO("sesion-"+a.idPersona(),1,
            new ContextosService.IdentidadAutenticada(1000+a.idPersona(),"fixture","fixture@example.test","Fixture"),
            new ContextosService.Contexto("ctx",a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),fuente.individual?"INDIVIDUAL":"EMPRESARIAL","Prueba"),
            AHORA.minusSeconds(30),AHORA.plusSeconds(10000),EstadoDeAcceso.SIN_RESTRICCIONES);}

    private class FuenteFixture implements FuenteDeNetwork {
        @Override public Map<String,Object> camposDeInvitacion(com.controllocal.service.colaboracion.AccesoFuenteDeInvitacion.Permiso permiso){
            throw new UnsupportedOperationException("Este fixture de participación Network no modela invitaciones privadas.");
        }
        private Actor ultimoVerificador;
        boolean individual,legitimada=true,verificacionIncompleta;int propias,comprobaciones;long versionFisica=1;
        BigDecimal precio=new BigDecimal("2500.00");Set<String> causas=Set.of();Runnable alVerificar;
        ConfirmacionDisponibilidad confirmacion=new ConfirmacionDisponibilidad(AHORA.minusSeconds(100),false,"fixture-confirmacion-encargo9-org1-ALQUILER");
        private String autoridad(){return Idempotencia.huellaExacta("TRATAMIENTO_FIXTURE_EXPLICITO_ENCARGO9_V1/"+individual);}
        private Map<String,Object> datos(Set<String> campos) {
            Map<String,Object> datos=new LinkedHashMap<>();
            if(campos.contains("ENCARGO:operacion"))datos.put("ENCARGO:operacion","ALQUILER");
            if(campos.contains("PROPIEDAD:tipo_propiedad"))datos.put("PROPIEDAD:tipo_propiedad","DEPARTAMENTO");
            if(campos.contains("ENCARGO:precio_pedido"))datos.put("ENCARGO:precio_pedido",Map.of("importe",precio,"moneda","PEN","periodo","MENSUAL"));
            return datos;
        }
        private String hash(Set<String> campos){return Idempotencia.huellaExacta(json.objetoCanonico(datos(campos)));}
        private Set<String> motivos(){return !legitimada?Set.of("TRATAMIENTO_NETWORK_PENDIENTE_DE_VALIDACION"):causas;}
        @Override public AutoridadActual consultarAutoridadPropia(long id,Actor actor) {
            if(actor.idOrganizacion()!=1 || id!=9)throw new NoEncontradoException("Encargo");
            return new AutoridadActual(1,9,100,individual?null:101L,individual,motivos().isEmpty(),motivos());
        }
        @Override public FuentePropia consultarPropia(long id,Set<String> campos,Accion accion,Actor actor) {
            propias++;consultarAutoridadPropia(id,actor);PoliticaProyeccionNetworkV1.exigirCampos(campos);
            return new FuentePropia(1,9,100,individual?null:101L,individual,1,versionFisica,PoliticaProyeccionNetworkV1.VERSION,
                    PoliticaProyeccionNetworkV1.FINALIDAD,datos(campos),hash(campos),autoridad(),motivos().isEmpty(),motivos());
        }
        @Override public Map<Long,VerificacionActual> verificarActuales(Collection<ReferenciaAutorizada> refs,Actor actorReal) {
            ultimoVerificador=actorReal;
            if(alVerificar!=null)alVerificar.run();if(verificacionIncompleta)return Map.of();
            Map<Long,VerificacionActual> r=new HashMap<>();
            for(var ref:refs)r.put(ref.idEncargo(),new VerificacionActual(1,9,100,individual?null:101L,individual,
                    PoliticaProyeccionNetworkV1.VERSION,PoliticaProyeccionNetworkV1.FINALIDAD,
                    motivos().isEmpty()?hash(ref.campos()):null,motivos().isEmpty()?autoridad():null,
                    motivos().isEmpty(),motivos(),motivos().isEmpty()?confirmacion:null));
            return r;
        }
        @Override public Map<Long,VerificacionActual> verificarCambioCore(Collection<ReferenciaAutorizada> refs,Actor actorReal,EventoDominio eventoCausal) {
            return verificarActuales(refs,actorReal);
        }
    }
}
