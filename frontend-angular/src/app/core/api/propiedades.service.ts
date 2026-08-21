import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';
import { Publicacion } from './encargos.service';
import { PageResponse } from './api.types';

/**
 * Un encargo tal como se ve en una lista: su operación y lo que se pide.
 *
 * **`importe` no es «el precio de la propiedad».** Es el de ESTE encargo. Una
 * propiedad en venta y en alquiler trae dos, y son dos números que no se pueden
 * comparar entre sí ni sumar.
 */
export interface EncargoEnLista {
  /** VENTA o ALQUILER. Nunca un valor combinado: `AMBAS` no existe. */
  operacion: string;
  /** P pendiente · O observada · A activa. */
  estado: string;
  importe?: number | null;
  moneda?: string | null;
}

/** Una fila del listado universal. */
export interface FilaPropiedad {
  id: number;
  codigo: string;
  /** El nombre del valor: `LOCAL`, `DEPARTAMENTO`… Es lo que viaja, no lo que se lee. */
  tipoPropiedad: string;
  /**
   * El rótulo, ya escrito para una persona: «Local comercial», «Almacén».
   *
   * Viene del backend a propósito. Un `switch` aquí que convirtiera `LOCAL` en
   * «Local comercial» sería la matriz «tipo → texto» viviendo en la interfaz, y
   * con dos interfaces habría dos (D-A-1 §6).
   */
  tipoRotulo: string;
  uso?: string | null;
  direccion: string;
  distrito: string;
  metraje?: number | null;
  /** D disponible · N no disponible · I inactiva. */
  estado: string;
  idPropietario?: number | null;
  /** El **representante** de la titularidad, no «el propietario». */
  propietarioNombre?: string | null;
  /** Cuántos titulares vigentes. Con más de uno, la ficha los detalla. */
  titulares: number;
  encargos: EncargoEnLista[];
  fechaRegistro?: string | null;
}

export interface FiltrosPropiedades {
  pagina?: number;
  tamano?: number;
  texto?: string;
  tipoPropiedad?: string;
  distrito?: string;
  estado?: string;
  /**
   * Las que la propiedad tiene **vivas**. Con `VENTA,ALQUILER` significa «tiene
   * las dos», no «tiene alguna»: es el filtro que sirve para encontrar
   * exactamente esas.
   */
  operaciones?: string;
}

/** Lo que el filtro puede ofrecer sin inventarse opciones que no existan. */
export interface OpcionesDeFiltro {
  distritos: string[];
}

// ====================================================================
// La ficha
// ====================================================================

/** Un titular con la parte que le corresponde. Las cuotas vigentes suman 100. */
export interface TitularPropiedad {
  idPropietario: number;
  nombre: string;
  /** Porcentaje. Con un solo titular es 100 y no hace falta enseñarlo. */
  cuota?: number | null;
  /** Con quién se habla. Si nadie está marcado, es el primero. */
  representante: boolean;
  desde?: string | null;
}

/**
 * Una característica, con su etiqueta ya escrita.
 *
 * `rotulo`, `unidad` y `tipoDato` vienen del catálogo. **Esta pantalla no sabe
 * qué características tiene cada tipo de propiedad** —eso lo decide el
 * catálogo— y por eso las pinta todas igual, sin conocer ninguna por su nombre.
 */
export interface AtributoPropiedad {
  clave: string;
  rotulo: string;
  tipoDato?: string | null;
  unidad?: string | null;
  valor?: string | null;
}

/** Un hito de la serie económica de un encargo. */
export interface HitoEncargo {
  /** E esperado · R recomendado · U autorizado · P publicado · O ofertado · A aceptado · C cerrado. */
  hito: string;
  /** «Autorizado», «Publicado»… Lo publica el backend; aquí no se traduce. */
  hitoRotulo: string;
  monto?: number | null;
  moneda?: string | null;
  fecha?: string | null;
}

/** Una clave obligatoria que falta, con su nombre legible. */
export interface AtributoQueFalta {
  clave: string;
  /** La palabra del catálogo. Es lo que se enseña: nunca la clave. */
  rotulo: string;
}

/**
 * **Un encargo: la relación comercial, con su precio y su histórico.**
 *
 * La identidad es `idEncargo`, **nunca `operacion`**. Una propiedad puede haber
 * tenido tres alquileres sucesivos —2024 cerrado, 2025 cerrado, 2026 vigente— y
 * agruparlos por operación fundiría tres series económicas que no tienen nada
 * que ver. Lo que la base prohíbe es dos **vivos** de la misma operación.
 *
 * Todos los rótulos vienen del backend. No hay ninguno que se componga aquí:
 * decidir que un importe de venta se llama «precio» y uno de alquiler «renta»
 * es semántica inmobiliaria, y vive en el Core (D-A-1 §5).
 */
export interface EncargoPropiedad {
  idEncargo: number;
  codigo: string;
  /** VENTA o ALQUILER. El valor, para comparar; nunca para escribir en pantalla. */
  operacion: string;
  /** «Venta», «Alquiler». Lo que se lee. */
  operacionRotulo: string;
  estado: string;
  estadoRotulo: string;
  /** Si sigue en juego. Los cerrados también llegan: su histórico vive aquí. */
  vivo: boolean;
  importe?: number | null;
  moneda?: string | null;
  /** «precio de venta» o «renta mensual», según la operación. Lo dice el Core. */
  importeRotulo: string;
  exclusividad?: boolean | null;
  idAgente?: number | null;
  agenteNombre?: string | null;
  inicio?: string | null;
  fin?: string | null;
  /** **Sólo el suyo.** Dos encargos nunca comparten serie. */
  historico: HitoEncargo[];
  /**
   * **Sus anuncios.** Igual que el histórico: son de este encargo, no de la
   * propiedad. El de la venta no aparece ni se modifica desde el del alquiler.
   */
  publicaciones: Publicacion[];
  /**
   * Si se puede gestionar la publicación de este encargo, y si no, por qué.
   *
   * Llega como **capacidad** para que esta pantalla no escriba
   * `encargo.estado === 'A'` ni tenga que saber que un encargo cerrado no se
   * publica: eso es una regla de negocio y su dueño es el Core (D-A-1 §5). El
   * backend la vuelve a imponer al escribir.
   */
  publicacionGestionable?: GestionDePublicacion | null;
}

/** Si se puede publicar este encargo, y el hecho —no el tono— de por qué no. */
export interface GestionDePublicacion {
  permitida: boolean;
  motivo?: string | null;
}

/**
 * Un hecho comercial **con la constancia de dónde viene**.
 *
 * `idEncargo` lo pone el backend. Es lo que impide que la actividad vuelva a
 * mezclar lo que los dos primeros bloques separaron: una visita de quien quiere
 * comprar y otra de quien quiere alquilar la misma propiedad se leen igual en
 * una lista plana.
 */
export interface HechoDeActividad {
  /** OPORTUNIDAD · VISITA · INTERACCION · EXPEDIENTE · CONTRATO. */
  proceso: string;
  id: number;
  codigo?: string | null;
  /** El hecho en una línea, ya escrito por el Core. */
  titulo: string;
  detalle?: string | null;
  estado?: string | null;
  estadoRotulo?: string | null;
  fecha?: string | null;
  /** El importe del hecho, cuando lo tiene. Llega como número: lo formatea la pantalla. */
  monto?: number | null;
  moneda?: string | null;
  /** De qué encargo nace. Nunca se pierde. */
  idEncargo?: number | null;
  /** VENTA o ALQUILER: el valor, para comparar. */
  operacion?: string | null;
  /** «Venta», «Alquiler»: lo que se lee. Tampoco esto se traduce aquí. */
  operacionRotulo?: string | null;
  /** Dónde se abre, en el vocabulario del SPA. */
  ruta?: string | null;
}

/** Un importe con su fecha y el encargo del que sale. */
export interface ImporteFechado {
  monto?: number | null;
  moneda?: string | null;
  fecha?: string | null;
  /** De qué episodio sale esta cifra. Viaja incluso en un dato agregado. */
  idEncargo?: number | null;
  codigoEncargo?: string | null;
}

/**
 * Qué ha pasado con esta propiedad en **una** operación a lo largo del tiempo.
 *
 * `ultimoPedido` y `ultimoCierre` **no son el mismo dato**: uno es lo que se
 * pidió y otro lo que se cerró de verdad. Cuando no hubo cierre llega `null`, y
 * la pantalla lo dice; no cae al precio pedido, que convertiría «lo que
 * pedíamos» en «lo que vale».
 */
export interface EpisodiosDeOperacion {
  operacion: string;
  operacionRotulo: string;
  /** Cuántos encargos de esta operación ha tenido, vivos y cerrados. */
  veces: number;
  desde?: string | null;
  /** Cuándo terminó el último. `null` mientras siga vivo. */
  hasta?: string | null;
  vivoAhora: boolean;
  ultimoPedido?: ImporteFechado | null;
  ultimoCierre?: ImporteFechado | null;
}

/** Un movimiento económico del inmueble, con su procedencia intacta. */
export interface HitoDeLaHistoria {
  fecha?: string | null;
  hito: string;
  hitoRotulo: string;
  monto?: number | null;
  moneda?: string | null;
  idEncargo?: number | null;
  codigoEncargo?: string | null;
  operacion?: string | null;
  operacionRotulo?: string | null;
}

/**
 * **La memoria del inmueble.** Un nivel de lectura distinto del encargo:
 *
 * - `idEncargo` — la identidad técnica de UN episodio comercial.
 * - `idPropiedad` — la continuidad histórica del inmueble.
 *
 * Los bloques de encargo sirven para auditar y negociar: este importe, este
 * agente, esta serie. Esto contesta otra pregunta —«¿a cuánto se alquiló la
 * última vez?», «¿cuántas veces estuvo en venta?»— que un CRM de operaciones
 * vivas no sabe responder.
 *
 * **No fusiona históricos: los agrega para leerlos.** Cada cifra sigue
 * apuntando a su `idEncargo`, así que de cualquier dato de la historia se puede
 * volver al episodio que lo produjo.
 */
export interface HistoriaComercial {
  porOperacion: EpisodiosDeOperacion[];
  /** Todos los movimientos, del más reciente al más antiguo, cruzando encargos. */
  linea: HitoDeLaHistoria[];
}

/** Lo que ha pasado con la propiedad, repartido por proceso. */
export interface ActividadPropiedad {
  oportunidades: HechoDeActividad[];
  visitas: HechoDeActividad[];
  interacciones: HechoDeActividad[];
  expedientes: HechoDeActividad[];
  contratos: HechoDeActividad[];
}

export interface UbicacionPropiedad {
  direccion?: string | null;
  distrito?: string | null;
  zonaUrbanizacion?: string | null;
  latitud?: number | null;
  longitud?: number | null;
  interiorUnidad?: string | null;
  piso?: string | null;
  referenciaInterna?: string | null;
  nombreEdificioGaleria?: string | null;
}

/**
 * **La propiedad entera, leída por el modelo universal.**
 *
 * Tres conceptos que no se vuelven a mezclar:
 *
 * 1. **la cosa física** — tipo, ubicación, características, titulares;
 * 2. **su gestión comercial** — un `encargo` por relación, con su precio, su
 *    estado, su agente y su histórico;
 * 3. **su actividad** — los hechos, cada uno con el encargo del que nace.
 *
 * **No hay `precio` ni `operacion` en este objeto**, y no es un olvido: no son
 * de la propiedad. Viven en el encargo, que es donde el negocio los pone.
 *
 * **El metraje aparece una sola vez**, entre `atributos`, con su clave lógica
 * `metraje_total`. No hay campo `metraje` suelto: publicarlo además por
 * separado obligaría a esta pantalla a excluirlo de la lista para no enseñarlo
 * dos veces, y esa exclusión es el primer eslabón de una colección de
 * excepciones por clave.
 */
export interface FichaPropiedad {
  id: number;
  codigo: string;
  /** `LOCAL`, `DEPARTAMENTO`… El valor, no lo que se lee. */
  tipoPropiedad: string;
  /** «Local comercial», «Departamento». Lo publica el backend (D-A-1 §6). */
  tipoRotulo: string;
  uso?: string | null;
  usoRotulo?: string | null;
  descripcion?: string | null;
  estadoRegistro?: string | null;
  estadoRegistroRotulo?: string | null;
  disponibilidadComercial?: string | null;
  disponibilidadRotulo?: string | null;
  ubicacion?: UbicacionPropiedad | null;
  titulares: TitularPropiedad[];
  atributos: AtributoPropiedad[];
  /** Todos: los vivos y los cerrados. Un cerrado guarda su histórico. */
  encargos: EncargoPropiedad[];
  atributosQueFaltan: AtributoQueFalta[];
  /** La memoria del inmueble, agregada sobre TODOS sus encargos. */
  historia: HistoriaComercial;
  actividad: ActividadPropiedad;
  fechaRegistro?: string | null;
}

/**
 * **La cartera por el modelo universal.**
 *
 * Distinto de `LocalesService`, que sirve el listado heredado: allí cada fila
 * lleva un precio suelto que no dice de qué operación es, porque la proyección
 * nació cuando todo era alquiler. Aquí cada fila lleva **sus encargos**.
 */
@Injectable({ providedIn: 'root' })
export class PropiedadesService {
  private readonly api = inject(ApiClient);

  listar(filtros: FiltrosPropiedades = {}): Promise<PageResponse<FilaPropiedad>> {
    return this.api.get<PageResponse<FilaPropiedad>>('propiedades', { ...filtros });
  }

  /** Los valores del filtro, sacados de la cartera real. */
  filtros(): Promise<OpcionesDeFiltro> {
    return this.api.get<OpcionesDeFiltro>('propiedades/filtros');
  }

  /**
   * La ficha completa: física, comercial y actividad, en **una** llamada.
   *
   * Es una sola a propósito. Reunir la actividad desde aquí serían tres
   * llamadas por encargo, una más por cada oportunidad para sus visitas, y un
   * barrido de contratos filtrado a mano — y sobre todo obligaría a escribir
   * aquí la regla «la actividad de una propiedad es la de sus encargos», que es
   * dominio (D-A-1).
   */
  consultar(id: number): Promise<FichaPropiedad> {
    return this.api.get<FichaPropiedad>(`propiedades/${id}`);
  }
}
