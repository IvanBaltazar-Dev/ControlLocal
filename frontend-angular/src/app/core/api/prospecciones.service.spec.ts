import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ApiClient } from './api.client';
import { masAvanzada, Prospeccion, ProspeccionesService } from './prospecciones.service';

function prospeccion(id: number, estado: string): Prospeccion {
  return { id, estado };
}

describe('ProspeccionesService', () => {
  let api: jasmine.SpyObj<ApiClient>;
  let service: ProspeccionesService;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiClient>('ApiClient', ['get', 'get$', 'post']);
    api.get$.and.returnValue(of({ items: [], totalRecords: 0, page: 1, pageSize: 10 }));
    api.post.and.resolveTo({ id: 7 });

    TestBed.configureTestingModule({
      providers: [ProspeccionesService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(ProspeccionesService);
  });

  it('filtra por local en el servidor en vez de descargar la bandeja', () => {
    service.porLocal$(77).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('prospecciones', {
      idLocal: 77,
      pagina: 1,
      tamano: 20,
    });
  });

  it('pagina con pagina/tamano: este recurso no acepta los alias page/page_size', () => {
    service.pagina$({ pagina: 2, tamano: 50, estado: 'C' }).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('prospecciones', {
      pagina: 2,
      tamano: 50,
      estado: 'C',
    });
  });

  it('envía el broker supervisor: es un filtro por equipo, no por captación', () => {
    service.pagina$({ idBrokerSupervisor: 23, pagina: 1, tamano: 10 }).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('prospecciones', {
      idBrokerSupervisor: 23,
      pagina: 1,
      tamano: 10,
    });
  });

  it('usa la bandeja especial de recontacto con su propio reloj', () => {
    service.recontactar$(7, 2, 8).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('prospecciones/recontactar', {
      dias: 7,
      pagina: 2,
      tamano: 8,
    });
  });

  it('registra los hitos con los POST congelados', async () => {
    await service.contactar(7);
    await service.registrarReunion(7);
    await service.entregarPropuesta(7);
    await service.registrarSeguimiento(7);

    expect(api.post.calls.allArgs()).toEqual([
      ['prospecciones/7/contactar', null],
      ['prospecciones/7/reunion', null],
      ['prospecciones/7/propuesta', null],
      ['prospecciones/7/seguimiento', null],
    ]);
  });

  it('marca la prospección con id y código de la captación creada', async () => {
    await service.marcarCaptada(7, 19, 'CAP-0019');

    expect(api.post).toHaveBeenCalledOnceWith('prospecciones/7/marcar-captado', {
      idCaptacion: 19,
      codigoCaptacion: 'CAP-0019',
    });
  });

  it('elige la prospección más avanzada del local', () => {
    const elegida = masAvanzada([
      prospeccion(1, 'P'),
      prospeccion(2, 'T'),
      prospeccion(3, 'R'),
    ]);

    expect(elegida?.id).toBe(2);
  });

  it('ordena seguimiento por encima de propuesta entregada', () => {
    // La v1 nunca emite `E`, pero si apareciera no debe desplazar a `S`.
    expect(masAvanzada([prospeccion(1, 'E'), prospeccion(2, 'S')])?.id).toBe(2);
  });

  it('sin prospecciones devuelve null en vez de reventar', () => {
    expect(masAvanzada([])).toBeNull();
  });

  it('un estado desconocido no gana a uno conocido', () => {
    expect(masAvanzada([prospeccion(1, 'Z'), prospeccion(2, 'P')])?.id).toBe(2);
  });
});
