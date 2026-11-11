package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.OrganizacionRepository;
import com.controllocal.service.OfertaService.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.negociacion.FuentePrecioNegociacionCanonica;
import com.controllocal.service.excepcion.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FuentePrecioNegociacionCanonicaTest {
    ActividadDePrueba h;PrecioPropiedadRepository precios;AccesoEncargo acceso;FuentePrecioNegociacionCanonica fuente;
    final ObjetoExacto objeto=new ObjetoExacto(TipoObjeto.UNIDAD,20L,0L,30L,0L,null,null,null);
    final Dinero oferta=new Dinero(new BigDecimal("95000"),"PEN","TOTAL",null);
    @BeforeEach void montar(){
        h=new ActividadDePrueba();precios=mock(PrecioPropiedadRepository.class);
        var actual=mock(CaptacionRepository.ContextoActualEncargo.class);
        when(actual.getResponsable()).thenReturn(h.cap.getAgente().getId());
        when(actual.getVersion()).thenReturn(h.cap.getVersion());
        when(h.captaciones.contextoActual(1,20)).thenReturn(Optional.of(actual));
        // Se ejercitan las guardas reales; no se simula una denegación para satisfacer el test.
        acceso=spy(new AccesoEncargo(h.captaciones,h.alcances,mock(OrganizacionRepository.class),h.miembros,h.cuentas,h.sesiones));
        fuente=new FuentePrecioNegociacionCanonica(precios,h.encargos,acceso);
    }
    PrecioPropiedad precio(String hito,String moneda,String monto){
        var p=PrecioPropiedad.hito(1L,30L,OperacionInmobiliaria.VENTA,hito,moneda,new BigDecimal(monto),LocalDate.of(2026,10,30)).delEncargo(20L);
        ActividadDePrueba.id(p,900);return p;
    }
    @Test void sinPUReferenciaFaltanteNoEsCeroNiLaPropiaOferta(){
        assertNull(fuente.congelarReferencia(objeto,oferta,h.agente));verify(precios,never()).save(any());
    }
    @Test void ultimoPublicadoPrimaSobreAutorizadoDelMismoEncargo(){
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(20,"P")).thenReturn(Optional.of(precio("P","PEN","100000")));
        var r=fuente.congelarReferencia(objeto,oferta,h.agente);assertEquals("P",r.hito());assertEquals(0,new BigDecimal("100000").compareTo(r.monto()));
        verify(precios,never()).findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(20,"U");
    }
    @Test void sinAnuncioUsaUExacto(){
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(20,"U")).thenReturn(Optional.of(precio("U","USD","30000")));
        var r=fuente.congelarReferencia(objeto,oferta,h.agente);assertEquals("U",r.hito());assertEquals("USD",r.moneda());
        assertEquals(0,new BigDecimal("30000").compareTo(r.monto()));assertEquals("PEN",oferta.moneda());
    }
    @Test void noUsaSerieDeLaPropiedadQueMezclariaVentaYAlquiler(){
        fuente.congelarReferencia(objeto,oferta,h.agente);
        verify(precios,never()).findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(anyLong(),anyString());
    }
    @Test void serieCruzadaDeTenantEsFalloDeIntegridadNoOtraReferencia(){
        var p=precio("P","PEN","100000");p.setOrganizacionId(2L);
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(20,"P")).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class,()->fuente.congelarReferencia(objeto,oferta,h.agente));
    }
    @Test void serieCruzadaDeOperacionNoSeConvierteEnOtraMonedaOMonto(){
        var p=precio("P","PEN","3000");p.setOperacion(OperacionInmobiliaria.ALQUILER);
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(20,"P")).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class,()->fuente.congelarReferencia(objeto,oferta,h.agente));
    }
    @Test void hitoOConservaEncargoOfertaMonedaFechaSinCambiarUP(){
        var original=precio("U","PEN","100000");fuente.registrarHitoOfrecido(objeto,oferta,1000,LocalDate.of(2026,10,30),h.procedencia,h.agente);
        var captura=ArgumentCaptor.forClass(PrecioPropiedad.class);verify(precios,times(1)).save(captura.capture());var p=captura.getValue();
        assertEquals("O",p.getHito());assertEquals(20L,p.getIdCaptacion());assertEquals(1000L,p.getIdOferta());assertEquals(30L,p.getIdPropiedad());
        assertEquals("V",p.getOperacion());assertEquals("PEN",p.getMoneda());assertEquals(0,new BigDecimal("95000").compareTo(p.getMonto()));assertEquals(LocalDate.of(2026,10,30),p.getFecha());
        assertEquals(0,new BigDecimal("100000").compareTo(original.getMonto()));assertEquals("U",original.getHito());
        verify(acceso).leer(20,h.agente);verify(acceso).exigirResponsable(h.cap,h.agente);
        verify(acceso,never()).exigirEditor(any(),any());verify(h.encargos,never()).consultar(anyLong(),any());
    }
    @Test void registrarOExigeOperadorCompetente(){
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.registrarHitoOfrecido(objeto,oferta,1000,LocalDate.of(2026,10,30),h.procedencia,h.broker));
        verify(acceso).leer(20,h.broker);verify(acceso).exigirResponsable(h.cap,h.broker);
        verify(precios,never()).save(any());
    }
    @Test void noReseteaPrecioDeSalidaTrasUnCambioFormal(){
        var vigente=precio("U","PEN","102000");
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(20,"U")).thenReturn(Optional.of(vigente));
        assertEquals(0,new BigDecimal("102000").compareTo(fuente.congelarReferencia(objeto,oferta,h.agente).monto()));
        fuente.registrarHitoOfrecido(objeto,oferta,1000,LocalDate.of(2026,10,30),h.procedencia,h.agente);
        assertEquals(0,new BigDecimal("102000").compareTo(vigente.getMonto()));assertEquals("U",vigente.getHito());
    }
}
