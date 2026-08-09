package com.controllocal.persistence.repositorio;

import com.controllocal.domain.consentimiento.EvidenciaAutorizacion;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Evidencia de COMO se obtuvo una autorizacion (canal, y el rastro del canal
 * cuando existe). Es tabla global, no privada del tenant: la referencia el
 * evento, que si lleva organizacion.
 */
public interface EvidenciaAutorizacionRepository extends JpaRepository<EvidenciaAutorizacion, Long> {
}
