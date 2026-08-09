package com.controllocal.service.soporte;

/**
 * Descripciones CONGELADAS de los codigos de estado, tal como las emite
 * {@code CodigoEnum.getDescripcion()} de la v1. Son texto de cable: viajan tal
 * cual en el seguimiento comercial y en el avance por propiedad, y la pantalla
 * las usa como etiqueta y como valor de filtro, asi que un acento o una mayuscula
 * distintos rompen el filtro del cliente.
 *
 * <p>Un codigo desconocido se devuelve tal cual, y {@code null} sale como
 * {@code "-"} — el mismo relleno que el resto de columnas de texto ausentes.
 */
public final class Descripciones {

    private Descripciones() {
    }

    public static String prospeccion(String codigo) {
        return switch (texto(codigo)) {
            case "P" -> "Prospecto";
            case "C" -> "Contactado";
            case "R" -> "Reunion";
            case "E" -> "Propuesta entregada";
            case "S" -> "En seguimiento";
            case "T" -> "Captado";
            case "D" -> "Descartado";
            default -> texto(codigo);
        };
    }

    public static String captacion(String codigo) {
        return switch (texto(codigo)) {
            case "P" -> "Pendiente de revision";
            case "O" -> "Observada";
            case "R" -> "Rechazada";
            case "A" -> "Activa";
            case "C" -> "Cerrada";
            case "V" -> "Vencida";
            default -> texto(codigo);
        };
    }

    public static String oportunidad(String codigo) {
        return switch (texto(codigo)) {
            case "A" -> "Abierta";
            case "S" -> "Solicitud creada";
            case "N" -> "No continua";
            case "F" -> "Finalizada exitosa";
            case "X" -> "Finalizada no favorable";
            default -> texto(codigo);
        };
    }

    public static String solicitud(String codigo) {
        return switch (texto(codigo)) {
            case "G" -> "Registrada";
            case "E" -> "En revision";
            case "O" -> "Observada";
            case "A" -> "Aprobada";
            case "R" -> "Rechazada";
            case "D" -> "Desistida";
            case "C" -> "Cerrada";
            default -> texto(codigo);
        };
    }

    /**
     * El contrato es el unico que tiene relleno propio: la v1 emite
     * {@code "Alquilado"} cuando el estado falta, no {@code "-"}.
     */
    public static String contrato(String codigo) {
        return switch (codigo == null ? "" : codigo) {
            case "P" -> "En proceso";
            case "D" -> "Firmado";
            case "V" -> "Vigente";
            case "R" -> "Renovado";
            case "F" -> "Finalizado";
            case "S" -> "Rescindido";
            case "A" -> "Anulado";
            default -> "Alquilado";
        };
    }

    /** Razon de no continuidad (RF-017): el avance emite la descripcion, no el codigo. */
    public static String razonNoContinuidad(String codigo) {
        return switch (codigo == null ? "" : codigo) {
            case "P" -> "Precio";
            case "U" -> "Ubicacion";
            case "C" -> "Condiciones del contrato";
            case "L" -> "Local no adecuado";
            case "N" -> "Cliente no responde";
            case "E" -> "Encontro otra opcion";
            case "O" -> "Otro";
            default -> codigo == null ? "" : codigo;
        };
    }

    private static String texto(String codigo) {
        return codigo == null || codigo.isBlank() ? "-" : codigo;
    }
}
