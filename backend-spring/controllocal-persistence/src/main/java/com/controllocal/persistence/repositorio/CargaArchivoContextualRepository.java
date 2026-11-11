package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.CargaArchivoContextual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface CargaArchivoContextualRepository extends JpaRepository<CargaArchivoContextual,Long>{
    interface Actual{
        Long getId();UUID getReferencia();long getVersion();Long getAutor();Long getEncargo();Long getDocumento();
        String getFinalidad();long getVersionDestino();String getClave();String getNombre();String getContentType();String getHuella();
    }
    @Query(value="""
       select id_carga_archivo as id,archivo_ref as referencia,version,id_rol_autor as autor,
              id_encargo_previsto as encargo,id_documento_previsto as documento,finalidad,
              version_destino as versionDestino,clave_proveedor as clave,nombre,content_type as contentType,huella
         from carga_archivo_contextual where organizacion_id=:org and archivo_ref=:ref
       """,nativeQuery=true)
    Optional<Actual> actual(@Param("org") long org,@Param("ref") UUID ref);
    Optional<CargaArchivoContextual> findByOrganizacionIdAndReferencia(long org,UUID referencia);
}
