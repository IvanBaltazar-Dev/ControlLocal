import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import {
  AsuntoDelBroker,
  DashboardCarga,
  DashboardService,
  Hallazgo,
} from '../../core/api/dashboard.service';
import { IndicadoresResumen } from '../../core/api/indicadores.service';
import { RenglonExpediente, Tarea, TareasService } from '../../core/api/tareas.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { InteraccionesService } from '../../core/api/interacciones.service';
import { VisitasService } from '../../core/api/visitas.service';
import { NavegacionLegado } from '../../core/navegacion-legado';
import { Dashboard } from './dashboard';

const INDICADORES: IndicadoresResumen = {
  ambito: 'Mi actividad',
  captacionesPorRevisar: 2,
  solicitudesPorEvaluar: 3,
  captacionesTotales: 10,
  captacionesActivas: 6,
  captacionesObservadas: 1,
  oportunidadesActivas: 4,
  interacciones: 12,
  visitas: 5,
  cierres: 2,
  cierresCohorte: 2,
  conversionPropia: 20,
  agentesActivos: 3,
  brokersActivos: 1,
  propiedadesEquipo: 7,
  mesesEtiquetas: ['Jul 26'],
  cierresPorMes: [2],
  conversionPorPeriodo: [20],
  captacionesPorPeriodo: [10],
  etapas: [
    { nombre: 'Captacion activa', valor: 3 },
    { nombre: 'Clientes interesados', valor: 2 },
    { nombre: 'Con solicitud', valor: 1 },
    { nombre: 'En evaluacion', valor: 1 },
    { nombre: 'Alquilada', valor: 2 },
  ],
  captacionesSalud: [
    { nombre: 'Activas', valor: 6 },
    { nombre: 'Por revisar', valor: 2 },
    { nombre: 'Observadas', valor: 1 },
    { nombre: 'Bloqueadas/cerradas', valor: 1 },
  ],
  embudo: [{ etapa: 'Oportunidades activas', valor: 0, porcentaje: 100 }],
  desempeno: [{ nombre: 'Ana Perez', captaciones: 4, cierres: 1, conversion: 25 }],
  operativo: {
    recontactosVencidos: 2,
    recontactosAlDia: 5,
    diasPromedioSinSeguimiento: 9,
    visitasPendientes: 3,
    solicitudesSinCierre: 1,
    conversionProspeccionCaptacion: 40,
  },
  // Ya clasificadas por el backend (E1). El Inicio ya no las dibuja —el foco
  // sale de la bandeja y del foco del broker—, pero siguen viajando.
  senales: [
    {
      concepto: 'SOLICITUD_POR_EVALUAR',
      valor: 3,
      nivelAtencion: 'ALTO',
      requiereAtencion: true,
      prioridad: 1,
    },
    {
      concepto: 'DEMORA_DE_SEGUIMIENTO',
      valor: 9,
      nivelAtencion: 'MEDIO',
      requiereAtencion: true,
      prioridad: 5,
    },
  ],
  pendientesDeAtencion: 8,
  /**
   * El bloque de E2.6. Los cuatro KPI van con el rótulo del backend —**la
   * pantalla no los escribe**— y con casos distintos a propósito: uno en ritmo,
   * uno en atención, uno fuera y uno sin meta, que es la combinación que rompe
   * si alguien vuelve a suponer que todos traen meta.
   */
  rendimiento: {
    periodo: {
      codigo: '2026-08',
      desde: '2026-08-01',
      hasta: '2026-08-31',
      diasTranscurridos: 19,
      diasTotales: 31,
      enCurso: true,
    },
    generadoEn: '2026-08-19T12:00:00Z',
    kpis: [
      {
        codigo: 'C',
        rotulo: 'Propietarios contactados',
        hecho: 'prospeccion con fecha de contacto dentro del mes',
        actual: 19,
        metaPeriodo: 24,
        metaEsperadaAHoy: 15,
        porcentajeMeta: 79,
        faltante: 5,
        proyeccionCierre: 31,
        porcentajeProyectado: 129,
        estadoRitmo: 'EN_RITMO',
        motivoSinBase: 'NINGUNO',
        sinCadencia: false,
        variacionComparable: 4,
      },
      {
        codigo: 'P',
        rotulo: 'Propiedades captadas',
        hecho: 'transicion de captacion a ACTIVA dentro del mes',
        actual: 9,
        metaPeriodo: 15,
        metaEsperadaAHoy: 9,
        porcentajeMeta: 60,
        faltante: 6,
        proyeccionCierre: 14,
        porcentajeProyectado: 98,
        estadoRitmo: 'ATENCION',
        motivoSinBase: 'NINGUNO',
        sinCadencia: false,
        variacionComparable: -1,
      },
      {
        codigo: 'S',
        rotulo: 'Solicitudes ingresadas',
        hecho: 'solicitud registrada dentro del mes',
        actual: 2,
        metaPeriodo: 8,
        metaEsperadaAHoy: 5,
        porcentajeMeta: 25,
        faltante: 6,
        proyeccionCierre: 3,
        porcentajeProyectado: 41,
        estadoRitmo: 'FUERA_DE_RITMO',
        motivoSinBase: 'NINGUNO',
        sinCadencia: false,
        variacionComparable: 0,
      },
      {
        // Sin meta: los seis derivados NO viajan. El backend omite los nulos,
        // así que llegan como `undefined` y no como `null`.
        codigo: 'F',
        rotulo: 'Contratos firmados',
        hecho: 'contrato con fecha de cierre dentro del mes',
        actual: 4,
        estadoRitmo: 'SIN_BASE',
        motivoSinBase: 'SIN_META',
        sinCadencia: false,
      },
    ],
    puedeCerrarse: {
      operaciones: 2,
      importe: 12000,
      moneda: 'PEN',
      variasMonedas: false,
      esperanDecision: 1,
    },
    pulso: null,
  },
};

const TAREA: Tarea = {
  id: 1,
  tipo: 'RECONTACTO',
  entidadTipo: 'PROSPECCION',
  entidadId: 7,
  entidadCodigo: 'PRO-0002',
  rutaResolver: 'prospeccion-detail/7',
  descripcion: 'Recontacta la prospeccion PRO-0002.',
  estado: 'PENDIENTE',
  prioridad: 'ALTA',
  diasSinAccion: 34,
  lado: 'OFERTA',
  paso: 'PROSPECCION',
};

/** Un asunto del cliente, para que el foco tenga los dos lados. */
const TAREA_DEMANDA: Tarea = {
  ...TAREA,
  id: 90,
  tipo: 'VISITA',
  entidadTipo: 'VISITA',
  entidadId: 90,
  entidadCodigo: 'VIS-0090',
  rutaResolver: 'visitas?focus=90',
  descripcion: 'Registra el resultado de la visita.',
  prioridad: 'MEDIA',
  lado: 'DEMANDA',
  paso: 'VISITA',
};

const ASUNTO_DEL_BROKER: AsuntoDelBroker = {
  id: 'CAPTACION:12:BROKER',
  tipo: 'CAPTACION_POR_REVISAR',
  entidadTipo: 'CAPTACION',
  entidadId: 12,
  entidadCodigo: 'CAP-0012',
  destino: 'captaciones/pendientes',
  diasEsperando: 4,
  lado: 'OFERTA',
  paso: 'CAPTACION',
  interpretacion: {
    comoEsta: {
      avance: null,
      hechos: [
        { estado: 'FALTA', texto: 'Falta tu decision sobre la captacion' },
        { estado: 'DATO', texto: 'Esperando desde hace 4 dias' },
        { estado: 'FRENO', texto: 'El local no se puede ofrecer hasta que la apruebes' },
      ],
    },
    expediente: [],
    lectura: null,
  },
};

const HALLAZGO: Hallazgo = {
  id: 'COINCIDENCIA_DE_CARTERA:5:12',
  tipo: 'COINCIDENCIA_DE_CARTERA',
  titulo: 'Av. Arequipa 1840',
  porQue: 'Cruza 4 de 5 criterios; queda fuera en el metraje.',
  puntaje: 82,
  cumple: ['zona', 'renta', 'giro', 'plazo'],
  noCumple: ['metraje'],
  destino: 'cliente-detail/5',
  idCliente: 5,
  idCaptacion: 12,
  codigoCaptacion: 'CAP-0012',
};

/** Bandeja de `total` tareas alternando prioridades, para probar la cola. */
function bandejaDe(total: number): Tarea[] {
  const prioridades = ['ALTA', 'MEDIA', 'ALTA', 'BAJA'];
  return Array.from({ length: total }, (_, indice) => {
    const codigo = `PRO-${String(indice + 1).padStart(4, '0')}`;
    return {
      ...TAREA,
      id: indice + 1,
      entidadCodigo: codigo,
      descripcion: `Recontacta la prospeccion ${codigo}.`,
      prioridad: prioridades[indice % prioridades.length],
    };
  });
}

function carga(parcial: Partial<DashboardCarga> = {}): DashboardCarga {
  return {
    indicadores: INDICADORES,
    bandeja: { items: [TAREA], totalRecords: 3, page: 1, pageSize: 5 },
    // Sin hallazgos por defecto: un hallazgo NO es una tarea (E2.3). Quien
    // pruebe la superficie del Radar los pasa por `parcial`.
    hallazgos: [],
    // Y sin asuntos de broker: la bandeja del agente no es la del broker
    // (D-E2-5).
    focoDelBroker: [],
    accesos: [],
    ...parcial,
  };
}

function sesion(rol: RolSesion): Sesion {
  return {
    token: 't',
    expiraEnSegundos: 3600,
    rol,
    idUsuario: 1,
    idDominio: 28,
    nombre: 'Valentina Mora',
    usuario: 'vmora',
    expiraEn: '2026-12-31T00:00:00',
  };
}

describe('Inicio', () => {
  let api: jasmine.SpyObj<DashboardService>;
  let tareas: jasmine.SpyObj<TareasService>;
  let navegacion: jasmine.SpyObj<NavegacionLegado>;
  let visitas: jasmine.SpyObj<VisitasService>;
  let interacciones: jasmine.SpyObj<InteraccionesService>;
  let fixture: ComponentFixture<Dashboard>;

  function raiz(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function montar(rol: RolSesion = 'AGENTE'): Promise<Dashboard> {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: DashboardService, useValue: api },
        { provide: TareasService, useValue: tareas },
        { provide: NavegacionLegado, useValue: navegacion },
        { provide: VisitasService, useValue: visitas },
        { provide: InteraccionesService, useValue: interacciones },
        { provide: AuthService, useValue: { sesion: signal(sesion(rol)) } },
        // Router de verdad: el pie y los accesos son `routerLink`, y un espía
        // sin `createUrlTree` los rompe al pintar.
        provideRouter([]),
        // La ventana rápida del Radar inyecta los servicios de visitas e
        // interacciones; sin cliente HTTP el Radar no llega ni a pintarse.
        provideHttpClient(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  /** Abre un asunto en el Radar y deja el DOM pintado. */
  async function abrirEnElRadar(id: string): Promise<void> {
    fixture.componentInstance['seleccionar'](id);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<DashboardService>('DashboardService', ['cargar']);
    api.cargar.and.resolveTo(carga());
    tareas = jasmine.createSpyObj<TareasService>('TareasService', ['bandeja', 'cancelar']);
    tareas.bandeja.and.resolveTo([TAREA]);
    tareas.cancelar.and.resolveTo(undefined);
    navegacion = jasmine.createSpyObj<NavegacionLegado>('NavegacionLegado', [
      'abrir',
      'puedeAbrir',
    ]);
    navegacion.puedeAbrir.and.returnValue(true);
    navegacion.abrir.and.resolveTo(true);
    visitas = jasmine.createSpyObj<VisitasService>('VisitasService', ['realizar', 'noRealizada']);
    visitas.realizar.and.resolveTo({} as never);
    visitas.noRealizada.and.resolveTo({} as never);
    interacciones = jasmine.createSpyObj<InteraccionesService>('InteraccionesService', ['registrar']);
    interacciones.registrar.and.resolveTo({} as never);
  });

  // ==================================================================
  // La carga
  // ==================================================================

  /**
   * El punto del contrato E4: indicadores, bandeja, hallazgos y accesos llegan
   * juntos. Pedirlos por separado sería volver al doble round-trip que el
   * endpoint existe para evitar.
   */
  it('carga el Inicio entero con UNA llamada', async () => {
    await montar();

    expect(api.cargar).toHaveBeenCalledOnceWith('6m', 5);
    expect(tareas.bandeja).not.toHaveBeenCalled();
  });

  it('un fallo de la llamada deja la pantalla en error, no a medias', async () => {
    api.cargar.and.rejectWith(new Error('boom'));
    const inicio = await montar();

    expect(inicio['indicadores']()).toBeNull();
    expect(inicio['error']()).toBeTruthy();
  });

  // ==================================================================
  // EL FOCO · hasta cinco, y sin CTA en la fila
  // ==================================================================

  /**
   * **Nunca se rellena hasta cinco.** Con dos accionables se ven dos, y con
   * treinta se ven cinco: el resto vive en la cola (D-E2-1 §5).
   */
  it('el foco corta en cinco y con dos accionables ensena dos', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: bandejaDe(9), totalRecords: 9, page: 1, pageSize: 5 } }),
    );
    const inicio = await montar();
    expect(raiz().querySelectorAll('.foco .fila').length).toBe(5);

    TestBed.resetTestingModule();
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: bandejaDe(2), totalRecords: 2, page: 1, pageSize: 5 } }),
    );
    await montar();
    expect(raiz().querySelectorAll('.foco .fila').length).toBe(2);
    expect(inicio).toBeTruthy();
  });

  /**
   * La fila IDENTIFICA y SELECCIONA. Un botón aquí competiría con el del Radar
   * — es la regla de D-E2-1 §5 y por eso viaja con su prueba.
   */
  it('ninguna fila del foco lleva boton de accion', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: bandejaDe(5), totalRecords: 5, page: 1, pageSize: 5 } }),
    );
    await montar();

    for (const fila of Array.from(raiz().querySelectorAll('.foco .fila'))) {
      expect(fila.querySelector('button')).toBeNull();
      expect(fila.querySelector('a')).toBeNull();
    }
  });

  /**
   * Ninguna etiqueta de clasificación en el foco: se enseña el HECHO, y la
   * clasificación queda en el tono del número, que es una marca mínima.
   */
  it('el foco no lleva etiquetas de clasificacion', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: bandejaDe(5), totalRecords: 5, page: 1, pageSize: 5 } }),
    );
    await montar();
    const texto = raiz().querySelector('.foco')?.textContent?.toUpperCase() ?? '';

    for (const etiqueta of ['REQUIERE ATENCIÓN', 'AVANCE', 'ESPERA', 'PRIORIDAD ALTA']) {
      expect(texto).not.toContain(etiqueta);
    }
  });

  /**
   * **El orden se recorre, no se calcula.** La bandeja llega ordenada por la
   * política de despacho del dominio, y la pantalla la dibuja tal cual.
   */
  it('el foco respeta el orden que llega del dominio', async () => {
    const bandeja = bandejaDe(5);
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: bandeja, totalRecords: 5, page: 1, pageSize: 5 } }),
    );
    await montar();
    const titulos = Array.from(raiz().querySelectorAll('.foco .fila .tit')).map((t) =>
      t.textContent?.trim(),
    );

    expect(titulos).toEqual(bandeja.map((t) => t.descripcion));
  });

  /**
   * **Filtrar no renumera.** Renumerar diría que BROX cambió la prioridad, y no
   * la cambió (D-E2-1 §7.0.f).
   */
  it('filtrar por Cliente no cambia los numeros de las filas visibles', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: {
          items: [TAREA, { ...TAREA, id: 2 }, TAREA_DEMANDA],
          totalRecords: 3,
          page: 1,
          pageSize: 5,
        },
      }),
    );
    const inicio = await montar();

    inicio['filtrarLado']('DEMANDA');
    fixture.detectChanges();
    const numeros = Array.from(raiz().querySelectorAll('.foco .fila .num')).map((n) =>
      n.textContent?.trim(),
    );

    expect(numeros).toEqual(['03']);
  });

  /** Un botón que no separa nada estorba: sin los dos lados no hay filtros. */
  it('los filtros de lado solo aparecen si hay de los dos', async () => {
    await montar();
    expect(raiz().querySelector('.filtros')).toBeNull();

    TestBed.resetTestingModule();
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [TAREA, TAREA_DEMANDA], totalRecords: 2, page: 1, pageSize: 5 },
      }),
    );
    await montar();
    expect(raiz().querySelector('.filtros')).not.toBeNull();
  });

  /**
   * El broker recibe **sus** asuntos, no la bandeja del agente vista por otro
   * rol: son captaciones que aprobar y solicitudes que evaluar (D-E2-5).
   */
  it('el agente ve su bandeja y el broker ve sus propios asuntos', async () => {
    const agente = await montar('AGENTE');
    expect(agente['asuntos']().length).toBe(1);

    TestBed.resetTestingModule();
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
        focoDelBroker: [ASUNTO_DEL_BROKER],
      }),
    );
    const broker = await montar('BROKER');

    expect(broker['error']()).toBeNull();
    expect(broker['asuntos']().length).toBe(1);
    expect(raiz().querySelector('.foco .fila .tit')?.textContent).toContain(
      'Falta tu decision sobre la captacion',
    );
  });

  /**
   * Desde D-F4-5 el administrador no decide operaciones comerciales, así que su
   * Inicio llega sin asuntos — y eso **no es «día cubierto»**, es otra cosa.
   */
  it('el administrador no recibe un dia cubierto que no es suyo', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 } }),
    );
    await montar('TENANT_ADMIN');
    const texto = raiz().querySelector('.cubierto')?.textContent ?? '';

    expect(texto).toContain('Aquí no hay nada que decidir');
    expect(texto).not.toContain('Día cubierto');
  });

  // ==================================================================
  // LA CABECERA DEL DÍA
  // ==================================================================

  /**
   * El titular cuenta la **misma colección** que el foco y la cola. Usar
   * `pendientesDeAtencion` diría «8 asuntos» sobre una cola de 3.
   */
  it('el titular cuenta los asuntos, no los pendientes del resumen', async () => {
    await montar('AGENTE');
    const titular = raiz().querySelector('.titular')?.textContent ?? '';

    expect(titular).toContain('Hay 3 asuntos que dependen de ti');
    expect(titular).not.toContain('8');
  });

  /**
   * La segunda línea nombra lo que falta en el primero, **con la frase del
   * dominio**. Sin ella no hay segunda línea: inventar un motivo sería explicar
   * un ranking que la pantalla no conoce.
   */
  it('la propuesta sale del hecho que escribe el dominio', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
        focoDelBroker: [ASUNTO_DEL_BROKER],
      }),
    );
    await montar('BROKER');

    expect(raiz().querySelector('.titular .segunda')?.textContent).toContain(
      'Empieza por el 01: Falta tu decision sobre la captacion',
    );
  });

  it('sin asuntos no hay titular, y se ve el estado del dia cubierto', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 } }),
    );
    await montar('AGENTE');

    expect(raiz().querySelector('.titular')).toBeNull();
    expect(raiz().querySelector('.cubierto')).not.toBeNull();
  });

  /**
   * **Tres estados y el mismo vocabulario para los dos roles.** «Requiere
   * atención» no lo decide esta pantalla: aparece porque el dominio marcó
   * alguna señal como `ALTO`. Los otros dos solo distinguen si queda algo por
   * atender — ningún umbral vive aquí.
   */
  it('la pastilla dice el estado del dia con las mismas palabras para todos', async () => {
    const sinAltos = INDICADORES.senales.filter((s) => s.nivelAtencion !== 'ALTO');

    // Con una señal ALTA del dominio, aunque no quede ningún asunto propio.
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 } }),
    );
    await montar('AGENTE');
    expect(raiz().querySelector('.pastilla')?.textContent?.trim()).toBe('Requiere atención');

    // Sin señales altas y sin asuntos.
    TestBed.resetTestingModule();
    api.cargar.and.resolveTo(
      carga({
        indicadores: { ...INDICADORES, senales: sinAltos },
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
      }),
    );
    await montar('AGENTE');
    expect(raiz().querySelector('.pastilla')?.textContent?.trim()).toBe('Al día');

    // Sin señales altas pero con trabajo por delante, y el broker lo lee igual.
    TestBed.resetTestingModule();
    api.cargar.and.resolveTo(
      carga({
        indicadores: { ...INDICADORES, senales: sinAltos },
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
        focoDelBroker: [ASUNTO_DEL_BROKER],
      }),
    );
    await montar('BROKER');
    expect(raiz().querySelector('.pastilla')?.textContent?.trim()).toBe('En marcha');
  });

  /**
   * La fecha sale de `generadoEn`, que tiene un solo productor. Mirar el reloj
   * del navegador diría hoy sobre una pestaña abierta desde ayer.
   */
  it('la fecha de la cabecera sale del instante que declara el backend', async () => {
    await montar('AGENTE');

    // 19 de agosto de 2026, el `generadoEn` del fixture.
    expect(raiz().querySelector('.fecha')?.textContent).toContain('19');
    expect(raiz().querySelector('.fecha')?.textContent).toContain('agosto');
  });

  // ==================================================================
  // LA COLA Y LOS ACCESOS
  // ==================================================================

  it('la cola dice cuantos quedan fuera del foco', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: bandejaDe(5), totalRecords: 12, page: 1, pageSize: 5 } }),
    );
    await montar();

    expect(raiz().querySelector('.cola .q')?.textContent).toContain('7 siguen en tu cola');
  });

  it('sin nada fuera del foco no se pinta la banda de la cola', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [TAREA], totalRecords: 1, page: 1, pageSize: 5 } }),
    );
    await montar();

    expect(raiz().querySelector('.cola')).toBeNull();
  });

  /** Los cuatro los decide el dominio: aquí no hay `if (esBroker)`. */
  it('los accesos rapidos salen del cable, con su ruta en el href', async () => {
    api.cargar.and.resolveTo(
      carga({
        accesos: [
          { etiqueta: 'Nueva prospección', destino: 'prospecciones/nueva' },
          { etiqueta: 'Nuevo cliente', destino: 'clientes/nuevo' },
        ],
      }),
    );
    await montar();
    const accesos = Array.from(raiz().querySelectorAll('.acceso'));

    expect(accesos.map((a) => a.textContent?.trim())).toEqual([
      'Nueva prospección',
      'Nuevo cliente',
    ]);
    expect(accesos[0].getAttribute('href')).toBe('/prospecciones/nueva');
    // La ruta va en el href y NUNCA a la vista.
    expect(raiz().textContent).not.toContain('prospecciones/nueva');
  });

  // ==================================================================
  // EL RADAR · modo general
  // ==================================================================

  it('sin nada seleccionado el Radar ensena su cabecera de marca', async () => {
    await montar();

    expect(raiz().querySelector('.radar-cab .t')?.textContent?.trim()).toBe('Radar BROX');
    expect(raiz().querySelector('.radar-cab .s')?.textContent).toContain(
      'Vigilando 4 operaciones abiertas',
    );
  });

  it('sin hallazgos lo dice, en vez de dejar el hueco', async () => {
    await montar();

    expect(raiz().querySelector('.hallazgo')).toBeNull();
    expect(raiz().querySelector('.vacio-radar')?.textContent).toContain(
      'No hay ninguna coincidencia nueva',
    );
  });

  /** `porQue` llega REDACTADO del dominio: la pantalla no compone la frase. */
  it('el hallazgo destacado enseña el texto del dominio', async () => {
    api.cargar.and.resolveTo(carga({ hallazgos: [HALLAZGO] }));
    await montar();
    const hallazgo = raiz().querySelector('.hallazgo');

    expect(hallazgo?.querySelector('.q')?.textContent?.trim()).toBe('Av. Arequipa 1840');
    expect(hallazgo?.querySelector('.c')?.textContent).toContain('Cruza 4 de 5 criterios');
  });

  /**
   * **Regla del hogar único** (D-E2-1 §11): lo que ya tiene número en el foco no
   * vuelve a ser tarjeta con acción propia — queda el puntero a su número.
   */
  it('un hallazgo que ya esta en el foco apunta a su numero y no ofrece otra accion', async () => {
    const tareaDeLaCaptacion: Tarea = { ...TAREA, entidadCodigo: 'CAP-0012' };
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [tareaDeLaCaptacion], totalRecords: 1, page: 1, pageSize: 5 },
        hallazgos: [HALLAZGO],
      }),
    );
    await montar();
    const hallazgo = raiz().querySelector('.hallazgo');

    expect(hallazgo?.querySelector('.en-cola')?.textContent).toContain('Está en tu atención · 01');
    expect(hallazgo?.querySelector('.mas')).toBeNull();
  });

  it('un hallazgo que no esta en el foco si ofrece abrirlo', async () => {
    api.cargar.and.resolveTo(carga({ hallazgos: [HALLAZGO] }));
    await montar();

    expect(raiz().querySelector('.hallazgo .en-cola')).toBeNull();
    expect(raiz().querySelector('.hallazgo .mas')?.textContent).toContain('Ver la coincidencia');
  });

  // ==================================================================
  // EL RADAR · modo resolución
  // ==================================================================

  it('seleccionar una fila abre la resolucion en el Radar', async () => {
    await montar();
    await abrirEnElRadar('tarea:1');

    expect(raiz().querySelector('.radar-cab .t-caso')?.textContent).toContain(
      'Recontacta la prospeccion',
    );
    expect(raiz().querySelector('.radar-cab .t')).toBeNull();
    expect(raiz().querySelector('.fila.sel')).not.toBeNull();
  });

  /**
   * La ruta usa la cadena de SU lado y **no se solapan**: tres pasos en oferta,
   * cuatro en demanda. Y marca solo el paso actual, no el itinerario.
   */
  it('la ruta dibuja la cadena del lado del asunto', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [TAREA, TAREA_DEMANDA], totalRecords: 2, page: 1, pageSize: 5 },
      }),
    );
    await montar();

    await abrirEnElRadar('tarea:1');
    expect(raiz().querySelectorAll('.radar-caso .pasos i').length).toBe(3);
    expect(raiz().querySelector('.radar-caso .lado')?.textContent?.trim()).toBe('Propietario');
    expect(raiz().querySelector('.radar-caso .paso-actual')?.textContent?.trim()).toBe(
      'Prospección',
    );

    await abrirEnElRadar('tarea:90');
    expect(raiz().querySelectorAll('.radar-caso .pasos i').length).toBe(4);
    expect(raiz().querySelector('.radar-caso .lado')?.textContent?.trim()).toBe('Cliente');
  });

  /**
   * En el mismo asunto conviven un ✓ y un ⊘: el estado lo decide el dominio
   * hecho por hecho, y **no se hereda del tono del asunto** (D-E2-1 §10.1).
   */
  it('cada hecho lleva SU estado, no el del asunto', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
        focoDelBroker: [ASUNTO_DEL_BROKER],
      }),
    );
    await montar('BROKER');
    await abrirEnElRadar(ASUNTO_DEL_BROKER.id);
    const clases = Array.from(raiz().querySelectorAll('.ahora li')).map((li) => li.className);

    expect(clases).toContain('falta');
    expect(clases).toContain('dato');
    expect(clases.some((c) => c.includes('freno'))).toBeTrue();
  });

  /**
   * La recomendación no está en el cable, así que **no se redacta aquí**: lo
   * que se enseña son las frases que el dominio sí escribe.
   */
  it('el bloque de resolver usa las frases del dominio, no una inventada', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
        focoDelBroker: [ASUNTO_DEL_BROKER],
      }),
    );
    await montar('BROKER');
    await abrirEnElRadar(ASUNTO_DEL_BROKER.id);

    expect(raiz().querySelector('.reco .que')?.textContent?.trim()).toBe(
      'Falta tu decision sobre la captacion',
    );
    expect(raiz().querySelector('.reco .para')?.textContent?.trim()).toBe(
      'El local no se puede ofrecer hasta que la apruebes',
    );
  });

  /** «Por qué» no aparece en el Radar: BROX dice para qué, no por qué. */
  it('el Radar no explica por que', async () => {
    api.cargar.and.resolveTo(carga({ hallazgos: [HALLAZGO] }));
    await montar();
    await abrirEnElRadar('tarea:1');

    expect(raiz().querySelector('.radar')?.textContent?.toLowerCase()).not.toContain('por qué');
  });

  /** Un asunto sin expediente ni lectura no ofrece una pestaña que abre a nada. */
  it('sin antecedentes no se ofrece la pestana de antecedentes', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
        focoDelBroker: [ASUNTO_DEL_BROKER],
      }),
    );
    await montar('BROKER');
    await abrirEnElRadar(ASUNTO_DEL_BROKER.id);

    expect(raiz().querySelector('.vistas')).toBeNull();
  });

  it('avisa cuando resolver no lleva a ninguna parte, en vez de no hacer nada', async () => {
    navegacion.abrir.and.resolveTo(false);
    const inicio = await montar();

    await inicio['resolver'](inicio['asuntos']()[0]);

    expect(inicio['avisoBandeja']()).toContain('no tiene una pantalla');
  });

  // ==================================================================
  // EL EXPEDIENTE · cuatro renglones de evidencia (D-E2-1 §10.3)
  // ==================================================================

  /** Un asunto con encargo: los cuatro renglones del inmueble. */
  const EXPEDIENTE_CON_ENCARGO: RenglonExpediente[] = [
    {
      rotulo: 'Encargo',
      valor: 'Alta el 1 de agosto · vence en 165 dias',
      estado: null,
      ventana: { consumido: 15, total: 180 },
      serie: null,
    },
    {
      rotulo: 'Renta',
      valor: 'PEN 8500 · sin cambios desde hace 11 dias',
      estado: null,
      ventana: null,
      serie: [8500, 7900],
      // El caso normal mientras la cartera no tenga muestra.
      contraste: {
        forma: 'NINGUNA',
        motivo: 'SIN_REFERENCIA_INTERNA_SUFICIENTE',
        zona: 'Miraflores',
        banda: '100 a 200 m2',
        observaciones: 3,
      },
    },
    {
      rotulo: 'Actividad',
      valor: '0 visitas realizadas de 2 agendadas',
      estado: 'OJO',
      ventana: null,
      serie: null,
    },
    {
      rotulo: 'Propietario',
      valor: 'Inmobiliaria Pacifico SAC · Av. Larco 812 · Miraflores',
      estado: null,
      ventana: null,
      serie: null,
    },
  ];

  /** Una prospección: los suyos, que hablan de la prospección. */
  const EXPEDIENTE_DE_PROSPECCION: RenglonExpediente[] = [
    {
      rotulo: 'Prospección',
      valor: 'Abierta el 28 de junio · Contactado',
      estado: null,
      ventana: null,
      serie: null,
    },
    {
      rotulo: 'Contacto',
      valor: 'Sin contacto registrado',
      estado: 'OJO',
      ventana: null,
      serie: null,
    },
    {
      rotulo: 'Avance',
      valor: 'Sin reunión ni propuesta registrada',
      estado: null,
      ventana: null,
      serie: null,
    },
    {
      rotulo: 'Propietario',
      valor: 'Elena Castillo Paredes · Jr. Camana 615 · Lima',
      estado: null,
      ventana: null,
      serie: null,
    },
  ];

  function conExpediente(renglones: RenglonExpediente[]): Tarea {
    return {
      ...TAREA,
      interpretacion: {
        comoEsta: { avance: null, hechos: [] },
        expediente: renglones,
        lectura: null,
      },
    };
  }

  /** Abre el asunto y conmuta a Antecedentes, que es donde vive el expediente. */
  async function montarConExpediente(renglones: RenglonExpediente[]): Promise<void> {
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [conExpediente(renglones)], totalRecords: 1, page: 1, pageSize: 5 },
      }),
    );
    await montar('AGENTE');
    await abrirEnElRadar('tarea:1');
    const pestana = Array.from(raiz().querySelectorAll<HTMLButtonElement>('.vistas button')).find(
      (b) => b.textContent?.includes('Antecedentes'),
    );
    pestana?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function renglones(): Element[] {
    return Array.from(raiz().querySelectorAll('.ant-fila'));
  }

  it('el expediente se pinta, con sus cuatro renglones', async () => {
    await montarConExpediente(EXPEDIENTE_CON_ENCARGO);

    expect(renglones().length).toBe(4);
  });

  /**
   * Los cuatro **no son siempre los mismos**: los rótulos vienen del backend,
   * que los elige según la etapa. La pantalla no decide cuáles.
   */
  it('los rotulos salen del cable, no de la pantalla', async () => {
    await montarConExpediente(EXPEDIENTE_CON_ENCARGO);

    expect(renglones().map((r) => r.querySelector('.n')?.textContent?.trim())).toEqual([
      'Encargo',
      'Renta',
      'Actividad',
      'Propietario',
    ]);
  });

  /** Una prospección es anterior a la captación: no se le inventa un encargo. */
  it('una prospeccion pinta sus propios renglones, sin encargo ni renta', async () => {
    await montarConExpediente(EXPEDIENTE_DE_PROSPECCION);

    expect(renglones().map((r) => r.querySelector('.n')?.textContent?.trim())).toEqual([
      'Prospección',
      'Contacto',
      'Avance',
      'Propietario',
    ]);
  });

  /** Una fecha ausente se dice con palabras, nunca con un guion. */
  it('la ausencia de contacto se dice, y lleva senal', async () => {
    await montarConExpediente(EXPEDIENTE_DE_PROSPECCION);
    const contacto = renglones().find((r) => r.textContent?.includes('Contacto'));

    expect(contacto?.textContent).toContain('Sin contacto registrado');
    expect(contacto?.classList.contains('e-ojo')).toBeTrue();
  });

  /** El color lo pone el estado que decide el dominio, y solo donde lo hay. */
  it('solo los renglones con estado llevan canto de color', async () => {
    await montarConExpediente(EXPEDIENTE_CON_ENCARGO);
    const conColor = renglones().filter((r) => /\be-(bien|ojo|mal)\b/.test(r.className));

    expect(conColor.length).toBe(1);
    expect(conColor[0].textContent).toContain('Actividad');
  });

  /** Una barra sin su cifra es un adorno: la ventana lleva sus dos números. */
  it('la ventana lleva su razon al lado de la barra', async () => {
    await montarConExpediente(EXPEDIENTE_CON_ENCARGO);
    const encargo = renglones()[0];

    expect(encargo.querySelector('.ant-medida .r')?.textContent?.trim()).toBe('15/180');
    expect(encargo.querySelector('.ant-b')?.getAttribute('aria-label')).toBe('15 de 180');
  });

  /**
   * El contraste degradado **se dice con su N**. «3 propiedades en Miraflores»
   * informa; un silencio no dice si falta poco o falta todo.
   */
  it('sin muestra, el contraste dice cuantas propiedades hay y donde', async () => {
    await montarConExpediente(EXPEDIENTE_CON_ENCARGO);
    const contraste = raiz().querySelector('.vs');

    expect(contraste?.textContent).toContain('3 propiedades');
    expect(contraste?.textContent).toContain('Miraflores · 100 a 200 m2');
    expect(contraste?.textContent).toContain('pocas para un rango propio');
  });

  /** Y nunca, en ningún caso, una cifra que no salga de la casa. */
  it('el contraste no invoca al sector', async () => {
    await montarConExpediente(EXPEDIENTE_CON_ENCARGO);
    const texto = (raiz().querySelector('.radar')?.textContent ?? '').toLowerCase();

    for (const prohibida of ['sector', 'mercado nacional', 'industria', 'benchmark']) {
      expect(texto).not.toContain(prohibida);
    }
  });

  /** Sin ninguna observación el texto es otro: falta el hecho, no el volumen. */
  it('sin ninguna observacion lo dice de otra manera', async () => {
    const sinNada = EXPEDIENTE_CON_ENCARGO.map((r) =>
      r.rotulo === 'Renta'
        ? {
            ...r,
            contraste: {
              forma: 'NINGUNA' as const,
              motivo: 'SIN_OBSERVACIONES' as const,
              zona: 'Miraflores',
              banda: '100 a 200 m2',
              observaciones: 0,
            },
          }
        : r,
    );
    await montarConExpediente(sinNada);

    expect(raiz().querySelector('.vs')?.textContent).toContain(
      'Todavía sin renta publicada en Miraflores · 100 a 200 m2',
    );
  });

  /** Sin zona ni metraje no hay grupo, y entonces no se dice nada en absoluto. */
  it('sin grupo comparable no se pinta contraste', async () => {
    const sinGrupo = EXPEDIENTE_CON_ENCARGO.map((r) =>
      r.rotulo === 'Renta'
        ? {
            ...r,
            contraste: {
              forma: 'NINGUNA' as const,
              motivo: 'SIN_GRUPO_COMPARABLE' as const,
              observaciones: 0,
            },
          }
        : r,
    );
    await montarConExpediente(sinGrupo);

    expect(raiz().querySelector('.vs')).toBeNull();
  });

  // ==================================================================
  // EL PIE · anticipo de Indicadores (D-E2-1 §6.2, E2.6)
  // ==================================================================

  function pie(): HTMLElement | null {
    return raiz().querySelector('.pie');
  }

  /**
   * Los cuatro nombres van **completos y del backend**. Abreviarlos aquí
   * desharía la distinción que D-E2-2 §1.1 vino a fijar: 31 registros creados
   * no son 31 propietarios contactados.
   */
  it('el pie lleva los cuatro nombres canonicos, letra por letra y del cable', async () => {
    await montar('AGENTE');
    const nombres = Array.from(pie()?.querySelectorAll('.kpi .n') ?? []).map((n) =>
      n.textContent?.trim(),
    );

    expect(nombres).toEqual([
      'Propietarios contactados',
      'Propiedades captadas',
      'Solicitudes ingresadas',
      'Contratos firmados',
    ]);
  });

  /** Cuatro, ni uno más: el detalle vive en Indicadores, a un clic. */
  it('el pie no anade un quinto indicador', async () => {
    await montar('AGENTE');

    expect(pie()?.querySelectorAll('.kpi').length).toBe(4);
  });

  /**
   * **Sin meta no hay cero.** Pintar «19 de 0» diría «tu objetivo era cero y lo
   * cumpliste», que es lo contrario de «nadie te fijó meta».
   */
  it('un KPI sin meta ensena solo lo conseguido y dice por que no concluye', async () => {
    await montar('AGENTE');
    const contratos = Array.from(pie()?.querySelectorAll('.kpi') ?? []).find((k) =>
      k.textContent?.includes('Contratos firmados'),
    );

    expect(contratos?.querySelector('.d')?.textContent?.trim()).toBe('4');
    expect(contratos?.textContent).not.toContain('de 0');
    expect(contratos?.textContent).toContain('Sin meta fijada para este mes');
    expect(contratos?.getAttribute('data-ritmo')).toBe('SIN_BASE');
  });

  /**
   * El estado lo decide el dominio. La pantalla lo traduce a un atributo y el
   * color sale de ahí; nunca al revés.
   */
  it('el tono de cada KPI sale del estado que manda el dominio', async () => {
    await montar('AGENTE');
    const estados = Array.from(pie()?.querySelectorAll('.kpi') ?? []).map((k) =>
      k.getAttribute('data-ritmo'),
    );

    expect(estados).toEqual(['EN_RITMO', 'ATENCION', 'FUERA_DE_RITMO', 'SIN_BASE']);
  });

  /** Primero a cuánto estás de la meta, y después el ritmo. */
  it('cada KPI dice a cuanto estas de la meta antes que el ritmo', async () => {
    await montar('AGENTE');

    expect(pie()?.querySelector('.kpi .empuje')?.textContent?.trim()).toBe(
      'A 5 de la meta · hoy deberías ir por 15',
    );
  });

  /**
   * La marca del ritmo esperado es lo que hace que el semáforo se entienda: sin
   * ella, un 79 % no distingue ir por delante de ir por detrás (D-E2-2 §3).
   */
  it('la barra lleva la marca del ritmo esperado a hoy', async () => {
    await montar('AGENTE');
    const marca = pie()?.querySelector('.kpi .pista b') as HTMLElement | null;

    // 15 de meta 24 = 63 % del ancho.
    expect(marca?.style.left).toBe('63%');
  });

  /** Con meta pequeña no se prorratea, así que tampoco se dibuja la marca. */
  it('sin cadencia diaria no se dibuja marca esperada', async () => {
    await montar('AGENTE');
    const sinMeta = Array.from(pie()?.querySelectorAll('.kpi') ?? []).find((k) =>
      k.textContent?.includes('Contratos firmados'),
    );

    expect(sinMeta?.querySelector('.pista b')).toBeNull();
  });

  /**
   * **Instrucción 4 de D-E2-2**: al broker no se le atribuye una producción
   * personal que él no hace.
   */
  it('al broker no se le dice cuanto deberia llevar el: se le habla del equipo', async () => {
    await montar('BROKER');
    const texto = pie()?.textContent ?? '';

    expect(texto).not.toContain('hoy deberías ir por');
    expect(texto).toContain('el equipo');
  });

  /** Su pulso sería su propio ritmo contado otra vez (instrucción 14). */
  it('el agente no ve pulso de equipo', async () => {
    await montar('AGENTE');

    expect(pie()?.querySelector('.pulso')).toBeNull();
  });

  /** Un total en meta puede esconder a la mitad del equipo en cero. */
  it('el broker ve el pulso, una sola vez y sin nombres', async () => {
    api.cargar.and.resolveTo(
      carga({
        indicadores: {
          ...INDICADORES,
          rendimiento: {
            ...INDICADORES.rendimiento,
            pulso: { enRitmo: 6, atencion: 1, fueraDeRitmo: 1, sinBase: 0, agentes: 8 },
          },
        },
      }),
    );
    await montar('BROKER');
    const pulsos = pie()?.querySelectorAll('.pulso') ?? [];

    expect(pulsos.length).toBe(1);
    expect(pulsos[0].textContent).toContain('6 en ritmo');
    expect(pulsos[0].textContent).toContain('1 fuera de ritmo');
    // Los grupos vacíos no ocupan sitio: «0 sin meta fijada» no es un dato.
    expect(pulsos[0].textContent).not.toContain('0 sin meta');
  });

  /**
   * Cero operaciones **no se esconde**: es información, y esconderla dejaría el
   * hueco a que alguien lo leyera como un fallo de carga.
   */
  it('sin operaciones que cerrar lo dice, en vez de callarse', async () => {
    api.cargar.and.resolveTo(
      carga({
        indicadores: {
          ...INDICADORES,
          rendimiento: {
            ...INDICADORES.rendimiento,
            puedeCerrarse: {
              operaciones: 0,
              importe: 0,
              moneda: null,
              variasMonedas: false,
              esperanDecision: 0,
            },
          },
        },
      }),
    );
    await montar('AGENTE');

    expect(pie()?.querySelector('.juego')?.textContent).toContain(
      'Ninguna operación puede cerrarse este mes',
    );
  });

  /** El importe conserva su moneda: no se convierte a dólares para redondear. */
  it('la cifra en juego conserva su moneda', async () => {
    await montar('AGENTE');

    expect(pie()?.querySelector('.juego .v')?.textContent).toContain('PEN');
  });

  /**
   * `generadoEn` tiene un solo productor y esta pantalla lo **lee**. Mirar el
   * reloj del navegador diría «hace 0 min» sobre una respuesta cacheada.
   */
  it('la frescura sale del instante que declara el backend', async () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2026-08-19T12:07:00Z'));
    try {
      await montar('AGENTE');

      expect(pie()?.querySelector('.frescura')?.textContent).toContain('hace 7 min');
    } finally {
      jasmine.clock().uninstall();
    }
  });

  /** Los días del mes los cuenta el backend; la pantalla los repite. */
  it('el pie ensena el corte del mes tal como llega', async () => {
    await montar('AGENTE');

    expect(pie()?.querySelector('.cierre-mes')?.textContent).toContain('19 de 31 días del mes');
  });

  /** El enlace es la franja entera, no un botón dentro de ella. */
  it('la franja entera lleva a indicadores', async () => {
    await montar('AGENTE');

    expect(pie()?.getAttribute('href')).toBe('/indicadores');
  });

  // ==================================================================
  // LA COLA COMPLETA · el panel
  // ==================================================================

  /**
   * El backend retiró el tope de 10 (D-F7-2) y una bandeja de 32 volcada en el
   * foco estiraba la columna izquierda muy por debajo de la derecha. El foco se
   * queda en 5 **pase lo que pase** y las 32 se recorren en el panel.
   */
  it('el panel trae la cola entera sin estirar el foco', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(32));

    await agente['abrirPanel']();

    expect(agente['panelAbierto']()).toBeTrue();
    expect(agente['bandejaTodas']().length).toBe(32);
    expect(agente['foco']().length).toBe(5);
    expect(agente['totalBandeja']()).toBe(32);
  });

  /** `GET /tareas` reconcilia (escribe): cerrar y volver no puede costar otra. */
  it('cerrar el panel no vuelve a pedir la cola', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(32));
    await agente['abrirPanel']();
    tareas.bandeja.calls.reset();

    agente['cerrarPanel']();

    expect(agente['panelAbierto']()).toBeFalse();
    expect(tareas.bandeja).not.toHaveBeenCalled();
  });

  /** Convención de filtros: las cuentas salen de los datos, ninguna es fija. */
  it('los chips de prioridad cuentan sobre los datos y omiten las vacias', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo([
      { ...TAREA, id: 1, prioridad: 'ALTA' },
      { ...TAREA, id: 2, prioridad: 'ALTA' },
      { ...TAREA, id: 3, prioridad: 'MEDIA' },
    ]);

    await agente['abrirPanel']();

    expect(agente['chipsPrioridad']()).toEqual([
      jasmine.objectContaining({ valor: 'TODAS', cuenta: 3 }),
      jasmine.objectContaining({ valor: 'ALTA', cuenta: 2 }),
      jasmine.objectContaining({ valor: 'MEDIA', cuenta: 1 }),
    ]);
  });

  it('filtra por prioridad y por texto sin volver al API', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(8));
    await agente['abrirPanel']();
    tareas.bandeja.calls.reset();

    agente['filtrarPor']('ALTA');
    expect(agente['tareasFiltradas']().every((a) => a.tarea?.prioridad === 'ALTA')).toBeTrue();

    agente['limpiarFiltrosTareas']();
    agente['busquedaTareas'].set('pro-0003');
    expect(agente['tareasFiltradas']().map((a) => a.tarea?.id)).toEqual([3]);

    expect(tareas.bandeja).not.toHaveBeenCalled();
  });

  /**
   * Cancelar la última ALTA con el filtro en ALTA dejaba una lista vacía y el
   * chip al que volver ya no existía: callejón sin salida.
   */
  it('un filtro que se queda sin tareas vuelve a Todas', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo([
      { ...TAREA, id: 1, prioridad: 'ALTA' },
      { ...TAREA, id: 2, prioridad: 'MEDIA' },
    ]);
    await agente['abrirPanel']();
    agente['filtrarPor']('ALTA');

    tareas.bandeja.and.resolveTo([{ ...TAREA, id: 2, prioridad: 'MEDIA' }]);
    await agente['recargarTodas']();

    expect(agente['filtroPrioridad']()).toBe('TODAS');
    expect(agente['tareasFiltradas']().length).toBe(1);
  });

  /** El panel se queda abierto con su error reintentable; el Inicio no se cae. */
  it('un fallo de la cola completa no cierra el panel ni tumba el Inicio', async () => {
    const agente = await montar();
    tareas.bandeja.and.rejectWith(new Error('boom'));

    await agente['abrirPanel']();

    expect(agente['panelAbierto']()).toBeTrue();
    expect(agente['errorTodas']()).toBeTruthy();
    expect(agente['error']()).toBeNull();
    expect(agente['indicadores']()).not.toBeNull();
  });

  /**
   * El ESC del panel escucha en el documento, así que sin el guardado se
   * llevaba por delante el panel al pulsarlo sobre «¿cancelar esta tarea?».
   */
  it('no se cierra el panel mientras hay una confirmacion encima', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(8));
    await agente['abrirPanel']();
    agente['pedirCancelacion'](TAREA);

    agente['cerrarPanel']();

    expect(agente['panelAbierto']()).toBeTrue();
  });

  /**
   * Cancelar la 3.ª destapa la 6.ª en el foco, y esa no estaba descargada: por
   * eso se recarga la cola en vez de quitar la fila en el cliente.
   */
  it('tras cancelar recarga la cola en vez de borrar la fila', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(9));
    agente['pedirCancelacion'](TAREA);

    await agente['confirmarCancelacion']();

    expect(tareas.cancelar).toHaveBeenCalledOnceWith(1);
    expect(tareas.bandeja).toHaveBeenCalledTimes(1);
    expect(agente['porCancelar']()).toBeNull();
    expect(agente['foco']().length).toBe(5);
    expect(agente['totalBandeja']()).toBe(9);
  });

  /** `[class]` (tono) y `[class.activo]` conviven: el chip activo no pierde color. */
  it('el chip marcado conserva su tono y lo anuncia', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(8));
    await agente['abrirPanel']();
    agente['filtrarPor']('ALTA');
    fixture.detectChanges();

    const chips = raiz().querySelectorAll('.chip');
    expect(chips[0].getAttribute('aria-pressed')).toBe('false');
    expect(chips[1].classList).toContain('mal');
    expect(chips[1].classList).toContain('activo');
    expect(chips[1].getAttribute('aria-pressed')).toBe('true');
  });

  /**
   * La lista del panel es contenido **proyectado** en `cl-panel-lateral`, pero
   * su SCSS vive en el Inicio. Angular sella lo proyectado con el atributo de
   * encapsulación de quien lo declara; si eso dejara de cumplirse las filas del
   * panel se quedarían sin estilo y no lo vería ninguna prueba de lógica.
   */
  it('la lista del panel hereda la encapsulacion del Inicio', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(8));
    await agente['abrirPanel']();
    fixture.detectChanges();

    const filas = raiz().querySelectorAll('.fila-cola');
    expect(filas.length).toBe(8);

    const sello = (elemento: Element) =>
      Array.from(elemento.attributes)
        .map((atributo) => atributo.name)
        .filter((nombre) => nombre.startsWith('_ngcontent-'));
    expect(sello(filas[0]).length).toBe(1);
  });

  /** Un código de registro no se enseña como título de nada. */
  it('el titulo de un asunto no es su codigo', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 },
        focoDelBroker: [ASUNTO_DEL_BROKER],
      }),
    );
    await montar('BROKER');

    expect(raiz().querySelector('.foco .fila .tit')?.textContent).not.toContain('CAP-0012');
  });

  // ==================================================================
  // LOS HALLAZGOS NO DEFORMAN LA PANTALLA
  // ==================================================================

  /** Un local que encaja con doce clientes es UN hallazgo, no doce filas. */
  function coincidencias(locales: number, clientesPorLocal: number): Hallazgo[] {
    const lista: Hallazgo[] = [];
    for (let local = 1; local <= locales; local++) {
      for (let cliente = 1; cliente <= clientesPorLocal; cliente++) {
        lista.push({
          ...HALLAZGO,
          id: `COINCIDENCIA:${local}:${cliente}`,
          titulo: `Av. Pardo ${1000 + local}`,
          codigoCaptacion: `CAP-${String(local).padStart(4, '0')}`,
          idCaptacion: local,
          idCliente: cliente,
          porQue: `Cruza 4 de 5 criterios; queda fuera en rubro ${cliente}.`,
        });
      }
    }
    return lista;
  }

  /**
   * **El caso que deformaba la página**: dos locales por doce clientes llegaban
   * como veintidós filas con la misma dirección repetida, y el Radar crecía
   * hasta romper la rejilla.
   */
  it('agrupa los hallazgos por local en vez de repetir la direccion', async () => {
    api.cargar.and.resolveTo(carga({ hallazgos: coincidencias(2, 11) }));
    await montar();

    // Dos locales, no veintidós hallazgos.
    expect(raiz().querySelectorAll('.hallazgo').length).toBe(1);
    expect(raiz().querySelector('.hallazgo .c')?.textContent).toContain('11 clientes');
    expect(raiz().querySelectorAll('.ag.sin-dia').length).toBe(1);
  });

  /**
   * Y con muchos locales, el Radar corta: lo que no cabe se recorre en el
   * panel, que scrollea solo y no estira la página.
   */
  it('el Radar no crece con la lista: corta y ofrece el resto en el panel', async () => {
    api.cargar.and.resolveTo(carga({ hallazgos: coincidencias(30, 3) }));
    const inicio = await montar();

    // El destacado más tres, y nada más, pase lo que pase.
    expect(raiz().querySelectorAll('.ag.sin-dia').length).toBe(3);
    expect(raiz().querySelector('.rz .n-sec')?.textContent?.trim()).toBe('30');

    inicio['abrirHallazgos']();
    fixture.detectChanges();

    // El panel sí los tiene todos, uno por cliente: es donde la diferencia
    // entre clientes importa.
    expect(raiz().querySelectorAll('cl-panel-lateral .fila-cola').length).toBe(90);
  });

  // ==================================================================
  // LOS ACCESOS Y EL EXPEDIENTE
  // ==================================================================

  it('cada acceso rapido lleva su icono, y sale de la ruta', async () => {
    api.cargar.and.resolveTo(
      carga({
        accesos: [
          { etiqueta: 'Nueva prospección', destino: 'propiedades/nueva' },
          { etiqueta: 'Programar visita', destino: 'visitas/nueva' },
        ],
      }),
    );
    await montar();

    expect(raiz().querySelectorAll('.acceso .ic cl-icono').length).toBe(2);
  });

  /**
   * Las rutas de las tareas del agente son las del legado. Navegar a
   * `solicitud-detail/12` tal cual lleva a una pantalla que no existe: el
   * expediente tiene que pasar por el traductor, igual que «resolver».
   */
  it('ver el expediente pasa por el traductor de rutas', async () => {
    const inicio = await montar();

    await inicio['abrir']('prospeccion-detail/7');

    expect(navegacion.abrir).toHaveBeenCalledWith('prospeccion-detail/7');
  });

  // ==================================================================
  // LA VENTANA RÁPIDA
  // ==================================================================

  /**
   * **Lo que se cierra con un dato se cierra sin salir del Inicio.** Y lo que
   * se ofrece está atado al hecho que se enuncia arriba: si falta cerrar la
   * visita, aquí están «se realizó» y «no se realizó».
   */
  it('una visita sin cerrar se resuelve desde el Radar', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [TAREA_DEMANDA], totalRecords: 1, page: 1, pageSize: 5 } }),
    );
    await montar();
    await abrirEnElRadar('tarea:90');

    const botones = Array.from(raiz().querySelectorAll<HTMLButtonElement>('cl-accion-rapida button'));
    expect(botones.map((b) => b.textContent?.trim())).toEqual([
      'Sí, se realizó',
      'No se realizó',
      jasmine.stringContaining('Abrir el expediente') as unknown as string,
    ]);

    botones[0].click();
    await fixture.whenStable();

    expect(visitas.realizar).toHaveBeenCalledOnceWith(90);
  });

  /** «No se realizó» pide motivo, y lo pide el backend antes que la pantalla. */
  it('una visita caida no se registra sin motivo', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [TAREA_DEMANDA], totalRecords: 1, page: 1, pageSize: 5 } }),
    );
    await montar();
    await abrirEnElRadar('tarea:90');

    raiz().querySelectorAll<HTMLButtonElement>('cl-accion-rapida button')[1].click();
    fixture.detectChanges();
    raiz().querySelector<HTMLButtonElement>('cl-accion-rapida .bt-p')?.click();
    fixture.detectChanges();

    expect(visitas.noRealizada).not.toHaveBeenCalled();
    expect(raiz().querySelector('cl-accion-rapida .fallo')?.textContent).toContain('por qué');
  });

  /** Una prospección se cierra registrando el contacto, no abriendo su ficha. */
  it('un recontacto ofrece registrar el contacto sin salir del Inicio', async () => {
    await montar();
    await abrirEnElRadar('tarea:1');

    expect(raiz().querySelector('cl-accion-rapida .bt-p')?.textContent?.trim()).toBe(
      'Registrar el contacto',
    );
  });

  /**
   * Nada que necesite decisión larga tiene ventana rápida: meter un expediente
   * entero en una columna de 340 px sería peor que el viaje a su pantalla.
   */
  it('lo que no cabe en dos campos sigue abriendo su pantalla', async () => {
    api.cargar.and.resolveTo(
      carga({
        bandeja: {
          items: [{ ...TAREA, id: 5, entidadTipo: 'SOLICITUD_ALQUILER', entidadId: 5 }],
          totalRecords: 1,
          page: 1,
          pageSize: 5,
        },
      }),
    );
    await montar();
    await abrirEnElRadar('tarea:5');

    const unico = raiz().querySelectorAll('cl-accion-rapida button');
    expect(unico.length).toBe(1);
    expect(unico[0].textContent).toContain('Abrir el expediente');
  });
});
