package com.controllocal.domain.persona;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Detalle del rol CLIENTE interesado (lado DEMANDA). Igual que agente,
 * broker o propietario, el cliente es un ROL de una persona (Party-Role):
 * aqui vive solo lo comercial; nombre, documento, contacto y estado siguen
 * en {@link Persona}, sin duplicar.
 *
 * <p>En el cable congelado {@code idCliente} = {@code persona_rol.id} del rol
 * CLIENTE, igual que {@code idPropietario} = persona_rol del rol PROPIETARIO.
 */
@Entity
@Table(name = "detalle_cliente")
public class DetalleCliente extends EntidadDeOrganizacion {

    @Id
    @Column(name = "id_persona_rol")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id_persona_rol")
    private PersonaRol rol;

    @Column(name = "rubro_comercial", length = 150)
    private String rubroComercial;

    /**
     * Consentimiento para CONTACTO comercial. Es distinto del consentimiento
     * de uso del dato, que vive en la persona: uno habilita a llamarlo, el
     * otro a tratar su informacion.
     */
    @Column(name = "consentimiento_contacto")
    private Boolean consentimientoContacto;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    public Long getId() {
        return id;
    }

    public PersonaRol getRol() {
        return rol;
    }

    public void setRol(PersonaRol rol) {
        this.rol = rol;
    }

    public String getRubroComercial() {
        return rubroComercial;
    }

    public void setRubroComercial(String rubroComercial) {
        this.rubroComercial = rubroComercial;
    }

    public Boolean getConsentimientoContacto() {
        return consentimientoContacto;
    }

    public void setConsentimientoContacto(Boolean consentimientoContacto) {
        this.consentimientoContacto = consentimientoContacto;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
