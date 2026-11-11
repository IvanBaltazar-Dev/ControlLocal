package com.controllocal.service.demanda;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.AplicacionAtributo;
import com.controllocal.domain.inmueble.AplicacionPorOperacion;
import com.controllocal.domain.inmueble.Sujeto;
import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.persistence.repositorio.CatalogoAtributoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.GobiernoCatalogoCapturaService.*;
import com.controllocal.service.RequerimientoService.CriterioPedido;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.matching.ContratoMatching.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** D1-03/04/05/21/25: valores y fuentes explícitos, sin otro modelo o parser. */
@ExtendWith(MockitoExtension.class)
class NormalizadorCriteriosDemandaTest {
    private static final Actor AGENTE=new Actor(11,21,31,Actor.AGENTE);
    @Mock CatalogoAtributoRepository catalogo;
    @Mock GobiernoCatalogoCapturaService gobierno;
    private CatalogoAtributo definicion;
    private NormalizadorCriteriosDemanda normalizador;
    @BeforeEach void preparar() {
        definicion=new CatalogoAtributo();
        ReflectionTestUtils.setField(definicion,"id",42L);
        ReflectionTestUtils.setField(definicion,"versionDefinicion",4L);
        definicion.setClave("ascensores");definicion.setRotulo("Ascensores");
        definicion.setTipoDato("ENTERO");definicion.setUnidad("u");definicion.setSujeto(Sujeto.PROPIEDAD);
        definicion.setAplicaciones(Set.of(new AplicacionAtributo("D",com.controllocal.domain.inmueble.Exigencia.OPC)));
        normalizador=new NormalizadorCriteriosDemanda(catalogo,gobierno);
    }
    @Test void cambioDeVersionEntreBulkYShareNoEstampaRotulosAnterioresConVersionNueva() {
        prepararCatalogo(42,5);
        assertThrows(ConflictoException.class,()->normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null)));
    }
    @Test void cambioDeIdentidadDeDefinicionTampocoSeAceptaConElMismoContador() {
        prepararCatalogo(43,4);
        assertThrows(ConflictoException.class,()->normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null)));
    }
    @Test void laVersionEsperadaDeLaDefinicionEsUnaPrecondicionReal() {
        prepararCatalogo(42,4);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:42:3",3L,null,"Declaración TEST",null,null,null);
        assertThrows(ConflictoException.class,()->normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,meta)));
    }
    @Test void ceroConocidoConservaCeroYNoSeConvierteEnFaltante() {
        prepararCatalogo(42,4);
        var c=normalizar(pedido("IGUAL","I",BigDecimal.ZERO,null,null,null)).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.CONOCIDO,c.metadatos().estado());
        assertEquals(0,c.valor().numero().compareTo(BigDecimal.ZERO));
        assertEquals("CAT:D:42:4",c.metadatos().referenciaDefinicion());
    }
    @Test void cincoAscensoresEsUnaCantidadEnteraConocidaSinTopeDeSugerencias() {
        prepararCatalogo(42,4);
        var c=normalizar(pedido("MINIMO","I",new BigDecimal("5"),null,null,null)).getFirst().criterio();
        assertEquals(Exigencia.OBLIGATORIO,c.exigencia());assertNull(c.prioridad());
        assertEquals(new BigDecimal("5"),c.alternativas().getFirst().valor().numero());
    }
    @Test void contextoParcialReutilizaSoloLaOperacionExplicitaDelPadre() {
        prepararCatalogo(42,4);
        var parcial=new ContextoValor("CONCEPTO_DECLARADO_TEST","u",null,null,null,"config-test");
        var pedido=new CriterioPedido("ascensores","MINIMO","I",null,BigDecimal.ONE,null,null,null,null,null,
                null,null,parcial,null,false,null);
        var contexto=normalizar(pedido).getFirst().criterio().alternativas().getFirst().contexto();
        assertEquals("ALQUILER",contexto.operacion());assertEquals(parcial.concepto(),contexto.concepto());
        assertEquals(parcial.unidad(),contexto.unidad());assertEquals(parcial.configuracion(),contexto.configuracion());
        assertNull(contexto.moneda());assertNull(contexto.periodo());
    }
    @Test void contextoExplicitoContradictorioNoSeReemplazaPorLaOperacionDelPadre() {
        prepararCatalogo(42,4);
        var ajeno=new ContextoValor(null,"u",null,null,"VENTA",null);
        var pedido=new CriterioPedido("ascensores","MINIMO","I",null,BigDecimal.ONE,null,null,null,null,null,
                null,null,ajeno,null,false,null);
        assertThrows(ReglaNegocioException.class,()->normalizar(pedido));
    }
    @Test void criterioConservadoNoSeAdaptaSilenciosamenteAOtraOperacion() {
        prepararCatalogo(42,4);
        var conservados=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null));
        var error=assertThrows(ReglaNegocioException.class,()->normalizador.validarContextoConservado(
                conservados,AGENTE,Set.of("D"),"VENTA"));
        assertTrue(error.getMessage().contains("criterios"));
        assertEquals("ALQUILER",conservados.getFirst().criterio().alternativas().getFirst().contexto().operacion());
    }
    @Test void criterioConservadoDeEdificioNoSeReutilizaEnTerreno() {
        prepararCatalogo(42,4);
        var conservados=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null));
        assertThrows(ReglaNegocioException.class,()->normalizador.validarContextoConservado(
                conservados,AGENTE,Set.of("T"),"ALQUILER"));
    }
    @Test void cambioDeTiposCompatibleValidaSinReescribirDefinicionNiMetadatosConservados() {
        prepararCatalogo(42,4);
        var conservados=normalizar(pedido("MINIMO","D",BigDecimal.ONE,null,3,null));
        var original=conservados.getFirst();
        assertDoesNotThrow(()->normalizador.validarContextoConservado(conservados,AGENTE,Set.of("D","C"),"ALQUILER"));
        assertSame(original,conservados.getFirst());assertEquals(Prioridad.IMPORTANTE,original.criterio().prioridad());
    }
    @Test void validacionDeConservadosNoDegradaUnaVersionDeDefinicionIncompatible() {
        prepararCatalogo(42,4);
        var conservados=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null));
        when(catalogo.bloquearActualParaCaptura(11,"ascensores")).thenReturn(Optional.of(
                new DefinicionActual(42L,5,"ascensores","PROPIEDAD","ENTERO")));
        assertThrows(ConflictoException.class,()->normalizador.validarContextoConservado(
                conservados,AGENTE,Set.of("D","C"),"ALQUILER"));
    }
    @Test void validacionDeConservadosNoConvierteConflictoActivoEnDatoConocido() {
        prepararCatalogo(42,4);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,Causa.CONFLICTO,null,null,
                "evidencia-test","Fuente conflictiva TEST",LocalDate.of(2026,10,29),null,null);
        var conservados=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,meta));
        assertDoesNotThrow(()->normalizador.validarContextoConservado(conservados,AGENTE,Set.of("D","C"),"ALQUILER"));
        assertEquals(EstadoDato.CONOCIDO,conservados.getFirst().criterio().alternativas().getFirst().metadatos().estado());
        assertEquals(Causa.CONFLICTO,conservados.getFirst().criterio().alternativas().getFirst().metadatos().causa());
    }
    @Test void contextoAntiguoSinOperacionNoSeActualizaSilenciosamenteAlCambiarTipos() {
        prepararCatalogo(42,4);
        var normalizado=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null)).getFirst();
        var c=normalizado.criterio();var a=c.alternativas().getFirst();var contexto=a.contexto();
        var parcial=new ContextoValor(contexto.concepto(),contexto.unidad(),contexto.moneda(),contexto.periodo(),
                null,contexto.configuracion(),contexto.jurisdiccion(),contexto.referenciaCatalogo(),contexto.versionFuente());
        var antiguo=new NormalizadorCriteriosDemanda.Normalizado(new Criterio(c.referencia(),c.clave(),c.exigencia(),c.prioridad(),
                List.of(new Condicion(a.claveDato(),a.tipo(),a.operador(),a.valor(),a.hasta(),a.negada(),parcial,a.metadatos()))),
                normalizado.rotulo(),normalizado.unidad(),normalizado.tipoDato());
        assertThrows(ReglaNegocioException.class,()->normalizador.validarContextoConservado(List.of(antiguo),AGENTE,Set.of("D","C"),"ALQUILER"));
        assertNull(antiguo.criterio().alternativas().getFirst().contexto().operacion());
    }
    @Test void faltaDeOperandoSeConservaSinCantidadFicticia() {
        prepararCatalogo(42,4);
        var a=normalizar(pedido("MINIMO","I",null,null,null,null)).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.FALTANTE,a.metadatos().estado());assertNull(a.valor());
    }
    @Test void rangoParcialConservaElLimiteConocidoSinInventarElOtro() {
        prepararCatalogo(42,4);
        var a=normalizar(pedido("ENTRE","I",null,new BigDecimal("5"),null,null)).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.FALTANTE,a.metadatos().estado());assertNull(a.valor());
        assertEquals(Causa.LIMITES_INSUFICIENTES,a.metadatos().causa());assertEquals(new BigDecimal("5"),a.hasta().numero());
    }
    @Test void preferenciaNormalTienePesoDosYSigueSeparadaDeObligatoriedad() {
        prepararCatalogo(42,4);
        var c=normalizar(pedido("MINIMO","D",BigDecimal.ONE,null,null,null)).getFirst().criterio();
        assertEquals(Exigencia.PREFERENCIA,c.exigencia());assertEquals(Prioridad.NORMAL,c.prioridad());assertEquals(2,c.peso());
    }
    @Test void restriccionesCompatiblesDelMismoConceptoFormanUnSoloRango() {
        prepararCatalogo(42,4);
        var cs=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null),
                pedido("MAXIMO","I",new BigDecimal("3"),null,null,null));
        assertEquals(1,cs.size());var a=cs.getFirst().criterio().alternativas().getFirst();
        assertEquals(OperadorCriterio.ENTRE,a.operador());assertEquals(BigDecimal.ONE,a.valor().numero());
        assertEquals(new BigDecimal("3"),a.hasta().numero());
    }
    @Test void copiasNumericasEquivalentesNoCreanDoblePonderacion() {
        prepararCatalogo(42,4);
        var cs=normalizar(pedido("MINIMO","D",new BigDecimal("2.0"),null,null,null),
                pedido("MINIMO","D",new BigDecimal("2.00"),null,null,null));
        assertEquals(1,cs.size());assertEquals(2,cs.getFirst().criterio().peso());
    }
    @Test void fusionDeLimitesNoDescartaConflictoSegunElOrdenDeEntrada() {
        prepararCatalogo(42,4);
        var limpio=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var conflicto=new MetadatosDato(EstadoDato.CONOCIDO,Causa.CONFLICTO,null,null,null,null,null,null,null);
        var min=pedido("MINIMO","I",BigDecimal.ONE,null,null,limpio);
        var max=pedido("MAXIMO","I",new BigDecimal("3"),null,null,conflicto);
        assertThrows(ReglaNegocioException.class,()->normalizar(min,max));
        assertThrows(ReglaNegocioException.class,()->normalizar(max,min));
    }
    @Test void fusionDeLimitesNoEligeSilenciosamenteLaVigenciaDeUnaFuente() {
        prepararCatalogo(42,4);
        var vigente=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var acotado=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,Instant.parse("2026-10-20T00:00:00Z"));
        var min=pedido("MINIMO","I",BigDecimal.ONE,null,null,vigente);
        var max=pedido("MAXIMO","I",new BigDecimal("3"),null,null,acotado);
        assertThrows(ReglaNegocioException.class,()->normalizar(min,max));
        assertThrows(ReglaNegocioException.class,()->normalizar(max,min));
    }
    @Test void duplicadosConMismoValorNoOcultanUnaVigenciaDistinta() {
        prepararCatalogo(42,4);
        var vigente=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var acotado=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,Instant.parse("2026-10-20T00:00:00Z"));
        var uno=pedido("MINIMO","D",BigDecimal.ONE,null,null,vigente);
        var otro=pedido("MINIMO","D",BigDecimal.ONE,null,null,acotado);
        assertThrows(ReglaNegocioException.class,()->normalizar(uno,otro));
        assertThrows(ReglaNegocioException.class,()->normalizar(otro,uno));
    }
    @Test void laFuenteYFechaDeclaradasNoSeRejuvenecenAlNormalizar() {
        prepararCatalogo(42,4);
        var dia=LocalDate.of(2026,10,20);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:42:4",4L,"fuente-test","Pedido declarado TEST",dia,null,null);
        var a=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,meta)).getFirst().criterio().alternativas().getFirst();
        assertEquals(dia,a.metadatos().observadoEn());assertEquals("fuente-test",a.metadatos().evidenciaRef());
    }
    @Test void textoInvalidoComoCantidadQuedaPendienteConIncidenciaYNoDemuestraIncumplimiento() {
        prepararCatalogo(42,4);
        var p=new CriterioPedido("ascensores","MINIMO","I","dos",null,null,null,null,null,null,null,null,null,null,false,null);
        var a=normalizar(p).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.FALTANTE,a.metadatos().estado());assertEquals(Causa.VALOR_INVALIDO,a.metadatos().causa());assertNull(a.valor());
    }
    @Test void perfilNoEsUnaReferenciaDeDefinicionAunqueCoincidanIdYVersion() {
        prepararCatalogo(42,4);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:P:42:4",4L,null,null,null,null,null);
        assertThrows(ConflictoException.class,()->normalizar(pedido("IGUAL","I",BigDecimal.ONE,null,null,meta)));
    }
    @Test void definicionDeEncargoTambienConservaLaReferenciaDReal() {
        definicion.setSujeto(Sujeto.ENCARGO);definicion.setAplicaciones(Set.of());
        definicion.setAplicacionesOperacion(Set.of(new AplicacionPorOperacion("D","A",com.controllocal.domain.inmueble.Exigencia.OPC)));prepararCatalogo(42,4);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:42:4",4L,null,null,null,null,null);
        var a=normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,meta)).getFirst().criterio().alternativas().getFirst();
        assertEquals("ENCARGO:ascensores",a.claveDato());assertEquals("CAT:D:42:4",a.metadatos().referenciaDefinicion());
        verify(catalogo,never()).bloquearActualParaCaptura(anyLong(),anyString());
    }
    @Test void criterioFisicoNoNecesitaFilasDeAplicabilidadComercial() {
        prepararCatalogo(42,4);assertTrue(definicion.getAplicacionesOperacion().isEmpty());
        assertEquals(1,normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null)).size());
    }
    @Test void criterioFisicoDeOtroTipoSigueDenegadoAntesDeBloquearDefinicion() {
        when(catalogo.disponiblesPara(AGENTE.idOrganizacion())).thenReturn(List.of(definicion));
        assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->normalizador.normalizar(
                List.of(pedido("MINIMO","I",BigDecimal.ONE,null,null,null)),AGENTE,Set.of("T"),"ALQUILER"));
        verify(catalogo,never()).bloquearActualParaCaptura(anyLong(),anyString());
    }
    @Test void criterioComercialConservaLaDimensionDeOperacion() {
        definicion.setSujeto(Sujeto.ENCARGO);definicion.setAplicaciones(Set.of());
        definicion.setAplicacionesOperacion(Set.of(new AplicacionPorOperacion("D","V",com.controllocal.domain.inmueble.Exigencia.OPC)));
        when(catalogo.disponiblesPara(AGENTE.idOrganizacion())).thenReturn(List.of(definicion));
        assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->normalizar(pedido("MINIMO","I",BigDecimal.ONE,null,null,null)));
        verify(catalogo,never()).bloquearVersionDeEncargo(anyLong(),anyString());
    }
    @Test void zonificacionTextoUsaElPerfilRealYConservaDefinicionYFuenteSeparadas() {
        prepararZonificacion();perfil("CAT:P:20:3","Plan TEST v1");
        var a=normalizar(zona("RDM",territorio("JUR-TEST","CAT:P:20:3",null))).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.CONOCIDO,a.metadatos().estado());assertEquals("RDM",a.valor().texto());
        assertEquals("CAT:D:42:4",a.metadatos().referenciaDefinicion());assertEquals(4L,a.metadatos().versionDefinicion());
        assertEquals("CAT:P:20:3",a.contexto().referenciaCatalogo());assertEquals("JUR-TEST",a.contexto().jurisdiccion());
        assertEquals("Plan TEST v1",a.contexto().versionFuente());
        verify(gobierno,times(1)).opcionesVigentes(11,"zonificacion","JUR-TEST");
    }
    @Test void zonificacionTextoInventadaNoSeAceptaPorSerUnTextoBienFormado() {
        prepararZonificacion();perfil("CAT:P:20:3","Plan TEST v1");
        var a=normalizar(zona("OTRO",territorio("JUR-TEST","CAT:P:20:3","Plan TEST v1"))).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.FALTANTE,a.metadatos().estado());assertEquals(Causa.VALOR_INVALIDO,a.metadatos().causa());assertNull(a.valor());
        assertEquals("Documento aportado TEST",a.metadatos().fuente());assertEquals("evidencia-test",a.metadatos().evidenciaRef());
    }
    @Test void jurisdiccionAusenteNoSeDeduceDelCodigo() {
        prepararZonificacion();
        var a=normalizar(zona("RDM",territorio(null,"CAT:P:20:3","Plan TEST v1"))).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.FALTANTE,a.metadatos().estado());assertEquals(Causa.CONTEXTO_TERRITORIAL_INCOMPATIBLE,a.metadatos().causa());
        verifyNoInteractions(gobierno);
    }
    @Test void referenciaTerritorialObservadaDistintaProvocaConflicto() {
        prepararZonificacion();perfil("CAT:P:20:4","Plan TEST v1");
        assertThrows(ConflictoException.class,()->normalizar(zona("RDM",territorio("JUR-TEST","CAT:P:20:3","Plan TEST v1"))));
    }
    @Test void versionDeFuenteObservadaNoSeReemplazaSilenciosamentePorOtra() {
        prepararZonificacion();perfil("CAT:P:20:3","Plan TEST v2");
        assertThrows(ConflictoException.class,()->normalizar(zona("RDM",territorio("JUR-TEST","CAT:P:20:3","Plan TEST v1"))));
    }
    @Test void transporteAnteriorSinFuenteTerritorialNoAcreditaUnaZonificacion() {
        prepararZonificacion();
        when(gobierno.opcionesVigentes(11,"zonificacion","JUR-TEST")).thenReturn(new OpcionesCatalogo("CAT:P:20:3",3L,List.of(new OpcionCatalogo("RDM","Residencial TEST")),null));
        var a=normalizar(zona("RDM",territorio("JUR-TEST","CAT:P:20:3",null))).getFirst().criterio().alternativas().getFirst();
        assertEquals(EstadoDato.FALTANTE,a.metadatos().estado());assertEquals(Causa.VIGENCIA_NO_ACREDITADA,a.metadatos().causa());assertNull(a.valor());
    }
    @Test void unFalloDelCatalogoSePropagaSinTransformarloEnOpcionAusente() {
        prepararZonificacion();when(gobierno.opcionesVigentes(11,"zonificacion","JUR-TEST")).thenThrow(new IllegalStateException("TEST catálogo caído"));
        assertThrows(IllegalStateException.class,()->normalizar(zona("RDM",territorio("JUR-TEST","CAT:P:20:3","Plan TEST v1"))));
    }
    private void prepararZonificacion() {
        definicion.setClave("zonificacion");definicion.setRotulo("Zonificación");definicion.setTipoDato("TEXTO");definicion.setUnidad(null);
        prepararCatalogo(42,4);
    }
    private void perfil(String referencia,String fuente) {
        when(gobierno.opcionesVigentes(11,"zonificacion","JUR-TEST")).thenReturn(new OpcionesCatalogo(referencia,3L,List.of(new OpcionCatalogo("RDM","Residencial TEST")),null,"JUR-TEST",fuente));
    }
    private static ContextoValor territorio(String jurisdiccion,String referencia,String fuente) {
        return new ContextoValor(null,null,null,null,"ALQUILER",null,jurisdiccion,referencia,fuente);
    }
    private static CriterioPedido zona(String codigo,ContextoValor contexto) {
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:42:4",4L,"evidencia-test","Documento aportado TEST",null,null,null);
        return new CriterioPedido("zonificacion","IGUAL","I",codigo,null,null,null,null,null,null,null,null,contexto,meta,false,null);
    }
    private void prepararCatalogo(long id,long version) {
        when(catalogo.disponiblesPara(AGENTE.idOrganizacion())).thenReturn(List.of(definicion));
        var actual=new DefinicionActual(id,version,definicion.getClave(),definicion.getSujeto(),definicion.getTipoDato());
        if("PROPIEDAD".equals(definicion.getSujeto()))
            when(catalogo.bloquearActualParaCaptura(AGENTE.idOrganizacion(),definicion.getClave())).thenReturn(Optional.of(actual));
        else when(catalogo.bloquearVersionDeEncargo(AGENTE.idOrganizacion(),definicion.getClave())).thenReturn(Optional.of(actual));
    }
    private List<NormalizadorCriteriosDemanda.Normalizado> normalizar(CriterioPedido... pedidos) {
        return normalizador.normalizar(List.of(pedidos),AGENTE,Set.of("D"),"ALQUILER");
    }
    private static CriterioPedido pedido(String operador,String peso,BigDecimal numero,BigDecimal hasta,Integer prioridad,MetadatosDato meta) {
        return new CriterioPedido("ascensores",operador,peso,null,numero,hasta,null,null,null,null,null,prioridad,null,meta,false,null);
    }
    private record DefinicionActual(Long id,long version,String clave,String sujeto,String tipo) implements CatalogoAtributoRepository.DefinicionActual {
        public Long getId(){return id;}public Long getOrganizacion(){return null;}
        public String getClave(){return clave;}public String getSujeto(){return sujeto;}
        public String getTipoDato(){return tipo;}public long getVersion(){return version;}
    }
}
