package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Frontera interna B0; las fuentes/evidencias son sintéticas y no habilitan proveedores. */
class B0NoProcesamientoTest {
    final PresupuestoIaRepository repo=mock(PresupuestoIaRepository.class);
    final Instant inicio=Instant.parse("2026-10-31T12:00:00Z"),ahora=inicio.plusSeconds(60);
    final ReferenciaInterna ref=new ReferenciaInterna(17,UUID.randomUUID());
    final UUID fondo=UUID.randomUUID(),tecnica=UUID.randomUUID();
    final AtomicReference<ProyeccionCosto> costo=new AtomicReference<>(new ProyeccionCosto(tecnica,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"a".repeat(64),0));
    final AtomicReference<Operacion> actual=new AtomicReference<>();
    PresupuestoIaService servicio;
    @BeforeEach void preparar(){
        servicio=new PresupuestoIaService(repo,mock(ContextoPresupuestoB0.class),List.of(),
                new Documentos(new ObjectMapper().findAndRegisterModules()),mock(DerechosDePlanB0.class),false,Clock.fixed(ahora,ZoneOffset.UTC));
        actual.set(operacion("EN_PROCESO",null));
        when(repo.bloquearOrganizacion(ref.organizacion())).thenReturn(new Organizacion("EMPRESA",false));
        when(repo.operacion(ref.organizacion(),ref.operacion())).thenAnswer(x->Optional.of(actual.get()));
        when(repo.inicio(ref.organizacion(),ref.operacion())).thenReturn(Optional.of(inicio));
        when(repo.idsMediciones(ref.organizacion(),ref.operacion())).thenReturn(Set.of());
        when(repo.bloquearFondo(fondo)).thenReturn(Optional.of(new Fondo(fondo,n("10"),n("1"),BigDecimal.ZERO,inicio,inicio.plusSeconds(30),false)));
        when(repo.proyeccionCosto(tecnica)).thenAnswer(i->Optional.of(costo.get()));
        when(repo.costosOperacion(tecnica)).thenAnswer(i->List.of(new LineaCosto(tecnica,actual.get().estado(),n("1"),BigDecimal.ZERO,null,null,null,null)));
        doAnswer(i->{costo.set(i.getArgument(1));return null;}).when(repo).actualizarProyeccionCosto(any(),any());
        when(repo.imputaciones(ref.organizacion(),ref.operacion())).thenReturn(List.of(new Imputacion(UUID.randomUUID(),n("4"),null,1)));
        doAnswer(x->{actual.set(operacion(x.getArgument(2),x.getArgument(5)));return null;})
                .when(repo).cerrar(eq(ref.organizacion()),eq(ref.operacion()),anyString(),any(),any(),anyString(),any());
    }
    @Test void identidadEstableIncluyeOrganizacionYOperacion(){
        assertEquals(PresupuestoIaService.claveDeIntento(ref),PresupuestoIaService.claveDeIntento(ref));
        assertNotEquals(PresupuestoIaService.claveDeIntento(ref),PresupuestoIaService.claveDeIntento(new ReferenciaInterna(18,ref.operacion())));
        assertNotEquals(PresupuestoIaService.claveDeIntento(ref),PresupuestoIaService.claveDeIntento(new ReferenciaInterna(17,UUID.randomUUID())));
        assertThrows(ReglaNegocioException.class,()->PresupuestoIaService.claveDeIntento(null));
        assertThrows(ReglaNegocioException.class,()->PresupuestoIaService.claveDeIntento(new ReferenciaInterna(0,ref.operacion())));
    }
    @Test void pruebaPosteriorAlClaimLiberaUnaVezSinInventarMedicion(){
        var c=prueba();assertEquals("CANCELADA",servicio.cancelarNoProcesada(ref,c).estado());
        assertEquals("CANCELADA",servicio.cancelarNoProcesada(ref,c).estado());
        verify(repo,times(1)).liquidarFondo(fondo,n("1"),BigDecimal.ZERO);
        verify(repo,times(1)).liquidarFuente(eq(ref.organizacion()),eq(ref.operacion()),any(),eq(BigDecimal.ZERO.setScale(12)));
        verify(repo,times(1)).antecedente(eq(ref.organizacion()),eq(ref.operacion()),eq("NO_PROCESAMIENTO_COMPROBADO"),contains("referenciaEvidencia"),any());
        verify(repo,never()).medicion(anyLong(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),nullable(String.class));
        verify(repo,never()).iniciar(anyLong(),any(),any());
    }
    @Test void incertidumbreSoloSeResuelveConLaPruebaAunqueNoHayaAccesoOFondoActivo(){
        actual.set(operacion("INCIERTA",null));
        assertEquals("CANCELADA",servicio.cancelarNoProcesada(ref,prueba()).estado());
        verify(repo).liquidarFondo(fondo,n("1"),BigDecimal.ZERO);
    }
    @Test void timeoutYPruebaIncompletaOFuturaNoLiberan(){
        var c=prueba();
        var invalidas=Arrays.asList(null,
                new ComprobacionNoProcesamiento(c.claveIntento(),"",c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),c.comprobadaEn()),
                new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),null,c.referenciaEvidencia(),c.huellaEvidencia(),c.comprobadaEn()),
                new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),c.referenciaIntento(),"",c.huellaEvidencia(),c.comprobadaEn()),
                new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),c.referenciaIntento(),c.referenciaEvidencia(),"no-es-huella",c.comprobadaEn()),
                new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),ahora.plusNanos(1)));
        for(var invalida:invalidas)assertThrows(ReglaNegocioException.class,()->servicio.cancelarNoProcesada(ref,invalida));
        verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void claveAjenaYPruebaAnteriorAlClaimSeRechazan(){
        var c=prueba();
        var ajena=new ComprobacionNoProcesamiento(PresupuestoIaService.claveDeIntento(new ReferenciaInterna(18,ref.operacion())),c.proveedor(),c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),c.comprobadaEn());
        assertThrows(ConflictoException.class,()->servicio.cancelarNoProcesada(ref,ajena));
        var anterior=new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),inicio.minusNanos(1));
        assertThrows(ConflictoException.class,()->servicio.cancelarNoProcesada(ref,anterior));
        verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void estadosSinIntentoOYaLiquidadosNoAdmitenEstaPrueba(){
        for(String estado:List.of("RESERVADA","LIQUIDADA","CANCELADA")){
            actual.set(operacion(estado,"a".repeat(64)));
            assertThrows(ConflictoException.class,()->servicio.cancelarNoProcesada(ref,prueba()));
        }
        verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void cualquierMedicionPreviaImpideAfirmarNoProcesamiento(){
        actual.set(operacion("INCIERTA",null));
        when(repo.idsMediciones(ref.organizacion(),ref.operacion())).thenReturn(Set.of(UUID.randomUUID()));
        assertThrows(ConflictoException.class,()->servicio.cancelarNoProcesada(ref,prueba()));
        verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void repetirConOtraEvidenciaNoSobrescribeLaPrimera(){
        var c=prueba();servicio.cancelarNoProcesada(ref,c);
        var cambiada=new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),c.referenciaIntento(),"OTRA EVIDENCIA",c.huellaEvidencia(),c.comprobadaEn());
        assertThrows(ConflictoException.class,()->servicio.cancelarNoProcesada(ref,cambiada));
        verify(repo,times(1)).liquidarFondo(any(),any(),any());
    }
    private ComprobacionNoProcesamiento prueba(){return new ComprobacionNoProcesamiento(PresupuestoIaService.claveDeIntento(ref),"PROVEEDOR_TEST","INTENTO_TEST","EVIDENCIA_SINTETICA_NO_RED","e".repeat(64),inicio.plusSeconds(30));}
    private Operacion operacion(String estado,String resultado){return new Operacion(ref.organizacion(),ref.operacion(),7L,9L,"PRUEBA","1","RECURSO_TEST","17","PRUEBA","a".repeat(64),"b".repeat(64),estado,n("4"),n("1"),fondo,inicio.minusSeconds(1),resultado,tecnica,null,null,null,null);}
    private static BigDecimal n(String valor){return new BigDecimal(valor);}
}
