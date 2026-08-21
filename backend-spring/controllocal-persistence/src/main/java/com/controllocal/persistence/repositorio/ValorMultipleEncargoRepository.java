package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ValorMultipleEncargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Los valores de las claves LISTA_MULTIPLE del ENCARGO (V73).
 *
 * <p>Gemelo de {@code ValorMultipleAtributoRepository}, con las mismas dos
 * operaciones y por las mismas razones: lectura <b>por lote</b> --nunca por
 * fila-- y borrado completo antes de reescribir, porque editar una lista es
 * SUSTITUIR: sin el borrado no habria forma de quitar una opcion.
 */
public interface ValorMultipleEncargoRepository extends JpaRepository<ValorMultipleEncargo, Long> {

    @Query("""
            select v from ValorMultipleEncargo v
            where v.idAtributoEncargo in :idsAtributo
            order by v.idAtributoEncargo, v.valor
            """)
    List<ValorMultipleEncargo> deVarios(@Param("idsAtributo") Collection<Long> idsAtributo);

    @Modifying
    @Query("delete from ValorMultipleEncargo v where v.idAtributoEncargo = :idAtributo")
    void borrarDe(@Param("idAtributo") long idAtributo);
}
