import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PanelLateral } from './panel-lateral';

@Component({
  imports: [PanelLateral],
  template: `
    <cl-panel-lateral [abierto]="abierto()" titulo="Bandeja" (cerrar)="cierres = cierres + 1">
      <p class="contenido">contenido proyectado</p>
    </cl-panel-lateral>
  `,
})
class Anfitrion {
  readonly abierto = signal(false);
  cierres = 0;
}

describe('PanelLateral', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let anfitrion: Anfitrion;
  let raiz: HTMLElement;

  function escape(): void {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Anfitrion] }).compileComponents();
    fixture = TestBed.createComponent(Anfitrion);
    anfitrion = fixture.componentInstance;
    raiz = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    // Una fuga de `overflow: hidden` deja sin scroll a las specs siguientes.
    document.body.style.overflow = '';
  });

  it('no monta nada hasta que se abre', () => {
    expect(raiz.querySelector('.panel')).toBeNull();

    anfitrion.abierto.set(true);
    fixture.detectChanges();

    expect(raiz.querySelector('.panel')).not.toBeNull();
    expect(raiz.querySelector('.contenido')?.textContent).toContain('contenido proyectado');
  });

  /** El scroll es del panel: si el documento también scrollea, la rueda mueve la página de detrás. */
  it('bloquea el scroll del documento y lo devuelve al cerrar', () => {
    anfitrion.abierto.set(true);
    fixture.detectChanges();
    expect(document.body.style.overflow).toBe('hidden');

    anfitrion.abierto.set(false);
    fixture.detectChanges();
    expect(document.body.style.overflow).toBe('');
  });

  /**
   * El caso real: "Resolver" navega fuera con el panel abierto y destruye el
   * componente sin pasar por `cerrar`. Sin el `ngOnDestroy`, la aplicación
   * entera se queda sin scroll.
   */
  it('destruirlo abierto no deja la pagina sin scroll', () => {
    anfitrion.abierto.set(true);
    fixture.detectChanges();

    fixture.destroy();

    expect(document.body.style.overflow).toBe('');
  });

  it('ESC pide el cierre, y solo mientras esta abierto', () => {
    escape();
    expect(anfitrion.cierres).toBe(0);

    anfitrion.abierto.set(true);
    fixture.detectChanges();
    escape();

    expect(anfitrion.cierres).toBe(1);
  });

  /** Quien cierra es el padre: el panel pide, no se cierra solo. */
  it('el fondo pide el cierre sin ocultarse por su cuenta', () => {
    anfitrion.abierto.set(true);
    fixture.detectChanges();

    raiz.querySelector<HTMLElement>('.fondo')!.click();
    fixture.detectChanges();

    expect(anfitrion.cierres).toBe(1);
    expect(raiz.querySelector('.panel')).not.toBeNull();
  });
});
