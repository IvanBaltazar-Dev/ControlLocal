package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Casos de uso de captacion (F2). Records espejo del contrato CONGELADO
 * (Dtos.CaptacionRequest/CaptacionResponse/ReasignacionCaptacionResponse v1).
 * Maquina P->{A|O|R}, O->(editar)->P, A->{C|V} sobre soporte/Transiciones
 * (auditada); la reasignacion es un evento de actor (tabla-evento), no una
 * transicion. El alcance por rol se aplica en el service.
 */
public interface CaptacionService {

    /** Espejo de CaptacionRequest. idLocal = propiedad; idAgente lo fija el actor (agente). */
    record DatosCaptacion(String codigoCaptacion, LocalDate fechaCaptacion, LocalDate fechaInicioVigencia,
                          LocalDate fechaFinVigencia, BigDecimal comisionPactada, String observaciones,
                          Long idLocal, Long idAgente, String motivoOperacion, Integer urgencia,
                          Boolean exclusividad, String tipoOperacion, BigDecimal importeReferencia,
                          String monedaReferencia, String tipoComision, String baseCalculo,
                          BigDecimal valorComision, String monedaComision, String tratamientoIgv,
                          String motivoSinComision) {
        public DatosCaptacion(String codigoCaptacion, LocalDate fechaCaptacion,
                              LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
                              BigDecimal comisionPactada, String observaciones, Long idLocal,
                              Long idAgente, String motivoOperacion, Integer urgencia,
                              Boolean exclusividad) {
            this(codigoCaptacion, fechaCaptacion, fechaInicioVigencia, fechaFinVigencia,
                    comisionPactada, observaciones, idLocal, idAgente, motivoOperacion,
                    urgencia, exclusividad, null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * <b>Que puede hacer QUIEN PREGUNTA con este encargo</b> (D-P0-12).
     *
     * <p>Viaja resuelta por el Core y no la deduce el cliente. Sin esto, la
     * pantalla del encargo tendria que escribir su propia version de tres reglas
     * —«soy el agente y esta pendiente», «soy broker y lo superviso», «esta
     * activa»— y una copia de una regla de autoridad diverge siempre hacia el
     * lado que pinta un boton que el backend va a rechazar. Es el mismo motivo
     * por el que {@code Responsabilidad.puedeEditar} viaja resuelta.
     *
     * <p>Cada booleano lo produce el <b>mismo</b> predicado que despues deniega
     * el comando, no una segunda tabla de decision.
     *
     * <p><b>No autoriza nada</b>: el comando revalida. Y llega {@code null} en
     * los listados —donde la pregunta no es «que puedo hacer con este» sino «que
     * hay»—, asi que por NON_NULL el campo no viaja ahi.
     *
     * @param puedeEditar  {@code PUT /captaciones/{id}}: su propio agente, y
     *                     solo mientras el encargo sea editable (P u O)
     * @param puedeRevisar {@code POST /captaciones/{id}/decision}: el BROKER que
     *                     supervisa a su agente, y solo mientras sea editable.
     *                     El TENANT_ADMIN <b>no</b> — es operacion comercial y el
     *                     gobierno no la hereda (D-S0-17 fila 5)
     * @param puedeCerrar  {@code POST /captaciones/{id}/cierre}: el mismo BROKER,
     *                     y solo si el encargo esta ACTIVO (D-S0-17 fila 7)
     * @param puedeReasignar {@code POST /captaciones/{id}/reasignar}: el BROKER
     *                     que supervisa hoy al agente que lo lleva, <b>y tambien
     *                     el TENANT_ADMIN</b> — reasignar entre equipos es
     *                     organigrama, no operacion comercial (D-S0-17 fila 6),
     *                     asi que es la unica de las cuatro que el gobierno del
     *                     tenant si hereda. Un AGENTE nunca, tampoco sobre el
     *                     suyo. Son las guardas del comando <b>sin el
     *                     destino</b>, que en la ficha todavia no existe: el
     *                     comando revalida ademas el destino, su elegibilidad
     *                     (D-P0-7) y el agente observado (D-P0-9)
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
    Pagina<FichaCaptacion> reasignables(int pagina, int tamano, String q, Actor actor);

    FichaCaptacion obtener(long id, Actor actor);

    FichaCaptacion obtenerPorCodigo(String codigo, Actor actor);

    FichaCaptacion registrar(DatosCaptacion datos, Actor actor);

    FichaCaptacion actualizar(long id, DatosCaptacion datos, Actor actor);

    /** Decision del broker: accion APROBAR/OBSERVAR/RECHAZAR (o A/O/R). */
    FichaCaptacion decidir(long id, String accion, String observacion, Actor actor);

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
    FichaCaptacion reasignar(long id, long idAgenteNuevo, String motivo,
                             long idAgenteObservado, Actor actor);

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

    FichaCaptacion cerrar(long id, String motivo, Actor actor);

    /** Cierre causal usado por la cascada contractual. */
    FichaCaptacion cerrarPorContrato(long id, LocalDate fecha, Actor actor, String detalle);

    /** Historial de reasignaciones (gobierno broker/admin), el mas reciente primero. */
    List<FichaReasignacion> listarReasignaciones(Actor actor);
}
