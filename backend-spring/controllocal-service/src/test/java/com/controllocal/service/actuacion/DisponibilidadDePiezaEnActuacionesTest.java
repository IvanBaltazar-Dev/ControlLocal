package com.controllocal.service.actuacion;

import com.controllocal.domain.captura.OperacionHerramienta;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.capacidades.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.documentos.PiezasComercialesService;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La consulta usa el validador del efecto vigente; no compone, ejecuta o confirma. */
class DisponibilidadDePiezaEnActuacionesTest {
    private final Actor actor=new Actor(1,2,3,Actor.AGENTE);
    private final String referencia="b288f0f5-f542-41f0-a493-4e3e1a750954";
    private final OperacionHerramientaRepository operaciones=mock(OperacionHerramientaRepository.class);
    private final PiezasComercialesService piezas=mock(PiezasComercialesService.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final Documentos documentos=new Documentos(json);
    private final ActuacionesServiceImpl servicio=new ActuacionesServiceImpl(operaciones,mock(ComandoIdempotenteRepository.class),
            mock(BorradorCapturaRepository.class),mock(MotorDeCaptura.class),mock(AdaptadoresDeCapacidad.class),documentos,json,
            mock(PlatformTransactionManager.class),mock(ContextosService.class),mock(UnidadRelacionadaService.class),
            mock(PropiedadUniversalService.class),mock(CuentaAccesoRepository.class),mock(UsuarioOrganizacionRepository.class),
            List.of(new EfectosPiezaDeActuacion(piezas,json)));

    private void pendiente(){
        String contenido=documentos.objetoCanonico(Map.of("contrato",CapacidadesService.VERSION,"efecto",PiezasComercialesService.OBTENER,
                "datos",Map.of("pieza",referencia,"versionContenido",1,"huellaOrigen","a".repeat(64)),"revision",Map.of()));
        var vista=mock(OperacionHerramientaRepository.ContextoDeCapacidad.class);
        when(vista.getCapacidad()).thenReturn(PiezasComercialesService.OBTENER);when(vista.getEstado()).thenReturn(OperacionHerramienta.PREPARADA);
        when(vista.getIdBorrador()).thenReturn(null);
        when(vista.getContenido()).thenReturn(contenido);when(vista.getHuella()).thenReturn(Idempotencia.huellaExacta(contenido));
        when(vista.getIdPersona()).thenReturn(actor.idPersona());when(vista.getIdPersonaRol()).thenReturn(actor.idRolOperativo());
        when(vista.getRolEfectivo()).thenReturn(actor.rolEfectivo());
        when(operaciones.contextoDeLaActuacion(actor.idOrganizacion(),referencia)).thenReturn(Optional.of(vista));
    }
    @Test void configuracionApagadaSeDescribeSinHabilitarRevisionOConfirmacion(){
        pendiente();when(piezas.validarEntrega(any(),eq(actor))).thenThrow(new CapacidadDeshabilitadaException());
        var r=servicio.capacidadSobre(referencia,actor);
        assertEquals("CAPACIDAD_DESHABILITADA",r.motivoRevision());assertEquals("CAPACIDAD_DESHABILITADA",r.motivoConfirmacion());
        verify(piezas).exigirLecturaEntrega(any(),eq(actor));verify(piezas).validarEntrega(any(),eq(actor));verifyNoMoreInteractions(piezas);
    }
    @Test void unFalloTecnicoConservaSuFalloSinPresentarseComoConfiguracionApagada(){
        pendiente();var fallo=new IllegalStateException("TEST renderer configurado averiado");
        when(piezas.validarEntrega(any(),eq(actor))).thenThrow(fallo);
        assertSame(fallo,assertThrows(IllegalStateException.class,()->servicio.capacidadSobre(referencia,actor)));
        verify(piezas).exigirLecturaEntrega(any(),eq(actor));verify(piezas).validarEntrega(any(),eq(actor));verifyNoMoreInteractions(piezas);
    }
}
