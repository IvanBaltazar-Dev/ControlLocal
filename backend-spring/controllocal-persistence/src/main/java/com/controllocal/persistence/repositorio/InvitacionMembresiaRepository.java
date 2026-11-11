package com.controllocal.persistence.repositorio;

import com.controllocal.domain.organizacion.InvitacionMembresia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvitacionMembresiaRepository extends JpaRepository<InvitacionMembresia, Long> {
    interface ContextoInvitacion {
        long getOrganizacion();
        String getPrincipal();
        Long getSupervisor();
        String getCorreo();
        java.time.Instant getAceptadaEn();
        java.time.Instant getRetiradaEn();
    }
    /** Snapshot inmutable mínimo para ordenar candados; todavía no permite aceptar ni leer la ficha. */
    @Query(value="""
        select organizacion_id as organizacion,rol_principal as principal,id_broker_supervisor as supervisor,
          correo_cuenta as correo,aceptada_en as "aceptadaEn",retirada_en as "retiradaEn"
        from invitacion_membresia where referencia=:referencia
        """,nativeQuery=true)
    Optional<ContextoInvitacion> contextoDe(@Param("referencia") UUID referencia);
    /** Cuenta WRITE se obtiene antes: dos invitaciones no pueden crear dos pertenencias activas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvitacionMembresia i where i.referencia=:referencia")
    Optional<InvitacionMembresia> bloquearParaAceptar(@Param("referencia") UUID referencia);

    /** Comprueba el reloj real después de esperar los candados; un rechazo revierte toda el alta. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
        update invitacion_membresia set aceptada_en=clock_timestamp(), id_cuenta_aceptante=:cuenta,
          id_membresia_aceptada=:membresia, id_persona_aceptada=:persona, id_rol_aceptado=:rol,
          version=version+1 where referencia=:referencia and aceptada_en is null
            and retirada_en is null and vence_en>clock_timestamp()
        """, nativeQuery = true)
    int consumir(@Param("referencia") UUID referencia, @Param("cuenta") long cuenta,
                 @Param("membresia") long membresia, @Param("persona") long persona,
                 @Param("rol") Long rol);

    /** Consulta fresca de gobierno; un rol escrito en el cuerpo o en un Actor antiguo no basta. */
    @Query(value = """
        select exists(select 1 from usuario_organizacion u
          join cuenta_acceso c on c.id_cuenta_acceso=u.id_usuario
          join organizacion o on o.id_organizacion=u.organizacion_id and o.estado='A'
          join persona p on p.id_persona=u.id_persona and p.organizacion_id=u.organizacion_id and p.estado='A'
          join persona_rol r on r.id_persona=p.id_persona and r.organizacion_id=u.organizacion_id
        where u.organizacion_id=:org and u.id_persona=:persona and u.estado='A' and u.administrar
          and c.estado='A' and c.correo_confirmado_en is not null
          and r.id_persona_rol=:rol and r.tipo_rol='ADMIN' and r.vigencia_hasta is null
          and r.vigencia_desde<=(current_timestamp at time zone 'America/Lima')::date)
        """, nativeQuery = true)
    boolean gobiernoVigente(@Param("org") long org, @Param("persona") long persona,
                            @Param("rol") long rol);

    interface SupervisorActual {
        long getCuenta();
        long getMembresia();
        long getPersona();
    }

    /** Referencias actuales, sin hidratar una ficha comercial. El servicio bloquea en orden canónico. */
    @Query(value = """
        select u.id_usuario as cuenta, u.id_usuario_organizacion as membresia, p.id_persona as persona
        from detalle_broker b
          join persona_rol r on r.id_persona_rol=b.id_persona_rol and r.organizacion_id=b.organizacion_id
          join persona p on p.id_persona=r.id_persona and p.organizacion_id=b.organizacion_id
          join usuario_organizacion u on u.id_persona=p.id_persona and u.organizacion_id=b.organizacion_id
          join cuenta_acceso c on c.id_cuenta_acceso=u.id_usuario
          join organizacion o on o.id_organizacion=b.organizacion_id
        where b.organizacion_id=:org and b.id_persona_rol=:supervisor
          and r.tipo_rol='BROKER' and r.vigencia_hasta is null
          and r.vigencia_desde<=(current_timestamp at time zone 'America/Lima')::date
          and p.estado='A' and u.estado='A' and u.rol='BROKER' and c.estado='A'
          and c.correo_confirmado_en is not null and o.estado='A'
        """, nativeQuery = true)
    Optional<SupervisorActual> supervisorActual(@Param("org") long org, @Param("supervisor") long supervisor);

    @Query("""
        select count(u)>0 from UsuarioOrganizacion u where u.organizacionId=:org
        and u.idPersona=:persona and u.idUsuario<>:cuenta
        """)
    boolean personaLigadaAOtraCuenta(@Param("org") long org, @Param("persona") long persona,
                                     @Param("cuenta") long cuenta);
}
