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
     * <b>El conjunto de UNA clave, como texto y no como entidades</b> (4.P).
     *
     * <p>La diferencia con {@link #deVarios} no es de comodidad: es lo unico que
     * hace segura la secuencia <i>leer el conjunto anterior → borrarlo →
     * escribir el nuevo</i>, que es lo que el linaje necesita para conservar el
     * conjunto entero en vez de una diferencia.
     *
     * <p>Con {@code deVarios} esa secuencia <b>pierde silenciosamente</b> los
     * elementos que estan en los dos conjuntos. Medido el 2026-08-25: cambiar
     * {@code vigilancia} de {@code {CASETA_24H, CAMARAS_CCTV}} a
     * {@code {CAMARAS_CCTV, CONTROL_DE_ACCESO}} dejaba la ficha con
     * <b>{@code {CONTROL_DE_ACCESO}}</b> a secas. La causa: leer las entidades
     * las mete en el contexto de persistencia, el {@code borrarDe} es un DELETE
     * masivo que <b>no lo limpia</b>, y el {@code save} posterior con la misma
     * clave compuesta se resuelve como {@code merge} de algo que Hibernate cree
     * ya gestionado — asi que emite un UPDATE de una fila que ya no existe en
     * vez de un INSERT.
     *
     * <p>Devolver escalares corta el problema de raiz: no entra ninguna entidad
     * en el contexto, y {@code save} vuelve a ser un alta.
     */
    @Query("""
            select v.valor from ValorMultipleAtributo v
            where v.idAtributoPropiedad = :idAtributo
            order by v.valor asc
            """)
    List<String> valoresDe(@Param("idAtributo") long idAtributo);

    /**
     * <b>Retira SOLO los elementos que se van</b> (4.P, segunda vuelta).
     *
     * <p>Editar una lista es SUSTITUIR, y hasta esta correccion sustituir era
     * «borrarlo todo y volver a escribirlo». Funcionaba, pero apoyaba la
     * correccion entera en una invariante que no fijaba nadie: <b>que ninguna
     * entidad de este tipo estuviera en el contexto de persistencia</b> cuando
     * corriera el borrado. Bastaba que alguien leyera la ficha antes de guardar
     * para que volviera el fallo, en silencio.
     *
     * <p>Borrar solo lo que se va lo cierra <b>por construccion</b>: el elemento
     * que esta en los dos conjuntos —el unico que podia perderse— ya no se borra
     * ni se vuelve a insertar, asi que no hay {@code merge} que pueda
     * convertirse en un UPDATE de una fila que ya no existe. Y ademas es lo que
     * de verdad pasa: la propiedad no dejo de tener camaras durante un instante.
     *
     * <p>{@code flushAutomatically} ordena lo pendiente antes del borrado: el
     * ancla puede acabar de insertarse en esta misma transaccion.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from ValorMultipleAtributo v
            where v.idAtributoPropiedad = :idAtributo and v.valor in :valores
            """)
    void borrarDe(@Param("idAtributo") long idAtributo,
                  @Param("valores") Collection<String> valores);
}
