package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class Visita {

    private long idVisita;
    private LocalDateTime fechaVisita;
    private LocalDateTime horaVisita;
    private String observaciones;
    private String estado;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteInmobiliario;

    public long getIdVisita() {
        return idVisita;
    }

    public void setIdVisita(long idVisita) {
        this.idVisita = idVisita;
    }

    public LocalDateTime getFechaVisita() {
        return fechaVisita;
    }

    public void setFechaVisita(LocalDateTime fechaVisita) {
        this.fechaVisita = fechaVisita;
    }

    public LocalDateTime getHoraVisita() {
        return horaVisita;
    }

    public void setHoraVisita(LocalDateTime horaVisita) {
        this.horaVisita = horaVisita;
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

    public AgenteInmobiliario getAgenteInmobiliario() {
        return agenteInmobiliario;
    }

    public void setAgenteInmobiliario(AgenteInmobiliario agenteInmobiliario) {
        this.agenteInmobiliario = agenteInmobiliario;
    }

    public void programar() {
    }

    public void reprogramar(LocalDateTime fechaVisita, LocalDateTime horaVisita) {
    }

    public void cancelar() {
    }

    public void registrarResultado(String observacion) {
    }
}
