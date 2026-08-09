package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Bitacora POLIMORFICA del contacto comercial: cada fila cuelga de UNA de
 * cuatro entidades segun {@code contexto} — la oportunidad, la prospeccion,
 * la captacion o el cliente. Un CHECK de la BD garantiza que sea exactamente
 * una (la v1 solo lo validaba en la capa REST).
 *
 * <p>No es {@link com.controllocal.domain.comun.Transicionable}: una
 * interaccion es un EVENTO, no tiene maquina de estados. Su
 * {@code resultado} describe como termino ESA conversacion, y el vocabulario
 * permitido depende del contexto (lo aplica la capa service).
 */
@Entity
@Table(name = "interaccion_comercial")
public class InteraccionComercial extends EntidadDeOrganizacion {

    public static final String OPORTUNIDAD = "OPORTUNIDAD";
    public static final String PROSPECCION = "PROSPECCION";
    public static final String CAPTACION = "CAPTACION";
    public static final String CLIENTE = "CLIENTE";
    public static final Set<String> CONTEXTOS = Set.of(OPORTUNIDAD, PROSPECCION, CAPTACION, CLIENTE);

    /** 'L' llamada, 'W' WhatsApp, 'E' email, 'P' presencial, 'R' reunion, 'T' portal, 'O' otro. */
    public static final Set<String> CANALES = Set.of("L", "W", "E", "P", "R", "T", "O");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_interaccion")
    private Long id;

    @Column(name = "contexto", nullable = false, length = 20)
    private String contexto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oportunidad")
    private OportunidadComercial oportunidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prospeccion")
    private Prospeccion prospeccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_captacion")
    private Captacion captacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_cliente")
    private DetalleCliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @Column(name = "canal_contacto", nullable = false, length = 1)
    private String canalContacto;

    /** Codigo de ResultadoInteraccion: MIXTO en el cable (1 char o palabra). */
    @Column(name = "resultado", length = 30)
    private String resultado;

    @Column(name = "observaciones")
    private String observaciones;

    /** Nota dictada/transcrita del contacto (semilla del canal conversacional). */
    @Column(name = "transcripcion_nota")
    private String transcripcionNota;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @PrePersist
    void prePersist() {
        if (fechaHora == null) {
            fechaHora = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getContexto() {
        return contexto;
    }

    public void setContexto(String contexto) {
        this.contexto = contexto;
    }

    public OportunidadComercial getOportunidad() {
        return oportunidad;
    }

    public void setOportunidad(OportunidadComercial oportunidad) {
        this.oportunidad = oportunidad;
    }

    public Prospeccion getProspeccion() {
        return prospeccion;
    }

    public void setProspeccion(Prospeccion prospeccion) {
        this.prospeccion = prospeccion;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public void setCaptacion(Captacion captacion) {
        this.captacion = captacion;
    }

    public DetalleCliente getCliente() {
        return cliente;
    }

    public void setCliente(DetalleCliente cliente) {
        this.cliente = cliente;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public String getCanalContacto() {
        return canalContacto;
    }

    public void setCanalContacto(String canalContacto) {
        this.canalContacto = canalContacto;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getTranscripcionNota() {
        return transcripcionNota;
    }

    public void setTranscripcionNota(String transcripcionNota) {
        this.transcripcionNota = transcripcionNota;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(OffsetDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
