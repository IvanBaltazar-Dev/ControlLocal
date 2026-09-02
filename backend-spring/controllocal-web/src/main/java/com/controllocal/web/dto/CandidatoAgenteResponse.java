package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

/**
 * <b>Un destino ya elegible para reasignar un encargo</b> (D-P0-7 + D-P0-12).
 *
 * <p>Lleva lo justo para elegir en una lista. <b>No lleva estado
 * administrativo</b> y no es un olvido: quien esta aqui cumple las cinco
 * condiciones de D-P0-7, y de quien no esta no se publica el motivo — la
 * suspension de una cuenta ajena no es dato de un selector de reasignacion.
 *
 * <p>Tiene la <b>misma forma</b> que {@code CandidatoResponsableResponse} —el de
 * la propiedad— porque responde la misma pregunta sobre el mismo sujeto. Son
 * dos records y no uno para no atar el cable de dos recursos distintos a la
 * misma clase: el dia que uno de los dos publique un campo mas, no arrastra al
 * otro.
 */
public record CandidatoAgenteResponse(Long idAgente, String nombre, String codigoAgente,
                                      String zonaAsignada) {

    public static CandidatoAgenteResponse desde(CaptacionService.CandidatoAgente c) {
        return new CandidatoAgenteResponse(c.idAgente(), c.nombre(), c.codigoAgente(),
                c.zonaAsignada());
    }
}
