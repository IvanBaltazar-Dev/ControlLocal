package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.CodigoRespaldoMfa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CodigoRespaldoMfaRepository extends JpaRepository<CodigoRespaldoMfa, Long> {

    /**
     * Localiza <b>una sola fila</b> por su identificador publico.
     *
     * <p>Es lo que hace viable el hash lento (D-S0-24): sin identificador
     * habria que derivar PBKDF2 contra los ocho codigos en cada intento.
     */
    @Query("""
            select c from CodigoRespaldoMfa c
             where c.idFactor = :idFactor
               and c.identificador = :identificador
               and c.usadoEn is null
            """)
    Optional<CodigoRespaldoMfa> buscarDisponible(@Param("idFactor") long idFactor,
                                                 @Param("identificador") String identificador);

    @Query("""
            select count(c) from CodigoRespaldoMfa c
             where c.idFactor = :idFactor and c.usadoEn is null
            """)
    long contarDisponibles(@Param("idFactor") long idFactor);

    /**
     * Consumo <b>atomico</b>: el veredicto es cuantas filas afecto. Dos
     * peticiones simultaneas con el mismo codigo no pueden ganar las dos.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CodigoRespaldoMfa c set c.usadoEn = :ahora
             where c.id = :idCodigo and c.usadoEn is null
            """)
    int consumir(@Param("idCodigo") long idCodigo, @Param("ahora") OffsetDateTime ahora);

    /** Regenerar invalida TODOS los anteriores, usados o no. */
    @Modifying
    @Query("delete from CodigoRespaldoMfa c where c.idFactor = :idFactor")
    int borrarDe(@Param("idFactor") long idFactor);
}
