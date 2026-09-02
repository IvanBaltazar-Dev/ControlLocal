package com.controllocal.web.controlador;

import com.controllocal.service.CaptacionService;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.dto.CandidatoAgenteResponse;
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

    /**
     * <b>El traspaso de la autoridad del ENCARGO</b> (D-S0-17 fila 6, D-P0-7 y
     * D-P0-9).
     *
     * <p><b>El cuerpo declara sobre que agente se actua</b>:
     * {@code idAgenteActual} —el que se vio en la lista— es obligatorio, y su
     * ausencia es <b>400</b>. Si al ejecutarse el encargo ya no lo lleva ese
     * agente, la respuesta es <b>409</b> y no se ha escrito nada: la
     * reasignacion no se reinterpreta sobre un estado que el actor no vio. La
     * validacion de la declaracion vive en el propio DTO para que sea la misma
     * por cualquier canal.
     *
     * <p>Quien puede <b>no cambia</b> con esto: BROKER dentro de su equipo,
     * TENANT_ADMIN entre equipos. Lo que se anade es que el <b>destino</b> tiene
     * que poder recibirlo hoy (D-P0-7, las mismas cinco condiciones que el
     * traspaso de una propiedad) y que la escritura es un compare-and-set sobre
     * el agente observado.
     */
    @PostMapping("{id}/reasignar")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public CaptacionResponse reasignar(@PathVariable long id,
                                       @RequestBody(required = false) ReasignacionRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("El agente destino es obligatorio.");
        }
        return CaptacionResponse.desde(captaciones.reasignar(id, dto.destino(), dto.motivo(),
                dto.observado(), SesionActual.actor()));
    }

    /**
     * <b>A quien puedo pasarle este encargo</b> — los candidatos ya elegibles
     * para ESTE encargo y ESTE actor (D-P0-7 + D-P0-12).
     *
     * <p><b>Existe para que Angular no decida autoridad.</b> Hasta aqui la
     * pantalla de reasignaciones pedia {@code GET /agentes} y depuraba la lista
     * en el cliente con dos de las seis condiciones —cuenta activa y
     * disponibilidad— resueltas sobre <b>una pagina</b> de cien agentes: una
     * copia parcial de una regla de autoridad, que ofrecia agentes que el POST
     * rechaza y escondia a otros perfectamente validos en cuanto la organizacion
     * pasara de cien.
     *
     * <p>Sale del <b>mismo</b> componente y del <b>mismo</b> predicado SQL que
     * los candidatos de una propiedad: quien puede recibir una propiedad y quien
     * puede recibir un encargo responden a las mismas condiciones, y lo unico
     * que cambia es a quien se excluye —alli el responsable actual, aqui el
     * agente actual del encargo.
     *
     * <p>Paginado y buscable en el servidor por la misma razon que el de la
     * propiedad. <b>Y no autoriza nada</b>: el POST revalida.
     */
    @GetMapping("{id}/reasignacion/candidatos")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public PageResponse<CandidatoAgenteResponse> candidatosAReasignacion(
            @PathVariable long id,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer tamano) {
        int paginaSolicitada = ClientesController.paginaSolicitada(page, pagina);
        int tamanoSolicitado = pageSize != null ? pageSize : tamano != null ? tamano : 20;
        var resultado = captaciones.candidatosAReasignacion(id, texto, paginaSolicitada,
                tamanoSolicitado, SesionActual.actor());
        return new PageResponse<>(
                resultado.items().stream().map(CandidatoAgenteResponse::desde).toList(),
                resultado.total(), Math.max(1, paginaSolicitada),
                Math.max(1, Math.min(100, tamanoSolicitado)));
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
