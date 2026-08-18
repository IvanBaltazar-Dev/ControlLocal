package com.kairos.conversacion;

import com.kairos.brox.SesionBrox;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>Un turno de conversacion.</b>
 *
 * <h2>Este endpoint es de KAIROS, no de BROX</h2>
 * Y esa es la diferencia con lo que habia antes. Un
 * {@code POST /kairos/turnos} dentro de BROX habria sido un endpoint de canal
 * dentro del dominio: la puerta por la que KAIROS crece hacia adentro hasta que
 * apagarlo deja de ser posible.
 *
 * <p>Aqui, en cambio, BROX no sabe que esto existe. Si este servicio se apaga,
 * BROX sigue registrando propiedades, agendando visitas y cerrando operaciones
 * — y hay un gate en su build que lo comprueba.
 *
 * <h2>Quien llama</h2>
 * Hoy, la integracion de WhatsApp: normaliza el webhook, resuelve tenant y
 * persona <b>tecnicamente</b> —nunca preguntandoselo al modelo— y manda el
 * turno con el token de esa persona. Manana, cualquier otro canal
 * conversacional, sin que este codigo cambie.
 */
@RestController
@RequestMapping("kairos")
public class KairosController {

    private final Kairos kairos;

    public KairosController(Kairos kairos) {
        this.kairos = kairos;
    }

    /**
     * @param conversacionId obligatorio: sin el, lo que se escriba en BROX
     *                       queda sin poder explicarse
     * @param mensajeId      el identificador del mensaje del canal. Es la clave
     *                       de idempotencia: un webhook reenviado trae el mismo
     * @param confirmado     la persona dijo que si a lo propuesto
     */
    public record TurnoRequest(String conversacionId, String turnoId, String mensajeId,
                               String texto, Long idBorrador, Boolean confirmado) {
    }

    /**
     * El token de la persona viaja tal cual hasta BROX.
     *
     * <p>KAIROS no lo interpreta ni decide nada con el: no sabe que rol tiene
     * ni a que organizacion pertenece hasta que BROX se lo dice. Un asistente
     * que resolviera permisos por su cuenta seria una segunda politica de
     * autorizacion, y la segunda es la que nadie audita.
     */
    @PostMapping("turnos")
    public Kairos.Respuesta turno(@RequestBody TurnoRequest dto,
                                  @RequestHeader("Authorization") String autorizacion) {
        String token = autorizacion == null ? null : autorizacion.replaceFirst("(?i)^Bearer ", "");
        return kairos.turno(new Kairos.Turno(
                dto == null ? null : dto.conversacionId(),
                dto == null ? null : dto.turnoId(),
                dto == null ? null : dto.mensajeId(),
                dto == null ? null : dto.texto(),
                dto == null ? null : dto.idBorrador(),
                dto != null && Boolean.TRUE.equals(dto.confirmado())),
                new SesionBrox(token, null));
    }
}
