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
  firstValueFrom,
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
  FiltrosCaptacionesPendientes,
} from '../../core/api/captaciones.service';
import { describir, ESTADO_CAPTACION } from '../../core/api/codigos';
import { descargarCsv } from '../../core/csv';
import { descripcionCondicionComision } from '../../core/comision';
import { fechaCorta, numero, SIN_DATO, texto } from '../../core/formato';
import { AgenteOpcion, PersonalService } from '../../core/api/personal.service';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = RESULTADOS_POR_PAGINA;
const MAX_EXPORTACION = 100;

interface FiltrosRevisionUrl {
  texto: string;
  estado: string;
  idAgente: number | null;
  page: number;
}

interface MetricasRevision {
  total: number;
  pendientes: number;
  observadas: number;
}

interface CargaRevision {
  pagina: PageResponse<Captacion>;
  metricas: MetricasRevision;
}

type ResultadoCarga = CargaRevision | { error: string };

@Component({
  selector: 'app-bandeja-captaciones',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './bandeja-captaciones.html',
  styleUrl: './bandeja-captaciones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BandejaCaptaciones implements OnInit {
  private readonly api = inject(CaptacionesService);
  private readonly personal = inject(PersonalService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly exportando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly avisoExportacion = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Captacion>>(paginaVacia(POR_PAGINA));
  protected readonly metricas = signal<MetricasRevision>({ total: 0, pendientes: 0, observadas: 0 });
  protected readonly agentes = signal<AgenteOpcion[]>([]);
  protected readonly filtros = signal<FiltrosRevisionUrl>({
    texto: '', estado: '', idAgente: null, page: 1,
  });

  protected readonly porPagina = POR_PAGINA;
  protected readonly opcionesEstado: OpcionFiltro[] = [
    { valor: 'P', etiqueta: 'Pendiente de revisión' },
    { valor: 'O', etiqueta: 'Observada' },
  ];
  protected readonly opcionesAgente = computed<OpcionFiltro[]>(() =>
    this.agentes().map((agente) => ({ valor: String(agente.id), etiqueta: agente.nombre })),
  );
  protected readonly hayFiltros = computed(() => {
    const filtros = this.filtros();
    return !!filtros.texto || !!filtros.estado || !!filtros.idAgente;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna captación por revisar coincide con los filtros.'
      : 'No hay captaciones pendientes de revisión en tu alcance.',
  );

  ngOnInit(): void {
    this.cargarAgentes();
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosRevisionDesdeUrl),
      distinctUntilChanged(mismosFiltros),
    );
    combineLatest([filtrosUrl$, this.recargar$.pipe(startWith(undefined))])
      .pipe(
        map(([filtros]) => filtros),
        tap((filtros) => {
          this.filtros.set(filtros);
          this.cargando.set(true);
          this.error.set(null);
          this.avisoExportacion.set(null);
        }),
        switchMap((filtros) =>
          forkJoin({
            pagina: this.api.pendientes$(this.filtrosApi(filtros, filtros.estado, POR_PAGINA)),
        pendientes: this.api.pendientes$(this.filtrosApi({ ...filtros, page: 1 }, 'P', 1)),
        observadas: this.api.pendientes$(this.filtrosApi({ ...filtros, page: 1 }, 'O', 1)),
          }).pipe(
            map((respuesta) => ({
              pagina: respuesta.pagina,
              metricas: {
                total: respuesta.pendientes.totalRecords + respuesta.observadas.totalRecords,
                pendientes: respuesta.pendientes.totalRecords,
                observadas: respuesta.observadas.totalRecords,
              },
            })),
            switchMap((carga) => this.corregirPagina(filtros, carga)),
            catchError((error) =>
              of({ error: mensajeError(error, 'No se pudo cargar la bandeja de revisión.') }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
  }

  protected cambiarTexto(texto: string): void {
    const limpio = texto.trim();
    if (limpio !== this.filtros().texto) this.navegar({ texto: limpio, page: 1 });
  }

  protected cambiarEstado(estado: string): void {
    this.navegar({ estado, page: 1 });
  }

  protected seleccionarEstado(estado: string): void {
    this.navegar({ estado: this.filtros().estado === estado ? '' : estado, page: 1 });
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

  protected revisar(codigo: string | undefined): void {
    if (codigo) void this.router.navigate(['/captaciones', codigo, 'revisar']);
  }

  protected expediente(codigo: string | undefined): void {
    if (codigo) void this.router.navigate(['/captaciones', codigo]);
  }

  protected etiquetaEstado(estado: string | undefined): string {
    return describir(ESTADO_CAPTACION, estado) || SIN_DATO;
  }

  protected tonoEstado(estado: string | undefined): string {
    return estado === 'O' ? 'aviso' : '';
  }

  protected valor(valor: string | undefined): string {
    return texto(valor);
  }

  protected area(valor: number | undefined): string {
    return valor === undefined ? SIN_DATO : `${numero(valor)} m²`;
  }

  protected comision(captacion: Captacion): string {
    return descripcionCondicionComision(captacion);
  }

  protected fecha(valor: string | undefined): string {
    return fechaCorta(valor);
  }

  protected async exportar(): Promise<void> {
    if (this.exportando()) return;
    this.exportando.set(true);
    this.avisoExportacion.set(null);
    try {
      const filtros = this.filtros();
      const pagina = await firstValueFrom(
        this.api.pendientes$(this.filtrosApi({ ...filtros, page: 1 }, filtros.estado, MAX_EXPORTACION)),
      );
      if (pagina.items.length === 0) {
        this.avisoExportacion.set('No hay captaciones que exportar con los filtros actuales.');
        return;
      }
      const nombre = descargarCsv(
        'captaciones_por_revisar',
        ['Código', 'Local', 'Distrito', 'Área m²', 'Rubro', 'Propietario', 'Agente',
          'Fecha', 'Tipo de comisión', 'Base', 'Valor', 'Moneda', 'IGV', 'Estado'],
        pagina.items.map((c) => [
          c.codigoCaptacion, c.direccionLocal, c.distritoLocal, c.areaM2, c.rubro,
          c.propietarioNombre, c.agenteNombre, c.fechaCaptacion, c.tipoComision,
          c.baseCalculo, c.valorComision, c.monedaComision, c.tratamientoIgv,
          this.etiquetaEstado(c.estado),
        ]),
      );
      this.avisoExportacion.set(
        pagina.totalRecords > pagina.items.length
          ? `Se exportaron ${pagina.items.length} de ${pagina.totalRecords} captaciones en ${nombre}. Acota los filtros para exportar el resto.`
          : `Se exportaron ${pagina.items.length} captaciones en ${nombre}.`,
      );
    } catch (error) {
      this.avisoExportacion.set(mensajeError(error, 'No se pudo exportar la bandeja.'));
    } finally {
      this.exportando.set(false);
    }
  }

  private filtrosApi(
    filtros: FiltrosRevisionUrl,
    estado: string,
    tamano: number,
  ): FiltrosCaptacionesPendientes {
    return {
      pagina: filtros.page,
      tamano,
      estado: estado || undefined,
      idAgente: filtros.idAgente ?? undefined,
      q: filtros.texto || undefined,
    };
  }

  private cargarAgentes(): void {
    this.personal.agentes$()
      .pipe(catchError(() => of(paginaVacia<AgenteOpcion>(100))), takeUntilDestroyed(this.destroyRef))
      .subscribe((pagina) => this.agentes.set(pagina.items));
  }

  private corregirPagina(
    filtros: FiltrosRevisionUrl,
    carga: CargaRevision,
  ): Observable<CargaRevision> {
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
      this.metricas.set({ total: 0, pendientes: 0, observadas: 0 });
      this.error.set(resultado.error);
    } else {
      this.paginaDatos.set(resultado.pagina);
      this.metricas.set(resultado.metricas);
    }
    this.cargando.set(false);
  }

  private navegar(cambios: Partial<FiltrosRevisionUrl>, replaceUrl = false): void {
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

export function filtrosRevisionDesdeUrl(params: ParamMap): FiltrosRevisionUrl {
  const estadoSolicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const estado = ['P', 'O'].includes(estadoSolicitado) ? estadoSolicitado : '';
  const pagina = Number(params.get('page') ?? '1');
  return {
    texto: (params.get('texto') ?? '').trim(),
    estado,
    idAgente: idPositivo(params.get('idAgente')),
    page: Number.isSafeInteger(pagina) && pagina > 0 ? pagina : 1,
  };
}

function idPositivo(valor: string | null): number | null {
  const id = Number(valor);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function mismosFiltros(a: FiltrosRevisionUrl, b: FiltrosRevisionUrl): boolean {
  return a.texto === b.texto && a.estado === b.estado && a.idAgente === b.idAgente && a.page === b.page;
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
