package com.controllocal.web.controlador;

import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.Pagina;
import com.controllocal.service.ProspeccionService;
import com.controllocal.service.ProspeccionService.FiltrosProspeccion;
import com.controllocal.web.dto.CaptarProspeccionRequest;
import com.controllocal.web.dto.CoincidenciasResponse;
import com.controllocal.web.dto.MarcarProspeccionCaptadaRequest;
import com.controllocal.web.dto.ProspeccionRequest;
import com.controllocal.web.dto.ProspeccionResponse;
import com.controllocal.web.dto.RechazoProspeccionRequest;
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
 * Contrato CONGELADO del ProspeccionesRest Jakarta: mismas rutas, formas y
 * mensajes. El alcance por rol y las guardas de la maquina de estados viven
 * en el service; aqui solo el gate por rol (AGENTE escribe/opera) y el
 * mapeo DTO. {@code {id}/coincidencias} llego con F3 (matching de cartera).
 */
@RestController
@RequestMapping("prospecciones")
public class ProspeccionesController {

    private final ProspeccionService prospecciones;
    private final CoincidenciaService coincidencias;

    public ProspeccionesController(ProspeccionService prospecciones, CoincidenciaService coincidencias) {
        this.prospecciones = prospecciones;
        this.coincidencias = coincidencias;
    }

    @GetMapping
    public PageResponse<ProspeccionResponse> listar(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String distrito,
            @RequestParam(required = false) Long idCaptacion,
            @RequestParam(required = false) Long idLocal,
            @RequestParam(required = false) Long idAgente,
            @RequestParam(required = false) Long idBrokerSupervisor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String orden) {
        FiltrosProspeccion filtros = new FiltrosProspeccion(estado, distrito, idCaptacion, idLocal,
                idAgente, idBrokerSupervisor, q, orden, pagina, tamano);
        return pagina(prospecciones.listar(filtros, SesionActual.actor()), pagina, tamano);
    }

    @GetMapping("recontactar")
    public PageResponse<ProspeccionResponse> recontactar(
            @RequestParam(defaultValue = "7") int dias,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "10") int tamano) {
        return pagina(prospecciones.recontactar(dias, pagina, tamano, SesionActual.actor()), pagina, tamano);
    }

    @GetMapping("{id}")
    public ProspeccionResponse obtener(@PathVariable long id) {
        return ProspeccionResponse.desde(prospecciones.obtener(id, SesionActual.actor()));
    }

    /**
     * Prospeccion -> clientes compatibles: senal de demanda TEMPRANA, antes de
     * que exista captacion. Solo es accionable ("Proponer") si la prospeccion
     * ya tiene captacion; si no, la ruta viaja vacia.
     */
    @GetMapping("{id}/coincidencias")
    public CoincidenciasResponse coincidencias(@PathVariable long id,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pagina,
                                               @RequestParam(name = "page_size", required = false) Integer pageSize,
                                               @RequestParam(required = false) Integer tamano) {
        return CoincidenciasResponse.desde(coincidencias.clientesParaProspeccion(id,
                ClientesController.paginaSolicitada(page, pagina),
                ClientesController.tamanoSolicitado(pageSize, tamano), SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<ProspeccionResponse> registrar(@RequestBody(required = false) ProspeccionRequest dto) {
        ProspeccionResponse creada = ProspeccionResponse.desde(
                prospecciones.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PostMapping("{id}/contactar")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse contactar(@PathVariable long id) {
        return ProspeccionResponse.desde(prospecciones.contactar(id, SesionActual.actor()));
    }

    @PostMapping("{id}/reunion")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse registrarReunion(@PathVariable long id) {
        return ProspeccionResponse.desde(prospecciones.registrarReunion(id, SesionActual.actor()));
    }

    @PostMapping("{id}/propuesta")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse entregarPropuesta(@PathVariable long id) {
        return ProspeccionResponse.desde(prospecciones.entregarPropuesta(id, SesionActual.actor()));
    }

    @PostMapping("{id}/seguimiento")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse registrarSeguimiento(@PathVariable long id) {
        return ProspeccionResponse.desde(prospecciones.registrarSeguimiento(id, SesionActual.actor()));
    }

    @PostMapping("{id}/rechazar")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse rechazar(@PathVariable long id,
                                        @RequestBody(required = false) RechazoProspeccionRequest dto) {
        return ProspeccionResponse.desde(
                prospecciones.rechazar(id, dto != null ? dto.motivo() : null, SesionActual.actor()));
    }

    @PostMapping("{id}/descartar")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse descartar(@PathVariable long id,
                                         @RequestBody(required = false) RechazoProspeccionRequest dto) {
        return ProspeccionResponse.desde(
                prospecciones.descartar(id, dto != null ? dto.motivo() : null, SesionActual.actor()));
    }

    /**
     * <b>Aqui nace el Encargo</b> (V75). El cuerpo es obligatorio desde que la
     * operacion tiene que declararse: con {@code required = false} un POST sin
     * cuerpo dejaba llegar todo nulo por el mismo hueco por el que antes se
     * colaba el ALQUILER por defecto.
     */
    @PostMapping("{id}/captar")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse captar(@PathVariable long id,
                                      @RequestBody CaptarProspeccionRequest dto) {
        return ProspeccionResponse.desde(
                prospecciones.captar(id, dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    @PostMapping("{id}/marcar-captado")
    @PreAuthorize("hasRole('AGENTE')")
    public ProspeccionResponse marcarCaptado(@PathVariable long id,
                                             @RequestBody(required = false) MarcarProspeccionCaptadaRequest dto) {
        Long idCaptacion = dto != null ? dto.idCaptacion() : null;
        String codigo = dto != null ? dto.codigoCaptacion() : null;
        return ProspeccionResponse.desde(prospecciones.marcarCaptado(id, idCaptacion, codigo, SesionActual.actor()));
    }

    private static PageResponse<ProspeccionResponse> pagina(Pagina<ProspeccionService.FichaProspeccion> pagina,
                                                            int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(ProspeccionResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
