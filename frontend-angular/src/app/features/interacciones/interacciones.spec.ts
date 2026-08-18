import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';

import { PageResponse } from '../../core/api/api.types';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { filtrosInteraccionesDesdeUrl, Interacciones } from './interacciones';

const ITEMS: Interaccion[] = [
  {
    id: 1,
    contexto: 'CLIENTE',
    idCliente: 5,
    fechaHora: '2026-07-30T15:00:00',
    canalContacto: 'L',
    resultado: 'SEGUIMIENTO',
    personaNombre: 'Lucía Ramírez',
    personaTipo: 'Cliente',
    agenteNombre: 'Valeria Mora',
  },
  {
    id: 2,
    contexto: 'CAPTACION',
    idCaptacion: 3,
    codigoCaptacion: 'CAP-0001',
    fechaHora: '2026-07-29T11:00:00',
    canalContacto: 'W',
    resultado: 'DOCS_SOLICITADOS',
    personaNombre: 'Jorge Salinas',
    personaTipo: 'Propietario',
    agenteNombre: 'Valeria Mora',
  },
];

const PAGINA: PageResponse<Interaccion> = {
  items: ITEMS,
  totalRecords: 2,
  page: 1,
  pageSize: 20,
};

describe('Interacciones', () => {
  let api: jasmine.SpyObj<InteraccionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<InteraccionesService>('InteraccionesService', ['pagina$']);
    api.pagina$.and.returnValue(of(PAGINA));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('manda grupo, canal, resultado y búsqueda al backend', async () => {
    await montar('AGENTE', {
      grupo: 'PROPIETARIO',
      canal: 'W',
      resultado: 'DOCS_SOLICITADOS',
      texto: 'salinas',
      page: '2',
    });

    expect(api.pagina$).toHaveBeenCalledWith({
      pagina: 2,
      tamano: RESULTADOS_POR_PAGINA,
      grupo: 'PROPIETARIO',
      canal: 'W',
      resultado: 'DOCS_SOLICITADOS',
      q: 'salinas',
    });
  });

  /**
   * `grupo` parte el universo en dos, no filtra por contexto: PROPIETARIO
   * agrupa prospección y captación, y su complemento es el lado del cliente.
   */
  it('la pestaña de propietario solo ofrece resultados de prospección y captación', async () => {
    const fixture = await montar('AGENTE', { grupo: 'PROPIETARIO' });

    const opciones = acceder(fixture).opcionesResultado().map((o) => o.valor);
    expect(opciones).toContain('DOCS_SOLICITADOS');
    expect(opciones).toContain('ACEPTA_CAPTAR');
    expect(opciones).not.toContain('VISITA_AGENDADA');
  });

  it('la pestaña de cliente ofrece los de cliente y oportunidad', async () => {
    const fixture = await montar('AGENTE', { grupo: 'CLIENTE' });

    const opciones = acceder(fixture).opcionesResultado().map((o) => o.valor);
    expect(opciones).toContain('BUSQUEDA_LEVANTADA');
    expect(opciones).toContain('VISITA_AGENDADA');
    expect(opciones).not.toContain('DOCS_SOLICITADOS');
  });

  /** `PROPUESTA_ENVIADA` y `DESCARTADO` viven en dos contextos: sin duplicar. */
  it('la pestaña "todas" une los cuatro catálogos sin repetir', async () => {
    const fixture = await montar();

    const opciones = acceder(fixture).opcionesResultado().map((o) => o.valor);
    expect(opciones.filter((o) => o === 'PROPUESTA_ENVIADA').length).toBe(1);
    expect(opciones.filter((o) => o === 'DESCARTADO').length).toBe(1);
  });

  /** Conservar el resultado al cambiar de pestaña dejaría un filtro sin resultados. */
  it('cambiar de pestaña limpia el resultado', async () => {
    const fixture = await montar('AGENTE', { grupo: 'CLIENTE', resultado: 'SEGUIMIENTO' });

    acceder(fixture).cambiarGrupo('PROPIETARIO');

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({
        queryParams: jasmine.objectContaining({ grupo: 'PROPIETARIO', resultado: null }),
      }),
    );
  });

  it('distingue visualmente el lado propietario del lado cliente', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    expect(acceso.esDePropietario(ITEMS[0])).toBeFalse();
    expect(acceso.esDePropietario(ITEMS[1])).toBeTrue();
  });

  it('solo el agente ve el alta', async () => {
    const agente = await montar('AGENTE');
    expect(texto(agente)).toContain('Nueva interacción');

    const broker = await montar('BROKER');
    expect(texto(broker)).toContain('Solo lectura');
  });

  it('un canal inventado en la URL no viaja al backend', () => {
    const filtros = filtrosInteraccionesDesdeUrl(
      convertToParamMap({ canal: 'ZZ', grupo: 'OTRO', page: 'x' }),
    );

    expect(filtros).toEqual({
      texto: '',
      grupo: 'TODAS',
      canal: '',
      resultado: '',
      page: 1,
    });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    query: Record<string, string> = {},
  ): Promise<ComponentFixture<Interacciones>> {
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
      imports: [Interacciones],
      providers: [
        { provide: InteraccionesService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(Interacciones);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoBitacora {
  opcionesResultado(): { valor: string; etiqueta: string }[];
  cambiarGrupo(grupo: string): void;
  esDePropietario(interaccion: Interaccion): boolean;
}

function acceder(fixture: ComponentFixture<Interacciones>): AccesoBitacora {
  return fixture.componentInstance as unknown as AccesoBitacora;
}

function texto(fixture: ComponentFixture<Interacciones>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
