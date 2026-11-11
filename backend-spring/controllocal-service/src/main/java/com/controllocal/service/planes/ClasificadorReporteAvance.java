package com.controllocal.service.planes;

import com.controllocal.domain.comercial.ArchivoEncargo;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.repositorio.ReporteAvanceRepository;
import com.controllocal.service.*;
import com.controllocal.service.documentos.DocumentosReporteAvance;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** La composición RF078 reutiliza admisión/ledger B0; no recibe excepciones esenciales por etiqueta. */
@Component
public class ClasificadorReporteAvance implements ClasificadorCargaAlmacenamientoB0 {
    private final ReporteAvanceRepository repo;
    private final ObjectProvider<ReportePropietarioService> reportes;
    private final ContextoPresupuestoB0 contexto;
    private final Documentos json;
    public ClasificadorReporteAvance(ReporteAvanceRepository r,ObjectProvider<ReportePropietarioService> s,ContextoPresupuestoB0 c,Documentos j){repo=r;reportes=s;contexto=c;json=j;}
    public boolean atiende(UsoAlmacenamientoB0.Recurso r){return r!=null&&"REPORTE_AVANCE_C7".equals(r.tipo());}
    @Transactional(propagation=Propagation.MANDATORY)
    public UsoAlmacenamientoB0.Recurso preparar(DocumentosReporteAvance.Origen o){
        contexto.iniciarArchivo(o.actor(),o.sesion());var r=new UsoAlmacenamientoB0.Recurso("REPORTE_AVANCE_C7",o.archivo().toString(),ArchivoEncargo.REPORTE,o.huellaOrigen());
        var c=describir(r,o.actor());if(!c.huellaFuente().equals(o.huellaOrigen()))throw new ConflictoException("Cambió la versión que origina el reporte.");return r;
    }
    public Clasificacion clasificar(UsoAlmacenamientoB0.Solicitud s,Actor a){return describir(s.recurso(),a);}
    private Clasificacion describir(UsoAlmacenamientoB0.Recurso r,Actor a){
        if(!atiende(r)||!ArchivoEncargo.REPORTE.equals(r.finalidad()))throw new AccesoNoAutorizadoException();
        UUID ref;try{ref=UUID.fromString(r.referencia());}catch(RuntimeException e){throw new ReglaNegocioException("Identifica el archivo del reporte.");}
        reportes.getObject().exigirLecturaArchivo(ref,a);
        var h=repo.porArchivo(a.idOrganizacion(),ref).orElseThrow(()->new NoEncontradoException("Reporte"));
        var fuente=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("reporte",new ReportePropietarioService.Referencia(h.referencia(),h.version()),
            "contenido",repo.contenido(a.idOrganizacion(),h.id()).orElseThrow(),"fuentes",h.fuentes())));
        return new Clasificacion(CategoriaAlmacenamiento.DOCUMENTO,Necesidad.ORDINARIA,fuente);
    }
}
