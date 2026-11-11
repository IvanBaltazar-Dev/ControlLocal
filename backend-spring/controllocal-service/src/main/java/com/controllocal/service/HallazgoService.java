package com.controllocal.service;

import java.util.List;

/** D1–D4: aptitudes por Demanda/version; concentración de broker conserva su contrato propio. */
public interface HallazgoService {
    String COINCIDENCIA_DE_CARTERA="COINCIDENCIA_DE_CARTERA";
    String CONCENTRACION_DEL_EQUIPO="CONCENTRACION_DEL_EQUIPO";
    /** aptitud es null en concentración; nunca convierte esa métrica en un puntaje de matching. */
    record Hallazgo(String id,String tipo,String titulo,String porQue,CoincidenciaService.Coincidencia aptitud,
                    String destino,Long idDemanda,Long idCaptacion,String codigoCaptacion) { }
    /** Lectura actual y orden estable de referencias; no compara S entre demandas distintas. */
    List<Hallazgo> de(Actor actor);
}