import { ContrasteDelRenglon } from './api/tareas.service';

/**
 * **El contraste degradado, dicho con palabras** (E2.6).
 *
 * Es el camino que se recorre casi siempre hoy: el rango de renta necesita
 * bastantes propiedades comparables con renta **publicada**, y la cartera
 * todavía no las tiene. Decirlo con su N es el producto — «3 propiedades en
 * Miraflores, pocas para un rango» informa; un silencio no dice si falta poco o
 * falta todo.
 *
 * **Nunca aparece aquí una cifra del sector.** Todo lo que se compara sale de la
 * base de la organización, y por eso se puede ir a comprobar.
 *
 * Vive en `core/` y no dentro de una pantalla porque el Radar del Inicio y la
 * ficha comercial enseñan el mismo renglón: dos redacciones del mismo hecho
 * empezarían a divergir a la primera corrección.
 */
export function textoDelContraste(contraste: ContrasteDelRenglon): string {
  const donde = [contraste.zona, contraste.banda].filter(Boolean).join(' · ');
  if (contraste.motivo === 'SIN_OBSERVACIONES') {
    return donde
      ? `Todavía sin renta publicada en ${donde} con la que comparar`
      : 'Todavía sin renta publicada con la que comparar';
  }
  const cuantas =
    contraste.observaciones === 1 ? '1 propiedad' : `${contraste.observaciones} propiedades`;
  return donde
    ? `${cuantas} en ${donde}: pocas para un rango propio`
    : `${cuantas}: pocas para un rango propio`;
}
