package com.controllocal.service.materialia;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import com.controllocal.service.materialia.FuentesMaterialIa.Snapshot;
import com.controllocal.service.materialia.ProveedorMaterialIa.Salida;
import com.controllocal.service.materialia.ControlesMaterialIa.Control;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import java.time.Instant;
import java.util.*;

/** C6: selección por original, retención y artefacto; distintos del job privado, QC y consumo IA. */
public interface ResultadosMaterialIa {
    /** Callback privado de hechos causados. Sin endpoint, entrega o autoridad derivada del SID histórico. */
    default void recibirResultado(long organizacion,UUID job,String claveIntento,Salida salida){throw new com.controllocal.service.excepcion.CapacidadDeshabilitadaException();}
    /** Reconstruye desde PG y almacenamiento; nunca recibe bytes del proceso anterior. */
    default Salida recuperarRecibido(long organizacion,UUID job,String claveIntento){throw new com.controllocal.service.excepcion.CapacidadDeshabilitadaException();}
    /** Par operacion/archivo nulo = ORIGINAL. Revisión0 no fabrica una elección humana. */
    record Seleccion(UUID original,long revision,UUID operacion,UUID archivo) { }
    /** revision es de ESTA versión de retención, no de la selección del original. */
    record Version(UUID operacion,UUID original,UUID archivo,long revision,Instant generadoEn,
            Instant temporalDesde,Instant venceEn,boolean guardada,boolean protegida,boolean disponible,String estadoGestion) { }
    record GrupoCaducidad(UUID original,long revision,String huellaCausa,Instant avisoEn,
            Instant vencePrimeroEn,List<Version> versiones) {
        public GrupoCaducidad { versiones=List.copyOf(versiones); }
    }
    record Retirada(UUID id,UUID operacion,UUID archivo,long revision) { }
    record CursorConservacion(long organizacion,UUID operacion) { }
    record CandidatoConservacion(long organizacion,UUID operacion,long revision) { }
    record LoteConservacion(List<CandidatoConservacion> candidatos,CursorConservacion siguiente,boolean fin) {
        public LoteConservacion { candidatos=List.copyOf(candidatos); }
    }
    enum EstadoCausaCaducidad { VIGENTE,SUSTITUIDA,FINALIZADA,NO_EVALUABLE }
    /** Metadata interna fija: nunca claves, credenciales o aceptación implícita. */
    record Archivo(UUID operacion,UUID original,UUID archivo,String huella,String contentType,
            String proceso,String versionProceso,Instant originalRegistradoEn) { }
    record Guardado(UUID archivo,String huella) { }
    record Contenido(@com.fasterxml.jackson.annotation.JsonIgnore byte[] bytes,String contentType,String nombre) {
        public Contenido { bytes=bytes.clone(); }
        @Override @com.fasterxml.jackson.annotation.JsonIgnore public byte[] bytes(){return bytes.clone();}
    }
    Guardado guardar(Solicitud solicitud,Snapshot snapshot,Salida salida,Control control,Actor actor,ContextoSesion sesion);
    void exigirLectura(Solicitud solicitud,Snapshot snapshot,UUID archivo,Actor actor,ContextoSesion sesion);
    Contenido descargar(UUID operacion,UUID archivo,Actor actor,ContextoSesion sesion);
    /** Sólo composición Core: padre SQL real previo, sin suspensión ni inferencia. */
    Contenido leerParaPieza(UUID operacion,UUID archivo,Actor actor,ContextoSesion sesion);
    Archivo exigirArchivo(UUID operacion,UUID archivo,Actor actor);
    Seleccion seleccionActual(UUID original,Actor actor,ContextoSesion sesion);
    Seleccion seleccionar(UUID original,UUID operacion,UUID archivo,long revisionEsperada,String clave,Actor actor,ContextoSesion sesion);
    List<Version> versiones(UUID original,Actor actor,ContextoSesion sesion);
    /** Lector interno del artefacto, sin ceder el job privado ni exigir su autor histórico. */
    Version version(UUID operacion,Actor actor);
    Version guardarAlternativa(UUID operacion,UUID archivo,long revisionEsperada,String clave,Actor actor,ContextoSesion sesion);
    Version descartarTemporal(UUID operacion,UUID archivo,long revisionEsperada,String clave,Actor actor,ContextoSesion sesion);
    Version retirarAlternativa(UUID operacion,UUID archivo,long revisionEsperada,String clave,Actor actor,ContextoSesion sesion);
    List<Seleccion> consultarSelecciones(List<UUID> originales,Actor actor);
    /** Padre WRITE: todos los O ordenados, luego todos los J ordenados, antes de copias/B0. */
    List<Seleccion> bloquearSelecciones(List<UUID> originales,Actor actor);
    /** Padre TX de escritura; O/J compartidos para un snapshot de consulta, excluyendo sus mutadores. */
    List<Seleccion> bloquearSeleccionesParaLectura(List<UUID> originales,Actor actor);
    /** Revalidación del conjunto ya bloqueado; no descubre ni adquiere O/J nuevos. */
    void exigirMismasSelecciones(List<Seleccion> snapshot,Actor actor);
    /** Revalidación bajo la lectura protegida; no satisface un fence de reserva, selección o protección. */
    void exigirMismasSeleccionesParaLectura(List<Seleccion> snapshot,Actor actor);
    void protegerPorPieza(UUID pieza,long versionPieza,List<Seleccion> snapshot,Actor actor,ContextoSesion sesion);
    List<GrupoCaducidad> avisosDeCaducidad(Actor actor);
    /** Reconciliación técnica de bandeja, en su TX WRITE; nunca desde un getter readOnly. */
    boolean registrarCausaCaducidad(long organizacion,GrupoCaducidad grupo);
    EstadoCausaCaducidad estadoCausaCaducidad(long organizacion,UUID original,String huellaCausa);
    EstadoCausaCaducidad estadoCausaCaducidad(long organizacion,UUID original,String anterior,String actual);
    /** Pipeline interno: mismaTX C1→O→J→previsión B0 antes del fondo IA. */
    void bloquearOriginal(UUID original,Actor actor);
    UsoAlmacenamientoB0.RefInterna previsionar(Solicitud solicitud,Snapshot snapshot,Actor actor,ContextoSesion sesion);
    void exigirPrevisionAntesDeClaim(UUID operacion,Actor actor,ContextoSesion sesion);
    /** Previsión comprometida y namespace actual, sin nueva reserva, dinero o límite cliente. */
    UsoAlmacenamientoB0.LimiteRecepcion limiteRecepcionParaInvocar(UUID operacion,Actor actor);
    /** Callbacks internos sin permiso de usuario nuevo: no impiden conservar medición/conciliación. */
    void registrarGeneracion(long organizacion,UUID operacion);
    void resolverSinArchivo(long organizacion,UUID operacion,String causa);
    /** Sólo reconciliación Core de este job; ninguna retirada por clave de cliente. */
    void conciliarCaducidad(long organizacion,UUID operacion);
    LoteConservacion candidatosConservacion(String namespaceHash,CursorConservacion desde,int limite);
}
