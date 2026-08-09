package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.persona.DetalleBroker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Decision del broker sobre una solicitud. NO es Transicionable: es un EVENTO
 * con resultado (como {@code ReasignacionCaptacion}), y lo que transiciona es
 * la SOLICITUD que este evento mueve.
 *
 * <p>Dos reglas del cable que sorprenden:
 * <ul>
 *   <li>el broker <b>no elige</b> el tipo de evaluacion: se DERIVA del
 *       resultado —observada ⇒ OBSERVACION, aprobada/rechazada ⇒ FINAL— para
 *       conservar la trazabilidad. Lo garantiza tambien un CHECK de la BD;</li>
 *   <li>solo puede existir <b>una evaluacion FINAL por solicitud</b> (indice
 *       unico parcial nativo, donde la v1 usaba una columna generada).</li>
 * </ul>
 */
@Entity
@Table(name = "evaluacion_solicitud")
public class EvaluacionSolicitud extends EntidadDeOrganizacion {

    public static final String ENTIDAD_TIPO = "EVALUACION";

    /** ResultadoEvaluacionSolicitud. */
    public static final String APROBADA = "A";
    public static final String RECHAZADA = "R";
    public static final String OBSERVADA = "O";
    public static final Set<String> RESULTADOS = Set.of("A", "R", "O");

    /** TipoEvaluacionSolicitud (derivado, no elegido). */
    public static final String PRELIMINAR = "P";
    public static final String OBSERVACION = "O";
    public static final String FINAL = "F";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evaluacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private SolicitudAlquiler solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_broker", nullable = false)
    private DetalleBroker broker;

    /**
     * La fija el caso de uso al registrar (la v1 hace lo mismo en su BL); el
     * DEFAULT now() de la BD es la red de seguridad. Con
     * {@code insertable = false} la respuesta del alta viajaria sin fecha.
     */
    @Column(name = "fecha_evaluacion", nullable = false)
    private OffsetDateTime fechaEvaluacion;

    @Column(name = "resultado", nullable = false, length = 1)
    private String resultado;

    @Column(name = "tipo_evaluacion", nullable = false, length = 1)
    private String tipoEvaluacion;

    @Column(name = "observaciones")
    private String observaciones;

    /**
     * El tipo NO lo elige el broker: observada ⇒ OBSERVACION, cualquier otro
     * resultado ⇒ FINAL. Se aplica al fijar el resultado.
     */
    public static String tipoSegunResultado(String resultado) {
        return OBSERVADA.equals(resultado) ? OBSERVACION : FINAL;
    }

    /** Al fijar el resultado se deriva el tipo, para que no puedan desincronizarse. */
    public void fijarResultado(String resultado) {
        this.resultado = resultado;
        this.tipoEvaluacion = tipoSegunResultado(resultado);
    }

    /** Sella el momento de la decision si el caso de uso no trajo uno. */
    public void sellarFecha() {
        if (fechaEvaluacion == null) {
            fechaEvaluacion = OffsetDateTime.now();
        }
    }

    public boolean esFinal() {
        return FINAL.equals(tipoEvaluacion);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SolicitudAlquiler getSolicitud() {
        return solicitud;
    }

    public void setSolicitud(SolicitudAlquiler solicitud) {
        this.solicitud = solicitud;
    }

    public DetalleBroker getBroker() {
        return broker;
    }

    public void setBroker(DetalleBroker broker) {
        this.broker = broker;
    }

    public OffsetDateTime getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public String getResultado() {
        return resultado;
    }

    public String getTipoEvaluacion() {
        return tipoEvaluacion;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
