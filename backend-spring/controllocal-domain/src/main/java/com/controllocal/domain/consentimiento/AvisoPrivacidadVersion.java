package com.controllocal.domain.consentimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Version del aviso de privacidad que se mostro al titular (D-25). El hash del
 * contenido permite demostrar QUE texto acepto, aunque el aviso cambie despues.
 * Documento de la plataforma: es GLOBAL, no lleva tenant.
 */
@Entity
@Table(name = "aviso_privacidad_version")
public class AvisoPrivacidadVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aviso")
    private Long id;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    /** SHA-256 del contenido: la evidencia de integridad del texto aceptado. */
    @Column(name = "contenido_hash", nullable = false, length = 64)
    private String contenidoHash;

    @Column(name = "contenido", nullable = false)
    private String contenido;

    @Column(name = "vigente_desde", nullable = false)
    private OffsetDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private OffsetDateTime vigenteHasta;

    /**
     * TRUE si esta version cambia MATERIALMENTE el tratamiento (V28, D-27 §3.4).
     * Publicarla caduca las autorizaciones otorgadas contra versiones
     * anteriores: no se borra ningun evento, deja de contar la proyeccion.
     * Una correccion de redaccion se publica con FALSE y no molesta a nadie.
     */
    @Column(name = "cambio_material", nullable = false)
    private boolean cambioMaterial = false;

    public boolean estaVigente() {
        return vigenteHasta == null;
    }

    public boolean isCambioMaterial() {
        return cambioMaterial;
    }

    public void setCambioMaterial(boolean cambioMaterial) {
        this.cambioMaterial = cambioMaterial;
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getContenidoHash() {
        return contenidoHash;
    }

    public void setContenidoHash(String contenidoHash) {
        this.contenidoHash = contenidoHash;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public OffsetDateTime getVigenteDesde() {
        return vigenteDesde;
    }

    public void setVigenteDesde(OffsetDateTime vigenteDesde) {
        this.vigenteDesde = vigenteDesde;
    }

    public OffsetDateTime getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(OffsetDateTime vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }
}
