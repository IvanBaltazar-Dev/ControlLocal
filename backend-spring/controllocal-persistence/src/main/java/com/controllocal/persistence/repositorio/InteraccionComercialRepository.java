package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.InteraccionComercial;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/** Cabeceras sin contenido privado; Core autoriza cada vínculo antes de hidratar. */
public interface InteraccionComercialRepository extends JpaRepository<InteraccionComercial,Long> {
    interface Cabecera {
        long getId();long getVersion();long getAgente();String getAutorizacion();String getDiccionario();
        Long getOportunidad();Long getProspeccion();Long getEncargo();Long getCliente();
    }
    /** DTO de lectura masiva: evita un proxy de proyección por cada comunicación. */
    record CabeceraActual(long id,long version,long agente,String autorizacion,String diccionario,
                          Long oportunidad,Long prospeccion,Long encargo,Long cliente) implements Cabecera {
        public long getId(){return id;}public long getVersion(){return version;}public long getAgente(){return agente;}
        public String getAutorizacion(){return autorizacion;}public String getDiccionario(){return diccionario;}
        public Long getOportunidad(){return oportunidad;}public Long getProspeccion(){return prospeccion;}
        public Long getEncargo(){return encargo;}public Long getCliente(){return cliente;}
    }
    @Query(value="""
        with recursive fuentes(id) as (
          select id_interaccion from interaccion_comercial where organizacion_id=:org and id_interaccion in (:ids)
          union
          select a.id from fuentes f
          join interaccion_comercial i on i.organizacion_id=:org and i.id_interaccion=f.id
          cross join lateral (
            select (i.autorizacion_json::jsonb->>'anterior')::bigint as id
             where i.autorizacion_json::jsonb->>'anterior' is not null
            union
            select (r->>'id')::bigint from jsonb_array_elements(coalesce(
              nullif(i.autorizacion_json::jsonb->'consultasOriginales','null'::jsonb),'[]'::jsonb)) r
          ) a
        )
        select i.id_interaccion as id,i.version,i.id_rol_agente as agente,
          i.autorizacion_json as autorizacion,i.version_diccionario as diccionario,
          i.id_oportunidad as oportunidad,i.id_prospeccion as prospeccion,i.id_captacion as encargo,i.id_rol_cliente as cliente
        from fuentes f join interaccion_comercial i on i.organizacion_id=:org and i.id_interaccion=f.id
        order by i.id_interaccion
        """,nativeQuery=true)
    List<Cabecera> antecedentes(@Param("org") long org,@Param("ids") Collection<Long> ids);
    String CABECERA="select new com.controllocal.persistence.repositorio.InteraccionComercialRepository$CabeceraActual(i.id,i.version,i.agente.id,i.autorizacionJson,i.versionDiccionario,i.oportunidad.id,i.prospeccion.id,i.captacion.id,i.cliente.id) from InteraccionComercial i ";
    @Query(CABECERA+"where i.organizacionId=:org and i.id=:id and i.agente.id in :roles")
    Optional<Cabecera> cabecera(@Param("org") long org,@Param("id") long id,@Param("roles") Collection<Long> roles);
    @Query("select i from InteraccionComercial i where i.organizacionId=:org and i.id=:id")
    Optional<InteraccionComercial> actual(@Param("org") long org,@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InteraccionComercial i where i.organizacionId=:org and i.id=:id")
    Optional<InteraccionComercial> bloquear(@Param("org") long org,@Param("id") long id);
    @Query("select i from InteraccionComercial i where i.organizacionId=:org and i.id in :ids")
    List<InteraccionComercial> actuales(@Param("org") long org,@Param("ids") Collection<Long> ids);
}
