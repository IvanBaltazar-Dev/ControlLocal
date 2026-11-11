package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.encargo.*;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.controllocal.domain.comercial.ArchivoEncargo.HITO;

/** Lector independiente de los efectos: los punteros explícitos eligen hechos, nunca la fecha de carga. */
@Service
@Transactional(readOnly=true)
public class HitosConfirmadosImpl implements HitosConfirmados {
    private final ExpedienteOperacionRepository expedientes;
    private final RevisionExpedienteOperacionRepository revisiones;
    private final HitoContractualRepository hitos;
    private final RevisionHitoContractualRepository hechos;
    private final AccesoEncargo acceso;
    private final PactosDeEncargo pactos;
    private final FuentesDeFormalizacion fuentes;
    private final EvidenciasDeFormalizacion evidencias;
    private final Documentos json;
    HitosConfirmadosImpl(ExpedienteOperacionRepository expedientes,RevisionExpedienteOperacionRepository revisiones,
            HitoContractualRepository hitos,RevisionHitoContractualRepository hechos,AccesoEncargo acceso,
            PactosDeEncargo pactos,FuentesDeFormalizacion fuentes,EvidenciasDeFormalizacion evidencias,Documentos json){
        this.expedientes=expedientes;this.revisiones=revisiones;this.hitos=hitos;this.hechos=hechos;
        this.acceso=acceso;this.pactos=pactos;this.fuentes=fuentes;this.evidencias=evidencias;this.json=json;
    }

    @Override public ContextoEconomico consultarContexto(ReferenciaContextoEconomico ref,Actor actor){
        var e=expediente(ref,actor);var c=acceso.leer(e.getEncargo(),actor);
        var p=new ReferenciaPacto(e.getEncargo(),e.getRevisionPacto());pactos.consultarPactoFirmado(p,actor);
        var presentacion=presentacion(e,actor);
        FuenteEconomicaFinal fuente=e.getRevisionFuente()==null?presentacion.fuenteC4():
                fuenteRevision(e,e.getRevisionFuente(),actor);
        var confirmados=new ArrayList<FuenteHitoConfirmado>();
        for(var h:hitos.delExpediente(actor.idOrganizacion(),e.getId())){
            if(h.getRevisionConfirmada()!=null)confirmados.add(confirmado(e,h,h.getRevisionConfirmada(),actor));
        }
        return new ContextoEconomico(ref,actor.idOrganizacion(),e.getEncargo(),c.operacion(),e.getOportunidad(),
                e.getSolicitudAlquiler(),e.getContratoAlquiler(),e.getContratoVenta(),p,fuente,confirmados);
    }

    @Override public FuenteEconomicaFinal consultarFuente(ReferenciaContextoEconomico contexto,
            ReferenciaFuenteEconomica ref,Actor actor){
        var e=expediente(contexto,actor);
        if(ref==null)throw new ReglaNegocioException("Identifica la fuente económica histórica.");
        if("REVISION_C5".equals(ref.tipo())){
            if(ref.id()!=e.getId())throw new NoEncontradoException("Fuente del contexto económico");
            return fuenteRevision(e,ref.revision(),actor);
        }
        if("ACUERDO_C4".equals(ref.tipo())){
            // No se acepta un acuerdo libre: la referencia debe estar en una presentación de ESTE contexto.
            for(var r:revisiones.historia(actor.idOrganizacion(),e.getId())){
                if(!"PRESENTACION".equals(r.getTipoRegistro()))continue;
                var p=json.comoObjeto(r.getContenido(),ContenidoFormalizacion.Presentacion.class);
                if(p.fuenteC4()!=null&&ref.equals(p.fuenteC4().referencia())){
                    fuentes.reautorizar(e.getEncargo(),e.getOportunidad(),p.cobertura(),actor);
                    return p.fuenteC4();
                }
            }
            throw new NoEncontradoException("Acuerdo histórico del contexto económico");
        }
        throw new ReglaNegocioException("Tipo de fuente económica no admitido.");
    }

    ExpedienteOperacion expediente(ReferenciaContextoEconomico ref,Actor actor){
        if(ref==null||ref.id()<=0)throw new ReglaNegocioException("Identifica el contexto económico.");
        var e=expedientes.buscar(actor.idOrganizacion(),ref.id()).orElseThrow(()->new NoEncontradoException("Expediente"));
        acceso.exigirLectura(e.getEncargo(),actor);return e;
    }
    ContenidoFormalizacion.Presentacion presentacion(ExpedienteOperacion e,Actor actor){
        if(e.getRevisionPresentada()==null)throw new IllegalStateException("El expediente no conserva su presentación.");
        var r=revision(e,e.getRevisionPresentada(),actor);
        if(!"PRESENTACION".equals(r.getTipoRegistro()))throw new IllegalStateException("El puntero de presentación no identifica una presentación.");
        var p=json.comoObjeto(r.getContenido(),ContenidoFormalizacion.Presentacion.class);
        if(!Objects.equals(p.datos().encargo(),e.getEncargo())||!Objects.equals(p.datos().oportunidad(),e.getOportunidad())
                ||p.datos().expediente()!=null&&!Objects.equals(p.datos().expediente(),e.getId()))
            throw new IllegalStateException("La presentación no conserva la identidad del expediente.");
        fuentes.reautorizar(e.getEncargo(),e.getOportunidad(),p.cobertura(),actor);return p;
    }
    ContenidoFormalizacion.Revision revisionAprobada(ExpedienteOperacion e,long id,Actor actor){
        var r=revision(e,id,actor);
        if(!"REVISION".equals(r.getTipoRegistro())||!"APROBAR".equals(r.getDecision()))
            throw new NoEncontradoException("Revisión aprobada del expediente");
        var contenido=json.comoObjeto(r.getContenido(),ContenidoFormalizacion.Revision.class);
        if(!Objects.equals(contenido.datos().expediente(),e.getId())||!Objects.equals(contenido.datos().versionEsperada(),r.getVersionRevisada())
                ||!Objects.equals(contenido.datos().decision(),r.getDecision()))
            throw new IllegalStateException("La revisión no conserva la identidad y versión revisadas del expediente.");
        fuentes.reautorizar(e.getEncargo(),e.getOportunidad(),contenido.cobertura(),actor);return contenido;
    }
    private FuenteEconomicaFinal fuenteRevision(ExpedienteOperacion e,long id,Actor actor){
        var r=revisionAprobada(e,id,actor);
        if(r.datos().acuerdoFinal()==null)throw new NoEncontradoException("Fuente económica final documentada");
        var f=fuentes.fuenteRevision(e.getId(),id,r);
        evidencias.exigir(e.getEncargo(),f.documentos(),HITO,actor);return f;
    }
    RevisionExpedienteOperacion revision(ExpedienteOperacion e,long id,Actor actor){
        return revisiones.buscar(actor.idOrganizacion(),e.getId(),id).orElseThrow(()->new NoEncontradoException("Revisión del expediente"));
    }
    FuenteHitoConfirmado confirmado(ExpedienteOperacion e,HitoContractual h,long revision,Actor actor){
        var r=hechos.buscar(actor.idOrganizacion(),e.getId(),h.getId(),revision)
                .orElseThrow(()->new NoEncontradoException("Revisión confirmada del hito"));
        if(!Set.of("CONFIRMACION","RECTIFICACION").contains(r.getTipoRegistro())||r.getFechaEfectiva()==null)
            throw new IllegalStateException("El puntero confirmado no identifica un hecho confirmado.");
        var contenido=json.comoObjeto(r.getContenido(),ContenidoFormalizacion.Hecho.class);
        if(contenido.pacto()==null||contenido.pacto().encargo()!=e.getEncargo())throw new IllegalStateException("El hito no conserva el pacto de su Encargo.");
        pactos.consultarPactoFirmado(contenido.pacto(),actor);
        evidencias.exigir(e.getEncargo(),contenido.evidencias(),HITO,actor);
        if(r.getRevisionExpediente()==null)throw new IllegalStateException("El hecho no conserva revisión del expediente.");
        revisionAprobada(e,r.getRevisionExpediente(),actor);
        if(contenido.fuente()!=null)consultarFuente(new ReferenciaContextoEconomico(e.getId()),contenido.fuente(),actor);
        return new FuenteHitoConfirmado(new ReferenciaContextoEconomico(e.getId()),new ReferenciaHito(h.getId(),r.getId()),
                h.getTipo(),r.getFechaEfectiva(),r.getRegistradoEn(),contenido.pacto(),contenido.fuente(),
                "RECTIFICACION".equals(r.getTipoRegistro())?new ReferenciaHito(h.getId(),r.getAntecedente()):null,contenido.procedencia());
    }
}
