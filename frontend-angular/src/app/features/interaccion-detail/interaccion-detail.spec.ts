import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { Interaccion, InteraccionesService } from '../../core/api/interacciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { Sesion } from '../../core/auth/sesion.model';
import { InteraccionDetail } from './interaccion-detail';

const INTERACCION_PARCIAL: Interaccion = {
  id: 31,
  contexto: 'PROSPECCION',
  idProspeccion: 7,
  propietarioNombre: 'Hugo Salazar',
  contactoTipo: 'Administrador',
  contactoTelefono: '+51 999 333 444',
  fechaHora: '2026-08-01T10:00:00',
  canalContacto: 'L',
  resultado: 'CONTACTADO',
};

describe('InteraccionDetail', () => {
  let api: jasmine.SpyObj<InteraccionesService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    api = jasmine.createSpyObj<InteraccionesService>('InteraccionesService', [
      'obtener',
      'actualizar',
    ]);
    api.obtener.and.resolveTo(INTERACCION_PARCIAL);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
  });

  it('presenta FALTANTE y el rol del snapshot parcial, sin mostrar al propietario', async () => {
    const fixture = await montar();
    const contenido = texto(fixture);

    expect(contenido).toContain('FALTANTE');
    expect(contenido).toContain('Administrador');
    expect(contenido).toContain('+51 999 333 444');
    expect(contenido).not.toContain('Hugo Salazar');
  });

  async function montar(): Promise<ComponentFixture<InteraccionDetail>> {
    TestBed.resetTestingModule();
    const sesion = signal<Sesion | null>({
        idCuenta: 901, idOrganizacion: 1, revisionContexto: 1, contextoRequerido: false, contextos: [],
        modalidad: 'EMPRESARIAL', referenciaSesion: 'sesion-fixture', contextoRef: 'contexto-fixture',
      token: 't',
      expiraEnSegundos: 3600,
      rol: 'AGENTE',
      idUsuario: 1,
      idDominio: 30,
      nombre: 'Prueba',
      usuario: 'prueba',
      expiraEn: '2099-01-01T00:00:00',
    });
    TestBed.configureTestingModule({
      imports: [InteraccionDetail],
      providers: [
        { provide: InteraccionesService, useValue: api },
        { provide: AuthService, useValue: { sesion } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '31' }) } },
        },
        { provide: Router, useValue: router },
      ],
    });
    const fixture = TestBed.createComponent(InteraccionDetail);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<InteraccionDetail>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
