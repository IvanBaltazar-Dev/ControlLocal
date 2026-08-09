package com.controllocal.domain.auditoria;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.LocalDate;

/**
 * Transicion de estado de cualquier entidad auditable (RC-002).
 * La escritura llega SOLO desde el mecanismo de auditoria de la capa
 * service (aspecto/listener), nunca por llamadas manuales dispersas.
 * id_actor NULL = actor de sistema (jobs).
 */
@Entity
@Table(name = "historial_estado")
public class HistorialEstado extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long id;

    /** Codigo del catalogo maestro entidad_tipo. */
    @Column(name = "entidad_tipo", nullable = false, length = 30)
    private String entidadTipo;

    @Column(name = "id_entidad", nullable = false)
    private Long idEntidad;

    @Column(name = "estado_anterior", length = 1)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 1)
    private String estadoNuevo;

    /** Identidad unica del actor: persona.id (no ids por subtipo). */
    @Column(name = "id_actor")
    private Long idActor;

    /** Rol con el que el actor ejecuto la transicion (para el alcance por rol). */
    @Column(name = "tipo_rol_actor", length = 20)
    private String tipoRolActor;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @Column(name = "fecha_evento", nullable = false)
    private OffsetDateTime fechaEvento;

    @Column(name = "fecha_efectiva")
    private LocalDate fechaEfectiva;

    @PrePersist
    void prePersist() {
        if (fechaEvento == null) {
            fechaEvento = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEntidadTipo() {
        return entidadTipo;
    }

    public void setEntidadTipo(String entidadTipo) {
        this.entidadTipo = entidadTipo;
    }

    public Long getIdEntidad() {
        return idEntidad;
    }

    public void setIdEntidad(Long idEntidad) {
        this.idEntidad = idEntidad;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(String estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(String estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public Long getIdActor() {
        return idActor;
    }

    public void setIdActor(Long idActor) {
        this.idActor = idActor;
    }

    public String getTipoRolActor() {
        return tipoRolActor;
    }

    public void setTipoRolActor(String tipoRolActor) {
        this.tipoRolActor = tipoRolActor;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public OffsetDateTime getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(OffsetDateTime fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public LocalDate getFechaEfectiva() { return fechaEfectiva; }
    public void setFechaEfectiva(LocalDate fechaEfectiva) { this.fechaEfectiva = fechaEfectiva; }
}
