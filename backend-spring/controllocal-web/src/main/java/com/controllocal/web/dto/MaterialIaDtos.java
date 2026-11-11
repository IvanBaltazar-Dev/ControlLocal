package com.controllocal.web.dto;

import com.controllocal.service.materialia.MaterialIaService;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa;
import com.fasterxml.jackson.annotation.*;
import java.util.UUID;

/** Entrada comercial cerrada: cuenta, SID, proveedor, tarifas, evidencia y QC los resuelve Core. */
public final class MaterialIaDtos {
    private MaterialIaDtos(){ }
    public record Recurso(@JsonProperty(value="tipo",required=true) @JsonSetter(nulls=Nulls.FAIL) String tipo,
                          @JsonProperty(value="referencia",required=true) @JsonSetter(nulls=Nulls.FAIL) String referencia) {
        @JsonAnySetter public void desconocido(String nombre,Object valor){throw new IllegalArgumentException("El recurso contiene campos fuera de contrato.");}
    }
    public record Solicitud(@JsonProperty(value="operacion",required=true) @JsonSetter(nulls=Nulls.FAIL) UUID operacion,
            @JsonProperty(value="accion",required=true) @JsonSetter(nulls=Nulls.FAIL) MaterialIaService.Accion accion,
            @JsonProperty(value="recurso",required=true) @JsonSetter(nulls=Nulls.FAIL) Recurso recurso,
            String versionOrigen,UUID fotoOriginal,String instruccion) {
        @JsonAnySetter public void desconocido(String nombre,Object valor){throw new IllegalArgumentException("La solicitud contiene campos fuera de contrato.");}
        public MaterialIaService.Solicitud aCore(){
            return new MaterialIaService.Solicitud(operacion,accion,recurso==null?null:new MaterialIaService.Recurso(recurso.tipo(),recurso.referencia()),
                    versionOrigen,fotoOriginal,instruccion);
        }
    }
    /** Los dos nulos significan intención ORIGINAL; omitirlos nunca deselecciona. */
    public record SeleccionFoto(@JsonProperty(value="operacion",required=true) UUID operacion,
            @JsonProperty(value="archivo",required=true) UUID archivo,
            @JsonProperty(value="revisionEsperada",required=true) @JsonSetter(nulls=Nulls.FAIL) long revisionEsperada) {
        public SeleccionFoto { if((operacion==null)!=(archivo==null))throw new IllegalArgumentException("Indica ambos datos del resultado o ambos nulos para usar el original."); }
        @JsonAnySetter public void desconocido(String nombre,Object valor){throw new IllegalArgumentException("La selección contiene campos fuera de contrato.");}
    }
    /** CAS de esta versión, distinto del CAS de selección por original. */
    public record GestionVersion(@JsonProperty(value="revisionEsperada",required=true) @JsonSetter(nulls=Nulls.FAIL) long revisionEsperada) {
        @JsonAnySetter public void desconocido(String nombre,Object valor){throw new IllegalArgumentException("La gestión contiene campos fuera de contrato.");}
    }
    public record Consulta(@JsonProperty(value="operacion",required=true) @JsonSetter(nulls=Nulls.FAIL) UUID operacion) {
        @JsonAnySetter public void desconocido(String nombre,Object valor){throw new IllegalArgumentException("La consulta contiene campos fuera de contrato.");}
    }
    public record CapturaTratamiento(@JsonProperty(value="evento",required=true) @JsonSetter(nulls=Nulls.FAIL) UUID evento,
            @JsonProperty(value="referenciaPresentacion",required=true) @JsonSetter(nulls=Nulls.FAIL) String referenciaPresentacion,
            @JsonProperty(value="versionCobertura",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionCobertura,
            @JsonProperty(value="decision",required=true) @JsonSetter(nulls=Nulls.FAIL) EvidenciasTratamientoMaterialIa.Decision decision) {
        @JsonAnySetter public void desconocido(String nombre,Object valor){throw new IllegalArgumentException("La captura contiene campos fuera de contrato.");}
        public EvidenciasTratamientoMaterialIa.Captura aCore(){return new EvidenciasTratamientoMaterialIa.Captura(evento,referenciaPresentacion,versionCobertura,decision);}
    }
}
