import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: los 24 campos de `CaptacionResponse`.
 *
 * Lo importante para quien la consuma: **la captación trae `idLocal`**. Toda
 * la ficha de una propiedad se resuelve encadenando ids desde aquí
 * (`idLocal` → local → `idPropietario` → propietario), sin buscar el local por
 * dirección ni el propietario por nombre.
 */
export interface Captacion {
  id: number;
  codigoCaptacion?: string;
  fechaCaptacion?: string;
  fechaInicioVigencia?: string;
  fechaFinVigencia?: string;
  comisionPactada?: number;
  /** A arrendamiento, V venta. */
  tipoOperacion?: string;
  importeReferencia?: number;
  monedaReferencia?: string;
  /** E mensualidades, P porcentaje, F importe fijo. */
  tipoComision?: string;
  /** R renta, V precio de venta, N no aplica. */
  baseCalculo?: string;
  valorComision?: number;
  monedaComision?: string;
  /** I incluido, A adicional, N no aplica. */
  tratamientoIgv?: string;
  motivoSinComision?: string;
  fechaCierre?: string;
  motivoCierre?: string;
  detalleMotivoCierre?: string;
  observaciones?: string;
  /** P pendiente, O observada, R rechazada, A activa, C cerrada, V vencida. */
  estado?: string;
  motivoOperacion?: string;
  /** 1 a 5. */
  urgencia?: number;
  exclusividad?: boolean;
  observacionRevision?: string;
  fechaRevision?: string;
  idLocal?: number;
  direccionLocal?: string;
  distritoLocal?: string;
  areaM2?: number;
  rubro?: string;
  propietarioNombre?: string;
  idAgente?: number;
  agenteNombre?: string;
  idBrokerRevisor?: number;
  fotoPortadaClave?: string;
  /**
   * **Qué puede hacer con este encargo quien lo está pidiendo** (D-P0-12).
   *
   * No es del contrato heredado: lo añade el Core v2 y **sólo en las fichas
   * individuales** (`GET /captaciones/{id}` y `GET /captaciones/codigo/{codigo}`,
   * que son dos puertas al mismo recurso). En los listados llega nula y con
   * `NON_NULL` **no viaja**: allí la pregunta es «qué hay», no «qué puedo hacer
   * con éste».
   *
   * Por eso su ausencia significa **«no calculado aquí»** y se compara con
   * `== null`; el defecto seguro es no ofrecer la acción.
   */
  capacidades?: CapacidadesCaptacion | null;
}

/**
 * Las tres capacidades del encargo, **resueltas por el Core** (D-P0-12).
 *
 * Cada booleano lo produce **el mismo predicado que después deniega el
 * comando**, no una segunda tabla de decisión: sin esto la pantalla tenía que
 * escribir su propia versión de tres reglas —«soy el agente y está pendiente»,
 * «soy bróker y lo superviso», «está activa»— y una copia de una regla de
 * autoridad diverge siempre hacia el lado que pinta un botón que el backend va
 * a rechazar.
 *
 * **No autorizan nada**: el comando revalida.
 */
export interface CapacidadesCaptacion {
  /** `PUT /captaciones/{id}`: su propio agente, mientras el encargo sea editable (P u O). */
  puedeEditar: boolean;
  /**
   * `POST /captaciones/{id}/decision`: el BROKER que supervisa a ese agente,
   * mientras el encargo sea editable. El TENANT_ADMIN **no**: es operación
   * comercial y el gobierno no la hereda.
   */
  puedeRevisar: boolean;
  /** `POST /captaciones/{id}/cierre`: el mismo BROKER, y sólo si está ACTIVO. */
  puedeCerrar: boolean;
  /**
   * `POST /captaciones/{id}/reasignar`: el BRÓKER que supervisa hoy al agente
   * que lo lleva, **y también el TENANT_ADMIN**.
   *
   * Es la única de las cuatro que el gobierno del tenant hereda —reasignar
   * entre equipos es organigrama, no operación comercial (D-S0-17 fila 6)— y un
   * AGENTE recibe `false` incluso sobre el encargo que lleva: quien lleva un
   * encargo no decide dejar de llevarlo.
   *
   * Son las guardas del comando **sin el destino**, que en la ficha todavía no
   * existe. El comando revalida además el destino, su elegibilidad y el agente
   * observado.
   */
  puedeReasignar: boolean;
}

/**
 * **Un destino ya elegible para reasignar un encargo** (D-P0-7 + D-P0-12).
 *
 * Lleva lo justo para elegir en una lista —quién es, su código y su zona— y
 * **ningún estado administrativo**, que no es un olvido: quien aparece cumple
 * las cinco condiciones de elegibilidad, y de quien no aparece no se publica el
 * motivo.
 *
 * `idAgente` es el **`persona_rol.id` del rol AGENTE**, el mismo identificador
 * que espera `reasignar`.
 */
export interface CandidatoAgente {
  idAgente: number;
  nombre?: string;
  codigoAgente?: string;
  zonaAsignada?: string;
}

export interface FiltrosCaptaciones {
  pagina?: number;
  tamano?: number;
  estado?: string;
  idAgente?: number;
  q?: string;
}

export interface FiltrosCaptacionesPendientes {
  pagina?: number;
  tamano?: number;
  /** Vacío = P y O; acepta P u O para las pestañas de revisión. */
  estado?: string;
  idAgente?: number;
  q?: string;
}

export interface FiltrosCaptacionesReasignables {
  pagina?: number;
  tamano?: number;
  q?: string;
}

/** Evento congelado del historial de cambios de agente responsable. */
export interface ReasignacionCaptacion {
  idReasignacion: number;
  idCaptacion: number;
  codigoCaptacion?: string;
  direccionLocal?: string;
  idAgenteAnterior?: number;
  agenteAnteriorNombre?: string;
  idAgenteNuevo?: number;
  agenteNuevoNombre?: string;
  idBroker?: number;
  brokerNombre?: string;
  fechaCambio?: string;
  motivo?: string;
}

/** Cuerpo congelado de POST/PUT /captaciones. */
export interface CaptacionRequest {
  codigoCaptacion: string;
  fechaCaptacion: string;
  fechaInicioVigencia: string;
  fechaFinVigencia: string;
  /** Adaptador del contrato histórico; el backend prioriza los campos tipados. */
  comisionPactada: number | null;
  observaciones: string | null;
  idLocal: number;
  idAgente: number;
  /**
   * Espejo heredado de `tipoOperacion`; el backend exige que coincidan
   * (`tg_captacion_operacion_coherente`). Deja de ir fijo a `'A'` en V76: la
   * pantalla declara la operacion, no la supone.
   */
  motivoOperacion: 'A' | 'V';
  urgencia: number;
  exclusividad: boolean;
  tipoOperacion: 'A' | 'V';
  importeReferencia: number;
  monedaReferencia: 'PEN' | 'USD';
  tipoComision: 'E' | 'P' | 'F';
  baseCalculo: 'R' | 'V' | 'N';
  valorComision: number;
  monedaComision: 'PEN' | 'USD';
  tratamientoIgv: 'I' | 'A' | 'N';
  motivoSinComision: string | null;
}

/**
 * Una fila de la cartera del equipo, vista **por inmueble**: la captación más
 * reciente de cada propiedad.
 *
 * No es contrato congelado de la v1 — es una **extensión aditiva** del backend
 * (`GET /captaciones/propiedades-equipo`). Existe porque deduplicar por
 * propiedad no se puede hacer sobre una página de captaciones: obligaría a
 * descargarlas todas, que es lo que hacía el Blazor.
 */
export interface PropiedadEquipo {
  idPropiedad: number;
  idCaptacion?: number;
  codigoCaptacion?: string;
  /** Estado de la captación más reciente: P, O, R, A, C o V. */
  estado?: string;
  codigoLocal?: string;
  direccion?: string;
  distrito?: string;
  rubro?: string;
  areaM2?: number;
  idAgente?: number;
  agenteNombre?: string;
}

export interface FiltrosPropiedadesEquipo {
  pagina?: number;
  tamano?: number;
  texto?: string;
  distrito?: string;
}

export interface ResumenPropiedadesEquipo {
  propiedades: number;
  conCaptacionActiva: number;
  agentesConCartera: number;
  distritos: number;
  /** Distritos presentes en la cartera, para que el filtro sea data-driven. */
  distritosDisponibles: string[];
}

@Injectable({ providedIn: 'root' })
export class CaptacionesService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosCaptaciones = {}): Observable<PageResponse<Captacion>> {
    return this.api.get$<PageResponse<Captacion>>('captaciones', { ...filtros });
  }

  /** Variante Promise, para los selectores que buscan bajo demanda. */
  pagina(filtros: FiltrosCaptaciones = {}): Promise<PageResponse<Captacion>> {
    return this.api.get<PageResponse<Captacion>>('captaciones', { ...filtros });
  }

  /** Bandeja de revisión BROKER/ADMIN: solo P/O dentro de su alcance. */
  pendientes$(
    filtros: FiltrosCaptacionesPendientes = {},
  ): Observable<PageResponse<Captacion>> {
    return this.api.get$<PageResponse<Captacion>>('captaciones/pendientes', {
      ...filtros,
    });
  }

  /** Captaciones activas que BROKER/ADMIN pueden mover dentro de su alcance. */
  reasignables$(
    filtros: FiltrosCaptacionesReasignables = {},
  ): Observable<PageResponse<Captacion>> {
    return this.api.get$<PageResponse<Captacion>>('captaciones/reasignables', {
      ...filtros,
    });
  }

  /**
   * Rastro de reasignaciones del encargo, del movimiento más reciente al más antiguo.
   * **No es el historial completo**: el backend lo acota con el mismo alcance que el
   * listado de captaciones (D-P0-6) — el TENANT_ADMIN ve todo su tenant, y el BRÓKER
   * solo las filas de los encargos que **hoy** lleva un agente al que supervisa.
   */
  historialReasignaciones$(): Observable<ReasignacionCaptacion[]> {
    return this.api.get$<ReasignacionCaptacion[]>('captaciones/reasignaciones');
  }

  obtener$(id: number): Observable<Captacion> {
    return this.api.get$<Captacion>(`captaciones/${id}`);
  }

  obtener(id: number): Promise<Captacion> {
    return this.api.get<Captacion>(`captaciones/${id}`);
  }

  /**
   * Captación por su correlativo (`CAP-0001`).
   *
   * El alcance lo impone el backend: el AGENTE solo alcanza las suyas, el
   * BROKER las de sus supervisados y el ADMIN las del tenant; fuera de ahí
   * responde 403 (no 404), así que un código ajeno no se distingue de uno
   * inexistente por el cuerpo, sino por el estado.
   */
  obtenerPorCodigo$(codigo: string): Observable<Captacion> {
    return this.api.get$<Captacion>(`captaciones/codigo/${encodeURIComponent(codigo)}`);
  }

  obtenerPorCodigo(codigo: string): Promise<Captacion> {
    return this.api.get<Captacion>(`captaciones/codigo/${encodeURIComponent(codigo)}`);
  }

  registrar(datos: CaptacionRequest): Promise<Captacion> {
    return this.api.post<Captacion>('captaciones', datos);
  }

  actualizar(id: number, datos: CaptacionRequest): Promise<Captacion> {
    return this.api.put<Captacion>(`captaciones/${id}`, datos);
  }

  decidir(id: number, accion: 'A' | 'O' | 'R', observacion: string | null): Promise<Captacion> {
    return this.api.post<Captacion>(`captaciones/${id}/decision`, { accion, observacion });
  }

  /**
   * **Mueve el encargo a otro agente** (D-S0-17 fila 6, D-P0-7 y D-P0-9).
   *
   * El cuerpo declara **desde dónde**: `idAgenteActual` es el agente que se
   * estaba viendo en la lista cuando se decidió. Es **obligatorio** —un cuerpo
   * sin él es **400**— y no se deduce aquí ni se vuelve a leer: lo que importa
   * es lo que se estaba mirando al decidir.
   *
   * Si al ejecutarse el encargo ya **no** lo lleva ese agente, el Core responde
   * **409** y **no ha escrito nada**: hay que **volver a cargar la lista** y
   * decidir sobre el estado actual. La reasignación no se reintenta tal cual —
   * sería ejecutar una decisión sobre un estado que nadie miró.
   *
   * El Core revalida además la banda, el tenant, el destino y su elegibilidad
   * (D-P0-7), así que esta llamada no autoriza nada por haber salido de la
   * lista de candidatos.
   */
  reasignar(
    id: number,
    idAgenteNuevo: number,
    motivo: string,
    idAgenteActual: number,
  ): Promise<Captacion> {
    return this.api.post<Captacion>(`captaciones/${id}/reasignar`, {
      idAgenteNuevo,
      motivo,
      idAgenteActual,
    });
  }

  /**
   * **A quién puedo pasarle este encargo**: los destinos ya elegibles para ESTE
   * encargo y ESTE actor (D-P0-7 + D-P0-12).
   *
   * El Core devuelve la lista **depurada**: mismo tenant, rol AGENTE vigente,
   * cuenta habilitada, relación organizacional viva, estado operativo y —si
   * quien pregunta es un BRÓKER— supervisión vigente; y sin el agente actual,
   * porque una reasignación «de A a A» no cuenta ningún hecho. **Aquí no se
   * filtra nada**: depurar en el cliente sería la lista de condiciones escrita
   * por segunda vez, y una copia parcial de una regla de autoridad ofrece lo que
   * el POST rechaza. Es exactamente lo que hacía esta pantalla hasta el
   * 2026-09-01, con dos de las seis condiciones y sobre una sola página.
   *
   * Se **pagina y se busca en el servidor** por la misma razón: la lista es del
   * tenant, no del formulario.
   *
   * Un actor que no puede reasignar este encargo recibe **403** y no una lista
   * vacía: «no hay candidatos» y «no te corresponde» son dos respuestas
   * distintas. Un encargo de otra corredora, **404**.
   */
  candidatosReasignacion(
    id: number,
    texto?: string,
    pagina = 1,
    tamano = 50,
  ): Promise<PageResponse<CandidatoAgente>> {
    return this.api.get<PageResponse<CandidatoAgente>>(
      `captaciones/${id}/reasignacion/candidatos`,
      { texto, page: pagina, page_size: tamano },
    );
  }

  cerrar(id: number, motivo: string): Promise<Captacion> {
    return this.api.post<Captacion>(`captaciones/${id}/cierre`, { motivo });
  }

  /**
   * Cartera del equipo, una fila por inmueble. Solo BROKER y ADMIN; el
   * alcance (supervisados o tenant) lo decide el backend.
   */
  propiedadesEquipo$(
    filtros: FiltrosPropiedadesEquipo = {},
  ): Observable<PageResponse<PropiedadEquipo>> {
    return this.api.get$<PageResponse<PropiedadEquipo>>('captaciones/propiedades-equipo', {
      ...filtros,
    });
  }

  /** KPI con el mismo `texto` que la lista. No acepta `distrito` a propósito. */
  resumenPropiedadesEquipo$(texto?: string): Observable<ResumenPropiedadesEquipo> {
    return this.api.get$<ResumenPropiedadesEquipo>(
      'captaciones/propiedades-equipo/resumen',
      { texto },
    );
  }
}
