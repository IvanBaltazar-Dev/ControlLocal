import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import {
  FilaSeguimiento,
  PaginaSeguimiento,
  SeguimientoService,
} from '../../core/api/seguimiento.service';
import { NavegacionLegado } from '../../core/navegacion-legado';
import { SeguimientoComercial } from './seguimiento-comercial';

function fila(parcial: Partial<FilaSeguimiento> = {}): FilaSeguimiento {
  return {
    proceso: 'Captacion',
    codigo: 'CAP-0001',
    cliente: '-',
    local: 'Av. Larco 812',
    distrito: 'Miraflores',
    agente: 'Valentina Mora',
    propietario: 'Inmobiliaria Pacifico SAC',
    estado: 'Pendiente de revision',
    ultimoHito: 'Vigente hasta 08 Jan 2027',
    ruta: 'captacion-detail/CAP-0001',
    rutaRevision: 'captacion-review/CAP-0001',
    icono: 'pin',
    tono: 'blue',
    fechaOrden: '2026-07-08T00:00',
    monto: '',
    ...parcial,
  };
}

const PAGINA: PaginaSeguimiento = {
  items: [fila()],
  totalRecords: 5,
  page: 1,
  pageSize: 8,
  counts: { todos: 5, prospeccion: 2, captacion: 1, oportunidad: 1, solicitud: 1, cierre: 0 },
  options: {
    agentes: ['Valentina Mora'],
    propietarios: ['Inmobiliaria Pacifico SAC'],
    estados: ['Abierta', 'Captado'],
    distritos: ['Lima', 'Miraflores'],
  },
};

describe('SeguimientoComercial', () => {
  let api: jasmine.SpyObj<SeguimientoService>;
  let navegacion: jasmine.SpyObj<NavegacionLegado>;
  let fixture: ComponentFixture<SeguimientoComercial>;

  async function montar(params: Record<string, string> = {}): Promise<SeguimientoComercial> {
    await TestBed.configureTestingModule({
      imports: [SeguimientoComercial],
      providers: [
        { provide: SeguimientoService, useValue: api },
        { provide: NavegacionLegado, useValue: navegacion },
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(params) } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SeguimientoComercial);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<SeguimientoService>('SeguimientoService', ['pagina']);
    api.pagina.and.resolveTo(PAGINA);
    navegacion = jasmine.createSpyObj<NavegacionLegado>('NavegacionLegado', [
      'abrir',
      'puedeAbrir',
    ]);
    navegacion.puedeAbrir.and.returnValue(true);
    navegacion.abrir.and.resolveTo(true);
  });

  /** El 8 es el techo del recurso: pedir más no devuelve más. */
  it('pide siempre el tamano tope del recurso', async () => {
    await montar();

    expect(api.pagina).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ tamano: 8, pagina: 1 }),
    );
  });

  it('los filtros vacios no viajan como cadena vacia', async () => {
    await montar();
    const enviado = api.pagina.calls.mostRecent().args[0]!;

    expect(enviado.tipo).toBeUndefined();
    expect(enviado.q).toBeUndefined();
    expect(enviado.agente).toBeUndefined();
  });

  it('respeta el proceso de la URL y descarta el inventado', async () => {
    const conTipo = await montar({ tipo: 'Solicitud' });
    expect(conTipo['tipo']()).toBe('Solicitud');

    TestBed.resetTestingModule();
    const inventado = await montar({ tipo: 'Trámite' });
    expect(inventado['tipo']()).toBe('');
  });

  /**
   * Los KPI son atajos y `counts` viene del backend contando con todos los
   * filtros menos el de proceso: recalcularlos aquí los rompería.
   */
  it('los KPI salen de counts, no de las filas visibles', async () => {
    const pantalla = await montar();
    const kpis = pantalla['kpis']();

    expect(kpis[0]).toEqual(jasmine.objectContaining({ etiqueta: 'Todos', total: 5 }));
    expect(kpis.find((k) => k.etiqueta === 'Prospección')?.total).toBe(2);
    expect(pantalla['datos']().items.length).toBe(1);
  });

  it('pulsar dos veces el mismo KPI quita el filtro', async () => {
    const pantalla = await montar();

    pantalla['filtrarPorProceso']('Solicitud');
    expect(pantalla['tipo']()).toBe('Solicitud');

    pantalla['filtrarPorProceso']('Solicitud');
    expect(pantalla['tipo']()).toBe('');
  });

  /** Los selectores salen de `options`, que el backend calcula sin filtros. */
  it('llena los selectores con las opciones del alcance, no de la pagina', async () => {
    const pantalla = await montar();

    expect(pantalla['opciones']().distritos).toEqual(['Lima', 'Miraflores']);
    expect(pantalla['opciones']().agentes).toEqual(['Valentina Mora']);
  });

  it('cambiar un filtro vuelve a la pagina 1', async () => {
    const pantalla = await montar();
    pantalla['cambiarPagina'](2);
    expect(pantalla['pagina']()).toBe(2);

    pantalla['texto'].set('larco');
    pantalla['aplicar']();

    expect(pantalla['pagina']()).toBe(1);
  });

  /**
   * El `ultimoHito` cambia de forma según el proceso: solo se formatea cuando
   * es una fecha ISO pelada, y el resto se muestra tal cual.
   */
  it('formatea el hito solo cuando es una fecha', async () => {
    const pantalla = await montar();

    expect(pantalla['hito'](fila({ ultimoHito: 'Vigente hasta 08 Jan 2027' }))).toBe(
      'Vigente hasta 08 Jan 2027',
    );
    expect(pantalla['hito'](fila({ ultimoHito: '2026-07-16T14:00' }))).toContain('jul');
    expect(pantalla['hito'](fila({ ultimoHito: 'CAP-0001' }))).toBe('CAP-0001');
  });

  it('agrupa el monto solo cuando de verdad es un numero', async () => {
    const pantalla = await montar();

    expect(pantalla['importe'](fila({ monto: '9000.00' }))).toBe('9,000');
    expect(pantalla['importe'](fila({ monto: '' }))).toBe('');
    expect(pantalla['importe'](fila({ monto: 'a convenir' }))).toBe('a convenir');
  });

  it('solo ofrece Revisar cuando el cable manda ruta de revision', async () => {
    const pantalla = await montar();

    expect(pantalla['puedeRevisar'](fila())).toBeTrue();
    expect(pantalla['puedeRevisar'](fila({ rutaRevision: '' }))).toBeFalse();
  });

  it('avisa cuando la fila no lleva a ninguna pantalla migrada', async () => {
    navegacion.abrir.and.resolveTo(false);
    const pantalla = await montar();

    await pantalla['abrir']('pantalla-inventada/1');

    expect(pantalla['aviso']()).toContain('no tiene una pantalla');
  });
});
