package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.PerfilCatalogoOpciones;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PerfilCatalogoOpcionesRepository extends JpaRepository<PerfilCatalogoOpciones,Long> {
    interface PerfilActual {
        Long getId(); long getVersion(); String getJurisdiccion();
        String getVersionFuente(); String getFuente();
    }
    String ACTUAL = """
            select p.id_perfil as id, p.version_definicion as version, p.jurisdiccion as jurisdiccion,
                   p.version_fuente as "versionFuente", p.fuente_definicion as fuente
              from perfil_catalogo_opciones p
             where p.organizacion_id=:idOrganizacion and p.id_catalogo_atributo=:idCatalogo
               and p.jurisdiccion=:jurisdiccion and p.activo
            """;
    @Query(value=ACTUAL,nativeQuery=true)
    List<PerfilActual> actuales(@Param("idOrganizacion") long org,@Param("idCatalogo") long id,@Param("jurisdiccion") String jurisdiccion);
    @Query(value=ACTUAL+" for share",nativeQuery=true)
    List<PerfilActual> bloquearActuales(@Param("idOrganizacion") long org,@Param("idCatalogo") long id,@Param("jurisdiccion") String jurisdiccion);
    @Query(value="select valor as codigo,rotulo from perfil_catalogo_opcion where id_perfil=:id and activo order by orden,valor",nativeQuery=true)
    List<CatalogoAtributoRepository.OpcionActual> opcionesActuales(@Param("id") long id);

    Optional<PerfilCatalogoOpciones> findByOrganizacionIdAndId(Long organizacionId,Long id);
    List<PerfilCatalogoOpciones> findByOrganizacionIdOrderByIdAsc(Long organizacionId);
}
