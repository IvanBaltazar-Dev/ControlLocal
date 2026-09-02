import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ApiClient } from './api.client';
import { CaptacionesService, CaptacionRequest } from './captaciones.service';

describe('CaptacionesService', () => {
  let api: jasmine.SpyObj<ApiClient>;
  let service: CaptacionesService;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiClient>('ApiClient', ['get', 'get$', 'post', 'put']);
    api.get$.and.returnValue(of({ id: 1 }));
    api.get.and.resolveTo({ id: 1 });
    api.post.and.resolveTo({ id: 1 });
    api.put.and.resolveTo({ id: 1 });

    TestBed.configureTestingModule({
      providers: [CaptacionesService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(CaptacionesService);
  });

  it('pide la captación por su ruta de código, sin listar la bandeja', () => {
    service.obtenerPorCodigo$('CAP-0001').subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('captaciones/codigo/CAP-0001');
  });

  it('escapa el código: viaja en el path, no en un parámetro', () => {
    service.obtenerPorCodigo$('CAP 1/2').subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('captaciones/codigo/CAP%201%2F2');
  });

  it('obtiene por id cuando se conoce', () => {
    service.obtener$(7).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('captaciones/7');
  });

  it('pide la cartera del equipo con filtro y página al backend', () => {
    service
      .propiedadesEquipo$({ pagina: 2, tamano: 10, texto: 'larco', distrito: 'Miraflores' })
      .subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('captaciones/propiedades-equipo', {
      pagina: 2,
      tamano: 10,
      texto: 'larco',
      distrito: 'Miraflores',
    });
  });

  it('el resumen lleva el texto pero NO el distrito', () => {
    service.resumenPropiedadesEquipo$('larco').subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('captaciones/propiedades-equipo/resumen', {
      texto: 'larco',
    });
  });

  it('pagina con pagina/tamano', () => {
    service.pagina$({ pagina: 2, tamano: 25, estado: 'A' }).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('captaciones', {
      pagina: 2,
      tamano: 25,
      estado: 'A',
    });
  });

  it('crea y actualiza con el cuerpo congelado', async () => {
    const datos: CaptacionRequest = {
      codigoCaptacion: 'CAP-0001', fechaCaptacion: '2026-08-01',
      fechaInicioVigencia: '2026-08-01', fechaFinVigencia: '2027-02-01',
      comisionPactada: 100, observaciones: null, idLocal: 7, idAgente: 30,
      motivoOperacion: 'A', urgencia: 3, exclusividad: false,
      tipoOperacion: 'A', importeReferencia: 2500, monedaReferencia: 'PEN',
      tipoComision: 'E', baseCalculo: 'R', valorComision: 1,
      monedaComision: 'PEN', tratamientoIgv: 'N', motivoSinComision: null,
    };

    await service.registrar(datos);
    await service.actualizar(1, datos);

    expect(api.post).toHaveBeenCalledOnceWith('captaciones', datos);
    expect(api.put).toHaveBeenCalledOnceWith('captaciones/1', datos);
  });

  it('pagina la bandeja de revisión con filtros en el servidor', () => {
    service.pendientes$({ pagina: 2, tamano: 10, estado: 'O', idAgente: 30, q: 'larco' }).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('captaciones/pendientes', {
      pagina: 2, tamano: 10, estado: 'O', idAgente: 30, q: 'larco',
    });
  });

  it('pagina reasignables y obtiene su historial por las rutas congeladas', () => {
    service.reasignables$({ pagina: 3, tamano: 8, q: 'larco' }).subscribe();
    service.historialReasignaciones$().subscribe();

    expect(api.get$.calls.allArgs()).toEqual([
      ['captaciones/reasignables', { pagina: 3, tamano: 8, q: 'larco' }],
      ['captaciones/reasignaciones'],
    ]);
  });

  it('envía decisión, reasignación y cierre por las rutas congeladas', async () => {
    await service.decidir(9, 'O', 'Corregir vigencia');
    await service.reasignar(9, 31, 'Balance de cartera', 30);
    await service.cerrar(9, 'Fin del encargo');

    expect(api.post.calls.allArgs()).toEqual([
      ['captaciones/9/decision', { accion: 'O', observacion: 'Corregir vigencia' }],
      // `idAgenteActual` es el agente que se vio (D-P0-9). Es obligatorio: un
      // cuerpo sin él es 400, para que la reasignación no pueda partir de un
      // estado que nadie miró.
      ['captaciones/9/reasignar',
        { idAgenteNuevo: 31, motivo: 'Balance de cartera', idAgenteActual: 30 }],
      ['captaciones/9/cierre', { motivo: 'Fin del encargo' }],
    ]);
  });

  it('pide los candidatos de reasignación al Core, paginados y con texto', async () => {
    await service.candidatosReasignacion(9, 'ruiz');

    expect(api.get.calls.mostRecent().args).toEqual([
      'captaciones/9/reasignacion/candidatos',
      { texto: 'ruiz', page: 1, page_size: 50 },
    ]);
  });
});
