import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import {
  Contrato,
  ContratosService,
  DatosMovimientoComision,
  huellaMovimiento,
  ResumenCierres,
} from '../../core/api/contratos.service';
import { ApiError } from '../../core/api/api.types';
import { ComandoIdempotente } from '../../core/comando-idempotente';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { Comisiones } from './comisiones';

const CONTRATO: Contrato = {
  id: 5,
  codigoSolicitud: 'SOL-260715103000',
  clienteNombre: 'Mariana Delgado',
  direccionLocal: 'Av. Larco 812',
  agenteNombre: 'Valentina Mora',
  comisionGenerada: 9000,
  monedaComision: 'PEN',
  comisionEstado: 'P',
  estadoContrato: 'V',
  idComision: 11,
  montoAgente: 3000,
  montoEmpresa: 6000,
  fechaCierre: '2026-07-20',
};

const RESUMEN: ResumenCierres = {
  cierres: 1,
  comisionesGeneradas: [{ moneda: 'PEN', monto: 9000 }],
  montosCobrados: [],
  saldosPendientes: [{ moneda: 'PEN', monto: 9000 }],
  montosPagadosAgente: [],
  saldosPendientesAgente: [{ moneda: 'PEN', monto: 3000 }],
  porLiquidar: 1,
  sinLiquidacion: 0,
  distritosDisponibles: ['Miraflores'],
  agentesDisponibles: [{ id: 28, nombre: 'Valentina Mora' }],
};

function sesion(rol: RolSesion): Sesion {
  return {
    token: 't',
    expiraEnSegundos: 3600,
    rol,
    idUsuario: 1,
    idDominio: 20,
    nombre: 'Ricardo Salas',
    usuario: 'rsalas',
    expiraEn: '2026-12-31T00:00:00',
  };
}

describe('Comisiones', () => {
  let api: jasmine.SpyObj<ContratosService>;
  let fixture: ComponentFixture<Comisiones>;

  async function montar(rol: RolSesion = 'BROKER'): Promise<Comisiones> {
    await TestBed.configureTestingModule({
      imports: [Comisiones],
      providers: [
        { provide: ContratosService, useValue: api },
        { provide: AuthService, useValue: { sesion: signal(sesion(rol)) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Comisiones);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = jasmine.createSpyObj<ContratosService>('ContratosService', [
      'pagina',
      'resumen$',
      'asignarComision',
      'registrarCobro',
      'registrarMovimiento',
      'nuevoComandoMovimiento',
    ]);
    api.pagina.and.resolveTo({ items: [CONTRATO], totalRecords: 1, page: 1, pageSize: 10 });
    api.resumen$.and.returnValue(of(RESUMEN));
    api.asignarComision.and.resolveTo(CONTRATO);
    api.registrarCobro.and.resolveTo(CONTRATO);
    api.registrarMovimiento.and.resolveTo(CONTRATO);
    // El componente ya no llama al endpoint directamente: pasa por el comando
    // idempotente, que es quien pone la clave. Se usa el real para que las
    // pruebas ejerciten esa ruta y no una simulacion.
    api.nuevoComandoMovimiento.and.callFake((id: number) =>
      new ComandoIdempotente<DatosMovimientoComision, Contrato>(
        (datos, clave) => api.registrarMovimiento(id, datos, clave),
        huellaMovimiento,
      ),
    );
  });

  /** El total se calcula en la base sobre el MISMO conjunto que la tabla. */
  it('pide el resumen con los mismos filtros que la lista', async () => {
    const pantalla = await montar();
    pantalla['texto'].set('larco');
    pantalla['distrito'].set('Miraflores');
    await pantalla['cargar']();

    expect(api.resumen$).toHaveBeenCalledWith({
      texto: 'larco',
      distrito: 'Miraflores',
      idAgente: undefined,
    });
  });

  it('ordena por fecha de cierre, no por id', async () => {
    await montar();

    expect(api.pagina).toHaveBeenCalledWith(jasmine.objectContaining({ orden: 'cierre' }));
  });

  /**
   * Las tres operaciones son de BROKER **sin ADMIN**: ofrecérselas al
   * administrador sería prometerle un 403.
   */
  it('solo el BROKER ve las acciones de liquidacion', async () => {
    const broker = await montar('BROKER');
    expect(broker['puedeLiquidar']()).toBeTrue();
    expect(broker['liquidable'](CONTRATO)).toBeTrue();

    TestBed.resetTestingModule();
    const admin = await montar('TENANT_ADMIN');
    expect(admin['puedeLiquidar']()).toBeFalse();
    expect(admin['liquidable'](CONTRATO)).toBeFalse();
  });

  /** `montoAgente`/`montoEmpresa` ni le llegan al agente: no se pintan vacías. */
  it('el reparto interno no se muestra al agente', async () => {
    const agente = await montar('AGENTE');
    expect(agente['veReparto']()).toBeFalse();

    TestBed.resetTestingModule();
    const broker = await montar('BROKER');
    expect(broker['veReparto']()).toBeTrue();
  });

  it('una comision anulada ya no se liquida', async () => {
    const pantalla = await montar();

    expect(pantalla['liquidable']({ ...CONTRATO, comisionEstado: 'A' })).toBeFalse();
    expect(pantalla['liquidable']({ ...CONTRATO, idComision: undefined })).toBeFalse();
  });

  /** Solo viaja la parte del agente: la de la empresa la calcula el backend. */
  it('al asignar manda solo el monto del agente', async () => {
    const pantalla = await montar();
    pantalla['abrirAsignacion'](CONTRATO);
    pantalla['montoAgente'].set('4200');

    await pantalla['guardarAsignacion']();

    expect(api.asignarComision).toHaveBeenCalledOnceWith(5, 4200);
    expect(pantalla['asignando']()).toBeNull();
  });

  it('un monto no numerico no llega a viajar', async () => {
    const pantalla = await montar();
    pantalla['abrirAsignacion'](CONTRATO);
    pantalla['montoAgente'].set('mucho');

    await pantalla['guardarAsignacion']();

    expect(api.asignarComision).not.toHaveBeenCalled();
    expect(pantalla['errorDialogo']()).toBeTruthy();
  });

  /**
   * La moneda del movimiento tiene que coincidir con la de la liquidación; se
   * toma del contrato en vez de pedirla y arriesgar un 400 evitable.
   */
  it('el movimiento hereda la moneda de la liquidacion', async () => {
    const pantalla = await montar();
    pantalla['abrirMovimiento'](CONTRATO);
    pantalla['montoMovimiento'].set('1500');

    await pantalla['guardarMovimiento']();

    expect(api.registrarMovimiento).toHaveBeenCalledOnceWith(
      5,
      jasmine.objectContaining({ tipo: 'C', monto: 1500, moneda: 'PEN' }),
      jasmine.any(String),
    );
  });

  /**
   * Doble clic real: dos pulsaciones antes de que responda la primera. El
   * comando devuelve la misma promesa, asi que solo sale una peticion. La UI
   * ademas deshabilita el boton con `guardando()`, pero eso es comodidad: la
   * garantia final es el indice unico del backend.
   */
  it('el doble submit no produce dos comandos independientes', async () => {
    let resolver: (contrato: Contrato) => void = () => undefined;
    api.registrarMovimiento.and.returnValue(new Promise<Contrato>((ok) => (resolver = ok)));
    const pantalla = await montar();
    pantalla['abrirMovimiento'](CONTRATO);
    pantalla['montoMovimiento'].set('1500');

    const primera = pantalla['guardarMovimiento']();
    const segunda = pantalla['guardarMovimiento']();
    resolver(CONTRATO);
    await Promise.all([primera, segunda]);

    expect(api.registrarMovimiento).toHaveBeenCalledTimes(1);
  });

  /** Un 409 es un conflicto de idempotencia, no un fallo generico. */
  it('el 409 por clave reutilizada se presenta como conflicto', async () => {
    api.registrarMovimiento.and.rejectWith(
      new ApiError(409, 'La clave de idempotencia ya se uso para otro movimiento de comision.'),
    );
    const pantalla = await montar();
    pantalla['abrirMovimiento'](CONTRATO);
    pantalla['montoMovimiento'].set('1500');

    await pantalla['guardarMovimiento']();

    expect(pantalla['errorDialogo']()).toContain('ya se registró con otros datos');
    expect(pantalla['moviendo']()).not.toBeNull();
  });

  it('el ajuste ya no se ofrece como tipo de movimiento', async () => {
    const pantalla = await montar();

    expect(pantalla['tiposMovimiento'].map((t) => t.valor)).toEqual(['C', 'P', 'R']);
  });

  it('un movimiento de monto cero no viaja', async () => {
    const pantalla = await montar();
    pantalla['abrirMovimiento'](CONTRATO);
    pantalla['montoMovimiento'].set('0');

    await pantalla['guardarMovimiento']();

    expect(api.registrarMovimiento).not.toHaveBeenCalled();
    expect(pantalla['errorDialogo']()).toBeTruthy();
  });

  /** Tras liquidar cambian los saldos: recargar lista y resumen, no solo la fila. */
  it('tras una operacion recarga tambien los totales', async () => {
    const pantalla = await montar();
    api.pagina.calls.reset();
    api.resumen$.calls.reset();

    pantalla['abrirCobro'](CONTRATO);
    await pantalla['guardarCobro']();

    expect(api.registrarCobro).toHaveBeenCalledOnceWith(5, jasmine.objectContaining({ estado: 'C' }));
    expect(api.pagina).toHaveBeenCalledTimes(1);
    expect(api.resumen$).toHaveBeenCalledTimes(1);
  });

  it('un fallo del backend se muestra en el dialogo y no lo cierra', async () => {
    api.asignarComision.and.rejectWith(new Error('sin permiso'));
    const pantalla = await montar();
    pantalla['abrirAsignacion'](CONTRATO);
    pantalla['montoAgente'].set('100');

    await pantalla['guardarAsignacion']();

    expect(pantalla['asignando']()).not.toBeNull();
    expect(pantalla['errorDialogo']()).toBeTruthy();
  });

  it('describe el estado de comision y distingue el contrato sin liquidacion', async () => {
    const pantalla = await montar();

    expect(pantalla['estadoComision'](CONTRATO)).toBe('Pendiente');
    expect(pantalla['estadoComision']({ ...CONTRATO, comisionEstado: undefined })).toBe(
      'Sin liquidación',
    );
  });
});
