package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * TABLA-EVENTO de actor (Doc 5 §7): la reasignacion cambia el agente
 * responsable de la captacion, no su estado. Conserva anterior/nuevo,
 * el broker que autorizo y el motivo; se integra al timeline en lectura.
 */
@Entity
@Table(name = "reasignacion_captacion")
public class ReasignacionCaptacion extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reasignacion")
    private Long id;

    @Column(name = "fecha_cambio", nullable = false)
    private OffsetDateTime fechaCambio;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_captacion", nullable = false)
    private Captacion captacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente_anterior", nullable = false)
    private DetalleAgente agenteAnterior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente_nuevo", nullable = false)
    private DetalleAgente agenteNuevo;

    /**
     * Broker que reasigno, cuando el autor <b>es</b> un broker. Nulo si la hizo
     * el gobierno del tenant (V35, D-S0-17 fila 6): un TENANT_ADMIN puede no
     * tener {@code detalle_broker}, que es justo lo que separa gobernar de
     * operar. El cable congelado lo expone como {@code idBroker} y el JSON
     * omite nulos, asi que una reasignacion de gobierno no lo trae.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_broker")
    private DetalleBroker broker;

    /** Persona que ejecuto la reasignacion, sea cual sea su banda. */
    @Column(name = "id_persona_actor")
    private Long idPersonaActor;

    /** Banda con la que actuo: {@code BROKER} o {@code TENANT_ADMIN}. */
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

    public OffsetDateTime getFechaCambio() {
        return fechaCambio;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public void setCaptacion(Captacion captacion) {
        this.captacion = captacion;
    }

    public DetalleAgente getAgenteAnterior() {
        return agenteAnterior;
    }

    public void setAgenteAnterior(DetalleAgente agenteAnterior) {
        this.agenteAnterior = agenteAnterior;
    }

    public DetalleAgente getAgenteNuevo() {
        return agenteNuevo;
    }

    public void setAgenteNuevo(DetalleAgente agenteNuevo) {
        this.agenteNuevo = agenteNuevo;
    }

    public DetalleBroker getBroker() {
        return broker;
    }

    public void setBroker(DetalleBroker broker) {
        this.broker = broker;
    }
}
