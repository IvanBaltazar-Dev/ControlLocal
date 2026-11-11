package com.controllocal.web.controlador;

import com.controllocal.service.SeguridadService;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Proyección mínima de gobierno; no modifica credenciales o factores de otra cuenta. */
@RestController
@RequestMapping("accesos")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AccesosController {
    private final SeguridadService seguridad;
    public AccesosController(SeguridadService seguridad) { this.seguridad=seguridad; }
    @GetMapping
    public List<SeguridadService.CuentaDeGobierno> cuentas() { return seguridad.cuentas(SesionActual.actor()); }
}
