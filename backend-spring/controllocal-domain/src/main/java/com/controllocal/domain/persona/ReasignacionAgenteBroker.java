package com.controllocal.domain.persona;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Evento inmutable de una reasignacion agente -> broker.
 *
 * <p>La supervision vigente vive en {@link SupervisionAgente}; este evento
 * conserva anterior, nuevo, autorizador, motivo y fecha-hora para el historial
 * del contrato congelado.
 */
@Entity
@Table(name = "reasignacion_agente_broker")
public class ReasignacionAgenteBroker extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reasignacion")
    private Long id;

    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    private OffsetDateTime fechaCambio;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "id_rol_agente", nullable = false)
    private Long idRolAgente;

    @Column(name = "id_rol_broker_anterior")
    private Long idRolBrokerAnterior;

    @Column(name = "id_rol_broker_nuevo", nullable = false)
    private Long idRolBrokerNuevo;

    /**
     * Broker administrador que reasignó, cuando el autor tenía detalle de
     * broker. Nulo desde el Bloque 5 (V36): administrar dejó de ser una
     * variedad de broker, así que el autor se registra en los dos campos
     * siguientes. Se conserva porque el cable congelado lo expone.
     */
    @Column(name = "id_rol_broker_administrador")
    private Long idRolBrokerAdministrador;

    /** Persona que ejecutó la reasignación. */
    @Column(name = "id_persona_actor")
    private Long idPersonaActor;

    /** Banda con la que actuó; hoy siempre {@code TENANT_ADMIN}. */
    @Column(name = "tipo_rol_actor", length = 20)
    private String tipoRolActor;

    @PrePersist
    void prePersist() {
        if (fechaCambio == null) {
            fechaCambio = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getFechaCambio() {
        return fechaCambio;
    }

    public void setFechaCambio(OffsetDateTime fechaCambio) {
        this.fechaCambio = fechaCambio;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Long getIdRolAgente() {
        return idRolAgente;
    }

    public void setIdRolAgente(Long idRolAgente) {
        this.idRolAgente = idRolAgente;
    }

    public Long getIdRolBrokerAnterior() {
        return idRolBrokerAnterior;
    }

    public void setIdRolBrokerAnterior(Long idRolBrokerAnterior) {
        this.idRolBrokerAnterior = idRolBrokerAnterior;
    }

    public Long getIdRolBrokerNuevo() {
        return idRolBrokerNuevo;
    }

    public void setIdRolBrokerNuevo(Long idRolBrokerNuevo) {
        this.idRolBrokerNuevo = idRolBrokerNuevo;
    }

    public Long getIdRolBrokerAdministrador() {
        return idRolBrokerAdministrador;
    }

    public void setIdRolBrokerAdministrador(Long idRolBrokerAdministrador) {
        this.idRolBrokerAdministrador = idRolBrokerAdministrador;
    }

    public Long getIdPersonaActor() {
        return idPersonaActor;
    }

    public void setIdPersonaActor(Long idPersonaActor) {
        this.idPersonaActor = idPersonaActor;
    }

    public String getTipoRolActor() {
        return tipoRolActor;
    }

    public void setTipoRolActor(String tipoRolActor) {
        this.tipoRolActor = tipoRolActor;
    }
}
