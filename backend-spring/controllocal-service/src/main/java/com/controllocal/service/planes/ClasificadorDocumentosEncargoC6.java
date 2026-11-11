package com.controllocal.service.planes;

import com.controllocal.domain.comercial.ArchivoEncargo;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.repositorio.DocumentoEncargoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.encargo.DocumentosEncargo;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Reutiliza la autoridad documental C2; preparar un borrador privado no requiere un Encargo ficticio. */
@Component
public class ClasificadorDocumentosEncargoC6 implements ClasificadorCargaAlmacenamientoB0 {
    private final DocumentoEncargoRepository documentos;
    private final ObjectProvider<EncargoService> encargos;
    private final AccesoEncargo acceso;
    private final Documentos json;
    private final ContextoPresupuestoB0 contexto;
    public ClasificadorDocumentosEncargoC6(DocumentoEncargoRepository documentos,ObjectProvider<EncargoService> encargos,
            AccesoEncargo acceso,Documentos json,ContextoPresupuestoB0 contexto){
        this.documentos=documentos;this.encargos=encargos;this.acceso=acceso;this.json=json;this.contexto=contexto;
    }
    @Override public boolean atiende(UsoAlmacenamientoB0.Recurso r){
        return r!=null&&Set.of("DOCUMENTO_ENCARGO_C6","EVIDENCIA_CONTEXTO_ENCARGO_C6").contains(r.tipo());
    }
    /** La huella del contenido preparado y la del contexto de admisión son conceptos distintos. */
    @Transactional(propagation=Propagation.MANDATORY)
    public UsoAlmacenamientoB0.Recurso preparar(DocumentosEncargo.Origen origen){
        if(origen==null||origen.huellaOrigen()==null)throw new IllegalArgumentException("Falta el origen documental Core.");
        contexto.iniciarArchivo(origen.actor(),origen.sesion());
        var r=new UsoAlmacenamientoB0.Recurso(origen.tipoRecurso(),origen.referencia(),origen.finalidad(),origen.huellaOrigen());
        if(!atiende(r))throw new CapacidadDeshabilitadaException();
        var clasificacion=describir(r,origen.actor(),origen.huellaOrigen());
        return new UsoAlmacenamientoB0.Recurso(r.tipo(),r.referencia(),r.finalidad(),clasificacion.huellaFuente());
    }
    @Override public Clasificacion clasificar(UsoAlmacenamientoB0.Solicitud s,Actor actor){
        return describir(s.recurso(),actor,null);
    }
    private Clasificacion describir(UsoAlmacenamientoB0.Recurso r,Actor actor,String huellaMaterialDeclarada){
        var fuente=new LinkedHashMap<String,Object>();
        if("DOCUMENTO_ENCARGO_C6".equals(r.tipo())){
            UUID ref;try{ref=UUID.fromString(r.referencia());}catch(RuntimeException e){throw new ReglaNegocioException("Identifica el documento Core preparado.");}
            var d=documentos.findByOrganizacionIdAndArchivoRef(actor.idOrganizacion(),ref)
                    .orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
            // Existe antes del PUT dentro de la TX del comando, y sólo su productor admite su binario.
            if(!Objects.equals(d.getIdAutor(),actor.idRolOperativo())||!ArchivoEncargo.PDF.equals(r.finalidad())
                    ||(huellaMaterialDeclarada!=null&&!Objects.equals(d.getHuella(),huellaMaterialDeclarada)))throw new AccesoNoAutorizadoException();
            var actual=encargos.getObject().consultarDocumento(d.getId(),actor);
            fuente.put("documento",actual.id());fuente.put("version",actual.version());fuente.put("huella",actual.huella());
            if(actual.encargo()!=null)fuente.put("encargo",actual.encargo());
        }else{
            if(!actor.esAgente()||!Set.of(ArchivoEncargo.ACEPTACION,ArchivoEncargo.TITULARIDAD,ArchivoEncargo.REPRESENTACION,
                    ArchivoEncargo.FIN,ArchivoEncargo.HITO,ArchivoEncargo.COBRO).contains(r.finalidad()))throw new AccesoNoAutorizadoException();
            String[] ref=r.referencia().split(":",-1);long id;
            try{if(ref.length!=2)throw new IllegalArgumentException();id=Long.parseLong(ref[1]);if(id<=0)throw new IllegalArgumentException();}
            catch(RuntimeException e){throw new ReglaNegocioException("Identifica el contexto documental existente.");}
            if("ENCARGO".equals(ref[0])){
                var c=acceso.leer(id,actor);acceso.exigirResponsable(c,actor);var actual=acceso.contexto(id,actor);
                fuente.put("encargo",id);fuente.put("version",actual.getVersion());fuente.put("responsable",actual.getResponsable());
            }else if("DOCUMENTO".equals(ref[0])){
                var d=documentos.findByOrganizacionIdAndId(actor.idOrganizacion(),id)
                        .orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
                var actual=encargos.getObject().consultarDocumento(id,actor);
                if(actual.encargo()!=null||!Objects.equals(d.getIdAutor(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
                fuente.put("documento",id);fuente.put("version",actual.version());fuente.put("huella",actual.huella());
            }else throw new ReglaNegocioException("El contexto no corresponde a un documento o Encargo Core.");
        }
        fuente.put("finalidad",r.finalidad());
        // La etiqueta declarada del archivo, por sí sola, no acredita una excepción esencial financiada.
        return new Clasificacion(CategoriaAlmacenamiento.DOCUMENTO,Necesidad.ORDINARIA,Idempotencia.huellaExacta(json.objetoCanonico(fuente)));
    }
}
