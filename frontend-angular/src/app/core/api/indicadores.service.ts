import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';

/**
 * Contrato CONGELADO E4 (`docs/ai/contrato-congelado-e4-…`). Dos lecturas que
 * **agregan sobre todas las verticales**: no leen nada nuevo, releen lo de
 * siempre.
 *
 * Tres cosas que hay que saber antes de pintar estos números:
 *
 * - **Ninguno de los dos lleva gate de rol.** Los tres roles entran; lo que
 *   cambia es el alcance y el `ambito`, que el backend resuelve y devuelve como
 *   texto para rotular la pantalla.
 * - **El alcance de indicadores es SOLO por agente responsable** — la captación
 *   no amplía el de nadie aquí. Es una regla distinta de la del seguimiento
 *   comercial y **no se unifican** (D-E4-4).
 * - **Cuatro rarezas del cable se replican a propósito** (D-E4-3) y se ven en
 *   pantalla: el `100` fijo de la primera fila del embudo, "Con visita
 *   realizada" que no mira el estado de la visita, `captacionesPendientes`
 *   duplicando a `captacionesPorRevisar` y el donut que **no** depende del
 *   periodo (la salud sí).
 */

/** Un cubo con nombre: etapas del donut y salud de captaciones. */
export interface IndicadorConteo {
  nombre: string;
  valor: number;
}

export interface IndicadorEmbudo {
  etapa: string;
  valor: number;
  /** La primera fila viaja con `100` aunque `valor` sea 0 (bug congelado). */
  porcentaje: number;
}

export interface IndicadorDesempeno {
  nombre: string;
  captaciones: number;
  cierres: number;
  conversion: number;
}

export interface IndicadorOperativo {
  recontactosVencidos: number;
  recontactosAlDia: number;
  diasPromedioSinSeguimiento: number;
  visitasPendientes: number;
  solicitudesSinCierre: number;
  conversionProspeccionCaptacion: number;
}

/**
 * Espejo de `IndicadoresResponse`. **Nada es nulo**: los escalares viajan en 0
 * y las listas vacías viajan igual, así que la pantalla no necesita defensa
 * contra ausencias — pero sí contra el fallo de la llamada entera.
 */
export interface IndicadoresResumen {
  /** `Reportes globales` · `Reportes de equipo` · `Mi actividad`. */
  ambito: string;
  captacionesPorRevisar: number;
  solicitudesPorEvaluar: number;
  captacionesTotales: number;
  captacionesActivas: number;
  /** Repite `captacionesPorRevisar`; es así en el cable (D-E4-3). */
  captacionesObservadas: number;
  oportunidadesActivas: number;
  interacciones: number;
  visitas: number;
  cierres: number;
  cierresCohorte: number;
  /** Conversión por COHORTE: nunca pasa de 100. */
  conversionPropia: number;
  agentesActivos: number;
  brokersActivos: number;
  propiedadesEquipo: number;
  mesesEtiquetas: string[];
  cierresPorMes: number[];
  conversionPorPeriodo: number[];
  captacionesPorPeriodo: number[];
  /** Partición EXCLUSIVA de todas las captaciones del alcance, sin ventana. */
  etapas: IndicadorConteo[];
  /** Otra lectura de las mismas captaciones, esta vez SÍ acotada al periodo. */
  captacionesSalud: IndicadorConteo[];
  embudo: IndicadorEmbudo[];
  /** Top 8: por broker para el ADMIN, por agente para BROKER y AGENTE. */
  desempeno: IndicadorDesempeno[];
  operativo: IndicadorOperativo;
}

/** Una fila del avance comercial (RF-017): una captación ACTIVA del alcance. */
export interface AvancePropiedad {
  idCaptacion: number;
  codigoCaptacion: string;
  direccion: string;
  distrito: string;
  estadoComercial: string;
  oportunidadesTotales: number;
  oportunidadesAbiertas: number;
  oportunidadesConVisita: number;
  oportunidadesConSolicitud: number;
  cerradasExitosas: number;
  cerradasNoFavorables: number;
  cerradasNoContinuidad: number;
  interesados: number;
  interacciones: number;
  visitasProgramadas: number;
  visitasConcretadas: number;
  solicitudesRecibidas: number;
  tasaOportVisita: number;
  tasaOportSolicitud: number;
  /** Razón más frecuente de no continuidad; `""` cuando no hay ninguna. */
  motivoNoContinuidad: string;
}

/**
 * RF-017. Lectura **acumulada**, no una ventana: por eso no acepta `periodo`.
 *
 * `interesados` de la cabecera **no es la suma de la columna**: son los clientes
 * distintos a nivel global, así que un cliente interesado en dos propiedades
 * cuenta una vez arriba y dos abajo. No es un descuadre.
 */
export interface AvanceComercial {
  /** `Avance comercial global` · `… del equipo` · `Mi avance comercial`. */
  ambito: string;
  propiedades: number;
  oportunidadesTotales: number;
  oportunidadesAbiertas: number;
  oportunidadesConVisita: number;
  oportunidadesConSolicitud: number;
  cerradasExitosas: number;
  cerradasNoFavorables: number;
  cerradasNoContinuidad: number;
  interesados: number;
  interacciones: number;
  visitasProgramadas: number;
  visitasConcretadas: number;
  solicitudesRecibidas: number;
  tasaOportVisita: number;
  tasaOportSolicitud: number;
  detalle: AvancePropiedad[];
}

/**
 * Los periodos que el cable reconoce. **Cualquier otro valor —incluido ausente
 * o vacío— cae en 6 meses**, así que el selector se limita a estos para que lo
 * que se ve rotulado sea lo que se pidió.
 */
export const PERIODOS_INDICADORES = [
  { valor: '7d', etiqueta: 'Últimos 7 días' },
  { valor: '15d', etiqueta: 'Últimos 15 días' },
  { valor: '1m', etiqueta: 'Último mes' },
  { valor: '3m', etiqueta: 'Últimos 3 meses' },
  { valor: '6m', etiqueta: 'Últimos 6 meses' },
  { valor: '1y', etiqueta: 'Último año' },
] as const;

export type PeriodoIndicadores = (typeof PERIODOS_INDICADORES)[number]['valor'];

export const PERIODO_POR_DEFECTO: PeriodoIndicadores = '6m';

/** ¿Es uno de los periodos del cable? Filtra lo que llega por la URL. */
export function esPeriodo(valor: string | null | undefined): valor is PeriodoIndicadores {
  return PERIODOS_INDICADORES.some((p) => p.valor === valor);
}

@Injectable({ providedIn: 'root' })
export class IndicadoresService {
  private readonly api = inject(ApiClient);

  resumen$(periodo?: string): Observable<IndicadoresResumen> {
    return this.api.get$<IndicadoresResumen>('indicadores/resumen', { periodo });
  }

  resumen(periodo?: string): Promise<IndicadoresResumen> {
    return this.api.get<IndicadoresResumen>('indicadores/resumen', { periodo });
  }

  /** RF-017. Sin `periodo` a propósito: es acumulado y **sin tope de filas**. */
  avance$(): Observable<AvanceComercial> {
    return this.api.get$<AvanceComercial>('indicadores/avance');
  }

  avance(): Promise<AvanceComercial> {
    return this.api.get<AvanceComercial>('indicadores/avance');
  }
}
