package com.controllocal.web.controlador;

import com.controllocal.service.InvitacionesMembresiaService;
import com.controllocal.service.MembresiasService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** CAT-E01/AC-024–025: gobierno organizacional y aceptación por el titular, sin credenciales ajenas. */
@RestController
@RequestMapping("membresias")
public class MembresiasController {
    private final InvitacionesMembresiaService invitaciones;
    private final MembresiasService membresias;
    public MembresiasController(InvitacionesMembresiaService invitaciones,MembresiasService membresias) {
        this.invitaciones=invitaciones; this.membresias=membresias;
    }
    public record Retiro(String motivo) { }
    public record Baja(Long version,String motivo) { }

    @PostMapping("invitaciones")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<InvitacionesMembresiaService.InvitacionEmitida> emitir(
            @RequestBody(required=false) InvitacionesMembresiaService.DatosInvitacion datos,
            @RequestHeader(value="Idempotency-Key",required=false) String claveComando) {
        return privada(invitaciones.emitir(datos,SesionActual.actor(),ContextoSesion.de(SesionActual.sesion().acceso()),claveComando));
    }
    @PostMapping("invitaciones/{referencia}/aceptar")
    public ResponseEntity<InvitacionesMembresiaService.PertenenciaAceptada> aceptar(@PathVariable String referencia) {
        var t=SesionActual.sesion().token();
        return privada(invitaciones.aceptar(referencia,t.referenciaSesion(),t.revisionContexto()));
    }
    @PostMapping("invitaciones/{referencia}/retirar")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<InvitacionesMembresiaService.InvitacionRetirada> retirar(
            @PathVariable String referencia,@RequestBody(required=false) Retiro datos) {
        return privada(invitaciones.retirar(referencia,datos==null?null:datos.motivo(),SesionActual.actor(),
                ContextoSesion.de(SesionActual.sesion().acceso())));
    }
    @PostMapping("{idMembresia}/baja")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<MembresiasService.BajaFormal> darBaja(@PathVariable long idMembresia,
            @RequestBody(required=false) Baja datos) {
        return privada(membresias.darBaja(idMembresia,datos==null||datos.version()==null?-1:datos.version(),
                datos==null?null:datos.motivo(),SesionActual.actor(),ContextoSesion.de(SesionActual.sesion().acceso())));
    }
    private static <T> ResponseEntity<T> privada(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
