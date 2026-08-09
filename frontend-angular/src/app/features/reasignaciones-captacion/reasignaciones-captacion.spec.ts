import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { of } from 'rxjs';

import { PageResponse } from '../../core/api/api.types';
import {
  Captacion,
  CaptacionesService,
  ReasignacionCaptacion,
} from '../../core/api/captaciones.service';
import { PersonalService } from '../../core/api/personal.service';
import { MODULOS } from '../../core/auth/acceso';
import {
  filtrosReasignacionesDesdeUrl,
  ReasignacionesCaptacion,
} from './reasignaciones-captacion';

const CAPTACION: Captacion = {
  id: 9,
  codigoCaptacion: 'CAP-0009',
  estado: 'A',
  direccionLocal: 'Av. Larco 700',
  idAgente: 30,
  agenteNombre: 'Valentina Mora',
};

const EVENTO: ReasignacionCaptacion = {
  idReasignacion: 4,
  idCaptacion: 9,
  codigoCaptacion: 'CAP-0009',
  direccionLocal: 'Av. Larco 700',
  idAgenteAnterior: 30,
  agenteAnteriorNombre: 'Valentina Mora',
  idAgenteNuevo: 31,
  agenteNuevoNombre: 'Javier Ruiz',
  idBroker: 20,
  brokerNombre: 'Ricardo Salas',
  fechaCambio: new Date().toISOString(),
  motivo: 'Balance de cartera comercial',
};

interface AccesoReasignaciones {
  motivo: { setValue(valor: string): void };
  seleccionarCaptacion(id: number): void;
  seleccionarAgente(id: number): void;
  prepararReasignacion(): void;
  confirmarReasignacion(): Promise<void>;
  cambiarTexto(texto: string): void;
  cambiarBusquedaHistorial(texto: string): void;
}

describe('ReasignacionesCaptacion', () => {
  let api: jasmine.SpyObj<CaptacionesService>;
  let personal: jasmine.SpyObj<PersonalService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', [
      'reasignables$', 'historialReasignaciones$', 'reasignar',
    ]);
    api.reasignables$.and.returnValue(of(pagina([CAPTACION], 1, 8)));
    api.historialReasignaciones$.and.returnValue(of([EVENTO]));
    api.reasignar.and.resolveTo({ ...CAPTACION, idAgente: 31, agenteNombre: 'Javier Ruiz' });
    personal = jasmine.createSpyObj<PersonalService>('PersonalService', ['agentes$']);
    personal.agentes$.and.returnValue(of(pagina([
      { id: 30, codigoAgente: 'AGE-030', nombre: 'Valentina Mora', estadoAdministrativo: 'A', estadoOperativo: 'D' },
      { id: 31, codigoAgente: 'AGE-031', nombre: 'Javier Ruiz', estadoAdministrativo: 'A', estadoOperativo: 'D' },
      { id: 32, codigoAgente: 'AGE-032', nombre: 'Inés Inactiva', estadoAdministrativo: 'I', estadoOperativo: 'D' },
      { id: 33, codigoAgente: 'AGE-033', nombre: 'Olga Ocupada', estadoAdministrativo: 'A', estadoOperativo: 'O' },
    ], 1, 100)));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('carga solo captaciones activas del endpoint y muestra la trazabilidad', async () => {
    const fixture = await montar();
    expect(api.reasignables$).toHaveBeenCalledOnceWith({ pagina: 1, tamano: 8, q: undefined });
    expect(api.historialReasignaciones$).toHaveBeenCalled();
    expect(texto(fixture)).toContain('CAP-0009');
    expect(texto(fixture)).toContain('Balance de cartera comercial');
  });

  it('solo ofrece destinos activos, disponibles y distintos del responsable actual', async () => {
    const contenido = texto(await montar());
    expect(contenido).toContain('Javier Ruiz');
    expect(contenido).not.toContain('Inés Inactiva');
    expect(contenido).not.toContain('Olga Ocupada');
  });

  it('exige un motivo trazable antes de abrir la confirmación', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    acceso.seleccionarCaptacion(9);
    acceso.seleccionarAgente(31);
    acceso.motivo.setValue('corto');
    acceso.prepararReasignacion();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('al menos 10 caracteres');
    expect(api.reasignar).not.toHaveBeenCalled();
  });

  it('reasigna, conserva el estado fuera del cuerpo y refresca lista e historial', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    acceso.seleccionarCaptacion(9);
    acceso.seleccionarAgente(31);
    acceso.motivo.setValue('  Balance de cartera  ');
    acceso.prepararReasignacion();
    await acceso.confirmarReasignacion();
    fixture.detectChanges();
    expect(api.reasignar).toHaveBeenCalledOnceWith(9, 31, 'Balance de cartera');
    expect(api.reasignables$).toHaveBeenCalledTimes(2);
    expect(api.historialReasignaciones$).toHaveBeenCalledTimes(2);
    expect(texto(fixture)).toContain('fue reasignada a Javier Ruiz');
  });

  it('conserva búsqueda y página de captaciones en la URL', async () => {
    const acceso = (await montar()).componentInstance as unknown as AccesoReasignaciones;
    acceso.cambiarTexto(' larco ');
    expect(router.navigate).toHaveBeenCalledOnceWith([], jasmine.objectContaining({
      queryParams: { texto: 'larco', page: 1 },
    }));
    expect(filtrosReasignacionesDesdeUrl(parametros({ texto: 'centro', page: '3' })))
      .toEqual({ texto: 'centro', page: 3 });
    expect(filtrosReasignacionesDesdeUrl(parametros({ page: '-2' })).page).toBe(1);
  });

  it('filtra el historial y no ofrece reportes PDF', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    acceso.cambiarBusquedaHistorial('sin coincidencia');
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Todavía no hay movimientos con estos filtros');
    expect(texto(fixture)).not.toContain('PDF');
  });

  it('activa el módulo solo para broker y admin', () => {
    const modulo = MODULOS.find((item) => item.ruta === 'captaciones/reasignaciones')!;
    expect(modulo).toBeDefined();
    expect(modulo.roles).toEqual(['BROKER', 'TENANT_ADMIN']);
  });

  async function montar(): Promise<ComponentFixture<ReasignacionesCaptacion>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReasignacionesCaptacion],
      providers: [
        { provide: CaptacionesService, useValue: api },
        { provide: PersonalService, useValue: personal },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(parametros({})) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(ReasignacionesCaptacion);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[], page: number, pageSize: number): PageResponse<T> {
  return { items, totalRecords: items.length, page, pageSize };
}

function parametros(valores: Record<string, string>): ParamMap {
  return convertToParamMap(valores);
}

function texto(fixture: ComponentFixture<ReasignacionesCaptacion>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
