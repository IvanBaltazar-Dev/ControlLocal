package com.controllocal.web.controlador;

import com.controllocal.service.PublicacionService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.dto.EstadoPublicacionRequest;
import com.controllocal.web.dto.PublicacionDtos.PublicacionRequest;
import com.controllocal.web.dto.PublicacionDtos.PublicacionResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <b>El encargo por el cable</b>, y de momento lo único que cuelga de él: sus
 * publicaciones (V70).
 *
 * <h2>Por qué la publicación vive aquí y no en {@code /locales/{id}}</h2>
 * Un anuncio no anuncia «una propiedad»: anuncia que esta propiedad se ofrece
 * en ESTA operación a ESTE precio. Con venta y alquiler simultáneos,
 * {@code /locales/{id}/publicaciones} devolvía las dos series juntas sin poder
 * decir cuál publica qué — y el cliente no tenía forma de separarlas, porque la
 * publicación no llevaba la operación.
 *
 * <p>La URL no es cosmética: es la relación real del modelo.
 *
 * <pre>
 *   Propiedad → Encargo → Publicación
 * </pre>
 *
 * <h2>Un recurso nuevo, no lógica nueva</h2>
 * Los casos de uso son los mismos de {@link PublicacionService}: se reutilizan.
 * Lo que cambia es <b>por dónde se entra</b> y qué se comprueba al entrar — que
 * el encargo sea del tenant, y que esté vigente para poder publicar.
 */
@RestController
@RequestMapping("encargos")
public class EncargosController {

    private final PublicacionService publicaciones;

    public EncargosController(PublicacionService publicaciones) {
        this.publicaciones = publicaciones;
    }

    /**
     * Los anuncios de este encargo, del más reciente al más antiguo. Un encargo
     * de otro tenant responde <b>404</b>.
     */
    @GetMapping("{idEncargo}/publicaciones")
    public List<PublicacionResponse> listar(@PathVariable long idEncargo) {
        return publicaciones.listarDeEncargo(idEncargo, SesionActual.actor()).stream()
                .map(PublicacionResponse::desde)
                .toList();
    }

    /**
     * Publica este encargo en un canal.
     *
     * <p>Rechaza el encargo no vigente: publicar uno cerrado pondría en el
     * mercado algo que ya no se ofrece. La regla la impone el servicio, no la
     * pantalla que dibuja el botón.
     */
    @PostMapping("{idEncargo}/publicaciones")
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<PublicacionResponse> crear(
            @PathVariable long idEncargo,
            @RequestBody(required = false) PublicacionRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos de la publicacion son obligatorios.");
        }
        PublicacionResponse creada = PublicacionResponse.desde(
                publicaciones.crearEnEncargo(idEncargo, dto.aDatos(), SesionActual.actor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("{idEncargo}/publicaciones/{idPublicacion}")
    @PreAuthorize("hasRole('AGENTE')")
    public PublicacionResponse actualizar(@PathVariable long idEncargo,
                                          @PathVariable long idPublicacion,
                                          @RequestBody(required = false) PublicacionRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos de la publicacion son obligatorios.");
        }
        return PublicacionResponse.desde(
                publicaciones.actualizar(idPublicacion, dto.aDatos(), SesionActual.actor()));
    }

    /** Publicar (`P`), pausar (`S`) o cerrar (`C`) el anuncio. */
    @PostMapping("{idEncargo}/publicaciones/{idPublicacion}/estado")
    @PreAuthorize("hasRole('AGENTE')")
    public PublicacionResponse cambiarEstado(@PathVariable long idEncargo,
                                             @PathVariable long idPublicacion,
                                             @RequestBody(required = false) EstadoPublicacionRequest dto) {
        if (dto == null || dto.estado() == null || dto.estado().isBlank()) {
            throw new ReglaNegocioException("El estado de la publicacion es obligatorio.");
        }
        return PublicacionResponse.desde(
                publicaciones.cambiarEstado(idPublicacion, dto.estado(), SesionActual.actor()));
    }
}
