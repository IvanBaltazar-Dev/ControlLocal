package com.controllocal.web.dto;

import com.controllocal.service.PropiedadUniversalService.ResponsableObservado;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.dto.PropiedadUniversalDtos.AsignarResponsableRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El comando declara sobre que responsable actua, visto desde el JSON</b>
 * (D-P0-9).
 *
 * <p>{@code CausalidadDelTraspasoIntegrationTest} demuestra el comportamiento
 * contra PostgreSQL —el 409, la carrera y el rollback— partiendo de un
 * {@link ResponsableObservado} ya construido. Este cubre el tramo que aquel no
 * recorre: <b>el cuerpo que manda el cliente</b>, y la traduccion de ese cuerpo
 * a la declaracion del Core.
 *
 * <p>Lo que se protege aqui es que la declaracion <b>no se pueda omitir</b>. Si
 * un cuerpo sin ninguna de las dos llaves se interpretara como «estaba
 * FALTANTE», el traspaso volveria a partir de un estado que nadie miro —y sobre
 * una propiedad con responsable ese comando entraria o no segun el azar de
 * quien escribiera antes—. Un cuerpo con las dos se contradice, y elegir la mas
 * probable seria inventar la observacion.
 *
 * <p>No hay {@code *ControllerTest} con MockMvc en este modulo: la validacion
 * vive en el propio DTO precisamente para que sea la misma por cualquier canal
 * —BROX Web y KAIROS entran por el mismo endpoint con el mismo cuerpo—, y aqui
 * se ataca donde vive.
 */
class AsignarResponsableRequestTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("declarar el responsable que se vio en la ficha")
    void declararElResponsableQueSeVio() throws Exception {
        ResponsableObservado observado = leer("""
                {"idAgente": 41, "motivo": "Reparto de cartera del sur",
                 "idResponsableActual": 30}
                """).observado();

        assertEquals(30L, observado.idRol());
        assertTrue(!observado.esFaltante(),
                "un responsable declarado no es FALTANTE");
    }

    @Test
    @DisplayName("declarar que la propiedad estaba FALTANTE")
    void declararQueEstabaFaltante() throws Exception {
        ResponsableObservado observado = leer("""
                {"idAgente": 41, "motivo": "Sale del inventario sin dueno",
                 "sinResponsableActual": true}
                """).observado();

        assertNull(observado.idRol());
        assertTrue(observado.esFaltante(),
                "«la vi sin responsable» es un hecho observado, no la ausencia de un dato");
    }

    /**
     * <b>El caso que cierra la puerta de atras.</b> Sin este rechazo, todo
     * cliente que no se enterara del cambio seguiria traspasando como antes —y
     * el comando volveria a ser «pon a B», que es la ultima escritura ganando.
     */
    @Test
    @DisplayName("un cuerpo que no declara nada es 400, y NO se lee como FALTANTE")
    void sinDeclaracionEsRechazo() throws Exception {
        AsignarResponsableRequest sinNada = leer("""
                {"idAgente": 41, "motivo": "Reparto de cartera del sur"}
                """);

        ReglaNegocioException fallo = assertThrows(ReglaNegocioException.class,
                sinNada::observado,
                "un cuerpo sin declaracion no dice «estaba FALTANTE»: dice que nadie miro");
        assertTrue(fallo.getMessage().contains("idResponsableActual")
                        && fallo.getMessage().contains("sinResponsableActual"),
                "y el mensaje tiene que decir COMO se arregla, nombrando las dos formas de "
                        + "declararlo. Decia: " + fallo.getMessage());

        // Y `sinResponsableActual: false` tampoco es una declaracion: dice «no
        // estaba FALTANTE» sin decir quien respondia, que no sirve para partir
        // de ningun sitio.
        assertThrows(ReglaNegocioException.class,
                () -> leer("""
                        {"idAgente": 41, "motivo": "Reparto de cartera del sur",
                         "sinResponsableActual": false}
                        """).observado(),
                "negar el FALTANTE no dice de quien se parte");
    }

    @Test
    @DisplayName("un cuerpo que declara las dos cosas se contradice, y no se elige la mas probable")
    void lasDosDeclaracionesALaVezEsRechazo() throws Exception {
        AsignarResponsableRequest ambas = leer("""
                {"idAgente": 41, "motivo": "Reparto de cartera del sur",
                 "idResponsableActual": 30, "sinResponsableActual": true}
                """);

        ReglaNegocioException fallo = assertThrows(ReglaNegocioException.class, ambas::observado,
                "«respondia el 30» y «no respondia nadie» no pueden ser las dos verdad; quedarse "
                        + "con una seria inventar lo que el actor vio");
        assertTrue(fallo.getMessage().contains("30"),
                "el mensaje nombra la contradiccion concreta: " + fallo.getMessage());
    }

    private AsignarResponsableRequest leer(String cuerpo) throws Exception {
        return json.readValue(cuerpo, AsignarResponsableRequest.class);
    }
}
