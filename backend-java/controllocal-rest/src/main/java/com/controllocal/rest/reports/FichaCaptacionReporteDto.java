package com.controllocal.rest.reports;

public class FichaCaptacionReporteDto {

    private final String codigoCaptacion;
    private final String direccion;
    private final String distrito;
    private final String propietario;
    private final String agente;
    private final String area;
    private final String rubro;
    private final String precioReferencial;
    private final String comision;
    private final String vigencia;
    private final String urgencia;
    private final String exclusividad;
    private final String estado;
    private final String observaciones;
    private final String fechaGeneracion;

    public FichaCaptacionReporteDto(String codigoCaptacion, String direccion, String distrito,
            String propietario, String agente, String area, String rubro, String precioReferencial,
            String comision, String vigencia, String urgencia, String exclusividad, String estado,
            String observaciones, String fechaGeneracion) {
        this.codigoCaptacion = codigoCaptacion;
        this.direccion = direccion;
        this.distrito = distrito;
        this.propietario = propietario;
        this.agente = agente;
        this.area = area;
        this.rubro = rubro;
        this.precioReferencial = precioReferencial;
        this.comision = comision;
        this.vigencia = vigencia;
        this.urgencia = urgencia;
        this.exclusividad = exclusividad;
        this.estado = estado;
        this.observaciones = observaciones;
        this.fechaGeneracion = fechaGeneracion;
    }

    public String getCodigoCaptacion() { return codigoCaptacion; }
    public String getDireccion() { return direccion; }
    public String getDistrito() { return distrito; }
    public String getPropietario() { return propietario; }
    public String getAgente() { return agente; }
    public String getArea() { return area; }
    public String getRubro() { return rubro; }
    public String getPrecioReferencial() { return precioReferencial; }
    public String getComision() { return comision; }
    public String getVigencia() { return vigencia; }
    public String getUrgencia() { return urgencia; }
    public String getExclusividad() { return exclusividad; }
    public String getEstado() { return estado; }
    public String getObservaciones() { return observaciones; }
    public String getFechaGeneracion() { return fechaGeneracion; }
}
