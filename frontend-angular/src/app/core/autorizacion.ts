/**
 * Autorización de datos personales (D-27).
 *
 * Compartido por el alta de **cliente** y la de **propietario**: las dos piden
 * exactamente lo mismo, y una sola vez.
 *
 * La regla de pantalla que ordena todo esto: el formulario muestra **dos cosas
 * y ni una más** — la casilla y el enlace al aviso. Fecha, actor, organización,
 * canal, versión del aviso, base jurídica y finalidad los pone el backend. Si
 * alguno de esos aparece como campo, la pantalla está mal.
 *
 * Hubo un desplegable de canal y se retiró: le pedía al agente que describiera
 * la pantalla en la que ya estaba, que es fricción sin información. El canal
 * sigue registrándose —la columna es obligatoria— pero lo sella el backend.
 */

/**
 * Texto de la casilla. Está aquí y no en cada plantilla porque es el texto que
 * el backend guarda como evidencia: dos redacciones distintas en dos pantallas
 * significarían dos autorizaciones distintas registradas como si fueran la
 * misma.
 */
export const TEXTO_AUTORIZACION =
  'La persona autorizó el registro y uso de sus datos para atender su solicitud y ' +
  'gestionar la relación comercial.';

/** Estado derivado de la autorización, tal como lo proyecta el backend. */
export type EstadoAutorizacion =
  | 'VIGENTE'
  | 'REVOCADA'
  | 'CADUCADA'
  /** Persona anterior a D-27: nunca hubo evento que registrar. */
  | 'SIN_REGISTRO'
  /** Defensivo: hay evento, de un tipo que hoy ningún flujo escribe. */
  | 'NO_VIGENTE';

/**
 * Espejo de `AutorizacionResponse`. Lo consumen la ficha de cliente y la de
 * propietario: es el mismo hecho sobre la misma persona, así que **un solo
 * tipo** y un solo componente que lo pinta.
 *
 * `versionAviso` y `versionVigente` viajan las dos porque el número **solo
 * aporta valor operativo cuando difieren** — ahí dice que esta persona
 * autorizó contra un aviso anterior al vigente. Si coinciden, no se muestra.
 */
export interface ConstanciaAutorizacion {
  estado: EstadoAutorizacion;
  registradaEn?: string;
  registradaPor?: string;
  versionAviso?: string;
  versionVigente?: string;
}
