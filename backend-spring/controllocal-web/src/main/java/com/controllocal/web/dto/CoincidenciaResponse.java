package com.controllocal.web.dto;

import com.controllocal.service.CoincidenciaService;
import java.util.List;
import java.util.Map;

/** Proyección permitida por Core; conserva el H original y la alternativa descriptiva cuando existe. */
public record CoincidenciaResponse(String referencia,long version,String capa,String categoria,
        short g,short h,long s,long u,long w,List<RazonResponse> razones,Map<String,Object> datosPermitidos,String coberturaCriterios,
        CoincidenciaService.AlternativaPrecio alternativaPrecio) {
    public record RazonResponse(String referenciaCriterio,String clave,String exigencia,int peso,
                                short resultado,String causa) { }
    public static CoincidenciaResponse desde(CoincidenciaService.Coincidencia c) {
        return new CoincidenciaResponse(c.referencia(),c.version(),c.capa().name(),c.categoria().name(),
                c.g().codigo(),c.h().codigo(),c.s(),c.u(),c.w(),c.razones().stream()
                .map(r->new RazonResponse(r.referenciaCriterio(),r.clave(),r.exigencia().name(),r.peso(),
                        r.conclusion().codigo(),r.causa()==null?null:r.causa().name())).toList(),c.datosPermitidos(),c.coberturaCriterios().name(),c.alternativaPrecio());
    }
}
