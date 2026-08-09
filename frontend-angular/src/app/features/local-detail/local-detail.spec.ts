import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ApiError, PageResponse } from '../../core/api/api.types';
import {
  Local,
  LocalesService,
  PrecioLocal,
  Publicacion,
} from '../../core/api/locales.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { LocalDetail, nivelProspeccion } from './local-detail';

const LOCAL: Local = {
  id: 77,
  codigoLocal: 'LC-0001',
  direccion: 'Av. Larco 123',
  distrito: 'Miraflores',
  metraje: 85,
  precioReferencial: 2500,
  monedaReferencial: 'USD',
  rubroPermitido: 'Restaurante',
  idPropietario: 42,
  propietarioNombre: 'Ana Torres',
  estado: 'D',
  tipoInmueble: 'L',
  uso: 'C',
  fechaRegistro: '2026-07-01T10:00:00',
};

const PROSPECCION: Prospeccion = {
  id: 5,
  codigoProspeccion: 'PRO-0002',
  localId: 77,
  estado: 'S',
  resultadoPropuesta: 'P',
  fechaContacto: '2026-07-05',
  fechaReunion: '2026-07-10',
  fechaPropuesta: '2026-07-15',
  captacionCodigo: undefined,
};

const PRECIO: PrecioLocal = {
  id: 1,
  idLocal: 77,
  hito: 'U',
  moneda: 'PEN',
  monto: 9000,
  fecha: '2026-07-12',
};

const PUBLICACION: Publicacion = {
  id: 3,
  canal: 'URBANIA',
  tituloAnuncio: 'Local en Larco',
  rentaPublicada: 9000,
  moneda: 'PEN',
  estado: 'P',
  fechaPublicacion: '2026-07-16T09:00:00',
  codigoOrigen: 'URBANIA-77',
};

interface AccesoLocalDetail {
  cambiarEstado(publicacion: Publicacion, estado: string): Promise<void>;
  publicacionGuardada(): Promise<void>;
}

describe('LocalDetail', () => {
  let locales: jasmine.SpyObj<LocalesService>;
  let prospecciones: jasmine.SpyObj<ProspeccionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    locales = jasmine.createSpyObj<LocalesService>('LocalesService', [
      'obtener$',
      'precios$',
      'publicaciones$',
      'publicaciones',
      'cambiarEstadoPublicacion',
    ]);
    locales.obtener$.and.returnValue(of(LOCAL));
    locales.precios$.and.returnValue(of([PRECIO]));
    locales.publicaciones$.and.returnValue(of([PUBLICACION]));
    locales.publicaciones.and.resolveTo([PUBLICACION]);
    locales.cambiarEstadoPublicacion.and.resolveTo({ ...PUBLICACION, estado: 'S' });

    prospecciones = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', [
      'porLocal$',
    ]);
    prospecciones.porLocal$.and.returnValue(of(pagina([PROSPECCION])));

    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('pide la prospección filtrada por local, sin descargar la bandeja', async () => {
    await montar();

    expect(prospecciones.porLocal$).toHaveBeenCalledOnceWith(77);
  });

  it('muestra la ficha con el histórico y la publicación', async () => {
    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('Av. Larco 123');
    expect(html).toContain('Ana Torres');
    expect(html).toContain('Autorizado'); // hito U del precio
    expect(html).toContain('Urbania'); // canal de la publicación
    expect(html).toContain('Publicado'); // estado P de la publicación
  });

  it('da la propuesta por entregada mirando la fecha, no el estado E', async () => {
    // La v1 nunca emite `E`: deja `S` y marca `fechaPropuesta`.
    const fixture = await montar();

    expect(texto(fixture)).toContain('Entregada');
  });

  it('sigue dibujando la ficha aunque falle un bloque complementario', async () => {
    locales.precios$.and.returnValue(
      throwError(() => new ApiError(500, 'Fallo el histórico.')),
    );

    const fixture = await montar();
    const html = texto(fixture);

    expect(html).toContain('Av. Larco 123');
    expect(html).toContain('Fallo el histórico.');
  });

  it('el agente puede publicar y cambiar el estado del anuncio', async () => {
    const fixture = await montar('AGENTE');

    expect(texto(fixture)).toContain('Publicar');
    expect(texto(fixture)).toContain('Editar local');

    const acceso = fixture.componentInstance as unknown as AccesoLocalDetail;
    await acceso.cambiarEstado(PUBLICACION, 'S');

    expect(locales.cambiarEstadoPublicacion).toHaveBeenCalledOnceWith(77, 3, 'S');
    expect(locales.publicaciones).toHaveBeenCalledWith(77);
  });

  it('el broker entra a la ficha pero sin acciones de escritura', async () => {
    const fixture = await montar('BROKER');
    const html = texto(fixture);

    expect(html).toContain('Av. Larco 123');
    expect(html).toContain('Solo lectura');
    expect(html).not.toContain('Editar local');
  });

  it('un id inválido no dispara ninguna lectura', async () => {
    const fixture = await montar('AGENTE', 'abc');

    expect(locales.obtener$).not.toHaveBeenCalled();
    expect(texto(fixture)).toContain('no es válido');
  });

  it('conserva la lista visible si el refresco posterior al cambio falla', async () => {
    const fixture = await montar('AGENTE');
    locales.publicaciones.and.rejectWith(new ApiError(500, 'Sin conexión.'));

    const acceso = fixture.componentInstance as unknown as AccesoLocalDetail;
    await acceso.cambiarEstado(PUBLICACION, 'C');
    fixture.detectChanges();

    const html = texto(fixture);
    expect(html).toContain('Sin conexión.');
    expect(html).toContain('Urbania');
  });

  describe('nivelProspeccion', () => {
    it('trata E y S como el mismo hito porque el cable no emite E', () => {
      expect(nivelProspeccion('E')).toBe(nivelProspeccion('S'));
    });

    it('ordena la línea de tiempo de contacto a captación', () => {
      expect(nivelProspeccion('C')).toBeLessThan(nivelProspeccion('R'));
      expect(nivelProspeccion('R')).toBeLessThan(nivelProspeccion('S'));
      expect(nivelProspeccion('S')).toBeLessThan(nivelProspeccion('T'));
    });

    it('un estado desconocido o ausente no completa ningún hito', () => {
      expect(nivelProspeccion(undefined)).toBe(0);
      expect(nivelProspeccion('Z')).toBe(0);
    });
  });

  async function montar(
    rol: RolSesion = 'AGENTE',
    id = '77',
  ): Promise<ComponentFixture<LocalDetail>> {
    const sesion = signal<Sesion | null>({
      token: 't',
      expiraEnSegundos: 3600,
      rol,
      idUsuario: 1,
      idDominio: 2,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });

    TestBed.configureTestingModule({
      imports: [LocalDetail],
      providers: [
        { provide: LocalesService, useValue: locales },
        { provide: ProspeccionesService, useValue: prospecciones },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id })) },
        },
        { provide: Router, useValue: router },
      ],
    });

    const fixture = TestBed.createComponent(LocalDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<LocalDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function pagina(items: Prospeccion[]): PageResponse<Prospeccion> {
  return { items, totalRecords: items.length, page: 1, pageSize: 20 };
}
