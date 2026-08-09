package com.controllocal.web.controlador;

import com.controllocal.service.CaptacionService;
import com.controllocal.web.dto.ReasignacionCaptacionResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrato CONGELADO del ReasignacionesCaptacionRest Jakarta: historial de
 * reasignaciones para gobierno del broker/administrador, el mas reciente
 * primero. La ruta literal gana a captaciones/{id} por especificidad.
 */
@RestController
@RequestMapping("captaciones/reasignaciones")
public class ReasignacionesCaptacionController {

    private final CaptacionService captaciones;

    public ReasignacionesCaptacionController(CaptacionService captaciones) {
        this.captaciones = captaciones;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public List<ReasignacionCaptacionResponse> listar() {
        return captaciones.listarReasignaciones(SesionActual.actor()).stream()
                .map(ReasignacionCaptacionResponse::desde)
                .toList();
    }
}
