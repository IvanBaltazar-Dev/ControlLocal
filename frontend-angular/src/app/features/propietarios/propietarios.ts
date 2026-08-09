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

import { ApiError, paginaVacia, PageResponse } from '../../core/api/api.types';
import { describir, TIPO_DOCUMENTO, TIPO_PERSONA } from '../../core/api/codigos';
import {
  Propietario,
  PropietariosService,
  ResumenPropietarios,
} from '../../core/api/propietarios.service';
import { AuthService } from '../../core/auth/auth.service';
import { SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = 10;

const RESUMEN_VACIO: ResumenPropietarios = { total: 0, activos: 0, inactivos: 0 };

interface FiltrosUrl {
  texto: string;
  estado: string;
  page: number;
}

type ResultadoCarga =
  | { pagina: PageResponse<Propietario>; resumen: ResumenPropietarios }
  | { error: string };

/**
 * Catálogo de propietarios.
 *
 * **La búsqueda y los cubos los resuelve el backend**, no el navegador:
 * `texto` (nombre o razón social, documento y correo) y `estado` viajan como
 * parámetros aditivos y la base decide. Filtrar en memoria solo filtraría las
 * diez filas cargadas, y «no hay resultados» significaría «no hay en esta
 * página».
 *
 * **El alcance NO es igual para los tres roles** y la pantalla lo dice en vez de
 * dejar que el usuario lo deduzca de una lista corta: ADMIN y AGENTE ven el
 * catálogo entero del tenant; el BROKER solo alcanza los propietarios de **sus
 * propiedades** (vía captación o prospección). Por eso `cantidadLocales` es un
 * contador **con alcance**: dos actores ven números distintos del mismo
 * propietario y ninguno está mal.
 *
 * Escribir es de **AGENTE**: alta, edición y baja lógica. Broker y admin leen.
 */
@Component({
  selector: 'cl-propietarios',
  imports: [BarraFiltros, DialogoConfirmacion, EstadoListado, Paginacion, TarjetaKpi],
  templateUrl: './propietarios.html',
  styleUrl: './propietarios.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Propietarios implements OnInit {
  private readonly servicio = inject(PropietariosService);
  private readonly auth = inject(AuthService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly datos = signal<PageResponse<Propietario>>(paginaVacia<Propietario>());
  protected readonly resumen = signal<ResumenPropietarios>(RESUMEN_VACIO);
  protected readonly filtros = signal<FiltrosUrl>({ texto: '', estado: '', page: 1 });
  protected readonly porPagina = POR_PAGINA;

  protected readonly pagina = computed(() => this.filtros().page);
  protected readonly hayFiltros = computed(
    () => !!(this.filtros().texto || this.filtros().estado),
  );

  /** Propietario pendiente de confirmación de baja; null = diálogo cerrado. */
  protected readonly aDesactivar = signal<Propietario | null>(null);
  protected readonly guardando = signal(false);

  private readonly recargar$ = new Subject<void>();

  protected readonly puedeEditar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly filas = computed(() => this.datos().items ?? []);
  protected readonly total = computed(() => this.datos().totalRecords ?? 0);

  /**
   * El alcance del broker se explica arriba de la tabla, no en un tooltip: una
   * lista corta sin explicación se lee como «faltan datos».
   */
  protected readonly avisoAlcance = computed(() =>
    this.auth.sesion()?.rol === 'BROKER'
      ? 'Ves los propietarios de las propiedades de tu equipo. El número de locales también está acotado a tu alcance.'
      : null,
  );

  /** Los filtros viven en la URL: recargar o compartir el enlace los conserva. */
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
      page: Number.isFinite(page) && page >= 1 ? Math.floor(page) : 1,
    };
  }

  /**
   * Cancelable: al cambiar de filtro `switchMap` aborta la lectura anterior.
   * Lista y cubos se piden juntos y del MISMO conjunto, o describirían filtros
   * distintos.
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
      }),
      resumen: this.servicio.resumen$({ texto: f.texto || undefined }),
    }).pipe(
      catchError((fallo: ApiError) =>
        of({ error: fallo.message || 'No se pudo cargar el catálogo de propietarios.' }),
      ),
    );
  }

  private aplicar(resultado: ResultadoCarga): void {
    this.cargando.set(false);
    if ('error' in resultado) {
      this.error.set(resultado.error);
      this.datos.set(paginaVacia<Propietario>());
      this.resumen.set(RESUMEN_VACIO);
      return;
    }
    this.datos.set(resultado.pagina);
    this.resumen.set(resultado.resumen);
  }

  /** Cambiar un filtro vuelve a la página 1: la 7 podría no existir ya. */
  private navegar(cambios: Partial<FiltrosUrl>): void {
    const f = { ...this.filtros(), ...cambios };
    const page = 'page' in cambios ? f.page : 1;
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: {
        texto: f.texto || null,
        estado: f.estado || null,
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

  protected limpiarFiltros(): void {
    this.navegar({ texto: '', estado: '' });
  }

  protected irAPagina(page: number): void {
    this.navegar({ page });
  }

  protected reintentar(): void {
    this.recargar$.next();
  }

  protected nuevo(): void {
    void this.router.navigate(['/propietarios/nuevo']);
  }

  protected editar(propietario: Propietario): void {
    void this.router.navigate(['/propietarios', propietario.id, 'editar']);
  }

  protected abrir(propietario: Propietario): void {
    void this.router.navigate(['/propietarios', propietario.id]);
  }

  protected pedirBaja(propietario: Propietario): void {
    this.aDesactivar.set(propietario);
  }

  protected cancelarBaja(): void {
    this.aDesactivar.set(null);
  }

  /**
   * Baja **lógica**: la persona queda en estado `I`. No desaparece del catálogo
   * ni se borra su historia comercial, así que la confirmación no promete un
   * borrado.
   */
  protected async confirmarBaja(): Promise<void> {
    const propietario = this.aDesactivar();
    if (!propietario || this.guardando()) {
      return;
    }
    this.guardando.set(true);
    try {
      await this.servicio.desactivar(propietario.id);
      this.aDesactivar.set(null);
      this.recargar$.next();
    } catch (fallo) {
      this.error.set((fallo as ApiError).message || 'No se pudo dar de baja al propietario.');
      this.aDesactivar.set(null);
    } finally {
      this.guardando.set(false);
    }
  }

  protected async reactivar(propietario: Propietario): Promise<void> {
    if (this.guardando()) {
      return;
    }
    this.guardando.set(true);
    try {
      await this.servicio.reactivar(propietario.id, propietario);
      this.recargar$.next();
    } catch (fallo) {
      this.error.set((fallo as ApiError).message || 'No se pudo reactivar al propietario.');
    } finally {
      this.guardando.set(false);
    }
  }

  // -- presentación --------------------------------------------------------

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected tipoPersona(codigo: string | undefined): string {
    return describir(TIPO_PERSONA, codigo) || SIN_DATO;
  }

  protected documento(propietario: Propietario): string {
    const tipo = describir(TIPO_DOCUMENTO, propietario.tipoDocumento);
    const numero = textoDe(propietario.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected activo(propietario: Propietario): boolean {
    return propietario.estado === 'A';
  }
}
