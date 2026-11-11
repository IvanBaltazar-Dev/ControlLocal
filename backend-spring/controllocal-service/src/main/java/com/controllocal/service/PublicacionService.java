package com.controllocal.service;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** RF021–027: un anuncio del Encargo; preparar una pieza no lo publica. */
public interface PublicacionService {
    String CREAR_BORRADOR="crear_borrador_anuncio", EDITAR_BORRADOR="editar_borrador_anuncio",
            PUBLICAR="publicar_anuncio", ACTUALIZAR_VISIBLE="actualizar_anuncio_visible",
            PAUSAR="pausar_anuncio", REANUDAR="reanudar_anuncio", FINALIZAR="finalizar_difusion_anuncio";
    record DatosContenido(String canal,String urlPublicacion,BigDecimal importePublicado,
                          String moneda,String tituloAnuncio,String codigoOrigen) { }
    /** Hecho externo conocido: nunca se deduce del estado local. */
    record EstadoExterno(String estado,Instant fechaEfectiva,String procedencia) { }
    record DatosBorrador(@JsonProperty(value="encargo",required=true) @JsonSetter(nulls=Nulls.FAIL) long encargo,
                         @JsonProperty(value="versionEncargo",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEncargo,
                         Long publicacion,Long versionPublicacion,
                         DatosContenido contenido) { }
    /** huellaContexto se sella al preparar y se compara al confirmar. */
    record DatosCambio(@JsonProperty(value="encargo",required=true) @JsonSetter(nulls=Nulls.FAIL) long encargo,
                       @JsonProperty(value="versionEncargo",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionEncargo,
                       @JsonProperty(value="publicacion",required=true) @JsonSetter(nulls=Nulls.FAIL) long publicacion,
                       @JsonProperty(value="versionPublicacion",required=true) @JsonSetter(nulls=Nulls.FAIL) long versionPublicacion,
                       DatosContenido contenido,EstadoExterno estadoExterno,String huellaContexto,String motivo) { }
    record EvaluacionPublicabilidad(boolean cumple,long encargo,long versionEncargo,long propiedad,
            long versionFisica,String canal,List<String> faltantesFicha,List<String> faltantesEncargo,
            List<String> motivos,String versionReglas,Map<String,Long> versionesOrigen,
            String huellaContexto,Instant evaluadaEn,Long composicion,Long versionComposicion) { }
    record FichaPublicacion(Long id,Long idEncargo,String canal,String tituloAnuncio,
            BigDecimal importePublicado,String moneda,String importeRotulo,String estado,String estadoRotulo,
            LocalDateTime fechaPublicacion,LocalDateTime fechaBaja,String urlPublicacion,String codigoOrigen,
            long version,long versionEncargo,long versionFisica,EstadoExterno estadoExterno,
            Long composicion,Long versionComposicion) { }
    record VersionPublicada(long version,String actuacion,Instant registradaEn,Map<String,Object> contenido,String motivo) { }

    EvaluacionPublicabilidad evaluarPublicabilidad(long encargo,String canal,Actor actor);
    EvaluacionPublicabilidad exigirPublicabilidad(long encargo,String canal,Actor actor);
    /** RF080: evalúa el inmueble concreto de la pieza dentro del objeto autorizado del Encargo. */
    EvaluacionPublicabilidad exigirPublicabilidadDePieza(long encargo,long propiedadFicha,String canal,Actor actor);
    List<FichaPublicacion> listarDeEncargo(long encargo,Actor actor);
    FichaPublicacion obtener(long encargo,long publicacion,Actor actor);
    List<VersionPublicada> consultarHistoria(long encargo,long publicacion,Actor actor);
    FichaPublicacion crearBorradorAnuncio(DatosBorrador datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    FichaPublicacion editarBorradorAnuncio(DatosBorrador datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    DatosCambio validarActuacion(String capacidad,DatosCambio datos,Actor actor);
    Object previsualizarActuacion(String capacidad,Map<String,Object> datos,Actor actor);
    void exigirLecturaActuacion(String capacidad,Map<String,Object> datos,Actor actor);
    FichaPublicacion publicarAnuncio(DatosCambio datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    FichaPublicacion actualizarAnuncioVisible(DatosCambio datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    FichaPublicacion pausarAnuncio(DatosCambio datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    FichaPublicacion reanudarAnuncio(DatosCambio datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    FichaPublicacion finalizarDifusionAnuncio(DatosCambio datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Optional<FichaPublicacion> consultarResultado(String capacidad,String clave,Actor actor);
}
