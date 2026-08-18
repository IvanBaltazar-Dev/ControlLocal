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
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { ApiError, paginaVacia, PageResponse } from '../../core/api/api.types';
import {
  Captacion,
  CaptacionesService,
} from '../../core/api/captaciones.service';
import { describir, ESTADO_CAPTACION } from '../../core/api/codigos';
import { AgenteOpcion, PersonalService } from '../../core/api/personal.service';
import { AuthService } from '../../core/auth/auth.service';
import { descripcionCondicionComision } from '../../core/comision';
import { comoFecha, fechaCorta, numero, SIN_DATO, texto } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = RESULTADOS_POR_PAGINA;
const ESTADOS_VALIDOS = new Set(['P', 'O', 'A', 'R', 'C', 'V']);

export interface FiltrosCaptacionesUrl {
  texto: string;
  estado: string;
  idAgente: number | null;
  page: number;
}

interface MetricasCaptaciones {
  total: number;
  pendientes: number;
  observadas: number;
  activas: number;
  cerradas: number;
  rechazadas: number;
  vencidas: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Captacion>;
  metricas: MetricasCaptaciones;
}

type ResultadoCarga = CargaCorrecta | { error: string };

const METRICAS_VACIAS: MetricasCaptaciones = {
  total: 0,
  pendientes: 0,
  observadas: 0,
  activas: 0,
  cerradas: 0,
  rechazadas: 0,
  vencidas: 0,
};

/**
 * Bandeja general de captaciones. El listado, la búsqueda, el estado, el
 * agente y la paginación se resuelven en SQL; Angular nunca descarga toda la
 * cartera para volver a filtrarla como hacía la pantalla Blazor.
 */
@Component({
  selector: 'app-captaciones',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './captaciones.html',
  styleUrl: './captaciones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Captaciones implements OnInit {
  private readonly captaciones = inject(CaptacionesService);
  private readonly personal = inject(PersonalService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Captacion>>(paginaVacia(POR_PAGINA));
  protected readonly metricas = signal<MetricasCaptaciones>(METRICAS_VACIAS);
  protected readonly filtros = signal<FiltrosCaptacionesUrl>({
    texto: '',
    estado: '',
    idAgente: null,
    page: 1,
  });
  protected readonly agentes = signal<AgenteOpcion[]>([]);

  protected readonly porPagina = POR_PAGINA;
  protected readonly puedeRegistrar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly supervisa = computed(() => this.auth.sesion()?.rol !== 'AGENTE');
  protected readonly subtitulo = computed(() =>
    this.puedeRegistrar()
      ? 'Expedientes de captación que registraste y su estado de revisión'
      : 'Captaciones dentro de tu alcance de supervisión',
  );
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.estado || !!f.idAgente;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna captación coincide con los filtros.'
      : 'Todavía no hay captaciones en tu alcance.',
  );
  protected readonly opcionesEstado: OpcionFiltro[] = Object.entries(ESTADO_CAPTACION).map(
    ([valor, etiqueta]) => ({ valor, etiqueta }),
  );
  protected readonly opcionesAgente = computed<OpcionFiltro[]>(() =>
    this.agentes().map((a) => ({ valor: String(a.id), etiqueta: a.nombre })),
  );

  ngOnInit(): void {
    this.cargarAgentes();
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosCaptacionesDesdeUrl),
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
            pagina: this.captaciones.pagina$({
              pagina: filtros.page,
              tamano: POR_PAGINA,
              estado: filtros.estado,
              idAgente: filtros.idAgente ?? undefined,
              q: filtros.texto,
            }),
            metricas: this.cargarMetricas(filtros.idAgente),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la bandeja de captaciones.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected nueva(): void {
    void this.router.navigate(['/captaciones/nueva']);
  }

  protected editar(codigo: string | undefined): void {
    if (codigo) {
      void this.router.navigate(['/captaciones', codigo, 'editar']);
    }
  }

  protected verLocal(idLocal: number | undefined): void {
    if (idLocal) {
      void this.router.navigate(['/propiedades', idLocal]);
    }
  }

  protected verResumen(codigo: string | undefined): void {
    if (codigo) {
      void this.router.navigate(['/captaciones', codigo, 'ficha']);
    }
  }

  protected verExpediente(codigo: string | undefined): void {
    if (codigo) {
      void this.router.navigate(['/captaciones', codigo]);
    }
  }

  protected seleccionarEstado(estado: string): void {
    this.navegar({ estado: this.filtros().estado === estado ? '' : estado, page: 1 });
  }

  protected cambiarTexto(valor: string): void {
    const normalizado = valor.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, page: 1 });
    }
  }

  protected cambiarEstado(estado: string): void {
    this.navegar({ estado, page: 1 });
  }

  protected cambiarAgente(valor: string): void {
    this.navegar({ idAgente: idPositivo(valor), page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', estado: '', idAgente: null, page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_CAPTACION, codigo) || SIN_DATO;
  }

  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'A' || codigo === 'C') return 'bien';
    if (codigo === 'P' || codigo === 'O') return 'aviso';
    return codigo === 'R' || codigo === 'V' ? 'mal' : '';
  }

  protected vigencia(c: Captacion): string {
    if (!c.fechaInicioVigencia && !c.fechaFinVigencia) return SIN_DATO;
    const inicio = fechaCorta(c.fechaInicioVigencia);
    const fin = fechaCorta(c.fechaFinVigencia);
    return `${inicio} – ${fin}`;
  }

  protected diasRestantes(c: Captacion): string {
    const fin = comoFecha(c.fechaFinVigencia);
    if (!fin) return SIN_DATO;
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const dias = Math.ceil((fin.getTime() - hoy.getTime()) / 86_400_000);
    if (dias < 0) return `Venció hace ${Math.abs(dias)} día(s)`;
    if (dias === 0) return 'Vence hoy';
    return `${numero(dias, 0)} día(s) restantes`;
  }

  protected vencida(c: Captacion): boolean {
    const fin = comoFecha(c.fechaFinVigencia);
    if (!fin) return false;
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    return fin < hoy;
  }

  protected comision(captacion: Captacion): string {
    return descripcionCondicionComision(captacion);
  }

  protected valor(valor: string | undefined): string {
    return texto(valor);
  }

  private cargarMetricas(idAgente: number | null): Observable<MetricasCaptaciones> {
    const conteo = (estado?: string) =>
      this.captaciones.pagina$({
        estado,
        idAgente: idAgente ?? undefined,
        pagina: 1,
        tamano: 1,
      });
    return forkJoin({
      total: conteo(),
      pendientes: conteo('P'),
      observadas: conteo('O'),
      activas: conteo('A'),
      cerradas: conteo('C'),
      rechazadas: conteo('R'),
      vencidas: conteo('V'),
    }).pipe(
      map((r) => ({
        total: r.total.totalRecords,
        pendientes: r.pendientes.totalRecords,
        observadas: r.observadas.totalRecords,
        activas: r.activas.totalRecords,
        cerradas: r.cerradas.totalRecords,
        rechazadas: r.rechazadas.totalRecords,
        vencidas: r.vencidas.totalRecords,
      })),
    );
  }

  private cargarAgentes(): void {
    if (!this.supervisa()) return;
    this.personal
      .agentes$()
      .pipe(catchError(() => of(paginaVacia<AgenteOpcion>(100))), takeUntilDestroyed(this.destroyRef))
      .subscribe((pagina) => this.agentes.set(pagina.items));
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosCaptacionesUrl,
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
      this.metricas.set(METRICAS_VACIAS);
      this.error.set(resultado.error);
    } else {
      this.paginaDatos.set(resultado.pagina);
      this.metricas.set(resultado.metricas);
    }
    this.cargando.set(false);
  }

  private navegar(cambios: Partial<FiltrosCaptacionesUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        estado: siguiente.estado || null,
        idAgente: siguiente.idAgente,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosCaptacionesDesdeUrl(params: ParamMap): FiltrosCaptacionesUrl {
  const texto = (params.get('texto') ?? '').trim();
  const solicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const estado = ESTADOS_VALIDOS.has(solicitado) ? solicitado : '';
  const idAgente = idPositivo(params.get('idAgente'));
  const solicitada = Number(params.get('page') ?? '1');
  const page = Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1;
  return { texto, estado, idAgente, page };
}

function idPositivo(valor: string | null): number | null {
  const numero = Number(valor);
  return Number.isSafeInteger(numero) && numero > 0 ? numero : null;
}

function mismosFiltros(a: FiltrosCaptacionesUrl, b: FiltrosCaptacionesUrl): boolean {
  return a.texto === b.texto && a.estado === b.estado && a.idAgente === b.idAgente && a.page === b.page;
}
