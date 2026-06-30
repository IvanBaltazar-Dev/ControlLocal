package com.controllocal.rest.reports;

public class ContratoExclusividadReporteDto {

    private final String codigoCaptacion;
    private final String propietario;
    private final String agente;
    private final String direccion;
    private final String distrito;
    private final String area;
    private final String comision;
    private final String vigencia;
    private final String exclusividad;
    private final String fechaGeneracion;

    public ContratoExclusividadReporteDto(String codigoCaptacion, String propietario, String agente,
            String direccion, String distrito, String area, String comision, String vigencia,
            String exclusividad, String fechaGeneracion) {
        this.codigoCaptacion = codigoCaptacion;
        this.propietario = propietario;
        this.agente = agente;
        this.direccion = direccion;
        this.distrito = distrito;
        this.area = area;
        this.comision = comision;
        this.vigencia = vigencia;
        this.exclusividad = exclusividad;
        this.fechaGeneracion = fechaGeneracion;
    }

    public String getCodigoCaptacion() { return codigoCaptacion; }
    public String getPropietario() { return propietario; }
    public String getAgente() { return agente; }
    public String getDireccion() { return direccion; }
    public String getDistrito() { return distrito; }
    public String getArea() { return area; }
    public String getComision() { return comision; }
    public String getVigencia() { return vigencia; }
    public String getExclusividad() { return exclusividad; }
    public String getFechaGeneracion() { return fechaGeneracion; }
}
