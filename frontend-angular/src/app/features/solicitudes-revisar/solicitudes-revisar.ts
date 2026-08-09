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
import { describir, ESTADO_SOLICITUD } from '../../core/api/codigos';
import {
  PENDIENTES,
  ResumenSolicitudes,
  Solicitud,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { fechaCorta, monto as montoDe, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';
import { RESUMEN_SOLICITUDES_VACIO } from '../solicitudes/solicitudes';

const POR_PAGINA = 10;
/** Los tres cubos que la cola ofrece; `PENDIENTES` es el defecto. */
const VISTAS_VALIDAS = new Set([PENDIENTES, 'E', 'O']);

export interface FiltrosRevisarUrl {
  texto: string;
  vista: string;
  distrito: string;
  idAgente: string;
  page: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Solicitud>;
  resumen: ResumenSolicitudes;
}

type ResultadoCarga = CargaCorrecta | { error: string };

/**
 * Cola del broker: las solicitudes que esperan una decisión suya.
 *
 * **Por qué es una pantalla aparte de la bandeja general** y no un filtro más:
 * es el punto de entrada del trabajo del broker —cada fila lleva a evaluar—,
 * mientras que `Solicitudes` es consulta. El Blazor las tenía separadas por lo
 * mismo, y el precedente dentro del SPA es `BandejaCaptaciones` frente a
 * `Captaciones`.
 *
 * **La cola es `E` + `O`, y la `O` no es un descuido.** Una observada espera al
 * agente, no al broker; sigue aquí porque es *su* observación la que la puso ahí
 * y quiere verla hasta que se resuelva. Es la definición del legado, replicada.
 *
 * El cubo llega del backend como `estado=PENDIENTES`, que **no es un estado**
 * sino la unión de los dos —igual que `GESTION` en prospecciones—, así que la
 * cola se pagina en la base con una sola consulta en vez de dos.
 */
@Component({
  selector: 'app-solicitudes-revisar',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './solicitudes-revisar.html',
  styleUrl: './solicitudes-revisar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitudesRevisar implements OnInit {
  private readonly api = inject(SolicitudesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Solicitud>>(paginaVacia(POR_PAGINA));
  protected readonly resumen = signal<ResumenSolicitudes>(RESUMEN_SOLICITUDES_VACIO);
  protected readonly filtros = signal<FiltrosRevisarUrl>({
    texto: '',
    vista: PENDIENTES,
    distrito: '',
    idAgente: '',
    page: 1,
  });

  protected readonly porPagina = POR_PAGINA;
  protected readonly pendientes = PENDIENTES;
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || f.vista !== PENDIENTES || !!f.distrito || !!f.idAgente;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna solicitud coincide con los filtros.'
      : 'No hay solicitudes esperando tu decisión.',
  );
  protected readonly opcionesDistrito = computed<OpcionFiltro[]>(() =>
    this.resumen().distritos.map((d) => ({ valor: d, etiqueta: d })),
  );
  protected readonly opcionesAgente = computed<OpcionFiltro[]>(() =>
    this.resumen().agentes.map((a) => ({ valor: String(a.id), etiqueta: a.nombre })),
  );

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosRevisarDesdeUrl),
      distinctUntilChanged(mismosFiltros),
    );

    combineLatest([filtrosUrl$, this.recargar$.pipe(startWith(undefined))])
      .pipe(
        map(([filtros]) => filtros),
        tap((filtros) => {
          this.filtros.set(filtros);
          this.cargando.set(true);
          this.error.set(null);
        }),
        switchMap((filtros) =>
          forkJoin({
            pagina: this.api.pagina$({
              pagina: filtros.page,
              tamano: POR_PAGINA,
              estado: filtros.vista,
              distrito: filtros.distrito || undefined,
              idAgente: Number(filtros.idAgente) || undefined,
              texto: filtros.texto || undefined,
            }),
            // El resumen cuenta TODOS los cubos del alcance, no solo la cola:
            // por eso los KPI de "en revisión" y "observadas" cuadran con la
            // bandeja general aunque aquí solo se listen esos dos.
            resumen: this.api.resumen$({ texto: filtros.texto || undefined }),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la cola de revisión.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected evaluar(codigo: string | undefined): void {
    if (codigo) void this.router.navigate(['/solicitudes', codigo, 'evaluar']);
  }

  protected verExpediente(codigo: string | undefined): void {
    if (codigo) void this.router.navigate(['/solicitudes', codigo]);
  }

  protected verTodas(): void {
    void this.router.navigate(['/solicitudes']);
  }

  protected cambiarTexto(texto: string): void {
    const normalizado = texto.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, page: 1 });
    }
  }

  /** Los tres KPI de arriba son las tres vistas de la cola, no filtros sueltos. */
  protected cambiarVista(vista: string): void {
    this.navegar({ vista: VISTAS_VALIDAS.has(vista) ? vista : PENDIENTES, page: 1 });
  }

  protected cambiarDistrito(distrito: string): void {
    this.navegar({ distrito, page: 1 });
  }

  protected cambiarAgente(idAgente: string): void {
    this.navegar({ idAgente, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', vista: PENDIENTES, distrito: '', idAgente: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_SOLICITUD, codigo) || SIN_DATO;
  }

  /** En la cola solo hay dos estados, y ninguno es un desenlace. */
  protected tonoEstado(codigo: string | undefined): string {
    return codigo === 'O' ? 'aviso' : 'bien';
  }

  protected avanceDocumentos(s: Solicitud): number {
    const requeridos = s.documentosRequeridos ?? 0;
    if (requeridos <= 0) {
      return 0;
    }
    return Math.round(((s.documentosEntregados ?? 0) * 100) / requeridos);
  }

  protected checklist(s: Solicitud): string {
    return `${s.documentosEntregados ?? 0}/${s.documentosRequeridos ?? 0}`;
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  protected monto(valor: number | undefined, moneda: string | undefined): string {
    return montoDe(valor, moneda);
  }

  protected plazo(s: Solicitud): string {
    if (s.plazoMeses && s.plazoMeses > 0) {
      return `${s.plazoMeses} meses`;
    }
    return textoDe(s.plazoTentativo);
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosRevisarUrl,
    carga: CargaCorrecta,
  ): Observable<CargaCorrecta> {
    const ultima = Math.max(1, Math.ceil(carga.pagina.totalRecords / POR_PAGINA));
    if (filtros.page > ultima) {
      this.navegar({ page: ultima }, true);
      return EMPTY;
    }
    return of(carga);
  }

  private publicar(resultado: ResultadoCarga): void {
    if ('error' in resultado) {
      this.paginaDatos.set(paginaVacia(POR_PAGINA));
      this.resumen.set(RESUMEN_SOLICITUDES_VACIO);
      this.error.set(resultado.error);
    } else {
      this.paginaDatos.set(resultado.pagina);
      this.resumen.set(resultado.resumen);
    }
    this.cargando.set(false);
  }

  private navegar(cambios: Partial<FiltrosRevisarUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        // El defecto no ensucia la URL.
        vista: siguiente.vista === PENDIENTES ? null : siguiente.vista,
        distrito: siguiente.distrito || null,
        idAgente: siguiente.idAgente || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosRevisarDesdeUrl(params: ParamMap): FiltrosRevisarUrl {
  const vistaSolicitada = (params.get('vista') ?? '').trim().toUpperCase();
  const agenteSolicitado = Number(params.get('idAgente') ?? '');
  const solicitada = Number(params.get('page') ?? '1');
  return {
    texto: (params.get('texto') ?? '').trim(),
    // Una vista inventada en la URL cae al cubo por defecto: esta pantalla
    // NUNCA lista fuera de la cola, ni siquiera escribiendo el estado a mano.
    vista: VISTAS_VALIDAS.has(vistaSolicitada) ? vistaSolicitada : PENDIENTES,
    distrito: (params.get('distrito') ?? '').trim(),
    idAgente:
      Number.isSafeInteger(agenteSolicitado) && agenteSolicitado > 0
        ? String(agenteSolicitado)
        : '',
    page: Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1,
  };
}

function mismosFiltros(a: FiltrosRevisarUrl, b: FiltrosRevisarUrl): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
