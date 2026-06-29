package com.controllocal.bl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Prospeccion;

/**
 * Embudo de prospeccion (pre-captacion). Espejo, del lado de la oferta, de
 * {@link OportunidadComercialBusinessLogic}.
 */
public interface ProspeccionBusinessLogic {

    Long registrar(Prospeccion prospeccion);
    Optional<Prospeccion> buscarPorId(Long idProspeccion);
    List<Prospeccion> listarTodos();
    boolean actualizar(Prospeccion prospeccion);
    boolean eliminar(Long idProspeccion);

    /** Prospecciones vivas cuya ultima accion de seguimiento tiene ya {@code diasAviso} dias o mas (recontacto vencido). */
    List<Prospeccion> listarPorRecontactar(int diasAviso);

    // Filas acotadas a los agentes dados (alcance por rol). Vacio si la coleccion viene vacia.
    List<Prospeccion> listarPorAgentes(java.util.Collection<Long> idsAgente);
    List<Prospeccion> listarPorPropietario(Long idPropietario);

    /**
     * Crea las alertas SIN_RESPUESTA de las prospecciones con recontacto vencido
     * (desde el dia 8 sin nueva accion), sin duplicar las ya activas. Sustituye al
     * planificador: se invoca al consultar las alertas. Devuelve cuantas creo.
     */
    int sincronizarRecontacto();

    // Transiciones del embudo (solo si la prospeccion sigue en proceso). Cada accion
    // reinicia el reloj de recontacto y atiende la alerta SIN_RESPUESTA activa.
    boolean contactar(Long idProspeccion);
    boolean registrarReunion(Long idProspeccion);
    boolean entregarPropuesta(Long idProspeccion);
    /** Accion de seguimiento del propietario: reinicia el reloj de recontacto (reemplaza el agendar manual). */
    boolean registrarSeguimiento(Long idProspeccion);
    boolean rechazar(Long idProspeccion, String motivo);
    boolean descartar(Long idProspeccion, String motivo);

    /** El propietario acepta: marca CAPTADO y crea la captacion (pendiente de revision). Devuelve su id. */
    Long captar(Long idProspeccion, BigDecimal comisionPactada);
}
