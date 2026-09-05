import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { BroxPageHeader } from './page-header';

/**
 * `BroxPageHeader` es la cabecera de **50 pantallas**. Antes cada una se
 * pintaba su miga a mano y por eso había cuatro tipografías para lo mismo;
 * ahora la miga es dato y el dibujo lo decide un solo sitio.
 *
 * Eso concentra el riesgo: un fallo aquí no se ve en una pantalla, se ve en
 * todas. Lo que fija esta prueba es el contrato del que dependen las 50 —qué
 * se dibuja, qué es enlace y qué no, y que lo proyectado llega a su ranura—,
 * no el aspecto, que es CSS.
 */
describe('BroxPageHeader', () => {
  /** Un anfitrión para poder probar las tres ranuras de proyección. */
  @Component({
    imports: [BroxPageHeader],
    template: `
      <brox-page-header [miga]="['Oferta', 'Propiedades']" titulo="Cartera">
        <p class="bajada-propia">Lo que proyecta la pantalla</p>
        <div acciones class="acciones"><button type="button">Registrar</button></div>
      </brox-page-header>
    `,
  })
  class Anfitrion {}

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  /** Monta la cabecera con las entradas que se le pasen y devuelve su DOM. */
  function montar(entradas: Record<string, unknown> = {}): HTMLElement {
    const fixture = TestBed.createComponent(BroxPageHeader);
    for (const [nombre, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nombre, valor);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const clases = (elemento: Element | null | undefined): string[] =>
    Array.from(elemento?.classList ?? []);

  // ------------------------------------------------------------------
  // La miga
  // ------------------------------------------------------------------

  it('acepta la miga como un texto suelto y la pinta en un solo paso', () => {
    const nodo = montar({ miga: 'Cierre · Solicitudes' });

    expect(nodo.querySelector('.miga')?.textContent?.trim()).toBe('Cierre · Solicitudes');
    expect(nodo.querySelectorAll('.miga .sep').length).toBe(0);
  });

  it('pinta un separador ENTRE pasos, nunca al final', () => {
    const nodo = montar({ miga: ['Oferta', 'Propiedades', 'Editar'] });

    expect(nodo.querySelectorAll('.miga .sep').length).toBe(2);
    expect(nodo.querySelector('.miga .sep')?.textContent).toBe('·');
    // Decorativo: lo que el lector de pantalla anuncia son los pasos.
    expect(nodo.querySelector('.miga .sep')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('respeta el separador que pide la pantalla', () => {
    const nodo = montar({ miga: ['Propiedades', 'Editar'], separador: '›' });

    expect(nodo.querySelector('.miga .sep')?.textContent).toBe('›');
  });

  /**
   * Un paso con ruta es un <b>enlace de verdad</b>, no un botón que navega: se
   * puede abrir en otra pestaña y el lector de pantalla lo anuncia como
   * enlace. Un paso sin ruta no es pulsable, y prometer lo contrario sería
   * peor que no ofrecerlo.
   */
  it('hace enlace el paso que tiene ruta y texto el que no', () => {
    const nodo = montar({
      miga: [
        { etiqueta: 'Propiedades', ruta: '/propiedades' },
        { etiqueta: 'PRO-0001', ruta: ['/propiedades', 7] },
        { etiqueta: 'Editar' },
      ],
    });

    const enlaces = Array.from(nodo.querySelectorAll<HTMLAnchorElement>('.miga a'));
    expect(enlaces.map((a) => a.textContent?.trim())).toEqual(['Propiedades', 'PRO-0001']);
    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual([
      '/propiedades',
      '/propiedades/7',
    ]);
    expect(nodo.querySelector('.miga span:not(.sep)')?.textContent?.trim()).toBe('Editar');
  });

  it('sin miga no dibuja la fila', () => {
    expect(montar().querySelector('.miga')).toBeNull();
  });

  /**
   * Se comprueban los PASOS, uno a uno, y no el texto de la fila entera: el
   * aire alrededor del `·` lo pone el margen de `.sep`, no un espacio escrito
   * —Angular borra los nodos de texto en blanco de la plantilla, así que un
   * `·` con espacios se pintaría pegado a las dos palabras—. Medir la cadena
   * concatenada sería medir el CSS, que es lo que esta prueba no hace.
   */
  it('descarta los pasos sin etiqueta en vez de pintar un hueco', () => {
    const nodo = montar({ miga: ['Oferta', '', { etiqueta: '' }, 'Propiedades'] });

    expect(nodo.querySelectorAll('.miga .sep').length).toBe(1);
    const pasos = Array.from(nodo.querySelectorAll('.miga > a, .miga > span:not(.sep)'));
    expect(pasos.map((paso) => paso.textContent?.trim())).toEqual(['Oferta', 'Propiedades']);
  });

  // ------------------------------------------------------------------
  // Título y bajada
  // ------------------------------------------------------------------

  /**
   * El `<h1>` vacío se omite a propósito: las pantallas cuyo título lleva
   * insignia o formato proyectan el suyo con `[titulo]`, y dos `<h1>` en la
   * misma cabecera dejarían el documento con dos encabezados de nivel uno.
   */
  it('no dibuja un <h1> ni una bajada vacíos', () => {
    const nodo = montar();

    expect(nodo.querySelector('h1')).toBeNull();
    expect(nodo.querySelector('.sub')).toBeNull();
  });

  it('dibuja título y bajada cuando llegan como dato', () => {
    const nodo = montar({
      titulo: 'Cartera de propiedades',
      sub: 'Lo que hay para vender y alquilar',
    });

    expect(nodo.querySelector('h1')?.textContent?.trim()).toBe('Cartera de propiedades');
    expect(nodo.querySelector('.sub')?.textContent?.trim()).toBe(
      'Lo que hay para vender y alquilar',
    );
  });

  // ------------------------------------------------------------------
  // Las ranuras
  // ------------------------------------------------------------------

  it('recoge lo proyectado: la bajada rica dentro y las acciones a la derecha', () => {
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    const cabecera = (fixture.nativeElement as HTMLElement).querySelector('.cl-cabecera')!;

    expect(cabecera.querySelector('.bajada-propia')?.textContent?.trim()).toBe(
      'Lo que proyecta la pantalla',
    );
    // Las acciones son hijas DIRECTAS de la cabecera, no del bloque del
    // título: es lo que las manda a la derecha.
    expect(cabecera.querySelector(':scope > .acciones')).not.toBeNull();
    expect(cabecera.querySelector('h1')?.textContent?.trim()).toBe('Cartera');
  });

  // ------------------------------------------------------------------
  // La variante
  // ------------------------------------------------------------------

  /**
   * `prototipo` no es un tema alternativo: es la métrica que las cuatro
   * pantallas portadas de `docs/ai/prototipos/` ya tenían. Unificar el
   * componente no podía costarles su tipografía.
   */
  it('marca la variante del prototipo con su clase y deja la estándar sin ella', () => {
    expect(clases(montar().querySelector('.cl-cabecera'))).not.toContain('cl-cabecera-proto');

    const proto = montar({ variante: 'prototipo' });
    expect(clases(proto.querySelector('.cl-cabecera'))).toContain('cl-cabecera-proto');
  });
});
