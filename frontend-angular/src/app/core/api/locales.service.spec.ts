import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiClient } from './api.client';
import { LocalesService, LocalRequest } from './locales.service';

const SOLICITUD: LocalRequest = {
  codigoLocal: 'LC-260730120000000',
  direccion: 'Av. Larco 123',
  distrito: 'Miraflores',
  metraje: 85.5,
  precioReferencial: 2500,
  monedaReferencial: 'PEN',
  rubroPermitido: 'Restaurante',
  descripcion: 'Esquina comercial',
  idPropietario: 42,
  estado: 'D',
  tipoInmueble: 'L',
  uso: 'C',
  ambientes: 3,
  antiguedadAnios: 8,
  zonaUrbanizacion: 'Centro',
  geoLat: -12.12,
  geoLong: -77.03,
  estadoPublicacion: 'B',
  frente: 7.5,
  zonificacion: 'CZ',
  aptoLicenciaFuncionamiento: true,
  cargaElectricaKw: 12,
  numeroEstacionamientos: 2,
  cuotaMantenimiento: 150,
  interiorUnidad: 'Tienda 4',
  piso: '1',
  referenciaInterna: 'REF-04',
  nombreEdificioGaleria: 'Galería Central',
};

describe('LocalesService', () => {
  let api: jasmine.SpyObj<ApiClient>;
  let service: LocalesService;

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiClient>('ApiClient', ['get', 'get$', 'post', 'put']);
    api.get$.and.returnValue(of({ items: [], totalRecords: 0, page: 1, pageSize: 10 }));
    api.post.and.resolveTo({ id: 7 });
    api.put.and.resolveTo({ id: 7 });

    TestBed.configureTestingModule({
      providers: [LocalesService, { provide: ApiClient, useValue: api }],
    });
    service = TestBed.inject(LocalesService);
  });

  it('consulta posibles duplicados sin perder el id excluido', async () => {
    await service.posiblesDuplicados(SOLICITUD, 7);

    expect(api.post).toHaveBeenCalledOnceWith(
      'locales/posibles-duplicados', SOLICITUD, { idExcluir: 7 },
    );
  });

  it('envia filtros y pagina al endpoint sin reconstruir la cartera', () => {
    service
      .pagina$({ page: 3, tamano: 20, texto: 'camana', estado: 'N' })
      .subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('locales', {
      page: 3,
      tamano: 20,
      texto: 'camana',
      estado: 'N',
    });
    expect((service as unknown as { cartera?: unknown }).cartera).toBeUndefined();
  });

  it('pide los KPI al resumen con el mismo texto', () => {
    service.resumen$('camana').subscribe();

    expect(api.get$).toHaveBeenCalledOnceWith('locales/resumen', { texto: 'camana' });
  });

  it('registra con el cuerpo congelado sin renombrar ni envolver campos', async () => {
    await service.registrar(SOLICITUD);

    expect(api.post).toHaveBeenCalledOnceWith('locales', SOLICITUD);
  });

  it('actualiza el recurso exacto con el mismo cuerpo congelado', async () => {
    await service.actualizar(77, SOLICITUD);

    expect(api.put).toHaveBeenCalledOnceWith('locales/77', SOLICITUD);
  });
});
