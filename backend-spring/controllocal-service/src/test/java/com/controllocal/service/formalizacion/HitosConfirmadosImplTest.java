package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.encargo.*;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HitosConfirmadosImplTest {
    final Actor actor=new Actor(1,100,101,Actor.BROKER);
    final ExpedienteOperacionRepository expedientes=mock(ExpedienteOperacionRepository.class);
    final RevisionExpedienteOperacionRepository revisiones=mock(RevisionExpedienteOperacionRepository.class);
    final HitoContractualRepository hitos=mock(HitoContractualRepository.class);
    final RevisionHitoContractualRepository hechos=mock(RevisionHitoContractualRepository.class);
    final AccesoEncargo acceso=mock(AccesoEncargo.class);
    final PactosDeEncargo pactos=mock(PactosDeEncargo.class);
    final FuentesDeFormalizacion fuentes=mock(FuentesDeFormalizacion.class);
    final EvidenciasDeFormalizacion evidencias=mock(EvidenciasDeFormalizacion.class);
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final HitosConfirmadosImpl sut=new HitosConfirmadosImpl(expedientes,revisiones,hitos,hechos,acceso,pactos,fuentes,evidencias,json);
    final ReferenciaPacto pacto=new ReferenciaPacto(10,30);
    final ReferenciaDocumento doc=new ReferenciaDocumento(UUID.fromString("48a4a22b-44d1-4bc2-992e-9f9775e011c0"),3,"a".repeat(64));
    final OffsetDateTime registrado=OffsetDateTime.parse("2026-10-31T15:00:00Z");
    final ExpedienteOperacion e=ExpedienteOperacion.abrir(1,10,20,30,null,null,registrado);
    final CoberturaRevision cobertura=new CoberturaRevision(pacto,null,
            new OfertaService.ObjetoExacto(OfertaService.TipoObjeto.UNIDAD,10L,2L,15L,1L,null,null,List.of(15L)),
            new EncargoService.PartesNegociacion(10,2,1,35L,List.of()),new OportunidadService.InteresadoReferencia(200,300L),
            null,null,List.of(doc),List.of(),List.of(),null);

    void montar(){
        ReflectionTestUtils.setField(e,"id",70L);e.presentar(71,30,null,null);
        when(expedientes.buscar(1,70)).thenReturn(Optional.of(e));
        var c=mock(Captacion.class);when(acceso.leer(10,actor)).thenReturn(c);when(c.operacion()).thenReturn(OperacionInmobiliaria.VENTA);
        var d=new DatosPresentacion(20L,1L,10L,2L,null,null,pacto,List.of(doc),null,null,null);
        var row=RevisionExpedienteOperacion.anexar(1,70,0,"PRESENTACION",null,null,null,101,
                json.objetoCanonico(new ContenidoFormalizacion.Presentacion(d,cobertura,null,null)),registrado);
        ReflectionTestUtils.setField(row,"id",71L);when(revisiones.buscar(1,70,71)).thenReturn(Optional.of(row));
        when(hitos.delExpediente(1,70)).thenReturn(List.of());
    }
    void revisionAprobada(long id,AcuerdoFinalDocumentado acuerdo){
        var d=new DatosRevision(70L,1L,"APROBAR",null,List.of(doc),true,true,acuerdo,cobertura);
        var r=RevisionExpedienteOperacion.anexar(1,70,1,"REVISION","APROBAR",71L,null,101,
                json.objetoCanonico(new ContenidoFormalizacion.Revision(d,cobertura,null,null)),registrado);
        ReflectionTestUtils.setField(r,"id",id);when(revisiones.buscar(1,70,id)).thenReturn(Optional.of(r));
    }
    @Test void contextoParcialConservaAusenciasSinHitoNiContratoInventados(){
        montar();var c=sut.consultarContexto(new ReferenciaContextoEconomico(70),actor);
        assertNull(c.fuenteEconomica());assertNull(c.solicitudAlquiler());assertNull(c.contratoAlquiler());assertNull(c.contratoVenta());
        assertTrue(c.hechosConfirmados().isEmpty());assertEquals(pacto,c.pacto());
        verifyNoInteractions(hechos);
    }
    @Test void otroTenantNoRecuperaElContextoPorUnIdGlobal(){
        var otro=new Actor(2,100,101,Actor.BROKER);when(expedientes.buscar(2,70)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->sut.consultarContexto(new ReferenciaContextoEconomico(70),otro));verifyNoInteractions(pactos,fuentes,hechos);
    }
    @Test void denegacionActualNoSeConvierteEnFuenteVacia(){
        montar();doThrow(new AccesoNoAutorizadoException()).when(acceso).exigirLectura(10,actor);
        assertThrows(AccesoNoAutorizadoException.class,()->sut.consultarContexto(new ReferenciaContextoEconomico(70),actor));verifyNoInteractions(pactos);
    }
    @Test void falloTecnicoDePactoNoSeConvierteEnBasePendiente(){
        montar();when(pactos.consultarPactoFirmado(pacto,actor)).thenThrow(new IllegalStateException("Fallo TEST"));
        assertThrows(IllegalStateException.class,()->sut.consultarContexto(new ReferenciaContextoEconomico(70),actor));
    }
    @Test void punteroConfirmadoDominaFechaDeCargaYConservaAntecedenteRectificado(){
        montar();revisionAprobada(72,null);
        var h=HitoContractual.abrir(1,70,TipoHitoContractual.MINUTA);ReflectionTestUtils.setField(h,"id",80L);h.informar(81);h.confirmar(83);
        var contenido=new ContenidoFormalizacion.Hecho(pacto,null,List.of(doc),"Corrección TEST",null);
        var actual=RevisionHitoContractual.anexar(1,70,80,"RECTIFICACION",LocalDate.of(2026,10,20),72L,82L,101,
                json.objetoCanonico(contenido),OffsetDateTime.parse("2026-10-25T15:00:00Z"));ReflectionTestUtils.setField(actual,"id",83L);
        when(hitos.delExpediente(1,70)).thenReturn(List.of(h));when(hechos.buscar(1,70,80,83)).thenReturn(Optional.of(actual));
        var c=sut.consultarContexto(new ReferenciaContextoEconomico(70),actor);
        assertEquals(1,c.hechosConfirmados().size());var f=c.hechosConfirmados().getFirst();
        assertEquals(new ReferenciaHito(80,83),f.referencia());assertEquals(new ReferenciaHito(80,82),f.rectifica());
        assertEquals(LocalDate.of(2026,10,20),f.fechaEfectiva());assertEquals(OffsetDateTime.parse("2026-10-25T15:00:00Z"),f.registradoEn());
        verify(hechos,never()).historia(anyLong(),anyLong(),anyLong());
    }
    @Test void fuenteHistoricaC5NoUsaLaRevisionAplicableNueva(){
        montar();var precio=new OfertaService.Dinero(new BigDecimal("94000.00"),"USD",null,null);
        var a=new AcuerdoFinalDocumentado(precio,null,null,List.of(doc),"Fuente TEST",null);revisionAprobada(72,a);
        var esperada=new FuenteEconomicaFinal(new ReferenciaFuenteEconomica("REVISION_C5",70,72),null,null,cobertura.objeto(),precio,null,List.of(doc),null);
        when(fuentes.fuenteRevision(eq(70L),eq(72L),any())).thenReturn(esperada);
        e.revisar(99,true,true);
        var f=sut.consultarFuente(new ReferenciaContextoEconomico(70),new ReferenciaFuenteEconomica("REVISION_C5",70,72),actor);
        assertSame(esperada,f);verify(revisiones,never()).buscar(1,70,99);verify(evidencias).exigir(10,List.of(doc),"HITO_CONTRACTUAL",actor);
    }
    @Test void fuenteC5DeOtroContextoNoSeBuscaNiEntrega(){
        montar();assertThrows(NoEncontradoException.class,()->sut.consultarFuente(new ReferenciaContextoEconomico(70),new ReferenciaFuenteEconomica("REVISION_C5",71,72),actor));
        verify(revisiones,never()).buscar(1,70,72);
    }
    @Test void fuenteC4NoVinculadaEnEsteContextoNoSeAceptaPorCoincidirElTenant(){
        montar();when(revisiones.historia(1,70)).thenReturn(List.of());
        assertThrows(NoEncontradoException.class,()->sut.consultarFuente(new ReferenciaContextoEconomico(70),new ReferenciaFuenteEconomica("ACUERDO_C4",90,1),actor));
    }
    @Test void jsonHistoricoCorruptoFallaEnLugarDeDesaparecer(){
        montar();var r=RevisionExpedienteOperacion.anexar(1,70,0,"PRESENTACION",null,null,null,101,"{",registrado);
        when(revisiones.buscar(1,70,71)).thenReturn(Optional.of(r));
        assertThrows(IllegalStateException.class,()->sut.consultarContexto(new ReferenciaContextoEconomico(70),actor));
    }
}
