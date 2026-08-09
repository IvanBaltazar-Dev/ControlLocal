import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SubidaArchivo } from './subida-archivo';

describe('SubidaArchivo', () => {
  let fixture: ComponentFixture<SubidaArchivo>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubidaArchivo],
    }).compileComponents();
    fixture = TestBed.createComponent(SubidaArchivo);
    fixture.componentRef.setInput('extensiones', ['.pdf']);
    fixture.detectChanges();
  });

  it('emite únicamente archivos que pasaron la validación compartida', async () => {
    const recibidos: string[] = [];
    fixture.componentInstance.seleccion.subscribe((archivos) =>
      recibidos.push(...archivos.map((archivo) => archivo.nombreSeguro)),
    );
    const archivo = new File(
      [new Uint8Array([0x25, 0x50, 0x44, 0x46])],
      'dni final.pdf',
      { type: 'application/pdf' },
    );

    await seleccionar(fixture, [archivo]);

    expect(recibidos).toEqual(['dni_final.pdf']);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('muestra y emite el error sin entregar archivos inválidos', async () => {
    const seleccion = jasmine.createSpy('seleccion');
    const fallo = jasmine.createSpy('fallo');
    fixture.componentInstance.seleccion.subscribe(seleccion);
    fixture.componentInstance.fallo.subscribe(fallo);

    await seleccionar(
      fixture,
      [new File(['contenido falso'], 'dni.pdf', { type: 'application/pdf' })],
    );

    expect(seleccion).not.toHaveBeenCalled();
    expect(fallo).toHaveBeenCalledWith(
      'El contenido del archivo no corresponde a su extensión.',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'no corresponde',
    );
  });
});

async function seleccionar(
  fixture: ComponentFixture<SubidaArchivo>,
  archivos: readonly File[],
): Promise<void> {
  const componente = fixture.componentInstance as unknown as {
    alSeleccionar(evento: Event): Promise<void>;
  };
  await componente.alSeleccionar({
    target: { files: archivos, value: 'seleccionado' },
  } as unknown as Event);
  fixture.detectChanges();
}
