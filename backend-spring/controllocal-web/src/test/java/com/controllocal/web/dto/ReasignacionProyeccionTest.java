package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReasignacionProyeccionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void gobiernoNoRecibeCondicionesPorLaListaDeDestinos() {
        var respuesta = EncargoReasignableResponse.desde(new CaptacionService.EncargoReasignable(
                1L, "CAP-1", "APROBADO", "Dirección del recurso", 2L, "Responsable", 3));
        assertEquals(Set.of("id", "codigoCaptacion", "estado", "direccionLocal", "idAgente", "agenteNombre", "version"),
                campos(respuesta));
    }

    @Test void resultadoDeGobiernoNoSeSerializaComoFichaComercial() {
        var respuesta = ResultadoReasignacionResponse.desde(new CaptacionService.ResultadoReasignacion(
                1L, "CAP-1", "APROBADO", 2L, 3L, 4L, 5));
        assertEquals(Set.of("id", "codigoCaptacion", "estado", "idAgenteAnterior", "idAgente", "idReasignacion", "version"),
                campos(respuesta));
    }

    private Set<String> campos(Object respuesta) {
        Set<String> campos = new HashSet<>();
        json.valueToTree(respuesta).fieldNames().forEachRemaining(campos::add);
        return campos;
    }
}
