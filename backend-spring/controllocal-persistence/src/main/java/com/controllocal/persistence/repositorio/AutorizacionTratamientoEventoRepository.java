package com.controllocal.persistence.repositorio;

import com.controllocal.domain.consentimiento.AutorizacionTratamientoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Registro APPEND-ONLY de autorizaciones (D-27). Nunca se actualiza ni se
 * borra una fila: revocar es escribir otro evento.
 * <p>
 * Por eso "esta autorizado" no es una columna sino una <b>proyeccion</b>: el
 * ultimo evento de cada (organizacion, persona, finalidad).
 */
public interface AutorizacionTratamientoEventoRepository
        extends JpaRepository<AutorizacionTratamientoEvento, Long> {

    /**
     * Ultimo evento de esa persona para esa finalidad. Se ordena por id y no
     * por {@code ocurrido_en}: dos eventos del mismo instante —posible dentro
     * de una sola transaccion— empatarian por fecha, y entonces cual gana
     * dependeria del plan de ejecucion.
     */
    @Query("""
            select a from AutorizacionTratamientoEvento a
             where a.organizacionId = :idOrganizacion
               and a.idPersona = :idPersona
               and a.finalidadCodigo = :finalidad
             order by a.id desc
            limit 1
            """)
    Optional<AutorizacionTratamientoEvento> ultimoEvento(@Param("idOrganizacion") long idOrganizacion,
                                                         @Param("idPersona") long idPersona,
                                                         @Param("finalidad") String finalidad);

    /** Historial completo, del mas reciente al mas antiguo. Para la ficha. */
    List<AutorizacionTratamientoEvento> findByOrganizacionIdAndIdPersonaOrderByIdDesc(long organizacionId,
                                                                                      long idPersona);
}
