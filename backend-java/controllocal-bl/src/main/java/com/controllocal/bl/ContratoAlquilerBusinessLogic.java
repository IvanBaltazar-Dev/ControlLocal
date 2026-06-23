package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.ContratoAlquiler;

public interface ContratoAlquilerBusinessLogic {

    // Formaliza el alquiler a partir de una solicitud APROBADA: crea el contrato minimo
    // (vinculo + cierre), crea la liquidacion de comision PENDIENTE (total = renta x %pactado,
    // split 50/50 agente/empresa), registra el precio CERRADO del local, cierra la oportunidad
    // como finalizada exitosa y marca el local como No disponible. Todo en una transaccion.
    // Las condiciones del trato (renta, plazo, etc.) se leen de la solicitud. Devuelve el id del contrato.
    Long registrarPorSolicitud(Long idSolicitud);

    Optional<ContratoAlquiler> buscarPorId(Long idContrato);

    Optional<ContratoAlquiler> buscarPorOportunidad(Long idOportunidad);

    List<ContratoAlquiler> listarTodos();
}
