package com.controllocal.service.colaboracion;

import com.controllocal.service.Actor;
import java.util.Set;

/** Puerto de la fuente canónica de oferta para validar un subset ya concedido, sin entregar atributos. */
public interface FuenteOfertaConcedida {
    record Verificacion(long organizacion,long idEncargo,long responsableActual,long versionActual,
                        String huellaSubset,boolean elegible,Set<String> causas) { }
    Verificacion verificarCamposColaboracion(ReferenciaParteConcedida referencia,Actor receptorReal);
}
