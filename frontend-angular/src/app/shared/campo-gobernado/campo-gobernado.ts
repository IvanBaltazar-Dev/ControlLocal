import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { PreguntaCaptura } from '../../core/api/captura.service';

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
 * ## El valor viaja como texto
 *
 * Siempre `string`, sea cual sea el control: `'true'`/`'false'` para un
 * interruptor, `'VENTA,ALQUILER'` para un selector múltiple, la fecha ISO para
 * una fecha. Es el formato que el motor de captura y `PUT /propiedades/{id}`
 * entienden, y evita que cada consumidor convierta por su cuenta.
 *
 * **Un campo vacío emite `''`.** Qué significa vacío —«no lo toqué», «no lo
 * sé» o «quítalo»— no lo decide este componente: lo decide quien lo usa, y el
 * editor lo resuelve declarando el borrado aparte. Aquí no hay ningún valor por
 * defecto: un selector sin elegir es `''`, no la primera opción.
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
  /**
   * Se pinta pero no se cambia. Lo usa el editor para lo que el cable de
   * edición todavía no sabe transportar (un `IMPORTE` con su moneda, una
   * lista múltiple) y para lo que la propiedad no permite cambiar (su código).
   * Que se VEA es deliberado: esconderlo haría creer que el dato no existe.
   */
  readonly soloLectura = input(false);
  readonly cambio = output<string>();

  /** El `type` del `<input>`, derivado del control y de nada más. */
  protected readonly tipoEntrada = computed(() => {
    const control = this.pregunta().control;
    if (control === 'MONEDA' || control === 'ENTERO' || control === 'DECIMAL') {
      return 'number';
    }
    return control === 'FECHA' ? 'date' : 'text';
  });

  protected readonly marcadas = computed(
    () => new Set(this.valor().split(',').filter((parte) => parte.length > 0)),
  );

  protected responder(evento: Event): void {
    const destino = evento.target as HTMLInputElement | HTMLSelectElement;
    this.cambio.emit(destino.value ?? '');
  }

  protected interruptor(evento: Event): void {
    this.cambio.emit((evento.target as HTMLInputElement).checked ? 'true' : 'false');
  }

  /**
   * Una opción de un selector múltiple. El valor viaja como lista separada por
   * comas —`VENTA,ALQUILER`— porque cada elemento es un valor de verdad; lo
   * que nunca viaja es un valor combinado.
   */
  protected alternar(opcion: string, evento: Event): void {
    const marcada = (evento.target as HTMLInputElement).checked;
    const actuales = [...this.marcadas()].filter((valor) => valor !== opcion);
    this.cambio.emit((marcada ? [...actuales, opcion] : actuales).join(','));
  }
}
