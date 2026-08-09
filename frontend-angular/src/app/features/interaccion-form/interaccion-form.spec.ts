import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { PageResponse } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { Cliente, ClientesService } from '../../core/api/clientes.service';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { Oportunidad, OportunidadesService } from '../../core/api/oportunidades.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { InteraccionForm } from './interaccion-form';

function pagina<T>(items: T[]): PageResponse<T> {
  return { items, totalRecords: items.length, page: 1, pageSize: 20 };
}

const CLIENTE: Cliente = { id: 5, nombre: 'Lucía Ramírez', rubroComercial: 'Cafetería' };
const OPORTUNIDAD: Oportunidad = {
  id: 9,
  codigoOportunidad: 'OP-260730120000',
  clienteNombre: 'Lucía Ramírez',
  direccionLocal: 'Av. Larco 123',
};
const CAPTACION: Captacion = {
  id: 3,
  codigoCaptacion: 'CAP-0001',
  direccionLocal: 'Av. Larco 123',
  distritoLocal: 'Miraflores',
};
const PROSPECCION: Prospeccion = {
  id: 7,
  codigoProspeccion: 'PRO-0001',
  direccion: 'Jr. Unión 500',
  distrito: 'Lima',
};

describe('InteraccionForm', () => {
  let api: jasmine.SpyObj<InteraccionesService>;
  let oportunidades: jasmine.SpyObj<OportunidadesService>;
  let prospecciones: jasmine.SpyObj<ProspeccionesService>;
  let captaciones: jasmine.SpyObj<CaptacionesService>;
  let clientes: jasmine.SpyObj<ClientesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<InteraccionesService>('InteraccionesService', ['registrar']);
    api.registrar.and.resolveTo({ id: 42 } as Interaccion);
    oportunidades = jasmine.createSpyObj<OportunidadesService>('OportunidadesService', [
      'pagina',
      'obtener',
    ]);
    oportunidades.pagina.and.resolveTo(pagina([OPORTUNIDAD]));
    oportunidades.obtener.and.resolveTo(OPORTUNIDAD);
    prospecciones = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', [
      'pagina',
      'obtener',
    ]);
    prospecciones.pagina.and.resolveTo(pagina([PROSPECCION]));
    prospecciones.obtener.and.resolveTo(PROSPECCION);
    captaciones = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', [
      'pagina',
      'obtener',
    ]);
    captaciones.pagina.and.resolveTo(pagina([CAPTACION]));
    captaciones.obtener.and.resolveTo(CAPTACION);
    clientes = jasmine.createSpyObj<ClientesService>('ClientesService', ['pagina', 'obtener']);
    clientes.pagina.and.resolveTo(pagina([CLIENTE]));
    clientes.obtener.and.resolveTo(CLIENTE);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  /**
   * La allow-list del backend por contexto se replica aquí: enviar un código
   * de otro contexto responde 400 con "Resultado no valido para {contexto}".
   */
  it('ofrece solo los resultados del contexto elegido', async () => {
    const fixture = await montar('AGENTE', { contexto: 'CLIENTE', cliente: '5' });

    const opciones = acceder(fixture).opcionesResultado().map((o) => o.valor);
    expect(opciones).toContain('BUSQUEDA_LEVANTADA');
    expect(opciones).not.toContain('VISITA_AGENDADA');
    expect(opciones).not.toContain('DOCS_SOLICITADOS');
  });

  it('cambiar de contexto limpia el resultado y rehace la búsqueda', async () => {
    const fixture = await montar();
    const acceso = acceder(fixture);

    acceso.formulario.patchValue({ resultado: 'INTERESADO', contexto: 'CAPTACION' });
    await acceso.cambiarContexto();

    expect(acceso.formulario.getRawValue().resultado).toBe('');
    expect(captaciones.pagina).toHaveBeenCalled();
  });

  /** El CHECK de la base exige exactamente una FK: solo viaja la de su contexto. */
  it('solo envía el id de la entidad de su contexto', async () => {
    const fixture = await montar('AGENTE', { contexto: 'CLIENTE', cliente: '5' });
    const acceso = acceder(fixture);

    acceso.formulario.patchValue({ canalContacto: 'L', resultado: 'SEGUIMIENTO' });
    await acceso.guardar();

    const enviado = api.registrar.calls.mostRecent().args[0];
    expect(enviado).toEqual(
      jasmine.objectContaining({ contexto: 'CLIENTE', idCliente: 5, canalContacto: 'L' }),
    );
    expect(enviado.idOportunidad).toBeUndefined();
    expect(enviado.idCaptacion).toBeUndefined();
    expect(enviado.idProspeccion).toBeUndefined();
  });

  it('canal y resultado son obligatorios antes de enviar', async () => {
    const fixture = await montar('AGENTE', { contexto: 'CLIENTE', cliente: '5' });
    const acceso = acceder(fixture);

    await acceso.guardar();

    expect(api.registrar).not.toHaveBeenCalled();
  });

  /** Llegar desde un registro concreto fija la conversación: no se cambia a media. */
  it('con entidad fijada el contexto queda deshabilitado pero se envía igual', async () => {
    const fixture = await montar('AGENTE', { contexto: 'PROSPECCION', prospeccion: '7' });
    const acceso = acceder(fixture);

    expect(acceso.formulario.controls.contexto.disabled).toBeTrue();
    acceso.formulario.patchValue({ canalContacto: 'P', resultado: 'CONTACTADO' });
    await acceso.guardar();

    const enviado = api.registrar.calls.mostRecent().args[0];
    expect(enviado.contexto).toBe('PROSPECCION');
    expect(enviado.idProspeccion).toBe(7);
  });

  it('sin entidad en la URL busca candidatos en el servidor', async () => {
    await montar();

    expect(oportunidades.pagina).toHaveBeenCalledWith({
      pagina: 1,
      tamano: 20,
      estado: 'A',
      query: undefined,
    });
  });

  it('el broker no puede guardar', async () => {
    const fixture = await montar('BROKER', { contexto: 'CLIENTE', cliente: '5' });
    const acceso = acceder(fixture);

    acceso.formulario.patchValue({ canalContacto: 'L', resultado: 'SEGUIMIENTO' });
    await acceso.guardar();

    expect(api.registrar).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('Solo el');
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    query: Record<string, string> = {},
  ): Promise<ComponentFixture<InteraccionForm>> {
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
      imports: [InteraccionForm],
      providers: [
        { provide: InteraccionesService, useValue: api },
        { provide: OportunidadesService, useValue: oportunidades },
        { provide: ProspeccionesService, useValue: prospecciones },
        { provide: CaptacionesService, useValue: captaciones },
        { provide: ClientesService, useValue: clientes },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(InteraccionForm);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoAlta {
  opcionesResultado(): { valor: string; etiqueta: string }[];
  cambiarContexto(): Promise<void>;
  guardar(): Promise<void>;
  formulario: {
    patchValue(valores: Record<string, unknown>): void;
    getRawValue(): { resultado: string };
    controls: { contexto: { disabled: boolean } };
  };
}

function acceder(fixture: ComponentFixture<InteraccionForm>): AccesoAlta {
  return fixture.componentInstance as unknown as AccesoAlta;
}

function texto(fixture: ComponentFixture<InteraccionForm>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
