package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.DocumentoEncargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface DocumentoEncargoRepository extends JpaRepository<DocumentoEncargo,Long> {
    interface ContextoActual { Long getEncargo(); Long getAutor(); long getVersion(); }
    @Query("select d.idEncargo as encargo,d.idAutor as autor,d.version as version from DocumentoEncargo d where d.organizacionId=:org and d.id=:id")
    Optional<ContextoActual> contextoActual(@Param("org") long org,@Param("id") long id);
    interface CoberturaResponsable { boolean getDefinida(); Long getResponsableFirmado(); }
    /** RF017 gobierno sólo necesita saber si el cambio altera lo firmado, no leer el pacto. */
    @Query(value="""
       select jsonb_exists(d.contenido::jsonb,'responsableFirmado') as definida,
              cast(d.contenido::jsonb->>'responsableFirmado' as bigint) as responsableFirmado
         from documento_encargo d
        where d.organizacion_id=:org and d.id_documento_encargo=:id and d.id_captacion=:encargo
       """,nativeQuery=true)
    Optional<CoberturaResponsable> coberturaResponsable(@Param("org") long org,@Param("id") long id,
                                                       @Param("encargo") long encargo);
    Optional<DocumentoEncargo> findByOrganizacionIdAndId(long org,long id);
    Optional<DocumentoEncargo> findByOrganizacionIdAndArchivoRef(long org,java.util.UUID archivoRef);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentoEncargo d where d.organizacionId=:org and d.id=:id")
    Optional<DocumentoEncargo> bloquear(@Param("org") long org,@Param("id") long id);
}
