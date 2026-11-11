package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.*;
import com.controllocal.service.encargo.*;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FuentesDeFormalizacionTest {
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final EncargoService encargos=mock(EncargoService.class);
    final AccesoEncargo acceso=mock(AccesoEncargo.class);
    final AccesoOportunidad oportunidades=mock(AccesoOportunidad.class);
    final PactosDeEncargo pactos=mock(PactosDeEncargo.class);
    final OfertaService ofertas=mock(OfertaService.class);
    final EvidenciasDeFormalizacion evidencias=mock(EvidenciasDeFormalizacion.class);
    final AutoridadDePropiedad autoridad=mock(AutoridadDePropiedad.class);
    final PreparacionesDeContrato contratos=mock(PreparacionesDeContrato.class);
    final FuentesDeFormalizacion sut=new FuentesDeFormalizacion(encargos,acceso,oportunidades,pactos,ofertas,evidencias,json,autoridad,contratos);
    final ReferenciaDocumento doc=new ReferenciaDocumento(UUID.fromString("48a4a22b-44d1-4bc2-992e-9f9775e011c0"),3,"a".repeat(64));
    final ReferenciaPacto pacto=new ReferenciaPacto(10,20);
    final AcreditacionPacto firma=new AcreditacionPacto(30,40,50,"a".repeat(64),"b".repeat(64),"c".repeat(64));
    CoberturaRevision cobertura(long versionEncargo,long versionFisica,Long representante,List<ReferenciaDocumento> docs){
        var objeto=new OfertaService.ObjetoExacto(OfertaService.TipoObjeto.UNIDAD,10L,versionEncargo,15L,versionFisica,null,null,List.of(15L));
        var parte=new EncargoService.ParteNegociacion(100L,200L,representante,null,null,null,null,null);
        var partes=new EncargoService.PartesNegociacion(10,versionEncargo,7,30L,List.of(parte));
        return new CoberturaRevision(pacto,firma,objeto,partes,new OportunidadService.InteresadoReferencia(201,301L),60L,1L,docs,List.of(),List.of(),null);
    }
    @Test void notasOAgendaNoInvalidanUnaCoberturaMaterialIdentica(){
        assertTrue(FuentesDeFormalizacion.mismaCobertura(cobertura(4,2,null,List.of(doc)),cobertura(9,2,null,List.of(doc)),json));
    }
    @Test void unaVersionFisicaDistintaNoSeConfundeConCambioDeNotas(){
        assertFalse(FuentesDeFormalizacion.mismaCobertura(cobertura(4,2,null,List.of(doc)),cobertura(4,3,null,List.of(doc)),json));
    }
    @Test void cambiaRepresentacionSinCambiarLaVersionGlobalYLaCoberturaSeInvalida(){
        assertFalse(FuentesDeFormalizacion.mismaCobertura(cobertura(4,2,400L,List.of(doc)),cobertura(4,2,401L,List.of(doc)),json));
    }
    @Test void noSePierdeLaVersionDeUnArchivoNiSeConfundeConSuUuid(){
        var otra=new ReferenciaDocumento(doc.referencia(),4,doc.huella());
        assertFalse(FuentesDeFormalizacion.mismaCobertura(cobertura(4,2,null,List.of(doc)),cobertura(4,2,null,List.of(otra)),json));
    }
    @Test void ordenDeArchivosNoEsCambioMaterialPeroLaPoblacionSi(){
        var b=new ReferenciaDocumento(UUID.fromString("5d72f460-d1b8-4764-8f34-2eb1c1f7cb46"),1,"d".repeat(64));
        assertTrue(FuentesDeFormalizacion.mismaCobertura(cobertura(1,2,null,List.of(doc,b)),cobertura(1,2,null,List.of(b,doc)),json));
        assertFalse(FuentesDeFormalizacion.mismaCobertura(cobertura(1,2,null,List.of(doc,b)),cobertura(1,2,null,List.of(doc)),json));
    }
    @Test void rentaTipadaConservaImporteMonedaYPeriodoSinTomarUnTotalMensual(){
        var c=mock(Captacion.class);when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);when(c.getId()).thenReturn(10L);
        var renta=new OfertaService.Dinero(new BigDecimal("2500.25"),"PEN","RENTA_DECLARADA","MES_DECLARADO");
        var acuerdo=new AcuerdoFinalDocumentado(null,renta,null,List.of(doc),"Fuente TEST",new TerminosAlquiler(LocalDate.of(2026,11,1),LocalDate.of(2027,11,1)));
        assertSame(acuerdo,sut.validarFinal(acuerdo,c,null));
        assertEquals(0,renta.importe().compareTo(acuerdo.rentaMensual().importe()));
        assertNull(acuerdo.precioVenta());verify(evidencias).exigir(10,List.of(doc),"HITO_CONTRACTUAL",null);
    }
    @Test void conceptoDeLaOperacionNoSeCompletaDesdeLaMoneda(){
        var c=mock(Captacion.class);when(c.operacion()).thenReturn(OperacionInmobiliaria.VENTA);
        var renta=new OfertaService.Dinero(new BigDecimal("2500"),"PEN",null,null);
        assertThrows(ReglaNegocioException.class,()->sut.validarFinal(new AcuerdoFinalDocumentado(null,renta,null,List.of(doc),null,null),c,null));
        verifyNoInteractions(evidencias);
    }
    @Test void fechasDeContratoInvalidasNoSeSustituyenPorFechaDeRegistro(){
        var c=mock(Captacion.class);when(c.operacion()).thenReturn(OperacionInmobiliaria.ALQUILER);when(c.getId()).thenReturn(10L);
        var renta=new OfertaService.Dinero(new BigDecimal("2500"),"PEN",null,null);
        var a=new AcuerdoFinalDocumentado(null,renta,null,List.of(doc),null,new TerminosAlquiler(LocalDate.of(2026,11,1),LocalDate.of(2026,10,31)));
        assertThrows(ReglaNegocioException.class,()->sut.validarFinal(a,c,null));
    }
    @Test void cifraFinalSinFuenteDocumentadaNoSeConvierteEnAcuerdoC4(){
        var c=mock(Captacion.class);when(c.operacion()).thenReturn(OperacionInmobiliaria.VENTA);
        var venta=new OfertaService.Dinero(new BigDecimal("94000"),"USD",null,null);
        assertThrows(ReglaNegocioException.class,()->sut.validarFinal(new AcuerdoFinalDocumentado(venta,null,null,List.of(),null,null),c,null));
    }
    @Test void fuenteC5EsLaRevisionInmutableYConservaDeclaradoSeparado(){
        var venta=new OfertaService.Dinero(new BigDecimal("94000.00"),"USD",null,null);
        var declarado=new OfertaService.Dinero(new BigDecimal("300000.00"),"PEN",null,null);
        var a=new AcuerdoFinalDocumentado(venta,null,declarado,List.of(doc),"C5 TEST",null);
        var cov=cobertura(4,2,null,List.of(doc));
        var datos=new DatosRevision(70L,2L,"APROBAR",null,List.of(doc),true,true,a,cov);
        var f=sut.fuenteRevision(70,900,new ContenidoFormalizacion.Revision(datos,cov,null,null));
        assertEquals(new ReferenciaFuenteEconomica("REVISION_C5",70,900),f.referencia());
        assertEquals(0,venta.importe().compareTo(f.precioVenta().importe()));assertEquals("USD",f.precioVenta().moneda());
        assertNull(f.rentaMensual());assertEquals("PEN",a.declaradoEnEscritura().moneda());
    }
    CoberturaRevision conContrato(ContratoService.PreparacionContrato p){
        var c=cobertura(4,2,null,List.of(doc));
        return new CoberturaRevision(c.pacto(),c.acreditacionPacto(),c.objeto(),c.partes(),c.interesado(),null,null,c.documentos(),c.titularidad(),c.representacion(),p);
    }
    @Test void candidatoDistintoOCambioDeSuHuellaInvalidaLaCobertura(){
        var p1=new ContratoService.PreparacionContrato(80,"P",10,20,null,70L,new BigDecimal("2500"),"PEN",null,null,"a".repeat(64));
        var p2=new ContratoService.PreparacionContrato(81,"P",10,20,null,70L,new BigDecimal("2500"),"PEN",null,null,"a".repeat(64));
        var cambiado=new ContratoService.PreparacionContrato(80,"P",10,20,null,70L,new BigDecimal("2600"),"PEN",null,null,"b".repeat(64));
        assertFalse(FuentesDeFormalizacion.mismaCobertura(conContrato(p1),conContrato(p2),json));
        assertFalse(FuentesDeFormalizacion.mismaCobertura(conContrato(p1),conContrato(cambiado),json));
    }
    @Test void historiaDePSeReautorizaAunqueAhoraEsteFirmadoSinExigirLaHuellaAnterior(){
        var actor=new Actor(1,100,101,Actor.BROKER);var original=new ContratoService.PreparacionContrato(80,"P",10,20,null,70L,new BigDecimal("2500"),"PEN",null,null,"a".repeat(64));
        var actual=new ContratoService.PreparacionContrato(80,"D",10,20,null,70L,new BigDecimal("2600"),"PEN",null,null,"b".repeat(64));
        when(contratos.consultar(80,actor)).thenReturn(actual);
        when(oportunidades.contextoParaNegociacion(20,actor)).thenReturn(new AccesoOportunidad.PermisoOportunidad(1,20,1,10,101,"A","ALQUILER",15,conContrato(original).interesado()));
        when(autoridad.puedeLeer(actor,15)).thenReturn(true);
        assertDoesNotThrow(()->sut.reautorizar(10,20,conContrato(original),actor));verify(contratos).consultar(80,actor);
    }
    @Test void dineroC4DeSemanticaAbiertaConservaAntecedenteSinInventarRentaNiPrecioTotal(){
        var actor=new Actor(1,100,101,Actor.BROKER);var a=mock(OfertaService.AcuerdoAceptado.class);
        var propuesta=mock(OfertaService.DatosRegistroOferta.class);var c=cobertura(4,2,null,List.of(doc));
        when(a.id()).thenReturn(60L);when(a.version()).thenReturn(1L);when(a.propuesta()).thenReturn(propuesta);when(propuesta.objeto()).thenReturn(c.objeto());
        when(ofertas.consultarAcuerdoAceptado(20,null,actor)).thenReturn(Optional.of(a));
        var f=sut.fuenteC4(c,20,actor);assertEquals(new ReferenciaFuenteEconomica("ACUERDO_C4",60,1),f.referencia());
        assertNull(f.precioVenta());assertNull(f.rentaMensual());assertEquals(c.objeto(),f.objeto());verify(propuesta,never()).importe();
    }
}
