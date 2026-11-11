package com.controllocal.web.controlador;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.mercado.InvestigacionesMercado;
import com.controllocal.service.mercado.DatosInvestigacionMercado.*;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Puertas compartidas Web/KAIROS; contexto y cálculo pertenecen íntegramente a Core. */
@RestController
@RequestMapping("mercado")
public class MercadoController {
    private final InvestigacionesMercado mercado;
    public MercadoController(InvestigacionesMercado mercado){this.mercado=mercado;}
    public record ConsultaReferencia(String resultado,ReferenciaOrientativa referencia) { }
    @PostMapping("investigaciones/preparar") public ResponseEntity<Propuesta> preparar(@RequestBody Preparar d){return privado(mercado.preparar(d,SesionActual.actor(),sesion()));}
    @PostMapping("investigaciones") public ResponseEntity<EstadoTrabajo> solicitar(@RequestBody Aceptacion d){return privado(mercado.solicitar(d,SesionActual.actor(),sesion()));}
    @GetMapping("investigaciones/{solicitud}") public ResponseEntity<EstadoTrabajo> consultar(@PathVariable UUID solicitud){return privado(mercado.consultar(solicitud,SesionActual.actor(),sesion()));}
    @GetMapping("investigaciones/{solicitud}/resultado") public ResponseEntity<ResultadoPrivado> resultado(@PathVariable UUID solicitud){return privado(mercado.resultado(solicitud,SesionActual.actor(),sesion()));}
    @PostMapping("investigaciones/{solicitud}/retiro") public ResponseEntity<EstadoTrabajo> retirar(@PathVariable UUID solicitud,@RequestBody Retiro d){return privado(mercado.retirarNoIniciada(solicitud,d,SesionActual.actor(),sesion()));}
    @GetMapping("referencias/{politica}/{version}") public ResponseEntity<ConsultaReferencia> referencia(@PathVariable UUID politica,@PathVariable String version){
        var r=mercado.referencia(politica,version,SesionActual.actor(),sesion());return privado(new ConsultaReferencia(r.isPresent()?r.get().estado():"SIN_BASE_VIGENTE",r.orElse(null)));
    }
    private static ContextoSesion sesion(){return ContextoSesion.de(SesionActual.sesion().acceso());}
    private static <T> ResponseEntity<T> privado(T cuerpo){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);}
}
