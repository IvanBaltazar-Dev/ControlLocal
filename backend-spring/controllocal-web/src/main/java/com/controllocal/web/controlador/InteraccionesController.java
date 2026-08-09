package com.controllocal.web.controlador;

import com.controllocal.service.InteraccionService;
import com.controllocal.service.InteraccionService.FiltrosInteraccion;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.InteraccionRequest;
import com.controllocal.web.dto.InteraccionResponse;
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

/**
 * Contrato CONGELADO del InteraccionesRest Jakarta: la bitacora polimorfica del
 * sistema. El contexto, la allow-list de resultado y el alcance (por AGENTE
 * responsable, no por captacion) viven en el service. Ojo con el tamano por
 * defecto: 50, no 10 — es una bitacora, no una bandeja.
 */
@RestController
@RequestMapping("interacciones")
public class InteraccionesController {

    private final InteraccionService interacciones;

    public InteraccionesController(InteraccionService interacciones) {
        this.interacciones = interacciones;
    }

    @GetMapping
    public PageResponse<InteraccionResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                    @RequestParam(defaultValue = "50") int tamano,
                                                    @RequestParam(required = false) String contexto,
                                                    @RequestParam(required = false) Long idOportunidad,
                                                    @RequestParam(required = false) Long idProspeccion,
                                                    @RequestParam(required = false) Long idCaptacion,
                                                    @RequestParam(required = false) Long idCliente,
                                                    @RequestParam(required = false) String grupo,
                                                    @RequestParam(required = false) String resultado,
                                                    @RequestParam(required = false) String canal,
                                                    @RequestParam(required = false) String q) {
        FiltrosInteraccion filtros = new FiltrosInteraccion(contexto, idOportunidad, idProspeccion,
                idCaptacion, idCliente, grupo, resultado, canal, q, pagina, tamano);
        return pagina(interacciones.listar(filtros, SesionActual.actor()), pagina, tamano);
    }

    @GetMapping("{id}")
    public InteraccionResponse obtener(@PathVariable long id) {
        return InteraccionResponse.desde(interacciones.obtener(id, SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<InteraccionResponse> registrar(
            @RequestBody(required = false) InteraccionRequest dto) {
        InteraccionResponse creada = InteraccionResponse.desde(
                interacciones.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public InteraccionResponse actualizar(@PathVariable long id,
                                          @RequestBody(required = false) InteraccionRequest dto) {
        return InteraccionResponse.desde(
                interacciones.actualizar(id, dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    private static PageResponse<InteraccionResponse> pagina(
            Pagina<InteraccionService.FichaInteraccion> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(InteraccionResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
