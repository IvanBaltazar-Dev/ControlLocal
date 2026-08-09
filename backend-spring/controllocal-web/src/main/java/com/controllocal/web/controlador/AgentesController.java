package com.controllocal.web.controlador;

import com.controllocal.service.AgenteService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.AgenteFichaResponse;
import com.controllocal.web.dto.AgenteRequest;
import com.controllocal.web.dto.AgenteResponse;
import com.controllocal.web.dto.AgentesResumenResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contrato congelado de {@code AgentesRest}. */
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

    /**
     * Alta de agente: <b>gobierno del tenant</b> (D-S0-17 fila 17, D-S0-18 "un
     * broker no crea cuentas"). Exige {@code idBrokerSupervisor} en el cuerpo,
     * porque quien gobierna no supervisa a nadie de quien deducirlo.
     */
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<AgenteResponse> registrar(
            @RequestBody(required = false) AgenteRequest dto) {
        AgenteResponse creado = AgenteResponse.desde(
                agentes.registrar(dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * Edicion de agente: gobierno (fila 18). Lo editable es identidad
     * administrativa — el {@code PUT} ya descartaba en silencio documento,
     * usuario, contrasena y codigo.
     */
    @PutMapping("{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public AgenteResponse actualizar(@PathVariable long id,
                                     @RequestBody(required = false) AgenteRequest dto) {
        return AgenteResponse.desde(
                agentes.actualizar(id, dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
    }
}
