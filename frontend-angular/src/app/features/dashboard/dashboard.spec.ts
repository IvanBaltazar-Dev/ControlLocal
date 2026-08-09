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

function carga(parcial: Partial<DashboardCarga> = {}): DashboardCarga {
  return {
    indicadores: INDICADORES,
    bandeja: { items: [TAREA], totalRecords: 3, page: 1, pageSize: 5 },
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

  it('ver todas pide la bandeja completa, que el dashboard no trae', async () => {
    const agente = await montar();
    tareas.bandeja.and.resolveTo([TAREA, { ...TAREA, id: 2 }, { ...TAREA, id: 3 }]);

    await agente['verTodas']();

    expect(tareas.bandeja).toHaveBeenCalledTimes(1);
    expect(agente['bandeja']().length).toBe(3);
    expect(agente['bandejaCompleta']()).toBeTrue();
  });

  /**
   * Cancelar puede destapar una tarea que el corte en 10 dejaba fuera, así que
   * se recarga la bandeja en vez de quitar la fila en el cliente.
   */
  it('tras cancelar recarga la bandeja en vez de borrar la fila', async () => {
    const agente = await montar();
    agente['pedirCancelacion'](TAREA);

    await agente['confirmarCancelacion']();

    expect(tareas.cancelar).toHaveBeenCalledOnceWith(1);
    expect(tareas.bandeja).toHaveBeenCalledTimes(1);
    expect(agente['porCancelar']()).toBeNull();
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

  it('sin cohorte propia la conversion cae a la fila de desempeno', async () => {
    api.cargar.and.resolveTo(
      carga({ indicadores: { ...INDICADORES, conversionPropia: 0 } }),
    );
    const agente = await montar();

    expect(agente['conversion']()).toBe(25);
  });

  it('un fallo de la llamada deja la pantalla en error, no a medias', async () => {
    api.cargar.and.rejectWith(new Error('boom'));
    const agente = await montar();

    expect(agente['indicadores']()).toBeNull();
    expect(agente['error']()).toBeTruthy();
  });
});
