package com.controllocal.web.controlador;

import com.controllocal.service.OfertaService;
import com.controllocal.web.dto.OfertaDtos.OfertaRequest;
import com.controllocal.web.dto.OfertaDtos.OfertaResponse;
import com.controllocal.web.dto.OfertaDtos.RespuestaOfertaRequest;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La mesa de negociacion de una oportunidad (WP-A).
 *
 * <p><b>Leer</b> va con el alcance de siempre —el agente lo suyo, el bróker por
 * captacion supervisada, el gobierno del tenant su organizacion—, asi que el GET
 * no lleva gate de rol. <b>Escribir</b> es operar el encargo: lo firma el
 * AGENTE, y el Core comprueba ademas que sea el agente de ESE encargo
 * ({@code AutoridadDePropiedad.exigirEdicionDelEncargo}). La anotacion protege
 * una puerta; la regla protege el hecho, y KAIROS entra por el mismo caso de uso.
 *
 * <p>El listado de la mesa tambien viaja dentro de {@code GET /oportunidades/{id}}.
 * No son dos motores: los dos salen de {@code OfertaService.listar}. Este
 * endpoint existe para refrescar la mesa sin recargar la ficha entera.
 */
@RestController
@RequestMapping("oportunidades/{id}/ofertas")
public class OfertasController {

    private final OfertaService ofertas;
    private final ProcedenciaDeCabeceras procedencias;

    public OfertasController(OfertaService ofertas, ProcedenciaDeCabeceras procedencias) {
        this.ofertas = ofertas;
        this.procedencias = procedencias;
    }

    /** La cadena completa, en el orden en que ocurrio. */
    @GetMapping
    public List<OfertaResponse> listar(@PathVariable long id) {
        return ofertas.listar(id, SesionActual.actor()).stream()
                .map(OfertaResponse::desde)
                .toList();
    }

    /**
     * Presentar una cifra nueva. Exige que no haya ninguna viva: mientras hay
     * una sobre la mesa, la siguiente es una contraoferta.
     */
    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<OfertaResponse> presentar(@PathVariable long id,
                                                    @RequestBody(required = false) OfertaRequest dto,
                                                    HttpServletRequest peticion) {
        OfertaResponse creada = OfertaResponse.desde(ofertas.presentar(id,
                dto == null ? null : dto.aDatos(), procedencias.de(peticion),
                SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /**
     * Responder con otra cifra: la anterior queda SUPERADA y se abre la ronda
     * siguiente desde el lado contrario, que no se declara porque se deduce.
     */
    @PostMapping("{idOferta}/contraoferta")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<OfertaResponse> contraofertar(
            @PathVariable long id, @PathVariable long idOferta,
            @RequestBody(required = false) OfertaRequest dto, HttpServletRequest peticion) {
        OfertaResponse creada = OfertaResponse.desde(ofertas.contraofertar(id, idOferta,
                dto == null ? null : dto.aDatos(), procedencias.de(peticion),
                SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /** El si. Una mesa admite una sola aceptada; la segunda responde 409. */
    @PostMapping("{idOferta}/aceptar")
    @PreAuthorize("hasRole('AGENTE')")
    public OfertaResponse aceptar(@PathVariable long id, @PathVariable long idOferta,
                                  @RequestBody(required = false) RespuestaOfertaRequest dto) {
        return OfertaResponse.desde(ofertas.aceptar(id, idOferta,
                dto == null ? null : dto.motivo(), SesionActual.actor()));
    }

    /** El no a una cifra. NO cierra la oportunidad: eso es un acto aparte. */
    @PostMapping("{idOferta}/rechazar")
    @PreAuthorize("hasRole('AGENTE')")
    public OfertaResponse rechazar(@PathVariable long id, @PathVariable long idOferta,
                                   @RequestBody(required = false) RespuestaOfertaRequest dto) {
        return OfertaResponse.desde(ofertas.rechazar(id, idOferta,
                dto == null ? null : dto.motivo(), SesionActual.actor()));
    }

    /** Quien la presento se echa atras antes de que le respondan. */
    @PostMapping("{idOferta}/retirar")
    @PreAuthorize("hasRole('AGENTE')")
    public OfertaResponse retirar(@PathVariable long id, @PathVariable long idOferta,
                                  @RequestBody(required = false) RespuestaOfertaRequest dto) {
        return OfertaResponse.desde(ofertas.retirar(id, idOferta,
                dto == null ? null : dto.motivo(), SesionActual.actor()));
    }
}
