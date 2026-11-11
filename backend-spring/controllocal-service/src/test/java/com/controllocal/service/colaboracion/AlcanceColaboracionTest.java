package com.controllocal.service.colaboracion;

import com.controllocal.domain.colaboracion.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlcanceColaboracionTest {
    private static final Instant DESDE=Instant.parse("2026-10-28T00:00:00Z"),HASTA=DESDE.plusSeconds(86400);
    private AlcanceColaboracion alcance(Set<String> acciones,Set<String> campos){return new AlcanceColaboracion(
            Set.of(LadoColaboracion.DEMANDA),acciones,Map.of(LadoColaboracion.ENCARGO,campos),"RED_COLABORATIVA",DESDE,HASTA);}
    @Test void limitarNoPuedeSumarCamposYElComunNuncaLosRecupera() {
        var amplio=alcance(Set.of("CONSULTAR","DESCARGAR"),Set.of("tipo","precioPedido"));
        var limitado=alcance(Set.of("CONSULTAR"),Set.of("tipo"));
        assertTrue(amplio.contiene(limitado));assertFalse(limitado.contiene(amplio));
        assertEquals(limitado,amplio.interseccion(limitado).orElseThrow());
    }
    @Test void accionesDisjuntasNoConcedenNadaAunqueCoincidaElRecurso() {
        assertTrue(alcance(Set.of("CONSULTAR"),Set.of("tipo")).interseccion(alcance(Set.of("DESCARGAR"),Set.of("tipo"))).isEmpty());
    }
    @Test void finalidadYVigenciaNoSeCombinanParaInventarUnPermiso() {
        var a=alcance(Set.of("CONSULTAR"),Set.of("tipo"));
        var otro=new AlcanceColaboracion(a.destinatarios(),a.acciones(),a.campos(),"OTRA_FINALIDAD",DESDE,HASTA);
        assertTrue(a.interseccion(otro).isEmpty());assertFalse(a.contiene(otro));
        assertTrue(a.vigente(DESDE));assertFalse(a.vigente(HASTA));
    }
}
