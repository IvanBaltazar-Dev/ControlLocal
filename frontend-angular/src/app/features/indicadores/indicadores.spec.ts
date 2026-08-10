import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { IndicadoresResumen, IndicadoresService } from '../../core/api/indicadores.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { Indicadores } from './indicadores';

const RESUMEN: IndicadoresResumen = {
  ambito: 'Reportes de equipo',
  captacionesPorRevisar: 1,
  solicitudesPorEvaluar: 1,
  captacionesTotales: 4,
  captacionesActivas: 3,
  captacionesObservadas: 0,
  oportunidadesActivas: 2,
  interacciones: 6,
  visitas: 3,
  cierres: 2,
  cierresCohorte: 1,
  conversionPropia: 25,
  agentesActivos: 2,
  brokersActivos: 1,
  propiedadesEquipo: 3,
  mesesEtiquetas: ['Jun 26', 'Jul 26'],
  cierresPorMes: [1, 1],
  conversionPorPeriodo: [50, 0],
  captacionesPorPeriodo: [2, 2],
  etapas: [
    { nombre: 'Captacion activa', valor: 1 },
    { nombre: 'Clientes interesados', valor: 1 },
    { nombre: 'Con solicitud', valor: 0 },
    { nombre: 'En evaluacion', valor: 0 },
    { nombre: 'Alquilada', valor: 2 },
  ],
  captacionesSalud: [{ nombre: 'Activas', valor: 3 }],
  embudo: [
    { etapa: 'Oportunidades activas', valor: 0, porcentaje: 100 },
    { etapa: 'Con visita realizada', valor: 0, porcentaje: 0 },
  ],
  desempeno: [{ nombre: 'Ana Perez', captaciones: 2, cierres: 1, conversion: 50 }],
  operativo: {
    recontactosVencidos: 1,
    recontactosAlDia: 2,
    diasPromedioSinSeguimiento: 8,
    visitasPendientes: 1,
    solicitudesSinCierre: 0,
    conversionProspeccionCaptacion: 40,
  },
  // Esta pantalla no las pinta todavía; viajan en la respuesta desde E1.
  senales: [
    {
      concepto: 'RECONTACTO_VENCIDO',
      valor: 1,
      nivelAtencion: 'ALTO',
      requiereAtencion: true,
      prioridad: 2,
    },
  ],
  pendientesDeAtencion: 1,
};

function sesion(rol: RolSesion): Sesion {
  return {
    token: 't',
    expiraEnSegundos: 3600,
    rol,
    idUsuario: 1,
    idDominio: 20,
    nombre: 'Ricardo Salas',
    usuario: 'rsalas',
    expiraEn: '2026-12-31T00:00:00',
  };
}

describe('Indicadores', () => {
  let api: jasmine.SpyObj<IndicadoresService>;
  let fixture: ComponentFixture<Indicadores>;

  async function montar(rol: RolSesion = 'BROKER', periodo?: string): Promise<Indicadores> {
    await TestBed.configureTestingModule({
      imports: [Indicadores],
      providers: [
        { provide: IndicadoresService, useValue: api },
        { provide: AuthService, useValue: { sesion: signal(sesion(rol)) } },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(periodo ? { periodo } : {}) },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Indicadores);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<IndicadoresService>('IndicadoresService', ['resumen']);
    api.resumen.and.resolveTo(RESUMEN);
  });

  it('pide el periodo por defecto y respeta el de la URL', async () => {
    await montar('BROKER');
    expect(api.resumen).toHaveBeenCalledOnceWith('6m');

    TestBed.resetTestingModule();
    api.resumen.calls.reset();
    await montar('BROKER', '3m');
    expect(api.resumen).toHaveBeenCalledOnceWith('3m');
  });

  it('descarta un periodo inventado en vez de mandarlo al backend', async () => {
    await montar('BROKER', 'trimestre');

    expect(api.resumen).toHaveBeenCalledOnceWith('6m');
  });

  /**
   * La regla de forma que no se negocia: conteos y porcentaje no comparten
   * marco. Dos escalas en un mismo gráfico es la forma más fácil de mentir.
   */
  it('separa los conteos del porcentaje en dos series distintas', async () => {
    const pantalla = await montar();

    expect(pantalla['seriesConteo']().map((s) => s.nombre)).toEqual(['Captaciones', 'Cierres']);
    expect(pantalla['serieConversion']().length).toBe(1);
    expect(pantalla['serieConversion']()[0].nombre).toBe('Conversión');
  });

  /** Los dos colores de serie están validados; distintos por construcción. */
  it('da a cada serie de conteo un color propio', async () => {
    const pantalla = await montar();
    const [captaciones, cierres] = pantalla['seriesConteo']();

    expect(captaciones.color).not.toBe(cierres.color);
  });

  it('el embudo conserva el 100 fijo del cable y lo marca como tal', async () => {
    const pantalla = await montar();
    const embudo = pantalla['embudo']();

    expect(embudo[0].porcentaje).toBe(100);
    expect(embudo[0].valor).toBe(0);
    expect(embudo[0].fijo).toBeTrue();
    expect(embudo[1].fijo).toBeFalse();
  });

  /** Las etapas son un reparto exclusivo: sus porcentajes salen de su total. */
  it('reparte las etapas sobre su propio total, no sobre las captaciones', async () => {
    const pantalla = await montar();

    expect(pantalla['totalEtapas']()).toBe(4);
    expect(pantalla['etapas']().map((e) => e.porcentaje)).toEqual([25, 25, 0, 0, 50]);
  });

  it('rotula el desempeno segun quien mira', async () => {
    const broker = await montar('BROKER');
    expect(broker['tituloDesempeno']()).toBe('Desempeño por agente');

    TestBed.resetTestingModule();
    const admin = await montar('TENANT_ADMIN');
    expect(admin['tituloDesempeno']()).toBe('Desempeño por broker');

    TestBed.resetTestingModule();
    const agente = await montar('AGENTE');
    expect(agente['tituloDesempeno']()).toBe('Mi desempeño');
  });

  it('el ADMIN suma la tarjeta de plantilla que los otros no ven', async () => {
    const broker = await montar('BROKER');
    const admin = await (async () => {
      TestBed.resetTestingModule();
      return montar('TENANT_ADMIN');
    })();

    expect(broker['kpis']().some((k) => k.etiqueta === 'Equipo activo')).toBeFalse();
    expect(admin['kpis']().some((k) => k.etiqueta === 'Equipo activo')).toBeTrue();
  });

  it('un fallo deja la pantalla en error y sin datos a medias', async () => {
    api.resumen.and.rejectWith(new Error('boom'));
    const pantalla = await montar();

    expect(pantalla['datos']()).toBeNull();
    expect(pantalla['error']()).toBeTruthy();
  });
});
