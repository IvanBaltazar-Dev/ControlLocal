package com.controllocal.service.impl;

import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreparacionesDeContratoImplTest {
    private final Actor agente=new Actor(1,10,30,Actor.AGENTE),otro=new Actor(1,12,31,Actor.AGENTE),broker=new Actor(1,11,40,Actor.BROKER);
    private final ContratoAlquilerRepository repo=mock(ContratoAlquilerRepository.class);
    private final Alcances alcances=mock(Alcances.class);
    private final ContratoAlquilerRepository.PreparacionActual fila=mock(ContratoAlquilerRepository.PreparacionActual.class);
    private final PreparacionesDeContratoImpl lector=new PreparacionesDeContratoImpl(new AccesoContrato(repo,alcances),new Documentos(JsonMapper.builder().findAndAddModules().build()));
    @BeforeEach void datos() {
        when(repo.preparacionActual(1,70)).thenReturn(Optional.of(fila));when(fila.getId()).thenReturn(70L);when(fila.getEstado()).thenReturn("P");
        when(fila.getEncargo()).thenReturn(9L);when(fila.getOportunidad()).thenReturn(8L);when(fila.getSolicitud()).thenReturn(5L);
        when(fila.getAgenteSolicitud()).thenReturn(30L);when(fila.getResponsableEncargo()).thenReturn(31L);
        when(fila.getAnterior()).thenReturn(null);
        when(alcances.supervisados(1,40)).thenReturn(List.of(31L));
    }
    @Test void desconocidosNoSeConviertenEnFirmaHoyRentaCeroNiMonedaPorDefecto() {
        var p=lector.consultar(70,agente);assertEquals("P",p.estado());assertNull(p.rentaContractual());assertNull(p.moneda());
        assertNull(p.inicio());assertNull(p.fin());assertNull(p.contratoAnterior());assertNotNull(p.huella());
    }
    @Test void laSolicitudConocidaMantieneSuPropioAlcanceSinConfundirloConResponsableDeEncargo() {
        assertEquals(70,lector.consultar(70,agente).id());
        assertThrows(AccesoNoAutorizadoException.class,()->lector.consultar(70,otro));
    }
    @Test void sinSolicitudSoloLlegaElResponsableActualDelEncargo() {
        when(fila.getSolicitud()).thenReturn(null);
        assertEquals(70,lector.consultar(70,otro).id());
        assertThrows(AccesoNoAutorizadoException.class,()->lector.consultar(70,agente));
    }
    @Test void brokerSeRevalidaCadaVezSinCacheDePermiso() {
        assertEquals(70,lector.consultar(70,broker).id());when(alcances.supervisados(1,40)).thenReturn(List.of());
        assertThrows(AccesoNoAutorizadoException.class,()->lector.consultar(70,broker));
    }
    @Test void gobiernoNoLeePreparacionesComerciales() {
        assertThrows(AccesoNoAutorizadoException.class,()->lector.consultar(70,new Actor(1,13,45,Actor.TENANT_ADMIN)));
        verifyNoInteractions(repo);
    }
    @Test void otroTenantNoObtieneLaFilaAunqueConozcaIdYRolCoincidentes() {
        assertThrows(NoEncontradoException.class,()->lector.consultar(70,new Actor(2,10,30,Actor.AGENTE)));
        verify(repo,never()).preparacionActual(1,70);
    }
    @Test void huellaDescribeDatosNoElActorNiElPermiso() {
        assertEquals(lector.consultar(70,agente).huella(),lector.consultar(70,broker).huella());
        when(fila.getRenta()).thenReturn(new BigDecimal("1000.00"));String a=lector.consultar(70,agente).huella();
        when(fila.getRenta()).thenReturn(new BigDecimal("1000.0000"));assertEquals(a,lector.consultar(70,agente).huella());
        when(fila.getRenta()).thenReturn(new BigDecimal("1001"));assertNotEquals(a,lector.consultar(70,agente).huella());
    }
    @Test void lectorNeutralPermaneceValidoEnDPerolaHuellaYaNoAcreditaElPAnterior() {
        String p=lector.consultar(70,agente).huella();when(fila.getEstado()).thenReturn("D");
        when(fila.getInicio()).thenReturn(LocalDate.of(2026,11,1));when(fila.getFin()).thenReturn(LocalDate.of(2027,11,1));
        var d=lector.consultar(70,agente);assertEquals("D",d.estado());assertNotEquals(p,d.huella());
    }
    @Test void errorTecnicoNoDevuelveUnaPreparacionVaciaNiUnPermisoNegado() {
        var fallo=new IllegalStateException("Fallo técnico de ensayo");when(repo.preparacionActual(1,70)).thenThrow(fallo);
        assertSame(fallo,assertThrows(IllegalStateException.class,()->lector.consultar(70,agente)));
    }
}
