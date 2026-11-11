package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class IdentidadComandoAutomaticoTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final Documentos documentos=new Documentos(json);
    private final IdentidadComandoAutomatico identidad=new IdentidadComandoAutomatico(documentos,json);
    private final Actor actor=new Actor(1,2,3,Actor.AGENTE);
    private final ComandosAutomaticosService.CorrelacionAutomatico correlacion=
            new ComandosAutomaticosService.CorrelacionAutomatico("conversación","turno","mensaje");
    @Test void conservaExactamenteLaDerivacionAnterior(){
        Map<String,Object> a=json.convertValue(actor,new TypeReference<LinkedHashMap<String,Object>>(){});
        Map<String,Object> c=json.convertValue(correlacion,new TypeReference<LinkedHashMap<String,Object>>(){});
        String anterior=Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("contrato",1,
                "capacidad","registrar_interaccion","actor",a,"correlacion",c)));
        assertEquals(anterior,identidad.clavePara("registrar_interaccion",actor,correlacion));
        assertEquals(anterior,new IdentidadComandoAutomatico(documentos,json).clavePara("registrar_interaccion",actor,correlacion));
    }
    @Test void actorAmbitoAtribucionYOperacionConservanIdentidadesDistintas(){
        String inicial=identidad.clavePara("crear_borrador_anuncio",actor,correlacion);
        assertEquals(64,inicial.length());
        for(Actor otro:new Actor[]{new Actor(9,2,3,Actor.AGENTE),new Actor(1,9,3,Actor.AGENTE),
                new Actor(1,2,9,Actor.AGENTE),new Actor(1,2,3,Actor.BROKER)})
            assertNotEquals(inicial,identidad.clavePara("crear_borrador_anuncio",otro,correlacion));
        assertNotEquals(inicial,identidad.clavePara("editar_borrador_anuncio",actor,correlacion));
    }
    @Test void correlacionNoSeRecortaNiColapsaNuloConSuLiteral(){
        String inicial=identidad.clavePara("crear_borrador_anuncio",actor,correlacion);
        assertNotEquals(inicial,identidad.clavePara("crear_borrador_anuncio",actor,
                new ComandosAutomaticosService.CorrelacionAutomatico("conversación","otro","mensaje")));
        assertNotEquals(identidad.clavePara("crear_borrador_anuncio",actor,
                new ComandosAutomaticosService.CorrelacionAutomatico("c","t",null)),
                identidad.clavePara("crear_borrador_anuncio",actor,new ComandosAutomaticosService.CorrelacionAutomatico("c","t","null")));
    }
}
