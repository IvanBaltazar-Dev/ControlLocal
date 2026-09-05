import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { PestanasRuta } from './pestanas-ruta';

@Component({ template: '' })
class Vacio {}

/**
 * Las pestañas de una página con **dos caras**: Indicadores mide por actor y
 * el avance por propiedad mide por inmueble, y eran dos entradas de menú para
 * una sola pregunta (D-E3-1 §4.3).
 *
 * Lo que esta prueba fija es por qué son <b>enlaces</b> y no botones: cada
 * cara conserva su URL, así que un enlace guardado sigue abriendo la que se
 * guardó y el botón «atrás» del navegador funciona. Y que la pestaña activa
 * la marca el router, no un `@if` de la pantalla.
 */
describe('PestanasRuta', () => {
  const PESTANAS = [
    { etiqueta: 'Rendimiento', ruta: '/indicadores' },
    { etiqueta: 'Avance por propiedad', ruta: '/reportes' },
  ];

  let fixture: ComponentFixture<PestanasRuta>;

  function raiz(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function enlaces(): HTMLAnchorElement[] {
    return Array.from(raiz().querySelectorAll<HTMLAnchorElement>('.cl-pestanas a'));
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'indicadores', component: Vacio },
          { path: 'reportes', component: Vacio },
        ]),
      ],
    });
    fixture = TestBed.createComponent(PestanasRuta);
    fixture.componentRef.setInput('pestanas', PESTANAS);
    fixture.detectChanges();
  });

  it('dibuja cada cara como un enlace con su propia URL', () => {
    expect(enlaces().map((a) => a.textContent?.trim())).toEqual([
      'Rendimiento',
      'Avance por propiedad',
    ]);
    expect(enlaces().map((a) => a.getAttribute('href'))).toEqual(['/indicadores', '/reportes']);
  });

  it('anuncia el grupo con la etiqueta que se le pase', () => {
    fixture.componentRef.setInput('etiqueta', 'Vistas de Indicadores');
    fixture.detectChanges();

    expect(raiz().querySelector('.cl-pestanas')?.getAttribute('aria-label')).toBe(
      'Vistas de Indicadores',
    );
  });

  /**
   * `aria-current="page"` y la clase van juntas y solo en la cara abierta: dos
   * pestañas marcadas a la vez es lo mismo que ninguna.
   */
  it('marca activa la cara abierta, y solo esa', async () => {
    await TestBed.inject(Router).navigateByUrl('/reportes');
    fixture.detectChanges();

    const marcados = enlaces().filter((a) => a.classList.contains('activa'));
    expect(marcados.map((a) => a.textContent?.trim())).toEqual(['Avance por propiedad']);
    expect(marcados[0].getAttribute('aria-current')).toBe('page');
    expect(enlaces()[0].getAttribute('aria-current')).toBeNull();
  });

  it('cambia la marca al navegar a la otra cara', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/reportes');
    fixture.detectChanges();
    await router.navigateByUrl('/indicadores');
    fixture.detectChanges();

    expect(
      enlaces()
        .filter((a) => a.classList.contains('activa'))
        .map((a) => a.textContent?.trim()),
    ).toEqual(['Rendimiento']);
  });
});
