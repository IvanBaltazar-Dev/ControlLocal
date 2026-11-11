package com.controllocal.service.formalizacion;

import com.controllocal.service.Actor;
import com.controllocal.service.encargo.AccesoArchivoEncargo;
import com.controllocal.service.encargo.EncargoService.ArchivoLectura;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaDocumento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** A04/RF053/058/064: metadatos autorizados, sin descargar ni interpretar el contenido. */
@Component
public class EvidenciasDeFormalizacion {
    private final AccesoArchivoEncargo archivos;
    public EvidenciasDeFormalizacion(AccesoArchivoEncargo archivos) { this.archivos=archivos; }

    @Transactional(readOnly=true)
    public ReferenciaDocumento consultar(long encargo,java.util.UUID referencia,String finalidad,Actor actor) {
        if(referencia==null)throw new ReglaNegocioException("Identifica la referencia documental.");
        var actual=archivos.autorizar(referencia,actor);
        exigirContexto(encargo,finalidad,actual);
        if(actual.huella()==null||!actual.huella().matches("[a-fA-F0-9]{64}")||actual.version()<0)
            throw new IllegalStateException("El archivo no conserva versión y huella del contenido.");
        return new ReferenciaDocumento(actual.referencia(),actual.version(),actual.huella());
    }

    @Transactional(readOnly=true)
    public void exigir(long encargo,List<ReferenciaDocumento> referencias,String finalidad,Actor actor) {
        if(referencias==null)throw new ReglaNegocioException("Conserva las referencias documentales de la actuación.");
        var ids=new HashSet<java.util.UUID>();
        for(var ref:referencias) {
            if(ref==null||!ids.add(ref.referencia()))
                throw new ReglaNegocioException("Las referencias documentales no admiten duplicados ni valores vacíos.");
            exigir(encargo,ref,finalidad,actor);
        }
    }

    @Transactional(readOnly=true)
    public ArchivoLectura exigir(long encargo,ReferenciaDocumento ref,String finalidad,Actor actor) {
        if(ref==null||ref.referencia()==null||ref.version()<0||ref.huella()==null
                ||!ref.huella().matches("[a-fA-F0-9]{64}"))
            throw new ReglaNegocioException("La evidencia requiere referencia, versión y huella del contenido.");
        var actual=archivos.autorizar(ref.referencia(),actor);
        exigirContexto(encargo,finalidad,actual);
        if(actual.version()!=ref.version()||!ref.huella().equalsIgnoreCase(actual.huella()))
            throw new ConflictoException("La evidencia cambió; revisa su versión y contenido actuales.");
        return actual;
    }

    private static void exigirContexto(long encargo,String finalidad,ArchivoLectura actual) {
        if(!Objects.equals(actual.encargo(),encargo)
                ||finalidad!=null&&!finalidad.equals(actual.finalidad()))
            throw new NoEncontradoException("Evidencia de la actuación");
    }
}
