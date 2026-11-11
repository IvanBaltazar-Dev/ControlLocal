package com.controllocal.web.controlador;

import com.controllocal.service.CuentaService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.dto.ContrasenaDtos.CambioContrasenaRequest;
import com.controllocal.web.dto.CuentaDtos.CambioCorreoRequest;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;

/** Cuenta personal: disponible con identidad verificada aunque no haya pertenencia activa. */
@RestController
@RequestMapping("cuenta")
public class CuentaController {
    private final CuentaService cuentas;
    public CuentaController(CuentaService cuentas) {this.cuentas=cuentas;}
    @GetMapping
    public ResponseEntity<CuentaService.FichaCuenta> propia() {
        var t=SesionActual.sesion().token();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuentas.consultarPropia(t.referenciaSesion(),t.revisionContexto()));
    }
    @PostMapping("contrasena")
    public ResponseEntity<Void> cambiarContrasena(@RequestBody(required=false) CambioContrasenaRequest dto) {
        if(dto==null) throw new ReglaNegocioException("La contrasena actual es obligatoria.");
        var t=SesionActual.sesion().token();
        char[] actual=caracteres(dto.contrasenaActual()),nueva=caracteres(dto.contrasenaNueva());
        try {
            cuentas.cambiarContrasena(t.referenciaSesion(),t.revisionContexto(),actual,nueva);
            return ResponseEntity.noContent().build();
        } finally {Arrays.fill(actual,'\0');Arrays.fill(nueva,'\0');}
    }
    private static char[] caracteres(String v) {return v==null ? new char[0] : v.toCharArray();}

    @PostMapping("correo")
    public ResponseEntity<Void> solicitarCambioCorreo(@RequestBody(required=false) CambioCorreoRequest dto) {
        if(dto==null) throw new ReglaNegocioException("El correo nuevo y la prueba propia son obligatorios.");
        var t=SesionActual.sesion().token();char[] p=caracteres(dto.contrasenaActual());
        try {
            cuentas.solicitarCambioCorreo(t.referenciaSesion(),t.revisionContexto(),dto.correoNuevo(),p,dto.codigoMfa());
            return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
        } finally {Arrays.fill(p,'\0');}
    }
}
