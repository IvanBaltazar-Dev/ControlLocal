import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

/**
 * `App` es solo el contenedor del `<router-outlet>`: lo que se ve lo ponen el
 * shell y las pantallas. Por eso aquí no se comprueba contenido — el test de
 * andamiaje que buscaba el "Hello, controllocal-web" de `ng new` quedó
 * obsoleto al construir el shell, y en rojo tapaba fallos reales.
 */
describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('se crea', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('monta el router-outlet donde entran las pantallas', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compilado = fixture.nativeElement as HTMLElement;
    expect(compilado.querySelector('router-outlet')).not.toBeNull();
  });
});
