package com.controllocal.model.usuario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.MotivoNoContinuidad;
import com.controllocal.model.comercial.ReasignacionCaptacion;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.ClienteInteresado;

public class AgenteInmobiliario extends UsuarioInterno {

    private long idAgente;
    private String codigoAgente;
    private String zonaAsignada;
    private LocalDate fechaIngreso;
    private EstadoOperativoAgente estadoOperativo;
    private List<Captacion> captacionesAsignadas = new ArrayList<>();
    private List<InteraccionComercial> interaccionesRegistradas = new ArrayList<>();
    private List<Visita> visitasProgramadas = new ArrayList<>();
    private List<SolicitudAlquiler> solicitudesRegistradas = new ArrayList<>();
    private List<MotivoNoContinuidad> motivosNoContinuidadRegistrados = new ArrayList<>();
    private List<ReasignacionCaptacion> reasignacionesComoAgenteAnterior = new ArrayList<>();
    private List<ReasignacionCaptacion> reasignacionesComoAgenteNuevo = new ArrayList<>();

    public long getIdAgente() {
        return idAgente;
    }

    public void setIdAgente(long idAgente) {
        this.idAgente = idAgente;
    }

    public String getCodigoAgente() {
        return codigoAgente;
    }

    public void setCodigoAgente(String codigoAgente) {
        this.codigoAgente = codigoAgente;
    }

    public String getZonaAsignada() {
        return zonaAsignada;
    }

    public void setZonaAsignada(String zonaAsignada) {
        this.zonaAsignada = zonaAsignada;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public EstadoOperativoAgente getEstadoOperativo() {
        return estadoOperativo;
    }

    public void setEstadoOperativo(EstadoOperativoAgente estadoOperativo) {
        this.estadoOperativo = estadoOperativo;
    }

    public List<Captacion> getCaptacionesAsignadas() {
        return captacionesAsignadas;
    }

    public void setCaptacionesAsignadas(List<Captacion> captacionesAsignadas) {
        this.captacionesAsignadas = captacionesAsignadas;
    }

    public List<Visita> getVisitasProgramadas() {
        return visitasProgramadas;
    }

    public void setVisitasProgramadas(List<Visita> visitasProgramadas) {
        this.visitasProgramadas = visitasProgramadas;
    }

    public List<InteraccionComercial> getInteraccionesRegistradas() {
        return interaccionesRegistradas;
    }

    public void setInteraccionesRegistradas(List<InteraccionComercial> interaccionesRegistradas) {
        this.interaccionesRegistradas = interaccionesRegistradas;
    }

    public List<SolicitudAlquiler> getSolicitudesRegistradas() {
        return solicitudesRegistradas;
    }

    public void setSolicitudesRegistradas(List<SolicitudAlquiler> solicitudesRegistradas) {
        this.solicitudesRegistradas = solicitudesRegistradas;
    }

    public List<ReasignacionCaptacion> getReasignacionesComoAgenteAnterior() {
        return reasignacionesComoAgenteAnterior;
    }

    public void setReasignacionesComoAgenteAnterior(List<ReasignacionCaptacion> reasignacionesComoAgenteAnterior) {
        this.reasignacionesComoAgenteAnterior = reasignacionesComoAgenteAnterior;
    }

    public List<ReasignacionCaptacion> getReasignacionesComoAgenteNuevo() {
        return reasignacionesComoAgenteNuevo;
    }

    public void setReasignacionesComoAgenteNuevo(List<ReasignacionCaptacion> reasignacionesComoAgenteNuevo) {
        this.reasignacionesComoAgenteNuevo = reasignacionesComoAgenteNuevo;
    }

    public List<MotivoNoContinuidad> getMotivosNoContinuidadRegistrados() {
        return motivosNoContinuidadRegistrados;
    }

    public void setMotivosNoContinuidadRegistrados(List<MotivoNoContinuidad> motivosNoContinuidadRegistrados) {
        this.motivosNoContinuidadRegistrados = motivosNoContinuidadRegistrados;
    }

    public void registrarLocal(LocalComercial localComercial) {
    }

    public void registrarCaptacion(Captacion captacion) {
    }

    public void registrarClienteInteresado(ClienteInteresado clienteInteresado) {
    }

    public void registrarInteraccion(InteraccionComercial interaccionComercial) {
    }

    public void programarVisita(Visita visita) {
    }

    public void registrarSolicitud(SolicitudAlquiler solicitudAlquiler) {
    }
}
