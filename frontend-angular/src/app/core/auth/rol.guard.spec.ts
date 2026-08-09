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
            sesion: () => ({ rol }),
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

  it('bloquea al broker aunque el listado de locales sea visible para todos', () => {
    rol = 'BROKER';

    const resultado = ejecutar(['AGENTE']);

    expect(resultado).toBe(denegado);
    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/acceso-denegado']);
  });

  function ejecutar(roles: readonly RolSesion[]): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      rolGuard(
        { data: { roles } } as unknown as ActivatedRouteSnapshot,
        { url: '/locales/nuevo' } as RouterStateSnapshot,
      ),
    ) as boolean | UrlTree;
  }
});
