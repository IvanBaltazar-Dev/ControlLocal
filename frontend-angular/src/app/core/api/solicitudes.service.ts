import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: `SolicitudResponse` de la v1.
 *
 * La solicitud es la **oferta formal** del interesado sobre una oportunidad, y
 * lo que el broker evalúa. Tres cosas que condicionan cómo se lee:
 *
 * - **Las condiciones del trato viven aquí, no en el contrato**: renta
 *   propuesta, plazo, fecha de inicio, forma de pago, garantía y adelanto. El
 *   contrato las hereda al cerrar, así que lo que se aprueba es esto.
 * - `documentosEntregados`/`documentosRequeridos` son el checklist "X/6" ya
 *   contado por el backend. **`documentosRequeridos` es siempre 6**: los ocho
 *   tipos se pueden subir, pero poder de representación y "otro" no suman.
 * - `codigoSolicitud` es `SOL-yyMMddHHmmss`, una **marca de tiempo**, no un
 *   correlativo como `PRO-####`/`CAP-####`.
 */
export interface Solicitud {
  id: number;
  codigoSolicitud?: string;
  fechaRegistro?: string;
  montoPropuesto?: number;
  moneda?: string;
  plazoTentativo?: string;
  observaciones?: string;
  /** G registrada, E en revisión, O observada, A aprobada, R rechazada, D desistida, C cerrada. */
  estado?: string;
  fechaActualizacionEstado?: string;
  fechaVigenciaOferta?: string;
  idOportunidad?: number;
  codigoOportunidad?: string;
  idCliente?: number;
  clienteNombre?: string;
  idCaptacion?: number;
  codigoCaptacion?: string;
  direccionLocal?: string;
  distritoLocal?: string;
  idAgente?: number;
  agenteNombre?: string;
  plazoMeses?: number;
  fechaInicio?: string;
  formaPago?: string;
  mesesGarantia?: number;
  mesesAdelanto?: number;
  documentosEntregados?: number;
  /** Siempre 6: el checklist no cuenta los ocho tipos, solo los requeridos. */
  documentosRequeridos?: number;
}

/**
 * Filtros de `GET /solicitudes`. `idOportunidad` e `idCaptacion` son del cable
 * v1; los otros cuatro son **extensión aditiva** del v2 y, omitidos, la
 * respuesta es la de siempre —incluido el orden por id descendente—.
 *
 * Ojo con dos nombres: aquí la búsqueda libre se llama `texto` (como en
 * locales), no `query` (oportunidades/visitas) ni `q` (interacciones); y la
 * paginación usa `pagina`/`tamano`, sin los alias `page`/`page_size`.
 */
export interface FiltrosSolicitudes {
  pagina?: number;
  tamano?: number;
  idOportunidad?: number;
  idCaptacion?: number;
  idAgente?: number;
  /** Código de estado, o el cubo `PENDIENTES` — ver {@link PENDIENTES}. */
  estado?: string;
  distrito?: string;
  texto?: string;
}

/** Agente con al menos una solicitud en el alcance, para el filtro data-driven. */
export interface AgenteConSolicitudes {
  id: number;
  nombre: string;
}

/**
 * KPI de la bandeja por estado + los distritos y agentes del alcance, contados
 * en la base sobre el mismo conjunto que pagina la lista.
 *
 * `pendientes` llega **calculado** (`enRevision + observadas`): es el cubo que
 * usa la cola del broker, y viaja resuelto para que la pantalla no lo sume por
 * su cuenta y se desincronice del filtro `estado=PENDIENTES` que pide esa misma
 * cola.
 */
export interface ResumenSolicitudes {
  total: number;
  registradas: number;
  enRevision: number;
  observadas: number;
  aprobadas: number;
  rechazadas: number;
  desistidas: number;
  cerradas: number;
  pendientes: number;
  distritos: string[];
  agentes: AgenteConSolicitudes[];
}

/** Espejo de `SolicitudRequest`. */
export interface DatosSolicitud {
  /** Vacío ⇒ el backend genera `SOL-yyMMddHHmmss`. */
  codigoSolicitud?: string;
  fechaRegistro?: string;
  montoPropuesto?: number;
  moneda?: string;
  plazoTentativo?: string;
  observaciones?: string;
  fechaVigenciaOferta?: string;
  idOportunidad?: number;
  plazoMeses?: number;
  fechaInicio?: string;
  formaPago?: string;
  mesesGarantia?: number;
  mesesAdelanto?: number;
}

/**
 * Contrato CONGELADO: `DocumentoSolicitudResponse`.
 *
 * `rutaArchivo` es la **clave del almacén**, no una ruta del sistema: es lo que
 * el visor pasa a `DocumentosService`. Sin clave no hay archivo que abrir.
 */
export interface DocumentoSolicitud {
  id: number;
  idSolicitud?: number;
  /** I, R, V, P, E, G, D, O. */
  tipoDocumento?: string;
  tipoNombre?: string;
  nombreArchivo?: string;
  rutaArchivo?: string;
  fechaEntrega?: string;
  /** R registrado, O observado, V validado. */
  estado?: string;
  /** P pendiente, C conforme, O observado. */
  resultadoRevision?: string;
  observaciones?: string;
}

/** Contrato CONGELADO: `EvaluacionResponse`. */
export interface Evaluacion {
  id: number;
  fechaEvaluacion?: string;
  /** A aprobada, R rechazada, O observada. */
  resultado?: string;
  observaciones?: string;
  idBroker?: number;
  brokerNombre?: string;
  /** P preliminar, O observación, F final. */
  tipoEvaluacion?: string;
  idSolicitud?: number;
}

const RECURSO = 'solicitudes';

/**
 * Cubo de la cola del broker: lo que espera decisión suya. **No es un estado**
 * —no existe en el vocabulario del cable—, es `E` + `O`, igual que `GESTION` en
 * prospecciones. Se manda tal cual en `estado`.
 */
export const PENDIENTES = 'PENDIENTES';

/** Estados desde los que el agente puede (re)enviar a evaluación. */
export const SOLICITUD_REENVIABLE = new Set(['G', 'O']);

/** El expediente deja de admitir cambios cuando la decisión ya está tomada. */
export const SOLICITUD_RESUELTA = new Set(['A', 'R', 'D', 'C']);

@Injectable({ providedIn: 'root' })
export class SolicitudesService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosSolicitudes = {}): Observable<PageResponse<Solicitud>> {
    return this.api.get$<PageResponse<Solicitud>>(RECURSO, { ...filtros });
  }

  pagina(filtros: FiltrosSolicitudes = {}): Promise<PageResponse<Solicitud>> {
    return this.api.get<PageResponse<Solicitud>>(RECURSO, { ...filtros });
  }

  /** No recibe `estado`, `distrito` ni `idAgente`: son los filtros que acota. */
  resumen$(
    filtros: Omit<FiltrosSolicitudes, 'estado' | 'distrito' | 'idAgente' | 'pagina' | 'tamano'> = {},
  ): Observable<ResumenSolicitudes> {
    return this.api.get$<ResumenSolicitudes>(`${RECURSO}/resumen`, { ...filtros });
  }

  obtener(id: number): Promise<Solicitud> {
    return this.api.get<Solicitud>(`${RECURSO}/${id}`);
  }

  obtener$(id: number): Observable<Solicitud> {
    return this.api.get$<Solicitud>(`${RECURSO}/${id}`);
  }

  /** Entrada por el correlativo, que es lo que llevan las URL del SPA. */
  porCodigo$(codigo: string): Observable<Solicitud> {
    return this.api.get$<Solicitud>(`${RECURSO}/codigo/${encodeURIComponent(codigo)}`);
  }

  porCodigo(codigo: string): Promise<Solicitud> {
    return this.api.get<Solicitud>(`${RECURSO}/codigo/${encodeURIComponent(codigo)}`);
  }

  /**
   * Alta. Deja la solicitud **REGISTRADA**, no en evaluación: enviarla es un
   * segundo paso ({@link reenviar}), y esa separación es la que permite adjuntar
   * los documentos antes de que el broker la vea.
   *
   * Dos precondiciones que no se ven en el cuerpo y explican los 400: la
   * captación debe estar ACTIVA y la oportunidad ABIERTA. El alta además mueve
   * la oportunidad a `S`.
   */
  registrar(datos: DatosSolicitud): Promise<Solicitud> {
    return this.api.post<Solicitud>(RECURSO, datos);
  }

  /**
   * (Re)enviar a evaluación: solo desde REGISTRADA u OBSERVADA. Exige además
   * que el agente responsable **tenga broker supervisor activo** — sin él no
   * habría quién evalúe, y el backend responde 400 diciéndolo.
   */
  reenviar(id: number): Promise<Solicitud> {
    return this.api.post<Solicitud>(`${RECURSO}/${id}/reenviar`, {});
  }

  // ------------------------------------------------------------------
  // Expediente documental
  // ------------------------------------------------------------------

  documentos$(id: number): Observable<DocumentoSolicitud[]> {
    return this.api.get$<DocumentoSolicitud[]>(`${RECURSO}/${id}/documentos`);
  }

  documentos(id: number): Promise<DocumentoSolicitud[]> {
    return this.api.get<DocumentoSolicitud[]>(`${RECURSO}/${id}/documentos`);
  }

  /**
   * Sube el binario **crudo** (octet-stream), sin pasarlo a base64.
   *
   * De las cuatro vías de subida de la v1 el SPA usa solo ésta: base64 infla un
   * tercio el cuerpo, la subida por trozos existe por un bug del cliente .NET
   * —y se retira con el Blazor— y `documentos/local` no se portó (leía del
   * disco del servidor, D-F4-1).
   *
   * El backend valida extensión, tamaño (5 MB) y tipo; si el tipo es inválido
   * **borra el binario recién subido**, así que no quedan huérfanos.
   */
  subirDocumento(
    idSolicitud: number,
    tipoDocumento: string,
    archivo: File,
  ): Promise<DocumentoSolicitud> {
    return this.api.postBinario<DocumentoSolicitud>(
      `${RECURSO}/${idSolicitud}/documentos/archivo`,
      archivo,
      { tipoDocumento, nombreArchivo: archivo.name },
    );
  }

  /**
   * Revisión de UN documento: `C` lo deja conforme (y **borra** la observación
   * previa), cualquier otro resultado lo deja observado y exige el porqué.
   *
   * A diferencia de la v1, comprueba el alcance del broker: revisar un
   * documento de otro equipo responde 403 (D-F4-5, divergencia deliberada).
   */
  revisarDocumento(
    idSolicitud: number,
    idDocumento: number,
    resultado: string,
    observaciones?: string,
  ): Promise<DocumentoSolicitud> {
    return this.api.patch<DocumentoSolicitud>(
      `${RECURSO}/${idSolicitud}/documentos/${idDocumento}/revisar`,
      { resultado, observaciones },
    );
  }

  /**
   * "Validar todos": deja conformes **los pendientes**, y devuelve el
   * expediente completo. No toca los ya observados — resolverlos es una
   * decisión, no un atajo.
   */
  conformarDocumentos(idSolicitud: number): Promise<DocumentoSolicitud[]> {
    return this.api.patch<DocumentoSolicitud[]>(`${RECURSO}/${idSolicitud}/documentos/conformar`, {});
  }

  // ------------------------------------------------------------------
  // Historial de evaluaciones
  // ------------------------------------------------------------------

  /**
   * Historial de decisiones de la solicitud. Este GET **lo ve también el agente
   * dueño**, a diferencia de `/evaluaciones`, que es solo de BROKER/ADMIN.
   */
  evaluaciones$(id: number): Observable<Evaluacion[]> {
    return this.api.get$<Evaluacion[]>(`${RECURSO}/${id}/evaluaciones`);
  }

  evaluaciones(id: number): Promise<Evaluacion[]> {
    return this.api.get<Evaluacion[]>(`${RECURSO}/${id}/evaluaciones`);
  }
}
