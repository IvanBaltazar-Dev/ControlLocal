package com.controllocal.model.comercial;

import com.controllocal.model.comercial.enums.OperacionRequerimiento;

public class TipoDocumentoRequerido {
    private Long idTipoDocumentoRequerido;
    private OperacionRequerimiento tipoOperacion;
    private String tipoDocumento;
    private boolean obligatorio;
    private boolean activo;
    private String descripcion;

    public Long getIdTipoDocumentoRequerido() { return idTipoDocumentoRequerido; }
    public void setIdTipoDocumentoRequerido(Long idTipoDocumentoRequerido) { this.idTipoDocumentoRequerido = idTipoDocumentoRequerido; }
    public OperacionRequerimiento getTipoOperacion() { return tipoOperacion; }
    public void setTipoOperacion(OperacionRequerimiento tipoOperacion) { this.tipoOperacion = tipoOperacion; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public boolean isObligatorio() { return obligatorio; }
    public void setObligatorio(boolean obligatorio) { this.obligatorio = obligatorio; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
