import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { ApiError, PageResponse } from '../../core/api/api.types';
import { Captacion, CaptacionesService, FiltrosCaptacionesPendientes } from '../../core/api/captaciones.service';
import { PersonalService } from '../../core/api/personal.service';
import { MODULOS, puedeEntrar } from '../../core/auth/acceso';
import { BandejaCaptaciones, filtrosRevisionDesdeUrl } from './bandeja-captaciones';

const FILA: Captacion = {
  id: 9, codigoCaptacion: 'CAP-0009', estado: 'P', idLocal: 7,
  direccionLocal: 'Av. Larco 700', distritoLocal: 'Miraflores',
  propietarioNombre: 'Ana Torres', idAgente: 30, agenteNombre: 'Valentina Mora',
  comisionPactada: 100,
};

describe('BandejaCaptaciones', () => {
  let api: jasmine.SpyObj<CaptacionesService>;
  let personal: jasmine.SpyObj<PersonalService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', ['pendientes$']);
    api.pendientes$.and.callFake((f: FiltrosCaptacionesPendientes = {}) => {
      if (f.tamano === RESULTADOS_POR_PAGINA) return of(pagina([FILA], 12));
      return of(pagina([], f.estado === 'P' ? 8 : 4, 1));
    });
    personal = jasmine.createSpyObj<PersonalService>('PersonalService', ['agentes$']);
    personal.agentes$.and.returnValue(of(pagina([{ id: 30, nombre: 'Valentina Mora' }], 1, 100)));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('baja búsqueda, estado, agente y página al endpoint de pendientes', async () => {
    await montar({ texto: 'larco', estado: 'o', idAgente: '30', page: '2' });
    const llamada = api.pendientes$.calls.allArgs().find(([f]) => f?.tamano === RESULTADOS_POR_PAGINA)![0];
    expect(llamada).toEqual({ pagina: 2, tamano: RESULTADOS_POR_PAGINA, estado: 'O', idAgente: 30, q: 'larco' });
  });

  it('los KPI cuentan P y O fuera de la página visible', async () => {
    const fixture = await montar({});
    expect(texto(fixture)).toContain('12');
    expect(texto(fixture)).toContain('8');
    expect(texto(fixture)).toContain('4');
  });

  it('distingue revisar de abrir el expediente', async () => {
    const fixture = await montar({});
    const botones = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('tbody button'));
    expect(botones.map((boton) => boton.textContent?.trim())).toEqual(['Revisar', 'Expediente']);
    botones[0].click();
    botones[1].click();
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009', 'revisar']);
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009']);
  });

  it('un error limpia la página y muestra el cuerpo del API', async () => {
    api.pendientes$.and.returnValue(throwError(() => new ApiError(500, 'Bandeja caída.')));
    const fixture = await montar({});
    expect(texto(fixture)).toContain('Bandeja caída.');
    expect(texto(fixture)).not.toContain('CAP-0009');
  });

  it('el módulo queda solo para broker y admin', () => {
    const modulo = MODULOS.find((m) => m.ruta === 'captaciones/pendientes')!;
    expect(modulo).toBeDefined();
    expect(puedeEntrar(modulo, 'BROKER')).toBeTrue();
    expect(puedeEntrar(modulo, 'TENANT_ADMIN')).toBeTrue();
    expect(puedeEntrar(modulo, 'AGENTE')).toBeFalse();
  });

  it('normaliza la URL y descarta valores fuera del contrato', () => {
    expect(filtrosRevisionDesdeUrl(convertToParamMap({ estado: 'x', idAgente: '-1', page: '0' }))).toEqual({
      texto: '', estado: '', idAgente: null, page: 1,
    });
  });

  async function montar(query: Record<string, string>): Promise<ComponentFixture<BandejaCaptaciones>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BandejaCaptaciones],
      providers: [
        { provide: CaptacionesService, useValue: api },
        { provide: PersonalService, useValue: personal },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(BandejaCaptaciones);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[], total: number, pageSize = 10): PageResponse<T> {
  return { items, totalRecords: total, page: 1, pageSize };
}

function texto(fixture: ComponentFixture<BandejaCaptaciones>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
