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
import { Cliente, ClientesService, ResumenClientes } from '../../core/api/clientes.service';
import { describir, TIPO_DOCUMENTO, TIPO_PERSONA } from '../../core/api/codigos';
import { AuthService } from '../../core/auth/auth.service';
import { SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = 10;
const TIPOS_VALIDOS = new Set(['N', 'J']);
const ESTADOS_VALIDOS = new Set(['A', 'I']);

export interface FiltrosClientesUrl {
  texto: string;
  tipoPersona: string;
  rubro: string;
  estado: string;
  page: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Cliente>;
  resumen: ResumenClientes;
}

type ResultadoCarga = CargaCorrecta | { error: string };

const RESUMEN_VACIO: ResumenClientes = {
  total: 0,
  activos: 0,
  inactivos: 0,
  contactoAutorizado: 0,
  usoDatoAutorizado: 0,
  rubros: [],
};

/**
 * Bandeja de clientes interesados.
 *
 * El Blazor descargaba la cartera entera, filtraba en memoria y derivaba de
 * ahí los KPI y la lista de rubros. Aquí **los cuatro filtros y los cuatro
 * contadores bajan al servidor**: la página, el total y el resumen salen del
 * mismo conjunto, así que no pueden discrepar, y los rubros del selector
 * llegan con el resumen en vez de deducirse de las filas visibles —que con
 * paginación real solo conocería la página actual—.
 *
 * El cliente es **catálogo compartido**: ADMIN y AGENTE ven todos los del
 * tenant y cualquier agente puede editar cualquiera; el único rol acotado es
 * el BROKER, y ese recorte lo aplica el backend, no esta pantalla.
 */
@Component({
  selector: 'app-clientes',
  imports: [BarraFiltros, DialogoConfirmacion, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Clientes implements OnInit {
  private readonly clientes = inject(ClientesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Cliente>>(paginaVacia(POR_PAGINA));
  protected readonly resumen = signal<ResumenClientes>(RESUMEN_VACIO);
  protected readonly filtros = signal<FiltrosClientesUrl>({
    texto: '',
    tipoPersona: '',
    rubro: '',
    estado: '',
    page: 1,
  });

  /** Diálogo de baja/reactivación: null = cerrado. */
  protected readonly enCambioEstado = signal<Cliente | null>(null);
  protected readonly procesando = signal(false);
  protected readonly aviso = signal<string | null>(null);

  protected readonly porPagina = POR_PAGINA;
  /** El alta, la edición y la baja son del AGENTE (contrato F3 §2). */
  protected readonly puedeEditar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.tipoPersona || !!f.rubro || !!f.estado;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ningún cliente coincide con los filtros.'
      : 'Todavía no hay clientes interesados en tu alcance.',
  );
  protected readonly opcionesTipo: OpcionFiltro[] = [
    { valor: 'N', etiqueta: 'Persona natural' },
    { valor: 'J', etiqueta: 'Persona jurídica' },
  ];
  protected readonly opcionesEstado: OpcionFiltro[] = [
    { valor: 'A', etiqueta: 'Activo' },
    { valor: 'I', etiqueta: 'Inactivo' },
  ];
  /** Data-driven: los rubros vienen del resumen, del alcance completo. */
  protected readonly opcionesRubro = computed<OpcionFiltro[]>(() =>
    this.resumen().rubros.map((r) => ({ valor: r, etiqueta: r })),
  );

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosClientesDesdeUrl),
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
            pagina: this.clientes.pagina$({
              pagina: filtros.page,
              tamano: POR_PAGINA,
              texto: filtros.texto || undefined,
              tipoPersona: filtros.tipoPersona || undefined,
              rubro: filtros.rubro || undefined,
              estado: filtros.estado || undefined,
            }),
            // El resumen NO lleva estado: cuenta activos e inactivos.
            resumen: this.clientes.resumen$({
              texto: filtros.texto || undefined,
              tipoPersona: filtros.tipoPersona || undefined,
              rubro: filtros.rubro || undefined,
            }),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la bandeja de clientes.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected nuevo(): void {
    void this.router.navigate(['/clientes/nuevo']);
  }

  protected editar(id: number): void {
    void this.router.navigate(['/clientes', id, 'editar']);
  }

  protected verFicha(id: number): void {
    void this.router.navigate(['/clientes', id]);
  }

  protected cambiarTexto(texto: string): void {
    const normalizado = texto.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, page: 1 });
    }
  }

  protected cambiarTipo(tipoPersona: string): void {
    this.navegar({ tipoPersona, page: 1 });
  }

  protected cambiarRubro(rubro: string): void {
    this.navegar({ rubro, page: 1 });
  }

  protected cambiarEstado(estado: string): void {
    this.navegar({ estado, page: 1 });
  }

  /** El KPI es un atajo al filtro: volver a pulsarlo lo quita. */
  protected seleccionarEstado(estado: string): void {
    this.navegar({ estado: this.filtros().estado === estado ? '' : estado, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', tipoPersona: '', rubro: '', estado: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected pedirCambioEstado(cliente: Cliente): void {
    this.aviso.set(null);
    this.enCambioEstado.set(cliente);
  }

  protected cerrarCambioEstado(): void {
    if (!this.procesando()) {
      this.enCambioEstado.set(null);
    }
  }

  /**
   * Baja lógica y reactivación comparten diálogo porque son la misma decisión
   * vista desde los dos lados. La reactivación no tiene endpoint propio: es el
   * PUT con estado `A`, y el servicio lo encapsula.
   */
  protected async confirmarCambioEstado(): Promise<void> {
    const cliente = this.enCambioEstado();
    if (!cliente || this.procesando()) return;
    this.procesando.set(true);
    try {
      if (cliente.estado === 'I') {
        await this.clientes.reactivar(cliente.id, cliente);
        this.aviso.set(`${textoDe(cliente.nombre)} vuelve a estar activo.`);
      } else {
        await this.clientes.desactivar(cliente.id);
        this.aviso.set(`${textoDe(cliente.nombre)} quedó inactivo.`);
      }
      this.enCambioEstado.set(null);
      this.recargar();
    } catch (error) {
      this.aviso.set(
        error instanceof ApiError ? error.message : 'No se pudo cambiar el estado del cliente.',
      );
    } finally {
      this.procesando.set(false);
    }
  }

  protected descripcionCambioEstado(cliente: Cliente): string {
    const nombre = textoDe(cliente.nombre);
    return this.activo(cliente)
      ? `${nombre} dejará de aparecer como activo. Es una baja lógica: su historial comercial se conserva y puede reactivarse.`
      : `${nombre} volverá a estar disponible para nuevas oportunidades.`;
  }

  protected etiquetaTipo(codigo: string | undefined): string {
    return describir(TIPO_PERSONA, codigo) || SIN_DATO;
  }

  protected documento(cliente: Cliente): string {
    const tipo = describir(TIPO_DOCUMENTO, cliente.tipoDocumento);
    const numero = textoDe(cliente.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected activo(cliente: Cliente): boolean {
    return cliente.estado !== 'I';
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosClientesUrl,
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
      this.resumen.set(RESUMEN_VACIO);
      this.error.set(resultado.error);
    } else {
      this.paginaDatos.set(resultado.pagina);
      this.resumen.set(resultado.resumen);
    }
    this.cargando.set(false);
  }

  private navegar(cambios: Partial<FiltrosClientesUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        tipoPersona: siguiente.tipoPersona || null,
        rubro: siguiente.rubro || null,
        estado: siguiente.estado || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosClientesDesdeUrl(params: ParamMap): FiltrosClientesUrl {
  const texto = (params.get('texto') ?? '').trim();
  const tipoSolicitado = (params.get('tipoPersona') ?? '').trim().toUpperCase();
  const estadoSolicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const solicitada = Number(params.get('page') ?? '1');
  return {
    texto,
    // Un código inventado en la URL no se manda al backend: se ignora.
    tipoPersona: TIPOS_VALIDOS.has(tipoSolicitado) ? tipoSolicitado : '',
    rubro: (params.get('rubro') ?? '').trim(),
    estado: ESTADOS_VALIDOS.has(estadoSolicitado) ? estadoSolicitado : '',
    page: Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1,
  };
}

function mismosFiltros(a: FiltrosClientesUrl, b: FiltrosClientesUrl): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
