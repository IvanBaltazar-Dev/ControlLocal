package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.persona.ClienteInteresado;

public class InteraccionComercial {

    private long idInteraccion;
    private LocalDateTime fechaInteraccion;
    private String canalContacto;
    private String observaciones;
    private String estado;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;

    public long getIdInteraccion() {
        return idInteraccion;
    }

    public void setIdInteraccion(long idInteraccion) {
        this.idInteraccion = idInteraccion;
    }

    public LocalDateTime getFechaInteraccion() {
        return fechaInteraccion;
    }

    public void setFechaInteraccion(LocalDateTime fechaInteraccion) {
        this.fechaInteraccion = fechaInteraccion;
    }

    public String getCanalContacto() {
        return canalContacto;
    }

    public void setCanalContacto(String canalContacto) {
        this.canalContacto = canalContacto;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public ClienteInteresado getClienteInteresado() {
        return clienteInteresado;
    }

    public void setClienteInteresado(ClienteInteresado clienteInteresado) {
        this.clienteInteresado = clienteInteresado;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public void setCaptacion(Captacion captacion) {
        this.captacion = captacion;
    }

    public void actualizarEstado(String estado) {
    }

    public void registrarObservacion(String observacion) {
    }
}
