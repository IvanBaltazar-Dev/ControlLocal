import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api/api.config';
import { CODIGO_ENROLAMIENTO_REQUERIDO } from './codigos-mfa';
import { destinoSeguro, PARAM_DESTINO } from './destino-tras-login';
import {
  DesafioMfa,
  LoginRequest,
  LoginResponse,
  RolSesion,
  Sesion,
  SesionEfectiva,
} from './sesion.model';

const CLAVE_SESION = 'controllocal.sesion.v2';

/**
 * Traduce el rol del cable congelado a una banda efectiva **sin conceder
 * gobierno**. Es el mismo criterio que aplica el backend cuando una cuenta no
 * tiene membresía: el `ADMIN` del token es la banda heredada, no `TENANT_ADMIN`.
 */
function sinGobierno(rolDelToken: LoginResponse['rol']): RolSesion {
  return rolDelToken === 'ADMIN' ? 'BROKER' : rolDelToken;
}

/**
 * Lo que devuelve el primer paso del ingreso: o ya hay sesión, o falta el
 * segundo factor. **La pantalla no puede saberlo antes de preguntar** —si la
 * cuenta tiene MFA es un dato de la cuenta, y anticiparlo obligaría a un
 * endpoint que dijera quién lo tiene, es decir, un padrón.
 */
export type ResultadoIngreso =
  | { tipo: 'sesion'; sesion: Sesion }
  | { tipo: 'desafio'; desafio: string; expiraEn: string };

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private cerrandoSesion = false;

  private readonly sesionActual = signal<Sesion | null>(leerSesionGuardada());

  /** La sesión existe pero está capada hasta enrolar el segundo factor. */
  private readonly enrolamientoPendiente = signal(false);

  readonly sesion = this.sesionActual.asReadonly();
  readonly debeEnrolarMfa = this.enrolamientoPendiente.asReadonly();
  readonly autenticado = computed(() => {
    const s = this.sesionActual();
    return !!s && new Date(s.expiraEn).getTime() > Date.now();
  });

  get token(): string | null {
    return this.sesionActual()?.token ?? null;
  }

  /**
   * Primer paso del ingreso. **Va contra `/auth/mfa/desafio`, no contra
   * `/auth/login`**, y ese cambio no es cosmético: desde V37 una cuenta con
   * segundo factor activo **no entra** por `/auth/login` —responde 401 aunque
   * la contraseña sea correcta (D-S0-22)—, así que el camino viejo dejaría
   * fuera del SPA a quien acabara de enrolarse.
   *
   * El endpoint nuevo sirve a los dos casos con una sola llamada: **200** con
   * la sesión si la cuenta no tiene factor, **202** con un desafío si lo
   * tiene. Por eso hace falta mirar el `status` y no solo el cuerpo.
   *
   * Se descartó mandar usuario + contraseña + código de una vez: obligaría a
   * tener el código listo antes de saber si hace falta y **quemaría códigos
   * legítimos contra contraseñas mal escritas**.
   */
  async login(credenciales: LoginRequest): Promise<ResultadoIngreso> {
    const respuesta = await firstValueFrom(
      this.http.post<LoginResponse | DesafioMfa>(
        `${API_BASE_URL}/auth/mfa/desafio`,
        credenciales,
        { observe: 'response' },
      ),
    );
    if (respuesta.status === 202) {
      const desafio = respuesta.body as DesafioMfa;
      return { tipo: 'desafio', desafio: desafio.desafio, expiraEn: desafio.expiraEn };
    }
    return { tipo: 'sesion', sesion: await this.abrirSesion(respuesta.body as LoginResponse) };
  }

  /**
   * Segundo paso: canjea el desafío por la sesión. Admite un código TOTP **o**
   * uno de respaldo — el servidor decide cuál es sin que el cliente lo
   * declare, porque pedirle que lo distinga solo añade una forma de
   * equivocarse.
   */
  async verificarSegundoFactor(desafio: string, codigo: string): Promise<Sesion> {
    const respuesta = await firstValueFrom(
      this.http.post<LoginResponse>(`${API_BASE_URL}/auth/mfa/verificar`, { desafio, codigo }),
    );
    return this.abrirSesion(respuesta);
  }

  /**
   * Guardar la sesión son **dos** llamadas, y la segunda no es opcional.
   *
   * El `LoginResponse` está congelado: su `rol` solo admite `ADMIN | BROKER |
   * AGENTE`, y ese `ADMIN` es la banda heredada que dejó de existir. La banda
   * real la resuelve el servidor desde la membresía y se pide con
   * `GET /sesion`. Por eso el token se guarda **antes** de preguntar: sin él en
   * la señal, el interceptor no tendría con qué autenticar esa segunda llamada.
   *
   * Si `GET /sesion` falla, se cae a traducir el rol del cable, y esa
   * traducción **nunca concede gobierno** (`ADMIN` → `BROKER`): ante la duda,
   * menos permisos. El backend manda de todos modos —esto solo decide qué se
   * muestra—, así que equivocarse hacia abajo se ve como un menú corto y
   * equivocarse hacia arriba, como botones que responden 403.
   */
  private async abrirSesion(respuesta: LoginResponse): Promise<Sesion> {
    const sesion: Sesion = { ...respuesta, rol: sinGobierno(respuesta.rol) };
    guardarSesion(sesion);
    this.cerrandoSesion = false;
    this.sesionActual.set(sesion);

    const efectiva = await this.rolEfectivo();
    if (efectiva && efectiva !== sesion.rol) {
      const conBanda: Sesion = { ...sesion, rol: efectiva };
      guardarSesion(conBanda);
      this.sesionActual.set(conBanda);
      return conBanda;
    }
    return sesion;
  }

  /**
   * `GET /sesion` es la **primera** llamada de una sesión recién abierta, así
   * que es donde el backend dice si viene capada por segundo factor pendiente:
   * responde 403 con `ENROLAMIENTO_MFA_REQUERIDO` (D-S0-25). Se anota aquí y
   * no se deja para que lo descubra el interceptor porque el login navega
   * inmediatamente después, y sin el dato mandaría al panel — que respondería
   * 403 entero y rebotaría, con el parpadeo incluido.
   */
  private async rolEfectivo(): Promise<RolSesion | null> {
    try {
      const efectiva = await firstValueFrom(
        this.http.get<SesionEfectiva>(`${API_BASE_URL}/sesion`),
      );
      this.enrolamientoPendiente.set(false);
      return efectiva.rol;
    } catch (error) {
      const cuerpo = (error as HttpErrorResponse)?.error as { codigo?: string } | undefined;
      this.enrolamientoPendiente.set(cuerpo?.codigo === CODIGO_ENROLAMIENTO_REQUERIDO);
      return null;
    }
  }

  /**
   * Renueva la sesión presentando el token actual (`POST /auth/renovar`).
   *
   * Reutiliza `abrirSesion` a propósito: además de guardar el token nuevo,
   * vuelve a preguntar la banda efectiva. Es una llamada de más cada ~25
   * minutos y compra que un rol cambiado en el servidor se refleje en el menú
   * sin esperar a que el usuario vuelva a entrar.
   *
   * Quién decide *cuándo* llamar aquí es `RenovacionSesion`, no este servicio:
   * la política de inactividad es una decisión de producto y vive junto a su
   * explicación.
   */
  async renovar(): Promise<Sesion> {
    const respuesta = await firstValueFrom(
      this.http.post<LoginResponse>(`${API_BASE_URL}/auth/renovar`, {}),
    );
    return this.abrirSesion(respuesta);
  }

  /**
   * Salida deliberada del usuario (el botón "Salir"). Avisa al servidor
   * **antes** de limpiar, para que el token deje de valer de verdad: hasta
   * ahora cerrar sesión era solo borrar `localStorage` y el token seguía
   * siendo válido hasta expirar (D-S0-12).
   *
   * **Cierra todas las sesiones de la cuenta**, no solo esta pestaña: sesiones
   * individuales exigirían un `jti` que no cabe en el token congelado.
   *
   * El aviso es *best-effort*. Si la red falla o el token ya expiró, se limpia
   * igual: dejar al usuario atrapado en la aplicación porque el servidor no
   * contestó sería peor que un token que caduca solo en 30 minutos.
   */
  async salir(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>(`${API_BASE_URL}/auth/logout`, {}));
    } catch {
      // Se limpia igual; ver arriba.
    }
    // Sin recordar destino: quien sale a propósito y vuelve a entrar espera el
    // panel, no la pantalla que estaba mirando hace un rato.
    this.cerrarSesion(false);
  }

  /**
   * Cierre COMPLETO **local**: estado + almacenamiento antes de navegar.
   * (Lección del frontend Blazor: un 401 con limpieza parcial provocaba
   * un bucle infinito login↔dashboard.)
   *
   * No llama al servidor a propósito: este es también el camino del 401 del
   * interceptor, y ahí el token ya no vale — pedir el logout con él devolvería
   * otro 401 y realimentaría el mismo cierre.
   *
   * `recordarDestino` distingue los dos motivos por los que se llega aquí. Si
   * la sesión **caducó**, se anota la pantalla para devolver al usuario a ella
   * tras volver a entrar: perder el trabajo Y el sitio es dos castigos por un
   * despiste. Si el usuario **pulsó Salir**, no se anota nada.
   */
  cerrarSesion(recordarDestino = true): void {
    const destino = recordarDestino ? destinoSeguro(this.router.url) : null;
    this.olvidarSesionLocal();
    if (this.cerrandoSesion) {
      return;
    }
    this.cerrandoSesion = true;
    // El parámetro solo viaja cuando hay algo que recordar: mandar siempre un
    // `queryParams` vacío ensuciaría la URL del login del caso normal.
    void this.router.navigate(
      ['/login'],
      destino
        ? { queryParams: { [PARAM_DESTINO]: destino }, replaceUrl: true }
        : { replaceUrl: true },
    );
  }

  /**
   * Olvida la sesión **sin navegar**. Lo usa el cambio de contraseña: ahí el
   * token ya está muerto en el servidor —cambiar la clave invalida todas las
   * sesiones—, pero la pantalla tiene que poder decir que salió bien antes de
   * mandar al login. Navegar aquí se comería ese mensaje.
   *
   * Tampoco avisa al servidor, por lo mismo que `cerrarSesion`: el token ya no
   * vale y el logout devolvería un 401.
   */
  olvidarSesionLocal(): void {
    eliminarSesionGuardada();
    this.sesionActual.set(null);
    this.enrolamientoPendiente.set(false);
  }
}

function leerSesionGuardada(): Sesion | null {
  try {
    const crudo = localStorage.getItem(CLAVE_SESION);
    if (!crudo) {
      return null;
    }
    const sesion = JSON.parse(crudo) as Partial<Sesion>;
    if (
      typeof sesion.token !== 'string' ||
      typeof sesion.expiraEn !== 'string' ||
      !Number.isFinite(Date.parse(sesion.expiraEn)) ||
      Date.parse(sesion.expiraEn) <= Date.now()
    ) {
      eliminarSesionGuardada();
      return null;
    }
    return sesion as Sesion;
  } catch {
    eliminarSesionGuardada();
    return null;
  }
}

function guardarSesion(sesion: Sesion): void {
  try {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
  } catch {
    // El almacenamiento puede estar bloqueado; la sesión en memoria sigue siendo válida.
  }
}

function eliminarSesionGuardada(): void {
  try {
    localStorage.removeItem(CLAVE_SESION);
  } catch {
    // La señal se limpia igualmente aunque el navegador bloquee localStorage.
  }
}
