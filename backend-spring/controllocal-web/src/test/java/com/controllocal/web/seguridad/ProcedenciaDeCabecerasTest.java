package com.controllocal.web.seguridad;

import com.controllocal.service.soporte.Procedencia;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcedenciaDeCabecerasTest {

    @Test
    void puenteaLaHerramientaDelAgenteAlRastro() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ProcedenciaDeCabeceras.CANAL)).thenReturn("WHATSAPP");
        when(request.getHeader(ProcedenciaDeCabeceras.AGENTE)).thenReturn("kairos");
        when(request.getHeader(ProcedenciaDeCabeceras.AGENTE_MODELO)).thenReturn("modelo");
        when(request.getHeader(ProcedenciaDeCabeceras.AGENTE_VERSION)).thenReturn("v1");
        when(request.getHeader(ProcedenciaDeCabeceras.CONVERSACION)).thenReturn("conv-1");
        when(request.getHeader(ProcedenciaDeCabeceras.TURNO)).thenReturn("turno-1");
        when(request.getHeader(ProcedenciaDeCabeceras.HERRAMIENTA)).thenReturn("captura.avanzar");

        Procedencia procedencia = new ProcedenciaDeCabeceras().de(request);

        assertEquals("WHATSAPP", procedencia.canal());
        assertEquals("kairos", procedencia.agente());
        assertEquals("captura.avanzar", procedencia.herramienta());
    }

    @Test
    void conservaLaHerramientaAunqueLaPeticionSeaDeUnaPersona() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ProcedenciaDeCabeceras.CANAL)).thenReturn("API");
        when(request.getHeader(ProcedenciaDeCabeceras.HERRAMIENTA)).thenReturn("propiedad.registrar");

        Procedencia procedencia = new ProcedenciaDeCabeceras().de(request);

        assertEquals("API", procedencia.canal());
        assertEquals("propiedad.registrar", procedencia.herramienta());
    }
}
