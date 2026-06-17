package com.controllocal.model.persona;

public class ClienteInteresado {

    private Long idCliente;
    private Persona persona;
    private String rubroComercial;

    // Consentimientos del interesado (Diccionario v2 P1).
    private Boolean consentimientoContacto;
    private Boolean consentimientoUsoDato;

    public Boolean getConsentimientoContacto() {
        return consentimientoContacto;
    }

    public void setConsentimientoContacto(Boolean consentimientoContacto) {
        this.consentimientoContacto = consentimientoContacto;
    }

    public Boolean getConsentimientoUsoDato() {
        return consentimientoUsoDato;
    }

    public void setConsentimientoUsoDato(Boolean consentimientoUsoDato) {
        this.consentimientoUsoDato = consentimientoUsoDato;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(Long idCliente) {
        this.idCliente = idCliente;
    }

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    public String getRubroComercial() {
        return rubroComercial;
    }

    public void setRubroComercial(String rubroComercial) {
        this.rubroComercial = rubroComercial;
    }

    public void activar() {
        persona().activar();
    }

    public void desactivar() {
        persona().desactivar();
    }

    public void actualizarDatos(String telefono, String correo, String nombresORazonSocial) {
        persona().actualizarDatos(telefono, correo, nombresORazonSocial);
    }

    private Persona persona() {
        if (persona == null) {
            persona = new Persona();
        }
        return persona;
    }
}
