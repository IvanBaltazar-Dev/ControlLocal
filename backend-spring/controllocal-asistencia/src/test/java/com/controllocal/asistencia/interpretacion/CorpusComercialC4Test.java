package com.controllocal.asistencia.interpretacion;
import com.controllocal.asistencia.brox.*;
import com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Corpus acumulado C4–C6: transporte explícito, no validación comercial de los DTOs en Core. */
class CorpusComercialC4Test {
    @Test void gestosComercialesTienenGoldPropioSinInferirDatosAdicionales() throws Exception {
        var json=new ObjectMapper();var brox=mock(ClienteBrox.class);var i=new InterpreteDeterminista(brox);
        var sid=new SesionBrox(1,2,3,"AGENTE","sesion",Long.MAX_VALUE);
        var ids=new HashSet<String>();var gestos=new HashSet<OperacionComercial>();
        for(var archivo:List.of("/evaluacion/tareas-c4-es-v1.json","/evaluacion/tareas-c5-es-v1.json","/evaluacion/tareas-c6-es-v1.json")) {
          try(var recurso=getClass().getResourceAsStream(archivo)) {
            assertNotNull(recurso);var casos=json.readTree(recurso).path("casos");assertEquals(archivo.contains("c4-")?17:archivo.contains("c5-")?10:19,casos.size());
            for(var c:casos) {
                assertTrue(ids.add(c.path("id").asText()));var op=OperacionComercial.valueOf(c.path("accion").asText());gestos.add(op);
                var l=i.leer(c.path("texto").asText(),sid,new Traza("API","KAIROS","TEST","1","c",c.path("id").asText(),"m",null,null));
                assertEquals(op.name(),l.accion().name());assertTrue(l.noEntendido().isEmpty());
                var actual=json.readTree(l.datos().get("datosComerciales"));assertEquals(c.path("datos"),actual,c.path("id").asText());
            }
          }
        }
            var base=EnumSet.allOf(OperacionComercial.class);
            base.removeAll(EnumSet.of(OperacionComercial.CONSULTAR_SEGUIMIENTOS_PROSPECCION,
                    OperacionComercial.PLANIFICAR_SEGUIMIENTO_PROSPECCION,OperacionComercial.POSPONER_SEGUIMIENTO_PROSPECCION));
            assertEquals(base,gestos);verifyNoInteractions(brox);
    }
}
