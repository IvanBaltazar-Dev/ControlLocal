package com.controllocal.domain.persona;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Supervision broker->agente con vigencia (tabla de V1 de la BD v2).
 * Es la base del alcance por fila del broker supervisor (Doc 5 §8):
 * un broker solo ve/gestiona lo de los agentes que supervisa hoy.
 * Se mapea por ids de rol (sin asociaciones) porque solo se consulta.
 */
@Entity
@Table(name = "supervision_agente")
public class SupervisionAgente extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_supervision")
    private Long id;

    @Column(name = "id_rol_broker", nullable = false)
    private Long idRolBroker;

    @Column(name = "id_rol_agente", nullable = false)
    private Long idRolAgente;

    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDate fechaAsignacion;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    public boolean estaActiva() {
        return fechaFin == null;
    }

    public Long getId() {
        return id;
    }

    public Long getIdRolBroker() {
        return idRolBroker;
    }

    public void setIdRolBroker(Long idRolBroker) {
        this.idRolBroker = idRolBroker;
    }

    public Long getIdRolAgente() {
        return idRolAgente;
    }

    public void setIdRolAgente(Long idRolAgente) {
        this.idRolAgente = idRolAgente;
    }

    public LocalDate getFechaAsignacion() {
        return fechaAsignacion;
    }

    public void setFechaAsignacion(LocalDate fechaAsignacion) {
        this.fechaAsignacion = fechaAsignacion;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
