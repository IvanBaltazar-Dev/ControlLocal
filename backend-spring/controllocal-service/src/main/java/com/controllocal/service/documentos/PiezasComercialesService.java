package com.controllocal.service.documentos;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.*;

/** AC165–171. La preparación privada nunca acredita publicación ni envío externo. */
public interface PiezasComercialesService {
    String OBTENER="obtener_pieza_para_difusion";
    enum Formato { PDF_A4, PNG_VERTICAL }
    /** Versión de elección revisada; no equivale a autorizar el archivo por conocer su UUID. */
    record Derivada(UUID operacion,UUID archivo,long versionSeleccion) { }
    record Foto(UUID original,String ambiente,String rotulo,@JsonInclude(JsonInclude.Include.NON_NULL) Derivada derivada) {
        public Foto(UUID original,String ambiente,String rotulo){this(original,ambiente,rotulo,null);}
    }
    /** Canal elegido expresamente para esta pieza; no se toma el teléfono personal por defecto. */
    record Contacto(String tipo,String valor) { }
    record Preparacion(@JsonProperty(value="encargo",required=true) @JsonSetter(nulls=Nulls.FAIL) long encargo,
            @JsonProperty(value="versionEncargo",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEncargo,
            @JsonProperty(value="versionFisica",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionFisica,String canal,
            List<Foto> fotos,String versionGaleria,Formato formato,String tema,Contacto contacto,Long propiedadFicha){
        public Preparacion { fotos=fotos==null?List.of():List.copyOf(fotos); }
    }
    record Regeneracion(UUID pieza,@JsonProperty(value="versionContenido",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionContenido,Preparacion preparacion) { }
    record Entrega(UUID pieza,@JsonProperty(value="versionContenido",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionContenido,String huellaOrigen) { }
    record Estado(UUID pieza,long versionContenido,long encargo,String plantilla,String versionPlantilla,
            Formato formato,String estadoArchivo,boolean desactualizada,List<String> causas,
            String huellaOrigen,UUID archivoPrivado,UUID archivoDifusion) {
        public Estado { causas=List.copyOf(causas); }
    }
    record Archivo(UUID referencia,UUID pieza,long versionContenido,String finalidad,String nombre,
                   String contentType,String huella) { }
    record Descarga(Archivo archivo,byte[] contenido){
        public Descarga { contenido=contenido.clone(); }
        @Override public byte[] contenido(){return contenido.clone();}
    }
    Map<String,Object> consultarPlantillasYFormatos(long encargo,Actor actor);
    boolean composicionHabilitada();
    SeleccionFotosContextualesService.Observacion consultarFotosPermitidas(long encargo,Long propiedadFicha,Actor actor);
    Estado consultarEstado(UUID pieza,Actor actor);
    Estado preparar(Preparacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Archivo previsualizar(UUID pieza,long versionContenido,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Estado regenerar(Regeneracion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Entrega validarEntrega(Entrega datos,Actor actor);
    Object resumenEntrega(Entrega datos,Actor actor);
    void exigirLecturaEntrega(Entrega datos,Actor actor);
    Archivo obtenerParaDifusion(Entrega datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Optional<Archivo> consultarResultadoEntrega(String clave,Actor actor);
    Optional<Map<String,Object>> consultarResultadoGeneracion(String capacidad,String clave,Actor actor);
    Descarga descargar(UUID archivo,Actor actor);
}
