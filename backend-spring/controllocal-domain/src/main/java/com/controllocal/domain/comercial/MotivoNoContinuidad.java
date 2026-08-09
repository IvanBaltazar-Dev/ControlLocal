package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.persona.DetalleAgente;
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
 * Por que se cayo una oportunidad. La transicion a 'N' ya queda en
 * historial_estado; esto guarda la razon TIPIFICADA, que es lo que permite
 * analizar despues si el problema es precio, ubicacion o el contrato.
 */
@Entity
@Table(name = "motivo_no_continuidad")
public class MotivoNoContinuidad extends EntidadDeOrganizacion {

    /** 'P' precio, 'U' ubicacion, 'C' condiciones, 'L' local no adecuado,
     *  'N' cliente no responde, 'E' encontro otra opcion, 'O' otro. */
    public static final Set<String> RAZONES = Set.of("P", "U", "C", "L", "N", "E", "O");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_motivo")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oportunidad", nullable = false)
    private OportunidadComercial oportunidad;

    @Column(name = "razon_principal", nullable = false, length = 1)
    private String razonPrincipal;

    @Column(name = "observaciones")
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    public Long getId() {
        return id;
    }

    public OportunidadComercial getOportunidad() {
        return oportunidad;
    }

    public void setOportunidad(OportunidadComercial oportunidad) {
        this.oportunidad = oportunidad;
    }

    public String getRazonPrincipal() {
        return razonPrincipal;
    }

    public void setRazonPrincipal(String razonPrincipal) {
        this.razonPrincipal = razonPrincipal;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
