package com.controllocal.web.seguridad;

import com.controllocal.service.soporte.Procedencia;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * <b>De donde dice el cliente que viene la peticion</b> (V59).
 *
 * <h2>Por que cabeceras y no un campo en cada cuerpo</h2>
 * Porque la procedencia es <b>transversal</b>: vale igual para registrar una
 * propiedad, agendar una visita o anotar una oferta. Meterla en el DTO de cada
 * operacion significaria tocar mas de cien contratos para anadir siempre lo
 * mismo, y significaria que la operacion que se olvidara de declararla no
 * dejaria rastro sin que nadie lo notara.
 *
 * <h2>El conjunto</h2>
 * <pre>
 *   X-Canal                 SPA · WHATSAPP · API · SISTEMA
 *   X-Agente                que sistema automatico lo pide; ausente = una persona
 *   X-Agente-Modelo         con que modelo razono
 *   X-Agente-Version        y en que version
 *   X-Conversacion          de que conversacion sale
 *   X-Turno                 de que turno exacto
 *   X-Mensaje               el mensaje del canal: el puntero a la evidencia
 *   X-Peticion-B64          lo que la persona escribio o dicto, en base64
 * </pre>
 *
 * <h2>Por que la peticion viaja en base64</h2>
 * Porque una cabecera HTTP es ASCII y una frase de trabajo real trae tildes y
 * enes — <i>"el departamento de la senora Nunez"</i>. Mandarla en claro produce
 * mojibake o un 400, segun el servidor, y las dos formas de fallar son peores
 * que un {@code decode}. Es la misma razon por la que los guiones E2E mandan
 * sus cuerpos JSON codificados.
 *
 * <h2>Esto es una AFIRMACION, no una prueba</h2>
 * Cualquiera puede mandar {@code X-Agente}. Por eso ninguna decision de
 * seguridad cuelga de estas cabeceras: los permisos salen del token y del rol
 * efectivo, como en cualquier otra peticion. Lo que estas cabeceras deciden es
 * <b>que se escribe en el rastro</b>, y una etiqueta mentida produce un rastro
 * pobre — nunca un permiso de mas.
 */
@Component
public class ProcedenciaDeCabeceras {

    public static final String CANAL = "X-Canal";
    /** El nombre historico del canal, anterior a V59. Se sigue aceptando. */
    public static final String CANAL_HEREDADO = "X-Origen";
    public static final String AGENTE = "X-Agente";
    public static final String AGENTE_MODELO = "X-Agente-Modelo";
    public static final String AGENTE_VERSION = "X-Agente-Version";
    public static final String CONVERSACION = "X-Conversacion";
    public static final String TURNO = "X-Turno";
    public static final String MENSAJE = "X-Mensaje";
    public static final String PETICION = "X-Peticion-B64";

    public Procedencia de(HttpServletRequest peticion) {
        if (peticion == null) {
            return Procedencia.deLaPantalla();
        }
        String canal = primero(peticion.getHeader(CANAL), peticion.getHeader(CANAL_HEREDADO));
        String agente = peticion.getHeader(AGENTE);

        if (agente == null || agente.isBlank()) {
            // Sin agente declarado lo pidio una persona directamente, que es el
            // caso de todo el cable actual. No lleva conversacion ni turno, y no
            // es una carencia: una pantalla no tiene turnos.
            return Procedencia.deCabecera(canal);
        }
        return Procedencia.deAgente(canal, agente,
                peticion.getHeader(AGENTE_MODELO), peticion.getHeader(AGENTE_VERSION),
                peticion.getHeader(CONVERSACION), peticion.getHeader(TURNO),
                peticion.getHeader(MENSAJE), textoDe(peticion.getHeader(PETICION)));
    }

    private static String primero(String preferido, String alternativo) {
        return preferido != null && !preferido.isBlank() ? preferido : alternativo;
    }

    /**
     * Una cabecera mal codificada <b>no rompe la operacion</b>: se pierde la
     * frase y se conserva todo lo demas. Fallar aqui significaria que un acento
     * mal empaquetado impide registrar una propiedad, y el rastro esta para
     * ayudar a explicar el trabajo, no para impedirlo.
     */
    private static String textoDe(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(base64.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
