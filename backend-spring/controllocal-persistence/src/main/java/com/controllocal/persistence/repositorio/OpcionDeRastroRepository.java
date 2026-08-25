package com.controllocal.persistence.repositorio;

import com.controllocal.domain.auditoria.OpcionDeRastro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Los conjuntos completos que dejo cada escritura de un multivalor (4.P, V83).
 *
 * <p>{@link #deVarios} existe por la misma razon que su gemela de
 * {@code ValorMultipleAtributoRepository}: reconstruir la historia de un
 * multivalor son N escrituras, y preguntar sus elementos una por una seria el
 * N+1 que RC-003 retiro del repositorio.
 */
public interface OpcionDeRastroRepository extends JpaRepository<OpcionDeRastro, OpcionDeRastro.Clave> {

    @Query("""
            select o from OpcionDeRastro o
            where o.idRastro in :idsRastro
            order by o.idRastro asc, o.momento asc, o.valor asc
            """)
    List<OpcionDeRastro> deVarios(@Param("idsRastro") Collection<Long> idsRastro);
}
