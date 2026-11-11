package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.DocumentoSolicitud;
import com.controllocal.persistence.query.ReferenciaArchivo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Lectura acotada de vínculos binarios vigentes; no interpreta rutas del proveedor. */
public interface ReferenciaArchivoRepository extends Repository<DocumentoSolicitud, Long> {
    @Query(nativeQuery = true, value = """
            SELECT d.archivo_ref AS referencia, 'DOCUMENTO_SOLICITUD' AS tipo,
                   d.id_documento AS "idReferencia", d.id_solicitud AS "idRecurso",
                   d.ruta_archivo AS "claveProveedor", 'A' AS estado
            FROM documento_solicitud d
            WHERE d.organizacion_id = :organizacion AND d.archivo_ref = :referencia
              AND d.ruta_archivo IS NOT NULL AND btrim(d.ruta_archivo) <> ''
            UNION ALL
            SELECT f.archivo_ref AS referencia, 'FOTO_PROPIEDAD' AS tipo,
                   f.id_foto AS "idReferencia", f.id_propiedad AS "idRecurso",
                   f.clave AS "claveProveedor", f.estado AS estado
            FROM foto_propiedad f
            WHERE f.organizacion_id = :organizacion AND f.archivo_ref = :referencia
            UNION ALL
            SELECT p.foto_ref AS referencia, 'FOTO_PERFIL' AS tipo,
                   p.id_persona AS "idReferencia", p.id_persona AS "idRecurso",
                   p.foto_clave AS "claveProveedor", 'A' AS estado
            FROM persona p
            WHERE p.organizacion_id = :organizacion AND p.foto_ref = :referencia
              AND p.foto_clave IS NOT NULL AND btrim(p.foto_clave) <> ''
            UNION ALL
            SELECT a.archivo_ref AS referencia, 'ARCHIVO_ENCARGO' AS tipo,
                   a.id_archivo_encargo AS "idReferencia",
                   coalesce(a.id_captacion,a.id_documento_encargo,a.id_archivo_encargo) AS "idRecurso",
                   a.clave_proveedor AS "claveProveedor", 'A' AS estado
            FROM archivo_encargo a
            WHERE a.organizacion_id = :organizacion AND a.archivo_ref = :referencia
            UNION ALL
            SELECT c.archivo_ref AS referencia, 'CARGA_TEMPORAL' AS tipo,
                   c.id_carga_archivo AS "idReferencia",
                   coalesce(c.id_encargo_previsto,c.id_documento_previsto,c.id_carga_archivo) AS "idRecurso",
                   c.clave_proveedor AS "claveProveedor", 'T' AS estado
            FROM carga_archivo_contextual c
            WHERE c.organizacion_id = :organizacion AND c.archivo_ref = :referencia
            """)
    List<ReferenciaArchivo> actuales(@Param("organizacion") long organizacion,
                                    @Param("referencia") UUID referencia);
}
