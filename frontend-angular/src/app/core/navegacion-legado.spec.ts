import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { SolicitudesService } from './api/solicitudes.service';
import { NavegacionLegado, traducirRutaLegado } from './navegacion-legado';

describe('traducirRutaLegado', () => {
  it('traduce los detalles por id del cable a las rutas del SPA', () => {
    expect(traducirRutaLegado('prospeccion-detail/7')).toEqual({
      tipo: 'ruta',
      comandos: ['/prospecciones', '7'],
      queryParams: undefined,
    });
    expect(traducirRutaLegado('oportunidad-detail/3')).toEqual({
      tipo: 'ruta',
      comandos: ['/oportunidades', '3'],
      queryParams: undefined,
    });
    expect(traducirRutaLegado('cliente-detail/9')).toEqual({
      tipo: 'ruta',
      comandos: ['/clientes', '9'],
      queryParams: undefined,
    });
  });

  /** `owner-detail` es el nombre del Blazor; el SPA los llama propietarios. */
  it('renombra owner-detail a propietarios', () => {
    expect(traducirRutaLegado('owner-detail/4')).toEqual({
      tipo: 'ruta',
      comandos: ['/propietarios', '4'],
      queryParams: undefined,
    });
  });

  it('separa la query de las visitas en vez de meterla en el path', () => {
    expect(traducirRutaLegado('visitas?focus=12')).toEqual({
      tipo: 'ruta',
      comandos: ['/visitas'],
      queryParams: { focus: '12' },
    });
  });

  /**
   * La ficha de solicitud del SPA enruta por CÓDIGO, pero las alertas viajan
   * con el id numérico. Se distingue por la forma del valor, no por el origen.
   */
  it('distingue el codigo de solicitud del id numerico', () => {
    expect(traducirRutaLegado('solicitud-detail/SOL-260715103000')).toEqual({
      tipo: 'ruta',
      comandos: ['/solicitudes', 'SOL-260715103000'],
      queryParams: undefined,
    });
    expect(traducirRutaLegado('solicitud-detail/42')).toEqual({
      tipo: 'solicitud-por-id',
      id: 42,
    });
  });

  /** Una captación por id no es resoluble: cae a su bandeja, no a un 404. */
  it('manda a la bandeja cuando la captacion viene sin codigo', () => {
    expect(traducirRutaLegado('captacion-detail/CAP-0001')).toEqual({
      tipo: 'ruta',
      comandos: ['/captaciones', 'CAP-0001'],
      queryParams: undefined,
    });
    expect(traducirRutaLegado('captacion-detail/15')).toEqual({
      tipo: 'ruta',
      comandos: ['/captaciones'],
      queryParams: undefined,
    });
  });

  it('acepta los listados cuyo nombre ya coincide', () => {
    expect(traducirRutaLegado('comisiones')).toEqual({
      tipo: 'ruta',
      comandos: ['/comisiones'],
      queryParams: undefined,
    });
    expect(traducirRutaLegado('captaciones')).toEqual({
      tipo: 'ruta',
      comandos: ['/captaciones'],
      queryParams: undefined,
    });
  });

  /**
   * Lo que no se sabe traducir devuelve null a propósito: el aviso se muestra
   * sin enlace. Inventar un destino sería peor.
   */
  it('devuelve null para lo desconocido, vacio o sin id', () => {
    expect(traducirRutaLegado('pantalla-que-no-existe/1')).toBeNull();
    expect(traducirRutaLegado('')).toBeNull();
    expect(traducirRutaLegado(undefined)).toBeNull();
    expect(traducirRutaLegado('cliente-detail/')).toBeNull();
  });
});

describe('NavegacionLegado', () => {
  let router: jasmine.SpyObj<Router>;
  let solicitudes: jasmine.SpyObj<SolicitudesService>;
  let navegacion: NavegacionLegado;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
    solicitudes = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['obtener']);

    TestBed.configureTestingModule({
      providers: [
        NavegacionLegado,
        { provide: Router, useValue: router },
        { provide: SolicitudesService, useValue: solicitudes },
      ],
    });
    navegacion = TestBed.inject(NavegacionLegado);
  });

  it('navega sin pedir nada al API cuando la ruta ya es resoluble', async () => {
    expect(await navegacion.abrir('visitas?focus=12')).toBeTrue();

    expect(router.navigate).toHaveBeenCalledOnceWith(['/visitas'], {
      queryParams: { focus: '12' },
    });
    expect(solicitudes.obtener).not.toHaveBeenCalled();
  });

  /** El id→código se resuelve al PULSAR, no al listar los avisos. */
  it('resuelve el codigo de la solicitud antes de navegar', async () => {
    solicitudes.obtener.and.resolveTo({ id: 42, codigoSolicitud: 'SOL-1' });

    expect(await navegacion.abrir('solicitud-detail/42')).toBeTrue();

    expect(solicitudes.obtener).toHaveBeenCalledOnceWith(42);
    expect(router.navigate).toHaveBeenCalledOnceWith(['/solicitudes', 'SOL-1']);
  });

  it('no navega si la solicitud ya no es visible para este actor', async () => {
    solicitudes.obtener.and.rejectWith(new Error('403'));

    expect(await navegacion.abrir('solicitud-detail/42')).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('una ruta desconocida no navega y se puede preguntar antes', async () => {
    expect(navegacion.puedeAbrir('pantalla-inventada/1')).toBeFalse();
    expect(navegacion.puedeAbrir('visitas?focus=1')).toBeTrue();
    expect(await navegacion.abrir('pantalla-inventada/1')).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
