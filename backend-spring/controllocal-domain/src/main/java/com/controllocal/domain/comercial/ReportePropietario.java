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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Reporte periodico de avances al propietario de una captacion.
 *
 * <p><b>Entra en F7 a medias, y a proposito</b>: su recurso REST
 * ({@code /captaciones/{id}/reportes-propietario}) todavia no esta migrado,
 * pero el disparador 6 de la bandeja necesita UNA lectura suya —cuando fue el
 * ultimo reporte de cada captacion— para saber si toca pedir el siguiente
 * (cadencia de 15 dias). Sin la tabla ese disparador no existiria, que es una
 * divergencia observable en {@code GET /tareas} (D-F7-1). Cuando llegue su
 * modulo, esta entidad ya esta.
 */
@Entity
@Table(name = "reporte_propietario")
public class ReportePropietario extends EntidadDeOrganizacion {

    /** CanalEnvio del cable: llamada, WhatsApp, email, presencial, reunion, teams, otro. */
    public static final String CANAL_LLAMADA = "L";
    public static final String CANAL_WHATSAPP = "W";
    public static final String CANAL_EMAIL = "E";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte_propietario")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_captacion", nullable = false)
    private Captacion captacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol_agente", nullable = false)
    private DetalleAgente agente;

    @Column(name = "fecha_reporte", nullable = false)
    private LocalDate fechaReporte;

    @Column(name = "periodo_inicio")
    private LocalDate periodoInicio;

    @Column(name = "periodo_fin")
    private LocalDate periodoFin;

    @Column(name = "consultas_reportadas", nullable = false)
    private int consultasReportadas;

    @Column(name = "visitas_reportadas", nullable = false)
    private int visitasReportadas;

    @Column(name = "objeciones_frecuentes", length = 500)
    private String objecionesFrecuentes;

    @Column(name = "ajustes_recomendados", length = 500)
    private String ajustesRecomendados;

    @Column(name = "canal_envio", nullable = false, length = 1)
    private String canalEnvio;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public void setCaptacion(Captacion captacion) {
        this.captacion = captacion;
    }

    public DetalleAgente getAgente() {
        return agente;
    }

    public void setAgente(DetalleAgente agente) {
        this.agente = agente;
    }

    public LocalDate getFechaReporte() {
        return fechaReporte;
    }

    public void setFechaReporte(LocalDate fechaReporte) {
        this.fechaReporte = fechaReporte;
    }

    public LocalDate getPeriodoInicio() {
        return periodoInicio;
    }

    public void setPeriodoInicio(LocalDate periodoInicio) {
        this.periodoInicio = periodoInicio;
    }

    public LocalDate getPeriodoFin() {
        return periodoFin;
    }

    public void setPeriodoFin(LocalDate periodoFin) {
        this.periodoFin = periodoFin;
    }

    public int getConsultasReportadas() {
        return consultasReportadas;
    }

    public void setConsultasReportadas(int consultasReportadas) {
        this.consultasReportadas = consultasReportadas;
    }

    public int getVisitasReportadas() {
        return visitasReportadas;
    }

    public void setVisitasReportadas(int visitasReportadas) {
        this.visitasReportadas = visitasReportadas;
    }

    public String getObjecionesFrecuentes() {
        return objecionesFrecuentes;
    }

    public void setObjecionesFrecuentes(String objecionesFrecuentes) {
        this.objecionesFrecuentes = objecionesFrecuentes;
    }

    public String getAjustesRecomendados() {
        return ajustesRecomendados;
    }

    public void setAjustesRecomendados(String ajustesRecomendados) {
        this.ajustesRecomendados = ajustesRecomendados;
    }

    public String getCanalEnvio() {
        return canalEnvio;
    }

    public void setCanalEnvio(String canalEnvio) {
        this.canalEnvio = canalEnvio;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
