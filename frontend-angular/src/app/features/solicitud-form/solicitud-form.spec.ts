import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { Oportunidad, OportunidadesService } from '../../core/api/oportunidades.service';
import { Solicitud, SolicitudesService } from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { SolicitudForm } from './solicitud-form';

const OPORTUNIDAD: Oportunidad = {
  id: 7,
  codigoOportunidad: 'OP-0001',
  clienteNombre: 'Mariana Delgado',
  codigoCaptacion: 'CAP-0001',
  direccionLocal: 'Av. Larco 812',
  estado: 'A',
};

const CREADA: Solicitud = { id: 4, codigoSolicitud: 'SOL-260802120000', estado: 'G' };

describe('SolicitudForm', () => {
  let api: jasmine.SpyObj<SolicitudesService>;
  let oportunidades: jasmine.SpyObj<OportunidadesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['registrar']);
    api.registrar.and.resolveTo(CREADA);
    oportunidades = jasmine.createSpyObj<OportunidadesService>('OportunidadesService', [
      'pagina',
      'obtener',
    ]);
    oportunidades.pagina.and.resolveTo({
      items: [OPORTUNIDAD],
      totalRecords: 1,
      page: 1,
      pageSize: 20,
    });
    oportunidades.obtener.and.resolveTo(OPORTUNIDAD);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  /**
   * El Blazor descargaba 100 oportunidades y filtraba en memoria. Aquí se
   * buscan 20 candidatos en el servidor, y solo ABIERTAS: una que ya tiene
   * solicitud está en `S` y el alta fallaría.
   */
  it('busca candidatos en el servidor y solo oportunidades abiertas', async () => {
    await montar();

    expect(oportunidades.pagina).toHaveBeenCalledWith({
      pagina: 1,
      tamano: 20,
      estado: 'A',
      query: undefined,
    });
  });

  it('con la oportunidad en la URL no busca: la fija', async () => {
    await montar('AGENTE', { oportunidad: '7' });

    expect(oportunidades.obtener).toHaveBeenCalledWith(7);
    expect(oportunidades.pagina).not.toHaveBeenCalled();
  });

  it('no envía sin oportunidad ni condiciones', async () => {
    const fixture = await montar();

    await acceder(fixture).guardar();

    expect(api.registrar).not.toHaveBeenCalled();
  });

  /** El código NO se manda: lo genera el backend como `SOL-yyMMddHHmmss`. */
  it('registra sin código y lleva al expediente documental', async () => {
    const fixture = await montar('AGENTE', { oportunidad: '7' });
    const pantalla = acceder(fixture);

    pantalla.formulario.patchValue({ montoPropuesto: 9000, plazoMeses: 24, moneda: 'USD' });
    await pantalla.guardar();

    const enviado = api.registrar.calls.mostRecent().args[0] as Record<string, unknown>;
    expect(enviado['codigoSolicitud']).toBeUndefined();
    expect(enviado['idOportunidad']).toBe(7);
    expect(enviado['moneda']).toBe('USD');
    expect(router.navigate).toHaveBeenCalledWith([
      '/solicitudes',
      'SOL-260802120000',
      'documentos',
    ]);
  });

  it('el broker no registra', async () => {
    const fixture = await montar('BROKER', { oportunidad: '7' });
    const pantalla = acceder(fixture);

    pantalla.formulario.patchValue({ montoPropuesto: 9000, plazoMeses: 24 });
    await pantalla.guardar();

    expect(api.registrar).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('Esta vista es de consulta');
  });

  it('el error del backend no pierde lo escrito', async () => {
    api.registrar.and.rejectWith(new Error('La captacion debe estar ACTIVA.'));
    const fixture = await montar('AGENTE', { oportunidad: '7' });
    const pantalla = acceder(fixture);

    pantalla.formulario.patchValue({ montoPropuesto: 9000, plazoMeses: 24 });
    await pantalla.guardar();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('La captacion debe estar ACTIVA.');
    expect(pantalla.formulario.getRawValue().montoPropuesto).toBe(9000);
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    query: Record<string, string> = {},
  ): Promise<ComponentFixture<SolicitudForm>> {
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
      imports: [SolicitudForm],
      providers: [
        { provide: SolicitudesService, useValue: api },
        { provide: OportunidadesService, useValue: oportunidades },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(SolicitudForm);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

interface AccesoFormulario {
  guardar(): Promise<void>;
  formulario: {
    patchValue(valores: Record<string, unknown>): void;
    getRawValue(): { montoPropuesto: number | null };
  };
}

function acceder(fixture: ComponentFixture<SolicitudForm>): AccesoFormulario {
  return fixture.componentInstance as unknown as AccesoFormulario;
}

function texto(fixture: ComponentFixture<SolicitudForm>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
