import {
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
import { describir, ESTADO_LOCAL, opcionesPresentes } from '../../core/api/codigos';
import { AuthService } from '../../core/auth/auth.service';
import { monto } from '../../core/formato';
import {
  Local,
  LocalesService,
  ResumenLocales,
} from '../../core/api/locales.service';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = 10;
const ESTADOS_VALIDOS = new Set(['D', 'N', 'I']);
const RESUMEN_VACIO: ResumenLocales = {
  total: 0,
  disponibles: 0,
  noDisponibles: 0,
  inactivos: 0,
};

export interface FiltrosLocalesUrl {
  texto: string;
  estado: string;
  page: number;
  /**
   * "Solo mis captaciones" (RF-004). Sustituye al módulo de menú "Mis locales",
   * que nunca llegó a migrarse: era una pantalla-silo sobre este mismo listado.
   * Solo lo puede activar un AGENTE — es el gate de `GET /locales/mis-locales`.
   */
  mios: boolean;
}

interface CargaCorrecta {
  pagina: PageResponse<Local>;
  resumen: ResumenLocales;
}

type ResultadoCarga = CargaCorrecta | { error: string };

/**
 * Cartera de locales. Esta pantalla fija el patrón de las bandejas nuevas:
 *
 * controles -> query parameters -> servicio -> endpoint paginado ->
 * PageResponse -> componentes compartidos.
 *
 * La URL es la única fuente que dispara lecturas. `switchMap` cancela la
 * petición anterior y `forkJoin` publica página + KPI de forma atómica:
 * nunca se muestra una página nueva con contadores viejos o viceversa.
 */
@Component({
  selector: 'app-locales',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './locales.html',
  styleUrl: './locales.scss',
})
export class Locales implements OnInit {
  private readonly locales = inject(LocalesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Local>>(paginaVacia(POR_PAGINA));
  protected readonly resumen = signal<ResumenLocales>(RESUMEN_VACIO);
  protected readonly filtros = signal<FiltrosLocalesUrl>({
    texto: '',
    estado: '',
    page: 1,
    mios: false,
  });

  protected readonly ESTADO_LOCAL = ESTADO_LOCAL;
  protected readonly porPagina = POR_PAGINA;
  protected readonly estados = opcionesPresentes(ESTADO_LOCAL, ['D', 'N', 'I']);
  protected readonly hayFiltros = computed(
    () => !!this.filtros().texto || !!this.filtros().estado,
  );
  /** El gate de `GET /locales/mis-locales` y el de crear/editar son el mismo. */
  protected readonly esAgente = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly puedeEditar = this.esAgente;
  protected readonly soloMios = computed(() => this.filtros().mios);
  protected readonly mensajeVacio = computed(() => {
    if (this.soloMios()) {
      return 'Todavía no hay locales en tus captaciones.';
    }
    return this.hayFiltros()
      ? 'Ningún local coincide con los filtros.'
      : 'Todavía no hay locales en la cartera.';
  });

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosLocalesDesdeUrl),
      // Un `?mios=1` escrito a mano por quien no es AGENTE pediría un endpoint
      // con gate de rol y se llevaría un 403 en pantalla. Se ignora aquí: el
      // backend sigue mandando, esto solo evita el viaje.
      map((filtros) => (filtros.mios && !this.esAgente() ? { ...filtros, mios: false } : filtros)),
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
          this.cargar(filtros).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la cartera de locales.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  /**
   * Las dos vistas del recurso, detrás del mismo sobre de paginación.
   *
   * "Solo mis captaciones" no lleva `texto` ni `estado` porque
   * `GET /locales/mis-locales` no los acepta —solo pagina—, y tampoco lleva
   * resumen: `GET /locales/resumen` cuenta TODO el tenant, así que pintarlo
   * junto a una lista acotada al agente daría unos KPI que contradicen las
   * filas de debajo. La plantilla los oculta en vez de enseñar números que no
   * corresponden a lo que se ve.
   */
  private cargar(filtros: FiltrosLocalesUrl): Observable<CargaCorrecta> {
    if (filtros.mios) {
      return this.locales
        .misLocales$(filtros.page, POR_PAGINA)
        .pipe(map((pagina) => ({ pagina, resumen: RESUMEN_VACIO })));
    }
    return forkJoin({
      pagina: this.locales.pagina$({
        page: filtros.page,
        tamano: POR_PAGINA,
        texto: filtros.texto,
        estado: filtros.estado,
      }),
      resumen: this.locales.resumen$(filtros.texto),
    });
  }

  protected nuevo(): void {
    void this.router.navigate(['/locales/nuevo']);
  }

  /** Alterna entre la cartera de la corredora y los locales de sus captaciones. */
  protected alternarMios(): void {
    this.navegar({ mios: !this.filtros().mios, texto: '', estado: '', page: 1 });
  }

  protected ver(id: number): void {
    void this.router.navigate(['/locales', id]);
  }

  protected editar(id: number): void {
    void this.router.navigate(['/locales', id, 'editar']);
  }

  /** El KPI y el select escriben exactamente el mismo `estado` de la URL. */
  protected alternarEstado(codigo: string): void {
    const actual = this.filtros().estado;
    this.navegar({ estado: actual === codigo ? '' : codigo, page: 1 });
  }

  protected cambiarEstado(estado: string): void {
    this.navegar({ estado, page: 1 });
  }

  protected cambiarTexto(texto: string): void {
    const normalizado = texto.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, page: 1 });
    }
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  /** No toca `mios`: la barra de filtros solo se pinta en la cartera completa. */
  protected limpiar(): void {
    this.navegar({ texto: '', estado: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_LOCAL, codigo);
  }

  protected renta(local: Local): string {
    return monto(local.precioReferencial, local.monedaReferencial);
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosLocalesUrl,
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
      // Página y KPI fallan juntos: no se conserva información parcial.
      this.paginaDatos.set(paginaVacia(POR_PAGINA));
      this.resumen.set(RESUMEN_VACIO);
      this.error.set(resultado.error);
    } else {
      this.paginaDatos.set(resultado.pagina);
      this.resumen.set(resultado.resumen);
    }
    this.cargando.set(false);
  }

  private navegar(cambios: Partial<FiltrosLocalesUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        estado: siguiente.estado || null,
        page: siguiente.page,
        mios: siguiente.mios ? '1' : null,
      },
      replaceUrl,
    });
  }
}

/** Interpreta la URL sin permitir página 0, NaN o estados ajenos al cable. */
export function filtrosLocalesDesdeUrl(params: ParamMap): FiltrosLocalesUrl {
  const texto = (params.get('texto') ?? '').trim();
  const estadoSolicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const estado = ESTADOS_VALIDOS.has(estadoSolicitado) ? estadoSolicitado : '';
  const pageSolicitada = Number(params.get('page') ?? '1');
  const page =
    Number.isSafeInteger(pageSolicitada) && pageSolicitada > 0 ? pageSolicitada : 1;
  const mios = params.get('mios') === '1';
  // `mios` gana: la vista del agente no admite texto ni estado, así que
  // conservarlos en el objeto los dejaría pintados en la barra sin que
  // filtraran nada. Se descartan aquí, no en la plantilla.
  return mios ? { texto: '', estado: '', page, mios: true } : { texto, estado, page, mios: false };
}

function mismosFiltros(a: FiltrosLocalesUrl, b: FiltrosLocalesUrl): boolean {
  return (
    a.texto === b.texto && a.estado === b.estado && a.page === b.page && a.mios === b.mios
  );
}
