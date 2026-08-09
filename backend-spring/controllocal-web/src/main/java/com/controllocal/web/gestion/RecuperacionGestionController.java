package com.controllocal.web.gestion;

import com.controllocal.service.RecuperacionEmergenciaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Superficie de la recuperacion de emergencia. <b>Vive solo en el conector de
 * gestion local</b> (127.0.0.1, puerto propio, no publicado): pedirla por el
 * puerto publico responde 404 antes de llegar aqui.
 *
 * <h2>Tres cosas que no hace, y no por falta de tiempo</h2>
 * <ol>
 *   <li><b>No emite sesion.</b> Ninguna respuesta lleva token, JWT ni cookie.
 *       No hay nada en lo que entrar.</li>
 *   <li><b>No esta en la matriz operacion→rol</b> y eso es correcto: la matriz
 *       gobierna el API del producto, y esto no lo es. No hay rol que
 *       comprobar porque no hay sesion.</li>
 *   <li><b>No aparece en el SPA.</b> Ninguna pantalla la conoce ni debe
 *       conocerla.</li>
 * </ol>
 *
 * <p>El secreto de la concesion viaja <b>en cabecera</b>, nunca en la URL: una
 * URL acaba en los registros del servidor y en el historial del cliente.
 */
@RestController
@RequestMapping(ConectorGestionLocal.RUTA_GESTION + "/recuperacion")
@ConditionalOnProperty(name = "controllocal.recuperacion.habilitada", havingValue = "true")
public class RecuperacionGestionController {

    private final RecuperacionEmergenciaService recuperacion;
    private final com.controllocal.service.soporte.CustodiosConfigurados custodios;

    public RecuperacionGestionController(
            RecuperacionEmergenciaService recuperacion,
            com.controllocal.service.soporte.CustodiosConfigurados custodios) {
        this.recuperacion = recuperacion;
        this.custodios = custodios;
    }

    /**
     * ¿Esta esto listo para usarse? Dos booleanos, y ninguno revela nada: si
     * hay custodios configurados y si la funcion esta encendida.
     *
     * <p>Existe porque la alternativa es descubrirlo en plena emergencia. Un
     * operador que llega aqui con el tenant caido necesita saber en un segundo
     * si el problema es la configuracion o su secreto, y un 400 opaco no se lo
     * dice. <b>No expone identificadores ni hashes</b>: solo si estan.
     */
    @GetMapping("estado")
    public ResponseEntity<Map<String, Object>> preparacion() {
        return sinCache(Map.of(
                "habilitada", true,
                "custodiosConfigurados", custodios.estanConfigurados()));
    }

    /** Abre la concesion en PENDIENTE. Todavia no autoriza nada. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> emitir(@RequestBody Map<String, String> cuerpo) {
        long id = recuperacion.emitir(new RecuperacionEmergenciaService.Emision(
                Long.parseLong(cuerpo.getOrDefault("idOrganizacion", "0")),
                Long.parseLong(cuerpo.getOrDefault("idPersonaObjetivo", "0")),
                cuerpo.get("operador"),
                cuerpo.get("motivo")));
        return sinCache(Map.of("idConcesion", id, "estado", "PENDIENTE"));
    }

    /**
     * Aprobacion de un custodio. La segunda devuelve el secreto de la
     * concesion <b>una sola vez</b>; la primera no devuelve nada que sirva.
     */
    @PostMapping("{idConcesion}/aprobaciones")
    public ResponseEntity<Map<String, Object>> aprobar(@PathVariable long idConcesion,
                                                       @RequestBody Map<String, String> cuerpo) {
        char[] secreto = cuerpo.getOrDefault("secreto", "").toCharArray();
        try {
            var concesion = recuperacion.aprobar(idConcesion, cuerpo.get("custodio"), secreto);
            return sinCache(concesion
                    .<Map<String, Object>>map(valor -> Map.of("estado", "VIGENTE", "concesion", valor))
                    .orElseGet(() -> Map.of("estado", "PENDIENTE",
                            "mensaje", "Falta la segunda aprobacion.")));
        } finally {
            java.util.Arrays.fill(secreto, '\0');
        }
    }

    @GetMapping("{idConcesion}")
    public ResponseEntity<RecuperacionEmergenciaService.Estado> consultar(
            @PathVariable long idConcesion) {
        return sinCache(recuperacion.consultar(idConcesion));
    }

    /** Aplica UNA de las tres acciones. Consume capacidad aunque no cambie nada. */
    @PostMapping("acciones/{tipo}")
    public ResponseEntity<RecuperacionEmergenciaService.Resultado> aplicar(
            @PathVariable String tipo,
            @RequestHeader("X-Concesion") String concesion) {
        return sinCache(recuperacion.aplicar(concesion, tipo));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
