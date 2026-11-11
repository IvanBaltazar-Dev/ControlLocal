import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { AuthService } from './auth.service';
import { rolGuard } from './rol.guard';
import { RolSesion } from './sesion.model';

describe('rolGuard por operacion', () => {
  let rol: RolSesion;
  let router: jasmine.SpyObj<Router>;
  let denegado: UrlTree;

  beforeEach(() => {
    rol = 'AGENTE';
    denegado = new UrlTree();
    router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
    router.createUrlTree.and.returnValue(denegado);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            autenticado: () => true,
            sesion: () => ({ rol, contextoRequerido: rol === 'IDENTIDAD' }),
          },
        },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('permite al agente entrar al alta de locales', () => {
    const resultado = ejecutar(['AGENTE']);

    expect(resultado).toBeTrue();
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('bloquea al broker en un alta reservada al agente', () => {
    rol = 'BROKER';

    const resultado = ejecutar(['AGENTE']);

    expect(resultado).toBe(denegado);
    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/acceso-denegado']);
  });

  it('IDENTIDAD no entra a una operación por poseer un token', () => {
    rol = 'IDENTIDAD';
    expect(ejecutar(['AGENTE', 'BROKER', 'TENANT_ADMIN'])).toBe(denegado);
    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/login']);
  });

  it('dirige el inicio de gobierno a Seguridad antes de abrir el tablero comercial', () => {
    rol = 'TENANT_ADMIN';
    expect(ejecutar(undefined, '/?origen=entrada')).toBe(denegado);
    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/seguridad']);
  });

  it('bloquea módulos y detalles comerciales al administrador', () => {
    rol = 'TENANT_ADMIN';
    for (const url of ['/propiedades', '/propiedades/42', '/clientes/12/contacto', '/solicitudes/7/documentos']) {
      router.createUrlTree.calls.reset();
      expect(ejecutar(undefined, url)).toBe(denegado);
      expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/acceso-denegado']);
    }
  });

  it('permite el padrón, el gobierno y el perfil propio con sus rutas hijas', () => {
    rol = 'TENANT_ADMIN';
    for (const url of ['/agentes', '/brokers/42', '/asignaciones', '/seguridad', '/perfil', '/perfil/editar']) {
      expect(ejecutar(undefined, url)).toBeTrue();
    }
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('conserva la actuación explícita de gobierno sin heredar el módulo comercial', () => {
    rol = 'TENANT_ADMIN';
    expect(ejecutar(['TENANT_ADMIN'], '/miembros/invitar')).toBeTrue();
    expect(ejecutar(['TENANT_ADMIN'], '/agentes/30/editar')).toBeTrue();
    expect(ejecutar(['BROKER', 'TENANT_ADMIN'], '/captaciones/reasignaciones')).toBeTrue();
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('la restricción explícita de la ficha comercial prevalece sobre el padrón', () => {
    rol = 'TENANT_ADMIN';
    expect(ejecutar(['BROKER'], '/agentes/30')).toBe(denegado);
    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/acceso-denegado']);
  });

  it('no abre a gobierno una ruta sin operación ni módulo autorizado', () => {
    rol = 'TENANT_ADMIN';
    expect(ejecutar(undefined, '/sin-modulo/42')).toBe(denegado);
    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/acceso-denegado']);
  });

  it('mantiene el inicio y los detalles comerciales de agente y broker', () => {
    for (const comercial of ['AGENTE', 'BROKER'] as const) {
      rol = comercial;
      expect(ejecutar(undefined, '/')).toBeTrue();
      expect(ejecutar(undefined, '/propiedades/42')).toBeTrue();
    }
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  function ejecutar(roles?: readonly RolSesion[], url = '/propiedades/nueva'): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      rolGuard(
        { data: { roles } } as unknown as ActivatedRouteSnapshot,
        { url } as RouterStateSnapshot,
      ),
    ) as boolean | UrlTree;
  }
});
