package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.ArchivoEncargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ArchivoEncargoRepository extends JpaRepository<ArchivoEncargo,Long> {
    interface ReferenciaActual {
        Long getId(); UUID getArchivoRef(); Long getEncargo(); Long getDocumento(); Long getAutor();
        String getFinalidad(); String getClaveProveedor(); String getNombre(); String getContentType();
        String getHuella(); long getVersion();
    }
    @Query(value="""
       select a.id_archivo_encargo as id,a.archivo_ref as archivoRef,a.id_captacion as encargo,
              a.id_documento_encargo as documento,a.id_rol_autor as autor,a.finalidad as finalidad,
              a.clave_proveedor as claveProveedor,a.nombre as nombre,a.content_type as contentType,
              a.huella as huella,a.version as version
         from archivo_encargo a where a.organizacion_id=:org and a.archivo_ref=:ref
       """,nativeQuery=true)
    Optional<ReferenciaActual> actual(@Param("org") long org,@Param("ref") UUID ref);
    Optional<ArchivoEncargo> findByOrganizacionIdAndArchivoRef(long org,UUID ref);
    List<ArchivoEncargo> findAllByOrganizacionIdAndArchivoRefIn(long org,Collection<UUID> refs);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ArchivoEncargo a where a.organizacionId=:org and a.archivoRef in :refs order by a.id")
    List<ArchivoEncargo> bloquear(@Param("org") long org,@Param("refs") Collection<UUID> refs);
}
