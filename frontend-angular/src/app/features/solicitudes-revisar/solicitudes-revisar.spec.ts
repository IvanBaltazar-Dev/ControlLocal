import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { PageResponse } from '../../core/api/api.types';
import {
  PENDIENTES,
  ResumenSolicitudes,
  Solicitud,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { filtrosRevisarDesdeUrl, SolicitudesRevisar } from './solicitudes-revisar';

const ITEMS: Solicitud[] = [
  {
    id: 4,
    codigoSolicitud: 'SOL-260715103000',
    codigoOportunidad: 'OP-0001',
    clienteNombre: 'Mariana Delgado',
    agenteNombre: 'Valentina Mora',
    direccionLocal: 'Av. Larco 812',
    distritoLocal: 'Miraflores',
    estado: 'E',
    montoPropuesto: 9000,
    moneda: 'PEN',
    plazoMeses: 24,
    documentosEntregados: 4,
    documentosRequeridos: 6,
  },
];

const PAGINA: PageResponse<Solicitud> = { items: ITEMS, totalRecords: 1, page: 1, pageSize: 10 };

const RESUMEN: ResumenSolicitudes = {
  total: 7,
  registradas: 1,
  enRevision: 2,
  observadas: 1,
  aprobadas: 2,
  rechazadas: 0,
  desistidas: 0,
  cerradas: 1,
  pendientes: 3,
  distritos: ['Miraflores'],
  agentes: [{ id: 28, nombre: 'Valentina Mora' }],
};

describe('SolicitudesRevisar', () => {
  let api: jasmine.SpyObj<SolicitudesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['pagina$', 'resumen$']);
    api.pagina$.and.returnValue(of(PAGINA));
    api.resumen$.and.returnValue(of(RESUMEN));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  /**
   * El cubo es del backend (`E` + `O` en una sola consulta paginada), no dos
   * llamadas ni un filtro en memoria.
   */
  it('la cola pide el cubo PENDIENTES por defecto', async () => {
    await montar();

    expect(api.pagina$).toHaveBeenCalledWith(
      jasmine.objectContaining({ estado: PENDIENTES, pagina: 1, tamano: RESULTADOS_POR_PAGINA }),
    );
  });

  it('las tres vistas de la cola son cubos, no filtros libres', async () => {
    await montar({ vista: 'O' });

    expect(api.pagina$).toHaveBeenCalledWith(jasmine.objectContaining({ estado: 'O' }));
  });

  /** Esta pantalla NUNCA lista fuera de la cola, ni escribiendo el estado a mano. */
  it('una vista inventada en la URL cae al cubo por defecto', async () => {
    await montar({ vista: 'A' });

    expect(api.pagina$).toHaveBeenCalledWith(jasmine.objectContaining({ estado: PENDIENTES }));
  });

  it('el resumen no lleva la vista: cuenta todos los cubos del alcance', async () => {
    await montar({ vista: 'E', texto: 'larco', distrito: 'Miraflores' });

    expect(api.resumen$).toHaveBeenCalledWith({ texto: 'larco' });
  });

  it('el defecto no ensucia la URL', async () => {
    const fixture = await montar({ vista: 'E' });

    acceder(fixture).cambiarVista(PENDIENTES);

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({ queryParams: jasmine.objectContaining({ vista: null }) }),
    );
  });

  it('cada fila lleva a evaluar esa solicitud, no a la lista genérica', async () => {
    const fixture = await montar();

    acceder(fixture).evaluar('SOL-260715103000');

    expect(router.navigate).toHaveBeenCalledWith([
      '/solicitudes',
      'SOL-260715103000',
      'evaluar',
    ]);
  });

  it('la lectura de la URL normaliza la vista y la página', () => {
    expect(filtrosRevisarDesdeUrl(convertToParamMap({ vista: 'o', page: '0' }))).toEqual({
      texto: '',
      vista: 'O',
      distrito: '',
      idAgente: '',
      page: 1,
    });
  });

  async function montar(
    query: Record<string, string> = {},
  ): Promise<ComponentFixture<SolicitudesRevisar>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [SolicitudesRevisar],
      providers: [
        { provide: SolicitudesService, useValue: api },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(SolicitudesRevisar);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoCola {
  cambiarVista(vista: string): void;
  evaluar(codigo: string): void;
}

function acceder(fixture: ComponentFixture<SolicitudesRevisar>): AccesoCola {
  return fixture.componentInstance as unknown as AccesoCola;
}
