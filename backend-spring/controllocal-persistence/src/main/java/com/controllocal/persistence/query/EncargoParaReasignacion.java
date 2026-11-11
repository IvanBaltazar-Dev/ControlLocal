package com.controllocal.persistence.query;

/** Proyección de gobierno RF017; no incluye condiciones ni expediente comercial. */
public interface EncargoParaReasignacion {
    Long getId();
    String getCodigoCaptacion();
    String getEstado();
    String getDireccionLocal();
    Long getIdAgente();
    String getAgenteNombre();
    long getVersion();
    boolean getTerminosAceptados();
    Long getIdDocumentoActual();
}
