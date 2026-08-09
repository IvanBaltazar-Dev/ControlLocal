import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  CaptacionesService,
  PropiedadEquipo as FilaEquipo,
  ResumenPropiedadesEquipo,
} from '../../core/api/captaciones.service';
import { MODULOS, puedeEntrar } from '../../core/auth/acceso';
import { filtrosEquipoDesdeUrl, PropiedadesEquipo } from './propiedades-equipo';

const FILA: FilaEquipo = {
  idPropiedad: 1,
  idCaptacion: 10,
  codigoCaptacion: 'CAP-0001',
  estado: 'A',
  codigoLocal: 'LOC-0001',
  direccion: 'Av. Larco 812',
  distrito: 'Miraflores',
  rubro: 'Restaurante',
  areaM2: 120,
  idAgente: 9,
  agenteNombre: 'Valentina Mora',
};

const RESUMEN: ResumenPropiedadesEquipo = {
  propiedades: 36,
  conCaptacionActiva: 18,
  agentesConCartera: 2,
  distritos: 2,
  distritosDisponibles: ['Barranco', 'Miraflores'],
};

describe('PropiedadesEquipo', () => {
  let captaciones: jasmine.SpyObj<CaptacionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    captaciones = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', [
      'propiedadesEquipo$',
      'resumenPropiedadesEquipo$',
    ]);
    captaciones.propiedadesEquipo$.and.returnValue(of(pagina([FILA])));
    captaciones.resumenPropiedadesEquipo$.and.returnValue(of(RESUMEN));

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('pide filtro, página y conteo al backend: no descarga la cartera', async () => {
    await montar({ texto: 'larco', distrito: 'Miraflores', page: '2' });

    expect(captaciones.propiedadesEquipo$).toHaveBeenCalledOnceWith({
      pagina: 2,
      tamano: 10,
      texto: 'larco',
      distrito: 'Miraflores',
    });
    // El resumen lleva el mismo texto y NO el distrito: es lo que ese filtro acota.
    expect(captaciones.resumenPropiedadesEquipo$).toHaveBeenCalledOnceWith('larco');
  });

  it('muestra una fila por inmueble con su captación más reciente', async () => {
    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('CAP-0001');
    expect(html).toContain('Av. Larco 812');
    expect(html).toContain('Valentina Mora');
    expect(html).toContain('120 m²');
    expect(html).toContain('Activa');
  });

  it('identifica el local por su código: la dirección puede repetirse', async () => {
    // Dos inmuebles distintos en la misma dirección (una galería, un centro
    // comercial) se leerían como filas duplicadas sin el código.
    const gemela: FilaEquipo = { ...FILA, idPropiedad: 2, codigoLocal: 'LOC-0002' };
    captaciones.propiedadesEquipo$.and.returnValue(of(pagina([FILA, gemela])));

    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('LOC-0001');
    expect(html).toContain('LOC-0002');
  });

  it('los KPI cuentan inmuebles distintos, no las filas descargadas', async () => {
    const fixture = await montar();
    const html = texto(fixture);

    // Una sola fila en la página, pero los contadores vienen del backend.
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
    expect(html).toContain('36');
    expect(html).toContain('18');
  });

  it('ofrece solo los distritos que la cartera tiene', async () => {
    const fixture = await montar();
    const opciones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('option'),
    ).map((o) => o.textContent?.trim());

    expect(opciones).toContain('Barranco');
    expect(opciones).toContain('Miraflores');
    expect(opciones).not.toContain('Surco');
  });

  it('es de solo lectura: la única acción lleva a la ficha', async () => {
    const fixture = await montar();
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody button'),
    ).map((b) => b.textContent?.trim());

    expect(botones).toEqual(['Resumen comercial']);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('tbody button')!
      .click();
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0001', 'ficha']);
  });

  it('un error deja la página y los KPI vacíos, sin datos a medias', async () => {
    captaciones.propiedadesEquipo$.and.returnValue(
      throwError(() => new ApiError(500, 'Cartera caída.')),
    );

    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('Cartera caída.');
    expect(html).not.toContain('36');
  });

  it('el vacío distingue "sin cartera" de "sin resultados del filtro"', async () => {
    captaciones.propiedadesEquipo$.and.returnValue(of(pagina([])));

    const sinFiltros = await montar();
    expect(texto(sinFiltros)).toContain('aún no tiene inmuebles captados');

    const conFiltros = await montar({ texto: 'nada' });
    expect(texto(conFiltros)).toContain('coincide con los filtros');
  });

  describe('acceso por rol', () => {
    const modulo = MODULOS.find((m) => m.ruta === 'propiedades-equipo')!;

    it('está declarado en el mapa que dibuja el menú y usa el guard', () => {
      expect(modulo).toBeDefined();
    });

    it('entran BROKER y ADMIN, que es el gate del backend', () => {
      expect(puedeEntrar(modulo, 'BROKER')).toBeTrue();
      expect(puedeEntrar(modulo, 'TENANT_ADMIN')).toBeTrue();
    });

    it('el AGENTE no entra: el endpoint le responde 403', () => {
      expect(puedeEntrar(modulo, 'AGENTE')).toBeFalse();
    });
  });

  describe('filtrosEquipoDesdeUrl', () => {
    it('no permite página 0 ni NaN', () => {
      expect(filtrosEquipoDesdeUrl(convertToParamMap({ page: '0' })).page).toBe(1);
      expect(filtrosEquipoDesdeUrl(convertToParamMap({ page: 'x' })).page).toBe(1);
      expect(filtrosEquipoDesdeUrl(convertToParamMap({ page: '3' })).page).toBe(3);
    });

    it('recorta el texto y conserva el distrito tal cual', () => {
      const filtros = filtrosEquipoDesdeUrl(
        convertToParamMap({ texto: '  larco  ', distrito: 'Miraflores' }),
      );

      expect(filtros).toEqual({ texto: 'larco', distrito: 'Miraflores', page: 1 });
    });
  });

  async function montar(
    queryParams: Record<string, string> = {},
  ): Promise<ComponentFixture<PropiedadesEquipo>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PropiedadesEquipo],
      providers: [
        { provide: CaptacionesService, useValue: captaciones },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap(queryParams)) },
        },
        { provide: Router, useValue: router },
      ],
    });

    const fixture = TestBed.createComponent(PropiedadesEquipo);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<PropiedadesEquipo>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function pagina(items: FilaEquipo[]): PageResponse<FilaEquipo> {
  return { items, totalRecords: items.length, page: 1, pageSize: 10 };
}
