import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError, PageResponse } from '../../core/api/api.types';
import { Cliente, ClientesService, ResumenClientes } from '../../core/api/clientes.service';
import { MODULOS, puedeEntrar } from '../../core/auth/acceso';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { Clientes, filtrosClientesDesdeUrl } from './clientes';

const FILA: Cliente = {
  id: 5,
  tipoPersona: 'J',
  tipoDocumento: 'R',
  numeroDocumento: '20123456789',
  nombre: 'Inversiones Centro SAC',
  telefono: '987654321',
  correo: 'contacto@centro.pe',
  rubroComercial: 'Retail',
  estado: 'A',
  consentimientoContacto: true,
  consentimientoUsoDato: false,
};

const RESUMEN: ResumenClientes = {
  total: 12,
  activos: 10,
  inactivos: 2,
  contactoAutorizado: 8,
  usoDatoAutorizado: 6,
  rubros: ['Cafeteria', 'Retail'],
};

describe('Clientes', () => {
  let service: jasmine.SpyObj<ClientesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    service = jasmine.createSpyObj<ClientesService>('ClientesService', [
      'pagina$',
      'resumen$',
      'desactivar',
      'reactivar',
    ]);
    service.pagina$.and.returnValue(of(pagina([FILA], 12)));
    service.resumen$.and.returnValue(of(RESUMEN));
    service.desactivar.and.resolveTo(undefined);
    service.reactivar.and.resolveTo(FILA);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('manda los cuatro filtros y la página al backend, no filtra en memoria', async () => {
    await montar('AGENTE', {
      texto: 'retail',
      tipoPersona: 'j',
      rubro: 'Retail',
      estado: 'a',
      page: '2',
    });

    expect(service.pagina$).toHaveBeenCalledWith({
      pagina: 2,
      tamano: 10,
      texto: 'retail',
      tipoPersona: 'J',
      rubro: 'Retail',
      estado: 'A',
    });
  });

  /** El KPI cuenta los tres cubos; si le pasara el estado, contaría uno solo. */
  it('el resumen viaja con los mismos filtros salvo el estado', async () => {
    await montar('AGENTE', { texto: 'retail', tipoPersona: 'J', rubro: 'Retail', estado: 'I' });

    expect(service.resumen$).toHaveBeenCalledWith({
      texto: 'retail',
      tipoPersona: 'J',
      rubro: 'Retail',
    });
  });

  it('los KPI y el selector de rubros salen del resumen del backend', async () => {
    const fixture = await montar();

    const contenido = texto(fixture);
    expect(contenido).toContain('12');
    expect(contenido).toContain('10');
    expect(contenido).toContain('8');
    // Los rubros del selector son los del alcance completo, no los de la página.
    const opciones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('select option'),
    ).map((o) => o.textContent?.trim());
    expect(opciones).toContain('Cafeteria');
    expect(opciones).toContain('Retail');
  });

  it('un código inventado en la URL no se manda al backend', async () => {
    await montar('AGENTE', { tipoPersona: 'X', estado: 'Z' });

    expect(service.pagina$).toHaveBeenCalledWith({
      pagina: 1,
      tamano: 10,
      texto: undefined,
      tipoPersona: undefined,
      rubro: undefined,
      estado: undefined,
    });
  });

  it('la ficha la abre cualquier rol; editar y dar de baja son del AGENTE', async () => {
    const agente = await montar('AGENTE');
    expect(filaBotones(agente)).toEqual(['Ficha', 'Editar', 'Dar de baja']);

    const broker = await montar('BROKER');
    expect(filaBotones(broker)).toEqual(['Ficha']);
    expect(texto(broker)).toContain('Solo lectura');
  });

  it('la baja pide confirmación y solo entonces llama al backend', async () => {
    const fixture = await montar('AGENTE');

    expect(service.desactivar).not.toHaveBeenCalled();
    pulsar(fixture, 'Dar de baja');
    expect(service.desactivar).not.toHaveBeenCalled();

    const acceso = fixture.componentInstance as unknown as AccesoPantalla;
    await acceso.confirmarCambioEstado();
    expect(service.desactivar).toHaveBeenCalledWith(5);
  });

  /** Reactivar no tiene endpoint propio: es el PUT con estado A. */
  it('un cliente inactivo se reactiva, no se vuelve a dar de baja', async () => {
    service.pagina$.and.returnValue(of(pagina([{ ...FILA, estado: 'I' }], 1)));
    const fixture = await montar('AGENTE');

    expect(filaBotones(fixture)).toEqual(['Ficha', 'Editar', 'Reactivar']);
    const acceso = fixture.componentInstance as unknown as AccesoPantalla;
    acceso.pedirCambioEstado({ ...FILA, estado: 'I' });
    await acceso.confirmarCambioEstado();
    expect(service.reactivar).toHaveBeenCalled();
    expect(service.desactivar).not.toHaveBeenCalled();
  });

  it('un error del backend deja la pantalla en estado recuperable, no en blanco', async () => {
    service.pagina$.and.returnValue(throwError(() => new ApiError(500, 'Se cayó el servidor.')));
    const fixture = await montar();

    expect(texto(fixture)).toContain('Se cayó el servidor.');
  });

  it('los dos consentimientos se muestran por separado', async () => {
    const fixture = await montar();

    const contenido = texto(fixture);
    expect(contenido).toContain('Contacto: sí');
    expect(contenido).toContain('Uso de datos: no');
  });

  it('la ruta está abierta a los tres roles (catálogo compartido)', () => {
    const modulo = MODULOS.find((m) => m.ruta === 'clientes')!;
    expect(modulo).toBeDefined();
    for (const rol of ['AGENTE', 'BROKER', 'TENANT_ADMIN'] as RolSesion[]) {
      expect(puedeEntrar(modulo, rol)).toBeTrue();
    }
  });

  describe('filtrosClientesDesdeUrl', () => {
    it('normaliza códigos, recorta el texto y descarta páginas imposibles', () => {
      const f = filtrosClientesDesdeUrl(
        convertToParamMap({ texto: '  retail ', tipoPersona: 'j', estado: 'a', page: '-3' }),
      );
      expect(f).toEqual({ texto: 'retail', tipoPersona: 'J', rubro: '', estado: 'A', page: 1 });
    });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    queryParams: Record<string, string> = {},
  ): Promise<ComponentFixture<Clientes>> {
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
      imports: [Clientes],
      providers: [
        { provide: ClientesService, useValue: service },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(queryParams)) } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(Clientes);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoPantalla {
  pedirCambioEstado(cliente: Cliente): void;
  confirmarCambioEstado(): Promise<void>;
}

function pagina<T>(items: T[], total: number, pageSize = 10): PageResponse<T> {
  return { items, totalRecords: total, page: 1, pageSize };
}

function texto(fixture: ComponentFixture<Clientes>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function filaBotones(fixture: ComponentFixture<Clientes>): string[] {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody button')).map(
    (b) => b.textContent?.trim() ?? '',
  );
}

function pulsar(fixture: ComponentFixture<Clientes>, etiqueta: string): void {
  const boton = Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('tbody button'),
  ).find((b) => b.textContent?.trim() === etiqueta) as HTMLButtonElement | undefined;
  boton?.click();
  fixture.detectChanges();
}
