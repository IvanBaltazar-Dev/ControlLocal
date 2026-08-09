import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api.client';
import { PageResponse } from './api.types';
import { ComandoIdempotente } from '../comando-idempotente';

/**
 * Contrato CONGELADO: espejo de `ContratoResponse`.
 *
 * Dos cosas que condicionan cómo se lee:
 * - **`montoAgente` y `montoEmpresa` solo viajan para ADMIN y BROKER.** Al
 *   agente ni siquiera le llegan (Jackson omite nulos), así que su ausencia no
 *   significa "sin reparto": significa "no es asunto tuyo".
 * - La comisión es de la **liquidación**, no del contrato: `comisionEstado`
 *   viaja como código unitario P/R/C/A y un contrato puede no tener
 *   liquidación todavía.
 */
export interface Contrato {
  id: number;
  idSolicitud?: number;
  codigoSolicitud?: string;
  idOportunidad?: number;
  codigoOportunidad?: string;
  clienteNombre?: string;
  direccionLocal?: string;
  distritoLocal?: string;
  /** D disponible, N no disponible, I inactivo. */
  estadoDisponibilidadLocal?: string;
  codigoCaptacion?: string;
  agenteNombre?: string;
  rentaMensual?: number;
  moneda?: string;
  plazoContratoMeses?: number;
  comisionGenerada?: number;
  /** Siempre hereda la moneda de la renta final del contrato. */
  monedaComision?: string;
  fechaInicioContrato?: string;
  fechaFinContrato?: string;
  fechaCierre?: string;
  estadoContrato?: string;
  comisionEstado?: string;
  incidencias?: string;
  idComision?: number;
  agenteId?: number;
  propietarioId?: number;
  propietarioNombre?: string;
  montoAgente?: number;
  montoEmpresa?: number;
  formaPago?: string;
  fechaCobro?: string;
}

/**
 * Filtros del listado. Son **aditivos**: omitirlos devuelve exactamente lo que
 * el cable congelado devolvía, incluido el orden por id descendente.
 *
 * Ojo con `tamano`: el defecto de este recurso es **100**, no 10.
 */
export interface FiltrosContratos {
  pagina?: number;
  tamano?: number;
  texto?: string;
  distrito?: string;
  idAgente?: number;
  /** `cierre` ordena por fecha de cierre descendente. */
  orden?: string;
}

export interface AgenteConCierres {
  id: number;
  nombre: string;
}

export interface ResumenCierres {
  cierres: number;
  /** Compatibilidad para una sola moneda; se omite cuando hay varias. */
  comisionGenerada?: number;
  moneda?: string;
  comisionesGeneradas: ImporteMoneda[];
  montosCobrados: ImporteMoneda[];
  saldosPendientes: ImporteMoneda[];
  montosPagadosAgente: ImporteMoneda[];
  saldosPendientesAgente: ImporteMoneda[];
  porLiquidar: number;
  sinLiquidacion: number;
  distritosDisponibles: string[];
  agentesDisponibles: AgenteConCierres[];
}

export interface ImporteMoneda {
  moneda: string;
  monto: number;
}

/**
 * Espejo de `ContratoRequest`. **Las condiciones del trato no viajan aquí**
 * —renta, plazo, forma de pago, garantía y adelanto se leen de la solicitud
 * aprobada, y la comisión la deriva el backend—: el agente solo captura la
 * formalización del cierre.
 *
 * Los tres campos tienen defecto en el backend (cierre = hoy, estado =
 * `V` vigente, sin incidencias), pero la pantalla los pide explícitos porque
 * son la única decisión que se toma ahí.
 */
export interface DatosContrato {
  idSolicitud?: number;
  fechaCierre?: string;
  /** Solo `D` firmado o `V` vigente: el cierre no admite otros. */
  estadoContrato?: string;
  incidencias?: string;
}

@Injectable({ providedIn: 'root' })
export class ContratosService {
  private readonly api = inject(ApiClient);

  pagina$(filtros: FiltrosContratos = {}): Observable<PageResponse<Contrato>> {
    return this.api.get$<PageResponse<Contrato>>('contratos', { ...filtros });
  }

  pagina(filtros: FiltrosContratos = {}): Promise<PageResponse<Contrato>> {
    return this.api.get<PageResponse<Contrato>>('contratos', { ...filtros });
  }

  /** KPI con exactamente los mismos criterios de negocio que la tabla. */
  resumen$(filtros: FiltrosContratos = {}): Observable<ResumenCierres> {
    return this.api.get$<ResumenCierres>('contratos/resumen', {
      texto: filtros.texto,
      distrito: filtros.distrito,
      idAgente: filtros.idAgente,
    });
  }

  /**
   * El contrato de una oportunidad, si ya se cerró. Responde **404 cuando no
   * hay ninguno**, que es el caso normal mientras la operación sigue viva: quien
   * lo llame tiene que tratar ese 404 como "todavía no", no como un error.
   */
  porOportunidad$(idOportunidad: number): Observable<Contrato> {
    return this.api.get$<Contrato>(`contratos/oportunidad/${idOportunidad}`);
  }

  porOportunidad(idOportunidad: number): Promise<Contrato> {
    return this.api.get<Contrato>(`contratos/oportunidad/${idOportunidad}`);
  }

  /**
   * **La operación más pesada del sistema**: una transacción con siete efectos
   * —crea el contrato y la comisión PENDIENTE, cierra la oportunidad como
   * exitosa, cierra la solicitud y la captación, deja el local NO DISPONIBLE
   * dando de baja sus publicaciones, y resuelve tareas y alertas—.
   *
   * De ahí que no exista un botón de "cerrar oportunidad exitosa": ese cierre
   * lo produce esto. Tres precondiciones explican los 400: solicitud APROBADA,
   * oportunidad todavía abierta y sin contrato previo.
   */
  registrar(datos: DatosContrato): Promise<Contrato> {
    return this.api.post<Contrato>('contratos', datos);
  }

  /**
   * Asigna la parte del agente. **El monto de la empresa NO viaja**: lo calcula
   * el backend como el resto de la comisión bruta, así que aquí no hay forma de
   * descuadrar el reparto.
   *
   * Las tres operaciones de comisión llevan gate de `BROKER` **sin ADMIN**
   * (funciona porque el filtro JWT publica una sola authority): al administrador
   * le responden 403 y por eso la pantalla no se las ofrece.
   */
  asignarComision(idContrato: number, montoAgente: number): Promise<Contrato> {
    return this.api.post<Contrato>(`contratos/${idContrato}/comision/asignar`, { montoAgente });
  }

  /** Desenlace del cobro al cliente: `C` cobrada o `A` anulada. */
  registrarCobro(idContrato: number, datos: DatosCobroComision): Promise<Contrato> {
    return this.api.post<Contrato>(`contratos/${idContrato}/comision/cobro`, datos);
  }

  /**
   * Movimiento suelto de la liquidación. Es lo que permite un cobro **parcial**
   * (estado `R`) o el pago al agente en cuotas, sin cerrar la comisión.
   *
   * <p>`clave` viaja como `Idempotency-Key`. Con ella, reenviar el MISMO
   * comando devuelve el resultado original sin volver a cobrar; reenviarla con
   * datos distintos es un 409. La produce {@link ComandoIdempotente}, no este
   * método: la clave pertenece a la operación, no a la llamada.
   */
  registrarMovimiento(
    idContrato: number,
    datos: DatosMovimientoComision,
    clave?: string,
  ): Promise<Contrato> {
    return this.api.post<Contrato>(
      `contratos/${idContrato}/comision/movimientos`,
      datos,
      undefined,
      clave ? { 'Idempotency-Key': clave } : undefined,
    );
  }

  /**
   * Comando idempotente para UNA operación de movimiento. Se crea al abrir el
   * diálogo y se desecha al cerrarlo: mientras viva, sus reintentos comparten
   * clave y su doble clic no produce un segundo cobro.
   */
  nuevoComandoMovimiento(idContrato: number): ComandoIdempotente<DatosMovimientoComision, Contrato> {
    return new ComandoIdempotente<DatosMovimientoComision, Contrato>(
      (datos, clave) => this.registrarMovimiento(idContrato, datos, clave),
      huellaMovimiento,
    );
  }
}

/**
 * Los campos que definen la operación, en orden fijo. Cambiar cualquiera de
 * ellos es empezar otra operación y merece una clave nueva.
 */
export function huellaMovimiento(datos: DatosMovimientoComision): string {
  return [
    datos.tipo,
    String(datos.monto),
    datos.moneda,
    datos.fecha ?? '',
    datos.formaPago ?? '',
    datos.observacion ?? '',
  ].join('|');
}

export interface DatosCobroComision {
  /** `C` cobrada · `A` anulada. */
  estado: string;
  fechaCobro?: string;
  formaPago?: string;
}

/**
 * Tipos del movimiento: `C` cobro, `P` pago al agente, `R` reverso.
 *
 * `A` (ajuste) **ya no se ofrece**: nunca tuvo una regla economica que
 * definiera que saldo modifica, y el backend lo rechaza con 400. El CHECK de
 * la base lo conserva solo por si hubiera filas historicas.
 */
export interface DatosMovimientoComision {
  tipo: string;
  monto: number;
  moneda: string;
  fecha?: string;
  formaPago?: string;
  observacion?: string;
}
