package com.controllocal.service.archivo;

import java.util.UUID;

/** Dueño de binarios, distinto del trabajo IA y de la autorización para leerlos. */
public sealed interface PropietarioBinario permits PropietarioBinario.Organizacion,PropietarioBinario.CasoEvaluacion {
    record Organizacion(long id) implements PropietarioBinario {
        public Organizacion {if(id<=0)throw new IllegalArgumentException("Organización real requerida.");}
    }
    record CasoEvaluacion(UUID id) implements PropietarioBinario {
        public CasoEvaluacion {if(id==null)throw new IllegalArgumentException("Caso real requerido.");}
    }
    static PropietarioBinario de(Long organizacion,UUID caso){
        if((organizacion==null)==(caso==null))throw new IllegalArgumentException("Dueño binario ambiguo.");
        return organizacion!=null?new Organizacion(organizacion):new CasoEvaluacion(caso);
    }
    default Long organizacion(){return this instanceof Organizacion o?o.id():null;}
    default UUID caso(){return this instanceof CasoEvaluacion c?c.id():null;}
    default String identidad(){return this instanceof Organizacion o?"ORG:"+o.id():"EVALUACION:"+((CasoEvaluacion)this).id();}
    default String prefijo(){return this instanceof Organizacion o?"tenant/"+o.id():"plataforma/evaluacion/"+((CasoEvaluacion)this).id();}
}
