package com.controllocal.persistence.repositorio;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Serializa la primera ejecución aun cuando todavía no existe una fila que bloquear. */
    @Query(value = "select 1 from pg_advisory_xact_lock(:identidad)", nativeQuery = true)
    Integer bloquearIdentidad(@Param("identidad") long identidad);

    /** Lectores de un artefacto inmutable; no sirve para crear ni sustituir el artefacto. */
    @Query(value = "select 1 from pg_advisory_xact_lock_shared(:identidad)", nativeQuery = true)
    Integer bloquearLecturaIdentidad(@Param("identidad") long identidad);

    Optional<ComandoIdempotente> findByOrganizacionIdAndClaveIdempotencia(long idOrganizacion,
                                                                          String claveIdempotencia);
}
