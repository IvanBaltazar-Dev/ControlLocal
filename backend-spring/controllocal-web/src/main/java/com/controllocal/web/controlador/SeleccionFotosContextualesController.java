package com.controllocal.web.controlador;

import com.controllocal.service.documentos.SeleccionFotosContextualesService;
import com.controllocal.service.documentos.SeleccionFotosContextualesService.Observacion;
import com.controllocal.service.documentos.SeleccionFotosContextualesService.Seleccion;
import com.controllocal.service.documentos.SeleccionFotosContextualesService.Solicitud;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AC-121: misma selección contextual Core para Web y KAIROS. */
@RestController
@RequestMapping("propiedades/{id}/fotos/seleccion")
public class SeleccionFotosContextualesController {
    private final SeleccionFotosContextualesService fotos;

    public SeleccionFotosContextualesController(SeleccionFotosContextualesService fotos) {
        this.fotos = fotos;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENTE','BROKER')")
    @CapacidadReconocida("consultar_fotos_contextuales")
    public Observacion observar(@PathVariable long id) {
        return fotos.observar(id, SesionActual.actor());
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("seleccionar_fotos_contextuales")
    public Seleccion seleccionar(@PathVariable long id, @RequestBody(required = false) Solicitud datos) {
        return fotos.seleccionar(id, datos, SesionActual.actor());
    }
}
