import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BarraFiltros } from './barra-filtros';

describe('BarraFiltros', () => {
  let fixture: ComponentFixture<BarraFiltros>;
  let componente: BarraFiltros;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BarraFiltros] }).compileComponents();
    fixture = TestBed.createComponent(BarraFiltros);
    componente = fixture.componentInstance;
  });

  it('limpia todos los filtros con un solo evento del padre', () => {
    fixture.componentRef.setInput('busqueda', 'camana');
    fixture.componentRef.setInput('hayFiltros', true);
    fixture.detectChanges();

    const cambiosBusqueda: string[] = [];
    let limpiezas = 0;
    componente.busqueda.subscribe((valor) => cambiosBusqueda.push(valor));
    componente.limpiar.subscribe(() => limpiezas++);

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.limpiar')!.click();

    expect(limpiezas).toBe(1);
    expect(cambiosBusqueda).toEqual([]);
  });
});
