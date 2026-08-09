package com.controllocal.web.almacen;

/**
 * Extension y content-type por nombre de archivo. Portado del
 * NombresArchivo de la v1, recortado a lo que la v2 usa: el saneo del
 * nombre y de la carpeta ya lo hace {@link AlmacenDisco} al construir la
 * clave, asi que aqui solo queda la lectura de la extension (validacion de
 * tipos permitidos) y la cabecera con la que se sirve el binario.
 */
public final class NombresArchivo {

    private NombresArchivo() {
    }

    /** Extension en minusculas CON el punto ({@code ".pdf"}), o cadena vacia. */
    public static String extension(String nombreOClave) {
        if (nombreOClave == null) {
            return "";
        }
        int punto = nombreOClave.lastIndexOf('.');
        return punto >= 0 ? nombreOClave.substring(punto).toLowerCase() : "";
    }

    public static String contentType(String nombreOClave) {
        return switch (extension(nombreOClave)) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }
}
