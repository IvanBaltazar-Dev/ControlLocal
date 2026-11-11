package com.controllocal.persistence.repositorio;

import com.controllocal.domain.disponibilidad.ConfirmacionDisponibilidadContextual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** El consumidor prueba el permiso del recurso/contexto ANTES de leer. Network usa sólo la proyección mínima. */
public interface ConfirmacionDisponibilidadContextualRepository extends JpaRepository<ConfirmacionDisponibilidadContextual,Long> {
    Optional<ConfirmacionDisponibilidadContextual> findByOrganizacionIdAndId(long org,long id);

    /** Ningún actor/fuente/evidencia privada se carga para después ocultarla en Network. */
    interface HechoParaMatching {
        Long getId();
        long getVersion();
        boolean getDisponible();
        String getPrecision();
        Instant getBaseFrescura();
        LocalDate getFechaSinHora();
        Instant getRegistradoEn();
    }
    @Query(value="""
        select id_confirmacion as id,version_registro as version,disponible,precision_hecho as precision,
               base_frescura as baseFrescura,fecha_sin_hora as fechaSinHora,registrado_en as registradoEn
          from confirmacion_disponibilidad_contextual
         where organizacion_id=:org and tipo_recurso=:tipo and id_recurso=:recurso
           and operacion=:operacion and aspecto=:aspecto and contexto=:contexto
         order by version_registro
        """,nativeQuery=true)
    List<HechoParaMatching> hechosParaMatching(@Param("org")long org,@Param("tipo")String tipo,
        @Param("recurso")long recurso,@Param("operacion")String operacion,@Param("aspecto")String aspecto,
        @Param("contexto")String contexto);
}

