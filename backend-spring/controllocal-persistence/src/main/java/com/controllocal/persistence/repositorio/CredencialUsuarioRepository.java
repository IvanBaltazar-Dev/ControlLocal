package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.CredencialUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CredencialUsuarioRepository extends JpaRepository<CredencialUsuario, Long> {

    /**
     * Credencial lista para autenticar: rol USUARIO_INTERNO vigente y persona activa.
     * Trae rol y persona en el mismo select (join fetch) porque el login siempre los necesita.
     */
    @Query("""
            select c from CredencialUsuario c
              join fetch c.rol r
              join fetch r.persona p
            where c.nombreUsuario = :nombreUsuario
              and c.organizacionId = :idOrganizacion
              and r.vigenciaHasta is null
              and p.estado = 'A'
            """)
    Optional<CredencialUsuario> buscarActivaPorNombreUsuario(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("nombreUsuario") String nombreUsuario);

    @Query("""
            select c from CredencialUsuario c
              join fetch c.rol r
              join fetch r.persona p
            where c.organizacionId = :idOrganizacion
              and p.id in :idsPersona
              and r.vigenciaHasta is null
            """)
    List<CredencialUsuario> buscarPorPersonas(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsPersona") Collection<Long> idsPersona);

    @Query("""
            select c from CredencialUsuario c
              join fetch c.rol r
              join fetch r.persona p
            where c.organizacionId = :idOrganizacion
              and p.id = :idPersona
              and r.vigenciaHasta is null
            """)
    Optional<CredencialUsuario> buscarPorPersona(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idPersona") long idPersona);

    /**
     * Lo que el filtro necesita en CADA request: si sus sesiones fueron
     * invalidadas (D-S0-12), si la cuenta esta capada por contrasena temporal
     * (§4.5) y con que <b>banda</b> entra al tenant (D-S0-8).
     * <p>
     * Es una <b>proyeccion estrecha</b> y no la entidad porque la consulta esta
     * en el camino caliente: traer credencial + rol + persona para leer un
     * instante y un booleano seria pagar tres tablas por dos datos. Los tres
     * datos viajan juntos porque se piden a la vez y en el mismo punto —
     * separarlos costaria dos consultas por peticion.
     * <p>
     * La banda se lee <b>por request y no del token</b> a proposito: es lo que
     * hace que degradar a alguien surta efecto en el acto en vez de esperar a
     * que caduque su token (R1 impide que la banda real viaje en el).
     * <p>
     * El {@code left join} es deliberado: una cuenta sin membresia activa sigue
     * pudiendo operar con su rol operativo; lo que no obtiene es gobierno.
     * <p>
     * Devuelve vacio cuando no hay credencial, que significa "nada que
     * restringir".
     */
    @Query("""
            select new com.controllocal.persistence.repositorio.EstadoDeAccesoFila(
                       c.sesionesInvalidasDesde, c.debeCambiarContrasena,
                       c.debeEnrolarMfa, uo.rol)
              from CredencialUsuario c
              join c.rol r
              left join UsuarioOrganizacion uo
                on uo.idUsuario = r.id
               and uo.organizacionId = c.organizacionId
               and uo.estado = 'A'
            where c.organizacionId = :idOrganizacion
              and r.persona.id = :idPersona
              and r.vigenciaHasta is null
            """)
    Optional<EstadoDeAccesoFila> estadoDeAcceso(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idPersona") long idPersona);

    /**
     * Padron de cuentas del tenant para <b>gobierno</b>: quien tiene acceso, con
     * que banda y en que estado administrativo.
     *
     * <p><b>Consulta pelada, y no es preferencia de estilo.</b> La primera
     * version resolvia banda, factor y codigos aqui mismo —un {@code left join}
     * a una entidad sin relacion declarada y dos subconsultas {@code count(...)}
     * en el {@code SELECT}— y devolvia <b>una unica fila</b> para todo el
     * tenant. Aqui solo quedan {@code join} por relacion; la banda y los codigos
     * se cruzan en el service con sus propias consultas.
     *
     * <p>Aparecen <b>todas</b> las cuentas con rol vigente, tengan o no
     * membresia. Filtrar por membresia dejaria fuera del padron justo a las
     * cuentas raras, que son las que hay que mirar.
     */
    @Query("""
            select new com.controllocal.persistence.repositorio.CuentaDeGobiernoFila(
                       p.id, r.id, c.id, p.nombresORazonSocial, c.nombreUsuario,
                       c.estadoAdministrativo,
                       c.debeCambiarContrasena, c.debeEnrolarMfa)
              from CredencialUsuario c
              join c.rol r
              join r.persona p
             where c.organizacionId = :idOrganizacion
               and r.vigenciaHasta is null
             order by p.nombresORazonSocial
            """)
    List<CuentaDeGobiernoFila> cuentasDeGobierno(@Param("idOrganizacion") long idOrganizacion);

    /** Credencial por nombre de usuario, sin exigir que la persona este activa. */
    @Query("""
            select c from CredencialUsuario c
              join fetch c.rol r
              join fetch r.persona p
            where c.nombreUsuario = :nombreUsuario
              and c.organizacionId = :idOrganizacion
              and r.vigenciaHasta is null
            """)
    Optional<CredencialUsuario> buscarPorNombreUsuario(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("nombreUsuario") String nombreUsuario);
}
