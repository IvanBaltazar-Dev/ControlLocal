import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';

import { AgentesService, FichaAgente } from '../../core/api/agentes.service';
import { ApiError } from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { AgenteDetail } from './agente-detail';

const FICHA: FichaAgente = {
  agente: {
    id: 30,
    codigoAgente: 'AGE-001',
    nombre: 'Valentina Mora',
    tipoDocumento: 'D',
    numeroDocumento: '45678912',
    telefono: '999888777',
    correo: 'vmora@corredora.test',
    usuario: 'vmora',
    zona: 'Miraflores',
    fechaIngreso: '2024-02-01',
    estadoAdministrativo: 'A',
    estadoOperativo: 'D',
  },
  supervision: {
    idBroker: 20,
    brokerNombre: 'Ricardo Salas',
    codigoBroker: 'BRK-001',
    fechaAsignacion: '2024-02-01',
    motivo: 'Asignacion inicial por registro de agente.',
  },
  captaciones: [{ estado: 'A', descripcion: 'Activa', total: 3 }],
  oportunidades: [{ estado: 'A', descripcion: 'Abierta', total: 5 }],
  solicitudes: [{ estado: 'C', descripcion: 'Cerrada', total: 2 }],
  cierres: 2,
  comisiones: {
    generada: [{ moneda: 'PEN', monto: 10000 }],
    cobrada: [{ moneda: 'PEN', monto: 6000 }],
    pendienteCobro: [{ moneda: 'PEN', monto: 4000 }],
    asignadaAgente: [{ moneda: 'PEN', monto: 3000 }],
    pagadaAgente: [{ moneda: 'PEN', monto: 1000 }],
    pendientePagoAgente: [{ moneda: 'PEN', monto: 2000 }],
  },
  ultimosCierres: [],
};

function sesion(rol: RolSesion): Sesion {
  return {
    token: 't',
    expiraEnSegundos: 3600,
    rol,
    idUsuario: 1,
    idDominio: 20,
    nombre: 'Quien sea',
    usuario: 'quien',
    expiraEn: '2026-12-31T00:00:00',
  };
}

describe('AgenteDetail', () => {
  let api: jasmine.SpyObj<AgentesService>;
  let fixture: ComponentFixture<AgenteDetail>;

  function montar(rol: RolSesion = 'BROKER'): AgenteDetail {
    TestBed.configureTestingModule({
      imports: [AgenteDetail],
      providers: [
        { provide: AgentesService, useValue: api },
        { provide: AuthService, useValue: { sesion: signal(sesion(rol)) } },
        {
          // `createUrlTree` y `serializeUrl` los pide `routerLink`, que la ficha
          // usa para enlazar al gobierno de accesos. Sin ellos el doble espía
          // rompe el render entero, no solo el enlace.
          provide: Router,
          useValue: Object.assign(
            jasmine.createSpyObj<Router>('Router', ['navigate', 'createUrlTree', 'serializeUrl']),
            { events: EMPTY },
          ),
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '30' }) } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AgenteDetail);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<AgentesService>('AgentesService', ['ficha$']);
    api.ficha$.and.returnValue(of(FICHA));
  });

  it('arma la ficha con UNA sola llamada, no combinando bandejas', () => {
    montar();

    expect(api.ficha$).toHaveBeenCalledOnceWith(30);
  });

  /**
   * Lo importante de esta pantalla: generado, cobrado y pagado son tres cosas
   * distintas y se muestran separadas. Un solo total escondería justo lo que
   * hay que ver.
   */
  it('separa las seis magnitudes del dinero en vez de sumarlas', () => {
    const componente = montar();
    const bloques = componente['dinero']();

    expect(bloques.length).toBe(6);
    expect(bloques.map((b) => b.importes[0].monto)).toEqual([
      10000, 6000, 4000, 3000, 1000, 2000,
    ]);
  });

  it('suma los conteos de cada maquina para la cabecera de su bloque', () => {
    const componente = montar();

    expect(componente['totalCaptaciones']()).toBe(3);
    expect(componente['totalOportunidades']()).toBe(5);
    expect(componente['totalSolicitudes']()).toBe(2);
  });

  /**
   * Un 403 aqui no es un fallo del sistema: es que el agente no es del equipo
   * de quien mira. Se explica como alcance y no como error.
   */
  it('un 403 se explica como alcance, no como error', () => {
    api.ficha$.and.returnValue(throwError(() => new ApiError(403, 'No tienes permisos')));

    const componente = montar();

    expect(componente['fueraDeAlcance']()).toBeTrue();
    expect(componente['error']()).toBeNull();
  });

  it('un fallo real si se muestra como error recuperable', () => {
    api.ficha$.and.returnValue(throwError(() => new ApiError(500, 'Se cayo')));

    const componente = montar();

    expect(componente['fueraDeAlcance']()).toBeFalse();
    expect(componente['error']()).toBe('Se cayo');
  });

  it('al agente no se le ofrece editar los datos de otro agente', () => {
    expect(montar('AGENTE')['puedeEditar']()).toBeFalse();
  });

  /**
   * D-S0-17 fila 18: editar la ficha administrativa de un agente pasó a ser
   * gobierno del tenant. El broker supervisa a su equipo, pero no administra
   * sus cuentas — ofrecerle el botón sería prometerle un 403.
   */
  it('el broker ya no edita agentes: administrar cuentas es gobierno', () => {
    expect(montar('BROKER')['puedeEditar']()).toBeFalse();
  });

  it('el administrador de la corredora si edita: es su padron', () => {
    expect(montar('TENANT_ADMIN')['puedeEditar']()).toBeTrue();
  });
});
