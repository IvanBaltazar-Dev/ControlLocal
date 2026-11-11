package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** CAT-E39: opciones privadas por jurisdicción, subordinadas a una definición sin sombrearla. */
@Entity
@Table(name = "perfil_catalogo_opciones")
public class PerfilCatalogoOpciones extends EntidadDeOrganizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_perfil") private Long id;
    @Column(name = "id_catalogo_atributo", nullable = false) private Long idCatalogoAtributo;
    @Column(name = "jurisdiccion", nullable = false, length = 160) private String jurisdiccion;
    @Column(name = "version_fuente", nullable = false, length = 100) private String versionFuente;
    @Column(name = "fuente_definicion", nullable = false) private String fuenteDefinicion;
    @Version @Column(name = "version_definicion", nullable = false) private long versionDefinicion;
    @Column(name = "activo", nullable = false) private boolean activo = true;
    @Column(name = "fecha_creacion", nullable = false) private Instant fechaCreacion = Instant.now();
    @Column(name = "fecha_retiro") private Instant fechaRetiro;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "perfil_catalogo_opcion", joinColumns = @JoinColumn(name = "id_perfil"))
    private Set<OpcionDeAtributo> opciones = new LinkedHashSet<>();

    public Long getId() { return id; }
    public Long getIdCatalogoAtributo() { return idCatalogoAtributo; }
    public void setIdCatalogoAtributo(Long id) { idCatalogoAtributo = id; }
    public String getJurisdiccion() { return jurisdiccion; }
    public void setJurisdiccion(String valor) { jurisdiccion = valor; }
    public String getVersionFuente() { return versionFuente; }
    public void setVersionFuente(String valor) { versionFuente = valor; }
    public String getFuenteDefinicion() { return fuenteDefinicion; }
    public void setFuenteDefinicion(String valor) { fuenteDefinicion = valor; }
    public long getVersionDefinicion() { return versionDefinicion; }
    public boolean isActivo() { return activo; }
    public void retirar(Instant ahora) { activo = false; fechaRetiro = ahora; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaRetiro() { return fechaRetiro; }
    public Set<OpcionDeAtributo> getOpciones() { return opciones; }
    public void setOpciones(Set<OpcionDeAtributo> valor) { opciones = valor; }
}
