import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { API_BASE_URL } from '../api/api.config';
import { LoginResponse, Sesion } from './sesion.model';
import { AuthService } from './auth.service';

const CLAVE_SESION = 'controllocal.sesion.v2';

describe('AuthService', () => {
  const navegar = jasmine.createSpy('navigate');
  // `url` es mutable: es lo que lee `cerrarSesion` para recordar dónde estaba
  // el usuario cuando le caducó la sesión.
  const enrutador = { navigate: navegar, url: '/' };
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    navegar.calls.reset();
    navegar.and.resolveTo(true);
    enrutador.url = '/';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: Router,
          useValue: enrutador,
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => localStorage.clear());

  it('descarta una sesión persistida que ya expiró', () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() - 60_000))));

    const auth = TestBed.inject(AuthService);

    expect(auth.sesion()).toBeNull();
    expect(auth.token).toBeNull();
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
  });

  it('limpia estado y almacenamiento y navega una sola vez ante 401 concurrentes', () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() + 60_000))));
    const auth = TestBed.inject(AuthService);

    auth.cerrarSesion();
    auth.cerrarSesion();

    expect(auth.sesion()).toBeNull();
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    expect(navegar).toHaveBeenCalledOnceWith(['/login'], { replaceUrl: true });
  });

  // ------------------------------------------------------------------
  // D-S0-12: logout con efecto en servidor
  // ------------------------------------------------------------------

  it('salir avisa al servidor ANTES de limpiar, y luego limpia', async () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() + 60_000))));
    const auth = TestBed.inject(AuthService);

    const salida = auth.salir();
    const peticion = http.expectOne(`${API_BASE_URL}/auth/logout`);
    expect(peticion.request.method).toBe('POST');
    // La sesión sigue viva mientras la petición viaja: si se limpiara antes, el
    // interceptor mandaría el logout SIN token y el servidor no sabría a quién
    // invalidar.
    expect(auth.sesion()).not.toBeNull();

    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await salida;

    expect(auth.sesion()).toBeNull();
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    expect(navegar).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });

  it('si el servidor falla, salir limpia igual: no deja al usuario atrapado', async () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() + 60_000))));
    const auth = TestBed.inject(AuthService);

    const salida = auth.salir();
    http.expectOne(`${API_BASE_URL}/auth/logout`)
      .flush({ error: 'boom' }, { status: 500, statusText: 'Server Error' });
    await salida;

    // El token caduca solo en 30 min; dejar al usuario dentro porque el
    // servidor no contestó sería peor.
    expect(auth.sesion()).toBeNull();
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    expect(navegar).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });

  // ------------------------------------------------------------------
  // Sesión por INACTIVIDAD: cuando caduca, el usuario no debería perder
  // además la pantalla en la que estaba.
  // ------------------------------------------------------------------

  it('al caducar la sesión recuerda la pantalla para volver a ella', () => {
    enrutador.url = '/locales?estado=D&page=3';
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() + 60_000))));
    const auth = TestBed.inject(AuthService);

    auth.cerrarSesion();

    expect(navegar).toHaveBeenCalledOnceWith(['/login'], {
      queryParams: { volverA: '/locales?estado=D&page=3' },
      replaceUrl: true,
    });
  });

  it('salir a propósito NO recuerda la pantalla: se espera el panel al volver', async () => {
    enrutador.url = '/comisiones';
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() + 60_000))));
    const auth = TestBed.inject(AuthService);

    const salida = auth.salir();
    http.expectOne(`${API_BASE_URL}/auth/logout`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await salida;

    expect(navegar).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });

  it('renovar reemite la sesión y vuelve a resolver la banda efectiva', async () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() + 60_000))));
    const auth = TestBed.inject(AuthService);

    const renovada = auth.renovar();
    const peticion = http.expectOne(`${API_BASE_URL}/auth/renovar`);
    expect(peticion.request.method).toBe('POST');
    peticion.flush({ ...cable(), token: 'token-renovado' });
    await tick();
    // La banda efectiva no cabe en el token: se relee en cada renovación para
    // que un rol cambiado en el servidor no siga mandando en el menú otra
    // media hora. Aquí el cable dice ADMIN y el servidor, TENANT_ADMIN.
    http.expectOne(`${API_BASE_URL}/sesion`).flush({ rol: 'TENANT_ADMIN' });

    await renovada;

    expect(auth.token).toBe('token-renovado');
    expect(auth.sesion()?.rol).toBe('TENANT_ADMIN');
  });

  // ------------------------------------------------------------------
  // V37 (D-S0-22): el ingreso es de dos pasos, y el primero no siempre abre
  // sesión. El SPA no puede saber de antemano quién tiene segundo factor —
  // preguntarlo sería un padrón de cuentas—, así que distingue por el estado.
  // ------------------------------------------------------------------

  it('el 202 del desafío NO abre sesión: un desafío no autoriza nada', async () => {
    const auth = TestBed.inject(AuthService);

    const entrando = auth.login({ usuario: 'admin', contrasena: 'x' });
    const peticion = http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`);
    peticion.flush(
      { desafio: 'vale-de-cinco-minutos', expiraEn: '2026-08-06T10:00:00Z', metodo: 'TOTP' },
      { status: 202, statusText: 'Accepted' },
    );
    const resultado = await entrando;

    expect(resultado.tipo).toBe('desafio');
    expect(auth.sesion()).toBeNull();
    expect(auth.token).toBeNull();
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    // Nada que pedir todavía: sin sesión no hay banda efectiva que resolver.
    http.expectNone(`${API_BASE_URL}/sesion`);
  });

  it('canjear el desafío abre la sesión igual que un login sin segundo factor', async () => {
    const auth = TestBed.inject(AuthService);

    const verificando = auth.verificarSegundoFactor('vale', '123456');
    http.expectOne(`${API_BASE_URL}/auth/mfa/verificar`).flush(cable());
    await tick();
    // La banda real la resuelve el servidor: el `rol` del cable congelado solo
    // admite ADMIN|BROKER|AGENTE y ese ADMIN no es gobierno.
    http.expectOne(`${API_BASE_URL}/sesion`).flush({
      rol: 'TENANT_ADMIN',
      usuario: 'admin',
      idPersona: 1,
      idDominio: 1,
    });
    const abierta = await verificando;

    expect(abierta.rol).toBe('TENANT_ADMIN');
    expect(auth.token).toBe('token-prueba');
    expect(localStorage.getItem(CLAVE_SESION)).not.toBeNull();
  });

  it('anota la sesión capada cuando GET /sesion responde el 403 del enrolamiento', async () => {
    const auth = TestBed.inject(AuthService);

    const entrando = auth.login({ usuario: 'admin', contrasena: 'x' });
    http.expectOne(`${API_BASE_URL}/auth/mfa/desafio`).flush(cable());
    await tick();
    http.expectOne(`${API_BASE_URL}/sesion`).flush(
      { error: 'Debes enrolar tu segundo factor antes de continuar.', codigo: 'ENROLAMIENTO_MFA_REQUERIDO' },
      { status: 403, statusText: 'Forbidden' },
    );
    await entrando;

    // La sesión existe —el token vale para el enrolamiento— pero el panel
    // respondería 403 entero, así que el login manda al paso que falta.
    expect(auth.debeEnrolarMfa()).toBeTrue();
    expect(auth.token).toBe('token-prueba');
  });

  it('cerrarSesion NO llama al servidor: es también el camino del 401', () => {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion(new Date(Date.now() + 60_000))));
    const auth = TestBed.inject(AuthService);

    auth.cerrarSesion();

    // Con el token ya inválido, pedir el logout devolvería otro 401 y
    // realimentaría el mismo cierre.
    http.expectNone(`${API_BASE_URL}/auth/logout`);
    expect(auth.sesion()).toBeNull();
  });
});

/**
 * Deja correr la cola de microtareas. Hace falta porque `GET /sesion` no se
 * emite dentro del `flush`: sale del `await` que sigue a la respuesta del
 * login, y sin ceder el turno `expectOne` buscaría una petición que todavía no
 * existe.
 */
function tick(): Promise<void> {
  return new Promise((resolver) => setTimeout(resolver, 0));
}

/** `LoginResponse` tal cual sale del cable congelado. */
function cable(): LoginResponse {
  return {
    token: 'token-prueba',
    expiraEnSegundos: 3600,
    rol: 'ADMIN',
    idUsuario: 1,
    idDominio: 1,
    nombre: 'Administrador',
    usuario: 'admin',
    expiraEn: new Date(Date.now() + 3_600_000).toISOString(),
  };
}

function sesion(expiraEn: Date): Sesion {
  return {
    token: 'token-prueba',
    expiraEnSegundos: 3600,
    rol: 'AGENTE',
    idUsuario: 1,
    idDominio: 2,
    nombre: 'Agente Prueba',
    usuario: 'agente',
    expiraEn: expiraEn.toISOString(),
  };
}
