import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { PreguntaCaptura } from '../../core/api/captura.service';

/**
 * Las monedas que BROX maneja. **No es un código inmobiliario**: es ISO 4217 y
 * su autoridad server-side es `CondicionesEconomicas.MONEDAS`, que admite
 * exactamente estas dos. Un `IMPORTE` lleva cifra **y** moneda, así que
 * dibujarlo exige ofrecerlas — igual que dibujar una fecha exige un calendario.
 * Lo que este componente no hace, y no debe hacer nunca, es conocer una clave
 * concreta del catálogo.
 */
const MONEDAS: readonly { valor: string; rotulo: string }[] = [
  { valor: 'PEN', rotulo: 'S/ soles' },
  { valor: 'USD', rotulo: 'US$ dólares' },
];

/**
 * **UN campo, sea cual sea.** El único sitio del SPA que sabe dibujar una
 * pregunta del catálogo.
 *
 * Mira `control` y **nunca la clave**: es lo que impide que una pantalla acabe
 * con su propia matriz «tipo → campos», que es la forma concreta en que se
 * pierde el modelo universal (D-A-1 §6). Si un día alguien escribe aquí
 * `if (clave === 'piso')`, el gate `FronteraDeAutoridadEnElSpaTest` rompe el
 * build — y con razón.
 *
 * ## Por qué es un componente y no una plantilla dentro del alta
 *
 * Vivió como `ng-template` dentro de `propiedad-form` hasta que hizo falta un
 * segundo consumidor: el editor. Duplicarla habría sido tener dos sitios que
 * pintan un `SELECTOR`, y en cuanto el Core cambió la forma de las opciones
 * —de `string` a `{valor, rotulo}` en el Corte 0B— uno de los dos se quedó
 * atrás sin que nada avisara. Con un solo renderizador, el contrato se corrige
 * una vez.
 *
 * ## Un booleano tiene TRES estados, no dos
 *
 * Se dibujaba con una casilla, y una casilla sin marcar se lee igual que un
 * «no»: «acepta mascotas» sin declarar y «no acepta mascotas» eran píxeles
 * idénticos. Eso es **inventar un defecto en la presentación** — el dato no se
 * perdía, pero la persona leía una respuesta que nadie dio.
 *
 * ```
 *   ''       Sin declarar   ←  todavía no se sabe
 *   'true'   Sí
 *   'false'  No             ←  alguien lo preguntó y la respuesta fue no
 * ```
 *
 * Y es lo que permitirá a KAIROS preguntar sólo lo que falta.
 *
 * ## El valor viaja como texto, y los huecos crudos aparte
 *
 * Siempre `string` para el hueco principal. Un `IMPORTE` lleva además su
 * `moneda` y un `SELECTOR_MULTIPLE` sus `valores`: van por entradas y salidas
 * propias porque componer «PEN 350» y volver a partirlo es exactamente lo que
 * no se puede hacer sin inferir.
 */
@Component({
  selector: 'cl-campo-gobernado',
  templateUrl: './campo-gobernado.html',
  styleUrl: './campo-gobernado.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampoGobernado {
  readonly pregunta = input.required<PreguntaCaptura>();
  readonly valor = input('');
  /** La moneda de un `IMPORTE`. Vacía mientras no se haya declarado. */
  readonly moneda = input('');
  /** Los elementos de un `SELECTOR_MULTIPLE`, ya separados por el Core. */
  readonly valores = input<readonly string[] | null>(null);
  /**
   * Se pinta pero no se cambia. Lo usa el editor para lo que la propiedad no
   * permite cambiar (su código, su uso). Que se **vea** es deliberado:
   * esconderlo haría creer que el dato no existe.
   */
  readonly soloLectura = input(false);

  readonly cambio = output<string>();
  readonly cambioMoneda = output<string>();
  /**
   * La lista completa, **no un delta**: un multivalor es un conjunto y quien
   * lo recibe sustituye.
   *
   * Es la **única** salida de un `SELECTOR_MULTIPLE`: emitir además el texto
   * unido por comas haría viajar el mismo dato en dos formas, y el Core
   * rechaza un multivalor que llegue también como escalar —«sus valores van en
   * su tabla, no en la fila»—. Quien necesite la cadena la compone.
   */
  readonly cambioValores = output<readonly string[]>();

  protected readonly MONEDAS = MONEDAS;

  /** El `type` del `<input>`, derivado del control y de nada más. */
  protected readonly tipoEntrada = computed(() => {
    const control = this.pregunta().control;
    if (control === 'MONEDA' || control === 'ENTERO' || control === 'DECIMAL') {
      return 'number';
    }
    return control === 'FECHA' ? 'date' : 'text';
  });

  /**
   * Lo marcado. Prefiere `valores` —que llega ya separado— y sólo parte el
   * texto cuando el consumidor no los da: en un vocabulario cerrado la coma no
   * puede aparecer dentro de un elemento, así que ahí sí es seguro.
   */
  protected readonly marcadas = computed(() => {
    const lista = this.valores();
    return new Set(lista ?? this.valor().split(',').filter((parte) => parte.length > 0));
  });

  protected responder(evento: Event): void {
    const destino = evento.target as HTMLInputElement | HTMLSelectElement;
    this.cambio.emit(destino.value ?? '');
  }

  protected responderMoneda(evento: Event): void {
    this.cambioMoneda.emit((evento.target as HTMLSelectElement).value ?? '');
  }

  protected alternar(opcion: string, evento: Event): void {
    const marcada = (evento.target as HTMLInputElement).checked;
    const actuales = [...this.marcadas()].filter((valor) => valor !== opcion);
    this.cambioValores.emit(marcada ? [...actuales, opcion] : actuales);
  }
}
