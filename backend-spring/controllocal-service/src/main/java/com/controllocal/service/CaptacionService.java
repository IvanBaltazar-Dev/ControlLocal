package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;

/**
 * Lecturas comerciales de Encargo y gobierno acotado de su reasignacion.
 * Las mutaciones de preparacion, peticion/revision y fin corresponden a
 * {@link com.controllocal.service.encargo.EncargoService}; sus estados y
 * facultades versionadas se consultan en ese contrato. Esta proyeccion no
 * concede aprobacion ni historia comercial por ejercer gobierno.
 */
public interface CaptacionService {


    /**
     * Capacidades resueltas por Core para los consumidores de esta proyeccion.
     * Los tres campos de mutaciones sustituidas permanecen en false durante la
     * transicion del consumidor Angular; no representan rutas ejecutables.
     * Las facultades del ciclo vigente se entregan con sus recursos versionados
     * en EncargoService, sin que el cliente replique sus reglas.
     *
     * <p>Reasignar conserva el predicado canonico del comando sin el destino:
     * BROKER dentro de su equipo o TENANT_ADMIN para el cruce autorizado;
     * AGENTE no reasigna. El comando revalida ademas contexto, version observada,
     * responsable y elegibilidad actuales del destino. La lectura de gobierno
     * emplea su proyeccion minima, no esta ficha comercial.
     *
     * @param puedeEditar false; la edicion vigente pertenece a EncargoService
     * @param puedeRevisar false; se decide una peticion versionada de Encargo
     * @param puedeCerrar false; el fin vigente usa el caso canonico de Encargo
     * @param puedeReasignar capacidad de reasignacion actual; el comando reautoriza
     */
    record Capacidades(boolean puedeEditar, boolean puedeRevisar, boolean puedeCerrar,
                       boolean puedeReasignar) {
    }

    /**
     * <b>Un destino ya elegible para reasignar ESTE encargo</b> (D-P0-7 +
     * D-P0-12).
     *
     * <p>Lleva lo justo para elegir en una lista —quien es, su codigo y su
     * zona— y <b>ningun estado administrativo</b>: si el agente aparece es que
     * cumple las cinco condiciones, y si no aparece no se publica por que. El
     * motivo de una ausencia es informacion de la ficha del agente, no de un
     * selector de reasignacion.
     *
     * <p>{@code idAgente} es el {@code persona_rol.id} del rol AGENTE, el mismo
     * identificador que espera {@link #reasignar}.
     */
    record CandidatoAgente(Long idAgente, String nombre, String codigoAgente,
                           String zonaAsignada) {
    }

    /** Espejo de CaptacionResponse (idAgente/idBrokerRevisor = persona_rol.id de esos roles). */
    record FichaCaptacion(Long id, String codigoCaptacion, LocalDate fechaCaptacion,
                          LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
                          BigDecimal comisionPactada, String observaciones, String estado,
                          String motivoOperacion, Integer urgencia, Boolean exclusividad,
                          String observacionRevision, LocalDateTime fechaRevision, Long idLocal,
                          String direccionLocal, String distritoLocal, BigDecimal areaM2, String rubro,
                          String propietarioNombre, Long idAgente, String agenteNombre,
                          Long idBrokerRevisor, String fotoPortadaClave, String tipoOperacion,
                          BigDecimal importeReferencia, String monedaReferencia,
                          String tipoComision, String baseCalculo, BigDecimal valorComision,
                          String monedaComision, String tratamientoIgv, String motivoSinComision,
                          LocalDate fechaCierre, String motivoCierre, String detalleMotivoCierre,
                          Capacidades capacidades) {
    }

    /** Espejo de ReasignacionCaptacionResponse. */
    record FichaReasignacion(Long idReasignacion, Long idCaptacion, String codigoCaptacion,
                             String direccionLocal, Long idAgenteAnterior, String agenteAnteriorNombre,
                             Long idAgenteNuevo, String agenteNuevoNombre, Long idBroker,
                             String brokerNombre, LocalDateTime fechaCambio, String motivo) {
    }

    /** RF017: datos mínimos del expediente para elegir y comprobar el responsable. */
    record EncargoReasignable(Long id, String codigoCaptacion, String estado,
                             String direccionLocal, Long idAgente, String agenteNombre,long version) { }

    /** Efecto real de gobierno. No incorpora la ficha ni condiciones del Encargo. */
    record ResultadoReasignacion(Long id, String codigoCaptacion, String estado,
                                 Long idAgenteAnterior, Long idAgente, Long idReasignacion,long version) { }
    record DatosReasignacion(long idAgenteNuevo,long idAgenteObservado,
            @com.fasterxml.jackson.annotation.JsonProperty(value="versionEsperada",required=true)
            @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL) long versionEsperada,String motivo) { }

    /**
     * Filtros opcionales del listado Angular. Son una extension aditiva: si
     * se omiten, {@code GET /captaciones} conserva exactamente el resultado
     * del cable v1 (mismo alcance, orden y paginacion).
     */
    record FiltrosCaptacion(String estado, Long idAgente, String q,
                            int pagina, int tamano) {
    }

    /**
     * Filtros aditivos de la bandeja de revision. Sin filtros conserva el
     * cable v1: todas las P/O dentro del alcance del broker o administrador.
     */
    record FiltrosPendientes(String estado, Long idAgente, String q,
                             int pagina, int tamano) {
    }

    Pagina<FichaCaptacion> listar(FiltrosCaptacion filtros, Actor actor);

    /**
     * Cartera del equipo vista POR INMUEBLE: una fila por propiedad captada,
     * con los datos de su captacion mas reciente.
     *
     * <p>Extension aditiva (no toca el contrato congelado). Existe porque la
     * pantalla del broker necesita deduplicar por inmueble, y eso no se puede
     * hacer sobre una pagina de captaciones: obligaria a descargar todas.
     * Filtro, orden, deduplicacion, paginacion y conteo bajan a SQL.
     */
    record PropiedadEquipo(Long idPropiedad, Long idCaptacion, String codigoCaptacion, String estado,
                           String codigoLocal, String direccion, String distrito, String rubro,
                           BigDecimal areaM2, Long idAgente, String agenteNombre) {
    }

    record ResumenEquipo(long propiedades, long conCaptacionActiva, long agentesConCartera,
                         long distritos) {
    }

    record FiltrosEquipo(String texto, String distrito, int pagina, int tamano) {
    }

    Pagina<PropiedadEquipo> carteraDelEquipo(FiltrosEquipo filtros, Actor actor);

    ResumenEquipo resumenCarteraDelEquipo(String texto, Actor actor);

    /** Distritos presentes en la cartera del equipo, para el filtro data-driven. */
    List<String> distritosDelEquipo(String texto, Actor actor);

    /** Bandeja del broker/admin: captaciones pendientes de revision (P u O). */
    Pagina<FichaCaptacion> pendientes(FiltrosPendientes filtros, Actor actor);

    /** Captaciones ACTIVAS en el alcance del broker/admin (candidatas a reasignar). */
    Pagina<EncargoReasignable> reasignables(int pagina, int tamano, String q, Actor actor);
    EncargoReasignable consultarReasignable(long id,Actor actor);

    FichaCaptacion obtener(long id, Actor actor);

    FichaCaptacion obtenerPorCodigo(String codigo, Actor actor);


    /**
     * <b>La unica puerta que mueve el agente de un encargo</b> (D-P0-9/D-P0-10
     * aplicados al ENCARGO).
     *
     * <p>El comando declara <b>desde donde</b>: {@code idAgenteObservado} es el
     * agente que quien decide <b>vio</b>. Si al ejecutarse ya no es ese, la
     * respuesta es <b>409</b> y no se ha escrito nada — no se reinterpreta un
     * «cambia A por C» en «cambia B por C», que seria una decision distinta de
     * la que se firmo. De un estado concreto parte <b>exactamente una</b>
     * reasignacion legitima.
     *
     * <p>No hay sobrecarga sin {@code idAgenteObservado} <b>a proposito</b>:
     * una segunda firma seria la puerta por la que volveria a entrar una
     * reasignacion que no declara de donde parte.
     */
    ResultadoReasignacion reasignar(long id,DatosReasignacion datos,Actor actor,ContextoSesion sesion,
                                    String claveComando,Procedencia procedencia);

    /**
     * <b>Los destinos que ESTE actor puede elegir para ESTE encargo</b>
     * (D-P0-12).
     *
     * <p>El Core responde «que destinos puedo seleccionar» ya resuelto, con las
     * cinco condiciones de D-P0-7 aplicadas <b>en la base</b> y el agente actual
     * fuera. Angular no decide autoridad: sin esta superficie, la pantalla de
     * reasignaciones tenia que pedir la lista de agentes del tenant y depurarla
     * con su propia copia de la regla —dos condiciones de las seis, resueltas en
     * el cliente sobre una pagina—, que es una lista de permisos viviendo fuera
     * del Core.
     *
     * <p>Un id de otra corredora responde <b>404</b>; un actor que no puede
     * reasignar este encargo —el mismo predicado que apaga la capacidad—
     * responde <b>403</b>, y no una lista vacia.
     */
    Pagina<CandidatoAgente> candidatosAReasignacion(long id, String texto, int pagina,
                                                    int tamano, Actor actor);


    /** Historial de reasignaciones (gobierno broker/admin), el mas reciente primero. */
    List<FichaReasignacion> listarReasignaciones(Actor actor);
}
