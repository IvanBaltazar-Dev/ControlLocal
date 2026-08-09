import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { API_BASE_URL } from '../api/api.config';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let cliente: HttpClient;
  const navegar = jasmine.createSpy('navigate').and.resolveTo(true);
  const auth = {
    token: 'jwt-prueba',
    cerrarSesion: jasmine.createSpy('cerrarSesion'),
  };

  beforeEach(() => {
    auth.cerrarSesion.calls.reset();
    navegar.calls.reset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: { navigate: navegar } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    cliente = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('adjunta el token únicamente a peticiones del API ControlLocal', () => {
    cliente.get(`${API_BASE_URL}/locales`).subscribe();
    const api = http.expectOne(`${API_BASE_URL}/locales`);
    expect(api.request.headers.get('Authorization')).toBe('Bearer jwt-prueba');
    api.flush({});

    cliente.get('https://servicio-externo.test/recurso').subscribe();
    const externo = http.expectOne('https://servicio-externo.test/recurso');
    expect(externo.request.headers.has('Authorization')).toBeFalse();
    externo.flush({});
  });

  it('no adjunta token ni cierra una sesión por el 401 esperado del login', () => {
    cliente.post(`${API_BASE_URL}/auth/login`, {}).subscribe({ error: () => undefined });

    const login = http.expectOne(`${API_BASE_URL}/auth/login`);
    expect(login.request.headers.has('Authorization')).toBeFalse();
    login.flush({ error: 'Credenciales invalidas.' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.cerrarSesion).not.toHaveBeenCalled();
  });

  it('cierra la sesión ante un 401 de cualquier endpoint protegido', () => {
    cliente.get(`${API_BASE_URL}/perfil`).subscribe({ error: () => undefined });

    http
      .expectOne(`${API_BASE_URL}/perfil`)
      .flush({ error: 'Token invalido o expirado.' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.cerrarSesion).toHaveBeenCalledTimes(1);
  });

  it('ignora un 401 de una URL externa', () => {
    cliente.get('https://servicio-externo.test/recurso').subscribe({ error: () => undefined });

    http
      .expectOne('https://servicio-externo.test/recurso')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.cerrarSesion).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------
  // V37: los dos caminos nuevos del segundo factor
  // ------------------------------------------------------------------

  it('trata el desafío de MFA como entrada pública: sin token y sin cerrar sesión', () => {
    cliente.post(`${API_BASE_URL}/auth/mfa/desafio`, {}).subscribe({ error: () => undefined });

    const desafio = http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`);
    // Si el 401 de una contraseña equivocada cerrara la sesión, recargaría la
    // pantalla de login y se llevaría por delante el mensaje de error.
    expect(desafio.request.headers.has('Authorization')).toBeFalse();
    desafio.flush({ error: 'Credenciales invalidas.' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.cerrarSesion).not.toHaveBeenCalled();
  });

  it('lleva al enrolamiento ante el 403 de la sesión capada por segundo factor', () => {
    cliente.get(`${API_BASE_URL}/sesion`).subscribe({ error: () => undefined });

    http.expectOne(`${API_BASE_URL}/sesion`).flush(
      { error: 'Debes enrolar tu segundo factor antes de continuar.', codigo: 'ENROLAMIENTO_MFA_REQUERIDO' },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(navegar).toHaveBeenCalledOnceWith(['/enrolar-mfa'], {
      queryParams: { obligatorio: '1' },
      replaceUrl: true,
    });
  });

  it('un 403 SIN código no desvía a ninguna parte: es un "no tienes permisos" normal', () => {
    cliente.get(`${API_BASE_URL}/agentes`).subscribe({ error: () => undefined });

    http
      .expectOne(`${API_BASE_URL}/agentes`)
      .flush({ error: 'No tienes permisos para esta operacion.' }, { status: 403, statusText: 'Forbidden' });

    expect(navegar).not.toHaveBeenCalled();
  });
});
