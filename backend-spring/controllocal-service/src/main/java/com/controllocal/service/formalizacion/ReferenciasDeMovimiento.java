package com.controllocal.service.formalizacion;

import com.controllocal.service.Actor;
import com.controllocal.service.ComisionService;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaContextoEconomico;

/** RF064: las referencias económicas no sustituyen permiso de identidad ni de evidencia. */
public interface ReferenciasDeMovimiento {
    void exigirLectura(ReferenciaContextoEconomico contexto,ReferenciaPacto pacto,
            ComisionService.DatosCobro original,Actor actor);
}
