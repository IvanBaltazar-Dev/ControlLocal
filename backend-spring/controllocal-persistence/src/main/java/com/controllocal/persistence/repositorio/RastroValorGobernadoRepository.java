package com.controllocal.persistence.repositorio;

import com.controllocal.domain.auditoria.RastroValorGobernado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * El linaje de los valores gobernados (4.P, V83).
 *
 * <p><b>Se consulta por la identidad LOGICA</b>
 * —{@code (organizacion, sujeto, agregado, clave)}— y nunca por el id de la fila
 * vigente. Es lo que permite responder «que ha pasado con {@code tipo_acceso} de
 * esta propiedad» aunque hoy no tenga valor: una retirada borra la fila de
 * {@code atributo_propiedad} y su historia sigue aqui.
 *
 * <p>No hay ningun metodo de borrado ni de actualizacion, y no es un descuido:
 * la tabla es append-only y {@code tg_rastro_valor_append_only} lo garantiza en
 * la base. Un {@code deleteBy…} heredado de {@code JpaRepository} fallaria con
 * el mensaje del trigger, que es lo correcto.
 */
public interface RastroValorGobernadoRepository extends JpaRepository<RastroValorGobernado, Long> {

    /** La historia de UNA clave logica, del primer hecho al ultimo. */
    @Query("""
            select r from RastroValorGobernado r
            where r.organizacionId = :idOrganizacion
              and r.sujeto = :sujeto
              and r.idAgregado = :idAgregado
              and r.clave = :clave
            order by r.id asc
            """)
    List<RastroValorGobernado> historiaDe(@Param("idOrganizacion") long idOrganizacion,
                                          @Param("sujeto") String sujeto,
                                          @Param("idAgregado") long idAgregado,
                                          @Param("clave") String clave);

    /** Todo el linaje de un agregado, para leer su expediente de una vez. */
    @Query("""
            select r from RastroValorGobernado r
            where r.organizacionId = :idOrganizacion
              and r.sujeto = :sujeto
              and r.idAgregado = :idAgregado
            order by r.clave asc, r.id asc
            """)
    List<RastroValorGobernado> historiaDelAgregado(@Param("idOrganizacion") long idOrganizacion,
                                                   @Param("sujeto") String sujeto,
                                                   @Param("idAgregado") long idAgregado);
}
