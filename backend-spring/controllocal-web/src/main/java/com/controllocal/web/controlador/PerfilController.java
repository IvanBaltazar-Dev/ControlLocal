package com.controllocal.web.controlador;

import com.controllocal.service.ContrasenaService;
import com.controllocal.service.PerfilService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.almacen.AlmacenException;
import com.controllocal.web.dto.ContrasenaDtos.CambioContrasenaRequest;
import com.controllocal.web.dto.FotoPerfilRequest;
import com.controllocal.web.dto.FotoPerfilResponse;
import com.controllocal.web.dto.PerfilRequest;
import com.controllocal.web.dto.PerfilResponse;
import com.controllocal.web.http.ErrorAlmacenException;
import com.controllocal.web.seguridad.SesionActual;
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
 * Contrato congelado de {@code PerfilRest} <b>más</b> el cambio de contraseña,
 * que es aditivo: la v1 no lo tenía (H-02) y su pantalla Blazor era un mock.
 */
@RestController
@RequestMapping("perfil")
public class PerfilController {

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    private final PerfilService perfil;
    private final AlmacenDocumentos almacen;
    private final ContrasenaService contrasenas;

    public PerfilController(PerfilService perfil, AlmacenDocumentos almacen,
                            ContrasenaService contrasenas) {
        this.perfil = perfil;
        this.almacen = almacen;
        this.contrasenas = contrasenas;
    }

    /**
     * Cambio de contraseña del propio actor (§4.2). <b>Aditivo</b>: no existe
     * en la v1, y hasta hoy el {@code PUT} de brokers y agentes <b>ignoraba</b>
     * el campo en silencio.
     *
     * <p>Alcance implícito y no discutible, igual que el logout: solo puede
     * cambiar <b>su propia</b> contraseña, porque la persona sale del token y
     * no del cuerpo.
     *
     * <p>Responde <b>204</b> y deja la sesión muerta: cambiar la contraseña
     * invalida todas las sesiones de la cuenta, incluida la que hace la
     * llamada. Es intencionado — si una sesión robada sobreviviera al cambio,
     * el cambio no serviría de nada— y el SPA lo traduce en "vuelve a entrar".
     */
    @PostMapping("contrasena")
    public ResponseEntity<Void> cambiarContrasena(
            @RequestBody(required = false) CambioContrasenaRequest dto) {
        if (dto == null) {
            throw new ReglaNegocioException("La contrasena actual es obligatoria.");
        }
        char[] actual = aCaracteres(dto.contrasenaActual());
        char[] nueva = aCaracteres(dto.contrasenaNueva());
        try {
            contrasenas.cambiar(SesionActual.actor(), actual, nueva);
            return ResponseEntity.noContent().build();
        } finally {
            // Las contrasenas salen de un String del cuerpo JSON, asi que el
            // original sigue en el heap hasta que el GC lo recoja; limpiar la
            // copia es lo unico que esta en nuestra mano y no cuesta nada.
            java.util.Arrays.fill(actual, '\0');
            java.util.Arrays.fill(nueva, '\0');
        }
    }

    private static char[] aCaracteres(String valor) {
        return valor == null ? new char[0] : valor.toCharArray();
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
    public FotoPerfilResponse subirFoto(
            @RequestBody(required = false) FotoPerfilRequest dto) {
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

        try {
            AlmacenDocumentos.ArchivoGuardado guardado = almacen.guardar(
                    AlmacenDocumentos.carpetaDeTenant(
                            SesionActual.actor().idOrganizacion(), "perfiles"),
                    dto.nombreArchivo(), contenido, contentType);
            return new FotoPerfilResponse(
                    perfil.actualizarFoto(guardado.clave(), SesionActual.actor()));
        } catch (AlmacenException error) {
            throw new ErrorAlmacenException(
                    "No se pudo guardar la foto en el almacen: "
                            + error.getMessage());
        }
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
