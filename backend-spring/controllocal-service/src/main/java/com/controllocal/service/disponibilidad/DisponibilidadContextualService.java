package com.controllocal.service.disponibilidad;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad;
import com.controllocal.service.matching.ContratoMatching.Causa;
import com.controllocal.service.soporte.Procedencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** CAT-R21/E28/D2: hecho específico; no programa tareas ni cambia estado contractual. */
public interface DisponibilidadContextualService {
    String ASPECTO = "DISPONIBILIDAD_COMERCIAL";
    enum TipoRecurso { ENCARGO, DEMANDA }
    enum PrecisionHecho { INSTANTE, FECHA }
    enum Aplicacion { APLICADA, ANTECEDENTE_CONSERVADO, PRECISION_PENDIENTE }

    /** Clave calculada por Core a partir del objeto actual; nunca incluye responsable o versión. */
    record Clave(TipoRecurso tipo, long idRecurso, OperacionInmobiliaria operacion,
                  String aspecto, String contexto) { }
    /** Una sola precisión; fecha sin hora no se presenta como si hubiera ocurrido a medianoche. */
    record DatosConfirmacion(TipoRecurso tipo, long idRecurso, long versionRecursoObservada,
                              long versionConfirmacionEsperada, Boolean disponible,
                              Instant fechaHecho, LocalDate fechaSinHora, String fuente,
                              String evidenciaRef) { }
    /** Registro original privado, no tarjeta Network. */
    record Registro(long id, Clave clave, long version, long versionRecursoObservada,
                     boolean disponible, PrecisionHecho precision, Instant fechaHecho,
                     LocalDate fechaSinHora, Instant baseFrescura, Instant registradoEn,
                     String fuente, String evidenciaRef, long idPersonaConfirmante,
                     long idRolConfirmante) { }
    /** Secuencia/CAS no es el hecho vigente: un antecedente posterior en registro no renueva frescura. */
    record Vista(Clave clave, long versionRecurso, long versionConfirmacion, Registro vigente, Causa indeterminacion) { }
    /** vigente es el seleccionado en esta actuación; consultar obtiene la lectura actualmente autorizada. */
    record ResultadoConfirmacion(Registro registrado, Aplicacion aplicacion, Registro vigente, Causa indeterminacion) { }

    Vista consultar(TipoRecurso tipo, long idRecurso, Actor actor);
    ResultadoConfirmacion confirmar(DatosConfirmacion datos, Actor actor, ContextoSesion sesion,
                        String claveIdempotencia, Procedencia procedencia);
    Optional<ResultadoConfirmacion> consultarResultado(String claveIdempotencia, Actor actor);
    /** Sólo lectura local legítima. Network usa la proyección mínima del repo DESPUÉS de su permiso publicado. */
    ConfirmacionDisponibilidad paraMatching(TipoRecurso tipo, long idRecurso, Actor actor);

    static String contextoUnidad(long idPropiedad) { return "UNIDAD:" + idPropiedad; }
    static String contextoComposicion(long idComposicion) { return "COMPOSICION:" + idComposicion; }
    static String contextoDemanda(long idDemanda) { return "DEMANDA:" + idDemanda; }
}
