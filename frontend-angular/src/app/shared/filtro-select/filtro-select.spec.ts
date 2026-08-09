import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FiltroSelect } from './filtro-select';

describe('FiltroSelect', () => {
  let fixture: ComponentFixture<FiltroSelect>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FiltroSelect],
    }).compileComponents();

    fixture = TestBed.createComponent(FiltroSelect);
    fixture.componentRef.setInput('opciones', [
      { valor: 'D', etiqueta: 'Disponible' },
      { valor: 'N', etiqueta: 'No disponible' },
    ]);
    fixture.detectChanges();
  });

  it('sincroniza el valor cuando llega después de renderizar las opciones', () => {
    fixture.componentRef.setInput('valor', 'D');
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.value).toBe('D');
  });
});
