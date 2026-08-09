package com.controllocal.web.controlador;

import com.controllocal.service.Pagina;
import com.controllocal.service.VisitaService;
import com.controllocal.service.VisitaService.FiltrosVisita;
import com.controllocal.web.dto.CancelarVisitaRequest;
import com.controllocal.web.dto.NoRealizadaVisitaRequest;
import com.controllocal.web.dto.ReprogramarVisitaRequest;
import com.controllocal.web.dto.ResultadoVisitaRequest;
import com.controllocal.web.dto.ResumenVisitasResponse;
import com.controllocal.web.dto.VisitaRequest;
import com.controllocal.web.dto.VisitaResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrato CONGELADO del VisitasRest Jakarta, el unico recurso de la migracion
 * que usa PATCH: cada cambio de agenda es una operacion propia
 * (reprogramar, cancelar, realizar, no-realizada, resultado). Las rutas
 * literales (proximas, mes) ganan a {id} por especificidad.
 */
@RestController
@RequestMapping("visitas")
public class VisitasController {

    private final VisitaService visitas;

    public VisitasController(VisitaService visitas) {
        this.visitas = visitas;
    }

    @GetMapping
    public PageResponse<VisitaResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                               @RequestParam(defaultValue = "10") int tamano,
                                               @RequestParam(required = false) Long idOportunidad,
                                               @RequestParam(required = false) String estado,
                                               @RequestParam(required = false) String distrito,
                                               @RequestParam(required = false) String query) {
        FiltrosVisita filtros = new FiltrosVisita(idOportunidad, estado, distrito, query, pagina, tamano);
        return pagina(visitas.listar(filtros, SesionActual.actor()), pagina, tamano);
    }

    /**
     * Extension aditiva (no existe en la v1): los cinco contadores de la
     * bandeja y los distritos disponibles, calculados en la BASE sobre el mismo
     * conjunto que pagina la lista. No acepta {@code estado} ni {@code
     * distrito}: son justo los filtros que este resumen acota.
     */
    @GetMapping("resumen")
    public ResumenVisitasResponse resumen(@RequestParam(required = false) Long idOportunidad,
                                          @RequestParam(required = false) String query) {
        return ResumenVisitasResponse.desde(visitas.resumen(
                new FiltrosVisita(idOportunidad, null, null, query, 1, 1), SesionActual.actor()));
    }

    /** Agenda: total = items devueltos y page = 1 (el cable no pagina esta bandeja). */
    @GetMapping("proximas")
    public PageResponse<VisitaResponse> proximas(@RequestParam(defaultValue = "8") int tamano) {
        Pagina<VisitaService.FichaVisita> agenda = visitas.proximas(tamano, SesionActual.actor());
        int tamanoValido = Math.min(Math.max(1, Math.min(100, tamano)), 8);
        return new PageResponse<>(items(agenda), agenda.total(), 1, tamanoValido);
    }

    /** Calendario del mes: sin paginar, pageSize = cuantas hay (minimo 1). */
    @GetMapping("mes")
    public PageResponse<VisitaResponse> mes(@RequestParam int anio, @RequestParam int mes) {
        Pagina<VisitaService.FichaVisita> calendario = visitas.mes(anio, mes, SesionActual.actor());
        List<VisitaResponse> items = items(calendario);
        return new PageResponse<>(items, items.size(), 1, Math.max(items.size(), 1));
    }

    @GetMapping("{id}")
    public VisitaResponse obtener(@PathVariable long id) {
        return VisitaResponse.desde(visitas.obtener(id, SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<VisitaResponse> programar(@RequestBody(required = false) VisitaRequest dto) {
        VisitaResponse creada = VisitaResponse.desde(
                visitas.programar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PatchMapping("{id}/reprogramar")
    @PreAuthorize("hasRole('AGENTE')")
    public VisitaResponse reprogramar(@PathVariable long id,
                                      @RequestBody(required = false) ReprogramarVisitaRequest dto) {
        if (dto == null) {
            throw new IllegalArgumentException("La nueva fecha y hora son obligatorias.");
        }
        return VisitaResponse.desde(visitas.reprogramar(id, dto.fechaVisita(), dto.horaVisita(),
                SesionActual.actor()));
    }

    @PatchMapping("{id}/cancelar")
    @PreAuthorize("hasRole('AGENTE')")
    public VisitaResponse cancelar(@PathVariable long id,
                                   @RequestBody(required = false) CancelarVisitaRequest dto) {
        return VisitaResponse.desde(
                visitas.cancelar(id, dto != null ? dto.motivo() : null, SesionActual.actor()));
    }

    @PatchMapping("{id}/realizar")
    @PreAuthorize("hasRole('AGENTE')")
    public VisitaResponse marcarRealizada(@PathVariable long id) {
        return VisitaResponse.desde(visitas.marcarRealizada(id, SesionActual.actor()));
    }

    @PatchMapping("{id}/no-realizada")
    @PreAuthorize("hasRole('AGENTE')")
    public VisitaResponse marcarNoRealizada(@PathVariable long id,
                                            @RequestBody(required = false) NoRealizadaVisitaRequest dto) {
        return VisitaResponse.desde(
                visitas.marcarNoRealizada(id, dto != null ? dto.motivo() : null, SesionActual.actor()));
    }

    @PatchMapping("{id}/resultado")
    @PreAuthorize("hasRole('AGENTE')")
    public VisitaResponse registrarResultado(@PathVariable long id,
                                             @RequestBody(required = false) ResultadoVisitaRequest dto) {
        return VisitaResponse.desde(visitas.registrarResultado(id,
                dto == null ? null : dto.aDesenlace(), SesionActual.actor()));
    }

    private static List<VisitaResponse> items(Pagina<VisitaService.FichaVisita> pagina) {
        return pagina.items().stream().map(VisitaResponse::desde).toList();
    }

    private static PageResponse<VisitaResponse> pagina(Pagina<VisitaService.FichaVisita> pagina,
                                                       int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(items(pagina), pagina.total(), paginaValida, tamanoValido);
    }
}
