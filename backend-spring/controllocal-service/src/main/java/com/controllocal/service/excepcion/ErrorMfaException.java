package com.controllocal.service.excepcion;

/**
 * Fallo de segundo factor con <b>codigo estable</b> (V37).
 *
 * <p><b>Por que existe.</b> Todos estos fallos son 400 con el mismo cuerpo
 * {@code {"error": ...}}, asi que un cliente que quisiera reaccionar distinto a
 * "el codigo esta mal" y a "el desafio caduco" solo tenia una forma de
 * distinguirlos: <b>comparar la cadena en español</b>. Eso ata el SPA a un texto
 * traducible y convierte cualquier retoque de redaccion en un fallo silencioso
 * de comportamiento. El {@code codigo} es lo que se puede comparar; el
 * {@code error} sigue siendo lo que se enseña.
 *
 * <p><b>El mensaje NO se especializa cuando especializarlo delataria algo.</b>
 * Un desafio que no existe, uno que reemplazo otro y una cuenta que perdio su
 * factor comparten el texto generico a proposito — y comparten tambien
 * {@link #DESAFIO_INVALIDO}, porque el cliente no necesita mas para saber que
 * tiene que volver a empezar. Solo caducado y consumido se dicen con nombre
 * propio, y ninguno de los dos revela nada: para provocarlos hay que tener ya
 * el desafio en la mano, y para tenerlo hay que haber acertado la contraseña.
 */
public class ErrorMfaException extends ReglaNegocioException {

    /** El codigo no cuadra con ningun paso admitido ni con un codigo de respaldo. */
    public static final String CODIGO_INVALIDO = "MFA_CODIGO_INVALIDO";

    /**
     * El codigo era bueno pero su paso ya se consumio (anti-replay, D-S0-31).
     *
     * <p>Se dice aparte porque la accion del usuario es otra: no ha escrito mal
     * nada, tiene que <b>esperar al siguiente</b>. Y no es un oraculo — un
     * codigo que provoca esta respuesta es un codigo ya muerto, asi que
     * confirmarlo no entrega nada utilizable.
     */
    public static final String CODIGO_REUTILIZADO = "MFA_CODIGO_REUTILIZADO";

    /** No hay tal desafio, o lo reemplazo otro, o la cuenta ya no tiene factor. */
    public static final String DESAFIO_INVALIDO = "MFA_DESAFIO_INVALIDO";

    public static final String DESAFIO_VENCIDO = "MFA_DESAFIO_VENCIDO";

    public static final String DESAFIO_CONSUMIDO = "MFA_DESAFIO_CONSUMIDO";

    /**
     * Se acabaron los intentos: los de ESTE desafio (5, y muere) o los
     * acumulados por cuenta (D-S0-32, con espera progresiva). Un solo codigo
     * para los dos porque la salida del usuario es la misma —volver a
     * empezar por la contraseña— y ninguno se resuelve tecleando otro codigo.
     */
    public static final String LIMITE_INTENTOS = "MFA_LIMITE_INTENTOS";

    /**
     * No hay enrolamiento en curso: nunca se inicio, o caduco a los 15 minutos.
     * Fuera del minimo acordado y aun asi estable, por lo mismo que el resto:
     * la pantalla de enrolamiento tiene que poder pedir un secreto nuevo sin
     * mirar el texto del error.
     */
    public static final String ENROLAMIENTO_INVALIDO = "MFA_ENROLAMIENTO_INVALIDO";

    private final String codigo;

    public ErrorMfaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    // ------------------------------------------------------------- fabricas
    // Mensaje y codigo se fijan juntos: son dos caras del mismo fallo y
    // separarlos invita a que un sitio cambie uno y olvide el otro.

    public static ErrorMfaException codigoInvalido() {
        // Texto EXACTO del contrato de V37; el E2E lo comprueba.
        return new ErrorMfaException(CODIGO_INVALIDO, "El codigo no es valido.");
    }

    public static ErrorMfaException codigoReutilizado() {
        return new ErrorMfaException(CODIGO_REUTILIZADO,
                "Ese codigo ya se uso. Espera a que tu aplicacion muestre el siguiente.");
    }

    public static ErrorMfaException desafioInvalido() {
        return new ErrorMfaException(DESAFIO_INVALIDO, "El desafio no es valido o ya caduco.");
    }

    public static ErrorMfaException desafioVencido() {
        return new ErrorMfaException(DESAFIO_VENCIDO,
                "El desafio caduco. Vuelve a iniciar sesion.");
    }

    public static ErrorMfaException desafioConsumido() {
        return new ErrorMfaException(DESAFIO_CONSUMIDO,
                "El desafio ya se uso. Vuelve a iniciar sesion.");
    }

    public static ErrorMfaException desafioAgotado() {
        return new ErrorMfaException(LIMITE_INTENTOS,
                "Demasiados intentos fallidos con este desafio. Vuelve a iniciar sesion.");
    }

    public static ErrorMfaException limiteIntentos(int esperaSegundos) {
        // Texto EXACTO del contrato de V37; el E2E lo comprueba.
        return new ErrorMfaException(LIMITE_INTENTOS,
                "Demasiados intentos fallidos. Espera " + (esperaSegundos / 60) + " minutos.");
    }

    public static ErrorMfaException enrolamientoInvalido() {
        return new ErrorMfaException(ENROLAMIENTO_INVALIDO,
                "No hay un enrolamiento en curso. Empieza de nuevo.");
    }
}
