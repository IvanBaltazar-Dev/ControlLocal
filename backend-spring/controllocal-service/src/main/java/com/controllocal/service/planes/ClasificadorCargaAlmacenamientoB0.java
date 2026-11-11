package com.controllocal.service.planes;

import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;

/** El adaptador Core comprueba recurso/finalidad y deriva categoría/necesidad de hechos actuales. */
public interface ClasificadorCargaAlmacenamientoB0 {
    enum Necesidad { ORDINARIA,EVIDENCIA_OPERACION_ABIERTA,CIERRE,RECTIFICACION }
    record Clasificacion(CategoriaAlmacenamiento categoria,Necesidad necesidad,String huellaFuente){}
    boolean atiende(UsoAlmacenamientoB0.Recurso recurso);
    Clasificacion clasificar(UsoAlmacenamientoB0.Solicitud solicitud,Actor actor);
    /** La intención todavía no tiene salida/hash/QC. El propietario verifica su job y snapshot. */
    default Clasificacion clasificarPrevision(UsoAlmacenamientoB0.PrevisionIa solicitud,Actor actor){
        throw new CapacidadDeshabilitadaException();
    }
    /** Sólo un resultado ya causado con recepción/claim durables; nunca una admisión humana alternativa. */
    default Clasificacion clasificarRecepcion(UsoAlmacenamientoB0.Solicitud solicitud,UsoAlmacenamientoB0.RecepcionComprobada prueba){
        throw new CapacidadDeshabilitadaException();
    }
    /** Intención durable de retiro vigente; no adquiere O/J después del ledger. */
    default void exigirRetiro(UsoAlmacenamientoB0.Recurso recurso,UsoAlmacenamientoB0.AusenciaComprobada prueba){
        throw new CapacidadDeshabilitadaException();
    }
    /** Ausencia de entrada al escritor comprobada, independiente del consumo de inferencia. */
    default void exigirNoAlmacenado(UsoAlmacenamientoB0.Recurso recurso,UsoAlmacenamientoB0.NoAlmacenadoComprobado prueba){
        throw new CapacidadDeshabilitadaException();
    }
}
