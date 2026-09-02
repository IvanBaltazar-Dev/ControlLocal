package com.controllocal.web.dto;

import com.controllocal.service.excepcion.ReglaNegocioException;

/**
 * Cuerpo de {@code POST /captaciones/{id}/reasignar}: agente destino, motivo y
 * <b>el agente que se vio</b>.
 *
 * <p>{@code idAgenteActual} entra el 2026-09-01 con D-P0-9 aplicado al ENCARGO
 * y es <b>obligatorio</b>. Sin el, dos comandos que salieran del mismo agente A
 * —uno hacia B y otro hacia C— pasaban las mismas guardas y el segundo pisaba al
 * primero: la ultima escritura ganaba y el historial quedaba afirmando «de A a
 * C» sobre un encargo que ya llevaba B.
 *
 * <p>La validacion vive <b>aqui</b> y no en el controlador para que la regla
 * tenga <b>un solo</b> sitio: BROX Web y KAIROS entran por el mismo endpoint con
 * el mismo cuerpo, y una comprobacion escrita en la puerta se reescribe para
 * cada puerta nueva. Es la misma decision que en
 * {@code AsignarResponsableRequest.observado()}.
 *
 * <p><b>No hay equivalente a {@code sinResponsableActual}</b>, y no es un olvido:
 * {@code captacion.id_rol_agente} es NOT NULL desde V5, asi que un encargo sin
 * agente no existe y FALTANTE no es un estado que se pueda observar aqui.
 */
public record ReasignacionRequest(Long idAgenteNuevo, String motivo, Long idAgenteActual) {

    /**
     * El agente destino, o el 400 que ya respondia el Core cuando el cuerpo no
     * lo traia. El mensaje es el mismo: era la unica forma de fallar y no puede
     * cambiar por mudarse de capa.
     */
    public long destino() {
        if (idAgenteNuevo == null || idAgenteNuevo <= 0) {
            throw new ReglaNegocioException("El agente destino es obligatorio.");
        }
        return idAgenteNuevo;
    }

    /**
     * <b>El agente que quien decide vio</b>, o el 400 que lo exige.
     *
     * <p>Un cuerpo sin esta declaracion no dice «me da igual quien lo lleve»:
     * dice que <b>nadie miro</b>. Quedarse con el agente que hubiera en ese
     * instante seria inventar la observacion y convertir la reasignacion en un
     * «pon a B» que no sabe de donde parte.
     */
    public long observado() {
        if (idAgenteActual == null || idAgenteActual <= 0) {
            throw new ReglaNegocioException(
                    "Falta decir que agente viste llevando este encargo (`idAgenteActual`). Un "
                            + "cambio de autoridad no parte de un estado que nadie miro: si el "
                            + "agente ya no es ese, esta reasignacion no se reinterpreta sobre "
                            + "el que haya ahora.");
        }
        return idAgenteActual;
    }
}
