package com.controllocal.service.planes;

import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.repositorio.PiezaComercialRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.controllocal.service.planes.ClasificadorCargaAlmacenamientoB0.*;

/** Clasifica los productores existentes desde sus casos Core, nunca desde un flag del cliente. */
@Component
public class ClasificadorArchivosExistentesB0 implements ClasificadorCargaAlmacenamientoB0 {
    public static final String FOTO="CARGA_FOTO_PROPIEDAD",PERFIL="CARGA_FOTO_PERFIL",SOLICITUD="CARGA_DOCUMENTO_SOLICITUD",
            PIEZA="CARGA_PIEZA_COMERCIAL",COPIA="CARGA_COPIA_FOTO";
    private static final Set<String> TIPOS=Set.of(FOTO,PERFIL,SOLICITUD,PIEZA,COPIA);
    private final ContextoPresupuestoB0 contexto;private final FotoPropiedadService fotos;private final PerfilService perfiles;
    private final DocumentoSolicitudService solicitudes;private final AccesoArchivoService archivos;private final AccesoEncargo encargos;
    private final PiezaComercialRepository piezas;private final Documentos json;
    public ClasificadorArchivosExistentesB0(ContextoPresupuestoB0 contexto,FotoPropiedadService fotos,PerfilService perfiles,
            DocumentoSolicitudService solicitudes,AccesoArchivoService archivos,AccesoEncargo encargos,PiezaComercialRepository piezas,Documentos json){
        this.contexto=contexto;this.fotos=fotos;this.perfiles=perfiles;this.solicitudes=solicitudes;this.archivos=archivos;this.encargos=encargos;this.piezas=piezas;this.json=json;
    }
    /** Orden único: cuenta/SID C1 antes de bloquear el recurso. */
    @Transactional(propagation=Propagation.MANDATORY)
    public UsoAlmacenamientoB0.Recurso preparar(String tipo,String referencia,String finalidad,Actor actor,ContextoSesion sesion){
        contexto.iniciarArchivo(actor,sesion);var c=describir(tipo,referencia,finalidad,actor);
        return new UsoAlmacenamientoB0.Recurso(tipo,referencia,finalidad,c.huellaFuente());
    }
    public boolean atiende(UsoAlmacenamientoB0.Recurso r){return r!=null&&TIPOS.contains(r.tipo());}
    public Clasificacion clasificar(UsoAlmacenamientoB0.Solicitud s,Actor actor){
        var r=s.recurso();return describir(r.tipo(),r.referencia(),r.finalidad(),actor);
    }
    private Clasificacion describir(String tipo,String referencia,String finalidad,Actor a){
        return switch(tipo){
            case FOTO->{
                int tope=switch(finalidad){case "GALERIA_UNIVERSAL"->FotoPropiedadService.TOPE_UNIVERSAL;case "GALERIA_LOCAL"->FotoPropiedadService.TOPE_DEL_LOCAL;default->throw new ReglaNegocioException("La finalidad de la foto no está definida.");};
                yield new Clasificacion(CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,fotos.prepararCarga(numero(referencia),tope,a));
            }
            case PERFIL->{
                if(!"FOTO_PERFIL".equals(finalidad)||numero(referencia)!=a.idPersona())throw new AccesoNoAutorizadoException();
                var perfil=perfiles.obtener(a);
                yield clasificacion(CategoriaAlmacenamiento.ORIGINAL,Map.of("persona",a.idPersona(),"fotoAnterior",Objects.toString(perfil.fotoClave(),"")));
            }
            case SOLICITUD->{
                if(finalidad==null||!finalidad.startsWith("TIPO:"))throw new ReglaNegocioException("Falta el tipo documental de la solicitud.");
                yield new Clasificacion(CategoriaAlmacenamiento.DOCUMENTO,Necesidad.ORDINARIA,solicitudes.prepararCarga(numero(referencia),finalidad.substring(5),a));
            }
            case COPIA->{
                if(!"COPIA_TECNICA_COMERCIAL".equals(finalidad))throw new ReglaNegocioException("La finalidad de copia no está definida.");
                var original=archivos.resolver(uuid(referencia).toString(),a);
                if(!"FOTO_PROPIEDAD".equals(original.tipo()))throw new NoEncontradoException("Fotografía original");
                yield clasificacion(CategoriaAlmacenamiento.COPIA_TECNICA,Map.of("original",original));
            }
            case PIEZA->{
                if(!Set.of("REVISION_PRIVADA","DIFUSION_AUTORIZADA").contains(finalidad))throw new ReglaNegocioException("La finalidad de pieza no está definida.");
                var p=piezas.findByOrganizacionIdAndReferencia(a.idOrganizacion(),uuid(referencia)).orElseThrow(()->new NoEncontradoException("Pieza"));
                var encargoActual=encargos.leer(p.getEncargo(),a);
                encargos.exigirResponsable(encargoActual,a);
                yield clasificacion("REVISION_PRIVADA".equals(finalidad)?CategoriaAlmacenamiento.PREVIEW:CategoriaAlmacenamiento.EXPORTACION_PERSISTENTE,
                        Map.of("pieza",p.getReferencia(),"version",p.getVersionContenido(),"origen",p.getHuellaOrigen(),"finalidad",finalidad));
            }
            default->throw new CapacidadDeshabilitadaException();
        };
    }
    private Clasificacion clasificacion(CategoriaAlmacenamiento categoria,Map<String,?> fuente){return new Clasificacion(categoria,Necesidad.ORDINARIA,Idempotencia.huellaExacta(json.objetoCanonico(fuente)));}
    private static long numero(String s){try{long n=Long.parseLong(s);if(n<=0)throw new NumberFormatException();return n;}catch(RuntimeException ex){throw new ReglaNegocioException("Identifica el recurso de la carga.");}}
    private static UUID uuid(String s){try{return UUID.fromString(s);}catch(RuntimeException ex){throw new ReglaNegocioException("Identifica la referencia de la carga.");}}
}
