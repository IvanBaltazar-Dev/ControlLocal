import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ContextoAcceso } from '../../core/auth/sesion.model';
import { Login } from './login';

const OPCION: ContextoAcceso = { referencia: 'referencia-core', idOrganizacion: 7,
  idPersona: 11, idRolOperativo: 21, rol: 'BROKER', modalidad: 'EMPRESARIAL', nombreOrganizacion: 'Organización' };
interface AccesoLogin {
  formularioContexto: { controls: { referencia: { setValue(v: string): void } } };
  elegirContexto(): Promise<void>;
}
describe('Login selector seguro', () => {
  let auth: jasmine.SpyObj<AuthService>;
  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['autenticado', 'contextoRequerido',
      'tieneContexto', 'debeEnrolarMfa', 'debeCambiarContrasena', 'contextosDisponibles',
      'seleccionarContexto', 'salir']);
    auth.autenticado.and.returnValue(true);
    auth.contextoRequerido.and.returnValue(true);
    auth.tieneContexto.and.returnValue(false);
    auth.debeEnrolarMfa.and.returnValue(false);
    auth.debeCambiarContrasena.and.returnValue(false);
    auth.contextosDisponibles.and.resolveTo([OPCION]);
    auth.seleccionarContexto.and.resolveTo(undefined as never);
    TestBed.configureTestingModule({ imports: [Login], providers: [provideRouter([]),
      { provide: AuthService, useValue: auth },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ volverA: '/propiedades' }) } } },
    ] });
  });
  it('incluso con una única opción espera elección humana y conserva el destino', async () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(auth.seleccionarContexto).not.toHaveBeenCalled();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#contexto');
    expect(select.value).toBe('');
    const acceso = fixture.componentInstance as unknown as AccesoLogin;
    acceso.formularioContexto.controls.referencia.setValue(OPCION.referencia);
    await acceso.elegirContexto();
    expect(auth.seleccionarContexto).toHaveBeenCalledOnceWith(OPCION.referencia, '/propiedades');
  });
  it('no construye una organización o rol desde una referencia no ofrecida', async () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges(); await fixture.whenStable();
    const acceso = fixture.componentInstance as unknown as AccesoLogin;
    acceso.formularioContexto.controls.referencia.setValue('tenant-arbitrario');
    await acceso.elegirContexto();
    expect(auth.seleccionarContexto).not.toHaveBeenCalled();
  });
});
