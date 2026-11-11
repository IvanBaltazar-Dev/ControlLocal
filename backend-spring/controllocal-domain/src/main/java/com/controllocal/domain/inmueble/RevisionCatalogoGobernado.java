package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;

/** Estado anterior hallado al cambiar una definición/perfil; append-only en PostgreSQL. */
@Entity
@Table(name = "revision_catalogo_gobernado")
public class RevisionCatalogoGobernado extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_revision") private Long id;
    @Column(name = "clase_recurso", nullable = false, length = 20) private String claseRecurso;
    @Column(name = "id_recurso", nullable = false) private Long idRecurso;
    @Column(name = "version_definicion", nullable = false) private long versionDefinicion;
    @Column(name = "snapshot_json", nullable = false) private String snapshotJson;
    @Column(name = "actuacion", nullable = false, length = 20) private String actuacion;
    @Column(name = "id_rol_actor", nullable = false) private Long idRolActor;
    @Column(name = "registrado_en", nullable = false) private Instant registradoEn = Instant.now();

    public Long getId() { return id; }
    public String getClaseRecurso() { return claseRecurso; }
    public void setClaseRecurso(String valor) { claseRecurso = valor; }
    public Long getIdRecurso() { return idRecurso; }
    public void setIdRecurso(Long valor) { idRecurso = valor; }
    public long getVersionDefinicion() { return versionDefinicion; }
    public void setVersionDefinicion(long valor) { versionDefinicion = valor; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String valor) { snapshotJson = valor; }
    public String getActuacion() { return actuacion; }
    public void setActuacion(String valor) { actuacion = valor; }
    public Long getIdRolActor() { return idRolActor; }
    public void setIdRolActor(Long valor) { idRolActor = valor; }
    public Instant getRegistradoEn() { return registradoEn; }
}
