import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  CandidatoAgente,
  Captacion,
  CaptacionesService,
} from '../../core/api/captaciones.service';
import { Local, LocalesService } from '../../core/api/locales.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { CaptacionReview } from './captacion-review';

const CAPTACION: Captacion = {
  id: 9, codigoCaptacion: 'CAP-0009', estado: 'P', idLocal: 7,
  direccionLocal: 'Av. Larco 700', distritoLocal: 'Miraflores',
  propietarioNombre: 'Ana Torres', idAgente: 30, agenteNombre: 'Valentina Mora',
  comisionPactada: 100, fechaCaptacion: '2026-08-01',
  capacidades: { puedeEditar: false, puedeRevisar: true, puedeCerrar: false, puedeReasignar: true },
};

/** Los destinos ya depurados por el Core: aquí no se filtra nada. */
const CANDIDATOS: CandidatoAgente[] = [
  { idAgente: 31, nombre: 'Javier Ruiz', codigoAgente: 'AGE-031', zonaAsignada: 'Lima Norte' },
];
const LOCAL: Local = { id: 7, codigoLocal: 'LOC-0007', direccion: 'Av. Larco 700', monedaReferencial: 'PEN' };
const PROSPECCION: Prospeccion = { id: 4, codigoProspeccion: 'PRO-0004', estado: 'T' };

interface AccesoReview {
  observacion: { setValue(valor: string): void };
  agenteNuevo: { setValue(valor: number): void };
  motivoReasignacion: { setValue(valor: string): void };
  abrirDecision(decision: 'A' | 'O' | 'R'): void;
  confirmarDecision(): Promise<void>;
  abrirReasignacion(): void;
  confirmarReasignacion(): Promise<void>;
}

describe('CaptacionReview', () => {
  let api: jasmine.SpyObj<CaptacionesService>;
  let locales: jasmine.SpyObj<LocalesService>;
  let prospecciones: jasmine.SpyObj<ProspeccionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<CaptacionesService>('CaptacionesService', [
      'obtenerPorCodigo', 'decidir', 'reasignar', 'candidatosReasignacion',
    ]);
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION });
    api.decidir.and.resolveTo({ ...CAPTACION, estado: 'A' });
    api.reasignar.and.resolveTo({ ...CAPTACION, idAgente: 31, agenteNombre: 'Javier Ruiz' });
    api.candidatosReasignacion.and.resolveTo(pagina(CANDIDATOS));
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', ['obtener']);
    locales.obtener.and.resolveTo(LOCAL);
    prospecciones = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', ['pagina']);
    prospecciones.pagina.and.resolveTo(pagina([PROSPECCION]));
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('aprueba con nota opcional y actualiza el estado', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReview;
    acceso.abrirDecision('A');
    await acceso.confirmarDecision();
    fixture.detectChanges();
    expect(api.decidir).toHaveBeenCalledOnceWith(9, 'A', null);
    expect(texto(fixture)).toContain('Captación aprobada. Ya está activa.');
    expect(texto(fixture)).not.toContain('Aprobar captación');
  });

  it('observar exige motivo y envía el texto limpio', async () => {
    const acceso = (await montar()).componentInstance as unknown as AccesoReview;
    acceso.abrirDecision('O');
    await acceso.confirmarDecision();
    expect(api.decidir).not.toHaveBeenCalled();
    acceso.observacion.setValue('  Precisar la vigencia  ');
    await acceso.confirmarDecision();
    expect(api.decidir).toHaveBeenCalledOnceWith(9, 'O', 'Precisar la vigencia');
  });

  it('pide los destinos al Core y reasigna declarando el agente observado', async () => {
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReview;
    acceso.abrirReasignacion();
    await fixture.whenStable();
    fixture.detectChanges();

    // Los destinos los decide el Core, para ESTE encargo. Esta pantalla ya no
    // pide `GET /agentes` ni se queda con «todos menos el actual».
    expect(api.candidatosReasignacion).toHaveBeenCalledOnceWith(9);
    expect(texto(fixture)).toContain('Javier Ruiz');

    acceso.agenteNuevo.setValue(31);
    acceso.motivoReasignacion.setValue('Balance de cartera');
    await acceso.confirmarReasignacion();
    // Y el cuarto argumento es el agente que la pantalla estaba MOSTRANDO
    // (D-P0-9): sin él, la reasignación no sabría de dónde parte.
    expect(api.reasignar).toHaveBeenCalledOnceWith(9, 31, 'Balance de cartera', 30);
  });

  /**
   * El 409 no es «no se pudo»: es «el estado que veías ya no es». No se
   * reintenta — se recarga el expediente para que la siguiente decisión parta
   * de quien lo lleva ahora.
   */
  it('ante un 409 muestra el mensaje del Core y recarga el expediente', async () => {
    api.reasignar.and.rejectWith(new ApiError(409,
      'El agente de este encargo cambio desde que se miro: hoy lo lleva 31.'));
    const fixture = await montar();
    const acceso = fixture.componentInstance as unknown as AccesoReview;
    acceso.abrirReasignacion();
    await fixture.whenStable();
    acceso.agenteNuevo.setValue(31);
    acceso.motivoReasignacion.setValue('Balance de cartera');
    await acceso.confirmarReasignacion();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('hoy lo lleva 31');
    expect(api.obtenerPorCodigo).toHaveBeenCalledTimes(2);
  });

  /**
   * La autoridad la dice el Core, no esta pantalla. Sin `puedeReasignar` no se
   * ofrece la acción: el defecto seguro es no prometer una escritura que el
   * backend va a rechazar.
   */
  it('no ofrece reasignar cuando el Core dice que este actor no puede', async () => {
    api.obtenerPorCodigo.and.resolveTo({
      ...CAPTACION,
      capacidades: {
        puedeEditar: false, puedeRevisar: true, puedeCerrar: false, puedeReasignar: false,
      },
    });
    const fixture = await montar();
    expect(texto(fixture)).toContain('Observar y devolver');
    expect(texto(fixture)).not.toContain('Reasignar agente');
  });

  it('una captación ya activa se muestra sin botones de decisión', async () => {
    api.obtenerPorCodigo.and.resolveTo({ ...CAPTACION, estado: 'A' });
    const fixture = await montar();
    expect(texto(fixture)).toContain('La revisión ya fue registrada');
    expect(texto(fixture)).not.toContain('Observar y devolver');
  });

  it('no ofrece ninguna exportación PDF', async () => {
    expect(texto(await montar())).not.toContain('PDF');
  });

  async function montar(): Promise<ComponentFixture<CaptacionReview>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CaptacionReview],
      providers: [
        { provide: CaptacionesService, useValue: api },
        { provide: LocalesService, useValue: locales },
        { provide: ProspeccionesService, useValue: prospecciones },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ codigo: 'CAP-0009' }) } } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(CaptacionReview);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function pagina<T>(items: T[]): PageResponse<T> {
  return { items, totalRecords: items.length, page: 1, pageSize: 100 };
}
function texto(fixture: ComponentFixture<CaptacionReview>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
