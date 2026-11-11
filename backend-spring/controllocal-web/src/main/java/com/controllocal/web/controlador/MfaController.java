package com.controllocal.web.controlador;

import com.controllocal.service.CuentaService;
import com.controllocal.web.dto.MfaDtos.*;
import com.controllocal.web.seguridad.IpDelCliente;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;

/** Factor de la cuenta propia; no depende de una pertenencia ni acepta identidad ajena. */
@RestController
@RequestMapping("cuenta")
public class MfaController {
    private final CuentaService cuentas;
    private final IpDelCliente ipDelCliente;
    public MfaController(CuentaService cuentas,IpDelCliente ipDelCliente) {this.cuentas=cuentas;this.ipDelCliente=ipDelCliente;}
    @GetMapping("mfa")
    public EstadoMfaResponse estado() {
        var t=SesionActual.sesion().token();
        return EstadoMfaResponse.desde(cuentas.estadoMfa(t.referenciaSesion(),t.revisionContexto()));
    }
    @PostMapping("mfa")
    public ResponseEntity<EnrolamientoResponse> iniciar() {
        var t=SesionActual.sesion().token();var e=cuentas.iniciarMfa(t.referenciaSesion(),t.revisionContexto());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EnrolamientoResponse(e.secretoBase32(),e.uri()));
    }
    @PostMapping("mfa/confirmar")
    public ResponseEntity<CodigosResponse> confirmar(@RequestBody(required=false) CodigoRequest dto) {
        var t=SesionActual.sesion().token();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CodigosResponse(
                cuentas.confirmarMfa(t.referenciaSesion(),t.revisionContexto(),dto==null ? null : dto.codigo())));
    }
    @PostMapping("mfa/codigos")
    public ResponseEntity<CodigosResponse> regenerar(@RequestBody(required=false) ReautenticacionRequest dto) {
        var t=SesionActual.sesion().token();char[] p=contrasena(dto);
        try {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CodigosResponse(
                cuentas.regenerarCodigosMfa(t.referenciaSesion(),t.revisionContexto(),p,dto==null ? null : dto.codigo())));}
        finally {Arrays.fill(p,'\0');}
    }
    @DeleteMapping("mfa")
    public ResponseEntity<Void> revocar(@RequestBody(required=false) ReautenticacionRequest dto,HttpServletRequest request) {
        var t=SesionActual.sesion().token();char[] p=contrasena(dto);
        try {
            cuentas.revocarMfaPropio(t.referenciaSesion(),t.revisionContexto(),p,dto==null ? null : dto.codigo(),
                    ipDelCliente.de(request),request.getHeader("User-Agent"));
            return ResponseEntity.noContent().build();
        } finally {Arrays.fill(p,'\0');}
    }
    private static char[] contrasena(ReautenticacionRequest dto) {return dto==null || dto.contrasena()==null ? new char[0] : dto.contrasena().toCharArray();}
}
