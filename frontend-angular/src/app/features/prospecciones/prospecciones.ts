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
import { describir, ESTADO_PROSPECCION } from '../../core/api/codigos';
import {
  AgenteOpcion,
  BrokerOpcion,
  PersonalService,
} from '../../core/api/personal.service';
import {
  Prospeccion,
  ProspeccionesService,
} from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = 10;
const DIAS_RECONTACTO = 7;
const ESTADOS_VALIDOS = new Set(['GESTION', 'P', 'C', 'R', 'S', 'T', 'D']);

export interface FiltrosProspeccionesUrl {
  texto: string;
  estado: string;
  recontactar: boolean;
  idAgente: number | null;
  idBroker: number | null;
  orden: string;
  page: number;
}

interface MetricasProspecciones {
  activas: number;
  recontactar: number;
  captadas: number;
  descartadas: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Prospeccion>;
  metricas: MetricasProspecciones;
}

type ResultadoCarga = CargaCorrecta | { error: string };

const METRICAS_VACIAS: MetricasProspecciones = {
  activas: 0,
  recontactar: 0,
  captadas: 0,
  descartadas: 0,
};

/**
 * Bandeja de entrada de prospecciones.
 *
 * `GESTION` se envía al backend como el cubo de activas —P/C/R/E/S— y nunca
 * se interpreta como una columna. La URL conserva filtros y `switchMap`
 * cancela la lectura anterior. La bandeja especial de recontacto usa su ruta
 * congelada; no se descarga la lista para calcular vencimientos en Angular.
 */
@Component({
  selector: 'app-prospecciones',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './prospecciones.html',
  styleUrl: './prospecciones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Prospecciones implements OnInit {
  private readonly prospecciones = inject(ProspeccionesService);
  private readonly personal = inject(PersonalService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Prospeccion>>(paginaVacia(POR_PAGINA));
  protected readonly metricas = signal<MetricasProspecciones>(METRICAS_VACIAS);
  protected readonly filtros = signal<FiltrosProspeccionesUrl>({
    texto: '',
    estado: '',
    recontactar: false,
    idAgente: null,
    idBroker: null,
    orden: '',
    page: 1,
  });
  protected readonly agentes = signal<AgenteOpcion[]>([]);
  protected readonly brokers = signal<BrokerOpcion[]>([]);

  protected readonly porPagina = POR_PAGINA;
  protected readonly puedeRegistrar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly supervisa = computed(() => this.auth.sesion()?.rol !== 'AGENTE');
  protected readonly administra = computed(() => this.auth.sesion()?.rol === 'TENANT_ADMIN');
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.estado || f.recontactar || !!f.idAgente || !!f.idBroker || !!f.orden;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna prospección coincide con los filtros.'
      : 'Todavía no hay prospecciones en tu alcance.',
  );
  protected readonly opcionesEstado: OpcionFiltro[] = [
    { valor: 'GESTION', etiqueta: 'Activas' },
    { valor: 'P', etiqueta: 'Prospecto' },
    { valor: 'C', etiqueta: 'Contactado' },
    { valor: 'R', etiqueta: 'Reunión' },
    { valor: 'S', etiqueta: 'En seguimiento' },
    { valor: 'T', etiqueta: 'Captadas' },
    { valor: 'D', etiqueta: 'Descartadas' },
  ];
  protected readonly opcionesOrden: OpcionFiltro[] = [
    { valor: 'ultimo_contacto', etiqueta: 'Último contacto primero' },
  ];
  protected readonly opcionesAgente = computed<OpcionFiltro[]>(() =>
    this.agentes().map((a) => ({ valor: String(a.id), etiqueta: a.nombre })),
  );
  protected readonly opcionesBroker = computed<OpcionFiltro[]>(() =>
    this.brokers().map((b) => ({ valor: String(b.id), etiqueta: b.nombre })),
  );

  ngOnInit(): void {
    this.cargarCatalogos();
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosProspeccionesDesdeUrl),
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
            pagina: this.listar(filtros),
            metricas: this.cargarMetricas(),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudo cargar la bandeja de prospecciones.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected nueva(): void {
    // El alta del local crea su prospección inicial en el backend.
    void this.router.navigate(['/locales/nuevo']);
  }

  protected verLocal(idLocal: number | undefined): void {
    if (idLocal) {
      void this.router.navigate(['/locales', idLocal]);
    }
  }

  protected verSeguimiento(id: number): void {
    void this.router.navigate(['/prospecciones', id]);
  }

  protected verResumen(codigoCaptacion: string | undefined): void {
    if (codigoCaptacion) {
      void this.router.navigate(['/captaciones', codigoCaptacion, 'ficha']);
    }
  }

  protected seleccionarEstado(estado: string): void {
    const siguiente = this.filtros().estado === estado ? '' : estado;
    this.navegar({ estado: siguiente, recontactar: false, page: 1 });
  }

  protected seleccionarRecontacto(): void {
    const activo = !this.filtros().recontactar;
    this.navegar({
      recontactar: activo,
      estado: '',
      texto: activo ? '' : this.filtros().texto,
      idAgente: activo ? null : this.filtros().idAgente,
      idBroker: activo ? null : this.filtros().idBroker,
      orden: activo ? '' : this.filtros().orden,
      page: 1,
    });
  }

  protected cambiarTexto(texto: string): void {
    const normalizado = texto.trim();
    if (normalizado !== this.filtros().texto) {
      this.navegar({ texto: normalizado, recontactar: false, page: 1 });
    }
  }

  protected cambiarEstado(estado: string): void {
    this.navegar({ estado, recontactar: false, page: 1 });
  }

  protected cambiarAgente(valor: string): void {
    this.navegar({ idAgente: idPositivo(valor), recontactar: false, page: 1 });
  }

  protected cambiarBroker(valor: string): void {
    this.navegar({ idBroker: idPositivo(valor), idAgente: null, recontactar: false, page: 1 });
  }

  protected cambiarOrden(orden: string): void {
    this.navegar({ orden, recontactar: false, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({
      texto: '',
      estado: '',
      recontactar: false,
      idAgente: null,
      idBroker: null,
      orden: '',
      page: 1,
    });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_PROSPECCION, codigo) || SIN_DATO;
  }

  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'T') return 'bien';
    if (codigo === 'D') return 'mal';
    return codigo === 'S' || codigo === 'R' ? 'aviso' : '';
  }

  protected ultimoMovimiento(p: Prospeccion): string {
    if (p.estado === 'T') return 'En captación';
    if (p.estado === 'D') return 'Descartada';
    if (p.fechaPropuesta) return `Propuesta · ${fechaCorta(p.fechaPropuesta)}`;
    if (p.fechaReunion) return `Reunión · ${fechaCorta(p.fechaReunion)}`;
    if (p.fechaContacto) return `Contacto · ${fechaCorta(p.fechaContacto)}`;
    return 'Sin contacto';
  }

  protected vencida(p: Prospeccion): boolean {
    if (!p.fechaRecontacto || p.estado === 'T' || p.estado === 'D') return false;
    const limite = new Date();
    limite.setHours(0, 0, 0, 0);
    limite.setDate(limite.getDate() - DIAS_RECONTACTO);
    const valor = new Date(`${p.fechaRecontacto}T00:00:00`);
    return !Number.isNaN(valor.getTime()) && valor <= limite;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private listar(f: FiltrosProspeccionesUrl): Observable<PageResponse<Prospeccion>> {
    if (f.recontactar) {
      return this.prospecciones.recontactar$(DIAS_RECONTACTO, f.page, POR_PAGINA);
    }
    return this.prospecciones.pagina$({
      pagina: f.page,
      tamano: POR_PAGINA,
      estado: f.estado,
      idAgente: f.idAgente ?? undefined,
      idBrokerSupervisor: f.idBroker ?? undefined,
      q: f.texto,
      orden: f.orden,
    });
  }

  private cargarMetricas(): Observable<MetricasProspecciones> {
    return forkJoin({
      activas: this.prospecciones.pagina$({ estado: 'GESTION', pagina: 1, tamano: 1 }),
      recontactar: this.prospecciones.recontactar$(DIAS_RECONTACTO, 1, 1),
      captadas: this.prospecciones.pagina$({ estado: 'T', pagina: 1, tamano: 1 }),
      descartadas: this.prospecciones.pagina$({ estado: 'D', pagina: 1, tamano: 1 }),
    }).pipe(
      map((r) => ({
        activas: r.activas.totalRecords,
        recontactar: r.recontactar.totalRecords,
        captadas: r.captadas.totalRecords,
        descartadas: r.descartadas.totalRecords,
      })),
    );
  }

  private cargarCatalogos(): void {
    const rol = this.auth.sesion()?.rol;
    if (rol === 'AGENTE') return;
    this.personal
      .agentes$()
      .pipe(catchError(() => of(paginaVacia<AgenteOpcion>(100))), takeUntilDestroyed(this.destroyRef))
      .subscribe((pagina) => this.agentes.set(pagina.items));
    if (rol === 'TENANT_ADMIN') {
      this.personal
        .brokers$()
        .pipe(catchError(() => of(paginaVacia<BrokerOpcion>(100))), takeUntilDestroyed(this.destroyRef))
        .subscribe((pagina) => this.brokers.set(pagina.items));
    }
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosProspeccionesUrl,
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

  private navegar(cambios: Partial<FiltrosProspeccionesUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        estado: siguiente.estado || null,
        recontactar: siguiente.recontactar ? '1' : null,
        idAgente: siguiente.idAgente,
        idBroker: siguiente.idBroker,
        orden: siguiente.orden || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosProspeccionesDesdeUrl(params: ParamMap): FiltrosProspeccionesUrl {
  const texto = (params.get('texto') ?? '').trim();
  const solicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const estado = ESTADOS_VALIDOS.has(solicitado) ? solicitado : '';
  const recontactar = params.get('recontactar') === '1';
  const idAgente = idPositivo(params.get('idAgente'));
  const idBroker = idPositivo(params.get('idBroker'));
  const orden = params.get('orden') === 'ultimo_contacto' ? 'ultimo_contacto' : '';
  const solicitada = Number(params.get('page') ?? '1');
  const page = Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1;
  return recontactar
    ? { texto: '', estado: '', recontactar, idAgente: null, idBroker: null, orden: '', page }
    : { texto, estado, recontactar, idAgente, idBroker, orden, page };
}

function idPositivo(valor: string | null): number | null {
  const numero = Number(valor);
  return Number.isSafeInteger(numero) && numero > 0 ? numero : null;
}

function mismosFiltros(a: FiltrosProspeccionesUrl, b: FiltrosProspeccionesUrl): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
