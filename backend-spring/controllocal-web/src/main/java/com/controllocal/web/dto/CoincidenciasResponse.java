package com.controllocal.web.dto;

import com.controllocal.service.CoincidenciaService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record CoincidenciasResponse(String estado,CoincidenciaService.Seleccion seleccion,Instant corte,
        String versionRegla,String referenciaConsulta,List<GrupoResponse> grupos,Long total,
        String cobertura,Set<String> capasNoDisponibles,String continuacion) {
    public record GrupoResponse(String referenciaDemanda,long versionDemanda,List<CoincidenciaResponse> candidatos) { }
    public static CoincidenciasResponse desde(CoincidenciaService.Coincidencias c) {
        return new CoincidenciasResponse(c.estado().name(),c.seleccion(),c.corte(),c.versionRegla(),c.referenciaConsulta(),
                c.grupos().stream().map(g->new GrupoResponse(g.referenciaDemanda(),g.versionDemanda(),
                        g.candidatos().stream().map(CoincidenciaResponse::desde).toList())).toList(),c.total(),c.cobertura().name(),
                c.capasNoDisponibles().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),c.continuacion());
    }
}