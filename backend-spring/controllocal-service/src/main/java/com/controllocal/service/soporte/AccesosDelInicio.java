package com.controllocal.service.soporte;

import com.controllocal.service.Actor;

import java.util.List;

/**
 * <b>El ámbito y los cuatro accesos rápidos del Inicio</b> (D-E2-1 §6.1, E2.5).
 *
 * <h2>Por qué lo decide el dominio y no la pantalla</h2>
 * <blockquote>No son los mismos para los dos roles: <b>el agente crea, el broker
 * revisa, decide y reparte.</b></blockquote>
 *
 * <p>Eso no es una preferencia de layout: es una afirmación sobre qué empieza
 * cada rol desde cero. Si viviera en Angular, sería una séptima interpretación
 * en el cliente —después de urgencia, orden y de quién depende, que E2.2 acaba
 * de traer al backend— y KAIROS necesitaría escribir la suya para ofrecer lo
 * mismo por WhatsApp.
 *
 * <h2>El orden importa</h2>
 * Primero se resuelve lo que ya existe (el foco), después lo que sigue en la
 * cola, y <b>sólo entonces</b> lo que se crea de nuevo. Por eso la barra va
 * debajo y no arriba: empezar algo nuevo con cinco cosas sin atender es
 * precisamente lo que el Inicio viene a evitar.
 *
 * <h2>La ruta va en el `href` y nunca a la vista</h2>
 * Las ocho rutas existen hoy en el SPA. El cliente las usa como destino, no las
 * enseña: un Inicio que muestra `/captaciones/pendientes` como texto está
 * enseñando su propia fontanería.
 */
public final class AccesosDelInicio {

    /** Cuatro. Ni tres ni cinco: es una barra, no un menú. */
    public static final int CUANTOS = 4;

    private AccesosDelInicio() {
    }

    /**
     * Un acceso rápido.
     *
     * @param destino ruta REAL del SPA; viaja para el `href`, no para leerse
     */
    public record Acceso(String etiqueta, String destino) {
    }

    // El ambito NO vive aqui: ya lo publica `IndicadoresResponse`, y darle un
    // segundo productor seria crear la doble verdad que D-E4-3 cerro para los
    // datos de la propiedad. Se alineo con el diseno en su unico dueno.

    /**
     * Los cuatro accesos del rol, en su orden.
     *
     * <p>El TENANT_ADMIN recibe los del broker: audita las mismas colas, aunque
     * desde D-F4-5 no pueda decidir sobre ellas. Darle los del agente sería
     * ofrecerle crear operaciones que no le corresponden.
     */
    public static List<Acceso> de(Actor actor) {
        if (actor.esAgente()) {
            return List.of(
                    new Acceso("Nueva prospección", "propiedades/nueva"),
                    new Acceso("Nueva captación", "captaciones/nueva"),
                    new Acceso("Programar visita", "visitas/nueva"),
                    new Acceso("Reporte al propietario", "reportes"));
        }
        return List.of(
                new Acceso("Revisar captaciones", "captaciones/pendientes"),
                new Acceso("Evaluar solicitudes", "solicitudes/revisar"),
                new Acceso("Seguimiento del equipo", "seguimiento-comercial"),
                new Acceso("Reasignar cartera", "captaciones/reasignaciones"));
    }
}
