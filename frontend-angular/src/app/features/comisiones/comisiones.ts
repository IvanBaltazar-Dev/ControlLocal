import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiError, PageResponse, paginaVacia } from '../../core/api/api.types';
import { describir, ESTADO_COMISION, ESTADO_CONTRATO } from '../../core/api/codigos';
import {
  Contrato,
  ContratosService,
  DatosMovimientoComision,
  ResumenCierres,
} from '../../core/api/contratos.service';
import { AuthService } from '../../core/auth/auth.service';
import { ComandoIdempotente } from '../../core/comando-idempotente';
import { fechaCorta, monto as formatoMonto, SIN_DATO } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = 10;

const RESUMEN_VACIO: ResumenCierres = {
  cierres: 0,
  comisionesGeneradas: [],
  montosCobrados: [],
  saldosPendientes: [],
  montosPagadosAgente: [],
  saldosPendientesAgente: [],
  porLiquidar: 0,
  sinLiquidacion: 0,
  distritosDisponibles: [],
  agentesDisponibles: [],
};

/** Tipos de movimiento del cable. Cobro y pago son los dos del día a día. */
/**
 * `A` (Ajuste) **ya no se ofrece**: nunca existió una regla económica que
 * dijera qué saldo modifica, con qué signo ni contra qué tope, y el backend lo
 * rechaza con 400. Dejarlo en el desplegable era ofrecer un botón que solo
 * podía fallar.
 */
const TIPOS_MOVIMIENTO = [
  { valor: 'C', etiqueta: 'Cobro al cliente' },
  { valor: 'P', etiqueta: 'Pago al agente' },
  { valor: 'R', etiqueta: 'Reversión' },
];

/**
 * Liquidación de comisiones: qué se generó, qué se cobró y qué se le pagó al
 * agente por cada cierre.
 *
 * **Generado, cobrado y pagado son tres cosas distintas** y la pantalla las
 * muestra separadas a propósito. Un único total es justo lo que oculta el
 * problema: una comisión facturada y no cobrada se leería como dinero en caja.
 *
 * Reglas del contrato que explican qué se ofrece y qué no:
 *
 * - **Las tres operaciones son de BROKER, sin ADMIN.** No es un olvido del
 *   gate: es como está en el cable, así que al administrador se le muestra la
 *   lectura y no los botones —ofrecérselos sería prometerle un 403—.
 * - **`montoAgente` y `montoEmpresa` solo viajan para ADMIN y BROKER.** Al
 *   agente ni le llegan (Jackson omite nulos), y su ausencia no significa "sin
 *   reparto" sino "no es asunto tuyo": por eso esas columnas no se pintan
 *   vacías para él, no se pintan.
 * - **El reparto no se descuadra desde aquí**: al asignar solo viaja la parte
 *   del agente; la de la empresa la calcula el backend con el resto.
 * - **Un movimiento parcial deja la comisión en `R`**, no en `C`. Cobrar del
 *   todo es el desenlace (`C`) y anularla también (`A`); son operaciones
 *   distintas y se ofrecen aparte.
 * - Los totales salen de `/contratos/resumen`, **calculados en la base sobre el
 *   mismo conjunto que pagina la tabla**: con el tope de 100 filas por página,
 *   sumarlos en el cliente daría un número falso pasados los 100 cierres y no
 *   lo avisaría.
 */
@Component({
  selector: 'app-comisiones',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, FormsModule, Paginacion, TarjetaKpi],
  templateUrl: './comisiones.html',
  styleUrl: './comisiones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Comisiones implements OnInit {
  private readonly api = inject(ContratosService);
  private readonly auth = inject(AuthService);

  protected readonly sinDato = SIN_DATO;
  protected readonly tamano = POR_PAGINA;
  protected readonly tiposMovimiento = TIPOS_MOVIMIENTO;

  protected readonly pagina = signal<PageResponse<Contrato>>(paginaVacia<Contrato>(POR_PAGINA));
  protected readonly resumen = signal<ResumenCierres>(RESUMEN_VACIO);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly texto = signal('');
  protected readonly distrito = signal('');
  protected readonly idAgente = signal('');
  protected readonly numeroPagina = signal(1);

  /** Solo el BROKER liquida; los otros dos roles leen. */
  protected readonly puedeLiquidar = computed(() => this.auth.sesion()?.rol === 'BROKER');
  /** El reparto interno solo llega —y solo importa— a quien supervisa. */
  protected readonly veReparto = computed(() => this.auth.sesion()?.rol !== 'AGENTE');

  protected readonly hayFiltros = computed(
    () => !!this.texto() || !!this.distrito() || !!this.idAgente(),
  );

  // --- Diálogos de liquidación -------------------------------------------

  protected readonly asignando = signal<Contrato | null>(null);
  protected readonly montoAgente = signal('');
  protected readonly cobrando = signal<Contrato | null>(null);
  protected readonly estadoCobro = signal('C');
  protected readonly fechaCobro = signal('');
  protected readonly formaPago = signal('');
  protected readonly moviendo = signal<Contrato | null>(null);
  /**
   * Clave de idempotencia viva mientras el diálogo de movimiento esté abierto.
   * No es una señal: no se pinta, y su identidad no debe disparar renders.
   */
  private comandoMovimiento: ComandoIdempotente<DatosMovimientoComision, Contrato> | null = null;
  protected readonly tipoMovimiento = signal('C');
  protected readonly montoMovimiento = signal('');
  protected readonly fechaMovimiento = signal('');
  protected readonly observacion = signal('');
  protected readonly guardando = signal(false);
  protected readonly errorDialogo = signal<string | null>(null);

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    const filtros = {
      texto: this.texto() || undefined,
      distrito: this.distrito() || undefined,
      idAgente: this.idAgente() ? Number(this.idAgente()) : undefined,
    };
    try {
      const [pagina, resumen] = await Promise.all([
        this.api.pagina({ ...filtros, pagina: this.numeroPagina(), tamano: POR_PAGINA, orden: 'cierre' }),
        this.api.resumen$(filtros).toPromise(),
      ]);
      this.pagina.set(pagina);
      this.resumen.set(resumen ?? RESUMEN_VACIO);
    } catch (fallo) {
      this.pagina.set(paginaVacia<Contrato>(POR_PAGINA));
      this.resumen.set(RESUMEN_VACIO);
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudieron cargar las comisiones.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  protected aplicar(): void {
    this.numeroPagina.set(1);
    void this.cargar();
  }

  protected cambiarPagina(valor: number): void {
    this.numeroPagina.set(valor);
    void this.cargar();
  }

  protected limpiar(): void {
    this.texto.set('');
    this.distrito.set('');
    this.idAgente.set('');
    this.aplicar();
  }

  protected readonly agentes = computed(() =>
    this.resumen().agentesDisponibles.map((a) => ({ valor: String(a.id), etiqueta: a.nombre })),
  );

  // --- Presentación -------------------------------------------------------

  protected estadoComision(contrato: Contrato): string {
    return describir(ESTADO_COMISION, contrato.comisionEstado) || 'Sin liquidación';
  }

  protected tonoComision(contrato: Contrato): string {
    switch (contrato.comisionEstado) {
      case 'C':
        return 'bien';
      case 'R':
        return 'aviso';
      case 'A':
        return 'mal';
      default:
        return '';
    }
  }

  protected estadoContrato(contrato: Contrato): string {
    return describir(ESTADO_CONTRATO, contrato.estadoContrato) || SIN_DATO;
  }

  protected importe(valor: number | undefined, moneda: string | undefined): string {
    return valor === undefined || valor === null ? SIN_DATO : formatoMonto(valor, moneda);
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  /** Solo tiene sentido liquidar lo que ya tiene liquidación y no está anulada. */
  protected liquidable(contrato: Contrato): boolean {
    return (
      this.puedeLiquidar() && !!contrato.idComision && contrato.comisionEstado !== 'A'
    );
  }

  // --- Operaciones --------------------------------------------------------

  protected abrirAsignacion(contrato: Contrato): void {
    this.errorDialogo.set(null);
    this.montoAgente.set(contrato.montoAgente != null ? String(contrato.montoAgente) : '');
    this.asignando.set(contrato);
  }

  protected async guardarAsignacion(): Promise<void> {
    const contrato = this.asignando();
    const valor = Number(this.montoAgente());
    if (!contrato) {
      return;
    }
    if (!Number.isFinite(valor) || valor < 0) {
      this.errorDialogo.set('Escribe un monto válido para el agente.');
      return;
    }
    await this.ejecutar(() => this.api.asignarComision(contrato.id, valor), () =>
      this.asignando.set(null),
    );
  }

  protected abrirCobro(contrato: Contrato): void {
    this.errorDialogo.set(null);
    this.estadoCobro.set('C');
    this.fechaCobro.set(hoy());
    this.formaPago.set(contrato.formaPago ?? '');
    this.cobrando.set(contrato);
  }

  protected async guardarCobro(): Promise<void> {
    const contrato = this.cobrando();
    if (!contrato) {
      return;
    }
    await this.ejecutar(
      () =>
        this.api.registrarCobro(contrato.id, {
          estado: this.estadoCobro(),
          fechaCobro: this.fechaCobro() || undefined,
          formaPago: this.formaPago() || undefined,
        }),
      () => this.cobrando.set(null),
    );
  }

  /**
   * Abrir el diálogo ES el inicio de la operación económica, así que aquí nace
   * su clave de idempotencia. Vive mientras el diálogo esté abierto: los
   * reintentos la comparten y el doble clic no produce un segundo cobro.
   */
  protected abrirMovimiento(contrato: Contrato): void {
    this.errorDialogo.set(null);
    this.tipoMovimiento.set('C');
    this.montoMovimiento.set('');
    this.fechaMovimiento.set(hoy());
    this.observacion.set('');
    this.comandoMovimiento = this.api.nuevoComandoMovimiento(contrato.id);
    this.moviendo.set(contrato);
  }

  protected async guardarMovimiento(): Promise<void> {
    const contrato = this.moviendo();
    const valor = Number(this.montoMovimiento());
    if (!contrato) {
      return;
    }
    if (!Number.isFinite(valor) || valor <= 0) {
      this.errorDialogo.set('El monto del movimiento debe ser mayor que cero.');
      return;
    }
    const comando = this.comandoMovimiento;
    if (!comando) {
      return;
    }
    await this.ejecutar(
      () =>
        comando.enviar({
          tipo: this.tipoMovimiento(),
          monto: valor,
          // La moneda tiene que coincidir con la de la liquidación: se toma de
          // la propia comisión en vez de pedirla y arriesgar un 400 evitable.
          moneda: contrato.monedaComision ?? contrato.moneda ?? 'PEN',
          fecha: this.fechaMovimiento() || undefined,
          observacion: this.observacion() || undefined,
        }),
      () => {
        this.comandoMovimiento = null;
        this.moviendo.set(null);
      },
    );
  }

  protected cerrarDialogos(): void {
    this.asignando.set(null);
    this.cobrando.set(null);
    this.moviendo.set(null);
    // Cerrar el diálogo termina la operación: la siguiente estrena clave.
    this.comandoMovimiento = null;
    this.errorDialogo.set(null);
  }

  /**
   * Todas las operaciones terminan igual: recargar. El backend devuelve el
   * contrato actualizado, pero **los totales del resumen cambian también**, y
   * dejarlos viejos mostraría un saldo que ya no es.
   */
  private async ejecutar(operacion: () => Promise<unknown>, alTerminar: () => void): Promise<void> {
    this.guardando.set(true);
    this.errorDialogo.set(null);
    try {
      await operacion();
      alTerminar();
      this.aviso.set(null);
      await this.cargar();
    } catch (fallo) {
      this.errorDialogo.set(mensajeDeFallo(fallo));
    } finally {
      this.guardando.set(false);
    }
  }
}

/**
 * Un **409 es un conflicto, no un error genérico**: significa que la clave de
 * idempotencia de esta operación ya se usó para otra distinta. Mostrar el texto
 * técnico del backend dejaría al broker sin saber qué hacer; lo que tiene que
 * hacer es cerrar y volver a empezar, porque eso estrena clave.
 */
export function mensajeDeFallo(fallo: unknown): string {
  if (fallo instanceof ApiError && fallo.status === 409) {
    return (
      'Esta operación ya se registró con otros datos. Cierra el diálogo y vuelve ' +
      'a abrirlo para registrar una operación nueva.'
    );
  }
  return fallo instanceof ApiError ? fallo.message : 'No se pudo registrar la operación.';
}

/** Fecha de hoy en el formato del input date, sin correr un día por zona. */
function hoy(): string {
  const ahora = new Date();
  const mes = String(ahora.getMonth() + 1).padStart(2, '0');
  const dia = String(ahora.getDate()).padStart(2, '0');
  return `${ahora.getFullYear()}-${mes}-${dia}`;
}
