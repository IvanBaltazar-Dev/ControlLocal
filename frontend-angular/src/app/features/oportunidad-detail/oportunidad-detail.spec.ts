import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { ApiError, PageResponse } from '../../core/api/api.types';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { Oportunidad, OportunidadesService } from '../../core/api/oportunidades.service';
import { Visita, VisitasService } from '../../core/api/visitas.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { OportunidadDetail } from './oportunidad-detail';

const OPORTUNIDAD: Oportunidad = {
  id: 9,
  codigoOportunidad: 'OP-260730120000',
  idCliente: 5,
  clienteNombre: 'Lucía Ramírez',
  idCaptacion: 3,
  codigoCaptacion: 'CAP-0001',
  direccionLocal: 'Av. Larco 123',
  distritoLocal: 'Miraflores',
  agenteNombre: 'Valeria Mora',
  estado: 'A',
  fechaRegistro: '2026-07-28T09:00:00',
};

function pagina<T>(items: T[]): PageResponse<T> {
  return { items, totalRecords: items.length, page: 1, pageSize: 100 };
}

const VISITAS: Visita[] = [
  {
    id: 21,
    idOportunidad: 9,
    fechaVisita: '2026-08-15',
    horaVisita: '16:00',
    estado: 'R',
    resultado: 'I',
    nivelInteres: 4,
  },
  { id: 22, idOportunidad: 9, fechaVisita: '2026-08-20', horaVisita: '10:30', estado: 'P' },
];

describe('OportunidadDetail', () => {
  let api: jasmine.SpyObj<OportunidadesService>;
  let interacciones: jasmine.SpyObj<InteraccionesService>;
  let visitas: jasmine.SpyObj<VisitasService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<OportunidadesService>('OportunidadesService', [
      'obtener',
      'noContinuidad',
    ]);
    api.obtener.and.resolveTo(OPORTUNIDAD);
    api.noContinuidad.and.resolveTo({ ...OPORTUNIDAD, estado: 'N', motivoCierre: 'Precio' });
    interacciones = jasmine.createSpyObj<InteraccionesService>('InteraccionesService', ['pagina']);
    interacciones.pagina.and.resolveTo(pagina<Interaccion>([]));
    visitas = jasmine.createSpyObj<VisitasService>('VisitasService', ['pagina']);
    visitas.pagina.and.resolveTo(pagina(VISITAS));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('pide las interacciones de la oportunidad y sus visitas', async () => {
    await montar();

    expect(interacciones.pagina).toHaveBeenCalledWith({
      contexto: 'OPORTUNIDAD',
      idOportunidad: 9,
      pagina: 1,
      tamano: 100,
    });
    expect(visitas.pagina).toHaveBeenCalledWith({ idOportunidad: 9, pagina: 1, tamano: 100 });
  });

  /** Solo la oportunidad es fatal: los dos paneles caen por separado. */
  it('un fallo de las visitas no tumba el seguimiento', async () => {
    visitas.pagina.and.rejectWith(new ApiError(500, 'Falló la agenda.'));
    const fixture = await montar();

    const contenido = texto(fixture);
    expect(contenido).toContain('Falló la agenda.');
    expect(contenido).toContain('OP-260730120000');
  });

  /**
   * `A` cubre dos etapas y el cable no las distingue: pasa a "en seguimiento"
   * cuando ya hay algo registrado.
   */
  it('la etapa avanza a seguimiento cuando hay visitas registradas', async () => {
    const conRastro = await montar();
    expect(acceder(conRastro).etapaActual()).toBe(1);

    visitas.pagina.and.resolveTo(pagina<Visita>([]));
    const sinRastro = await montar();
    expect(acceder(sinRastro).etapaActual()).toBe(0);
  });

  it('una oportunidad con solicitud está en la tercera etapa', async () => {
    api.obtener.and.resolveTo({ ...OPORTUNIDAD, estado: 'S' });
    const fixture = await montar();

    expect(acceder(fixture).etapaActual()).toBe(2);
  });

  /** El cierre favorable lo produce el contrato, no un botón de esta pantalla. */
  it('no ofrece cerrar la oportunidad como exitosa', async () => {
    const fixture = await montar();

    const contenido = texto(fixture);
    expect(contenido).not.toContain('Cerrar alquiler');
    expect(contenido).toContain('no se registra desde aquí');
  });

  it('cerrar por no continuidad exige la razón tipificada', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.abrirCierre();
    await acceso.confirmarCierre();
    expect(api.noContinuidad).not.toHaveBeenCalled();

    acceso.razon.setValue('P');
    await acceso.confirmarCierre();
    expect(api.noContinuidad).toHaveBeenCalledWith(9, 'P', undefined);
  });

  it('una oportunidad cerrada no ofrece acciones', async () => {
    api.obtener.and.resolveTo({ ...OPORTUNIDAD, estado: 'N', motivoCierre: 'Precio' });
    const fixture = await montar();

    const contenido = texto(fixture);
    expect(contenido).toContain('no continúa con la propiedad');
    expect(contenido).not.toContain('Programar visita');
  });

  it('el broker ve el seguimiento pero no opera', async () => {
    const broker = await montar('BROKER');

    const contenido = texto(broker);
    expect(contenido).toContain('Solo lectura');
    expect(contenido).not.toContain('Marcar sin continuidad');
  });

  it('programar visita lleva la oportunidad fijada', async () => {
    const fixture = await montar();

    acceder(fixture).programarVisita();

    expect(router.navigate).toHaveBeenCalledWith(['/visitas/nueva'], {
      queryParams: { oportunidad: 9 },
    });
  });

  async function montar(rol: RolSesion = 'AGENTE'): Promise<ComponentFixture<OportunidadDetail>> {
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
      imports: [OportunidadDetail],
      providers: [
        { provide: OportunidadesService, useValue: api },
        { provide: InteraccionesService, useValue: interacciones },
        { provide: VisitasService, useValue: visitas },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '9' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(OportunidadDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoSeguimiento {
  etapaActual(): number;
  abrirCierre(): void;
  confirmarCierre(): Promise<void>;
  programarVisita(): void;
  razon: { setValue(valor: string): void };
}

function acceder(fixture: ComponentFixture<OportunidadDetail>): AccesoSeguimiento {
  return fixture.componentInstance as unknown as AccesoSeguimiento;
}

function texto(fixture: ComponentFixture<OportunidadDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
