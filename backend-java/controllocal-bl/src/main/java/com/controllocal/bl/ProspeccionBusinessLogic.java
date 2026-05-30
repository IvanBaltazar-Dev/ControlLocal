package com.controllocal.bl;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Prospecciones en seguimiento cuyo recontacto vence dentro de {@code diasAviso} (o ya vencio). */
    List<Prospeccion> listarPorRecontactar(int diasAviso);

    // Transiciones del embudo (solo si la prospeccion sigue en proceso).
    boolean contactar(Long idProspeccion);
    boolean registrarReunion(Long idProspeccion);
    boolean entregarPropuesta(Long idProspeccion);
    /** "Por ahora no": deja en seguimiento; el recontacto no puede superar 15 dias. */
    boolean posponer(Long idProspeccion, LocalDate fechaRecontacto);
    boolean rechazar(Long idProspeccion, String motivo);
    boolean descartar(Long idProspeccion, String motivo);

    /** El propietario acepta: marca CAPTADO y crea la captacion (pendiente de revision). Devuelve su id. */
    Long captar(Long idProspeccion, BigDecimal comisionPactada);
}
