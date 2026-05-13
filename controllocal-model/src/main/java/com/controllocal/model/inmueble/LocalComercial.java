package com.controllocal.model.inmueble;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import com.controllocal.model.persona.Propietario;

/**
 * Representa un local comercial persistido en la tabla local_comercial.
 */
public class LocalComercial {

    private Long idLocal;
    private String codigoLocal;
    private String direccion;
    private String distrito;
    private BigDecimal metraje;
    private BigDecimal precioReferencial;
    private String rubroPermitido;
    private String descripcion;
    private EstadoLocalComercial estado;
    private Long idPropietario;
    private Propietario propietario;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaActualizacion;

    public LocalComercial() {
    }

    public LocalComercial(
            String codigoLocal,
            String direccion,
            String distrito,
            BigDecimal metraje,
            BigDecimal precioReferencial,
            String rubroPermitido,
            String descripcion,
            EstadoLocalComercial estado,
            Long idPropietario
    ) {
        this.codigoLocal = codigoLocal;
        this.direccion = direccion;
        this.distrito = distrito;
        this.metraje = metraje;
        this.precioReferencial = precioReferencial;
        this.rubroPermitido = rubroPermitido;
        this.descripcion = descripcion;
        this.estado = estado;
        setIdPropietario(idPropietario);
    }

    public LocalComercial(
            Long idLocal,
            String codigoLocal,
            String direccion,
            String distrito,
            BigDecimal metraje,
            BigDecimal precioReferencial,
            String rubroPermitido,
            String descripcion,
            EstadoLocalComercial estado,
            Long idPropietario,
            LocalDateTime fechaRegistro,
            LocalDateTime fechaActualizacion
    ) {
        this.idLocal = idLocal;
        this.codigoLocal = codigoLocal;
        this.direccion = direccion;
        this.distrito = distrito;
        this.metraje = metraje;
        this.precioReferencial = precioReferencial;
        this.rubroPermitido = rubroPermitido;
        this.descripcion = descripcion;
        this.estado = estado;
        setIdPropietario(idPropietario);
        this.fechaRegistro = fechaRegistro;
        this.fechaActualizacion = fechaActualizacion;
    }

    public Long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(Long idLocal) {
        this.idLocal = idLocal;
    }

    public String getCodigoLocal() {
        return codigoLocal;
    }

    public void setCodigoLocal(String codigoLocal) {
        this.codigoLocal = codigoLocal;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getDistrito() {
        return distrito;
    }

    public void setDistrito(String distrito) {
        this.distrito = distrito;
    }

    public BigDecimal getMetraje() {
        return metraje;
    }

    public void setMetraje(BigDecimal metraje) {
        this.metraje = metraje;
    }

    public BigDecimal getPrecioReferencial() {
        return precioReferencial;
    }

    public void setPrecioReferencial(BigDecimal precioReferencial) {
        this.precioReferencial = precioReferencial;
    }

    public String getRubroPermitido() {
        return rubroPermitido;
    }

    public void setRubroPermitido(String rubroPermitido) {
        this.rubroPermitido = rubroPermitido;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public EstadoLocalComercial getEstado() {
        return estado;
    }

    public void setEstado(EstadoLocalComercial estado) {
        this.estado = estado;
    }

    public Long getIdPropietario() {
        if (propietario != null && propietario.getIdPropietario() != null && propietario.getIdPropietario() > 0) {
            return propietario.getIdPropietario();
        }
        return idPropietario;
    }

    public void setIdPropietario(Long idPropietario) {
        this.idPropietario = idPropietario;
        if (idPropietario == null) {
            this.propietario = null;
            return;
        }
        if (this.propietario == null) {
            this.propietario = new Propietario();
        }
        this.propietario.setIdPropietario(idPropietario);
    }

    public Propietario getPropietario() {
        return propietario;
    }

    public void setPropietario(Propietario propietario) {
        this.propietario = propietario;
        this.idPropietario = propietario != null ? propietario.getIdPropietario() : null;
    }

    public void cambiarEstado(EstadoLocalComercial estado) {
        this.estado = estado;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String obtenerResumen() {
        return codigoLocal + " - " + direccion + " (" + distrito + ")";
    }

    public void actualizarDatos(
            String direccion,
            String distrito,
            BigDecimal metraje,
            BigDecimal precioReferencial,
            String rubroPermitido,
            String descripcion
    ) {
        this.direccion = direccion;
        this.distrito = distrito;
        this.metraje = metraje;
        this.precioReferencial = precioReferencial;
        this.rubroPermitido = rubroPermitido;
        this.descripcion = descripcion;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    @Override
    public String toString() {
        return "LocalComercial{" +
                "idLocal=" + idLocal +
                ", codigoLocal='" + codigoLocal + '\'' +
                ", direccion='" + direccion + '\'' +
                ", distrito='" + distrito + '\'' +
                ", metraje=" + metraje +
                ", precioReferencial=" + precioReferencial +
                ", rubroPermitido='" + rubroPermitido + '\'' +
                ", descripcion='" + descripcion + '\'' +
                ", estado=" + estado +
                ", idPropietario=" + getIdPropietario() +
                ", fechaRegistro=" + fechaRegistro +
                ", fechaActualizacion=" + fechaActualizacion +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocalComercial that)) {
            return false;
        }
        if (idLocal == null || that.idLocal == null) {
            return false;
        }
        return Objects.equals(idLocal, that.idLocal);
    }

    @Override
    public int hashCode() {
        return idLocal != null ? Objects.hash(idLocal) : System.identityHashCode(this);
    }
}
