package com.controllocal.web.controlador;

import com.controllocal.service.RequerimientoService;
import com.controllocal.web.dto.EstadoRequerimientoRequest;
import com.controllocal.web.dto.RequerimientoRequest;
import com.controllocal.web.dto.RequerimientoResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrato CONGELADO del RequerimientosRest Jakarta. Crear un requerimiento
 * NUNCA crea oportunidades: solo activa al cliente para el matching de cartera.
 * Listar es de sesion (los clientes son catalogo compartido); escribir es del
 * AGENTE.
 */
@RestController
@RequestMapping("requerimientos")
public class RequerimientosController {

    private final RequerimientoService requerimientos;

    public RequerimientosController(RequerimientoService requerimientos) {
        this.requerimientos = requerimientos;
    }

    @GetMapping("cliente/{idCliente}")
    public List<RequerimientoResponse> listarPorCliente(@PathVariable long idCliente) {
        return requerimientos.listarPorCliente(idCliente, SesionActual.actor()).stream()
                .map(RequerimientoResponse::desde)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<RequerimientoResponse> crear(
            @RequestBody(required = false) RequerimientoRequest dto) {
        RequerimientoResponse creado = RequerimientoResponse.desde(
                requerimientos.crear(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public RequerimientoResponse actualizar(@PathVariable long id,
                                            @RequestBody(required = false) RequerimientoRequest dto) {
        return RequerimientoResponse.desde(
                requerimientos.actualizar(id, dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    @PostMapping("{id}/estado")
    @PreAuthorize("hasRole('AGENTE')")
    public RequerimientoResponse cambiarEstado(@PathVariable long id,
                                               @RequestBody(required = false) EstadoRequerimientoRequest dto) {
        return RequerimientoResponse.desde(
                requerimientos.cambiarEstado(id, dto == null ? null : dto.estado(), SesionActual.actor()));
    }
}
