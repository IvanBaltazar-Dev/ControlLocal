package com.controllocal.domain.inmueble;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * <b>El traspaso del responsable de una propiedad</b> (V87, P0-2).
 *
 * <p>Cambiar quien puede escribir los hechos de un inmueble es un hecho de
 * gobierno, y un hecho de gobierno sin rastro es indistinguible de no haber
 * ocurrido. La fila dice las cinco cosas que hacen falta para auditarlo:
 * <b>que propiedad</b>, <b>de quien</b> —vacio si estaba FALTANTE—, <b>a
 * quien</b>, <b>quien lo autorizo</b> y con que banda, y <b>cuando y por
 * que</b>.
 *
 * <p><b>Append-only.</b> No hay setter de actualizacion ni borrado en el
 * servicio: la historia de quien respondio por una propiedad se acumula, no se
 * corrige. Un traspaso equivocado se arregla con otro traspaso, que es
 * exactamente lo que paso.
 *
 * <p><b>Lo que esta fila NO es.</b> No es una reasignacion de encargo: no toca
 * ninguna {@code captacion} ni ninguna condicion comercial, y su gemela
 * {@code ReasignacionCaptacion} tampoco toca esta autoridad. Y no modifica
 * ningun atributo inmobiliario: cambiar de responsable no cambia el inmueble.
 */
@Entity
@Table(name = "asignacion_responsable_propiedad")
public class AsignacionResponsablePropiedad extends EntidadDeOrganizacion {

    /** La fijo el agente que registro una propiedad NUEVA. Una por propiedad. */
    public static final String ORIGEN_ALTA = "ALTA";
    /** La decidio un BROKER o el gobierno del tenant. Puede haber varias. */
    public static final String ORIGEN_TRASPASO = "TRASPASO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion")
    private Long id;

    @Column(name = "id_propiedad", nullable = false)
    private Long idPropiedad;

    /**
     * Quien respondia antes. <b>NULL en la primera asignacion</b> de una
     * propiedad que estaba FALTANTE — y ese NULL es informacion: dice que no
     * hubo predecesor, en vez de nombrar al agente de algun encargo, que seria
     * inventarle una procedencia al permiso.
     */
    @Column(name = "id_rol_responsable_anterior")
    private Long idRolResponsableAnterior;

    @Column(name = "id_rol_responsable_nuevo", nullable = false)
    private Long idRolResponsableNuevo;

    /** Persona que autorizo el traspaso. */
    @Column(name = "id_persona_actor", nullable = false)
    private Long idPersonaActor;

    /**
     * Banda con la que actuo: {@code BROKER} o {@code TENANT_ADMIN}. Se guarda
     * ademas de la persona porque alguien puede gobernar Y operar, y el rastro
     * tiene que decir cual de las dos uso (la leccion de H-09).
     */
    @Column(name = "tipo_rol_actor", nullable = false, length = 20)
    private String tipoRolActor;

    /**
     * <b>De donde sale esta asignacion</b> (V88): { ALTA} o { TRASPASO}.
     *
     * <p><b>No se deduce de { #idRolResponsableAnterior}</b>, y esa es la
     * razon de que exista la columna: la PRIMERA asignacion de una propiedad
     * FALTANTE tampoco tiene predecesor y es un traspaso. Medido antes de
     * anadirla: de 63 filas, 12 no tenian anterior y las 63 eran de BROKER --
     * deducirlo del NULL habria clasificado 12 traspasos como altas.
     */
    @Column(name = "origen", nullable = false, length = 8)
    private String origen;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "fecha_asignacion", nullable = false)
    private OffsetDateTime fechaAsignacion;

    @PrePersist
    void alGrabar() {
        if (fechaAsignacion == null) {
            fechaAsignacion = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getIdPropiedad() {
        return idPropiedad;
    }

    public void setIdPropiedad(Long idPropiedad) {
        this.idPropiedad = idPropiedad;
    }

    public Long getIdRolResponsableAnterior() {
        return idRolResponsableAnterior;
    }

    public void setIdRolResponsableAnterior(Long idRolResponsableAnterior) {
        this.idRolResponsableAnterior = idRolResponsableAnterior;
    }

    public Long getIdRolResponsableNuevo() {
        return idRolResponsableNuevo;
    }

    public void setIdRolResponsableNuevo(Long idRolResponsableNuevo) {
        this.idRolResponsableNuevo = idRolResponsableNuevo;
    }

    public Long getIdPersonaActor() {
        return idPersonaActor;
    }

    public void setIdPersonaActor(Long idPersonaActor) {
        this.idPersonaActor = idPersonaActor;
    }

    public String getTipoRolActor() {
        return tipoRolActor;
    }

    public void setTipoRolActor(String tipoRolActor) {
        this.tipoRolActor = tipoRolActor;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    /** ¿La fijo el alta de una propiedad nueva? */
    public boolean naceDelAlta() {
        return ORIGEN_ALTA.equals(origen);
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public OffsetDateTime getFechaAsignacion() {
        return fechaAsignacion;
    }
}
