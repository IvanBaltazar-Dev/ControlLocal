package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository.Recurso;
import com.controllocal.service.Actor;
import com.controllocal.service.soporte.*;
import com.controllocal.service.soporte.FuentesAsuntos.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Filas/metadata TEST declaradas. No acredita autoridad canónica, SQL ni concurrencia real. */
final class MontajeAsuntosC7 {
    private MontajeAsuntosC7(){ }
    static void tareas(TareaRepository repo,List<Tarea> tabla,DetalleAgente agente,Actor actor,
                       SolicitudAlquilerRepository solicitudes,FuentesAsuntos fuentes,AutoridadAsuntos autoridad){
        fuentesDeclaradas(fuentes,autoridad,actor);
        when(fuentes.solicitudes(anyCollection(),eq(actor))).thenAnswer(i->{
            Collection<Long> ids=i.getArgument(0);var estados=new HashMap<Long,String>();
            for(String estado:List.of(SolicitudAlquiler.APROBADA,SolicitudAlquiler.OBSERVADA))
                for(var s:solicitudes.porEstadoDelAgente(actor.idOrganizacion(),actor.idRolOperativo(),estado))estados.put(s.getEntidadId(),estado);
            var resultado=new HashMap<Long,Causa>();
            for(long id:ids){String estado=estados.get(id);if(estado!=null)resultado.put(id,solicitud(id,estado,actor.idRolOperativo()));}
            return Map.copyOf(resultado);
        });
        filasTarea(repo,tabla,agente,actor);
    }
    /** Respuestas declaradas de puertos TEST; no demuestra permiso, vigencia ni serialización SQL. */
    static void fuentesDeclaradas(FuentesAsuntos fuentes,AutoridadAsuntos autoridad,Actor actor){
        var json=new Documentos(JsonMapper.builder().findAndAddModules().build());
        when(autoridad.legibles(anyCollection(),eq(actor))).thenAnswer(i->Set.copyOf(i.<Collection<AutoridadAsuntos.Consulta>>getArgument(0)));
        when(fuentes.guardar(any())).thenAnswer(i->json.objetoCanonico(i.getArgument(0)));
        when(fuentes.leer(anyString())).thenAnswer(i->json.comoObjeto(i.getArgument(0),Causa.class));
        when(fuentes.huella(eq(actor.idOrganizacion()),any())).thenAnswer(i->{Causa c=i.getArgument(1);
            return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("organizacion",actor.idOrganizacion(),"recurso",c.recurso(),"hecho",c.hecho(),"finalidad",c.finalidad())));});
        when(fuentes.vigentes(anyCollection(),eq(actor))).thenAnswer(i->Set.copyOf(i.<Collection<Causa>>getArgument(0)));
    }
    private static void filasTarea(TareaRepository repo,List<Tarea> tabla,DetalleAgente agente,Actor actor){
        when(repo.cabecerasPersonales(actor.idOrganizacion(),actor.idRolOperativo())).thenAnswer(i->tabla.stream()
                .filter(t->t.getAgente()!=null&&t.getAgente().getId()==actor.idRolOperativo()&&Objects.equals(t.getOrganizacionId(),actor.idOrganizacion()))
                .map(MontajeAsuntosC7::cabecera).toList());
        when(repo.cabeceraPersonal(eq(actor.idOrganizacion()),eq(actor.idRolOperativo()),anyLong())).thenAnswer(i->{
            Long id=i.getArgument(2);return tabla.stream().filter(t->Objects.equals(id,t.getId())&&Objects.equals(t.getOrganizacionId(),actor.idOrganizacion())
                    &&t.getAgente()!=null&&t.getAgente().getId()==actor.idRolOperativo()).map(MontajeAsuntosC7::cabecera).findFirst();});
        when(repo.personalesAutorizadas(eq(actor.idOrganizacion()),eq(actor.idRolOperativo()),anyCollection())).thenAnswer(i->{
            Collection<Long> ids=i.getArgument(2);return tabla.stream().filter(t->ids.contains(t.getId())).toList();});
        when(repo.antecedenteC7(eq(actor.idOrganizacion()),anyString(),anyLong())).thenAnswer(i->{
            String entidad=i.getArgument(1);Long id=i.getArgument(2);return tabla.stream()
                    .filter(t->t.getHuellaCausaC7()!=null&&entidad.equals(t.getEntidadTipo())&&id.equals(t.getEntidadId()))
                    .map(Tarea::getId).max(Long::compare);});
        when(repo.crearCausaC7(eq(actor.idOrganizacion()),anyString(),anyString(),anyLong(),eq(actor.idRolOperativo()),anyString(),anyString(),anyString(),anyString(),any()))
                .thenAnswer(i->{String huella=i.getArgument(7);if(tabla.stream().anyMatch(t->huella.equals(t.getHuellaCausaC7())))return 0;
                    var t=new Tarea();t.setOrganizacionId(actor.idOrganizacion());t.setId(100L+tabla.size());t.setTipo(i.getArgument(1));t.setEntidadTipo(i.getArgument(2));
                    t.setEntidadId(i.getArgument(3));t.setAgente(agente);t.setDescripcion(i.getArgument(5));t.setFechaProgramada(OffsetDateTime.parse("2026-11-04T14:00:00Z"));
                    t.nacer(i.getArgument(6));t.setHuellaCausaC7(huella);t.setFuenteCausaC7(i.getArgument(8));t.setIdAntecedente(i.getArgument(9));
                    t.setEstadoCausaC7("VIGENTE");tabla.add(t);return 1;});
    }
    static Causa solicitud(long id,String estado,long responsable){
        // Dos transiciones distintas declaradas por este montaje, nunca un timestamp como identidad.
        long historial=100000+2*id+(SolicitudAlquiler.APROBADA.equals(estado)?1:0);
        return new Causa(1,new Recurso("SOLICITUD_ALQUILER",id),"SEGUIMIENTO_SOLICITUD",
                new HechoAsunto("HISTORIAL_ESTADO",historial,null),Origen.de("ESTADO_SOLICITUD",id),
                Instant.parse("2026-11-04T14:00:00Z"),null,responsable,estado);
    }
    private record Cabecera(Long id,String entidadTipo,Long entidadId,String fuenteCausa,String huellaCausa) implements TareaRepository.CabeceraC7{
        public Long getId(){return id;}public String getEntidadTipo(){return entidadTipo;}public Long getEntidadId(){return entidadId;}
        public String getFuenteCausa(){return fuenteCausa;}public String getHuellaCausa(){return huellaCausa;}
    }
    private static TareaRepository.CabeceraC7 cabecera(Tarea t){return new Cabecera(t.getId(),t.getEntidadTipo(),t.getEntidadId(),t.getFuenteCausaC7(),t.getHuellaCausaC7());}
}
