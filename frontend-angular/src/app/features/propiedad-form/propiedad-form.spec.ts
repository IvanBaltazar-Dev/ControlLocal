import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import {
  CapturaService,
  DefinicionCaptura,
  EstadoCaptura,
  PreguntaCaptura,
} from '../../core/api/captura.service';
import { PropietariosService } from '../../core/api/propietarios.service';
import { PropiedadForm } from './propiedad-form';

/**
 * Lo que el motor publica antes de saber nada: las dos decisiones que ordenan
 * el resto. La pantalla NO se las sabe — las pinta.
 */
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

const BORRADOR: EstadoCaptura = {
  idBorrador: 7,
  codigo: 'CAP-0007',
  intencion: 'REGISTRAR_PROPIEDAD',
  estado: 'E',
  conocido: {},
  faltante: ['tipoPropiedad', 'operaciones'],
  siguiente: APERTURA[0],
  listoParaEjecutar: false,
};

function pregunta(clave: string, rotulo: string, extra: Partial<PreguntaCaptura> = {}) {
  return {
    clave,
    rotulo,
    familia: 'TIPO',
    control: 'TEXTO',
    tipoDato: 'TEXTO',
    obligatoria: false,
    orden: 0,
    ...extra,
  } as PreguntaCaptura;
}

/** Un departamento en venta: dormitorios sí, rubro no, un solo bloque económico. */
const DEPARTAMENTO_VENTA: DefinicionCaptura = {
  intencion: 'REGISTRAR_PROPIEDAD',
  tipoPropiedad: 'DEPARTAMENTO',
  operaciones: ['VENTA'],
  comunes: [
    pregunta('titulares', 'Titulares', { familia: 'COMUN', control: 'TITULARES', obligatoria: true }),
    pregunta('direccion', 'Dirección', { familia: 'COMUN', obligatoria: true }),
  ],
  delTipo: [pregunta('dormitorios', 'Dormitorios', { control: 'ENTERO', obligatoria: true })],
  deLaOperacion: [
    {
      operacion: 'VENTA',
      rotulo: 'Condición de venta',
      preguntas: [
        pregunta('importe:VENTA', 'Precio de venta', {
          familia: 'OPERACION',
          control: 'MONEDA',
          obligatoria: true,
        }),
      ],
    },
  ],
};

/** El caso que decide el modelo: una ficha física, dos condiciones económicas. */
const LOCAL_VENTA_Y_ALQUILER: DefinicionCaptura = {
  intencion: 'REGISTRAR_PROPIEDAD',
  tipoPropiedad: 'LOCAL',
  operaciones: ['VENTA', 'ALQUILER'],
  comunes: DEPARTAMENTO_VENTA.comunes,
  delTipo: [pregunta('rubro_permitido', 'Rubro permitido')],
  deLaOperacion: [
    {
      operacion: 'VENTA',
      rotulo: 'Condición de venta',
      preguntas: [
        pregunta('importe:VENTA', 'Precio de venta', {
          familia: 'OPERACION',
          control: 'MONEDA',
          obligatoria: true,
        }),
      ],
    },
    {
      operacion: 'ALQUILER',
      rotulo: 'Condición de alquiler',
      preguntas: [
        pregunta('importe:ALQUILER', 'Renta mensual', {
          familia: 'OPERACION',
          control: 'MONEDA',
          obligatoria: true,
        }),
      ],
    },
  ],
};

describe('PropiedadForm', () => {
  let fixture: ComponentFixture<PropiedadForm>;
  let captura: jasmine.SpyObj<CapturaService>;
  let propietarios: jasmine.SpyObj<PropietariosService>;

  async function montar(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PropiedadForm],
      providers: [
        { provide: CapturaService, useValue: captura },
        { provide: PropietariosService, useValue: propietarios },
        provideRouter([]),
        provideHttpClient(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PropiedadForm);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Contesta el tipo y la operación como lo haría una persona. */
  async function elegir(tipo: string, operaciones: string[]): Promise<void> {
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = tipo;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    for (const operacion of operaciones) {
      const casillas: HTMLInputElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('.opciones input[type=checkbox]'),
      );
      const indice = APERTURA[1].opciones!.indexOf(operacion);
      casillas[indice].checked = true;
      casillas[indice].dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    }
  }

  function raiz(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  /** Un botón por su texto. Es como lo busca una persona. */
  function boton(texto: string): HTMLButtonElement {
    const encontrado = Array.from(raiz().querySelectorAll('button')).find((candidato) =>
      (candidato.textContent ?? '').trim().startsWith(texto),
    );
    if (!encontrado) {
      throw new Error(`No hay ningún botón que empiece por "${texto}"`);
    }
    return encontrado;
  }

  function textoVisible(): string {
    return raiz().textContent ?? '';
  }

  beforeEach(() => {
    captura = jasmine.createSpyObj<CapturaService>('CapturaService', [
      'apertura',
      'abrir',
      'definicion',
      'avanzar',
      'ejecutar',
      'descartar',
    ]);
    captura.apertura.and.resolveTo(APERTURA);
    captura.abrir.and.resolveTo(BORRADOR);
    captura.definicion.and.resolveTo(DEPARTAMENTO_VENTA);

    propietarios = jasmine.createSpyObj<PropietariosService>('PropietariosService', [
      'pagina',
      'registrar',
    ]);
    propietarios.pagina.and.resolveTo({ items: [], totalRecords: 0, page: 1, pageSize: 50 });
  });

  it('abre un borrador y pinta las dos decisiones que el motor declara', async () => {
    await montar();

    expect(captura.abrir).toHaveBeenCalled();
    expect(textoVisible()).toContain('Tipo de propiedad');
    expect(textoVisible()).toContain('Operación');
    // Los siete tipos salen del contrato, no de una lista escrita aquí.
    const opciones: HTMLOptionElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('select option'),
    );
    expect(opciones.map((o) => o.value)).toEqual([
      '',
      'LOCAL',
      'OFICINA',
      'DEPARTAMENTO',
      'CASA',
      'TERRENO',
      'ALMACEN',
      'OTRO',
    ]);
  });

  /**
   * El **tipo** manda: es quien decide qué se pregunta. La operación ya no,
   * desde V75: una propiedad puede registrarse sin encargo —para prospectarla—
   * y exigirla aquí dejaba la pantalla parada en la apertura, con el backend
   * aceptando `operaciones: []` y BROX Web sin forma de mandarlo.
   */
  it('pide la definición en cuanto hay tipo, aunque no haya operación', async () => {
    await montar();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = 'DEPARTAMENTO';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(captura.definicion).toHaveBeenCalledWith('DEPARTAMENTO', '');
  });

  it('sin operación, avisa de que la propiedad queda registrada y no se ofrece', async () => {
    await montar();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = 'DEPARTAMENTO';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(textoVisible()).toContain('no se ofrece');
  });

  it('no pide la definición mientras no haya tipo', async () => {
    await montar();
    expect(captura.definicion).not.toHaveBeenCalled();
  });

  it('pinta lo que el catálogo dice para departamento en venta, y nada más', async () => {
    await montar();
    await elegir('DEPARTAMENTO', ['VENTA']);

    expect(captura.definicion).toHaveBeenCalledWith('DEPARTAMENTO', 'VENTA');
    expect(textoVisible()).toContain('Dormitorios');
    expect(textoVisible()).toContain('Precio de venta');
    expect(textoVisible()).not.toContain('Rubro');
    expect(textoVisible()).not.toContain('Renta mensual');
  });

  it('venta y alquiler produce DOS bloques económicos y una sola ficha física', async () => {
    captura.definicion.and.resolveTo(LOCAL_VENTA_Y_ALQUILER);
    await montar();
    await elegir('LOCAL', ['VENTA', 'ALQUILER']);

    expect(captura.definicion).toHaveBeenCalledWith('LOCAL', 'VENTA,ALQUILER');

    const bloques = fixture.nativeElement.querySelectorAll('.bloque-economico');
    expect(bloques.length).toBe(2);
    expect(textoVisible()).toContain('Condición de venta');
    expect(textoVisible()).toContain('Condición de alquiler');
    expect(textoVisible()).toContain('Precio de venta');
    expect(textoVisible()).toContain('Renta mensual');

    // La dirección se pregunta UNA vez: es una propiedad, no dos.
    const direcciones = Array.from(
      fixture.nativeElement.querySelectorAll('label.campo span'),
    ).filter((s) => (s as HTMLElement).textContent?.includes('Dirección'));
    expect(direcciones.length).toBe(1);
  });

  it('al cambiar de tipo descarta lo que ya no aplica', async () => {
    captura.definicion.and.resolveTo(LOCAL_VENTA_Y_ALQUILER);
    await montar();
    await elegir('LOCAL', ['VENTA']);

    const rubro: HTMLInputElement = Array.from(
      fixture.nativeElement.querySelectorAll('label.campo'),
    )
      .map((l) => l as HTMLElement)
      .find((l) => l.textContent?.includes('Rubro permitido'))!
      .querySelector('input')!;
    rubro.value = 'Restaurante';
    rubro.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // Ahora es un terreno: el rubro no aplica y no puede viajar escondido.
    captura.definicion.and.resolveTo(DEPARTAMENTO_VENTA);
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = 'DEPARTAMENTO';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    captura.avanzar.and.resolveTo({ ...BORRADOR, faltante: [], listoParaEjecutar: true });
    boton('Revisar').click();
    await fixture.whenStable();

    const enviado = captura.avanzar.calls.mostRecent().args[1];
    expect(enviado['rubro_permitido']).toBeUndefined();
  });

  it('confirma con la MISMA clave de idempotencia si se reintenta', async () => {
    await montar();
    await elegir('DEPARTAMENTO', ['VENTA']);

    captura.avanzar.and.resolveTo({ ...BORRADOR, faltante: [], listoParaEjecutar: true });
    boton('Revisar').click();
    await fixture.whenStable();
    fixture.detectChanges();

    captura.ejecutar.and.rejectWith(new Error('se cayo la red'));
    boton('Confirmar').click();
    await fixture.whenStable();
    fixture.detectChanges();
    boton('Confirmar').click();
    await fixture.whenStable();

    const claves = captura.ejecutar.calls.all().map((llamada) => llamada.args[1]);
    expect(claves.length).toBe(2);
    expect(claves[0]).toBe(claves[1]);
  });

  it('descarta el borrador al salir: no deja una propiedad a medias', async () => {
    await montar();
    captura.descartar.and.resolveTo({ ...BORRADOR, estado: 'D' });

    boton('Volver').click();
    await fixture.whenStable();

    expect(captura.descartar).toHaveBeenCalledWith(7);
  });
});
