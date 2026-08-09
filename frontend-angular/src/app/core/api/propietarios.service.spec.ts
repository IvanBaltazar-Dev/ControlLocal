import { TestBed } from '@angular/core/testing';

import { ApiClient } from './api.client';
import { PropietariosService } from './propietarios.service';

describe('PropietariosService', () => {
  let api: jasmine.SpyObj<ApiClient>;
  let service: PropietariosService;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiClient>('ApiClient', ['get']);
    api.get.and.resolveTo({ items: [], totalRecords: 0, page: 1, pageSize: 50 });

    TestBed.configureTestingModule({
      providers: [PropietariosService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(PropietariosService);
  });

  it('pagina propietarios con los aliases congelados pagina y tamano', async () => {
    await service.pagina(2, 50);

    expect(api.get).toHaveBeenCalledOnceWith('propietarios', { pagina: 2, tamano: 50 });
  });

  it('obtiene un propietario concreto para conservarlo durante la edicion', async () => {
    await service.obtener(91);

    expect(api.get).toHaveBeenCalledOnceWith('propietarios/91');
  });
});
