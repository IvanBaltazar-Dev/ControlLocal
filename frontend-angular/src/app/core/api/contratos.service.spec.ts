import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { API_BASE_URL } from './api.config';
import { ContratosService, huellaMovimiento } from './contratos.service';

/**
 * Lo que se blinda aquí es el CABLE de la idempotencia: que la clave viaje en
 * la cabecera `Idempotency-Key` —que es donde el backend la busca— y que sin
 * clave la petición siga siendo válida, porque la cabecera es opcional mientras
 * el contrato legado esté congelado.
 */
describe('ContratosService · idempotencia de movimientos', () => {
  let servicio: ContratosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    servicio = TestBed.inject(ContratosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('manda la clave en la cabecera Idempotency-Key', () => {
    servicio.registrarMovimiento(7, { tipo: 'C', monto: 100, moneda: 'PEN' }, 'clave-1');

    const peticion = http.expectOne(`${API_BASE_URL}/contratos/7/comision/movimientos`);
    expect(peticion.request.headers.get('Idempotency-Key')).toBe('clave-1');
    peticion.flush({ id: 7 });
  });

  it('sin clave no manda la cabecera: sigue siendo opcional', () => {
    servicio.registrarMovimiento(7, { tipo: 'C', monto: 100, moneda: 'PEN' });

    const peticion = http.expectOne(`${API_BASE_URL}/contratos/7/comision/movimientos`);
    expect(peticion.request.headers.has('Idempotency-Key')).toBeFalse();
    peticion.flush({ id: 7 });
  });

  it('el comando reenvía la misma clave en el reintento y otra tras el éxito', async () => {
    const comando = servicio.nuevoComandoMovimiento(7);
    const datos = { tipo: 'C', monto: 100, moneda: 'PEN' };

    const fallido = comando.enviar(datos);
    const primera = http.expectOne(
      `${API_BASE_URL}/contratos/7/comision/movimientos`,
    );
    const claveInicial = primera.request.headers.get('Idempotency-Key');
    primera.flush({ error: 'sin red' }, { status: 0, statusText: '' });
    await expectAsync(fallido).toBeRejected();

    const reintento = comando.enviar(datos);
    const segunda = http.expectOne(
      `${API_BASE_URL}/contratos/7/comision/movimientos`,
    );
    expect(segunda.request.headers.get('Idempotency-Key')).toBe(claveInicial);
    segunda.flush({ id: 7 });
    await reintento;

    const nueva = comando.enviar(datos);
    const tercera = http.expectOne(
      `${API_BASE_URL}/contratos/7/comision/movimientos`,
    );
    expect(tercera.request.headers.get('Idempotency-Key')).not.toBe(claveInicial);
    tercera.flush({ id: 7 });
    await nueva;
  });
});

describe('huellaMovimiento', () => {
  it('distingue dos comandos que solo difieren en el importe', () => {
    const base = { tipo: 'C', monto: 100, moneda: 'PEN' };
    expect(huellaMovimiento(base)).not.toBe(huellaMovimiento({ ...base, monto: 250 }));
  });

  it('trata como el mismo comando dos objetos equivalentes', () => {
    expect(huellaMovimiento({ tipo: 'C', monto: 100, moneda: 'PEN' })).toBe(
      huellaMovimiento({ moneda: 'PEN', monto: 100, tipo: 'C' }),
    );
  });
});
