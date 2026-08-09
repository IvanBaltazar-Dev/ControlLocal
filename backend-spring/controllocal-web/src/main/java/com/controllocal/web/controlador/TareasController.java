package com.controllocal.web.controlador;

import com.controllocal.service.TareaService;
import com.controllocal.web.dto.TareaResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrato CONGELADO del TareasRest Jakarta: la bandeja "Acciones Pendientes".
 *
 * <p><b>Es el unico recurso del sistema sin acceso de ADMIN</b>, y es
 * coherente: la bandeja no es un tablero de control, es la lista de cosas que
 * un agente tiene que hacer. Ni el broker ni el admin entran.
 *
 * <p>Tres formas del cable que se pierden al portar:
 * <ul>
 *   <li>{@code GET /tareas} devuelve una <b>lista pelada</b>, sin sobre de
 *       paginacion, cortada en 10;</li>
 *   <li>{@code GET /tareas/pendientes} si lleva sobre, y su {@code tamano} por
 *       defecto es <b>5</b>, no 10;</li>
 *   <li>cancelar responde <b>204 sin cuerpo</b>.</li>
 * </ul>
 *
 * <p>Y la mas importante: <b>las dos lecturas reconcilian</b>, o sea escriben.
 * Es la unica forma de que la bandeja este al dia sin planificador (D-F7-3).
 */
@RestController
@RequestMapping("tareas")
public class TareasController {

    private final TareaService tareas;

    public TareasController(TareaService tareas) {
        this.tareas = tareas;
    }

    @GetMapping
    @PreAuthorize("hasRole('AGENTE')")
    public List<TareaResponse> bandeja() {
        return tareas.bandejaDe(SesionActual.actor()).stream()
                .map(TareaResponse::desde)
                .toList();
    }

    /**
     * La misma bandeja con sobre de paginacion. Ojo: la fuente ya viene
     * cortada en 10 por el service, asi que esto pagina sobre ese tope — es lo
     * que hace la v1.
     */
    @GetMapping("pendientes")
    @PreAuthorize("hasRole('AGENTE')")
    public PageResponse<TareaResponse> pendientes(@RequestParam(defaultValue = "1") int pagina,
                                                  @RequestParam(defaultValue = "5") int tamano) {
        List<TareaResponse> fuente = tareas.bandejaDe(SesionActual.actor()).stream()
                .map(TareaResponse::desde)
                .toList();
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        return new PageResponse<>(fuente.subList(desde, hasta), fuente.size(),
                paginaValida, tamanoValido);
    }

    @PostMapping("{id}/cancelar")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<Void> cancelar(@PathVariable long id) {
        tareas.cancelar(id, SesionActual.actor());
        return ResponseEntity.noContent().build();
    }
}
