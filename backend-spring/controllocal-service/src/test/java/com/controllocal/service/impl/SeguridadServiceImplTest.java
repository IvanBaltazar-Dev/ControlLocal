package com.controllocal.service.impl;

import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.CuentaDeGobiernoFila;
import com.controllocal.persistence.repositorio.EventoSeguridadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeguridadServiceImplTest {
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final EventoSeguridadRepository eventos=mock(EventoSeguridadRepository.class);
    private final SeguridadServiceImpl seguridad=new SeguridadServiceImpl(eventos,cuentas);
    private static final Actor GOBIERNO=new Actor(71,90,99,Actor.TENANT_ADMIN);

    @Test void padronSeparaBrokerYAdministracionSinConsultarOtraFamilia() {
        when(cuentas.cuentasDeGobierno(71)).thenReturn(List.of(
                new CuentaDeGobiernoFila(90L,190L,"Profesional", "referencia",Actor.BROKER,true,true,700,2),
                new CuentaDeGobiernoFila(91L,191L,"Gobierno", "gobierno",null,true,true,701,0),
                new CuentaDeGobiernoFila(92L,192L,"Anterior", "anterior",Actor.AGENTE,false,false,702,5)));
        var filas=seguridad.cuentas(GOBIERNO);assertEquals(3,filas.size());
        assertEquals(Actor.BROKER,filas.getFirst().rolPrincipal());assertTrue(filas.getFirst().administrar());
        assertNull(filas.get(1).rolPrincipal());assertTrue(filas.get(1).administrar());
        assertFalse(filas.get(2).pertenenciaActiva());
        assertEquals(700,filas.getFirst().idMembresia());assertEquals(2,filas.getFirst().version());
        verify(cuentas).cuentasDeGobierno(71);verifyNoMoreInteractions(cuentas);verifyNoInteractions(eventos);
    }

    @Test void proyeccionSoloExponeLosNueveCamposPermitidos() {
        var nombres=Arrays.stream(com.controllocal.service.SeguridadService.CuentaDeGobierno.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());
        assertEquals(Set.of("idPersona","idCuenta","nombre","usuario","rolPrincipal","administrar","pertenenciaActiva","idMembresia","version"),nombres);
    }

    @Test void brokerAgenteOIdentidadSinContextoNoLeenElPadron() {
        for(String rol:new String[]{Actor.BROKER,Actor.AGENTE,"IDENTIDAD",null})
            assertThrows(AccesoNoAutorizadoException.class,()->seguridad.cuentas(new Actor(71,90,99,rol)));
        assertThrows(AccesoNoAutorizadoException.class,()->seguridad.cuentas(null));
        verifyNoInteractions(cuentas,eventos);
    }
}
