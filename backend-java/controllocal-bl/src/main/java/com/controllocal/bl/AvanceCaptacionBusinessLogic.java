package com.controllocal.bl;

import java.time.LocalDate;

public interface AvanceCaptacionBusinessLogic {

    /**
     * Resumen de avance de una captacion en el rango [desde, hasta] (limites inclusive; null deja
     * ese extremo abierto). Deriva de la actividad real lo que el agente reporta al propietario:
     * consultas (interacciones), visitas realizadas y objeciones (motivos de no continuidad). Es
     * una lectura sin efectos secundarios.
     */
    ResumenAvance resumen(Long idCaptacion, LocalDate desde, LocalDate hasta);

    record ResumenAvance(int consultas, int visitas, String objeciones) {
    }
}
