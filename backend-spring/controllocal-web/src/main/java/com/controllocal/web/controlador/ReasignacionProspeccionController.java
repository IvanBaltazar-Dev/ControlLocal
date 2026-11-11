package com.controllocal.web.controlador;

import com.controllocal.service.actividad.ReasignacionDeProspeccion;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Gobierno RF017: proyección mínima y comando humano, sin lectura del expediente comercial. */
@RestController
@RequestMapping("prospecciones/{id}/reasignacion")
@PreAuthorize("hasAnyRole('BROKER','TENANT_ADMIN')")
public class ReasignacionProspeccionController {
    private final ReasignacionDeProspeccion caso;
    private final ProcedenciaDeCabeceras procedencias;
    public ReasignacionProspeccionController(ReasignacionDeProspeccion c,ProcedenciaDeCabeceras p){caso=c;procedencias=p;}
    @GetMapping @CapacidadReconocida("consultar_reasignacion_prospeccion")
    public ReasignacionDeProspeccion.Contexto consultar(@PathVariable long id){return caso.consultar(id,SesionActual.actor());}
    public record Cambio(@com.fasterxml.jackson.annotation.JsonProperty(value="versionEsperada",required=true)
                         @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL)
                         long versionEsperada,long responsableObservado,long responsableNuevo,String motivo) { }
    @PostMapping @CapacidadReconocida("reasignar_prospeccion")
    public ReasignacionDeProspeccion.Resultado reasignar(@PathVariable long id,@RequestBody Cambio d,
            @RequestHeader("Idempotency-Key")String clave,HttpServletRequest request){
        return caso.reasignar(new ReasignacionDeProspeccion.Datos(id,d.versionEsperada(),d.responsableObservado(),d.responsableNuevo(),d.motivo()),
                SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(request));
    }
}
