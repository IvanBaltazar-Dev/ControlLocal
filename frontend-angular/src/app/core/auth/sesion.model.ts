/** Contratos contextuales emitidos por Core. Ningún rol se deduce del token legado. */
export interface LoginRequest { correo: string; contrasena: string; }
export type RolOperativo = 'TENANT_ADMIN' | 'BROKER' | 'AGENTE';
export type RolSesion = RolOperativo | 'IDENTIDAD';

/** Referencia ofrecida por Core para la selección en superficie segura. */
export interface ContextoAcceso {
  referencia: string;
  idOrganizacion: number;
  idPersona: number;
  idRolOperativo: number;
  rol: RolOperativo;
  modalidad: string;
  nombreOrganizacion: string;
}

export interface LoginResponse {
  token: string;
  expiraEnSegundos: number;
  rol: RolSesion;
  /** Persona del contexto; en IDENTIDAD no concede acceso organizacional. */
  idUsuario: number;
  idDominio: number;
  nombre: string;
  usuario: string;
  expiraEn: string;
  contextoRequerido: boolean;
  contextos: ContextoAcceso[];
  idOrganizacion: number;
  idCuenta: number;
  revisionContexto: number;
  modalidad: string | null;
}

/** Un desafío MFA acredita únicamente el primer paso; todavía no es una sesión. */
export interface DesafioMfa { desafio: string; expiraEn: string; metodo: string; }

export interface SesionEfectiva {
  rol: RolSesion;
  usuario: string;
  idPersona: number;
  idDominio: number;
  idOrganizacion: number;
  idCuenta: number;
  referenciaSesion: string;
  revisionContexto: number;
  contextoRef: string | null;
  modalidad: string | null;
  contextoRequerido: boolean;
  debeCambiarContrasena: boolean;
  debeEnrolarMfa: boolean;
}

export interface Sesion extends LoginResponse {
  /** No se inventa esta referencia antes de consultar GET /sesion. */
  referenciaSesion: string | null;
  contextoRef: string | null;
}

export const ETIQUETA_ROL: Record<RolSesion, string> = {
  IDENTIDAD: 'Seleccionar contexto',
  TENANT_ADMIN: 'Administrador de la organización',
  BROKER: 'Broker supervisor',
  AGENTE: 'Agente inmobiliario',
};
