package com.controllocal.web.controlador;

import com.controllocal.service.PerfilService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.web.dto.FotoPerfilRequest;
import com.controllocal.web.dto.FotoPerfilResponse;
import com.controllocal.web.dto.PerfilRequest;
import com.controllocal.web.dto.PerfilResponse;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.almacen.CuotaArchivosB0;
import com.controllocal.web.almacen.SalidaBinariaPersistente;
import com.controllocal.service.planes.ClasificadorArchivosExistentesB0;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Locale;

/**
 * Perfil de la Persona contextual: contacto laboral y foto. La cuenta personal
 * tiene una autoridad independiente del contexto organizacional.
 */
@RestController
@RequestMapping("perfil")
public class PerfilController {

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    private final PerfilService perfil;
    private final RevalidacionDeSesion sesiones;
    private final SalidaBinariaPersistente salidas;private final CuotaArchivosB0 cuota;private final ClasificadorArchivosExistentesB0 clasificador;

    public PerfilController(PerfilService perfil,RevalidacionDeSesion sesiones,SalidaBinariaPersistente salidas,
                            CuotaArchivosB0 cuota,ClasificadorArchivosExistentesB0 clasificador) {
        this.perfil = perfil;
        this.sesiones = sesiones;
        this.salidas=salidas;this.cuota=cuota;this.clasificador=clasificador;
    }

    @GetMapping
    public PerfilResponse obtener() {
        return PerfilResponse.desde(perfil.obtener(SesionActual.actor()));
    }

    @PatchMapping
    public PerfilResponse actualizar(@RequestBody(required = false) PerfilRequest dto) {
        return PerfilResponse.desde(perfil.actualizarTelefono(
                dto == null ? null : dto.telefono(), SesionActual.actor()));
    }

    @PostMapping("foto")
    @Transactional
    public FotoPerfilResponse subirFoto(
            @RequestBody(required = false) FotoPerfilRequest dto) {
        var actor = sesiones.exigirVigente();
        if (dto == null || enBlanco(dto.nombreArchivo())
                || enBlanco(dto.contenidoBase64())) {
            throw new ReglaNegocioException("La foto es obligatoria.");
        }
        String contentType = contentType(dto.nombreArchivo());
        if (contentType == null) {
            throw new ReglaNegocioException(
                    "Solo se permiten imagenes PNG o JPG.");
        }
        byte[] contenido;
        try {
            contenido = Base64.getDecoder().decode(dto.contenidoBase64());
        } catch (IllegalArgumentException error) {
            throw new ReglaNegocioException(
                    "El contenido de la imagen (base64) es invalido.");
        }
        if (contenido.length == 0) {
            throw new ReglaNegocioException("La imagen esta vacia.");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new ReglaNegocioException(
                    "La imagen supera el maximo de "
                            + (TAMANO_MAXIMO / 1024 / 1024) + " MB.");
        }

        var sid=ContextoDeActuacion.de(SesionActual.sesion());
        var c=new CuotaArchivosB0.Contexto(clasificador.preparar(ClasificadorArchivosExistentesB0.PERFIL,Long.toString(actor.idPersona()),"FOTO_PERFIL",actor,sid),actor,sid);
        var guardado=salidas.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(actor.idOrganizacion()),java.util.UUID.randomUUID(),"FOTO-PERFIL:"+actor.idPersona(),
                "image/png".equals(contentType)?"png":"jpg",contentType,contenido,cuota.admision(c));
        if(!actor.equals(sesiones.exigirVigente()))throw new AccesoNoAutorizadoException();
        var resultado=new FotoPerfilResponse(perfil.actualizarFoto(guardado.clave(),actor));cuota.confirmar(c,guardado);return resultado;
    }

    private static String contentType(String nombre) {
        String valor = nombre == null
                ? "" : nombre.toLowerCase(Locale.ROOT);
        if (valor.endsWith(".png")) {
            return "image/png";
        }
        if (valor.endsWith(".jpg") || valor.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return null;
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }
}
