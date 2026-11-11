package com.controllocal.service.formalizacion;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.formalizacion.HitosConfirmados.ContextoEconomico;
import com.controllocal.service.formalizacion.HitosConfirmados.FuenteHitoConfirmado;
import com.controllocal.service.soporte.Procedencia;

/** RF059/060: efecto interno de la confirmación; no otra puerta pública de cierre. */
public interface EfectosContractualesDeFormalizacion {
    record VinculosContractuales(Long contratoAlquiler,Long contratoVenta) { }
    VinculosContractuales aplicar(ContextoEconomico contexto,FuenteHitoConfirmado hito,
            Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia,
            FormalizacionService.AcuerdoFinalDocumentado acuerdoFinalRevisado,
            com.controllocal.service.ContratoService.PreparacionContrato preparacionRevisada);
}
