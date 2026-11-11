package com.controllocal.domain.organizacion;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;

/**
 * Decisión de pertenencia de CAT-E01, con snapshot del gobierno y aceptación del titular.
 * La referencia es un selector opaco: no acredita identidad, correo ni autoridad por sí sola.
 * Las credenciales y el canal personal pertenecen exclusivamente a CuentaAcceso.
 */
@Entity
@Table(name = "invitacion_membresia")
public class InvitacionMembresia extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invitacion_membresia")
    private Long id;

    @Column(name = "referencia", nullable = false, updatable = false, unique = true)
    private java.util.UUID referencia;

    @Column(name = "correo_cuenta", nullable = false, updatable = false, length = 320)
    private String correoCuenta;

    @Column(name = "id_persona_local", updatable = false)
    private Long idPersonaLocal;

    @Column(name = "nombre_local", updatable = false, length = 150)
    private String nombre;

    @Column(name = "tipo_persona", updatable = false, length = 1)
    private String tipoPersona;

    @Column(name = "tipo_documento", updatable = false, length = 1)
    private String tipoDocumento;

    @Column(name = "numero_documento", updatable = false, length = 30)
    private String numeroDocumento;

    @Column(name = "telefono", updatable = false, length = 20)
    private String telefono;

    @Column(name = "correo_laboral", updatable = false, length = 150)
    private String correoLaboral;

    @Column(name = "rol_principal", updatable = false, length = 16)
    private String rolPrincipal;

    @Column(name = "administrar", nullable = false, updatable = false)
    private boolean administrar;

    @Column(name = "id_broker_supervisor", updatable = false)
    private Long idBrokerSupervisor;

    @Column(name = "codigo_interno", updatable = false, length = 20)
    private String codigoInterno;

    @Column(name = "zona", updatable = false, length = 100)
    private String zona;

    @Column(name = "estado_operativo", updatable = false, length = 1)
    private String estadoOperativo;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private java.time.Instant creadaEn;

    @Column(name = "vence_en", nullable = false, updatable = false)
    private java.time.Instant venceEn;

    @Column(name = "id_persona_emisora", nullable = false, updatable = false)
    private Long idPersonaEmisora;

    @Column(name = "id_rol_emisor", nullable = false, updatable = false)
    private Long idRolEmisor;

    @Column(name = "motivo", nullable = false, updatable = false, length = 500)
    private String motivo;

    @Column(name = "aceptada_en")
    private java.time.Instant aceptadaEn;

    @Column(name = "id_cuenta_aceptante")
    private Long idCuentaAceptante;

    @Column(name = "id_membresia_aceptada")
    private Long idMembresiaAceptada;

    @Column(name = "id_persona_aceptada")
    private Long idPersonaAceptada;

    @Column(name = "id_rol_aceptado")
    private Long idRolAceptado;

    @Column(name = "retirada_en") private java.time.Instant retiradaEn;
    @Column(name = "motivo_retiro", length = 500) private String motivoRetiro;
    @Column(name = "id_persona_retiro") private Long idPersonaRetiro;
    @Column(name = "id_rol_retiro") private Long idRolRetiro;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Long getId() { return id; }
    public java.util.UUID getReferencia() { return referencia; }
    public void setReferencia(java.util.UUID valor) { referencia = valor; }
    public String getCorreoCuenta() { return correoCuenta; }
    public void setCorreoCuenta(String valor) { correoCuenta = valor; }
    public Long getIdPersonaLocal() { return idPersonaLocal; }
    public void setIdPersonaLocal(Long valor) { idPersonaLocal = valor; }
    public String getNombre() { return nombre; }
    public void setNombre(String valor) { nombre = valor; }
    public String getTipoPersona() { return tipoPersona; }
    public void setTipoPersona(String valor) { tipoPersona = valor; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String valor) { tipoDocumento = valor; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String valor) { numeroDocumento = valor; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String valor) { telefono = valor; }
    public String getCorreoLaboral() { return correoLaboral; }
    public void setCorreoLaboral(String valor) { correoLaboral = valor; }
    public String getRolPrincipal() { return rolPrincipal; }
    public void setRolPrincipal(String valor) { rolPrincipal = valor; }
    public boolean isAdministrar() { return administrar; }
    public void setAdministrar(boolean valor) { administrar = valor; }
    public Long getIdBrokerSupervisor() { return idBrokerSupervisor; }
    public void setIdBrokerSupervisor(Long valor) { idBrokerSupervisor = valor; }
    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String valor) { codigoInterno = valor; }
    public String getZona() { return zona; }
    public void setZona(String valor) { zona = valor; }
    public String getEstadoOperativo() { return estadoOperativo; }
    public void setEstadoOperativo(String valor) { estadoOperativo = valor; }
    public java.time.Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(java.time.Instant valor) { creadaEn = valor; }
    public java.time.Instant getVenceEn() { return venceEn; }
    public void setVenceEn(java.time.Instant valor) { venceEn = valor; }
    public Long getIdPersonaEmisora() { return idPersonaEmisora; }
    public void setIdPersonaEmisora(Long valor) { idPersonaEmisora = valor; }
    public Long getIdRolEmisor() { return idRolEmisor; }
    public void setIdRolEmisor(Long valor) { idRolEmisor = valor; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String valor) { motivo = valor; }
    public java.time.Instant getAceptadaEn() { return aceptadaEn; }
    public void setAceptadaEn(java.time.Instant valor) { aceptadaEn = valor; }
    public Long getIdCuentaAceptante() { return idCuentaAceptante; }
    public void setIdCuentaAceptante(Long valor) { idCuentaAceptante = valor; }
    public Long getIdMembresiaAceptada() { return idMembresiaAceptada; }
    public void setIdMembresiaAceptada(Long valor) { idMembresiaAceptada = valor; }
    public Long getIdPersonaAceptada() { return idPersonaAceptada; }
    public void setIdPersonaAceptada(Long valor) { idPersonaAceptada = valor; }
    public Long getIdRolAceptado() { return idRolAceptado; }
    public void setIdRolAceptado(Long valor) { idRolAceptado = valor; }
    public java.time.Instant getRetiradaEn() { return retiradaEn; }
    public void setRetiradaEn(java.time.Instant valor) { retiradaEn=valor; }
    public String getMotivoRetiro() { return motivoRetiro; }
    public void setMotivoRetiro(String valor) { motivoRetiro=valor; }
    public Long getIdPersonaRetiro() { return idPersonaRetiro; }
    public void setIdPersonaRetiro(Long valor) { idPersonaRetiro=valor; }
    public Long getIdRolRetiro() { return idRolRetiro; }
    public void setIdRolRetiro(Long valor) { idRolRetiro=valor; }
    public long getVersion() { return version; }
}

