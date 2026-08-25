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

    /**
     * El conjunto de UNA clave, como texto (4.P). Gemelo de
     * {@code ValorMultipleAtributoRepository.valoresDe}, incluida la razon por
     * la que devuelve escalares y no entidades: leerlas las mete en el contexto
     * de persistencia, el borrado masivo no lo limpia, y el {@code save}
     * posterior de un elemento que estaba en los dos conjuntos se convierte en
     * un UPDATE de una fila que ya no existe — o sea, en una perdida callada.
     */
    @Query("""
            select v.valor from ValorMultipleEncargo v
            where v.idAtributoEncargo = :idAtributo
            order by v.valor asc
            """)
    List<String> valoresDe(@Param("idAtributo") long idAtributo);

    /**
     * Retira SOLO los elementos que se van (4.P, segunda vuelta). Gemelo de
     * {@code ValorMultipleAtributoRepository.borrarDe}, y por la misma razon:
     * borrar el conjunto entero y reescribirlo apoyaba la correccion en una
     * invariante que no fijaba nadie —que ninguna entidad de este tipo estuviera
     * en el contexto de persistencia—, y borrar solo lo que se va la hace
     * innecesaria.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from ValorMultipleEncargo v
            where v.idAtributoEncargo = :idAtributo and v.valor in :valores
            """)
    void borrarDe(@Param("idAtributo") long idAtributo,
                  @Param("valores") Collection<String> valores);
}
