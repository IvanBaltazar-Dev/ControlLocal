package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.ConcesionRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConcesionRecuperacionRepository extends JpaRepository<ConcesionRecuperacion, Long> {

    /** Por el hash del secreto presentado. El secreto en si no vive en ningun sitio. */
    Optional<ConcesionRecuperacion> findByHashSecreto(String hashSecreto);

    /**
     * La concesion viva del tenant, si la hay. Solo puede haber una: lo
     * garantiza el indice parcial {@code uq_concesion_viva_por_organizacion}.
     */
    @Query("""
            select c from ConcesionRecuperacion c
             where c.organizacionId = :idOrganizacion
               and c.estado in (com.controllocal.domain.seguridad.ConcesionRecuperacion.PENDIENTE,
                                com.controllocal.domain.seguridad.ConcesionRecuperacion.VIGENTE)
            """)
    Optional<ConcesionRecuperacion> buscarViva(@Param("idOrganizacion") long idOrganizacion);

    /**
     * <b>Consumo ATOMICO de capacidad (§9.7).</b> Mismo patron que el
     * anti-replay del TOTP y por la misma razon: el veredicto es <b>cuantas
     * filas afecto</b>, no una lectura previa.
     *
     * <p>Leer el contador, comprobarlo y despues escribir deja una ventana en
     * la que dos llamadas simultaneas gastan la misma capacidad. Y aqui eso no
     * es un contador mal llevado: es una concesion que ejecuta mas acciones de
     * las que se le concedieron.
     *
     * <p>Las cuatro condiciones van juntas a proposito —vigente, no caducada,
     * con capacidad—: separarlas invita a que una llamada compruebe tres y se
     * olvide de la cuarta. En particular <b>la caducidad se comprueba aqui</b>,
     * en cada uso, y no solo en el barrido programado: la concesion caduca
     * aunque el {@code @Scheduled} no se haya ejecutado.
     *
     * @return {@code 1} si quedaba capacidad y se consumio; {@code 0} si la
     *         concesion esta agotada, caducada o cerrada.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ConcesionRecuperacion c
               set c.accionesConsumidas = c.accionesConsumidas + 1
             where c.id = :id
               and c.estado = com.controllocal.domain.seguridad.ConcesionRecuperacion.VIGENTE
               and c.expiraEn > :ahora
               and c.accionesConsumidas < c.maxAcciones
            """)
    int consumirCapacidad(@Param("id") long id, @Param("ahora") OffsetDateTime ahora);

    /** Todas las vivas, para cerrar las que ya no hacen falta. */
    @Query("""
            select c from ConcesionRecuperacion c
             where c.estado in (com.controllocal.domain.seguridad.ConcesionRecuperacion.PENDIENTE,
                                com.controllocal.domain.seguridad.ConcesionRecuperacion.VIGENTE)
            """)
    List<ConcesionRecuperacion> vivas();

    /** Las que vencieron y siguen figurando como utilizables. */
    @Query("""
            select c from ConcesionRecuperacion c
             where c.estado in (com.controllocal.domain.seguridad.ConcesionRecuperacion.PENDIENTE,
                                com.controllocal.domain.seguridad.ConcesionRecuperacion.VIGENTE)
               and c.expiraEn is not null
               and c.expiraEn <= :ahora
            """)
    List<ConcesionRecuperacion> vencidas(@Param("ahora") OffsetDateTime ahora);
}
