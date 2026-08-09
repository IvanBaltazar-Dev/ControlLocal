import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { PageResponse } from '../../core/api/api.types';
import { Local, LocalesService, LocalRequest } from '../../core/api/locales.service';
import {
  Propietario,
  PropietariosService,
} from '../../core/api/propietarios.service';
import { generarCodigoLocal, LocalForm } from './local-form';

const PROPIETARIO: Propietario = {
  id: 42,
  tipoPersona: 'N',
  tipoDocumento: 'DNI',
  numeroDocumento: '12345678',
  nombre: 'Ana Torres',
  estado: 'A',
  cantidadLocales: 1,
};

const LOCAL_EXISTENTE: Local = {
  id: 77,
  codigoLocal: 'LC-EXISTENTE',
  direccion: 'Av. Larco 123',
  distrito: 'Miraflores',
  metraje: 85,
  precioReferencial: 2500,
  monedaReferencial: 'USD',
  rubroPermitido: 'Restaurante',
  descripcion: 'Descripcion anterior',
  idPropietario: PROPIETARIO.id,
  estado: 'D',
  tipoInmueble: 'L',
  uso: 'C',
  ambientes: 3,
  antiguedadAnios: 8,
  estadoPublicacion: 'P',
};

interface AccesoLocalForm {
  formulario: FormGroup;
  guardar(): Promise<void>;
}

describe('LocalForm', () => {
  let locales: jasmine.SpyObj<LocalesService>;
  let propietarios: jasmine.SpyObj<PropietariosService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', [
      'obtener',
      'registrar',
      'actualizar',
      'posiblesDuplicados',
    ]);
    locales.obtener.and.resolveTo(LOCAL_EXISTENTE);
    locales.registrar.and.resolveTo({ ...LOCAL_EXISTENTE, id: 78 });
    locales.actualizar.and.resolveTo(LOCAL_EXISTENTE);
    locales.posiblesDuplicados.and.resolveTo([]);

    propietarios = jasmine.createSpyObj<PropietariosService>('PropietariosService', [
      'pagina',
      'obtener',
    ]);
    propietarios.pagina.and.resolveTo(paginaPropietarios([PROPIETARIO]));
    propietarios.obtener.and.resolveTo(PROPIETARIO);

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('registra el payload congelado con uso comercial y publicacion borrador', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;
    acceso.formulario.patchValue({
      idPropietario: PROPIETARIO.id,
      direccion: '  Jr. Camana 456  ',
      distrito: 'Lima',
      metraje: 100.5,
      precioReferencial: 1800,
      monedaReferencial: 'PEN',
      rubroPermitido: 'Tienda',
      descripcion: '  Cerca de la plaza  ',
      zonaUrbanizacion: '  Centro historico  ',
      estado: 'D',
      tipoInmueble: 'L',
      aptoLicencia: 'true',
    });

    await acceso.guardar();

    const solicitud = locales.registrar.calls.mostRecent().args[0] as LocalRequest;
    expect(solicitud.codigoLocal).toMatch(/^LC-\d{15}$/);
    expect(solicitud).toEqual(
      jasmine.objectContaining({
        direccion: 'Jr. Camana 456',
        distrito: 'Lima',
        metraje: 100.5,
        precioReferencial: 1800,
        monedaReferencial: 'PEN',
        rubroPermitido: 'Tienda',
        descripcion: 'Cerca de la plaza',
        zonaUrbanizacion: 'Centro historico',
        idPropietario: PROPIETARIO.id,
        uso: 'C',
        estadoPublicacion: 'B',
        aptoLicenciaFuncionamiento: true,
      }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/locales'], { replaceUrl: true });
  });

  it('edita sin permitir cambiar propietario, codigo ni estado de publicacion', async () => {
    const fixture = await montar(String(LOCAL_EXISTENTE.id));
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    expect(acceso.formulario.get('idPropietario')!.disabled).toBeTrue();
    acceso.formulario.patchValue({ descripcion: 'Descripcion actualizada' });
    await acceso.guardar();

    const [id, solicitud] = locales.actualizar.calls.mostRecent().args;
    expect(id).toBe(LOCAL_EXISTENTE.id);
    expect(solicitud).toEqual(
      jasmine.objectContaining({
        codigoLocal: LOCAL_EXISTENTE.codigoLocal,
        idPropietario: PROPIETARIO.id,
        estadoPublicacion: 'P',
        descripcion: 'Descripcion actualizada',
      }),
    );
    expect(locales.registrar).not.toHaveBeenCalled();
  });

  it('no invoca el backend si faltan los campos obligatorios', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    await acceso.guardar();

    expect(locales.registrar).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('genera un codigo estable con fecha UTC y milisegundos', () => {
    expect(generarCodigoLocal(new Date('2026-07-30T20:15:16.007Z'))).toBe(
      'LC-260730201516007',
    );
  });

  async function montar(id: string | null): Promise<ComponentFixture<LocalForm>> {
    TestBed.configureTestingModule({
      imports: [LocalForm],
      providers: [
        { provide: LocalesService, useValue: locales },
        { provide: PropietariosService, useValue: propietarios },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap(id === null ? {} : { id }),
            },
          },
        },
        { provide: Router, useValue: router },
      ],
    });

    const fixture = TestBed.createComponent(LocalForm);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function paginaPropietarios(items: Propietario[]): PageResponse<Propietario> {
  return {
    items,
    totalRecords: items.length,
    page: 1,
    pageSize: 50,
  };
}
