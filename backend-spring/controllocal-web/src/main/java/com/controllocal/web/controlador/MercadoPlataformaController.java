package com.controllocal.web.controlador;

import com.controllocal.service.mercado.InvestigacionesMercado;
import com.controllocal.service.mercado.DatosInvestigacionMercado.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Identidad global y concesión R25 explícita; ningún Actor o tenant ficticio. */
@RestController
@RequestMapping("plataforma/mercado")
public class MercadoPlataformaController {
    private final InvestigacionesMercado mercado;private final AtribucionesPlataforma atribuciones;
    public MercadoPlataformaController(InvestigacionesMercado mercado,AtribucionesPlataforma atribuciones){this.mercado=mercado;this.atribuciones=atribuciones;}
    public record Configurar(Configuracion datos,UUID decision) { }
    @GetMapping("atribuciones") public ResponseEntity<List<OpcionMercado>> opciones(){var t=SesionActual.sesion().token();return privado(atribuciones.atribucionesMercado(t.referenciaSesion(),t.revisionContexto()));}
    @PostMapping("configuraciones") public ResponseEntity<Void> configurar(@RequestHeader("X-Brox-Concesion") UUID c,@RequestBody Configurar d){mercado.configurar(d.datos(),d.decision(),sesion(c));return privado(null);}
    @GetMapping("configuraciones/{politica}/{version}") public ResponseEntity<Configuracion> configuracion(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable UUID politica,@PathVariable String version){return privado(mercado.consultarConfiguracion(politica,version,sesion(c)));}
    @PostMapping("configuraciones/{politica}/{version}/retiro") public ResponseEntity<Void> retirarPolitica(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable UUID politica,@PathVariable String version,@RequestBody RetiroPolitica d){mercado.retirarPolitica(politica,version,d,sesion(c));return privado(null);}
    @PostMapping("mantenimiento") public ResponseEntity<CabeceraTrabajo> mantenimiento(@RequestHeader("X-Brox-Concesion") UUID c,@RequestBody Preparar d){return privado(mercado.mantenimiento(d,sesion(c)));}
    @GetMapping("colas/ejecucion") public ResponseEntity<List<CabeceraTrabajo>> ejecucion(@RequestHeader("X-Brox-Concesion") UUID c,@RequestParam(defaultValue="0") long despues,@RequestParam(defaultValue="20") int cantidad){return privado(mercado.cola(despues,cantidad,sesion(c)));}
    @GetMapping("colas/revision") public ResponseEntity<List<CabeceraTrabajo>> revision(@RequestHeader("X-Brox-Concesion") UUID c,@RequestParam(defaultValue="0") long despues,@RequestParam(defaultValue="20") int cantidad){return privado(mercado.colaRevision(despues,cantidad,sesion(c)));}
    @GetMapping("colas/finanzas") public ResponseEntity<List<CabeceraTrabajo>> finanzas(@RequestHeader("X-Brox-Concesion") UUID c,@RequestParam(defaultValue="0") long despues,@RequestParam(defaultValue="20") int cantidad){return privado(mercado.colaFinanciera(despues,cantidad,sesion(c)));}
    @GetMapping("trabajos/{id}/operador") public ResponseEntity<TrabajoOperador> operador(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id){return privado(mercado.consultarOperador(id,sesion(c)));}
    @GetMapping("trabajos/{id}/revision") public ResponseEntity<TrabajoRevision> revision(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id){return privado(mercado.consultarRevision(id,sesion(c)));}
    @GetMapping("trabajos/{id}/finanzas") public ResponseEntity<TrabajoFinanciero> finanzas(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id){return privado(mercado.consultarFinanciero(id,sesion(c)));}
    @PostMapping("trabajos/{id}/tomar") public ResponseEntity<CabeceraTrabajo> tomar(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id,@RequestBody Comando d){return privado(mercado.tomar(id,d,sesion(c)));}
    @PostMapping("trabajos/{id}/entrega") public ResponseEntity<CabeceraTrabajo> entregar(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id,@RequestBody Entrega d){return privado(mercado.entregar(id,d,sesion(c)));}
    @PostMapping("trabajos/{id}/revision") public ResponseEntity<CabeceraTrabajo> revisar(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id,@RequestBody RevisionEntrega d){return privado(mercado.revisar(id,d,sesion(c)));}
    @PostMapping("trabajos/{id}/publicacion") public ResponseEntity<CabeceraTrabajo> publicar(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id,@RequestBody Publicacion d){return privado(mercado.publicar(id,d,sesion(c)));}
    @PostMapping("trabajos/{id}/liquidacion") public ResponseEntity<CabeceraTrabajo> liquidar(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id,@RequestBody Comando d){return privado(mercado.liquidar(id,d,sesion(c)));}
    @PostMapping("trabajos/{id}/liberacion-no-iniciada") public ResponseEntity<CabeceraTrabajo> liberar(@RequestHeader("X-Brox-Concesion") UUID c,@PathVariable long id,@RequestBody Retiro d){return privado(mercado.liberarNoIniciada(id,d,sesion(c)));}
    private static SesionPlataforma sesion(UUID c){var t=SesionActual.sesion().token();return new SesionPlataforma(t.referenciaSesion(),t.revisionContexto(),c);}
    private static <T> ResponseEntity<T> privado(T cuerpo){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);}
}
