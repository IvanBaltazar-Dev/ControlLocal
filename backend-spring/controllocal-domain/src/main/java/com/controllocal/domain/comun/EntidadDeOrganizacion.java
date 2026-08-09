package com.controllocal.domain.comun;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

/**
 * Frontera de tenant (D-16/D-24, V6): toda entidad PRIVADA de una organizacion
 * lleva {@code organizacion_id} NOT NULL. Las entidades GLOBALES del catalogo
 * compartido (distrito, entidad_tipo, las de consentimiento no ligadas a una
 * persona) NO heredan de aqui — la clasificacion vive en el §1 del plan
 * {@code docs/ai/plan-migracion-v6-tenancy.md} y la vigila
 * {@code ArquitecturaTenancyTest}.
 *
 * <p>Aqui SI se usa superclase de columnas (a diferencia de
 * {@link Transicionable}, que es interfaz porque los esquemas de estado
 * divergen): el discriminador de tenant es la MISMA columna en las 15 tablas
 * privadas, y centralizarla es lo que hace verificable el criterio "ninguna
 * entidad privada acepta organizacion_id = NULL".
 *
 * <p>La columna es {@code Long} (no {@code long}) y no tiene DEFAULT en la BD
 * a proposito: si un caso de uso olvida fijar la organizacion, la escritura
 * falla en vez de colar la fila en un tenant arbitrario (V6.5 del plan).
 */
@MappedSuperclass
public abstract class EntidadDeOrganizacion {

    @Column(name = "organizacion_id", nullable = false)
    private Long organizacionId;

    /**
     * Red de seguridad del criterio #6 del gate: falla con un mensaje que dice
     * QUE entidad se intento grabar sin tenant, en vez del violacion-de-NOT-NULL
     * opaco de Postgres.
     */
    @PrePersist
    void exigirOrganizacion() {
        if (organizacionId == null) {
            throw new IllegalStateException(
                    "Se intento grabar " + getClass().getSimpleName() + " sin organizacion: "
                            + "el caso de uso debe fijar la organizacion del actor.");
        }
    }

    public Long getOrganizacionId() {
        return organizacionId;
    }

    public void setOrganizacionId(Long organizacionId) {
        this.organizacionId = organizacionId;
    }
}
