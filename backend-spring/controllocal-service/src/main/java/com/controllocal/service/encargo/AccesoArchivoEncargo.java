package com.controllocal.service.encargo;

import com.controllocal.persistence.repositorio.ArchivoEncargoRepository;
import com.controllocal.persistence.repositorio.CargaArchivoContextualRepository;
import com.controllocal.persistence.repositorio.DocumentoEncargoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/** Autoridad escalar A04 sin depender del motor comercial ni del adaptador de binarios. */
@Component
public class AccesoArchivoEncargo {
    private final ArchivoEncargoRepository archivos;
    private final AccesoEncargo acceso;
    private final CargaArchivoContextualRepository cargas;
    private final DocumentoEncargoRepository documentos;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.controllocal.service.ReportePropietarioService> reportes;
    public AccesoArchivoEncargo(ArchivoEncargoRepository archivos,AccesoEncargo acceso,
            CargaArchivoContextualRepository cargas,DocumentoEncargoRepository documentos){
        this.archivos=archivos;this.acceso=acceso;this.cargas=cargas;this.documentos=documentos;
    }
    @Transactional(readOnly=true)
    public EncargoService.ArchivoLectura autorizar(UUID ref,Actor actor) {
        var a=archivos.actual(actor.idOrganizacion(),ref).orElseThrow(()->new NoEncontradoException("Archivo del Encargo"));
        if(a.getEncargo()!=null)acceso.exigirLectura(a.getEncargo(),actor);
        else if(!Objects.equals(a.getAutor(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
        if(!actor.esAgente()&&!actor.esBroker())throw new AccesoNoAutorizadoException();
        if(com.controllocal.domain.comercial.ArchivoEncargo.REPORTE.equals(a.getFinalidad()))
            reportes.getObject().exigirLecturaArchivo(ref,actor);
        return new EncargoService.ArchivoLectura(a.getArchivoRef(),a.getId(),a.getEncargo(),a.getDocumento(),a.getAutor(),
                a.getFinalidad(),a.getClaveProveedor(),a.getNombre(),a.getContentType(),a.getHuella(),a.getVersion());
    }
    @Transactional(readOnly=true)
    public EncargoService.ArchivoLectura autorizarCarga(UUID ref,Actor actor){
        var c=cargas.actual(actor.idOrganizacion(),ref).orElseThrow(()->new NoEncontradoException("Carga de archivo"));
        if(!actor.esAgente()||!Objects.equals(c.getAutor(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
        Long encargo=c.getEncargo();
        if(c.getDocumento()!=null){
            var d=documentos.contextoActual(actor.idOrganizacion(),c.getDocumento()).orElseThrow(()->new NoEncontradoException("Documento del Encargo"));
            if(d.getEncargo()!=null){
                if(encargo!=null&&!encargo.equals(d.getEncargo()))throw new com.controllocal.service.excepcion.ConflictoException("El contexto de la carga cambió.");
                encargo=d.getEncargo();
            }else if(!Objects.equals(d.getAutor(),actor.idRolOperativo()))throw new AccesoNoAutorizadoException();
        }
        if(encargo!=null)acceso.exigirLectura(encargo,actor);
        return new EncargoService.ArchivoLectura(c.getReferencia(),c.getId(),encargo,c.getDocumento(),c.getAutor(),c.getFinalidad(),
                c.getClave(),c.getNombre(),c.getContentType(),c.getHuella(),c.getVersion());
    }
}
