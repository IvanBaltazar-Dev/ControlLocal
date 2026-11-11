package com.controllocal.service;

import java.time.*;
import java.util.*;
import com.controllocal.service.InteraccionService.ReferenciaContactoContextual;
import com.controllocal.service.actividad.CatalogoInteracciones.CanalComunicacion;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;

/**
 * RF078/079 y CAT-E31. Preparar conserva hechos y versión; obtener el archivo
 * no comunica. Registrar una comunicación declara un hecho ya realizado.
 */
public interface ReportePropietarioService {

    String PLANTILLA="REPORTE-AVANCE-MVP-1";
    record Referencia(UUID referencia,Long version) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,com.fasterxml.jackson.databind.JsonNode v){throw new com.controllocal.service.excepcion.ReglaNegocioException("La referencia del reporte sólo admite identidad y versión.");}
    }
    /** Función declarada para la gestión, sin acreditar titularidad ni poder de firma. */
    record Destinatario(ReferenciaContactoContextual contacto,String funcionDeclarada,String procedenciaDeclaracion) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,com.fasterxml.jackson.databind.JsonNode v){throw new com.controllocal.service.excepcion.ReglaNegocioException("Dato de destinatario no admitido.");}
    }
    record DatosReporte(Referencia versionAnterior,Long versionEncargo,LocalDate periodoInicio,LocalDate periodoFin,
                        Destinatario destinatarioPrevisto,String finalidad,List<String> recomendacionesProfesionales,String plantillaVersion) {
        public DatosReporte {recomendacionesProfesionales=recomendacionesProfesionales==null?List.of():List.copyOf(recomendacionesProfesionales);}
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,com.fasterxml.jackson.databind.JsonNode v){throw new com.controllocal.service.excepcion.ReglaNegocioException("Campo no admitido en la preparación del reporte.");}
    }
    record PersonaDestinataria(long persona,String nombre,String funcionDeclarada,String procedenciaDeclaracion) { }
    record Hechos(int comunicacionesRegistradas,int comunicacionesSinFechaAcreditada,int consultasComercialesRegistradas,int visitasRealizadas,
                   int visitasSinFechaAcreditada,int ofertasRegistradas,int contraofertasRegistradas,
                   Map<String,Integer> resultadosComunicativos,String cobertura) {
        public Hechos {resultadosComunicativos=Map.copyOf(resultadosComunicativos);}
    }
    record VistaPrevia(long encargo,String codigoEncargo,String operacion,String situacionActual,long versionEncargo,
                       LocalDate desde,LocalDate hasta,Instant corte,Hechos hechos,List<String> proximasActuaciones) {
        public VistaPrevia {proximasActuaciones=List.copyOf(proximasActuaciones);}
    }
    record Archivo(UUID referencia,String nombre,String contentType,String huella) { }
    record FichaReporte(Referencia reporte,long encargo,String plantillaVersion,PersonaDestinataria destinatarioPrevisto,
                        String finalidad,VistaPrevia avance,List<String> recomendacionesProfesionales,Archivo archivo,Instant preparadoEn) {
        public FichaReporte {recomendacionesProfesionales=List.copyOf(recomendacionesProfesionales);}
    }
    record CabeceraReporte(Long antecedenteSinVersion,Referencia reporte,long encargo,LocalDate desde,LocalDate hasta,
                            Instant preparadoEn,String disponibilidad) { }
    record AntecedenteReporte(long id,long encargo,LocalDate fecha,LocalDate desde,LocalDate hasta,int contadorHistoricoInteracciones,
                              int contadorHistoricoVisitas,Instant registradoEn,String alcance) { }
    record DatosComunicacion(Referencia reporte,Destinatario destinatarioReal,LocalDate fechaReal,CanalComunicacion canal,
                            String medioReal,String constancia,List<UUID> evidencias) {
        public DatosComunicacion {evidencias=evidencias==null?List.of():List.copyOf(evidencias);}
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,com.fasterxml.jackson.databind.JsonNode v){throw new com.controllocal.service.excepcion.ReglaNegocioException("Campo no admitido en la comunicación realizada.");}
    }
    /** La corrección conserva la misma versión de reporte; no genera otro envío. */
    record Rectificacion(UUID comunicacion,Long versionEsperada,Destinatario destinatarioReal,LocalDate fechaReal,
                         CanalComunicacion canal,String medioReal,String constancia,List<UUID> evidencias,String motivo) {
        public Rectificacion {evidencias=evidencias==null?List.of():List.copyOf(evidencias);}
        @com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,com.fasterxml.jackson.databind.JsonNode v){throw new com.controllocal.service.excepcion.ReglaNegocioException("Campo no admitido en la rectificación de la comunicación.");}
    }
    record Comunicacion(UUID referencia,long version,Referencia reporte,PersonaDestinataria destinatarioReal,
                        LocalDate fechaReal,CanalComunicacion canal,String medioReal,String constancia,List<UUID> evidencias,
                        String naturaleza,Instant registradaEn,Instant rectificadaEn,String motivoRectificacion) {
        public Comunicacion {evidencias=List.copyOf(evidencias);}
    }
    record HistorialComunicacion(Comunicacion vigente,List<Comunicacion> antecedentes) {
        public HistorialComunicacion {antecedentes=List.copyOf(antecedentes);}
    }
    List<CabeceraReporte> listar(long encargo,int pagina,int tamano,Actor actor);
    VistaPrevia preview(long encargo,LocalDate desde,LocalDate hasta,Actor actor);
    FichaReporte preparar(long encargo,DatosReporte datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    FichaReporte consultar(Referencia reporte,Actor actor);
    Archivo obtenerArchivo(Referencia reporte,Actor actor,ContextoSesion sesion);
    FichaReporte exigirLecturaArchivo(UUID archivo,Actor actor);
    AntecedenteReporte consultarAntecedente(long id,Actor actor);
    Comunicacion registrarComunicacion(DatosComunicacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    List<HistorialComunicacion> comunicaciones(long encargo,int pagina,int tamano,Actor actor);
    Comunicacion consultarComunicacion(UUID referencia,long version,Actor actor);
    Rectificacion validarRectificacion(Rectificacion datos,Actor actor);
    void exigirLecturaRectificacion(Rectificacion datos,Actor actor);
    Comunicacion rectificar(Rectificacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Optional<Comunicacion> resultadoRectificacion(String clave,Actor actor);
    Optional<FichaReporte> resultadoPreparacion(String clave,Actor actor);
    Optional<Comunicacion> resultadoComunicacion(String clave,Actor actor);
}
