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
  CaptacionesService,
  PropiedadEquipo,
  ResumenPropiedadesEquipo,
} from '../../core/api/captaciones.service';
import { describir, ESTADO_CAPTACION } from '../../core/api/codigos';
import { numero, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = RESULTADOS_POR_PAGINA;
const RESUMEN_VACIO: ResumenPropiedadesEquipo = {
  propiedades: 0,
  conCaptacionActiva: 0,
  agentesConCartera: 0,
  distritos: 0,
  distritosDisponibles: [],
};

export interface FiltrosEquipoUrl {
  texto: string;
  distrito: string;
  page: number;
}

interface CargaCorrecta {
  pagina: PageResponse<PropiedadEquipo>;
  resumen: ResumenPropiedadesEquipo;
}

type ResultadoCarga = CargaCorrecta | { error: string };

/**
 * Cartera del equipo: los inmuebles captados por los agentes que el broker
 * supervisa. **Solo lectura** — desde aquí se mira la cartera, no se opera.
 *
 * Porta `PropiedadesEquipo.razor` y sigue la convención de listados paginados
 * (`docs/ai/contrato-listados-paginados.md`): la URL es la única fuente que
 * dispara lecturas, `switchMap` cancela la anterior y `forkJoin` publica
 * página y KPI juntos.
 *
 * **La diferencia con el Blazor está en dónde se hace el trabajo.** Allí la
 * pantalla descargaba TODAS las captaciones del equipo, las agrupaba por local
 * en memoria —quedándose con la de vigencia más lejana—, y filtraba, contaba y
 * paginaba en el navegador. Aquí eso baja a SQL: el backend deduplica por
 * inmueble con `DISTINCT ON`, filtra, ordena, pagina y cuenta.
 *
 * Hacía falta una extensión aditiva del backend
 * (`GET /captaciones/propiedades-equipo` + `/resumen`), porque **deduplicar
 * por propiedad no se puede hacer sobre una página**: con el listado normal de
 * captaciones habría que descargarlas todas otra vez.
 *
 * Divergencia deliberada: el legado da esta pantalla **solo al BROKER**; aquí
 * también entra el **ADMIN**, como en el resto de endpoints de supervisión del
 * v2 (`/captaciones/pendientes`, `/reasignables`), y porque el ADMIN es un
 * broker administrador. El menú y el guard usan el mismo mapa que el backend.
 */
@Component({
  selector: 'app-propiedades-equipo',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './propiedades-equipo.html',
  styleUrl: './propiedades-equipo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropiedadesEquipo implements OnInit {
  private readonly captaciones = inject(CaptacionesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<PropiedadEquipo>>(
    paginaVacia(POR_PAGINA),
  );
  protected readonly resumen = signal<ResumenPropiedadesEquipo>(RESUMEN_VACIO);
  protected readonly filtros = signal<FiltrosEquipoUrl>({
    texto: '',
    distrito: '',
    page: 1,
  });

  protected readonly porPagina = POR_PAGINA;
  protected readonly SIN_DATO = SIN_DATO;

  /**
   * Los distritos los manda el backend con el resumen: se ofrece solo lo que
   * la cartera tiene (convención data-driven), sin una llamada extra y sin
   * deducirlos de la página visible, que solo vería 10 filas.
   */
  protected readonly distritos = computed<OpcionFiltro[]>(() =>
    this.resumen().distritosDisponibles.map((valor) => ({ valor, etiqueta: valor })),
  );

  protected readonly hayFiltros = computed(
    () => !!this.filtros().texto || !!this.filtros().distrito,
  );

  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna propiedad del equipo coincide con los filtros.'
      : 'Tu equipo aún no tiene inmuebles captados.',
  );

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosEquipoDesdeUrl),
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
            pagina: this.captaciones.propiedadesEquipo$({
              pagina: filtros.page,
              tamano: POR_PAGINA,
              texto: filtros.texto,
              distrito: filtros.distrito,
            }),
            resumen: this.captaciones.resumenPropiedadesEquipo$(filtros.texto),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la cartera del equipo.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  /** Única acción: es una pantalla de solo lectura. */
  protected verFicha(codigoCaptacion: string | undefined): void {
    if (codigoCaptacion) {
      void this.router.navigate(['/captaciones', codigoCaptacion, 'ficha']);
    }
  }

  protected cambiarTexto(valor: string): void {
    const normalizado = valor.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, page: 1 });
    }
  }

  protected cambiarDistrito(distrito: string): void {
    this.navegar({ distrito, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', distrito: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_CAPTACION, codigo) || SIN_DATO;
  }

  /** Tono por dominio de captación, no por la letra (ver `styles.scss`). */
  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'A') {
      return 'bien';
    }
    return codigo === 'P' || codigo === 'O' ? 'aviso' : codigo === 'R' ? 'mal' : '';
  }

  protected area(valor: number | undefined): string {
    return valor === undefined || valor === null ? SIN_DATO : `${numero(valor)} m²`;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosEquipoUrl,
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

  private navegar(cambios: Partial<FiltrosEquipoUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        distrito: siguiente.distrito || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

/**
 * Interpreta la URL sin permitir página 0 ni NaN. El distrito **no** se valida
 * contra una lista: es texto libre del cable y el backend responde vacío si no
 * existe, que es la respuesta correcta a un filtro sin resultados.
 */
export function filtrosEquipoDesdeUrl(params: ParamMap): FiltrosEquipoUrl {
  const texto = (params.get('texto') ?? '').trim();
  const distrito = (params.get('distrito') ?? '').trim();
  const pageSolicitada = Number(params.get('page') ?? '1');
  const page =
    Number.isSafeInteger(pageSolicitada) && pageSolicitada > 0 ? pageSolicitada : 1;
  return { texto, distrito, page };
}

function mismosFiltros(a: FiltrosEquipoUrl, b: FiltrosEquipoUrl): boolean {
  return a.texto === b.texto && a.distrito === b.distrito && a.page === b.page;
}
