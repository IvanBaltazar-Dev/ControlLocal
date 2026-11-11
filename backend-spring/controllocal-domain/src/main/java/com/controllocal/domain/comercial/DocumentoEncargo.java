package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Versión privada inmutable. Preparar este documento no materializa ni aprueba un Encargo. */
@Entity
@Table(name = "documento_encargo")
public class DocumentoEncargo extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento_encargo") private Long id;
    @Version @Column(name = "version", nullable = false) private long version;
    @Column(name = "archivo_ref", nullable = false, unique = true) private UUID archivoRef;
    @Column(name = "id_captacion") private Long idEncargo;
    @Column(name = "version_encargo", nullable = false) private long versionEncargo;
    @Column(name = "id_rol_autor", nullable = false) private Long idAutor;
    @Column(name = "plantilla_version", nullable = false, length = 100) private String plantillaVersion;
    @Column(name = "huella", nullable = false, length = 64) private String huella;
    @Column(name = "contenido", nullable = false, columnDefinition = "text") private String contenido;
    @Column(name = "faltantes", nullable = false, columnDefinition = "text") private String faltantes;
    @Column(name = "preparado_en", nullable = false) private OffsetDateTime preparadoEn;
    protected DocumentoEncargo() { }
    public static DocumentoEncargo preparar(long org, UUID ref, Long encargo, long version,
            long autor, String plantilla, String huella, String contenido, String faltantes,
            OffsetDateTime ahora) {
        DocumentoEncargo d = new DocumentoEncargo(); d.setOrganizacionId(org); d.archivoRef = ref;
        d.idEncargo = encargo; d.versionEncargo = version; d.idAutor = autor;
        d.plantillaVersion = plantilla; d.huella = huella; d.contenido = contenido;
        d.faltantes = faltantes; d.preparadoEn = ahora; return d;
    }
    public Long getId() { return id; }
    public long getVersion() { return version; }
    public void vincular(long encargo) {
        if(idEncargo!=null && idEncargo!=encargo)
            throw new IllegalStateException("El documento ya pertenece a otro episodio.");
        idEncargo=encargo;
    }
    public UUID getArchivoRef() { return archivoRef; }
    public Long getIdEncargo() { return idEncargo; }
    public long getVersionEncargo() { return versionEncargo; }
    public Long getIdAutor() { return idAutor; }
    public String getPlantillaVersion() { return plantillaVersion; }
    public String getHuella() { return huella; }
    public String getContenido() { return contenido; }
    public String getFaltantes() { return faltantes; }
    public OffsetDateTime getPreparadoEn() { return preparadoEn; }
}
