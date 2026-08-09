import { RolSesion } from './sesion.model';

/**
 * Qué módulo ve cada rol. Equivale al `RouteAccess.cs` del Blazor y se deriva
 * de `docs/ai/matriz-operacion-rol.md`, que es la fuente de verdad del backend
 * (y está cubierta por `MatrizOperacionRolTest`).
 *
 * <b>Esto NO es autorización.</b> La autorización real la impone el backend en
 * cada request; aquí solo se decide qué se muestra, para no ofrecer al usuario
 * botones que van a responder 403. Si los dos discrepan, manda el backend.
 *
 * La regla que hay que entender antes de tocar esto: en el backend
 * Las operaciones sin gate de rol se limitan por alcance y tenant. En esas,
 * lo que limita no es
 * el acceso sino el **alcance** — los tres roles entran y cada uno recibe su
 * porción—. Por eso la mayoría de los módulos de abajo son visibles para todos:
 * ocultarlos sería inventar una restricción que el backend no tiene.
 */

/** Un módulo del menú y quién puede entrar. */
export interface Modulo {
  /** Ruta del router, sin la barra inicial. */
  readonly ruta: string;
  readonly etiqueta: string;
  /** Roles que pueden entrar. `TODOS` = cualquier sesión autenticada. */
  readonly roles: readonly RolSesion[] | 'TODOS';
  /** Agrupador del menú lateral. */
  readonly seccion: Seccion;
}

export type Seccion = 'Panel' | 'Oferta' | 'Proceso' | 'Demanda' | 'Cierre' | 'Gestión';

export const SECCIONES: readonly Seccion[] = [
  'Panel',
  'Oferta',
  'Proceso',
  'Demanda',
  'Cierre',
  'Gestión',
];

/**
 * Los módulos, en el orden del menú. `roles` copia la columna Roles de la
 * matriz para la operación de ENTRADA de cada pantalla (su GET de listado):
 * lo que decide si el módulo se ve es poder listarlo, no poder editarlo.
 */
export const MODULOS: readonly Modulo[] = [
  // Panel — los tres agregadores de E4 no llevan gate: cambia el alcance y el
  // `ambito`, no el acceso. La bandeja del dashboard llega VACÍA para
  // BROKER/TENANT_ADMIN (sale de /tareas, que es solo del agente), y eso no es un 403.
  // El dashboard ES la home (`/`), no una pantalla aparte: dos entradas de menú
  // al mismo tablero serían ruido.
  { ruta: '', etiqueta: 'Dashboard', roles: 'TODOS', seccion: 'Panel' },
  // La bandeja del agente vive DENTRO del dashboard, no en una página-silo: es
  // lo primero que se ve al entrar, y sacarla a su propia ruta la escondería
  // detrás de un clic. `/tareas` sigue siendo el recurso; lo que no hay es
  // pantalla propia.

  // Oferta — el catálogo de locales es de toda la organización. El alcance
  // acotado al agente ("los locales de SUS captaciones", RF-004) no es un
  // módulo aparte sino un filtro dentro de Locales: como entrada de menú era
  // una pantalla-silo sobre el mismo listado, y mientras no se migró fue texto
  // muerto que el agente no podía pulsar.
  { ruta: 'locales', etiqueta: 'Locales', roles: 'TODOS', seccion: 'Oferta' },
  {
    ruta: 'propietarios',
    etiqueta: 'Propietarios',
    roles: 'TODOS',
    seccion: 'Oferta',
  },
  // La cartera del equipo es supervisión: mismos roles que el resto de
  // endpoints de supervisión del v2 (`/captaciones/pendientes`,
  // `/reasignables`) y que el gate de `/captaciones/propiedades-equipo`.
  {
    ruta: 'propiedades-equipo',
    etiqueta: 'Cartera del equipo',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Proceso',
  },

  // Proceso F2.
  {
    ruta: 'prospecciones',
    etiqueta: 'Prospecciones',
    roles: 'TODOS',
    seccion: 'Proceso',
  },
  {
    ruta: 'captaciones',
    etiqueta: 'Captaciones',
    roles: 'TODOS',
    seccion: 'Proceso',
  },
  {
    ruta: 'captaciones/pendientes',
    etiqueta: 'Captaciones por revisar',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Proceso',
  },
  {
    ruta: 'captaciones/reasignaciones',
    etiqueta: 'Reasignaciones',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Proceso',
  },

  // Demanda F3 — clientes es catálogo COMPARTIDO (TENANT_ADMIN y AGENTE ven todo);
  // el único acotado es el BROKER, vía las oportunidades de su equipo.
  { ruta: 'clientes', etiqueta: 'Clientes', roles: 'TODOS', seccion: 'Demanda' },
  {
    ruta: 'oportunidades',
    etiqueta: 'Oportunidades',
    roles: 'TODOS',
    seccion: 'Demanda',
  },
  { ruta: 'visitas', etiqueta: 'Visitas', roles: 'TODOS', seccion: 'Demanda' },
  {
    ruta: 'interacciones',
    etiqueta: 'Interacciones',
    roles: 'TODOS',
    seccion: 'Demanda',
  },

  // Cierre F4 — el listado no lleva gate (los tres roles entran y cambia el
  // alcance: el BROKER alcanza por AGENTE SUPERVISADO, no por captación).
  {
    ruta: 'solicitudes',
    etiqueta: 'Solicitudes',
    roles: 'TODOS',
    seccion: 'Cierre',
  },
  // La cola de revisión SÍ lleva gate, y no por su listado —que es el mismo
  // `GET /solicitudes` sin gate— sino por la decisión a la que conduce.
  // Desde D-S0-17 (fila 13) `POST /evaluaciones` es de BROKER **a secas**: el
  // TENANT_ADMIN puede auditar lo firmado (`GET /evaluaciones`) pero no firmar,
  // así que ofrecerle la cola sería prometerle un 403 al final del camino.
  {
    ruta: 'solicitudes/revisar',
    etiqueta: 'Solicitudes por revisar',
    roles: ['BROKER'],
    seccion: 'Cierre',
  },
  // Cierres exitosos: lo ven los tres roles, cada uno con su alcance (el
  // AGENTE los suyos, el BROKER por captación supervisada, el TENANT_ADMIN el tenant).
  {
    ruta: 'propiedades-alquiladas',
    etiqueta: 'Cierres exitosos',
    roles: 'TODOS',
    seccion: 'Cierre',
  },

  // Liquidación de comisiones: la ven los tres roles con su alcance, pero las
  // tres operaciones son de BROKER **sin el admin** (así está el gate en el
  // cable), así que al administrador se le muestra solo la lectura.
  {
    ruta: 'comisiones',
    etiqueta: 'Comisiones',
    roles: 'TODOS',
    seccion: 'Cierre',
  },

  // Gestión.
  {
    ruta: 'seguimiento-comercial',
    etiqueta: 'Seguimiento',
    roles: 'TODOS',
    seccion: 'Gestión',
  },
  {
    ruta: 'indicadores',
    etiqueta: 'Indicadores',
    roles: 'TODOS',
    seccion: 'Gestión',
  },
  // RF-017: el avance por propiedad. Su endpoint existe en la v1 pero **ningún
  // `.razor` lo consume**; esta es su primera pantalla.
  {
    ruta: 'reportes',
    etiqueta: 'Reportes',
    roles: 'TODOS',
    seccion: 'Gestión',
  },
  // El catálogo lo ven los dos (filas 14-16); dar de alta y editar es solo del
  // TENANT_ADMIN (filas 17-18), y eso lo decide la pantalla, no el menú.
  {
    ruta: 'agentes',
    etiqueta: 'Agentes',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Gestión',
  },
  { ruta: 'brokers', etiqueta: 'Brokers', roles: 'TODOS', seccion: 'Gestión' },
  // "Mi equipo" es del BROKER: muestra SU equipo, resuelto por el rol de la
  // sesión. El TENANT_ADMIN no supervisa a nadie —gobernar no es operar— y su
  // vista del organigrama completo es Asignaciones, así que no se le ofrece.
  {
    ruta: 'mi-equipo',
    etiqueta: 'Mi equipo',
    roles: ['BROKER'],
    seccion: 'Gestión',
  },
  {
    ruta: 'asignaciones',
    etiqueta: 'Asignaciones',
    roles: ['TENANT_ADMIN'],
    seccion: 'Gestión',
  },
  // Gobierno de accesos: padrón de cuentas + aviso persistente de lo que se ha
  // hecho con ellos. `GET /accesos` y `GET /seguridad/avisos` son TENANT_ADMIN
  // en la matriz, así que el módulo lo es también.
  {
    ruta: 'seguridad',
    etiqueta: 'Seguridad y accesos',
    roles: ['TENANT_ADMIN'],
    seccion: 'Gestión',
  },
  // Solo consulta y sin endpoint detrás: sale de `core/api/codigos.ts`, la
  // misma fuente que leen las pantallas.
  {
    ruta: 'catalogos',
    etiqueta: 'Catálogos',
    roles: 'TODOS',
    seccion: 'Gestión',
  },
  { ruta: 'perfil', etiqueta: 'Mi perfil', roles: 'TODOS', seccion: 'Gestión' },
];

/** ¿Puede este rol entrar al módulo? */
export function puedeEntrar(modulo: Modulo, rol: RolSesion | undefined): boolean {
  if (!rol) {
    return false;
  }
  return modulo.roles === 'TODOS' || modulo.roles.includes(rol);
}

/** Módulos visibles para un rol, en el orden declarado. */
export function modulosDe(rol: RolSesion | undefined): Modulo[] {
  return MODULOS.filter((modulo) => puedeEntrar(modulo, rol));
}

/**
 * Módulos visibles agrupados por sección, saltando las secciones que quedan
 * vacías para ese rol (al ADMIN, por ejemplo, no le aparece "Mis tareas").
 */
export function menuDe(rol: RolSesion | undefined): { seccion: Seccion; modulos: Modulo[] }[] {
  const visibles = modulosDe(rol);
  return SECCIONES.map((seccion) => ({
    seccion,
    modulos: visibles.filter((modulo) => modulo.seccion === seccion),
  })).filter((grupo) => grupo.modulos.length > 0);
}

/** Busca el módulo que cubre una ruta, para el guard. */
export function moduloDeRuta(ruta: string): Modulo | undefined {
  const limpia = ruta.replace(/^\/+/, '');
  return MODULOS.find((modulo) => modulo.ruta === limpia);
}
