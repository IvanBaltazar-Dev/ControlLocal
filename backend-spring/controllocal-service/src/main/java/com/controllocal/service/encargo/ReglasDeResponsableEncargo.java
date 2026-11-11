package com.controllocal.service.encargo;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.persistence.repositorio.DocumentoEncargoRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Component;

/** CAT-E12/RF017: un cambio operativo no cambia la identidad profesional firmada. */
@Component
public final class ReglasDeResponsableEncargo {
    private final DocumentoEncargoRepository documentos;
    public ReglasDeResponsableEncargo(DocumentoEncargoRepository documentos) { this.documentos=documentos; }
    public void exigirCambioOperativo(Captacion c) {
        exigirCambioOperativo(c.getOrganizacionId(),c.getId(),c.estadoActual(),c.isTerminosAceptados(),c.getIdDocumentoActual());
    }
    public void exigirCambioOperativo(long org,long id,String estado,boolean aceptados,Long documento) {
        if(!Captacion.esVivo(estado)) throw new ReglaNegocioException("El episodio ya terminó.");
        if(!aceptados) return;
        var cobertura=documento==null?null:documentos.coberturaResponsable(org,documento,id).orElse(null);
        if(cobertura==null || !cobertura.getDefinida() || cobertura.getResponsableFirmado()!=null)
            throw new ReglaNegocioException("La reasignación afecta al profesional de lo firmado o su cobertura no está acreditada; corresponde adenda.");
    }
    public boolean permiteCambioOperativo(Captacion c) {
        try { exigirCambioOperativo(c); return true; }
        catch(ReglaNegocioException falta) { return false; }
    }
}
