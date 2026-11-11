import { computed, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api/api.config';
import { destinoSeguro, PARAM_DESTINO } from './destino-tras-login';
import { ContextoAcceso, DesafioMfa, LoginRequest, LoginResponse, RolSesion, Sesion, SesionEfectiva } from './sesion.model';

const CLAVE_SESION = 'controllocal.sesion.v3';
const CLAVE_SESION_RETIRADA = 'controllocal.sesion.v2';
export const NAVEGACION_COMPLETA = new InjectionToken<(destino: string) => void>('NAVEGACION_COMPLETA', {
  providedIn: 'root', factory: () => (destino) => window.location.assign(destino),
});
export type ResultadoIngreso =
  | { tipo: 'sesion'; sesion: Sesion }
  | { tipo: 'desafio'; desafio: string; expiraEn: string };

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly navegarCompletamente = inject(NAVEGACION_COMPLETA);
  private cerrandoSesion = false;
  private readonly sesionActual = signal<Sesion | null>(leerSesionGuardada());
  private readonly enrolamientoPendiente = signal(false);
  private readonly cambioPendiente = signal(false);
  readonly sesion = this.sesionActual.asReadonly();
  readonly debeEnrolarMfa = this.enrolamientoPendiente.asReadonly();
  readonly debeCambiarContrasena = this.cambioPendiente.asReadonly();
  readonly autenticado = computed(() => {
    const s = this.sesionActual();
    return !!s && Date.parse(s.expiraEn) > Date.now();
  });
  readonly tieneContexto = computed(() => this.autenticado() && this.sesionActual()?.contextoRequerido === false);
  readonly contextoRequerido = computed(() => this.autenticado() && this.sesionActual()?.contextoRequerido === true);
  get token(): string | null { return this.sesionActual()?.token ?? null; }

  async login(credenciales: LoginRequest): Promise<ResultadoIngreso> {
    const respuesta = await firstValueFrom(this.http.post<LoginResponse | DesafioMfa>(
      `${API_BASE_URL}/auth/mfa/desafio`, credenciales, { observe: 'response' }));
    if (respuesta.status === 202) {
      const d = respuesta.body as DesafioMfa;
      return { tipo: 'desafio', desafio: d.desafio, expiraEn: d.expiraEn };
    }
    return { tipo: 'sesion', sesion: await this.abrirSesion(respuesta.body as LoginResponse) };
  }

  async verificarSegundoFactor(desafio: string, codigo: string): Promise<Sesion> {
    return this.abrirSesion(await firstValueFrom(this.http.post<LoginResponse>(
      `${API_BASE_URL}/auth/mfa/verificar`, { desafio, codigo })));
  }

  /** IDENTIDAD habilita sólo los pasos de acceso; una respuesta fallida no inventa un rol. */
  private async abrirSesion(respuesta: LoginResponse, publicar = true): Promise<Sesion> {
    if (!respuesta || !rolReconocido(respuesta.rol) || !Array.isArray(respuesta.contextos) ||
        !enteroPositivo(respuesta.idCuenta) || !Number.isSafeInteger(respuesta.revisionContexto) ||
        respuesta.revisionContexto < 0 || typeof respuesta.token !== 'string' || !respuesta.token) {
      this.olvidarSesionLocal();
      throw new Error('Core no devolvió una sesión contextual verificable.');
    }
    const identidad: Sesion = { ...respuesta, rol: 'IDENTIDAD', contextoRequerido: true,
      idOrganizacion: 0, idUsuario: 0, idDominio: 0, modalidad: null, referenciaSesion: null, contextoRef: null };
    this.cerrandoSesion = false;
    this.sesionActual.set(identidad);
    this.enrolamientoPendiente.set(false);
    this.cambioPendiente.set(false);
    try {
      const e = await firstValueFrom(this.http.get<SesionEfectiva>(`${API_BASE_URL}/sesion`));
      if (typeof e.debeCambiarContrasena !== 'boolean' || typeof e.debeEnrolarMfa !== 'boolean') {
        throw new Error('Core no devolvió las limitaciones de acceso de la sesión.');
      }
      const actual: Sesion = { ...respuesta, rol: e.rol, usuario: e.usuario, idUsuario: e.idPersona,
        idDominio: e.idDominio, idOrganizacion: e.idOrganizacion, idCuenta: e.idCuenta,
        revisionContexto: e.revisionContexto, referenciaSesion: e.referenciaSesion,
        contextoRef: e.contextoRef, modalidad: e.modalidad, contextoRequerido: e.contextoRequerido };
      if (e.idCuenta !== respuesta.idCuenta || e.revisionContexto !== respuesta.revisionContexto ||
          e.idOrganizacion !== respuesta.idOrganizacion || !sesionVerificable(actual)) {
        throw new Error('Los datos de la sesión no corresponden al contexto confirmado por Core.');
      }
      guardarSesion(actual);
      this.cambioPendiente.set(e.debeCambiarContrasena);
      this.enrolamientoPendiente.set(e.debeEnrolarMfa);
      if (publicar) this.sesionActual.set(actual);
      return actual;
    } catch (error) {
      this.olvidarSesionLocal();
      throw error;
    }
  }

  /** Opciones actuales ofrecidas por Core; no se compone tenant o rol en el cliente. */
  async contextosDisponibles(): Promise<ContextoAcceso[]> {
    const opciones = await firstValueFrom(this.http.get<ContextoAcceso[]>(`${API_BASE_URL}/sesion/contextos`));
    if (!Array.isArray(opciones) || !opciones.every(contextoVerificable)) {
      throw new Error('Core no devolvió opciones de contexto verificables.');
    }
    this.sesionActual.update(s => s ? { ...s, contextos: opciones } : null);
    return opciones;
  }

  async seleccionarContexto(referencia: string, destino = '/'): Promise<Sesion> {
    const respuesta = await firstValueFrom(this.http.post<LoginResponse>(
      `${API_BASE_URL}/sesion/contexto`, { referencia }));
    const actual = await this.abrirSesion(respuesta, false);
    if (actual.contextoRequerido) throw new Error('El contexto todavía no está habilitado por Core.');
    // El estado activo se lee tras reiniciar la página. Los componentes y las
    // pruebas de revisión de otro contexto no reciben una segunda vida en memoria.
    const siguiente = this.debeCambiarContrasena() ? '/cambiar-contrasena?obligatorio=1'
      : this.debeEnrolarMfa() ? '/enrolar-mfa?obligatorio=1' : (destinoSeguro(destino) ?? '/');
    this.navegarCompletamente(siguiente);
    return actual;
  }

  async renovar(): Promise<Sesion> {
    return this.abrirSesion(await firstValueFrom(this.http.post<LoginResponse>(`${API_BASE_URL}/auth/renovar`, {})));
  }

  /** Se conserva el cierre global del servidor; una red caída no impide limpiar localmente. */
  async salir(): Promise<void> {
    try { await firstValueFrom(this.http.post<void>(`${API_BASE_URL}/auth/logout`, {})); }
    catch { /* El acceso local se retira también cuando la red falla. */ }
    this.cerrarSesion(false);
  }

  cerrarSesion(recordarDestino = true): void {
    const destino = recordarDestino ? destinoSeguro(this.router.url) : null;
    this.olvidarSesionLocal();
    if (this.cerrandoSesion) return;
    this.cerrandoSesion = true;
    void this.router.navigate(['/login'], destino
      ? { queryParams: { [PARAM_DESTINO]: destino }, replaceUrl: true }
      : { replaceUrl: true });
  }

  olvidarSesionLocal(): void {
    eliminarSesionGuardada();
    this.sesionActual.set(null);
    this.enrolamientoPendiente.set(false);
    this.cambioPendiente.set(false);
  }
}

function enteroPositivo(n: unknown): n is number { return typeof n === 'number' && Number.isSafeInteger(n) && n > 0; }
function rolReconocido(r: unknown): r is RolSesion { return ['IDENTIDAD', 'AGENTE', 'BROKER', 'TENANT_ADMIN'].includes(r as string); }
function contextoVerificable(c: ContextoAcceso): boolean {
  return !!c && typeof c.referencia === 'string' && !!c.referencia && enteroPositivo(c.idOrganizacion) &&
    enteroPositivo(c.idPersona) && enteroPositivo(c.idRolOperativo) &&
    ['AGENTE', 'BROKER', 'TENANT_ADMIN'].includes(c.rol) && typeof c.nombreOrganizacion === 'string';
}
function sesionVerificable(s: Partial<Sesion>): s is Sesion {
  if (typeof s.token !== 'string' || !s.token || typeof s.expiraEn !== 'string' ||
      !Number.isFinite(Date.parse(s.expiraEn)) || Date.parse(s.expiraEn) <= Date.now() ||
      !rolReconocido(s.rol) || !enteroPositivo(s.idCuenta) || !Number.isSafeInteger(s.revisionContexto) ||
      (s.revisionContexto ?? -1) < 0 || !Array.isArray(s.contextos) || !s.contextos.every(contextoVerificable)) return false;
  if (s.contextoRequerido === true) return s.rol === 'IDENTIDAD' && s.idOrganizacion === 0 &&
    s.idUsuario === 0 && s.idDominio === 0 && s.contextoRef === null;
  return s.contextoRequerido === false && s.rol !== 'IDENTIDAD' && enteroPositivo(s.idOrganizacion) &&
    enteroPositivo(s.idUsuario) && enteroPositivo(s.idDominio) && typeof s.referenciaSesion === 'string' &&
    !!s.referenciaSesion && typeof s.contextoRef === 'string' && !!s.contextoRef;
}
function leerSesionGuardada(): Sesion | null {
  try {
    localStorage.removeItem(CLAVE_SESION_RETIRADA);
    const crudo = localStorage.getItem(CLAVE_SESION);
    if (!crudo) return null;
    const s = JSON.parse(crudo) as Partial<Sesion>;
    if (sesionVerificable(s)) return s;
  } catch { /* Una sesión ilegible no concede un contexto. */ }
  eliminarSesionGuardada();
  return null;
}
function guardarSesion(sesion: Sesion): void {
  try { localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion)); }
  catch { /* La sesión válida en memoria no depende del almacenamiento local. */ }
}
function eliminarSesionGuardada(): void {
  try { localStorage.removeItem(CLAVE_SESION); localStorage.removeItem(CLAVE_SESION_RETIRADA); }
  catch { /* El estado en memoria se retira de todos modos. */ }
}
