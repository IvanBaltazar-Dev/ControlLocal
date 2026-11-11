package com.controllocal.web.controlador;

import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.planes.ComprasManualesB0;
import com.controllocal.service.planes.DatosCompraManual.*;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Superficie de compra Web. Core prepara el enlace; no envía mensajes o confirma pagos. */
@RestController
@RequestMapping("compras")
public class ComprasController {
    private final ComprasManualesB0 compras;
    public ComprasController(ComprasManualesB0 compras){this.compras=compras;}
    public record Preparacion(UUID referencia,Seleccion producto){}
    @GetMapping("catalogo") public ResponseEntity<List<Producto>> catalogo(){return privado(compras.catalogo(SesionActual.actor(),sesion()));}
    @GetMapping public ResponseEntity<List<Compra>> listar(@RequestParam(defaultValue="20") int limite,@RequestParam(defaultValue="0") int desplazamiento){
        return privado(compras.listar(SesionActual.actor(),sesion(),limite,desplazamiento));
    }
    @GetMapping("{pedido}") public ResponseEntity<Compra> consultar(@PathVariable UUID pedido){return privado(compras.consultar(SesionActual.actor(),sesion(),pedido));}
    @PostMapping public ResponseEntity<Compra> preparar(@RequestBody Preparacion d){return privado(compras.preparar(SesionActual.actor(),sesion(),d.referencia(),d.producto()));}
    @PostMapping("{pedido}/consentimiento") public ResponseEntity<Apertura> consentir(@PathVariable UUID pedido,@RequestBody Confirmacion d){return privado(compras.consentir(SesionActual.actor(),sesion(),pedido,d));}
    private static ContextoSesion sesion(){return ContextoSesion.de(SesionActual.sesion().acceso());}
    private static <T> ResponseEntity<T> privado(T cuerpo){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);}
}
