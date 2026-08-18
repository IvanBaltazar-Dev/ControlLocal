package com.controllocal.web.controlador;

import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import jakarta.servlet.http.HttpServletRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.EdicionRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.PreguntaCatalogoResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.PropiedadResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.RegistroRequest;
import com.controllocal.web.dto.PropiedadUniversalDtos.RegistroResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <b>La propiedad universal por el cable</b> (D-E4-1).
 *
 * <h2>Por que un recurso nuevo y no mas verbos en {@code /locales}</h2>
 * {@code /locales} es el alta de la v1: un local comercial en alquiler, con un
 * solo propietario y el precio en una columna. Estirarlo para admitir siete
 * tipos, copropiedad y dos operaciones simultaneas habria dejado un recurso con
 * dos comportamientos segun que campos llegaran — y con las 57 pantallas
 * actuales colgando del comportamiento viejo.
 *
 * <p>{@code /propiedades} es el modelo nuevo entero, y el viejo sigue donde
 * estaba hasta que las pantallas migren. Los dos escriben en las mismas tablas.
 *
 * <h2>Las dos cabeceras</h2>
 * <ul>
 *   <li>{@code Idempotency-Key}: del cliente. Con ella, un reintento devuelve
 *       lo que produjo el primer intento en vez de crear una segunda propiedad.
 *       Es lo que hace seguro poner un canal conversacional delante.</li>
 *   <li>{@code X-Origen}: UI, KAIROS, API o SISTEMA. Va al evento de dominio y
 *       es lo que permite responder <i>"quien decidio esto"</i> cuando el
 *       agente conversacional opere (D-K-1).</li>
 * </ul>
 */
@RestController
@RequestMapping("propiedades")
public class PropiedadesUniversalesController {

    private final PropiedadUniversalService propiedades;
    private final ProcedenciaDeCabeceras procedencias;

    public PropiedadesUniversalesController(PropiedadUniversalService propiedades,
                                            ProcedenciaDeCabeceras procedencias) {
        this.propiedades = propiedades;
        this.procedencias = procedencias;
    }

    /**
     * Alta universal. Una transaccion: propiedad, ubicacion, titulares,
     * atributos, encargos, condiciones, primeros hitos y evento.
     */
    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    public ResponseEntity<RegistroResponse> registrar(
            @RequestBody(required = false) RegistroRequest dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            HttpServletRequest peticion) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos de la propiedad son obligatorios.");
        }
        RegistroResponse creada = RegistroResponse.desde(
                propiedades.registrar(dto.aDatos(claveIdempotencia, procedencias.de(peticion)), SesionActual.actor()));
        // 200 y no 201 cuando fue un reintento: no se creo nada esta vez, y
        // decirle 201 al cliente le haria contar dos altas donde hubo una.
        return ResponseEntity.status(creada.reintento() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(creada);
    }

    /** La propiedad leida por el modelo universal: titulares, atributos y encargos. */
    @GetMapping("{id}")
    public PropiedadResponse consultar(@PathVariable long id) {
        return PropiedadResponse.desde(propiedades.consultar(id, SesionActual.actor()));
    }

    /**
     * Edicion parcial. Cambiar el importe de una operacion <b>anade</b> un hito
     * al historico; el anterior no se pierde nunca.
     */
    @PutMapping("{id}")
    @PreAuthorize("hasRole('AGENTE')")
    public PropiedadResponse editar(
            @PathVariable long id,
            @RequestBody(required = false) EdicionRequest dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            HttpServletRequest peticion) {
        if (dto == null) {
            throw new ReglaNegocioException("Los datos de la propiedad son obligatorios.");
        }
        return PropiedadResponse.desde(
                propiedades.editar(id, dto.aDatos(claveIdempotencia, procedencias.de(peticion)), SesionActual.actor()));
    }

    /**
     * Que se pregunta para un tipo de propiedad. Sale del catalogo, no de una
     * lista escrita en el cliente: por eso un TERRENO no devuelve dormitorios.
     */
    @GetMapping("catalogo/{tipoPropiedad}")
    public List<PreguntaCatalogoResponse> catalogo(@PathVariable String tipoPropiedad) {
        return propiedades.catalogoDe(tipoPropiedad, SesionActual.actor()).stream()
                .map(pregunta -> new PreguntaCatalogoResponse(pregunta.clave(), pregunta.rotulo(),
                        pregunta.tipoDato(), pregunta.unidad(), pregunta.obligatoria(),
                        pregunta.orden()))
                .toList();
    }
}
