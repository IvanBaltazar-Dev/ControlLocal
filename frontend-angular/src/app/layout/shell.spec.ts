import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AlertasService } from '../core/api/alertas.service';
import { PerfilService } from '../core/api/perfil.service';
import { AuthService } from '../core/auth/auth.service';
import { RenovacionSesion } from '../core/auth/renovacion-sesion';
import { RolSesion, Sesion } from '../core/auth/sesion.model';
import { NavegacionLegado } from '../core/navegacion-legado';
import { Shell } from './shell';

/**
 * **El cajón del móvil y el menú que lo llena.**
 *
 * El armazón no tenía prueba y hasta este corte tampoco tenía móvil: la
 * auditoría midió *cero* media queries en `shell.scss` y una rejilla de 218 px
 * a cualquier ancho (`auditoria-ui-brox.md` §1 hallazgo 7). D-E3-1 §6.4 lo
 * resolvió con un cajón, y un cajón tiene una regla que no se ve en el CSS:
 * **hay que poder cerrarlo por todas las puertas por las que se abre**.
 *
 * Eso es lo que se fija aquí — que el velo solo existe con el cajón abierto,
 * que se cierra por el velo, por Escape y al entrar a una pantalla, y que el
 * botón dice si está abierto—. El aspecto (los 900 px, el deslizamiento) es
 * CSS y no se mide en una prueba unitaria: el navegador de Karma tiene su
 * propio ancho.
 */
describe('Shell — el cajón del menú', () => {
  const SESION: Sesion = {
      idCuenta: 901, idOrganizacion: 1, revisionContexto: 1, contextoRequerido: false, contextos: [],
      modalidad: 'EMPRESARIAL', referenciaSesion: 'sesion-fixture', contextoRef: 'contexto-fixture',
    token: 't',
    expiraEnSegundos: 3600,
    rol: 'AGENTE',
    idUsuario: 1,
    idDominio: 30,
    nombre: 'Valeria Mora',
    usuario: 'vmora',
    expiraEn: '2099-01-01T00:00:00',
  };

  let fixture: ComponentFixture<Shell>;
  let alertas: jasmine.SpyObj<AlertasService>;

  async function montar(rol: RolSesion = 'AGENTE'): Promise<Shell> {
    TestBed.resetTestingModule();
    alertas = jasmine.createSpyObj<AlertasService>('AlertasService', ['pagina', 'atender']);
    alertas.pagina.and.resolveTo({ items: [], totalRecords: 0, page: 1, pageSize: 20 });
    const navegacion = jasmine.createSpyObj<NavegacionLegado>('NavegacionLegado', [
      'abrir',
      'puedeAbrir',
    ]);
    navegacion.puedeAbrir.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { sesion: signal<Sesion | null>({...SESION, rol}), salir: () => Promise.resolve() } },
        {
          provide: PerfilService,
          useValue: {
            fotoClave: signal<string | null>(null),
            obtener: () => Promise.resolve({}),
            olvidarFoto: () => undefined,
          },
        },
        // El vigilante de inactividad pide un token nuevo por su cuenta: aquí
        // se mide el cajón, no la renovación de la sesión.
        { provide: RenovacionSesion, useValue: { iniciar: () => undefined } },
        { provide: AlertasService, useValue: alertas },
        { provide: NavegacionLegado, useValue: navegacion },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const raiz = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const boton = (): HTMLButtonElement => raiz().querySelector<HTMLButtonElement>('.abrir-menu')!;
  const velo = (): HTMLElement | null => raiz().querySelector('.velo');

  function estabilizar(): void {
    fixture.detectChanges();
  }

  /**
   * El botón está SIEMPRE en el DOM y es el CSS quien lo enseña por debajo de
   * 900 px. Ocultarlo con un `@if` sobre el ancho obligaría a escuchar el
   * `resize` para algo que la media query ya resuelve.
   */
  it('la puerta del cajón existe siempre y dice si está abierto', async () => {
    await montar();

    expect(boton()).not.toBeNull();
    expect(boton().getAttribute('aria-expanded')).toBe('false');
    expect(boton().getAttribute('aria-controls')).toBe('navegacion-brox');
    expect(raiz().querySelector('#navegacion-brox')).not.toBeNull();

    boton().click();
    estabilizar();

    expect(boton().getAttribute('aria-expanded')).toBe('true');
    expect(boton().getAttribute('aria-label')).toBe('Cerrar el menú');
  });

  /**
   * El velo dice dos cosas a la vez: que lo de detrás está inerte y dónde
   * pulsar para volver. Sin cajón abierto no pinta nada, porque taparía el
   * contenido sin motivo.
   */
  it('el velo solo existe con el cajón abierto, y cerrarlo lo retira', async () => {
    await montar();

    expect(velo()).toBeNull();

    boton().click();
    estabilizar();
    expect(velo()).not.toBeNull();

    velo()!.click();
    estabilizar();
    expect(velo()).toBeNull();
    expect(boton().getAttribute('aria-expanded')).toBe('false');
  });

  /**
   * Escape se escucha en el DOCUMENTO y no en el `<aside>`: al abrir el cajón
   * el foco sigue en el botón que lo abrió, así que un `keydown` sobre el
   * lateral no llegaría nunca.
   */
  it('Escape cierra el cajón desde donde esté el foco', async () => {
    await montar();

    boton().click();
    estabilizar();
    expect(velo()).not.toBeNull();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    estabilizar();

    expect(velo()).toBeNull();
  });

  /**
   * Entrar a una pantalla cierra el cajón. Dejarlo abierto taparía justo
   * aquello a lo que se acaba de entrar, que es el error clásico del patrón.
   */
  it('pulsar una entrada del menú cierra el cajón', async () => {
    await montar();

    boton().click();
    estabilizar();

    const entrada = raiz().querySelector<HTMLAnchorElement>('#navegacion-brox nav a')!;
    entrada.click();
    estabilizar();

    expect(velo()).toBeNull();
  });

  /**
   * El menú sale del mapa `acceso.ts`, no de la plantilla, y a un AGENTE le
   * corresponden 13 entradas —sin Organización— con su icono cada una. Lo que
   * esta prueba impide es que el armazón vuelva a escribirse su propia lista.
   */
  it('dibuja el menú del rol, con un icono por entrada', async () => {
    await montar();

    const entradas = raiz().querySelectorAll('#navegacion-brox nav a');
    expect(entradas.length).toBe(13);
    expect(raiz().querySelectorAll('#navegacion-brox nav a cl-icono').length).toBe(13);

    const rotulos = Array.from(entradas).map((a) => a.textContent?.trim());
    expect(rotulos).toContain('Inicio');
    expect(rotulos).toContain('Contratos');
    // Las dos uniones de D-E3-1 §4: ninguna de las dos es una entrada de menú.
    expect(rotulos).not.toContain('Interacciones');
    expect(rotulos).not.toContain('Avance por propiedad');
  });

  it('gobierno ve sus cinco entradas y no crea el consumidor de avisos comerciales', async () => {
    await montar('TENANT_ADMIN');

    const rotulos = Array.from(raiz().querySelectorAll('#navegacion-brox nav a')).map((a) =>
      a.textContent?.trim(),
    );
    expect(rotulos).toEqual(['Reasignaciones', 'Agentes', 'Brokers', 'Asignaciones', 'Seguridad y accesos']);
    expect(raiz().querySelector('cl-campana')).toBeNull();
    expect(alertas.pagina).not.toHaveBeenCalled();
  });

  it('conserva la campana comercial de agente y broker', async () => {
    for (const rol of ['AGENTE', 'BROKER'] as const) {
      await montar(rol);
      expect(raiz().querySelector('cl-campana')).not.toBeNull();
      expect(alertas.pagina).toHaveBeenCalled();
    }
  });

  /**
   * La cuenta vive arriba a la derecha, no abajo en el lateral: repetirla la
   * ponía dos veces en la misma pantalla.
   */
  it('la cuenta no se dibuja en el lateral', async () => {
    await montar();

    const rotulos = Array.from(raiz().querySelectorAll('#navegacion-brox nav a')).map((a) =>
      a.textContent?.trim(),
    );
    expect(rotulos).not.toContain('Mi perfil');
  });
});
