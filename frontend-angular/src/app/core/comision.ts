/**
 * Comisión inmobiliaria: cálculo y forma de decirla.
 *
 * **Qué es `comisionPactada` en el contrato histórico.** Es el adaptador
 * porcentual que se aplicaba sobre la renta mensual:
 *
 * ```
 * comision = renta * comisionPactada / 100
 * ```
 *
 * De ahí que la escala del negocio se lea en meses de alquiler:
 *
 * | Valor    | Significa                 |
 * |----------|---------------------------|
 * | `50.00`  | medio mes de alquiler     |
 * | `100.00` | un mes de alquiler        |
 * | `150.00` | un mes y medio de alquiler|
 * | `200.00` | dos meses de alquiler     |
 *
 * Un `4250.00` **no** son S/ 4.250: son 4250 % de la renta, o sea 42,5 meses.
 * Ese era el valor sembrado de `CAP-0001` y lo corrige la migración V12.
 *
 * El contrato normalizado distingue mensualidades, porcentaje y monto fijo,
 * junto con su base, moneda e IGV. `comisionPactada` queda únicamente como
 * adaptador de compatibilidad del cable histórico.
 */

import { monto, numero, porcentaje, SIN_DATO } from './formato';

/** Un mes completo de alquiler, en la escala del campo. */
export const UN_MES = 100;

/**
 * Equivalencias que el negocio nombra en meses. Fuera de esta tabla se habla
 * en porcentaje: inventar "1,37 meses" no ayuda a nadie.
 */
const EN_MESES: Readonly<Record<number, string>> = {
  50: 'Medio mes de alquiler',
  100: 'Un mes de alquiler',
  150: 'Un mes y medio de alquiler',
  200: 'Dos meses de alquiler',
};

/**
 * Cómo se dice la comisión en la interfaz.
 *
 * Las equivalencias redondas se dicen en palabras —"Un mes de alquiler"— y el
 * resto, como porcentaje sobre la renta. **Nunca se escribe "0.5 meses"**: es
 * jerga de cálculo, no cómo se pacta una comisión.
 */
export function equivalenciaComision(pactada: number | null | undefined): string {
  if (pactada === null || pactada === undefined || !Number.isFinite(pactada)) {
    return SIN_DATO;
  }
  const nombrada = EN_MESES[pactada];
  return nombrada ?? `${porcentaje(pactada)} de la renta mensual`;
}

/** `true` si la equivalencia tiene nombre propio en meses de alquiler. */
export function esEquivalenciaEnMeses(pactada: number | null | undefined): boolean {
  return (
    pactada !== null &&
    pactada !== undefined &&
    Number.isFinite(pactada) &&
    pactada in EN_MESES
  );
}

/** Importe con su moneda; siempre viajan juntos para que no puedan divergir. */
export interface Importe {
  readonly valor: number;
  readonly moneda: string;
}

export interface CondicionComision {
  readonly tipoComision?: string | null;
  readonly baseCalculo?: string | null;
  readonly valorComision?: number | null;
  readonly monedaComision?: string | null;
  readonly importeReferencia?: number | null;
  readonly monedaReferencia?: string | null;
  readonly motivoSinComision?: string | null;
  readonly comisionPactada?: number | null;
}

/** Importe derivado de la condición tipada; no adivina moneda ni unidad. */
export function calcularCondicionComision(
  condicion: CondicionComision | null | undefined,
): Importe | null {
  if (!condicion) return null;
  const valor = condicion.valorComision;
  if (valor === null || valor === undefined || !Number.isFinite(valor) || valor < 0) {
    return null;
  }
  if (condicion.tipoComision === 'F') {
    return condicion.monedaComision
      ? { valor: redondear(valor), moneda: condicion.monedaComision }
      : null;
  }
  if (!condicion.monedaReferencia || condicion.importeReferencia === null
      || condicion.importeReferencia === undefined
      || !Number.isFinite(condicion.importeReferencia)) {
    return null;
  }
  if (condicion.tipoComision === 'E' && condicion.baseCalculo === 'R') {
    return {
      valor: redondear(condicion.importeReferencia * valor),
      moneda: condicion.monedaReferencia,
    };
  }
  if (condicion.tipoComision === 'P'
      && (condicion.baseCalculo === 'R' || condicion.baseCalculo === 'V')) {
    return {
      valor: redondear((condicion.importeReferencia * valor) / 100),
      moneda: condicion.monedaReferencia,
    };
  }
  return null;
}

/** Forma natural de presentar el acuerdo, usando el legado solo como fallback. */
export function descripcionCondicionComision(
  condicion: CondicionComision | null | undefined,
): string {
  if (!condicion?.tipoComision) {
    return equivalenciaComision(condicion?.comisionPactada);
  }
  const valor = condicion.valorComision;
  if (condicion.tipoComision === 'F' && valor === 0 && condicion.motivoSinComision) {
    return `Sin comisión · ${condicion.motivoSinComision}`;
  }
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return SIN_DATO;
  if (condicion.tipoComision === 'E') {
    const conocidas: Record<string, string> = {
      '0.5': 'Medio mes de alquiler',
      '1': 'Un mes de alquiler',
      '1.5': 'Un mes y medio de alquiler',
      '2': 'Dos meses de alquiler',
    };
    return conocidas[String(valor)] ?? `${numero(valor, 2)} mensualidades`;
  }
  if (condicion.tipoComision === 'P') {
    const base = condicion.baseCalculo === 'V' ? 'del precio de venta' : 'de la renta mensual';
    return `${porcentaje(valor)} ${base}`;
  }
  if (condicion.tipoComision === 'F') {
    return condicion.monedaComision ? `Monto fijo de ${monto(valor, condicion.monedaComision)}` : SIN_DATO;
  }
  return SIN_DATO;
}

/** Valor del campo histórico que acompaña a la condición normalizada. */
export function comisionPactadaCompatible(
  tipo: 'E' | 'P' | 'F',
  valor: number,
): number {
  return tipo === 'E' ? valor * 100 : valor;
}

/**
 * Comisión bruta = renta × porcentaje / 100, **en la moneda de la renta**.
 *
 * La moneda no se elige aquí ni se asume: se hereda del importe de la renta.
 * Devolverla junto al valor es lo que impide que la pantalla muestre un monto
 * con una moneda distinta de la que lo originó.
 *
 * Espeja `ComisionServiceImpl.bruta()` con el mismo redondeo a 2 decimales.
 * Es una **estimación de pantalla**: la liquidación real la calcula el backend
 * al cerrar el contrato.
 */
export function comisionSobreRenta(
  pactada: number | null | undefined,
  renta: Importe | null | undefined,
): Importe | null {
  if (
    pactada === null ||
    pactada === undefined ||
    !Number.isFinite(pactada) ||
    !renta ||
    !Number.isFinite(renta.valor)
  ) {
    return null;
  }
  return { valor: redondear((renta.valor * pactada) / 100), moneda: renta.moneda };
}

/**
 * Desembolso inicial del inquilino, **concepto por concepto**.
 *
 * Los tres conceptos son distintos y no se suman entre sí antes de mostrarse:
 * la garantía y el adelanto son del **propietario** (la garantía además se
 * devuelve al final), y la comisión es de la **inmobiliaria**. Presentarlos
 * como un único número es lo que hace que el cliente crea que paga tres meses
 * de renta al propietario.
 *
 * `total` es solo la suma de los tres para "cuánto necesito para entrar", y
 * nunca sustituye al desglose.
 *
 * Todavía **ninguna pantalla migrada lo muestra**: `mesesGarantia` y
 * `mesesAdelanto` viven en la solicitud (F4) y sus pantallas no están
 * migradas. Está aquí, con sus pruebas, para que cuando lleguen no se vuelva a
 * decidir cómo se combinan.
 */
export interface DesembolsoInicial {
  readonly garantia: Importe;
  readonly adelanto: Importe;
  readonly comision: Importe;
  readonly total: Importe;
}

export function desembolsoInicial(entrada: {
  renta: Importe;
  mesesGarantia?: number | null;
  mesesAdelanto?: number | null;
  comisionPactada?: number | null;
}): DesembolsoInicial {
  const moneda = entrada.renta.moneda;
  const garantia = redondear(entrada.renta.valor * meses(entrada.mesesGarantia));
  const adelanto = redondear(entrada.renta.valor * meses(entrada.mesesAdelanto));
  const comision = comisionSobreRenta(entrada.comisionPactada, entrada.renta)?.valor ?? 0;
  return {
    garantia: { valor: garantia, moneda },
    adelanto: { valor: adelanto, moneda },
    comision: { valor: comision, moneda },
    total: { valor: redondear(garantia + adelanto + comision), moneda },
  };
}

/** Texto de un importe con su moneda, sin poder separarlos por error. */
export function importeTexto(importe: Importe | null | undefined): string {
  return importe ? monto(importe.valor, importe.moneda) : SIN_DATO;
}

function meses(valor: number | null | undefined): number {
  return valor !== null && valor !== undefined && Number.isFinite(valor) && valor > 0
    ? valor
    : 0;
}

/** Dos decimales, medio hacia arriba: el mismo redondeo que el backend. */
function redondear(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 100) / 100;
}
