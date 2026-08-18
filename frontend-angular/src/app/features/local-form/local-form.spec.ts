import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { PageResponse } from '../../core/api/api.types';
import { Local, LocalesService, LocalRequest } from '../../core/api/locales.service';
import {
  Propietario,
  PropietariosService,
} from '../../core/api/propietarios.service';
import { CapturaService } from '../../core/api/captura.service';
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
  formAlta: FormGroup;
  busquedaPropietario: { set(v: string): void; (): string };
  sinCoincidencias(): boolean;
  altaAbierta(): boolean;
  abrirAlta(): void;
  guardarAlta(): Promise<void>;
  cambiarTipoPersona(tipo: 'N' | 'J'): void;
}

describe('LocalForm', () => {
  let locales: jasmine.SpyObj<LocalesService>;
  let propietarios: jasmine.SpyObj<PropietariosService>;
  let captura: jasmine.SpyObj<CapturaService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    // Los minimos de los campos gobernados los declara el catalogo y llegan
    // por contrato. Aqui se devuelve un mapa vacio a proposito: este test
    // blinda el payload y las reglas propias del formulario, no la
    // resolucion de restricciones, que se prueba contra PostgreSQL.
    captura = jasmine.createSpyObj<CapturaService>('CapturaService', [
      'definicion',
      'restriccionesPorClave',
    ]);
    captura.restriccionesPorClave.and.resolveTo(new Map());

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
      'registrar',
    ]);
    propietarios.registrar.and.resolveTo({ ...PROPIETARIO, id: 99, nombre: 'Bruno Aliaga' });
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
    expect(router.navigate).toHaveBeenCalledWith(['/propiedades'], { replaceUrl: true });
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

  /* ---- Alta de propietario en contexto (D-E2-3 §3.1) ----
     Lo que se protege no es el panel: es que el agente NO tenga que salir
     del formulario, y que al volver el propietario quede seleccionado. */
  it('ofrece registrar al propietario cuando la busqueda no encuentra a nadie', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    expect(acceso.sinCoincidencias()).toBeFalse();
    acceso.busquedaPropietario.set('Aliaga');
    expect(acceso.sinCoincidencias()).toBeTrue();
  });

  it('reaprovecha lo escrito: nombre al nombre, digitos al documento', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    acceso.busquedaPropietario.set('Bruno Aliaga');
    acceso.abrirAlta();
    expect(acceso.formAlta.get('nombre')!.value).toBe('Bruno Aliaga');
    expect(acceso.formAlta.get('numeroDocumento')!.value).toBe('');

    acceso.busquedaPropietario.set('44556677');
    acceso.abrirAlta();
    expect(acceso.formAlta.get('nombre')!.value).toBe('');
    expect(acceso.formAlta.get('numeroDocumento')!.value).toBe('44556677');
  });

  it('registra sin salir del formulario y deja el propietario SELECCIONADO', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    acceso.formulario.patchValue({ direccion: 'Jr. Ica 118', metraje: 85 });
    acceso.busquedaPropietario.set('Bruno Aliaga');
    acceso.abrirAlta();
    acceso.formAlta.patchValue({
      nombre: 'Bruno Aliaga',
      tipoDocumento: 'D',
      numeroDocumento: '44556677',
      telefono: '987654321',
    });

    await acceso.guardarAlta();

    expect(propietarios.registrar).toHaveBeenCalledWith(
      jasmine.objectContaining({ nombre: 'Bruno Aliaga', numeroDocumento: '44556677' }),
    );
    // Lo que importa: queda elegido y el formulario del local NO se perdio.
    expect(acceso.formulario.get('idPropietario')!.value).toBe(99);
    expect(acceso.formulario.get('direccion')!.value).toBe('Jr. Ica 118');
    expect(acceso.altaAbierta()).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('no crea un duplicado: si el documento ya existe, selecciona al que hay', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    acceso.busquedaPropietario.set('12345678');
    acceso.abrirAlta();
    acceso.formAlta.patchValue({
      nombre: 'Ana Torres Duplicada',
      tipoDocumento: 'D',
      numeroDocumento: PROPIETARIO.numeroDocumento,
      telefono: '987654321',
    });

    await acceso.guardarAlta();

    expect(propietarios.registrar).not.toHaveBeenCalled();
    expect(acceso.formulario.get('idPropietario')!.value).toBe(PROPIETARIO.id);
  });

  it('persona juridica fuerza RUC y bloquea el tipo de documento', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    acceso.abrirAlta();
    acceso.cambiarTipoPersona('J');
    expect(acceso.formAlta.get('tipoDocumento')!.value).toBe('R');
    expect(acceso.formAlta.get('tipoDocumento')!.disabled).toBeTrue();
  });

  it('rechaza un DNI que no tiene 8 digitos', async () => {
    const fixture = await montar(null);
    const acceso = fixture.componentInstance as unknown as AccesoLocalForm;

    acceso.abrirAlta();
    acceso.formAlta.patchValue({
      nombre: 'Bruno Aliaga',
      tipoDocumento: 'D',
      numeroDocumento: '4455',
      telefono: '987654321',
    });

    await acceso.guardarAlta();

    expect(propietarios.registrar).not.toHaveBeenCalled();
  });

  async function montar(id: string | null): Promise<ComponentFixture<LocalForm>> {
    TestBed.configureTestingModule({
      imports: [LocalForm],
      providers: [
        { provide: LocalesService, useValue: locales },
        { provide: PropietariosService, useValue: propietarios },
        { provide: CapturaService, useValue: captura },
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
