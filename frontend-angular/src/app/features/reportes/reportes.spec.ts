import { ComponentFixture, TestBed } from '@angular/core/testing';

import {
  AvanceComercial,
  AvancePropiedad,
  IndicadoresService,
} from '../../core/api/indicadores.service';
import { Reportes } from './reportes';

function propiedad(parcial: Partial<AvancePropiedad> = {}): AvancePropiedad {
  return {
    idCaptacion: 1,
    codigoCaptacion: 'CAP-0001',
    direccion: 'Av. Larco 812',
    distrito: 'Miraflores',
    estadoComercial: 'Activa',
    oportunidadesTotales: 3,
    oportunidadesAbiertas: 1,
    oportunidadesConVisita: 2,
    oportunidadesConSolicitud: 1,
    cerradasExitosas: 1,
    cerradasNoFavorables: 0,
    cerradasNoContinuidad: 1,
    interesados: 2,
    interacciones: 6,
    visitasProgramadas: 1,
    visitasConcretadas: 2,
    solicitudesRecibidas: 1,
    tasaOportVisita: 67,
    tasaOportSolicitud: 33,
    motivoNoContinuidad: 'Precio',
    ...parcial,
  };
}

const AVANCE: AvanceComercial = {
  ambito: 'Mi avance comercial',
  propiedades: 2,
  oportunidadesTotales: 3,
  oportunidadesAbiertas: 1,
  oportunidadesConVisita: 2,
  oportunidadesConSolicitud: 1,
  cerradasExitosas: 1,
  cerradasNoFavorables: 0,
  cerradasNoContinuidad: 1,
  interesados: 2,
  interacciones: 6,
  visitasProgramadas: 1,
  visitasConcretadas: 2,
  solicitudesRecibidas: 1,
  tasaOportVisita: 67,
  tasaOportSolicitud: 33,
  detalle: [
    propiedad(),
    propiedad({ idCaptacion: 2, codigoCaptacion: 'CAP-0002', oportunidadesTotales: 0 }),
  ],
};

describe('Reportes (avance comercial)', () => {
  let api: jasmine.SpyObj<IndicadoresService>;
  let fixture: ComponentFixture<Reportes>;

  async function montar(): Promise<Reportes> {
    await TestBed.configureTestingModule({
      imports: [Reportes],
      providers: [{ provide: IndicadoresService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(Reportes);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<IndicadoresService>('IndicadoresService', ['avance']);
    api.avance.and.resolveTo(AVANCE);
  });

  /** Es acumulado: no acepta periodo, así que la pantalla no lo manda. */
  it('pide el avance sin periodo', async () => {
    await montar();

    expect(api.avance).toHaveBeenCalledOnceWith();
  });

  it('cuenta las propiedades captadas y quietas, que son las que hay que ver', async () => {
    const pantalla = await montar();

    expect(pantalla['filas']().length).toBe(2);
    expect(pantalla['sinMovimiento']()).toBe(1);
  });

  it('la exportacion lleva exactamente las filas mostradas', async () => {
    const pantalla = await montar();
    spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');

    pantalla['exportar']();

    expect(pantalla['exportado']()).toContain('2 propiedades');
  });

  it('sin captaciones activas no hay nada que exportar ni que mentir', async () => {
    api.avance.and.resolveTo({ ...AVANCE, propiedades: 0, detalle: [] });
    const pantalla = await montar();

    expect(pantalla['filas']()).toEqual([]);
    expect(pantalla['sinMovimiento']()).toBe(0);
  });

  it('un fallo deja la pantalla en error, sin tabla a medias', async () => {
    api.avance.and.rejectWith(new Error('boom'));
    const pantalla = await montar();

    expect(pantalla['datos']()).toBeNull();
    expect(pantalla['error']()).toBeTruthy();
  });
});
