import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  EMPTY,
  forkJoin,
  map,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { ApiError, paginaVacia, PageResponse } from '../../core/api/api.types';
import {
  Captacion,
  CaptacionesService,
  FiltrosCaptacionesReasignables,
  ReasignacionCaptacion,
} from '../../core/api/captaciones.service';
import { AgenteOpcion, PersonalService } from '../../core/api/personal.service';
import { descargarCsv } from '../../core/csv';
import { fechaHora, SIN_DATO, texto } from '../../core/formato';
import { POLITICA_COMERCIAL } from '../../core/politica-comercial';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = 8;
const POR_PAGINA_HISTORIAL = 10;

/**
 * Largo mínimo del motivo. **La regla es del backend** —lo rechaza igual si se
 * llama al API directamente, que hasta E1 no ocurría—; esto solo avisa antes de
 * enviar, que es mejor que un 400 después. Ver `core/politica-comercial.ts`.
 */
const MINIMO_MOTIVO = POLITICA_COMERCIAL.motivoReasignacionCaracteres;

interface FiltrosReasignacionUrl {
  texto: string;
  page: number;
}

type ResultadoCaptaciones = PageResponse<Captacion> | { error: string };

/**
 * Gobierno de responsables de captación.
 *
 * Consolida los dos Razor del broker (reasignar + historial) porque son una
 * misma tarea: elegir un expediente activo, moverlo a un agente disponible y
 * comprobar inmediatamente la trazabilidad generada. La reasignación no es
 * una transición de estado; ese invariante lo impone el service Spring.
 */
@Component({
  selector: 'app-reasignaciones-captacion',
  imports: [
    ReactiveFormsModule,
    BarraFiltros,
    DialogoConfirmacion,
    EstadoListado,
    FiltroSelect,
    Paginacion,
    TarjetaKpi,
  ],
  templateUrl: './reasignaciones-captacion.html',
  styleUrl: './reasignaciones-captacion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReasignacionesCaptacion implements OnInit {
  private readonly api = inject(CaptacionesService);
  private readonly personal = inject(PersonalService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargarCaptaciones$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly cargandoGobierno = signal(true);
  protected readonly procesando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorGobierno = signal<string | null>(null);
  protected readonly errorAccion = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);
  protected readonly avisoExportacion = signal<string | null>(null);
  protected readonly dialogo = signal(false);
  protected readonly paginaDatos = signal<PageResponse<Captacion>>(paginaVacia(POR_PAGINA));
  protected readonly agentes = signal<AgenteOpcion[]>([]);
  protected readonly historial = signal<ReasignacionCaptacion[]>([]);
  protected readonly filtros = signal<FiltrosReasignacionUrl>({ texto: '', page: 1 });
  protected readonly idCaptacion = signal<number | null>(null);
  protected readonly idAgente = signal<number | null>(null);
  protected readonly busquedaAgente = signal('');
  protected readonly busquedaHistorial = signal('');
  protected readonly brokerHistorial = signal('');
  protected readonly agenteHistorial = signal('');
  protected readonly paginaHistorialActual = signal(1);
  protected readonly motivo = new FormControl('', { nonNullable: true });
  protected readonly minimoMotivo = MINIMO_MOTIVO;

  protected readonly porPagina = POR_PAGINA;
  protected readonly porPaginaHistorial = POR_PAGINA_HISTORIAL;
  protected readonly sinDato = SIN_DATO;
  protected readonly captacionSeleccionada = computed(() =>
    this.paginaDatos().items.find((captacion) => captacion.id === this.idCaptacion()) ?? null,
  );
  protected readonly agentesDestino = computed(() => {
    const actual = this.captacionSeleccionada()?.idAgente;
    const q = normalizar(this.busquedaAgente());
    return this.agentes().filter((agente) =>
      agente.id !== actual
      && agente.estadoAdministrativo === 'A'
      && agente.estadoOperativo === 'D'
      && (!q || normalizar([
        agente.nombre,
        agente.codigoAgente,
        agente.numeroDocumento,
        agente.zona,
      ].join(' ')).includes(q)),
    );
  });
  protected readonly agenteSeleccionado = computed(() =>
    this.agentesDestino().find((agente) => agente.id === this.idAgente()) ?? null,
  );
  protected readonly historialFiltrado = computed(() => {
    const q = normalizar(this.busquedaHistorial());
    const broker = this.brokerHistorial();
    const agente = this.agenteHistorial();
    return this.historial().filter((evento) =>
      (!broker || evento.brokerNombre === broker)
      && (!agente || evento.agenteAnteriorNombre === agente || evento.agenteNuevoNombre === agente)
      && (!q || normalizar([
        evento.codigoCaptacion,
        evento.direccionLocal,
        evento.agenteAnteriorNombre,
        evento.agenteNuevoNombre,
        evento.brokerNombre,
        evento.motivo,
      ].join(' ')).includes(q)),
    );
  });
  protected readonly historialVisible = computed(() => {
    const inicio = (this.paginaHistorialActual() - 1) * POR_PAGINA_HISTORIAL;
    return this.historialFiltrado().slice(inicio, inicio + POR_PAGINA_HISTORIAL);
  });
  protected readonly agentesUnicos = computed(() => new Set(
    this.historial().flatMap((evento) => [evento.agenteAnteriorNombre, evento.agenteNuevoNombre])
      .filter((nombre): nombre is string => !!nombre),
  ).size);
  protected readonly brokersUnicos = computed(() => new Set(
    this.historial().map((evento) => evento.brokerNombre).filter((nombre): nombre is string => !!nombre),
  ).size);
  protected readonly recientes = computed(() => {
    const desde = Date.now() - 30 * 24 * 60 * 60 * 1000;
    return this.historial().filter((evento) => {
      const fecha = evento.fechaCambio ? Date.parse(evento.fechaCambio) : Number.NaN;
      return Number.isFinite(fecha) && fecha >= desde;
    }).length;
  });
  protected readonly opcionesBroker = computed<OpcionFiltro[]>(() =>
    [...new Set(this.historial().map((evento) => evento.brokerNombre).filter((v): v is string => !!v))]
      .sort((a, b) => a.localeCompare(b, 'es'))
      .map((nombre) => ({ valor: nombre, etiqueta: nombre })),
  );
  protected readonly opcionesAgenteHistorial = computed<OpcionFiltro[]>(() =>
    [...new Set(this.historial().flatMap((evento) => [evento.agenteAnteriorNombre, evento.agenteNuevoNombre])
      .filter((v): v is string => !!v))]
      .sort((a, b) => a.localeCompare(b, 'es'))
      .map((nombre) => ({ valor: nombre, etiqueta: nombre })),
  );
  protected readonly hayFiltrosHistorial = computed(() =>
    !!this.busquedaHistorial() || !!this.brokerHistorial() || !!this.agenteHistorial(),
  );

  ngOnInit(): void {
    this.cargarGobierno();
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosReasignacionesDesdeUrl),
      distinctUntilChanged((a, b) => a.texto === b.texto && a.page === b.page),
    );
    combineLatest([filtrosUrl$, this.recargarCaptaciones$.pipe(startWith(undefined))])
      .pipe(
        map(([filtros]) => filtros),
        tap((filtros) => {
          this.filtros.set(filtros);
          this.cargando.set(true);
          this.error.set(null);
        }),
        switchMap((filtros) => this.api.reasignables$(this.filtrosApi(filtros)).pipe(
          switchMap((pagina) => this.corregirPagina(filtros, pagina)),
          catchError((error) => of({
            error: mensajeError(error, 'No se pudieron cargar las captaciones reasignables.'),
          })),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicarCaptaciones(resultado));
  }

  protected cambiarTexto(texto: string): void {
    const limpio = texto.trim();
    if (limpio !== this.filtros().texto) this.navegar({ texto: limpio, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiarCaptaciones(): void {
    this.navegar({ texto: '', page: 1 });
  }

  protected seleccionarCaptacion(id: number): void {
    this.idCaptacion.set(id);
    this.idAgente.set(null);
    this.errorAccion.set(null);
    this.exito.set(null);
  }

  protected seleccionarAgente(id: number): void {
    this.idAgente.set(id);
    this.errorAccion.set(null);
  }

  protected cambiarBusquedaAgente(valor: string): void {
    this.busquedaAgente.set(valor);
  }

  protected prepararReasignacion(): void {
    const captacion = this.captacionSeleccionada();
    const agente = this.agenteSeleccionado();
    const motivo = this.motivo.value.trim();
    if (!captacion) {
      this.errorAccion.set('Selecciona una captación activa para reasignar.');
      return;
    }
    if (!agente) {
      this.errorAccion.set('Selecciona un agente activo y disponible de tu alcance.');
      return;
    }
    if (motivo.length < MINIMO_MOTIVO) {
      this.errorAccion.set(
        `Explica el motivo con al menos ${MINIMO_MOTIVO} caracteres: queda en el historial de la captación.`,
      );
      this.motivo.markAsTouched();
      return;
    }
    this.errorAccion.set(null);
    this.dialogo.set(true);
  }

  protected cerrarDialogo(): void {
    if (!this.procesando()) this.dialogo.set(false);
  }

  protected async confirmarReasignacion(): Promise<void> {
    const captacion = this.captacionSeleccionada();
    const agente = this.agenteSeleccionado();
    const motivo = this.motivo.value.trim();
    if (!captacion || !agente || motivo.length < MINIMO_MOTIVO || this.procesando()) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    this.exito.set(null);
    try {
      await this.api.reasignar(captacion.id, agente.id, motivo);
      this.exito.set(`${texto(captacion.codigoCaptacion)} fue reasignada a ${agente.nombre}.`);
      this.dialogo.set(false);
      this.idAgente.set(null);
      this.motivo.reset('');
      this.recargarCaptaciones$.next();
      this.cargarHistorial();
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudo reasignar la captación.'));
    } finally {
      this.procesando.set(false);
    }
  }

  protected cambiarBusquedaHistorial(valor: string): void {
    this.busquedaHistorial.set(valor.trim());
    this.paginaHistorialActual.set(1);
  }

  protected cambiarBroker(valor: string): void {
    this.brokerHistorial.set(valor);
    this.paginaHistorialActual.set(1);
  }

  protected cambiarAgenteHistorial(valor: string): void {
    this.agenteHistorial.set(valor);
    this.paginaHistorialActual.set(1);
  }

  protected cambiarPaginaHistorial(page: number): void {
    this.paginaHistorialActual.set(page);
  }

  protected limpiarHistorial(): void {
    this.busquedaHistorial.set('');
    this.brokerHistorial.set('');
    this.agenteHistorial.set('');
    this.paginaHistorialActual.set(1);
  }

  protected exportarHistorial(): void {
    const filas = this.historialFiltrado();
    if (filas.length === 0) {
      this.avisoExportacion.set('No hay movimientos que exportar con los filtros actuales.');
      return;
    }
    const nombre = descargarCsv(
      'historial_reasignaciones_captaciones',
      ['Fecha', 'Captación', 'Local', 'Agente anterior', 'Agente nuevo', 'Broker responsable', 'Motivo'],
      filas.map((evento) => [
        evento.fechaCambio,
        evento.codigoCaptacion,
        evento.direccionLocal,
        evento.agenteAnteriorNombre,
        evento.agenteNuevoNombre,
        evento.brokerNombre,
        evento.motivo,
      ]),
    );
    this.avisoExportacion.set(`Se exportaron ${filas.length} movimientos en ${nombre}.`);
  }

  protected recargar(): void {
    this.recargarCaptaciones$.next();
    this.cargarGobierno();
  }

  protected valor(valor: string | undefined): string {
    return texto(valor);
  }

  protected fecha(valor: string | undefined): string {
    return fechaHora(valor);
  }

  private cargarGobierno(): void {
    this.cargandoGobierno.set(true);
    this.errorGobierno.set(null);
    forkJoin({
      agentes: this.personal.agentes$(),
      historial: this.api.historialReasignaciones$(),
    }).pipe(
      catchError((error) => {
        this.errorGobierno.set(mensajeError(error, 'No se pudieron cargar agentes e historial.'));
        return of({ agentes: paginaVacia<AgenteOpcion>(100), historial: [] });
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(({ agentes, historial }) => {
      this.agentes.set(agentes.items);
      this.historial.set(historial);
      this.cargandoGobierno.set(false);
    });
  }

  private cargarHistorial(): void {
    this.api.historialReasignaciones$().pipe(
      catchError((error) => {
        this.errorGobierno.set(mensajeError(error, 'La reasignación se guardó, pero no se pudo refrescar el historial.'));
        return EMPTY;
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((historial) => this.historial.set(historial));
  }

  private filtrosApi(filtros: FiltrosReasignacionUrl): FiltrosCaptacionesReasignables {
    return { pagina: filtros.page, tamano: POR_PAGINA, q: filtros.texto || undefined };
  }

  private corregirPagina(
    filtros: FiltrosReasignacionUrl,
    pagina: PageResponse<Captacion>,
  ): Observable<PageResponse<Captacion>> {
    const ultima = Math.max(1, Math.ceil(pagina.totalRecords / POR_PAGINA));
    if (filtros.page > ultima) {
      this.navegar({ page: ultima }, true);
      return EMPTY;
    }
    return of(pagina);
  }

  private publicarCaptaciones(resultado: ResultadoCaptaciones): void {
    if ('error' in resultado) {
      this.paginaDatos.set(paginaVacia(POR_PAGINA));
      this.error.set(resultado.error);
      this.idCaptacion.set(null);
    } else {
      this.paginaDatos.set(resultado);
      if (!resultado.items.some((captacion) => captacion.id === this.idCaptacion())) {
        this.idCaptacion.set(resultado.items[0]?.id ?? null);
        this.idAgente.set(null);
      }
    }
    this.cargando.set(false);
  }

  private navegar(cambios: Partial<FiltrosReasignacionUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { texto: siguiente.texto || null, page: siguiente.page },
      replaceUrl,
    });
  }
}

export function filtrosReasignacionesDesdeUrl(params: ParamMap): FiltrosReasignacionUrl {
  const pagina = Number(params.get('page') ?? '1');
  return {
    texto: (params.get('texto') ?? '').trim(),
    page: Number.isSafeInteger(pagina) && pagina > 0 ? pagina : 1,
  };
}

function normalizar(valor: string): string {
  return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es').trim();
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
