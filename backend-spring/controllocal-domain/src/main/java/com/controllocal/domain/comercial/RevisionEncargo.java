package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Antecedente inmutable de presentación, corrección o decisión. Nunca reemplaza una revisión. */
@Entity
@Table(name = "revision_encargo")
public class RevisionEncargo extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_revision_encargo") private Long id;
    @Column(name = "id_captacion", nullable = false) private Long idEncargo;
    @Column(name = "id_peticion") private Long idPeticion;
    @Column(name = "id_solicitud_fin") private Long idSolicitudFin;
    @Column(name = "version_encargo", nullable = false) private long versionEncargo;
    @Column(name = "id_rol_actor", nullable = false) private Long idActor;
    @Column(name = "actuacion", nullable = false, length = 40) private String actuacion;
    @Column(name = "observaciones", columnDefinition = "text") private String observaciones;
    @Column(name = "snapshot", nullable = false, columnDefinition = "text") private String snapshot;
    @Column(name = "huella_cobertura", nullable = false, length = 64) private String huellaCobertura;
    @Column(name = "registrada_en", nullable = false) private OffsetDateTime registradaEn;
    protected RevisionEncargo() { }
    public static RevisionEncargo registrar(long org, long encargo, Long peticion, Long fin,
            long version, long actor, String actuacion, String observaciones, String snapshot,
            String huella, OffsetDateTime ahora) {
        RevisionEncargo r=new RevisionEncargo(); r.setOrganizacionId(org); r.idEncargo=encargo;
        r.idPeticion=peticion; r.idSolicitudFin=fin; r.versionEncargo=version; r.idActor=actor;
        r.actuacion=actuacion; r.observaciones=observaciones; r.snapshot=snapshot;
        r.huellaCobertura=huella; r.registradaEn=ahora; return r;
    }
    public Long getId() { return id; }
    public Long getIdEncargo() { return idEncargo; }
    public Long getIdPeticion() { return idPeticion; }
    public Long getIdSolicitudFin() { return idSolicitudFin; }
    public long getVersionEncargo() { return versionEncargo; }
    public Long getIdActor() { return idActor; }
    public String getActuacion() { return actuacion; }
    public String getObservaciones() { return observaciones; }
    public String getSnapshot() { return snapshot; }
    public String getHuellaCobertura() { return huellaCobertura; }
    public OffsetDateTime getRegistradaEn() { return registradaEn; }
}
