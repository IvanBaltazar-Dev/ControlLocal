package com.controllocal.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Casos de uso del expediente documental de la solicitud: el agente entrega
 * los documentos y el broker los revisa uno a uno o en bloque. Los records
 * espejan el contrato CONGELADO (Dtos.DocumentoSolicitudRequest/Response v1).
 *
 * <p>Maquina: R Registrado -&gt; {V Validado | O Observado}, con el resultado
 * de revision P Pendiente / C Conforme / O Observado al lado. El estado lo
 * mueve {@code Transiciones}, asi que —a diferencia de la v1— cada revision
 * deja fila en {@code historial_estado}.
 *
 * <p><b>Frontera con la capa web</b>: aqui NO entra ningun binario. El
 * contenido (base64, octet-stream o por trozos), su extension, su tamano y el
 * almacen hibrido son cosa del controlador, igual que en las fotos de F2; a
 * este service solo llega el metadato ya resuelto, con {@code rutaArchivo} =
 * clave del almacen.
 */
public interface DocumentoSolicitudService {

    /** Espejo de DocumentoSolicitudRequest sin el contenido: {@code rutaArchivo} es la clave del almacen. */
    record DatosDocumento(String tipoDocumento, String nombreArchivo, String rutaArchivo,
                          String observaciones) {
    }

    /** Espejo de DocumentoSolicitudResponse. {@code tipoDocumento} es el codigo del cable (I, R, V...). */
    record FichaDocumento(Long id, Long idSolicitud, String tipoDocumento, String tipoNombre,
                          String nombreArchivo, String rutaArchivo, LocalDateTime fechaEntrega,
                          String estado, String resultadoRevision, String observaciones) {
    }

    List<FichaDocumento> listarPorSolicitud(long idSolicitud, Actor actor);

    /** Alta del AGENTE. El documento nace Registrado con revision Pendiente. */
    FichaDocumento registrar(long idSolicitud, DatosDocumento datos, Actor actor);

    /** Revision individual del BROKER/ADMIN: Conforme valida, Observado exige el porque. */
    FichaDocumento revisar(long idSolicitud, long idDocumento, String resultado, String observaciones,
                           Actor actor);

    /**
     * Atajo "validar todos" del dialogo de aprobacion: deja conformes en
     * bloque SOLO los pendientes. Respeta los observados —son un hallazgo
     * deliberado del broker— y devuelve el expediente completo.
     */
    List<FichaDocumento> conformarPendientes(long idSolicitud, Actor actor);
}
