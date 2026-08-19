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
});
