package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Tarea;

import java.util.Locale;

/**
 * <b>Qué clase de asunto es cada tipo de tarea</b>, para que la política de
 * despacho pueda pesarlo (D-E2-1 §3, E2.2).
 *
 * <h2>Tres hechos, declarados una vez</h2>
 * <pre>
 *   dependeDeMi   ¿la siguiente acción es del agente, o espera a otro?
 *   esOcasion     ¿algo acaba de moverse y conviene aprovecharlo?
 *   desbloquea    ¿al resolverlo continúa un proceso hoy detenido?
 * </pre>
 *
 * <h2>Por qué se declaran y no se persisten</h2>
 * Los tres se derivan del <b>tipo</b> del asunto, que ya está guardado. Añadir
 * tres columnas sería guardar la respuesta a una pregunta que el tipo ya
 * contesta, y el día que cambiara la regla habría que reescribir el histórico
 * para que las tareas viejas dijeran lo nuevo. Se derivan al leer, que es lo que
 * el mapa pantalla→dominio llama {@code DERIVADO_BACKEND}.
 *
 * <h2>El hallazgo que hizo falta medir</h2>
 * De los siete disparadores, <b>uno no depende del agente</b>: la comisión lista
 * para cobro. {@code POST /contratos/{id}/comision/cobro} y
 * {@code /comision/movimientos} son <b>BROKER</b> en la matriz operación-rol, así
 * que ese asunto lleva tiempo en la bandeja del agente sin que pueda resolverlo.
 * Sigue estando —la cola lo enseña, y saber que el dinero está esperando importa—
 * pero deja de ocupar uno de los cinco puestos del foco.
 *
 * <p>No se dedujo del nombre: se comprobó contra
 * {@code docs/ai/matriz-operacion-rol.md}, que es la fuente de verdad y está
 * vigilada por su propio test.
 */
public final class NaturalezaDelAsunto {

    private NaturalezaDelAsunto() {
    }

    /**
     * ¿La siguiente acción es de quien tiene el asunto en su bandeja?
     *
     * <p><b>Criterio 1 de la política.</b> Lo que espera al interesado, al
     * propietario, al broker o a documentación no compite por el foco.
     *
     * <p>Hacen falta el tipo <b>y</b> la entidad.
     * <p>{@code SEGUIMIENTO} se usa para dos cosas distintas: una solicitud
     * aprobada pendiente de cierre —que cierra el agente con {@code POST
     * /contratos}— y una comisión lista para cobro —que registra el broker—. El
     * tipo por sí solo no distingue, y meter dos naturalezas bajo un mismo
     * nombre es justo lo que hace que una regla parezca arbitraria.
     */
    public static boolean dependeDelAgente(String tipoDeTarea, String entidadTipo) {
        String tipo = normalizado(tipoDeTarea);
        String entidad = normalizado(entidadTipo);
        if (Tarea.SEGUIMIENTO.equals(tipo) && "CONTRATO_ALQUILER".equals(entidad)) {
            // Comision lista para cobro: la cobra el BROKER (matriz operacion-rol).
            return false;
        }
        return true;
    }

    /**
     * ¿Algo acaba de moverse y conviene aprovecharlo?
     *
     * <p><b>Criterio 3.</b> Una coincidencia de cartera es la ocasión por
     * excelencia: apareció un cliente que encaja con un local que ya se tiene, y
     * eso caduca — el cliente encuentra otro sitio. Puede superar a un
     * vencimiento lejano, y es deliberado.
     */
    public static boolean esOcasion(String tipoDeTarea) {
        return Tarea.PROPONER_OPORTUNIDAD.equals(normalizado(tipoDeTarea));
    }

    /**
     * ¿Al resolverlo continúa un proceso hoy detenido?
     *
     * <p><b>Criterio 4.</b> Los dos casos son expedientes parados esperando algo
     * del agente: una solicitud observada que no avanza hasta que se subsanan
     * los documentos, y un local retirado que no se puede volver a ofrecer hasta
     * que se revisa. En ambos hay alguien esperando al otro lado.
     */
    public static boolean desbloquea(String tipoDeTarea) {
        String tipo = normalizado(tipoDeTarea);
        return Tarea.SUBIR_DOCUMENTOS.equals(tipo) || Tarea.REVISION_INMUEBLE.equals(tipo);
    }

    private static String normalizado(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
