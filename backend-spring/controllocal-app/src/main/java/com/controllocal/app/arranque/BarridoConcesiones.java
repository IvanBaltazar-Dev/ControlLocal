package com.controllocal.app.arranque;

import com.controllocal.service.RecuperacionEmergenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cierra las concesiones vencidas y las que ya no hacen falta.
 *
 * <p><b>No es el control de caducidad, es el que ordena el tablero.</b> La
 * vigencia se comprueba en <i>cada</i> uso, dentro del {@code UPDATE}
 * condicional que consume capacidad: una concesion caduca aunque este barrido
 * no llegue a ejecutarse nunca. Lo que hace esto es que una concesion muerta
 * deje de figurar como viva — porque un tablero que dice VIGENTE de algo que ya
 * no lo esta es peor que no tener tablero.
 *
 * <p>Solo existe con la recuperacion habilitada: sin ella no hay nada que
 * barrer.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "controllocal.recuperacion.habilitada", havingValue = "true")
public class BarridoConcesiones {

    private static final Logger log = LoggerFactory.getLogger(BarridoConcesiones.class);

    private final RecuperacionEmergenciaService recuperacion;

    public BarridoConcesiones(RecuperacionEmergenciaService recuperacion) {
        this.recuperacion = recuperacion;
    }

    @Scheduled(fixedDelayString = "${controllocal.recuperacion.barrido-ms:60000}")
    public void barrer() {
        try {
            int cerradas = recuperacion.caducarVencidas();
            if (cerradas > 0) {
                log.info("[recuperacion] {} concesion(es) cerradas por el barrido", cerradas);
            }
        } catch (RuntimeException error) {
            // Que el barrido falle no puede tumbar el planificador: la
            // caducidad real ya la garantiza cada uso.
            log.warn("[recuperacion] el barrido de concesiones fallo", error);
        }
    }
}
