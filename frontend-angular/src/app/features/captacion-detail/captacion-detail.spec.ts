import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { PageResponse } from '../../core/api/api.types';
import {
  CapacidadesCaptacion,
  Captacion,
  CaptacionesService,
} from '../../core/api/captaciones.service';
import { LocalesService } from '../../core/api/locales.service';
import { ProspeccionesService } from '../../core/api/prospecciones.service';
import { CaptacionDetail } from './captacion-detail';

const CAPTACION: Captacion = {
  id: 9, codigoCaptacion: 'CAP-0009', estado: 'A', idLocal: 7,
  direccionLocal: 'Av. Larco 700', distritoLocal: 'Miraflores',
  propietarioNombre: 'Ana Torres', agenteNombre: 'Valentina Mora', comisionPactada: 100,
};

/** Las tres acciones de gobierno del expediente, por su etiqueta. */
const ACCIONES = ['Editar', 'Subsanar', 'Revisar captación', 'Cerrar captación'];

interface AccesoDetail {
  motivoCierre: { setValue(valor: string): void };
  abrirCierre(): void;
  confirmarCierre(): Promise<void>;
}

/**
 * **Quién puede hacer qué lo dice el Core, no la sesión** (D-P0-12).
 *
 * Estas pruebas no montan ninguna sesión y el componente **no inyecta
 * `AuthService`**: si alguien volviera a decidir la autoridad leyendo
 * `sesion().rol`, el `TestBed` —que no provee `HttpClient` ni el `Router` real
 * que `AuthService` necesita— fallaría al construirlo. Es el control positivo
 * de que la regla ya no vive aquí.
 */
describe('CaptacionDetail', () => {
  let api: jasmine.SpyObj<CaptacionesService>;
  let locales: jasmine.SpyObj<LocalesService>;
  let prospecciones: jasmine.SpyObj<ProspeccionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', ['obtenerPorCodigo', 'cerrar']);
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION });
    // Cerrada ya no se cierra otra vez: el Core recalcula las capacidades.
    api.cerrar.and.resolveTo({
      ...CAPTACION, estado: 'C', observacionRevision: 'Fin del encargo',
      capacidades: { puedeEditar: false, puedeRevisar: false, puedeCerrar: false, puedeReasignar: false },
    });
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', ['obtener']);
    locales.obtener.and.resolveTo({ id: 7, codigoLocal: 'LOC-0007', precioReferencial: 2800, monedaReferencial: 'PEN' });
    prospecciones = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', ['pagina']);
    prospecciones.pagina.and.resolveTo(pagina([{ id: 4, codigoProspeccion: 'PRO-0004', estado: 'T' }]));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  /**
   * **Sin capacidades no se ofrece nada.** Es el caso real de un listado —donde
   * el bloque no viaja— y el defecto seguro: ausente significa «no calculado
   * aquí», y de ahí no se deduce un permiso.
   */
  it('sin capacidades no ofrece ninguna accion sobre el encargo', async () => {
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION, capacidades: undefined });
    const fixture = await montar();

    for (const accion of ACCIONES) {
      expect(boton(fixture, accion)).withContext(accion).toBeUndefined();
    }
    // Y el expediente sí se pintó: lo que falta son las acciones, no la ficha.
    expect(boton(fixture, 'Volver')).toBeDefined();
  });

  /**
   * Su propia captación observada: **subsanar sí, revisar no**. Es el caso que
   * separa las dos capacidades — la regla vieja las deducía del mismo par
   * «estado editable + banda», así que un estado `P`/`O` bastaba para ofrecer
   * la revisión a quien no la decide.
   */
  it('el agente observado ve Subsanar y navega al formulario', async () => {
    api.obtenerPorCodigo.and.resolveTo({
      ...CAPTACION, estado: 'O', observacionRevision: 'Corregir vigencia',
      capacidades: capacidades({ puedeEditar: true }),
    });
    const fixture = await montar();

    expect(boton(fixture, 'Revisar captación')).toBeUndefined();
    expect(boton(fixture, 'Cerrar captación')).toBeUndefined();

    boton(fixture, 'Subsanar').click();
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009', 'editar']);
  });

  /**
   * Sólo `puedeRevisar`: aparece **Revisar captación** y nada más. Con la regla
   * vieja —«no ser AGENTE»— el mismo actor veía además «Cerrar captación» en
   * cuanto la captación estaba activa, y el estado ya no es quien lo decide.
   */
  it('con solo puedeRevisar aparece Revisar captacion y ninguna otra accion', async () => {
    api.obtenerPorCodigo.and.resolveTo({
      ...CAPTACION, estado: 'P', capacidades: capacidades({ puedeRevisar: true }),
    });
    const fixture = await montar();

    expect(boton(fixture, 'Revisar captación')).toBeDefined();
    expect(boton(fixture, 'Editar')).toBeUndefined();
    expect(boton(fixture, 'Cerrar captación')).toBeUndefined();

    boton(fixture, 'Revisar captación').click();
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones', 'CAP-0009', 'revisar']);
  });

  it('con solo puedeCerrar aparece Cerrar captacion y ninguna otra accion', async () => {
    api.obtenerPorCodigo.and.resolveTo({
      ...CAPTACION, capacidades: capacidades({ puedeCerrar: true }),
    });
    const fixture = await montar();

    expect(boton(fixture, 'Cerrar captación')).toBeDefined();
    expect(boton(fixture, 'Editar')).toBeUndefined();
    expect(boton(fixture, 'Revisar captación')).toBeUndefined();
  });

  /**
   * **El estado por sí solo no concede nada.** Una captación ACTIVA —el caso en
   * que la regla vieja ofrecía cerrar a todo el que no fuera agente— sin
   * capacidad de cierre no enseña el botón.
   */
  it('una captacion activa sin la capacidad no ofrece cerrarla', async () => {
    api.obtenerPorCodigo.and.resolveTo({
      ...CAPTACION, estado: 'A', capacidades: capacidades({ puedeEditar: true }),
    });
    const fixture = await montar();

    expect(boton(fixture, 'Cerrar captación')).toBeUndefined();
    expect(boton(fixture, 'Editar')).toBeDefined();
  });

  it('cierra una activa con motivo y conserva la trazabilidad', async () => {
    api.obtenerPorCodigo.and.resolveTo({
      ...CAPTACION, capacidades: capacidades({ puedeCerrar: true }),
    });
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoDetail;
    acceso.abrirCierre();
    acceso.motivoCierre.setValue('Fin del encargo');
    await acceso.confirmarCierre();
    fixture.detectChanges();
    expect(api.cerrar).toHaveBeenCalledOnceWith(9, 'Fin del encargo');
    expect(texto(fixture)).toContain('Captación cerrada. El motivo quedó registrado');
    // Y con la ficha releída ya no se ofrece volver a cerrarla.
    expect(boton(fixture, 'Cerrar captación')).toBeUndefined();
  });

  it('distingue expediente, datos del local y resumen comercial sin PDF', async () => {
    const html = texto(await montar());
    expect(html).toContain('Expediente de CAP-0009');
    expect(html).toContain('Datos del local');
    expect(html).toContain('Resumen comercial');
    expect(html).not.toContain('PDF');
  });

  async function montar(): Promise<ComponentFixture<CaptacionDetail>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CaptacionDetail],
      providers: [
        { provide: CaptacionesService, useValue: api },
        { provide: LocalesService, useValue: locales },
        { provide: ProspeccionesService, useValue: prospecciones },
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

/** Las CUATRO, y las que no se declaran son `false`: el defecto es no ofrecer. */
function capacidades(concedidas: Partial<CapacidadesCaptacion>): CapacidadesCaptacion {
  return {
    puedeEditar: false, puedeRevisar: false, puedeCerrar: false, puedeReasignar: false,
    ...concedidas,
  };
}
function pagina<T>(items: T[]): PageResponse<T> { return { items, totalRecords: items.length, page: 1, pageSize: 1 }; }
function texto(fixture: ComponentFixture<CaptacionDetail>): string { return (fixture.nativeElement as HTMLElement).textContent ?? ''; }
function boton(fixture: ComponentFixture<CaptacionDetail>, etiqueta: string): HTMLButtonElement {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) => b.textContent?.trim() === etiqueta) as HTMLButtonElement;
}
