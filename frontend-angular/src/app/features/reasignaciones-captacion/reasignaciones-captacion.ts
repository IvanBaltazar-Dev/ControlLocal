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
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  map,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { ApiError, paginaVacia, PageResponse } from '../../core/api/api.types';
import {
  CandidatoAgente,
  Captacion,
  CaptacionesService,
  FiltrosCaptacionesReasignables,
  ReasignacionCaptacion,
} from '../../core/api/captaciones.service';
import { descargarCsv } from '../../core/csv';
import { fechaHora, SIN_DATO, texto } from '../../core/formato';
import { POLITICA_COMERCIAL } from '../../core/politica-comercial';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = RESULTADOS_POR_PAGINA;
const POR_PAGINA_HISTORIAL = RESULTADOS_POR_PAGINA;

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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargarCaptaciones$ = new Subject<void>();
  /**
   * El texto del buscador de agentes, antes del `debounce`. Va al Core como
   * `texto` y no se resuelve aquí: la lista es del tenant y está paginada.
   */
  private readonly busquedaAgente$ = new Subject<string>();

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
  /**
   * **Los destinos que el Core dice** para la captación seleccionada
   * (D-P0-12). No es «los agentes del tenant»: llega ya depurada por las seis
   * condiciones de elegibilidad y sin el agente actual, y esta pantalla la
   * pinta tal cual.
   */
  protected readonly candidatos = signal<CandidatoAgente[]>([]);
  protected readonly cargandoCandidatos = signal(false);
  protected readonly errorCandidatos = signal<string | null>(null);
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
  /**
   * **Los destinos, tal como los devuelve el Core** (D-P0-12).
   *
   * Hasta el 2026-09-01 esto era un `computed` que pedía `GET /agentes` y
   * depuraba en el cliente por `estadoAdministrativo === 'A'` y
   * `estadoOperativo === 'D'`: **dos** de las seis condiciones de elegibilidad,
   * resueltas sobre **una página** de cien agentes. Ofrecía a gente que el POST
   * rechaza —sin rol vigente, sin membresía viva, del equipo de otro bróker— y
   * escondía a candidatos válidos en cuanto la organización pasara de cien.
   *
   * Ahora **no se filtra nada aquí**: la lista llega ya depurada por las seis
   * condiciones y sin el agente actual, y esta pantalla la pinta tal cual.
   */
  protected readonly agentesDestino = computed(() => this.candidatos());
  protected readonly agenteSeleccionado = computed(() =>
    this.candidatos().find((agente) => agente.idAgente === this.idAgente()) ?? null,
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
    // Los candidatos los decide el CORE, y se piden cuando cambia la captación
    // seleccionada o el texto de búsqueda. El `debounce` es de la búsqueda, no
    // de la selección: cambiar de expediente tiene que recargar la lista al
    // instante, porque los destinos válidos dependen de quién lo lleva hoy.
    this.busquedaAgente$
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((texto) => {
        this.busquedaAgente.set(texto);
        void this.cargarCandidatos();
      });
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
    // Cada encargo tiene SUS destinos: la lista excluye a quien ya lo lleva y
    // depende del alcance del actor sobre ese agente. Reutilizar la del
    // expediente anterior ofrecería al agente actual de éste.
    void this.cargarCandidatos();
  }

  protected seleccionarAgente(id: number): void {
    this.idAgente.set(id);
    this.errorAccion.set(null);
  }

  protected cambiarBusquedaAgente(valor: string): void {
    // El texto viaja al Core como `texto`: la lista es del tenant y está
    // paginada, así que acotar en el cliente devolvería resultados incompletos
    // en cuanto haya más agentes que sitio en una página.
    this.busquedaAgente$.next(valor);
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
      this.errorAccion.set('Selecciona uno de los agentes que el sistema ofrece como destino.');
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

  /**
   * **Declara sobre qué agente se actúa** (D-P0-9).
   *
   * `idAgenteActual` es el agente que se estaba **viendo** en la fila cuando se
   * decidió, no el que haya ahora: si otro bróker lo movió mientras tanto, el
   * Core responde **409** y no escribe nada. Ante ese 409 esta pantalla **no
   * reintenta** —sería ejecutar sobre un estado que nadie miró—: muestra el
   * mensaje del Core y **recarga la lista**, porque el expediente que se estaba
   * mirando ya no está en ese estado.
   */
  protected async confirmarReasignacion(): Promise<void> {
    const captacion = this.captacionSeleccionada();
    const agente = this.agenteSeleccionado();
    const motivo = this.motivo.value.trim();
    const observado = captacion?.idAgente;
    if (!captacion || !agente || observado == null
      || motivo.length < MINIMO_MOTIVO || this.procesando()) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    this.exito.set(null);
    try {
      await this.api.reasignar(captacion.id, agente.idAgente, motivo, observado);
      this.exito.set(`${texto(captacion.codigoCaptacion)} fue reasignada a ${texto(agente.nombre)}.`);
      this.dialogo.set(false);
      this.idAgente.set(null);
      this.motivo.reset('');
      this.recargarCaptaciones$.next();
      this.cargarHistorial();
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudo reasignar la captación.'));
      if (error instanceof ApiError && error.conflicto) {
        // El estado que se vio ya no existe. Se cierra el diálogo y se vuelve a
        // pedir la lista para que la siguiente decisión parta de lo que hay.
        this.dialogo.set(false);
        this.idAgente.set(null);
        this.recargarCaptaciones$.next();
      }
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

  /**
   * El historial de gobierno. **Ya no trae agentes**: la lista de destinos
   * dejó de ser «los del tenant, depurados aquí» y pasó a ser una pregunta por
   * expediente que responde el Core (`cargarCandidatos`).
   */
  private cargarGobierno(): void {
    this.cargandoGobierno.set(true);
    this.errorGobierno.set(null);
    this.api.historialReasignaciones$().pipe(
      catchError((error) => {
        this.errorGobierno.set(mensajeError(error, 'No se pudo cargar el historial.'));
        return of([] as ReasignacionCaptacion[]);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((historial) => {
      this.historial.set(historial);
      this.cargandoGobierno.set(false);
    });
  }

  /**
   * **Los destinos elegibles para ESTE encargo, decididos por el Core**
   * (D-P0-7 + D-P0-12).
   *
   * No filtra ni ordena nada de lo que llega: la lista ya viene depurada por
   * las seis condiciones de elegibilidad y sin el agente actual. Un **403** aquí
   * no es «no hay candidatos» sino «no te corresponde», y se muestra el mensaje
   * del Core en vez de una lista vacía, que es lo que dejaría a alguien
   * buscando un agente que no existe.
   */
  private async cargarCandidatos(): Promise<void> {
    const captacion = this.captacionSeleccionada();
    if (!captacion) {
      this.candidatos.set([]);
      this.errorCandidatos.set(null);
      return;
    }
    const pedido = captacion.id;
    this.cargandoCandidatos.set(true);
    this.errorCandidatos.set(null);
    try {
      const pagina = await this.api.candidatosReasignacion(
        pedido, this.busquedaAgente() || undefined,
      );
      // Si mientras tanto se seleccionó otro expediente, esta respuesta ya no
      // es de lo que se está mirando: pintarla ofrecería destinos de otro.
      if (this.idCaptacion() !== pedido) return;
      this.candidatos.set(pagina.items);
      if (!pagina.items.some((agente) => agente.idAgente === this.idAgente())) {
        this.idAgente.set(null);
      }
    } catch (error) {
      if (this.idCaptacion() !== pedido) return;
      this.candidatos.set([]);
      this.idAgente.set(null);
      this.errorCandidatos.set(
        mensajeError(error, 'No se pudieron cargar los destinos de esta captación.'),
      );
    } finally {
      if (this.idCaptacion() === pedido) this.cargandoCandidatos.set(false);
    }
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
    const anterior = this.expedienteMirado();
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
    // Los destinos dependen de QUÉ expediente se está mirando y de quién lo
    // lleva hoy, así que se vuelven a pedir cuando la selección cambia — y
    // también tras una recarga por 409, que es justamente cuando el agente que
    // se veía ya no es el que hay.
    if (this.expedienteMirado() !== anterior) void this.cargarCandidatos();
  }

  /**
   * **Qué expediente se está mirando, y en manos de quién.**
   *
   * Lleva el agente y no sólo el id a propósito: tras un 409 la captación sigue
   * siendo la misma pero **la lleva otro**, y los destinos válidos cambian —el
   * nuevo responsable sale de la lista y el anterior vuelve a entrar—. Comparar
   * sólo el id dejaría en pantalla una lista que ya no corresponde.
   */
  private expedienteMirado(): string | null {
    const captacion = this.captacionSeleccionada();
    return captacion ? `${captacion.id}:${captacion.idAgente ?? ''}` : null;
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
