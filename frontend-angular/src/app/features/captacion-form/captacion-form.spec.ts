import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { PageResponse } from '../../core/api/api.types';
import { Captacion, CaptacionesService, CaptacionRequest } from '../../core/api/captaciones.service';
import { Local, LocalesService } from '../../core/api/locales.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { Sesion } from '../../core/auth/sesion.model';
import { CaptacionForm, generarCodigoCaptacion } from './captacion-form';

const LOCAL: Local = {
  id: 12, codigoLocal: 'LOC-0012', direccion: 'Av. Larco 700', distrito: 'Miraflores',
  metraje: 95, precioReferencial: 2800, monedaReferencial: 'PEN', rubroPermitido: 'Tienda', estado: 'D',
  propietarioNombre: 'Ana Torres',
};
const PROSPECCION: Prospeccion = {
  id: 7, codigoProspeccion: 'PRO-0007', localId: LOCAL.id, estado: 'S',
};
const CREADA: Captacion = {
  id: 21, codigoCaptacion: 'CAP-0021', idLocal: LOCAL.id, idAgente: 30, estado: 'P',
};

interface AccesoFormulario {
  formulario: FormGroup;
  guardar(): Promise<void>;
}

describe('CaptacionForm', () => {
  let captaciones: jasmine.SpyObj<CaptacionesService>;
  let prospecciones: jasmine.SpyObj<ProspeccionesService>;
  let locales: jasmine.SpyObj<LocalesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    captaciones = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', [
      'obtenerPorCodigo', 'registrar', 'actualizar',
    ]);
    captaciones.registrar.and.resolveTo(CREADA);
    captaciones.actualizar.and.resolveTo(CREADA);
    prospecciones = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', [
      'obtener', 'marcarCaptada',
    ]);
    prospecciones.obtener.and.resolveTo(PROSPECCION);
    prospecciones.marcarCaptada.and.resolveTo({ ...PROSPECCION, estado: 'T' });
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', ['obtener', 'pagina']);
    locales.obtener.and.resolveTo(LOCAL);
    locales.pagina.and.resolveTo(pagina([LOCAL]));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('crea desde prospección y enlaza por id y código sin usar el alta abreviada', async () => {
    const fixture = await montar(null, { prospeccion: '7' });
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    acceso.formulario.patchValue({
      operacion: 'A',
      comisionConfirmada: true,
      observaciones: '  Exclusiva  ',
    });

    await acceso.guardar();

    const datos = captaciones.registrar.calls.mostRecent().args[0] as CaptacionRequest;
    expect(datos).toEqual(jasmine.objectContaining({
      idLocal: 12, idAgente: 30, comisionPactada: 100, observaciones: 'Exclusiva',
      motivoOperacion: 'A', urgencia: 3, exclusividad: false,
      tipoOperacion: 'A', importeReferencia: 2800, monedaReferencia: 'PEN',
      tipoComision: 'E', baseCalculo: 'R', valorComision: 1,
      monedaComision: 'PEN', tratamientoIgv: 'N', motivoSinComision: null,
    }));
    expect(datos.codigoCaptacion).toMatch(/^CAP-\d{15}$/);
    expect(prospecciones.marcarCaptada).toHaveBeenCalledOnceWith(7, 21, 'CAP-0021');
    expect(router.navigate).toHaveBeenCalledWith(['/captaciones'], {
      queryParams: { estado: 'P' }, replaceUrl: true,
    });
  });

  it('subsanar preserva código, local y agente y hace PUT', async () => {
    captaciones.obtenerPorCodigo.and.resolveTo({
      ...CREADA, estado: 'O', fechaCaptacion: '2026-07-01',
      fechaInicioVigencia: '2026-07-01', fechaFinVigencia: '2027-01-01',
      tipoOperacion: 'A',
      comisionPactada: 80, urgencia: 4, exclusividad: true,
      observacionRevision: 'Precisar vigencia',
    });
    const fixture = await montar('CAP-0021', {});
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    expect(texto(fixture)).toContain('Observación del broker');
    expect(texto(fixture)).toContain('Guardar y reenviar a revisión');
    acceso.formulario.patchValue({ comisionConfirmada: true });
    expect(acceso.formulario.get('operacion')?.value).toBe('A');

    await acceso.guardar();

    expect(captaciones.actualizar).toHaveBeenCalledOnceWith(
      21,
      jasmine.objectContaining({ codigoCaptacion: 'CAP-0021', idLocal: 12, idAgente: 30 }),
    );
    expect(captaciones.registrar).not.toHaveBeenCalled();
  });

  it('no guarda si el fin de vigencia es anterior al inicio', async () => {
    const fixture = await montar(null, { prospeccion: '7' });
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    acceso.formulario.patchValue({
      operacion: 'A',
      comisionConfirmada: true,
      fechaInicioVigencia: '2026-09-01',
      fechaFinVigencia: '2026-08-01',
    });

    await acceso.guardar();
    expect(captaciones.registrar).not.toHaveBeenCalled();
  });

  // El backend pide fin ESTRICTAMENTE posterior; un encargo de duracion cero
  // pasaba el formulario y volvia como 400 desde el servidor.
  it('tampoco guarda si el encargo empieza y termina el mismo dia', async () => {
    const fixture = await montar(null, { prospeccion: '7' });
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    acceso.formulario.patchValue({
      operacion: 'A',
      comisionConfirmada: true,
      fechaInicioVigencia: '2026-09-01',
      fechaFinVigencia: '2026-09-01',
    });

    await acceso.guardar();
    expect(captaciones.registrar).not.toHaveBeenCalled();
  });

  it('propone un mes como modalidad habitual pero exige confirmarlo expresamente', async () => {
    const fixture = await montar(null, { prospeccion: '7' });
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    acceso.formulario.patchValue({ operacion: 'A' });
    (fixture.componentInstance as unknown as { elegirOperacion(): void }).elegirOperacion();
    fixture.detectChanges();

    expect(acceso.formulario.get('modalidadComision')?.value).toBe('E1');
    expect(texto(fixture)).toContain('Un mes de alquiler');

    await acceso.guardar();
    expect(captaciones.registrar).not.toHaveBeenCalled();

    acceso.formulario.patchValue({ comisionConfirmada: true });
    await acceso.guardar();
    expect(captaciones.registrar).toHaveBeenCalledTimes(1);
  });

  it('exige un motivo expreso cuando se pacta no cobrar comisión', async () => {
    const fixture = await montar(null, { prospeccion: '7' });
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    acceso.formulario.patchValue({
      operacion: 'A',
      modalidadComision: 'S', motivoSinComision: '', comisionConfirmada: true,
    });
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Registra el motivo expreso');
    await acceso.guardar();
    expect(captaciones.registrar).not.toHaveBeenCalled();

    acceso.formulario.patchValue({ motivoSinComision: 'Acuerdo institucional' });
    await acceso.guardar();
    const datos = captaciones.registrar.calls.mostRecent().args[0] as CaptacionRequest;
    expect(datos).toEqual(jasmine.objectContaining({
      tipoComision: 'F', baseCalculo: 'N', valorComision: 0,
      motivoSinComision: 'Acuerdo institucional',
    }));
  });

  // La constante `motivoOperacion: 'A'` que esta pantalla enviaba pase lo que
  // pase convertia cualquier encargo de venta en un alquiler, con el precio de
  // venta metido en la casilla de la renta y la comision calculada sobre el.
  it('no envía nada mientras no se declare si el encargo es alquiler o venta', async () => {
    const fixture = await montar(null, { prospeccion: '7' });
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    acceso.formulario.patchValue({ comisionConfirmada: true });

    await acceso.guardar();

    expect(captaciones.registrar).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('Importe del encargo');
  });

  it('una venta rotula el importe, cambia la base y no ofrece mensualidades', async () => {
    const fixture = await montar(null, { prospeccion: '7' });
    const acceso = fixture.componentInstance as unknown as AccesoFormulario;
    acceso.formulario.patchValue({ operacion: 'V' });
    (fixture.componentInstance as unknown as { elegirOperacion(): void }).elegirOperacion();
    fixture.detectChanges();

    // «Un mes de alquiler» no es una comisión que exista en una venta.
    expect(acceso.formulario.get('modalidadComision')?.value).toBe('P');
    expect(texto(fixture)).toContain('Precio de venta');
    expect(texto(fixture)).not.toContain('Un mes de alquiler');

    acceso.formulario.patchValue({
      importeReferencia: 420000, monedaReferencia: 'USD',
      valorComision: 3, comisionConfirmada: true,
    });
    await acceso.guardar();

    const datos = captaciones.registrar.calls.mostRecent().args[0] as CaptacionRequest;
    expect(datos).toEqual(jasmine.objectContaining({
      motivoOperacion: 'V', tipoOperacion: 'V',
      importeReferencia: 420000, monedaReferencia: 'USD',
      tipoComision: 'P', baseCalculo: 'V', valorComision: 3, monedaComision: 'USD',
    }));
  });

  it('el alta libre pide solo una página de locales disponibles', async () => {
    await montar(null, {});
    expect(locales.pagina).toHaveBeenCalledOnceWith({
      page: 1, tamano: 20, texto: undefined, estado: 'D',
    });
  });

  it('genera un correlativo temporal UTC con milisegundos', () => {
    expect(generarCodigoCaptacion(new Date('2026-08-01T05:06:07.008Z'))).toBe(
      'CAP-260801050607008',
    );
  });

  async function montar(
    codigo: string | null,
    query: Record<string, string>,
  ): Promise<ComponentFixture<CaptacionForm>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't', expiraEnSegundos: 3600, rol: 'AGENTE', idUsuario: 1, idDominio: 30,
      nombre: 'Prueba', usuario: 'prueba', expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [CaptacionForm],
      providers: [
        { provide: CaptacionesService, useValue: captaciones },
        { provide: ProspeccionesService, useValue: prospecciones },
        { provide: LocalesService, useValue: locales },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap(codigo ? { codigo } : {}),
              queryParamMap: convertToParamMap(query),
            },
          },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(CaptacionForm);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[]): PageResponse<T> {
  return { items, totalRecords: items.length, page: 1, pageSize: 20 };
}

function texto(fixture: ComponentFixture<CaptacionForm>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
