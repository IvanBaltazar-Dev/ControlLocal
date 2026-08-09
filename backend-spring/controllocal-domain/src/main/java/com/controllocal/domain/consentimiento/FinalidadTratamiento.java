package com.controllocal.domain.consentimiento;

import com.controllocal.domain.comun.EstadosDominio.Codigos;
import com.controllocal.domain.comun.EstadosDominio.EstadoActivoInactivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Catalogo GLOBAL de finalidades de tratamiento de datos (D-25): para que se
 * usa un dato y si esa finalidad necesita consentimiento o se apoya en otra
 * base juridica. Es vocabulario compartido por todas las organizaciones, como
 * {@code entidad_tipo} o {@code distrito}, asi que NO lleva tenant.
 *
 * <p>El contrato queda listo en V6; los flujos conversacionales que piden y
 * revocan autorizaciones llegan con KAIROS.
 */
@Entity
@Table(name = "finalidad_tratamiento")
public class FinalidadTratamiento {

    /** Necesaria para operar el servicio: no se consiente ni se revoca. */
    public static final String OPERACION_SERVICIO = "OPERACION_SERVICIO";
    public static final String ANALITICA_AGREGADA = "ANALITICA_AGREGADA";
    public static final String MEJORA_MODELOS = "MEJORA_MODELOS";
    public static final String RED_COLABORATIVA = "RED_COLABORATIVA";
    public static final String PROSPECCION_COMERCIAL = "PROSPECCION_COMERCIAL";

    @Id
    @Column(name = "codigo", length = 40)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    @Column(name = "requiere_consentimiento", nullable = false)
    private boolean requiereConsentimiento = true;

    @Column(name = "permite_revocacion", nullable = false)
    private boolean permiteRevocacion = true;

    /** NECESARIA | OPCIONAL. */
    @Column(name = "nivel", length = 20)
    private String nivel;

    /** 'A' activa, 'I' inactiva. */
    @Column(name = "estado", nullable = false, length = 1)
    private String estado = Codigos.ActivoInactivo.ACTIVO;

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isRequiereConsentimiento() {
        return requiereConsentimiento;
    }

    public void setRequiereConsentimiento(boolean requiereConsentimiento) {
        this.requiereConsentimiento = requiereConsentimiento;
    }

    public boolean isPermiteRevocacion() {
        return permiteRevocacion;
    }

    public void setPermiteRevocacion(boolean permiteRevocacion) {
        this.permiteRevocacion = permiteRevocacion;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
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
}
