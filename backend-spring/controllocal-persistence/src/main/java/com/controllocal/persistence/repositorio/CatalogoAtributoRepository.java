package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * El catalogo de caracteristicas (D-E4-1 M2).
 *
 * <p>Es el repositorio del que el motor de registro (D-E4-2) deriva las
 * preguntas: <b>ninguna pantalla tiene una lista de campos escrita a mano</b>.
 *
 * <p>Todas las consultas resuelven la union de dos ambitos — los atributos del
 * sistema ({@code organizacionId is null}) y los de la organizacion — porque el
 * catalogo es hibrido por diseno. Y en las tres se trae {@code aplicaciones}
 * con {@code join fetch}: sin eso, preguntar "¿aplica a un terreno?" por cada
 * atributo dispara una consulta mas por fila.
 */
public interface CatalogoAtributoRepository extends JpaRepository<CatalogoAtributo, Long> {

    /** Todo lo que esta organizacion puede usar, en el orden de presentacion. */
    @Query("""
            select distinct c from CatalogoAtributo c
            left join fetch c.aplicaciones
            where c.activo = true
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
            order by c.orden asc, c.clave asc
            """)
    List<CatalogoAtributo> disponiblesPara(@Param("idOrganizacion") long idOrganizacion);

    /**
     * Lo que se pregunta para un tipo de propiedad, y solo eso. Es la consulta
     * que hace que registrar un terreno no pida dormitorios.
     */
    @Query("""
            select distinct c from CatalogoAtributo c
            left join fetch c.aplicaciones a
            where c.activo = true
              and c.sujeto = 'PROPIEDAD'
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
              and (c.aplicaTodos = true
                   or exists (select 1 from CatalogoAtributo c2 join c2.aplicaciones a2
                               where c2 = c and a2.tipoPropiedad = :tipoPropiedad))
            order by c.orden asc, c.clave asc
            """)
    List<CatalogoAtributo> aplicablesA(@Param("idOrganizacion") long idOrganizacion,
                                       @Param("tipoPropiedad") String tipoPropiedad);

    /**
     * <b>Lo que se pregunta para UNA comercializacion</b> (V73).
     *
     * <p>Gemela de la de arriba y separada a proposito. Podrian parecer la misma
     * consulta con un parametro mas, y no lo son: preguntan por sujetos
     * distintos, miran tablas de aplicabilidad distintas y responden en momentos
     * distintos --lo fisico se sabe al registrar el inmueble; lo comercial, al
     * firmar el encargo--. Fundirlas en un metodo con {@code tipoOperacion}
     * anulable haria que un descuido devolviera condiciones comerciales dentro
     * del bloque fisico, que es exactamente el saco comun que este corte
     * prohibe.
     *
     * <p>El {@code sujeto = 'ENCARGO'} no es defensivo: es la mitad del
     * enrutamiento. Sin el, una clave fisica que por error declarara
     * aplicabilidad por operacion se colaria en el bloque del encargo.
     */
    @Query("""
            select distinct c from CatalogoAtributo c
            left join fetch c.aplicacionesOperacion o
            where c.activo = true
              and c.sujeto = 'ENCARGO'
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
              and (c.aplicaTodos = true
                   or exists (select 1 from CatalogoAtributo c2 join c2.aplicacionesOperacion o2
                               where c2 = c and o2.tipoPropiedad = :tipoPropiedad
                                 and o2.tipoOperacion = :tipoOperacion))
            order by c.orden asc, c.clave asc
            """)
    List<CatalogoAtributo> aplicablesAEncargo(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("tipoPropiedad") String tipoPropiedad,
                                              @Param("tipoOperacion") String tipoOperacion);

    /**
     * Una clave concreta. La del sistema y la de la organizacion pueden coexistir
     * con el mismo nombre; gana la de la organizacion, que es la que la
     * particulariza — y por eso el orden es {@code organizacionId desc}.
     */
    @Query("""
            select c from CatalogoAtributo c
            where c.clave = :clave
              and c.activo = true
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
            order by c.organizacionId desc nulls last
            limit 1
            """)
    Optional<CatalogoAtributo> porClave(@Param("idOrganizacion") long idOrganizacion,
                                        @Param("clave") String clave);

    /** Los del sistema, que ninguna organizacion puede borrar ni redefinir. */
    List<CatalogoAtributo> findByDelSistemaTrueOrderByOrdenAscClaveAsc();
}
