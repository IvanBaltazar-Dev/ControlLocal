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
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

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

/**
 * Equipo de agentes inmobiliarios.
 *
 * **El gate es de clase, BROKER y ADMIN**, y el alcance lo decide el backend:
 * el ADMIN ve el tenant y el BROKER **solo los agentes que supervisa**. Al
 * agente no se le ofrece este módulo.
 *
 * **La búsqueda y los filtros los resuelve el backend**, no el navegador:
 * `texto` (nombre, documento, código o zona), `estado` administrativo,
 * `estadoOperativo` y `zona` viajan como parámetros y la base decide. Filtrar
 * en memoria solo filtraría las diez filas cargadas, y «no hay resultados»
 * significaría «no hay en esta página».
 *
 * **Los dos estados son máquinas distintas y no se mezclan**: el administrativo
 * (activo/inactivo) vive en la credencial y el operativo
 * (disponible/ocupado/vacaciones/suspendido) en el agente. Un agente activo
 * puede estar de vacaciones, y eso cambia si se le pueden reasignar encargos.
 *
 * Dos rarezas del cable que la pantalla tiene que respetar:
 *
 * - **`captacionesActivas` y `operacionesActivas` solo son reales aquí**, en el
 *   GET de lista. POST y PUT los responden en `0`, así que tras guardar hay que
 *   releer la lista en vez de creerse la respuesta.
 * - **El ADMIN no puede dar de alta agentes** aunque el gate lo admita: el alta
 *   crea la supervisión inicial *por el broker en sesión*, y el administrador no
 *   supervisa a nadie. Se anticipa aquí en vez de dejar que lo explique un 400.
 */
@Component({
  selector: 'cl-agentes',
  imports: [BarraFiltros, DialogoConfirmacion, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
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
  protected readonly aDarDeBaja = signal<Agente | null>(null);

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
  protected readonly total = computed(() => this.datos().totalRecords ?? 0);

  protected readonly avisoAlcance = computed(() =>
    this.esBroker()
      ? 'Ves y editas únicamente los agentes que supervisas.'
      : 'Como administrador ves todo el tenant, pero el alta de un agente la hace su broker supervisor.',
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

  protected pedirBaja(agente: Agente): void {
    this.aDarDeBaja.set(agente);
  }

  protected cancelarBaja(): void {
    this.aDarDeBaja.set(null);
  }

  /**
   * **No hay DELETE en este recurso**: la baja es administrativa y se hace con
   * el PUT poniendo el estado en `I`. La confirmación no promete un borrado.
   */
  protected async confirmarBaja(): Promise<void> {
    const agente = this.aDarDeBaja();
    if (!agente || this.guardando()) {
      return;
    }
    this.guardando.set(true);
    try {
      await this.servicio.desactivar(agente.id, agente);
      this.aDarDeBaja.set(null);
      this.recargar$.next();
    } catch (fallo) {
      this.error.set((fallo as ApiError).message || 'No se pudo dar de baja al agente.');
      this.aDarDeBaja.set(null);
    } finally {
      this.guardando.set(false);
    }
  }

  protected async reactivar(agente: Agente): Promise<void> {
    if (this.guardando()) {
      return;
    }
    this.guardando.set(true);
    try {
      await this.servicio.reactivar(agente.id, agente);
      this.recargar$.next();
    } catch (fallo) {
      this.error.set((fallo as ApiError).message || 'No se pudo reactivar al agente.');
    } finally {
      this.guardando.set(false);
    }
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
