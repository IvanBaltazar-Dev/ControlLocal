import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  Contrato,
  ContratosService,
  FiltrosContratos,
  ResumenCierres,
} from '../../core/api/contratos.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import {
  CABECERAS_CSV,
  filaCsv,
  filtrosCierresDesdeUrl,
  MAXIMO_EXPORTACION,
  PropiedadesAlquiladas,
} from './propiedades-alquiladas';

const CONTRATO: Contrato = {
  id: 7,
  codigoSolicitud: 'SOL-0007',
  codigoOportunidad: 'OPO-0003',
  codigoCaptacion: 'CAP-0001',
  clienteNombre: 'Comercial Andina SAC',
  direccionLocal: 'Av. Larco 812',
  distritoLocal: 'Miraflores',
  estadoDisponibilidadLocal: 'N',
  agenteNombre: 'Valentina Mora',
  rentaMensual: 8500,
  moneda: 'USD',
  plazoContratoMeses: 24,
  comisionGenerada: 8500,
  monedaComision: 'USD',
  fechaCierre: '2026-07-29',
  estadoContrato: 'V',
  comisionEstado: 'P',
};

const RESUMEN: ResumenCierres = {
  cierres: 14,
  comisionGenerada: 4150,
  moneda: 'USD',
  comisionesGeneradas: [{ moneda: 'USD', monto: 4150 }],
  montosCobrados: [{ moneda: 'USD', monto: 3000 }],
  saldosPendientes: [{ moneda: 'USD', monto: 1150 }],
  montosPagadosAgente: [{ moneda: 'USD', monto: 900 }],
  saldosPendientesAgente: [{ moneda: 'USD', monto: 345 }],
  porLiquidar: 3,
  sinLiquidacion: 1,
  distritosDisponibles: ['Miraflores'],
  agentesDisponibles: [{ id: 28, nombre: 'Valentina Mora' }],
};

interface AccesoPantalla {
  exportar(): Promise<void>;
}

describe('PropiedadesAlquiladas', () => {
  let contratos: jasmine.SpyObj<ContratosService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    contratos = jasmine.createSpyObj<ContratosService>('ContratosService', [
      'pagina$',
      'pagina',
      'resumen$',
    ]);
    contratos.pagina$.and.returnValue(of(pagina([CONTRATO], 1)));
    contratos.pagina.and.resolveTo(pagina([CONTRATO], 1));
    contratos.resumen$.and.returnValue(of(RESUMEN));

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('pide filtro, orden, página y conteo al backend', async () => {
    await montar('BROKER', { texto: 'larco', distrito: 'Miraflores', idAgente: '28', page: '2' });

    expect(contratos.pagina$).toHaveBeenCalledOnceWith({
      pagina: 2,
      tamano: 10,
      texto: 'larco',
      distrito: 'Miraflores',
      idAgente: 28,
      orden: 'cierre',
    });
    expect(contratos.resumen$).toHaveBeenCalledOnceWith({
      texto: 'larco',
      distrito: 'Miraflores',
      idAgente: 28,
    });
  });

  it('los KPI vienen del backend, con su moneda', async () => {
    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('14');
    expect(html).toContain('USD 4,150');
    expect(html).toContain('Cobrado');
    expect(html).toContain('USD 3,000');
    expect(html).toContain('Pendiente de cobro');
    expect(html).toContain('USD 1,150');
    expect(html).toContain('Pagado a agentes');
    expect(html).toContain('USD 900');
  });

  it('muestra el estado REAL del contrato, no un "Alquilado" fijo', async () => {
    contratos.pagina$.and.returnValue(
      of(pagina([{ ...CONTRATO, estadoContrato: 'S' }], 1)),
    );

    const fixture = await montar();

    expect(texto(fixture)).toContain('Rescindido');
    expect(texto(fixture)).not.toContain('Alquilado');
  });

  it('traduce el código unitario de la liquidación', async () => {
    const fixture = await montar();

    expect(texto(fixture)).toContain('Pendiente');
    expect(texto(fixture)).not.toContain('>P<');
  });

  it('separa estado jurídico, disponibilidad y cobro de comisión', async () => {
    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('Estado jurídico del contrato');
    expect(html).toContain('Disponibilidad del local');
    expect(html).toContain('Estado de cobro de comisión');
    expect(html).toContain('Vigente');
    expect(html).toContain('No disponible');
    expect(html).toContain('Pendiente');
  });

  it('hace visible un contrato sin liquidación', async () => {
    contratos.pagina$.and.returnValue(
      of(pagina([{ ...CONTRATO, comisionGenerada: undefined, monedaComision: undefined, comisionEstado: undefined }], 1)),
    );

    const fixture = await montar();

    expect(texto(fixture)).toContain('Sin liquidación');
  });

  it('un error deja lista y KPI vacíos, sin datos a medias', async () => {
    contratos.pagina$.and.returnValue(throwError(() => new ApiError(500, 'Cierres caídos.')));

    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('Cierres caídos.');
    expect(html).not.toContain('USD 4,150');
  });

  describe('filtro por agente', () => {
    it('se ofrece al broker y al admin', async () => {
      for (const rol of ['BROKER', 'TENANT_ADMIN'] as RolSesion[]) {
        const fixture = await montar(rol);
        expect(selects(fixture).length)
          .withContext(rol)
          .toBe(2);
      }
    });

    it('NO se ofrece al agente: su alcance ya es él mismo', async () => {
      const fixture = await montar('AGENTE');

      expect(selects(fixture).length).toBe(1);
    });
  });

  describe('exportación', () => {
    it('exporta el conjunto filtrado, no la página visible', async () => {
      // 3 páginas de 100 y una final: la exportación las recorre.
      contratos.pagina.and.returnValues(
        Promise.resolve(pagina(muchos(100), 150)),
        Promise.resolve(pagina(muchos(50), 150)),
      );

      const fixture = await montar('BROKER', { texto: 'larco' });
      await (fixture.componentInstance as unknown as AccesoPantalla).exportar();
      fixture.detectChanges();

      expect(contratos.pagina).toHaveBeenCalledTimes(2);
      const primera = contratos.pagina.calls.first().args[0] as FiltrosContratos;
      expect(primera.tamano).toBe(100);
      expect(primera.texto).toBe('larco');
      expect(texto(fixture)).toContain('Se exportaron 150 cierres');
    });

    it('avisa cuando el conjunto supera el máximo: nunca recorta en silencio', async () => {
      const total = MAXIMO_EXPORTACION + 500;
      contratos.pagina.and.callFake(() => Promise.resolve(pagina(muchos(100), total)));

      const fixture = await montar();
      await (fixture.componentInstance as unknown as AccesoPantalla).exportar();
      fixture.detectChanges();

      const html = texto(fixture);
      expect(html).toContain(`Se exportaron ${MAXIMO_EXPORTACION} de ${total}`);
      expect(html).toContain('Acota los filtros');
    });

    it('sin resultados lo dice en vez de descargar un archivo vacío', async () => {
      contratos.pagina.and.resolveTo(pagina([], 0));

      const fixture = await montar();
      await (fixture.componentInstance as unknown as AccesoPantalla).exportar();
      fixture.detectChanges();

      expect(texto(fixture)).toContain('No hay cierres que exportar');
    });

    it('un fallo del API se muestra y no rompe la pantalla', async () => {
      contratos.pagina.and.rejectWith(new ApiError(500, 'Sin conexión.'));

      const fixture = await montar();
      await (fixture.componentInstance as unknown as AccesoPantalla).exportar();
      fixture.detectChanges();

      expect(texto(fixture)).toContain('Sin conexión.');
    });
  });

  describe('filaCsv', () => {
    it('lleva los importes CRUDOS y la moneda en su columna', () => {
      const fila = filaCsv(CONTRATO);

      expect(fila.length).toBe(CABECERAS_CSV.length);
      expect(fila).toContain(8500);
      expect(fila).toContain('USD');
      // Nada de "USD 8,500" formateado: una hoja de cálculo necesita números.
      expect(fila.some((c) => typeof c === 'string' && c.includes('USD 8'))).toBeFalse();
    });

    it('traduce los estados y no deja huecos "undefined"', () => {
      const fila = filaCsv({ id: 1 });

      expect(fila.length).toBe(CABECERAS_CSV.length);
      expect(fila.every((c) => c !== undefined && c !== null)).toBeTrue();
    });
  });

  describe('filtrosCierresDesdeUrl', () => {
    it('descarta agente y página inválidos', () => {
      expect(filtrosCierresDesdeUrl(convertToParamMap({ idAgente: 'x' })).idAgente).toBeNull();
      expect(filtrosCierresDesdeUrl(convertToParamMap({ idAgente: '0' })).idAgente).toBeNull();
      expect(filtrosCierresDesdeUrl(convertToParamMap({ idAgente: '28' })).idAgente).toBe(28);
      expect(filtrosCierresDesdeUrl(convertToParamMap({ page: '0' })).page).toBe(1);
    });
  });

  async function montar(
    rol: RolSesion = 'BROKER',
    queryParams: Record<string, string> = {},
  ): Promise<ComponentFixture<PropiedadesAlquiladas>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 2,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });

    TestBed.configureTestingModule({
      imports: [PropiedadesAlquiladas],
      providers: [
        { provide: ContratosService, useValue: contratos },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap(queryParams)) },
        },
        { provide: Router, useValue: router },
      ],
    });

    const fixture = TestBed.createComponent(PropiedadesAlquiladas);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<PropiedadesAlquiladas>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function selects(fixture: ComponentFixture<PropiedadesAlquiladas>): HTMLSelectElement[] {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('select'));
}

function pagina(items: Contrato[], total: number): PageResponse<Contrato> {
  return { items, totalRecords: total, page: 1, pageSize: 10 };
}

function muchos(cuantos: number): Contrato[] {
  return Array.from({ length: cuantos }, (_, i) => ({ ...CONTRATO, id: i + 1 }));
}
