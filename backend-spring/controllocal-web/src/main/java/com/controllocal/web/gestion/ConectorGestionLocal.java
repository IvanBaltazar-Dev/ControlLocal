package com.controllocal.web.gestion;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Conector de <b>gestion local</b> para la recuperacion de emergencia (§9.2).
 *
 * <h2>Por que un puerto propio y no una ruta mas del API</h2>
 * Porque el control que hace segura a esta superficie <b>no es una contraseña,
 * es la red</b>. La recuperacion de emergencia no tiene sesion que exigir —no
 * hay nadie dentro, esa es la situacion— asi que lo unico que puede sostenerla
 * es que <b>no sea alcanzable</b>: escucha en {@code 127.0.0.1}, en un puerto
 * que Docker no publica y que el proxy inverso no conoce. Para usarla hay que
 * estar en el host donde corre el backend.
 *
 * <p>Una ruta mas del API publico, por muy protegida que estuviera, sería
 * alcanzable desde Internet. Eso convierte «hace falta acceso al servidor» en
 * «hace falta acertar dos secretos», que es un control mucho mas debil.
 *
 * <p><b>Apagado por defecto.</b> Sin
 * {@code controllocal.recuperacion.habilitada=true} este conector no existe, y
 * entonces la superficie tampoco.
 */
@Configuration
@ConditionalOnProperty(name = "controllocal.recuperacion.habilitada", havingValue = "true")
public class ConectorGestionLocal
        implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final Logger LOG = LoggerFactory.getLogger(ConectorGestionLocal.class);

    /** Prefijo de las rutas que SOLO viven en este conector. */
    public static final String RUTA_GESTION = "/gestion";

    private final int puerto;

    public ConectorGestionLocal(
            @org.springframework.beans.factory.annotation.Value(
                    "${controllocal.recuperacion.puerto-gestion:8091}") int puerto) {
        this.puerto = puerto;
    }

    public int puerto() {
        return puerto;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        Connector conector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        conector.setPort(puerto);
        // LA linea que importa: ligado al loopback. Sin esto, el puerto de
        // gestion queda expuesto en todas las interfaces del host.
        conector.setProperty("address", "127.0.0.1");
        factory.addAdditionalTomcatConnectors(conector);
        LOG.warn("[recuperacion] conector de gestion local en 127.0.0.1:{} — "
                + "no debe publicarse ni por Docker ni por el proxy inverso", puerto);
    }
}
