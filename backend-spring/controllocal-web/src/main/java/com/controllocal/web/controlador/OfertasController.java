package com.controllocal.web.controlador;

import com.controllocal.service.OfertaService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.dto.OfertaDtos.OfertaResponse;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** C4: cada escritura prepara una actuación exacta; Core confirma su efecto. */
@RestController
@RequestMapping("oportunidades/{id}")
public class OfertasController {
    private final OfertaService ofertas;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public OfertasController(OfertaService ofertas, ActuacionesService actuaciones, ProcedenciaDeCabeceras procedencias) {
        this.ofertas=ofertas; this.actuaciones=actuaciones; this.procedencias=procedencias;
    }
    @GetMapping("ofertas")
    public List<OfertaResponse> listar(@PathVariable long id) {
        return ofertas.listar(id, SesionActual.actor()).stream().map(OfertaResponse::desde).toList();
    }
    @PostMapping("ofertas/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("presentar_oferta")
    public ActuacionesService.Propuesta presentar(@PathVariable long id,
            @RequestBody OfertaService.DatosRegistroOferta datos,HttpServletRequest r) {
        mismoRecurso(id,datos.oportunidad());
        return actuaciones.prepararRegistroOferta(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("contraofertas/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_contraoferta")
    public ActuacionesService.Propuesta contraofertar(@PathVariable long id,
            @RequestBody OfertaService.DatosContraoferta datos,HttpServletRequest r) {
        mismoRecurso(id,datos.oportunidad());
        return actuaciones.prepararContraoferta(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("aceptaciones/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_aceptacion_oferta")
    public ActuacionesService.Propuesta aceptar(@PathVariable long id,
            @RequestBody OfertaService.DatosAceptacion datos,HttpServletRequest r) {
        mismoRecurso(id,datos.oportunidad());
        return actuaciones.prepararAceptacionOferta(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("rechazos/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_rechazo_oferta")
    public ActuacionesService.Propuesta rechazar(@PathVariable long id,
            @RequestBody OfertaService.DatosRechazo datos,HttpServletRequest r) {
        mismoRecurso(id,datos.oportunidad());
        return actuaciones.prepararRechazoOferta(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("retiradas/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_retirada_oferta")
    public ActuacionesService.Propuesta retirar(@PathVariable long id,
            @RequestBody OfertaService.DatosRetirada datos,HttpServletRequest r) {
        mismoRecurso(id,datos.oportunidad());
        return actuaciones.prepararRetiradaOferta(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    private static void mismoRecurso(long ruta,Long cuerpo) {
        if(cuerpo==null||ruta!=cuerpo)throw new ReglaNegocioException("El recurso de la actuación debe coincidir con la ruta.");
    }
}
