package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.soporte.Procedencia;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** CAT-E24/25 y RF051–060: revisar un expediente, informar y confirmar son actos distintos. */
public interface FormalizacionService {
    record DatosPresentacion(Long oportunidad,Long versionOportunidad,
            Long encargo,Long versionEncargo,Long acuerdo,Long versionAcuerdo,
            ReferenciaPacto pacto,List<ReferenciaDocumento> documentos,
            Long expediente,Long versionEsperada,Long contratoPreparado) {
        public DatosPresentacion { documentos=documentos==null?null:List.copyOf(documentos); }
    }
    record DatosRevision(Long expediente,Long versionEsperada,String decision,
            String observaciones,List<ReferenciaDocumento> documentosRevisados,
            Boolean partesYRepresentacionRevisadas,Boolean composicionYAcuerdoRevisados,
            AcuerdoFinalDocumentado acuerdoFinal,CoberturaRevision coberturaEsperada) {
        public DatosRevision { documentosRevisados=documentosRevisados==null?null:List.copyOf(documentosRevisados); }
    }
    record DatosInformacionHito(Long expediente,Long versionExpediente,
            TipoHitoContractual tipo,LocalDate fechaEfectiva,
            List<ReferenciaDocumento> evidencias,String descripcion) {
        public DatosInformacionHito { evidencias=evidencias==null?null:List.copyOf(evidencias); }
    }
    /** Importe realmente pactado; declarado en escritura conserva otro concepto y moneda. */
    record AcuerdoFinalDocumentado(OfertaService.Dinero precioVenta,
            OfertaService.Dinero rentaMensual,OfertaService.Dinero declaradoEnEscritura,
            List<ReferenciaDocumento> documentos,String descripcion,TerminosAlquiler terminosAlquiler) {
        public AcuerdoFinalDocumentado { documentos=documentos==null?null:List.copyOf(documentos); }
    }
    /** Fechas contractuales acreditadas; no son la fecha de firma ni una agenda prevista. */
    record TerminosAlquiler(LocalDate inicio,LocalDate fin) { }
    record DatosConfirmacionHito(Long hito,Long versionHito,Long versionExpediente,
            Long revisionExpediente,Long revisionInformada,ReferenciaPacto pacto,
            AcuerdoFinalDocumentado acuerdoFinal) { }
    record DatosRectificacionHito(ReferenciaHito confirmado,Long versionHito,
            Long versionExpediente,LocalDate fechaEfectiva,String motivo,
            List<ReferenciaDocumento> evidencias,AcuerdoFinalDocumentado acuerdoFinal) {
        public DatosRectificacionHito { evidencias=evidencias==null?null:List.copyOf(evidencias); }
    }
    /** Referencias/snapshots materiales realmente vistos; el checkbox no sustituye su cobertura. */
    record CoberturaRevision(ReferenciaPacto pacto,
            com.controllocal.service.encargo.PactosDeEncargo.AcreditacionPacto acreditacionPacto,
            OfertaService.ObjetoExacto objeto,
            com.controllocal.service.encargo.EncargoService.PartesNegociacion partes,
            com.controllocal.service.OportunidadService.InteresadoReferencia interesado,
            Long acuerdoC4,Long versionAcuerdoC4,
            List<ReferenciaDocumento> documentos,List<ReferenciaDocumento> titularidad,
            List<ReferenciaDocumento> representacion,
            com.controllocal.service.ContratoService.PreparacionContrato contratoPreparado) {
        public CoberturaRevision { documentos=List.copyOf(documentos);titularidad=List.copyOf(titularidad);representacion=List.copyOf(representacion); }
    }
    record RevisionExpediente(long id,long expediente,long versionRevisada,
            String decision,String observaciones,Long antecedente,
            OffsetDateTime registradoEn,Procedencia procedencia,CoberturaRevision cobertura) { }
    record Hito(long id,long version,long expediente,TipoHitoContractual tipo,
            LocalDate fechaEfectiva,long revisionInformada,Long revisionConfirmada) { }
    record Expediente(long id,long version,long encargo,long oportunidad,
            ReferenciaPacto pacto,String estado,Long revisionAplicable,
            FuenteEconomicaFinal fuenteEconomica,List<ReferenciaDocumento> documentos,
            List<Hito> hitos,List<RevisionExpediente> revisiones) {
        public Expediente {
            documentos=List.copyOf(documentos);hitos=List.copyOf(hitos);revisiones=List.copyOf(revisiones);
        }
    }

    Expediente consultar(long expediente,Actor actor);
    Object validarActuacion(String capacidad,Map<String,Object> datos,Actor actor);
    Object resumenRevision(String capacidad,Map<String,Object> datos,Actor actor);
    void exigirLecturaActuacion(String capacidad,Map<String,Object> datos,Actor actor);
    Optional<?> consultarResultado(String capacidad,String clave,Actor actor);

    Expediente presentar(DatosPresentacion datos,Actor actor,ContextoSesion sesion,
            String clave,Procedencia procedencia);
    Expediente evaluar(DatosRevision datos,Actor actor,ContextoSesion sesion,
            String clave,Procedencia procedencia);
    Hito informar(DatosInformacionHito datos,Actor actor,ContextoSesion sesion,
            String clave,Procedencia procedencia);
    Hito confirmar(DatosConfirmacionHito datos,Actor actor,ContextoSesion sesion,
            String clave,Procedencia procedencia);
    Hito rectificar(DatosRectificacionHito datos,Actor actor,ContextoSesion sesion,
            String clave,Procedencia procedencia);
}
