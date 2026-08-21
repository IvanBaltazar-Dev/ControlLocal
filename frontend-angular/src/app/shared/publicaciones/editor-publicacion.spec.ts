import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import {
  EncargosService,
  Publicacion,
  PublicacionRequest,
} from '../../core/api/encargos.service';
import { EditorPublicacion, montoFinito } from './editor-publicacion';

const EXISTENTE: Publicacion = {
  id: 3,
  canal: 'URBANIA',
  tituloAnuncio: 'Local en Larco',
  importePublicado: 9000,
  moneda: 'PEN',
  estado: 'P',
  codigoOrigen: 'URBANIA-77',
};

interface AccesoEditor {
  formulario: FormGroup;
  guardar(): Promise<void>;
}

/** Anfitrión mínimo: el editor recibe sus entradas como cualquier padre real. */
@Component({
  imports: [EditorPublicacion],
  template: `
    <cl-editor-publicacion
      [abierto]="true"
      [idEncargo]="77"
      [publicacion]="publicacion()"
    />
  `,
})
class Anfitrion {
  readonly publicacion = signal<Publicacion | null>(null);
}

describe('EditorPublicacion', () => {
  let encargos: jasmine.SpyObj<EncargosService>;

  beforeEach(() => {
    encargos = jasmine.createSpyObj<EncargosService>('EncargosService', [
      'crearPublicacion',
      'actualizarPublicacion',
    ]);
    encargos.crearPublicacion.and.resolveTo(EXISTENTE);
    encargos.actualizarPublicacion.and.resolveTo(EXISTENTE);
  });

  it('no publica sin renta: es obligatoria, no se rellena con 0', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ canal: 'URBANIA', importePublicado: null });

    await editor.guardar();

    expect(encargos.crearPublicacion).not.toHaveBeenCalled();
    expect(editor.formulario.controls['importePublicado'].invalid).toBeTrue();
  });

  it('rechaza una renta negativa', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ importePublicado: -1 });

    await editor.guardar();

    expect(encargos.crearPublicacion).not.toHaveBeenCalled();
  });

  it('rechaza NaN, que required y min dejan pasar', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ importePublicado: Number.NaN });

    await editor.guardar();

    expect(encargos.crearPublicacion).not.toHaveBeenCalled();
  });

  it('envía la renta tal cual, sin coaccionarla', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({
      canal: 'ADONDEVIVIR',
      importePublicado: 3500.5,
      moneda: 'USD',
      tituloAnuncio: '  Local esquina  ',
      urlPublicacion: '  https://ejemplo.test/aviso  ',
    });

    await editor.guardar();

    const [idEncargo, datos] = encargos.crearPublicacion.calls.mostRecent().args as [
      number,
      PublicacionRequest,
    ];
    expect(idEncargo).toBe(77);
    expect(datos).toEqual({
      canal: 'ADONDEVIVIR',
      urlPublicacion: 'https://ejemplo.test/aviso',
      importePublicado: 3500.5,
      moneda: 'USD',
      tituloAnuncio: 'Local esquina',
      // En blanco el backend lo genera; el estado por defecto del alta es P.
      codigoOrigen: null,
      estado: 'P',
    });
  });

  it('el cero sí es una renta válida cuando el usuario la escribe', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ importePublicado: 0, moneda: 'PEN' });

    await editor.guardar();

    const [, datos] = encargos.crearPublicacion.calls.mostRecent().args as [
      number,
      PublicacionRequest,
    ];
    expect(datos.importePublicado).toBe(0);
  });

  it('no asume PEN al crear: la moneda debe seleccionarse', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ importePublicado: 3500, moneda: '' });

    await editor.guardar();

    expect(encargos.crearPublicacion).not.toHaveBeenCalled();
    expect(editor.formulario.controls['moneda'].invalid).toBeTrue();
  });

  it('al editar conserva código de origen y estado, y actualiza el recurso exacto', async () => {
    const editor = await montar(EXISTENTE);
    editor.formulario.patchValue({ importePublicado: 9500 });

    await editor.guardar();

    const [idEncargo, idPublicacion, datos] =
      encargos.actualizarPublicacion.calls.mostRecent().args as [
        number,
        number,
        PublicacionRequest,
      ];
    expect([idEncargo, idPublicacion]).toEqual([77, 3]);
    expect(datos.importePublicado).toBe(9500);
    expect(datos.codigoOrigen).toBe('URBANIA-77');
    expect(datos.estado).toBe('P');
    expect(encargos.crearPublicacion).not.toHaveBeenCalled();
  });

  it('precarga la publicación que se edita', async () => {
    const editor = await montar(EXISTENTE);

    expect(editor.formulario.getRawValue()).toEqual(
      jasmine.objectContaining({
        canal: 'URBANIA',
        tituloAnuncio: 'Local en Larco',
        importePublicado: 9000,
        moneda: 'PEN',
      }),
    );
  });

  describe('montoFinito', () => {
    it('deja pasar la ausencia: de eso se ocupa required', () => {
      expect(montoFinito(new FormControl<number | null>(null))).toBeNull();
    });

    it('marca NaN e infinito', () => {
      expect(montoFinito(new FormControl(Number.NaN))).toEqual({ montoNoFinito: true });
      expect(montoFinito(new FormControl(Number.POSITIVE_INFINITY))).toEqual({
        montoNoFinito: true,
      });
    });

    it('acepta un importe normal', () => {
      expect(montoFinito(new FormControl(1200.75))).toBeNull();
    });
  });

  async function montar(publicacion: Publicacion | null): Promise<AccesoEditor> {
    TestBed.configureTestingModule({
      imports: [Anfitrion],
      providers: [{ provide: EncargosService, useValue: encargos }],
    });

    const fixture: ComponentFixture<Anfitrion> = TestBed.createComponent(Anfitrion);
    fixture.componentInstance.publicacion.set(publicacion);
    fixture.detectChanges();
    await fixture.whenStable();

    const editor = fixture.debugElement.children[0].componentInstance as EditorPublicacion;
    return editor as unknown as AccesoEditor;
  }
});
