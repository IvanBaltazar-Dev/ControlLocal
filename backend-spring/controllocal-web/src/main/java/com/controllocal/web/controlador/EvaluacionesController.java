package com.controllocal.web.controlador;

import com.controllocal.service.EvaluacionService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.EvaluacionRequest;
import com.controllocal.web.dto.EvaluacionResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato CONGELADO del EvaluacionRest Jakarta: la decision del broker sobre
 * la solicitud. Recurso de BROKER/ADMIN — el agente no entra (403, §7); el
 * broker ve solo las que firmo y el historial por solicitud vive en
 * {@code GET /solicitudes/{id}/evaluaciones}, que si alcanza el agente dueno.
 *
 * <p>Todo lo que sorprende de esta operacion esta en el service: el tipo se
 * DERIVA del resultado (pero el cable lo exige presente y valido), solo cabe
 * una evaluacion FINAL por solicitud, y la evaluacion MUEVE la solicitud en la
 * misma transaccion.
 *
 * <p>La paginacion de {@code GET} bajo a SQL (MEJ-05 / RC-003): la v1 traia
 * {@code listarTodos()} y cortaba con {@code subList}, y {@code GET {id}}
 * filtraba la lista completa. Misma respuesta, sin cargar la tabla.
 */
@RestController
@RequestMapping("evaluaciones")
public class EvaluacionesController {

    private final EvaluacionService evaluaciones;

    public EvaluacionesController(EvaluacionService evaluaciones) {
        this.evaluaciones = evaluaciones;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public PageResponse<EvaluacionResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                   @RequestParam(defaultValue = "10") int tamano) {
        return pagina(evaluaciones.listar(pagina, tamano, SesionActual.actor()), pagina, tamano);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public EvaluacionResponse obtener(@PathVariable long id) {
        return EvaluacionResponse.desde(evaluaciones.obtener(id, SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('BROKER')")
    public ResponseEntity<EvaluacionResponse> registrar(
            @RequestBody(required = false) EvaluacionRequest dto) {
        EvaluacionResponse creada = EvaluacionResponse.desde(
                evaluaciones.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    private static PageResponse<EvaluacionResponse> pagina(
            Pagina<EvaluacionService.FichaEvaluacion> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(EvaluacionResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
