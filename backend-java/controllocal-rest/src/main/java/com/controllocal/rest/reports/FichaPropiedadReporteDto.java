package com.controllocal.rest.reports;

public record FichaPropiedadReporteDto(
        String codigo,
        String direccion,
        String distrito,
        String estado,
        String area,
        String rubro,
        String ambientes,
        String antiguedad,
        String referencia,
        String frente,
        String estacionamientos,
        String cargaElectrica,
        String aptoLicencia,
        String zonificacion,
        String cuotaMantenimiento,
        String precioReferencial,
        String comision,
        String urgencia,
        String exclusividad,
        String vigencia,
        String diasRestantes,
        String propietarioNombre,
        String propietarioTipo,
        String propietarioDocumento,
        String propietarioTelefono,
        String propietarioCorreo,
        String agente,
        String descripcion,
        String preciosHistoricos,
        String fotosResumen,
        String fechaGeneracion) {

    public String getCodigo() { return codigo; }
    public String getDireccion() { return direccion; }
    public String getDistrito() { return distrito; }
    public String getEstado() { return estado; }
    public String getArea() { return area; }
    public String getRubro() { return rubro; }
    public String getAmbientes() { return ambientes; }
    public String getAntiguedad() { return antiguedad; }
    public String getReferencia() { return referencia; }
    public String getFrente() { return frente; }
    public String getEstacionamientos() { return estacionamientos; }
    public String getCargaElectrica() { return cargaElectrica; }
    public String getAptoLicencia() { return aptoLicencia; }
    public String getZonificacion() { return zonificacion; }
    public String getCuotaMantenimiento() { return cuotaMantenimiento; }
    public String getPrecioReferencial() { return precioReferencial; }
    public String getComision() { return comision; }
    public String getUrgencia() { return urgencia; }
    public String getExclusividad() { return exclusividad; }
    public String getVigencia() { return vigencia; }
    public String getDiasRestantes() { return diasRestantes; }
    public String getPropietarioNombre() { return propietarioNombre; }
    public String getPropietarioTipo() { return propietarioTipo; }
    public String getPropietarioDocumento() { return propietarioDocumento; }
    public String getPropietarioTelefono() { return propietarioTelefono; }
    public String getPropietarioCorreo() { return propietarioCorreo; }
    public String getAgente() { return agente; }
    public String getDescripcion() { return descripcion; }
    public String getPreciosHistoricos() { return preciosHistoricos; }
    public String getFotosResumen() { return fotosResumen; }
    public String getFechaGeneracion() { return fechaGeneracion; }
}
