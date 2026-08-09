import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AvisoPrivacidadService } from '../../core/api/aviso-privacidad.service';
import { Privacidad } from './privacidad';

/**
 * Página pública corporativa. Los tests fijan lo que se decidió **retirar**,
 * porque es lo que tiende a volver: detalles técnicos, instrucciones extensas
 * de eliminación, el texto interno guardado como evidencia y las referencias a
 * funcionalidades futuras.
 */
describe('Privacidad', () => {
  let api: jasmine.SpyObj<AvisoPrivacidadService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<AvisoPrivacidadService>('AvisoPrivacidadService', ['vigente']);
    api.vigente.and.resolveTo({
      version: '1.0',
      vigenteDesde: '2026-08-05T00:00:00Z',
      cambioMaterial: false,
      contenido: 'TEXTO INTERNO QUE NO DEBE PUBLICARSE',
    });
  });

  it('publica las cinco secciones corporativas', async () => {
    const contenido = texto(await montar());

    expect(contenido).toContain('Nuestro compromiso');
    expect(contenido).toContain('Para qué utilizamos la información');
    expect(contenido).toContain('Protección de la información');
    expect(contenido).toContain('Conservación');
    expect(contenido).toContain('Actualizaciones del aviso');
  });

  it('NO publica el texto que el backend guarda como evidencia', async () => {
    const contenido = texto(await montar());

    // El contenido del aviso es dato interno: la página muestra el texto
    // corporativo, no lo que se almacena como prueba de la autorización.
    expect(contenido).not.toContain('TEXTO INTERNO QUE NO DEBE PUBLICARSE');
  });

  it('NO ofrece autoservicio de revocación ni detalles técnicos', async () => {
    const fixture = await montar();
    const contenido = texto(fixture);

    // Las solicitudes se atienden por el correo oficial, no con un botón.
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('button').length).toBe(0);
    expect(contenido).not.toContain('PBKDF2');
    expect(contenido).not.toContain('Revocación:');
    expect(contenido).not.toContain('entrenar modelos');
  });

  it('muestra el correo oficial de atención', async () => {
    const fixture = await montar();
    const enlace = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href^="mailto:"]',
    ) as HTMLAnchorElement | null;

    expect(enlace).not.toBeNull();
    expect(enlace?.getAttribute('href')).toBe('mailto:sivansolutionsgo@gmail.com');
  });

  it('si el API no responde, la página abre igual con la versión publicada', async () => {
    api.vigente.and.rejectWith(new Error('sin backend'));

    const contenido = texto(await montar());

    // Es una página pública: un banner de error asusta y no aporta nada.
    expect(contenido).toContain('Versión 1.0');
    expect(contenido).toContain('5 de agosto de 2026');
    expect(contenido).toContain('Nuestro compromiso');
    expect(contenido).not.toContain('No se pudo');
  });

  async function montar(): Promise<ComponentFixture<Privacidad>> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [Privacidad],
      providers: [{ provide: AvisoPrivacidadService, useValue: api }],
    });
    const fixture = TestBed.createComponent(Privacidad);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
});

function texto(fixture: ComponentFixture<Privacidad>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
