/**
 * Política de contraseñas, replicada en el cliente (Plan S0 §4.5).
 *
 * **El backend es quien manda**: aquí solo se evita un viaje y un mensaje de
 * error que el usuario podía haber previsto. Si las dos discrepan, gana el
 * backend y el formulario muestra su mensaje tal cual.
 *
 * Se replica **solo el mínimo de longitud**, que es lo único estable y lo único
 * que el usuario puede corregir mientras escribe. La lista de claves comunes y
 * el historial de reutilización **no** se replican: mantener aquí una copia de
 * esa lista la volvería obsoleta en silencio, y comprobar el historial exigiría
 * mandar la contraseña candidata al servidor antes de tiempo.
 */
export const LARGO_MINIMO_CONTRASENA = 12;

/**
 * Código estable que acompaña al 403 cuando la sesión está capada por
 * contraseña temporal. Es lo que distingue "cambia tu contraseña" de "no
 * tienes permisos", sin atarse al texto — que es traducible.
 */
export const CODIGO_CAMBIO_OBLIGATORIO = 'CAMBIO_CONTRASENA_REQUERIDO';
