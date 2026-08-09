import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError, PageResponse } from '../../core/api/api.types';
import { PersonalService } from '../../core/api/personal.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { MODULOS, puedeEntrar } from '../../core/auth/acceso';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { filtrosProspeccionesDesdeUrl, Prospecciones } from './prospecciones';

const FILA: Prospeccion = {
  id: 5,
  codigoProspeccion: 'PRO-0005',
  localId: 9,
  localCodigo: 'LOC-0100',
  direccion: 'Av. Larco 812',
  distrito: 'Miraflores',
  propietarioNombre: 'Inversiones Centro SAC',
  idAgente: 30,
  agenteNombre: 'Valentina Mora',
  estado: 'T',
  fechaPropuesta: '2026-07-20',
  captacionCodigo: 'CAP-0002',
};

describe('Prospecciones', () => {
  let service: jasmine.SpyObj<ProspeccionesService>;
  let personal: jasmine.SpyObj<PersonalService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    service = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', [
      'pagina$',
      'recontactar$',
    ]);
    service.pagina$.and.returnValue(of(pagina([FILA], 12)));
    service.recontactar$.and.returnValue(of(pagina([], 2)));
    personal = jasmine.createSpyObj<PersonalService>('PersonalService', ['agentes$', 'brokers$']);
    personal.agentes$.and.returnValue(of(pagina([], 0, 100)));
    personal.brokers$.and.returnValue(of(pagina([], 0, 100)));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('pide al backend GESTION, broker, agente, búsqueda, orden y página', async () => {
    await montar('TENANT_ADMIN', {
      texto: 'larco',
      estado: 'gestion',
      idBroker: '23',
      idAgente: '30',
      orden: 'ultimo_contacto',
      page: '2',
    });

    const llamada = service.pagina$.calls.allArgs().find(([f]) => f?.tamano === 10)![0];
    expect(llamada).toEqual({
      pagina: 2,
      tamano: 10,
      estado: 'GESTION',
      idAgente: 30,
      idBrokerSupervisor: 23,
      q: 'larco',
      orden: 'ultimo_contacto',
    });
  });

  it('la tarjeta de recontacto usa la bandeja especial, no un filtro inventado', async () => {
    await montar('AGENTE', { recontactar: '1', page: '2' });

    expect(service.recontactar$).toHaveBeenCalledWith(7, 2, 10);
  });

  it('distingue las acciones por lo que realmente muestran', async () => {
    const fixture = await montar();
    const botones = filaBotones(fixture);

    expect(botones).toEqual(['Seguimiento', 'Datos del local', 'Resumen comercial']);
    const elementos = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('tbody button');
    elementos[0].click();
    elementos[1].click();
    elementos[2].click();
    expect(router.navigate).toHaveBeenCalledWith(['/prospecciones', 5]);
    expect(router.navigate).toHaveBeenCalledWith(['/locales', 9]);
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0002', 'ficha']);
  });

  it('un error deja lista y métricas vacías', async () => {
    service.pagina$.and.returnValue(throwError(() => new ApiError(500, 'Prospecciones caídas.')));
    const fixture = await montar();
    expect(texto(fixture)).toContain('Prospecciones caídas.');
    expect(texto(fixture)).not.toContain('PRO-0005');
  });

  it('la entrada del menú existe y está disponible para los tres roles', () => {
    const modulo = MODULOS.find((m) => m.ruta === 'prospecciones')!;
    expect(modulo).toBeDefined();
    expect(puedeEntrar(modulo, 'AGENTE')).toBeTrue();
    expect(puedeEntrar(modulo, 'BROKER')).toBeTrue();
    expect(puedeEntrar(modulo, 'TENANT_ADMIN')).toBeTrue();
  });

  describe('filtrosProspeccionesDesdeUrl', () => {
    it('normaliza GESTION y descarta ids y páginas inválidos', () => {
      const f = filtrosProspeccionesDesdeUrl(
        convertToParamMap({ estado: 'gestion', idAgente: '0', idBroker: 'x', page: '-2' }),
      );
      expect(f.estado).toBe('GESTION');
      expect(f.idAgente).toBeNull();
      expect(f.idBroker).toBeNull();
      expect(f.page).toBe(1);
    });

    it('recontactar limpia filtros incompatibles del enlace', () => {
      const f = filtrosProspeccionesDesdeUrl(
        convertToParamMap({ recontactar: '1', texto: 'larco', estado: 'T', idAgente: '30' }),
      );
      expect(f).toEqual({
        texto: '', estado: '', recontactar: true, idAgente: null, idBroker: null, orden: '', page: 1,
      });
    });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    queryParams: Record<string, string> = {},
  ): Promise<ComponentFixture<Prospecciones>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't', expiraEnSegundos: 3600, rol, idUsuario: 1, idDominio: 30,
      nombre: 'Prueba', usuario: 'prueba', expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [Prospecciones],
      providers: [
        { provide: ProspeccionesService, useValue: service },
        { provide: PersonalService, useValue: personal },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(queryParams)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(Prospecciones);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[], total: number, pageSize = 10): PageResponse<T> {
  return { items, totalRecords: total, page: 1, pageSize };
}

function texto(fixture: ComponentFixture<Prospecciones>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function filaBotones(fixture: ComponentFixture<Prospecciones>): string[] {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody button'))
    .map((b) => b.textContent?.trim() ?? '');
}
