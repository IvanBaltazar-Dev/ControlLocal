package com.controllocal.bl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.enums.EstadoContrato;

public interface ContratoAlquilerBusinessLogic {

    // Formaliza el alquiler a partir de una solicitud APROBADA: crea el contrato minimo
    // (vinculo + cierre), crea la liquidacion de comision PENDIENTE (total = renta x %pactado,
    // split 50/50 agente/empresa), registra el precio CERRADO del local, cierra la oportunidad
    // como finalizada exitosa y marca el local como No disponible. Todo en una transaccion.
    // Las condiciones del trato (renta, plazo, etc.) se leen de la solicitud. Devuelve el id del contrato.
    // Atajo con valores por defecto (cierre = hoy, estado = VIGENTE, sin incidencias).
    Long registrarPorSolicitud(Long idSolicitud);

    // Variante con los datos de formalizacion que captura el agente en el cierre: fecha de
    // cierre, estado del contrato (FIRMADO o VIGENTE) e incidencias. Ademas de lo anterior,
    // deja conformes los documentos del expediente, cierra las publicaciones del local y
    // resuelve (Completadas) las tareas asociadas a la operacion. Todo en una transaccion.
    Long registrarPorSolicitud(Long idSolicitud, LocalDate fechaCierre,
            EstadoContrato estadoContrato, String incidencias);

    Optional<ContratoAlquiler> buscarPorId(Long idContrato);

    Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad);

    List<ContratoAlquiler> listarTodos();
}
