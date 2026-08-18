import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { PageResponse, paginaVacia } from '../../core/api/api.types';
import { ResumenVisitas, Visita, VisitasService } from '../../core/api/visitas.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { filtrosVisitasDesdeUrl, Visitas } from './visitas';

const PROGRAMADA: Visita = {
  id: 21,
  idOportunidad: 9,
  codigoOportunidad: 'OP-260730120000',
  fechaVisita: '2026-08-20',
  horaVisita: '10:30',
  estado: 'P',
  clienteNombre: 'Lucía Ramírez',
  direccionLocal: 'Av. Larco 123',
  distritoLocal: 'Miraflores',
  agenteNombre: 'Valeria Mora',
};

const REALIZADA_SIN_DESENLACE: Visita = { ...PROGRAMADA, id: 22, estado: 'R' };
const REALIZADA_CON_DESENLACE: Visita = { ...PROGRAMADA, id: 23, estado: 'R', resultado: 'I' };

const PAGINA: PageResponse<Visita> = {
  items: [PROGRAMADA, REALIZADA_SIN_DESENLACE, REALIZADA_CON_DESENLACE],
  totalRecords: 3,
  page: 1,
  pageSize: 10,
};

const RESUMEN: ResumenVisitas = {
  total: 9,
  programadas: 3,
  reprogramadas: 1,
  realizadas: 3,
  noRealizadas: 1,
  canceladas: 1,
  distritos: ['Miraflores', 'San Isidro'],
};

describe('Visitas', () => {
  let api: jasmine.SpyObj<VisitasService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<VisitasService>('VisitasService', [
      'pagina$',
      'resumen$',
      'proximas$',
      'realizar',
      'noRealizada',
      'cancelar',
      'reprogramar',
      'registrarResultado',
    ]);
    api.pagina$.and.returnValue(of(PAGINA));
    api.resumen$.and.returnValue(of(RESUMEN));
    api.proximas$.and.returnValue(of(paginaVacia<Visita>(8)));
    api.realizar.and.resolveTo(REALIZADA_SIN_DESENLACE);
    api.noRealizada.and.resolveTo({ ...PROGRAMADA, estado: 'N' });
    api.cancelar.and.resolveTo({ ...PROGRAMADA, estado: 'C' });
    api.reprogramar.and.resolveTo({ ...PROGRAMADA, estado: 'G' });
    api.registrarResultado.and.resolveTo(REALIZADA_CON_DESENLACE);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('manda estado, distrito y búsqueda al backend', async () => {
    await montar('AGENTE', { texto: 'larco', estado: 'P', distrito: 'Miraflores', page: '2' });

    expect(api.pagina$).toHaveBeenCalledWith({
      pagina: 2,
      tamano: RESULTADOS_POR_PAGINA,
      estado: 'P',
      distrito: 'Miraflores',
      query: 'larco',
    });
  });

  /** El resumen devuelve los cubos y los distritos: no puede recibirlos. */
  it('el resumen no lleva estado ni distrito', async () => {
    await montar('AGENTE', { texto: 'larco', estado: 'P', distrito: 'Miraflores' });

    expect(api.resumen$).toHaveBeenCalledWith({ query: 'larco' });
  });

  it('los distritos del filtro salen del resumen, no de la página', async () => {
    const fixture = await montar();

    expect(acceder(fixture).opcionesDistrito().map((o) => o.valor)).toEqual([
      'Miraflores',
      'San Isidro',
    ]);
  });

  /**
   * El desenlace exige visita realizada y es irrepetible: el segundo intento
   * responde "La visita ya tiene un resultado registrado.".
   */
  it('solo ofrece registrar desenlace sobre una realizada sin resultado', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    expect(acceso.admiteResultado(REALIZADA_SIN_DESENLACE)).toBeTrue();
    expect(acceso.admiteResultado(REALIZADA_CON_DESENLACE)).toBeFalse();
    expect(acceso.admiteResultado(PROGRAMADA)).toBeFalse();
  });

  it('una programada admite las cuatro acciones de agenda', async () => {
    const fixture = await montar();

    expect(acceder(fixture).pendiente(PROGRAMADA)).toBeTrue();
    expect(acceder(fixture).pendiente(REALIZADA_SIN_DESENLACE)).toBeFalse();
  });

  it('cancelar y no realizada exigen motivo', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.abrir('cancelar', PROGRAMADA);
    expect(acceso.bloqueado()).toBeTrue();

    acceso.motivo.setValue('El cliente no asistió.');
    expect(acceso.bloqueado()).toBeFalse();

    await acceso.confirmar();
    expect(api.cancelar).toHaveBeenCalledWith(21, 'El cliente no asistió.');
  });

  /**
   * Un desenlace que implica no continuidad cierra la oportunidad: el backend
   * pide la razón tipificada, y la pantalla la exige antes de enviar.
   */
  it('el desenlace de no continuidad exige la razón', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.abrir('resultado', REALIZADA_SIN_DESENLACE);
    acceso.desenlace.patchValue({ resultado: 'N' });
    expect(acceso.exigeRazon()).toBeTrue();
    expect(acceso.bloqueado()).toBeTrue();

    acceso.desenlace.patchValue({ razonNoContinuidad: 'P' });
    expect(acceso.bloqueado()).toBeFalse();
  });

  it('un desenlace normal no pide razón', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.abrir('resultado', REALIZADA_SIN_DESENLACE);
    acceso.desenlace.patchValue({ resultado: 'I' });

    expect(acceso.exigeRazon()).toBeFalse();
    expect(acceso.bloqueado()).toBeFalse();
  });

  /** 0 es "sin indicar": el nivel de interés va de 1 a 5 y el cable distingue ausente de cero. */
  it('el nivel de interés en cero no viaja', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.abrir('resultado', REALIZADA_SIN_DESENLACE);
    acceso.desenlace.patchValue({ resultado: 'I', nivelInteres: 0, objecionPrincipal: '' });
    await acceso.confirmar();

    const enviado = api.registrarResultado.calls.mostRecent().args[1];
    expect(enviado.nivelInteres).toBeUndefined();
    expect(enviado.objecionPrincipal).toBeUndefined();
  });

  it('el broker no ve las acciones de agenda', async () => {
    const broker = await montar('BROKER');

    const contenido = texto(broker);
    expect(contenido).toContain('Solo lectura');
    expect(contenido).not.toContain('Registrar desenlace');
  });

  it('un estado inventado en la URL se ignora', () => {
    const filtros = filtrosVisitasDesdeUrl(convertToParamMap({ estado: 'ZZ', page: '0' }));

    expect(filtros).toEqual({ texto: '', estado: '', distrito: '', page: 1 });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    query: Record<string, string> = {},
  ): Promise<ComponentFixture<Visitas>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 30,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [Visitas],
      providers: [
        { provide: VisitasService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(Visitas);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoAgenda {
  opcionesDistrito(): { valor: string; etiqueta: string }[];
  admiteResultado(visita: Visita): boolean;
  pendiente(visita: Visita): boolean;
  abrir(accion: string, visita: Visita): void;
  confirmar(): Promise<void>;
  bloqueado(): boolean;
  exigeRazon(): boolean;
  motivo: { setValue(valor: string): void };
  desenlace: { patchValue(valores: Record<string, unknown>): void };
}

function acceder(fixture: ComponentFixture<Visitas>): AccesoAgenda {
  return fixture.componentInstance as unknown as AccesoAgenda;
}

function texto(fixture: ComponentFixture<Visitas>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
