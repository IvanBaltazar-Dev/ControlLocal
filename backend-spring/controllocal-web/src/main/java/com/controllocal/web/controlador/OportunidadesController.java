package com.controllocal.web.controlador;

import com.controllocal.service.OportunidadService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.NoContinuidadRequest;
import com.controllocal.web.dto.OportunidadRequest;
import com.controllocal.web.dto.OportunidadResponse;
import com.controllocal.web.dto.ResumenOportunidadesResponse;
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
 * Contrato CONGELADO del OportunidadesRest Jakarta. El alcance por rol vive en
 * el service (BROKER alcanza por CAPTACION, no por agente); aqui el gate: el
 * AGENTE registra y cierra.
 */
@RestController
@RequestMapping("oportunidades")
public class OportunidadesController {

    private final OportunidadService oportunidades;

    public OportunidadesController(OportunidadService oportunidades) {
        this.oportunidades = oportunidades;
    }

    /**
     * {@code estado} es una <b>extension aditiva</b> del v2 (no existe en la
     * v1): omitido, la respuesta es byte a byte la del cable congelado. Existe
     * porque la bandeja Angular filtra por etapa y no puede repetir lo que
     * hacia el Blazor —descargar todas las oportunidades y agrupar en memoria—.
     */
    @GetMapping
    public PageResponse<OportunidadResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                    @RequestParam(defaultValue = "10") int tamano,
                                                    @RequestParam(required = false) Long idCaptacion,
                                                    @RequestParam(required = false) Long idCliente,
                                                    @RequestParam(required = false) String estado,
                                                    @RequestParam(required = false) String query) {
        return pagina(oportunidades.listar(pagina, tamano, idCaptacion, idCliente, estado, query,
                SesionActual.actor()), pagina, tamano);
    }

    /**
     * Extension aditiva: KPI por etapa del MISMO conjunto que pagina la lista.
     * No acepta {@code estado} — son los cubos que devuelve.
     */
    @GetMapping("resumen")
    public ResumenOportunidadesResponse resumen(@RequestParam(required = false) Long idCaptacion,
                                                @RequestParam(required = false) Long idCliente,
                                                @RequestParam(required = false) String query) {
        return ResumenOportunidadesResponse.desde(
                oportunidades.resumen(idCaptacion, idCliente, query, SesionActual.actor()));
    }

    @GetMapping("{id}")
    public OportunidadResponse obtener(@PathVariable long id) {
        return OportunidadResponse.desde(oportunidades.obtener(id, SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<OportunidadResponse> registrar(
            @RequestBody(required = false) OportunidadRequest dto) {
        OportunidadResponse creada = OportunidadResponse.desde(
                oportunidades.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PostMapping("{id}/no-continuidad")
    @PreAuthorize("hasRole('AGENTE')")
    public OportunidadResponse noContinuidad(@PathVariable long id,
                                             @RequestBody(required = false) NoContinuidadRequest dto) {
        return OportunidadResponse.desde(oportunidades.noContinuidad(id,
                dto != null ? dto.razon() : null, dto != null ? dto.observaciones() : null,
                SesionActual.actor()));
    }

    /**
     * Existe y SIEMPRE responde 400: el cierre exitoso lo produce la solicitud
     * aprobada (F4). Es cable real que el frontend consume — no se "arregla".
     */
    @PostMapping("{id}/cierre-exitoso")
    @PreAuthorize("hasRole('AGENTE')")
    public OportunidadResponse cierreExitoso(@PathVariable long id) {
        return OportunidadResponse.desde(oportunidades.cierreExitoso(id, SesionActual.actor()));
    }

    private static PageResponse<OportunidadResponse> pagina(
            Pagina<OportunidadService.FichaOportunidad> pagina, int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(OportunidadResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
