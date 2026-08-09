package com.controllocal.web.controlador;

import com.controllocal.service.AsignacionService;
import com.controllocal.web.dto.AsignacionAgenteResponse;
import com.controllocal.web.dto.AsignacionBrokerResponse;
import com.controllocal.web.dto.BrokerAgenteResponse;
import com.controllocal.web.dto.ReasignarAgenteRequest;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contrato congelado de {@code AsignacionesRest}. */
@RestController
@RequestMapping("asignaciones")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AsignacionesController {

    private final AsignacionService asignaciones;

    public AsignacionesController(AsignacionService asignaciones) {
        this.asignaciones = asignaciones;
    }

    @GetMapping("agentes")
    public List<AsignacionAgenteResponse> agentes() {
        return asignaciones.agentes(SesionActual.actor()).stream()
                .map(AsignacionAgenteResponse::desde)
                .toList();
    }

    @GetMapping("brokers")
    public List<AsignacionBrokerResponse> brokers() {
        return asignaciones.brokers(SesionActual.actor()).stream()
                .map(AsignacionBrokerResponse::desde)
                .toList();
    }

    @GetMapping("historial")
    public List<BrokerAgenteResponse> historial() {
        return asignaciones.historial(SesionActual.actor()).stream()
                .map(BrokerAgenteResponse::desde)
                .toList();
    }

    @PostMapping("reasignar")
    public BrokerAgenteResponse reasignar(
            @RequestBody(required = false) ReasignarAgenteRequest dto) {
        return BrokerAgenteResponse.desde(
                asignaciones.reasignar(
                        dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
    }
}
