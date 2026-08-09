package com.controllocal.domain.persona;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoOperativoAgente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDate;

/**
 * Detalle del rol AGENTE inmobiliario.
 */
@Entity
@Table(name = "detalle_agente")
public class DetalleAgente extends EntidadDeOrganizacion {

    @Id
    @Column(name = "id_persona_rol")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id_persona_rol")
    private PersonaRol rol;

    @Column(name = "codigo_agente", nullable = false, length = 20)
    private String codigoAgente;

    @Column(name = "zona_asignada", length = 100)
    private String zonaAsignada;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    /** 'D' disponible, 'L' licencia, 'N' no disponible. */
    @Column(name = "estado_operativo", nullable = false, length = 1)
    private String estadoOperativo = Codigos.OperacionAgente.DISPONIBLE;

    public Long getId() {
        return id;
    }

    public PersonaRol getRol() {
        return rol;
    }

    public void setRol(PersonaRol rol) {
        this.rol = rol;
    }

    public String getCodigoAgente() {
        return codigoAgente;
    }

    public void setCodigoAgente(String codigoAgente) {
        this.codigoAgente = codigoAgente;
    }

    public String getZonaAsignada() {
        return zonaAsignada;
    }

    public void setZonaAsignada(String zonaAsignada) {
        this.zonaAsignada = zonaAsignada;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public String getEstadoOperativo() {
        return estadoOperativo;
    }

    public void setEstadoOperativo(String estadoOperativo) {
        this.estadoOperativo = EstadoOperativoAgente.desde(estadoOperativo).codigo();
    }

    @Transient
    public EstadoOperativoAgente estadoOperativoTipado() {
        return estadoOperativo == null ? null : EstadoOperativoAgente.desde(estadoOperativo);
    }
}
