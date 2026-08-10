import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { DashboardService } from '../../core/api/dashboard.service';
import {
  esPeriodo,
  IndicadorConteo,
  IndicadoresResumen,
  IndicadorSenal,
  PERIODO_POR_DEFECTO,
  PERIODOS_INDICADORES,
} from '../../core/api/indicadores.service';
import { ConceptoSenal, NivelAtencion } from '../../core/politica-comercial';
import { Tarea, TareasService } from '../../core/api/tareas.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion } from '../../core/auth/sesion.model';
import { fechaCorta, SIN_DATO } from '../../core/formato';
import { NavegacionLegado } from '../../core/navegacion-legado';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { PanelLateral } from '../../shared/panel-lateral/panel-lateral';
import { TarjetaKpi, TonoKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

/** Una tarjeta de la fila superior: número grande y, si lleva ruta, atajo. */
interface Kpi {
  etiqueta: string;
  valor: number;
  tono: TonoKpi;
  pie?: string;
  ruta?: string;
}

/** Una señal del panel de disciplina: número + lectura de si está bien o mal. */
interface Senal {
  etiqueta: string;
  valor: string;
  tono: TonoKpi;
  pie: string;
}

/** Un foco del centro de control de quien supervisa (no tiene bandeja). */
interface Foco {
  concepto: ConceptoSenal;
  titulo: string;
  descripcion: string;
  valor: number;
  ruta: string;
  tono: TonoKpi;
  /** Del backend: 1 se atiende primero. El valor desempata. */
  prioridad: number;
}

/** Un tramo de la barra apilada de captaciones. */
interface Tramo extends IndicadorConteo {
  porcentaje: number;
  color: string;
}

/** Un chip del filtro de prioridad del panel, con su cuenta ya hecha. */
interface ChipPrioridad {
  valor: FiltroPrioridad;
  etiqueta: string;
  cuenta: number;
  tono: string;
}

type FiltroPrioridad = 'TODAS' | 'ALTA' | 'MEDIA' | 'BAJA';

/**
 * Cuántas tareas compone la tarjeta del panel. No es un tope de la bandeja —el
 * backend ya no tiene ninguno—, es el alto que la home puede gastar sin que la
 * columna izquierda se coma a la derecha. El resto se ve en el panel lateral.
 */
const TAREAS_EN_TARJETA = 5;

/** Las tres prioridades del cable, en el orden en que urgen. */
const PRIORIDADES: readonly { valor: FiltroPrioridad; etiqueta: string; tono: string }[] = [
  { valor: 'ALTA', etiqueta: 'Alta', tono: 'mal' },
  { valor: 'MEDIA', etiqueta: 'Media', tono: 'aviso' },
  { valor: 'BAJA', etiqueta: 'Baja', tono: '' },
];

/**
 * Los cuatro cubos de salud son ESTADOS, no series: llevan la paleta de estado
 * reservada (bien / atención / grave / neutro) y nunca se reutiliza para otra
 * cosa. Cada tramo va etiquetado con su nombre y su valor, así que el color
 * nunca es la única señal.
 */
const COLOR_SALUD: Record<string, string> = {
  Activas: 'var(--cl-exito)',
  'Por revisar': '#b26a00',
  Observadas: '#c0392b',
  'Bloqueadas/cerradas': '#7c8894',
};

/**
 * Las etapas son un recorrido ordenado (activa → interesados → solicitud →
 * evaluación → alquilada), así que su color es SECUENCIAL de un solo tono:
 * más avanzado, más oscuro. Un arcoíris categórico aquí sugeriría que las
 * etapas son independientes entre sí, y no lo son.
 */
const RAMPA_ETAPAS = ['#9dbecb', '#6ba0b4', '#3d829a', '#1d6180', '#0e3a4c'];

const COLOR_RESTO = '#c9d4dc';

/**
 * Cómo se ve cada nivel de atención.
 *
 * Esta tabla es **la mitad de R-07 que sí pertenece a la pantalla**: el dominio
 * dice cuánto urge algo y esto decide de qué color se pinta. Antes la pantalla
 * decidía las dos cosas, con ocho ternarios que además se contradecían entre
 * roles —el mismo recontacto vencido iba primero para el administrador y cuarto
 * para el broker— y con un `> 7` que era la cuarta copia del plazo de
 * recontacto.
 *
 * `SIN_PENDIENTES` es verde (no queda nada por atender) e `INFORMATIVO` azul
 * (es un dato, no un pendiente): la diferencia importa, porque un cero de
 * visitas agendadas no es una buena noticia, es un cero.
 */
const TONO_POR_NIVEL: Record<NivelAtencion, TonoKpi> = {
  ALTO: 'rojo',
  MEDIO: 'ambar',
  INFORMATIVO: 'azul',
  SIN_PENDIENTES: 'verde',
};

/** Lo que se asume si el concepto no viniera en la respuesta. Nunca debería faltar. */
const SENAL_AUSENTE: Omit<IndicadorSenal, 'concepto'> = {
  valor: 0,
  nivelAtencion: 'SIN_PENDIENTES',
  requiereAtencion: false,
  prioridad: Number.MAX_SAFE_INTEGER,
};

/**
 * Home del sistema. Porta `Dashboard.razor`, pero con una diferencia de fondo:
 * el Blazor pedía indicadores y bandeja por separado, aquí las trae
 * `GET /dashboard` en **una sola llamada** (contrato congelado E4).
 *
 * Eso resuelve de paso la ambigüedad que el Blazor tenía que manejar a mano:
 * allí una bandeja vacía podía ser "todo al día" o "la llamada falló", y por eso
 * distinguía los dos estados. Aquí, si la respuesta llegó, la bandeja es
 * autoritativa.
 *
 * Tres reglas del cable que la pantalla respeta:
 *
 * - **Solo el AGENTE tiene bandeja.** Para BROKER y ADMIN llega vacía y **eso
 *   no es un error**: se les muestra su centro de control (los focos derivados
 *   de los indicadores), no un "no hay tareas".
 * - **La bandeja ya no tiene tope.** El corte en 10 se retiró el 2026-08-08
 *   (era D-F7-2), así que `totalRecords` es el total real y puede ser 30 o 50.
 *   La tarjeta se queda en las **5 primeras** pase lo que pase, y el resto se ve
 *   en un panel lateral con su propio scroll: expandir la lista dentro de la
 *   tarjeta estiraba la columna izquierda y descuadraba la rejilla, y encima no
 *   había vuelta atrás porque no existía "contraer".
 * - **Cancelar una tarea es definitivo**: `CANCELADA` impide que el
 *   reconciliador la vuelva a crear para esa entidad. El diálogo lo dice antes
 *   de confirmar; no es "recordar más tarde".
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    BarraFiltros,
    DialogoConfirmacion,
    EstadoListado,
    NgTemplateOutlet,
    PanelLateral,
    RouterLink,
    TarjetaKpi,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard implements OnInit {
  private readonly api = inject(DashboardService);
  private readonly tareas = inject(TareasService);
  private readonly navegacion = inject(NavegacionLegado);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly periodos = PERIODOS_INDICADORES;
  protected readonly sinDato = SIN_DATO;
  /** Cuántas tareas compone la tarjeta; el resto vive en el panel. */
  protected readonly enTarjeta = TAREAS_EN_TARJETA;

  protected readonly periodo = signal<string>(PERIODO_POR_DEFECTO);
  protected readonly indicadores = signal<IndicadoresResumen | null>(null);
  protected readonly bandeja = signal<Tarea[]>([]);
  protected readonly totalBandeja = signal(0);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /**
   * La bandeja completa vive en el panel y se pide aparte: `/dashboard` solo
   * compone las 5 primeras. Se recarga en **cada apertura** porque `GET /tareas`
   * es la reconciliación —abrir el panel es la forma de ver la bandeja al día—,
   * y cachearla mostraría tareas ya resueltas en otra pestaña.
   */
  protected readonly panelAbierto = signal(false);
  protected readonly bandejaTodas = signal<Tarea[]>([]);
  protected readonly cargandoTodas = signal(false);
  /**
   * Fallo al traer la bandeja completa. Va aparte de `avisoBandeja` porque este
   * **se reintenta** y el otro no: "esa tarea no tiene pantalla" con un botón de
   * reintentar sería una promesa falsa.
   */
  protected readonly errorTodas = signal<string | null>(null);

  /** Filtros del panel. Ambos son en memoria: la lista ya está entera. */
  protected readonly filtroPrioridad = signal<FiltroPrioridad>('TODAS');
  protected readonly busquedaTareas = signal('');

  protected readonly porCancelar = signal<Tarea | null>(null);
  protected readonly cancelando = signal(false);
  protected readonly avisoBandeja = signal<string | null>(null);

  private readonly rol = computed<RolSesion | undefined>(() => this.auth.sesion()?.rol);
  protected readonly esAgente = computed(() => this.rol() === 'AGENTE');
  protected readonly esBroker = computed(() => this.rol() === 'BROKER');
  protected readonly esAdmin = computed(() => this.rol() === 'TENANT_ADMIN');

  protected readonly nombre = computed(
    () => this.auth.sesion()?.nombre?.split(/\s+/)[0] ?? 'equipo',
  );

  protected readonly titulo = computed(() => {
    if (this.esAdmin()) return 'Panel administrativo';
    if (this.esBroker()) return 'Panel del broker';
    return `Buen día, ${this.nombre()}`;
  });

  protected readonly subtitulo = computed(() => {
    if (this.esAdmin()) return 'Lectura global de la corredora: carga, seguimiento y cierres.';
    if (this.esBroker())
      return 'Supervisión de tu equipo: revisiones pendientes, riesgo operativo y cierres.';
    return 'Tu actividad comercial y lo que necesita tu atención hoy.';
  });

  ngOnInit(): void {
    const pedido = this.route.snapshot.queryParamMap.get('periodo');
    this.periodo.set(esPeriodo(pedido) ? pedido : PERIODO_POR_DEFECTO);
    void this.cargar();
  }

  /** Refresca en cada entrada: el dashboard es lo primero que envejece. */
  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    this.avisoBandeja.set(null);
    try {
      const carga = await this.api.cargar(this.periodo(), TAREAS_EN_TARJETA);
      this.indicadores.set(carga.indicadores);
      this.bandeja.set(carga.bandeja.items ?? []);
      this.totalBandeja.set(carga.bandeja.totalRecords ?? 0);
    } catch (fallo) {
      this.indicadores.set(null);
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cargar el panel.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  protected cambiarPeriodo(valor: string): void {
    if (!esPeriodo(valor) || valor === this.periodo()) {
      return;
    }
    this.periodo.set(valor);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { periodo: valor === PERIODO_POR_DEFECTO ? null : valor },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    void this.cargar();
  }

  protected etiquetaPeriodo(): string {
    return this.periodos.find((p) => p.valor === this.periodo())?.etiqueta ?? '';
  }

  // --- Bandeja del agente -------------------------------------------------

  /** ¿Quedan tareas fuera de las que compone la tarjeta? */
  protected readonly hayMasTareas = computed(() => this.totalBandeja() > this.bandeja().length);

  /**
   * Abre el panel y trae la bandeja entera. Un fallo **no lo cierra**: se queda
   * abierto con el aviso y el botón de reintentar, como el resto de listados.
   */
  protected async abrirPanel(): Promise<void> {
    this.panelAbierto.set(true);
    this.limpiarFiltrosTareas();
    this.avisoBandeja.set(null);
    await this.recargarTodas();
  }

  protected cerrarPanel(): void {
    // No con una confirmación abierta encima. El ESC del panel escucha en el
    // documento, así que sin esto un ESC sobre "¿cancelar esta tarea?" se
    // llevaba por delante el panel y dejaba el diálogo flotando solo.
    if (this.porCancelar() !== null) {
      return;
    }
    this.panelAbierto.set(false);
  }

  /**
   * La lectura que reconcilia (`GET /tareas` escribe). Refresca de paso las 5
   * de la tarjeta: son un prefijo de la misma lista, y dejarlas desfasadas
   * mientras el panel muestra otra cosa es peor que la llamada.
   */
  protected async recargarTodas(): Promise<void> {
    this.cargandoTodas.set(true);
    this.errorTodas.set(null);
    try {
      const todas = await this.tareas.bandeja();
      this.bandejaTodas.set(todas);
      this.bandeja.set(todas.slice(0, TAREAS_EN_TARJETA));
      this.totalBandeja.set(todas.length);
      // Si la prioridad filtrada se quedó sin tareas (se canceló la última
      // ALTA), el filtro dejaría una lista vacía sin chip al que volver.
      const prioridad = this.filtroPrioridad();
      if (prioridad !== 'TODAS' && !todas.some((t) => t.prioridad === prioridad)) {
        this.filtroPrioridad.set('TODAS');
      }
    } catch (fallo) {
      this.bandejaTodas.set([]);
      this.errorTodas.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cargar la bandeja completa.',
      );
    } finally {
      this.cargandoTodas.set(false);
    }
  }

  /**
   * Chips de prioridad del panel. **Las cuentas salen de los datos**: ni un
   * `<option>` a mano ni un número fijo, que es la convención de filtros del
   * sistema. Una prioridad sin tareas no se ofrece.
   */
  protected readonly chipsPrioridad = computed<ChipPrioridad[]>(() => {
    const todas = this.bandejaTodas();
    const chips: ChipPrioridad[] = [
      { valor: 'TODAS', etiqueta: 'Todas', cuenta: todas.length, tono: '' },
    ];
    for (const prioridad of PRIORIDADES) {
      const cuenta = todas.filter((t) => t.prioridad === prioridad.valor).length;
      if (cuenta > 0) {
        chips.push({ ...prioridad, cuenta });
      }
    }
    return chips;
  });

  /** Filtrado en memoria: la lista ya está entera, no hay que volver al API. */
  protected readonly tareasFiltradas = computed<Tarea[]>(() => {
    const prioridad = this.filtroPrioridad();
    const texto = this.busquedaTareas().trim().toLowerCase();
    return this.bandejaTodas().filter((tarea) => {
      if (prioridad !== 'TODAS' && tarea.prioridad !== prioridad) {
        return false;
      }
      if (!texto) {
        return true;
      }
      return (
        tarea.descripcion.toLowerCase().includes(texto) ||
        (tarea.entidadCodigo ?? '').toLowerCase().includes(texto)
      );
    });
  });

  protected readonly hayFiltroTareas = computed(
    () => this.filtroPrioridad() !== 'TODAS' || this.busquedaTareas().trim().length > 0,
  );

  protected filtrarPor(valor: FiltroPrioridad): void {
    this.filtroPrioridad.set(valor);
  }

  protected limpiarFiltrosTareas(): void {
    this.filtroPrioridad.set('TODAS');
    this.busquedaTareas.set('');
  }

  protected async resolver(tarea: Tarea): Promise<void> {
    const abierta = await this.navegacion.abrir(tarea.rutaResolver);
    if (!abierta) {
      this.avisoBandeja.set('Esa tarea no tiene una pantalla a la que llevarte todavía.');
    }
  }

  protected puedeResolver(tarea: Tarea): boolean {
    return this.navegacion.puedeAbrir(tarea.rutaResolver);
  }

  protected pedirCancelacion(tarea: Tarea): void {
    this.porCancelar.set(tarea);
  }

  protected async confirmarCancelacion(): Promise<void> {
    const tarea = this.porCancelar();
    if (!tarea) {
      return;
    }
    this.cancelando.set(true);
    try {
      await this.tareas.cancelar(tarea.id);
      this.porCancelar.set(null);
      // Se recarga la bandeja entera en vez de quitar la fila en el cliente:
      // cancelar la 3ª destapa la 6ª en la tarjeta, y esa no estaba descargada.
      await this.recargarTodas();
    } catch (fallo) {
      this.avisoBandeja.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cancelar la tarea.',
      );
      this.porCancelar.set(null);
    } finally {
      this.cancelando.set(false);
    }
  }

  protected tonoPrioridad(prioridad: string): string {
    if (prioridad === 'ALTA') return 'mal';
    if (prioridad === 'MEDIA') return 'aviso';
    return '';
  }

  protected diasTexto(dias: number | undefined): string {
    if (!dias || dias <= 0) return 'sin demora';
    return dias === 1 ? '1 día sin acción' : `${dias} días sin acción`;
  }

  protected vencimiento(tarea: Tarea): string {
    return tarea.fechaVencimiento ? `vence ${fechaCorta(tarea.fechaVencimiento)}` : '';
  }

  // --- Tarjetas y señales -------------------------------------------------

  /**
   * Las señales del backend indexadas por concepto. Vienen ya clasificadas y
   * ordenadas; aquí solo se buscan por nombre para componer cada tarjeta.
   */
  private readonly porConcepto = computed<Map<ConceptoSenal, IndicadorSenal>>(
    () => new Map((this.indicadores()?.senales ?? []).map((s) => [s.concepto, s])),
  );

  private senal(concepto: ConceptoSenal): IndicadorSenal {
    return this.porConcepto().get(concepto) ?? { concepto, ...SENAL_AUSENTE };
  }

  /** El color que le toca a un concepto. Lo único que decide la pantalla. */
  private tono(concepto: ConceptoSenal): TonoKpi {
    return TONO_POR_NIVEL[this.senal(concepto).nivelAtencion];
  }

  protected readonly kpis = computed<Kpi[]>(() => {
    const i = this.indicadores();
    if (!i) {
      return [];
    }
    if (this.esAdmin()) {
      return [
        { etiqueta: 'Brokers activos', valor: i.brokersActivos, tono: 'azul', ruta: '/brokers' },
        {
          etiqueta: 'Agentes activos',
          valor: i.agentesActivos,
          tono: 'azul',
          ruta: '/asignaciones',
        },
        {
          etiqueta: 'Captaciones activas',
          valor: i.captacionesActivas,
          tono: 'verde',
          ruta: '/captaciones',
        },
        {
          etiqueta: 'Operaciones abiertas',
          valor: i.oportunidadesActivas,
          tono: 'info',
          ruta: '/oportunidades',
        },
      ];
    }
    if (this.esBroker()) {
      return [
        {
          etiqueta: 'Captaciones por revisar',
          valor: i.captacionesPorRevisar,
          tono: this.tono('CAPTACION_POR_REVISAR'),
          ruta: '/captaciones/pendientes',
        },
        {
          etiqueta: 'Solicitudes por revisar',
          valor: i.solicitudesPorEvaluar,
          tono: this.tono('SOLICITUD_POR_EVALUAR'),
          ruta: '/solicitudes/revisar',
        },
        {
          etiqueta: 'Operaciones abiertas',
          valor: i.oportunidadesActivas,
          tono: 'info',
          ruta: '/oportunidades',
        },
        {
          etiqueta: 'Cierres del equipo',
          valor: i.cierres,
          tono: 'verde',
          pie: this.etiquetaPeriodo().toLowerCase(),
          ruta: '/propiedades-alquiladas',
        },
        {
          etiqueta: 'Propiedades del equipo',
          valor: i.propiedadesEquipo,
          tono: 'azul',
          pie: 'inmuebles captados',
          ruta: '/propiedades-equipo',
        },
      ];
    }
    return [
      {
        etiqueta: 'Mis captaciones',
        valor: i.captacionesTotales,
        tono: 'azul',
        pie: `${i.captacionesActivas} activas · ${i.captacionesObservadas} observadas`,
        ruta: '/captaciones',
      },
      {
        etiqueta: 'Operaciones abiertas',
        valor: i.oportunidadesActivas,
        tono: 'info',
        ruta: '/oportunidades',
      },
      {
        etiqueta: 'Visitas',
        valor: i.visitas,
        tono: 'azul',
        pie: this.etiquetaPeriodo().toLowerCase(),
        ruta: '/visitas',
      },
      {
        etiqueta: 'Interacciones',
        valor: i.interacciones,
        tono: 'azul',
        pie: this.etiquetaPeriodo().toLowerCase(),
        ruta: '/interacciones',
      },
    ];
  });

  protected readonly senales = computed<Senal[]>(() => {
    const i = this.indicadores();
    if (!i) {
      return [];
    }
    const op = i.operativo;
    if (this.esAdmin()) {
      return [
        {
          etiqueta: 'Prospectos sin contactar a tiempo',
          valor: String(op.recontactosVencidos),
          tono: this.tono('RECONTACTO_VENCIDO'),
          pie: 'se enfrían si nadie los llama',
        },
        {
          etiqueta: 'Aprobadas sin contrato',
          valor: String(op.solicitudesSinCierre),
          tono: this.tono('SOLICITUD_APROBADA_SIN_CIERRE'),
          pie: 'ya aprobadas, falta firmar',
        },
        {
          etiqueta: 'Prospectos captados',
          valor: `${op.conversionProspeccionCaptacion}%`,
          tono: 'info',
          pie: 'de los trabajados en el periodo',
        },
        {
          etiqueta: 'Equipo en operación',
          valor: String(i.agentesActivos),
          tono: 'azul',
          pie: `${i.brokersActivos} brokers`,
        },
      ];
    }
    if (this.esBroker()) {
      return [
        {
          etiqueta: 'Prospectos sin contactar a tiempo',
          valor: String(op.recontactosVencidos),
          tono: this.tono('RECONTACTO_VENCIDO'),
          pie: 'de tu equipo',
        },
        {
          etiqueta: 'Aprobadas sin contrato',
          valor: String(op.solicitudesSinCierre),
          tono: this.tono('SOLICITUD_APROBADA_SIN_CIERRE'),
          pie: 'falta firmar',
        },
        {
          etiqueta: 'Visitas pendientes',
          valor: String(op.visitasPendientes),
          tono: this.tono('VISITA_PENDIENTE'),
          pie: 'por hacer o sin resultado',
        },
        {
          etiqueta: 'Prospectos captados',
          valor: `${op.conversionProspeccionCaptacion}%`,
          tono: 'info',
          pie: 'de los trabajados en el periodo',
        },
      ];
    }
    return [
      {
        etiqueta: 'Prospectos sin contactar a tiempo',
        valor: String(op.recontactosVencidos),
        tono: this.tono('RECONTACTO_VENCIDO'),
        pie: 'empieza por aquí',
      },
      {
        etiqueta: 'Atraso promedio',
        valor: String(op.diasPromedioSinSeguimiento),
        tono: this.tono('DEMORA_DE_SEGUIMIENTO'),
        pie: 'días desde que debiste llamarlos',
      },
      {
        etiqueta: 'Visitas pendientes',
        valor: String(op.visitasPendientes),
        tono: this.tono('VISITA_PENDIENTE'),
        pie: 'por hacer o sin resultado',
      },
      {
        etiqueta: 'Aprobadas sin contrato',
        valor: String(op.solicitudesSinCierre),
        tono: this.tono('SOLICITUD_APROBADA_SIN_CIERRE'),
        pie: 'falta firmar',
      },
    ];
  });

  /**
   * Centro de control de quien supervisa. Sustituye a la bandeja —que no
   * tienen— y **solo enlaza a pantallas migradas**: un foco que no lleva a
   * ninguna parte es peor que su ausencia.
   *
   * El orden ya no se inventa aquí. Antes cada rol traía su propia escala de
   * pesos y las dos se contradecían: para el administrador lo primero eran los
   * recontactos vencidos y para el broker eran los cuartos. Ahora el orden lo
   * da la política del dominio y es el mismo para todos; lo que sigue siendo de
   * la pantalla es **qué focos enseña cada rol** y a dónde llevan.
   */
  protected readonly focos = computed<Foco[]>(() => {
    const i = this.indicadores();
    if (!i || this.esAgente()) {
      return [];
    }
    const candidatos: Foco[] = this.esAdmin()
      ? [
          this.foco('RECONTACTO_VENCIDO', {
            titulo: 'Prospectos sin contactar a tiempo',
            descripcion: 'En toda la corredora, ya se pasaron de fecha',
            ruta: '/prospecciones',
          }),
          this.foco('SOLICITUD_APROBADA_SIN_CIERRE', {
            titulo: 'Aprobadas sin contrato',
            descripcion: 'Ya se aprobaron y todavía nadie firma',
            ruta: '/solicitudes',
          }),
          this.foco('CIERRE_REGISTRADO', {
            titulo: 'Alquileres firmados',
            descripcion: 'Contratos cerrados y su comisión',
            ruta: '/propiedades-alquiladas',
          }),
          this.foco('COBERTURA_DE_AGENTES', {
            titulo: 'Agentes en operación',
            descripcion: 'Quién supervisa a quién',
            ruta: '/asignaciones',
          }),
        ]
      : [
          this.foco('SOLICITUD_POR_EVALUAR', {
            titulo: 'Solicitudes por revisar',
            descripcion: 'Hay interesados esperando tu respuesta',
            ruta: '/solicitudes/revisar',
          }),
          this.foco('CAPTACION_POR_REVISAR', {
            titulo: 'Captaciones por revisar',
            descripcion: 'No se pueden ofrecer hasta que las apruebes',
            ruta: '/captaciones/pendientes',
          }),
          this.foco('SOLICITUD_APROBADA_SIN_CIERRE', {
            titulo: 'Aprobadas sin contrato',
            descripcion: 'Falta firmar; la comisión todavía no se gana',
            ruta: '/solicitudes',
          }),
          this.foco('RECONTACTO_VENCIDO', {
            titulo: 'Prospectos sin contactar a tiempo',
            descripcion: 'Del equipo; se enfrían si nadie los llama',
            ruta: '/prospecciones',
          }),
          this.foco('VISITA_PENDIENTE', {
            titulo: 'Visitas pendientes',
            descripcion: 'Por hacer, o hechas y sin registrar el resultado',
            ruta: '/visitas',
          }),
        ];
    return candidatos
      .filter((foco) => foco.valor > 0)
      .sort((a, b) => a.prioridad - b.prioridad || b.valor - a.valor)
      .slice(0, 5);
  });

  /** Compone un foco con el rótulo y la ruta de la pantalla, y el resto del dominio. */
  private foco(
    concepto: ConceptoSenal,
    presentacion: Pick<Foco, 'titulo' | 'descripcion' | 'ruta'>,
  ): Foco {
    const senal = this.senal(concepto);
    return {
      concepto,
      ...presentacion,
      valor: senal.valor,
      tono: TONO_POR_NIVEL[senal.nivelAtencion],
      prioridad: senal.prioridad,
    };
  }

  // --- Captaciones: barra apilada -----------------------------------------

  /**
   * Los cubos de salud son **del periodo**; las etapas, acumuladas. Cuando el
   * periodo no tuvo ninguna captación se muestran las etapas —que es lo que
   * hacía el Blazor— pero **diciéndolo en el subtítulo**, porque son dos
   * lecturas distintas y no un mismo dato con otro nombre.
   */
  protected readonly usandoEtapas = computed(() => {
    const i = this.indicadores();
    if (!i) return false;
    return i.captacionesSalud.reduce((total, c) => total + c.valor, 0) === 0;
  });

  protected readonly cubos = computed<IndicadorConteo[]>(() => {
    const i = this.indicadores();
    if (!i) return [];
    return this.usandoEtapas() ? i.etapas : i.captacionesSalud;
  });

  protected readonly totalCubos = computed(() =>
    this.cubos().reduce((total, c) => total + c.valor, 0),
  );

  protected readonly tramos = computed<Tramo[]>(() => {
    const total = this.totalCubos();
    const etapas = this.usandoEtapas();
    return this.cubos().map((cubo, indice) => ({
      ...cubo,
      porcentaje: total > 0 ? Math.round((cubo.valor * 100) / total) : 0,
      color: etapas
        ? (RAMPA_ETAPAS[indice] ?? COLOR_RESTO)
        : (COLOR_SALUD[cubo.nombre] ?? COLOR_RESTO),
    }));
  });

  protected readonly tituloCubos = computed(() =>
    this.usandoEtapas() ? 'Captaciones por etapa' : 'Salud de captaciones',
  );

  protected readonly subtituloCubos = computed(() =>
    this.usandoEtapas()
      ? `Sin captaciones nuevas en ${this.etiquetaPeriodo().toLowerCase()}; se muestra el acumulado por etapa del proceso`
      : `Estado de las captaciones nacidas en ${this.etiquetaPeriodo().toLowerCase()}`,
  );

  // --- Desempeño y conversión ---------------------------------------------

  /** Top 3 por carga sobre lo que ya llegó (≤ 8 filas): sin llamada extra. */
  protected readonly desempeno = computed(() =>
    [...(this.indicadores()?.desempeno ?? [])]
      .sort((a, b) => b.captaciones - a.captaciones || b.cierres - a.cierres)
      .slice(0, 3),
  );

  /**
   * Conversión propia por cohorte: de las captaciones del periodo, cuántas ya
   * cerraron. `null` cuando no hubo ninguna, y entonces la pantalla lo dice en
   * vez de pintar un número.
   *
   * Aquí había un respaldo heredado del Blazor: si la cohorte era 0, se tomaba
   * **la conversión del primero de la tabla de desempeño**. Un agente sin
   * cierres veía como propia la cifra del que más cerró. No era un umbral mal
   * puesto —era un número de otra persona— y el descongelado del contrato dice
   * justamente que una rareza de la v1 no se replica por inercia.
   */
  protected readonly conversion = computed(() => this.indicadores()?.conversionPropia ?? null);

  /** Ancho de la barra del medidor. Sin muestra no hay barra que llenar. */
  protected readonly conversionAncho = computed(() => this.conversion() ?? 0);

  protected iniciales(nombre: string): string {
    return nombre
      .split(/\s+/)
      .slice(0, 2)
      .map((parte) => parte.charAt(0).toUpperCase())
      .join('');
  }
}
