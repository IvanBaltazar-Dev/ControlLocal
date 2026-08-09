import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApiClient } from './api.client';
import { API_BASE_URL } from './api.config';
import { ApiError } from './api.types';

describe('ApiClient binario', () => {
  let api: ApiClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sube octet-stream con parámetros y devuelve la respuesta final', async () => {
    const archivo = new File(['contenido'], 'dni.pdf', { type: 'application/pdf' });
    const respuesta = api.postBinario<{ idDocumento: number }>(
      'solicitudes/7/documentos/archivo',
      archivo,
      { tipoDocumento: 'DNI', nombreArchivo: 'dni.pdf' },
    );

    const peticion = http.expectOne(
      `${API_BASE_URL}/solicitudes/7/documentos/archivo?tipoDocumento=DNI&nombreArchivo=dni.pdf`,
    );
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toBe(archivo);
    expect(peticion.request.headers.get('Content-Type')).toBe('application/octet-stream');
    peticion.flush({ idDocumento: 19 });

    await expectAsync(respuesta).toBeResolvedTo({ idDocumento: 19 });
  });

  it('traduce el JSON de error que llega dentro de un Blob', async () => {
    const respuesta = api.descargar('documentos/contenido', { clave: 'inexistente' });
    const peticion = http.expectOne(
      `${API_BASE_URL}/documentos/contenido?clave=inexistente`,
    );
    expect(peticion.request.responseType).toBe('blob');
    peticion.flush(
      new Blob([JSON.stringify({ error: 'Documento no encontrado.' })], {
        type: 'application/json',
      }),
      { status: 404, statusText: 'Not Found' },
    );

    await expectAsync(respuesta).toBeRejectedWith(
      jasmine.objectContaining<ApiError>({
        status: 404,
        message: 'Documento no encontrado.',
      }),
    );
  });
});
