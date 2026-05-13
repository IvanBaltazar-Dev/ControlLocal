package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class InteraccionComercial {

    private Long idInteraccion;
    private LocalDateTime fechaHora;
    private CanalContacto canalContacto;
    private String observaciones;
    private ResultadoInteraccion resultado;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteResponsable;
    private LocalDateTime fechaCreacion;

    public Long getIdInteraccion() { return idInteraccion; }
    public void setIdInteraccion(Long idInteraccion) { this.idInteraccion = idInteraccion; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public CanalContacto getCanalContacto() { return canalContacto; }
    public void setCanalContacto(CanalContacto canalContacto) { this.canalContacto = canalContacto; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public ResultadoInteraccion getResultado() { return resultado; }
    public void setResultado(ResultadoInteraccion resultado) { this.resultado = resultado; }
    public ClienteInteresado getClienteInteresado() { return clienteInteresado; }
    public void setClienteInteresado(ClienteInteresado clienteInteresado) { this.clienteInteresado = clienteInteresado; }
    public Captacion getCaptacion() { return captacion; }
    public void setCaptacion(Captacion captacion) { this.captacion = captacion; }
    public AgenteInmobiliario getAgenteResponsable() { return agenteResponsable; }
    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) { this.agenteResponsable = agenteResponsable; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public void registrar() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
        if (resultado == null) {
            resultado = ResultadoInteraccion.PENDIENTE;
        }
    }

    public void actualizarResultado(ResultadoInteraccion resultado) {
        this.resultado = resultado;
    }

    public void registrarObservacion(String observacion) {
        this.observaciones = observacion;
    }
}
