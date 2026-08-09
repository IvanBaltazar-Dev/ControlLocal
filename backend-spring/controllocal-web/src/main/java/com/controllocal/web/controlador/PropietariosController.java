package com.controllocal.web.controlador;

import com.controllocal.service.FichaComercialService;
import com.controllocal.service.Pagina;
import com.controllocal.service.PropietarioService;
import com.controllocal.web.dto.AutorizacionResponse;
import com.controllocal.web.dto.FichaPropietarioResponse;
import com.controllocal.web.dto.FichaSectionResponse;
import com.controllocal.web.dto.PropietarioRequest;
import com.controllocal.web.dto.PropietarioResponse;
import com.controllocal.web.dto.PropietariosResumenResponse;
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
 * Contrato CONGELADO del {@code PropietariosRest} Jakarta. El alcance por rol
 * vive en el service (solo el BROKER queda acotado); aqui el gate por rol —el
 * AGENTE es quien registra, edita y da de baja, igual que en clientes— y el
 * mapeo DTO.
 *
 * <p>E3 completa aqui la ficha transversal compartida con {@code /clientes}.
 */
@RestController
@RequestMapping("propietarios")
public class PropietariosController {

    private final PropietarioService propietarios;
    private final FichaComercialService fichas;

    public PropietariosController(PropietarioService propietarios,
                                  FichaComercialService fichas) {
        this.propietarios = propietarios;
        this.fichas = fichas;
    }

    /**
     * Listado con filtros <b>aditivos</b> (`texto`, `estado`): omitidos, la
     * respuesta es la del cable congelado, orden por id descendente incluido.
     */
    @GetMapping
    public PageResponse<PropietarioResponse> listar(@RequestParam(defaultValue = "1") int pagina,
                                                    @RequestParam(defaultValue = "10") int tamano,
                                                    @RequestParam(required = false) String texto,
                                                    @RequestParam(required = false) String estado) {
        return pagina(propietarios.listar(
                        new PropietarioService.FiltrosPropietario(texto, estado, pagina, tamano),
                        SesionActual.actor()),
                pagina, tamano);
    }

    /** Cubos del catálogo. No acepta `estado`: es el cubo que devuelve. */
    @GetMapping("resumen")
    public PropietariosResumenResponse resumen(@RequestParam(required = false) String texto) {
        return PropietariosResumenResponse.desde(propietarios.resumen(
                new PropietarioService.FiltrosPropietario(texto, null, 1, 1),
                SesionActual.actor()));
    }

    @GetMapping("{id}")
    public PropietarioResponse obtener(@PathVariable long id) {
        return PropietarioResponse.desde(propietarios.obtener(id, SesionActual.actor()));
    }

    /**
     * Constancia de la autorizacion de datos (D-27), igual que la de cliente:
     * endpoint aparte para no ampliar una respuesta congelada.
     */
    @GetMapping("{id}/autorizacion")
    public AutorizacionResponse autorizacion(@PathVariable long id) {
        return AutorizacionResponse.desde(propietarios.autorizacion(id, SesionActual.actor()));
    }

    @GetMapping("{id}/ficha-comercial")
    public FichaPropietarioResponse fichaComercial(
            @PathVariable long id,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer tamano) {
        return FichaPropietarioResponse.desde(fichas.fichaPropietario(
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
        return FichaSectionResponse.desde(fichas.seccionPropietario(
                id, section, paginaSolicitada(page, pagina), tamanoFicha(pageSize, tamano),
                SesionActual.actor()));
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<PropietarioResponse> registrar(
            @RequestBody(required = false) PropietarioRequest dto) {
        PropietarioResponse creado = PropietarioResponse.desde(
                propietarios.registrar(dto == null ? null : dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public PropietarioResponse actualizar(@PathVariable long id,
                                          @RequestBody(required = false) PropietarioRequest dto) {
        return PropietarioResponse.desde(
                propietarios.actualizar(id, dto == null ? null : dto.aDatos(), SesionActual.actor()));
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<Void> eliminar(@PathVariable long id) {
        if (!propietarios.desactivar(id, SesionActual.actor())) {
            throw new RecursoNoEncontradoException("Propietario");
        }
        return ResponseEntity.noContent().build();
    }

    private static int paginaSolicitada(Integer page, Integer pagina) {
        return page != null ? page : pagina != null ? pagina : 1;
    }

    private static int tamanoFicha(Integer pageSize, Integer tamano) {
        return pageSize != null ? pageSize
                : tamano != null ? tamano : FichaComercialService.TAMANO_POR_DEFECTO;
    }

    private static PageResponse<PropietarioResponse> pagina(
            Pagina<PropietarioService.FichaPropietario> pagina,
            int paginaSolicitada, int tamanoSolicitado) {
        int paginaValida = Math.max(1, paginaSolicitada);
        int tamanoValido = Math.max(1, Math.min(100, tamanoSolicitado));
        return new PageResponse<>(pagina.items().stream().map(PropietarioResponse::desde).toList(),
                pagina.total(), paginaValida, tamanoValido);
    }
}
