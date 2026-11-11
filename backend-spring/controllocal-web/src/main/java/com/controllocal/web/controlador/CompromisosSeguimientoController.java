package com.controllocal.web.controlador;

import com.controllocal.service.actividad.CompromisosDeSeguimiento;
import com.controllocal.web.seguridad.*;
import com.controllocal.web.capacidades.CapacidadReconocida;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/** CAT20.8: constancias del compromiso; no registra interacciones ni modifica estados comerciales. */
@RestController
@RequestMapping("prospecciones/{id}/compromisos")
public class CompromisosSeguimientoController {
    private final CompromisosDeSeguimiento servicio;
    private final ProcedenciaDeCabeceras procedencias;
    public CompromisosSeguimientoController(CompromisosDeSeguimiento s,ProcedenciaDeCabeceras p){servicio=s;procedencias=p;}
    @GetMapping @PreAuthorize("hasAnyRole('AGENTE','BROKER')")
    @CapacidadReconocida("consultar_compromisos_prospeccion")
    public List<CompromisosDeSeguimiento.Vista> listar(@PathVariable long id){return servicio.listar(id,SesionActual.actor());}
    @PostMapping("seguimientos/{seguimiento}") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("asumir_compromiso_prospeccion")
    public CompromisosDeSeguimiento.Vista planificar(@PathVariable long id,@PathVariable long seguimiento,
            @RequestBody CompromisosDeSeguimiento.Plan datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){
        return servicio.planificar(id,seguimiento,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));
    }
    @PostMapping("{referencia}/actuacion") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("acreditar_compromiso_prospeccion")
    public CompromisosDeSeguimiento.Vista acreditar(@PathVariable long id,@PathVariable UUID referencia,
            @RequestBody CompromisosDeSeguimiento.Acreditacion datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){
        return servicio.acreditar(id,referencia,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));
    }
    @PostMapping("{referencia}/resolucion") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("resolver_compromiso_prospeccion")
    public CompromisosDeSeguimiento.Vista resolver(@PathVariable long id,@PathVariable UUID referencia,
            @RequestBody CompromisosDeSeguimiento.Resolucion datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){
        return servicio.resolver(id,referencia,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));
    }
}
