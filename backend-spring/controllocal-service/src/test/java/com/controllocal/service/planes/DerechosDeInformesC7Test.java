package com.controllocal.service.planes;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.domain.planes.PlanBrox;
import com.controllocal.service.Actor;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.planes.DerechosDeInformesC7.Composicion.*;
class DerechosDeInformesC7Test {
    final PresupuestoIaRepository repo=mock(PresupuestoIaRepository.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final DerechosDeInformesC7 servicio=new DerechosDeInformesC7(repo,contexto);
    final Actor a=new Actor(1,2,3,"AGENTE");final Instant ahora=Instant.parse("2026-11-10T15:00:00Z");
    void plan(PlanBrox p){when(repo.organizacionActual(1)).thenReturn(Optional.of(new PresupuestoIaRepository.Organizacion(p.empresarial()?"EMPRESARIAL":"INDIVIDUAL",true)));when(repo.cicloVigente(1,ahora)).thenReturn(Optional.of(new PresupuestoIaRepository.Ciclo(UUID.randomUUID(),p.name(),ahora.minusSeconds(10),ahora.plusSeconds(100),false,p.precioUsd(),10,0,1)));}
    @Test void freeConservaOperacionYNoConcedePreparacionSuperior(){when(repo.organizacionActual(1)).thenReturn(Optional.of(new PresupuestoIaRepository.Organizacion("INDIVIDUAL",true)));when(repo.cicloVigente(1,ahora)).thenReturn(Optional.empty());assertEquals(Set.of(OPERACION_ACTUAL),servicio.actuales(a,ahora).composiciones());assertThrows(CapacidadDeshabilitadaException.class,()->servicio.exigir(EVOLUCION_MEJORA,a,ahora));}
    @Test void oneNoConcedeProYOneProSi(){plan(PlanBrox.ONE);assertFalse(servicio.actuales(a,ahora).programasPersonales());assertEquals(Set.of(OPERACION_ACTUAL,RESUMEN_MENSUAL),servicio.actuales(a,ahora).composiciones());plan(PlanBrox.ONE_PRO);assertTrue(servicio.exigirPro(a,ahora).programasPersonales());assertTrue(servicio.actuales(a,ahora).composiciones().contains(EVOLUCION_MEJORA));}
    @Test void empresarialesNoCompranProgramasPersonalesNiOtrasCarteras(){for(var p:List.of(PlanBrox.TEAM,PlanBrox.GROWTH,PlanBrox.SCALE,PlanBrox.PRIME)){plan(p);assertFalse(servicio.actuales(a,ahora).programasPersonales());assertThrows(CapacidadDeshabilitadaException.class,()->servicio.exigirPro(a,ahora));}plan(PlanBrox.TEAM);assertThrows(CapacidadDeshabilitadaException.class,()->servicio.exigir(SEGUIMIENTO_RESPONSABLES,a,ahora));plan(PlanBrox.GROWTH);servicio.exigir(SEGUIMIENTO_RESPONSABLES,a,ahora);assertThrows(CapacidadDeshabilitadaException.class,()->servicio.exigir(RITMO_METAS,a,ahora));plan(PlanBrox.SCALE);servicio.exigir(RITMO_METAS,a,ahora);assertThrows(CapacidadDeshabilitadaException.class,()->servicio.exigir(DIAGNOSTICO_RESPONSABLE,a,ahora));plan(PlanBrox.PRIME);servicio.exigir(DIAGNOSTICO_RESPONSABLE,a,ahora);}
    @Test void revocacionNoConservaDerechosPagados(){plan(PlanBrox.ONE_PRO);var c=repo.cicloVigente(1,ahora).orElseThrow();when(repo.cicloVigente(1,ahora)).thenReturn(Optional.of(new PresupuestoIaRepository.Ciclo(c.id(),c.plan(),c.desde(),c.hasta(),true,c.financiado(),10,0,2)));assertThrows(CapacidadDeshabilitadaException.class,()->servicio.exigirPro(a,ahora));}
    @Test void perdidaDeContextoSeDeniegaAntesDeConsultarElPlan(){doThrow(new AccesoNoAutorizadoException()).when(contexto).exigirOperador(a);assertThrows(AccesoNoAutorizadoException.class,()->servicio.actuales(a,ahora));verifyNoInteractions(repo);}
    @Test void noAceptaPlanConOtraModalidad(){plan(PlanBrox.ONE_PRO);when(repo.organizacionActual(1)).thenReturn(Optional.of(new PresupuestoIaRepository.Organizacion("EMPRESARIAL",true)));assertThrows(IllegalStateException.class,()->servicio.actuales(a,ahora));}
}
