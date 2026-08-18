package com.controllocal.persistence.repositorio;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Los comandos ya ejecutados (V57).
 *
 * <p>Una sola consulta y siempre la misma: por {@code (organizacion, clave)},
 * que es exactamente el indice unico de la tabla. Por organizacion y no global
 * porque dos tenants pueden generar el mismo identificador sin que eso
 * signifique nada, y la clave de uno no debe poder resolver el comando de otro.
 */
public interface ComandoIdempotenteRepository extends JpaRepository<ComandoIdempotente, Long> {

    Optional<ComandoIdempotente> findByOrganizacionIdAndClaveIdempotencia(long idOrganizacion,
                                                                          String claveIdempotencia);
}
