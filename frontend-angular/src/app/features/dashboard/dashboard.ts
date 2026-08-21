import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { AccesoRapido, DashboardService, Hallazgo } from '../../core/api/dashboard.service';
import {
  IndicadoresResumen,
  KpiCanonico,
  PERIODO_POR_DEFECTO,
} from '../../core/api/indicadores.service';
import {
  avanceDe,
  cierreLegible,
  cifraDe,
  frescuraDe,
  lecturaDe,
  marcaEsperadaDe,
  vozDelRitmo,
} from '../../core/rendimiento';
import { Tarea, TareasService } from '../../core/api/tareas.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion } from '../../core/auth/sesion.model';
import { estadoDelDia } from '../../core/estado-del-dia';
import { comoFecha } from '../../core/formato';
import { NavegacionLegado } from '../../core/navegacion-legado';
import { NombreIcono } from '../../shared/icono/icono';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { Icono } from '../../shared/icono/icono';
import { MallaBrox } from '../../shared/marca-brox/malla-brox';
import { PanelLateral } from '../../shared/panel-lateral/panel-lateral';
import { AsuntoDelFoco, desdeAsuntoDelBroker, desdeTarea } from './asunto-del-foco';
import { HallazgoEnRadar, Radar } from './partes/radar';

/**
 * Cuántos asuntos caben en el foco. **Es layout, no política**: si solo hay dos
 * accionables se ven dos, y nunca se rellena hasta cinco (D-E2-1 §5).
 */
const MAXIMO_FOCOS = 5;

/** El color del estado de ritmo. El estado lo decide el dominio; el color, no. */
const COLOR_RITMO: Record<string, string> = {
  EN_RITMO: 'var(--positivo)',
  ATENCION: 'var(--atencion)',
  FUERA_DE_RITMO: 'var(--riesgo)',
  SIN_BASE: 'var(--ink-3)',
};

/**
 * El icono y el color de cada acceso rápido, por su destino.
 *
 * **La etiqueta y la ruta las decide el dominio** (`AccesosDelInicio`); esto
 * solo dice con qué se dibuja cada una. Es presentación, y va por `destino` y
 * no por texto porque la ruta es la parte estable: el rótulo puede reescribirse
 * sin que el icono deje de corresponder.
 *
 * Un destino que no esté aquí sale sin icono, no con uno genérico: un icono
 * equivocado dice algo falso sobre a dónde lleva el enlace.
 */
const ICONO_DE_ACCESO: Record<string, { icono: NombreIcono; color: string }> = {
  'propiedades/nueva': { icono: 'mapa', color: 'var(--p-prospeccion)' },
  'captaciones/nueva': { icono: 'firma', color: 'var(--p-captacion)' },
  'visitas/nueva': { icono: 'cal', color: 'var(--p-visita)' },
  reportes: { icono: 'graf', color: 'var(--p-comision)' },
  'captaciones/pendientes': { icono: 'firma', color: 'var(--p-captacion)' },
  'solicitudes/revisar': { icono: 'doc', color: 'var(--p-solicitud)' },
  'seguimiento-comercial': { icono: 'pulso', color: 'var(--p-oportunidad)' },
  'captaciones/reasignaciones': { icono: 'persona', color: 'var(--p-cliente)' },
};

/** Qué se filtra en el foco. Vacío es «todo». */
type FiltroLado = '' | 'OFERTA' | 'DEMANDA';
type FiltroPrioridad = 'TODAS' | 'ALTA' | 'MEDIA' | 'BAJA';

/** Un chip del filtro de prioridad del panel, con su cuenta ya hecha. */
interface ChipPrioridad {
  valor: FiltroPrioridad;
  etiqueta: string;
  cuenta: number;
  tono: string;
}

/** Las tres prioridades del cable, en el orden en que urgen. */
const PRIORIDADES: readonly { valor: FiltroPrioridad; etiqueta: string; tono: string }[] = [
  { valor: 'ALTA', etiqueta: 'Alta', tono: 'mal' },
  { valor: 'MEDIA', etiqueta: 'Media', tono: 'aviso' },
  { valor: 'BAJA', etiqueta: 'Baja', tono: '' },
];

const FECHA_LARGA = new Intl.DateTimeFormat('es-PE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/**
 * **EL INICIO** — foco y resolución (D-E2-1).
 *
 * No es «lista de tareas + widgets». Son **dos superficies con una sola
 * lógica**: a la izquierda qué atender ahora —hasta cinco asuntos, para
 * elegir— y a la derecha el Radar BROX, que es donde se comprende y se
 * resuelve. La fila identifica; el Radar resuelve. Por eso **la fila no lleva
 * CTA**: llevaría el mismo botón que la recomendación y competirían.
 *
 * ## Lo que la pantalla decide y lo que no
 *
 * Decide cuántos caben, cuándo se filtra y en qué orden se dibuja. **No decide
 * nada más**: el orden del foco, el lado, el paso, la prioridad, el estado de
 * cada hecho, el expediente, el contraste y el ritmo llegan resueltos del
 * dominio. Es la regla de E1, y aquí no se negocia — si hiciera falta una cifra
 * que el cable no trae, la respuesta correcta es añadirla al backend.
 *
 * ## Sin selector de periodo
 *
 * El anterior tenía uno. Ya no gobierna nada de lo que se ve: el foco sale de
 * la bandeja (que no tiene ventana) y el pie mide contra un **mes de
 * calendario**, que es otra cosa distinta de la ventana móvil. Un control que
 * no cambia nada es peor que su ausencia; el periodo sigue viajando en la
 * llamada con su valor por defecto para el resto de agregados.
 *
 * ## Tres formas del cable que hay que respetar
 *
 * - **Solo el AGENTE tiene bandeja.** Para BROKER y ADMIN llega vacía y **eso
 *   no es un error**: el broker recibe sus propios asuntos (`focoDelBroker`) y
 *   el administrador, ninguno — desde D-F4-5 no decide operaciones
 *   comerciales, y un Inicio con asuntos que no puede resolver sería un tablero
 *   de control.
 * - **La bandeja no tiene tope.** `totalRecords` es el total real; el foco
 *   compone las cinco primeras y el resto vive en la cola.
 * - **Cancelar una tarea es definitivo**: `CANCELADA` impide que el
 *   reconciliador la vuelva a crear para esa entidad.
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    BarraFiltros,
    DialogoConfirmacion,
    EstadoListado,
    Icono,
    MallaBrox,
    PanelLateral,
    Radar,
    RouterLink,
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
  private readonly router = inject(Router);

  protected readonly indicadores = signal<IndicadoresResumen | null>(null);
  protected readonly bandeja = signal<Tarea[]>([]);
  protected readonly hallazgosCrudos = signal<Hallazgo[]>([]);
  protected readonly asuntosDelBroker = signal<AsuntoDelFoco[]>([]);
  protected readonly accesosCrudos = signal<AccesoRapido[]>([]);

  /** Los accesos con su icono. La etiqueta y la ruta siguen siendo del dominio. */
  protected readonly accesos = computed(() =>
    this.accesosCrudos().map((acceso) => ({ ...acceso, ...ICONO_DE_ACCESO[acceso.destino] })),
  );
  protected readonly totalBandeja = signal(0);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Qué asunto tiene abierto el Radar. `null` es el modo general. */
  protected readonly seleccionado = signal<string | null>(null);
  protected readonly filtro = signal<FiltroLado>('');

  // --- Panel de la cola completa -----------------------------------------
  protected readonly panelAbierto = signal(false);
  protected readonly bandejaTodas = signal<Tarea[]>([]);
  protected readonly cargandoTodas = signal(false);
  protected readonly errorTodas = signal<string | null>(null);
  protected readonly filtroPrioridad = signal<FiltroPrioridad>('TODAS');
  protected readonly busquedaTareas = signal('');
  protected readonly porCancelar = signal<Tarea | null>(null);
  protected readonly cancelando = signal(false);
  protected readonly avisoBandeja = signal<string | null>(null);

  private readonly rol = computed<RolSesion | undefined>(() => this.auth.sesion()?.rol);
  protected readonly esAgente = computed(() => this.rol() === 'AGENTE');
  protected readonly esBroker = computed(() => this.rol() === 'BROKER');
  protected readonly esAdmin = computed(() => this.rol() === 'TENANT_ADMIN');

  protected readonly avanceDe = avanceDe;
  protected readonly marcaEsperadaDe = marcaEsperadaDe;
  protected readonly cifraDe = cifraDe;

  // ==================================================================
  // La cabecera del día
  // ==================================================================

  protected readonly nombre = computed(() => this.auth.sesion()?.nombre?.split(/\s+/)[0] ?? '');

  protected readonly saludo = computed(() => {
    const nombre = this.nombre();
    return nombre ? `Buen día, ${nombre}` : 'Buen día';
  });

  /**
   * La fecha sale de **`generadoEn`**, no del reloj del navegador.
   *
   * Es el mismo criterio que `frescuraDe`: el instante lo declara el backend y
   * tiene un solo productor en todo el sistema. Una pestaña abierta desde ayer
   * diría hoy mirando su propio reloj.
   */
  protected readonly fecha = computed(() => {
    const fecha = comoFecha(this.indicadores()?.rendimiento?.generadoEn);
    return fecha ? FECHA_LARGA.format(fecha) : '';
  });

  /**
   * La pastilla del día. El vocabulario y su porqué viven en
   * `core/estado-del-dia.ts`: es el mismo para agente y para broker.
   */
  protected readonly pastilla = computed(() =>
    estadoDelDia(this.totalAsuntos(), this.indicadores()?.senales ?? []),
  );

  /**
   * Cuántos asuntos dependen de ti. **No es `pendientesDeAtencion`**: aquel
   * cuenta cosas del resumen de indicadores y aquí se cuenta la misma
   * colección que alimenta el foco y la cola. Mezclarlas dejaría el titular
   * diciendo «8 asuntos» sobre una cola de 12.
   */
  protected readonly totalAsuntos = computed(() =>
    this.esAgente() ? this.totalBandeja() : this.asuntosDelBroker().length,
  );

  /**
   * La segunda línea del titular: **por dónde empezar y qué falta ahí**.
   *
   * La frase la escribe el dominio (es el hecho `FALTA` del primer asunto), no
   * esta pantalla. Sin ella, el titular se queda en una línea: inventar un
   * motivo sería explicar un ranking que no conocemos.
   */
  protected readonly propuesta = computed(() => {
    const primero = this.foco()[0];
    if (!primero) {
      return null;
    }
    const falta = primero.interpretacion?.comoEsta?.hechos?.find((h) => h.estado === 'FALTA');
    return falta?.texto ?? primero.titulo;
  });

  // ==================================================================
  // El foco: hasta cinco, ya ordenados por el dominio
  // ==================================================================

  /**
   * Los asuntos, vengan de donde vengan.
   *
   * **El orden se recorre, no se calcula.** `GET /tareas` y el foco del broker
   * llegan ordenados por la política de despacho del dominio, y su contrato
   * dice que la pantalla filtra pero «no reclasifica ni reordena».
   */
  protected readonly asuntos = computed<AsuntoDelFoco[]>(() =>
    this.esAgente() ? this.bandeja().map(desdeTarea) : this.asuntosDelBroker(),
  );

  protected readonly foco = computed(() => this.asuntos().slice(0, MAXIMO_FOCOS));

  /** Cuántos hay de cada lado. Sin los dos, el filtro no se ofrece. */
  protected readonly porLado = computed(() => {
    const cuenta = { OFERTA: 0, DEMANDA: 0 };
    for (const asunto of this.foco()) {
      if (asunto.lado === 'OFERTA' || asunto.lado === 'DEMANDA') {
        cuenta[asunto.lado]++;
      }
    }
    return cuenta;
  });

  protected readonly hayFiltroDeLado = computed(
    () => this.porLado().OFERTA > 0 && this.porLado().DEMANDA > 0,
  );

  /**
   * Lo que se ve, con su número.
   *
   * **El número sale de la posición en el foco completo, no en el filtrado.**
   * Renumerar al filtrar diría que BROX cambió la prioridad, y no la cambió
   * (D-E2-1 §7.0.f).
   */
  protected readonly focoVisible = computed(() => {
    const filtro = this.filtro();
    return this.foco()
      .map((asunto, i) => ({ asunto, numero: `0${i + 1}`.slice(-2) }))
      .filter((fila) => !filtro || fila.asunto.lado === filtro);
  });

  protected readonly asuntoAbierto = computed(
    () => this.asuntos().find((a) => a.id === this.seleccionado()) ?? null,
  );

  /** Los que no caben en el foco. La cola es una banda, no otra tabla. */
  protected readonly enCola = computed(() =>
    Math.max(0, this.totalAsuntos() - this.foco().length),
  );

  // ==================================================================
  // El Radar
  // ==================================================================

  protected readonly vigila = computed(() => {
    const abiertas = this.indicadores()?.oportunidadesActivas ?? 0;
    if (abiertas === 0) {
      return 'Sin operaciones abiertas ahora mismo';
    }
    return abiertas === 1 ? 'Vigilando 1 operación abierta' : `Vigilando ${abiertas} operaciones abiertas`;
  });

  /**
   * Los hallazgos, con lo único que la pantalla les añade: **si ya están en el
   * foco**.
   *
   * Es la regla del hogar único (D-E2-1 §11): lo que ya tiene un número en la
   * cola no vuelve a ser tarjeta en el Radar, queda el puntero a su número. Se
   * emparejan por el código de la captación, que es el único identificador que
   * comparten los dos lados del cable.
   */
  protected readonly hallazgos = computed<HallazgoEnRadar[]>(() => {
    const numeroPorCodigo = new Map<string, string>();
    this.foco().forEach((asunto, i) => {
      const codigo = asunto.tarea?.entidadCodigo;
      if (codigo) {
        numeroPorCodigo.set(codigo, `0${i + 1}`.slice(-2));
      }
    });

    // AGRUPADOS POR LOCAL. El motor evalúa cada cliente contra cada captación,
    // así que un local que encaja con doce clientes llega como doce hallazgos
    // con el mismo título. En pantalla eso era la misma dirección repetida doce
    // veces —y con dos locales así, veintidós filas que deformaban la página—.
    // El hecho no son doce hallazgos: es un local que encaja con doce clientes.
    const porLocal = new Map<string, HallazgoEnRadar>();
    for (const hallazgo of this.hallazgosCrudos()) {
      const clave = hallazgo.codigoCaptacion ?? `${hallazgo.idCaptacion ?? hallazgo.id}`;
      const grupo = porLocal.get(clave);
      if (grupo) {
        grupo.clientes++;
        (grupo.detalle as Hallazgo[]).push(hallazgo);
        continue;
      }
      porLocal.set(clave, {
        ...hallazgo,
        posicion: hallazgo.codigoCaptacion
          ? (numeroPorCodigo.get(hallazgo.codigoCaptacion) ?? null)
          : null,
        clientes: 1,
        detalle: [hallazgo],
      });
    }
    return [...porLocal.values()];
  });

  /** La lista entera de hallazgos, uno por cliente, para el panel. */
  protected readonly panelHallazgos = signal(false);

  protected readonly hallazgosDelPanel = computed(() =>
    this.hallazgos().flatMap((grupo) =>
      grupo.detalle.map((hallazgo) => ({ hallazgo, local: grupo.titulo })),
    ),
  );

  protected abrirHallazgos(): void {
    this.panelHallazgos.set(true);
  }

  /** Abrir desde el panel lo cierra: se va a otra pantalla, no se vuelve a él. */
  protected async abrirDesdePanel(destino: string): Promise<void> {
    this.panelHallazgos.set(false);
    await this.abrir(destino);
  }

  // ==================================================================
  // El pie: anticipo de Indicadores (D-E2-1 §6.2)
  // ==================================================================
  //
  // NO calcula nada de negocio, y ni siquiera redacta: las frases salen de
  // `core/rendimiento.ts`, el MISMO módulo que usa la pantalla de Indicadores.
  // Por construcción no pueden contradecirse.

  protected readonly rendimiento = computed(() => this.indicadores()?.rendimiento ?? null);
  protected readonly kpisDelPie = computed(() => this.rendimiento()?.kpis ?? []);
  protected readonly cierreDelMes = computed(() => cierreLegible(this.rendimiento()));
  protected readonly calculadoHace = computed(() => frescuraDe(this.rendimiento()));

  /** La voz cambia por rol; los números no. */
  protected lecturaDe(kpi: KpiCanonico): string {
    return lecturaDe(kpi, this.esAgente());
  }

  protected colorDeRitmo(kpi: KpiCanonico): string {
    return COLOR_RITMO[kpi.estadoRitmo] ?? 'var(--ink-3)';
  }

  /**
   * El pulso del equipo: la **distribución**, no el total (D-E2-2 §6.1).
   *
   * Sin nombres — la instrucción 13 prohíbe el ranking — y solo del broker: para
   * un agente sería su propio ritmo contado otra vez. Los grupos vacíos no se
   * enseñan: «0 fuera de ritmo» ocupa lo mismo que un dato y no lo es.
   */
  protected readonly pulso = computed(() => {
    const pulso = this.rendimiento()?.pulso;
    if (!pulso) {
      return [];
    }
    return [
      { n: pulso.enRitmo, voz: vozDelRitmo('EN_RITMO'), color: COLOR_RITMO['EN_RITMO'] },
      { n: pulso.atencion, voz: vozDelRitmo('ATENCION'), color: COLOR_RITMO['ATENCION'] },
      { n: pulso.fueraDeRitmo, voz: vozDelRitmo('FUERA_DE_RITMO'), color: COLOR_RITMO['FUERA_DE_RITMO'] },
      { n: pulso.sinBase, voz: 'sin meta fijada', color: COLOR_RITMO['SIN_BASE'] },
    ].filter((grupo) => grupo.n > 0);
  });

  // ==================================================================
  // Carga
  // ==================================================================

  ngOnInit(): void {
    void this.cargar();
  }

  /** Refresca en cada entrada: el Inicio es lo primero que envejece. */
  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    this.avisoBandeja.set(null);
    try {
      const carga = await this.api.cargar(PERIODO_POR_DEFECTO, MAXIMO_FOCOS);
      this.indicadores.set(carga.indicadores);
      this.bandeja.set(carga.bandeja.items ?? []);
      this.totalBandeja.set(carga.bandeja.totalRecords ?? 0);
      this.hallazgosCrudos.set(carga.hallazgos ?? []);
      this.asuntosDelBroker.set((carga.focoDelBroker ?? []).map(desdeAsuntoDelBroker));
      this.accesosCrudos.set(carga.accesos ?? []);
      // Una recarga no debe dejar el Radar abierto sobre un asunto que ya no
      // está: eso es lo que produce una resolución sobre datos de hace un rato.
      if (!this.asuntos().some((a) => a.id === this.seleccionado())) {
        this.seleccionado.set(null);
      }
    } catch (fallo) {
      this.indicadores.set(null);
      this.error.set(fallo instanceof ApiError ? fallo.message : 'No se pudo cargar el Inicio.');
    } finally {
      this.cargando.set(false);
    }
  }

  // ==================================================================
  // Interacción
  // ==================================================================

  protected seleccionar(id: string): void {
    this.seleccionado.set(this.seleccionado() === id ? null : id);
  }

  protected filtrarLado(lado: FiltroLado): void {
    this.filtro.set(lado);
  }

  /**
   * Abre un destino del cable.
   *
   * **Pasa siempre por el traductor.** Las rutas de las tareas del agente son
   * las del legado (`solicitud-detail/12`, `visitas?focus=3`) y las del broker y
   * los hallazgos son rutas reales del SPA; navegar a las primeras tal cual
   * llevaba a una pantalla que no existe. Lo que el traductor no sabe resolver
   * se intenta como ruta directa, y si tampoco existe **se dice** — no se
   * inventa un destino.
   */
  protected async abrir(destino: string): Promise<void> {
    if (this.navegacion.puedeAbrir(destino)) {
      const abierta = await this.navegacion.abrir(destino);
      if (abierta) {
        return;
      }
    }
    const llego = await this.router.navigateByUrl(`/${destino}`);
    if (!llego) {
      this.avisoBandeja.set('Eso no tiene todavía una pantalla a la que llevarte.');
    }
  }

  /**
   * Resolver: el botón del Radar.
   *
   * Las tareas del agente traen rutas **del legado** (`solicitud-detail/12`,
   * `visitas?focus=3`) y pasan por el traductor; el resto ya viaja con rutas
   * reales del SPA. Un destino que no se sabe traducir **se dice**, no se
   * inventa.
   */
  protected async resolver(asunto: AsuntoDelFoco): Promise<void> {
    if (!asunto.destino) {
      return;
    }
    if (asunto.tarea) {
      const abierta = await this.navegacion.abrir(asunto.destino);
      if (!abierta) {
        this.avisoBandeja.set('Esa tarea no tiene una pantalla a la que llevarte todavía.');
      }
      return;
    }
    await this.abrir(asunto.destino);
  }

  /**
   * Se resolvió sin salir del Inicio.
   *
   * **Se recarga entero, no se quita la fila.** La bandeja se reconcilia al
   * leerla: cerrar una visita puede cerrar su tarea, destapar la sexta de la
   * cola y mover los indicadores del pie a la vez. Borrar la fila en el cliente
   * dejaría los otros tres desfasados sobre la misma pantalla.
   */
  protected async trasResolver(hecho: string): Promise<void> {
    this.seleccionado.set(null);
    this.avisoBandeja.set(hecho);
    await this.cargar();
    // `cargar` limpia los avisos, así que el acuse se repone después: es lo
    // único que le queda al agente para saber que su clic hizo algo.
    this.avisoBandeja.set(hecho);
  }

  // --- La cola completa ---------------------------------------------------

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
    // No con una confirmación abierta encima: el ESC del panel escucha en el
    // documento, y sin esto un ESC sobre «¿cancelar esta tarea?» se llevaba por
    // delante el panel y dejaba el diálogo flotando solo.
    if (this.porCancelar() !== null) {
      return;
    }
    this.panelAbierto.set(false);
  }

  /**
   * La lectura que reconcilia (`GET /tareas` escribe). Refresca de paso las del
   * foco: son un prefijo de la misma lista, y dejarlas desfasadas mientras el
   * panel muestra otra cosa es peor que la llamada.
   */
  protected async recargarTodas(): Promise<void> {
    this.cargandoTodas.set(true);
    this.errorTodas.set(null);
    try {
      const todas = await this.tareas.bandeja();
      this.bandejaTodas.set(todas);
      this.bandeja.set(todas.slice(0, MAXIMO_FOCOS));
      this.totalBandeja.set(todas.length);
      const prioridad = this.filtroPrioridad();
      if (prioridad !== 'TODAS' && !todas.some((t) => t.prioridad === prioridad)) {
        this.filtroPrioridad.set('TODAS');
      }
    } catch (fallo) {
      this.bandejaTodas.set([]);
      this.errorTodas.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cargar la cola completa.',
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
  protected readonly tareasFiltradas = computed<AsuntoDelFoco[]>(() => {
    const prioridad = this.filtroPrioridad();
    const texto = this.busquedaTareas().trim().toLowerCase();
    return this.bandejaTodas()
      .filter((tarea) => {
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
      })
      .map(desdeTarea);
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

  /** Traer un asunto de la cola lo abre en el Radar; el foco no se reordena. */
  protected async traerDeLaCola(asunto: AsuntoDelFoco): Promise<void> {
    this.panelAbierto.set(false);
    await this.resolver(asunto);
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
      // cancelar la 3.ª destapa la 6.ª en el foco, y esa no estaba descargada.
      await this.recargarTodas();
      if (!this.asuntos().some((a) => a.id === this.seleccionado())) {
        this.seleccionado.set(null);
      }
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
}
