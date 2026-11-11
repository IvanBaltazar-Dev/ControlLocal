import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AgentesService } from './agentes.service';
import { ApiClient } from './api.client';

describe('AgentesService', () => {
  let api: jasmine.SpyObj<ApiClient>;
  let service: AgentesService;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiClient>('ApiClient', ['get', 'get$', 'post', 'put']);
    api.get$.and.returnValue(of({ items: [], totalRecords: 0, page: 1, pageSize: 50 }));
    api.get.and.resolveTo({ items: [], totalRecords: 0, page: 1, pageSize: 50 });

    TestBed.configureTestingModule({
      providers: [AgentesService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(AgentesService);
  });

  it('sin filtros pide el catalogo tal cual, sin parametros inventados', () => {
    service.pagina$().subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('agentes', {});
  });

  it('los cuatro filtros viajan al backend, no se aplican en memoria', () => {
    service
      .pagina$({ pagina: 2, tamano: 10, texto: 'mora', estado: 'A', estadoOperativo: 'D', zona: 'Lima' })
      .subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('agentes', {
      pagina: 2,
      tamano: 10,
      texto: 'mora',
      estado: 'A',
      estadoOperativo: 'D',
      zona: 'Lima',
    });
  });

  it('el resumen no manda zona: es una de las opciones que devuelve', () => {
    service.resumen$({ texto: 'mora', estado: 'A' }).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('agentes/resumen', {
      texto: 'mora',
      estado: 'A',
    });
  });

  it('la ficha se pide con UNA llamada, no combinando bandejas', () => {
    service.ficha$(30).subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('agentes/30');
    expect(api.get$).toHaveBeenCalledTimes(1);
  });

  it('el perfil de gobierno se consulta directamente sin recorrer páginas ni pedir actividad comercial', async () => {
    await service.perfilParaGobierno(30);
    expect(api.get).toHaveBeenCalledOnceWith('agentes/30/perfil');
  });
});
