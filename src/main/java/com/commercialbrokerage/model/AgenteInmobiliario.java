package com.commercialbrokerage.model;

import java.util.ArrayList;
import java.util.List;

public class AgenteInmobiliario extends UsuarioInterno {

    private long idAgente;
    private String codigoAgente;
    private String telefono;
    private List<Captacion> captacionesAsignadas = new ArrayList<>();
    private List<Visita> visitasProgramadas = new ArrayList<>();
    private List<SolicitudAlquiler> solicitudesRegistradas = new ArrayList<>();
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

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
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

    public void registrarCaptacion(Captacion captacion) {
    }

    public void registrarClienteInteresado(ClienteInteresado clienteInteresado) {
    }

    public void programarVisita(Visita visita) {
    }

    public void registrarSolicitud(SolicitudAlquiler solicitudAlquiler) {
    }
}
