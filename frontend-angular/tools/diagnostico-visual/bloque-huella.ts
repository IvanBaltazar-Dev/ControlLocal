
// ====================================================================
// HUELLA DE ESTILO CALCULADO — instrumento, NO prueba.
// --------------------------------------------------------------------
// Esto NO forma parte de la suite. Se pega a mano al final de
// `dashboard.spec.ts`, se ejecuta una vez a cada lado de un refactor y
// se comparan las dos salidas con `comparar.js`. Después se retira.
//
// Lee `README.md` antes de usarlo: lleva el procedimiento y, sobre todo,
// por qué esto no es un gate.
//
// Depende de lo que ya declara `dashboard.spec.ts` (`TAREA`, `HALLAZGO`,
// `carga`, `sesion`, `Dashboard` y los servicios). Por eso se pega ahí y
// no compila por su cuenta: `tools/` queda fuera de los `tsconfig`, así
// que nadie lo typechequea mientras vive aquí.
// ====================================================================

const PROPS_HUELLA = [
  'display',
  'position',
  'top',
  'left',
  'right',
  'bottom',
  'width',
  'height',
  'margin-top',
  'margin-right',
  'margin-bottom',
  'margin-left',
  'padding-top',
  'padding-right',
  'padding-bottom',
  'padding-left',
  'border-top-width',
  'border-right-width',
  'border-bottom-width',
  'border-left-width',
  'border-top-style',
  'border-top-color',
  'border-left-color',
  'border-top-left-radius',
  'border-bottom-right-radius',
  'background-color',
  'background-image',
  'box-shadow',
  'color',
  'font-size',
  'font-weight',
  'line-height',
  'letter-spacing',
  'text-transform',
  'text-align',
  'gap',
  'grid-template-columns',
  'flex-direction',
  'flex-grow',
  'flex-shrink',
  'align-items',
  'justify-content',
  'opacity',
  'z-index',
  'overflow-x',
  'overflow-y',
  'animation-name',
  'animation-duration',
  'animation-delay',
  'transform',
  'transition-property',
  'transition-duration',
  'cursor',
  'list-style-type',
  'min-width',
  'max-width',
];

function huellaDeLaSuperficie(raizDelDom: HTMLElement, estado: string): void {
  const radar = raizDelDom.querySelector('.radar');
  if (!radar) {
    console.log(`##H## ${estado} | SIN RADAR`);
    return;
  }
  const todos = [radar, ...Array.from(radar.querySelectorAll('*'))];
  todos.forEach((el, indice) => {
    const clases = Array.from(el.classList).sort().join('.');
    const calculado = getComputedStyle(el as HTMLElement);
    const declaraciones = PROPS_HUELLA.map((p) => `${p}=${calculado.getPropertyValue(p)}`).join(';');
    // La clave ignora el nombre de la etiqueta a propósito: al partir el Radar
    // cuatro `<div>` pasan a ser anfitriones de componente y solo cambia eso.
    console.log(
      `##H## ${estado} | ${indice} | ${clases || '(sin clase)'} | ${el.tagName.toLowerCase()} | ${declaraciones}`,
    );
  });
  console.log(`##H## ${estado} | TOTAL | ${todos.length}`);
}

const TAREA_RICA: Tarea = {
  ...TAREA,
  fechaProgramada: '2026-09-02',
  interpretacion: {
    comoEsta: {
      avance: { hechos: 2, total: 4, unidad: 'documentos' },
      hechos: [
        { estado: 'HECHO', texto: 'El encargo esta firmado' },
        { estado: 'FALTA', texto: 'Falta la ficha tecnica del local' },
        { estado: 'FRENO', texto: 'No se puede publicar sin la ficha' },
      ],
    },
    expediente: [
      {
        rotulo: 'Encargo',
        valor: 'Exclusiva hasta el 30 de noviembre',
        estado: 'BIEN',
        ventana: { consumido: 168, total: 180 },
        serie: null,
      },
      {
        rotulo: 'Renta',
        valor: 'S/ 4 200 mensuales',
        estado: 'OJO',
        ventana: null,
        serie: [3800, 3950, 4100, 4200],
        contraste: {
          forma: 'POSICION_EN_RANGO',
          motivo: 'NINGUNO',
          minimo: 3000,
          maximo: 5200,
          valor: 4200,
          posicionPorcentaje: 55,
          moneda: 'PEN',
          zona: 'Miraflores',
          observaciones: 9,
        },
      },
      {
        rotulo: 'Plazo',
        valor: '17 dias por encima del plazo',
        estado: 'MAL',
        ventana: { consumido: 17, total: 15 },
        serie: null,
      },
      {
        rotulo: 'Propietario',
        valor: 'Elena Castillo Paredes',
        estado: null,
        ventana: null,
        serie: null,
      },
    ],
    lectura: 'El encargo aguanta, pero la ficha lleva parada mas de dos semanas.',
  },
};

describe('HUELLA del Radar', () => {
  let api: jasmine.SpyObj<DashboardService>;
  let tareas: jasmine.SpyObj<TareasService>;
  let navegacion: jasmine.SpyObj<NavegacionLegado>;
  let visitas: jasmine.SpyObj<VisitasService>;
  let interacciones: jasmine.SpyObj<InteraccionesService>;
  let fixture: ComponentFixture<Dashboard>;

  function raiz(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function montar(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: DashboardService, useValue: api },
        { provide: TareasService, useValue: tareas },
        { provide: NavegacionLegado, useValue: navegacion },
        { provide: VisitasService, useValue: visitas },
        { provide: InteraccionesService, useValue: interacciones },
        { provide: AuthService, useValue: { sesion: signal(sesion('AGENTE')) } },
        provideRouter([]),
        provideHttpClient(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function asentar(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<DashboardService>('DashboardService', ['cargar']);
    tareas = jasmine.createSpyObj<TareasService>('TareasService', ['bandeja', 'cancelar']);
    tareas.bandeja.and.resolveTo([TAREA_RICA]);
    tareas.cancelar.and.resolveTo(undefined);
    navegacion = jasmine.createSpyObj<NavegacionLegado>('NavegacionLegado', [
      'abrir',
      'puedeAbrir',
    ]);
    navegacion.puedeAbrir.and.returnValue(true);
    navegacion.abrir.and.resolveTo(true);
    visitas = jasmine.createSpyObj<VisitasService>('VisitasService', ['realizar', 'noRealizada']);
    interacciones = jasmine.createSpyObj<InteraccionesService>('InteraccionesService', ['registrar']);
  });

  it('estado 1 - vista general con hallazgo', async () => {
    api.cargar.and.resolveTo(carga({ hallazgos: [HALLAZGO] }));
    await montar();

    huellaDeLaSuperficie(raiz(), 'GENERAL');
    expect(raiz().querySelector('.hallazgo')).not.toBeNull();
  });

  it('estado 2 - resolver', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [TAREA_RICA], totalRecords: 1, page: 1, pageSize: 5 } }),
    );
    await montar();
    fixture.componentInstance['seleccionar']('tarea:1');
    await asentar();

    huellaDeLaSuperficie(raiz(), 'RESOLVER');
    expect(raiz().querySelector('.reco')).not.toBeNull();
  });

  it('estado 3 - antecedentes', async () => {
    api.cargar.and.resolveTo(
      carga({ bandeja: { items: [TAREA_RICA], totalRecords: 1, page: 1, pageSize: 5 } }),
    );
    await montar();
    fixture.componentInstance['seleccionar']('tarea:1');
    await asentar();
    const pestana = Array.from(raiz().querySelectorAll<HTMLButtonElement>('.vistas button')).find(
      (b) => b.textContent?.includes('Antecedentes'),
    );
    pestana?.click();
    await asentar();

    huellaDeLaSuperficie(raiz(), 'ANTECEDENTES');
    expect(raiz().querySelector('.ant-fila')).not.toBeNull();
  });
});
