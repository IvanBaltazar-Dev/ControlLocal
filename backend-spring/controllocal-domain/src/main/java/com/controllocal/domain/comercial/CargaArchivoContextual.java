package com.controllocal.domain.comercial;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** RF052: original técnico con contexto previsto; no vincula ni acredita conformidad. */
@Entity @Table(name="carga_archivo_contextual")
public class CargaArchivoContextual extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_carga_archivo") private Long id;
    @Version @Column(name="version",nullable=false) private long version;
    @Column(name="archivo_ref",nullable=false,unique=true) private UUID referencia;
    @Column(name="id_rol_autor",nullable=false) private Long autor;
    @Column(name="id_encargo_previsto") private Long encargo;
    @Column(name="id_documento_previsto") private Long documento;
    @Column(name="finalidad",nullable=false,length=40) private String finalidad;
    @Column(name="version_destino",nullable=false) private long versionDestino;
    @Column(name="clave_proveedor",nullable=false,length=500) private String clave;
    @Column(name="nombre",nullable=false,length=200) private String nombre;
    @Column(name="content_type",nullable=false,length=100) private String contentType;
    @Column(name="huella",nullable=false,length=64) private String huella;
    @Column(name="tamano_bytes",nullable=false) private long tamanoBytes;
    @Column(name="procedencia",nullable=false,columnDefinition="text") private String procedencia;
    @Column(name="cargada_en",nullable=false) private OffsetDateTime cargadaEn;
    protected CargaArchivoContextual(){}
    public static CargaArchivoContextual cargar(long org,UUID ref,long autor,Long encargo,Long documento,
            String finalidad,long versionDestino,String clave,String nombre,String contentType,String huella,
            long tamano,String procedencia,OffsetDateTime ahora){
        var c=new CargaArchivoContextual();c.setOrganizacionId(org);c.referencia=ref;c.autor=autor;c.encargo=encargo;c.documento=documento;
        c.finalidad=finalidad;c.versionDestino=versionDestino;c.clave=clave;c.nombre=nombre;c.contentType=contentType;c.huella=huella;
        c.tamanoBytes=tamano;c.procedencia=procedencia;c.cargadaEn=ahora;return c;
    }
    public Long getId(){return id;}public long getVersion(){return version;}public UUID getReferencia(){return referencia;}
    public Long getAutor(){return autor;}public Long getEncargo(){return encargo;}public Long getDocumento(){return documento;}
    public String getFinalidad(){return finalidad;}public long getVersionDestino(){return versionDestino;}
    public String getClave(){return clave;}public String getNombre(){return nombre;}public String getContentType(){return contentType;}
    public String getHuella(){return huella;}public long getTamanoBytes(){return tamanoBytes;}public String getProcedencia(){return procedencia;}
    public OffsetDateTime getCargadaEn(){return cargadaEn;}
}
