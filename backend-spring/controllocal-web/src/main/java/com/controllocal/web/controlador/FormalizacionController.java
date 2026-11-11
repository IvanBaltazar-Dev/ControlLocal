package com.controllocal.web.controlador;

import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.formalizacion.FormalizacionService;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Transporte RF051/053/058–060; todo efecto reservado termina en ActuacionesController. */
@RestController @RequestMapping("formalizacion")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class FormalizacionController {
    private final FormalizacionService formalizacion;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public FormalizacionController(FormalizacionService formalizacion,ActuacionesService actuaciones,
            ProcedenciaDeCabeceras procedencias) {
        this.formalizacion=formalizacion;this.actuaciones=actuaciones;this.procedencias=procedencias;
    }
    @GetMapping("expedientes/{id}") @CapacidadReconocida("consultar_expediente_operacion")
    public FormalizacionService.Expediente consultar(@PathVariable long id) {
        return formalizacion.consultar(id,SesionActual.actor());
    }
    @PostMapping("expedientes/propuestas") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("presentar_expediente_operacion")
    public ActuacionesService.Propuesta presentar(@RequestBody FormalizacionService.DatosPresentacion datos,HttpServletRequest r) {
        return actuaciones.prepararPresentacionExpediente(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("evaluaciones/propuestas") @CapacidadReconocida("evaluar_expediente_operacion")
    public ActuacionesService.Propuesta evaluar(@RequestBody FormalizacionService.DatosRevision datos,HttpServletRequest r) {
        return actuaciones.prepararEvaluacionExpediente(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("hitos/informes/propuestas") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("informar_hito_contractual")
    public ActuacionesService.Propuesta informar(@RequestBody FormalizacionService.DatosInformacionHito datos,HttpServletRequest r) {
        return actuaciones.prepararInformacionHito(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("hitos/confirmaciones/propuestas") @CapacidadReconocida("confirmar_hito_contractual")
    public ActuacionesService.Propuesta confirmar(@RequestBody FormalizacionService.DatosConfirmacionHito datos,HttpServletRequest r) {
        return actuaciones.prepararConfirmacionHito(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("hitos/rectificaciones/propuestas") @CapacidadReconocida("rectificar_hito_contractual")
    public ActuacionesService.Propuesta rectificar(@RequestBody FormalizacionService.DatosRectificacionHito datos,HttpServletRequest r) {
        return actuaciones.prepararRectificacionHito(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
}
