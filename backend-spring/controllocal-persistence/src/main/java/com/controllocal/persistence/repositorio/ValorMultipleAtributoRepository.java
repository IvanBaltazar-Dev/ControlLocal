package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.ValorMultipleAtributo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Los valores de las claves LISTA_MULTIPLE (V72).
 *
 * <p>Las dos consultas de lectura son <b>por lote</b> y no por fila. No es una
 * optimizacion: RC-003 retiro del repositorio toda consulta que creciera con el
 * numero de filas de la pagina, y una lista de opciones por propiedad seria
 * exactamente eso -- la N+1 que reaparece cuando una capacidad nueva se lee
 * "solo para este caso".
 */
public interface ValorMultipleAtributoRepository extends JpaRepository<ValorMultipleAtributo, Long> {

    /**
     * Los valores de varias filas ancla, de una vez.
     *
     * <p>Con esto, hidratar N propiedades cuesta <b>una</b> consulta mas, sea N
     * uno o quinientos.
     */
    @Query("""
            select v from ValorMultipleAtributo v
            where v.idAtributoPropiedad in :idsAtributo
            order by v.idAtributoPropiedad, v.valor
            """)
    List<ValorMultipleAtributo> deVarios(@Param("idsAtributo") Collection<Long> idsAtributo);

    /**
     * Retira todos los valores de una clave.
     *
     * <p>Editar una lista es SUSTITUIR, no anadir: sin este borrado no habria
     * forma de quitar una opcion, que es la mitad de lo que significa editarla.
     */
    @Modifying
    @Query("delete from ValorMultipleAtributo v where v.idAtributoPropiedad = :idAtributo")
    void borrarDe(@Param("idAtributo") long idAtributo);
}
