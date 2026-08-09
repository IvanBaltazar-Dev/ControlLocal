import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { DashboardService } from '../../core/api/dashboard.service';
import {
  esPeriodo,
  IndicadorConteo,
  IndicadoresResumen,
  PERIODO_POR_DEFECTO,
  PERIODOS_INDICADORES,
} from '../../core/api/indicadores.service';
import { MAXIMO_BANDEJA, Tarea, TareasService } from '../../core/api/tareas.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion } from '../../core/auth/sesion.model';
import { fechaCorta, SIN_DATO } from '../../core/formato';
import { NavegacionLegado } from '../../core/navegacion-legado';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
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
  titulo: string;
  descripcion: string;
  valor: number;
  ruta: string;
  tono: TonoKpi;
  /** Ordena los focos; el valor desempata. */
  peso: number;
}

/** Un tramo de la barra apilada de captaciones. */
interface Tramo extends IndicadorConteo {
  porcentaje: number;
  color: string;
}

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
 * - **La bandeja está cortada en 10** y el resto se descarta en silencio, así
 *   que el contador se rotula como "hasta 10", no como el total de pendientes.
 * - **Cancelar una tarea es definitivo**: `CANCELADA` impide que el
 *   reconciliador la vuelva a crear para esa entidad. El diálogo lo dice antes
 *   de confirmar; no es "recordar más tarde".
 */
@Component({
  selector: 'app-dashboard',
  imports: [DialogoConfirmacion, EstadoListado, RouterLink, TarjetaKpi],
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
  protected readonly maximoBandeja = MAXIMO_BANDEJA;
  protected readonly sinDato = SIN_DATO;

  protected readonly periodo = signal<string>(PERIODO_POR_DEFECTO);
  protected readonly indicadores = signal<IndicadoresResumen | null>(null);
  protected readonly bandeja = signal<Tarea[]>([]);
  protected readonly totalBandeja = signal(0);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /** La bandeja completa se pide aparte; hasta entonces solo llegan 5. */
  protected readonly bandejaCompleta = signal(false);
  protected readonly ampliando = signal(false);

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
      const carga = await this.api.cargar(this.periodo(), 5);
      this.indicadores.set(carga.indicadores);
      this.bandeja.set(carga.bandeja.items ?? []);
      this.totalBandeja.set(carga.bandeja.totalRecords ?? 0);
      this.bandejaCompleta.set((carga.bandeja.items ?? []).length >= (carga.bandeja.totalRecords ?? 0));
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

  /** Trae la bandeja entera (hasta 10). El dashboard solo compone las 5. */
  protected async verTodas(): Promise<void> {
    if (this.bandejaCompleta() || this.ampliando()) {
      return;
    }
    this.ampliando.set(true);
    this.avisoBandeja.set(null);
    try {
      const todas = await this.tareas.bandeja();
      this.bandeja.set(todas);
      this.totalBandeja.set(todas.length);
      this.bandejaCompleta.set(true);
    } catch (fallo) {
      this.avisoBandeja.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cargar el resto de la bandeja.',
      );
    } finally {
      this.ampliando.set(false);
    }
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
      // Se recarga la bandeja entera: cancelar una puede destapar otra que el
      // corte en 10 dejaba fuera.
      const todas = await this.tareas.bandeja();
      this.bandeja.set(this.bandejaCompleta() ? todas : todas.slice(0, 5));
      this.totalBandeja.set(todas.length);
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
          tono: i.captacionesPorRevisar > 0 ? 'ambar' : 'verde',
          ruta: '/captaciones/pendientes',
        },
        {
          etiqueta: 'Solicitudes por revisar',
          valor: i.solicitudesPorEvaluar,
          tono: i.solicitudesPorEvaluar > 0 ? 'rojo' : 'verde',
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
          etiqueta: 'Recontactos vencidos',
          valor: String(op.recontactosVencidos),
          tono: op.recontactosVencidos > 0 ? 'rojo' : 'verde',
          pie: 'riesgo de enfriamiento',
        },
        {
          etiqueta: 'Aprobadas sin cierre',
          valor: String(op.solicitudesSinCierre),
          tono: op.solicitudesSinCierre > 0 ? 'ambar' : 'verde',
          pie: 'ingreso comprometido',
        },
        {
          etiqueta: 'Prospección → captación',
          valor: `${op.conversionProspeccionCaptacion}%`,
          tono: 'info',
          pie: 'captación de inventario',
        },
        {
          etiqueta: 'Equipo activo',
          valor: String(i.agentesActivos),
          tono: 'azul',
          pie: `${i.brokersActivos} brokers`,
        },
      ];
    }
    if (this.esBroker()) {
      return [
        {
          etiqueta: 'Recontactos vencidos',
          valor: String(op.recontactosVencidos),
          tono: op.recontactosVencidos > 0 ? 'rojo' : 'verde',
          pie: 'seguimiento del equipo',
        },
        {
          etiqueta: 'Aprobadas sin cierre',
          valor: String(op.solicitudesSinCierre),
          tono: op.solicitudesSinCierre > 0 ? 'ambar' : 'verde',
          pie: 'contrato pendiente',
        },
        {
          etiqueta: 'Visitas pendientes',
          valor: String(op.visitasPendientes),
          tono: 'azul',
          pie: 'programadas o vencidas',
        },
        {
          etiqueta: 'Prospección → captación',
          valor: `${op.conversionProspeccionCaptacion}%`,
          tono: 'info',
          pie: 'disciplina de captación',
        },
      ];
    }
    return [
      {
        etiqueta: 'Recontactos vencidos',
        valor: String(op.recontactosVencidos),
        tono: op.recontactosVencidos > 0 ? 'rojo' : 'verde',
        pie: 'atiéndelos primero',
      },
      {
        etiqueta: 'Días sin seguimiento',
        valor: String(op.diasPromedioSinSeguimiento),
        tono: op.diasPromedioSinSeguimiento > 7 ? 'ambar' : 'azul',
        pie: 'promedio de lo vencido',
      },
      {
        etiqueta: 'Visitas pendientes',
        valor: String(op.visitasPendientes),
        tono: 'azul',
        pie: 'prepara o cierra resultado',
      },
      {
        etiqueta: 'Aprobadas sin cierre',
        valor: String(op.solicitudesSinCierre),
        tono: op.solicitudesSinCierre > 0 ? 'ambar' : 'verde',
        pie: 'contrato pendiente',
      },
    ];
  });

  /**
   * Centro de control de quien supervisa. Sustituye a la bandeja —que no
   * tienen— y **solo enlaza a pantallas migradas**: un foco que no lleva a
   * ninguna parte es peor que su ausencia.
   */
  protected readonly focos = computed<Foco[]>(() => {
    const i = this.indicadores();
    if (!i || this.esAgente()) {
      return [];
    }
    const op = i.operativo;
    const candidatos: Foco[] = this.esAdmin()
      ? [
          {
            titulo: 'Seguimiento en riesgo',
            descripcion: 'Recontactos vencidos en toda la corredora',
            valor: op.recontactosVencidos,
            ruta: '/prospecciones',
            tono: 'rojo',
            peso: 96,
          },
          {
            titulo: 'Cierres sin formalizar',
            descripcion: 'Solicitudes aprobadas que aún no generan contrato',
            valor: op.solicitudesSinCierre,
            ruta: '/solicitudes',
            tono: 'ambar',
            peso: 90,
          },
          {
            titulo: 'Cierres registrados',
            descripcion: 'Alquileres formalizados y su comisión',
            valor: i.cierres,
            ruta: '/propiedades-alquiladas',
            tono: 'verde',
            peso: 78,
          },
          {
            titulo: 'Cobertura de agentes',
            descripcion: 'Asignaciones y supervisión por broker',
            valor: i.agentesActivos,
            ruta: '/asignaciones',
            tono: 'azul',
            peso: 72,
          },
        ]
      : [
          {
            titulo: 'Solicitudes por revisar',
            descripcion: 'Expedientes esperando tu evaluación',
            valor: i.solicitudesPorEvaluar,
            ruta: '/solicitudes/revisar',
            tono: 'rojo',
            peso: 100,
          },
          {
            titulo: 'Captaciones por revisar',
            descripcion: 'Captaciones esperando tu decisión',
            valor: i.captacionesPorRevisar,
            ruta: '/captaciones/pendientes',
            tono: 'ambar',
            peso: 92,
          },
          {
            titulo: 'Aprobadas sin cierre',
            descripcion: 'Ingreso comprometido sin contrato registrado',
            valor: op.solicitudesSinCierre,
            ruta: '/solicitudes',
            tono: 'ambar',
            peso: 86,
          },
          {
            titulo: 'Recontactos vencidos',
            descripcion: 'Prospecciones del equipo sin seguimiento a tiempo',
            valor: op.recontactosVencidos,
            ruta: '/prospecciones',
            tono: 'rojo',
            peso: 84,
          },
          {
            titulo: 'Visitas pendientes',
            descripcion: 'Visitas próximas o vencidas sin resultado',
            valor: op.visitasPendientes,
            ruta: '/visitas',
            tono: 'azul',
            peso: 72,
          },
        ];
    return candidatos
      .filter((foco) => foco.valor > 0)
      .sort((a, b) => b.peso - a.peso || b.valor - a.valor)
      .slice(0, 5);
  });

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
   * Conversión propia por cohorte (captaciones del periodo que ya cerraron).
   * El backend la entrega acotada a 100; el respaldo por la fila de desempeño
   * es el del Blazor, para cuando el periodo no tiene cohorte.
   */
  protected readonly conversion = computed(() => {
    const i = this.indicadores();
    if (!i) return 0;
    return i.conversionPropia > 0 ? i.conversionPropia : (i.desempeno[0]?.conversion ?? 0);
  });

  protected iniciales(nombre: string): string {
    return nombre
      .split(/\s+/)
      .slice(0, 2)
      .map((parte) => parte.charAt(0).toUpperCase())
      .join('');
  }
}
