package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.controllocal.domain.comun.Transicionable;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Petición durable de una versión exacta; las revisiones se conservan separadas y anexadas. */
@Entity
@Table(name = "peticion_revision_encargo")
public class PeticionRevisionEncargo extends EntidadDeOrganizacion implements Transicionable {
    public static final String TIPO = "PETICION_REVISION_ENCARGO";
    public static final String PENDIENTE="P", OBSERVADA="O", APROBADA="A", RECHAZADA="R";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_peticion_revision_encargo") private Long id;
    @Version @Column(name = "version", nullable = false) private long version;
    @Column(name = "id_captacion", nullable = false) private Long idEncargo;
    @Column(name = "version_presentada", nullable = false) private long versionPresentada;
    @Column(name = "id_documento_encargo", nullable = false) private Long idDocumento;
    @Column(name = "id_rol_solicitante", nullable = false) private Long idSolicitante;
    @Column(name = "estado", nullable = false, length = 1) private String estado;
    @Column(name = "evidencias", nullable = false, columnDefinition = "text") private String evidencias;
    @Column(name = "partes", nullable = false, columnDefinition = "text") private String partes;
    @Column(name = "huella_cobertura", nullable = false, length = 64) private String huellaCobertura;
    @Column(name = "presentada_en", nullable = false) private OffsetDateTime presentadaEn;
    @Column(name = "id_ultima_revision") private Long idUltimaRevision;
    protected PeticionRevisionEncargo() { }
    public static PeticionRevisionEncargo nueva(long org, long encargo, long solicitante) {
        PeticionRevisionEncargo p = new PeticionRevisionEncargo(); p.setOrganizacionId(org);
        p.idEncargo = encargo; p.idSolicitante = solicitante; return p;
    }
    public void presentar(long version, long documento, String evidencias, String partes,
                          String cobertura, OffsetDateTime ahora) {
        this.versionPresentada=version; this.idDocumento=documento; this.evidencias=evidencias;
        this.partes=partes; this.huellaCobertura=cobertura; this.presentadaEn=ahora;
    }
    @Override public String entidadTipo() { return TIPO; }
    @Override public String estadoActual() { return estado; }
    @Override public void transicionarA(String nuevo) {
        if (!java.util.Set.of(PENDIENTE,OBSERVADA,APROBADA,RECHAZADA).contains(nuevo))
            throw new IllegalArgumentException("Estado de petición desconocido.");
        estado=nuevo;
    }
    public String estadoNombre() { return switch(estado) {
        case PENDIENTE->"PENDIENTE"; case OBSERVADA->"OBSERVADA";
        case APROBADA->"APROBADA"; case RECHAZADA->"RECHAZADA";
        default->throw new IllegalStateException("Petición sin estado."); }; }
    public Long getId() { return id; }
    public long getVersion() { return version; }
    public Long getIdEncargo() { return idEncargo; }
    public long getVersionPresentada() { return versionPresentada; }
    public Long getIdDocumento() { return idDocumento; }
    public Long getIdSolicitante() { return idSolicitante; }
    public String getEvidencias() { return evidencias; }
    public String getPartes() { return partes; }
    public String getHuellaCobertura() { return huellaCobertura; }
    public OffsetDateTime getPresentadaEn() { return presentadaEn; }
    public Long getIdUltimaRevision() { return idUltimaRevision; }
    public void registrarRevision(long revision) { idUltimaRevision=revision; }
}
