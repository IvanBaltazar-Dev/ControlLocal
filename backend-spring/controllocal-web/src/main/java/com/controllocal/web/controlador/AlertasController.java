package com.controllocal.web.controlador;

import com.controllocal.service.AlertaService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.AlertaResponse;
import com.controllocal.web.dto.AtenderAlertaResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Avisos privados: Core reconcilia por actor y causa en la misma TX; un fallo no se presenta como vacío. */
@RestController
@RequestMapping("alertas")
public class AlertasController {

    private final AlertaService alertas;

    public AlertasController(AlertaService alertas) {
        this.alertas = alertas;
    }

    @GetMapping
    public PageResponse<AlertaResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                               @RequestParam(defaultValue = "20") int tamano) {
        return pagina(alertas.listar(pagina, tamano, SesionActual.actor()), pagina, tamano);
    }

    @PostMapping("{id}/atender")
    public AtenderAlertaResponse atenderPorPost(@PathVariable long id) {
        return atender(id);
    }

    /** Identico al POST: los dos verbos existen en el cable. */
    @PatchMapping("{id}/atender")
    public AtenderAlertaResponse atenderPorPatch(@PathVariable long id) {
        return atender(id);
    }

    private AtenderAlertaResponse atender(long id) {
        return new AtenderAlertaResponse(alertas.atender(id, SesionActual.actor()));
    }

    private static PageResponse<AlertaResponse> pagina(
            Pagina<AlertaService.FichaAlerta> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(AlertaResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
