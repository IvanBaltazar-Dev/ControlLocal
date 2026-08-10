/**
 * Espejo **de solo lectura** de la política comercial del backend
 * (`service/soporte/PoliticaComercial.java`).
 *
 * ## Qué hace aquí, si la regla es del dominio
 *
 * R-07 dice que Angular no decide qué significa un número, y no lo decide: todo
 * lo que se **clasifica** —qué urge, qué está atrasado, qué merece proponerse—
 * llega ya interpretado desde el backend (`senales`, con su `nivelAtencion`).
 *
 * Quedan dos valores que el formulario necesita conocer **antes** de enviar
 * nada, y para los que pedir una respuesta del servidor sería peor experiencia
 * sin ganar nada:
 *
 * - la longitud mínima del motivo al cambiar de responsable, para avisar
 *   mientras se escribe en vez de después del envío;
 * - la duración habitual del encargo, para proponer una fecha de fin razonable
 *   al crear la captación.
 *
 * ## La autoridad sigue siendo el backend
 *
 * En los dos casos el servidor vuelve a aplicar la regla y **rechaza** lo que no
 * la cumple: esto adelanta el aviso, no lo sustituye. Antes de E1 el mínimo de
 * 10 caracteres vivía SOLO aquí y bastaba llamar al API para saltárselo.
 *
 * No se añaden más valores a este archivo. Si algo hay que clasificar, se
 * clasifica en el backend y viaja clasificado; exponer la política para que el
 * cliente la vuelva a evaluar solo movería la duplicación de sitio.
 *
 * @see PoliticaComercial.java — cambiar un valor allí obliga a cambiarlo aquí, y
 *      hay un test del backend que lo recuerda por su nombre.
 */
export const POLITICA_COMERCIAL = {
  /** `reasignacion.caracteres-minimos-del-motivo`. */
  motivoReasignacionCaracteres: 10,

  /** `encargo.meses-por-defecto`. */
  encargoMesesPorDefecto: 6,
} as const;

/**
 * Cuánto urge algo, tal como lo clasificó el dominio. Angular elige el color de
 * un `ALTO`; nunca cuándo algo pasa a serlo.
 */
export type NivelAtencion = 'ALTO' | 'MEDIO' | 'INFORMATIVO' | 'SIN_PENDIENTES';

/**
 * Los conceptos que el backend clasifica. Son claves del dominio, no rótulos:
 * el texto que ve el usuario se escribe en la pantalla, con las palabras que
 * usaría alguien del rubro.
 */
export type ConceptoSenal =
  | 'SOLICITUD_POR_EVALUAR'
  | 'RECONTACTO_VENCIDO'
  | 'CAPTACION_POR_REVISAR'
  | 'SOLICITUD_APROBADA_SIN_CIERRE'
  | 'DEMORA_DE_SEGUIMIENTO'
  | 'VISITA_PENDIENTE'
  | 'CIERRE_REGISTRADO'
  | 'COBERTURA_DE_AGENTES';
