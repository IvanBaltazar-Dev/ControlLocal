import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { EvaluacionesService, tipoDeResultado } from '../../core/api/evaluaciones.service';
import {
  DocumentoSolicitud,
  Evaluacion,
  Solicitud,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { EvaluacionSolicitud } from './evaluacion-solicitud';

const SOLICITUD: Solicitud = {
  id: 4,
  codigoSolicitud: 'SOL-260715103000',
  codigoOportunidad: 'OP-0001',
  clienteNombre: 'Mariana Delgado',
  agenteNombre: 'Valentina Mora',
  direccionLocal: 'Av. Larco 812',
  distritoLocal: 'Miraflores',
  estado: 'E',
  montoPropuesto: 9000,
  moneda: 'PEN',
  plazoMeses: 24,
  documentosEntregados: 4,
  documentosRequeridos: 6,
};

const REGISTRADO: DocumentoSolicitud = {
  id: 11,
  tipoDocumento: 'E',
  tipoNombre: 'Sustento economico',
  nombreArchivo: 'sustento.pdf',
  rutaArchivo: 'SOL-1/abc-sustento.pdf',
  estado: 'R',
};

const OBSERVADO: DocumentoSolicitud = {
  id: 12,
  tipoDocumento: 'G',
  tipoNombre: 'Documento de garantia',
  nombreArchivo: 'garantia.pdf',
  rutaArchivo: 'SOL-1/def-garantia.pdf',
  estado: 'O',
  observaciones: 'Está vencido.',
};

describe('EvaluacionSolicitud', () => {
  let api: jasmine.SpyObj<SolicitudesService>;
  let evaluaciones: jasmine.SpyObj<EvaluacionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', [
      'porCodigo',
      'documentos',
      'evaluaciones',
      'revisarDocumento',
      'conformarDocumentos',
    ]);
    api.porCodigo.and.resolveTo(SOLICITUD);
    api.documentos.and.resolveTo([REGISTRADO]);
    api.evaluaciones.and.resolveTo([] as Evaluacion[]);
    api.revisarDocumento.and.resolveTo(REGISTRADO);
    api.conformarDocumentos.and.resolveTo([REGISTRADO]);

    evaluaciones = jasmine.createSpyObj<EvaluacionesService>('EvaluacionesService', ['registrar']);
    evaluaciones.registrar.and.resolveTo({ id: 1 } as Evaluacion);

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  /** El tipo lo deriva el resultado: el broker no lo elige en pantalla. */
  it('no ofrece elegir el tipo de evaluación', async () => {
    expect(texto(await montar())).not.toContain('Tipo de evaluación');
    expect(tipoDeResultado('O')).toBe('O');
    expect(tipoDeResultado('A')).toBe('F');
    expect(tipoDeResultado('R')).toBe('F');
  });

  it('observar y rechazar exigen motivo; aprobar no', async () => {
    const fixture = await montar();
    const pantalla = acceder(fixture);

    pantalla.pedirDecision('O');
    expect(pantalla.faltaMotivo()).toBeTrue();

    pantalla.pedirDecision('A');
    expect(pantalla.faltaMotivo()).toBeFalse();
  });

  /**
   * Regla de la casa, no del backend: aprobar con un hallazgo sin resolver es
   * contradecir la propia revisión.
   */
  it('no se aprueba con documentos observados sin resolver', async () => {
    api.documentos.and.resolveTo([OBSERVADO]);
    const fixture = await montar();
    const pantalla = acceder(fixture);

    expect(pantalla.bloqueaAprobar()).toBeTrue();
    pantalla.pedirDecision('A');
    expect(pantalla.confirmacionBloqueada()).toBeTrue();
  });

  it('"validar todos" solo aparece si hay cargados sin revisar', async () => {
    expect(texto(await montar())).toContain('Validar todos');

    api.documentos.and.resolveTo([{ ...REGISTRADO, estado: 'V' }]);
    expect(texto(await montar())).not.toContain('Validar todos');
  });

  it('observar un documento manda el resultado y la nota', async () => {
    const fixture = await montar();
    const pantalla = acceder(fixture);

    pantalla.pedirObservacionDocumento(REGISTRADO);
    pantalla.notaDocumento.setValue('Ilegible.');
    await pantalla.confirmarObservacionDocumento();

    expect(api.revisarDocumento).toHaveBeenCalledWith(4, 11, 'O', 'Ilegible.');
  });

  it('la decisión mueve la solicitud sin un segundo POST de estado', async () => {
    const fixture = await montar();
    const pantalla = acceder(fixture);

    pantalla.pedirDecision('R');
    pantalla.observaciones.setValue('No acredita ingresos.');
    await pantalla.confirmarDecision();

    expect(evaluaciones.registrar).toHaveBeenCalledWith(4, 'R', 'No acredita ingresos.');
    expect(router.navigate).toHaveBeenCalledWith(['/solicitudes/revisar']);
  });

  it('el agente entra en solo lectura', async () => {
    const fixture = await montar('AGENTE');

    expect(acceder(fixture).puedeDecidir()).toBeFalse();
    expect(texto(fixture)).toContain('Esta vista es de consulta');
  });

  /** El historial es complementario: si falla, la decisión sigue siendo posible. */
  it('un fallo del historial no impide evaluar', async () => {
    api.evaluaciones.and.rejectWith(new Error('sin historial'));
    const fixture = await montar();

    expect(acceder(fixture).puedeDecidir()).toBeTrue();
    expect(texto(fixture)).toContain('Todavía no hay evaluaciones registradas');
  });

  async function montar(rol: RolSesion = 'BROKER'): Promise<ComponentFixture<EvaluacionSolicitud>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 20,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [EvaluacionSolicitud],
      providers: [
        { provide: SolicitudesService, useValue: api },
        { provide: EvaluacionesService, useValue: evaluaciones },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ codigo: 'SOL-260715103000' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(EvaluacionSolicitud);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoEvaluacion {
  puedeDecidir(): boolean;
  bloqueaAprobar(): boolean;
  faltaMotivo(): boolean;
  confirmacionBloqueada(): boolean;
  pedirDecision(decision: 'A' | 'O' | 'R'): void;
  confirmarDecision(): Promise<void>;
  pedirObservacionDocumento(documento: DocumentoSolicitud): void;
  confirmarObservacionDocumento(): Promise<void>;
  observaciones: { setValue(valor: string): void };
  notaDocumento: { setValue(valor: string): void };
}

function acceder(fixture: ComponentFixture<EvaluacionSolicitud>): AccesoEvaluacion {
  return fixture.componentInstance as unknown as AccesoEvaluacion;
}

function texto(fixture: ComponentFixture<EvaluacionSolicitud>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
