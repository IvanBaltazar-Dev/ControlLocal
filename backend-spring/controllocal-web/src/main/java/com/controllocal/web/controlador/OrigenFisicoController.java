package com.controllocal.web.controlador;

import com.controllocal.service.UnidadRelacionadaService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

/** RF014: lectura contextual o propuesta; la única confirmación humana pertenece a Actuaciones. */
@RestController
@RequestMapping("origen-fisico")
public class OrigenFisicoController {
    private final UnidadRelacionadaService origen;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public OrigenFisicoController(UnidadRelacionadaService origen,ActuacionesService actuaciones,
                                  ProcedenciaDeCabeceras procedencias) {
        this.origen=origen;this.actuaciones=actuaciones;this.procedencias=procedencias;
    }
    @GetMapping("{idPropiedad}")
    @PreAuthorize("hasAnyRole('AGENTE','BROKER')")
    @CapacidadReconocida("consultar_origen_fisico_unidad")
    public List<UnidadRelacionadaService.FichaOrigen> consultar(@PathVariable long idPropiedad,
            @RequestParam(required=false) Instant corte) {
        return origen.consultarOrigen(idPropiedad,corte,SesionActual.actor());
    }
    @PostMapping("propuestas")
    @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("registrar_origen_fisico_unidad")
    public ActuacionesService.Propuesta preparar(@RequestBody UnidadRelacionadaService.DatosOrigen datos,
            HttpServletRequest request) {
        return actuaciones.prepararOrigen(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(request));
    }
}
