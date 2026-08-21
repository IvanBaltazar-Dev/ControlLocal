import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CapturaService, PreguntaCaptura } from '../../core/api/captura.service';
import { FilaPropiedad, PropiedadesService } from '../../core/api/propiedades.service';
import { Propiedades } from './propiedades';

const APERTURA: PreguntaCaptura[] = [
  {
    clave: 'tipoPropiedad',
    rotulo: 'Tipo de propiedad',
    familia: 'APERTURA',
    control: 'SELECTOR',
    tipoDato: 'LISTA',
    opciones: ['LOCAL', 'OFICINA', 'DEPARTAMENTO', 'CASA', 'TERRENO', 'ALMACEN', 'OTRO'],
    obligatoria: true,
    orden: 0,
  },
  {
    clave: 'operaciones',
    rotulo: 'Operación',
    familia: 'APERTURA',
    control: 'SELECTOR_MULTIPLE',
    tipoDato: 'LISTA_MULTIPLE',
    opciones: ['VENTA', 'ALQUILER'],
    obligatoria: true,
    orden: 1,
  },
];

function fila(parcial: Partial<FilaPropiedad>): FilaPropiedad {
  return {
    id: 1,
    codigo: 'PROP-0001',
    tipoPropiedad: 'LOCAL',
    tipoRotulo: 'Local comercial',
    direccion: 'Av. La Marina 2450',
    distrito: 'San Miguel',
    metraje: 160,
    estado: 'D',
    propietarioNombre: 'Grupo Aurora',
    titulares: 1,
    encargos: [],
    ...parcial,
  };
}

/** El caso que decide el modelo: una fila, dos encargos. */
const DOBLE = fila({
  id: 10,
  codigo: 'PROP-0010',
  encargos: [
    { operacion: 'VENTA', estado: 'A', importe: 320000, moneda: 'USD' },
    { operacion: 'ALQUILER', estado: 'A', importe: 4800, moneda: 'USD' },
  ],
});

const SOLO_VENTA = fila({
  id: 11,
  codigo: 'PROP-0011',
  tipoPropiedad: 'DEPARTAMENTO',
  tipoRotulo: 'Departamento',
  encargos: [{ operacion: 'VENTA', estado: 'A', importe: 185000, moneda: 'USD' }],
});

const SIN_ENCARGO = fila({ id: 12, codigo: 'PROP-0012', encargos: [] });

describe('Propiedades', () => {
  let fixture: ComponentFixture<Propiedades>;
  let api: jasmine.SpyObj<PropiedadesService>;
  let captura: jasmine.SpyObj<CapturaService>;

  async function montar(filas: FilaPropiedad[] = [DOBLE, SOLO_VENTA, SIN_ENCARGO]): Promise<void> {
    api.listar.and.resolveTo({
      items: filas,
      totalRecords: filas.length,
      page: 1,
      pageSize: 20,
    });
    await TestBed.configureTestingModule({
      imports: [Propiedades],
      providers: [
        { provide: PropiedadesService, useValue: api },
        { provide: CapturaService, useValue: captura },
        provideRouter([]),
        provideHttpClient(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Propiedades);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function celdas(indiceFila: number): string[] {
    const filas = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    return Array.from(filas[indiceFila].querySelectorAll('td')).map((celda) =>
      (celda.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<PropiedadesService>('PropiedadesService', ['listar', 'filtros']);
    api.filtros.and.resolveTo({ distritos: ['Miraflores', 'San Miguel'] });
    captura = jasmine.createSpyObj<CapturaService>('CapturaService', ['apertura']);
    captura.apertura.and.resolveTo(APERTURA);
  });

  it('compone «Venta + alquiler» a partir de los encargos, sin un valor combinado', async () => {
    await montar();

    expect(celdas(0)[3]).toBe('Venta + alquiler');
    expect(celdas(1)[3]).toBe('Venta');
    // Sin encargo vivo NO es «disponible para todo»: es que no hay ninguno, y
    // callarlo la haria parecer ofertada.
    expect(celdas(2)[3]).toBe('Sin encargo');
  });

  it('ensena un importe por encargo, y nunca un total', async () => {
    await montar();

    const precios = celdas(0)[6];
    expect(precios).toContain('Venta: USD 320,000');
    expect(precios).toContain('Alquiler: USD 4,800');
    expect(precios).not.toContain('324,800');
  });

  it('el tipo se pinta con el rotulo que manda el backend', async () => {
    await montar();

    expect(celdas(0)[2]).toBe('Local comercial');
    expect(celdas(1)[2]).toBe('Departamento');
  });

  it('los tipos y las operaciones del filtro salen del motor, no de esta pantalla', async () => {
    await montar();

    expect(captura.apertura).toHaveBeenCalled();
    const selects = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'cl-filtro-select select',
    );
    const valores = (indice: number) =>
      Array.from(selects[indice].querySelectorAll('option')).map((opcion) => opcion.value);

    expect(valores(0)).toEqual([
      '',
      'LOCAL',
      'OFICINA',
      'DEPARTAMENTO',
      'CASA',
      'TERRENO',
      'ALMACEN',
      'OTRO',
    ]);
    // «Venta y alquiler» es un FILTRO compuesto, no una tercera operacion: su
    // valor son las dos, separadas por coma.
    expect(valores(1)).toEqual(['', 'VENTA', 'ALQUILER', 'VENTA,ALQUILER']);
  });

  it('filtrar por «venta y alquiler» pide las DOS al backend y vuelve a la primera pagina', async () => {
    await montar();
    api.listar.calls.reset();

    const operaciones = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'cl-filtro-select select',
    )[1] as HTMLSelectElement;
    operaciones.value = 'VENTA,ALQUILER';
    operaciones.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const enviado = api.listar.calls.mostRecent().args[0];
    expect(enviado?.operaciones).toBe('VENTA,ALQUILER');
    expect(enviado?.pagina).toBe(1);
  });

  it('con copropiedad dice cuantos titulares mas hay', async () => {
    await montar([fila({ id: 20, titulares: 3, propietarioNombre: 'Maria Torres' })]);

    expect(celdas(0)[7]).toContain('Maria Torres');
    expect(celdas(0)[7]).toContain('y 2 más');
  });
});
