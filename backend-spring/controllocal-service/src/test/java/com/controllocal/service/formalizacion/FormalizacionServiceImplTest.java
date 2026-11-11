package com.controllocal.service.formalizacion;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.*;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.controllocal.service.actuacion.EfectosFormalizacionDeActuacion.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Preparación y negativas de contrato. No certifica la transacción real ni la autoridad simulada. */
class FormalizacionServiceImplTest {
    final Actor actor=new Actor(1,100,101,Actor.BROKER);
    final ExpedienteOperacionRepository expedientes=mock(ExpedienteOperacionRepository.class);
    final RevisionExpedienteOperacionRepository revisiones=mock(RevisionExpedienteOperacionRepository.class);
    final HitoContractualRepository hitos=mock(HitoContractualRepository.class);
    final RevisionHitoContractualRepository hechos=mock(RevisionHitoContractualRepository.class);
    final AccesoEncargo acceso=mock(AccesoEncargo.class);
    final ContextosDeActividad contextos=mock(ContextosDeActividad.class);
    final FuentesDeFormalizacion fuentes=mock(FuentesDeFormalizacion.class);
    final HitosConfirmadosImpl lector=mock(HitosConfirmadosImpl.class);
    final EvidenciasDeFormalizacion evidencias=mock(EvidenciasDeFormalizacion.class);
    final PropiedadRepository propiedades=mock(PropiedadRepository.class);
    final EncargoService encargos=mock(EncargoService.class);
    final ComandosIdempotentes comandos=mock(ComandosIdempotentes.class);
    final ComandoIdempotenteRepository recibos=mock(ComandoIdempotenteRepository.class);
    final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    final EfectosContractualesDeFormalizacion efectos=mock(EfectosContractualesDeFormalizacion.class);
    final ComisionService comisiones=mock(ComisionService.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final Documentos json=new Documentos(mapper);
    final FormalizacionServiceImpl sut=new FormalizacionServiceImpl(expedientes,revisiones,hitos,hechos,acceso,contextos,fuentes,lector,
            evidencias,propiedades,encargos,comandos,recibos,eventos,efectos,comisiones,json,mapper,mock(EntityManager.class));
    final ReferenciaPacto pacto=new ReferenciaPacto(10,30);
    final ReferenciaDocumento doc=new ReferenciaDocumento(UUID.fromString("48a4a22b-44d1-4bc2-992e-9f9775e011c0"),3,"a".repeat(64));
    final CoberturaRevision cobertura=new CoberturaRevision(pacto,null,
            new OfertaService.ObjetoExacto(OfertaService.TipoObjeto.UNIDAD,10L,2L,15L,1L,null,null,List.of(15L)),
            new EncargoService.PartesNegociacion(10,2,1,35L,List.of(new EncargoService.ParteNegociacion(10L,11L,null,null,null,null,null,null))),
            new OportunidadService.InteresadoReferencia(200,300L),null,null,List.of(doc),List.of(),List.of(),null);
    final ExpedienteOperacion e=mock(ExpedienteOperacion.class);
    final Captacion c=mock(Captacion.class);
    Map<String,Object> mapa(Object d){return mapper.convertValue(d,new TypeReference<Map<String,Object>>(){});}
    void montar(){
        when(e.getId()).thenReturn(70L);when(e.getEncargo()).thenReturn(10L);when(e.getOportunidad()).thenReturn(20L);
        when(e.getVersion()).thenReturn(7L);when(e.getRevisionPacto()).thenReturn(30L);when(e.getRevisionAplicable()).thenReturn(72L);
        when(e.getAcuerdoC4()).thenReturn(null);when(e.getVersionAcuerdoC4()).thenReturn(null);
        when(e.getContratoAlquiler()).thenReturn(null);when(e.getContratoOrigen()).thenReturn(null);
        when(lector.expediente(new ReferenciaContextoEconomico(70),actor)).thenReturn(e);
        when(acceso.leer(10,actor)).thenReturn(c);when(c.getId()).thenReturn(10L);when(c.operacion()).thenReturn(OperacionInmobiliaria.VENTA);
        when(contextos.hoy()).thenReturn(LocalDate.of(2026,10,31));
        var p=new DatosPresentacion(20L,1L,10L,2L,null,null,pacto,List.of(doc),null,null,null);
        when(lector.presentacion(e,actor)).thenReturn(new ContenidoFormalizacion.Presentacion(p,cobertura,null,null));
        when(fuentes.cubrir(c,20,pacto,null,null,List.of(doc),null,LocalDate.of(2026,10,31),actor)).thenReturn(cobertura);
    }
    @Test void prepararRevisionAnexaLaCoberturaCoreQueLuegoSeraComparada(){
        montar();var d=new DatosRevision(70L,7L,"APROBAR",null,List.of(doc),true,true,null,null);
        var preparada=(DatosRevision)sut.validarActuacion(EVALUAR,mapa(d),actor);
        assertSame(cobertura,preparada.coberturaEsperada());assertNull(preparada.acuerdoFinal());
        verifyNoInteractions(efectos,comisiones,eventos,comandos);verify(revisiones,never()).saveAndFlush(any());
    }
    @Test void unCheckboxNoSustituyeLaRevisionDeLosElementos(){
        montar();var d=new DatosRevision(70L,7L,"APROBAR",null,List.of(doc),true,false,null,null);
        assertThrows(ReglaNegocioException.class,()->sut.validarActuacion(EVALUAR,mapa(d),actor));verifyNoInteractions(efectos,comisiones);
    }
    @Test void revisionNoPuedeOmitirUnaEvidenciaPresentada(){
        montar();var d=new DatosRevision(70L,7L,"APROBAR",null,List.of(),true,true,null,null);
        assertThrows(ReglaNegocioException.class,()->sut.validarActuacion(EVALUAR,mapa(d),actor));verifyNoInteractions(efectos,comisiones);
    }
    @Test void confirmarExigeFechaEfectivaDelHechoYNoUsaHoy(){
        montar();var h=mock(HitoContractual.class);when(h.getId()).thenReturn(80L);when(h.getExpediente()).thenReturn(70L);
        when(h.getVersion()).thenReturn(2L);when(h.getTipo()).thenReturn(TipoHitoContractual.MINUTA);when(h.getRevisionInformada()).thenReturn(81L);
        when(hitos.buscar(1,80)).thenReturn(Optional.of(h));
        var info=RevisionHitoContractual.anexar(1,70,80,"INFORMACION",null,72L,null,101,"{}",OffsetDateTime.parse("2026-10-31T15:00:00Z"));
        when(hechos.buscar(1,70,80,81)).thenReturn(Optional.of(info));
        var d=new DatosConfirmacionHito(80L,2L,7L,72L,81L,pacto,null);
        assertThrows(ReglaNegocioException.class,()->sut.validarActuacion(CONFIRMAR,mapa(d),actor));verifyNoInteractions(efectos,comisiones);
    }
    @Test void informarParcialNoExigePrecioFinalNiProduceContratoOComision(){
        montar();var d=new DatosInformacionHito(70L,7L,TipoHitoContractual.MINUTA,null,List.of(),"Declaración TEST por completar");
        assertInstanceOf(DatosInformacionHito.class,sut.validarActuacion(INFORMAR,mapa(d),actor));verifyNoInteractions(efectos,comisiones,eventos,comandos);
    }
    @Test void unHitoDeVentaNoSeAdmiteEnAlquiler(){
        montar();when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);
        var d=new DatosInformacionHito(70L,7L,TipoHitoContractual.MINUTA,LocalDate.of(2026,10,31),List.of(doc),null);
        assertThrows(ReglaNegocioException.class,()->sut.validarActuacion(INFORMAR,mapa(d),actor));verifyNoInteractions(efectos,comisiones);
    }
    @Test void consultaDeReciboDeOtroActorDeniegaYNoFingeAusencia(){
        var r=mock(ComandoIdempotente.class);when(r.getIdPersonaRol()).thenReturn(999L);
        when(recibos.findByOrganizacionIdAndClaveIdempotencia(1,"clave-TEST")).thenReturn(Optional.of(r));
        assertThrows(AccesoNoAutorizadoException.class,()->sut.consultarResultado(CONFIRMAR,"clave-TEST",actor));verify(r,never()).getResultado();
    }
    @Test void reciboDeOtraCapacidadEsConflictoNoResultadoVacio(){
        var r=mock(ComandoIdempotente.class);when(r.getIdPersonaRol()).thenReturn(101L);when(r.getTipoComando()).thenReturn(INFORMAR);
        when(recibos.findByOrganizacionIdAndClaveIdempotencia(1,"clave-TEST")).thenReturn(Optional.of(r));
        assertThrows(ConflictoException.class,()->sut.consultarResultado(CONFIRMAR,"clave-TEST",actor));verify(r,never()).getResultado();
    }
    @Test void ausenciaRealDeReciboSeDistingueDelFalloTecnico(){
        when(recibos.findByOrganizacionIdAndClaveIdempotencia(1,"ausente-TEST")).thenReturn(Optional.empty());
        assertTrue(sut.consultarResultado(INFORMAR,"ausente-TEST",actor).isEmpty());
        when(recibos.findByOrganizacionIdAndClaveIdempotencia(1,"tecnico-TEST")).thenThrow(new IllegalStateException("Fallo TEST"));
        assertThrows(IllegalStateException.class,()->sut.consultarResultado(INFORMAR,"tecnico-TEST",actor));
    }
    CoberturaRevision prepararContrato(long id){
        var p=new ContratoService.PreparacionContrato(id,ContratoAlquiler.EN_PROCESO,10,20,null,77L,new BigDecimal("2500.25"),"PEN",LocalDate.of(2026,11,1),LocalDate.of(2027,11,1),"a".repeat(64));
        return new CoberturaRevision(cobertura.pacto(),cobertura.acreditacionPacto(),cobertura.objeto(),cobertura.partes(),cobertura.interesado(),null,null,List.of(doc),List.of(),List.of(),p);
    }
    @Test void preparaRentaYFechasConocidasDePComoPropuestaRevisableSinEfectos(){
        montar();when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);when(e.getContratoAlquiler()).thenReturn(88L);when(e.getContratoOrigen()).thenReturn(77L);
        var cov=prepararContrato(88);when(fuentes.cubrir(c,20,pacto,null,null,List.of(doc),88L,LocalDate.of(2026,10,31),actor)).thenReturn(cov);
        var d=new DatosRevision(70L,7L,"APROBAR",null,List.of(doc),true,true,null,null);
        var propuesta=(DatosRevision)sut.validarActuacion(EVALUAR,mapa(d),actor);
        assertEquals(0,new BigDecimal("2500.25").compareTo(propuesta.acuerdoFinal().rentaMensual().importe()));
        assertEquals("PEN",propuesta.acuerdoFinal().rentaMensual().moneda());assertNull(propuesta.acuerdoFinal().rentaMensual().periodo());
        assertEquals(LocalDate.of(2026,11,1),propuesta.acuerdoFinal().terminosAlquiler().inicio());
        assertEquals(88,propuesta.coberturaEsperada().contratoPreparado().id());verifyNoInteractions(efectos,comisiones,eventos,comandos);
    }
    @Test void acuerdoFinalExpresoNoSeReemplazaConLaRentaDeP(){
        montar();when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);when(e.getContratoAlquiler()).thenReturn(88L);when(e.getContratoOrigen()).thenReturn(77L);
        when(fuentes.cubrir(c,20,pacto,null,null,List.of(doc),88L,LocalDate.of(2026,10,31),actor)).thenReturn(prepararContrato(88));
        var finalExpreso=new AcuerdoFinalDocumentado(null,new OfertaService.Dinero(new BigDecimal("2400"),"USD",null,null),null,List.of(doc),"Fuente TEST",null);
        var d=new DatosRevision(70L,7L,"APROBAR",null,List.of(doc),true,true,finalExpreso,null);
        var propuesta=(DatosRevision)sut.validarActuacion(EVALUAR,mapa(d),actor);
        assertEquals(0,new BigDecimal("2400").compareTo(propuesta.acuerdoFinal().rentaMensual().importe()));assertEquals("USD",propuesta.acuerdoFinal().rentaMensual().moneda());
    }
    @Test void seleccionExplicitaP2AntesDeHitoEsAdmitidaYDespuesSeDeniega(){
        montar();when(c.getVersion()).thenReturn(2L);when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);
        when(e.getContratoAlquiler()).thenReturn(88L);when(e.getContratoOrigen()).thenReturn(77L);
        when(fuentes.cubrir(c,20,pacto,null,null,List.of(doc),89L,LocalDate.of(2026,10,31),actor)).thenReturn(prepararContrato(89));
        var d=new DatosPresentacion(20L,1L,10L,2L,null,null,pacto,List.of(doc),70L,7L,89L);
        assertInstanceOf(DatosPresentacion.class,sut.validarActuacion(PRESENTAR,mapa(d),actor));
        var confirmado=mock(HitoContractual.class);when(confirmado.getRevisionConfirmada()).thenReturn(90L);when(hitos.delExpediente(1,70)).thenReturn(List.of(confirmado));
        assertThrows(ConflictoException.class,()->sut.validarActuacion(PRESENTAR,mapa(d),actor));verifyNoInteractions(efectos,comisiones);
    }
    @Test void propuestaDeRevisionConCasAntiguoNoApruebaElCandidatoNuevo(){
        montar();when(e.getVersion()).thenReturn(8L);var d=new DatosRevision(70L,7L,"APROBAR",null,List.of(doc),true,true,null,cobertura);
        assertThrows(ConflictoException.class,()->sut.validarActuacion(EVALUAR,mapa(d),actor));verifyNoInteractions(efectos,comisiones,eventos,comandos);
    }
    void montarResumenDeHito(){
        montar();var h=mock(HitoContractual.class);when(h.getId()).thenReturn(80L);when(h.getExpediente()).thenReturn(70L);
        when(h.getTipo()).thenReturn(TipoHitoContractual.MINUTA);when(h.getVersion()).thenReturn(2L);when(h.getRevisionInformada()).thenReturn(81L);
        when(h.getRevisionConfirmada()).thenReturn(null);
        when(hitos.buscar(1,80)).thenReturn(Optional.of(h));when(hitos.delExpediente(1,70)).thenReturn(List.of(h));
        var original=new ContenidoFormalizacion.Hecho(pacto,null,List.of(doc),"Fuente literal TEST",Procedencia.deLaPantalla());
        var row=RevisionHitoContractual.anexar(1,70,80,"INFORMACION",LocalDate.of(2026,10,20),72L,null,101,
                json.objetoCanonico(original),OffsetDateTime.parse("2026-10-30T15:00:00Z"));
        when(hechos.buscar(1,70,80,81)).thenReturn(Optional.of(row));
        when(lector.consultarContexto(new ReferenciaContextoEconomico(70),actor)).thenReturn(new ContextoEconomico(new ReferenciaContextoEconomico(70),1,10,
                OperacionInmobiliaria.VENTA,20L,null,null,null,pacto,null,List.of()));
    }
    @Test void resumenHumanoMuestraInformacionYDocumentosExactosDelHechoNoFechaDeHoy(){
        montarResumenDeHito();var d=new DatosConfirmacionHito(80L,2L,7L,72L,81L,pacto,null);
        var r=(Map<?,?>)sut.resumenRevision(CONFIRMAR,mapa(d),actor);var h=(Map<?,?>)r.get("hechoInformadoExacto");
        assertEquals(new ReferenciaHito(80,81),h.get("referencia"));assertEquals(LocalDate.of(2026,10,20),h.get("fechaEfectiva"));
        assertEquals(OffsetDateTime.parse("2026-10-30T15:00:00Z"),h.get("registradoEn"));
        var contenido=(ContenidoFormalizacion.Hecho)h.get("informacion");assertEquals(List.of(doc),contenido.evidencias());
        assertEquals("Fuente literal TEST",contenido.descripcion());assertEquals(Procedencia.deLaPantalla(),contenido.procedencia());
        verifyNoInteractions(efectos,comisiones,eventos,comandos);
    }
    @Test void archivoDeHitoRevocadoDeniegaLaLecturaDeLaPropuestaYaPreparada(){
        montarResumenDeHito();doThrow(new AccesoNoAutorizadoException()).when(evidencias).exigir(10,List.of(doc),"HITO_CONTRACTUAL",actor);
        var d=new DatosConfirmacionHito(80L,2L,7L,72L,81L,pacto,null);
        assertThrows(AccesoNoAutorizadoException.class,()->sut.exigirLecturaActuacion(CONFIRMAR,mapa(d),actor));
        verifyNoInteractions(efectos,comisiones,eventos,comandos);
    }
}
