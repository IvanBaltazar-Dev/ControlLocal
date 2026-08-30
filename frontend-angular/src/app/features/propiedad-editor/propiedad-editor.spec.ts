import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';

import {
  CapturaService,
  DefinicionCaptura,
  PreguntaCaptura,
} from '../../core/api/captura.service';
import {
  AtributoPropiedad,
  EdicionPropiedad,
  EncargoPropiedad,
  FichaPropiedad,
  PropiedadesService,
} from '../../core/api/propiedades.service';
import { PropietariosService } from '../../core/api/propietarios.service';
import { PropiedadEditor } from './propiedad-editor';

// ====================================================================
// El contrato, como lo publica el Core. Los siete tipos pueden nombrarse en
// un spec; lo que un spec no puede es convertirse en catalogo.
// ====================================================================

function pregunta(clave: string, rotulo: string, extra: Partial<PreguntaCaptura> = {}): PreguntaCaptura {
  return {
    clave,
    rotulo,
    seccion: 'TIPO',
    control: 'TEXTO',
    tipoDato: 'TEXTO',
    obligatoria: false,
    orden: 0,
    ...extra,
  };
}

const MONEDAS = [
  { valor: 'PEN', rotulo: 'Soles' },
  { valor: 'USD', rotulo: 'Dólares' },
];

/** Un local en venta Y en alquiler: el caso que decide el modelo. */
const DEFINICION: DefinicionCaptura = {
  intencion: 'REGISTRAR_PROPIEDAD',
  tipoPropiedad: 'LOCAL',
  operaciones: ['VENTA', 'ALQUILER'],
  comunes: [
    pregunta('titulares', 'Titulares', { seccion: 'COMUN', control: 'TITULARES', tipoDato: 'TITULARES' }),
    pregunta('direccion', 'Dirección', { seccion: 'COMUN', obligatoria: true }),
    pregunta('distrito', 'Distrito', { seccion: 'COMUN', obligatoria: true }),
    pregunta('descripcion', 'Descripción', { seccion: 'COMUN' }),
    pregunta('codigo', 'Código', { seccion: 'COMUN' }),
  ],
  delTipo: [
    pregunta('ambientes', 'Ambientes', { control: 'ENTERO', tipoDato: 'ENTERO' }),
    pregunta('rubro_permitido', 'Rubro permitido'),
    pregunta('zonificacion', 'Zonificación', {
      control: 'SELECTOR',
      tipoDato: 'LISTA',
      opciones: [
        { valor: 'CZ', rotulo: 'Comercio zonal' },
        { valor: 'CV', rotulo: 'Comercio vecinal' },
      ],
    }),
  ],
  deLaOperacion: [
    {
      operacion: 'VENTA',
      rotulo: 'Condición de venta',
      preguntas: [
        pregunta('importe:VENTA', 'Precio de venta', { seccion: 'OPERACION', control: 'MONEDA', tipoDato: 'DECIMAL', unidad: 'moneda' }),
        pregunta('moneda:VENTA', 'Moneda', { seccion: 'OPERACION', control: 'SELECTOR', tipoDato: 'LISTA', opciones: MONEDAS }),
        pregunta('exclusividad:VENTA', 'Exclusividad', { seccion: 'OPERACION', control: 'INTERRUPTOR', tipoDato: 'BOOLEANO' }),
      ],
    },
    {
      operacion: 'ALQUILER',
      rotulo: 'Condición de alquiler',
      preguntas: [
        pregunta('importe:ALQUILER', 'Renta mensual', { seccion: 'OPERACION', control: 'MONEDA', tipoDato: 'DECIMAL', unidad: 'moneda' }),
        pregunta('moneda:ALQUILER', 'Moneda', { seccion: 'OPERACION', control: 'SELECTOR', tipoDato: 'LISTA', opciones: MONEDAS }),
        pregunta('exclusividad:ALQUILER', 'Exclusividad', { seccion: 'OPERACION', control: 'INTERRUPTOR', tipoDato: 'BOOLEANO' }),
        pregunta('garantia_meses:ALQUILER', 'Garantía', { seccion: 'OPERACION', control: 'ENTERO', tipoDato: 'ENTERO', unidad: 'meses' }),
        pregunta('mascotas_aceptadas:ALQUILER', 'Acepta mascotas', { seccion: 'OPERACION', control: 'INTERRUPTOR', tipoDato: 'BOOLEANO' }),
        pregunta('precio_estacionamiento_adicional:ALQUILER', 'Precio por cochera adicional', {
          seccion: 'OPERACION', control: 'IMPORTE', tipoDato: 'IMPORTE',
        }),
        pregunta('equipamiento_incluido:ALQUILER', 'Equipamiento incluido', {
          seccion: 'OPERACION', control: 'SELECTOR_MULTIPLE', tipoDato: 'LISTA_MULTIPLE',
          opciones: [
            { valor: 'COCINA', rotulo: 'Cocina' },
            { valor: 'LAVADORA', rotulo: 'Lavadora' },
          ],
        }),
      ],
    },
  ],
};

function encargo(
  idEncargo: number,
  codigo: string,
  operacion: string,
  vivo: boolean,
  importe: number,
  moneda: string,
  condiciones: AtributoPropiedad[] = [],
): EncargoPropiedad {
  const venta = operacion === 'VENTA';
  return {
    idEncargo,
    codigo,
    operacion,
    operacionRotulo: venta ? 'Venta' : 'Alquiler',
    estado: vivo ? 'A' : 'C',
    estadoRotulo: vivo ? 'Activo' : 'Cerrado',
    vivo,
    importe,
    moneda,
    importeRotulo: venta ? 'precio de venta' : 'renta mensual',
    exclusividad: venta,
    inicio: '2026-02-01',
    historico: [],
    publicaciones: [],
    condiciones,
  };
}

const FICHA: FichaPropiedad = {
  // El caso normal: la ficha la abre su responsable (P0). El bloqueo se
  // declara en su prueba, que es donde se lee.
  responsabilidad: { idResponsable: 30, nombre: 'Valeria Mora', puedeEditar: true },
  id: 3259,
  codigo: 'LOC-0022',
  tipoPropiedad: 'LOCAL',
  tipoRotulo: 'Local comercial',
  uso: 'C',
  usoRotulo: 'Comercial',
  descripcion: 'Esquina con vitrina',
  estadoRegistro: 'A',
  estadoRegistroRotulo: 'Activa',
  ubicacion: { direccion: 'Av. Larco 700', distrito: 'Miraflores', zonaUrbanizacion: 'Centro', piso: '1' },
  titulares: [
    { idPropietario: 11, nombre: 'Ana Torres', cuota: 60, representante: true },
    { idPropietario: 12, nombre: 'Luis Rojas', cuota: 40, representante: false },
  ],
  atributos: [
    { clave: 'ambientes', rotulo: 'Ambientes', tipoDato: 'ENTERO', valor: '4' },
    { clave: 'rubro_permitido', rotulo: 'Rubro permitido', tipoDato: 'TEXTO', valor: 'Cafetería' },
  ],
  encargos: [
    encargo(16, 'ENC-0016', 'VENTA', true, 320000, 'USD'),
    encargo(32, 'ENC-0032', 'ALQUILER', true, 2800, 'PEN', [
      { clave: 'garantia_meses', rotulo: 'Garantía', tipoDato: 'ENTERO', unidad: 'meses', valor: '2' },
    ]),
    // Un alquiler anterior, cerrado: su historia se conserva y no se toca.
    encargo(9, 'ENC-0009', 'ALQUILER', false, 2500, 'PEN'),
  ],
  atributosQueFaltan: [],
  faltanParaPublicar: [],
  historia: { porOperacion: [], linea: [] },
  actividad: { oportunidades: [], visitas: [], interacciones: [], expedientes: [], contratos: [] },
};

// ====================================================================

describe('PropiedadEditor', () => {
  let fixture: ComponentFixture<PropiedadEditor>;
  let api: jasmine.SpyObj<PropiedadesService>;
  let captura: jasmine.SpyObj<CapturaService>;
  let propietarios: jasmine.SpyObj<PropietariosService>;
  let router: Router;

  async function montar(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PropiedadEditor],
      providers: [
        { provide: PropiedadesService, useValue: api },
        { provide: CapturaService, useValue: captura },
        { provide: PropietariosService, useValue: propietarios },
        provideRouter([]),
        provideHttpClient(),
        // DESPUÉS de provideRouter, o gana el ActivatedRoute del router y el
        // editor se monta sin id.
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '3259' }) } },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);

    fixture = TestBed.createComponent(PropiedadEditor);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function raiz(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  /** El control de un campo por su rótulo, como lo encuentra una persona. */
  function control(rotulo: string, dentroDe: ParentNode = raiz()): HTMLInputElement | HTMLSelectElement {
    const campo = Array.from(dentroDe.querySelectorAll('cl-campo-gobernado')).find((candidato) =>
      (candidato.textContent ?? '').includes(rotulo),
    );
    const encontrado = campo?.querySelector<HTMLInputElement | HTMLSelectElement>('input, select');
    if (!encontrado) {
      throw new Error(`No hay ningún campo rotulado "${rotulo}"`);
    }
    return encontrado;
  }

  function escribir(elemento: HTMLInputElement | HTMLSelectElement, valor: string): void {
    elemento.value = valor;
    elemento.dispatchEvent(new Event(elemento instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function quitarDe(rotulo: string): void {
    const celda = Array.from(raiz().querySelectorAll('.celda')).find((candidata) =>
      (candidata.textContent ?? '').includes(rotulo),
    );
    const boton = celda?.querySelector<HTMLButtonElement>('button.quitar');
    if (!boton) {
      throw new Error(`El campo "${rotulo}" no ofrece Quitar`);
    }
    boton.click();
    fixture.detectChanges();
  }

  function botonGuardar(): HTMLButtonElement {
    return raiz().querySelector<HTMLButtonElement>('.acciones .primario')!;
  }

  async function guardar(): Promise<void> {
    botonGuardar().click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function enviado(): EdicionPropiedad {
    return api.editar.calls.mostRecent().args[1];
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<PropiedadesService>('PropiedadesService', ['consultar', 'editar']);
    api.consultar.and.resolveTo(structuredClone(FICHA));
    api.editar.and.resolveTo(structuredClone(FICHA));
    captura = jasmine.createSpyObj<CapturaService>('CapturaService', ['definicion']);
    captura.definicion.and.resolveTo(DEFINICION);
    propietarios = jasmine.createSpyObj<PropietariosService>('PropietariosService', ['pagina']);
    propietarios.pagina.and.resolveTo({ items: [], totalRecords: 0, page: 1, pageSize: 50 });
  });

  // ------------------------------------------------------------------
  // De dónde sale lo que se pinta
  // ------------------------------------------------------------------

  it('lee la ficha por el modelo universal y pide la definición con las operaciones VIVAS', async () => {
    await montar();

    expect(api.consultar).toHaveBeenCalledWith(3259);
    // El alquiler cerrado no cuenta: no hay nada que editar en él.
    expect(captura.definicion).toHaveBeenCalledWith('LOCAL', 'VENTA,ALQUILER');
  });

  it('precarga cada campo con lo que la ficha dice, sin conocer ninguna clave', async () => {
    await montar();

    expect((control('Distrito') as HTMLInputElement).value).toBe('Miraflores');
    expect((control('Ambientes') as HTMLInputElement).value).toBe('4');
    expect((control('Descripción') as HTMLInputElement).value).toBe('Esquina con vitrina');
    const venta = raiz().querySelector('[data-encargo="16"]')!;
    expect((control('Precio de venta', venta) as HTMLInputElement).value).toBe('320000');
    const alquiler = raiz().querySelector('[data-encargo="32"]')!;
    expect((control('Garantía', alquiler) as HTMLInputElement).value).toBe('2');
  });

  // ------------------------------------------------------------------
  // La regla del cuerpo: sólo lo tocado
  // ------------------------------------------------------------------

  it('sin tocar nada, no hay nada que guardar', async () => {
    await montar();

    expect(botonGuardar().disabled).toBeTrue();
    await guardar();
    expect(api.editar).not.toHaveBeenCalled();
  });

  it('tocar el distrito manda SOLO ubicacion.distrito', async () => {
    await montar();
    escribir(control('Distrito'), 'Surco');

    await guardar();

    expect(enviado()).toEqual({ ubicacion: { distrito: 'Surco' } });
  });

  it('la descripción viaja en su sitio, no como atributo ni como ubicación', async () => {
    await montar();
    escribir(control('Descripción'), 'Con mezzanine');

    await guardar();

    expect(enviado()).toEqual({ descripcion: 'Con mezzanine' });
  });

  it('tocar una característica no toca ubicación, titulares ni encargos', async () => {
    await montar();
    escribir(control('Ambientes'), '5');

    await guardar();

    expect(enviado()).toEqual({ atributos: [{ clave: 'ambientes', valor: '5' }] });
  });

  // ------------------------------------------------------------------
  // no sé ≠ inventar · no toqué ≠ vacío · eliminar = intención
  // ------------------------------------------------------------------

  it('vaciar un campo NO viaja: ni como valor vacío ni como borrado', async () => {
    await montar();
    escribir(control('Ambientes'), '');

    expect(botonGuardar().disabled).toBeTrue();
    await guardar();
    expect(api.editar).not.toHaveBeenCalled();
  });

  it('«Quitar» es la única forma de retirar un valor, y viaja como intención', async () => {
    await montar();
    quitarDe('Ambientes');

    await guardar();

    expect(enviado()).toEqual({ atributosABorrar: ['ambientes'] });
  });

  it('un selector sin elegir queda en blanco: nunca la primera opción', async () => {
    await montar();

    const zonificacion = control('Zonificación') as HTMLSelectElement;
    expect(zonificacion.value).toBe('');
    expect(botonGuardar().disabled).toBeTrue();
  });

  // Una casilla sin marcar se lee igual que un «no», y «todavía no se sabe» no
  // es «no». Con dos estados, «acepta mascotas» sin declarar y «no acepta
  // mascotas» eran píxeles idénticos: el dato no se perdía, pero la persona
  // leía una respuesta que nadie dio.
  it('un booleano sin declarar se ve como tal, y no como un «no»', async () => {
    await montar();
    const alquiler = raiz().querySelector('[data-encargo="32"]')!;

    const mascotas = control('Acepta mascotas', alquiler) as HTMLSelectElement;
    expect(mascotas.tagName).toBe('SELECT');
    expect(mascotas.value).toBe('');
    expect(Array.from(mascotas.options).map((o) => o.textContent?.trim())).toEqual([
      'Sin declarar',
      'Sí',
      'No',
    ]);

    // Y declarar «No» sí viaja: es una respuesta, no una ausencia.
    escribir(mascotas, 'false');
    await guardar();
    expect(enviado()).toEqual({
      condiciones: [{ idEncargo: 32, atributos: [{ clave: 'mascotas_aceptadas', valor: 'false' }] }],
    });
  });

  it('un IMPORTE viaja con su moneda, no pegada al número', async () => {
    await montar();
    const alquiler = raiz().querySelector('[data-encargo="32"]')!;
    const campo = Array.from(alquiler.querySelectorAll('cl-campo-gobernado')).find((c) =>
      (c.textContent ?? '').includes('Precio por cochera'),
    )!;

    escribir(campo.querySelector('select')!, 'PEN');
    escribir(campo.querySelector('input')!, '250');
    await guardar();

    expect(enviado()).toEqual({
      condiciones: [
        {
          idEncargo: 32,
          atributos: [{ clave: 'precio_estacionamiento_adicional', valor: '250', moneda: 'PEN' }],
        },
      ],
    });
  });

  it('un multivalor viaja como lista, no como texto con comas', async () => {
    await montar();
    const alquiler = raiz().querySelector('[data-encargo="32"]')!;
    const campo = Array.from(alquiler.querySelectorAll('cl-campo-gobernado')).find((c) =>
      (c.textContent ?? '').includes('Equipamiento'),
    )!;
    const casillas = Array.from(campo.querySelectorAll<HTMLInputElement>('input[type=checkbox]'));

    casillas[0].checked = true;
    casillas[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await guardar();

    expect(enviado()).toEqual({
      condiciones: [
        { idEncargo: 32, atributos: [{ clave: 'equipamiento_incluido', valores: ['COCINA'] }] },
      ],
    });
  });

  it('lo que el cable no transporta se ve y no se edita', async () => {
    await montar();

    const codigo = control('Código') as HTMLInputElement;
    expect(codigo.value).toBe('LOC-0022');
    expect(codigo.readOnly).toBeTrue();
    // Y ningún campo ofrece elegir el uso ni el tipo: no son del PUT.
    const opciones = Array.from(raiz().querySelectorAll('option')).map((opcion) => opcion.value);
    expect(opciones).not.toContain('C');
  });

  // ------------------------------------------------------------------
  // Un bloque por ENCARGO
  // ------------------------------------------------------------------

  it('cambiar el precio de venta toca SOLO el encargo de venta', async () => {
    await montar();
    const venta = raiz().querySelector('[data-encargo="16"]')!;
    escribir(control('Precio de venta', venta), '330000');

    await guardar();

    // La moneda viaja porque el cable la exige junto al importe; no cambió, y
    // el Core no produce hito por ella. El alquiler no aparece.
    expect(enviado()).toEqual({
      operaciones: [{ operacion: 'VENTA', importe: 330000, moneda: 'USD' }],
    });
  });

  it('lo pactado en un encargo viaja por idEncargo, con la clave sin calificar', async () => {
    await montar();
    const alquiler = raiz().querySelector('[data-encargo="32"]')!;
    escribir(control('Garantía', alquiler), '3');

    await guardar();

    expect(enviado()).toEqual({
      condiciones: [{ idEncargo: 32, atributos: [{ clave: 'garantia_meses', valor: '3' }] }],
    });
  });

  it('un encargo cerrado se ve y no se edita, y ningún control cambia la operación', async () => {
    await montar();

    const cerrado = raiz().querySelector('[data-encargo="9"]')!;
    expect(cerrado.textContent).toContain('ENC-0009');
    expect(cerrado.querySelector('input, select')).toBeNull();

    const valores = Array.from(raiz().querySelectorAll('option')).map((opcion) => opcion.value);
    expect(valores).not.toContain('VENTA');
    expect(valores).not.toContain('ALQUILER');
  });

  // ------------------------------------------------------------------
  // Titulares: el conjunto completo, y sólo si se tocó
  // ------------------------------------------------------------------

  it('tocar una cuota manda el conjunto completo de titulares', async () => {
    await montar();
    const cuotas = Array.from(raiz().querySelectorAll<HTMLInputElement>('.cuota input'));
    escribir(cuotas[0], '70');
    escribir(cuotas[1], '30');

    await guardar();

    expect(enviado()).toEqual({
      titulares: [
        { idPropietario: 11, cuota: 70, representante: true },
        { idPropietario: 12, cuota: 30, representante: false },
      ],
    });
  });

  it('con las cuotas descuadradas no se guarda, y se dice', async () => {
    await montar();
    escribir(Array.from(raiz().querySelectorAll<HTMLInputElement>('.cuota input'))[0], '70');

    await guardar();

    expect(api.editar).not.toHaveBeenCalled();
    expect(raiz().textContent).toContain('suman 110');
  });

  // ------------------------------------------------------------------
  // Guardar
  // ------------------------------------------------------------------

  it('guarda con Idempotency-Key y vuelve a la ficha', async () => {
    await montar();
    escribir(control('Distrito'), 'Surco');

    await guardar();

    const [id, , clave] = api.editar.calls.mostRecent().args;
    expect(id).toBe(3259);
    expect(typeof clave).toBe('string');
    expect(router.navigate).toHaveBeenCalledWith(['/propiedades', 3259]);
  });

  // ------------------------------------------------------------------
  // La autoridad, que llega resuelta del cable (P0)
  // ------------------------------------------------------------------

  /**
   * **No se guarda lo que el Core va a rechazar, y se dice por qué.**
   *
   * El motivo se pinta tal como viene: esta pantalla no redacta rechazos. Si
   * lo hiciera, BROX Web y KAIROS acabarían diciendo dos cosas distintas del
   * mismo hecho.
   */
  it('con la propiedad de otro responsable no deja guardar, y dice por que', async () => {
    api.consultar.and.resolveTo({
      ...structuredClone(FICHA),
      responsabilidad: {
        idResponsable: 44,
        nombre: 'Otro Agente',
        puedeEditar: false,
        motivoTexto: 'De esta propiedad responde otro agente.',
      },
    });
    await montar();
    escribir(control('Distrito'), 'Surco');
    fixture.detectChanges();

    expect(raiz().textContent).toContain('De esta propiedad responde otro agente.');
    expect(raiz().textContent).toContain('Otro Agente');
    for (const boton of Array.from(
      raiz().querySelectorAll<HTMLButtonElement>('button.primario'),
    )) {
      expect(boton.disabled)
        .withContext('hay cambios tocados: sin la autoridad, el boton seguiria activo')
        .toBeTrue();
    }
  });

  it('sin responsable tampoco: FALTANTE no habilita a quien pase por ahi', async () => {
    api.consultar.and.resolveTo({
      ...structuredClone(FICHA),
      responsabilidad: {
        puedeEditar: false,
        motivoTexto: 'Esta propiedad no tiene agente responsable asignado.',
      },
    });
    await montar();
    escribir(control('Distrito'), 'Surco');
    fixture.detectChanges();

    expect(raiz().textContent).toContain('no tiene agente responsable asignado');
  });

  it('si el Core rechaza, el error se lee y lo tocado no se pierde', async () => {
    await montar();
    api.editar.and.rejectWith(new Error('La comision derivada debe usar la moneda de su base.'));
    escribir(control('Distrito'), 'Surco');

    await guardar();

    expect(raiz().textContent).toContain('La comision derivada debe usar la moneda de su base.');
    expect((control('Distrito') as HTMLInputElement).value).toBe('Surco');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
