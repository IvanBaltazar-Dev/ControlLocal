package com.controllocal.service.encargo;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService.*;
import com.controllocal.service.captura.SuficienciaInmueble.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** AC066: sujeto/operación, procedencia, interpretación privada y contrato documental compartidos. */
class CondicionesDinamicasEncargoTest {
    private final AtributosDeEncargo editor=mock(AtributosDeEncargo.class);
    private final LectorPorAutoridad lector=mock(LectorPorAutoridad.class);
    private final CondicionesDinamicasEncargo condiciones=new CondicionesDinamicasEncargo(editor,lector);
    private final Actor agente=new Actor(1,10,30,Actor.AGENTE);
    private final Procedencia acto=Procedencia.deCabecera("API");

    @Test void cambiarYRetirarConservanEpisodioOperacionYProcedenciaPorValor(){
        var c=encargo();var d=definicion("garantia_meses","DECIMAL","meses");
        when(editor.definicionesDe(eq(1L),any(Comercializacion.class))).thenReturn(Map.of(d.getClave(),d));
        var antes=ValoresGobernados.constructor().con("garantia_meses",ValorLogico.deNumero(new BigDecimal("2"))).con("amoblado",ValorLogico.deBooleano(true)).construir();
        var despues=ValoresGobernados.constructor().con("garantia_meses",ValorLogico.deNumero(new BigDecimal("3"))).construir();
        when(lector.deEncargo(9)).thenReturn(antes,despues);
        when(editor.retirar(any(),any(),eq("amoblado"),any())).thenReturn(ValorRetirado.de(ValorLogico.deBooleano(true)));
        var entrada=new ValorAtributo("garantia_meses","3",null,null,"DECLARADO",new BigDecimal("0.9"),LocalDate.of(2026,10,27),"Documento TEST",null);
        assertTrue(condiciones.aplicar(c,List.of(entrada),List.of("amoblado"),agente,acto));
        var valor=org.mockito.ArgumentCaptor.forClass(ValorEntrante.class);verify(editor).escribir(eq(agente),eq(new Comercializacion(9,"D","A")),valor.capture());
        assertEquals(acto,valor.getValue().procedencia().acto());assertEquals("Documento TEST",valor.getValue().procedencia().evidenciaRef());
        assertEquals(LocalDate.of(2026,10,27),valor.getValue().procedencia().observadoEn());
        verify(editor).retirar(eq(agente),eq(new Comercializacion(9,"D","A")),eq("amoblado"),any());
    }
    @Test void valorYRetiradaContrariosNoEligenUnaIntencionNiEscriben(){
        assertThrows(ReglaNegocioException.class,()->condiciones.aplicar(encargo(),List.of(new ValorAtributo("garantia_meses","2")),List.of("garantia_meses"),agente,acto));
        verifyNoInteractions(editor,lector);
    }
    @Test void unaCondicionDeAlquilerNoSeAdmiteEnOtraOperacionPorCompartirInmueble(){
        when(editor.definicionesDe(1,"D","V")).thenReturn(Map.of());
        when(editor.definicionAplicable(1,"garantia_meses","D","V")).thenThrow(new ReglaNegocioException("La garantía no aplica en VENTA."));
        assertThrows(ReglaNegocioException.class,()->condiciones.propuesta(1,"D","V",List.of(new ValorAtributo("garantia_meses","2")),acto));
        verify(editor,never()).interpretarPropuesta(anyLong(),anyString(),anyString(),anyList());verifyNoInteractions(lector);
    }
    @Test void versionDeDefinicionObservadaDiferenteNoSeReinterpreta(){
        var d=definicion("garantia_meses","DECIMAL","meses");when(editor.definicionesDe(1,"D","A")).thenReturn(Map.of(d.getClave(),d));
        var meta=new MetadatosDeCaptura(EstadoDato.CONOCIDO,null,null,"1",false);
        assertThrows(ConflictoException.class,()->condiciones.propuesta(1,"D","A",List.of(new ValorAtributo(d.getClave(),"2",null,null,null,null,null,null,meta)),acto));
        verify(editor,never()).interpretarPropuesta(anyLong(),anyString(),anyString(),anyList());
    }
    @Test void faltanteOConceptoFisicoNoSeConviertenEnCondicionConocida(){
        var d=definicion("garantia_meses","DECIMAL","meses");when(editor.definicionesDe(1,"D","A")).thenReturn(Map.of(d.getClave(),d));
        for(var meta:List.of(new MetadatosDeCaptura(EstadoDato.FALTANTE,null,null,null,false),new MetadatosDeCaptura(EstadoDato.CONOCIDO,"AREA_TOTAL",null,null,false)))
            assertThrows(ReglaNegocioException.class,()->condiciones.propuesta(1,"D","A",List.of(new ValorAtributo(d.getClave(),"2",null,null,null,null,null,null,meta)),acto));
    }
    @Test void condicionComercialNoSeTomaDeUnValorFisicoHomonimo(){
        var d=definicion("precio_estacionamiento_adicional","IMPORTE","moneda");when(editor.definicionesDe(1,"D","A")).thenReturn(Map.of(d.getClave(),d));
        var fisicos=Map.of("precio_estacionamiento_adicional",Dato.conocido(999),"moneda:precio_estacionamiento_adicional",Dato.conocido("USD"),"piso",Dato.conocido(3));
        var sinDato=condiciones.paraSuficiencia(1,"D","A",fisicos,ValoresGobernados.vacio());assertFalse(sinDato.containsKey(d.getClave()));assertEquals(3,sinDato.get("piso").valor());
        var comerciales=ValoresGobernados.constructor().con(d.getClave(),ValorLogico.deImporte(new BigDecimal("250"),"PEN")).construir();
        var propios=condiciones.paraSuficiencia(1,"D","A",fisicos,comerciales);assertEquals(new BigDecimal("250"),propios.get(d.getClave()).valor());assertEquals("PEN",propios.get("moneda:"+d.getClave()).valor());assertEquals(999,fisicos.get(d.getClave()).valor());
    }
    @Test void snapshotYFichaConservanRotuloUnidadMonedaYRetiradoParaLectura(){
        var c=encargo();var d=definicion("precio_estacionamiento_adicional","IMPORTE","moneda");d.setRotulo("Precio por cochera adicional");
        var valores=ValoresGobernados.constructor().con(d.getClave(),ValorLogico.deImporte(new BigDecimal("350.00"),"PEN")).construir();
        when(lector.deEncargo(9)).thenReturn(valores);when(editor.definicionesParaLeer(eq(1L),any(),anyCollection())).thenReturn(Map.of(d.getClave(),d));
        var documento=(Map<?,?>)condiciones.paraDocumento(c).get(d.getClave());assertEquals("Precio por cochera adicional",documento.get("rotulo"));assertEquals("IMPORTE",documento.get("tipoDato"));assertEquals("moneda",documento.get("unidad"));assertEquals(valores.valor(d.getClave()),documento.get("valor"));
        var ficha=condiciones.ficha(c,true).getFirst();assertEquals("PEN",ficha.moneda());assertEquals("PEN 350",ficha.valor());
        d.setActivo(false);var antigua=condiciones.ficha(c,true).getFirst();assertTrue(antigua.retirado());assertFalse(antigua.editable());assertEquals("PEN 350",antigua.valor());
    }
    @Test void propuestaPrivadaInterpretaConElMismoTipoSinCrearFilasNiLinaje(){
        var catalogo=mock(CatalogoAtributoRepository.class);var filas=mock(AtributoEncargoRepository.class);var multiples=mock(ValorMultipleEncargoRepository.class);var linaje=mock(LinajeDelValor.class);
        var real=new AtributosDeEncargo(catalogo,filas,multiples,linaje);var d=definicion("precio_estacionamiento_adicional","IMPORTE","moneda");when(catalogo.aplicablesAEncargo(1,"D","A")).thenReturn(List.of(d));
        var privado=new CondicionesDinamicasEncargo(real,lector);var salida=privado.propuesta(1,"D","A",List.of(new ValorAtributo(d.getClave(),"350.00","PEN",null,null,null,null,null)),acto);
        assertEquals(ValorLogico.deImporte(new BigDecimal("350"),"PEN"),salida.valor(d.getClave()));verifyNoInteractions(filas,multiples,linaje,lector);
        assertThrows(ReglaNegocioException.class,()->privado.propuesta(1,"D","A",List.of(new ValorAtributo(d.getClave(),"350")),acto));
    }
    @Test void propuestaRespetaVocabularioMultiplesYEscalaLogicaPersistida(){
        var d=definicion("garantia_meses","DECIMAL","meses");assertEquals(ValorLogico.deNumero(new BigDecimal("2")),ConversionDeValores.interpretar(d,new ValorEntrante(d.getClave(),"2.00",null,null,null)));
        var multi=definicion("condiciones_test","LISTA_MULTIPLE",null);
        assertEquals(List.of("A","B"),ConversionDeValores.interpretar(multi,new ValorEntrante(multi.getClave(),null,null,List.of("B","A"),null)).valores());
        assertThrows(ReglaNegocioException.class,()->ConversionDeValores.interpretar(multi,new ValorEntrante(multi.getClave(),null,null,List.of("A","A"),null)));
        assertThrows(ReglaNegocioException.class,()->ConversionDeValores.interpretar(d,new ValorEntrante(d.getClave(),null,null,List.of("A"),null)));
    }
    @Test void escritorNoUsaUnaDefinicionGestionadaSiLaVersionEscalarYaCambio(){
        var catalogo=mock(CatalogoAtributoRepository.class);var filas=mock(AtributoEncargoRepository.class);var multiples=mock(ValorMultipleEncargoRepository.class);var linaje=mock(LinajeDelValor.class);
        var real=new AtributosDeEncargo(catalogo,filas,multiples,linaje);var d=definicion("garantia_meses","DECIMAL","meses");ReflectionTestUtils.setField(d,"id",42L);
        when(catalogo.porClaveYSujeto(1,d.getClave(),"ENCARGO")).thenReturn(Optional.of(d));
        var actual=mock(CatalogoAtributoRepository.DefinicionActual.class);when(actual.getId()).thenReturn(42L);when(actual.getVersion()).thenReturn(3L);when(catalogo.bloquearVersionDeEncargo(1,d.getClave())).thenReturn(Optional.of(actual));
        assertThrows(ConflictoException.class,()->real.escribir(agente,new Comercializacion(9,"D","A"),new ValorEntrante(d.getClave(),"2",null,null,ProcedenciaDelValor.delActo(acto))));verifyNoInteractions(filas,multiples,linaje);
    }
    @Test void unaDefinicionLocalNuevaNoSeConfundeConLaGlobalGestionada(){
        var catalogo=mock(CatalogoAtributoRepository.class);var filas=mock(AtributoEncargoRepository.class);var multiples=mock(ValorMultipleEncargoRepository.class);var linaje=mock(LinajeDelValor.class);
        var real=new AtributosDeEncargo(catalogo,filas,multiples,linaje);var d=definicion("garantia_meses","DECIMAL","meses");ReflectionTestUtils.setField(d,"id",42L);
        when(catalogo.porClaveYSujeto(1,d.getClave(),"ENCARGO")).thenReturn(Optional.of(d));
        var actual=mock(CatalogoAtributoRepository.DefinicionActual.class);when(actual.getId()).thenReturn(43L);when(actual.getVersion()).thenReturn(2L);when(catalogo.bloquearVersionDeEncargo(1,d.getClave())).thenReturn(Optional.of(actual));
        assertThrows(ConflictoException.class,()->real.escribir(agente,new Comercializacion(9,"D","A"),new ValorEntrante(d.getClave(),"2",null,null,ProcedenciaDelValor.delActo(acto))));verifyNoInteractions(filas,multiples,linaje);
    }
    private static Captacion encargo(){var p=new Propiedad();p.setOrganizacionId(1L);p.setTipoInmueble("D");var c=new Captacion();c.setOrganizacionId(1L);c.setPropiedad(p);c.setMotivoOperacion("A");ReflectionTestUtils.setField(c,"id",9L);return c;}
    private static CatalogoAtributo definicion(String clave,String tipo,String unidad){var d=new CatalogoAtributo();d.setClave(clave);d.setRotulo(clave);d.setTipoDato(tipo);d.setUnidad(unidad);d.setSujeto(Sujeto.ENCARGO);d.setDestino("ATRIBUTO");d.setActivo(true);d.setAplicacionesOperacion(Set.of(new AplicacionPorOperacion("D","A",Exigencia.OPC)));ReflectionTestUtils.setField(d,"versionDefinicion",2L);return d;}
}
