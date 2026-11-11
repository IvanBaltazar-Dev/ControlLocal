package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactosDeInteraccionTest {
    private final Actor actor=new Actor(1,101,201,Actor.AGENTE);
    private final InteresadosDeDemanda interesados=mock(InteresadosDeDemanda.class);
    private final PropietarioService propietarios=mock(PropietarioService.class);
    private final PersonaRepository personas=mock(PersonaRepository.class);
    private final AutoriaContactoContextualRepository autoria=mock(AutoriaContactoContextualRepository.class);
    private final OportunidadComercialRepository operadores=mock(OportunidadComercialRepository.class);
    private final Alcances alcances=mock(Alcances.class);
    private final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    private final ContactosDeInteraccion servicio=new ContactosDeInteraccion(interesados,propietarios,personas,autoria,operadores,alcances,contexto,new Documentos(new ObjectMapper()));
    private final ReferenciaContactoContextual ref=new ReferenciaContactoContextual(TipoContacto.PERSONA,99L,null,null);
    @Test void poseerElIdYTenantNoConcedeAccesoAlContacto(){
        when(autoria.origenes(1,99)).thenReturn(List.of());
        assertThrows(NoEncontradoException.class,()->servicio.comprobar(ref,null,actor));verifyNoInteractions(personas);
    }
    @Test void unOrigenSinPertenenciaActualNoDaPermisoPerpetuo(){
        prepararOrigen();when(operadores.contextoOperativoActual(1,101,201,"AGENTE",LocalDate.of(2026,10,30))).thenReturn(false);
        assertThrows(NoEncontradoException.class,()->servicio.comprobar(ref,99L,actor));verifyNoInteractions(personas);
    }
    @Test void retirarLaSupervisionImpideConsultarAunqueElOrigenSigaVigente(){
        prepararOrigen();when(alcances.alcanza(actor,201L)).thenReturn(false);
        assertThrows(NoEncontradoException.class,()->servicio.comprobar(ref,99L,actor));verifyNoInteractions(operadores,personas);
    }
    @Test void elContactoOriginalSeComparaSinHidratarUnaPersonaDistinta(){
        prepararOrigen();when(operadores.contextoOperativoActual(1,101,201,"AGENTE",LocalDate.of(2026,10,30))).thenReturn(true);
        assertEquals(99L,servicio.comprobar(ref,99L,actor));
        assertThrows(NoEncontradoException.class,()->servicio.comprobar(ref,100L,actor));verifyNoInteractions(personas);
    }
    @Test void elErrorTecnicoDeOrigenNoSeConvierteEnContactoAusente(){
        when(autoria.origenes(1,99)).thenThrow(new IllegalStateException("Fallo de consulta"));
        assertThrows(IllegalStateException.class,()->servicio.comprobar(ref,null,actor));
    }
    private void prepararOrigen(){
        when(contexto.hoy()).thenReturn(LocalDate.of(2026,10,30));
        when(autoria.origenes(1,99)).thenReturn(List.of(new AutoriaContactoContextualRepository.Origen(99,201,101)));
        when(alcances.alcanza(actor,201L)).thenReturn(true);
    }
}
