package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Decision explicita que recupera la disponibilidad de un inmueble despues de
 * que su contrato terminara.
 *
 * <p><b>Existe porque terminar un contrato no devuelve el local al mercado.</b>
 * Finalizar y rescindir dejan la propiedad ALQUILADA y crean una tarea de
 * revision: alguien tiene que mirar como se entrego el local y decidir si
 * vuelve a comercializarse o se retira. Esta fila es esa decision.
 *
 * <p>La transicion de disponibilidad se audita, como todas, en
 * {@code historial_estado}. Lo que aquella no puede expresar es el CONTRATO
 * ORIGEN —no tiene columna para una entidad relacionada— y por eso esta tabla:
 * responde "por que volvio al mercado este local".
 *
 * <p>Una por contrato ({@code uq_revision_contrato}): un contrato terminado se
 * revisa una vez, y esa unicidad da ademas idempotencia al endpoint.
 */
@Entity
@Table(name = "revision_disponibilidad")
public class RevisionDisponibilidad extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_revision")
    private Long id;

    @Column(name = "id_contrato_alquiler", nullable = false)
    private Long idContratoAlquiler;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /** Siempre {@code A}: solo se revisa un local que estaba ocupado. */
    @Column(name = "disponibilidad_anterior", nullable = false, length = 1)
    private String disponibilidadAnterior;

    /** {@code D} vuelve al mercado o {@code T} se retira. Nunca {@code R}. */
    @Column(name = "disponibilidad_nueva", nullable = false, length = 1)
    private String disponibilidadNueva;

    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @Column(name = "id_actor")
    private Long idActor;

    @Column(name = "tipo_rol_actor", length = 20)
    private String tipoRolActor;

    @Column(name = "fecha_revision", nullable = false)
    private LocalDate fechaRevision;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public Long getId() { return id; }
    public Long getIdContratoAlquiler() { return idContratoAlquiler; }
    public void setIdContratoAlquiler(Long idContratoAlquiler) { this.idContratoAlquiler = idContratoAlquiler; }
    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }
    public String getDisponibilidadAnterior() { return disponibilidadAnterior; }
    public void setDisponibilidadAnterior(String disponibilidadAnterior) { this.disponibilidadAnterior = disponibilidadAnterior; }
    public String getDisponibilidadNueva() { return disponibilidadNueva; }
    public void setDisponibilidadNueva(String disponibilidadNueva) { this.disponibilidadNueva = disponibilidadNueva; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Long getIdActor() { return idActor; }
    public void setIdActor(Long idActor) { this.idActor = idActor; }
    public String getTipoRolActor() { return tipoRolActor; }
    public void setTipoRolActor(String tipoRolActor) { this.tipoRolActor = tipoRolActor; }
    public LocalDate getFechaRevision() { return fechaRevision; }
    public void setFechaRevision(LocalDate fechaRevision) { this.fechaRevision = fechaRevision; }
    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
}
