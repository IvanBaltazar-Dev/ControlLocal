package com.controllocal.web.controlador;

import com.controllocal.service.BrokerService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.AgenteResponse;
import com.controllocal.web.dto.BrokerRequest;
import com.controllocal.web.dto.BrokerResponse;
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

import java.util.List;

/** Directorio de brokers; las credenciales pertenecen exclusivamente a su titular. */
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

    @PutMapping("{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public BrokerResponse actualizar(@PathVariable long id,
                                     @RequestBody(required = false) BrokerRequest dto) {
        return BrokerResponse.desde(
                brokers.actualizar(id, dto == null ? null : dto.aDatos(),
                        SesionActual.actor()));
    }
}
