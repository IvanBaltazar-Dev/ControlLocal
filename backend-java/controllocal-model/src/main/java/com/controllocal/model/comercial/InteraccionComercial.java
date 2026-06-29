package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.CanalContacto;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class InteraccionComercial {

    private Long idInteraccion;
    private String contexto;
    private LocalDateTime fechaHora;
    private CanalContacto canalContacto;
    private String observaciones;
    private ResultadoInteraccion resultado;
    private OportunidadComercial oportunidadComercial;
    private Prospeccion prospeccion;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteResponsable;
    private LocalDateTime fechaCreacion;

    // Transcripcion o resumen de la conversacion, registrado con consentimiento.
    private String transcripcionNota;

    public String getTranscripcionNota() { return transcripcionNota; }
    public void setTranscripcionNota(String transcripcionNota) { this.transcripcionNota = transcripcionNota; }

    public Long getIdInteraccion() { return idInteraccion; }
    public void setIdInteraccion(Long idInteraccion) { this.idInteraccion = idInteraccion; }
    public String getContexto() { return contexto; }
    public void setContexto(String contexto) { this.contexto = contexto; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public CanalContacto getCanalContacto() { return canalContacto; }
    public void setCanalContacto(CanalContacto canalContacto) { this.canalContacto = canalContacto; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public ResultadoInteraccion getResultado() { return resultado; }
    public void setResultado(ResultadoInteraccion resultado) { this.resultado = resultado; }
    public OportunidadComercial getOportunidadComercial() { return oportunidadComercial; }
    public void setOportunidadComercial(OportunidadComercial oportunidadComercial) { this.oportunidadComercial = oportunidadComercial; }
    public Prospeccion getProspeccion() { return prospeccion; }
    public void setProspeccion(Prospeccion prospeccion) { this.prospeccion = prospeccion; }
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
        if (contexto == null || contexto.isBlank()) {
            if (prospeccion != null) {
                contexto = "PROSPECCION";
            } else if (captacion != null) {
                contexto = "CAPTACION";
            } else if (clienteInteresado != null) {
                contexto = "CLIENTE";
            } else {
                contexto = "OPORTUNIDAD";
            }
        }
        contexto = contexto.trim().toUpperCase();
        if (resultado == null) {
            resultado = switch (contexto) {
                case "PROSPECCION" -> ResultadoInteraccion.RECONTACTAR;
                case "CAPTACION" -> ResultadoInteraccion.PAUSAR_GESTION;
                case "CLIENTE" -> ResultadoInteraccion.SEGUIMIENTO_CLIENTE;
                default -> ResultadoInteraccion.INTERESADO_OPORTUNIDAD;
            };
        }
    }

    public void actualizarResultado(ResultadoInteraccion resultado) {
        this.resultado = resultado;
    }

    public void registrarObservacion(String observacion) {
        this.observaciones = observacion;
    }
}
