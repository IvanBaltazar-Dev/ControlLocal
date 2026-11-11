package com.controllocal.web.controlador;

import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.seguridad.InicioSeguroService;
import com.controllocal.service.seguridad.InicioSeguroService.Proposito;
import com.controllocal.service.seguridad.InicioSeguroService.SesionVerificada;
import com.controllocal.service.soporte.BloqueoAccesos;
import com.controllocal.web.dto.AccesoSeguroDtos.*;
import com.controllocal.web.http.DemasiadasSolicitudesException;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.IpDelCliente;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.web.seguridad.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

/**
 * UC-K-16: bootstrap de acceso sin identidad ficticia, sin conversación y sin
 * proveedor IA. Los secretos se introducen directamente en las operaciones de
 * autenticación reconocidas. Ni el flujo ni su retorno conceden negocio.
 */
@RestController
@RequestMapping("acceso")
public class AccesoSeguroController {
    private static final String ENTRADA_ACCESO = "auth/mfa/desafio";
    private final InicioSeguroService inicio;
    private final BloqueoAccesos bloqueo;
    private final IpDelCliente ipDelCliente;

    public AccesoSeguroController(InicioSeguroService inicio, BloqueoAccesos bloqueo, IpDelCliente ipDelCliente) {
        this.inicio = inicio;
        this.bloqueo = bloqueo;
        this.ipDelCliente = ipDelCliente;
    }

    @GetMapping("protocolo")
    public ResponseEntity<Protocolo> protocolo() {
        return privada(new Protocolo(1, List.of(Proposito.ACCESO.name()), List.of(
                new DestinoSeguro("ACCESO", "POST", ENTRADA_ACCESO),
                new DestinoSeguro("SEGUNDO_FACTOR", "POST", "auth/mfa/verificar"),
                new DestinoSeguro("RECUPERACION", "POST", "auth/recuperacion"),
                new DestinoSeguro("CANJE_RECUPERACION", "POST", "auth/recuperacion/canje"),
                new DestinoSeguro("PRIVACIDAD", "GET", "aviso-privacidad"))));
    }

    @PostMapping("flujos")
    public ResponseEntity<VinculoResponse> iniciar(@RequestBody(required = false) IniciarRequest dto,
                                                  HttpServletRequest request) {
        String ip = ipDelCliente.de(request);
        var veredicto = bloqueo.permitir(null, ip);
        if (veredicto.bloqueado()) {
            throw new DemasiadasSolicitudesException(BloqueoAccesos.esperaSegundos(veredicto.fallos()));
        }
        // Igual que recuperación: cada emisión consume el cupo IP, incluso
        // válida. No se supone cuenta ni organización para crear un vínculo.
        bloqueo.registrarSoloIp(ip, false, null, request.getHeader("User-Agent"));
        var vinculo = inicio.iniciar(proposito(dto == null ? null : dto.proposito()),
                Instant.now().plusSeconds(TokenService.DURACION_SEGUNDOS));
        return privada(new VinculoResponse(vinculo.flujoRef(), vinculo.proposito().name(),
                vinculo.venceEn(), ENTRADA_ACCESO));
    }

    @PostMapping("flujos/{flujoRef}/completar")
    public ResponseEntity<RetornoPreparadoResponse> completar(@PathVariable String flujoRef,
            @RequestBody(required = false) CompletarRequest dto) {
        var retorno = inicio.completar(flujoRef, proposito(dto == null ? null : dto.proposito()), sesionVerificada());
        return privada(new RetornoPreparadoResponse(retorno.flujoRef(), retorno.proposito().name(),
                retorno.retornoRef(), retorno.venceEn()));
    }

    @PostMapping("flujos/{flujoRef}/retorno")
    public ResponseEntity<RetornoResponse> consumir(@PathVariable String flujoRef,
            @RequestBody(required = false) RetornoRequest dto) {
        var retorno = inicio.consumir(flujoRef, proposito(dto == null ? null : dto.proposito()),
                dto == null ? null : dto.retornoRef(), sesionVerificada());
        return privada(new RetornoResponse(retorno.flujoRef(), retorno.proposito().name(), retorno.estado()));
    }

    private static SesionVerificada sesionVerificada() {
        var sesion = SesionActual.sesion();
        var contexto = ContextoDeActuacion.de(sesion);
        return new SesionVerificada(SesionActual.actor(), contexto.identidad(),
                sesion.token().emitidoEn(), sesion.token().expiraEn(),
                sesion.token().referenciaSesion(), sesion.token().revisionContexto());
    }

    private static Proposito proposito(String valor) {
        if (!Proposito.ACCESO.name().equals(valor)) {
            throw new ReglaNegocioException("El propósito no pertenece al contrato de inicio seguro.");
        }
        return Proposito.ACCESO;
    }

    private static <T> ResponseEntity<T> privada(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
