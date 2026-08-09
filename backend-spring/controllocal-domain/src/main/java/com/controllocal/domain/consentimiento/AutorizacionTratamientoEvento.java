package com.controllocal.domain.consentimiento;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Evento del consentimiento de una persona para una finalidad (D-25).
 * Registro APPEND-ONLY: revocar no borra el otorgamiento anterior, agrega un
 * evento; el estado vigente se DERIVA leyendo el ultimo evento de cada
 * (persona, finalidad). Asi la trazabilidad sobrevive a los cambios de opinion.
 *
 * <p>Es privado del tenant porque audita a personas de una organizacion.
 */
@Entity
@Table(name = "autorizacion_tratamiento_evento")
public class AutorizacionTratamientoEvento extends EntidadDeOrganizacion {

    /** Ciclo de vida de la autorizacion. */
    public static final String INFORMADO = "INFORMADO";
    public static final String OTORGADO = "OTORGADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String REVOCADO = "REVOCADO";
    public static final String REOTORGADO = "REOTORGADO";
    public static final String EXPIRADO = "EXPIRADO";
    public static final String CAMBIO_BASE_JURIDICA = "CAMBIO_BASE_JURIDICA";

    /** Por que es licito tratar el dato (no todo se apoya en consentimiento). */
    public static final String BASE_CONSENTIMIENTO = "CONSENTIMIENTO";
    public static final String BASE_RELACION_CONTRACTUAL = "RELACION_CONTRACTUAL";
    public static final String BASE_OBLIGACION_LEGAL = "OBLIGACION_LEGAL";
    public static final String BASE_OTRA_EXCEPCION_LEGAL = "OTRA_EXCEPCION_LEGAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long id;

    /** Titular del dato: persona.id (identidad unica Party-Role). */
    @Column(name = "id_persona", nullable = false)
    private Long idPersona;

    @Column(name = "finalidad_codigo", nullable = false, length = 40)
    private String finalidadCodigo;

    @Column(name = "evento", nullable = false, length = 24)
    private String evento;

    @Column(name = "base_juridica", nullable = false, length = 24)
    private String baseJuridica;

    @Column(name = "version_aviso", length = 20)
    private String versionAviso;

    @Column(name = "ocurrido_en", nullable = false)
    private OffsetDateTime ocurridoEn;

    @Column(name = "id_evidencia")
    private Long idEvidencia;

    /**
     * persona_rol del usuario interno que registro el evento (V28). NULL cuando
     * lo produce el propio titular o un proceso del sistema.
     */
    @Column(name = "registrada_por")
    private Long registradaPor;

    /** Solo puede llevar valor cuando el evento es REVOCADO (CHECK en V28). */
    @Column(name = "motivo_revocacion", length = 300)
    private String motivoRevocacion;

    public Long getId() {
        return id;
    }

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public String getFinalidadCodigo() {
        return finalidadCodigo;
    }

    public void setFinalidadCodigo(String finalidadCodigo) {
        this.finalidadCodigo = finalidadCodigo;
    }

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }

    public String getBaseJuridica() {
        return baseJuridica;
    }

    public void setBaseJuridica(String baseJuridica) {
        this.baseJuridica = baseJuridica;
    }

    public String getVersionAviso() {
        return versionAviso;
    }

    public void setVersionAviso(String versionAviso) {
        this.versionAviso = versionAviso;
    }

    public OffsetDateTime getOcurridoEn() {
        return ocurridoEn;
    }

    public void setOcurridoEn(OffsetDateTime ocurridoEn) {
        this.ocurridoEn = ocurridoEn;
    }

    public Long getIdEvidencia() {
        return idEvidencia;
    }

    public void setIdEvidencia(Long idEvidencia) {
        this.idEvidencia = idEvidencia;
    }

    public Long getRegistradaPor() {
        return registradaPor;
    }

    public void setRegistradaPor(Long registradaPor) {
        this.registradaPor = registradaPor;
    }

    public String getMotivoRevocacion() {
        return motivoRevocacion;
    }

    public void setMotivoRevocacion(String motivoRevocacion) {
        this.motivoRevocacion = motivoRevocacion;
    }
}
