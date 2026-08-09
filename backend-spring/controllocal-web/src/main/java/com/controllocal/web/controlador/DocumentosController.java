package com.controllocal.web.controlador;

import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.http.RecursoNoEncontradoException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contenido binario del almacen por clave opaca (contrato congelado del
 * DocumentosRest v1). El endpoint es PUBLICO: la clave no es adivinable
 * (capability) y asi el visor puede cargar archivos sin propagar el JWT
 * al navegador. Esta en la lista permitAll de ConfiguracionSeguridad.
 */
@RestController
@RequestMapping("documentos")
public class DocumentosController {

    private final AlmacenDocumentos almacen;

    public DocumentosController(AlmacenDocumentos almacen) {
        this.almacen = almacen;
    }

    @GetMapping("contenido")
    public ResponseEntity<byte[]> contenido(@RequestParam(required = false) String clave) {
        if (clave == null || clave.isBlank() || clave.contains("..") || clave.contains("\\")) {
            throw new ReglaNegocioException("Clave de documento invalida.");
        }
        AlmacenDocumentos.ArchivoDescargado archivo = almacen.abrir(clave)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento"));
        MediaType tipo = archivo.contentType() != null
                ? MediaType.parseMediaType(archivo.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(tipo)
                .header("Content-Disposition", "inline; filename=\"" + archivo.nombre() + "\"")
                .body(archivo.contenido());
    }
}
