package com.controllocal.rest.almacen;

/**
 * Saneo de nombres y resolucion de content-type por extension. Equivale al
 * ValidadorArchivos/TiposContenido del frontend, para que la clave generada en el
 * backend sea segura (sin rutas ni caracteres especiales) y el contenido se sirva
 * con la cabecera correcta (PDF/imagen en linea).
 */
public final class NombresArchivo {

    private NombresArchivo() {
    }

    /** Nombre de archivo seguro: sin rutas, solo alfanumericos, guion y guion bajo. */
    public static String nombreSeguro(String nombre) {
        String soloNombre = quitarRuta(nombre == null ? "" : nombre);
        int punto = soloNombre.lastIndexOf('.');
        String base = punto >= 0 ? soloNombre.substring(0, punto) : soloNombre;
        String extension = punto >= 0 ? soloNombre.substring(punto) : "";

        StringBuilder limpio = new StringBuilder();
        for (char c : base.toCharArray()) {
            limpio.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        }
        String resultado = limpio.toString();
        if (resultado.isBlank()) {
            resultado = "documento";
        }
        if (resultado.length() > 80) {
            resultado = resultado.substring(0, 80);
        }
        return resultado + extensionSegura(extension);
    }

    /** Carpeta segura para agrupar archivos por expediente. */
    public static String carpetaSegura(String carpeta) {
        String seguro = nombreSeguro((carpeta == null ? "" : carpeta) + ".carpeta");
        return seguro.substring(0, seguro.length() - ".carpeta".length());
    }

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

    private static String extensionSegura(String extension) {
        StringBuilder limpio = new StringBuilder(".");
        for (char c : extension.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                limpio.append(c);
            }
        }
        return limpio.length() == 1 ? "" : limpio.toString();
    }

    private static String quitarRuta(String nombre) {
        String normalizado = nombre.replace('\\', '/');
        int barra = normalizado.lastIndexOf('/');
        return barra >= 0 ? normalizado.substring(barra + 1) : normalizado;
    }
}
