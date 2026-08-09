import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from './api.client';
import { PageResponse } from './api.types';

/**
 * Contrato CONGELADO: `AgenteResponse` de la v1 (E1 §2).
 *
 * `id` es el **`persona_rol.id` del rol AGENTE**, no el de la persona: igual
 * que cliente y propietario, el agente es un rol (Party-Role). Es el mismo id
 * que viaja como `idAgente` en las bandejas de todas las verticales.
 *
 * Los dos contadores comerciales **solo llegan con valor real en el GET de
 * lista**: POST y PUT los responden en `0` por paridad con el cable, así que la
 * pantalla no debe creerse el contador que devuelve un alta o una edición.
 */
export interface Agente {
  id: number;
  codigoAgente?: string;
  nombre?: string;
  /** N natural, J jurídica. */
  tipoPersona?: string;
  /** D DNI, R RUC, C carnet, P pasaporte. */
  tipoDocumento?: string;
  numeroDocumento?: string;
  telefono?: string;
  correo?: string;
  usuario?: string;
  zona?: string;
  fechaIngreso?: string;
  /** A activo, I inactivo. */
  estadoAdministrativo?: string;
  /** D disponible, O ocupado, V vacaciones, S suspendido. */
  estadoOperativo?: string;
  /** Captaciones en estados P, O, A. Solo real en el GET de lista. */
  captacionesActivas?: number;
  /** Oportunidades en estados A, S. Solo real en el GET de lista. */
  operacionesActivas?: number;
}

/**
 * Espejo de `AgenteRequest`.
 *
 * En el **alta** `nombre`, `usuario` y `contrasena` son obligatorios. En la
 * **edición** el cable ignora documento, tipos, usuario, contraseña, código y
 * fecha: solo cambia nombre (si no viene vacío), teléfono, correo, estado
 * administrativo, estado operativo y zona cuando llegan.
 */
export interface DatosAgente {
  nombre?: string;
  tipoPersona?: string;
  tipoDocumento?: string;
  numeroDocumento?: string;
  telefono?: string;
  correo?: string;
  usuario?: string;
  contrasena?: string;
  zona?: string;
  codigoAgente?: string;
  /** Estado administrativo: A activo, I inactivo. */
  estado?: string;
  estadoOperativo?: string;
}

/**
 * Filtros del catálogo. Los cuatro son **aditivos y opcionales**: omitidos, el
 * endpoint responde exactamente lo que respondía antes de que existieran.
 * `estado` es el administrativo (vive en la credencial) y `estadoOperativo` el
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

/**
 * `GET /agentes` es de BROKER y ADMIN, con el gate declarado a nivel de clase.
 * El alcance lo decide el backend: el ADMIN ve el tenant y el BROKER solo los
 * agentes que supervisa. La pantalla no replica esa regla, la explica.
 *
 * **No hay GET individual ni DELETE** en este recurso (E1 §2): la ficha de un
 * agente se arma desde la fila del listado, y la baja es un PUT con
 * `estado = 'I'`.
 */
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

  /**
   * El alta crea en una transacción persona, roles `USUARIO_INTERNO` y
   * `AGENTE`, credencial, detalle y la supervisión inicial **por el broker en
   * sesión**. Por eso el ADMIN no puede darlos de alta aunque el gate lo
   * admita: no es supervisor de nadie y el backend responde 400.
   */
  registrar(datos: DatosAgente): Promise<Agente> {
    return this.api.post<Agente>(RECURSO, datos);
  }

  actualizar(id: number, datos: DatosAgente): Promise<Agente> {
    return this.api.put<Agente>(`${RECURSO}/${id}`, datos);
  }

  /**
   * No hay DELETE: la baja es administrativa y se hace con el PUT. Se expone
   * con nombre propio para que la pantalla no tenga que saberlo.
   */
  desactivar(id: number, agente: Agente): Promise<Agente> {
    return this.actualizar(id, this.editables(agente, 'I'));
  }

  reactivar(id: number, agente: Agente): Promise<Agente> {
    return this.actualizar(id, this.editables(agente, 'A'));
  }

  /**
   * Solo los campos que el PUT mira. Mandar el resto no rompe nada —el cable
   * los ignora— pero enviarlos invita a creer que se pueden cambiar.
   */
  private editables(agente: Agente, estado: string): DatosAgente {
    return {
      nombre: agente.nombre,
      telefono: agente.telefono,
      correo: agente.correo,
      zona: agente.zona,
      estadoOperativo: agente.estadoOperativo,
      estado,
    };
  }
}
