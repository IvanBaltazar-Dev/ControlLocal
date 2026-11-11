package com.controllocal.domain.matching;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** D4: metadata técnica privada; no conserva ficha, atributos ni resultados de otro tenant. */
@Entity
@Table(name = "matching_recorrido")
public class RecorridoMatching extends EntidadDeOrganizacion {
    @Id
    @Column(name = "id_recorrido", nullable = false)
    private UUID id;
    @Column(name = "id_persona", nullable = false)
    private long idPersona;
    @Column(name = "id_rol", nullable = false)
    private long idRol;
    @Column(name = "rol", nullable = false, length = 20)
    private String rol;
    @Column(name = "huella_contexto", nullable = false, length = 64)
    private String huellaContexto;
    @Column(name = "tipo_origen", nullable = false, length = 20)
    private String tipoOrigen;
    @Column(name = "id_origen", nullable = false)
    private long idOrigen;
    @Column(name = "version_origen", nullable = false)
    private long versionOrigen;
    @Column(name = "huella_criterios", nullable = false, length = 64)
    private String huellaCriterios;
    @Column(name = "modalidad", nullable = false, length = 12)
    private String modalidad;
    @Column(name = "capas", nullable = false, length = 20)
    private String capas;
    @Column(name = "capas_no_disponibles", nullable = false, length = 20)
    private String capasNoDisponibles = "";
    @Column(name = "categoria", nullable = false, length = 32)
    private String categoria;
    @Column(name = "version_regla", nullable = false, length = 80)
    private String versionRegla;
    @Column(name = "corte", nullable = false)
    private Instant corte;
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
    @Column(name = "vence_en", nullable = false)
    private Instant venceEn;
    @Column(name = "tamano", nullable = false)
    private int tamano;
    @Column(name="contexto_exploracion",columnDefinition="text")
    private String contextoExploracion;
    @Column(name="huella_escalones",length=64)
    private String huellaEscalones;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getIdPersona() { return idPersona; }
    public void setIdPersona(long idPersona) { this.idPersona = idPersona; }

    public long getIdRol() { return idRol; }
    public void setIdRol(long idRol) { this.idRol = idRol; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getHuellaContexto() { return huellaContexto; }
    public void setHuellaContexto(String huellaContexto) { this.huellaContexto = huellaContexto; }

    public String getTipoOrigen() { return tipoOrigen; }
    public void setTipoOrigen(String tipoOrigen) { this.tipoOrigen = tipoOrigen; }

    public long getIdOrigen() { return idOrigen; }
    public void setIdOrigen(long idOrigen) { this.idOrigen = idOrigen; }

    public long getVersionOrigen() { return versionOrigen; }
    public void setVersionOrigen(long versionOrigen) { this.versionOrigen = versionOrigen; }

    public String getHuellaCriterios() { return huellaCriterios; }
    public void setHuellaCriterios(String huellaCriterios) { this.huellaCriterios = huellaCriterios; }

    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }

    public String getCapas() { return capas; }
    public void setCapas(String capas) { this.capas = capas; }

    public String getCapasNoDisponibles() { return capasNoDisponibles; }
    public void setCapasNoDisponibles(String capasNoDisponibles) { this.capasNoDisponibles = capasNoDisponibles; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getVersionRegla() { return versionRegla; }
    public void setVersionRegla(String versionRegla) { this.versionRegla = versionRegla; }

    public Instant getCorte() { return corte; }
    public void setCorte(Instant corte) { this.corte = corte; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getVenceEn() { return venceEn; }
    public void setVenceEn(Instant venceEn) { this.venceEn = venceEn; }

    public int getTamano() { return tamano; }
    public void setTamano(int tamano) { this.tamano = tamano; }
    public String getContextoExploracion(){return contextoExploracion;}
    public void setContextoExploracion(String contextoExploracion){this.contextoExploracion=contextoExploracion;}
    public String getHuellaEscalones(){return huellaEscalones;}
    public void setHuellaEscalones(String huellaEscalones){this.huellaEscalones=huellaEscalones;}
}

