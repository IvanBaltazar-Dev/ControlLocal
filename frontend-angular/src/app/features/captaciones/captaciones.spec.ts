import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { ApiError, PageResponse } from '../../core/api/api.types';
import { Captacion, CaptacionesService, FiltrosCaptaciones } from '../../core/api/captaciones.service';
import { PersonalService } from '../../core/api/personal.service';
import { MODULOS, puedeEntrar } from '../../core/auth/acceso';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { Captaciones, filtrosCaptacionesDesdeUrl } from './captaciones';

const FILA: Captacion = {
  id: 9,
  codigoCaptacion: 'CAP-0009',
  idLocal: 7,
  direccionLocal: 'Av. Arequipa 4100',
  distritoLocal: 'Miraflores',
  propietarioNombre: 'Comercial Centro SAC',
  idAgente: 30,
  agenteNombre: 'Valentina Mora',
  comisionPactada: 100,
  fechaInicioVigencia: '2026-07-01',
  fechaFinVigencia: '2099-09-30',
  estado: 'A',
};

describe('Captaciones', () => {
  let service: jasmine.SpyObj<CaptacionesService>;
  let personal: jasmine.SpyObj<PersonalService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    service = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', ['pagina$']);
    service.pagina$.and.callFake((f: FiltrosCaptaciones = {}) => {
      if (f.tamano === RESULTADOS_POR_PAGINA) return of(pagina([FILA], 14));
      const totales: Record<string, number> = { P: 3, O: 2, A: 8, C: 1, R: 4, V: 5 };
      return of(pagina([], f.estado ? (totales[f.estado] ?? 0) : 23, 1));
    });
    personal = jasmine.createSpyObj<PersonalService>('PersonalService', ['agentes$']);
    personal.agentes$.and.returnValue(of(pagina([], 0, 100)));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('lleva búsqueda, estado, agente y página al listado SQL', async () => {
    await montar('BROKER', { texto: 'arequipa', estado: 'a', idAgente: '30', page: '2' });
    const llamada = service.pagina$.calls.allArgs().find(([f]) => f?.tamano === RESULTADOS_POR_PAGINA)![0];
    expect(llamada).toEqual({
      pagina: 2, tamano: RESULTADOS_POR_PAGINA, estado: 'A', idAgente: 30, q: 'arequipa',
    });
  });

  it('los KPI cuentan todo el alcance, no solo la página visible', async () => {
    const fixture = await montar();
    const html = texto(fixture);
    expect(html).toContain('23');
    expect(html).toContain('8');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr').length).toBe(1);
  });

  it('distingue datos del local de resumen comercial', async () => {
    const fixture = await montar();
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('tbody button'),
    );
    expect(botones.map((b) => b.textContent?.trim())).toEqual([
      'Datos del local', 'Expediente', 'Resumen comercial',
    ]);
    botones[0].click();
    botones[1].click();
    botones[2].click();
    expect(router.navigate).toHaveBeenCalledWith(['/propiedades', 7]);
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009']);
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009', 'ficha']);
  });

  it('el agente no ve un filtro redundante por agente; broker y admin sí', async () => {
    const agente = await montar('AGENTE');
    expect((agente.nativeElement as HTMLElement).querySelectorAll('select').length).toBe(1);
    const broker = await montar('BROKER');
    expect((broker.nativeElement as HTMLElement).querySelectorAll('select').length).toBe(2);
  });

  it('el alta abre el formulario real de captación', async () => {
    const fixture = await montar('AGENTE');
    const alta = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('header button'),
    ).find((boton) => boton.textContent?.includes('Nueva captación'))!;
    alta.click();
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones/nueva']);
  });

  it('un error deja lista y métricas vacías', async () => {
    service.pagina$.and.returnValue(throwError(() => new ApiError(500, 'Captaciones caídas.')));
    const fixture = await montar();
    expect(texto(fixture)).toContain('Captaciones caídas.');
    expect(texto(fixture)).not.toContain('CAP-0009');
  });

  it('la entrada del menú existe y está disponible para los tres roles', () => {
    const modulo = MODULOS.find((m) => m.ruta === 'captaciones')!;
    expect(modulo).toBeDefined();
    expect(puedeEntrar(modulo, 'AGENTE')).toBeTrue();
    expect(puedeEntrar(modulo, 'BROKER')).toBeTrue();
    expect(puedeEntrar(modulo, 'TENANT_ADMIN')).toBeTrue();
  });

  describe('filtrosCaptacionesDesdeUrl', () => {
    it('normaliza el estado y descarta id y página inválidos', () => {
      const f = filtrosCaptacionesDesdeUrl(
        convertToParamMap({ texto: '  local  ', estado: 'o', idAgente: '-1', page: 'x' }),
      );
      expect(f).toEqual({ texto: 'local', estado: 'O', idAgente: null, page: 1 });
    });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    queryParams: Record<string, string> = {},
  ): Promise<ComponentFixture<Captaciones>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't', expiraEnSegundos: 3600, rol, idUsuario: 1, idDominio: 30,
      nombre: 'Prueba', usuario: 'prueba', expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [Captaciones],
      providers: [
        { provide: CaptacionesService, useValue: service },
        { provide: PersonalService, useValue: personal },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(queryParams)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(Captaciones);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[], total: number, pageSize = 10): PageResponse<T> {
  return { items, totalRecords: total, page: 1, pageSize };
}

function texto(fixture: ComponentFixture<Captaciones>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
