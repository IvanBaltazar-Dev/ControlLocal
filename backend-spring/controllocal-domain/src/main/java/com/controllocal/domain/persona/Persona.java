package com.controllocal.domain.persona;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoActivoInactivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;

/**
 * Party del modelo Party-Role: identidad UNICA del actor en todo el sistema
 * (auditoria, alcance por rol y trazabilidad cuelgan de este id).
 * Los roles que acumula viven en {@link PersonaRol}.
 */
@Entity
@Table(name = "persona")
public class Persona extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_persona")
    private Long id;

    /** 'N' natural, 'J' juridica (vocabulario heredado del cable congelado). */
    @Column(name = "tipo_persona", nullable = false, length = 1)
    private String tipoPersona;

    /** 'D' DNI, 'R' RUC, 'C' carnet de extranjeria, 'P' pasaporte. */
    @Column(name = "tipo_documento", nullable = false, length = 1)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 30)
    private String numeroDocumento;

    @Column(name = "nombres_o_razon_social", nullable = false, length = 150)
    private String nombresORazonSocial;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "correo", length = 150)
    private String correo;

    /** 'A' activa, 'I' inactiva. */
    @Column(name = "estado", nullable = false, length = 1)
    private String estado = Codigos.ActivoInactivo.ACTIVO;

    @Column(name = "consentimiento_uso_dato")
    private Boolean consentimientoUsoDato;

    /** Clave opaca de la foto de perfil en el almacen (S3/disco). */
    @Column(name = "foto_clave", length = 200)
    private String fotoClave;

    @Column(name = "fecha_creacion", updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    /**
     * La escribe la app (no el DEFAULT de la BD) porque el alta de cliente la
     * DEVUELVE en la misma respuesta: si la pusiera solo Postgres, el JSON del
     * POST saldria sin {@code fechaCreacion} hasta el siguiente refetch.
     */
    @PrePersist
    void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
    }

    public boolean estaActiva() {
        return estadoTipado() == EstadoActivoInactivo.ACTIVO;
    }

    public Long getId() {
        return id;
    }

    public String getTipoPersona() {
        return tipoPersona;
    }

    public void setTipoPersona(String tipoPersona) {
        this.tipoPersona = tipoPersona;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getNombresORazonSocial() {
        return nombresORazonSocial;
    }

    public void setNombresORazonSocial(String nombresORazonSocial) {
        this.nombresORazonSocial = nombresORazonSocial;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = EstadoActivoInactivo.desde(estado).codigo();
    }

    @Transient
    public EstadoActivoInactivo estadoTipado() {
        return estado == null ? null : EstadoActivoInactivo.desde(estado);
    }

    public Boolean getConsentimientoUsoDato() {
        return consentimientoUsoDato;
    }

    public void setConsentimientoUsoDato(Boolean consentimientoUsoDato) {
        this.consentimientoUsoDato = consentimientoUsoDato;
    }

    public String getFotoClave() {
        return fotoClave;
    }

    public void setFotoClave(String fotoClave) {
        this.fotoClave = fotoClave;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
