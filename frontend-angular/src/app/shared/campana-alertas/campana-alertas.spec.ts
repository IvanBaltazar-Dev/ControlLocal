import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Alerta, AlertasService } from '../../core/api/alertas.service';
import { NavegacionLegado } from '../../core/navegacion-legado';
import { CampanaAlertas } from './campana-alertas';

function alerta(parcial: Partial<Alerta> = {}): Alerta {
  return {
    id: 1,
    tipo: 'SIN_RESPUESTA',
    severidad: 'MEDIA',
    entidadTipo: 'PROSPECCION',
    entidadId: 7,
    idAgente: 28,
    agenteNombre: 'Valentina Mora',
    mensaje: 'Recontacta la prospeccion PRO-0002.',
    estado: 'A',
    fechaGeneracion: '2026-08-04T15:43:00Z',
    ruta: 'prospeccion-detail/7',
    ...parcial,
  };
}

describe('CampanaAlertas', () => {
  let api: jasmine.SpyObj<AlertasService>;
  let navegacion: jasmine.SpyObj<NavegacionLegado>;
  let fixture: ComponentFixture<CampanaAlertas>;

  async function montar(): Promise<CampanaAlertas> {
    await TestBed.configureTestingModule({
      imports: [CampanaAlertas],
      providers: [
        { provide: AlertasService, useValue: api },
        { provide: NavegacionLegado, useValue: navegacion },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CampanaAlertas);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<AlertasService>('AlertasService', ['pagina', 'atender']);
    api.pagina.and.resolveTo({ items: [alerta()], totalRecords: 3, page: 1, pageSize: 20 });
    api.atender.and.resolveTo({ atendida: true });
    navegacion = jasmine.createSpyObj<NavegacionLegado>('NavegacionLegado', [
      'abrir',
      'puedeAbrir',
    ]);
    navegacion.puedeAbrir.and.returnValue(true);
    navegacion.abrir.and.resolveTo(true);
  });

  /**
   * El recurso ya devuelve solo las ACTIVAS, así que el contador es su
   * `totalRecords` y no hay que filtrar por estado en el cliente.
   */
  it('el contador sale del total del recurso, no de las filas visibles', async () => {
    const campana = await montar();

    expect(api.pagina).toHaveBeenCalledOnceWith(1, 20);
    expect(campana['total']()).toBe(3);
    expect(campana['insignia']()).toBe('3');
    expect(campana['hayMas']()).toBeTrue();
  });

  it('mas de nueve avisos se rotulan 9+', async () => {
    api.pagina.and.resolveTo({ items: [], totalRecords: 25, page: 1, pageSize: 20 });
    const campana = await montar();

    expect(campana['insignia']()).toBe('9+');
  });

  /** Sin sondeo: se refresca al entrar y al abrir, que es cuando aporta. */
  it('refresca al abrir el panel', async () => {
    const campana = await montar();
    campana['alternar']();

    expect(campana['abierta']()).toBeTrue();
    expect(api.pagina).toHaveBeenCalledTimes(2);
  });

  it('cerrar no vuelve a pedir', async () => {
    const campana = await montar();
    campana['alternar']();
    campana['alternar']();

    expect(campana['abierta']()).toBeFalse();
    expect(api.pagina).toHaveBeenCalledTimes(2);
  });

  it('atender retira la fila y baja el contador', async () => {
    const campana = await montar();

    await campana['atender'](alerta());

    expect(api.atender).toHaveBeenCalledOnceWith(1);
    expect(campana['alertas']()).toEqual([]);
    expect(campana['total']()).toBe(2);
  });

  /**
   * `atendida: false` significa "ya estaba atendida", o sea que tampoco sigue
   * activa: la fila se retira igual en vez de quedarse zombi.
   */
  it('retira la fila tambien cuando el backend responde que ya estaba atendida', async () => {
    api.atender.and.resolveTo({ atendida: false });
    const campana = await montar();

    await campana['atender'](alerta());

    expect(campana['alertas']()).toEqual([]);
  });

  it('un fallo al atender se dice y no borra la fila', async () => {
    api.atender.and.rejectWith(new Error('boom'));
    const campana = await montar();

    await campana['atender'](alerta());

    expect(campana['alertas']().length).toBe(1);
    expect(campana['error']()).toBeTruthy();
  });

  /** Los tipos que la v1 no enruta viajan sin `ruta`: se leen, no navegan. */
  it('el aviso sin ruta no ofrece abrir', async () => {
    navegacion.puedeAbrir.and.returnValue(false);
    const campana = await montar();

    expect(campana['puedeAbrir'](alerta({ ruta: undefined }))).toBeFalse();
  });

  it('al abrir un aviso se cierra el panel', async () => {
    const campana = await montar();
    campana['alternar']();

    await campana['abrir'](alerta());

    expect(navegacion.abrir).toHaveBeenCalledOnceWith('prospeccion-detail/7');
    expect(campana['abierta']()).toBeFalse();
  });

  /** La campana es chrome: si falla, lo dice dentro y no rompe la pantalla. */
  it('un fallo de carga no deja el contador en un numero inventado', async () => {
    api.pagina.and.rejectWith(new Error('sin red'));
    const campana = await montar();

    expect(campana['total']()).toBe(0);
    expect(campana['error']()).toBeTruthy();
  });
});
