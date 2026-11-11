package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Derivación de identidad compartida; no admite una actuación, escribe o ejecuta un efecto. */
@Component
public class IdentidadComandoAutomatico {
    private final Documentos documentos;private final ObjectMapper json;
    public IdentidadComandoAutomatico(Documentos documentos,ObjectMapper json){this.documentos=documentos;this.json=json;}
    public String clavePara(String capacidad,Actor actor,CorrelacionAutomatico correlacion){
        Objects.requireNonNull(capacidad,"La capacidad procede del contrato Core.");
        Objects.requireNonNull(actor,"El actor lo resuelve la sesión Core.");
        Objects.requireNonNull(correlacion,"Se requiere la correlación original.");
        return Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("contrato",ComandosAutomaticosService.VERSION,
                "capacidad",capacidad,"actor",mapa(actor),"correlacion",mapa(correlacion))));
    }
    private Map<String,Object> mapa(Object o){return json.convertValue(o,new TypeReference<LinkedHashMap<String,Object>>(){});}
}
