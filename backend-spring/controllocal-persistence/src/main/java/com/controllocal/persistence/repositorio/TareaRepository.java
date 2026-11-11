package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import java.time.Instant;

/**
 * Tareas de la bandeja del agente.
 *
 * <p>Aqui NO hay paginacion en SQL y no es un descuido: la bandeja se
 * <b>reconcilia</b> en cada lectura y luego se ordena y corta <b>en memoria</b>
 * por dos campos que no estan en la tabla —{@code prioridad} pesada y
 * {@code diasSinAccion}, que se derivan de la entidad de origen—. Paginar en
 * SQL daria un orden distinto al del cable. El conjunto esta acotado por
 * agente, asi que es pequeno por construccion.
 */
public interface TareaRepository extends JpaRepository<Tarea, Long> {
    interface CabeceraC7 {Long getId();String getEntidadTipo();Long getEntidadId();String getFuenteCausa();String getHuellaCausa();}
    String CABECERA_C7="select t.id as id,t.entidadTipo as entidadTipo,t.entidadId as entidadId,t.fuenteCausaC7 as fuenteCausa,t.huellaCausaC7 as huellaCausa from Tarea t ";
    @Query(CABECERA_C7+"where t.organizacionId=:org and t.agente.id=:agente")
    List<CabeceraC7> cabecerasPersonales(@Param("org")long org,@Param("agente")long agente);
    @Query(CABECERA_C7+"where t.organizacionId=:org and t.agente.id=:agente and t.id=:id")
    Optional<CabeceraC7> cabeceraPersonal(@Param("org")long org,@Param("agente")long agente,@Param("id")long id);
    @Query("select t from Tarea t where t.organizacionId=:org and t.agente.id=:agente and t.id in :ids order by t.fechaProgramada")
    List<Tarea> personalesAutorizadas(@Param("org")long org,@Param("agente")long agente,@Param("ids")Collection<Long> ids);
    @Query(value="select id_tarea from tarea where organizacion_id=:org and entidad_tipo=:entidad and entidad_id=:recurso and huella_causa_c7 is not null order by id_tarea desc limit 1",nativeQuery=true)
    Optional<Long> antecedenteC7(@Param("org")long org,@Param("entidad")String entidad,@Param("recurso")long recurso);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query(value="""
      insert into tarea(organizacion_id,tipo,entidad_tipo,entidad_id,id_rol_agente,descripcion,fecha_programada,estado,prioridad,
                        huella_causa_c7,fuente_causa_c7,estado_causa_c7,id_antecedente)
      values(:org,:tipo,:entidad,:recurso,:agente,:descripcion,clock_timestamp(),'P',:prioridad,:huella,:fuente,'VIGENTE',:antecedente)
      on conflict(organizacion_id,huella_causa_c7) where huella_causa_c7 is not null do nothing
      """,nativeQuery=true)
    int crearCausaC7(@Param("org")long org,@Param("tipo")String tipo,@Param("entidad")String entidad,@Param("recurso")long recurso,
                    @Param("agente")long agente,@Param("descripcion")String descripcion,@Param("prioridad")String prioridad,
                    @Param("huella")String huella,@Param("fuente")String fuente,@Param("antecedente")Long antecedente);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query(value="""
      update tarea t set id_rol_agente=s.id_rol_agente from solicitud_alquiler s
       where t.organizacion_id=:org and s.organizacion_id=t.organizacion_id and s.id_solicitud=t.entidad_id
         and t.entidad_tipo='SOLICITUD_ALQUILER' and t.huella_causa_c7 is not null
         and t.estado in('P','E') and t.estado_causa_c7='VIGENTE' and t.id_rol_agente<>s.id_rol_agente
      """,nativeQuery=true)
    int reubicarCausasSolicitudC7(@Param("org")long org);
    /** Efecto del cierre acreditado: no hidrata descripciones de otros responsables. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query(value="""
      update tarea set estado='C',fecha_completada=clock_timestamp(),
             estado_causa_c7=case when huella_causa_c7 is not null then 'RESUELTA' else estado_causa_c7 end
       where organizacion_id=:org and entidad_tipo=:tipo and entidad_id in (:ids) and estado in('P','E')
         and original_material_ia_c6 is null
         and (huella_causa_c7 is null or estado_causa_c7='VIGENTE')
      """,nativeQuery=true)
    int resolverPorCierre(@Param("org")long org,@Param("tipo")String tipo,@Param("ids")Collection<Long> ids);
    interface FotoParaAvisoC6 { Long getIdFoto();UUID getOriginal(); }
    @Query(value="select id_foto as \"idFoto\",archivo_ref as original from foto_propiedad where organizacion_id=:org and archivo_ref in (:originales)",nativeQuery=true)
    List<FotoParaAvisoC6> fotosParaAvisosC6(@Param("org")long org,@Param("originales")Collection<UUID> originales);
    @Query(value="select exists(select 1 from tarea where organizacion_id=:org and id_tarea=:id and id_rol_agente=:actor and original_material_ia_c6 is not null)",nativeQuery=true)
    boolean avisoPropioC6(@Param("org")long org,@Param("id")long id,@Param("actor")long actor);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query(value="""
      insert into tarea(organizacion_id,tipo,entidad_tipo,entidad_id,id_rol_agente,descripcion,fecha_programada,estado,prioridad,
          marca_causa,id_antecedente,original_material_ia_c6,revision_material_ia_c6,estado_causa_material_ia_c6)
      values(:org,'SEGUIMIENTO','FOTO_PROPIEDAD',:foto,:actor,:descripcion,:disponible,'P',null,
          :huella,:antecedente,:original,:revision,'VIGENTE') on conflict do nothing
      """,nativeQuery=true)
    int crearAvisoC6(@Param("org")long org,@Param("foto")long foto,@Param("actor")long actor,@Param("descripcion")String descripcion,
                    @Param("disponible")Instant disponible,@Param("huella")String huella,@Param("antecedente")Long antecedente,
                    @Param("original")UUID original,@Param("revision")long revision);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query(value="""
      insert into observacion_aviso_material_ia_c6(organizacion_id,id_tarea,original_ref,huella,revision,tipo)
      select organizacion_id,id_tarea,original_material_ia_c6,marca_causa,revision_material_ia_c6,:tipo
        from tarea t where organizacion_id=:org and id_tarea=:id and original_material_ia_c6 is not null
          and not exists(select 1 from (select o.huella,o.revision,o.tipo
                from observacion_aviso_material_ia_c6 o where o.organizacion_id=:org and o.id_tarea=:id
                order by o.id_observacion desc limit 1) ultima
              where ultima.huella=t.marca_causa and ultima.revision=t.revision_material_ia_c6 and ultima.tipo=:tipo)
      """,nativeQuery=true)
    int observarAvisoC6(@Param("org")long org,@Param("id")long id,@Param("tipo")String tipo);
    @Query(value="select id_tarea from tarea where organizacion_id=:org and id_rol_agente=:actor and original_material_ia_c6=:original and estado='P' and estado_causa_material_ia_c6='VIGENTE'",nativeQuery=true)
    Optional<Long> avisoAbiertoC6(@Param("org")long org,@Param("actor")long actor,@Param("original")UUID original);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query(value="""
        update tarea t set id_rol_agente=p.id_rol_agente from prospeccion p
        where t.organizacion_id=:org and p.organizacion_id=t.organizacion_id and p.id_prospeccion=t.entidad_id
          and t.entidad_tipo='PROSPECCION' and t.tipo='RECONTACTO' and t.marca_causa is not null
          and t.estado in ('P','E') and t.id_rol_agente<>p.id_rol_agente
        """,nativeQuery=true)
    int reubicarRecontactos(@Param("org") long org);
    @Query("select t from Tarea t where t.organizacionId=:org and t.tipo='RECONTACTO' and t.entidadTipo='PROSPECCION' and t.entidadId in :ids order by t.id desc")
    List<Tarea> recontactosDeProspecciones(@Param("org") long org,@Param("ids") java.util.Collection<Long> ids);
    @Query(value="""
        select t.id_tarea from tarea t
          join seguimiento_prospeccion previo on previo.organizacion_id=t.organizacion_id and previo.id_seguimiento=t.id_seguimiento_prospeccion
          join seguimiento_prospeccion actual on actual.organizacion_id=previo.organizacion_id and actual.id_seguimiento=:origen
         where t.organizacion_id=:org and t.tipo='RECONTACTO' and t.entidad_tipo='PROSPECCION'
           and previo.id_prospeccion=actual.id_prospeccion and previo.destinatario_clave=actual.destinatario_clave and previo.finalidad=actual.finalidad
         order by t.id_tarea desc limit 1
        """,nativeQuery=true)
    Optional<Long> antecedenteDeRecontacto(@Param("org") long org,@Param("origen") long origen);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query(value="""
        insert into tarea(organizacion_id,tipo,entidad_tipo,entidad_id,id_rol_agente,descripcion,fecha_programada,estado,prioridad,marca_causa,id_antecedente,id_seguimiento_prospeccion)
        values(:org,'RECONTACTO','PROSPECCION',:prospeccion,:agente,:descripcion,clock_timestamp(),'P',:prioridad,:marca,:antecedente,:origen)
        on conflict(organizacion_id,id_seguimiento_prospeccion) where estado in ('P','E') and id_seguimiento_prospeccion is not null do nothing
        """,nativeQuery=true)
    int crearRecontacto(@Param("org") long org,@Param("prospeccion") long prospeccion,@Param("agente") long agente,
                        @Param("descripcion") String descripcion,@Param("prioridad") String prioridad,
                        @Param("marca") String marca,@Param("antecedente") Long antecedente,@Param("origen") long origen);

    /**
     * Abiertas de una entidad concreta. Es lo que consume el <b>efecto 7</b> de
     * la cascada de F4: al cerrar el alquiler se dan por hechas las tareas de
     * la oportunidad, la solicitud, la captacion y el local.
     */
    @Query("""
            select t from Tarea t
            where t.organizacionId = :idOrganizacion
              and t.entidadTipo = :entidadTipo
              and t.entidadId = :entidadId
              and t.estado in ('P', 'E')
            """)
    List<Tarea> abiertasDeEntidad(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("entidadTipo") String entidadTipo,
                                  @Param("entidadId") long entidadId);

    /**
     * Las tareas abiertas que produjo un contrato concreto. Es lo que permite
     * cerrar EXACTAMENTE la revision de ese contrato al resolverla, en vez de
     * buscar "alguna tarea abierta del inmueble" y arriesgarse a cerrar la de
     * un contrato anterior del mismo local.
     */
    @Query("""
            select t from Tarea t
            where t.organizacionId = :idOrganizacion
              and t.idContratoOrigen = :idContrato
              and t.tipo = 'REVISION_INMUEBLE' and t.entidadTipo = 'INMUEBLE'
              and t.huellaCausaC7 is null
              and t.estado in ('P', 'E')
            """)
    List<Tarea> abiertasDeContratoOrigen(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idContrato") long idContrato);

    /** Para cancelar: acotada al tenant; que sea del agente lo comprueba el service. */
    @Query("""
            select t from Tarea t
              join fetch t.agente
            where t.organizacionId = :idOrganizacion and t.id = :id
            """)
    Optional<Tarea> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                @Param("id") long id);
}
