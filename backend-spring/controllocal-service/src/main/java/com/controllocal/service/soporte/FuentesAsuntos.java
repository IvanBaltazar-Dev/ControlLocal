package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.HechoAsunto;
import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Identidad y procedencia de la proyección; no calcula cadencia, prioridad ni estado comercial. */
@Component
public class FuentesAsuntos {
    public record Origen(String selector,String referencia){
        public Origen {if(selector==null||selector.isBlank()||referencia==null||referencia.isBlank())throw new IllegalArgumentException("Referencia de origen incompleta.");}
        public static Origen de(String selector,long id){return new Origen(selector,Long.toString(id));}
    }
    public record Causa(int esquema,Recurso recurso,String finalidad,HechoAsunto hecho,Origen origen,
                        Instant registradoEn,LocalDate fechaEfectiva,long responsableAlDetectar,String estadoFuente){
        public Causa {if(esquema!=1||recurso==null||recurso.id()<=0||finalidad==null||finalidad.isBlank()||hecho==null||origen==null||registradoEn==null||responsableAlDetectar<=0)
            throw new IllegalArgumentException("El asunto no conserva una causa material acreditada.");}
    }
    private final FuentesAsuntosRepository repo;
    private final AutoridadAsuntos autoridad;
    private final Documentos documentos;
    private final EntityManager em;
    private final EventoDominioRepository eventos;
    public FuentesAsuntos(FuentesAsuntosRepository repo,AutoridadAsuntos autoridad,Documentos documentos,EntityManager em,EventoDominioRepository eventos){
        this.repo=repo;this.autoridad=autoridad;this.documentos=documentos;this.em=em;this.eventos=eventos;
    }
    public Causa resolver(Recurso recurso,String finalidad,Origen origen,Actor actor){
        if(origen==null)throw new ReglaNegocioException("El aviso debe conservar su hecho canónico.");
        em.flush();
        var f=repo.resolver(actor.idOrganizacion(),origen.selector(),origen.referencia())
                .orElseThrow(()->new NoEncontradoException("Hecho del aviso"));
        if(!f.recurso().equals(recurso))throw new ConflictoException("El hecho no corresponde al recurso del aviso.");
        var q=new AutoridadAsuntos.Consulta(recurso,finalidad);
        if(!autoridad.legibles(List.of(q),actor).contains(q))throw new AccesoNoAutorizadoException();
        var contexto=repo.contextos(actor.idOrganizacion(),List.of(recurso)).get(recurso);
        if(contexto==null)throw new NoEncontradoException("Contexto del aviso");
        Long responsable=responsableDeEntrega(contexto);
        if(responsable==null)throw new ConflictoException("El recurso no conserva un responsable actual.");
        return causa(f,finalidad,origen,responsable);
    }
    public Map<Long,Causa> solicitudes(Collection<Long> ids,Actor actor){
        var fuentes=repo.ultimasTransicionesSolicitud(actor.idOrganizacion(),ids);var consultas=new ArrayList<AutoridadAsuntos.Consulta>();
        fuentes.values().forEach(f->consultas.add(new AutoridadAsuntos.Consulta(f.recurso(),"SEGUIMIENTO_SOLICITUD")));
        var permitidas=autoridad.legibles(consultas,actor);var salida=new HashMap<Long,Causa>();
        fuentes.forEach((id,f)->{if(permitidas.contains(new AutoridadAsuntos.Consulta(f.recurso(),"SEGUIMIENTO_SOLICITUD")))
            salida.put(id,causa(f,"SEGUIMIENTO_SOLICITUD",Origen.de("ESTADO_SOLICITUD",id),actor.idRolOperativo()));});return Map.copyOf(salida);
    }
    public Set<Causa> vigentes(Collection<Causa> causas,Actor actor){
        if(causas.isEmpty())return Set.of();
        var consultas=causas.stream().map(c->new AutoridadAsuntos.Consulta(c.recurso(),c.finalidad())).toList();
        var permitidas=autoridad.legibles(consultas,actor);var actuales=new HashMap<Origen,Fuente>();var salida=new HashSet<Causa>();
        var grupos=new LinkedHashMap<String,Set<String>>();
        for(var c:causas)if(permitidas.contains(new AutoridadAsuntos.Consulta(c.recurso(),c.finalidad())))
            grupos.computeIfAbsent(c.origen().selector(),k->new LinkedHashSet<>()).add(c.origen().referencia());
        grupos.forEach((selector,refs)->repo.resolverLote(actor.idOrganizacion(),selector,refs)
                .forEach((ref,f)->actuales.put(new Origen(selector,ref),f)));
        for(var c:causas){if(!permitidas.contains(new AutoridadAsuntos.Consulta(c.recurso(),c.finalidad())))continue;
            var f=actuales.get(c.origen());
            if(f!=null&&f.recurso().equals(c.recurso())&&f.hecho().equals(c.hecho())
                    &&Objects.equals(f.registradoEn(),c.registradoEn())&&Objects.equals(f.fechaEfectiva(),c.fechaEfectiva())
                    &&Objects.equals(f.estado(),c.estadoFuente()))salida.add(c);
        }
        var finales=autoridad.legibles(salida.stream().map(c->new AutoridadAsuntos.Consulta(c.recurso(),c.finalidad())).toList(),actor);
        salida.removeIf(c->!finales.contains(new AutoridadAsuntos.Consulta(c.recurso(),c.finalidad())));return Set.copyOf(salida);
    }
    public Map<Long,Causa> decisiones(Collection<Decision> decisiones,Actor actor){
        var refs=decisiones.stream().map(d->Long.toString(d.revision())).toList();
        var fuentes=repo.resolverLote(actor.idOrganizacion(),"REVISION_ENCARGO",refs);var salida=new LinkedHashMap<Long,Causa>();
        for(var d:decisiones){var f=fuentes.get(Long.toString(d.revision()));
            if(f!=null&&f.recurso().equals(new Recurso("CAPTACION",d.encargo())))
                salida.put(d.revision(),causa(f,"CAPTACION_REVISADA",Origen.de("REVISION_ENCARGO",d.revision()),d.responsable()));
        }
        var vigentes=vigentes(salida.values(),actor);salida.values().removeIf(c->!vigentes.contains(c));return Map.copyOf(salida);
    }
    public record CierreConfirmado(HechoAsunto hecho,long encargo,Map<String,Set<Long>> recursos){
        public CierreConfirmado {recursos=Map.copyOf(recursos);}
    }
    /** Efecto del hecho confirmado, sin recibir un conjunto libre de recursos desde el consumidor. */
    public CierreConfirmado cierreConfirmado(long revision,Actor actor){
        if(revision<=0)throw new ReglaNegocioException("Identifica la revisión confirmada del cierre.");
        em.flush();
        var h=repo.hitoCierre(actor.idOrganizacion(),revision).orElseThrow(()->new NoEncontradoException("Hito confirmado del cierre"));
        autoridad.exigirDecisionEncargo(h.encargo(),actor);
        if(h.actor()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        var recursos=new LinkedHashMap<String,Set<Long>>();
        recursos.put("CAPTACION",Set.of(h.encargo()));recursos.put("OPORTUNIDAD",Set.of(h.oportunidad()));
        if("CONTRATO_ALQUILER".equals(h.tipo())){
            if(h.solicitud()!=null)recursos.put("SOLICITUD_ALQUILER",Set.of(h.solicitud()));
        }else if("ESCRITURA".equals(h.tipo())){
            Long[] unidades=documentos.comoObjeto(h.unidades(),Long[].class);
            if(unidades==null||unidades.length==0||Arrays.stream(unidades).anyMatch(id->id==null||id<=0))
                throw new IllegalStateException("El cierre no conserva sus unidades exactas.");
            var ids=Set.copyOf(Arrays.asList(unidades));
            if(ids.size()!=unidades.length)throw new IllegalStateException("La cobertura del cierre repite unidades.");
            recursos.put("INMUEBLE",ids);
        }else throw new IllegalStateException("El hito no tiene un efecto de cierre de tareas.");
        return new CierreConfirmado(new HechoAsunto("REVISION_HITO_CONTRACTUAL",revision,null),h.encargo(),recursos);
    }
    /** El caso de revisión conserva el lock del Documento hasta commit. No inventa una transición. */
    public Origen registrarRevisionDocumento(DocumentoSolicitud d,Actor actor){
        if(d==null||d.getId()==null||d.getSolicitud()==null||!Objects.equals(d.getOrganizacionId(),actor.idOrganizacion()))
            throw new IllegalStateException("Falta la fuente real de la revisión del documento.");
        var recurso=new Recurso("SOLICITUD_ALQUILER",d.getSolicitud().getId());var consulta=new AutoridadAsuntos.Consulta(recurso,"SOLICITUD_DOCUMENTO_REVISADO");
        if(!autoridad.legibles(List.of(consulta),actor).contains(consulta))throw new AccesoNoAutorizadoException();
        var material=new LinkedHashMap<String,Object>();material.put("esquema",1);material.put("documento",d.getId());
        material.put("solicitud",d.getSolicitud().getId());material.put("fuenteEntrega",new HechoAsunto("DOCUMENTO_SOLICITUD",d.getId(),null));
        material.put("archivo",d.getArchivoRef());material.put("resultado",d.getResultadoRevision());material.put("observacion",d.getObservaciones());
        material.put("finalidad","REVISION_DOCUMENTO_SOLICITUD");
        String huella=Idempotencia.huellaExacta(documentos.objetoCanonico(material));
        em.flush();var anterior=repo.ultimaRevisionDocumento(actor.idOrganizacion(),d.getId());
        if(anterior.isPresent()){
            var previo=documentos.comoMapa(anterior.get().contenido());
            if(Objects.equals(previo.get("huellaMaterial"),huella)){
                Object revisor=previo.get("actor");
                if(!(revisor instanceof Number n))throw new IllegalStateException("La revisión anterior no conserva su actor real.");
                if(n.longValue()!=actor.idRolOperativo()&&!repo.tieneConstanciaReiteracion(actor.idOrganizacion(),d.getId(),actor.idRolOperativo(),anterior.get().evento())){
                    // Acredita esta petición validada, no otra revisión material, otro aviso o una transición.
                    var constancia=Map.of("materialRef",anterior.get().evento(),"actor",actor.idRolOperativo(),
                            "finalidad","REVISION_DOCUMENTO_SOLICITUD","resultado","PETICION_VALIDADA_SIN_CAMBIO_MATERIAL");
                    eventos.saveAndFlush(EventoDominio.de(actor.idOrganizacion(),"REVISION_DOCUMENTO_REITERADA","DOCUMENTO_SOLICITUD",d.getId(),
                            actor.idRolOperativo(),EventoDominio.CANAL_SISTEMA).con(documentos.objetoCanonico(constancia)));
                }
                return Origen.de("REVISION_DOCUMENTO",anterior.get().evento());
            }
        }
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("material",material);snapshot.put("huellaMaterial",huella);
        snapshot.put("antecedente",anterior.map(RevisionDocumento::evento).orElse(null));snapshot.put("actor",actor.idRolOperativo());
        snapshot.put("finalidad","REVISION_DOCUMENTO_SOLICITUD");
        var evento=EventoDominio.de(actor.idOrganizacion(),"DOCUMENTO_SOLICITUD_REVISADO","DOCUMENTO_SOLICITUD",d.getId(),
                actor.idRolOperativo(),EventoDominio.CANAL_SISTEMA).con(documentos.objetoCanonico(snapshot));
        evento=eventos.saveAndFlush(evento);return Origen.de("REVISION_DOCUMENTO",evento.getId());
    }
    public String guardar(Causa causa){return documentos.objetoCanonico(causa);}
    /** Papel referido por el aviso, separado de la autoridad de lectura y del responsable histórico. */
    public static Long responsableDeEntrega(Contexto contexto){
        return Set.of("OPORTUNIDAD","CONTRATO_ALQUILER").contains(contexto.recurso().tipo())?contexto.responsableEncargo():contexto.responsable();
    }
    public Causa leer(String json){if(json==null)throw new IllegalStateException("Falta la fuente del asunto.");return documentos.comoObjeto(json,Causa.class);}
    public String huella(long org,Causa causa){
        var datos=new LinkedHashMap<String,Object>();datos.put("organizacion",org);datos.put("recurso",causa.recurso());
        datos.put("finalidad",causa.finalidad());datos.put("hecho",causa.hecho());return Idempotencia.huellaExacta(documentos.objetoCanonico(datos));
    }
    private static Causa causa(Fuente f,String finalidad,Origen origen,long responsable){return new Causa(1,f.recurso(),finalidad,f.hecho(),origen,f.registradoEn(),f.fechaEfectiva(),responsable,f.estado());}
}
