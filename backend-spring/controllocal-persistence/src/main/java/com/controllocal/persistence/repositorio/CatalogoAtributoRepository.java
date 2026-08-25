package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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


    /**
     * <b>Las definiciones de un lote de claves PARA LEER, incluidas las
     * retiradas</b> (Corte 5 · 5A).
     *
     * <p>Es la unica consulta del catalogo que <b>no</b> filtra por
     * {@code activo}, y esa excepcion es exactamente la asimetria que la
     * retirada de una clave exige:
     *
     * <pre>
     *   CAPTURA  (alta y edicion) -> solo lo ACTIVO   : una clave retirada no se pregunta
     *   LECTURA  (la ficha)       -> tambien lo RETIRADO: su valor sigue escrito y se lee
     * </pre>
     *
     * <p>Sin ella, retirar {@code servicios_disponibles} en {@code V84} dejaba
     * la ficha mostrando <b>la clave desnuda</b> —{@code rotulo = "servicios_disponibles"},
     * {@code tipoDato = null}— porque {@link #aplicablesA} ya no devolvia su
     * definicion. El valor se conservaba y la lectura se degradaba, que es la
     * mitad que faltaba de «retirar la pregunta no retira el dato». Un
     * {@code tipoDato} nulo ademas cambia lo que se pinta: el SPA decide con el
     * si un booleano se dice «Si/No» o «true».
     *
     * <p>No sustituye a {@link #aplicablesA}: se consulta <b>solo</b> para las
     * claves que ya tienen valor escrito y que la consulta de captura no
     * resolvio, asi que no puede reintroducir una clave retirada en ningun
     * formulario.
     *
     * <p>El orden repite la precedencia de {@link #porClave} —la definicion de
     * la organizacion particulariza la del sistema— y quien consuma la lista se
     * queda con la primera de cada clave.
     *
     * <p>Sin {@code join fetch}: la ficha usa el rotulo, el tipo, la unidad y el
     * orden, todos columnas de la propia fila. Traerse la aplicabilidad seria
     * multiplicar filas por tipo para no mirarlas.
     */
    @Query("""
            select c from CatalogoAtributo c
            where c.clave in :claves
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
            order by c.clave asc, c.organizacionId desc nulls last
            """)
    List<CatalogoAtributo> paraLeer(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("claves") Collection<String> claves);

    /**
     * <b>La clave logica cuya autoridad es este campo canonico</b> (4.P).
     *
     * <p>Es la consulta inversa de {@link #porClave}: se pregunta por el
     * CONCEPTO del dominio --{@code PISO}, {@code METRAJE}-- y responde con la
     * clave que lo alimenta en esta organizacion.
     *
     * <p>Existe porque el cable tiene un campo {@code ubicacion.piso} que NO es
     * una coordenada: es un hecho gobernado del inmueble, con su vocabulario y
     * su exigencia. Enrutarlo exige traducir el nombre del hueco del cable al
     * de la clave, y esa traduccion no puede ser un literal en el caso de uso
     * --seria la matriz «campo -> clave» otra vez, escondida-- ni puede
     * suponer que la clave se llama {@code "piso"}: una organizacion puede
     * declarar la suya con otro nombre sobre el mismo campo canonico.
     */
    @Query("""
            select c from CatalogoAtributo c
            where c.campoEstructural = :campo
              and c.activo = true
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
            order by c.organizacionId desc nulls last
            limit 1
            """)
    Optional<CatalogoAtributo> porCampoEstructural(@Param("idOrganizacion") long idOrganizacion,
                                                   @Param("campo") String campo);
    /** Los del sistema, que ninguna organizacion puede borrar ni redefinir. */
    List<CatalogoAtributo> findByDelSistemaTrueOrderByOrdenAscClaveAsc();
}
