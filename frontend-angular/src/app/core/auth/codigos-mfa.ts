/**
 * Códigos **estables** del segundo factor (V37).
 *
 * **La regla de esta pantalla y de todas las que toquen MFA: se decide por
 * `codigo`, nunca por el texto.** El `error` que acompaña a cada uno está en
 * español, es traducible y se retoca sin que nadie recuerde que un `if` del
 * SPA dependía de la coma. El `codigo` es lo que el backend se compromete a no
 * mover.
 *
 * El servidor los define en `ErrorMfaException` (los de 400) y en
 * `FiltroAutenticacionJwt` (el 403 de la sesión capada); si aquí falta uno, el
 * SPA cae al comportamiento genérico —mostrar el mensaje— y no adivina.
 */

/**
 * 403: la sesión existe pero está **capada** porque falta enrolar el segundo
 * factor. Gemelo de `CAMBIO_CONTRASENA_REQUERIDO`; el backend solo deja pasar
 * el perfil, el enrolamiento y el logout.
 */
export const CODIGO_ENROLAMIENTO_REQUERIDO = 'ENROLAMIENTO_MFA_REQUERIDO';

/** El código no cuadra: se corrige tecleando bien. */
export const MFA_CODIGO_INVALIDO = 'MFA_CODIGO_INVALIDO';

/**
 * El código era bueno pero su paso ya se consumió. **No se corrige
 * reescribiéndolo**: hay que esperar al siguiente, y decirlo así evita que el
 * usuario insista con el mismo hasta agotar sus intentos.
 */
export const MFA_CODIGO_REUTILIZADO = 'MFA_CODIGO_REUTILIZADO';

/** El desafío no existe, caducó o ya se usó: hay que volver a la contraseña. */
export const MFA_DESAFIO_INVALIDO = 'MFA_DESAFIO_INVALIDO';
export const MFA_DESAFIO_VENCIDO = 'MFA_DESAFIO_VENCIDO';
export const MFA_DESAFIO_CONSUMIDO = 'MFA_DESAFIO_CONSUMIDO';

/** Se agotaron los intentos. Tampoco se resuelve tecleando otro código. */
export const MFA_LIMITE_INTENTOS = 'MFA_LIMITE_INTENTOS';

/** No hay enrolamiento en curso: nunca se inició o caducó a los 15 minutos. */
export const MFA_ENROLAMIENTO_INVALIDO = 'MFA_ENROLAMIENTO_INVALIDO';

/**
 * Los tres códigos que obligan a **rehacer el primer paso**. Se agrupan aquí
 * y no en un `if` de la pantalla porque son la misma decisión —el desafío ya
 * no sirve— vista desde tres motivos distintos.
 */
export function exigeReiniciarLogin(codigo: string | undefined): boolean {
  return (
    codigo === MFA_DESAFIO_INVALIDO ||
    codigo === MFA_DESAFIO_VENCIDO ||
    codigo === MFA_DESAFIO_CONSUMIDO ||
    codigo === MFA_LIMITE_INTENTOS
  );
}
