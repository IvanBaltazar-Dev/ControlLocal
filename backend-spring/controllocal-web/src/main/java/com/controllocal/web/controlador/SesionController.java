package com.controllocal.web.controlador;

import com.controllocal.web.dto.SesionResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quien es el actor de esta sesion <b>segun el servidor</b>.
 *
 * <p>Aditivo (Bloque 5, R3): no existe en la v1 y no toca
 * {@code LoginResponse}, que esta congelado byte a byte. Hace falta porque el
 * dato mas importante para el SPA —la banda real— <b>no cabe en el token</b>:
 * el formato solo admite {@code AGENTE|BROKER|ADMIN} mientras GlassFish
 * conviva (R1), y {@code ADMIN} es la banda heredada que el Bloque 5 retira.
 * Sin este endpoint, el SPA tendria que adivinar el gobierno leyendo el token,
 * que es exactamente lo que no debe hacer.
 *
 * <p>Se consulta despues de entrar y al recargar. No lleva gate de rol: cada
 * quien puede preguntar por si mismo, y la respuesta se construye desde la
 * sesion, no desde parametros — no hay forma de preguntar por otro.
 */
@RestController
@RequestMapping("sesion")
public class SesionController {

    @GetMapping
    public SesionResponse actual() {
        var sesion = SesionActual.sesion();
        return new SesionResponse(
                sesion.rolEfectivo(),
                sesion.token().usuario(),
                sesion.token().idUsuario(),
                sesion.token().idDominio());
    }
}
