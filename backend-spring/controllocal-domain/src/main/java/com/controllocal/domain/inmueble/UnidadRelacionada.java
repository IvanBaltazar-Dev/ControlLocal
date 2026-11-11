package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Relacion fisica entre dos propiedades del mismo tenant.
 *
 * <p>La unidad relacionada tiene su propia identidad en {@link Propiedad};
 * esta fila solo conserva como se vinculan ambas propiedades y durante que
 * periodo fue valido el vinculo. No representa una oferta ni un paquete
 * comercial.
 */
@Entity
@Table(name = "unidad_relacionada")
public class UnidadRelacionada extends EntidadDeOrganizacion {

    public static final String ACCESORIA = "A";
    public static final String INDEPENDIENTE = "I";
    public static final String REGLAMENTO_INTERNO = "C";
    public static final String ORIGEN_MATRIZ_UNIDAD = "O";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_unidad_relacionada")
    private Long id;

    @Column(name = "id_propiedad_principal", nullable = false)
    private Long idPropiedadPrincipal;

    @Column(name = "id_propiedad_relacionada", nullable = false)
    private Long idPropiedadRelacionada;

    @Column(name = "tipo_relacion", nullable = false, length = 1)
    private String tipoRelacion;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @Column(name = "situacion_registral", columnDefinition = "text")
    private String situacionRegistral;
    @Column(name = "fuente", columnDefinition = "text")
    private String fuente;
    @Column(name = "procedencia", columnDefinition = "text")
    private String procedencia;
    @Column(name = "version_matriz_observada")
    private Long versionMatrizObservada;
    @Column(name = "version_unidad_observada")
    private Long versionUnidadObservada;
    @Column(name = "id_rol_actor")
    private Long idRolActor;
    @Column(name = "registrado_en")
    private Instant registradoEn;

    public Long getId() {
        return id;
    }

    public Long getIdPropiedadPrincipal() {
        return idPropiedadPrincipal;
    }

    public void setIdPropiedadPrincipal(Long idPropiedadPrincipal) {
        this.idPropiedadPrincipal = idPropiedadPrincipal;
    }

    public Long getIdPropiedadRelacionada() {
        return idPropiedadRelacionada;
    }

    public void setIdPropiedadRelacionada(Long idPropiedadRelacionada) {
        this.idPropiedadRelacionada = idPropiedadRelacionada;
    }

    public String getTipoRelacion() {
        return tipoRelacion;
    }

    public void setTipoRelacion(String tipoRelacion) {
        this.tipoRelacion = tipoRelacion;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public void setVigenteDesde(LocalDate vigenteDesde) {
        this.vigenteDesde = vigenteDesde;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(LocalDate vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public String getSituacionRegistral() { return situacionRegistral; }
    public String getFuente() { return fuente; }
    public String getProcedencia() { return procedencia; }
    public Long getVersionMatrizObservada() { return versionMatrizObservada; }
    public Long getVersionUnidadObservada() { return versionUnidadObservada; }
    public Long getIdRolActor() { return idRolActor; }
    public Instant getRegistradoEn() { return registradoEn; }

    @PreUpdate
    void preUpdate() {
        fechaActualizacion = OffsetDateTime.now();
    }

    public static UnidadRelacionada crear(Long organizacionId, Long principal,
                                          Long relacionada, String tipo, LocalDate desde) {
        if (principal == null || relacionada == null || principal.equals(relacionada)) {
            throw new IllegalArgumentException("Una relacion fisica exige dos propiedades distintas.");
        }
        if (!ACCESORIA.equals(tipo) && !INDEPENDIENTE.equals(tipo)
                && !REGLAMENTO_INTERNO.equals(tipo)) {
            throw new IllegalArgumentException("Tipo de relacion fisica invalido: " + tipo);
        }
        if (desde == null) {
            throw new IllegalArgumentException("La relacion fisica exige su fecha de inicio.");
        }
        UnidadRelacionada relacion = new UnidadRelacionada();
        relacion.setOrganizacionId(organizacionId);
        relacion.idPropiedadPrincipal = principal;
        relacion.idPropiedadRelacionada = relacionada;
        relacion.tipoRelacion = tipo;
        relacion.vigenteDesde = desde;
        return relacion;
    }

    public boolean estaVigente() {
        return vigenteHasta == null;
    }

    /** Hecho RF014 explícito: no convierte asociaciones A/I/C ni declara independencia jurídica. */
    public static UnidadRelacionada crearOrigen(Long organizacion, Long matriz, Long unidad,
            LocalDate desde, LocalDate hasta, String situacion, String fuente, String procedencia,
            long versionMatriz, long versionUnidad, long actor, Instant registradoEn) {
        if (organizacion == null || matriz == null || unidad == null || matriz.equals(unidad))
            throw new IllegalArgumentException("El origen requiere dos propiedades distintas del mismo contexto.");
        if (desde == null || (hasta != null && hasta.isBefore(desde)))
            throw new IllegalArgumentException("La vigencia del origen requiere un intervalo válido.");
        if (fuente == null || fuente.isBlank() || procedencia == null || procedencia.isBlank() || registradoEn == null)
            throw new IllegalArgumentException("El origen conserva fuente, procedencia y fecha de registro.");
        if (versionMatriz < 0 || versionUnidad < 0 || actor <= 0)
            throw new IllegalArgumentException("Las revisiones físicas y el actor del origen deben ser válidos.");
        UnidadRelacionada r = new UnidadRelacionada();
        r.setOrganizacionId(organizacion);
        r.idPropiedadPrincipal = matriz; r.idPropiedadRelacionada = unidad;
        r.tipoRelacion = ORIGEN_MATRIZ_UNIDAD;
        r.vigenteDesde = desde; r.vigenteHasta = hasta;
        r.situacionRegistral = situacion == null || situacion.isBlank() ? null : situacion.trim();
        r.fuente = fuente.trim(); r.procedencia = procedencia;
        r.versionMatrizObservada = versionMatriz; r.versionUnidadObservada = versionUnidad;
        r.idRolActor = actor; r.registradoEn = registradoEn;
        return r;
    }
}
