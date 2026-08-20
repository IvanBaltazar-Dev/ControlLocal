import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { ConceptoSenal, NivelAtencion } from '../politica-comercial';

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
 * - **De las cuatro rarezas heredadas de la v1 quedan una y media.** El `100`
 *   fijo de la primera fila del embudo y "Con visita realizada" que no miraba el
 *   estado de la visita se corrigieron el 2026-08-08 al descongelar el contrato,
 *   y `captacionesPendientes` —que duplicaba a `captacionesPorRevisar`— se
 *   retiró. Sigue en pie que el donut **no** depende del periodo mientras la
 *   salud sí, y eso no es una rareza: son dos lecturas distintas a propósito.
 * - **Desde E1 (2026-08-10) los números que se clasifican llegan clasificados**
 *   (`senales`). La pantalla elige el color de un `ALTO`; nunca cuándo algo pasa
 *   a serlo.
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
 * Un número del tablero con su lectura ya hecha por el dominio (R-07, E1).
 *
 * Hasta E1 el cable traía solo el número y era el componente quien decidía si
 * eso era grave —ocho ternarios repartidos por rol, que además se contradecían
 * entre sí, y un `> 7` que era la cuarta copia del plazo de recontacto—. Ahora
 * la interpretación viaja resuelta y la pantalla solo elige cómo se ve.
 *
 * La lista viene **completa**, con los conceptos en cero incluidos: un cero
 * clasificado es información ("no hay nada atrasado"), no una ausencia.
 */
export interface IndicadorSenal {
  /** Clave del dominio, no rótulo. El texto visible se escribe en la pantalla. */
  concepto: ConceptoSenal;
  /** El hecho, sin interpretar: cuántos son. */
  valor: number;
  nivelAtencion: NivelAtencion;
  /** `INFORMATIVO` y `SIN_PENDIENTES` no lo son. */
  requiereAtencion: boolean;
  /** 1 es lo que se atiende primero. Un único orden para los tres roles. */
  prioridad: number;
}

/** El estado de un KPI contra su meta. Lo decide el dominio; aquí solo se pinta. */
export type EstadoRitmo = "EN_RITMO" | "ATENCION" | "FUERA_DE_RITMO" | "SIN_BASE";

/**
 * Por qué un KPI no concluye. Sin esto, un `SIN_BASE` obliga a adivinar si
 * falta la meta, falta la de un compañero o falta el mes.
 */
export type MotivoSinBase =
  | "NINGUNO"
  | "SIN_META"
  | "COBERTURA_INCOMPLETA"
  | "PERIODO_SIN_RECORRIDO";

/**
 * El mes de calendario contra el que se mide la meta.
 *
 * **No es el `periodo` de siempre.** Aquel es una ventana móvil (7d/15d/1m/3m/1y)
 * y sigue gobernando series y agregados; este es un mes con inicio, fin y días
 * transcurridos, porque el ritmo lo necesita: `metaEsperadaAHoy` sobre una
 * ventana móvil sería siempre la meta entera.
 *
 * Los cinco campos vienen del backend. **La pantalla no cuenta días.**
 */
export interface PeriodoCalendario {
  /** `AAAA-MM`. */
  codigo: string;
  desde: string;
  hasta: string;
  /** Incluye hoy: el 19 de agosto de 2026 son 19 de 31. */
  diasTranscurridos: number;
  diasTotales: number;
  enCurso: boolean;
}

/**
 * Un KPI canónico con su lectura completa.
 *
 * **Los nulos son información.** `metaPeriodo` en `null` no es meta cero: es que
 * nadie fijó meta. Pintar un 0 diría "tu objetivo era cero y lo cumpliste", que
 * es lo contrario. Misma regla que `conversionPropia` desde E2.0.
 *
 * El `rotulo` viene del backend y **no se reescribe aquí**: es el mismo texto que
 * usa el pie del Inicio, y el día que cambie tiene que cambiar en un solo sitio.
 */
export interface KpiCanonico {
  /** `C` · `P` · `S` · `F`. Estable; el rótulo puede cambiar sin migrar nada. */
  codigo: string;
  rotulo: string;
  /** Qué fila cuenta exactamente. Hace el número auditable. */
  hecho: string;
  actual: number;
  /**
   * **Ausente, no cero, cuando nadie fijó meta.** El backend omite los campos
   * nulos, así que aquí llegan como `undefined`: los seis van marcados
   * opcionales para que TypeScript obligue a distinguir «no hay meta» de «la
   * meta es 0», que es toda la diferencia.
   */
  metaPeriodo?: number | null;
  metaEsperadaAHoy?: number | null;
  porcentajeMeta?: number | null;
  faltante?: number | null;
  proyeccionCierre?: number | null;
  porcentajeProyectado?: number | null;
  estadoRitmo: EstadoRitmo;
  motivoSinBase: MotivoSinBase;
  /** La meta es tan pequeña que repartirla por días inventaría una cadencia. */
  sinCadencia: boolean;
  variacionComparable?: number | null;
}

/**
 * «Puede cerrarse este mes»: determinista, no pronóstico.
 *
 * Solicitudes aprobadas, sin contrato y con la oferta vigente. Una oportunidad
 * prometedora no entra. **El importe conserva su moneda**: si hay dos,
 * `variasMonedas` lo dice y `importe` trae solo la principal — sumar soles con
 * dólares necesita un tipo de cambio que nadie declaró.
 */
export interface CierrePosible {
  operaciones: number;
  importe: number;
  moneda?: string | null;
  variasMonedas: boolean;
  /** La palanca del broker: lo único de esa franja sobre lo que actúa. */
  esperanDecision: number;
}

/**
 * Cómo se reparte el resultado del equipo, que no es lo mismo que el total.
 *
 * `null` para un agente: su pulso sería su propio ritmo contado otra vez.
 * `sinBase` son los agentes a los que nadie fijó meta — no cuentan como fuera de
 * ritmo, porque no se le reprocha a nadie una brecha contra un objetivo que no
 * existe.
 */
export interface PulsoEquipo {
  enRitmo: number;
  atencion: number;
  fueraDeRitmo: number;
  sinBase: number;
  agentes: number;
}

/**
 * El bloque de rendimiento (E2.6).
 *
 * `generadoEn` **tiene un solo productor en todo el sistema, y es éste**. El
 * Inicio lo lee de aquí para decir "hace 2 min" en vez de mirar su propio reloj,
 * igual que hace con `ambito`.
 */
export interface Rendimiento {
  periodo: PeriodoCalendario;
  /** ISO-8601 con zona. El instante en que el backend calculó todo esto. */
  generadoEn: string;
  /** Los cuatro, en el orden del embudo. Ni uno más. */
  kpis: KpiCanonico[];
  puedeCerrarse: CierrePosible;
  pulso?: PulsoEquipo | null;
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
  captacionesObservadas: number;
  oportunidadesActivas: number;
  interacciones: number;
  visitas: number;
  cierres: number;
  cierresCohorte: number;
  /**
   * Conversión por cohorte, nunca por encima de 100 — y **`null` cuando no hay
   * cohorte**: sin captaciones en el periodo no se convirtió nada *porque no
   * había nada que convertir*, que no es lo mismo que haber trabajado doce y no
   * cerrar ninguna. Es el único numérico nulable de la respuesta.
   */
  conversionPropia: number | null;
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
  /** Los mismos números, clasificados por el dominio y ya ordenados. */
  senales: IndicadorSenal[];
  /**
   * Cuántas **cosas** reclaman atención ahora mismo. No se deriva sumando
   * `senales`: `DEMORA_DE_SEGUIMIENTO` vale días, y colarla daría "11 cosas"
   * donde hay 2 pendientes y 9 días de atraso. Lo suma el dominio.
   */
  pendientesDeAtencion: number;
  /**
   * Los cuatro KPI canónicos con su meta y su ritmo, medidos contra un **mes de
   * calendario** y no contra la ventana móvil de arriba (E2.6). Aquí vive
   * también `generadoEn`, y es su único productor.
   */
  rendimiento: Rendimiento;
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

/**
 * Un paso del historial de una meta.
 *
 * Existe porque un «actualizado el 18/08» no basta: hace falta saber **de cuánto
 * a cuánto, quién y por qué**. Sin esto, dentro de tres meses la pantalla diría
 * que la meta siempre fue 6.
 */
export interface RevisionDeMeta {
  id: number;
  /** `B` la fijó el broker · `P` la propuso el agente. */
  origen: 'B' | 'P';
  /** `A` aplicada · `E` en espera · `R` rechazada. */
  estado: 'A' | 'E' | 'R';
  /** Ausente la primera vez que se fijó: no había de dónde venir. */
  valorAnterior?: number | null;
  valorPropuesto: number;
  motivo: string;
  autor: string;
  fecha: string;
  decisor?: string | null;
  motivoDecision?: string | null;
}

/** Un ajuste pedido por el agente que el broker todavía no ha resuelto. */
export interface PropuestaDeMeta {
  idRevision: number;
  idRolAgente: number;
  agente: string;
  kpi: string;
  rotulo: string;
  /** Ausente si propone sobre una meta que aún no le habían fijado. */
  valorVigente?: number | null;
  valorPropuesto: number;
  motivo: string;
  fecha: string;
}

/**
 * La meta de un agente para un KPI y mes.
 *
 * `valor` **ausente no es cero**: cero significa que este mes no se le pide ese
 * resultado —una decisión—; ausente significa que nadie ha decidido. La pantalla
 * necesita distinguirlos para poder enseñar a quién le falta.
 */
export interface MetaDeAgente {
  idRolAgente: number;
  agente: string;
  kpi: string;
  rotulo: string;
  valor?: number | null;
  propuesta?: PropuestaDeMeta | null;
  historial: RevisionDeMeta[];
}

/** Lo que el broker envía al fijar. El motivo es por asignación, no global. */
export interface AsignacionDeMeta {
  idRolAgente: number;
  kpi: string;
  valor: number;
  motivo: string;
}

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

  // ==================================================================
  // Metas · quien las ve, quien las propone y quien decide
  // ==================================================================
  //
  // Los tres roles llaman al MISMO `metas()`: el alcance lo resuelve el
  // backend. El agente recibe las suyas; el broker, las de su equipo; el
  // administrador, las de su organizacion en lectura.

  /** Las metas del mes que el actor alcanza, con propuesta viva e historial. */
  metas(mes?: string): Promise<MetaDeAgente[]> {
    return this.api.get<MetaDeAgente[]>('indicadores/metas', { mes });
  }

  /**
   * Fija o revisa metas. **Solo el broker**: al agente el backend le responde
   * 403, y a la pantalla le toca no ofrecer la accion.
   */
  fijarMetas(mes: string, metas: AsignacionDeMeta[]): Promise<MetaDeAgente[]> {
    return this.api.put<MetaDeAgente[]>('indicadores/metas', { mes, metas });
  }

  /**
   * El agente pide un ajuste de **su** meta. No la cambia: queda en espera.
   *
   * Es la mitad de la politica que impide manipular el indicador -bajar la meta
   * porque se va perdiendo- sin volverlo inmutable, que tampoco sirve cuando hay
   * licencias, altas a mitad de mes o cambios de cartera.
   */
  proponerMeta(mes: string, kpi: string, valor: number, motivo: string):
      Promise<MetaDeAgente[]> {
    return this.api.post<MetaDeAgente[]>('indicadores/metas/propuestas',
        { mes, kpi, valor, motivo });
  }

  /** Lo que espera una decision del broker sobre su equipo. */
  propuestasDeMeta(): Promise<PropuestaDeMeta[]> {
    return this.api.get<PropuestaDeMeta[]>('indicadores/metas/propuestas');
  }

  /**
   * El broker acepta o rechaza. El motivo se exige en los dos casos: un «no» sin
   * explicacion deja al agente sin nada que aprender.
   */
  decidirPropuesta(idRevision: number, acepta: boolean, motivo: string):
      Promise<MetaDeAgente[]> {
    return this.api.post<MetaDeAgente[]>(
        `indicadores/metas/propuestas/${idRevision}/decision`, { acepta, motivo });
  }
}
