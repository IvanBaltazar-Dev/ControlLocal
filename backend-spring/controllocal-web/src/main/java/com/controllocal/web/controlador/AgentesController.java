package com.controllocal.web.controlador;

import com.controllocal.service.AgenteService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.AgenteFichaResponse;
import com.controllocal.web.dto.AgenteRequest;
import com.controllocal.web.dto.AgenteResponse;
import com.controllocal.web.dto.AgentesResumenResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Directorio y edición local; el ingreso se formaliza mediante invitación y aceptación. */
@RestController
@RequestMapping("agentes")
@PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
public class AgentesController {

    private final AgenteService agentes;

    public AgentesController(AgenteService agentes) {
        this.agentes = agentes;
    }

    /**
     * Listado con filtros <b>aditivos</b>: omitidos los cuatro, la respuesta es
     * exactamente la del cable congelado, incluido el orden por id descendente.
     */
    @GetMapping
    public PageResponse<AgenteResponse> listar(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "50") int tamano,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String estadoOperativo,
            @RequestParam(required = false) String zona) {
        Pagina<AgenteService.FichaAgente> resultado = agentes.listar(
                new AgenteService.FiltrosAgente(texto, estado, estadoOperativo, zona,
                        pagina, tamano),
                SesionActual.actor());
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        return new PageResponse<>(
                resultado.items().stream().map(AgenteResponse::desde).toList(),
                resultado.total(), paginaValida, tamanoValido);
    }

    /**
     * Cubos del catálogo y zonas disponibles. No acepta {@code zona}: es uno de
     * los filtros que ofrece, y aceptarlo daría un selector que se vacía solo.
     */
    @GetMapping("resumen")
    public AgentesResumenResponse resumen(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String estadoOperativo) {
        return AgentesResumenResponse.desde(agentes.resumen(
                new AgenteService.FiltrosAgente(texto, estado, estadoOperativo, null, 1, 1),
                SesionActual.actor()));
    }

    /**
     * Ficha individual: identidad, supervisión vigente, actividad por estado y
     * el dinero real de sus cierres, atribuidos por V27. El BROKER solo alcanza
     * a los agentes que supervisa.
     */
    @GetMapping("{id}")
    public AgenteFichaResponse ficha(@PathVariable long id) {
        return AgenteFichaResponse.desde(agentes.ficha(id, SesionActual.actor()));
    }

    @GetMapping("{id}/perfil")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public AgenteResponse perfilParaGobierno(@PathVariable long id) {
        return AgenteResponse.desde(agentes.consultarParaGobierno(id,SesionActual.actor()));
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public AgenteResponse actualizar(@PathVariable long id,
                                     @RequestBody(required = false) AgenteRequest dto) {
        return AgenteResponse.desde(
                agentes.actualizar(id, dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
    }
}
