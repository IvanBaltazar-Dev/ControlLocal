import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO F7 (`docs/ai/contrato-congelado-f6-f7-alertas-tareas.md`).
 * La bandeja del agente: **no es un CRUD**, es una lista derivada del estado
 * del flujo.
 *
 * Cuatro cosas que condicionan cómo se usa desde el SPA:
 *
 * - **`GET /tareas` ESCRIBE.** Deriva qué tareas deberían existir, crea las que
 *   faltan, auto-completa las que ya no aplican y recién entonces devuelve. Es
 *   la única forma de tener la bandeja al día sin planificador, así que abrirla
 *   dos veces no es gratis — pero tampoco es incorrecto.
 * - **No hay alta manual.** El agente resuelve una tarea *trabajando* (se
 *   auto-completa) o la cancela.
 * - **Cancelar la mata para siempre**: `CANCELADA` bloquea que el reconcile la
 *   vuelva a crear para esa entidad. No es "recordármelo más tarde", y la
 *   pantalla tiene que decirlo antes de confirmar.
 * - **La bandeja es estrictamente personal**: el recurso es solo del AGENTE
 *   (BROKER y ADMIN reciben 403). En el dashboard eso no se nota porque
 *   `/dashboard` les manda una bandeja vacía en vez de fallar.
 *
 * **Ya no hay tope.** La v1 cortaba la fuente en 10 y descartaba el resto **en
 * silencio** (D-F7-2); se retiró el 2026-08-08 al descongelar el contrato,
 * porque el agente no podía distinguir "tengo 10 tareas" de "tengo 40" y las 30
 * que faltaban no aparecían en ningún sitio. `totalRecords` es desde entonces el
 * **total real** de tareas abiertas, así que la pantalla que las pinte tiene que
 * aguantar 30 o 50 filas sin descuadrarse.
 */

/** Tipos del cable. `PROPONER_OPORTUNIDAD` abre la ficha del CLIENTE. */
export type TipoTarea =
  | 'RECONTACTO'
  | 'SEGUIMIENTO'
  | 'SUBIR_DOCUMENTOS'
  | 'VISITA'
  | 'REPORTE_PROPIETARIO'
  | 'PROPONER_OPORTUNIDAD';

export type PrioridadTarea = 'ALTA' | 'MEDIA' | 'BAJA';

/**
 * Espejo de `TareaResponse`. Los cuatro campos derivados —`entidadCodigo`,
 * `rutaResolver`, `diasSinAccion` y `fechaVencimiento`— **no están en la
 * tabla**: se calculan al leer y su enriquecimiento es *best-effort*, así que
 * pueden faltar sin que eso signifique nada malo.
 *
 * `diasSinAccion` cuenta desde el **plazo real de la entidad** (recontacto,
 * cambio de estado, fecha de visita), no desde que se creó la tarea.
 */
/**
 * El estado de UN hecho, no del asunto.
 *
 * Lo decide el dominio. Angular mapea estado → marca y color, y nada más: si
 * lo dedujera del tono del asunto, un asunto en rojo pintaría de rojo también
 * sus buenas noticias — que es el problema que D-E2-1 §10.1 arregló.
 *
 * El vocabulario es de cinco y no crece.
 */
export type EstadoDelHecho = 'HECHO' | 'FALTA' | 'PLAZO' | 'FRENO' | 'DATO';

export interface HechoDelAsunto {
  estado: EstadoDelHecho;
  texto: string;
}

/** Cuánto se lleva de algo contable de verdad. Ausente = no hay nada que contar. */
export interface AvanceDelAsunto {
  hechos: number;
  total: number;
  unidad: string;
}

export interface ComoEsta {
  avance: AvanceDelAsunto | null;
  /** Como máximo tres. Tres viñetas, sin párrafos. */
  hechos: HechoDelAsunto[];
}

/** Cuánto se ha consumido de una ventana. Lleva los dos números, no el %. */
export interface VentanaDelRenglon {
  consumido: number;
  total: number;
}

/**
 * Dónde cae un dato respecto de **nuestra** operación (E2.6). Nunca del sector.
 *
 * `forma: NINGUNA` es el caso **normal** mientras la cartera no tenga muestra, y
 * `observaciones` viaja igual: «3 propiedades» informa y «sin datos» no dice si
 * falta poco o todo. El rango solo nace con bastantes propiedades comparables.
 */
export interface ContrasteDelRenglon {
  forma: 'POSICION_EN_RANGO' | 'DESVIACION_CONTRA_MEDIA' | 'NINGUNA';
  motivo:
    | 'NINGUNO'
    | 'SIN_REFERENCIA_INTERNA_SUFICIENTE'
    | 'SIN_OBSERVACIONES'
    | 'SIN_GRUPO_COMPARABLE';
  minimo?: number | null;
  maximo?: number | null;
  valor?: number | null;
  /** Dónde cae dentro del rango. Ausente si el rango no tiene ancho. */
  posicionPorcentaje?: number | null;
  moneda?: string | null;
  zona?: string | null;
  banda?: string | null;
  /** Cuántas propiedades distintas lo sostienen. Sin esto no se puede juzgar. */
  observaciones: number;
}

/**
 * Un renglón del expediente comercial. `estado` ausente = historial, sin color.
 *
 * Los cuatro renglones **no son siempre los mismos**: un asunto con encargo trae
 * Encargo · Renta · Actividad · Propietario, y una prospección —anterior a la
 * captación— trae Prospección · Contacto · Avance · Propietario. El rótulo viene
 * del backend; la pantalla no elige cuáles.
 */
export interface RenglonExpediente {
  rotulo: string;
  valor: string;
  estado: 'BIEN' | 'OJO' | 'MAL' | null;
  ventana: VentanaDelRenglon | null;
  /** La chispa de la renta; sale del histórico económico (E0). */
  serie: number[] | null;
  contraste?: ContrasteDelRenglon | null;
}

/**
 * La interpretación de un asunto (E2.4).
 *
 * `lectura` llega REDACTADA: sintetiza los cuatro renglones sin recitarlos, y
 * viaja `null` cuando no hay nada que concluir. Una lectura de relleno enseña a
 * no leerla, así que su ausencia se respeta.
 */
export interface InterpretacionDelAsunto {
  comoEsta: ComoEsta;
  /** Siempre cuatro, elegidos según la etapa del asunto. Nunca cuatro guiones. */
  expediente: RenglonExpediente[];
  lectura: string | null;
}

export interface Tarea {
  id: number;
  tipo: string;
  entidadTipo: string;
  entidadId: number;
  entidadCodigo?: string;
  /** Ruta del legado (`solicitud-detail/{codigo}`, `visitas?focus={id}`…). */
  rutaResolver?: string;
  descripcion: string;
  estado: string;
  prioridad: string;
  fechaProgramada?: string;
  diasSinAccion?: number;
  fechaVencimiento?: string;
  /** De quién es la pelota. Lo decide el dominio (E2.2). */
  dependeDeMi?: boolean;
  /** OFERTA (propietario) o DEMANDA (cliente). */
  lado?: string | null;
  /** Dónde cae en SU cadena: PROSPECCION…PUBLICACION o OPORTUNIDAD…CONTRATO. */
  paso?: string | null;
  /** Cómo está, su expediente y la lectura que lo sintetiza (E2.4). */
  interpretacion?: InterpretacionDelAsunto | null;
}

@Injectable({ providedIn: 'root' })
export class TareasService {
  private readonly api = inject(ApiClient);

  /**
   * La bandeja completa: **lista pelada, sin sobre de paginación**, sin tope y
   * ya ordenada por prioridad y días sin acción.
   */
  bandeja$(): Observable<Tarea[]> {
    return this.api.get$<Tarea[]>('tareas');
  }

  bandeja(): Promise<Tarea[]> {
    return this.api.get<Tarea[]>('tareas');
  }

  /** Misma fuente, paginada. `tamano` por defecto es **5**, no 10. */
  pendientes$(pagina = 1, tamano = 5): Observable<PageResponse<Tarea>> {
    return this.api.get$<PageResponse<Tarea>>('tareas/pendientes', { pagina, tamano });
  }

  /**
   * Cancelación definitiva (204 sin cuerpo). El backend exige que la tarea sea
   * del agente; a partir de aquí ningún disparador la vuelve a crear para esa
   * entidad.
   */
  cancelar(id: number): Promise<void> {
    return this.api.post<void>(`tareas/${id}/cancelar`);
  }
}
