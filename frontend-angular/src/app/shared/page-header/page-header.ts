import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Un paso de la miga de pan. Sin `ruta` es texto; con `ruta` es un enlace de
 * verdad —`routerLink`—, no un botón que navega: el usuario puede abrirlo en
 * otra pestaña y el lector de pantalla lo anuncia como enlace.
 */
export interface PasoMiga {
  readonly etiqueta: string;
  /** Destino del `routerLink`. Si falta, el paso no es pulsable. */
  readonly ruta?: string | readonly unknown[];
}

/** Lo que acepta `[miga]`: un texto suelto o la ruta paso a paso. */
export type Miga = string | readonly (string | PasoMiga)[];

/**
 * **La cabecera de página de BROX.**
 *
 * La auditoría de UI contó **50 pantallas pintándose su propia miga de pan**
 * (`auditoria-ui-brox.md` §1 hallazgo 3, §7 fila 1) porque el armazón no da
 * contexto y no existía un componente de cabecera: existían clases. Una clase
 * se copia, se olvida y se sobreescribe, y eso es exactamente lo que pasó —
 * cuatro tipografías distintas para la misma miga.
 *
 * Aquí la miga es **dato**, no marcado: `[miga]="['Cierre', 'Solicitudes']"`.
 * Quien la escribe declara los pasos; cómo se separan, con qué tamaño y en qué
 * color lo decide este componente y nadie más.
 *
 * ## Qué NO hace
 *
 * No decide el título ni la miga por la ruta ni por el tipo del objeto: la
 * pantalla presenta, no deduce (D-A-1). Y no dibuja los botones: los recibe
 * proyectados, para que cada pantalla siga siendo dueña de sus acciones y de
 * cuándo se ofrecen.
 *
 * ## Las tres ranuras
 *
 * | Ranura | Para qué |
 * |---|---|
 * | `[titulo]` | un `<h1>` propio cuando el título lleva insignia o formato |
 * | *(por defecto)* | el subtítulo rico: badges, `@if`, avisos bajo el título |
 * | `[acciones]` | el bloque de la derecha, tal cual lo escribe la pantalla |
 *
 * El contenido proyectado conserva la encapsulación de **quien lo escribe**,
 * así que las reglas de la propia pantalla —`.primario`, `.estado`,
 * `.direccion`— siguen alcanzándolo. Por eso la anatomía se comparte sin que
 * ninguna pantalla pierda lo suyo.
 *
 * ## Por qué el estilo vive en `styles.scss` y no aquí
 *
 * `.cl-cabecera` es una regla **global**, y una regla global sí llega al
 * contenido proyectado. Si estos estilos vivieran en la hoja del componente,
 * la encapsulación emulada sellaría cada selector con el atributo de este
 * componente y `<p class="sub">` escrito por la pantalla se quedaría sin
 * estilo. Aquí solo queda lo que este componente dibuja de su puño.
 */
@Component({
  selector: 'brox-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './page-header.html',
  styleUrl: './page-header.scss',
})
export class BroxPageHeader {
  /**
   * La miga de pan. Un texto se pinta tal cual —para las migas de un solo
   * paso—; una lista se pinta con el separador en medio.
   */
  readonly miga = input<Miga>('');

  /** El rótulo de la página. Vacío = la pantalla trae su propio `<h1 titulo>`. */
  readonly titulo = input('');

  /** La bajada. Vacía = o no hay, o la pantalla la proyecta con formato. */
  readonly sub = input('');

  /**
   * Lo que va entre pasos. `·` en casi todas; `/` y `›` existen porque ya
   * estaban escritas así y cambiarlas no era el trabajo de este corte.
   */
  readonly separador = input('·');

  /**
   * `prototipo` es la cabecera de las pantallas portadas de
   * `docs/ai/prototipos/` —propiedades y su ficha, alta y editor—: 25 px de
   * título y la miga en 11/700. No es un tema alternativo, es el mismo
   * componente con la métrica de esas cuatro pantallas, que ya la tenían.
   */
  readonly variante = input<'estandar' | 'prototipo'>('estandar');

  /** La miga normalizada a pasos, sea cual sea la forma en que llegó. */
  protected readonly pasos = computed<readonly PasoMiga[]>(() => {
    const valor = this.miga();
    if (typeof valor === 'string') {
      return valor ? [{ etiqueta: valor }] : [];
    }
    return valor
      .map((paso) => (typeof paso === 'string' ? { etiqueta: paso } : paso))
      .filter((paso) => !!paso.etiqueta);
  });
}
