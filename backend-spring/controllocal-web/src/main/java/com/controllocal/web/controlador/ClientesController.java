package com.controllocal.web.controlador;

import com.controllocal.service.ClienteService;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.FichaComercialService;
import com.controllocal.service.Pagina;
import com.controllocal.web.dto.AutorizacionResponse;
import com.controllocal.web.dto.ClienteRequest;
import com.controllocal.web.dto.ClienteResponse;
import com.controllocal.web.dto.CoincidenciasResponse;
import com.controllocal.web.dto.FichaClienteResponse;
import com.controllocal.web.dto.FichaSectionResponse;
import com.controllocal.web.dto.ResumenClientesResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.http.RecursoNoEncontradoException;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato CONGELADO del ClientesRest Jakarta. El alcance por rol vive en el
 * service (el cliente es catalogo compartido: solo el BROKER queda acotado);
 * aqui el gate por rol —el AGENTE es quien registra, edita y da de baja— y el
 * mapeo DTO. E3 completa la ficha comercial transversal.
 */
@RestController
@RequestMapping("clientes")
public class ClientesController {

    /** Tamano por defecto del panel de coincidencias (§7 del contrato). */
    private static final int COINCIDENCIAS_POR_DEFECTO = 6;

    private final ClienteService clientes;
    private final CoincidenciaService coincidencias;
    private final FichaComercialService fichas;

    public ClientesController(ClienteService clientes, CoincidenciaService coincidencias,
                              FichaComercialService fichas) {
        this.clientes = clientes;
        this.coincidencias = coincidencias;
        this.fichas = fichas;
    }

    /**
     * Los cuatro filtros son ADITIVOS y OPCIONALES: omitidos, la respuesta es
     * byte a byte la del cable congelado. Existen porque la bandeja Angular no
     * puede repetir lo que hacia el Blazor —descargar todos los clientes y
     * filtrar en memoria— y porque el rubro y el estado deben salir del mismo
     * conjunto que cuenta el resumen.
     */
    @GetMapping
    public PageResponse<ClienteResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                @RequestParam(defaultValue = "10") int tamano,
                                                @RequestParam(required = false) String texto,
                                                @RequestParam(required = false) String tipoPersona,
                                                @RequestParam(required = false) String rubro,
                                                @RequestParam(required = false) String estado) {
        ClienteService.FiltrosCliente filtros = new ClienteService.FiltrosCliente(
                texto, tipoPersona, rubro, estado, pagina, tamano);
        return pagina(clientes.listar(filtros, SesionActual.actor()), pagina, tamano);
    }

    /**
     * Extension aditiva (no existe en la v1): KPI del alcance con los mismos
     * filtros que la lista, mas los rubros disponibles para que el selector sea
     * data-driven sin descargar la cartera. No acepta {@code estado}: es uno de
     * los cubos que devuelve.
     */
    @GetMapping("resumen")
    public ResumenClientesResponse resumen(@RequestParam(required = false) String texto,
                                           @RequestParam(required = false) String tipoPersona,
                                           @RequestParam(required = false) String rubro) {
        return ResumenClientesResponse.desde(clientes.resumen(
                new ClienteService.FiltrosCliente(texto, tipoPersona, rubro, null, 1, 1),
                SesionActual.actor()));
    }

    @GetMapping("{id}")
    public ClienteResponse obtener(@PathVariable long id) {
        return ClienteResponse.desde(clientes.obtener(id, SesionActual.actor()));
    }

    /**
     * Constancia de la autorizacion de datos (D-27). <b>Endpoint nuevo</b> y no
     * un campo mas en {@code ClienteResponse}: esa respuesta es contrato
     * congelado, y ampliarla la separaria del cable de la v1.
     */
    @GetMapping("{id}/autorizacion")
    public AutorizacionResponse autorizacion(@PathVariable long id) {
        return AutorizacionResponse.desde(clientes.autorizacion(id, SesionActual.actor()));
    }

    @GetMapping("{id}/ficha-comercial")
    public FichaClienteResponse fichaComercial(
            @PathVariable long id,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer tamano) {
        return FichaClienteResponse.desde(fichas.fichaCliente(
                id, tamanoFicha(pageSize, tamano), SesionActual.actor()));
    }

    @GetMapping("{id}/ficha-comercial/{section}")
    public FichaSectionResponse fichaComercialSection(
            @PathVariable long id,
            @PathVariable String section,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer tamano) {
        return FichaSectionResponse.desde(fichas.seccionCliente(
                id, section, paginaSolicitada(page, pagina), tamanoFicha(pageSize, tamano),
                SesionActual.actor()));
    }

    /** Cliente -> propiedades compatibles. Acepta los dos juegos de parametros del cable. */
    @GetMapping("{id}/coincidencias")
    public CoincidenciasResponse coincidencias(@PathVariable long id,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pagina,
                                               @RequestParam(name = "page_size", required = false) Integer pageSize,
                                               @RequestParam(required = false) Integer tamano) {
        return CoincidenciasResponse.desde(coincidencias.propiedadesParaCliente(id,
                paginaSolicitada(page, pagina), tamanoSolicitado(pageSize, tamano), SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<ClienteResponse> registrar(@RequestBody(required = false) ClienteRequest dto) {
        ClienteResponse creado = ClienteResponse.desde(
                clientes.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public ClienteResponse actualizar(@PathVariable long id,
                                      @RequestBody(required = false) ClienteRequest dto) {
        return ClienteResponse.desde(
                clientes.actualizar(id, dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<Void> eliminar(@PathVariable long id) {
        if (!clientes.desactivar(id, SesionActual.actor())) {
            throw new RecursoNoEncontradoException("Cliente");
        }
        return ResponseEntity.noContent().build();
    }

    /** {@code page} gana a {@code pagina}; sin ninguno, la primera. */
    static int paginaSolicitada(Integer page, Integer pagina) {
        return page != null ? page : pagina != null ? pagina : 1;
    }

    /** {@code page_size} gana a {@code tamano}; sin ninguno, 6 (tope de 24 en el service). */
    static int tamanoSolicitado(Integer pageSize, Integer tamano) {
        return pageSize != null ? pageSize : tamano != null ? tamano : COINCIDENCIAS_POR_DEFECTO;
    }

    /** Alias de ficha: mismo orden de precedencia, pero su default congelado es 8. */
    private static int tamanoFicha(Integer pageSize, Integer tamano) {
        return pageSize != null ? pageSize
                : tamano != null ? tamano : FichaComercialService.TAMANO_POR_DEFECTO;
    }

    private static PageResponse<ClienteResponse> pagina(Pagina<ClienteService.FichaCliente> pagina,
                                                        int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(ClienteResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
