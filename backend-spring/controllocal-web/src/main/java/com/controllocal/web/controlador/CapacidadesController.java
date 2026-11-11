package com.controllocal.web.controlador;

import com.controllocal.service.capacidades.CapacidadesService;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.*;

/** Misma puerta para Web y KAIROS. El cliente no elige persona, rol ni organización. */
@RestController
@RequestMapping("capacidades")
public class CapacidadesController {
    private final CapacidadesService capacidades;
    public CapacidadesController(CapacidadesService capacidades) { this.capacidades = capacidades; }

    @GetMapping
    @CapacidadReconocida("consultar_capacidades")
    public CapacidadesService.CatalogoCapacidades consultar(
            @RequestParam(required = false) String idCore, @RequestParam(required = false) String recurso,
            @RequestParam(required = false) String referencia, @RequestParam(required = false) String operacion,
            @RequestParam(required = false) String atributo, @RequestParam(required = false) String etapa,
            @RequestParam(required = false) String referenciaRelacionada) {
        return capacidades.consultar(new CapacidadesService.Consulta(idCore, recurso, referencia,
                operacion, atributo, etapa, referenciaRelacionada), SesionActual.actor());
    }
}
