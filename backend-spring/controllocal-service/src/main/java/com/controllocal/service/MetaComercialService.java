package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.time.*;
import java.util.*;

/** RF074–077/CAT-E30 y catálogo20.10. Proponer no decide; C/P/S/F sólo son historia. */
public interface MetaComercialService {
    record Categoria(String codigo,String rotulo,String unidad,List<String> operaciones) { }
    record Definicion(String version,List<Categoria> categorias,String historia) { }
    Definicion definicion(Actor actor);
    boolean puedeDecidir(Actor actor);
    record CapacidadMeta(long id,long version,long responsable,String estado,Long reemplazadaPor,boolean puedeDecidir) { }
    CapacidadMeta capacidadSobrePropuesta(long propuesta,Actor actor);
    record Asignacion(Long idRolAgente,String kpi,String operacion,Long versionEsperada,Integer valor,
                      List<LocalDate> calendario,String motivo) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Campo de meta no admitido.");}
    }
    record Establecer(String mes,List<Asignacion> asignaciones) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Campo de decisión de metas no admitido.");}
    }
    record Propuesta(String kpi,String operacion,Long versionMetaEsperada,Long propuestaAnterior,Long versionPropuestaAnterior,
                     Integer valor,List<LocalDate> calendario,String motivo) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Campo de propuesta de meta no admitido.");}
    }
    enum TipoDecision { APROBAR,OBSERVAR,RECHAZAR }
    record Decision(Long propuesta,Long versionEsperada,TipoDecision decision,Integer valorAprobado,String motivo) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new ReglaNegocioException("Campo de decisión no admitido.");}
    }
    record Revision(long id,Long version,String origen,String estado,Integer valorAnterior,int valorPropuesto,
                    String motivo,long autor,Instant fecha,Long decisor,String motivoDecision,Instant fechaDecision,
                    Long reemplazadaPor,Long versionMetaEsperada,List<LocalDate> calendario,Integer valorAprobado) { }
    record PropuestaMeta(long idRevision,Long version,long idRolAgente,String nombreAgente,String kpi,String operacion,
                              String rotuloKpi,Integer valorVigente,int valorPropuesto,Long versionMetaEsperada,
                              List<LocalDate> calendario,String motivo,Instant fecha,String definicion,Long versionMetaActual,Revision constancia) { }
    record MetaDeAgente(long idRolAgente,String nombreAgente,String kpi,String operacion,String rotuloKpi,String unidad,
                        String definicion,Long idMeta,Long version,Integer valor,List<LocalDate> calendario,
                        boolean historica,PropuestaMeta propuesta,List<Revision> historial) {
        public MetaDeAgente {calendario=calendario==null?null:List.copyOf(calendario);historial=List.copyOf(historial);}
    }
    record Resultado(List<MetaDeAgente> metas) {public Resultado{metas=List.copyOf(metas);}}
    List<MetaDeAgente> del(String mes,Actor actor);
    List<PropuestaMeta> propuestasPendientes(Actor actor);
    PropuestaMeta consultarPropuesta(long id,Actor actor);
    Establecer validarEstablecimiento(Establecer d,Actor actor);
    void exigirLecturaEstablecimiento(Establecer d,Actor actor);
    Decision validarDecision(Decision d,Actor actor);
    Resultado fijar(Establecer datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen);
    Resultado proponer(String mes,Propuesta datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen);
    Resultado resolver(Decision datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen);
    Optional<Resultado> resultado(String capacidad,String clave,Actor actor);
}
