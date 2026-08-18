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
import { describir, ESTADO_SOLICITUD, opcionesDe } from '../../core/api/codigos';
import {
  ResumenSolicitudes,
  Solicitud,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, monto as montoDe, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = RESULTADOS_POR_PAGINA;
const ESTADOS_VALIDOS = new Set(['G', 'E', 'O', 'A', 'R', 'D', 'C']);

export interface FiltrosSolicitudesUrl {
  texto: string;
  estado: string;
  distrito: string;
  idAgente: string;
  page: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Solicitud>;
  resumen: ResumenSolicitudes;
}

type ResultadoCarga = CargaCorrecta | { error: string };

export const RESUMEN_SOLICITUDES_VACIO: ResumenSolicitudes = {
  total: 0,
  registradas: 0,
  enRevision: 0,
  observadas: 0,
  aprobadas: 0,
  rechazadas: 0,
  desistidas: 0,
  cerradas: 0,
  pendientes: 0,
  distritos: [],
  agentes: [],
};

/**
 * Bandeja de solicitudes de alquiler: la **oferta formal** del cliente sobre
 * una oportunidad, y lo que el broker evalúa antes del cierre.
 *
 * Tres cosas que hay que saber antes de tocarla:
 *
 * - **El alcance del BROKER es por AGENTE SUPERVISADO**, no por captación. Es
 *   distinto del de oportunidades y del de contratos —que sí alcanzan por
 *   captación— y no se unifica: son reglas del cable, y el backend las impone.
 * - **Los filtros y los KPI son extensión aditiva del v2** (`estado`, `texto`,
 *   `distrito`, `idAgente` en `GET /solicitudes` y `GET /solicitudes/resumen`).
 *   El Blazor descargaba todas las solicitudes del alcance y filtraba, contaba y
 *   derivaba las listas de distritos y agentes en memoria; con paginación real
 *   eso solo contaría la página visible.
 * - **La acción de la fila depende del estado y del rol**: una OBSERVADA lleva
 *   al agente a subsanar documentos, una APROBADA a cerrar el alquiler, y el
 *   resto solo se consulta.
 */
@Component({
  selector: 'app-solicitudes',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './solicitudes.html',
  styleUrl: './solicitudes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Solicitudes implements OnInit {
  private readonly api = inject(SolicitudesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Solicitud>>(paginaVacia(POR_PAGINA));
  protected readonly resumen = signal<ResumenSolicitudes>(RESUMEN_SOLICITUDES_VACIO);
  protected readonly filtros = signal<FiltrosSolicitudesUrl>({
    texto: '',
    estado: '',
    distrito: '',
    idAgente: '',
    page: 1,
  });

  protected readonly porPagina = POR_PAGINA;
  /** El alta y el cierre son del AGENTE (contrato F4 §2 y §5). */
  protected readonly esAgente = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  /** El filtro por agente solo tiene sentido para quien ve a varios. */
  protected readonly supervisa = computed(() => this.auth.sesion()?.rol !== 'AGENTE');
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.estado || !!f.distrito || !!f.idAgente;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna solicitud coincide con los filtros.'
      : 'Todavía no hay solicitudes en tu alcance.',
  );
  protected readonly opcionesEstado: OpcionFiltro[] = opcionesDe(ESTADO_SOLICITUD);
  protected readonly opcionesDistrito = computed<OpcionFiltro[]>(() =>
    this.resumen().distritos.map((d) => ({ valor: d, etiqueta: d })),
  );
  protected readonly opcionesAgente = computed<OpcionFiltro[]>(() =>
    this.resumen().agentes.map((a) => ({ valor: String(a.id), etiqueta: a.nombre })),
  );

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosSolicitudesDesdeUrl),
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
              distrito: filtros.distrito || undefined,
              idAgente: Number(filtros.idAgente) || undefined,
              texto: filtros.texto || undefined,
            }),
            // El resumen NO lleva estado, distrito ni agente: son los cubos y
            // las listas que devuelve. Solo comparte el texto con la tabla.
            resumen: this.api.resumen$({ texto: filtros.texto || undefined }),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la bandeja de solicitudes.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected nueva(): void {
    void this.router.navigate(['/solicitudes/nueva']);
  }

  protected verExpediente(codigo: string | undefined): void {
    if (codigo) void this.router.navigate(['/solicitudes', codigo]);
  }

  protected gestionarDocumentos(codigo: string | undefined): void {
    if (codigo) void this.router.navigate(['/solicitudes', codigo, 'documentos']);
  }

  protected verCliente(idCliente: number | undefined): void {
    if (idCliente) void this.router.navigate(['/clientes', idCliente]);
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

  protected cambiarDistrito(distrito: string): void {
    this.navegar({ distrito, page: 1 });
  }

  protected cambiarAgente(idAgente: string): void {
    this.navegar({ idAgente, page: 1 });
  }

  /** El KPI es un atajo al filtro: volver a pulsarlo lo quita. */
  protected seleccionarEstado(estado: string): void {
    this.navegar({ estado: this.filtros().estado === estado ? '' : estado, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', estado: '', distrito: '', idAgente: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_SOLICITUD, codigo) || SIN_DATO;
  }

  /**
   * Verde lo resuelto a favor, rojo lo que se cayó, ámbar lo que espera acción
   * de alguien. `C` (cerrada) es el desenlace bueno: su alquiler se firmó.
   */
  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'A' || codigo === 'C') return 'bien';
    if (codigo === 'R' || codigo === 'D') return 'mal';
    return 'aviso';
  }

  /**
   * Avance del checklist en tanto por ciento. El backend ya manda el contador,
   * así que aquí no se cuentan documentos: solo se dibuja.
   */
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
    filtros: FiltrosSolicitudesUrl,
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

  private navegar(cambios: Partial<FiltrosSolicitudesUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        estado: siguiente.estado || null,
        distrito: siguiente.distrito || null,
        idAgente: siguiente.idAgente || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosSolicitudesDesdeUrl(params: ParamMap): FiltrosSolicitudesUrl {
  const estadoSolicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const agenteSolicitado = Number(params.get('idAgente') ?? '');
  const solicitada = Number(params.get('page') ?? '1');
  return {
    texto: (params.get('texto') ?? '').trim(),
    // Un código inventado en la URL no se manda al backend: se ignora.
    estado: ESTADOS_VALIDOS.has(estadoSolicitado) ? estadoSolicitado : '',
    distrito: (params.get('distrito') ?? '').trim(),
    idAgente:
      Number.isSafeInteger(agenteSolicitado) && agenteSolicitado > 0
        ? String(agenteSolicitado)
        : '',
    page: Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1,
  };
}

function mismosFiltros(a: FiltrosSolicitudesUrl, b: FiltrosSolicitudesUrl): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
