package com.controllocal.app.arranque;

import com.controllocal.app.ControlLocalApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Escenario A8 del Plan S0, en su forma mas literal: <b>el contexto productivo
 * no arranca</b> si falta configuracion obligatoria.
 * <p>
 * No necesita base de datos: en perfil {@code prod} las propiedades criticas se
 * declaran <b>sin valor por defecto</b> ({@code ${DB_URL}}, {@code ${API_TOKEN_SECRET}},
 * {@code ${CORS_ORIGENES}}, {@code ${ALMACEN_DIR}}), asi que la resolucion de
 * marcadores falla antes de abrir una sola conexion. Ese es justamente el
 * comportamiento buscado: fallar pronto y nombrando la variable.
 * <p>
 * La contraparte —arrancar con todas las variables presentes pero con valores
 * <i>inseguros</i>— la cubren los 15 tests de
 * {@link ValidadorConfiguracionSeguridadTest} y, de punta a punta, la suite
 * {@code e2e-s0-seguridad.ps1} cuando exista.
 */
class ArranqueProduccionTest {

    private SpringApplicationBuilder aplicacion(String... propiedades) {
        return new SpringApplicationBuilder(ControlLocalApplication.class)
                .web(WebApplicationType.NONE)   // no toca el 8090 del contenedor
                .profiles("prod")
                .properties(propiedades);
    }

    @Test
    @DisplayName("el perfil prod NO arranca sin las variables obligatorias, y el mensaje las nombra TODAS")
    void produccionSinConfiguracionNoArranca() {
        Exception error = assertThrows(Exception.class,
                () -> { try (ConfigurableApplicationContext ctx = aplicacion().run()) { /* no debe llegar */ } },
                "un contexto prod sin configuracion NO puede levantar");

        String traza = trazaCompleta(error);
        // Un fallo que solo dice "configuracion invalida" se acaba resolviendo
        // desactivando la comprobacion. Y si solo nombrara la primera variable,
        // arreglar el despliegue costaria un intento por variable.
        // ALMACEN_DIR y ALMACEN_S3_BUCKET aparecen desde el Bloque 8 dentro de
        // la explicacion de ALMACEN_PROVEEDOR: cual de los dos hace falta
        // depende del proveedor, y decir los dos de entrada es lo que evita un
        // segundo despliegue solo para enterarse.
        for (String variable : new String[]{"DB_URL", "DB_USER", "DB_PASSWORD",
                "API_TOKEN_SECRET", "CORS_ORIGENES", "ALMACEN_PROVEEDOR",
                "ALMACEN_DIR", "ALMACEN_S3_BUCKET"}) {
            assertTrue(traza.contains(variable),
                    "el mensaje debe nombrar " + variable + "; traza: " + traza);
        }
        assertTrue(traza.contains("D-S0-20"), "debe citar la decision que lo justifica");
    }

    @Test
    @DisplayName("con las variables presentes, la guarda temprana deja pasar (el fallo ya no es por ellas)")
    void conVariablesPresentesLaGuardaTempranaNoBloquea() {
        // Se declaran todas; el arranque seguira fallando porque no hay
        // PostgreSQL en esta URL, pero la traza ya NO puede hablar de
        // variables ausentes: eso prueba que la guarda dejo pasar.
        Exception error = assertThrows(Exception.class,
                () -> { try (ConfigurableApplicationContext ctx = aplicacion(
                        "DB_URL=jdbc:postgresql://no-existe-a-proposito:5432/x",
                        "DB_USER=u",
                        "DB_PASSWORD=una-contrasena-cualquiera",
                        "API_TOKEN_SECRET=0123456789012345678901234567890123456789",
                        "CORS_ORIGENES=https://brox.pe",
                        // Desde el Bloque 8 el proveedor es obligatorio y decide
                        // cual es la otra variable que hace falta.
                        "ALMACEN_PROVEEDOR=DISCO",
                        "ALMACEN_DIR=/var/lib/controllocal/almacen").run()) { /* no llega */ } });

        String traza = trazaCompleta(error);
        assertNotNull(traza);
        assertTrue(!traza.contains("variable(s) de entorno obligatoria(s)"),
                "la guarda temprana no debe seguir quejandose; traza: " + traza);
    }

    private String trazaCompleta(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable actual = error;
        while (actual != null) {
            sb.append(actual.getClass().getSimpleName()).append(": ").append(actual.getMessage()).append(" | ");
            actual = actual.getCause();
        }
        return sb.toString();
    }
}
