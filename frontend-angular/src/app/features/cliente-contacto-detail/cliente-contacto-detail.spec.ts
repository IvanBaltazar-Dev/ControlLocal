import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { ApiError, PageResponse } from '../../core/api/api.types';
import { Cliente, ClientesService } from '../../core/api/clientes.service';
import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { Oportunidad, OportunidadesService } from '../../core/api/oportunidades.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { ClienteContactoDetail } from './cliente-contacto-detail';

const CLIENTE: Cliente = {
  id: 5,
  tipoPersona: 'N',
  tipoDocumento: 'D',
  numeroDocumento: '45678912',
  nombre: 'Lucía Ramírez',
  telefono: '987654321',
  correo: 'lucia@correo.test',
  rubroComercial: 'Cafetería',
  estado: 'A',
  consentimientoContacto: true,
  consentimientoUsoDato: true,
};

function pagina<T>(items: T[]): PageResponse<T> {
  return { items, totalRecords: items.length, page: 1, pageSize: 100 };
}

const INTERACCIONES: Interaccion[] = [
  {
    id: 1,
    contexto: 'CLIENTE',
    idCliente: 5,
    fechaHora: '2026-07-30T15:00:00',
    canalContacto: 'L',
    resultado: 'SEGUIMIENTO',
    observaciones: 'Pide opciones en Miraflores.',
    agenteNombre: 'Valeria Mora',
  },
  {
    id: 2,
    contexto: 'CLIENTE',
    idCliente: 5,
    fechaHora: '2026-07-25T10:00:00',
    canalContacto: 'W',
    resultado: 'BUSQUEDA_LEVANTADA',
    agenteNombre: 'Valeria Mora',
  },
];

const OPORTUNIDADES: Oportunidad[] = [
  {
    id: 9,
    codigoOportunidad: 'OP-260730120000',
    idCliente: 5,
    clienteNombre: 'Lucía Ramírez',
    codigoCaptacion: 'CAP-0001',
    direccionLocal: 'Av. Larco 123',
    distritoLocal: 'Miraflores',
    agenteNombre: 'Valeria Mora',
    estado: 'A',
    fechaRegistro: '2026-07-28T09:00:00',
  },
  {
    id: 10,
    codigoOportunidad: 'OP-260701090000',
    idCliente: 5,
    estado: 'N',
    motivoCierre: 'Precio',
    agenteNombre: 'Valeria Mora',
  },
];

describe('ClienteContactoDetail', () => {
  let clientes: jasmine.SpyObj<ClientesService>;
  let interacciones: jasmine.SpyObj<InteraccionesService>;
  let oportunidades: jasmine.SpyObj<OportunidadesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    clientes = jasmine.createSpyObj<ClientesService>('ClientesService', ['obtener']);
    clientes.obtener.and.resolveTo(CLIENTE);
    interacciones = jasmine.createSpyObj<InteraccionesService>('InteraccionesService', ['pagina']);
    interacciones.pagina.and.resolveTo(pagina(INTERACCIONES));
    oportunidades = jasmine.createSpyObj<OportunidadesService>('OportunidadesService', ['pagina']);
    oportunidades.pagina.and.resolveTo(pagina(OPORTUNIDADES));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  /** La bitácora del cliente son las de contexto CLIENTE, no todas las suyas. */
  it('pide solo las interacciones de contexto CLIENTE y las oportunidades del cliente', async () => {
    await montar();

    expect(interacciones.pagina).toHaveBeenCalledWith({
      contexto: 'CLIENTE',
      idCliente: 5,
      pagina: 1,
      tamano: 100,
    });
    expect(oportunidades.pagina).toHaveBeenCalledWith({ idCliente: 5, pagina: 1, tamano: 100 });
  });

  /**
   * Solo el cliente es fatal. Si una de las dos listas cae, la otra se sigue
   * viendo: son lecturas independientes de recursos distintos.
   */
  it('un fallo de las oportunidades no tumba la bitácora', async () => {
    oportunidades.pagina.and.rejectWith(new ApiError(500, 'Falló el listado.'));
    const fixture = await montar();

    const contenido = texto(fixture);
    expect(contenido).toContain('Falló el listado.');
    expect(contenido).toContain('Pide opciones en Miraflores.');
    expect(contenido).toContain('Lucía Ramírez');
  });

  it('un fallo del cliente sí es fatal', async () => {
    clientes.obtener.and.rejectWith(new ApiError(404, 'Cliente no encontrado.'));
    const fixture = await montar();

    expect(texto(fixture)).toContain('Cliente no encontrado.');
    expect(interacciones.pagina).not.toHaveBeenCalled();
  });

  /**
   * El rol CLIENTE no guarda agente asignado: quién lo atiende se deduce del
   * último rastro. Es un dato derivado, y por eso puede decir "no asignado".
   */
  it('el agente visible sale de la última interacción', async () => {
    const fixture = await montar();
    expect(texto(fixture)).toContain('Valeria Mora');

    interacciones.pagina.and.resolveTo(pagina([]));
    oportunidades.pagina.and.resolveTo(pagina([]));
    const sinRastro = await montar();
    expect(texto(sinRastro)).toContain('No asignado');
  });

  it('cuenta como activas solo las oportunidades abiertas o con solicitud', async () => {
    const fixture = await montar();
    expect(acceder(fixture).activas()).toBe(1);
  });

  it('el broker no ve las acciones de registro', async () => {
    const agente = await montar('AGENTE');
    expect(texto(agente)).toContain('Registrar contacto');

    const broker = await montar('BROKER');
    const contenido = texto(broker);
    expect(contenido).toContain('Solo lectura');
    expect(contenido).not.toContain('Nueva oportunidad');
  });

  it('crear oportunidad lleva el cliente ya fijado', async () => {
    const fixture = await montar();

    acceder(fixture).crearOportunidad();

    expect(router.navigate).toHaveBeenCalledWith(['/oportunidades/nueva'], {
      queryParams: { cliente: 5 },
    });
  });

  it('registrar contacto abre el alta con contexto CLIENTE', async () => {
    const fixture = await montar();

    acceder(fixture).registrarInteraccion();

    expect(router.navigate).toHaveBeenCalledWith(['/interacciones/nueva'], {
      queryParams: { contexto: 'CLIENTE', cliente: 5 },
    });
  });

  /** Sin consentimiento de contacto, la pantalla lo dice antes de llamar. */
  it('avisa cuando el cliente no autorizó el contacto', async () => {
    clientes.obtener.and.resolveTo({ ...CLIENTE, consentimientoContacto: false });
    const fixture = await montar();

    expect(texto(fixture)).toContain('no autorizó el contacto comercial');
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
  ): Promise<ComponentFixture<ClienteContactoDetail>> {
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
      imports: [ClienteContactoDetail],
      providers: [
        { provide: ClientesService, useValue: clientes },
        { provide: InteraccionesService, useValue: interacciones },
        { provide: OportunidadesService, useValue: oportunidades },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(ClienteContactoDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoBitacora {
  activas(): number;
  crearOportunidad(): void;
  registrarInteraccion(): void;
}

function acceder(fixture: ComponentFixture<ClienteContactoDetail>): AccesoBitacora {
  return fixture.componentInstance as unknown as AccesoBitacora;
}

function texto(fixture: ComponentFixture<ClienteContactoDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
