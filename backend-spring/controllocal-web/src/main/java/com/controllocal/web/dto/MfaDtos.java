package com.controllocal.web.dto;

import com.controllocal.service.MfaService;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cuerpos del segundo factor (V37). Todos <b>aditivos</b>: no existen en la v1
 * y no tocan ninguna respuesta congelada.
 *
 * <p><b>Ninguno devuelve el secreto salvo {@link EnrolamientoResponse}</b>, que
 * es la unica vez que sale del servidor. Su respuesta viaja con
 * {@code Cache-Control: no-store}.
 */
public final class MfaDtos {

    private MfaDtos() {
    }

    // --------------------------------------------------------------- login

    public record DesafioRequest(String usuario, String contrasena) {
    }

    /** 202: la cuenta tiene segundo factor y hace falta el codigo. */
    public record DesafioResponse(String desafio, OffsetDateTime expiraEn, String metodo) {
    }

    public record VerificacionRequest(String desafio, String codigo) {
    }

    // ---------------------------------------------------------- enrolamiento

    /**
     * El secreto y el QR, <b>una sola vez</b>. No hay endpoint que los relea:
     * perder el enrolamiento a la mitad se resuelve empezandolo de nuevo.
     */
    public record EnrolamientoResponse(String secreto, String uri) {
    }

    public record CodigoRequest(String codigo) {
    }

    /** Los ocho codigos de respaldo, tambien una sola vez. */
    public record CodigosResponse(List<String> codigos) {
    }

    /** Reautenticacion reforzada: contrasena Y codigo vigente (D-S0-34). */
    public record ReautenticacionRequest(String contrasena, String codigo) {
    }

    public record ElevacionResponse(String token, OffsetDateTime expiraEn) {
    }

    public record RevocacionRequest(String motivo) {
    }

    public record EstadoMfaResponse(boolean activo, boolean debeEnrolar,
                                    long codigosDisponibles, boolean codigosPorAgotarse,
                                    OffsetDateTime activadoEn) {

        public static EstadoMfaResponse desde(MfaService.EstadoFactor estado) {
            return new EstadoMfaResponse(estado.activo(), estado.debeEnrolar(),
                    estado.codigosDisponibles(), estado.codigosPorAgotarse(),
                    estado.activadoEn());
        }
    }
}
