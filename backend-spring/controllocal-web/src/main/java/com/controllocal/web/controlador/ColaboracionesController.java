package com.controllocal.web.controlador;

import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.colaboracion.ColaboracionService;
import com.controllocal.service.colaboracion.CanalesColaboracionService;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Preparación tipada; el efecto sigue requiriendo revisión y confirmación canónicas. */
@RestController
@RequestMapping("colaboraciones")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class ColaboracionesController {
    private final ActuacionesService actuaciones;
    private final ColaboracionService colaboraciones;
    private final ProcedenciaDeCabeceras procedencias;
    public ColaboracionesController(ActuacionesService actuaciones,ColaboracionService colaboraciones,ProcedenciaDeCabeceras procedencias) {
        this.actuaciones=actuaciones;this.colaboraciones=colaboraciones;this.procedencias=procedencias;
    }
    @PostMapping("solicitudes/propuestas")
    @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("solicitar_colaboracion")
    public ActuacionesService.Propuesta solicitar(@RequestBody ColaboracionService.DatosSolicitud datos,HttpServletRequest r) {
        return actuaciones.prepararSolicitudColaboracion(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("invitaciones/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("invitar_colaboracion_encargo")
    public ActuacionesService.Propuesta invitar(@RequestBody ColaboracionService.DatosInvitacion datos,HttpServletRequest r){
        return actuaciones.prepararInvitacionColaboracion(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @GetMapping("invitaciones") @CapacidadReconocida("consultar_invitaciones_colaboracion")
    public java.util.List<ColaboracionService.Acuerdo> invitaciones(@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="20")int tamano){return colaboraciones.invitacionesRecibidas(SesionActual.actor(),pagina,tamano);}
    @GetMapping("invitaciones/originadas") @CapacidadReconocida("consultar_invitaciones_originadas")
    public java.util.List<ColaboracionService.Acuerdo> originadas(@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="20")int tamano){return colaboraciones.invitacionesOriginadas(SesionActual.actor(),pagina,tamano);}
    @PostMapping("decisiones/propuestas")
    @CapacidadReconocida("decidir_colaboracion")
    public ActuacionesService.Propuesta decidir(@RequestBody ColaboracionService.DatosDecision datos,HttpServletRequest r) {
        return actuaciones.prepararDecisionColaboracion(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("revocaciones/propuestas")
    @CapacidadReconocida("revocar_colaboracion")
    public ActuacionesService.Propuesta revocar(@RequestBody ColaboracionService.DatosRevocacion datos,HttpServletRequest r) {
        return actuaciones.prepararRevocacionColaboracion(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("canales/propuestas")
    @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("declarar_canal_colaboracion")
    public ActuacionesService.Propuesta declararCanal(@RequestBody CanalesColaboracionService.DatosDeclaracion datos,HttpServletRequest r) {
        return actuaciones.prepararCanalColaboracion(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @GetMapping("solicitudes/{referencia}")
    @CapacidadReconocida("consultar_acuerdo_colaboracion")
    public ColaboracionService.Acuerdo consultar(@PathVariable String referencia) {
        return colaboraciones.consultarAcuerdo(referencia,SesionActual.actor());
    }
}
