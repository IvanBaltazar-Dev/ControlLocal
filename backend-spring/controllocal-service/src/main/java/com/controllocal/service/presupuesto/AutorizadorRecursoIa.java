package com.controllocal.service.presupuesto;

import com.controllocal.service.Actor;

/** C6 conserva autoridad por recurso/finalidad/versión. Cero o varios propietarios deniega. */
public interface AutorizadorRecursoIa {
    boolean atiende(String perfil,String tipoRecurso);
    void reautorizar(PresupuestoIaService.Solicitud solicitud,Actor actor);
    /** R24: el propietario acredita unidades/evidencia del MISMO trabajo; no supone tokens o un hallazgo. */
    default void exigirEvidenciaManual(long organizacion,java.util.UUID operacion,PresupuestoIaService.Recurso recurso,
            java.math.BigDecimal realizadas,String evidencia){throw new com.controllocal.service.excepcion.CapacidadDeshabilitadaException();}
}
