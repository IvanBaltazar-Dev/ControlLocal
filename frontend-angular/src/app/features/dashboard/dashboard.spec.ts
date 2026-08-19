import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { DashboardCarga, DashboardService } from '../../core/api/dashboard.service';
import { IndicadoresResumen } from '../../core/api/indicadores.service';
import { Tarea, TareasService } from '../../core/api/tareas.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
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
  desempeno: [
    { nombre: 'Ana Perez', captaciones: 4, cierres: 1, conversion: 25 },
    { nombre: 'Beto Diaz', captaciones: 6, cierres: 0, conversion: 0 },
    { nombre: 'Cira Luna', captaciones: 1, cierres: 3, conversion: 100 },
    { nombre: 'Dino Paz', captaciones: 0, cierres: 0, conversion: 0 },
  ],
  operativo: {
    recontactosVencidos: 2,
    recontactosAlDia: 5,
    diasPromedioSinSeguimiento: 9,
    visitasPendientes: 3,
    solicitudesSinCierre: 1,
    conversionProspeccionCaptacion: 40,
  },
  // Ya clasificadas por el backend (E1). La pantalla no recalcula ninguna:
  // estos niveles son los que deciden el color y este orden es el que
  // ordena los focos.
  senales: [
    {
      concepto: 'SOLICITUD_POR_EVALUAR',
      valor: 3,
      nivelAtencion: 'ALTO',
      requiereAtencion: true,
      prioridad: 1,
    },
    {
      concepto: 'RECONTACTO_VENCIDO',
      valor: 2,
      nivelAtencion: 'ALTO',
      requiereAtencion: true,
      prioridad: 2,
    },
    {
      concepto: 'CAPTACION_POR_REVISAR',
      valor: 2,
      nivelAtencion: 'MEDIO',
      requiereAtencion: true,
      prioridad: 3,
    },
    {
      concepto: 'SOLICITUD_APROBADA_SIN_CIERRE',
      valor: 1,
      nivelAtencion: 'MEDIO',
      requiereAtencion: true,
      prioridad: 4,
    },
    {
      concepto: 'DEMORA_DE_SEGUIMIENTO',
      valor: 9,
      nivelAtencion: 'MEDIO',
      requiereAtencion: true,
      prioridad: 5,
    },
    {
      concepto: 'VISITA_PENDIENTE',
      valor: 3,
      nivelAtencion: 'INFORMATIVO',
      requiereAtencion: false,
      prioridad: 6,
    },
    {
      concepto: 'CIERRE_REGISTRADO',
      valor: 2,
      nivelAtencion: 'INFORMATIVO',
      requiereAtencion: false,
      prioridad: 7,
    },
    {
      concepto: 'COBERTURA_DE_AGENTES',
      valor: 3,
      nivelAtencion: 'INFORMATIVO',
      requiereAtencion: false,
      prioridad: 8,
    },
  ],
  // 3 solicitudes + 2 recontactos + 2 captaciones + 1 aprobada. Los 9 días de
  // atraso NO entran: no son cosas.
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
};

/** Bandeja de `total` tareas alternando prioridades, para probar el panel. */
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
    // Sin hallazgos por defecto: lo que estos tests blindan es la bandeja, y
    // un hallazgo NO es una tarea (E2.3). Quien pruebe la superficie de
    // hallazgos la pasa por `parcial`.
    hallazgos: [],
    // Y sin asuntos de broker: estos tests son del AGENTE, y su bandeja no es
    // la del broker (D-E2-5). Quien pruebe el foco del broker lo pasa por `parcial`.
    focoDelBroker: [],
    // Los cuatro accesos los decide el dominio; quien pruebe la barra los pasa
    // por `parcial`.
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

describe('Dashboard', () => {
  let api: jasmine.SpyObj<DashboardService>;
  let tareas: jasmine.SpyObj<TareasService>;
  let navegacion: jasmine.SpyObj<NavegacionLegado>;
  let fixture: ComponentFixture<Dashboard>;

  async function montar(rol: RolSesion = 'AGENTE', periodo?: string): Promise<Dashboard> {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: DashboardService, useValue: api },
        { provide: TareasService, useValue: tareas },
        { provide: NavegacionLegado, useValue: navegacion },
        { provide: AuthService, useValue: { sesion: signal(sesion(rol)) } },
        // Router de verdad: los KPI y los focos son `routerLink`, y un espía
        // sin `createUrlTree` los rompe al pintar.
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(periodo ? { periodo } : {}),
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
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
  });

  /**
   * El punto del contrato E4: indicadores y bandeja llegan juntos. Pedirlos por
   * separado sería volver al doble round-trip que el endpoint existe para
   * evitar.
   */
  it('carga el panel entero con UNA llamada', async () => {
    await montar();

    expect(api.cargar).toHaveBeenCalledOnceWith('6m', 5);
    expect(tareas.bandeja).not.toHaveBeenCalled();
  });

  it('respeta el periodo que llega por la URL y descarta el inventado', async () => {
    await montar('AGENTE', '1m');
    expect(api.cargar).toHaveBeenCalledOnceWith('1m', 5);

    TestBed.resetTestingModule();
    api.cargar.calls.reset();
    await montar('AGENTE', 'ayer');
    expect(api.cargar).toHaveBeenCalledOnceWith('6m', 5);
  });

  it('el agente ve su bandeja y quien supervisa ve focos', async () => {
    const agente = await montar('AGENTE');
    expect(agente['bandeja']().length).toBe(1);
    expect(agente['focos']().length).toBe(0);

    TestBed.resetTestingModule();
    const broker = await montar('BROKER');
    expect(broker['focos']().length).toBeGreaterThan(0);
  });

  /**
   * Para BROKER y ADMIN la bandeja llega VACÍA por contrato: no es un 403 ni un
   * fallo, así que no puede pintarse como "no hay tareas" ni como error.
   */
  it('la bandeja vacia de quien supervisa no genera focos falsos ni error', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [], totalRecords: 0, page: 1, pageSize: 5 } }),
    );
    const broker = await montar('BROKER');

    expect(broker['error']()).toBeNull();
    expect(broker['bandeja']()).toEqual([]);
  });

  /** Solo se ofrecen focos con valor: un foco en cero no es un foco. */
  it('descarta los focos en cero y corta en cinco', async () => {
    const broker = await montar('BROKER');
    const focos = broker['focos']();

    expect(focos.every((f) => f.valor > 0)).toBeTrue();
    expect(focos.length).toBeLessThanOrEqual(5);
    expect(focos[0].titulo).toBe('Solicitudes por revisar');
  });

  // --- R-07: la clasificación es del backend -------------------------------

  /**
   * El caso del enunciado: 9 días de atraso llegan con su lectura hecha. Si la
   * pantalla siguiera aplicando `> 7`, un cambio de política en el backend no
   * se notaría aquí. Se comprueba mandando un nivel que **contradice** al
   * número: el color tiene que seguir al nivel.
   */
  it('el tono sale del nivel que manda el backend, no de recalcular el umbral', async () => {
    api.cargar.and.resolveTo(
      carga({
        indicadores: {
          ...INDICADORES,
          senales: INDICADORES.senales.map((s) =>
            s.concepto === 'DEMORA_DE_SEGUIMIENTO'
              ? { ...s, valor: 9, nivelAtencion: 'SIN_PENDIENTES' as const, requiereAtencion: false }
              : s,
          ),
        },
      }),
    );
    const agente = await montar('AGENTE');
    const demora = agente['senales']().find((s) => s.etiqueta === 'Atraso promedio');

    expect(demora?.valor).toBe('9');
    expect(demora?.tono).toBe('verde');
  });

  /**
   * Antes cada rol traía su propia escala de pesos y se contradecían: para el
   * administrador los recontactos vencidos eran lo primero y para el broker los
   * cuartos. Ahora el orden es el del backend, y es el mismo para los dos.
   */
  it('los focos se ordenan por la prioridad del dominio', async () => {
    const broker = await montar('BROKER');
    const prioridades = broker['focos']().map((f) => f.prioridad);

    expect(prioridades).toEqual([...prioridades].sort((a, b) => a - b));
    expect(broker['focos']()[0].concepto).toBe('SOLICITUD_POR_EVALUAR');

    TestBed.resetTestingModule();
    const admin = await montar('TENANT_ADMIN');
    expect(admin['focos']()[0].concepto).toBe('RECONTACTO_VENCIDO');
  });

  // --- E2.1: la cabecera de decisión ---------------------------------------

  it('la cabecera abre diciendo cuantas cosas te reclaman y cuantas operaciones hay', async () => {
    await montar('AGENTE');
    const texto = (fixture.nativeElement as HTMLElement).querySelector('.decision')?.textContent;

    expect(texto).toContain('8');
    expect(texto).toContain('cosas necesitan tu atención');
    expect(texto).toContain('4 operaciones abiertas');
    expect(texto).toContain('2 alquileres firmados');
  });

  /**
   * El titular sale del backend tal cual. Si la pantalla lo recalculara sumando
   * `senales`, colaría los 9 días de atraso entre las "cosas" y diría 17.
   */
  it('el titular no se recalcula en la pantalla', async () => {
    api.cargar.and.resolveTo(carga({ indicadores: { ...INDICADORES, pendientesDeAtencion: 1 } }));
    await montar('AGENTE');
    const texto = (fixture.nativeElement as HTMLElement).querySelector('.decision')?.textContent;

    expect(texto).toContain('cosa necesita tu atención');
    expect(texto).not.toContain('8');
  });

  it('sin pendientes la cabecera lo dice y se marca tranquila', async () => {
    api.cargar.and.resolveTo(carga({ indicadores: { ...INDICADORES, pendientesDeAtencion: 0 } }));
    await montar('AGENTE');
    const cabecera = (fixture.nativeElement as HTMLElement).querySelector('.decision');

    expect(cabecera?.textContent).toContain('Nada te reclama ahora mismo');
    expect(cabecera?.classList.contains('tranquila')).toBeTrue();
  });

  /** Sin cierres no se inventa una línea económica: simplemente no aparece. */
  it('la linea economica solo sale si hay alquileres firmados', async () => {
    api.cargar.and.resolveTo(carga({ indicadores: { ...INDICADORES, cierres: 0 } }));
    await montar('AGENTE');
    const texto = (fixture.nativeElement as HTMLElement).querySelector('.decision')?.textContent;

    expect(texto).not.toContain('alquiler');
  });

  /** Un concepto que no viniera no puede pintarse como alarma ni romper la home. */
  it('sobrevive a una respuesta sin senales', async () => {
    api.cargar.and.resolveTo(carga({ indicadores: { ...INDICADORES, senales: [] } }));
    const broker = await montar('BROKER');

    expect(broker['error']()).toBeNull();
    expect(broker['focos']()).toEqual([]);
    expect(broker['senales']().every((s) => s.tono !== 'rojo')).toBeTrue();
  });

  /**
   * El punto de la corrección: el backend retiró el tope de 10 (D-F7-2) y una
   * bandeja de 32 volcada en la tarjeta estiraba la columna izquierda muy por
   * debajo de la derecha, sin forma de contraerla. La tarjeta se queda en 5
   * **pase lo que pase** y las 32 se recorren en el panel.
   */
  it('el panel trae la bandeja entera sin estirar la tarjeta', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(32));

    await agente['abrirPanel']();

    expect(agente['panelAbierto']()).toBeTrue();
    expect(agente['bandejaTodas']().length).toBe(32);
    expect(agente['bandeja']().length).toBe(5);
    expect(agente['totalBandeja']()).toBe(32);
  });

  /** `GET /tareas` reconcilia (escribe): cerrar y volver no puede costar otra. */
  it('cerrar el panel no vuelve a pedir la bandeja', async () => {
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
    expect(agente['tareasFiltradas']().every((t) => t.prioridad === 'ALTA')).toBeTrue();

    agente['limpiarFiltrosTareas']();
    agente['busquedaTareas'].set('pro-0003');
    expect(agente['tareasFiltradas']().map((t) => t.id)).toEqual([3]);

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

  /** El panel se queda abierto con su error reintentable; la home no se cae. */
  it('un fallo de la bandeja completa no cierra el panel ni tumba el dashboard', async () => {
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
   * llevaba por delante el panel al pulsarlo sobre "¿cancelar esta tarea?".
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
   * Cancelar la 3ª destapa la 6ª en la tarjeta, y esa no estaba descargada: por
   * eso se recarga la bandeja en vez de quitar la fila en el cliente.
   */
  it('tras cancelar recarga la bandeja en vez de borrar la fila', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(9));
    agente['pedirCancelacion'](TAREA);

    await agente['confirmarCancelacion']();

    expect(tareas.cancelar).toHaveBeenCalledOnceWith(1);
    expect(tareas.bandeja).toHaveBeenCalledTimes(1);
    expect(agente['porCancelar']()).toBeNull();
    expect(agente['bandeja']().length).toBe(5);
    expect(agente['totalBandeja']()).toBe(9);
  });

  /**
   * La lista del panel es contenido **proyectado** en `cl-panel-lateral`, pero
   * su SCSS vive en el dashboard. Angular sella lo proyectado con el atributo
   * de encapsulación de quien lo declara; si eso dejara de cumplirse las filas
   * del panel se quedarían sin estilo y no lo vería ninguna prueba de lógica.
   */
  it('la lista del panel hereda la encapsulacion del dashboard', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(8));
    await agente['abrirPanel']();
    fixture.detectChanges();

    const listas = (fixture.nativeElement as HTMLElement).querySelectorAll('ul.tareas');
    expect(listas.length).toBe(2); // la de la tarjeta y la del panel

    const sello = (elemento: Element) =>
      Array.from(elemento.attributes)
        .map((atributo) => atributo.name)
        .filter((nombre) => nombre.startsWith('_ngcontent-'));
    expect(sello(listas[0]).length).toBe(1);
    expect(sello(listas[1])).toEqual(sello(listas[0]));
  });

  /** `[class]` (tono) y `[class.activo]` conviven: el chip activo no pierde color. */
  it('el chip marcado conserva su tono y lo anuncia', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo(bandejaDe(8));
    await agente['abrirPanel']();
    agente['filtrarPor']('ALTA');
    fixture.detectChanges();

    const chips = (fixture.nativeElement as HTMLElement).querySelectorAll('.chip');
    expect(chips[0].getAttribute('aria-pressed')).toBe('false');
    expect(chips[1].classList).toContain('mal');
    expect(chips[1].classList).toContain('activo');
    expect(chips[1].getAttribute('aria-pressed')).toBe('true');
  });

  it('una tarea sin pantalla destino no ofrece resolver', async () => {
    navegacion.puedeAbrir.and.returnValue(false);
    const agente = await montar();

    expect(agente['puedeResolver'](TAREA)).toBeFalse();
  });

  it('avisa cuando resolver no lleva a ninguna parte, en vez de no hacer nada', async () => {
    navegacion.abrir.and.resolveTo(false);
    const agente = await montar();

    await agente['resolver'](TAREA);

    expect(agente['avisoBandeja']()).toContain('no tiene una pantalla');
  });

  /**
   * Salud (del periodo) y etapas (acumulado) son dos lecturas distintas. Se
   * cambia de una a otra solo cuando la del periodo está vacía, y la pantalla
   * lo dice en el subtítulo.
   */
  it('usa la salud del periodo y solo cae a etapas cuando esta vacia', async () => {
    const conSalud = await montar();
    expect(conSalud['usandoEtapas']()).toBeFalse();
    expect(conSalud['totalCubos']()).toBe(10);

    TestBed.resetTestingModule();
    api.cargar.and.resolveTo(
      carga({
        indicadores: {
          ...INDICADORES,
          captacionesSalud: [
            { nombre: 'Activas', valor: 0 },
            { nombre: 'Por revisar', valor: 0 },
          ],
        },
      }),
    );
    const sinSalud = await montar();
    expect(sinSalud['usandoEtapas']()).toBeTrue();
    expect(sinSalud['tituloCubos']()).toBe('Captaciones por etapa');
    expect(sinSalud['subtituloCubos']()).toContain('acumulado');
  });

  it('los porcentajes de la barra se calculan sobre el total mostrado', async () => {
    const agente = await montar();
    const tramos = agente['tramos']();

    expect(tramos.map((t) => t.porcentaje)).toEqual([60, 20, 10, 10]);
    expect(tramos.every((t) => t.color.length > 0)).toBeTrue();
  });

  /** Recorte en memoria sobre las ≤8 filas que ya llegaron: sin llamada extra. */
  it('la carga del equipo son los tres primeros por captaciones', async () => {
    const broker = await montar('BROKER');

    expect(broker['desempeno']().map((d) => d.nombre)).toEqual([
      'Beto Diaz',
      'Ana Perez',
      'Cira Luna',
    ]);
  });

  /**
   * E2.0. Antes esta prueba exigía lo contrario: sin cohorte, la conversión
   * caía a la fila de desempeño y el agente veía **la cifra de otro**. Ahora
   * `null` significa "no hay nada que medir" y la pantalla lo dice.
   */
  it('sin cohorte no hay conversion, y no se toma prestada la de otro agente', async () => {
    api.cargar.and.resolveTo(carga({ indicadores: { ...INDICADORES, conversionPropia: null } }));
    const agente = await montar();

    expect(agente['conversion']()).toBeNull();
    expect(agente['conversionAncho']()).toBe(0);

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('25%');
    expect(texto).toContain('todavía no hay conversión que medir');
  });

  it('con cohorte la conversion es la propia', async () => {
    const agente = await montar();
    expect(agente['conversion']()).toBe(20);
  });

  it('un fallo de la llamada deja la pantalla en error, no a medias', async () => {
    api.cargar.and.rejectWith(new Error('boom'));
    const agente = await montar();

    expect(agente['indicadores']()).toBeNull();
    expect(agente['error']()).toBeTruthy();
  });

  // ==================================================================
  // EL PIE · anticipo de Indicadores (D-E2-1 §6.2, E2.6)
  // ==================================================================

  function pie(): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.pie-indicadores');
  }

  /**
   * Los cuatro nombres van **completos y del backend**. Abreviarlos aquí
   * desharía la distinción que D-E2-2 §1.1 vino a fijar: 31 registros creados no
   * son 31 propietarios contactados.
   */
  it('el pie lleva los cuatro nombres canonicos, letra por letra y del cable', async () => {
    await montar('AGENTE');
    const nombres = [...(pie()?.querySelectorAll('.kpi-pie .nombre') ?? [])].map(
      (n) => n.textContent?.trim(),
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

    expect(pie()?.querySelectorAll('.kpi-pie').length).toBe(4);
  });

  /**
   * **Sin meta no hay cero.** El backend omite los campos nulos, así que llegan
   * como `undefined`; pintar «19 de 0» diría «tu objetivo era cero y lo
   * cumpliste», que es lo contrario de «nadie te fijó meta».
   */
  it('un KPI sin meta ensena solo lo conseguido y dice por que no concluye', async () => {
    await montar('AGENTE');
    const contratos = [...(pie()?.querySelectorAll('.kpi-pie') ?? [])].find((k) =>
      k.textContent?.includes('Contratos firmados'),
    );

    expect(contratos?.querySelector('.cifra')?.textContent?.trim()).toBe('4');
    expect(contratos?.textContent).not.toContain('de 0');
    expect(contratos?.textContent).toContain('Sin meta fijada para este mes');
    expect(contratos?.getAttribute('data-ritmo')).toBe('SIN_BASE');
  });

  /**
   * El estado lo decide el dominio. La pantalla lo traduce a un atributo y el
   * color sale de la hoja de estilos; nunca al revés.
   */
  it('el tono de cada KPI sale del estado que manda el dominio', async () => {
    await montar('AGENTE');
    const estados = [...(pie()?.querySelectorAll('.kpi-pie') ?? [])].map((k) =>
      k.getAttribute('data-ritmo'),
    );

    expect(estados).toEqual(['EN_RITMO', 'ATENCION', 'FUERA_DE_RITMO', 'SIN_BASE']);
  });

  /** Primero a cuánto estás de la meta, y después el ritmo. */
  it('cada KPI dice a cuanto estas de la meta antes que el ritmo', async () => {
    await montar('AGENTE');
    const primero = pie()?.querySelector('.kpi-pie .lectura')?.textContent?.trim();

    expect(primero).toBe('A 5 de la meta · hoy deberías ir por 15');
  });

  /**
   * La marca del ritmo esperado es lo que hace que el semáforo se entienda: sin
   * ella, un 79 % no distingue ir por delante de ir por detrás (D-E2-2 §3).
   */
  it('la barra lleva la marca del ritmo esperado a hoy', async () => {
    await montar('AGENTE');
    const marca = pie()?.querySelector('.kpi-pie .marca-esperada') as HTMLElement | null;

    // 15 de meta 24 = 63 % del ancho.
    expect(marca?.style.left).toBe('63%');
  });

  /** Con meta pequeña no se prorratea, así que tampoco se dibuja la marca. */
  it('sin cadencia diaria no se dibuja marca esperada', async () => {
    await montar('AGENTE');
    const sinMeta = [...(pie()?.querySelectorAll('.kpi-pie') ?? [])].find((k) =>
      k.textContent?.includes('Contratos firmados'),
    );

    expect(sinMeta?.querySelector('.marca-esperada')).toBeNull();
  });

  /**
   * **Instrucción 4 de D-E2-2**: al broker no se le atribuye una producción
   * personal que él no hace. «Hoy deberías ir por 21» estaba prohibido.
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

    expect(pie()?.querySelector('.en-juego')?.textContent).toContain(
      'Ninguna operación puede cerrarse este mes',
    );
  });

  /** El importe conserva su moneda: no se convierte a dólares para redondear. */
  it('la cifra en juego conserva su moneda', async () => {
    await montar('AGENTE');

    expect(pie()?.querySelector('.en-juego .cifra')?.textContent).toContain('PEN');
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

    expect(pie()?.querySelector('.periodo-pie')?.textContent).toContain('19 de 31 días del mes');
  });

  /** El enlace es la franja entera, no un botón dentro de ella. */
  it('la franja entera lleva a indicadores', async () => {
    await montar('AGENTE');

    expect(pie()?.getAttribute('href')).toBe('/indicadores');
  });

});
