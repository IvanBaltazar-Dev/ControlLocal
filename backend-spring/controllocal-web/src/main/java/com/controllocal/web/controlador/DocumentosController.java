package com.controllocal.web.controlador;

import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.almacen.AlmacenException;
import com.controllocal.web.http.ErrorAlmacenException;
import com.controllocal.web.http.RecursoNoEncontradoException;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;

/**
 * Entrega privada por referencia Core. La referencia sólo selecciona el
 * vínculo: sesión, recurso, finalidad y versión se revalidan antes y después
 * de cargar bytes. La clave del proveedor nunca se acepta de quien descarga.
 */
@RestController
@RequestMapping("documentos")
public class DocumentosController {

    private final AlmacenDocumentos almacen;
    private final AccesoArchivoService acceso;
    private final RevalidacionDeSesion sesiones;

    public DocumentosController(AlmacenDocumentos almacen, AccesoArchivoService acceso,
                                RevalidacionDeSesion sesiones) {
        this.almacen = almacen;
        this.acceso = acceso;
        this.sesiones = sesiones;
    }

    @GetMapping("contenido")
    public ResponseEntity<byte[]> contenido(@RequestParam(required = false) String clave) {
        var actor = sesiones.exigirVigente();
        var referencia = acceso.resolver(clave, actor);
        AlmacenDocumentos.ArchivoDescargado archivo;
        try {
            archivo = almacen.abrir(referencia.claveProveedor())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Documento"));
        } catch (AlmacenException fallo) {
            throw new ErrorAlmacenException("No se pudo recuperar el archivo del almacén.");
        }
        var vigente = sesiones.exigirVigente();
        if (!actor.equals(vigente)) throw new AccesoNoAutorizadoException();
        acceso.revalidar(referencia, vigente);
        acceso.verificarContenido(referencia,archivo.contenido());
        MediaType tipo = archivo.contentType() != null
                ? MediaType.parseMediaType(archivo.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header("Content-Disposition", ContentDisposition.builder(admiteVistaInerte(tipo)?"inline":"attachment")
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(archivo.contenido());
    }

    private static boolean admiteVistaInerte(MediaType tipo) {
        return java.util.Set.of("application/pdf","image/png","image/jpeg","image/gif","image/webp","text/plain")
                .contains(tipo.getType()+"/"+tipo.getSubtype());
    }
}
