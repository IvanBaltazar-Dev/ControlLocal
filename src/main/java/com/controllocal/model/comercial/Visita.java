package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.controllocal.model.enums.EstadoVisita;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class Visita {

    private long idVisita;
    private LocalDate fechaVisita;
    private LocalTime horaVisita;
    private String observaciones;
    private EstadoVisita estado;
    private String resultado;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteResponsable;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public long getIdVisita() {
        return idVisita;
    }

    public void setIdVisita(long idVisita) {
        this.idVisita = idVisita;
    }

    public LocalDate getFechaVisita() {
        return fechaVisita;
    }

    public void setFechaVisita(LocalDate fechaVisita) {
        this.fechaVisita = fechaVisita;
    }

    public LocalTime getHoraVisita() {
        return horaVisita;
    }

    public void setHoraVisita(LocalTime horaVisita) {
        this.horaVisita = horaVisita;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public EstadoVisita getEstado() {
        return estado;
    }

    public void setEstado(EstadoVisita estado) {
        this.estado = estado;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
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

    public AgenteInmobiliario getAgenteResponsable() {
        return agenteResponsable;
    }

    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) {
        this.agenteResponsable = agenteResponsable;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public void programar() {
    }

    public void reprogramar(LocalDate fechaVisita, LocalTime horaVisita) {
    }

    public void cancelar() {
    }

    public void registrarResultado(String resultado) {
    }
}
