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
import { describir, ESTADO_OPORTUNIDAD, opcionesDe } from '../../core/api/codigos';
import {
  Oportunidad,
  OportunidadesService,
  ResumenOportunidades,
} from '../../core/api/oportunidades.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = 10;
const ESTADOS_VALIDOS = new Set(['A', 'S', 'N', 'F', 'X']);

export interface FiltrosOportunidadesUrl {
  texto: string;
  estado: string;
  page: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Oportunidad>;
  resumen: ResumenOportunidades;
}

type ResultadoCarga = CargaCorrecta | { error: string };

const RESUMEN_VACIO: ResumenOportunidades = {
  total: 0,
  abiertas: 0,
  conSolicitud: 0,
  noContinuan: 0,
  exitosas: 0,
  noFavorables: 0,
};

/**
 * Bandeja de oportunidades comerciales: **el hub del proceso**. Una
 * oportunidad existe desde que un cliente se interesa por una propiedad
 * captada y sobrevive aunque nunca llegue a haber solicitud, que es lo que da
 * trazabilidad a los intentos que no cerraron.
 *
 * Dos cosas que hay que saber antes de tocarla:
 * - **El alcance del BROKER es por CAPTACIÓN, no por agente supervisado.** Ve
 *   las oportunidades de los locales que su equipo captó, aunque la
 *   oportunidad se haya reasignado a otro agente. Es distinto del alcance de
 *   interacciones a propósito, y no se unifica.
 * - **El filtro por etapa y los KPI son extensión aditiva del v2**
 *   (`estado` en `GET /oportunidades` y `GET /oportunidades/resumen`). El
 *   Blazor descargaba todas las oportunidades del alcance y agrupaba en
 *   memoria; con paginación real eso solo contaría la página visible.
 *
 * No hay botón de "cerrar exitosa" en ninguna parte y no es un olvido: ese
 * cierre lo produce la cascada del contrato (F4). El endpoint existe y responde
 * 400 siempre.
 */
@Component({
  selector: 'app-oportunidades',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './oportunidades.html',
  styleUrl: './oportunidades.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Oportunidades implements OnInit {
  private readonly api = inject(OportunidadesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Oportunidad>>(paginaVacia(POR_PAGINA));
  protected readonly resumen = signal<ResumenOportunidades>(RESUMEN_VACIO);
  protected readonly filtros = signal<FiltrosOportunidadesUrl>({
    texto: '',
    estado: '',
    page: 1,
  });

  protected readonly porPagina = POR_PAGINA;
  /** El alta y el cierre por no continuidad son del AGENTE (contrato F3 §4). */
  protected readonly puedeOperar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.estado;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna oportunidad coincide con los filtros.'
      : 'Todavía no hay oportunidades en tu alcance.',
  );
  protected readonly opcionesEstado: OpcionFiltro[] = opcionesDe(ESTADO_OPORTUNIDAD);

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosOportunidadesDesdeUrl),
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
              estado: filtros.estado || undefined,
              query: filtros.texto || undefined,
            }),
            // El resumen NO lleva estado: cuenta los cinco cubos.
            resumen: this.api.resumen$({ query: filtros.texto || undefined }),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la bandeja de oportunidades.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected nueva(): void {
    void this.router.navigate(['/oportunidades/nueva']);
  }

  protected verSeguimiento(id: number): void {
    void this.router.navigate(['/oportunidades', id]);
  }

  protected verCliente(idCliente: number | undefined): void {
    if (idCliente) void this.router.navigate(['/clientes', idCliente]);
  }

  protected verPropiedad(codigoCaptacion: string | undefined): void {
    if (codigoCaptacion) void this.router.navigate(['/captaciones', codigoCaptacion, 'ficha']);
  }

  protected cambiarTexto(texto: string): void {
    const normalizado = texto.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, page: 1 });
    }
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
    this.navegar({ texto: '', estado: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_OPORTUNIDAD, codigo) || SIN_DATO;
  }

  /**
   * Verde solo el cierre favorable; rojo lo que no continuó. `S` (solicitud
   * creada) es avance, no desenlace, así que comparte tono con `A`.
   */
  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'F') return 'bien';
    if (codigo === 'N' || codigo === 'X') return 'mal';
    return 'aviso';
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosOportunidadesUrl,
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

  private navegar(cambios: Partial<FiltrosOportunidadesUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        estado: siguiente.estado || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosOportunidadesDesdeUrl(params: ParamMap): FiltrosOportunidadesUrl {
  const estadoSolicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const solicitada = Number(params.get('page') ?? '1');
  return {
    texto: (params.get('texto') ?? '').trim(),
    // Un código inventado en la URL no se manda al backend: se ignora.
    estado: ESTADOS_VALIDOS.has(estadoSolicitado) ? estadoSolicitado : '',
    page: Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1,
  };
}

function mismosFiltros(a: FiltrosOportunidadesUrl, b: FiltrosOportunidadesUrl): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
