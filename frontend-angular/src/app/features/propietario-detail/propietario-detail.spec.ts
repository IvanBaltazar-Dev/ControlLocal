import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';

import { ApiError } from '../../core/api/api.types';
import {
  FichaComercialService,
  FichaPropietario,
  SeccionFicha,
} from '../../core/api/ficha-comercial.service';
import { PropietariosService } from '../../core/api/propietarios.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { ConstanciaAutorizacion } from '../../core/autorizacion';
import { PropietarioDetail } from './propietario-detail';

/**
 * Suite focalizada en **D-27 — la constancia de autorización en la ficha del
 * propietario**, que es el fleco que cerraba el Bloque 0.
 *
 * Lo que fija: que la constancia se muestre con lo acordado (estado, fecha y
 * hora, quién la registró), que el **canal no aparezca** y que un fallo del
 * endpoint no arrastre a la ficha comercial.
 */
function pendiente(section: string): SeccionFicha {
  return { section, totalRecords: -1, page: 0, pageSize: 8, items: [] };
}

const FICHA: FichaPropietario = {
  propietario: {
    id: 12,
    tipoPersona: 'N',
    tipoDocumento: 'D',
    numeroDocumento: '45781234',
    nombre: 'Ana Ruiz Vega',
    telefono: '987654321',
    correo: 'ana@correo.test',
    estado: 'A',
    consentimientoUsoDato: true,
    cantidadLocales: 0,
  },
  sections: {
    locales: { section: 'locales', totalRecords: 0, page: 1, pageSize: 8, items: [] },
    prospecciones: pendiente('prospecciones'),
    captaciones: pendiente('captaciones'),
    oportunidades: pendiente('oportunidades'),
    solicitudes: pendiente('solicitudes'),
    cierres: pendiente('cierres'),
    agentes: pendiente('agentes'),
  },
};

const AUTORIZACION: ConstanciaAutorizacion = {
  estado: 'VIGENTE',
  registradaEn: '2026-08-05T10:30:00',
  registradaPor: 'Valeria Mora',
  versionAviso: '1.0',
  versionVigente: '1.0',
};

describe('PropietarioDetail — constancia de autorización (D-27)', () => {
  let api: jasmine.SpyObj<FichaComercialService>;
  let propietarios: jasmine.SpyObj<PropietariosService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<FichaComercialService>('FichaComercialService', [
      'fichaPropietario$',
      'seccionPropietario$',
    ]);
    api.fichaPropietario$.and.returnValue(of(FICHA));
    api.seccionPropietario$.and.returnValue(of(pendiente('cierres')));
    propietarios = jasmine.createSpyObj<PropietariosService>('PropietariosService', [
      'autorizacion',
    ]);
    propietarios.autorizacion.and.resolveTo(AUTORIZACION);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('muestra la autorización con fecha y con quién la registró', async () => {
    const fixture = await montar();
    const contenido = texto(fixture);

    expect(propietarios.autorizacion).toHaveBeenCalledWith(12);
    expect(contenido).toContain('Autorización de datos');
    expect(contenido).toContain('Autorización registrada');
    expect(contenido).toContain('Valeria Mora');
    expect(contenido).toContain('05 ago');
  });

  it('NO muestra el canal: es siempre el mismo y no informa de nada', async () => {
    const fixture = await montar();

    expect(texto(fixture)).not.toContain('Canal');
  });

  it('calla la versión del aviso cuando es la vigente', async () => {
    const fixture = await montar();

    expect(texto(fixture)).not.toContain('Aviso citado');
  });

  it('una revocación se distingue de "sin registro"', async () => {
    propietarios.autorizacion.and.resolveTo({
      ...AUTORIZACION,
      estado: 'REVOCADA',
    });
    const fixture = await montar();
    const contenido = texto(fixture);

    expect(contenido).toContain('Autorización revocada');
    expect(contenido).toContain('El titular retiró su autorización');
    // Sigue habiendo fecha y actor: es lo que la distingue de no tener nada.
    expect(contenido).toContain('Valeria Mora');
  });

  it('una persona anterior a D-27 dice SIN registro, no "no autorizó"', async () => {
    propietarios.autorizacion.and.resolveTo({ estado: 'SIN_REGISTRO', versionVigente: '1.0' });
    const fixture = await montar();

    expect(texto(fixture)).toContain('Sin registro de autorización');
  });

  it('si la autorización falla, la ficha comercial se sigue viendo', async () => {
    propietarios.autorizacion.and.rejectWith(new ApiError(500, 'Servicio no disponible.'));
    const fixture = await montar();
    const contenido = texto(fixture);

    expect(contenido).toContain('Ana Ruiz Vega');
    expect(contenido).toContain('Servicio no disponible.');
    // No se inventa un "sin autorización" que no consta.
    expect(contenido).not.toContain('Sin registro de autorización');
  });

  async function montar(rol: RolSesion = 'AGENTE'): Promise<ComponentFixture<PropietarioDetail>> {
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
      imports: [PropietarioDetail],
      providers: [
        { provide: FichaComercialService, useValue: api },
        { provide: PropietariosService, useValue: propietarios },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '12' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(PropietarioDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<PropietarioDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
