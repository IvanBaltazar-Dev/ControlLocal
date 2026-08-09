package com.controllocal.web.controlador;

import com.controllocal.service.soporte.Autorizaciones;
import com.controllocal.web.dto.AvisoPrivacidadResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aviso de privacidad vigente (D-27 §6 bis). Es <b>publico a proposito</b>: el
 * titular de los datos tiene que poder leerlo <b>sin tener cuenta</b>, y el
 * enlace del formulario de alta apunta aqui.
 *
 * <p>Publica exactamente el texto que se guarda como evidencia al registrar una
 * autorizacion: lo que la persona lee es lo que queda citado en su evento.
 *
 * <p>A diferencia de {@code GET /documentos/contenido} —publico por una
 * restriccion del Blazor y con deuda de seguridad abierta—, este endpoint
 * <b>si</b> cumple las condiciones de una ruta publica: no recibe parametros,
 * no expone datos personales y su respuesta es la misma para todo el mundo.
 */
@RestController
public class AvisoPrivacidadController {

    private final Autorizaciones autorizaciones;

    public AvisoPrivacidadController(Autorizaciones autorizaciones) {
        this.autorizaciones = autorizaciones;
    }

    @GetMapping("/aviso-privacidad")
    public AvisoPrivacidadResponse vigente() {
        // avisoParaPublicar() devuelve un record del service, no la entidad:
        // la web no ve dominio (regla de capas, verificada por ArchUnit).
        var aviso = autorizaciones.avisoParaPublicar();
        return new AvisoPrivacidadResponse(aviso.version(), aviso.vigenteDesde(),
                aviso.cambioMaterial(), aviso.contenido());
    }
}
