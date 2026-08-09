package com.controllocal.web.controlador;

import com.controllocal.service.Pagina;
import com.controllocal.service.SeguridadService;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>Aviso persistente de gobierno</b> (§11 del diseño de MFA). Aditivo: la v1
 * no tiene nada parecido.
 *
 * <p>Es de <b>solo lectura</b> sobre {@code evento_seguridad}, que es
 * append-only y tiene un unico escritor. Esa es la propiedad que se busca: un
 * aviso que se pudiera atender lo silenciaria, antes que nadie, quien acabara
 * de revocar un factor sin permiso.
 *
 * <p>{@code Cache-Control: no-store}: quien esta en este tablero esta mirando
 * quien tocó accesos, y eso no se queda en la cache del navegador despues de
 * cerrar sesion.
 */
@RestController
@RequestMapping("seguridad")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class SeguridadController {

    private final SeguridadService seguridad;

    public SeguridadController(SeguridadService seguridad) {
        this.seguridad = seguridad;
    }

    @GetMapping("avisos")
    public ResponseEntity<PageResponse<SeguridadService.AvisoDeGobierno>> avisos(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        int paginaValida = Math.max(1, pagina);
        int tamanoValido = Math.max(1, Math.min(100, tamano));
        Pagina<SeguridadService.AvisoDeGobierno> resultado =
                seguridad.avisosDeGobierno(paginaValida, tamanoValido, SesionActual.actor());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PageResponse<>(resultado.items(), resultado.total(),
                        paginaValida, tamanoValido));
    }
}
