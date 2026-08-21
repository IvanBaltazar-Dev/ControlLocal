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

/**
 * Un campo con todo lo que hace falta para pintarlo **sin conocerlo**.
 *
 * `control` es la única cosa que esta pantalla mira para decidir qué dibujar.
 * Nunca la clave: si un componente acabara escribiendo `if (clave === 'piso')`,
 * la matriz «tipo → campos» no se habría eliminado, se habría mudado — y hay un
 * gate que rompe el build por eso (`FronteraDeAutoridadEnElSpaTest`).
 */
export interface PreguntaCaptura {
  clave: string;
  rotulo: string;
  /** APERTURA, COMUN, TIPO u OPERACION. */
  familia: string;
  /** SELECTOR, SELECTOR_MULTIPLE, TITULARES, INTERRUPTOR, MONEDA, ENTERO, DECIMAL, TEXTO. */
  control: string;
  tipoDato: string;
  unidad?: string | null;
  opciones?: string[] | null;
  obligatoria: boolean;
  ayuda?: string | null;
  orden: number;
  restricciones?: RestriccionesCampo | null;
}

/**
 * La condición económica de **un** encargo, con su título ya puesto.
 *
 * Una propiedad que se ofrece para venta y para alquiler devuelve dos: misma
 * forma, distinto rótulo. La pantalla pinta una sección por bloque y no
 * necesita saber cuántos hay ni partir ninguna clave.
 */
export interface BloqueOperacion {
  /** VENTA o ALQUILER. Nunca un valor combinado: `AMBAS` no existe. */
  operacion: string;
  /** «Condición de venta», «Condición de alquiler». */
  rotulo: string;
  preguntas: PreguntaCaptura[];
}

export interface DefinicionCaptura {
  intencion: string;
  tipoPropiedad: string;
  operaciones: string[];
  comunes: PreguntaCaptura[];
  delTipo: PreguntaCaptura[];
  deLaOperacion: BloqueOperacion[];
}

/** Dónde se ha quedado una captura. */
export interface EstadoCaptura {
  idBorrador: number;
  codigo: string;
  intencion: string;
  /** E en curso · J ejecutado · D descartado. */
  estado: string;
  canal?: string | null;
  conocido: Record<string, unknown>;
  /** Lo que falta, **en el orden en que se preguntará**. */
  faltante: string[];
  siguiente?: PreguntaCaptura | null;
  listoParaEjecutar: boolean;
  entidadTipo?: string | null;
  idEntidad?: number | null;
  actualizadoEn?: string | null;
}

/** Lo que produjo una captura confirmada. */
export interface EjecucionCaptura {
  idBorrador: number;
  idPropiedad: number;
  codigoPropiedad: string;
  /** Uno por operación declarada: dos cuando la propiedad se vende Y se alquila. */
  idsEncargos: number[];
  /** `true` cuando la confirmación era un reintento y no creó nada nuevo. */
  reintento: boolean;
}

/**
 * **El motor de captura de BROX Core, por el cable.**
 *
 * BROX Web no decide qué se pregunta para cada tipo de propiedad: lo pregunta.
 * Sin este servicio, Angular necesitaría una tabla «tipo → campos» y KAIROS
 * otra, y las dos empezarían a divergir del catálogo —que es el real— desde el
 * primer atributo que alguien añada (D-A-1 §6).
 *
 * Nada de lo que pasa por aquí escribe negocio salvo `ejecutar`: abrir un
 * borrador y responderlo sólo anota lo que se sabe. Es lo que hace seguro
 * abandonar el alta a medias.
 */
@Injectable({ providedIn: 'root' })
export class CapturaService {
  private readonly api = inject(ApiClient);

  /**
   * Qué hay que decidir **antes** de que exista un plan de preguntas.
   *
   * Hoy son el tipo y la operación, pero la pantalla no lo sabe ni tiene por
   * qué: pinta lo que llega, en el orden en que llega.
   */
  apertura(intencion = 'REGISTRAR_PROPIEDAD'): Promise<PreguntaCaptura[]> {
    return this.api.get<PreguntaCaptura[]>('captura/apertura', { intencion });
  }

  /**
   * Qué se pregunta para este tipo y estas operaciones, con qué límites.
   *
   * @param operaciones una o varias: `VENTA`, `ALQUILER` o `VENTA,ALQUILER`.
   *        Con las dos, `deLaOperacion` trae dos bloques y la ficha física
   *        sigue viniendo una sola vez — una propiedad, dos encargos.
   */
  definicion(
    tipoPropiedad: string,
    operaciones: string,
    intencion = 'REGISTRAR_PROPIEDAD',
  ): Promise<DefinicionCaptura> {
    return this.api.get<DefinicionCaptura>('captura/definicion', {
      intencion,
      tipoPropiedad,
      operaciones,
    });
  }

  /** Abre una captura nueva. No escribe nada del negocio. */
  abrir(intencion = 'REGISTRAR_PROPIEDAD'): Promise<EstadoCaptura> {
    return this.api.post<EstadoCaptura>('captura', { intencion });
  }

  /**
   * Anota lo que se acaba de saber y devuelve qué falta.
   *
   * Un valor vacío **borra** el dato en vez de guardarlo en blanco: el usuario
   * corrigiéndose es un caso normal, y un `""` guardado se parece demasiado a
   * un dato que sí se declaró.
   */
  avanzar(idBorrador: number, datos: Record<string, string>): Promise<EstadoCaptura> {
    return this.api.post<EstadoCaptura>('captura', { idBorrador, datos });
  }

  consultar(idBorrador: number): Promise<EstadoCaptura> {
    return this.api.get<EstadoCaptura>(`captura/${idBorrador}`);
  }

  /**
   * Confirma: aquí, y sólo aquí, se escribe la propiedad.
   *
   * `Idempotency-Key` es lo que hace que un doble clic o un reintento por red
   * devuelvan la propiedad del primer intento en vez de crear una segunda.
   */
  ejecutar(idBorrador: number, claveIdempotencia: string): Promise<EjecucionCaptura> {
    return this.api.post<EjecucionCaptura>(`captura/${idBorrador}/ejecutar`, null, undefined, {
      'Idempotency-Key': claveIdempotencia,
    });
  }

  /** Abandonada a propósito. No se borra: que alguien la empezara también es un hecho. */
  descartar(idBorrador: number): Promise<EstadoCaptura> {
    return this.api.delete<EstadoCaptura>(`captura/${idBorrador}`);
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
    operaciones: string,
  ): Promise<Map<string, RestriccionesCampo>> {
    const definicion = await this.definicion(tipoPropiedad, operaciones);
    const porClave = new Map<string, RestriccionesCampo>();
    for (const pregunta of preguntasDe(definicion)) {
      if (pregunta.restricciones) {
        porClave.set(pregunta.clave, pregunta.restricciones);
      }
    }
    return porClave;
  }
}

/** Todas las preguntas de una definición, en el orden en que se presentan. */
export function preguntasDe(definicion: DefinicionCaptura): PreguntaCaptura[] {
  return [
    ...definicion.comunes,
    ...definicion.deLaOperacion.flatMap((bloque) => bloque.preguntas),
    ...definicion.delTipo,
  ];
}
