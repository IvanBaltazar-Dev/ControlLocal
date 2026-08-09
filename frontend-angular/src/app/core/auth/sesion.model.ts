/** Contrato congelado del backend (LoginRequest/LoginResponse). */

export interface LoginRequest {
  usuario: string;
  contrasena: string;
}

/**
 * Banda **efectiva** del actor, la que decide qué ve y qué puede hacer.
 *
 * No es la que viene en el login. El `LoginResponse` está congelado y su `rol`
 * solo admite `ADMIN | BROKER | AGENTE` mientras GlassFish conviva; ese `ADMIN`
 * es la banda heredada —un broker con un booleano— que el Bloque 5 retiró.
 * `TENANT_ADMIN` gobierna la organización y **no opera** en el proceso
 * comercial: no aprueba captaciones, no las cierra, no conforma documentos y no
 * evalúa solicitudes.
 *
 * La resuelve el servidor y se pide con `GET /sesion`. Aquí abajo ya no queda
 * rastro del valor del cable: `AuthService` lo traduce al entrar.
 */
export type RolSesion = 'TENANT_ADMIN' | 'BROKER' | 'AGENTE';

/** Lo que responde `POST /auth/login`, tal cual y sin traducir. */
export interface LoginResponse {
  token: string;
  expiraEnSegundos: number;
  /** Banda del cable congelado; `ADMIN` aquí NO significa `TENANT_ADMIN`. */
  rol: 'ADMIN' | 'BROKER' | 'AGENTE';
  idUsuario: number;
  idDominio: number;
  nombre: string;
  usuario: string;
  expiraEn: string;
}

/**
 * Cuerpo del **202** de `POST /auth/mfa/desafio` (aditivo, V37): la contraseña
 * era correcta y la cuenta tiene segundo factor, así que todavía no hay sesión.
 * **Un desafío no autoriza nada** — es un vale de cinco minutos para canjear
 * con un código en `POST /auth/mfa/verificar`.
 */
export interface DesafioMfa {
  desafio: string;
  expiraEn: string;
  metodo: string;
}

/** Lo que responde `GET /sesion` (aditivo). */
export interface SesionEfectiva {
  rol: RolSesion;
  usuario: string;
  idPersona: number;
  idDominio: number;
}

export interface Sesion {
  token: string;
  expiraEnSegundos: number;
  rol: RolSesion;
  /** v2: id de la persona (identidad única del actor). */
  idUsuario: number;
  /** v2: id del rol operativo (broker/agente). */
  idDominio: number;
  nombre: string;
  usuario: string;
  /** ISO local datetime que emite el backend. */
  expiraEn: string;
}

export const ETIQUETA_ROL: Record<RolSesion, string> = {
  // Ya no es "Broker administrador": administrar dejó de ser una variedad de
  // broker. Quien gobierna la corredora no capta, no decide encargos y no firma
  // evaluaciones — llamarlo broker prometería en pantalla lo que el backend
  // responde con 403.
  TENANT_ADMIN: 'Administrador de la corredora',
  BROKER: 'Broker supervisor',
  AGENTE: 'Agente inmobiliario',
};
