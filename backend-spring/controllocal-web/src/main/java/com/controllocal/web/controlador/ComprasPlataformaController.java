package com.controllocal.web.controlador;

import com.controllocal.service.planes.ComprasManualesB0;
import com.controllocal.service.planes.DatosCompraManual.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Identidad global y concesión R25; nunca crea un Actor de organización artificial. */
@RestController
@RequestMapping("plataforma")
public class ComprasPlataformaController {
    private final ComprasManualesB0 compras;private final AtribucionesPlataforma atribuciones;
    public ComprasPlataformaController(ComprasManualesB0 compras,AtribucionesPlataforma atribuciones){this.compras=compras;this.atribuciones=atribuciones;}
    public record Inicio(Confirmacion comando,PagoInformado pago){}
    public record FondosVerificados(Confirmacion comando,Verificacion fondos){}
    public record Incidencia(Confirmacion comando,String motivo){}
    @GetMapping("atribuciones/compras") public ResponseEntity<List<OpcionCompra>> atribuciones(){
        var t=SesionActual.sesion().token();return privado(atribuciones.atribucionesCompra(t.referenciaSesion(),t.revisionContexto()));
    }
    @GetMapping("compras") public ResponseEntity<List<Compra>> listar(@RequestHeader("X-Brox-Concesion") UUID concesion,
            @RequestParam(defaultValue="20") int limite,@RequestParam(defaultValue="0") int desplazamiento){return privado(compras.listarFinanciero(sesion(concesion),limite,desplazamiento));}
    @GetMapping("compras/{pedido}") public ResponseEntity<DetalleFinanciero> consultar(@PathVariable UUID pedido,@RequestHeader("X-Brox-Concesion") UUID concesion){return privado(compras.consultarFinanciero(sesion(concesion),pedido));}
    @PostMapping("compras/{pedido}/verificacion") public ResponseEntity<DetalleFinanciero> iniciar(@PathVariable UUID pedido,@RequestHeader("X-Brox-Concesion") UUID concesion,@RequestBody Inicio d){
        return privado(compras.iniciarVerificacion(sesion(concesion),pedido,d.comando(),d.pago()));
    }
    @PostMapping("compras/{pedido}/fondos") public ResponseEntity<DetalleFinanciero> verificar(@PathVariable UUID pedido,@RequestHeader("X-Brox-Concesion") UUID concesion,@RequestBody FondosVerificados d){
        var s=sesion(concesion);compras.verificarFondos(s,pedido,d.comando(),d.fondos());return privado(acreditar(s,pedido));
    }
    @PostMapping("compras/{pedido}/acreditacion") public ResponseEntity<DetalleFinanciero> reintentar(@PathVariable UUID pedido,@RequestHeader("X-Brox-Concesion") UUID concesion){return privado(acreditar(sesion(concesion),pedido));}
    @PostMapping("compras/{pedido}/incidencia") public ResponseEntity<DetalleFinanciero> incidencia(@PathVariable UUID pedido,@RequestHeader("X-Brox-Concesion") UUID concesion,@RequestBody Incidencia d){return privado(compras.incidencia(sesion(concesion),pedido,d.comando(),d.motivo()));}
    private DetalleFinanciero acreditar(SesionPlataforma s,UUID pedido){
        try{return compras.acreditar(s,pedido);}
        catch(com.controllocal.service.excepcion.ConflictoException|com.controllocal.service.excepcion.ReglaNegocioException|com.controllocal.service.excepcion.CapacidadDeshabilitadaException e){return compras.activacionPendiente(s,pedido);}
        // Un fallo técnico no se convierte en éxito. Los fondos ya fueron confirmados en otra TX.
    }
    private static SesionPlataforma sesion(UUID concesion){var t=SesionActual.sesion().token();return new SesionPlataforma(t.referenciaSesion(),t.revisionContexto(),concesion);}
    private static <T> ResponseEntity<T> privado(T cuerpo){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);}
}
