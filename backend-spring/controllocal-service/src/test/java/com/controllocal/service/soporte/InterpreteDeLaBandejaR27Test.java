package com.controllocal.service.soporte;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.ProspeccionService.RevisionSeguimiento;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.actividad.SeguimientoDeProspeccion.Necesidad;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterpreteDeLaBandejaR27Test {
    @Test void avisoDeTemporalesNoConsultaNiInventaUnExpedienteComercial(){
        var a=new InterpreteDeLaBandeja.AsuntoADescribir("SEGUIMIENTO","FOTO_PROPIEDAD",7L,
                "Revisa las versiones. Vence el 08/11/2026 a las 10:00 (Lima).",0,LocalDate.of(2026,11,8),true,"a".repeat(64));
        var c=interprete.contextoDe(actor,List.of(a));var r=interprete.de(a,c,hoy);
        assertTrue(r.expediente().isEmpty());assertEquals(a.descripcion(),r.comoEsta().hechos().getFirst().texto());
        assertFalse(r.lectura().contains("firmar"));assertFalse(r.lectura().contains("comisión"));
        verifyNoInteractions(captaciones,precios,contrastes,seguimiento);
    }
    private final CaptacionRepository captaciones=mock(CaptacionRepository.class);
    private final PrecioPropiedadRepository precios=mock(PrecioPropiedadRepository.class);
    private final ContrasteRepository contrastes=mock(ContrasteRepository.class);
    private final SeguimientoDeProspeccion seguimiento=mock(SeguimientoDeProspeccion.class);
    private final InterpreteDeLaBandeja interprete=new InterpreteDeLaBandeja(captaciones,precios,contrastes,seguimiento);
    private final Actor actor=new Actor(1,2,30,"AGENTE");
    private final LocalDate hoy=LocalDate.of(2026,10,30);

    @Test void dosDestinatariosDelMismoRecursoNoIntercambianFechasNiCausas() {
        var a=revision(101,hoy.minusDays(2),hoy,true);var b=revision(102,hoy.minusDays(9),hoy.plusDays(4),false);
        when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(new Necesidad(a,"a",30),new Necesidad(b,"b",30)));
        var asunto=asunto("a",11L);var contexto=interprete.contextoDe(actor,List.of(asunto));
        assertEquals(Set.of("a"),contexto.seguimientos().keySet());
        var resultado=interprete.de(asunto,contexto,hoy);
        assertEquals(4,resultado.expediente().size());
        assertTrue(resultado.expediente().get(1).valor().contains("28 de octubre"));
        assertTrue(resultado.comoEsta().hechos().stream().anyMatch(h->h.texto().contains("continuar, pausar o cerrar")));
        assertFalse(resultado.comoEsta().hechos().stream().anyMatch(h->h.texto().contains("Falta volver a contactar")));
        verifyNoInteractions(precios,contrastes);
    }
    @Test void sinFechaNoSeInventaContactoNiVencimiento() {
        var r=revision(101,null,null,false);
        when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(new Necesidad(r,"a",30)));
        var asunto=asunto("a",11L);var resultado=interprete.de(asunto,interprete.contextoDe(actor,List.of(asunto)),hoy);
        assertEquals("Sin último intento fechado en este seguimiento",resultado.expediente().get(1).valor());
        assertEquals("Sin próxima fecha declarada",resultado.expediente().get(2).valor());
    }
    @Test void laPosposicionConservaSuFechaDeclarada() {
        var r=new RevisionSeguimiento(101,2,11,"Propietario o representante declarado","CADENCIA",hoy.minusDays(2),1,hoy,"CADENCIA",hoy.plusDays(5),"Viaje",false,false,false,null);
        when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(new Necesidad(r,"a",30)));
        var asunto=asunto("a",11L);var resultado=interprete.de(asunto,interprete.contextoDe(actor,List.of(asunto)),hoy);
        assertTrue(resultado.expediente().get(2).valor().contains("4 de noviembre"));
    }
    @Test void unaMarcaNoLegibleNoDaUnExpedienteVacioEnganoso() {
        var asunto=asunto("no-autorizada",11L);
        assertThrows(AccesoNoAutorizadoException.class,()->interprete.de(asunto,interprete.contextoDe(actor,List.of(asunto)),hoy));
    }
    @Test void unaMarcaDeOtroRecursoNoSeAtribuyeAlAsunto() {
        when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(new Necesidad(revision(101,null,null,false),"a",30)));
        var asunto=asunto("a",99L);
        assertThrows(IllegalStateException.class,()->interprete.de(asunto,interprete.contextoDe(actor,List.of(asunto)),hoy));
    }
    @Test void unFalloDeLaFuenteNoSePresentaComoAusenciaDeSeguimiento() {
        when(seguimiento.necesidadesActuales(actor)).thenThrow(new IllegalStateException("fuente no disponible"));
        assertThrows(IllegalStateException.class,()->interprete.contextoDe(actor,List.of(asunto("a",11L))));
    }
    private static RevisionSeguimiento revision(long id,LocalDate ultimo,LocalDate proxima,boolean revisar) {
        return new RevisionSeguimiento(id,2,11,"Propietario o representante declarado",revisar?"REVISAR_CONTINUIDAD":"CADENCIA",ultimo,revisar?3:1,proxima,"CADENCIA",null,null,revisar,false,false,null);
    }
    private static InterpreteDeLaBandeja.AsuntoADescribir asunto(String marca,Long id) {
        return new InterpreteDeLaBandeja.AsuntoADescribir("RECONTACTO","PROSPECCION",id,"Revisa el seguimiento",null,null,true,marca);
    }
}
