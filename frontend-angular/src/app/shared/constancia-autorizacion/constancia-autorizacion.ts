import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ConstanciaAutorizacion } from '../../core/autorizacion';
import { fechaHora } from '../../core/formato';

/**
 * Constancia de la autorización de datos (D-27) en la ficha de cliente y en la
 * de propietario.
 *
 * **Es un componente compartido y no dos bloques copiados** por la misma razón
 * por la que el backend devuelve un solo record: es el mismo hecho sobre la
 * misma persona, y dos plantillas gemelas terminan divergiendo.
 *
 * Muestra exactamente cuatro cosas —estado, fecha y hora, quién la registró y,
 * *condicionalmente*, la versión del aviso—. **El canal no se muestra**: desde
 * que lo sella el backend vale siempre lo mismo, y un dato constante no informa
 * de nada.
 */
@Component({
  selector: 'cl-constancia-autorizacion',
  templateUrl: './constancia-autorizacion.html',
  styleUrl: './constancia-autorizacion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConstanciaAutorizacionPanel {
  readonly constancia = input<ConstanciaAutorizacion | null>(null);
  readonly cargando = input(false);
  readonly error = input<string | null>(null);

  protected readonly estado = computed(() => this.constancia()?.estado ?? null);
  protected readonly vigente = computed(() => this.estado() === 'VIGENTE');

  /** Una persona anterior a D-27 no tiene evento; no es lo mismo que revocada. */
  protected readonly sinRegistro = computed(() => this.estado() === 'SIN_REGISTRO');

  protected readonly etiqueta = computed(() => {
    switch (this.estado()) {
      case 'VIGENTE':
        return 'Autorización registrada';
      case 'REVOCADA':
        return 'Autorización revocada';
      case 'CADUCADA':
        return 'Autorización caducada';
      case 'SIN_REGISTRO':
        return 'Sin registro de autorización';
      default:
        return 'Autorización no vigente';
    }
  });

  /** El porqué, en una línea, para que el estado no haya que interpretarlo. */
  protected readonly explicacion = computed(() => {
    switch (this.estado()) {
      case 'REVOCADA':
        return 'El titular retiró su autorización. Se atiende de forma administrativa.';
      case 'CADUCADA':
        return 'El aviso de privacidad cambió de forma material después de que la otorgara: hay que volver a pedirla.';
      case 'SIN_REGISTRO':
        return 'Se registró antes de que la autorización se pidiera en el alta. No hay evento que mostrar.';
      default:
        return null;
    }
  });

  protected readonly registradaEn = computed(() => {
    const valor = this.constancia()?.registradaEn;
    return valor ? fechaHora(valor) : null;
  });

  protected readonly registradaPor = computed(() => this.constancia()?.registradaPor ?? null);

  /**
   * La versión **solo se muestra cuando difiere de la vigente**: ahí es cuando
   * aporta algo operativo —esta persona autorizó contra un aviso anterior—. Si
   * coinciden, el número es ruido en la ficha.
   */
  protected readonly versionSiAporta = computed(() => {
    const datos = this.constancia();
    if (!datos?.versionAviso || !datos.versionVigente) return null;
    return datos.versionAviso === datos.versionVigente ? null : datos.versionAviso;
  });

  protected readonly versionVigente = computed(() => this.constancia()?.versionVigente ?? null);
}
