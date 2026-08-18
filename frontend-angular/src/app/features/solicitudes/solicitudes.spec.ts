import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  ResumenSolicitudes,
  Solicitud,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { filtrosSolicitudesDesdeUrl, Solicitudes } from './solicitudes';

const ITEMS: Solicitud[] = [
  {
    id: 4,
    codigoSolicitud: 'SOL-260715103000',
    codigoOportunidad: 'OP-0001',
    idCliente: 5,
    clienteNombre: 'Mariana Delgado',
    idCaptacion: 3,
    codigoCaptacion: 'CAP-0001',
    direccionLocal: 'Av. Larco 812',
    distritoLocal: 'Miraflores',
    agenteNombre: 'Valentina Mora',
    estado: 'O',
    montoPropuesto: 9000,
    moneda: 'PEN',
    plazoMeses: 24,
    fechaRegistro: '2026-07-15',
    documentosEntregados: 4,
    documentosRequeridos: 6,
  },
];

const PAGINA: PageResponse<Solicitud> = { items: ITEMS, totalRecords: 1, page: 1, pageSize: 10 };

const RESUMEN: ResumenSolicitudes = {
  total: 7,
  registradas: 1,
  enRevision: 2,
  observadas: 1,
  aprobadas: 2,
  rechazadas: 0,
  desistidas: 0,
  cerradas: 1,
  pendientes: 3,
  distritos: ['Miraflores', 'San Isidro'],
  agentes: [{ id: 28, nombre: 'Valentina Mora' }],
};

describe('Solicitudes', () => {
  let api: jasmine.SpyObj<SolicitudesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['pagina$', 'resumen$']);
    api.pagina$.and.returnValue(of(PAGINA));
    api.resumen$.and.returnValue(of(RESUMEN));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('manda los cuatro filtros de la URL al backend', async () => {
    await montar('BROKER', {
      texto: 'larco',
      estado: 'A',
      distrito: 'Miraflores',
      idAgente: '28',
      page: '2',
    });

    expect(api.pagina$).toHaveBeenCalledWith({
      pagina: 2,
      tamano: RESULTADOS_POR_PAGINA,
      estado: 'A',
      distrito: 'Miraflores',
      idAgente: 28,
      texto: 'larco',
    });
  });

  /** El resumen cuenta los cubos y devuelve las listas: acotarlo las vaciaría. */
  it('el resumen solo comparte el texto con la tabla', async () => {
    await montar('BROKER', { texto: 'larco', estado: 'A', distrito: 'Miraflores', idAgente: '28' });

    expect(api.resumen$).toHaveBeenCalledWith({ texto: 'larco' });
  });

  it('un estado inventado en la URL no viaja al backend', async () => {
    await montar('AGENTE', { estado: 'ZZZ' });

    expect(api.pagina$).toHaveBeenCalledWith({
      pagina: 1,
      tamano: RESULTADOS_POR_PAGINA,
      estado: undefined,
      distrito: undefined,
      idAgente: undefined,
      texto: undefined,
    });
  });

  it('el KPI es un atajo del filtro y volver a pulsarlo lo quita', async () => {
    const fixture = await montar('AGENTE', { estado: 'A' });

    acceder(fixture).seleccionarEstado('A');

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({ queryParams: jasmine.objectContaining({ estado: null }) }),
    );
  });

  it('solo el agente ve el alta', async () => {
    expect(texto(await montar('AGENTE'))).toContain('Nueva solicitud');

    const broker = texto(await montar('BROKER'));
    expect(broker).not.toContain('Nueva solicitud');
    expect(broker).toContain('Consulta');
  });

  /** Filtrar por agente solo tiene sentido para quien ve a varios. */
  it('el filtro por agente es solo para quien supervisa', async () => {
    const agente = await montar('AGENTE');
    expect(opciones(agente)).not.toContain('Todos los agentes');

    const broker = await montar('BROKER');
    expect(opciones(broker)).toContain('Todos los agentes');
  });

  /** Subsanar es del agente y solo sobre una observada. */
  it('solo ofrece subsanar al agente y sobre una observada', async () => {
    expect(texto(await montar('AGENTE'))).toContain('Subsanar');
    expect(texto(await montar('BROKER'))).not.toContain('Subsanar');

    api.pagina$.and.returnValue(of({ ...PAGINA, items: [{ ...ITEMS[0], estado: 'A' }] }));
    expect(texto(await montar('AGENTE'))).not.toContain('Subsanar');
  });

  it('el avance del checklist sale del contador del backend, no de contar filas', async () => {
    const fixture = await montar('AGENTE');

    expect(acceder(fixture).avanceDocumentos(ITEMS[0])).toBe(67);
    expect(acceder(fixture).checklist(ITEMS[0])).toBe('4/6');
  });

  it('un error del backend deja la bandeja recuperable', async () => {
    api.pagina$.and.returnValue(throwError(() => new ApiError(500, 'Se cayó el listado.')));

    expect(texto(await montar('AGENTE'))).toContain('Se cayó el listado.');
  });

  it('la lectura de la URL normaliza página, estado y agente', () => {
    const filtros = filtrosSolicitudesDesdeUrl(
      convertToParamMap({ page: '-3', estado: 'a', texto: '  larco  ', idAgente: '0' }),
    );

    expect(filtros).toEqual({
      texto: 'larco',
      estado: 'A',
      distrito: '',
      idAgente: '',
      page: 1,
    });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    query: Record<string, string> = {},
  ): Promise<ComponentFixture<Solicitudes>> {
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
      imports: [Solicitudes],
      providers: [
        { provide: SolicitudesService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(Solicitudes);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoBandeja {
  seleccionarEstado(estado: string): void;
  avanceDocumentos(solicitud: Solicitud): number;
  checklist(solicitud: Solicitud): string;
}

function acceder(fixture: ComponentFixture<Solicitudes>): AccesoBandeja {
  return fixture.componentInstance as unknown as AccesoBandeja;
}

function texto(fixture: ComponentFixture<Solicitudes>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

/** Los marcadores de los selectores de filtro presentes en la pantalla. */
function opciones(fixture: ComponentFixture<Solicitudes>): string[] {
  return Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('option'),
  ).map((o) => o.textContent?.trim() ?? '');
}
