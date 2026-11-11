package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.*;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial;
import com.controllocal.domain.inmueble.*;
import com.controllocal.domain.persona.*;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.ContratoService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** RF058/059/CAT-E25: preparación no es firma; la confirmación interna no produce dinero. */
class ContratoServiceImplTest {
    private static final long ORG=1,CID=40;
    private static final LocalDate HECHO=LocalDate.of(2026,10,12),INICIO=LocalDate.of(2026,11,1),FIN=INICIO.plusYears(1);
    private final Actor agente=new Actor(ORG,3,30,"AGENTE"),broker=new Actor(ORG,2,20,"BROKER"),gobierno=new Actor(ORG,1,10,"TENANT_ADMIN");
    private final ContextoSesion sid=new ContextoSesion("fixture",2000000000,"sid-fixture",1);
    private final Procedencia proc=new Procedencia("API",null,null,null,null,null,null,null,null);
    private final ContratoAlquilerRepository contratos=mock(ContratoAlquilerRepository.class);
    private final SolicitudAlquilerRepository solicitudes=mock(SolicitudAlquilerRepository.class);
    private final OportunidadComercialRepository oportunidades=mock(OportunidadComercialRepository.class);
    private final CaptacionRepository captaciones=mock(CaptacionRepository.class);
    private final PropiedadRepository propiedades=mock(PropiedadRepository.class);
    private final PrecioPropiedadRepository precios=mock(PrecioPropiedadRepository.class);
    private final com.controllocal.service.publicacion.HistoriadorPublicacion publicaciones=mock(com.controllocal.service.publicacion.HistoriadorPublicacion.class);
    private final ComisionService comisiones=mock(ComisionService.class);
    private final Alcances alcances=mock(Alcances.class);
    private final HistorialEstadoRepository historial=mock(HistorialEstadoRepository.class);
    private final TareaService tareas=mock(TareaService.class);
    private final AlertaService alertas=mock(AlertaService.class);
    private final SupervisionAgenteRepository supervisiones=mock(SupervisionAgenteRepository.class);
    private final RevisionDisponibilidadRepository revisiones=mock(RevisionDisponibilidadRepository.class);
    private final AccesoContrato acceso=mock(AccesoContrato.class);
    private final PreparacionesDeContrato preparaciones=mock(PreparacionesDeContrato.class);
    private final ContextosDeActividad contextos=mock(ContextosDeActividad.class);
    private final ComandosIdempotentes comandos=mock(ComandosIdempotentes.class);
    private final ComandoIdempotenteRepository recibos=mock(ComandoIdempotenteRepository.class);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final EntityManager em=mock(EntityManager.class);
    private final AccesoEncargo accesoEncargo=mock(AccesoEncargo.class);
    private final MesaNegociacionRepository mesas=mock(MesaNegociacionRepository.class);
    private final Transiciones transiciones=new Transiciones(historial);
    private final ContratoServiceImpl service=crear(acceso);
    private final SolicitudAlquiler solicitud=solicitud();
    private final OportunidadComercial oportunidad=solicitud.getOportunidad();
    private final Captacion encargo=oportunidad.getCaptacion();
    private final Propiedad propiedad=encargo.getPropiedad();
    private final ReferenciaPacto pacto=new ReferenciaPacto(7,70);
    private final ReferenciaFuenteEconomica refFuente=new ReferenciaFuenteEconomica("REVISION_C5",60,1);

    @BeforeEach void montar() {
        when(solicitudes.buscarFicha(ORG,5L)).thenReturn(Optional.of(solicitud));
        when(oportunidades.buscarFicha(ORG,8L)).thenReturn(Optional.of(oportunidad));
        when(accesoEncargo.leer(7,broker)).thenReturn(encargo);
        when(propiedades.bloquearParaEscritura(ORG,9)).thenReturn(Optional.of(propiedad));
        when(contratos.saveAndFlush(any(ContratoAlquiler.class))).thenAnswer(i->{ContratoAlquiler c=i.getArgument(0);if(c.getId()==null)id(c,c.getContratoAnterior()==null?CID:41);return c;});
        when(contextos.ahora()).thenReturn(Instant.parse("2026-10-31T12:00:00Z"));
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(ORG,false,List.of(30L)));
    }
    @Test void prepararCreaPSinFirmaNiEfectoComercialOMonetario() {
        var f=service.iniciarEnProceso(new DatosContrato(5L,"Documento en preparación"),agente,sid,"preparar-1",proc);
        assertEquals("P",f.estadoContrato());assertNull(f.fechaCierre());assertNull(f.comision());
        assertEquals(OportunidadComercial.ABIERTA,oportunidad.estadoActual());assertEquals(Captacion.APROBADO,encargo.estadoActual());
        assertEquals(DisponibilidadComercial.DISPONIBLE.codigo(),propiedad.getDisponibilidadComercial());
        verifyNoInteractions(comisiones,precios,publicaciones,tareas,alertas,historial);verify(contextos).iniciar(agente,sid);
        verify(comandos).registrar(eq(agente),eq("preparar-1"),eq("PREPARAR_CONTRATO_ALQUILER"),anyString(),eq("CONTRATO_ALQUILER"),eq(CID),eq(proc),anyString());
    }
    @Test void preparacionParcialNoInterpretaDigitosDelTextoTentativo() {
        solicitud.setPlazoContratoMeses(null);solicitud.setPlazoTentativo("12 o 24 meses, por acordar");
        var f=service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"preparar-2",proc);
        assertNull(f.plazoContratoMeses());assertNull(f.fechaFinContrato());assertNull(f.fechaCierre());
    }
    @Test void prepararExigeSolicitudRealAprobadaPropia() {
        assertThrows(ReglaNegocioException.class,()->service.iniciarEnProceso(new DatosContrato(null,null),agente,sid,"p-3",proc));
        solicitud.setAgente(agente(99));assertThrows(AccesoNoAutorizadoException.class,()->service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"p-4",proc));
        verify(contratos,never()).saveAndFlush(any());
    }
    @Test void solicitudSinAprobarNoGeneraPreparacion() {
        ReflectionTestUtils.setField(solicitud,"estado",SolicitudAlquiler.REGISTRADA);
        assertThrows(ReglaNegocioException.class,()->service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"p-5",proc));verify(contratos,never()).saveAndFlush(any());
    }
    @Test void mesaConContratoNoAdmiteOtraPreparacionOriginal() {
        when(contratos.existeDeOportunidad(ORG,8)).thenReturn(true);
        assertThrows(ConflictoException.class,()->service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"p-6",proc));
    }
    @Test void identidadActualSeExigeAntesDeBuscarReciboYEfecto() {
        doThrow(new AccesoNoAutorizadoException()).when(contextos).iniciar(agente,sid);
        assertThrows(AccesoNoAutorizadoException.class,()->service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"p-7",proc));verifyNoInteractions(recibos,comandos);verify(contratos,never()).saveAndFlush(any());
    }
    @Test void repetirPreparacionReautorizaResultadoOriginalSinRepetirAlta() {
        var original=service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"p-replay",proc);
        var recibo=mock(ComandoIdempotente.class);when(recibo.getResultado()).thenReturn(documentos.objetoCanonico(original));
        when(comandos.buscar(eq(agente),eq("p-replay"),anyString(),anyString())).thenReturn(Optional.of(recibo));
        var c=contrato("P");when(acceso.leer(CID,agente)).thenReturn(c);
        assertEquals(original,service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"p-replay",proc));verify(contratos,times(1)).saveAndFlush(any());verify(contextos,times(2)).iniciar(agente,sid);
        doThrow(new AccesoNoAutorizadoException()).when(acceso).leer(CID,agente);
        assertThrows(AccesoNoAutorizadoException.class,()->service.iniciarEnProceso(new DatosContrato(5L,null),agente,sid,"p-replay",proc));
    }
    @Test void renovarPreparaSucesorSinCambiarPadreVNiCrearComision() {
        var padre=contrato("V");when(contratos.bloquear(ORG,CID)).thenReturn(Optional.of(padre));
        var f=service.renovar(CID,new DatosRenovacion(INICIO,FIN,new BigDecimal("2700"),"PEN","Renovación para revisar"),agente,sid,"ren-1",proc);
        assertEquals("P",f.estadoContrato());assertNull(f.fechaCierre());assertEquals(CID,f.idContratoAnterior());assertNotEquals(padre.getId(),f.id());assertEquals("V",padre.estadoActual());verifyNoInteractions(comisiones,precios,publicaciones,tareas,alertas,historial);
    }
    @Test void renovarNoSustituyeSucesorYaPreparado() {
        var padre=contrato("V");when(contratos.bloquear(ORG,CID)).thenReturn(Optional.of(padre));when(contratos.existsByOrganizacionIdAndContratoAnteriorId(ORG,CID)).thenReturn(true);
        assertThrows(ConflictoException.class,()->service.renovar(CID,new DatosRenovacion(INICIO,FIN,BigDecimal.TEN,"PEN","Preparar"),agente,sid,"ren-2",proc));verify(contratos,never()).saveAndFlush(any());verifyNoInteractions(comisiones);
    }
    @Test void confirmarUsaFechaYFuenteFinalYCierraSoloRecursosLegitimos() {
        assertEquals(CID,confirmar(null,null,null,fuenteUnidad(null)));var c=guardado();
        assertEquals(HECHO,c.getFechaCierre());assertEquals(INICIO,c.getFechaInicioContrato());assertEquals(FIN,c.getFechaFinContrato());assertEquals(new BigDecimal("2500"),c.getRentaContractual());assertEquals("D",c.estadoActual());assertEquals(20L,c.getIdRolBrokerCierre());
        assertEquals(OportunidadComercial.FINALIZADA_EXITOSA,oportunidad.estadoActual());assertEquals(SolicitudAlquiler.CERRADA,solicitud.estadoActual());assertEquals(Captacion.FINALIZADO,encargo.estadoActual());assertEquals(DisponibilidadComercial.ALQUILADO.codigo(),propiedad.getDisponibilidadComercial());
        verify(publicaciones).cerrarPorHito(encargo,broker);verify(historial,times(4)).save(any());verify(precios).save(any());
        verify(tareas).resolverPorCierreConfirmado(1L,broker);
        verifyNoMoreInteractions(tareas);verifyNoInteractions(comisiones);
    }
    @Test void rentaFinalNoSeTomaDeSolicitudNiTotalMensualDeMatching() {
        solicitud.setMontoPropuesto(new BigDecimal("9999"));confirmar(null,null,null,fuenteUnidad(null));assertEquals(new BigDecimal("2500"),guardado().getRentaContractual());
        var c=ArgumentCaptor.forClass(PrecioPropiedad.class);verify(precios).save(c.capture());assertEquals(new BigDecimal("2500"),c.getValue().getMonto());assertEquals(HECHO,c.getValue().getFecha());
    }
    @Test void sinSolicitudNiRolClienteNoFabricaEsosRecursos() {
        oportunidad.setCliente(null);service.confirmarHito(contexto(null,null),hito(null),fuenteUnidad(null),acuerdo(),broker,null);
        var c=guardado();assertNull(c.getSolicitud());assertNull(c.getIdRolCliente());assertEquals(30L,c.getIdRolAgenteCierre());verify(solicitudes,never()).buscarFicha(anyLong(),anyLong());verify(solicitudes,never()).save(any());verifyNoInteractions(comisiones);
    }
    @Test void faltanFechasContractualesNoSeInventanDesdeLaFirma() {
        var sinFechas=new AcuerdoFinalDocumentado(null,dinero(),null,List.of(),"Fuente revisada",null);
        assertThrows(ReglaNegocioException.class,()->service.confirmarHito(contexto(null,5L),hito(null),fuenteUnidad(null),sinFechas,broker,null));verify(contratos,never()).saveAndFlush(any());
    }
    @Test void fechaDeHitoDesconocidaNoSeSustituyePorHoy() {
        var h=new FuenteHitoConfirmado(new ReferenciaContextoEconomico(1),new ReferenciaHito(2,1),TipoHitoContractual.CONTRATO_ALQUILER,null,OffsetDateTime.now(),pacto,refFuente,null,proc);
        assertThrows(ReglaNegocioException.class,()->service.confirmarHito(contexto(null,5L),h,fuenteUnidad(null),acuerdo(),broker,null));verify(contratos,never()).saveAndFlush(any());
    }
    @Test void sinMesaRealNoCreaOportunidadFicticiaParaCerrar() {
        var sinMesa=new ContextoEconomico(new ReferenciaContextoEconomico(1),ORG,7,OperacionInmobiliaria.ALQUILER,null,5L,null,null,pacto,fuenteUnidad(null),List.of());
        assertThrows(ReglaNegocioException.class,()->service.confirmarHito(sinMesa,hito(null),fuenteUnidad(null),acuerdo(),broker,null));verifyNoInteractions(oportunidades,comisiones);
    }
    @Test void autoridadCompetenteEsObligatoriaAntesDelCierre() {
        doThrow(new AccesoNoAutorizadoException()).when(accesoEncargo).exigirDecision(encargo,broker);
        assertThrows(AccesoNoAutorizadoException.class,()->confirmar(null,null,null,fuenteUnidad(null)));verify(contratos,never()).saveAndFlush(any());verifyNoInteractions(precios,comisiones);
    }
    @Test void contextoDeOtroTenantNoSeReinterpreta() {
        var ajeno=new ContextoEconomico(new ReferenciaContextoEconomico(1),99,7,OperacionInmobiliaria.ALQUILER,8L,5L,null,null,pacto,fuenteUnidad(null),List.of());
        assertThrows(ConflictoException.class,()->service.confirmarHito(ajeno,hito(null),fuenteUnidad(null),acuerdo(),broker,null));verifyNoInteractions(accesoEncargo);verify(contratos,never()).saveAndFlush(any());
    }
    @Test void precioCUsaOfertaDelAcuerdoExactoSinElegirAceptadaActual() {
        var a=new MesaNegociacionRepository.AcuerdoGuardado(80,2,81,4,"{}",Instant.parse("2026-10-12T12:00:00Z"));when(mesas.acuerdoExacto(ORG,80,2)).thenReturn(Optional.of(a));confirmar(null,null,null,fuenteUnidad(80L));
        var c=ArgumentCaptor.forClass(PrecioPropiedad.class);verify(precios).save(c.capture());assertEquals(81L,c.getValue().getIdOferta());verify(mesas).acuerdoExacto(ORG,80,2);
    }
    @Test void ausenciaDeAcuerdoNoInventaVueltaDeNegociacion() {
        confirmar(null,null,null,fuenteUnidad(null));var c=ArgumentCaptor.forClass(PrecioPropiedad.class);verify(precios).save(c.capture());assertNull(c.getValue().getIdOferta());verifyNoInteractions(mesas);
    }
    @Test void paqueteCierraSoloUnidadesCubiertasSinImputarRentaTotalIndividual() {
        var otra=propiedad(10);when(propiedades.bloquearParaEscritura(ORG,10)).thenReturn(Optional.of(otra));
        var objeto=new OfertaService.ObjetoExacto(OfertaService.TipoObjeto.COMPOSICION,7L,1L,null,null,90L,1L,List.of(9L,10L));var f=new FuenteEconomicaFinal(refFuente,null,null,objeto,null,dinero(),List.of(),proc);
        confirmar(null,null,null,f);assertEquals(DisponibilidadComercial.ALQUILADO.codigo(),otra.getDisponibilidadComercial());verify(precios,never()).save(any());verify(propiedades,times(2)).bloquearParaEscritura(eq(ORG),anyLong());verify(publicaciones).cerrarPorHito(encargo,broker);
    }
    @Test void elCierreDeDifusionSeDelegaAlEscritorUnico() {
        confirmar(null,null,null,fuenteUnidad(null));verify(publicaciones).cerrarPorHito(encargo,broker);
    }
    @Test void confirmarPExigeHuellaExactaBajoLocks() {
        var p=contrato("P");var revisada=metadata(p,"h1");var actual=metadata(p,"h2");when(preparaciones.consultar(CID,broker)).thenReturn(actual);when(contratos.bloquear(ORG,CID)).thenReturn(Optional.of(p));
        assertThrows(ConflictoException.class,()->confirmar(CID,revisada,null,fuenteUnidad(null)));verify(contratos,never()).saveAndFlush(any());verifyNoInteractions(precios,comisiones);
    }
    @Test void confirmarRenovacionConsumeMismoPDespuesDeRetirarVDelPadre() {
        var padre=contrato("V");var p=contrato("P");id(p,41L);p.setContratoAnterior(padre);var m=metadata(p,"h1");when(preparaciones.consultar(41,broker)).thenReturn(m);when(contratos.bloquear(ORG,CID)).thenReturn(Optional.of(padre));when(contratos.bloquear(ORG,41)).thenReturn(Optional.of(p));
        assertEquals(41,confirmar(41L,m,null,fuenteUnidad(null)));assertEquals("R",padre.estadoActual());assertEquals("D",p.estadoActual());assertEquals(HECHO,p.getFechaCierre());
        var orden=inOrder(contratos);orden.verify(contratos).bloquear(ORG,CID);orden.verify(contratos).bloquear(ORG,41);orden.verify(contratos).saveAndFlush(padre);orden.verify(contratos).saveAndFlush(p);
        verifyNoInteractions(comisiones);verify(tareas,never()).resolverPorCierreConfirmado(anyLong(),any());
    }
    @Test void rectificarConservaContratoSinSegundaVentaPrecioCNiCobro() {
        var c=contrato("D");c.atribuir(30L,77L,7L,9L,50L);var m=metadata(c,"actual");when(preparaciones.consultar(CID,broker)).thenReturn(m);when(contratos.bloquear(ORG,CID)).thenReturn(Optional.of(c));
        assertEquals(CID,confirmar(CID,null,new ReferenciaHito(2,1),fuenteUnidad(null)));assertEquals("D",c.estadoActual());assertEquals(77L,c.getIdRolBrokerCierre());verifyNoInteractions(precios,publicaciones,tareas,alertas,comisiones,historial);verify(oportunidades,never()).save(any());
    }
    @Test void rectificarNoConfirmaPorPrimeraVezUnP() {
        var p=contrato("P");var m=metadata(p,"actual");when(preparaciones.consultar(CID,broker)).thenReturn(m);when(contratos.bloquear(ORG,CID)).thenReturn(Optional.of(p));
        assertThrows(ConflictoException.class,()->confirmar(CID,null,new ReferenciaHito(2,1),fuenteUnidad(null)));verify(contratos,never()).saveAndFlush(any());
    }
    @Test void callbackEsMandatorySinEfectoresLegacyDeFirmaODinero() throws Exception {
        var m=ContratoServiceImpl.class.getMethod("confirmarHito",ContextoEconomico.class,FuenteHitoConfirmado.class,FuenteEconomicaFinal.class,AcuerdoFinalDocumentado.class,Actor.class,PreparacionContrato.class);assertEquals(Propagation.MANDATORY,m.getAnnotation(Transactional.class).propagation());
        assertFalse(Arrays.stream(ContratoService.class.getMethods()).anyMatch(x->Set.of("registrar","firmar","asignarComision","registrarCobroComision","registrarMovimientoComision").contains(x.getName())));
    }
    @Test void fichaForwardeaUnaSolaProyeccionFinancieraCanonica() {
        var c=contrato("D");var f=mock(ComisionService.FichaComision.class);when(acceso.leer(CID,agente)).thenReturn(c);when(comisiones.porContratosAlquiler(List.of(CID),agente)).thenReturn(Map.of(CID,f));assertSame(f,service.obtener(CID,agente).comision());
    }
    @Test void listadoResuelveComisionesEnLoteNoUnaPorContrato() {
        var c=contrato("D");var f=mock(ComisionService.FichaComision.class);when(contratos.buscar(eq(ORG),eq(false),eq(false),eq(List.of(30L)),isNull(),isNull(),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(c)));when(comisiones.porContratosAlquiler(List.of(CID),broker)).thenReturn(Map.of(CID,f));
        assertSame(f,service.listar(1,20,broker).items().getFirst().comision());verify(comisiones,never()).porContratoAlquiler(anyLong(),any());
    }
    @Test void brokerSinEquipoObtieneConjuntoVacioSinLeerDinero() {
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(ORG,false,List.of()));assertEquals(0,service.listar(1,20,broker).total());assertEquals(0,service.resumenCierres(filtros(null),broker).cierres());verifyNoInteractions(contratos,comisiones);
    }
    @Test void gobiernoNoLeeContratoNiDineroPorConocerId() {
        doThrow(new AccesoNoAutorizadoException()).when(acceso).leer(CID,gobierno);assertThrows(AccesoNoAutorizadoException.class,()->service.obtener(CID,gobierno));verifyNoInteractions(comisiones);
    }
    @Test void agenteDeSolicitudConservaContratoAunqueNoLeCorrespondaProyeccionFinanciera() {
        var c=contrato("V");encargo.setAgente(agente(99));
        var cabecera=mock(ContratoAlquilerRepository.PreparacionActual.class);
        when(cabecera.getOportunidad()).thenReturn(8L);when(cabecera.getEncargo()).thenReturn(7L);when(cabecera.getSolicitud()).thenReturn(5L);
        when(cabecera.getAgenteSolicitud()).thenReturn(30L);when(cabecera.getResponsableEncargo()).thenReturn(99L);
        when(contratos.preparacionActual(ORG,CID)).thenReturn(Optional.of(cabecera));when(contratos.buscarFicha(ORG,CID)).thenReturn(Optional.of(c));
        when(comisiones.porContratosAlquiler(List.of(CID),agente)).thenReturn(Map.of());
        var real=crear(new AccesoContrato(contratos,alcances));var f=real.finalizar(CID,new DatosTransicion(HECHO,"Fin confirmado"),agente);
        assertEquals("F",f.estadoContrato());assertEquals(new BigDecimal("2500"),f.rentaMensual());assertNull(f.comision());
        verify(comisiones,never()).porContratoAlquiler(anyLong(),any());
        when(cabecera.getAgenteSolicitud()).thenReturn(31L);
        assertThrows(AccesoNoAutorizadoException.class,()->real.obtener(CID,agente));verify(comisiones,times(1)).porContratosAlquiler(List.of(CID),agente);
    }
    @Test void falloTecnicoDeProyeccionFinancieraSePropagaSinSimularAusencia() {
        var c=contrato("D");when(acceso.leer(CID,agente)).thenReturn(c);var fallo=new IllegalStateException("Fallo técnico de ensayo");
        when(comisiones.porContratosAlquiler(List.of(CID),agente)).thenThrow(fallo);
        assertSame(fallo,assertThrows(IllegalStateException.class,()->service.obtener(CID,agente)));
    }
    @Test void finalizarMantieneOcupacionYAbreRevisionExpresaSinDinero() {
        var c=contrato("V");propiedad.marcarAlquilado();when(acceso.leer(CID,agente)).thenReturn(c);service.finalizar(CID,new DatosTransicion(HECHO,"Fin del contrato"),agente);
        assertEquals("F",c.estadoActual());assertEquals(DisponibilidadComercial.ALQUILADO.codigo(),propiedad.getDisponibilidadComercial());verify(tareas).crearRevisionInmueble(eq(9L),eq(30L),anyString(),eq(CID),eq(agente));verify(comisiones).porContratosAlquiler(List.of(CID),agente);verifyNoMoreInteractions(comisiones);
    }
    @Test void anularPreparacionNoSimulaFirmaNiCondonaComision() {
        var c=contrato("P");when(acceso.leer(CID,broker)).thenReturn(c);service.anular(CID,new DatosTransicion(HECHO,"No se formalizó"),broker);assertEquals("A",c.estadoActual());assertNull(c.getFechaCierre());verify(comisiones).porContratosAlquiler(List.of(CID),broker);verifyNoMoreInteractions(comisiones);verifyNoInteractions(tareas);
    }
    @Test void transicionIlegalYMismoEstadoNoEscribenHistoria() {
        var p=contrato("P");when(acceso.leer(CID,agente)).thenReturn(p);assertThrows(ReglaNegocioException.class,()->service.activar(CID,new DatosTransicion(HECHO,"Cambio"),agente));
        var f=contrato("F");when(acceso.leer(CID,agente)).thenReturn(f);assertThrows(ReglaNegocioException.class,()->service.finalizar(CID,new DatosTransicion(HECHO,"Cambio"),agente));verifyNoInteractions(historial);
    }
    @Test void resumenSeparaPoblacionFinancieraYDesconocidosDeCero() {
        var filas=List.of(contadores(4,3,1,false),finanza("CALCULADO","PEN","500",1,1),finanza("CALCULADO","USD","40",1,0),finanza("DEVENGADO",null,null,0,1),finanza("RECIBIDO","PEN","100",1,0),finanza("SALDO","PEN","400",1,1));
        when(contratos.finanzasDeCierres(ORG,false,false,"{30}",null,null)).thenReturn(filas);var r=service.resumenCierres(filtros(null),broker);
        assertEquals(4,r.cierres());assertEquals(3,r.cierresConLecturaFinanciera());assertEquals(1,r.sinEvaluacion());assertEquals(new BigDecimal("500"),r.calculado().getFirst().monto());assertEquals(1,r.calculado().getFirst().pendientes());assertNull(r.devengadoAcreditado().getFirst().monto());assertNull(r.devengadoAcreditado().getFirst().moneda());assertEquals(new BigDecimal("100"),r.ingresosRealmenteRecibidos().getFirst().monto());
    }
    @Test void resumenConTextoUsaMismaPoblacionFiltradaDeBandeja() {
        var filas=List.of(contadores(1,1,0,false));when(contratos.finanzasDeCierresPorTexto(ORG,false,false,"{30}","Surco",null,null)).thenReturn(filas);assertEquals(1,service.resumenCierres(filtros(" Surco "),broker).cierres());verify(contratos,never()).finanzasDeCierres(anyLong(),anyBoolean(),anyBoolean(),anyString(),any(),any());
    }
    @Test void resumenNoConvierteAnomaliaHistoricaEnIngresoCero() {
        var filas=List.of(contadores(1,1,0,true));when(contratos.finanzasDeCierres(ORG,false,false,"{30}",null,null)).thenReturn(filas);assertThrows(IllegalStateException.class,()->service.resumenCierres(filtros(null),broker));
    }
    @Test void resumenNoAceptaMonedaIlegibleNiTotalSinPoblacion() {
        var filas=List.of(contadores(1,1,0,false),finanza("RECIBIDO","???","20",1,0));when(contratos.finanzasDeCierres(ORG,false,false,"{30}",null,null)).thenReturn(filas);assertThrows(IllegalStateException.class,()->service.resumenCierres(filtros(null),broker));
        when(contratos.finanzasDeCierres(ORG,false,false,"{30}",null,null)).thenReturn(List.of());assertThrows(IllegalStateException.class,()->service.resumenCierres(filtros(null),broker));
    }

    private long confirmar(Long contrato,PreparacionContrato p,ReferenciaHito r,FuenteEconomicaFinal f){return service.confirmarHito(contexto(contrato,5L),hito(r),f,acuerdo(),broker,p);}
    private ContratoServiceImpl crear(AccesoContrato guardia){return new ContratoServiceImpl(contratos,solicitudes,oportunidades,captaciones,propiedades,precios,
            publicaciones,comisiones,alcances,transiciones,tareas,alertas,supervisiones,revisiones,guardia,preparaciones,contextos,
            comandos,recibos,documentos,em,accesoEncargo,mesas);}
    private ContextoEconomico contexto(Long contrato,Long sol){return new ContextoEconomico(new ReferenciaContextoEconomico(1),ORG,7,OperacionInmobiliaria.ALQUILER,8L,sol,contrato,null,pacto,fuenteUnidad(null),List.of());}
    private FuenteHitoConfirmado hito(ReferenciaHito r){return new FuenteHitoConfirmado(new ReferenciaContextoEconomico(1),new ReferenciaHito(2,r==null?1:2),TipoHitoContractual.CONTRATO_ALQUILER,HECHO,OffsetDateTime.parse("2026-10-31T12:00Z"),pacto,refFuente,r,proc);}
    private FuenteEconomicaFinal fuenteUnidad(Long a){return new FuenteEconomicaFinal(refFuente,a,a==null?null:2L,new OfertaService.ObjetoExacto(OfertaService.TipoObjeto.UNIDAD,7L,1L,9L,1L,null,null,List.of(9L)),null,dinero(),List.of(),proc);}
    private OfertaService.Dinero dinero(){return new OfertaService.Dinero(new BigDecimal("2500"),"PEN",null,null);}
    private AcuerdoFinalDocumentado acuerdo(){return new AcuerdoFinalDocumentado(null,dinero(),null,List.of(),"Renta final documentada",new TerminosAlquiler(INICIO,FIN));}
    private ContratoAlquiler contrato(String e){var c=new ContratoAlquiler();c.setOrganizacionId(ORG);id(c,CID);c.setOportunidad(oportunidad);c.setSolicitud(solicitud);c.setFechaCierre("P".equals(e)?null:HECHO);c.setFechaInicioContrato(INICIO);c.setFechaFinContrato(FIN);c.setRentaContractual(new BigDecimal("2500"));c.setMoneda("PEN");c.atribuir(30L,20L,7L,9L,50L);new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(c,e);return c;}
    private PreparacionContrato metadata(ContratoAlquiler c,String h){return new PreparacionContrato(c.getId(),c.estadoActual(),7,8,5L,c.getContratoAnterior()==null?null:c.getContratoAnterior().getId(),c.getRentaContractual(),c.getMoneda(),c.getFechaInicioContrato(),c.getFechaFinContrato(),h);}
    private ContratoAlquiler guardado(){var c=ArgumentCaptor.forClass(ContratoAlquiler.class);verify(contratos).saveAndFlush(c.capture());return c.getValue();}
    private static SolicitudAlquiler solicitud(){var s=new SolicitudAlquiler();s.setOrganizacionId(ORG);id(s,5);s.setCodigoSolicitud("SOL-TEST");s.setMontoPropuesto(new BigDecimal("9000"));s.setMoneda("PEN");s.setFechaInicioContrato(INICIO);s.setPlazoContratoMeses(12);s.setAgente(agente(30));var o=new OportunidadComercial();o.setOrganizacionId(ORG);id(o,8);o.setCodigoOportunidad("OP-TEST");o.setAgente(s.getAgente());var cap=new Captacion();cap.setOrganizacionId(ORG);id(cap,7);cap.setCodigoCaptacion("CAP-TEST");cap.setAgente(s.getAgente());cap.setPropiedad(propiedad(9));new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(cap,Captacion.APROBADO);o.setCaptacion(cap);new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(o,OportunidadComercial.ABIERTA);var cli=new DetalleCliente();cli.setRol(rol("Cliente",TipoRol.CLIENTE));id(cli,50);o.setCliente(cli);s.setOportunidad(o);new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(s,SolicitudAlquiler.APROBADA);return s;}
    private static Propiedad propiedad(long n){var p=new Propiedad();p.setOrganizacionId(ORG);id(p,n);p.setDireccion("Av. de prueba");p.setDistrito("Surco");p.iniciarDisponible();return p;}
    private static DetalleAgente agente(long n){var a=new DetalleAgente();a.setRol(rol("Agente",TipoRol.AGENTE));id(a,n);return a;}
    private static PersonaRol rol(String n,TipoRol t){var p=new Persona();p.setNombresORazonSocial(n);var r=new PersonaRol();r.setPersona(p);r.setTipoRol(t);return r;}
    private static Publicacion publicacion(String e){var p=new Publicacion();p.setOrganizacionId(ORG);id(p,12);p.setIdPropiedad(9L);p.setEstado(e);return p;}
    private static void id(Object e,long n){ReflectionTestUtils.setField(e,"id",n);}
    private static FiltrosContrato filtros(String t){return new FiltrosContrato(t,null,null,null,1,20);}
    private static ContratoAlquilerRepository.FinanzasCierres contadores(long c,long l,long p,boolean a){var f=mock(ContratoAlquilerRepository.FinanzasCierres.class);when(f.getDimension()).thenReturn("CONTADORES");when(f.getCierres()).thenReturn(c);when(f.getConLecturaFinanciera()).thenReturn(l);when(f.getSinEvaluacion()).thenReturn(p);when(f.getAnomalia()).thenReturn(a);return f;}
    private static ContratoAlquilerRepository.FinanzasCierres finanza(String d,String m,String v,long c,long p){var f=mock(ContratoAlquilerRepository.FinanzasCierres.class);when(f.getDimension()).thenReturn(d);when(f.getMoneda()).thenReturn(m);when(f.getMonto()).thenReturn(v==null?null:new BigDecimal(v));when(f.getDeterminados()).thenReturn(c);when(f.getPendientes()).thenReturn(p);return f;}
}


