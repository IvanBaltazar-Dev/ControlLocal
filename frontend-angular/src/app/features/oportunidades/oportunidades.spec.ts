import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  Oportunidad,
  OportunidadesService,
  ResumenOportunidades,
} from '../../core/api/oportunidades.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { filtrosOportunidadesDesdeUrl, Oportunidades } from './oportunidades';

const ITEMS: Oportunidad[] = [
  {
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
  },
];

const PAGINA: PageResponse<Oportunidad> = {
  items: ITEMS,
  totalRecords: 1,
  page: 1,
  pageSize: 10,
};

const RESUMEN: ResumenOportunidades = {
  total: 12,
  abiertas: 5,
  conSolicitud: 3,
  noContinuan: 2,
  exitosas: 1,
  noFavorables: 1,
};

describe('Oportunidades', () => {
  let api: jasmine.SpyObj<OportunidadesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<OportunidadesService>('OportunidadesService', [
      'pagina$',
      'resumen$',
    ]);
    api.pagina$.and.returnValue(of(PAGINA));
    api.resumen$.and.returnValue(of(RESUMEN));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('manda los filtros de la URL al backend', async () => {
    await montar('AGENTE', { texto: 'larco', estado: 'A', page: '2' });

    expect(api.pagina$).toHaveBeenCalledWith({
      pagina: 2,
      tamano: 10,
      estado: 'A',
      query: 'larco',
    });
  });

  /** El resumen cuenta los cubos: mandarle `estado` los reduciría a uno. */
  it('el resumen no lleva estado', async () => {
    await montar('AGENTE', { texto: 'larco', estado: 'A' });

    expect(api.resumen$).toHaveBeenCalledWith({ query: 'larco' });
  });

  it('un estado inventado en la URL no viaja al backend', async () => {
    await montar('AGENTE', { estado: 'ZZZ' });

    expect(api.pagina$).toHaveBeenCalledWith({
      pagina: 1,
      tamano: 10,
      estado: undefined,
      query: undefined,
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
    const agente = await montar('AGENTE');
    expect(texto(agente)).toContain('Nueva oportunidad');

    const broker = await montar('BROKER');
    const contenido = texto(broker);
    expect(contenido).not.toContain('Nueva oportunidad');
    expect(contenido).toContain('Solo lectura');
  });

  /**
   * El cierre favorable lo produce la cascada del contrato (F4). El endpoint
   * existe y responde 400 siempre: ofrecerlo sería enseñar un error.
   */
  it('no ofrece cerrar una oportunidad como exitosa', async () => {
    const fixture = await montar('AGENTE');

    expect(texto(fixture)).not.toContain('Cerrar exitosa');
  });

  it('un error del backend deja la bandeja recuperable', async () => {
    api.pagina$.and.returnValue(throwError(() => new ApiError(500, 'Se cayó el listado.')));
    const fixture = await montar('AGENTE');

    expect(texto(fixture)).toContain('Se cayó el listado.');
  });

  it('la lectura de la URL normaliza página y estado', () => {
    const filtros = filtrosOportunidadesDesdeUrl(
      convertToParamMap({ page: '-3', estado: 'f', texto: '  larco  ' }),
    );

    expect(filtros).toEqual({ texto: 'larco', estado: 'F', page: 1 });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    query: Record<string, string> = {},
  ): Promise<ComponentFixture<Oportunidades>> {
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
      imports: [Oportunidades],
      providers: [
        { provide: OportunidadesService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap(query)) },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(Oportunidades);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoBandeja {
  seleccionarEstado(estado: string): void;
}

function acceder(fixture: ComponentFixture<Oportunidades>): AccesoBandeja {
  return fixture.componentInstance as unknown as AccesoBandeja;
}

function texto(fixture: ComponentFixture<Oportunidades>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
