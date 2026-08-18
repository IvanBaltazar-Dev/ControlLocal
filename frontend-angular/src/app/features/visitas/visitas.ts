import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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
  ESTADO_VISITA,
  implicaNoContinuidad,
  MOTIVO_NO_CONTINUIDAD,
  OBJECION_VISITA,
  opcionesDe,
  OPINION_PRECIO,
  PROXIMA_ACCION_VISITA,
  RESULTADO_VISITA,
} from '../../core/api/codigos';
import { ResumenVisitas, Visita, VisitasService, VISITA_PENDIENTE } from '../../core/api/visitas.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, hora as horaDe, SIN_DATO, texto as textoDe } from '../../core/formato';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect, OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

const POR_PAGINA = RESULTADOS_POR_PAGINA;
const ESTADOS_VALIDOS = new Set(['P', 'G', 'R', 'N', 'C']);

/** Las cinco operaciones de agenda del contrato, cada una con su diálogo. */
type Accion = 'realizar' | 'noRealizada' | 'reprogramar' | 'cancelar' | 'resultado';

/** Lo que se escribe en el formulario de desenlace. */
interface ValoresDesenlace {
  resultado: string;
  observaciones: string;
  razonNoContinuidad: string;
  nivelInteres: number;
  objecionPrincipal: string;
  opinionPrecio: string;
  proximaAccion: string;
}

export interface FiltrosVisitasUrl {
  texto: string;
  estado: string;
  distrito: string;
  page: number;
}

interface CargaCorrecta {
  pagina: PageResponse<Visita>;
  resumen: ResumenVisitas;
}

type ResultadoCarga = CargaCorrecta | { error: string };

const RESUMEN_VACIO: ResumenVisitas = {
  total: 0,
  programadas: 0,
  reprogramadas: 0,
  realizadas: 0,
  noRealizadas: 0,
  canceladas: 0,
  distritos: [],
};

/**
 * Agenda de visitas: bandeja + las cinco operaciones de la máquina de estados.
 *
 * Lo que hay que respetar del contrato al tocarla:
 * - **`realizar` y el desenlace son dos pasos, no uno.** Marcar la visita como
 *   realizada la lleva a `R`; solo entonces admite `PATCH …/resultado`, y ese
 *   es **irrepetible** (el segundo intento responde *"La visita ya tiene un
 *   resultado registrado."*). Por eso el botón "Registrar resultado" solo
 *   aparece sobre una visita `R` **sin** resultado.
 * - **Cancelar y no-realizada escriben el motivo en `observaciones` y limpian
 *   el desenlace.** No son variantes de lo mismo: no-realizada sale de una
 *   visita que iba a ocurrir y no ocurrió; cancelar la retira de la agenda.
 * - **Un desenlace que implica no continuidad cierra la oportunidad.** Cuando
 *   el resultado es "no interesado" o "descartado", el backend pide la razón
 *   tipificada y cierra la oportunidad con ella: el formulario la exige en ese
 *   caso, en vez de dejar que el 400 lo explique después.
 * - El alta (`POST /visitas`) exige que la oportunidad sea **del propio
 *   agente**, sin alcance de broker. Por eso programar se hace desde la
 *   oportunidad, no desde aquí.
 */
@Component({
  selector: 'app-visitas',
  imports: [
    BarraFiltros,
    DialogoConfirmacion,
    EstadoListado,
    FiltroSelect,
    Paginacion,
    ReactiveFormsModule,
    TarjetaKpi,
  ],
  templateUrl: './visitas.html',
  styleUrl: './visitas.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Visitas implements OnInit {
  private readonly api = inject(VisitasService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly recargar$ = new Subject<void>();

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly paginaDatos = signal<PageResponse<Visita>>(paginaVacia(POR_PAGINA));
  protected readonly resumen = signal<ResumenVisitas>(RESUMEN_VACIO);
  protected readonly proximas = signal<Visita[]>([]);
  protected readonly filtros = signal<FiltrosVisitasUrl>({
    texto: '',
    estado: '',
    distrito: '',
    page: 1,
  });

  protected readonly accion = signal<Accion | null>(null);
  protected readonly objetivo = signal<Visita | null>(null);
  protected readonly procesando = signal(false);
  protected readonly errorAccion = signal<string | null>(null);

  protected readonly motivo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(1000)],
  });
  protected readonly nuevaFecha = new FormControl('', { nonNullable: true });
  protected readonly nuevaHora = new FormControl('', { nonNullable: true });
  protected readonly desenlace = this.fb.group({
    resultado: this.fb.control('', [Validators.required]),
    observaciones: this.fb.control(''),
    razonNoContinuidad: this.fb.control(''),
    nivelInteres: this.fb.control(0),
    objecionPrincipal: this.fb.control(''),
    opinionPrecio: this.fb.control(''),
    proximaAccion: this.fb.control(''),
  });

  protected readonly porPagina = POR_PAGINA;
  protected readonly puedeOperar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return !!f.texto || !!f.estado || !!f.distrito;
  });
  protected readonly mensajeVacio = computed(() =>
    this.hayFiltros()
      ? 'Ninguna visita coincide con los filtros.'
      : 'Todavía no hay visitas en tu alcance.',
  );
  protected readonly opcionesEstado: OpcionFiltro[] = opcionesDe(ESTADO_VISITA);
  protected readonly opcionesResultado: OpcionFiltro[] = opcionesDe(RESULTADO_VISITA);
  protected readonly opcionesRazon: OpcionFiltro[] = opcionesDe(MOTIVO_NO_CONTINUIDAD);
  protected readonly opcionesObjecion: OpcionFiltro[] = opcionesDe(OBJECION_VISITA);
  protected readonly opcionesOpinion: OpcionFiltro[] = opcionesDe(OPINION_PRECIO);
  protected readonly opcionesProxima: OpcionFiltro[] = opcionesDe(PROXIMA_ACCION_VISITA);
  /** Data-driven: los distritos vienen del resumen, del alcance completo. */
  protected readonly opcionesDistrito = computed<OpcionFiltro[]>(() =>
    this.resumen().distritos.map((d) => ({ valor: d, etiqueta: d })),
  );

  /**
   * Espejo en señales de lo que hay escrito en el diálogo.
   *
   * Hace falta porque `FormControl.value` **no es reactivo**: un `computed()`
   * que lo lea directamente se calcula una vez y no vuelve a hacerlo, así que
   * el botón "Confirmar" se quedaba bloqueado aunque el motivo ya estuviera
   * escrito. `reset()` también emite, de modo que abrir el diálogo los deja
   * sincronizados.
   */
  private readonly motivoEscrito = toSignal(this.motivo.valueChanges, { initialValue: '' });
  private readonly fechaEscrita = toSignal(this.nuevaFecha.valueChanges, { initialValue: '' });
  private readonly horaEscrita = toSignal(this.nuevaHora.valueChanges, { initialValue: '' });
  private readonly desenlaceEscrito = toSignal(
    this.desenlace.valueChanges as Observable<Partial<ValoresDesenlace>>,
    { initialValue: this.desenlace.getRawValue() as Partial<ValoresDesenlace> },
  );

  /** El desenlace pide razón solo cuando el resultado implica no continuidad. */
  protected readonly exigeRazon = computed(() =>
    implicaNoContinuidad(this.desenlaceEscrito()?.resultado),
  );

  protected readonly tituloDialogo = computed(() => {
    switch (this.accion()) {
      case 'realizar':
        return 'Marcar la visita como realizada';
      case 'noRealizada':
        return 'Marcar la visita como no realizada';
      case 'reprogramar':
        return 'Reprogramar la visita';
      case 'cancelar':
        return 'Cancelar la visita';
      case 'resultado':
        return 'Registrar el desenlace de la visita';
      default:
        return '';
    }
  });

  protected readonly bloqueado = computed(() => {
    const desenlace = this.desenlaceEscrito();
    switch (this.accion()) {
      case 'noRealizada':
      case 'cancelar':
        return !this.motivoEscrito().trim();
      case 'reprogramar':
        return !this.fechaEscrita() || !this.horaEscrita();
      case 'resultado':
        return (
          !desenlace?.resultado || (this.exigeRazon() && !desenlace?.razonNoContinuidad)
        );
      default:
        return false;
    }
  });

  ngOnInit(): void {
    const filtrosUrl$ = this.route.queryParamMap.pipe(
      map(filtrosVisitasDesdeUrl),
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
              query: filtros.texto || undefined,
            }),
            // El resumen NO lleva estado ni distrito: son los cubos y las
            // opciones que devuelve.
            resumen: this.api.resumen$({ query: filtros.texto || undefined }),
          }).pipe(
            switchMap((carga) => this.corregirPaginaFueraDeRango(filtros, carga)),
            catchError((error) =>
              of({
                error:
                  error instanceof ApiError ? error.message : 'No se pudo cargar la agenda.',
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => this.publicar(resultado));

    // La agenda inmediata es independiente de los filtros y falla por su
    // cuenta: si no responde, la bandeja se ve igual.
    this.cargarProximas();
  }

  protected cargarProximas(): void {
    this.api
      .proximas$()
      .pipe(
        catchError(() => of(paginaVacia<Visita>(8))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina) => this.proximas.set(pagina.items ?? []));
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

  protected seleccionarEstado(estado: string): void {
    this.navegar({ estado: this.filtros().estado === estado ? '' : estado, page: 1 });
  }

  protected cambiarDistrito(distrito: string): void {
    this.navegar({ distrito, page: 1 });
  }

  protected cambiarPagina(page: number): void {
    this.navegar({ page });
  }

  protected limpiar(): void {
    this.navegar({ texto: '', estado: '', distrito: '', page: 1 });
  }

  protected recargar(): void {
    this.recargar$.next();
  }

  protected verSeguimiento(visita: Visita): void {
    if (visita.idOportunidad) void this.router.navigate(['/oportunidades', visita.idOportunidad]);
  }

  /** Programada o reprogramada: todavía puede realizarse, moverse o caerse. */
  protected pendiente(visita: Visita): boolean {
    return VISITA_PENDIENTE.has(visita.estado ?? '');
  }

  /** Realizada y sin desenlace: el resultado es irrepetible. */
  protected admiteResultado(visita: Visita): boolean {
    return visita.estado === 'R' && !visita.resultado;
  }

  protected abrir(accion: Accion, visita: Visita): void {
    this.objetivo.set(visita);
    this.errorAccion.set(null);
    this.aviso.set(null);
    this.motivo.reset('');
    this.nuevaFecha.reset(visita.fechaVisita ?? '');
    this.nuevaHora.reset(visita.horaVisita ?? '');
    this.desenlace.reset({
      resultado: '',
      observaciones: '',
      razonNoContinuidad: '',
      nivelInteres: 0,
      objecionPrincipal: '',
      opinionPrecio: '',
      proximaAccion: '',
    });
    this.accion.set(accion);
  }

  protected cerrarDialogo(): void {
    if (!this.procesando()) {
      this.accion.set(null);
      this.objetivo.set(null);
    }
  }

  protected async confirmar(): Promise<void> {
    const visita = this.objetivo();
    const accion = this.accion();
    if (!visita || !accion || this.procesando()) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    try {
      const resultado = await this.ejecutar(accion, visita);
      this.accion.set(null);
      this.objetivo.set(null);
      this.aviso.set(resultado);
      this.recargar();
      this.cargarProximas();
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudo actualizar la visita.'));
    } finally {
      this.procesando.set(false);
    }
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_VISITA, codigo) || SIN_DATO;
  }

  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'R') return 'bien';
    if (codigo === 'C' || codigo === 'N') return 'mal';
    return 'aviso';
  }

  protected etiquetaResultado(codigo: string | undefined): string {
    return codigo ? describir(RESULTADO_VISITA, codigo) : SIN_DATO;
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  /** `16:00:00` del cable -> `16:00`: la agenda no se cita al segundo. */
  protected hora(valor: string | undefined): string {
    return horaDe(valor);
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private async ejecutar(accion: Accion, visita: Visita): Promise<string> {
    switch (accion) {
      case 'realizar':
        await this.api.realizar(visita.id);
        return 'Visita marcada como realizada. Ahora puedes registrar su desenlace.';
      case 'noRealizada':
        await this.api.noRealizada(visita.id, this.motivo.value.trim());
        return 'Visita marcada como no realizada.';
      case 'cancelar':
        await this.api.cancelar(visita.id, this.motivo.value.trim());
        return 'Visita cancelada.';
      case 'reprogramar':
        await this.api.reprogramar(visita.id, this.nuevaFecha.value, this.nuevaHora.value);
        return 'Visita reprogramada.';
      case 'resultado': {
        const datos = this.desenlace.getRawValue();
        await this.api.registrarResultado(visita.id, {
          resultado: datos.resultado,
          observaciones: datos.observaciones.trim() || undefined,
          razonNoContinuidad: datos.razonNoContinuidad || undefined,
          // 0 = "sin indicar": el cable distingue ausente de cero, y el nivel
          // de interés va de 1 a 5.
          nivelInteres: datos.nivelInteres || undefined,
          objecionPrincipal: datos.objecionPrincipal || undefined,
          opinionPrecio: datos.opinionPrecio || undefined,
          proximaAccion: datos.proximaAccion || undefined,
        });
        return implicaNoContinuidad(datos.resultado)
          ? 'Desenlace registrado. La oportunidad quedó cerrada por no continuidad.'
          : 'Desenlace registrado.';
      }
    }
  }

  private corregirPaginaFueraDeRango(
    filtros: FiltrosVisitasUrl,
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

  private navegar(cambios: Partial<FiltrosVisitasUrl>, replaceUrl = false): void {
    const siguiente = { ...this.filtros(), ...cambios };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        texto: siguiente.texto || null,
        estado: siguiente.estado || null,
        distrito: siguiente.distrito || null,
        page: siguiente.page,
      },
      replaceUrl,
    });
  }
}

export function filtrosVisitasDesdeUrl(params: ParamMap): FiltrosVisitasUrl {
  const estadoSolicitado = (params.get('estado') ?? '').trim().toUpperCase();
  const solicitada = Number(params.get('page') ?? '1');
  return {
    texto: (params.get('texto') ?? '').trim(),
    // Un código inventado en la URL no se manda al backend: se ignora.
    estado: ESTADOS_VALIDOS.has(estadoSolicitado) ? estadoSolicitado : '',
    distrito: (params.get('distrito') ?? '').trim(),
    page: Number.isSafeInteger(solicitada) && solicitada > 0 ? solicitada : 1,
  };
}

function mismosFiltros(a: FiltrosVisitasUrl, b: FiltrosVisitasUrl): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
