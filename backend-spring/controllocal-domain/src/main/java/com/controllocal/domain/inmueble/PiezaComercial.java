package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** RF080: versión de pieza y dos salidas distintas. Ninguna implica publicación. */
@Entity @Table(name="pieza_comercial")
public class PiezaComercial extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_pieza") private Long id;
    @Column(name="referencia",nullable=false,updatable=false) private UUID referencia;
    @Column(name="familia",nullable=false,updatable=false) private UUID familia;
    @Column(name="version_contenido",nullable=false,updatable=false) private long versionContenido;
    @Column(name="id_captacion",nullable=false,updatable=false) private Long encargo;
    @Column(name="id_propiedad",nullable=false,updatable=false) private Long propiedad;
    @Column(name="id_rol_responsable",nullable=false,updatable=false) private Long responsable;
    @Column(name="solicitud",nullable=false,columnDefinition="text",updatable=false) private String solicitud;
    @Column(name="proyeccion",nullable=false,columnDefinition="text",updatable=false) private String proyeccion;
    @Column(name="huella_origen",nullable=false,length=64,updatable=false) private String huellaOrigen;
    @Column(name="formato",nullable=false,updatable=false) private String formato;
    @Column(name="plantilla_version",nullable=false,updatable=false) private String plantillaVersion;
    @Column(name="preparada_en",nullable=false,updatable=false) private Instant preparadaEn;
    @Version @Column(name="revision_fila",nullable=false) private long revision;
    @Column(name="archivo_privado_ref") private UUID archivoPrivado;
    @Column(name="clave_privada") private String clavePrivada;
    @Column(name="huella_privada",length=64) private String huellaPrivada;
    @Column(name="archivo_difusion_ref") private UUID archivoDifusion;
    @Column(name="clave_difusion") private String claveDifusion;
    @Column(name="huella_difusion",length=64) private String huellaDifusion;
    @Column(name="autorizada_en") private Instant autorizadaEn;
    protected PiezaComercial() { }
    public static PiezaComercial preparar(long org,long encargo,long propiedad,long responsable,
            UUID referencia,UUID familia,long version,String solicitud,String proyeccion,String huella,String formato,Instant ahora){
        var p=new PiezaComercial();p.setOrganizacionId(org);p.encargo=encargo;p.propiedad=propiedad;
        p.responsable=responsable;p.referencia=referencia;p.familia=familia;p.versionContenido=version;
        p.solicitud=solicitud;p.proyeccion=proyeccion;p.huellaOrigen=huella;p.formato=formato;p.plantillaVersion="V08";
        p.preparadaEn=ahora;return p;
    }
    public void registrarArchivo(String finalidad,UUID ref,String clave,String huella,Instant ahora){
        if("REVISION_PRIVADA".equals(finalidad)){
            if(archivoPrivado!=null)throw new IllegalStateException("La salida privada ya tiene una versión.");
            archivoPrivado=ref;clavePrivada=clave;huellaPrivada=huella;
        }else if("DIFUSION_AUTORIZADA".equals(finalidad)){
            if(archivoDifusion!=null)throw new IllegalStateException("La salida autorizada ya tiene una versión.");
            archivoDifusion=ref;claveDifusion=clave;huellaDifusion=huella;autorizadaEn=ahora;
        }else throw new IllegalArgumentException("Finalidad no admitida.");
    }
    public Long getId(){return id;} public UUID getReferencia(){return referencia;} public UUID getFamilia(){return familia;}
    public long getVersionContenido(){return versionContenido;} public Long getEncargo(){return encargo;}
    public Long getPropiedad(){return propiedad;} public Long getResponsable(){return responsable;}
    public String getSolicitud(){return solicitud;} public String getHuellaOrigen(){return huellaOrigen;}
    public String getProyeccion(){return proyeccion;}
    public String getFormato(){return formato;} public String getPlantillaVersion(){return plantillaVersion;}
    public Instant getPreparadaEn(){return preparadaEn;} public long getRevision(){return revision;}
    public UUID archivo(String finalidad){return "REVISION_PRIVADA".equals(finalidad)?archivoPrivado:archivoDifusion;}
    public String clave(String finalidad){return "REVISION_PRIVADA".equals(finalidad)?clavePrivada:claveDifusion;}
    public String huella(String finalidad){return "REVISION_PRIVADA".equals(finalidad)?huellaPrivada:huellaDifusion;}
}
