package com.controllocal.web.controlador;

import com.controllocal.service.presupuesto.MetricasIaB0;
import com.controllocal.service.presupuesto.ConfirmacionesCostoIaB0;
import com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

/** Identidad de la sesión y concesión R25; sin Actor de tenant ni permiso deducido del correo. */
@RestController
@RequestMapping("plataforma/metricas-ia")
public class MetricasIaPlataformaController {
    private final MetricasIaB0 metricas;
    private final ConfirmacionesCostoIaB0 costos;
    public MetricasIaPlataformaController(MetricasIaB0 metricas,ConfirmacionesCostoIaB0 costos){this.metricas=metricas;this.costos=costos;}
    @GetMapping("tecnicas")
    public ResponseEntity<MetricasIaB0.Tecnica> tecnicas(@RequestHeader("X-Brox-Concesion") UUID concesion,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant hasta){
        return privado(metricas.tecnica(sesion(concesion),desde,hasta));
    }
    @GetMapping("financieras/{caso}/{comando}")
    public ResponseEntity<MetricasIaB0.Financiera> financieras(@RequestHeader("X-Brox-Concesion") UUID concesion,
            @PathVariable UUID caso,@PathVariable UUID comando,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant hasta){
        return privado(metricas.financiera(sesion(concesion),caso,comando,desde,hasta));
    }
    @PostMapping("financieras/{caso}/{comando}/confirmaciones-costo")
    public ResponseEntity<ConfirmacionesCostoIaB0.Recibo> confirmarCosto(@RequestHeader("X-Brox-Concesion") UUID concesion,
            @PathVariable UUID caso,@PathVariable UUID comando,@RequestBody ConfirmacionesCostoIaB0.Solicitud solicitud){
        return privado(costos.confirmar(caso,comando,solicitud,sesion(concesion)));
    }
    @PostMapping("financieras/{caso}/{comando}/aplicacion-costo")
    public ResponseEntity<ConfirmacionesCostoIaB0.Recibo> aplicarCosto(@RequestHeader("X-Brox-Concesion") UUID concesion,
            @PathVariable UUID caso,@PathVariable UUID comando,@RequestBody ConfirmacionesCostoIaB0.Recuperacion solicitud){
        return privado(costos.aplicar(caso,comando,solicitud,sesion(concesion)));
    }
    private static SesionPlataforma sesion(UUID concesion){
        var t=SesionActual.sesion().token();return new SesionPlataforma(t.referenciaSesion(),t.revisionContexto(),concesion);
    }
    private static <T> ResponseEntity<T> privado(T cuerpo){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);}
}
