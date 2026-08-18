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
  describir,
  ESTADO_COMISION,
  ESTADO_CONTRATO,
  ESTADO_LOCAL,
} from '../../core/api/codigos';
import {
  Contrato,
  ContratosService,
  ResumenCierres,
} from '../../core/api/contratos.service';
import { AuthService } from '../../core/auth/auth.service';
import { descargarCsv } from '../../core/csv';
import { fechaCorta, monto, numero, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = RESULTADOS_POR_PAGINA;

/** Tope del cable por página; el recurso no admite más de 100. */
const TAMANO_MAXIMO = 100;

/**
 * Techo de la exportación. Existe para que un CSV no se convierta en una
 * descarga sin fin, y **si se alcanza se dice** — un export truncado en
 * silencio es peor que uno que no se hace.
 */
export const MAXIMO_EXPORTACION = 1000;

const RESUMEN_VACIO: ResumenCierres = {
  cierres: 0,
  comisionesGeneradas: [],
  montosCobrados: [],
  saldosPendientes: [],
  montosPagadosAgente: [],
  saldosPendientesAgente: [],
  porLiquidar: 0,
  sinLiquidacion: 0,
  distritosDisponibles: [],
  agentesDisponibles: [],
};

export interface FiltrosCierresUrl {
  texto: string;
  distrito: string;
  idAgente: number | null;
  page: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Contrato>;
  resumen: ResumenCierres;
}

type ResultadoCarga = CargaCorrecta | { error: string };

/**
 * Cierres exitosos: los locales alquilados con contrato registrado.
 *
 * Porta `PropiedadesAlquiladas.razor` siguiendo la convención de listados
 * paginados. Lo que cambia respecto del Blazor es dónde se hace el trabajo:
 * allí se descargaban **todos** los contratos del alcance para ordenar,
 * filtrar, contar y sumar la comisión en el navegador. Aquí filtro, orden,
 * paginación, conteo y agregados bajan a SQL.
 *
 * Ese cambio no es cosmético: el tope de página del recurso es 100, así que
 * una comisión total sumada en el cliente **sería falsa** en cuanto la
 * corredora pase de 100 cierres, y no lo avisaría.
 *
 * Dos divergencias deliberadas:
 * - **El estado que se muestra es el real del contrato**, no un "Alquilado"
 *   fijo. El Blazor rotulaba todas las filas igual; un contrato rescindido o
 *   anulado aparecía como alquilado.
 * - **No hay botón "Ver detalle"**: llevaba a `SolicitudDetail`, que todavía
 *   no está migrada. Un botón que no lleva a ninguna parte es peor que su
 *   ausencia.
 */
@Component({
  selector: 'app-propiedades-alquiladas',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './propiedades-alquiladas.html',
  styleUrl: './propiedades-alquiladas.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropiedadesAlquiladas implements OnInit {
  private readonly contratos = inject(ContratosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Contrato>>(paginaVacia(POR_PAGINA));
  protected readonly resumen = signal<ResumenCierres>(RESUMEN_VACIO);
  protected readonly filtros = signal<FiltrosCierresUrl>({
    texto: '',
    distrito: '',
    idAgente: null,
    page: 1,
  });

  protected readonly exportando = signal(false);
  protected readonly avisoExportacion = signal<string | null>(null);
  protected readonly errorExportacion = signal<string | null>(null);

  protected readonly porPagina = POR_PAGINA;
  protected readonly SIN_DATO = SIN_DATO;

  /**
   * El filtro por agente solo se ofrece a quien supervisa a varios. Para el
   * AGENTE sobra: su alcance ya es él mismo, y el backend le devolvería
   * exactamente lo mismo con y sin filtro.
   */
  protected readonly puedeFiltrarPorAgente = computed(() => {
    const rol = this.auth.sesion()?.rol;
    return rol === 'BROKER' || rol === 'TENANT_ADMIN';
  });

  protected readonly distritos = computed<OpcionFiltro[]>(() =>
    this.resumen().distritosDisponibles.map((valor) => ({ valor, etiqueta: valor })),
  );

  protected readonly agentes = computed<OpcionFiltro[]>(() =>
    this.resumen().agentesDisponibles.map((a) => ({
      valor: String(a.id),
      etiqueta: a.nombre,
    })),
  );

  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.distrito || f.idAgente !== null;
  });

  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ningún cierre coincide con los filtros.'
      : 'Todavía no hay cierres exitosos. Aparecerán al registrar el contrato de una solicitud aprobada.',
  );

  /** Suma separada por moneda: PEN y USD nunca se mezclan en un total falso. */
  protected readonly comisionTotal = computed(() => {
    const importes = this.resumen().comisionesGeneradas ?? [];
    return importes.length === 0
      ? 'Sin comisiones vigentes'
      : importes.map((importe) => monto(importe.monto, importe.moneda)).join(' · ');
  });

  protected readonly cobradoTotal = computed(() => importesTexto(this.resumen().montosCobrados));
  protected readonly pendienteTotal = computed(() => importesTexto(this.resumen().saldosPendientes));
  protected readonly pagadoAgenteTotal = computed(() =>
    importesTexto(this.resumen().montosPagadosAgente),
  );
  protected readonly pendienteAgenteTotal = computed(() =>
    importesTexto(this.resumen().saldosPendientesAgente),
  );

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosCierresDesdeUrl),
      distinctUntilChanged(mismosFiltros),
    );

    combineLatest([filtrosUrl$, this.recargar$.pipe(startWith(undefined))])
      .pipe(
        map(([filtros]) => filtros),
        tap((filtros) => {
          this.filtros.set(filtros);
          this.cargando.set(true);
          this.error.set(null);
          this.limpiarAvisosExportacion();
        }),
        switchMap((filtros) =>
          forkJoin({
            pagina: this.contratos.pagina$(this.consulta(filtros, filtros.page, POR_PAGINA)),
            resumen: this.contratos.resumen$(this.criterios(filtros)),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'No se pudieron cargar los cierres exitosos.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));
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

  protected cambiarAgente(idAgente: string): void {
    const id = Number(idAgente);
    this.navegar({ idAgente: Number.isSafeInteger(id) && id > 0 ? id : null, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', distrito: '', idAgente: null, page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected agenteSeleccionado(): string {
    const id = this.filtros().idAgente;
    return id === null ? '' : String(id);
  }

  // Presentación: todo delega en `core/formato` y `core/api/codigos`.

  protected etiquetaContrato(codigo: string | undefined): string {
    return describir(ESTADO_CONTRATO, codigo) || SIN_DATO;
  }

  protected tonoContrato(codigo: string | undefined): string {
    if (codigo === 'V' || codigo === 'D' || codigo === 'R') {
      return 'bien';
    }
    return codigo === 'S' || codigo === 'A' ? 'mal' : '';
  }

  protected etiquetaComision(codigo: string | undefined): string {
    return codigo ? describir(ESTADO_COMISION, codigo) : 'Sin liquidación';
  }

  protected tonoComision(codigo: string | undefined): string {
    if (codigo === 'C') return 'bien';
    if (codigo === 'A') return 'mal';
    return codigo ? 'aviso' : 'mal';
  }

  protected etiquetaDisponibilidad(codigo: string | undefined): string {
    return describir(ESTADO_LOCAL, codigo) || SIN_DATO;
  }

  protected tonoDisponibilidad(codigo: string | undefined): string {
    if (codigo === 'D') return 'bien';
    return codigo === 'N' ? 'aviso' : '';
  }

  protected importe(valor: number | undefined, moneda: string | undefined): string {
    return monto(valor, moneda);
  }

  protected plazo(meses: number | undefined): string {
    return meses === undefined || meses === null ? SIN_DATO : `${numero(meses, 0)} meses`;
  }

  protected fecha(valor: string | undefined): string {
    return fechaCorta(valor);
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  // =====================================================================
  // Exportación
  // =====================================================================

  /**
   * Exporta **el conjunto filtrado**, no la página visible: es lo que la
   * gente espera de un botón "Exportar" y lo que hacía el Blazor.
   *
   * Como el recurso pagina de 100 en 100, aquí sí se recorren páginas — es
   * una acción puntual del usuario, no una carga de pantalla, que es la
   * distinción que importa para RC-003. Y si el conjunto supera
   * {@link MAXIMO_EXPORTACION}, **se avisa** en vez de recortar en silencio.
   */
  protected async exportar(): Promise<void> {
    if (this.exportando()) {
      return;
    }
    this.exportando.set(true);
    this.limpiarAvisosExportacion();
    try {
      const filtros = this.filtros();
      const filas: Contrato[] = [];
      let total = 0;
      for (let pagina = 1; filas.length < MAXIMO_EXPORTACION; pagina++) {
        const respuesta = await this.contratos.pagina(
          this.consulta(filtros, pagina, TAMANO_MAXIMO),
        );
        total = respuesta.totalRecords;
        filas.push(...respuesta.items);
        if (respuesta.items.length === 0 || filas.length >= total) {
          break;
        }
      }

      if (filas.length === 0) {
        this.avisoExportacion.set('No hay cierres que exportar con los filtros actuales.');
        return;
      }

      const nombre = descargarCsv('cierres_exitosos', CABECERAS_CSV, filas.map(filaCsv));
      this.avisoExportacion.set(
        filas.length < total
          ? `Se exportaron ${filas.length} de ${total} cierres en ${nombre}: es el máximo por archivo. Acota los filtros para llevarte el resto.`
          : `Se exportaron ${filas.length} cierres en ${nombre}.`,
      );
    } catch (error) {
      this.errorExportacion.set(
        error instanceof ApiError ? error.message : 'No se pudo exportar el listado.',
      );
    } finally {
      this.exportando.set(false);
    }
  }

  private limpiarAvisosExportacion(): void {
    this.avisoExportacion.set(null);
    this.errorExportacion.set(null);
  }

  // =====================================================================
  // Carga
  // =====================================================================

  /** Un solo sitio arma los criterios que comparten tabla, resumen y exportación. */
  private criterios(filtros: FiltrosCierresUrl) {
    return {
      texto: filtros.texto,
      distrito: filtros.distrito,
      idAgente: filtros.idAgente ?? undefined,
    };
  }

  private consulta(filtros: FiltrosCierresUrl, pagina: number, tamano: number) {
    return {
      ...this.criterios(filtros),
      pagina,
      tamano,
      // El último cierre primero: el orden congelado del recurso es por id.
      orden: 'cierre',
    };
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosCierresUrl,
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

  private navegar(cambios: Partial<FiltrosCierresUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        distrito: siguiente.distrito || null,
        idAgente: siguiente.idAgente,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export const CABECERAS_CSV: readonly string[] = [
  'Local',
  'Distrito',
  'Inquilino',
  'Operación',
  'Captación',
  'Agente',
  'Renta mensual',
  'Moneda',
  'Plazo (meses)',
  'Comisión',
  'Moneda de comisión',
  'Estado de cobro de comisión',
  'Cierre',
  'Disponibilidad del local',
  'Estado jurídico del contrato',
];

/** Una fila del CSV. Va aparte para poder fijarla por test. */
export function filaCsv(c: Contrato): readonly (string | number | null)[] {
  return [
    c.direccionLocal ?? '',
    c.distritoLocal ?? '',
    c.clienteNombre ?? '',
    c.codigoOportunidad ?? '',
    c.codigoCaptacion ?? '',
    c.agenteNombre ?? '',
    // Los importes van CRUDOS: una hoja de cálculo necesita números, no
    // "USD 1,200" formateado. La moneda viaja en su propia columna.
    c.rentaMensual ?? '',
    c.moneda ?? '',
    c.plazoContratoMeses ?? '',
    c.comisionGenerada ?? '',
    c.monedaComision ?? '',
    describir(ESTADO_COMISION, c.comisionEstado),
    c.fechaCierre ?? '',
    describir(ESTADO_LOCAL, c.estadoDisponibilidadLocal),
    describir(ESTADO_CONTRATO, c.estadoContrato),
  ];
}

export function filtrosCierresDesdeUrl(params: ParamMap): FiltrosCierresUrl {
  const texto = (params.get('texto') ?? '').trim();
  const distrito = (params.get('distrito') ?? '').trim();
  const agenteSolicitado = Number(params.get('idAgente') ?? '');
  const idAgente =
    Number.isSafeInteger(agenteSolicitado) && agenteSolicitado > 0 ? agenteSolicitado : null;
  const pageSolicitada = Number(params.get('page') ?? '1');
  const page =
    Number.isSafeInteger(pageSolicitada) && pageSolicitada > 0 ? pageSolicitada : 1;
  return { texto, distrito, idAgente, page };
}

function mismosFiltros(a: FiltrosCierresUrl, b: FiltrosCierresUrl): boolean {
  return (
    a.texto === b.texto &&
    a.distrito === b.distrito &&
    a.idAgente === b.idAgente &&
    a.page === b.page
  );
}

function importesTexto(importes: readonly { moneda: string; monto: number }[]): string {
  return importes.length === 0
    ? SIN_DATO
    : importes.map((importe) => monto(importe.monto, importe.moneda)).join(' · ');
}
