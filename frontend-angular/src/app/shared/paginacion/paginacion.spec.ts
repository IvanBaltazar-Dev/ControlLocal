import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Paginacion } from './paginacion';

/**
 * La ventana de páginas y el rango "Mostrando X–Y de Z" son la única
 * aritmética real de los componentes compartidos, y la que falla en silencio:
 * un off-by-one no rompe nada, solo salta una página o muestra un rango
 * imposible.
 */
describe('Paginacion', () => {
  let fixture: ComponentFixture<Paginacion>;
  let componente: Paginacion;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Paginacion] }).compileComponents();
    fixture = TestBed.createComponent(Paginacion);
    componente = fixture.componentInstance;
  });

  /** Lee lo que se pinta, que es lo que ve el usuario. */
  const render = (total: number, tamano: number, pagina: number) => {
    fixture.componentRef.setInput('total', total);
    fixture.componentRef.setInput('tamano', tamano);
    fixture.componentRef.setInput('pagina', pagina);
    fixture.detectChanges();
    const raiz = fixture.nativeElement as HTMLElement;
    return {
      rango: raiz.querySelector('.rango')!.textContent!.replace(/\s+/g, ' ').trim(),
      botones: [...raiz.querySelectorAll('.botones button, .botones .elipsis')].map((b) =>
        b.textContent!.trim(),
      ),
      activa: raiz.querySelector('.botones .activa')?.textContent?.trim(),
    };
  };

  it('numera desde 1 (el PageResponse del cable es 1-based)', () => {
    expect(render(35, 10, 1).rango).toBe('Mostrando 1–10 de 35 resultados');
    expect(render(35, 10, 2).rango).toBe('Mostrando 11–20 de 35 resultados');
  });

  it('la última página no promete más filas de las que hay', () => {
    expect(render(35, 10, 4).rango).toBe('Mostrando 31–35 de 35 resultados');
  });

  it('con lista vacía muestra 0–0 y una sola página', () => {
    const { rango, botones } = render(0, 10, 1);
    expect(rango).toBe('Mostrando 0–0 de 0 resultados');
    expect(botones).toEqual(['‹', '1', '›']);
  });

  it('hasta 5 páginas las lista todas, sin elipsis', () => {
    expect(render(50, 10, 1).botones).toEqual(['‹', '1', '2', '3', '4', '5', '›']);
  });

  it('a partir de 6 recorta con elipsis alrededor de la actual', () => {
    expect(render(200, 10, 10).botones).toEqual(['‹', '1', '…', '9', '10', '11', '…', '20', '›']);
  });

  it('sustituye la elipsis de un solo hueco por su página', () => {
    // Entre 1 y 3 solo falta la 2: dibujarla cuesta lo mismo y es un clic menos.
    expect(render(200, 10, 3).botones).toEqual(['‹', '1', '2', '3', '4', '…', '20', '›']);
  });

  it('en los extremos mantiene tres vecinas visibles', () => {
    expect(render(200, 10, 1).botones).toEqual(['‹', '1', '2', '3', '…', '20', '›']);
    expect(render(200, 10, 20).botones).toEqual(['‹', '1', '…', '18', '19', '20', '›']);
  });

  it('marca la página actual', () => {
    expect(render(200, 10, 7).activa).toBe('7');
  });

  it('acota una página fuera de rango en vez de romper', () => {
    expect(render(35, 10, 99).rango).toBe('Mostrando 31–35 de 35 resultados');
    expect(render(35, 10, 0).rango).toBe('Mostrando 1–10 de 35 resultados');
  });

  it('deshabilita anterior en la primera y siguiente en la última', () => {
    const raiz = () => fixture.nativeElement as HTMLElement;

    render(50, 10, 1);
    let botones = raiz().querySelectorAll('.botones button');
    expect((botones[0] as HTMLButtonElement).disabled).toBeTrue();
    expect((botones[botones.length - 1] as HTMLButtonElement).disabled).toBeFalse();

    render(50, 10, 5);
    botones = raiz().querySelectorAll('.botones button');
    expect((botones[0] as HTMLButtonElement).disabled).toBeFalse();
    expect((botones[botones.length - 1] as HTMLButtonElement).disabled).toBeTrue();
  });

  it('navegar publica la página nueva', () => {
    render(200, 10, 5);
    const paginas: number[] = [];
    componente.pagina.subscribe((p) => paginas.push(p));

    (fixture.nativeElement as HTMLElement)
      .querySelectorAll('.botones button')
      .forEach((b) => {
        if (b.textContent!.trim() === '6') {
          (b as HTMLButtonElement).click();
        }
      });

    expect(paginas).toEqual([6]);
  });
});
