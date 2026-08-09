import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { PageResponse } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { LocalesService } from '../../core/api/locales.service';
import { ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { CaptacionDetail } from './captacion-detail';

const CAPTACION: Captacion = {
  id: 9, codigoCaptacion: 'CAP-0009', estado: 'A', idLocal: 7,
  direccionLocal: 'Av. Larco 700', distritoLocal: 'Miraflores',
  propietarioNombre: 'Ana Torres', agenteNombre: 'Valentina Mora', comisionPactada: 100,
};

interface AccesoDetail {
  motivoCierre: { setValue(valor: string): void };
  abrirCierre(): void;
  confirmarCierre(): Promise<void>;
}

describe('CaptacionDetail', () => {
  let api: jasmine.SpyObj<CaptacionesService>;
  let locales: jasmine.SpyObj<LocalesService>;
  let prospecciones: jasmine.SpyObj<ProspeccionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', ['obtenerPorCodigo', 'cerrar']);
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION });
    api.cerrar.and.resolveTo({ ...CAPTACION, estado: 'C', observacionRevision: 'Fin del encargo' });
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', ['obtener']);
    locales.obtener.and.resolveTo({ id: 7, codigoLocal: 'LOC-0007', precioReferencial: 2800, monedaReferencial: 'PEN' });
    prospecciones = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', ['pagina']);
    prospecciones.pagina.and.resolveTo(pagina([{ id: 4, codigoProspeccion: 'PRO-0004', estado: 'T' }]));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('el agente observado ve Subsanar y navega al formulario', async () => {
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION, estado: 'O', observacionRevision: 'Corregir vigencia' });
    const fixture = await montar('AGENTE');
    boton(fixture, 'Subsanar').click();
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009', 'editar']);
  });

  it('broker y admin revisan una pendiente desde el expediente', async () => {
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION, estado: 'P' });
    const fixture = await montar('BROKER');
    boton(fixture, 'Revisar captación').click();
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009', 'revisar']);
  });

  it('cierra una activa con motivo y conserva la trazabilidad', async () => {
    const fixture = await montar('TENANT_ADMIN');
    const acceso = fixture.componentInstance as unknown as AccesoDetail;
    acceso.abrirCierre();
    acceso.motivoCierre.setValue('Fin del encargo');
    await acceso.confirmarCierre();
    fixture.detectChanges();
    expect(api.cerrar).toHaveBeenCalledOnceWith(9, 'Fin del encargo');
    expect(texto(fixture)).toContain('Captación cerrada. El motivo quedó registrado');
  });

  it('distingue expediente, datos del local y resumen comercial sin PDF', async () => {
    const html = texto(await montar('AGENTE'));
    expect(html).toContain('Expediente de CAP-0009');
    expect(html).toContain('Datos del local');
    expect(html).toContain('Resumen comercial');
    expect(html).not.toContain('PDF');
  });

  async function montar(rol: RolSesion): Promise<ComponentFixture<CaptacionDetail>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't', expiraEnSegundos: 3600, rol, idUsuario: 1, idDominio: 30,
      nombre: 'Prueba', usuario: 'prueba', expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [CaptacionDetail],
      providers: [
        { provide: CaptacionesService, useValue: api },
        { provide: LocalesService, useValue: locales },
        { provide: ProspeccionesService, useValue: prospecciones },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ codigo: 'CAP-0009' }) } } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(CaptacionDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[]): PageResponse<T> { return { items, totalRecords: items.length, page: 1, pageSize: 1 }; }
function texto(fixture: ComponentFixture<CaptacionDetail>): string { return (fixture.nativeElement as HTMLElement).textContent ?? ''; }
function boton(fixture: ComponentFixture<CaptacionDetail>, etiqueta: string): HTMLButtonElement {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) => b.textContent?.trim() === etiqueta) as HTMLButtonElement;
}
