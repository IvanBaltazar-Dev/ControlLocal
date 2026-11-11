import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/** Directorio organizacional; el acceso personal y la pertenencia tienen contratos propios. */
export interface Agente {
  id: number;
  codigoAgente?: string;
  nombre?: string;
  tipoPersona?: string;
  tipoDocumento?: string;
  numeroDocumento?: string;
  telefono?: string;
  correo?: string;
  usuario?: string;
  zona?: string;
  fechaIngreso?: string;
  estadoAdministrativo?: string;
  estadoOperativo?: string;
  /** Ausentes en gobierno; nunca sustituir un dato reservado por cero. */
  captacionesActivas?: number;
  operacionesActivas?: number;
}

/** Edición laboral autorizada; sin credenciales ni alta/baja de pertenencia. */
export interface DatosAgente {
  nombre?: string;
  telefono?: string;
  correoLaboral?: string;
  zona?: string;
  estadoOperativo?: string;
}

/**
 * Filtros del catálogo. Los cuatro son **aditivos y opcionales**: omitidos, el
 * endpoint responde exactamente lo que respondía antes de que existieran.
 * `estado` es el administrativo de la pertenencia y `estadoOperativo` el
 * del agente: son dos máquinas distintas y un agente activo puede estar de
 * vacaciones.
 */
export interface FiltrosAgentes {
  pagina?: number;
  tamano?: number;
  texto?: string;
  estado?: string;
  estadoOperativo?: string;
  zona?: string;
}

/** Cubos del catálogo, calculados en la base sobre el mismo conjunto. */
export interface ResumenAgentes {
  total: number;
  activos: number;
  inactivos: number;
  disponibles: number;
  ocupados: number;
  vacaciones: number;
  suspendidos: number;
  /** Zonas del alcance completo, para que el selector sea data-driven. */
  zonas: string[];
}

export interface SupervisionVigente {
  idBroker?: number;
  brokerNombre?: string;
  codigoBroker?: string;
  fechaAsignacion?: string;
  motivo?: string;
}

export interface ConteoEstado {
  estado: string;
  descripcion: string;
  total: number;
}

export interface ImportePorMoneda {
  moneda: string;
  monto: number;
}

/**
 * Las cuatro magnitudes van **separadas y por moneda** porque responden
 * preguntas distintas y casi nunca coinciden: lo pactado no es lo cobrado, y lo
 * que le toca al agente no es lo que ya se le pagó. PEN y USD nunca se suman.
 */
export interface ComisionesAgente {
  generada: ImportePorMoneda[];
  cobrada: ImportePorMoneda[];
  pendienteCobro: ImportePorMoneda[];
  asignadaAgente: ImportePorMoneda[];
  pagadaAgente: ImportePorMoneda[];
  pendientePagoAgente: ImportePorMoneda[];
}

export interface CierreDeAgente {
  idContrato?: number;
  codigoSolicitud?: string;
  direccionLocal?: string;
  distrito?: string;
  clienteNombre?: string;
  fechaCierre?: string;
  estadoContrato?: string;
  rentaContractual?: number;
  moneda?: string;
}

/**
 * Ficha completa del agente. **Llega en una sola llamada**: no se arma
 * combinando páginas de las bandejas de captaciones, oportunidades, solicitudes
 * y cierres, que además daría números falsos —cada listado pagina, y contar
 * sobre la página visible no es contar—.
 *
 * Los cierres y el dinero salen de la **atribución histórica** grabada al
 * cerrar (V27), no de la cadena solicitud→agente: un agente que cambió de
 * equipo conserva su historia.
 */
export interface FichaAgente {
  agente: Agente;
  supervision?: SupervisionVigente;
  captaciones: ConteoEstado[];
  oportunidades: ConteoEstado[];
  solicitudes: ConteoEstado[];
  cierres: number;
  comisiones: ComisionesAgente;
  ultimosCierres: CierreDeAgente[];
}

const RECURSO = 'agentes';

/** Consultas y edición local; Core aplica autoridad y aislamiento. */
@Injectable({ providedIn: 'root' })
export class AgentesService {
  private readonly api = inject(ApiClient);

  /** Cancelable: `switchMap` aborta la lectura anterior al cambiar de filtro. */
  pagina$(filtros: FiltrosAgentes = {}): Observable<PageResponse<Agente>> {
    return this.api.get$<PageResponse<Agente>>(RECURSO, { ...filtros });
  }

  pagina(pagina = 1, tamano = 50): Promise<PageResponse<Agente>> {
    return this.api.get<PageResponse<Agente>>(RECURSO, { pagina, tamano });
  }

  /** No recibe `zona`: es una de las opciones que devuelve. */
  resumen$(
    filtros: Omit<FiltrosAgentes, 'zona' | 'pagina' | 'tamano'> = {},
  ): Observable<ResumenAgentes> {
    return this.api.get$<ResumenAgentes>(`${RECURSO}/resumen`, { ...filtros });
  }

  /**
   * Ficha completa. El BROKER solo alcanza a los agentes que supervisa **hoy**;
   * fuera de su equipo el backend responde 403, y eso la pantalla lo explica
   * como alcance, no como error.
   */
  ficha$(id: number): Observable<FichaAgente> {
    return this.api.get$<FichaAgente>(`${RECURSO}/${id}`);
  }

  ficha(id: number): Promise<FichaAgente> {
    return this.api.get<FichaAgente>(`${RECURSO}/${id}`);
  }

  actualizar(id: number, datos: DatosAgente): Promise<Agente> {
    return this.api.put<Agente>(`${RECURSO}/${id}`, datos);
  }
  perfilParaGobierno(id: number): Promise<Agente> {
    return this.api.get<Agente>(`${RECURSO}/${id}/perfil`);
  }
}
