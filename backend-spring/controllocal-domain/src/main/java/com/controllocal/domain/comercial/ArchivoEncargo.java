package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Vínculo privado de recurso y finalidad; el UUID no es una concesión de acceso. */
@Entity
@Table(name = "archivo_encargo")
public class ArchivoEncargo extends EntidadDeOrganizacion {
    public static final String PDF = "PDF_ENCARGO", ACEPTACION = "ACEPTACION_ENCARGO",
            TITULARIDAD = "TITULARIDAD_ENCARGO", REPRESENTACION = "REPRESENTACION_ENCARGO",
            FIN = "FIN_ENCARGO", HITO = "HITO_CONTRACTUAL", COBRO = "COBRO_COMISION", REPORTE = "REPORTE_AVANCE";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivo_encargo") private Long id;
    @Version @Column(name = "version", nullable = false) private long version;
    @Column(name = "archivo_ref", nullable = false, unique = true) private UUID archivoRef;
    @Column(name = "id_captacion") private Long idEncargo;
    @Column(name = "id_documento_encargo") private Long idDocumento;
    @Column(name = "id_rol_autor", nullable = false) private Long idAutor;
    @Column(name = "finalidad", nullable = false, length = 40) private String finalidad;
    @Column(name = "clave_proveedor", nullable = false, length = 500) private String claveProveedor;
    @Column(name = "nombre", nullable = false, length = 200) private String nombre;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "huella", nullable = false, length = 64) private String huella;
    @Column(name = "registrado_en", nullable = false) private OffsetDateTime registradoEn;
    @Column(name="origen_referencia") private UUID origenReferencia;
    @Column(name="origen_tipo",length=40) private String origenTipo;
    @Column(name="origen_huella_vinculo",length=64) private String origenHuellaVinculo;
    @Column(name="version_destino") private Long versionDestino;
    protected ArchivoEncargo() { }
    public static ArchivoEncargo registrar(long org, UUID ref, Long encargo, Long documento,
            long autor, String finalidad, String clave, String nombre, String tipo, String huella,
            OffsetDateTime ahora) {
        ArchivoEncargo a = new ArchivoEncargo(); a.setOrganizacionId(org); a.archivoRef = ref;
        a.idEncargo = encargo; a.idDocumento = documento; a.idAutor = autor; a.finalidad = finalidad;
        a.claveProveedor = clave; a.nombre = nombre; a.contentType = tipo; a.huella = huella;
        a.registradoEn = ahora; return a;
    }
    public Long getId() { return id; }
    public ArchivoEncargo conOrigen(UUID referencia,String tipo,String huella,long versionDestino){
        if(origenReferencia!=null)throw new IllegalStateException("El original de una referencia es inmutable.");
        this.origenReferencia=java.util.Objects.requireNonNull(referencia);this.origenTipo=java.util.Objects.requireNonNull(tipo);
        this.origenHuellaVinculo=java.util.Objects.requireNonNull(huella);this.versionDestino=versionDestino;return this;
    }
    public UUID getOrigenReferencia(){return origenReferencia;}public String getOrigenTipo(){return origenTipo;}
    public String getOrigenHuellaVinculo(){return origenHuellaVinculo;}public Long getVersionDestino(){return versionDestino;}
    public long getVersion() { return version; }
    public void vincular(long encargo) {
        if(idEncargo!=null && idEncargo!=encargo)
            throw new IllegalStateException("La evidencia ya pertenece a otro episodio.");
        idEncargo=encargo;
    }
    public UUID getArchivoRef() { return archivoRef; }
    public Long getIdEncargo() { return idEncargo; }
    public Long getIdDocumento() { return idDocumento; }
    public Long getIdAutor() { return idAutor; }
    public String getFinalidad() { return finalidad; }
    public String getClaveProveedor() { return claveProveedor; }
    public String getNombre() { return nombre; }
    public String getContentType() { return contentType; }
    public String getHuella() { return huella; }
    public OffsetDateTime getRegistradoEn() { return registradoEn; }
}
