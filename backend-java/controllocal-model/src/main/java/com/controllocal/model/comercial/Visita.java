package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.controllocal.model.comercial.enums.EstadoVisita;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class Visita {

    private Long idVisita;
    private LocalDate fechaVisita;
    private LocalTime horaVisita;
    private String observaciones;
    private EstadoVisita estado;
    private ResultadoInteraccion resultado;
    private OportunidadComercial oportunidadComercial;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteResponsable;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Long getIdVisita() { return idVisita; }
    public void setIdVisita(Long idVisita) { this.idVisita = idVisita; }
    public LocalDate getFechaVisita() { return fechaVisita; }
    public void setFechaVisita(LocalDate fechaVisita) { this.fechaVisita = fechaVisita; }
    public LocalTime getHoraVisita() { return horaVisita; }
    public void setHoraVisita(LocalTime horaVisita) { this.horaVisita = horaVisita; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public EstadoVisita getEstado() { return estado; }
    public void setEstado(EstadoVisita estado) { this.estado = estado; }
    public ResultadoInteraccion getResultado() { return resultado; }
    public void setResultado(ResultadoInteraccion resultado) { this.resultado = resultado; }
    public OportunidadComercial getOportunidadComercial() { return oportunidadComercial; }
    public void setOportunidadComercial(OportunidadComercial oportunidadComercial) { this.oportunidadComercial = oportunidadComercial; }
    public ClienteInteresado getClienteInteresado() { return clienteInteresado; }
    public void setClienteInteresado(ClienteInteresado clienteInteresado) { this.clienteInteresado = clienteInteresado; }
    public Captacion getCaptacion() { return captacion; }
    public void setCaptacion(Captacion captacion) { this.captacion = captacion; }
    public AgenteInmobiliario getAgenteResponsable() { return agenteResponsable; }
    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) { this.agenteResponsable = agenteResponsable; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public void programar() {
        this.estado = EstadoVisita.PROGRAMADA;
    }

    public void reprogramar(LocalDate nuevaFecha, LocalTime nuevaHora) {
        this.fechaVisita = nuevaFecha;
        this.horaVisita = nuevaHora;
        this.estado = EstadoVisita.REPROGRAMADA;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void cancelar(String motivo) {
        this.estado = EstadoVisita.CANCELADA;
        this.observaciones = motivo;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void registrarResultado(ResultadoInteraccion resultado) {
        this.resultado = resultado;
        this.estado = EstadoVisita.REALIZADA;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * Estados desde los que la visita aun puede cambiar (reprogramarse,
     * cancelarse o registrarse su resultado). Una visita REALIZADA o CANCELADA
     * es terminal.
     */
    public boolean esModificable() {
        return estado == EstadoVisita.PROGRAMADA || estado == EstadoVisita.REPROGRAMADA;
    }
}
