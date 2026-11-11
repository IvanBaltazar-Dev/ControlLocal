package com.controllocal.service.encargo;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.matching.ContratoMatching.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class CondicionesDeEncargoTest {
    private final CondicionesDeEncargo reglas=new CondicionesDeEncargo(JsonMapper.builder().findAndAddModules().build());
    private static EncargoService.Condiciones pacto(String tipo,String base,BigDecimal valor,String monedaComision) {
        return new EncargoService.Condiciones(new BigDecimal("2500"),"PEN","MENSUAL",tipo,base,valor,monedaComision,"N",null,
                "Firma del contrato","Propietario","Agencia","A la firma","Correo aportado por las partes",List.of(),null,null);
    }
    @Test void reutilizaLaMonedaExplicitaDeLaRentaSinOtraPregunta() {
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("E","R",BigDecimal.ONE,null));
        assertEquals("PEN",c.getMonedaComision()); assertTrue(reglas.faltantes(c,OperacionInmobiliaria.ALQUILER).isEmpty());
    }
    @Test void porcentajeConservaFraccionYBaseSinCalcularUnImporteFuturo() {
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("P","R",new BigDecimal("0.03"),null));
        assertEquals(new BigDecimal("0.03"),c.getValorComision());assertEquals("PEN",c.getMonedaComision());
        assertEquals(new BigDecimal("2500"),c.getImporteReferencia());
    }
    @Test void unImporteFijoNoInfiereMonedaDelPrecio() {
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("F","N",new BigDecimal("500"),null));
        assertNull(c.getMonedaComision());assertTrue(reglas.faltantes(c,OperacionInmobiliaria.ALQUILER).contains("MONEDA_COMISION"));
    }
    @Test void unaMonedaDistintaNoRedenominaLaComisionDerivada() {
        assertThrows(ReglaNegocioException.class,()->reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("E","R",BigDecimal.ONE,"USD")));
    }
    @Test void preparacionIncompletaNoFabricaUnCero() {
        assertNull(reglas.nueva(1,OperacionInmobiliaria.ALQUILER,null));
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto(null,null,null,null));
        assertNull(c.getValorComision()); assertTrue(reglas.faltantes(c,OperacionInmobiliaria.ALQUILER).contains("VALOR_COMISION"));
    }
    @Test void ceroDeclaradoNecesitaSuMotivo() {
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("F","N",BigDecimal.ZERO,"PEN"));
        assertTrue(reglas.faltantes(c,OperacionInmobiliaria.ALQUILER).contains("MOTIVO_SIN_COMISION"));
    }
    @Test void noCreaUnaModalidadDeComisionPorTenerTramos() {
        assertThrows(ReglaNegocioException.class,()->reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("T","N",BigDecimal.ONE,"PEN")));
    }
    @Test void laVentaNoSePactaEnMensualidades() {
        assertThrows(ReglaNegocioException.class,()->reglas.nueva(1,OperacionInmobiliaria.VENTA,pacto("E","R",BigDecimal.ONE,"PEN")));
    }
    @Test void unaEdicionParcialConservaElPactoConocido() {
        var anterior=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("E","R",BigDecimal.ONE,"PEN"));
        var parcial=new EncargoService.Condiciones(new BigDecimal("2800"),null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,reglas.completar(anterior,parcial));
        assertEquals(new BigDecimal("2800"),c.getImporteReferencia());assertEquals(BigDecimal.ONE,c.getValorComision());assertEquals("E",c.getTipoComision());
    }
    @Test void cambiarConceptoNoConvierteUnaMensualidadEnUnSol() {
        var anterior=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,pacto("E","R",BigDecimal.ONE,"PEN"));
        var parcial=new EncargoService.Condiciones(null,null,null,"F","N",null,null,null,null,null,null,null,null,null,null,null,null);
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,reglas.completar(anterior,parcial));
        assertEquals("F",c.getTipoComision());assertNull(c.getValorComision());assertNull(c.getMonedaComision());
    }
    @Test void totalAcreditadoSinDesgloseConservaFuenteYBaseYNoSumaLaRentaOtraVez() {
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Términos declarados para la oferta",List.of(linea("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))));
        reglas.vincularMensual(c,"UNIDAD:12");var antes=c.getComposicionMensual();
        var total=total(c,"UNIDAD:12");assertFalse(total.intervalo());assertEquals(0,new BigDecimal("2700").compareTo(total.valor().numero()));
        assertEquals("Términos declarados para la oferta",total.metadatos().fuente());assertEquals("DOCUMENTO_ENCARGO:9",total.metadatos().evidenciaRef());
        assertEquals(antes,c.getComposicionMensual(),"Proyectar nunca actualiza o vincula la declaración guardada.");
        assertEquals(reglas.ficha(c).composicionMensual(),reglas.fichaParaContexto(c,"UNIDAD:12").composicionMensual());
        var arbol=assertDoesNotThrow(()->JsonMapper.builder().findAndAddModules().build().readTree(antes));
        assertEquals(1,arbol.get("versionEsquema").intValue());assertTrue(arbol.get("huellaBaseEconomica").textValue().matches("[a-f0-9]{64}"));
        assertEquals("UNIDAD:12",arbol.get("configuracion").textValue());
    }
    @Test void rentaYMantenimientoSeparadosOIncluidosProducenElImporteQueCorresponde() {
        var renta=linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null);
        var cuota=linea("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.EXIGIBLE,null);
        var separado=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(renta,cuota)));reglas.vincularMensual(separado,"UNIDAD:12");
        assertEquals(0,new BigDecimal("2700").compareTo(total(separado,"UNIDAD:12").valor().numero()));
        var incluido=conMensual(new EncargoService.ComposicionMensual(true,"El total incluye la cuota",List.of(renta,
                linea("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.INCLUIDO_EN_OTRO,"RENTA_DEL_ENCARGO"))));reglas.vincularMensual(incluido,"UNIDAD:12");
        assertEquals(0,new BigDecimal("2500").compareTo(total(incluido,"UNIDAD:12").valor().numero()));
    }
    @Test void declararLaListaCompletaNoInventaUnCargoDesconocidoNiSuMoneda() {
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Oferta parcial",List.of(
                linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null),linea("cuota","MANTENIMIENTO",null,"PEN",TratamientoMensual.EXIGIBLE,null))));
        reglas.vincularMensual(c,"UNIDAD:12");var parcial=total(c,"UNIDAD:12");
        assertTrue(parcial.intervalo());assertNull(parcial.hasta());assertEquals(0,new BigDecimal("2500").compareTo(parcial.valor().numero()));
        assertEquals(Causa.DATO_FALTANTE,parcial.metadatos().causa());
        var cero=conMensual(new EncargoService.ComposicionMensual(true,"Cuota expresamente cero",List.of(
                linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null),linea("cuota","MANTENIMIENTO","0","PEN",TratamientoMensual.EXIGIBLE,null))));
        reglas.vincularMensual(cero,"UNIDAD:12");assertFalse(total(cero,"UNIDAD:12").intervalo());
        var sinMoneda=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(linea("total","TOTAL_MENSUAL_OBLIGATORIO","2700",null,TratamientoMensual.EXIGIBLE,null))));
        reglas.vincularMensual(sinMoneda,"UNIDAD:12");assertNull(total(sinMoneda,"UNIDAD:12").valor());assertEquals(Causa.MONEDA_NO_COMPARABLE,total(sinMoneda,"UNIDAD:12").metadatos().causa());
    }
    @Test void enumerarImportesNoDeclaraExhaustividadYNingunNuevoMinimoBloqueaElEncargo() {
        var c=conMensual(new EncargoService.ComposicionMensual(null,null,List.of(linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null))));
        reglas.vincularMensual(c,"UNIDAD:12");assertTrue(total(c,"UNIDAD:12").intervalo());assertNull(total(c,"UNIDAD:12").hasta());
        assertEquals("Correo aportado por las partes",reglas.ficha(c).composicionMensual().fuente());
        assertTrue(reglas.faltantes(c,OperacionInmobiliaria.ALQUILER).isEmpty());
    }
    @Test void otraConfiguracionUOtraTarifaNoHeredanLaDeclaracionAcreditada() {
        var anterior=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(linea("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))));
        reglas.vincularMensual(anterior,"UNIDAD:12");var original=anterior.getComposicionMensual();
        assertNull(reglas.fichaParaContexto(anterior,"COMPOSICION:19").composicionMensual());
        assertEquals(Causa.CONFIGURACION_INCOMPATIBLE,total(anterior,"COMPOSICION:19").metadatos().causa());
        var parcial=new EncargoService.Condiciones(new BigDecimal("2800"),null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        var nueva=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,reglas.completar(anterior,parcial));nueva.setComposicionMensual(original);
        assertNull(reglas.fichaParaContexto(nueva,"UNIDAD:12").composicionMensual());assertNull(total(nueva,"UNIDAD:12").valor());
        assertEquals(original,anterior.getComposicionMensual());assertEquals(0,new BigDecimal("2500").compareTo(anterior.getImporteReferencia()));
    }
    @Test void unCambioSoloDeComisionConservaLosCargosDeLaMismaBase() {
        var anterior=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(linea("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))));reglas.vincularMensual(anterior,"UNIDAD:12");
        var parcial=new EncargoService.Condiciones(null,null,null,null,null,new BigDecimal("2"),null,null,null,null,null,null,null,null,null,null,null);
        var nueva=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,reglas.completar(anterior,parcial));nueva.setComposicionMensual(anterior.getComposicionMensual());
        assertNotNull(reglas.fichaParaContexto(nueva,"UNIDAD:12").composicionMensual());assertFalse(total(nueva,"UNIDAD:12").intervalo());
    }
    @Test void descuentosFirmesNecesitanCoberturaDelPeriodoNoUnaPromocionTemporal() {
        var renta=linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null);
        var firme=new EncargoService.ImporteMensual("rebaja","REDUCCION_FIRME",new BigDecimal("100"),null,false,"PEN","MENSUAL",TratamientoMensual.DESCUENTO_FIRME,null,"Aceptada en los términos",INICIO,FIN);
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(renta,firme)));reglas.vincularMensual(c,"UNIDAD:12");
        assertEquals(0,new BigDecimal("2400").compareTo(total(c,"UNIDAD:12").valor().numero()));
        var delMismoPacto=linea("rebaja","REDUCCION_FIRME","100","PEN",TratamientoMensual.DESCUENTO_FIRME,null);
        var reutilizado=conMensual(new EncargoService.ComposicionMensual(true,"Descuento firme durante este pacto",List.of(renta,delMismoPacto)));reglas.vincularMensual(reutilizado,"UNIDAD:12");
        assertEquals(0,new BigDecimal("2400").compareTo(total(reutilizado,"UNIDAD:12").valor().numero()),"Se reutilizan las fechas inequívocas del pacto, sin recapturarlas.");
        var temporal=new EncargoService.ImporteMensual("rebaja","REDUCCION_FIRME",new BigDecimal("100"),null,false,"PEN","MENSUAL",TratamientoMensual.DESCUENTO_FIRME,null,"Sólo promoción inicial",INICIO,INICIO.plusDays(2));
        var p=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(renta,temporal)));reglas.vincularMensual(p,"UNIDAD:12");
        assertNull(total(p,"UNIDAD:12").valor());assertEquals(Causa.VIGENCIA_NO_ACREDITADA,total(p,"UNIDAD:12").metadatos().causa());
    }
    @Test void unaInclusionTemporalNoSePresentaComoGratuidadDeTodoElPacto() {
        var cuota=new EncargoService.ImporteMensual("cuota","MANTENIMIENTO",new BigDecimal("200"),null,false,"PEN","MENSUAL",TratamientoMensual.INCLUIDO_EN_OTRO,"RENTA_DEL_ENCARGO","Incluida sólo al inicio",INICIO,INICIO.plusDays(2));
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null),cuota)));reglas.vincularMensual(c,"UNIDAD:12");
        var total=total(c,"UNIDAD:12");assertNull(total.valor());assertEquals(Causa.VIGENCIA_NO_ACREDITADA,total.metadatos().causa());
    }
    @Test void componentesMultimonedaYDeOtroPeriodoSeConservanSinConversion() {
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null),linea("cuota","MANTENIMIENTO","50","USD",TratamientoMensual.EXIGIBLE,null))));reglas.vincularMensual(c,"UNIDAD:12");
        assertEquals(Causa.MONEDA_NO_COMPARABLE,total(c,"UNIDAD:12").metadatos().causa());assertEquals("USD",reglas.ficha(c).composicionMensual().componentes().get(1).moneda());
        var anual=new EncargoService.ImporteMensual("cuota","CUOTA",new BigDecimal("1200"),null,false,"PEN","ANUAL",TratamientoMensual.EXIGIBLE,null,null,null,null);
        var p=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(anual)));reglas.vincularMensual(p,"UNIDAD:12");
        assertEquals(Causa.PERIODO_INCOMPATIBLE,total(p,"UNIDAD:12").metadatos().causa());assertNull(total(p,"UNIDAD:12").valor());
    }
    @Test void limitesSustentadosConservanAmbosExtremosSinConvertirseEnTotalPuntual() {
        var cuota=new EncargoService.ImporteMensual("cuota","MANTENIMIENTO",new BigDecimal("100"),new BigDecimal("200"),true,"PEN","MENSUAL",TratamientoMensual.EXIGIBLE,null,null,null,null);
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Límites documentados",List.of(linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null),cuota)));reglas.vincularMensual(c,"UNIDAD:12");
        var total=total(c,"UNIDAD:12");assertTrue(total.intervalo());assertEquals(0,new BigDecimal("2600").compareTo(total.valor().numero()));assertEquals(0,new BigDecimal("2700").compareTo(total.hasta().numero()));
    }
    @Test void noSumarTotalYRentaNiAceptarInclusionesCirculares() {
        var total=linea("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null);
        var renta=linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null);
        assertThrows(ReglaNegocioException.class,()->conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(total,renta))));
        assertThrows(ReglaNegocioException.class,()->conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(
                linea("a","A","1","PEN",TratamientoMensual.INCLUIDO_EN_OTRO,"b"),linea("b","B","1","PEN",TratamientoMensual.INCLUIDO_EN_OTRO,"a")))));
    }
    @Test void ausenciaOpcionalNoCambiaElDocumentoAnteriorYUnaDeclaracionNuevaSiEntraEnSuHuella() {
        var documentos=new Documentos(JsonMapper.builder().findAndAddModules().build());
        var sinMensual=documentos.objetoCanonico(pacto("E","R",BigDecimal.ONE,"PEN"));assertFalse(sinMensual.contains("composicionMensual"));
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Oferta",List.of(linea("total","TOTAL_MENSUAL_OBLIGATORIO","2700","PEN",TratamientoMensual.EXIGIBLE,null))));reglas.vincularMensual(c,"UNIDAD:12");
        var conMensual=documentos.objetoCanonico(reglas.ficha(c));assertTrue(conMensual.contains("composicionMensual"));assertNotEquals(sinMensual,conMensual);
    }
    @Test void extrasSeparadosReutilizanLaRentaDelPactoSinVolverACapturarla() {
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Renta más cuota; no otros cargos",List.of(linea("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.EXIGIBLE,null))));
        reglas.vincularMensual(c,"UNIDAD:12");assertEquals(0,new BigDecimal("2700").compareTo(total(c,"UNIDAD:12").valor().numero()));
        assertEquals(1,reglas.ficha(c).composicionMensual().componentes().size(),"La fuente guardada no se reescribe al reutilizar la renta.");
    }
    @Test void cuotaIncluidaPuedeReferirLaRentaConocidaSinDuplicarSuRegistro() {
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Cuota incluida en renta",List.of(linea("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.INCLUIDO_EN_OTRO,"RENTA_DEL_ENCARGO"))));
        reglas.vincularMensual(c,"UNIDAD:12");assertFalse(total(c,"UNIDAD:12").intervalo());assertEquals(0,new BigDecimal("2500").compareTo(total(c,"UNIDAD:12").valor().numero()));
    }
    @Test void declaracionExhaustivaSinExtrasNoSignificaRentaCeroYRequiereBaseConocida() {
        var c=conMensual(new EncargoService.ComposicionMensual(true,"No hay cargos adicionales a esta renta",List.of()));reglas.vincularMensual(c,"UNIDAD:12");
        assertFalse(total(c,"UNIDAD:12").intervalo());assertEquals(0,new BigDecimal("2500").compareTo(total(c,"UNIDAD:12").valor().numero()));
        var pendiente=conMensual(new EncargoService.ComposicionMensual(true,"Todavía falta el precio",List.of()));pendiente.setImporteReferencia(null);reglas.vincularMensual(pendiente,"UNIDAD:12");
        assertNull(total(pendiente,"UNIDAD:12").valor());assertEquals(Causa.DATO_FALTANTE,total(pendiente,"UNIDAD:12").metadatos().causa());
    }
    @Test void rentaExplicitaCoherenteDuplicadaSeCuentaUnaVezYDiscrepanteNoEligeElMenor() {
        var renta=linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2500","PEN",TratamientoMensual.EXIGIBLE,null);
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Renta y cuota",List.of(renta,renta,linea("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.EXIGIBLE,null))));reglas.vincularMensual(c,"UNIDAD:12");
        assertEquals(0,new BigDecimal("2700").compareTo(total(c,"UNIDAD:12").valor().numero()));
        var discrepante=conMensual(new EncargoService.ComposicionMensual(true,"Dato discrepante",List.of(linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2400","PEN",TratamientoMensual.EXIGIBLE,null))));reglas.vincularMensual(discrepante,"UNIDAD:12");
        assertNull(total(discrepante,"UNIDAD:12").valor());assertEquals(Causa.CONFLICTO,total(discrepante,"UNIDAD:12").metadatos().causa());
    }
    @Test void tarifaPorMetroConExtrasNoSeConvierteEnRentaDelObjeto() {
        var c=conMensual(new EncargoService.ComposicionMensual(true,"Tarifa más cuota",List.of(linea("cuota","MANTENIMIENTO","200","PEN",TratamientoMensual.EXIGIBLE,null))));c.setImporteReferencia(new BigDecimal("6"));reglas.vincularMensual(c,"UNIDAD:12");
        var total=reglas.totalMensual(c,"ALQUILER","UNIDAD:12",INICIO,FIN,"DOCUMENTO_ENCARGO:9",Instant.parse("2026-10-30T12:00:00Z"),"POR_M2_AL_MES");
        assertNull(total.valor());assertEquals(Causa.UNIDAD_INCOMPATIBLE,total.metadatos().causa());
        var explicita=conMensual(new EncargoService.ComposicionMensual(true,"Renta del objeto acreditada aparte de la tarifa",List.of(linea("RENTA_DEL_ENCARGO","PRECIO_ALQUILER","2700","PEN",TratamientoMensual.EXIGIBLE,null))));explicita.setImporteReferencia(new BigDecimal("6"));reglas.vincularMensual(explicita,"UNIDAD:12");
        var conocido=reglas.totalMensual(explicita,"ALQUILER","UNIDAD:12",INICIO,FIN,"DOCUMENTO_ENCARGO:9",Instant.parse("2026-10-30T12:00:00Z"),"POR_M2_AL_MES");
        assertFalse(conocido.intervalo());assertEquals(0,new BigDecimal("2700").compareTo(conocido.valor().numero()));
    }
    private static final LocalDate INICIO=LocalDate.of(2026,10,1),FIN=LocalDate.of(2026,11,30);
    @Test void condicionEjecutableEsOpcionalYConservaLiteralDesconocidoSinInterpretarlo() {
        var p=pacto("P","R",new BigDecimal("0.03"),"PEN");
        var dato=new EncargoService.Condiciones(p.precioPedido(),p.monedaPrecio(),p.periodoPrecio(),p.tipoComision(),p.baseCalculo(),p.valorComision(),p.monedaComision(),
                p.tratamientoIgv(),p.motivoSinComision(),p.hitoDevengo(),p.parteObligada(),p.beneficiario(),"Cinco días después del hecho pactado",p.procedencia(),p.tramos(),null,null);
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,dato);
        assertEquals("Cinco días después del hecho pactado",reglas.ficha(c).exigibilidad());
        assertNull(c.getEjecucionPacto());assertNull(reglas.ficha(c).ejecucion());assertTrue(reglas.faltantes(c,OperacionInmobiliaria.ALQUILER).isEmpty());
    }
    @Test void estructuraExpresaSeConservaConSuHitoYFechaSinInventarCalendario() {
        var e=new PactosDeEncargo.EjecucionPacto(new PactosDeEncargo.CondicionDevengo(com.controllocal.domain.comercial.TipoHitoContractual.MINUTA),
                new PactosDeEncargo.CondicionExigibilidad(null,LocalDate.of(2026,11,3),null,null,null));
        var p=pacto("P","R",new BigDecimal("0.03"),"PEN");
        var d=new EncargoService.Condiciones(p.precioPedido(),p.monedaPrecio(),p.periodoPrecio(),p.tipoComision(),p.baseCalculo(),p.valorComision(),p.monedaComision(),
                p.tratamientoIgv(),p.motivoSinComision(),p.hitoDevengo(),p.parteObligada(),p.beneficiario(),p.exigibilidad(),p.procedencia(),p.tramos(),null,e);
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,d);assertNotNull(c.getEjecucionPacto());assertEquals(e,reglas.ficha(c).ejecucion());
        assertNull(reglas.ficha(c).ejecucion().exigibilidad().unidadExpresa());assertNull(reglas.ficha(c).ejecucion().exigibilidad().computoExpreso());
        var precio=new EncargoService.Condiciones(new BigDecimal("2800"),null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        assertEquals(e,reglas.completar(c,precio).ejecucion());
        var otroHito=new EncargoService.Condiciones(null,null,null,null,null,null,null,null,null,"Otro hito declarado",null,null,null,null,null,null,null);
        assertNull(reglas.completar(c,otroHito).ejecucion());assertEquals(e,reglas.ficha(c).ejecucion());
    }
    @Test void tramoConservaCondicionesSeparadasSinAfirmarDevengoPorSerUnaCuota() {
        var e=new PactosDeEncargo.EjecucionPacto(null,new PactosDeEncargo.CondicionExigibilidad(null,LocalDate.of(2026,11,3),null,null,null));
        var t=new EncargoService.Tramo("Cuota uno",new BigDecimal("500"),null,"PEN","Fecha expresamente pactada",e);
        var p=pacto("F","N",new BigDecimal("500"),"PEN");
        var d=new EncargoService.Condiciones(p.precioPedido(),p.monedaPrecio(),p.periodoPrecio(),p.tipoComision(),p.baseCalculo(),p.valorComision(),p.monedaComision(),
                p.tratamientoIgv(),p.motivoSinComision(),p.hitoDevengo(),p.parteObligada(),p.beneficiario(),p.exigibilidad(),p.procedencia(),List.of(t),null,null);
        var c=reglas.nueva(1,OperacionInmobiliaria.ALQUILER,d);var vuelta=reglas.ficha(c);
        assertEquals(e,vuelta.tramos().getFirst().ejecucion());assertNull(vuelta.tramos().getFirst().ejecucion().devengo());assertNull(vuelta.ejecucion());
    }
    private com.controllocal.domain.comercial.CondicionEconomicaCaptacion conMensual(EncargoService.ComposicionMensual mensual) {
        var p=pacto("E","R",BigDecimal.ONE,"PEN");return reglas.nueva(1,OperacionInmobiliaria.ALQUILER,new EncargoService.Condiciones(p.precioPedido(),p.monedaPrecio(),p.periodoPrecio(),p.tipoComision(),p.baseCalculo(),p.valorComision(),p.monedaComision(),p.tratamientoIgv(),p.motivoSinComision(),p.hitoDevengo(),p.parteObligada(),p.beneficiario(),p.exigibilidad(),p.procedencia(),p.tramos(),mensual,null));
    }
    private Dato total(com.controllocal.domain.comercial.CondicionEconomicaCaptacion c,String config){return reglas.totalMensual(c,"ALQUILER",config,INICIO,FIN,"DOCUMENTO_ENCARGO:9",Instant.parse("2026-10-30T12:00:00Z"),null);}
    private static EncargoService.ImporteMensual linea(String ref,String concepto,String importe,String moneda,TratamientoMensual trato,String incluido) {
        return new EncargoService.ImporteMensual(ref,concepto,importe==null?null:new BigDecimal(importe),null,false,moneda,"MENSUAL",trato,incluido,null,null,null);
    }
}
