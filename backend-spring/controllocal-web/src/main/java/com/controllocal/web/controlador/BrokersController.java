package com.controllocal.web.controlador;

import com.controllocal.service.BrokerService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.AgenteResponse;
import com.controllocal.web.dto.BrokerRequest;
import com.controllocal.web.dto.BrokerResponse;
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

import java.util.List;

/** Contrato congelado de {@code BrokersRest}. */
@RestController
@RequestMapping("brokers")
public class BrokersController {

    private final BrokerService brokers;

    public BrokersController(BrokerService brokers) {
        this.brokers = brokers;
    }

    @GetMapping
    public PageResponse<BrokerResponse> listar(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        Pagina<BrokerService.FichaBroker> resultado =
                brokers.listar(pagina, tamano, SesionActual.actor());
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        return new PageResponse<>(
                resultado.items().stream().map(BrokerResponse::desde).toList(),
                resultado.total(), paginaValida, tamanoValido);
    }

    @GetMapping("{id}")
    public BrokerResponse obtener(@PathVariable long id) {
        return BrokerResponse.desde(brokers.obtener(id, SesionActual.actor()));
    }

    @GetMapping("{id}/agentes")
    public List<AgenteResponse> agentes(@PathVariable long id) {
        return brokers.agentes(id, SesionActual.actor()).stream()
                .map(AgenteResponse::desde)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<BrokerResponse> registrar(
            @RequestBody(required = false) BrokerRequest dto) {
        BrokerResponse creado = BrokerResponse.desde(
                brokers.registrar(dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public BrokerResponse actualizar(@PathVariable long id,
                                     @RequestBody(required = false) BrokerRequest dto) {
        return BrokerResponse.desde(
                brokers.actualizar(id, dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
    }
}
