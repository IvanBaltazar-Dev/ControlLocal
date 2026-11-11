package com.controllocal.persistence.repositorio;

import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDate;

/** Pertenencia local de una CuentaAcceso, con Persona contextual y gobierno separado del principal. */
public interface UsuarioOrganizacionRepository extends JpaRepository<UsuarioOrganizacion, Long> {
    @Query("""
       select new com.controllocal.persistence.repositorio.PertenenciaDeBajaFila(u.id,u.idUsuario,
       u.organizacionId,u.idPersona,u.estado,u.rol,u.administrar,u.version,u.altaRealEn,u.bajaRealEn)
       from UsuarioOrganizacion u where u.id=:id and u.organizacionId=:org
       """)
    Optional<PertenenciaDeBajaFila> paraBaja(@Param("org") long org,@Param("id") long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioOrganizacion u where u.id=:id and u.organizacionId=:org")
    Optional<UsuarioOrganizacion> bloquearParaBaja(@Param("org") long org,@Param("id") long id);

    @Query("""
       select u.idUsuario from UsuarioOrganizacion u,Persona p,PersonaRol r,CuentaAcceso c
       where u.organizacionId=:org and u.idPersona=:persona and u.estado='A' and u.administrar=true
       and exists(select o.id from Organizacion o where o.id=u.organizacionId and o.estado='A'
         and o.modalidad in ('INDIVIDUAL','EMPRESARIAL'))
       and p.id=u.idPersona and p.organizacionId=u.organizacionId and p.estado='A'
       and r.id=:rol and r.persona.id=p.id and r.organizacionId=u.organizacionId
       and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.ADMIN
       and r.vigenciaHasta is null and r.vigenciaDesde<=:hoy
       and c.id=u.idUsuario and c.estado='A' and c.correoConfirmadoEn is not null
       and c.debeCambiarContrasena=false and c.debeEnrolarMfa=false
       and exists(select f.id from FactorAutenticacion f where f.idCuenta=c.id and f.estado='A')
       """)
    Optional<Long> cuentaDeGobiernoActual(@Param("org") long org,@Param("persona") long persona,@Param("rol") long rol,@Param("hoy") LocalDate hoy);

    @Query("""
       select count(u) from UsuarioOrganizacion u,CuentaAcceso c,Persona p,PersonaRol r
       where u.organizacionId=:org and u.estado='A' and u.id<>:excluir and u.rol='BROKER'
         and c.id=u.idUsuario and c.estado='A' and c.correoConfirmadoEn is not null
         and p.id=u.idPersona and p.organizacionId=u.organizacionId and p.estado='A'
         and r.persona.id=p.id and r.organizacionId=u.organizacionId
         and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.BROKER
         and r.vigenciaHasta is null and r.vigenciaDesde<=:hoy
       """)
    long otrosBrokersVigentes(@Param("org") long org,@Param("excluir") long excluir,@Param("hoy") LocalDate hoy);

    @Query("""
       select count(u) from UsuarioOrganizacion u,CuentaAcceso c,Persona p,PersonaRol r
       where u.organizacionId=:org and u.estado='A' and u.id<>:excluir and u.administrar=true
         and c.id=u.idUsuario and c.estado='A' and c.correoConfirmadoEn is not null
         and p.id=u.idPersona and p.organizacionId=u.organizacionId and p.estado='A'
         and r.persona.id=p.id and r.organizacionId=u.organizacionId
         and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.ADMIN
         and r.vigenciaHasta is null and r.vigenciaDesde<=:hoy
       """)
    long otrosAdministradoresVigentes(@Param("org") long org,@Param("excluir") long excluir,@Param("hoy") LocalDate hoy);

    @Query("""
       select count(a) from UsuarioOrganizacion a,PersonaRol ra,PersonaRol rb,SupervisionAgente s
       where a.organizacionId=:org and a.estado='A' and a.rol='AGENTE'
         and ra.persona.id=a.idPersona and ra.organizacionId=a.organizacionId
         and ra.tipoRol=com.controllocal.domain.persona.enums.TipoRol.AGENTE and ra.vigenciaHasta is null
         and s.idRolAgente=ra.id and s.organizacionId=a.organizacionId and s.fechaFin is null
         and rb.id=s.idRolBroker and rb.organizacionId=a.organizacionId and rb.persona.id=:personaBroker
       """)
    long agentesActivosDeBroker(@Param("org") long org,@Param("personaBroker") long personaBroker);
    @Query("select u from UsuarioOrganizacion u where u.organizacionId=:org and u.idUsuario=:cuenta")
    Optional<UsuarioOrganizacion> buscarPorCuenta(@Param("org") long org,@Param("cuenta") long cuenta);
    @Query("""
       select distinct u.organizacionId from UsuarioOrganizacion u,Persona p,PersonaRol r,Organizacion o
       where u.idUsuario=:cuenta and u.estado='A' and u.administrar=true
         and p.id=u.idPersona and p.organizacionId=u.organizacionId and p.estado='A'
         and r.persona.id=p.id and r.organizacionId=u.organizacionId
         and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.ADMIN and r.vigenciaHasta is null
         and r.vigenciaDesde<=:hoy and o.id=u.organizacionId and o.estado='A'
       """)
    java.util.List<Long> gobiernosActualesDeCuenta(@Param("cuenta") long cuenta,@Param("hoy") LocalDate hoy);
    @Query("select u.idUsuario from UsuarioOrganizacion u where u.organizacionId=:org and u.idPersona=:persona and u.estado='A'")
    java.util.List<Long> cuentasActivasDePersona(@Param("org") long org,@Param("persona") long persona);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select u from UsuarioOrganizacion u where u.id=:id and u.idUsuario=:cuenta")
    Optional<UsuarioOrganizacion> bloquearParaContexto(@Param("id") long id,@Param("cuenta") long cuenta);

    /** Una lectura actual, sin filtrar pertenencias que la defensa de exclusividad debe contar. */
    @Query("""
       select new com.controllocal.persistence.repositorio.ContextoCuentaFila(u.id,u.organizacionId,
       u.idPersona,u.rol,u.administrar,u.estado,o.estado,o.nombre,o.modalidad,
       r.id,p.estado,r.tipoRol,r.vigenciaDesde,r.vigenciaHasta)
       from UsuarioOrganizacion u join Organizacion o on o.id=u.organizacionId
       left join Persona p on p.id=u.idPersona and p.organizacionId=u.organizacionId
       left join PersonaRol r on r.persona.id=p.id and r.organizacionId=u.organizacionId
       where u.idUsuario=:cuenta
       """)
    java.util.List<ContextoCuentaFila> lecturaContextosDeCuenta(@Param("cuenta") long cuenta);

    @Query("""
       select new com.controllocal.persistence.repositorio.MembresiaContextoFila(u.id,u.idUsuario,
       u.organizacionId,u.idPersona,u.rol,u.administrar,u.estado,o.estado,o.nombre,o.modalidad)
       from UsuarioOrganizacion u,Organizacion o where u.idUsuario=:cuenta and o.id=u.organizacionId
       """)
    java.util.List<MembresiaContextoFila> contextosDeCuenta(@Param("cuenta") long cuenta);

    /** Membresia activa por cuenta, para modificarla (alta, baja, cambio de banda). */
    @Query("""
            select uo from UsuarioOrganizacion uo
            where uo.organizacionId = :idOrganizacion
              and uo.idUsuario = :idCuenta
              and uo.estado = 'A'
            """)
    Optional<UsuarioOrganizacion> buscarActivaPorCuenta(@Param("idOrganizacion") long idOrganizacion,
                                                        @Param("idCuenta") long idCuenta);

    /**
     * Administradores <b>OPERATIVOS</b> del tenant, excluyendo una cuenta
     * (D-S0-37).
     *
     * <p>Contar membresias activas <b>no basta</b>: una cuenta suspendida, sin
     * segundo factor o con un cambio obligatorio pendiente figura en el
     * recuento y <b>no puede gobernar</b>. Operativo es:
     *
     * <pre>
     *   membresia TENANT_ADMIN activa
     *     + credencial activa
     *     + factor ACTIVO
     *     + sin debe_cambiar_contrasena
     *     + sin debe_enrolar_mfa
     * </pre>
     *
     * <p>{@code idCredencialExcluida} es la cuenta sobre la que se va a operar:
     * la pregunta no es "¿cuantos hay?" sino "¿cuantos QUEDARIAN?". Pasar 0 (o
     * un id que no existe) cuenta todos.
     *
     * <p>Es el gemelo en la aplicacion del trigger de V37: la guarda da el
     * mensaje, el trigger da la garantia. Ninguna de las dos sobra.
     */
    @Query("""
            select count(uo)
              from UsuarioOrganizacion uo,
                   CuentaAcceso cu,
                   FactorAutenticacion f,Persona p,PersonaRol r
             where uo.organizacionId = :idOrganizacion
               and uo.estado = 'A'
               and uo.administrar = true
               and cu.id = uo.idUsuario
               and cu.id <> :idCredencialExcluida
               and cu.estado = 'A'
               and cu.correoConfirmadoEn is not null
               and p.id=uo.idPersona and p.organizacionId=uo.organizacionId and p.estado='A'
               and r.persona.id=p.id and r.organizacionId=uo.organizacionId
               and r.tipoRol=com.controllocal.domain.persona.enums.TipoRol.ADMIN
               and r.vigenciaHasta is null and r.vigenciaDesde<=:hoy
               and cu.debeCambiarContrasena = false
               and cu.debeEnrolarMfa = false
               and f.idCuenta = cu.id
               and f.estado = com.controllocal.domain.seguridad.FactorAutenticacion.ACTIVO
            """)
    long contarAdministradoresOperativos(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idCredencialExcluida") long idCredencialExcluida,@Param("hoy") LocalDate hoy);

    @Query("select u from UsuarioOrganizacion u where u.organizacionId=:org and u.idPersona=:persona")
    java.util.List<UsuarioOrganizacion> dePersona(@Param("org") long org,@Param("persona") long persona);

    @Query("select u.idPersona,u.estado from UsuarioOrganizacion u where u.organizacionId=:org and u.idPersona in :personas and u.rol=:principal")
    java.util.List<Object[]> estadosDePersonasDelRol(@Param("org") long org,@Param("personas") java.util.Collection<Long> personas,@Param("principal") String principal);

    @Query("select u.idPersona,u.administrar from UsuarioOrganizacion u where u.organizacionId=:org and u.idPersona in :personas")
    java.util.List<Object[]> administracionesDePersonas(@Param("org") long org,@Param("personas") java.util.Collection<Long> personas);

    @Query("select count(u) from UsuarioOrganizacion u where u.idUsuario=:cuenta and u.estado='A' and u.organizacionId<>:org")
    long contarOtrasPertenenciasActivas(@Param("cuenta") long cuenta,@Param("org") long org);
}
