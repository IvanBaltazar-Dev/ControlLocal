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
    // Como las publica el Core desde el Corte 0B: valor y rótulo.
    opciones: ['LOCAL', 'OFICINA', 'DEPARTAMENTO', 'CASA', 'TERRENO', 'ALMACEN', 'OTRO'].map(
      (valor) => ({ valor, rotulo: valor.charAt(0) + valor.slice(1).toLowerCase() }),
    ),
    obligatoria: true,
    orden: 0,
  },
  {
    clave: 'operaciones',
    rotulo: 'Operación',
    familia: 'APERTURA',
    control: 'SELECTOR_MULTIPLE',
    tipoDato: 'LISTA_MULTIPLE',
    opciones: [
      { valor: 'VENTA', rotulo: 'Venta' },
      { valor: 'ALQUILER', rotulo: 'Alquiler' },
    ],
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
          control: 'IMPORTE',
          tipoDato: 'IMPORTE',
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
          control: 'IMPORTE',
          tipoDato: 'IMPORTE',
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
          control: 'IMPORTE',
          tipoDato: 'IMPORTE',
          obligatoria: true,
        }),
      ],
    },
  ],
};

const DEPARTAMENTO_VENTA_Y_MANTENIMIENTO: DefinicionCaptura = {
  ...DEPARTAMENTO_VENTA,
  delTipo: [
    ...DEPARTAMENTO_VENTA.delTipo,
    pregunta('cuota_mantenimiento', 'Cuota de mantenimiento', {
      control: 'IMPORTE',
      tipoDato: 'IMPORTE',
    }),
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
      const indice = APERTURA[1].opciones!.findIndex((opcion) => opcion.valor === operacion);
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
      'enCurso',
      'abrir',
      'consultar',
      'definicion',
      'avanzar',
      'prepararRevision',
      'confirmarRevision',
      'consultarActuacion',
      'referenciaPendiente',
      'olvidarReferencia',
      'cargarContextoReferencias',
      'referenciasPendientes',
      'descartar',
    ]);
    captura.apertura.and.resolveTo(APERTURA);
    captura.enCurso.and.resolveTo([]);
    captura.abrir.and.resolveTo(BORRADOR);
    captura.consultar.and.resolveTo(BORRADOR);
    captura.definicion.and.resolveTo(DEPARTAMENTO_VENTA);
    captura.referenciaPendiente.and.returnValue(null);
    captura.cargarContextoReferencias.and.resolveTo();
    captura.referenciasPendientes.and.returnValue([]);
    captura.prepararRevision.and.callFake(async (idBorrador) => {
      const estado = await captura.avanzar.calls.mostRecent().returnValue;
      const definicion = await captura.definicion.calls.mostRecent().returnValue;
      return { propuesta: { referencia: 'actuacion-original', capacidad: 'confirmar_captura',
        version: 1, huella: 'huella-1', caducaEnEpochSeconds: 9999999999,
        contenido: { idBorrador, versionCaptura: 1, codigoCaptura: estado.codigo,
          intencion: estado.intencion, efecto: 'REGISTRAR_PROPIEDAD',
          datos: estado.conocido, definicionCore: definicion } }, pruebaRevision: 'prueba-opaca' };
    });


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

  it('guarda un avance sin operación ni titular elegido', async () => {
    await montar();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = 'DEPARTAMENTO';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    captura.avanzar.and.resolveTo({ ...BORRADOR, faltante: ['direccion'] });
    const revisar = boton('Revisar');
    expect(revisar.disabled).toBeFalse();
    revisar.click();
    await fixture.whenStable();

    expect(captura.avanzar).toHaveBeenCalledWith(
      7,
      jasmine.objectContaining({ tipoPropiedad: 'DEPARTAMENTO' }),
    );
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

  it('conserva la referencia incierta y consulta sin reenviar la confirmación', async () => {
    await montar();
    await elegir('DEPARTAMENTO', ['VENTA']);
    captura.avanzar.and.resolveTo({ ...BORRADOR, faltante: [], listoParaEjecutar: true });
    boton('Revisar').click();
    await fixture.whenStable(); fixture.detectChanges();
    captura.confirmarRevision.and.resolveTo({ referencia: 'actuacion-original',
      estado: 'EN_VERIFICACION', propuesta: null, resultado: null, motivo: null });
    captura.consultarActuacion.and.resolveTo({ referencia: 'actuacion-original',
      estado: 'EN_VERIFICACION', propuesta: null, resultado: null, motivo: null });
    boton('Confirmar').click();
    await fixture.whenStable(); fixture.detectChanges();
    expect(captura.confirmarRevision).toHaveBeenCalledTimes(1);
    expect(captura.confirmarRevision.calls.mostRecent().args[0].pruebaRevision).toBe('prueba-opaca');
    expect(captura.confirmarRevision.calls.mostRecent().args[1]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
    boton('Verificar resultado').click();
    await fixture.whenStable(); fixture.detectChanges();
    expect(captura.consultarActuacion).toHaveBeenCalledOnceWith('actuacion-original');
    expect(captura.confirmarRevision).toHaveBeenCalledTimes(1);
  });

  it('una foto distinta de la mostrada exige revisar otra vez y no confirma', async () => {
    await montar(); await elegir('DEPARTAMENTO', ['VENTA']);
    const estado = { ...BORRADOR, conocido: { direccion: 'Dirección mostrada' },
      faltante: [], listoParaEjecutar: true };
    captura.avanzar.and.resolveTo(estado);
    captura.prepararRevision.and.resolveTo({ propuesta: {
      referencia: 'cambiada', capacidad: 'confirmar_captura', version: 1,
      huella: 'otra-huella', caducaEnEpochSeconds: 9999999999,
      contenido: { idBorrador: 7, versionCaptura: 2, codigoCaptura: estado.codigo,
        intencion: estado.intencion, efecto: 'REGISTRAR_PROPIEDAD',
        datos: { direccion: 'Cambio ajeno' }, definicionCore: DEPARTAMENTO_VENTA } },
      pruebaRevision: 'otra-prueba' });
    boton('Revisar').click(); await fixture.whenStable(); fixture.detectChanges();
    expect(textoVisible()).toContain('El registro cambió');
    expect(captura.confirmarRevision).not.toHaveBeenCalled();
  });

  it('recupera una referencia preparada sin presentarla como escritura incierta ni reenviar', async () => {
    captura.referenciasPendientes.and.returnValue([{ idBorrador: 7, referencia: 'previa' }]);
    captura.consultarActuacion.and.resolveTo({ referencia: 'previa', estado: 'PREPARADA',
      propuesta: null, resultado: null, motivo: null });
    await montar();
    boton('Consultar resultado').click(); await fixture.whenStable(); fixture.detectChanges();
    expect(textoVisible()).toContain('no tiene confirmación registrada');
    expect(captura.consultarActuacion).toHaveBeenCalledOnceWith('previa');
    expect(captura.confirmarRevision).not.toHaveBeenCalled();
    expect(captura.avanzar).not.toHaveBeenCalled();
  });

  it('volver conserva el borrador para poder retomarlo', async () => {
    await montar();

    boton('Volver').click();
    await fixture.whenStable();

    expect(captura.descartar).not.toHaveBeenCalled();
  });

  it('lista y consulta un borrador existente antes de abrir otro', async () => {
    const existente: EstadoCaptura = {
      ...BORRADOR,
      conocido: { tipoPropiedad: 'DEPARTAMENTO', direccion: 'Av. Central 10' },
      faltante: ['operaciones'],
    };
    captura.enCurso.and.resolveTo([existente]);
    captura.consultar.and.resolveTo(existente);

    await montar();

    expect(captura.abrir).not.toHaveBeenCalled();
    expect(textoVisible()).toContain('CAP-0007');
    boton('Retomar').click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(captura.consultar).toHaveBeenCalledWith(7);
    expect(textoVisible()).toContain('Dormitorios');
    const direccion = Array.from(raiz().querySelectorAll('input')).find(
      (entrada) => entrada.value === 'Av. Central 10',
    );
    expect(direccion).toBeDefined();
  });

  it('al quitar el último titular de un borrador retomado envía el borrado explícito', async () => {
    const existente: EstadoCaptura = {
      ...BORRADOR,
      conocido: {
        tipoPropiedad: 'DEPARTAMENTO',
        direccion: 'Av. Central 10',
        titulares: '42',
      },
      faltante: ['operaciones'],
    };
    captura.enCurso.and.resolveTo([existente]);
    captura.consultar.and.resolveTo(existente);

    await montar();
    boton('Retomar').click();
    await fixture.whenStable();
    fixture.detectChanges();

    boton('Quitar').click();
    fixture.detectChanges();
    captura.avanzar.and.resolveTo({ ...existente, faltante: ['direccion'] });
    boton('Revisar').click();
    await fixture.whenStable();

    const enviado = captura.avanzar.calls.mostRecent().args[1];
    expect(enviado['titulares']).toBe('');
  });

  it('envía importes y monedas independientes para venta y alquiler', async () => {
    captura.definicion.and.resolveTo(LOCAL_VENTA_Y_ALQUILER);
    await montar();
    await elegir('LOCAL', ['VENTA', 'ALQUILER']);

    const bloques: HTMLElement[] = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.bloque-economico'),
    ) as HTMLElement[];
    expect(bloques.length).toBe(2);
    const datos = [
      { importe: '180000', moneda: 'USD' },
      { importe: '2800', moneda: 'PEN' },
    ];
    for (const [indice, bloque] of bloques.entries()) {
      const input = bloque.querySelector('.importe input') as HTMLInputElement;
      input.value = datos[indice].importe;
      input.dispatchEvent(new Event('input'));
      const moneda = bloque.querySelector('.importe select.moneda') as HTMLSelectElement;
      moneda.value = datos[indice].moneda;
      moneda.dispatchEvent(new Event('change'));
    }
    fixture.detectChanges();

    captura.avanzar.and.resolveTo({ ...BORRADOR, faltante: [], listoParaEjecutar: true });
    boton('Revisar').click();
    await fixture.whenStable();

    const enviado = captura.avanzar.calls.mostRecent().args[1];
    expect(enviado['importe:VENTA']).toBe('180000');
    expect(enviado['moneda:VENTA']).toBe('USD');
    expect(enviado['importe:ALQUILER']).toBe('2800');
    expect(enviado['moneda:ALQUILER']).toBe('PEN');
  });

  it('envía la moneda de un importe genérico con la clave de su atributo', async () => {
    captura.definicion.and.resolveTo(DEPARTAMENTO_VENTA_Y_MANTENIMIENTO);
    await montar();
    await elegir('DEPARTAMENTO', ['VENTA']);

    const campos: HTMLElement[] = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.campo.importe'),
    ) as HTMLElement[];
    const campo = campos.find(
      (elemento) => (elemento.textContent ?? '').includes('Cuota de mantenimiento'),
    ) as HTMLElement;
    expect(campo).toBeDefined();
    const importe = campo.querySelector('input') as HTMLInputElement;
    importe.value = '350';
    importe.dispatchEvent(new Event('input'));
    const moneda = campo.querySelector('select.moneda') as HTMLSelectElement;
    moneda.value = 'USD';
    moneda.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    captura.avanzar.and.resolveTo({ ...BORRADOR, faltante: [], listoParaEjecutar: true });
    boton('Revisar').click();
    await fixture.whenStable();

    const enviado = captura.avanzar.calls.mostRecent().args[1];
    expect(enviado['cuota_mantenimiento']).toBe('350');
    expect(enviado['moneda:cuota_mantenimiento']).toBe('USD');
  });
});
