import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion, Sesion } from '../../core/auth/sesion.model';
import { ProspeccionDetail, rangoEstado } from './prospeccion-detail';

const PROSPECCION: Prospeccion = {
  id: 7,
  codigoProspeccion: 'PRO-0007',
  localId: 12,
  localCodigo: 'LOC-0012',
  direccion: 'Av. Larco 700',
  distrito: 'Miraflores',
  propietarioNombre: 'Ana Torres',
  agenteNombre: 'Valentina Mora',
  estado: 'S',
  fechaContacto: '2026-07-20',
  fechaReunion: '2026-07-22',
  fechaPropuesta: '2026-07-25',
  fechaRecontacto: '2026-07-31',
};

describe('ProspeccionDetail', () => {
  let api: jasmine.SpyObj<ProspeccionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<ProspeccionesService>('ProspeccionesService', [
      'obtener', 'contactar', 'registrarReunion', 'entregarPropuesta',
      'registrarSeguimiento', 'rechazar', 'descartar',
    ]);
    api.obtener.and.resolveTo({ ...PROSPECCION });
    api.contactar.and.resolveTo({ ...PROSPECCION, estado: 'C' });
    api.registrarSeguimiento.and.resolveTo({ ...PROSPECCION, fechaRecontacto: '2026-08-08' });
    api.rechazar.and.resolveTo({ ...PROSPECCION, estado: 'D' });
    api.descartar.and.resolveTo({ ...PROSPECCION, estado: 'D' });
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('muestra las acciones de seguimiento y abre la captación con el id de origen', async () => {
    const fixture = await montar('AGENTE');
    expect(texto(fixture)).toContain('Registrar seguimiento');
    expect(texto(fixture)).toContain('Crear captación');

    boton(fixture, 'Crear captación').click();
    expect(router.navigate).toHaveBeenCalledOnceWith(['/captaciones/nueva'], {
      queryParams: { prospeccion: 7 },
    });
  });

  it('avanza el estado P por el endpoint contactar', async () => {
    api.obtener.and.resolveTo({ ...PROSPECCION, estado: 'P' });
    const fixture = await montar('AGENTE');
    boton(fixture, 'Registrar contacto').click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.contactar).toHaveBeenCalledOnceWith(7);
    expect(texto(fixture)).toContain('Contacto con el propietario registrado.');
  });

  it('broker y admin leen, pero no mutan la máquina de estados', async () => {
    const fixture = await montar('BROKER');
    expect(texto(fixture)).toContain('Solo lectura');
    expect(texto(fixture)).not.toContain('Registrar seguimiento');
    expect(texto(fixture)).not.toContain('Descartar prospección');
  });

  it('una prospección en seguimiento usa rechazar y exige motivo', async () => {
    const fixture = await montar('AGENTE');
    const componente = fixture.componentInstance as unknown as {
      motivo: { setValue(valor: string): void };
      abrirDescartar(): void;
      confirmarDescartar(): Promise<void>;
    };
    componente.abrirDescartar();
    componente.motivo.setValue('El propietario cambió de decisión');
    await componente.confirmarDescartar();

    expect(api.rechazar).toHaveBeenCalledOnceWith(7, 'El propietario cambió de decisión');
    expect(api.descartar).not.toHaveBeenCalled();
  });

  it('E y S comparten el hito de propuesta del cable real', () => {
    expect(rangoEstado('E')).toBe(rangoEstado('S'));
  });

  async function montar(rol: RolSesion): Promise<ComponentFixture<ProspeccionDetail>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
      token: 't', expiraEnSegundos: 3600, rol, idUsuario: 1, idDominio: 30,
      nombre: 'Prueba', usuario: 'prueba', expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [ProspeccionDetail],
      providers: [
        { provide: ProspeccionesService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 7 }) } } },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(ProspeccionDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<ProspeccionDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

function boton(fixture: ComponentFixture<ProspeccionDetail>, etiqueta: string): HTMLButtonElement {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
    .find((elemento) => elemento.textContent?.trim() === etiqueta) as HTMLButtonElement;
}
