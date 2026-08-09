/**
 * Avisos que otra pantalla puede pedirle al login que muestre al llegar.
 *
 * **Viaja el código, no el texto.** Un `?mensaje=…` con la frase dentro sería
 * más corto y convertiría la pantalla de acceso en un cartel que cualquiera
 * puede redactar desde un enlace: "tu sesión expiró, confirma tus datos aquí"
 * es un ataque de una línea. Con un catálogo cerrado, un valor desconocido
 * simplemente no muestra nada.
 */
export const AVISO_MFA_CONFIGURADO = 'mfa-configurado';

/**
 * Reemplazar el autenticador **no es un cambio en un solo acto**: revoca el
 * factor y mata las sesiones, así que el usuario vuelve por el login y llega
 * con la sesión capada, que es la que lo lleva al enrolamiento. Decirlo aquí
 * evita que interprete el cierre como un fallo.
 */
export const AVISO_MFA_REEMPLAZO = 'mfa-reemplazo';

const MENSAJES: Record<string, string> = {
  [AVISO_MFA_CONFIGURADO]:
    'MFA configurado correctamente. Por seguridad, vuelve a iniciar sesión.',
  [AVISO_MFA_REEMPLAZO]:
    'Tu segundo factor quedó revocado. Inicia sesión otra vez y configura el nuevo autenticador.',
};

export function mensajeDeAviso(aviso: string | null): string | null {
  return (aviso && MENSAJES[aviso]) ?? null;
}
