import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Cliente, ClientesService } from '../../core/api/clientes.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { ClienteForm } from './cliente-form';

const EXISTENTE: Cliente = {
  id: 7,
  tipoPersona: 'J',
  tipoDocumento: 'R',
  numeroDocumento: '20551234567',
  nombre: 'Retail Andino SAC',
  telefono: '014567890',
  correo: 'contacto@retailandino.test',
  rubroComercial: 'Rubro heredado de la v1',
  estado: 'A',
  consentimientoContacto: true,
  consentimientoUsoDato: false,
};

describe('ClienteForm', () => {
  let api: jasmine.SpyObj<ClientesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<ClientesService>('ClientesService', [
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

  it('no registra si faltan obligatorios y no llama al backend', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    await acceso.guardar();
    fixture.detectChanges();

    expect(api.registrar).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('Revisa los campos obligatorios');
  });

  it('una persona jurídica se identifica con RUC y no ofrece otro documento', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.cambiarTipoPersona('J');
    fixture.detectChanges();

    expect(acceso.formulario.getRawValue().tipoDocumento).toBe('R');
    // El selector de tipo de documento ni siquiera se dibuja para jurídicas.
    expect(select(fixture, 'tipoDocumento')).toBeNull();
  });

  /** Espejo de `Personas.validar`: el 400 no debe descubrirse al enviar. */
  it('valida el largo del documento antes de llamar al backend', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({
      tipoPersona: 'N',
      tipoDocumento: 'D',
      numeroDocumento: '123',
      nombre: 'Mariana Delgado',
      telefono: '987654321',
      correo: 'mariana@demo.test',
      rubroComercial: 'Retail',
      // La autorización va marcada para aislar lo que este test comprueba: que
      // lo que corta el envío es el documento, no otra cosa (D-27 tiene su
      // propio test más abajo).
      consentimientoUsoDato: true,
    });

    await acceso.guardar();
    expect(api.registrar).not.toHaveBeenCalled();

    acceso.formulario.patchValue({ numeroDocumento: '45781234' });
    await acceso.guardar();
    expect(api.registrar).toHaveBeenCalled();
  });

  it('un carné de extranjería no exige largo fijo', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({
      tipoPersona: 'N',
      tipoDocumento: 'C',
      numeroDocumento: 'CE-9081',
      nombre: 'Luca Rossi',
      telefono: '987654321',
      correo: 'luca@demo.test',
      rubroComercial: 'Retail',
      consentimientoUsoDato: true,
    });

    await acceso.guardar();

    expect(api.registrar).toHaveBeenCalled();
  });

  it('el alta manda los campos del cable, sin nada de la autorización salvo la casilla', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({
      tipoPersona: 'N',
      tipoDocumento: 'D',
      numeroDocumento: '45781234',
      nombre: '  Mariana Delgado  ',
      telefono: '987654321',
      correo: 'mariana@demo.test',
      rubroComercial: 'Retail',
      // D-27: una sola casilla. El consentimiento de contacto ya no se pide
      // aparte: se deriva de esta misma autorizacion.
      consentimientoUsoDato: true,
    });

    await acceso.guardar();

    expect(api.registrar).toHaveBeenCalledWith(
      jasmine.objectContaining({
        tipoPersona: 'N',
        tipoDocumento: 'D',
        numeroDocumento: '45781234',
        nombre: 'Mariana Delgado',
        rubroComercial: 'Retail',
        consentimientoContacto: true,
        consentimientoUsoDato: true,
      }),
    );
    // El canal lo sella el backend: mandarlo desde la pantalla volvería a
    // convertir en pregunta lo que se decidió no preguntar.
    const enviado = api.registrar.calls.mostRecent().args[0] as Record<string, unknown>;
    expect(enviado['canalAutorizacion'])
      .withContext('la pantalla no manda canal: lo pone el backend')
      .toBeUndefined();
    expect(router.navigate).toHaveBeenCalledWith(['/clientes'], { replaceUrl: true });
  });

  /**
   * La identidad va bloqueada porque el PUT la descarta en silencio: dejarla
   * editable prometería un cambio que el backend no hace.
   */
  it('en edición bloquea tipo de persona, tipo de documento y número', async () => {
    const fixture = await montar('AGENTE', '7');
    const acceso = acceder(fixture);

    expect(acceso.formulario.controls['tipoPersona'].disabled).toBeTrue();
    expect(acceso.formulario.controls['tipoDocumento'].disabled).toBeTrue();
    expect(acceso.formulario.controls['numeroDocumento'].disabled).toBeTrue();
    expect(texto(fixture)).toContain('El documento no se puede modificar');
  });

  it('en edición envía el documento original, no vacío', async () => {
    const fixture = await montar('AGENTE', '7');
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({ telefono: '999888777' });

    await acceso.guardar();

    expect(api.actualizar).toHaveBeenCalledWith(
      7,
      jasmine.objectContaining({ numeroDocumento: '20551234567', telefono: '999888777' }),
    );
  });

  /** El rubro llega de la v1 y no está en la lista sugerida: no se pierde. */
  it('conserva un rubro que no está en el catálogo sugerido', async () => {
    const fixture = await montar('AGENTE', '7');
    const acceso = acceder(fixture);

    expect(acceso.formulario.getRawValue().rubroComercial).toBe('Rubro heredado de la v1');
    const opciones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('select option'),
    ).map((o) => o.textContent?.trim());
    expect(opciones).toContain('Rubro heredado de la v1');
  });

  it('un error del backend se muestra sin perder lo escrito', async () => {
    api.registrar.and.rejectWith(new ApiError(409, 'Ya existe una persona con ese documento.'));
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({
      tipoPersona: 'N',
      tipoDocumento: 'D',
      numeroDocumento: '45781234',
      nombre: 'Mariana Delgado',
      telefono: '987654321',
      correo: 'mariana@demo.test',
      rubroComercial: 'Retail',
      consentimientoUsoDato: true,
    });

    await acceso.guardar();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Ya existe una persona con ese documento.');
    expect(acceso.formulario.getRawValue().nombre).toBe('Mariana Delgado');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------
  // D-27 — autorización de datos: una sola vez, en el alta
  // ------------------------------------------------------------------

  it('sin la casilla de autorización NO llama al backend', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({
      tipoPersona: 'N',
      tipoDocumento: 'D',
      numeroDocumento: '45781234',
      nombre: 'Mariana Delgado',
      telefono: '987654321',
      correo: 'mariana@demo.test',
      rubroComercial: 'Retail',
      consentimientoUsoDato: false,
    });

    await acceso.guardar();
    fixture.detectChanges();

    // Se corta en la pantalla, no se manda para que el backend lo rechace: el
    // usuario tiene que saber por qué antes de enviar.
    expect(api.registrar).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('no se guardará ningún dato');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('el formulario pide UNA casilla, no dos: el consentimiento de contacto se deriva', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({
      tipoPersona: 'N',
      tipoDocumento: 'D',
      numeroDocumento: '45781234',
      nombre: 'Mariana Delgado',
      telefono: '987654321',
      correo: 'mariana@demo.test',
      rubroComercial: 'Retail',
      consentimientoUsoDato: true,
    });

    await acceso.guardar();

    const casillas = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'input[type="checkbox"]',
    );
    expect(casillas.length).toBe(1);
    // La autorización única cubre también las comunicaciones (ámbito 2).
    expect(api.registrar).toHaveBeenCalledWith(
      jasmine.objectContaining({ consentimientoContacto: true, consentimientoUsoDato: true }),
    );
  });

  it('la sección son dos cosas: la casilla y el enlace al aviso — no hay canal', async () => {
    const fixture = await montar();
    fixture.detectChanges();

    const enlace = (fixture.nativeElement as HTMLElement).querySelector('a[href="/privacidad"]');
    expect(enlace).withContext('el enlace al aviso de privacidad es obligatorio').not.toBeNull();
    // El canal se retiró: se lo pone el backend, no el agente.
    expect(select(fixture, 'canalAutorizacion'))
      .withContext('el canal ya no se pregunta')
      .toBeNull();
  });

  it('en edición NO se vuelve a pedir la autorización', async () => {
    const fixture = await montar('AGENTE', '7');
    fixture.detectChanges();

    // Se pide una sola vez, al registrar: la pantalla de edición no la repite.
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('input[type="checkbox"]').length,
    ).toBe(0);
  });

  it('quien no es AGENTE no puede guardar', async () => {
    const fixture = await montar('BROKER');
    const acceso = acceder(fixture);
    acceso.formulario.patchValue({
      tipoPersona: 'N',
      tipoDocumento: 'D',
      numeroDocumento: '45781234',
      nombre: 'Mariana Delgado',
      telefono: '987654321',
      correo: 'mariana@demo.test',
      rubroComercial: 'Retail',
    });

    await acceso.guardar();
    fixture.detectChanges();

    expect(api.registrar).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('son del agente inmobiliario');
  });

  async function montar(rol: RolSesion = 'AGENTE', id?: string): Promise<ComponentFixture<ClienteForm>> {
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
      imports: [ClienteForm],
      providers: [
        { provide: ClientesService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(ClienteForm);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoFormulario {
  formulario: FormGroup;
  guardar(): Promise<void>;
  cambiarTipoPersona(tipo: 'N' | 'J'): void;
}

function acceder(fixture: ComponentFixture<ClienteForm>): AccesoFormulario {
  return fixture.componentInstance as unknown as AccesoFormulario;
}

function texto(fixture: ComponentFixture<ClienteForm>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function select(fixture: ComponentFixture<ClienteForm>, control: string): HTMLSelectElement | null {
  return (fixture.nativeElement as HTMLElement).querySelector(`select[formcontrolname="${control}"]`);
}
