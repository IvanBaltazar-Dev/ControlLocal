import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { convertToParamMap } from '@angular/router';

import {
  EncargoPropiedad,
  FichaPropiedad,
  HechoDeActividad,
  HistoriaComercial,
  PropiedadesService,
} from '../../core/api/propiedades.service';
import { AuthService } from '../../core/auth/auth.service';
import { Sesion } from '../../core/auth/sesion.model';
import { LocalesService } from '../../core/api/locales.service';
import { PropiedadDetail } from './propiedad-detail';

// ====================================================================
// Fixtures. Los cuatro casos que deciden si el diseño es correcto.
// ====================================================================

function encargo(parcial: Partial<EncargoPropiedad>): EncargoPropiedad {
  return {
    idEncargo: 1,
    codigo: 'ENC-0001',
    operacion: 'ALQUILER',
    operacionRotulo: 'Alquiler',
    estado: 'A',
    estadoRotulo: 'Activa',
    vivo: true,
    importe: 4800,
    moneda: 'USD',
    importeRotulo: 'renta mensual',
    exclusividad: false,
    idAgente: 7,
    agenteNombre: 'Valeria Mora',
    inicio: '2026-01-10',
    fin: '2026-12-10',
    historico: [{ hito: 'U', hitoRotulo: 'Autorizado', monto: 4800, moneda: 'USD', fecha: '2026-01-10' }],
    publicaciones: [],
    publicacionGestionable: { permitida: true, motivo: null },
    ...parcial,
  };
}

function ficha(parcial: Partial<FichaPropiedad>): FichaPropiedad {
  return {
    id: 3259,
    codigo: 'PROP-0022',
    tipoPropiedad: 'OFICINA',
    tipoRotulo: 'Oficina',
    uso: 'C',
    usoRotulo: 'Comercial',
    descripcion: null,
    estadoRegistro: 'A',
    estadoRegistroRotulo: 'Activo',
    disponibilidadComercial: 'D',
    disponibilidadRotulo: 'Disponible',
    ubicacion: {
      direccion: 'Av. Javier Prado 4321',
      distrito: 'San Isidro',
      piso: '8',
    },
    titulares: [{ idPropietario: 11, nombre: 'Ana Torres', cuota: 100, representante: true }],
    atributos: [
      { clave: 'metraje_total', rotulo: 'Metraje total', tipoDato: 'DECIMAL', unidad: 'm²', valor: '160' },
      { clave: 'ambientes', rotulo: 'Ambientes', tipoDato: 'ENTERO', unidad: null, valor: '4' },
    ],
    encargos: [],
    atributosQueFaltan: [],
    faltanParaPublicar: [],
    historia: { porOperacion: [], linea: [] },
    actividad: {
      oportunidades: [],
      visitas: [],
      interacciones: [],
      expedientes: [],
      contratos: [],
    },
    fechaRegistro: '2026-01-10T10:00:00',
    // Quien responde por ella y que puede hacer quien mira (P0). Por defecto,
    // el caso normal: la ficha la pide su responsable. Los casos de bloqueo se
    // declaran uno por uno, que es como se leen.
    responsabilidad: { idResponsable: 30, nombre: 'Valeria Mora', puedeEditar: true },
    ...parcial,
  };
}

/** Caso 1: un encargo, venta. */
const SOLO_VENTA = ficha({
  encargos: [
    encargo({
      idEncargo: 90,
      codigo: 'ENC-0090',
      operacion: 'VENTA',
      operacionRotulo: 'Venta',
      importe: 320000,
      importeRotulo: 'precio de venta',
      historico: [
        { hito: 'U', hitoRotulo: 'Autorizado', monto: 320000, moneda: 'USD', fecha: '2026-01-10' },
      ],
    }),
  ],
});

/** Caso 2: venta y alquiler simultáneos — PROP-0022, la propiedad real. */
const PROP_0022 = ficha({
  encargos: [
    encargo({
      idEncargo: 90,
      codigo: 'ENC-0090',
      operacion: 'VENTA',
      operacionRotulo: 'Venta',
      importe: 320000,
      importeRotulo: 'precio de venta',
      historico: [
        { hito: 'U', hitoRotulo: 'Autorizado', monto: 320000, moneda: 'USD', fecha: '2026-01-10' },
      ],
    }),
    encargo({
      idEncargo: 91,
      codigo: 'ENC-0091',
      importe: 4800,
      historico: [
        { hito: 'U', hitoRotulo: 'Autorizado', monto: 4800, moneda: 'USD', fecha: '2026-01-10' },
      ],
    }),
  ],
});

/** Caso 3: copropiedad con cuotas y representante. */
const COPROPIEDAD = ficha({
  titulares: [
    { idPropietario: 11, nombre: 'Ana Torres', cuota: 60, representante: true },
    { idPropietario: 12, nombre: 'Carlos Torres', cuota: 40, representante: false },
  ],
  encargos: [encargo({})],
});

/**
 * **Caso 4, el que rompe un diseño malo:** tres encargos de la MISMA operación
 * en momentos distintos. Venta + alquiler no lo detecta —con uno de cada,
 * agrupar por operación y listar por id dan lo mismo—; esto sí.
 */
const TRES_ALQUILERES = ficha({
  encargos: [
    encargo({
      idEncargo: 103,
      codigo: 'ENC-0103',
      importe: 2600,
      vivo: true,
      historico: [
        { hito: 'U', hitoRotulo: 'Autorizado', monto: 2600, moneda: 'PEN', fecha: '2026-01-10' },
      ],
    }),
    encargo({
      idEncargo: 102,
      codigo: 'ENC-0102',
      importe: 2400,
      vivo: false,
      estado: 'C',
      estadoRotulo: 'Cerrada',
      historico: [
        { hito: 'U', hitoRotulo: 'Autorizado', monto: 2400, moneda: 'PEN', fecha: '2025-01-15' },
      ],
    }),
    encargo({
      idEncargo: 101,
      codigo: 'ENC-0101',
      importe: 2200,
      vivo: false,
      estado: 'C',
      estadoRotulo: 'Cerrada',
      historico: [
        { hito: 'U', hitoRotulo: 'Autorizado', monto: 2200, moneda: 'PEN', fecha: '2024-02-10' },
      ],
    }),
  ],
});

/**
 * La memoria del inmueble para los tres alquileres: tres episodios, el ultimo
 * pedido del vigente y un cierre real por DEBAJO de lo que se pedia.
 */
const HISTORIA: HistoriaComercial = {
  porOperacion: [
    {
      operacion: 'ALQUILER',
      operacionRotulo: 'Alquiler',
      veces: 3,
      desde: '2024-02-10',
      hasta: null,
      vivoAhora: true,
      ultimoPedido: { monto: 2600, moneda: 'PEN', fecha: '2026-01-10', idEncargo: 103, codigoEncargo: 'ENC-0103' },
      ultimoCierre: { monto: 2250, moneda: 'PEN', fecha: '2025-03-01', idEncargo: 102, codigoEncargo: 'ENC-0102' },
    },
  ],
  linea: [
    { fecha: '2026-01-10', hito: 'U', hitoRotulo: 'Autorizado', monto: 2600, moneda: 'PEN', idEncargo: 103, codigoEncargo: 'ENC-0103', operacion: 'ALQUILER', operacionRotulo: 'Alquiler' },
    { fecha: '2025-01-15', hito: 'U', hitoRotulo: 'Autorizado', monto: 2400, moneda: 'PEN', idEncargo: 102, codigoEncargo: 'ENC-0102', operacion: 'ALQUILER', operacionRotulo: 'Alquiler' },
    { fecha: '2024-02-10', hito: 'U', hitoRotulo: 'Autorizado', monto: 2200, moneda: 'PEN', idEncargo: 101, codigoEncargo: 'ENC-0101', operacion: 'ALQUILER', operacionRotulo: 'Alquiler' },
  ],
};

function hecho(parcial: Partial<HechoDeActividad>): HechoDeActividad {
  return {
    proceso: 'VISITA',
    id: 1,
    codigo: null,
    titulo: 'Visita de Lucía Ramos',
    detalle: null,
    estado: 'R',
    estadoRotulo: 'Realizada',
    fecha: '2026-08-19',
    idEncargo: 90,
    operacion: 'VENTA',
    operacionRotulo: 'Venta',
    ruta: 'visitas',
    ...parcial,
  };
}

/** Dos visitas de la misma propiedad, de encargos distintos. */
const CON_ACTIVIDAD = ficha({
  encargos: PROP_0022.encargos,
  actividad: {
    oportunidades: [],
    visitas: [
      hecho({ id: 1, titulo: 'Visita de Lucía Ramos', idEncargo: 90, operacion: 'VENTA', operacionRotulo: 'Venta' }),
      hecho({
        id: 2,
        titulo: 'Visita de Marco Díaz',
        idEncargo: 91,
        operacion: 'ALQUILER',
        operacionRotulo: 'Alquiler',
      }),
    ],
    interacciones: [],
    expedientes: [],
    contratos: [],
  },
});

// ====================================================================

describe('PropiedadDetail', () => {
  let fixture: ComponentFixture<PropiedadDetail>;
  let api: jasmine.SpyObj<PropiedadesService>;

  async function montar(datos: FichaPropiedad, rol?: string): Promise<void> {
    api.consultar.and.resolveTo(datos);
    // Con rol, una sesion de verdad detras de `AuthService.sesion`: el
    // `computed` que decide quien edita lee un signal, y un spyOn puesto
    // despues de montar no lo hace reaccionar.
    const conSesion = rol
      ? [
          {
            provide: AuthService,
            useValue: {
              sesion: signal<Sesion | null>({
                token: 't',
                expiraEnSegundos: 3600,
                rol: rol as Sesion['rol'],
                idUsuario: 1,
                idDominio: 30,
                nombre: 'Prueba',
                usuario: 'prueba',
                expiraEn: '2099-01-01T00:00:00',
              }),
            },
          },
        ]
      : [];
    await TestBed.configureTestingModule({
      imports: [PropiedadDetail],
      providers: [
        { provide: PropiedadesService, useValue: api },
        ...conSesion,
        provideRouter([]),
        provideHttpClient(),
        // DESPUES de provideRouter: el router trae su propio ActivatedRoute y,
        // declarado antes, gana el suyo -- la ficha se monta sin id y todas las
        // pruebas fallan diciendo "el identificador no es valido".
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: String(datos.id) }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PropiedadDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function html(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function bloques(): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('cl-bloque-encargo .encargo'),
    );
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<PropiedadesService>('PropiedadesService', [
      'consultar',
      'listar',
      'filtros',
    ]);
  });

  // ------------------------------------------------------------------
  // El cable
  // ------------------------------------------------------------------

  it('lee la propiedad por el modelo universal y NUNCA por /locales/{id}', async () => {
    await montar(PROP_0022);

    expect(api.consultar).toHaveBeenCalledWith(3259);
    // Ni siquiera está inyectado: la ficha universal no toca el modelo heredado.
    expect(() => TestBed.inject(LocalesService, null as never)).not.toThrow();
    expect(TestBed.inject(PropiedadesService)).toBe(api);
  });

  // ------------------------------------------------------------------
  // 1. La cosa física
  // ------------------------------------------------------------------

  it('el tipo se pinta con el rotulo del backend, no traducido aqui', async () => {
    await montar(PROP_0022);

    expect(html()).toContain('Oficina');
    // El valor del cable no se enseña nunca.
    expect(html()).not.toContain('OFICINA');
  });

  it('el metraje aparece UNA sola vez, entre las caracteristicas', async () => {
    await montar(PROP_0022);

    const veces = (html().match(/Metraje total/g) ?? []).length;
    expect(veces).toBe(1);
    expect(html()).toContain('160 m²');
  });

  it('no hay precio en el bloque de la propiedad: el precio es del encargo', async () => {
    await montar(PROP_0022);

    const propiedad = (fixture.nativeElement as HTMLElement).querySelectorAll('.bloque')[0];
    expect(propiedad.textContent).not.toContain('320,000');
    expect(propiedad.textContent).not.toContain('4,800');
  });

  it('con copropiedad enseña la cuota de cada titular y quien representa', async () => {
    await montar(COPROPIEDAD);

    expect(html()).toContain('Ana Torres');
    expect(html()).toContain('60 %');
    expect(html()).toContain('Carlos Torres');
    expect(html()).toContain('40 %');
    expect(html()).toContain('Representante');
  });

  it('la titularidad enseña la cuota y el representante tambien con un solo titular', async () => {
    await montar(PROP_0022);

    // Ocultar «100 %» parecia limpieza, pero la cuota es justo el dato que
    // hace real la titularidad multiple: sin ella la seccion se vuelve a leer
    // como «el propietario».
    expect(html()).toContain('Titularidad');
    expect(html()).toContain('Ana Torres');
    expect(html()).toContain('100 %');
    expect(html()).toContain('Representante');
  });

  // ------------------------------------------------------------------
  // 2. La gestión comercial
  // ------------------------------------------------------------------

  it('un encargo: un bloque, con el nombre de SU importe', async () => {
    await montar(SOLO_VENTA);

    expect(bloques().length).toBe(1);
    expect(html()).toContain('USD 320,000');
    // El rotulo economico lo dice el read model: aqui no hay ningun ternario
    // sobre la operacion.
    expect(html()).toContain('precio de venta');
    expect(html()).not.toContain('renta mensual');
  });

  it('venta + alquiler: dos bloques, dos importes, dos historicos que no se mezclan', async () => {
    await montar(PROP_0022);

    const cajas = bloques();
    expect(cajas.length).toBe(2);

    expect(cajas[0].textContent).toContain('Venta');
    expect(cajas[0].textContent).toContain('USD 320,000');
    expect(cajas[0].textContent).toContain('precio de venta');
    expect(cajas[0].textContent).not.toContain('4,800');

    expect(cajas[1].textContent).toContain('Alquiler');
    expect(cajas[1].textContent).toContain('USD 4,800');
    expect(cajas[1].textContent).toContain('renta mensual');
    expect(cajas[1].textContent).not.toContain('320,000');

    // Y en ninguna parte la suma, que no significa nada.
    expect(html()).not.toContain('324,800');
  });

  it('cada bloque dice su estado, su agente y su exclusividad', async () => {
    await montar(PROP_0022);

    const venta = bloques()[0];
    expect(venta.textContent).toContain('Activa');
    expect(venta.textContent).toContain('Valeria Mora');
    expect(venta.textContent).toContain('Exclusividad');
  });

  /**
   * **La prueba que rompe un `groupBy(operacion)`.** Tres alquileres sucesivos
   * son tres bloques con tres históricos; agrupados serían uno con tres precios
   * dentro.
   */
  it('tres encargos de la MISMA operacion son tres bloques, no uno agrupado', async () => {
    await montar(TRES_ALQUILERES);

    // El vivo arriba; los dos cerrados, dentro de su plegable.
    expect(bloques().length).toBe(3);

    const codigos = bloques().map((caja) => caja.querySelector('.codigo')?.textContent?.trim());
    expect(codigos).toEqual(['ENC-0103', 'ENC-0102', 'ENC-0101']);

    // Cada histórico es el suyo: un bloque nunca ve la cifra de otro.
    const [vigente, anterior2025, anterior2024] = bloques();
    expect(vigente.textContent).toContain('2,600');
    expect(vigente.textContent).not.toContain('2,400');
    expect(vigente.textContent).not.toContain('2,200');
    expect(anterior2025.textContent).toContain('2,400');
    expect(anterior2025.textContent).not.toContain('2,600');
    expect(anterior2024.textContent).toContain('2,200');
    expect(anterior2024.textContent).not.toContain('2,600');
  });

  it('el encargo cerrado no se esconde: sigue con su historico', async () => {
    await montar(TRES_ALQUILERES);

    const plegable = (fixture.nativeElement as HTMLElement).querySelector('details.historia');
    expect(plegable).not.toBeNull();
    expect(plegable?.querySelector('summary')?.textContent).toContain('2 encargos anteriores');
    expect(plegable?.textContent).toContain('Autorizado');
  });

  it('sin ningun encargo lo dice, en vez de dejar el hueco en blanco', async () => {
    await montar(ficha({ encargos: [] }));

    expect(bloques().length).toBe(0);
    expect(html()).toContain('no tiene ningún encargo');
  });

  // ------------------------------------------------------------------
  // 3. La actividad
  // ------------------------------------------------------------------

  it('cada hecho conserva el encargo del que proviene', async () => {
    await montar(CON_ACTIVIDAD);

    const filas = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.hechos li'),
    );
    expect(filas.length).toBe(2);
    // Dos visitas de la misma propiedad que NO significan lo mismo: una es de
    // alguien que quiere comprar y otra de alguien que quiere alquilar.
    expect(filas[0].querySelector('.procedencia')?.textContent?.trim()).toBe('Venta');
    expect(filas[1].querySelector('.procedencia')?.textContent?.trim()).toBe('Alquiler');
  });

  it('acota la actividad por ENCARGO, no por operacion', async () => {
    await montar(CON_ACTIVIDAD);

    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.acotar button'),
    ) as HTMLButtonElement[];
    // «Todas» + uno por encargo, identificado por su codigo — no por su
    // operacion, que con varios encargos iguales los juntaria.
    expect(botones.map((b) => b.textContent?.trim())).toEqual([
      'Todas',
      'Venta · ENC-0090',
      'Alquiler · ENC-0091',
    ]);

    botones[2].click();
    fixture.detectChanges();

    const filas = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.hechos li'),
    );
    expect(filas.length).toBe(1);
    expect(filas[0].textContent).toContain('Marco Díaz');
  });

  it('sin actividad lo dice con palabras, no con un cero', async () => {
    await montar(PROP_0022);

    expect(html()).toContain('Todavía no hay oportunidades');
    expect(html()).not.toContain('0 visitas');
  });

  // ------------------------------------------------------------------
  // Lo que falta
  // ------------------------------------------------------------------

  it('lo que falta se dice con la palabra del catalogo, nunca con la clave', async () => {
    await montar(
      ficha({
        faltanParaPublicar: [{ clave: 'metraje_total', rotulo: 'Metraje total' }],
        encargos: [encargo({})],
      }),
    );

    const aviso = (fixture.nativeElement as HTMLElement).querySelector('.aviso');
    expect(aviso?.textContent).toContain('Metraje total');
    expect(aviso?.textContent).not.toContain('metraje_total');
  });

  // La deuda que midió V82: `tipo_acceso` pasó a PUB, salió de
  // `atributosQueFaltan` --que sólo lleva ALT-- y no cabía en el bloque del
  // encargo. La propiedad quedaba bloqueada sin que nada lo dijera.
  it('una clave PUB que falta se avisa, aunque no esté entre las del alta', async () => {
    await montar(
      ficha({
        atributosQueFaltan: [],
        faltanParaPublicar: [{ clave: 'tipo_acceso', rotulo: 'Tipo de acceso' }],
        encargos: [encargo({})],
      }),
    );

    const aviso = (fixture.nativeElement as HTMLElement).querySelector('.aviso');
    expect(aviso?.textContent).toContain('Tipo de acceso');
    expect(aviso?.textContent).toContain('para poder publicarla');
  });

  // Y al revés: sin nada que impida publicar, no se inventa un aviso porque el
  // alta tenga pendientes. Son dos preguntas distintas.
  it('sin faltantes de publicación no hay aviso, aunque el alta tenga pendientes', async () => {
    await montar(
      ficha({
        atributosQueFaltan: [{ clave: 'dormitorios', rotulo: 'Dormitorios' }],
        faltanParaPublicar: [],
        encargos: [encargo({})],
      }),
    );

    expect((fixture.nativeElement as HTMLElement).querySelector('.aviso')).toBeNull();
  });

  it('un dato ausente es —, y nunca un cero', async () => {
    await montar(
      ficha({
        encargos: [encargo({ agenteNombre: null, exclusividad: null, importe: null, moneda: null })],
      }),
    );

    const caja = bloques()[0];
    expect(caja.textContent).toContain('—');
    // Sin importe pactado NO es «cuesta 0».
    expect(caja.textContent).not.toContain('0.00');
  });

  // ------------------------------------------------------------------
  // Una característica HISTÓRICA: el dato se conserva y se distingue
  //
  // Un valor sale del contrato de escritura de dos maneras —la clave se retiró
  // del catálogo, o sigue viva y ya no aplica a este tipo—, y para quien lee la
  // ficha son la misma cosa: está escrito y no se puede corregir. Hasta aquí
  // llegaban indistinguibles de un dato corregible, así que el broker lo
  // intentaba, no encontraba el campo en el editor y nada se lo explicaba.
  //
  // Ninguna de estas pruebas nombra una clave real en la lógica: el estado y el
  // motivo los trae el Core, y una prueba escrita sobre `servicios_disponibles`
  // no distinguiría un mecanismo de un `if` con ese nombre dentro.
  // ------------------------------------------------------------------

  const HISTORICA = {
    clave: 'zz_vieja',
    rotulo: 'Pregunta vieja',
    tipoDato: 'TEXTO',
    valor: 'lo que se supo',
    estadoDato: 'HISTORICO' as const,
    editable: false,
    motivoNoEditable: 'Ya no se pregunta para terreno. El valor se conserva tal como se registro.',
  };

  it('una caracteristica historica sigue enseñando su valor', async () => {
    await montar(ficha({ atributos: [HISTORICA] }));

    expect(html()).toContain('Pregunta vieja');
    expect(html()).toContain('lo que se supo');
  });

  it('y se marca, con el motivo que redacta el Core y no esta pantalla', async () => {
    await montar(ficha({ atributos: [HISTORICA] }));

    const marca = (fixture.nativeElement as HTMLElement).querySelector('.caracteristicas .historica');
    expect(marca).not.toBeNull();
    expect(marca?.textContent?.trim()).toBe('histórica');
    // La frase llega escrita: componerla aquí sería la matriz «motivo → texto»
    // viviendo en la interfaz, y con dos consumidores serían dos.
    expect(html()).toContain(HISTORICA.motivoNoEditable);
  });

  it('el mismo mecanismo sirve para una clave retirada del catalogo', async () => {
    // Mismo estado, otro motivo. La pantalla no distingue los dos casos y no
    // tiene por qué: lo que cambia es la frase, que viene hecha.
    const retirada = {
      ...HISTORICA,
      clave: 'zz_otra',
      rotulo: 'Otra pregunta',
      motivoNoEditable: 'Ya no se pregunta: «Otra pregunta» se retiro del catalogo.',
    };
    await montar(ficha({ atributos: [retirada] }));

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.caracteristicas .historica'),
    ).not.toBeNull();
    expect(html()).toContain('se retiro del catalogo');
  });

  it('una caracteristica vigente no lleva ninguna marca', async () => {
    await montar(PROP_0022);

    expect(html()).toContain('160 m²');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.caracteristicas .historica'),
    ).toBeNull();
  });

  it('sin la señal del Core no se marca nada: no se deduce por el nombre de la clave', async () => {
    // Claves que de verdad están fuera del contrato en el Core, pero el cable
    // no dice `estadoDato`. Si la pantalla marcara igualmente, estaría
    // decidiéndolo ella — y ésa es la deducción que el Core no le delega.
    await montar(
      ficha({
        atributos: [
          { clave: 'servicios_disponibles', rotulo: 'Servicios disponibles', tipoDato: 'LISTA', valor: 'Agua y luz' },
          { clave: 'area_terreno', rotulo: 'Área del terreno', tipoDato: 'DECIMAL', unidad: 'm²', valor: '777' },
        ],
      }),
    );

    expect(html()).toContain('Agua y luz');
    expect(html()).toContain('777 m²');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.caracteristicas .historica'),
    ).toBeNull();
  });

  // ------------------------------------------------------------------
  // La memoria del inmueble: el OTRO nivel de lectura
  // ------------------------------------------------------------------

  it('responde cuantas veces estuvo en alquiler y a cuanto se cerro la ultima vez', async () => {
    await montar(ficha({ encargos: TRES_ALQUILERES.encargos, historia: HISTORIA }));

    const episodio = (fixture.nativeElement as HTMLElement).querySelector(
      '.episodio[data-operacion="ALQUILER"]',
    );
    expect(episodio?.textContent).toContain('3 veces');
    expect(episodio?.textContent).toContain('sigue vigente');
    // Lo pedido y lo cerrado son dos numeros distintos, y se leen los dos.
    expect(episodio?.textContent).toContain('PEN 2,600');
    expect(episodio?.textContent).toContain('PEN 2,250');
  });

  it('sin cierre lo dice, en vez de repetir el precio pedido', async () => {
    const sinCierre: HistoriaComercial = {
      porOperacion: [
        {
          operacion: 'ALQUILER',
          operacionRotulo: 'Alquiler',
          veces: 1,
          desde: '2026-01-10',
          hasta: null,
          vivoAhora: true,
          ultimoPedido: { monto: 2600, moneda: 'PEN', fecha: '2026-01-10', idEncargo: 103, codigoEncargo: 'ENC-0103' },
          ultimoCierre: null,
        },
      ],
      linea: [],
    };
    await montar(ficha({ encargos: [encargo({})], historia: sinCierre }));

    const episodio = (fixture.nativeElement as HTMLElement).querySelector('.episodio');
    expect(episodio?.textContent).toContain('Sin cierre registrado');
    // El precio pedido aparece UNA vez -- en su casilla -- y no se cuela
    // ademas en la del cierre.
    expect((episodio?.textContent?.match(/2,600/g) ?? []).length).toBe(1);
  });

  it('la linea cruza encargos pero cada cifra dice de cual sale', async () => {
    await montar(ficha({ encargos: TRES_ALQUILERES.encargos, historia: HISTORIA }));

    const filas = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.linea li'),
    );
    expect(filas.length).toBe(3);
    // Agregada para leerse, NO fusionada: la procedencia sobrevive.
    expect(filas.map((fila) => fila.querySelector('.de')?.textContent?.trim())).toEqual([
      'Alquiler · ENC-0103',
      'Alquiler · ENC-0102',
      'Alquiler · ENC-0101',
    ]);
  });

  it('los dos niveles conviven: la historia comercial no reemplaza a los bloques de encargo', async () => {
    await montar(ficha({ encargos: TRES_ALQUILERES.encargos, historia: HISTORIA }));

    // idEncargo -> tres bloques auditables.
    expect(bloques().length).toBe(3);
    // idPropiedad -> una lectura de continuidad.
    expect(html()).toContain('Historia comercial');
    // Y los historicos de cada encargo siguen sin mezclarse.
    expect(bloques()[0].textContent).not.toContain('2,400');
  });


  // ------------------------------------------------------------------
  // Publicación: por encargo, nunca global
  // ------------------------------------------------------------------

  it('cada encargo gestiona SU publicacion, y no hay boton global', async () => {
    await montar(PROP_0022);

    // Un «Publicar propiedad» en la cabecera no diria cual de los dos publica.
    const cabecera = (fixture.nativeElement as HTMLElement).querySelector('.cabecera');
    expect(cabecera?.textContent).not.toContain('Publicar');

    // La entrada vive dentro de cada bloque de encargo.
    const cajas = bloques();
    expect(cajas[0].textContent).toContain('Publicación');
    expect(cajas[1].textContent).toContain('Publicación');
  });

  it('los anuncios de un encargo no aparecen en el otro', async () => {
    await montar(
      ficha({
        encargos: [
          encargo({
            idEncargo: 90, codigo: 'ENC-0090', operacion: 'VENTA', operacionRotulo: 'Venta',
            importe: 320000, importeRotulo: 'precio de venta',
            publicaciones: [
              {
                id: 1, idEncargo: 90, canal: 'URBANIA', tituloAnuncio: 'Venta oficina',
                importePublicado: 320000, moneda: 'USD', importeRotulo: 'precio de venta',
                estado: 'P', estadoRotulo: 'Publicada',
              },
            ],
          }),
          encargo({
            idEncargo: 91, codigo: 'ENC-0091', importe: 4800,
            publicaciones: [
              {
                id: 2, idEncargo: 91, canal: 'FACEBOOK', tituloAnuncio: 'Alquiler oficina',
                importePublicado: 4800, moneda: 'USD', importeRotulo: 'renta mensual',
                estado: 'S', estadoRotulo: 'Pausada',
              },
            ],
          }),
        ],
      }),
    );

    const [venta, alquiler] = bloques();
    expect(venta.textContent).toContain('URBANIA');
    expect(venta.textContent).not.toContain('FACEBOOK');
    expect(alquiler.textContent).toContain('FACEBOOK');
    expect(alquiler.textContent).not.toContain('URBANIA');
  });

  it('si el Core dice que no se puede publicar, no se ofrece el boton', async () => {
    await montar(
      ficha({
        encargos: [
          encargo({
            vivo: false, estado: 'C', estadoRotulo: 'Cerrada',
            publicacionGestionable: { permitida: false, motivo: 'El encargo ENC-0001 ya no esta vigente.' },
          }),
        ],
      }),
    );

    // La pantalla NO decide esto con un `estado === 'A'`: lo dice la capacidad.
    const caja = bloques()[0];
    expect(caja.textContent).not.toContain('Gestionar publicación');
    expect(caja.textContent).toContain('ya no esta vigente');
  });

  // El caso que V82 dejó incoherente: encargo VIVO, pero la ficha del inmueble
  // sin un dato que impide publicar. Antes el Core decía `permitida: true` y el
  // POST devolvía 400; ahora dice `false` y la pantalla lo obedece igual que
  // obedece el encargo cerrado. La pantalla no cuenta faltantes ni mira
  // exigencias: sólo lee la capacidad.
  it('un encargo vivo con la ficha incompleta tampoco ofrece el boton', async () => {
    await montar(
      ficha({
        faltanParaPublicar: [{ clave: 'tipo_acceso', rotulo: 'Tipo de acceso' }],
        encargos: [
          encargo({
            vivo: true, estado: 'A', estadoRotulo: 'Activa',
            publicacionGestionable: {
              permitida: false,
              motivo: 'Faltan datos de la ficha del inmueble.',
            },
          }),
        ],
      }),
    );

    const caja = bloques()[0];
    expect(caja.textContent).not.toContain('Gestionar publicación');
    expect(caja.textContent).toContain('Faltan datos de la ficha del inmueble');

    // Y la causa concreta se lee arriba, con el rótulo del catálogo.
    const aviso = (fixture.nativeElement as HTMLElement).querySelector('.aviso');
    expect(aviso?.textContent).toContain('Tipo de acceso');
  });

  it('la disponibilidad no se presenta como verdad de cabecera', async () => {
    await montar(PROP_0022);

    // Su vocabulario es de UNA operacion («Alquilado») y con venta y alquiler
    // vivos no dice cual. Va rotulada, en el bloque de datos.
    const cabecera = (fixture.nativeElement as HTMLElement).querySelector('.cabecera');
    expect(cabecera?.textContent).not.toContain('Disponible');
    expect(html()).toContain('Disponibilidad comercial');
  });

  // ------------------------------------------------------------------
  // Permisos
  // ------------------------------------------------------------------

  /**
   * **El botón de editar sale del cable, no del rol** (P0).
   *
   * Esta prueba decía «sólo el AGENTE ve el botón» y comprobaba
   * `sesion()?.rol === 'AGENTE'`. Dejó de ser cierto con V87: la autoridad
   * ya no es «ser agente», es **ser el responsable de esta propiedad**, y con
   * la regla vieja todo agente del tenant veía «Editar» en toda propiedad y se
   * llevaba un 403 al guardar.
   */
  it('no ofrece editar cuando la propiedad responde a otro agente', async () => {
    await montar(
      ficha({
        responsabilidad: {
          idResponsable: 44,
          nombre: 'Otro Agente',
          puedeEditar: false,
          motivo: 'OTRO_RESPONSABLE',
          motivoTexto: 'De esta propiedad responde otro agente.',
        },
      }),
      'AGENTE',
    );

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.acciones .primario'),
    ).toBeNull();
  });

  it('tampoco cuando la propiedad no tiene responsable: FALTANTE no es de todos', async () => {
    await montar(
      ficha({
        responsabilidad: {
          puedeEditar: false,
          motivo: 'FALTA_RESPONSABLE',
          motivoTexto: 'Esta propiedad no tiene agente responsable asignado.',
        },
      }),
      'AGENTE',
    );

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.acciones .primario'),
    ).toBeNull();
  });

  it('el responsable entra al editor universal, nunca a /locales', async () => {
    await montar(PROP_0022, 'AGENTE');

    const editar = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      '.acciones .primario',
    );
    expect(editar).not.toBeNull();
    expect(editar!.getAttribute('href')).toBe('/propiedades/3259/editar');
  });

  /**
   * **Y cuando no se puede, se dice por qué** (H8).
   *
   * `motivoBloqueo()` y `responsable()` estaban calculados y **sin pintar**: el
   * agente veía desaparecer «Editar» sin ninguna explicación y tenía que
   * adivinar si le faltaba un permiso o un dato. El Core devuelve el motivo con
   * el 403 justamente para no obligar a eso, y la pantalla lo deshacía.
   */
  it('dice por qué no se puede editar, y quién responde', async () => {
    await montar(
      ficha({
        responsabilidad: {
          idResponsable: 44,
          nombre: 'Otro Agente',
          puedeEditar: false,
          motivo: 'OTRO_RESPONSABLE',
          motivoTexto: 'De esta propiedad responde otro agente.',
        },
      }),
      'AGENTE',
    );

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('De esta propiedad responde otro agente.');
    expect(html).toContain('Otro Agente');
  });

  /**
   * **Ante el silencio del cable, las tres pantallas dicen lo mismo** (H8).
   *
   * Jackson va `NON_NULL`, así que un `responsabilidad` ausente llega
   * `undefined`. Esta ficha caía a `false` y el editor a `true` sobre
   * exactamente la misma respuesta. Ahora lo decide `puedeEscribir`, una vez.
   */
  it('sin bloque de autoridad no ofrece editar, y lo explica', async () => {
    await montar(ficha({ responsabilidad: undefined }), 'AGENTE');

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.acciones .primario'),
    ).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No llegó quién responde por esta propiedad',
    );
  });

  /**
   * **La llave, donde ya se ve la propiedad** (C3).
   *
   * Que se ofrezca lo dice el Core en `puedeTraspasar`, no un rol leído de la
   * sesión: por eso el caso que lo comprueba monta la ficha con **rol AGENTE**
   * y `puedeTraspasar` verdadero. Si alguien volviera a decidirlo mirando la
   * sesión, esto se pone rojo.
   */
  it('ofrece traspasar sólo cuando el Core dice que se puede', async () => {
    await montar(
      ficha({
        responsabilidad: {
          idResponsable: 44,
          nombre: 'Otro Agente',
          puedeEditar: false,
          motivo: 'NO_OPERA',
          motivoTexto: 'Supervisar y gobernar no es registrar.',
          puedeTraspasar: true,
        },
      }),
      'AGENTE',
    );

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('cl-traspaso-responsable'),
    ).not.toBeNull();
  });

  it('no ofrece traspasar cuando el Core no lo concede', async () => {
    await montar(PROP_0022, 'BROKER');

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('cl-traspaso-responsable'),
    ).toBeNull();
  });
});
