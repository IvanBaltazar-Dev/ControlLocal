package com.controllocal.web.dto;

import com.controllocal.service.excepcion.ReglaNegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>La reasignacion de un ENCARGO declara sobre que agente actua, visto desde
 * el JSON</b> (D-P0-9 aplicado al ENCARGO).
 *
 * <p>{@code CausalidadDeLaReasignacionIntegrationTest} demuestra el
 * comportamiento contra PostgreSQL —el 409, la carrera, el rollback y el dirty
 * checking— partiendo de un id ya construido. Este cubre el tramo que aquel no
 * recorre: <b>el cuerpo que manda el cliente</b>.
 *
 * <p>Lo que se protege es que la declaracion <b>no se pueda omitir</b>. Si un
 * cuerpo sin {@code idAgenteActual} se completara con el agente que hubiera en
 * ese instante, la reasignacion volveria a ser un «pon a B» que no sabe de
 * donde parte — y todo cliente que no se enterara del cambio seguiria
 * reasignando como antes, sin que nada lo notara.
 *
 * <p>Es el gemelo de {@code AsignarResponsableRequestTest}, con <b>una</b>
 * diferencia declarada: aqui no hay equivalente a {@code sinResponsableActual}
 * porque {@code captacion.id_rol_agente} es NOT NULL desde V5. Un encargo sin
 * agente no existe, asi que FALTANTE no es un estado que se pueda observar y no
 * hay una segunda forma de declarar el punto de partida.
 */
class ReasignacionRequestTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("declarar el agente que se vio llevando el encargo")
    void declararElAgenteQueSeVio() throws Exception {
        ReasignacionRequest cuerpo = leer("""
                {"idAgenteNuevo": 31, "motivo": "Reparto de cartera del trimestre",
                 "idAgenteActual": 30}
                """);

        assertEquals(31L, cuerpo.destino());
        assertEquals(30L, cuerpo.observado());
    }

    /**
     * <b>El caso que cierra la puerta de atras.</b> El cuerpo anterior a este
     * corte era exactamente este: destino y motivo, sin punto de partida.
     */
    @Test
    @DisplayName("un cuerpo sin idAgenteActual es 400, y NO se completa con el agente de turno")
    void sinDeclaracionEsRechazo() throws Exception {
        ReasignacionRequest sinNada = leer("""
                {"idAgenteNuevo": 31, "motivo": "Reparto de cartera del trimestre"}
                """);

        // El destino si esta, asi que lo que falla es exactamente la
        // declaracion y no otra cosa: sin esta linea, el rechazo de abajo
        // podria venir de un cuerpo invalido por cualquier motivo.
        assertEquals(31L, sinNada.destino());

        ReglaNegocioException fallo = assertThrows(ReglaNegocioException.class,
                sinNada::observado,
                "un cuerpo sin declaracion no dice «me da igual quien lo lleve»: dice que nadie "
                        + "miro, y completarlo con el agente de ese instante seria inventar la "
                        + "observacion");
        assertTrue(fallo.getMessage().contains("idAgenteActual"),
                "y el mensaje tiene que decir COMO se arregla, nombrando el campo. Decia: "
                        + fallo.getMessage());
    }

    @Test
    @DisplayName("un cuerpo sin agente destino conserva el 400 que ya respondia el Core")
    void sinDestinoConservaElMensaje() throws Exception {
        ReasignacionRequest sinDestino = leer("""
                {"motivo": "Reparto de cartera del trimestre", "idAgenteActual": 30}
                """);

        assertEquals("El agente destino es obligatorio.",
                assertThrows(ReglaNegocioException.class, sinDestino::destino).getMessage(),
                "la validacion se mudo del servicio al DTO y el mensaje no puede cambiar por "
                        + "eso: era la unica forma de fallar y esta medida en el cable");
    }

    /**
     * Cero y negativo no son ids: los rechaza igual que la ausencia. Un
     * {@code 0} colandose como «agente observado» seria una declaracion que
     * nunca puede cuadrar con ninguna fila, o sea un 409 permanente sin motivo
     * legible.
     */
    @Test
    @DisplayName("un id que no es un id se rechaza igual que la ausencia")
    void unIdInvalidoNoEsUnaDeclaracion() throws Exception {
        assertThrows(ReglaNegocioException.class,
                () -> leer("""
                        {"idAgenteNuevo": 31, "motivo": "Reparto de cartera del trimestre",
                         "idAgenteActual": 0}
                        """).observado());
        assertThrows(ReglaNegocioException.class,
                () -> leer("""
                        {"idAgenteNuevo": 0, "motivo": "Reparto de cartera del trimestre",
                         "idAgenteActual": 30}
                        """).destino());
    }

    private ReasignacionRequest leer(String cuerpo) throws Exception {
        return json.readValue(cuerpo, ReasignacionRequest.class);
    }
}
