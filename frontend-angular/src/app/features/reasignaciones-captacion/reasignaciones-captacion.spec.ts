import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { of } from 'rxjs';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  CandidatoAgente,
  Captacion,
  CaptacionesService,
  ReasignacionCaptacion,
} from '../../core/api/captaciones.service';
import { MODULOS } from '../../core/auth/acceso';
import {
  filtrosReasignacionesDesdeUrl,
  ReasignacionesCaptacion,
} from './reasignaciones-captacion';

const CAPTACION: Captacion = {
  id: 9,
  codigoCaptacion: 'CAP-0009',
  estado: 'A',
  direccionLocal: 'Av. Larco 700',
  idAgente: 30,
  agenteNombre: 'Valentina Mora',
};

const EVENTO: ReasignacionCaptacion = {
  idReasignacion: 4,
  idCaptacion: 9,
  codigoCaptacion: 'CAP-0009',
  direccionLocal: 'Av. Larco 700',
  idAgenteAnterior: 30,
  agenteAnteriorNombre: 'Valentina Mora',
  idAgenteNuevo: 31,
  agenteNuevoNombre: 'Javier Ruiz',
  idBroker: 20,
  brokerNombre: 'Ricardo Salas',
  fechaCambio: new Date().toISOString(),
  motivo: 'Balance de cartera comercial',
};

/**
 * Los destinos **ya depurados por el Core** (D-P0-7 + D-P0-12). Que aquí sólo
 * venga uno es la mitad de lo que se prueba: la pantalla pinta exactamente lo
 * que llega, sin volver a filtrar por estado ni por texto.
 */
const CANDIDATOS: CandidatoAgente[] = [
  { idAgente: 31, nombre: 'Javier Ruiz', codigoAgente: 'AGE-031', zonaAsignada: 'Lima Norte' },
];

interface AccesoReasignaciones {
  motivo: { setValue(valor: string): void };
  seleccionarCaptacion(id: number): void;
  seleccionarAgente(id: number): void;
  cambiarBusquedaAgente(valor: string): void;
  prepararReasignacion(): void;
  confirmarReasignacion(): Promise<void>;
  cambiarTexto(texto: string): void;
  cambiarBusquedaHistorial(texto: string): void;
}

describe('ReasignacionesCaptacion', () => {
  let api: jasmine.SpyObj<CaptacionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', [
      'reasignables$', 'historialReasignaciones$', 'reasignar', 'candidatosReasignacion',
    ]);
    api.reasignables$.and.returnValue(of(pagina([CAPTACION], 1, 8)));
    api.historialReasignaciones$.and.returnValue(of([EVENTO]));
    api.reasignar.and.resolveTo({ ...CAPTACION, idAgente: 31, agenteNombre: 'Javier Ruiz' });
    api.candidatosReasignacion.and.resolveTo(pagina(CANDIDATOS, 1, 50));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('carga solo captaciones activas del endpoint y muestra la trazabilidad', async () => {
    const fixture = await montar();
    expect(api.reasignables$).toHaveBeenCalledOnceWith({ pagina: 1, tamano: 8, q: undefined });
    expect(api.historialReasignaciones$).toHaveBeenCalled();
    expect(texto(fixture)).toContain('CAP-0009');
    expect(texto(fixture)).toContain('Balance de cartera comercial');
  });

  /**
   * **Los destinos los decide el Core** (D-P0-12).
   *
   * Antes esta pantalla pedía `GET /agentes` y depuraba en el cliente por
   * `estadoAdministrativo` y `estadoOperativo`: dos de las seis condiciones,
   * sobre una página. Ahora pide la lista **de esta captación** y pinta lo que
   * llega — la prueba lo comprueba pidiendo el id correcto y comprobando que no
   * se consulta ninguna otra fuente de agentes.
   */
  it('pide al Core los destinos de la captación seleccionada, y no filtra en cliente', async () => {
    const fixture = await montar();
    expect(api.candidatosReasignacion).toHaveBeenCalledWith(9, undefined);
    expect(texto(fixture)).toContain('Javier Ruiz');
    expect(texto(fixture)).toContain('AGE-031');
  });

  /**
   * El texto viaja al Core como `texto`. Filtrar aquí devolvería resultados
   * incompletos en cuanto haya más agentes que sitio en una página.
   */
  it('busca los destinos en el servidor, con el texto escrito', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    api.candidatosReasignacion.calls.reset();
    acceso.cambiarBusquedaAgente('ruiz');
    await esperar(350);
    expect(api.candidatosReasignacion).toHaveBeenCalledWith(9, 'ruiz');
  });

  it('exige un motivo trazable antes de abrir la confirmación', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    acceso.seleccionarCaptacion(9);
    acceso.seleccionarAgente(31);
    acceso.motivo.setValue('corto');
    acceso.prepararReasignacion();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('al menos 10 caracteres');
    expect(api.reasignar).not.toHaveBeenCalled();
  });

  it('reasigna declarando el agente observado y refresca lista e historial', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    acceso.seleccionarCaptacion(9);
    acceso.seleccionarAgente(31);
    acceso.motivo.setValue('  Balance de cartera  ');
    acceso.prepararReasignacion();
    await acceso.confirmarReasignacion();
    fixture.detectChanges();
    // El cuarto argumento es el agente que se estaba VIENDO en la fila
    // (D-P0-9): sin él, la reasignación no declara de dónde parte y vuelve a
    // ser «pon a B», que es la última escritura ganando.
    expect(api.reasignar).toHaveBeenCalledOnceWith(9, 31, 'Balance de cartera', 30);
    expect(api.reasignables$).toHaveBeenCalledTimes(2);
    expect(api.historialReasignaciones$).toHaveBeenCalledTimes(2);
    expect(texto(fixture)).toContain('fue reasignada a Javier Ruiz');
  });

  /**
   * El 409 significa «el agente que veías ya no es el que hay». No se
   * reintenta: se muestra el mensaje del Core y se recarga la lista, porque la
   * siguiente decisión tiene que partir del estado real.
   */
  it('ante un 409 muestra el mensaje del Core y recarga la lista', async () => {
    api.reasignar.and.rejectWith(new ApiError(409,
      'El agente de este encargo cambio desde que se miro: hoy lo lleva 31.'));
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    acceso.seleccionarCaptacion(9);
    acceso.seleccionarAgente(31);
    acceso.motivo.setValue('Balance de cartera');
    acceso.prepararReasignacion();
    await acceso.confirmarReasignacion();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('hoy lo lleva 31');
    expect(api.reasignables$).toHaveBeenCalledTimes(2);
  });

  it('conserva búsqueda y página de captaciones en la URL', async () => {
    const acceso = (await montar()).componentInstance as unknown as AccesoReasignaciones;
    acceso.cambiarTexto(' larco ');
    expect(router.navigate).toHaveBeenCalledOnceWith([], jasmine.objectContaining({
      queryParams: { texto: 'larco', page: 1 },
    }));
    expect(filtrosReasignacionesDesdeUrl(parametros({ texto: 'centro', page: '3' })))
      .toEqual({ texto: 'centro', page: 3 });
    expect(filtrosReasignacionesDesdeUrl(parametros({ page: '-2' })).page).toBe(1);
  });

  it('filtra el historial y no ofrece reportes PDF', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReasignaciones;
    acceso.cambiarBusquedaHistorial('sin coincidencia');
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Todavía no hay movimientos con estos filtros');
    expect(texto(fixture)).not.toContain('PDF');
  });

  it('activa el módulo solo para broker y admin', () => {
    const modulo = MODULOS.find((item) => item.ruta === 'captaciones/reasignaciones')!;
    expect(modulo).toBeDefined();
    expect(modulo.roles).toEqual(['BROKER', 'TENANT_ADMIN']);
  });

  async function montar(): Promise<ComponentFixture<ReasignacionesCaptacion>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReasignacionesCaptacion],
      providers: [
        { provide: CaptacionesService, useValue: api },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(parametros({})) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(ReasignacionesCaptacion);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[], page: number, pageSize: number): PageResponse<T> {
  return { items, totalRecords: items.length, page, pageSize };
}

/** Para dejar pasar el `debounce` del buscador de destinos. */
function esperar(ms: number): Promise<void> {
  return new Promise((listo) => setTimeout(listo, ms));
}

function parametros(valores: Record<string, string>): ParamMap {
  return convertToParamMap(valores);
}

function texto(fixture: ComponentFixture<ReasignacionesCaptacion>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
