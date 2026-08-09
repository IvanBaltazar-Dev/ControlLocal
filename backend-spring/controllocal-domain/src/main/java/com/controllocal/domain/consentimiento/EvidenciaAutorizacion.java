package com.controllocal.domain.consentimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Prueba de COMO se obtuvo una autorizacion (D-25): canal, momento, texto
 * mostrado y respuesta literal del titular. Es lo que sostiene la
 * trazabilidad si alguien discute su consentimiento.
 *
 * <p>Los campos de WhatsApp son para el canal conversacional de KAIROS; en el
 * canal WEB se llenan ip y user agent.
 */
@Entity
@Table(name = "evidencia_autorizacion")
public class EvidenciaAutorizacion {

    public static final String CANAL_WEB = "WEB";
    public static final String CANAL_WHATSAPP = "WHATSAPP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evidencia")
    private Long id;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal;

    @Column(name = "mensaje_id", length = 100)
    private String mensajeId;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "phone_number_id", length = 60)
    private String phoneNumberId;

    @Column(name = "whatsapp_message_id", length = 100)
    private String whatsappMessageId;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @Column(name = "texto_mostrado")
    private String textoMostrado;

    @Column(name = "respuesta_recibida")
    private String respuestaRecibida;

    public Long getId() {
        return id;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getMensajeId() {
        return mensajeId;
    }

    public void setMensajeId(String mensajeId) {
        this.mensajeId = mensajeId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getWhatsappMessageId() {
        return whatsappMessageId;
    }

    public void setWhatsappMessageId(String whatsappMessageId) {
        this.whatsappMessageId = whatsappMessageId;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(OffsetDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getTextoMostrado() {
        return textoMostrado;
    }

    public void setTextoMostrado(String textoMostrado) {
        this.textoMostrado = textoMostrado;
    }

    public String getRespuestaRecibida() {
        return respuestaRecibida;
    }

    public void setRespuestaRecibida(String respuestaRecibida) {
        this.respuestaRecibida = respuestaRecibida;
    }
}
