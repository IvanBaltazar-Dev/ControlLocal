import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';

/**
 * Los límites de un valor, **declarados por el catálogo**.
 *
 * Todo es opcional: un campo sin restricciones declaradas viaja con `null` y el
 * cliente sólo valida lo que el contrato afirme. Lo que NO puede hacer es
 * deducirlas del nombre del campo ni llevar su propia copia — eso sería una
 * regla con dos dueños, que es la misma clase de problema que D-E4-3 cerró para
 * los valores, aplicada a las reglas.
 */
export interface RestriccionesCampo {
  minimo?: number | null;
  maximo?: number | null;
  longitudMaxima?: number | null;
  /** `0` = no admite decimales. `null` = el catálogo no lo declara. */
  decimales?: number | null;
}

/** Un campo con todo lo que hace falta para pintarlo sin conocerlo. */
export interface PreguntaCaptura {
  clave: string;
  rotulo: string;
  familia: string;
  /** SELECTOR, INTERRUPTOR, MONEDA, ENTERO, DECIMAL, TEXTO. */
  control: string;
  tipoDato: string;
  unidad?: string | null;
  opciones?: string[] | null;
  obligatoria: boolean;
  ayuda?: string | null;
  orden: number;
  restricciones?: RestriccionesCampo | null;
}

export interface DefinicionCaptura {
  intencion: string;
  tipoPropiedad: string;
  operacion: string;
  comunes: PreguntaCaptura[];
  delTipo: PreguntaCaptura[];
  deLaOperacion: PreguntaCaptura[];
}

/**
 * Qué se pregunta para un tipo de propiedad, y con qué límites.
 *
 * **Existe para que el cliente no tenga su propia matriz.** Sin este endpoint,
 * Angular necesitaría una tabla «tipo → campos» y KAIROS otra, y las dos
 * empezarían a divergir del catálogo —que es el real— desde el primer atributo
 * que alguien añada.
 */
@Injectable({ providedIn: 'root' })
export class CapturaService {
  private readonly api = inject(ApiClient);

  definicion(
    tipoPropiedad: string,
    operacion: string,
    intencion = 'REGISTRAR_PROPIEDAD',
  ): Promise<DefinicionCaptura> {
    return this.api.get<DefinicionCaptura>('captura/definicion', {
      intencion,
      tipoPropiedad,
      operacion,
    });
  }

  /**
   * Las restricciones de cada clave, aplanadas por clave.
   *
   * Las tres familias van separadas en el contrato porque se comportan distinto
   * al cambiar la selección; para aplicar un mínimo esa distinción no importa,
   * así que se aplanan aquí y no en cada componente.
   */
  async restriccionesPorClave(
    tipoPropiedad: string,
    operacion: string,
  ): Promise<Map<string, RestriccionesCampo>> {
    const definicion = await this.definicion(tipoPropiedad, operacion);
    const porClave = new Map<string, RestriccionesCampo>();
    for (const pregunta of [
      ...definicion.comunes,
      ...definicion.delTipo,
      ...definicion.deLaOperacion,
    ]) {
      if (pregunta.restricciones) {
        porClave.set(pregunta.clave, pregunta.restricciones);
      }
    }
    return porClave;
  }
}
