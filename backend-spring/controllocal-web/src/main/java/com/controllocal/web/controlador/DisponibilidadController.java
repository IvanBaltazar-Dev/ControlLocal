package com.controllocal.web.controlador;

import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** CAT-E28/D2: el responsable aporta el hecho específico, el broker sólo lo consulta. */
@RestController
@RequestMapping("disponibilidad")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class DisponibilidadController {
    private final DisponibilidadContextualService disponibilidad;
    private final ProcedenciaDeCabeceras procedencias;
    private final com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad solicitudes;
    public DisponibilidadController(DisponibilidadContextualService disponibilidad,ProcedenciaDeCabeceras procedencias,
            com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad solicitudes) {
        this.disponibilidad=disponibilidad;this.procedencias=procedencias;this.solicitudes=solicitudes;
    }
    @GetMapping("solicitudes") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("consultar_solicitudes_disponibilidad")
    public java.util.List<com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad.Vista> solicitudes(
            @ModelAttribute com.controllocal.service.soporte.PoliticaDeDespacho.Consulta consulta){
        return solicitudes.listar(consulta,SesionActual.actor());
    }
    public record Inicial(DisponibilidadContextualService.TipoRecurso tipo,long recurso,long version) { }
    @PostMapping("solicitudes") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("solicitar_confirmacion_inicial")
    public Object inicial(@RequestBody Inicial d,HttpServletRequest r){
        return solicitudes.solicitarInicial(d.tipo(),d.recurso(),d.version(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),r.getHeader("Idempotency-Key"),procedencias.de(r));
    }
    @PostMapping("solicitudes/espera") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("responder_solicitud_disponibilidad")
    public Object espera(@RequestBody com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad.Espera d,HttpServletRequest r){
        return solicitudes.registrarEspera(d,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),r.getHeader("Idempotency-Key"),procedencias.de(r));
    }
    @GetMapping("{tipo}/{id}") @CapacidadReconocida("consultar_disponibilidad")
    public DisponibilidadContextualService.Vista consultar(@PathVariable DisponibilidadContextualService.TipoRecurso tipo,@PathVariable long id) {
        return disponibilidad.consultar(tipo,id,SesionActual.actor());
    }
    @PostMapping("confirmaciones") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("confirmar_disponibilidad")
    public DisponibilidadContextualService.ResultadoConfirmacion confirmar(@RequestBody DisponibilidadContextualService.DatosConfirmacion datos,HttpServletRequest r) {
        return disponibilidad.confirmar(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),
                r.getHeader("Idempotency-Key"),procedencias.de(r));
    }
}
