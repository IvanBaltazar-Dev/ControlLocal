package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.TipoDocumentoRequerido;
import com.controllocal.model.comercial.enums.OperacionRequerimiento;

public interface TipoDocumentoRequeridoDAO extends CrudDAO<TipoDocumentoRequerido> {
    List<TipoDocumentoRequerido> listarRequeridos(OperacionRequerimiento tipoOperacion);
    List<TipoDocumentoRequerido> listarFaltantes(Long idSolicitud, OperacionRequerimiento tipoOperacion);
}
