import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';

import { PageResponse } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { Local, LocalesService } from '../../core/api/locales.service';
import { PersonalService } from '../../core/api/personal.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { CaptacionReview } from './captacion-review';

const CAPTACION: Captacion = {
  id: 9, codigoCaptacion: 'CAP-0009', estado: 'P', idLocal: 7,
  direccionLocal: 'Av. Larco 700', distritoLocal: 'Miraflores',
  propietarioNombre: 'Ana Torres', idAgente: 30, agenteNombre: 'Valentina Mora',
  comisionPactada: 100, fechaCaptacion: '2026-08-01',
};
const LOCAL: Local = { id: 7, codigoLocal: 'LOC-0007', direccion: 'Av. Larco 700', monedaReferencial: 'PEN' };
const PROSPECCION: Prospeccion = { id: 4, codigoProspeccion: 'PRO-0004', estado: 'T' };

interface AccesoReview {
  observacion: { setValue(valor: string): void };
  agenteNuevo: { setValue(valor: number): void };
  motivoReasignacion: { setValue(valor: string): void };
  abrirDecision(decision: 'A' | 'O' | 'R'): void;
  confirmarDecision(): Promise<void>;
  abrirReasignacion(): void;
  confirmarReasignacion(): Promise<void>;
}

describe('CaptacionReview', () => {
  let api: jasmine.SpyObj<CaptacionesService>;
  let locales: jasmine.SpyObj<LocalesService>;
  let prospecciones: jasmine.SpyObj<ProspeccionesService>;
  let personal: jasmine.SpyObj<PersonalService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', ['obtenerPorCodigo', 'decidir', 'reasignar']);
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION });
    api.decidir.and.resolveTo({ ...CAPTACION, estado: 'A' });
    api.reasignar.and.resolveTo({ ...CAPTACION, idAgente: 31, agenteNombre: 'Javier Ruiz' });
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', ['obtener']);
    locales.obtener.and.resolveTo(LOCAL);
    prospecciones = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', ['pagina']);
    prospecciones.pagina.and.resolveTo(pagina([PROSPECCION]));
    personal = jasmine.createSpyObj<PersonalService>('PersonalService', ['agentes$']);
    personal.agentes$.and.returnValue(of(pagina([
      { id: 30, nombre: 'Valentina Mora' }, { id: 31, nombre: 'Javier Ruiz' },
    ])));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('aprueba con nota opcional y actualiza el estado', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReview;
    acceso.abrirDecision('A');
    await acceso.confirmarDecision();
    fixture.detectChanges();
    expect(api.decidir).toHaveBeenCalledOnceWith(9, 'A', null);
    expect(texto(fixture)).toContain('Captación aprobada. Ya está activa.');
    expect(texto(fixture)).not.toContain('Aprobar captación');
  });

  it('observar exige motivo y envía el texto limpio', async () => {
    const acceso = (await montar()).componentInstance as unknown as AccesoReview;
    acceso.abrirDecision('O');
    await acceso.confirmarDecision();
    expect(api.decidir).not.toHaveBeenCalled();
    acceso.observacion.setValue('  Precisar la vigencia  ');
    await acceso.confirmarDecision();
    expect(api.decidir).toHaveBeenCalledOnceWith(9, 'O', 'Precisar la vigencia');
  });

  it('reasigna sin cambiar la decisión de revisión', async () => {
    const acceso = (await montar()).componentInstance as unknown as AccesoReview;
    acceso.abrirReasignacion();
    acceso.agenteNuevo.setValue(31);
    acceso.motivoReasignacion.setValue('Balance de cartera');
    await acceso.confirmarReasignacion();
    expect(api.reasignar).toHaveBeenCalledOnceWith(9, 31, 'Balance de cartera');
  });

  it('una captación ya activa se muestra sin botones de decisión', async () => {
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION, estado: 'A' });
    const fixture = await montar();
    expect(texto(fixture)).toContain('La revisión ya fue registrada');
    expect(texto(fixture)).not.toContain('Observar y devolver');
  });

  it('no ofrece ninguna exportación PDF', async () => {
    expect(texto(await montar())).not.toContain('PDF');
  });

  async function montar(): Promise<ComponentFixture<CaptacionReview>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CaptacionReview],
      providers: [
        { provide: CaptacionesService, useValue: api },
        { provide: LocalesService, useValue: locales },
        { provide: ProspeccionesService, useValue: prospecciones },
        { provide: PersonalService, useValue: personal },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ codigo: 'CAP-0009' }) } } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(CaptacionReview);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[]): PageResponse<T> {
  return { items, totalRecords: items.length, page: 1, pageSize: 100 };
}
function texto(fixture: ComponentFixture<CaptacionReview>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
