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
  distinctUntilChanged,
  forkJoin,
  map,
  Observable,
  of,
  Subject,
  switchMap,
} from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { Agente, AgentesService, ResumenAgentes } from '../../core/api/agentes.service';
import { ApiError, paginaVacia, PageResponse } from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';
import { BroxPageHeader } from '../../shared/page-header/page-header';

const POR_PAGINA = RESULTADOS_POR_PAGINA;

const RESUMEN_VACIO: ResumenAgentes = {
  total: 0,
  activos: 0,
  inactivos: 0,
  disponibles: 0,
  ocupados: 0,
  vacaciones: 0,
  suspendidos: 0,
  zonas: [],
};

const ESTADO_OPERATIVO: Readonly<Record<string, string>> = {
  D: 'Disponible',
  O: 'Ocupado',
  V: 'Vacaciones',
  S: 'Suspendido',
};

interface FiltrosUrl {
  texto: string;
  estado: string;
  estadoOperativo: string;
  zona: string;
  page: number;
}

type ResultadoCarga =
  | { pagina: PageResponse<Agente>; resumen: ResumenAgentes }
  | { error: string };

/** Directorio con filtros SQL y proyección mínima de gobierno o comercial según contexto. */
@Component({
  selector: 'cl-agentes',
  imports: [
    BarraFiltros,
    BroxPageHeader,
    EstadoListado,
    FiltroSelect,
    Paginacion,
    TarjetaKpi,
  ],
  templateUrl: './agentes.html',
  styleUrl: './agentes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Agentes implements OnInit {
  private readonly servicio = inject(AgentesService);
  private readonly auth = inject(AuthService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly datos = signal<PageResponse<Agente>>(paginaVacia<Agente>());
  protected readonly resumen = signal<ResumenAgentes>(RESUMEN_VACIO);
  protected readonly filtros = signal<FiltrosUrl>({
    texto: '',
    estado: '',
    estadoOperativo: '',
    zona: '',
    page: 1,
  });
  protected readonly porPagina = POR_PAGINA;
  protected readonly guardando = signal(false);

  protected readonly opcionesOperativo: OpcionFiltro[] = [
    { valor: 'D', etiqueta: 'Disponible' },
    { valor: 'O', etiqueta: 'Ocupado' },
    { valor: 'V', etiqueta: 'Vacaciones' },
    { valor: 'S', etiqueta: 'Suspendido' },
  ];

  protected readonly pagina = computed(() => this.filtros().page);
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!(f.texto || f.estado || f.estadoOperativo || f.zona);
  });

  private readonly recargar$ = new Subject<void>();

  protected readonly esBroker = computed(() => this.auth.sesion()?.rol === 'BROKER');
  /**
    * D-S0-17 filas 17 y 18: dar de alta y editar agentes es **gobierno**, no
    * supervisión. El broker deja de poder hacer las dos cosas — antes el alta
    * creaba su propia supervisión inicial, y ahora el supervisor viaja en la
    * petición porque quien gobierna no supervisa a nadie de quien deducirlo.
    */
  protected readonly gobierna = computed(() => this.auth.sesion()?.rol === 'TENANT_ADMIN');
  protected readonly puedeCrear = computed(() => this.gobierna());
  protected readonly puedeEditar = computed(() => this.gobierna());

  protected readonly filas = computed(() => this.datos().items ?? []);
  protected readonly actividadComercialVisible = computed(() => this.filas().some(
    a => a.captacionesActivas !== undefined || a.operacionesActivas !== undefined));
  protected readonly total = computed(() => this.datos().totalRecords ?? 0);

  protected readonly avisoAlcance = computed(() =>
    this.esBroker()
      ? 'Consultas únicamente los agentes que supervisas.'
      : 'Administras los datos organizacionales y las pertenencias, sin consultar su cartera comercial.',
  );

  /**
   * Los filtros viven en la URL: recargar la página o compartir el enlace
   * conserva la búsqueda. Cada cambio dispara UNA lectura cancelable.
   */
  ngOnInit(): void {
    this.ruta.queryParamMap
      .pipe(
        map((params) => this.filtrosDe(params)),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(this.destroyRef),
        switchMap((filtros) => {
          this.filtros.set(filtros);
          return this.leer$();
        }),
      )
      .subscribe((resultado) => this.aplicar(resultado));

    this.recargar$
      .pipe(
        switchMap(() => this.leer$()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.aplicar(resultado));
  }

  private filtrosDe(params: ParamMap): FiltrosUrl {
    const page = Number(params.get('page'));
    return {
      texto: params.get('texto') ?? '',
      estado: params.get('estado') ?? '',
      estadoOperativo: params.get('estadoOperativo') ?? '',
      zona: params.get('zona') ?? '',
      page: Number.isFinite(page) && page >= 1 ? Math.floor(page) : 1,
    };
  }

  /**
   * Lista y resumen se piden juntos y del MISMO conjunto: si se pidieran por
   * separado, los cubos podrían describir un filtro y la tabla otro.
   */
  private leer$(): Observable<ResultadoCarga> {
    this.cargando.set(true);
    this.error.set(null);
    const f = this.filtros();
    return forkJoin({
      pagina: this.servicio.pagina$({
        pagina: f.page,
        tamano: POR_PAGINA,
        texto: f.texto || undefined,
        estado: f.estado || undefined,
        estadoOperativo: f.estadoOperativo || undefined,
        zona: f.zona || undefined,
      }),
      resumen: this.servicio.resumen$({
        texto: f.texto || undefined,
        estado: f.estado || undefined,
        estadoOperativo: f.estadoOperativo || undefined,
      }),
    }).pipe(
      catchError((fallo: ApiError) =>
        of({ error: fallo.message || 'No se pudo cargar el equipo de agentes.' }),
      ),
    );
  }

  private aplicar(resultado: ResultadoCarga): void {
    this.cargando.set(false);
    if ('error' in resultado) {
      this.error.set(resultado.error);
      this.datos.set(paginaVacia<Agente>());
      this.resumen.set(RESUMEN_VACIO);
      return;
    }
    this.datos.set(resultado.pagina);
    this.resumen.set(resultado.resumen);
  }

  /** Cambiar cualquier filtro vuelve a la página 1: la 7 podría no existir ya. */
  private navegar(cambios: Partial<FiltrosUrl>): void {
    const f = { ...this.filtros(), ...cambios };
    const page = 'page' in cambios ? f.page : 1;
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: {
        texto: f.texto || null,
        estado: f.estado || null,
        estadoOperativo: f.estadoOperativo || null,
        zona: f.zona || null,
        page: page === 1 ? null : page,
      },
      queryParamsHandling: 'merge',
    });
  }

  protected buscar(texto: string): void {
    this.navegar({ texto });
  }

  protected cambiarEstado(estado: string): void {
    this.navegar({ estado });
  }

  protected cambiarOperativo(estadoOperativo: string): void {
    this.navegar({ estadoOperativo });
  }

  protected cambiarZona(zona: string): void {
    this.navegar({ zona });
  }

  protected limpiarFiltros(): void {
    this.navegar({ texto: '', estado: '', estadoOperativo: '', zona: '' });
  }

  protected irAPagina(page: number): void {
    this.navegar({ page });
  }

  protected reintentar(): void {
    this.recargar$.next();
  }

  protected abrir(agente: Agente): void {
    void this.router.navigate(['/agentes', agente.id]);
  }

  protected nuevo(): void {
    void this.router.navigate(['/agentes/nuevo']);
  }

  protected editar(agente: Agente): void {
    void this.router.navigate(['/agentes', agente.id, 'editar']);
  }

  // -- presentación --------------------------------------------------------

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected activo(agente: Agente): boolean {
    return agente.estadoAdministrativo === 'A';
  }

  protected operativo(agente: Agente): string {
    return ESTADO_OPERATIVO[agente.estadoOperativo ?? ''] ?? SIN_DATO;
  }

  /** Clase de tono del estado operativo; solo `D` es "todo bien". */
  protected tonoOperativo(agente: Agente): string {
    return agente.estadoOperativo === 'D' ? 'ok' : 'aviso';
  }
}
