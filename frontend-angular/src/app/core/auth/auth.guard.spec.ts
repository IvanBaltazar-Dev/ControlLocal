import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';
import { RolSesion } from './sesion.model';

describe('authGuard contextual', () => {
  let rol: RolSesion;
  let requerido: boolean;
  let router: jasmine.SpyObj<Router>;
  beforeEach(() => {
    rol = 'IDENTIDAD'; requerido = true;
    router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
    router.createUrlTree.and.returnValue(new UrlTree());
    TestBed.configureTestingModule({ providers: [
      { provide: Router, useValue: router },
      { provide: AuthService, useValue: { autenticado: () => true,
        sesion: () => ({ rol, contextoRequerido: requerido }) } },
    ] });
  });
  function ejecutar(path: string): unknown {
    return TestBed.runInInjectionContext(() => authGuard(
      { routeConfig: { path } } as ActivatedRouteSnapshot,
      { url: '/propiedades?pagina=2' } as RouterStateSnapshot));
  }
  it('un token IDENTIDAD no abre el shell del negocio', () => {
    expect(ejecutar('')).not.toBeTrue();
    expect(router.createUrlTree).toHaveBeenCalledOnceWith(['/login'],
      { queryParams: { volverA: '/propiedades?pagina=2' } });
  });
  it('mantiene las superficies seguras de contraseña y MFA sin abrir negocio', () => {
    expect(ejecutar('enrolar-mfa')).toBeTrue();
    expect(ejecutar('cambiar-contrasena')).toBeTrue();
  });
  it('requiere una atribución efectiva además de la bandera contextual', () => {
    requerido = false;
    expect(ejecutar('')).not.toBeTrue();
    rol = 'BROKER';
    expect(ejecutar('')).toBeTrue();
  });
});
