package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.RequerimientoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Perfiles de busqueda. Las consultas traen los distritos en el mismo select
 * porque tanto la respuesta del cable como el matching de cartera los
 * necesitan siempre (evita el N+1 de la coleccion LAZY).
 */
public interface RequerimientoClienteRepository extends JpaRepository<RequerimientoCliente, Long> {

    interface ContextoDemanda {
        Long getId(); Long getResponsable(); Long getPersonaInteresada();
        long getVersion(); String getEstado();
        String getOperacion();
        java.time.LocalDate getVigenteDesde(); java.time.LocalDate getVigenteHasta();
    }

    /** Guarda mínima previa a cargar cliente, datos o criterios. */
    @Query("""
        select r.id as id,r.idRolResponsable as responsable,r.idPersonaInteresada as personaInteresada,
          r.version as version,r.estado as estado,r.operacion as operacion,r.vigenteDesde as vigenteDesde,r.vigenteHasta as vigenteHasta
        from RequerimientoCliente r where r.organizacionId=:org and r.id=:id
        """)
    Optional<ContextoDemanda> contexto(@Param("org") long org,@Param("id") long id);

    /** Candidatos mínimos autorizados antes de recuperar un criterio o Cliente. */
    @Query("""
        select r.id as id,r.idRolResponsable as responsable,r.idPersonaInteresada as personaInteresada,
          r.version as version,r.estado as estado,r.operacion as operacion,r.vigenteDesde as vigenteDesde,r.vigenteHasta as vigenteHasta
        from RequerimientoCliente r where r.organizacionId=:org and r.idRolResponsable in :responsables
          and r.estado='A' and (r.vigenteDesde is null or r.vigenteDesde<=:hoy)
          and (r.vigenteHasta is null or r.vigenteHasta>=:hoy) order by r.id
        """)
    List<ContextoDemanda> referenciasVigentes(@Param("org") long org,@Param("responsables") List<Long> responsables,
        @Param("hoy") java.time.LocalDate hoy);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RequerimientoCliente r where r.organizacionId=:org and r.id=:id")
    Optional<RequerimientoCliente> bloquear(@Param("org") long org,@Param("id") long id);

    @Query("""
        select r from RequerimientoCliente r left join fetch r.distritos
        where r.organizacionId=:org and r.idRolResponsable=:responsable
          and r.idPersonaInteresada in (select pr.persona.id from PersonaRol pr,DetalleCliente dc
            where pr.organizacionId=:org and pr.id=:cliente
              and pr.tipoRol=com.controllocal.domain.persona.enums.TipoRol.CLIENTE
              and dc.id=pr.id and dc.organizacionId=pr.organizacionId)
        order by r.id desc
        """)
    List<RequerimientoCliente> delClienteYResponsable(@Param("org") long org,
        @Param("cliente") long cliente,@Param("responsable") long responsable);

    /** Sólo comprueba contexto actual; no devuelve Persona/Cliente de otro recurso. */
    @Query("""
        select count(r)>0 from PersonaRol r,UsuarioOrganizacion u,CuentaAcceso c,Organizacion o
        where r.id=:rol and r.organizacionId=:org and r.persona.id=:persona
          and r.persona.estado='A' and r.vigenciaHasta is null and r.vigenciaDesde<=:hoy
          and u.organizacionId=:org and u.idPersona=:persona and u.estado='A' and u.rol=:atribucion
          and ((:atribucion='AGENTE' and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.AGENTE)
            or (:atribucion='BROKER' and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.BROKER))
          and c.id=u.idUsuario and c.estado='A' and c.correoConfirmadoEn is not null
          and o.id=:org and o.estado='A'
        """)
    boolean contextoOperativoActual(@Param("org") long org,@Param("persona") long persona,
        @Param("rol") long rol,@Param("atribucion") String atribucion,@Param("hoy") java.time.LocalDate hoy);

    @Query("""
            select distinct r from RequerimientoCliente r
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion and r.id = :id
            """)
    Optional<RequerimientoCliente> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                               @Param("id") long id);

    @Query("""
            select distinct r from RequerimientoCliente r
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion and r.cliente.id = :idRolCliente
            order by r.id desc
            """)
    List<RequerimientoCliente> listarPorCliente(@Param("idOrganizacion") long idOrganizacion,
                                                @Param("idRolCliente") long idRolCliente);

    /**
     * Base del matching en el sentido propiedad→clientes: solo los ACTIVO
     * cuentan como demanda vigente, y el cliente viene con su persona porque
     * la coincidencia muestra su nombre.
     */
    @Query("""
            select distinct r from RequerimientoCliente r
              join fetch r.cliente cli
              join fetch cli.rol cliRol
              join fetch cliRol.persona
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion and r.estado = 'A'
            """)
    List<RequerimientoCliente> listarActivos(@Param("idOrganizacion") long idOrganizacion);

    @Query("""
            select distinct r from RequerimientoCliente r
              left join fetch r.distritos
            where r.organizacionId = :idOrganizacion
              and r.estado = 'A' and r.cliente.id = :idRolCliente
            """)
    List<RequerimientoCliente> listarActivosPorCliente(@Param("idOrganizacion") long idOrganizacion,
                                                       @Param("idRolCliente") long idRolCliente);
}
