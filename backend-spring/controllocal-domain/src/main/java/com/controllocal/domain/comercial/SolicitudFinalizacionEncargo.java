package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.Transicionable;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Solicitar no finaliza. Aprobar reutiliza el único fin del episodio en la misma transacción. */
@Entity
@Table(name = "solicitud_finalizacion_encargo")
public class SolicitudFinalizacionEncargo extends EntidadDeOrganizacion implements Transicionable {
    public static final String TIPO="SOLICITUD_FIN_ENCARGO";
    public static final String PENDIENTE="P", OBSERVADA="O", APROBADA="A", RECHAZADA="R", CANCELADA="C";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_finalizacion_encargo") private Long id;
    @Version @Column(name = "version", nullable = false) private long version;
    @Column(name = "id_captacion", nullable = false) private Long idEncargo;
    @Column(name = "version_encargo", nullable = false) private long versionEncargo;
    @Column(name = "id_rol_solicitante", nullable = false) private Long idSolicitante;
    @Column(name = "estado", nullable = false, length = 1) private String estado;
    @Column(name = "tipo_fin", nullable = false, length = 20) private String tipoFin;
    @Column(name = "fecha_solicitada", nullable = false) private LocalDate fechaSolicitada;
    @Column(name = "motivo", nullable = false, columnDefinition = "text") private String motivo;
    @Column(name = "evidencias", nullable = false, columnDefinition = "text") private String evidencias;
    @Column(name = "registrada_en", nullable = false) private OffsetDateTime registradaEn;
    @Column(name = "id_ultima_revision") private Long idUltimaRevision;
    protected SolicitudFinalizacionEncargo() { }
    public static SolicitudFinalizacionEncargo nueva(long org, long encargo, long solicitante,
                                                    OffsetDateTime ahora) {
        SolicitudFinalizacionEncargo s=new SolicitudFinalizacionEncargo(); s.setOrganizacionId(org);
        s.idEncargo=encargo; s.idSolicitante=solicitante; s.registradaEn=ahora; return s;
    }
    public void solicitar(long version, String tipo, LocalDate fecha, String motivo, String evidencias) {
        this.versionEncargo=version; this.tipoFin=tipo; this.fechaSolicitada=fecha;
        this.motivo=motivo; this.evidencias=evidencias;
    }
    @Override public String entidadTipo() { return TIPO; }
    @Override public String estadoActual() { return estado; }
    @Override public void transicionarA(String nuevo) {
        if (!java.util.Set.of(PENDIENTE,OBSERVADA,APROBADA,RECHAZADA,CANCELADA).contains(nuevo))
            throw new IllegalArgumentException("Estado de solicitud desconocido.");
        estado=nuevo;
    }
    public String estadoNombre() { return switch(estado) {
        case PENDIENTE->"PENDIENTE"; case OBSERVADA->"OBSERVADA"; case APROBADA->"APROBADA";
        case RECHAZADA->"RECHAZADA"; case CANCELADA->"CANCELADA";
        default->throw new IllegalStateException("Solicitud sin estado."); }; }
    public Long getId() { return id; }
    public long getVersion() { return version; }
    public Long getIdEncargo() { return idEncargo; }
    public long getVersionEncargo() { return versionEncargo; }
    public Long getIdSolicitante() { return idSolicitante; }
    public String getTipoFin() { return tipoFin; }
    public LocalDate getFechaSolicitada() { return fechaSolicitada; }
    public String getMotivo() { return motivo; }
    public String getEvidencias() { return evidencias; }
    public Long getIdUltimaRevision() { return idUltimaRevision; }
    public void registrarRevision(long revision) { idUltimaRevision=revision; }
}
