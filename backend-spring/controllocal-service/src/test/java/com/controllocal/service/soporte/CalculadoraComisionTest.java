package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.ComisionService;
import com.controllocal.service.OfertaService;
import com.controllocal.service.encargo.PactosDeEncargo.ReglaComision;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CalculadoraComisionTest {
    @Test void calculaMedioUnoUnoYMedioYDosMesesSinHeuristicas() {
        for(String[] caso:new String[][]{{"0.50","3600.00"},{"1","7200.00"},{"1.5","10800.00"},{"2","14400.00"}})
            assertEquals(new BigDecimal(caso[1]),calcular("E","R",caso[0],"PEN",fuente("7200","999999","PEN"),OperacionInmobiliaria.ALQUILER).cuantia().importe());
    }
    @Test void calculaPorcentajeSobreRentaComoFraccion() {
        assertEquals(new BigDecimal("216.00"),calcular("P","R","0.03","PEN",fuente("7200","500000","PEN"),OperacionInmobiliaria.ALQUILER).cuantia().importe());
    }
    @Test void calculaPorcentajeSobreVentaComoFraccionPuntoCeroTres() {
        assertEquals(new BigDecimal("15000.00"),calcular("P","V","0.03","PEN",fuente("7200","500000","PEN"),OperacionInmobiliaria.VENTA).cuantia().importe());
    }
    @Test void calculaMontoFijoEnSuMonedaSinNecesitarPrecioNiHito() {
        assertEquals(new BigDecimal("15000.00"),calcular("F","N","15000","USD",null,OperacionInmobiliaria.VENTA).cuantia().importe());
    }
    @Test void laBrutaRedondeaUnaSolaVezHalfUpDos() {
        assertEquals(new BigDecimal("1.01"),calcular("F","N","1.005","PEN",null,OperacionInmobiliaria.VENTA).cuantia().importe());
    }
    @Test void baseDesconocidaEsPendienteConMonedaConocidaNoCero() {
        var f=calcular("P","V","0.03","PEN",null,OperacionInmobiliaria.VENTA);
        assertNull(f.cuantia().importe());assertEquals("PEN",f.cuantia().moneda());
        assertEquals(List.of("PRECIO_VENTA_FINAL"),f.faltantes());
    }
    @Test void rentaFinalDesconocidaNoSeRellenaDesdeElOtroImporte() {
        var f=calcular("E","R","1","PEN",fuente(null,"500000","PEN"),OperacionInmobiliaria.ALQUILER);
        assertNull(f.cuantia().importe());assertEquals(List.of("RENTA_MENSUAL_FINAL"),f.faltantes());
    }
    @Test void montoFijoExigeMoneda() {
        assertThrows(ReglaNegocioException.class,()->calcular("F","N","15000",null,null,OperacionInmobiliaria.VENTA));
    }
    @Test void rechazaCombinacionTipoBaseInvalida() {
        assertThrows(ReglaNegocioException.class,()->calcular("F","V","15","PEN",null,OperacionInmobiliaria.VENTA));
        assertThrows(ReglaNegocioException.class,()->calcular("P","N","0.03","PEN",null,OperacionInmobiliaria.VENTA));
    }
    @Test void unaVentaNoSeComisionaEnMensualidadesNiSobreUnaRenta() {
        assertThrows(ReglaNegocioException.class,()->calcular("E","R","1","PEN",fuente("1000","500000","PEN"),OperacionInmobiliaria.VENTA));
        assertThrows(ReglaNegocioException.class,()->calcular("P","R","0.03","PEN",null,OperacionInmobiliaria.VENTA));
    }
    @Test void unAlquilerNoSeComisionaSobreUnPrecioDeVenta() {
        assertThrows(ReglaNegocioException.class,()->calcular("P","V","0.03","PEN",null,OperacionInmobiliaria.ALQUILER));
    }
    @Test void unaCondicionSinOperacionNoSeCalcula() {
        assertThrows(ReglaNegocioException.class,()->calcular("F","N","15","PEN",null,null));
    }
    @Test void conservaLaMonedaConocidaYPendienteSiLaBaseLegitimaNoEsComparable() {
        var fuente=fuente(null,"500000","PEN");
        var calculo=calcular("P","V","0.03","USD",fuente,OperacionInmobiliaria.VENTA);
        assertNull(calculo.cuantia().importe());assertEquals("USD",calculo.cuantia().moneda());
        assertEquals(List.of("MONEDA_BASE_FINAL_NO_COMPARABLE"),calculo.faltantes());
        assertEquals(new BigDecimal("500000"),fuente.precioVenta().importe());assertEquals("PEN",fuente.precioVenta().moneda());
    }
    @Test void monedaDeLaBaseDesconocidaNoSeHeredaDelPacto() {
        var f=calcular("P","V","0.03","PEN",fuente(null,"500000",null),OperacionInmobiliaria.VENTA);
        assertNull(f.cuantia().importe());assertEquals(List.of("MONEDA_BASE_FINAL"),f.faltantes());
    }
    @Test void ceroConocidoNecesitaMotivoYNoSustituyeBaseDesconocida() {
        assertThrows(ReglaNegocioException.class,()->calcular("F","N","0","PEN",null,OperacionInmobiliaria.VENTA));
        var r=new ReglaComision("F","N",BigDecimal.ZERO,"PEN","N","Sin comisión pactada",null,null,null,null,null,List.of(),null);
        assertEquals(new BigDecimal("0.00"),CalculadoraComision.calcular(r,OperacionInmobiliaria.VENTA,null).cuantia().importe());
        var p=new ReglaComision("P","V",BigDecimal.ZERO,"PEN","N","Sin comisión pactada",null,null,null,null,null,List.of(),null);
        assertEquals(new BigDecimal("0.00"),CalculadoraComision.calcular(p,OperacionInmobiliaria.VENTA,null).cuantia().importe());
    }
    private ComisionService.Calculo calcular(String tipo,String base,String valor,String moneda,FuenteEconomicaFinal fuente,OperacionInmobiliaria op){
        return CalculadoraComision.calcular(new ReglaComision(tipo,base,new BigDecimal(valor),moneda,"N",null,
                null,null,null,null,null,List.of(),null),op,fuente);
    }
    private FuenteEconomicaFinal fuente(String renta,String precio,String moneda){
        return new FuenteEconomicaFinal(new ReferenciaFuenteEconomica("REVISION_C5",3,11),null,null,null,
                precio==null?null:new OfertaService.Dinero(new BigDecimal(precio),moneda,"PRECIO_VENTA",null),
                renta==null?null:new OfertaService.Dinero(new BigDecimal(renta),moneda,"RENTA_MENSUAL","MENSUAL"),
                List.of(),Procedencia.deLaPantalla());
    }
}
