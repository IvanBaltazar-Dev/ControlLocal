import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ApiClient } from './api.client';
import { PersonalService } from './personal.service';

describe('PersonalService', () => {
  let api: jasmine.SpyObj<ApiClient>;
  let service: PersonalService;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiClient>('ApiClient', ['get$']);
    api.get$.and.returnValue(of({ items: [], totalRecords: 0, page: 1, pageSize: 100 }));
    TestBed.configureTestingModule({
      providers: [PersonalService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(PersonalService);
  });

  it('pide agentes ya acotados por el backend', () => {
    service.agentes$().subscribe();
    expect(api.get$).toHaveBeenCalledOnceWith('agentes', { pagina: 1, tamano: 100 });
  });

  it('pide el equipo del broker seleccionado sin reconstruirlo en el cliente', () => {
    service.agentesDelBroker$(23).subscribe();
    expect(api.get$).toHaveBeenCalledOnceWith('brokers/23/agentes');
  });
});
