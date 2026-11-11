package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Alerta;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/** Cabeceras sin mensajes; Core autoriza antes de hidratar, contar o paginar. */
public interface AlertaRepository extends JpaRepository<Alerta,Long> {
    interface NombreAgente {Long getId();String getNombre();}
    @Query("select a.id as id,p.nombresORazonSocial as nombre from DetalleAgente a join a.rol r join r.persona p where a.organizacionId=:org and a.id in :ids")
    List<NombreAgente> nombresDeAgentes(@Param("org")long org,@Param("ids")Collection<Long> ids);
    interface Cabecera {
        Long getId(); String getTipo(); String getEntidadTipo(); Long getEntidadId();
        Long getIdAgente(); String getMarcaCausa(); String getEstado();
        String getHuellaCausaC7();String getFuenteCausaC7();String getEstadoCausaC7();
    }
    String CABECERA="select a.id as id,a.tipo as tipo,a.entidadTipo as entidadTipo,a.entidadId as entidadId,"
            +"a.agente.id as idAgente,a.marcaCausa as marcaCausa,a.estado as estado,"
            +"a.huellaCausaC7 as huellaCausaC7,a.fuenteCausaC7 as fuenteCausaC7,a.estadoCausaC7 as estadoCausaC7 from Alerta a ";

    @Query(CABECERA+"where a.organizacionId=:org and a.estado='A' and (a.huellaCausaC7 is not null or :global=true or a.agente.id in :roles)")
    List<Cabecera> cabecerasActivas(@Param("org") long org,@Param("global") boolean global,@Param("roles") Collection<Long> roles);

    @Query(CABECERA+"where a.organizacionId=:org and a.id=:id")
    Optional<Cabecera> cabecera(@Param("org") long org,@Param("id") long id);

    @Query(CABECERA+"where a.organizacionId=:org and a.tipo='SIN_RESPUESTA' and a.entidadTipo='PROSPECCION' and a.entidadId in :prospecciones")
    List<Cabecera> cabecerasRecontacto(@Param("org") long org,@Param("prospecciones") Collection<Long> prospecciones);

    @Query(value="select a from Alerta a "
            +"where a.organizacionId=:org and a.estado='A' and a.id in :permitidas order by a.fechaGeneracion desc,a.id desc",
            countQuery="select count(a) from Alerta a where a.organizacionId=:org and a.estado='A' and a.id in :permitidas")
    Page<Alerta> buscarAutorizadas(@Param("org") long org,@Param("permitidas") Collection<Long> permitidas,Pageable pagina);

    @Query(value="select id_alerta from alerta where organizacion_id=:org and entidad_tipo=:tipo and entidad_id=:recurso and tipo=:finalidad and huella_causa_c7 is not null and huella_causa_c7<>:huella order by id_alerta desc limit 1",nativeQuery=true)
    Optional<Long> antecedenteC7(@Param("org")long org,@Param("tipo")String tipo,@Param("recurso")long recurso,@Param("finalidad")String finalidad,@Param("huella")String huella);
    @Modifying(flushAutomatically=true)
    @Query(value="""
      insert into alerta(organizacion_id,tipo,severidad,entidad_tipo,entidad_id,id_rol_agente,mensaje,estado,fecha_generacion,
                         huella_causa_c7,fuente_causa_c7,estado_causa_c7,id_antecedente_c7)
      values(:org,:tipo,:severidad,:entidad,:recurso,:agente,:mensaje,'A',clock_timestamp(),:huella,:fuente,'VIGENTE',:antecedente)
      on conflict(organizacion_id,huella_causa_c7) where huella_causa_c7 is not null do nothing
      """,nativeQuery=true)
    int crearCausaC7(@Param("org")long org,@Param("tipo")String tipo,@Param("severidad")String severidad,@Param("entidad")String entidad,
                    @Param("recurso")long recurso,@Param("agente")long agente,@Param("mensaje")String mensaje,
                    @Param("huella")String huella,@Param("fuente")String fuente,@Param("antecedente")Long antecedente);
    @Modifying(flushAutomatically=true)
    @Query(value="""
      update alerta set estado='T',fecha_resolucion=clock_timestamp()
       where organizacion_id=:org and id_alerta=:id and estado='A'
         and marca_causa is not distinct from :marca and huella_causa_c7 is not distinct from :huella
         and (huella_causa_c7 is null or estado_causa_c7='VIGENTE')
      """,nativeQuery=true)
    int atenderSiActiva(@Param("org")long org,@Param("id")long id,@Param("marca")String marca,@Param("huella")String huella);

    @Modifying(flushAutomatically=true)
    @Query(value="""
        insert into alerta(organizacion_id,tipo,severidad,entidad_tipo,entidad_id,id_rol_agente,mensaje,estado,fecha_generacion,marca_causa)
        values(:org,'SIN_RESPUESTA','MEDIA','PROSPECCION',:prospeccion,:responsable,
          'Revisa el recontacto pendiente del propietario o representante.','A',clock_timestamp(),:marca)
        on conflict do nothing
        """,nativeQuery=true)
    int crearRecontacto(@Param("org") long org,@Param("prospeccion") long prospeccion,
                        @Param("responsable") long responsable,@Param("marca") String marca);

    @Modifying(flushAutomatically=true)
    @Query(value="update alerta set id_rol_agente=:responsable where organizacion_id=:org and id_alerta=:id and marca_causa=:marca and estado='A'",nativeQuery=true)
    int actualizarResponsableRecontacto(@Param("org") long org,@Param("id") long id,
                                        @Param("marca") String marca,@Param("responsable") long responsable);

    @Modifying(flushAutomatically=true)
    @Query(value="update alerta set estado='D',fecha_resolucion=clock_timestamp() where organizacion_id=:org and id_alerta=:id and marca_causa=:marca and estado='A'",nativeQuery=true)
    int retirarRecontactoSustituido(@Param("org") long org,@Param("id") long id,@Param("marca") String marca);
}

