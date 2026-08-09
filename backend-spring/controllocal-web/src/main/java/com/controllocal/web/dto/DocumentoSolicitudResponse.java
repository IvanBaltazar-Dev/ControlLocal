package com.controllocal.web.dto;

import com.controllocal.service.DocumentoSolicitudService;

import java.time.LocalDateTime;

/**
 * Contrato CONGELADO: espejo de Dtos.DocumentoSolicitudResponse de la v1.
 * {@code tipoDocumento} viaja como codigo de negocio (I, R, V, P, E, G, D, O)
 * y {@code tipoNombre} como la descripcion del catalogo;
 * {@code rutaArchivo} es la clave del almacen con la que el visor pide el
 * binario a {@code GET /documentos/contenido}.
 */
public record DocumentoSolicitudResponse(Long id, Long idSolicitud, String tipoDocumento, String tipoNombre,
                                         String nombreArchivo, String rutaArchivo, LocalDateTime fechaEntrega,
                                         String estado, String resultadoRevision, String observaciones) {

    public static DocumentoSolicitudResponse desde(DocumentoSolicitudService.FichaDocumento f) {
        return new DocumentoSolicitudResponse(f.id(), f.idSolicitud(), f.tipoDocumento(), f.tipoNombre(),
                f.nombreArchivo(), f.rutaArchivo(), f.fechaEntrega(), f.estado(), f.resultadoRevision(),
                f.observaciones());
    }
}
