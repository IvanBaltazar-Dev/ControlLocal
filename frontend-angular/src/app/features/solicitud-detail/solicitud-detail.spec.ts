import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { Contrato, ContratosService } from '../../core/api/contratos.service';
import { Evaluacion, Solicitud, SolicitudesService } from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { SolicitudDetail } from './solicitud-detail';

const APROBADA: Solicitud = {
  id: 4,
  codigoSolicitud: 'SOL-260715103000',
  codigoOportunidad: 'OP-0001',
  idOportunidad: 7,
  idCaptacion: 3,
  codigoCaptacion: 'CAP-0001',
  idCliente: 5,
  clienteNombre: 'Mariana Delgado',
  agenteNombre: 'Valentina Mora',
  direccionLocal: 'Av. Larco 812',
  distritoLocal: 'Miraflores',
  estado: 'A',
  montoPropuesto: 9000,
  moneda: 'PEN',
  plazoMeses: 24,
  mesesGarantia: 2,
  mesesAdelanto: 1,
  documentosEntregados: 6,
  documentosRequeridos: 6,
};

/** `comisionPactada = 100` es UN MES de alquiler, no S/ 100. */
const CAPTACION: Captacion = { id: 3, codigoCaptacion: 'CAP-0001', comisionPactada: 100 };

const CONTRATO: Contrato = {
  id: 2,
  estadoContrato: 'V',
  rentaMensual: 9000,
  moneda: 'PEN',
  comisionGenerada: 9000,
  monedaComision: 'PEN',
  comisionEstado: 'P',
};

describe('SolicitudDetail', () => {
  let api: jasmine.SpyObj<SolicitudesService>;
  let contratos: jasmine.SpyObj<ContratosService>;
  let captaciones: jasmine.SpyObj<CaptacionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', [
      'porCodigo',
      'documentos',
      'evaluaciones',
    ]);
    api.porCodigo.and.resolveTo(APROBADA);
    api.documentos.and.resolveTo([]);
    api.evaluaciones.and.resolveTo([] as Evaluacion[]);

    contratos = jasmine.createSpyObj<ContratosService>('ContratosService', [
      'porOportunidad',
      'registrar',
    ]);
    // 404 = todavía no hay contrato. Es el caso normal, no un error.
    contratos.porOportunidad.and.rejectWith(new ApiError(404, 'Contrato'));
    contratos.registrar.and.resolveTo(CONTRATO);

    captaciones = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', ['obtener']);
    captaciones.obtener.and.resolveTo(CAPTACION);

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('el 404 del contrato no se muestra como error: es "todavía no"', async () => {
    const fixture = await montar();

    expect(acceder(fixture).errorContrato()).toBeNull();
    expect(acceder(fixture).puedeCerrar()).toBeTrue();
  });

  it('solo el agente cierra, y solo una aprobada sin contrato', async () => {
    expect(acceder(await montar('BROKER')).puedeCerrar()).toBeFalse();

    api.porCodigo.and.resolveTo({ ...APROBADA, estado: 'E' });
    expect(acceder(await montar()).puedeCerrar()).toBeFalse();

    api.porCodigo.and.resolveTo(APROBADA);
    contratos.porOportunidad.and.resolveTo(CONTRATO);
    expect(acceder(await montar()).puedeCerrar()).toBeFalse();
  });

  /** Estimación de pantalla con la fórmula del backend: renta × pactada / 100. */
  it('estima la comisión desde la captación', async () => {
    const fixture = await montar();

    expect(acceder(fixture).comisionEstimada()).toEqual({ valor: 9000, moneda: 'PEN' });
  });

  it('sin captación no inventa comisión y avisa aparte', async () => {
    captaciones.obtener.and.rejectWith(new ApiError(403, 'Fuera de tu alcance.'));
    const fixture = await montar();

    expect(acceder(fixture).comisionEstimada()).toBeNull();
    expect(acceder(fixture).errorCaptacion()).toContain('Fuera de tu alcance.');
    // El bloque cae solo: el expediente se sigue viendo.
    expect(texto(fixture)).toContain('Condiciones del alquiler');
  });

  /** Garantía y adelanto son del propietario; la comisión, de la inmobiliaria. */
  it('desglosa el desembolso inicial concepto por concepto', async () => {
    const fixture = await montar();
    const d = acceder(fixture).desembolso();

    expect(d?.garantia).toEqual({ valor: 18000, moneda: 'PEN' });
    expect(d?.adelanto).toEqual({ valor: 9000, moneda: 'PEN' });
    expect(d?.comision).toEqual({ valor: 9000, moneda: 'PEN' });
    expect(d?.total).toEqual({ valor: 36000, moneda: 'PEN' });
  });

  it('una fecha de cierre futura bloquea la confirmación', async () => {
    const fixture = await montar();
    const pantalla = acceder(fixture);

    pantalla.formularioCierre.patchValue({ fechaCierre: '2099-01-01' });
    expect(pantalla.cierreInvalido()).toBeTrue();
  });

  it('el cierre manda la formalización y recarga la solicitud ya cerrada', async () => {
    api.porCodigo.and.returnValues(
      Promise.resolve(APROBADA),
      Promise.resolve({ ...APROBADA, estado: 'C' }),
    );
    const fixture = await montar();
    const pantalla = acceder(fixture);

    pantalla.formularioCierre.patchValue({ estadoContrato: 'D', incidencias: '  ' });
    await pantalla.cerrarAlquiler();

    expect(contratos.registrar).toHaveBeenCalledWith(
      jasmine.objectContaining({ idSolicitud: 4, estadoContrato: 'D', incidencias: undefined }),
    );
    expect(pantalla.contrato()).toEqual(CONTRATO);
    expect(pantalla.solicitud()?.estado).toBe('C');
  });

  /** El fallo de un bloque complementario no puede tumbar el expediente. */
  it('un fallo del historial no esconde la ficha', async () => {
    api.evaluaciones.and.rejectWith(new ApiError(500, 'Historial caído.'));
    const fixture = await montar();

    expect(acceder(fixture).errorEvaluaciones()).toContain('Historial caído.');
    expect(texto(fixture)).toContain('Condiciones del alquiler');
  });

  async function montar(rol: RolSesion = 'AGENTE'): Promise<ComponentFixture<SolicitudDetail>> {
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
      imports: [SolicitudDetail],
      providers: [
        { provide: SolicitudesService, useValue: api },
        { provide: ContratosService, useValue: contratos },
        { provide: CaptacionesService, useValue: captaciones },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ codigo: 'SOL-260715103000' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(SolicitudDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoExpediente {
  puedeCerrar(): boolean;
  errorContrato(): string | null;
  errorCaptacion(): string | null;
  errorEvaluaciones(): string | null;
  comisionEstimada(): { valor: number; moneda: string } | null;
  desembolso(): {
    garantia: { valor: number; moneda: string };
    adelanto: { valor: number; moneda: string };
    comision: { valor: number; moneda: string };
    total: { valor: number; moneda: string };
  } | null;
  cierreInvalido(): boolean;
  cerrarAlquiler(): Promise<void>;
  contrato(): Contrato | null;
  solicitud(): Solicitud | null;
  formularioCierre: {
    patchValue(valores: Partial<{
      fechaCierre: string;
      estadoContrato: string;
      incidencias: string;
    }>): void;
  };
}

function acceder(fixture: ComponentFixture<SolicitudDetail>): AccesoExpediente {
  return fixture.componentInstance as unknown as AccesoExpediente;
}

function texto(fixture: ComponentFixture<SolicitudDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
