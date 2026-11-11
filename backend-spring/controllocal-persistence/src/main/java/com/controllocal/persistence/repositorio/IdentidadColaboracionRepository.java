package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.PersonaRol;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

/** Proyección estricta de identidad aprobada; no recupera cuenta, credenciales, correo o teléfono. */
public interface IdentidadColaboracionRepository extends Repository<PersonaRol,Long> {
    interface ActorVigente {Long getPersona();String getRol();}
    @Query(value="""
        select pr.id_persona as persona,pr.tipo_rol as rol from persona_rol pr
        join usuario_organizacion u on u.organizacion_id=pr.organizacion_id and u.id_persona=pr.id_persona and u.rol=pr.tipo_rol and u.estado='A'
        join cuenta_acceso c on c.id_cuenta_acceso=u.id_usuario and c.estado='A'
        join organizacion o on o.id_organizacion=pr.organizacion_id and o.estado='A'
        where pr.organizacion_id=:org and pr.id_persona_rol=:rol and pr.tipo_rol in('AGENTE','BROKER')
          and pr.vigencia_desde<=:hoy and (pr.vigencia_hasta is null or pr.vigencia_hasta>=:hoy)
        """,nativeQuery=true)
    Optional<ActorVigente> actorVigente(@Param("org")long org,@Param("rol")long rol,@Param("hoy")LocalDate hoy);
    interface MiembroInvitado { Long getPersona();Long getResponsable();Long getVersion();Long getBroker();String getModalidad(); }
    @Query(value="""
        select pr.id_persona as persona,pr.id_persona_rol as responsable,u.version as version,
          s.id_rol_broker as broker,o.modalidad
        from persona_rol pr join usuario_organizacion u on u.organizacion_id=pr.organizacion_id and u.id_persona=pr.id_persona and u.estado='A' and u.rol='AGENTE'
        join cuenta_acceso ca on ca.id_cuenta_acceso=u.id_usuario and ca.estado='A'
        join organizacion o on o.id_organizacion=pr.organizacion_id and o.estado='A'
        join detalle_agente a on a.organizacion_id=pr.organizacion_id and a.id_persona_rol=pr.id_persona_rol
        left join supervision_agente s on s.organizacion_id=pr.organizacion_id and s.id_rol_agente=pr.id_persona_rol and s.fecha_fin is null
        where pr.organizacion_id=:org and pr.id_persona_rol=:rol and pr.tipo_rol='AGENTE'
          and pr.vigencia_desde<=:hoy and (pr.vigencia_hasta is null or pr.vigencia_hasta>=:hoy)
        """,nativeQuery=true)
    Optional<MiembroInvitado> miembroInvitado(@Param("org")long org,@Param("rol")long rol,@Param("hoy")LocalDate hoy);
    interface Identidad {
        String getNombreProfesional();String getInmobiliaria();String getModalidad();
    }
    @Query(value="select modalidad from organizacion where id_organizacion=:org and estado='A'",nativeQuery=true)
    Optional<String> modalidad(@Param("org") long org);
    @Query(value="""
            select ca.nombre_profesional_declarado as "nombreProfesional",
                   case when o.modalidad='EMPRESARIAL' then o.nombre else null end as inmobiliaria,
                   o.modalidad as modalidad
            from persona_rol pr
            join usuario_organizacion u on u.organizacion_id=pr.organizacion_id and u.id_persona=pr.id_persona
                 and u.estado='A' and u.rol='AGENTE'
            join cuenta_acceso ca on ca.id_cuenta_acceso=u.id_usuario and ca.estado='A'
            join organizacion o on o.id_organizacion=pr.organizacion_id and o.estado='A'
            where pr.organizacion_id=:org and pr.id_persona_rol=:responsable and pr.tipo_rol='AGENTE'
              and pr.vigencia_desde<=:hoy and (pr.vigencia_hasta is null or pr.vigencia_hasta>=:hoy)
            """,nativeQuery=true)
    Optional<Identidad> deResponsable(@Param("org") long org,@Param("responsable") long responsable,@Param("hoy") LocalDate hoy);
}
