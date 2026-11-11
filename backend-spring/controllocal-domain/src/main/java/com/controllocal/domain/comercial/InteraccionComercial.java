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
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.Instant;

/**
 * Identidad canónica de una comunicación C4. Sus vínculos autorizados viven
 * en el índice relacional y el contenido versionado en el contrato de Core.
 * Las columnas de contexto/resultado/canal anteriores se conservan para
 * lectura histórica; no se traducen al diccionario actual por equivalencia.
 */
@Entity
@Table(name = "interaccion_comercial")
public class InteraccionComercial extends EntidadDeOrganizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_interaccion")
    private Long id;

    @Version
    @Column(name="version",nullable=false)
    private long version;
    @Column(name="fecha_hecho")
    private LocalDate fechaHecho;
    @Column(name="procedencia_json")
    private String procedenciaJson;
    @Column(name="contexto_declarado_json")
    private String contextoDeclaradoJson;
    @Column(name="version_diccionario",length=60)
    private String versionDiccionario;
    @Column(name="registrado_en")
    private Instant registradoEn;
    @Column(name="id_rol_autor",updatable=false)
    private Long idAutor;
    @Column(name="autorizacion_json")
    private String autorizacionJson;

    @Column(name = "contexto", length = 20)
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

    @Column(name = "canal_contacto", length = 1)
    private String canalContacto;

    /** Codigo de ResultadoInteraccion: MIXTO en el cable (1 char o palabra). */
    @Column(name = "resultado", length = 30)
    private String resultado;

    @Column(name = "observaciones")
    private String observaciones;

    /** Nota dictada/transcrita del contacto (semilla del canal conversacional). */
    @Column(name = "transcripcion_nota")
    private String transcripcionNota;

    /**
     * Snapshot opcional del interlocutor que atendio o coordino el contacto.
     * No es una titularidad ni una autoridad sobre la propiedad.
     */
    @Column(name = "contacto_tipo", length = 50)
    private String contactoTipo;

    @Column(name = "contacto_nombre", length = 200)
    private String contactoNombre;

    @Column(name = "contacto_telefono", length = 50)
    private String contactoTelefono;

    @Column(name = "fecha_hora")
    private OffsetDateTime fechaHora;

    @PrePersist
    void prePersist() {
        if (versionDiccionario==null && fechaHora == null) {
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

    public String getContactoTipo() {
        return contactoTipo;
    }

    public void setContactoTipo(String contactoTipo) {
        this.contactoTipo = contactoTipo;
    }

    public String getContactoNombre() {
        return contactoNombre;
    }

    public void setContactoNombre(String contactoNombre) {
        this.contactoNombre = contactoNombre;
    }

    public String getContactoTelefono() {
        return contactoTelefono;
    }

    public void setContactoTelefono(String contactoTelefono) {
        this.contactoTelefono = contactoTelefono;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(OffsetDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public long getVersion(){return version;}
    public void setVersion(long valor){version=valor;}
    public String getVersionDiccionario(){return versionDiccionario;}
    public void setVersionDiccionario(String valor){versionDiccionario=valor;}
    public Instant getRegistradoEn(){return registradoEn;}
    public void setRegistradoEn(Instant valor){registradoEn=valor;}
    public Long getIdAutor(){return idAutor;}
    public void setIdAutor(Long valor){idAutor=valor;}
    public String getAutorizacionJson(){return autorizacionJson;}
    public void setAutorizacionJson(String valor){autorizacionJson=valor;}
    public LocalDate getFechaHecho(){return fechaHecho;}
    public void setFechaHecho(LocalDate valor){fechaHecho=valor;}
    public String getProcedenciaJson(){return procedenciaJson;}
    public void setProcedenciaJson(String valor){procedenciaJson=valor;}
    public String getContextoDeclaradoJson(){return contextoDeclaradoJson;}
    public void setContextoDeclaradoJson(String valor){contextoDeclaradoJson=valor;}
}
