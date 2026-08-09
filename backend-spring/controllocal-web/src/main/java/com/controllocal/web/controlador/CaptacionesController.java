package com.controllocal.web.controlador;

import com.controllocal.service.CaptacionService;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.CaptacionRequest;
import com.controllocal.web.dto.CaptacionResponse;
import com.controllocal.web.dto.CierreRequest;
import com.controllocal.web.dto.CoincidenciasResponse;
import com.controllocal.web.dto.DecisionRequest;
import com.controllocal.web.dto.PropiedadEquipoResponse;
import com.controllocal.web.dto.ReasignacionRequest;
import com.controllocal.web.dto.ResumenEquipoResponse;
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
 * Contrato CONGELADO del CaptacionesRest Jakarta. El alcance por rol y las
 * guardas de estado viven en el service; aqui el gate por rol: el AGENTE
 * registra/edita lo suyo, el BROKER/ADMIN decide/reasigna/cierra. Las rutas
 * literales (pendientes, reasignables, codigo) ganan a {id} por especificidad.
 * {@code {idOrCodigo}/coincidencias} llego con F3. Los cuatro PDF Jasper de la
 * v1 (contrato de exclusividad, ficha de captacion, ficha de propiedad y el
 * reporte al propietario) <b>no se portan</b>: quedaron FUERA DEL ALCANCE de la
 * migracion (D-F5-1,
 * {@code docs/ai/decision-reportes-pdf-fuera-de-alcance.md}).
 */
@RestController
@RequestMapping("captaciones")
public class CaptacionesController {

    private final CaptacionService captaciones;
    private final CoincidenciaService coincidencias;

    public CaptacionesController(CaptacionService captaciones, CoincidenciaService coincidencias) {
        this.captaciones = captaciones;
        this.coincidencias = coincidencias;
    }

    @GetMapping
    public PageResponse<CaptacionResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                  @RequestParam(defaultValue = "10") int tamano,
                                                  @RequestParam(required = false) String estado,
                                                  @RequestParam(required = false) Long idAgente,
                                                  @RequestParam(required = false) String q) {
        var filtros = new CaptacionService.FiltrosCaptacion(
                estado, idAgente, q, pagina, tamano);
        return pagina(captaciones.listar(filtros, SesionActual.actor()), pagina, tamano);
    }

    @GetMapping("pendientes")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public PageResponse<CaptacionResponse> pendientes(@RequestParam(defaultValue = "1") int pagina,
                                                      @RequestParam(defaultValue = "10") int tamano,
                                                      @RequestParam(required = false) String estado,
                                                      @RequestParam(required = false) Long idAgente,
                                                      @RequestParam(required = false) String q) {
        var filtros = new CaptacionService.FiltrosPendientes(estado, idAgente, q, pagina, tamano);
        return pagina(captaciones.pendientes(filtros, SesionActual.actor()), pagina, tamano);
    }

    /**
     * Cartera del equipo vista POR INMUEBLE. <b>Extension aditiva</b>: no
     * existe en la v1, cuya pantalla descargaba todas las captaciones del
     * equipo y deduplicaba por local en el navegador. Aqui la deduplicacion,
     * el filtro, el orden, la paginacion y el conteo bajan a SQL — misma
     * convencion que {@code /locales} (docs/ai/contrato-listados-paginados.md).
     *
     * <p>El alcance es el mismo del resto del recurso: el BROKER ve los
     * inmuebles de sus supervisados y el ADMIN los del tenant.
     */
    @GetMapping("propiedades-equipo")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public PageResponse<PropiedadEquipoResponse> propiedadesEquipo(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String distrito) {
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        var resultado = captaciones.carteraDelEquipo(
                new CaptacionService.FiltrosEquipo(texto, distrito, paginaValida, tamanoValido),
                SesionActual.actor());
        return new PageResponse<>(resultado.items().stream()
                .map(PropiedadEquipoResponse::desde).toList(),
                resultado.total(), paginaValida, tamanoValido);
    }

    /**
     * KPI de la cartera del equipo, con el mismo {@code texto} que la lista y
     * los distritos disponibles para el filtro. No lleva {@code distrito} a
     * proposito: el resumen es lo que ese filtro acota.
     */
    @GetMapping("propiedades-equipo/resumen")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public ResumenEquipoResponse resumenPropiedadesEquipo(
            @RequestParam(required = false) String texto) {
        var actor = SesionActual.actor();
        return ResumenEquipoResponse.desde(captaciones.resumenCarteraDelEquipo(texto, actor),
                captaciones.distritosDelEquipo(texto, actor));
    }

    @GetMapping("reasignables")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public PageResponse<CaptacionResponse> reasignables(@RequestParam(defaultValue = "1") int pagina,
                                                        @RequestParam(defaultValue = "10") int tamano,
                                                        @RequestParam(required = false) String q) {
        return pagina(captaciones.reasignables(pagina, tamano, q, SesionActual.actor()), pagina, tamano);
    }

    @GetMapping("{id}")
    public CaptacionResponse obtener(@PathVariable long id) {
        return CaptacionResponse.desde(captaciones.obtener(id, SesionActual.actor()));
    }

    @GetMapping("codigo/{codigo}")
    public CaptacionResponse obtenerPorCodigo(@PathVariable String codigo) {
        return CaptacionResponse.desde(captaciones.obtenerPorCodigo(codigo, SesionActual.actor()));
    }

    /**
     * Captacion -> clientes compatibles, accionable ("Proponer"). Acepta id o
     * codigo en la misma ruta, como el cable v1.
     */
    @GetMapping("{idOrCodigo}/coincidencias")
    public CoincidenciasResponse coincidencias(@PathVariable String idOrCodigo,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pagina,
                                               @RequestParam(name = "page_size", required = false) Integer pageSize,
                                               @RequestParam(required = false) Integer tamano) {
        return CoincidenciasResponse.desde(coincidencias.clientesParaCaptacion(idOrCodigo,
                ClientesController.paginaSolicitada(page, pagina),
                ClientesController.tamanoSolicitado(pageSize, tamano), SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<CaptacionResponse> registrar(@RequestBody(required = false) CaptacionRequest dto) {
        CaptacionResponse creada = CaptacionResponse.desde(
                captaciones.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public CaptacionResponse actualizar(@PathVariable long id,
                                        @RequestBody(required = false) CaptacionRequest dto) {
        return CaptacionResponse.desde(
                captaciones.actualizar(id, dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    @PostMapping("{id}/decision")
    @PreAuthorize("hasRole('BROKER')")
    public CaptacionResponse decidir(@PathVariable long id, @RequestBody(required = false) DecisionRequest dto) {
        String accion = dto != null ? dto.accion() : null;
        String observacion = dto != null ? dto.observacion() : null;
        return CaptacionResponse.desde(captaciones.decidir(id, accion, observacion, SesionActual.actor()));
    }

    @PostMapping("{id}/reasignar")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public CaptacionResponse reasignar(@PathVariable long id,
                                       @RequestBody(required = false) ReasignacionRequest dto) {
        Long idAgenteNuevo = dto != null ? dto.idAgenteNuevo() : null;
        String motivo = dto != null ? dto.motivo() : null;
        return CaptacionResponse.desde(captaciones.reasignar(id, idAgenteNuevo, motivo, SesionActual.actor()));
    }

    @PostMapping("{id}/cierre")
    @PreAuthorize("hasRole('BROKER')")
    public CaptacionResponse cerrar(@PathVariable long id, @RequestBody(required = false) CierreRequest dto) {
        return CaptacionResponse.desde(
                captaciones.cerrar(id, dto != null ? dto.motivo() : null, SesionActual.actor()));
    }

    private static PageResponse<CaptacionResponse> pagina(Pagina<CaptacionService.FichaCaptacion> pagina,
                                                          int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(CaptacionResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
