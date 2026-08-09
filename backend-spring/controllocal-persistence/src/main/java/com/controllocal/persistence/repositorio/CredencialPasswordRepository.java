package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.CredencialPassword;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CredencialPasswordRepository extends JpaRepository<CredencialPassword, Long> {

    /**
     * Los ultimos hashes de una credencial, del mas reciente al mas antiguo.
     * El limite lo pone quien llama con un {@link Pageable}: la politica (§4.5)
     * vive en el service, no repartida por consultas.
     */
    @Query("""
            select h from CredencialPassword h
             where h.idCredencial = :idCredencial
             order by h.creadoEn desc
            """)
    List<CredencialPassword> ultimosDe(@Param("idCredencial") long idCredencial, Pageable pagina);

    /**
     * Recorta el historial al tamano de la politica. Se poda al escribir y no
     * con una tarea aparte: si la poda dependiera de un proceso externo, un
     * fallo suyo convertiria la tabla en un archivo de hashes indefinido.
     */
    @Modifying
    @Query("""
            delete from CredencialPassword h
             where h.idCredencial = :idCredencial
               and h.id not in :idsQueSeConservan
            """)
    int podar(@Param("idCredencial") long idCredencial,
              @Param("idsQueSeConservan") List<Long> idsQueSeConservan);
}
