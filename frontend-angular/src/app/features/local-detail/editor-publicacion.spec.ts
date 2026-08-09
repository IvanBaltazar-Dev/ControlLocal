import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import {
  LocalesService,
  Publicacion,
  PublicacionRequest,
} from '../../core/api/locales.service';
import { EditorPublicacion, montoFinito } from './editor-publicacion';

const EXISTENTE: Publicacion = {
  id: 3,
  canal: 'URBANIA',
  tituloAnuncio: 'Local en Larco',
  rentaPublicada: 9000,
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
    <app-editor-publicacion
      [abierto]="true"
      [idLocal]="77"
      [publicacion]="publicacion()"
    />
  `,
})
class Anfitrion {
  readonly publicacion = signal<Publicacion | null>(null);
}

describe('EditorPublicacion', () => {
  let locales: jasmine.SpyObj<LocalesService>;

  beforeEach(() => {
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', [
      'crearPublicacion',
      'actualizarPublicacion',
    ]);
    locales.crearPublicacion.and.resolveTo(EXISTENTE);
    locales.actualizarPublicacion.and.resolveTo(EXISTENTE);
  });

  it('no publica sin renta: es obligatoria, no se rellena con 0', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ canal: 'URBANIA', rentaPublicada: null });

    await editor.guardar();

    expect(locales.crearPublicacion).not.toHaveBeenCalled();
    expect(editor.formulario.controls['rentaPublicada'].invalid).toBeTrue();
  });

  it('rechaza una renta negativa', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ rentaPublicada: -1 });

    await editor.guardar();

    expect(locales.crearPublicacion).not.toHaveBeenCalled();
  });

  it('rechaza NaN, que required y min dejan pasar', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ rentaPublicada: Number.NaN });

    await editor.guardar();

    expect(locales.crearPublicacion).not.toHaveBeenCalled();
  });

  it('envía la renta tal cual, sin coaccionarla', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({
      canal: 'ADONDEVIVIR',
      rentaPublicada: 3500.5,
      moneda: 'USD',
      tituloAnuncio: '  Local esquina  ',
      urlPublicacion: '  https://ejemplo.test/aviso  ',
    });

    await editor.guardar();

    const [idLocal, datos] = locales.crearPublicacion.calls.mostRecent().args as [
      number,
      PublicacionRequest,
    ];
    expect(idLocal).toBe(77);
    expect(datos).toEqual({
      canal: 'ADONDEVIVIR',
      urlPublicacion: 'https://ejemplo.test/aviso',
      rentaPublicada: 3500.5,
      moneda: 'USD',
      tituloAnuncio: 'Local esquina',
      // En blanco el backend lo genera; el estado por defecto del alta es P.
      codigoOrigen: null,
      estado: 'P',
    });
  });

  it('el cero sí es una renta válida cuando el usuario la escribe', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ rentaPublicada: 0, moneda: 'PEN' });

    await editor.guardar();

    const [, datos] = locales.crearPublicacion.calls.mostRecent().args as [
      number,
      PublicacionRequest,
    ];
    expect(datos.rentaPublicada).toBe(0);
  });

  it('no asume PEN al crear: la moneda debe seleccionarse', async () => {
    const editor = await montar(null);
    editor.formulario.patchValue({ rentaPublicada: 3500, moneda: '' });

    await editor.guardar();

    expect(locales.crearPublicacion).not.toHaveBeenCalled();
    expect(editor.formulario.controls['moneda'].invalid).toBeTrue();
  });

  it('al editar conserva código de origen y estado, y actualiza el recurso exacto', async () => {
    const editor = await montar(EXISTENTE);
    editor.formulario.patchValue({ rentaPublicada: 9500 });

    await editor.guardar();

    const [idLocal, idPublicacion, datos] =
      locales.actualizarPublicacion.calls.mostRecent().args as [
        number,
        number,
        PublicacionRequest,
      ];
    expect([idLocal, idPublicacion]).toEqual([77, 3]);
    expect(datos.rentaPublicada).toBe(9500);
    expect(datos.codigoOrigen).toBe('URBANIA-77');
    expect(datos.estado).toBe('P');
    expect(locales.crearPublicacion).not.toHaveBeenCalled();
  });

  it('precarga la publicación que se edita', async () => {
    const editor = await montar(EXISTENTE);

    expect(editor.formulario.getRawValue()).toEqual(
      jasmine.objectContaining({
        canal: 'URBANIA',
        tituloAnuncio: 'Local en Larco',
        rentaPublicada: 9000,
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
      providers: [{ provide: LocalesService, useValue: locales }],
    });

    const fixture: ComponentFixture<Anfitrion> = TestBed.createComponent(Anfitrion);
    fixture.componentInstance.publicacion.set(publicacion);
    fixture.detectChanges();
    await fixture.whenStable();

    const editor = fixture.debugElement.children[0].componentInstance as EditorPublicacion;
    return editor as unknown as AccesoEditor;
  }
});
