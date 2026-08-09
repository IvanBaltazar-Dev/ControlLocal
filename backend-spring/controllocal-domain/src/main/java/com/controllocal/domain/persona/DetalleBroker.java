package com.controllocal.domain.persona;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Detalle del rol BROKER. El broker administrador es UNICO en el sistema
 * (indice parcial uq_broker_admin_unico en la BD).
 */
@Entity
@Table(name = "detalle_broker")
public class DetalleBroker extends EntidadDeOrganizacion {

    @Id
    @Column(name = "id_persona_rol")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id_persona_rol")
    private PersonaRol rol;

    @Column(name = "codigo_broker", nullable = false, length = 20)
    private String codigoBroker;

    @Column(name = "zona", length = 100)
    private String zona;

    @Column(name = "fecha_designacion", nullable = false)
    private LocalDate fechaDesignacion;

    @Column(name = "es_administrador", nullable = false)
    private boolean esAdministrador;

    public Long getId() {
        return id;
    }

    public PersonaRol getRol() {
        return rol;
    }

    public void setRol(PersonaRol rol) {
        this.rol = rol;
    }

    public String getCodigoBroker() {
        return codigoBroker;
    }

    public void setCodigoBroker(String codigoBroker) {
        this.codigoBroker = codigoBroker;
    }

    public String getZona() {
        return zona;
    }

    public void setZona(String zona) {
        this.zona = zona;
    }

    public LocalDate getFechaDesignacion() {
        return fechaDesignacion;
    }

    public void setFechaDesignacion(LocalDate fechaDesignacion) {
        this.fechaDesignacion = fechaDesignacion;
    }

    public boolean isEsAdministrador() {
        return esAdministrador;
    }

    public void setEsAdministrador(boolean esAdministrador) {
        this.esAdministrador = esAdministrador;
    }
}
