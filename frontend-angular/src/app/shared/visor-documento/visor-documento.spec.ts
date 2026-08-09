import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import { DocumentosService } from '../../core/api/documentos.service';
import { VisorDocumento } from './visor-documento';

describe('VisorDocumento', () => {
  let fixture: ComponentFixture<VisorDocumento>;
  let contenido: Subject<Blob>;
  const documentos = {
    contenido$: jasmine.createSpy('contenido$'),
    liberar: jasmine.createSpy('liberar'),
    tipoContenido: jasmine
      .createSpy('tipoContenido')
      .and.callFake((nombre: string) => (nombre.endsWith('.pdf') ? 'application/pdf' : 'image/png')),
  };

  beforeEach(async () => {
    contenido = new Subject<Blob>();
    documentos.contenido$.calls.reset();
    documentos.contenido$.and.returnValue(contenido);
    documentos.liberar.calls.reset();
    spyOn(URL, 'createObjectURL').and.returnValue('blob:documento-prueba');

    await TestBed.configureTestingModule({
      imports: [VisorDocumento],
      providers: [{ provide: DocumentosService, useValue: documentos }],
    }).compileComponents();

    fixture = TestBed.createComponent(VisorDocumento);
    fixture.componentRef.setInput('clave', 'solicitud/clave-opaca');
    fixture.componentRef.setInput('nombreArchivo', 'dni.png');
    fixture.detectChanges();
  });

  it('carga el binario autenticado y solo expone el object URL al elemento visual', () => {
    contenido.next(new Blob(['imagen'], { type: 'image/png' }));
    contenido.complete();
    fixture.detectChanges();

    const imagen = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(documentos.contenido$).toHaveBeenCalledWith('solicitud/clave-opaca');
    expect(imagen).not.toBeNull();
    expect(imagen.getAttribute('src')).toContain('blob:documento-prueba');
    expect(fixture.nativeElement.innerHTML).not.toContain('documentos/contenido?clave=');
  });

  it('cancela la lectura y revoca el object URL al cambiar de documento', () => {
    contenido.next(new Blob(['imagen'], { type: 'image/png' }));
    fixture.detectChanges();

    const siguiente = new Subject<Blob>();
    documentos.contenido$.and.returnValue(siguiente);
    fixture.componentRef.setInput('clave', 'solicitud/otra-clave');
    fixture.detectChanges();

    expect(documentos.liberar).toHaveBeenCalledWith('blob:documento-prueba');
    expect(documentos.contenido$).toHaveBeenCalledWith('solicitud/otra-clave');
  });

  it('muestra un error recuperable y permite reintentar', () => {
    contenido.error(new Error('Almacén temporalmente no disponible.'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Almacén temporalmente no disponible.');

    const reintento = new Subject<Blob>();
    documentos.contenido$.and.returnValue(reintento);
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Cargando documento');
    expect(documentos.contenido$).toHaveBeenCalledTimes(2);
  });
});
