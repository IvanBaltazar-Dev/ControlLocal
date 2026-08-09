package com.controllocal.domain.seguridad;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Una de las tres cosas que una concesion puede hacer (V38, §9.6), y
 * <b>como maximo una vez cada una</b>.
 *
 * <p>El {@code UNIQUE (id_concesion, tipo)} no es cosmetico: sin el,
 * {@code max_acciones = 3} deja ejecutar <b>tres veces la misma</b>.
 *
 * <p>Cada accion es <b>idempotente</b>: aplicarla sobre un estado que ya la
 * cumple no falla, se registra como {@link #SIN_EFECTO} y <b>consume capacidad
 * igual</b>. Lo que se gasta es el intento, no el cambio — si no, una
 * concesion podria sondear el estado de la cuenta sin coste.
 */
@Entity
@Table(name = "accion_recuperacion")
public class AccionRecuperacion extends EntidadDeOrganizacion {

    /** Vuelve a poner la credencial en activo. No toca la contrasena. */
    public static final String REACTIVAR_CUENTA = "REACTIVAR_CUENTA";
    /** Revoca el factor y deja {@code debe_enrolar_mfa}. NO configura uno nuevo. */
    public static final String REVOCAR_MFA = "REVOCAR_MFA";
    /** Devuelve la membresia TENANT_ADMIN. No crea personas ni roles. */
    public static final String REPONER_MEMBRESIA = "REPONER_MEMBRESIA";

    public static final String APLICADA = "APLICADA";
    public static final String SIN_EFECTO = "SIN_EFECTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_accion")
    private Long id;

    @Column(name = "id_concesion", nullable = false)
    private Long idConcesion;

    @Column(name = "tipo", nullable = false, length = 24)
    private String tipo;

    @Column(name = "aplicada_en", nullable = false)
    private OffsetDateTime aplicadaEn;

    @Column(name = "resultado", nullable = false, length = 12)
    private String resultado;

    public Long getId() {
        return id;
    }

    public Long getIdConcesion() {
        return idConcesion;
    }

    public void setIdConcesion(Long idConcesion) {
        this.idConcesion = idConcesion;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public OffsetDateTime getAplicadaEn() {
        return aplicadaEn;
    }

    public void setAplicadaEn(OffsetDateTime aplicadaEn) {
        this.aplicadaEn = aplicadaEn;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }
}
