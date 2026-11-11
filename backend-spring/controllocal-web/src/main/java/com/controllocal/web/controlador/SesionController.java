package com.controllocal.web.controlador;

import com.controllocal.service.ContextosService;
import com.controllocal.web.dto.LoginResponse;
import com.controllocal.web.dto.SesionResponse;
import com.controllocal.web.seguridad.EmisionDeSesion;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Superficie segura de contexto; no recibe organización o rol desde un diálogo. */
@RestController
@RequestMapping("sesion")
public class SesionController {
    private final ContextosService contextos;
    private final EmisionDeSesion emision;
    public SesionController(ContextosService contextos, EmisionDeSesion emision) {
        this.contextos = contextos; this.emision = emision;
    }
    public record Seleccion(String referencia) { }

    @GetMapping
    public SesionResponse actual() {
        var p = SesionActual.sesion();
        var t = p.token();
        var c = p.acceso().activo();
        return new SesionResponse(p.rolEfectivo(), t.usuario(), t.idUsuario(), t.idDominio(),
                p.idOrganizacion(), p.idCuenta(), t.referenciaSesion(), t.revisionContexto(),
                c == null ? null : c.referencia(), c == null ? null : c.modalidad(), c == null,
                p.acceso().restricciones().debeCambiarContrasena(),p.acceso().restricciones().debeEnrolarMfa());
    }
    @GetMapping("contextos")
    public ResponseEntity<List<ContextosService.Contexto>> contextos() {
        var t = SesionActual.sesion().token();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(contextos.contextos(t.referenciaSesion(), t.revisionContexto()));
    }
    @PostMapping("contexto")
    public ResponseEntity<LoginResponse> seleccionar(@RequestBody(required = false) Seleccion dto) {
        var t = SesionActual.sesion().token();
        var s = contextos.seleccionar(t.referenciaSesion(), t.revisionContexto(), dto == null ? null : dto.referencia());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(emision.emitir(s));
    }
}
