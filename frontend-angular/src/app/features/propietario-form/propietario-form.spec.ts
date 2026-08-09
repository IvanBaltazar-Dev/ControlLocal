import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { Propietario, PropietariosService } from '../../core/api/propietarios.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { PropietarioForm } from './propietario-form';

/**
 * Suite focalizada en **D-27 — autorización de datos** sobre el alta de
 * propietario.
 *
 * Existe porque el backend pasó a exigir la autorización en las dos altas y
 * esta pantalla se quedó sin ella: **el alta de propietario desde el SPA
 * fallaba**. Estos tests fijan que no vuelva a ocurrir.
 */
const EXISTENTE: Propietario = {
  id: 7,
  tipoPersona: 'N',
  tipoDocumento: 'D',
  numeroDocumento: '45781234',
  nombre: 'Ana Ruiz Vega',
  telefono: '987654321',
  correo: 'ana@correo.test',
  estado: 'A',
  consentimientoUsoDato: true,
  cantidadLocales: 0,
};

describe('PropietarioForm — autorización de datos (D-27)', () => {
  let api: jasmine.SpyObj<PropietariosService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<PropietariosService>('PropietariosService', [
      'obtener',
      'registrar',
      'actualizar',
    ]);
    api.obtener.and.resolveTo(EXISTENTE);
    api.registrar.and.resolveTo(EXISTENTE);
    api.actualizar.and.resolveTo(EXISTENTE);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('sin la casilla de autorización NO llama al backend', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({ ...VALIDOS, consentimientoUsoDato: false });

    await acceso.guardar();
    fixture.detectChanges();

    // Se corta en la pantalla: sin autorización no se guarda ningún dato, y el
    // usuario tiene que saber por qué antes de enviar.
    expect(api.registrar).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('no se guardará ningún dato');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('con la casilla marcada el alta sale, y sin canal: lo sella el backend', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({ ...VALIDOS, consentimientoUsoDato: true });

    await acceso.guardar();

    expect(api.registrar).toHaveBeenCalledWith(
      jasmine.objectContaining({
        numeroDocumento: '45781234',
        nombre: 'Ana Ruiz Vega',
        consentimientoUsoDato: true,
      }),
    );
    const enviado = api.registrar.calls.mostRecent().args[0] as Record<string, unknown>;
    expect(enviado['canalAutorizacion'])
      .withContext('la pantalla no manda canal: lo pone el backend')
      .toBeUndefined();
    expect(router.navigate).toHaveBeenCalledWith(['/propietarios'], { replaceUrl: true });
  });

  it('la sección muestra dos cosas: una casilla y el enlace al aviso', async () => {
    const fixture = await montar();
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    expect(elemento.querySelectorAll('input[type="checkbox"]').length)
      .withContext('una sola casilla, no una por finalidad')
      .toBe(1);
    expect(elemento.querySelector('a[href="/privacidad"]'))
      .withContext('el enlace al aviso de privacidad es obligatorio')
      .not.toBeNull();
    // Nada de canales, fechas, versiones ni finalidades: eso lo pone el backend.
    expect(elemento.querySelector('select[formcontrolname="canalAutorizacion"]'))
      .withContext('el canal ya no se pregunta')
      .toBeNull();
    expect(elemento.querySelector('input[formcontrolname="versionAviso"]')).toBeNull();
  });

  it('en edición NO se vuelve a pedir la autorización', async () => {
    const fixture = await montar('AGENTE', '7');
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    // Se pide una sola vez, al registrar.
    expect(elemento.querySelectorAll('input[type="checkbox"]').length).toBe(0);
  });

  it('en edición se puede guardar sin volver a marcar nada', async () => {
    const fixture = await montar('AGENTE', '7');
    const acceso = acceder(fixture);

    await acceso.guardar();

    // La guarda de autorización no puede bloquear una edición: si lo hiciera,
    // ningún propietario existente podría volver a editarse.
    expect(api.actualizar).toHaveBeenCalled();
    expect(api.registrar).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------

  const VALIDOS = {
    tipoPersona: 'N' as const,
    tipoDocumento: 'D' as const,
    numeroDocumento: '45781234',
    nombre: 'Ana Ruiz Vega',
    telefono: '987654321',
    correo: 'ana@correo.test',
  };

  async function montar(
    rol: RolSesion = 'AGENTE',
    id?: string,
  ): Promise<ComponentFixture<PropietarioForm>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 30,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [PropietarioForm],
      providers: [
        { provide: PropietariosService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(PropietarioForm);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoFormulario {
  formulario: FormGroup;
  guardar(): Promise<void>;
}

function acceder(fixture: ComponentFixture<PropietarioForm>): AccesoFormulario {
  return fixture.componentInstance as unknown as AccesoFormulario;
}

function texto(fixture: ComponentFixture<PropietarioForm>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
