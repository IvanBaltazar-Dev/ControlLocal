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
 * La regla que hay que entender antes de tocar esto: en el backend las
 * operaciones sin gate de rol se limitan por alcance y tenant. En esas, lo que
 * limita no es el acceso sino el **alcance** — los tres roles entran y cada uno
 * recibe su porción—. Por eso la mayoría de los módulos de abajo son visibles
 * para todos: ocultarlos sería inventar una restricción que el backend no tiene.
 *
 * <h2>La taxonomía es de D-E3-1</h2>
 * El menú representa el trabajo mental del usuario, no las tablas del modelo.
 * De ahí salen las tres reglas que ordenan esta lista:
 *
 * <ol>
 *   <li><b>Una cola de trabajo no es una sección.</b> «Captaciones por revisar»
 *       es el mismo listado que Captaciones con un filtro de estado.</li>
 *   <li><b>El alcance no crea entradas.</b> «Cartera del equipo» es Propiedades
 *       con el alcance del broker: lo resuelve el backend, no el menú.</li>
 *   <li><b>La acción pertenece al objeto que modifica.</b> Reasignar un encargo
 *       se hace desde el encargo.</li>
 * </ol>
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
  /**
   * `false` = la pantalla existe y sigue protegida, pero **no aparece en el
   * menú** porque se llega a ella desde su superficie natural: un filtro, un
   * segmento o una acción.
   *
   * <b>Por qué sigue en esta lista si no se muestra.</b> El guard resuelve el
   * rol con `moduloDeRuta`, y una ruta sin módulo cae en «sin data explícita:
   * la autorización definitiva sigue en el backend» — es decir, pasa. Borrar
   * la fila para quitarla del menú abriría la pantalla a cualquier rol por URL,
   * que es lo contrario de lo que se pretendía.
   */
  readonly enMenu?: boolean;
}

export type Seccion = 'Inicio' | 'Oferta' | 'Demanda' | 'Cierre' | 'Gestión' | 'Organización' | 'Cuenta';

export const SECCIONES: readonly Seccion[] = [
  'Inicio',
  'Oferta',
  'Demanda',
  'Cierre',
  'Gestión',
  'Organización',
  'Cuenta',
];

/**
 * La sección que NO se dibuja en el lateral: la cuenta vive arriba a la
 * derecha, en el menú de la identidad. Está en el mapa para que el guard
 * siga protegiendo sus rutas y para que el menú de cuenta salga de la misma
 * fuente que el resto.
 */
export const SECCION_DE_CUENTA: Seccion = 'Cuenta';

/**
 * Los módulos, en el orden del menú. `roles` copia la columna Roles de la
 * matriz para la operación de ENTRADA de cada pantalla (su GET de listado):
 * lo que decide si el módulo se ve es poder listarlo, no poder editarlo.
 */
export const MODULOS: readonly Modulo[] = [
  // ------------------------------------------------------------------
  // Inicio — sin sección, arriba del todo. Es a lo que se vuelve para saber
  // qué resolver. El tablero ES la home (`/`): dos entradas al mismo sitio
  // serían ruido.
  // ------------------------------------------------------------------
  { ruta: '', etiqueta: 'Inicio', roles: 'TODOS', seccion: 'Inicio' },

  // ------------------------------------------------------------------
  // OFERTA — Propietario → Propiedad → Prospección → Captación.
  // ------------------------------------------------------------------

  // «Propiedades», rótulo Y ruta: con el modelo universal (D-E4-1) lo que hay
  // en cartera son propiedades de siete tipos, en venta y en alquiler.
  //
  // La canónica es `/propiedades`. Mantener `/locales` habría dejado el
  // producto diciendo una cosa en el menú y otra en la barra de direcciones,
  // que es exactamente el cambio cosmético que este corte vino a evitar.
  // `app.routes.ts` conserva cuatro redirects temporales para los enlaces ya
  // guardados.
  { ruta: 'propiedades', etiqueta: 'Propiedades', roles: 'TODOS', seccion: 'Oferta' },
  {
    ruta: 'propietarios',
    etiqueta: 'Propietarios',
    roles: 'TODOS',
    seccion: 'Oferta',
  },
  {
    ruta: 'prospecciones',
    etiqueta: 'Prospecciones',
    roles: 'TODOS',
    seccion: 'Oferta',
  },
  {
    ruta: 'captaciones',
    etiqueta: 'Captaciones',
    roles: 'TODOS',
    seccion: 'Oferta',
  },

  // La cartera del equipo es ALCANCE, no un módulo: el mismo listado de
  // Propiedades resuelto con el alcance del broker. Se llega desde Propiedades.
  {
    ruta: 'propiedades-equipo',
    etiqueta: 'Cartera del equipo',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Oferta',
    enMenu: false,
  },
  // La cola de revisión es Captaciones filtrada por estado. Se llega desde
  // Captaciones, con su contador a la vista para el broker.
  {
    ruta: 'captaciones/pendientes',
    etiqueta: 'Captaciones por revisar',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Oferta',
    enMenu: false,
  },
  // Reasignar es una ACCIÓN sobre un encargo, y su historial pertenece a la
  // misma superficie. Se llega desde Captaciones.
  {
    ruta: 'captaciones/reasignaciones',
    etiqueta: 'Reasignaciones',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Oferta',
    enMenu: false,
  },

  // ------------------------------------------------------------------
  // DEMANDA — Cliente → Requerimiento → Oportunidad → Visita.
  // ------------------------------------------------------------------
  // Clientes es catálogo COMPARTIDO (TENANT_ADMIN y AGENTE ven todo); el único
  // acotado es el BROKER, vía las oportunidades de su equipo.
  { ruta: 'clientes', etiqueta: 'Clientes', roles: 'TODOS', seccion: 'Demanda' },
  {
    ruta: 'oportunidades',
    etiqueta: 'Oportunidades',
    roles: 'TODOS',
    seccion: 'Demanda',
  },
  { ruta: 'visitas', etiqueta: 'Visitas', roles: 'TODOS', seccion: 'Demanda' },
  // Interacciones se conserva como entrada. D-E3-1 §4 propone llevarla dentro
  // del expediente, pero la marca como una de las tres uniones «que hay que
  // mirar dos veces» y que **no se ejecutan sin decidirlas aparte**.
  {
    ruta: 'interacciones',
    etiqueta: 'Interacciones',
    roles: 'TODOS',
    seccion: 'Demanda',
  },

  // ------------------------------------------------------------------
  // CIERRE — Solicitud → Evaluación → Contrato → Comisión.
  // ------------------------------------------------------------------
  // «Solicitudes», no «Expedientes»: el expediente es la PROFUNDIDAD de
  // navegación de una solicitud (`/solicitudes/:codigo`), no el nombre del
  // objeto. El mapa pantalla↔dominio distingue bandeja de expediente, y el
  // menú nombra la bandeja. Si el detalle quiere titularse «Expediente de
  // solicitud», ese es su sitio.
  {
    ruta: 'solicitudes',
    etiqueta: 'Solicitudes',
    roles: 'TODOS',
    seccion: 'Cierre',
  },
  // La cola de evaluación es Solicitudes filtrada. Lleva gate no por su
  // listado —que es el mismo `GET /solicitudes` sin gate— sino por la decisión
  // a la que conduce: desde D-S0-17 `POST /evaluaciones` es de BROKER a secas.
  {
    ruta: 'solicitudes/revisar',
    etiqueta: 'Solicitudes por revisar',
    roles: ['BROKER'],
    seccion: 'Cierre',
    enMenu: false,
  },
  // «Contratos»: lo firmado. Lo ven los tres roles, cada uno con su alcance.
  {
    ruta: 'propiedades-alquiladas',
    etiqueta: 'Contratos',
    roles: 'TODOS',
    seccion: 'Cierre',
  },
  // Liquidación: la ven los tres con su alcance, pero las tres operaciones son
  // de BROKER **sin el admin**, así que al administrador se le muestra solo la
  // lectura.
  {
    ruta: 'comisiones',
    etiqueta: 'Comisiones',
    roles: 'TODOS',
    seccion: 'Cierre',
  },

  // ------------------------------------------------------------------
  // GESTIÓN — cómo va el negocio.
  // ------------------------------------------------------------------
  {
    ruta: 'indicadores',
    etiqueta: 'Indicadores',
    roles: 'TODOS',
    seccion: 'Gestión',
  },
  // RF-017: el avance por propiedad. D-E3-1 §4 propone unirlo a Indicadores
  // como pestaña; es la tercera de las uniones que no se ejecutan sin
  // decidirlas aparte, así que sigue siendo entrada.
  {
    ruta: 'reportes',
    etiqueta: 'Reportes',
    roles: 'TODOS',
    seccion: 'Gestión',
  },
  {
    ruta: 'seguimiento-comercial',
    etiqueta: 'Seguimiento',
    roles: 'TODOS',
    seccion: 'Gestión',
  },

  // ------------------------------------------------------------------
  // ORGANIZACIÓN — quién es quién. Cambia por rol, no por alcance.
  // ------------------------------------------------------------------
  // El catálogo lo ven los dos; dar de alta y editar es solo del TENANT_ADMIN,
  // y eso lo decide la pantalla, no el menú.
  {
    ruta: 'agentes',
    etiqueta: 'Agentes',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Organización',
  },
  // El backend deja leer `/brokers` a los tres roles (matriz, fila 272): es un
  // catálogo por tenant y no expone nada sensible. Pero **poder** no es lo
  // mismo que **tener sentido**: un agente no gestiona brokers, no los da de
  // alta y no los supervisa.
  //
  // Se restringe el MENÚ y la ruta; la lectura del API se conserva porque de
  // ella viven el filtro «por broker supervisor» y la ficha del equipo.
  {
    ruta: 'brokers',
    etiqueta: 'Brokers',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Organización',
  },
  // «Mi equipo» es ALCANCE del broker sobre Agentes, no un módulo aparte.
  {
    ruta: 'mi-equipo',
    etiqueta: 'Mi equipo',
    roles: ['BROKER'],
    seccion: 'Organización',
    enMenu: false,
  },
  {
    ruta: 'asignaciones',
    etiqueta: 'Asignaciones',
    roles: ['TENANT_ADMIN'],
    seccion: 'Organización',
  },
  // Gobierno de accesos: padrón de cuentas + aviso persistente de lo que se ha
  // hecho con ellos. `GET /accesos` y `GET /seguridad/avisos` son TENANT_ADMIN
  // en la matriz, así que el módulo lo es también.
  {
    ruta: 'seguridad',
    etiqueta: 'Seguridad y accesos',
    roles: ['TENANT_ADMIN'],
    seccion: 'Organización',
  },

  // ------------------------------------------------------------------
  // PIE — la cuenta, no el trabajo. Se dibuja abajo, separado.
  // ------------------------------------------------------------------
  // ------------------------------------------------------------------
  // CUENTA — no se dibuja en el lateral: vive arriba a la derecha, en el menú
  // de la identidad, que es donde el usuario ya la busca. Repetirla abajo la
  // ponía dos veces en la misma pantalla.
  //
  // Son DOS cosas, y las dos existen: el perfil —que ya contiene teléfono,
  // foto, contraseña y segundo factor— y cerrar sesión. No hay una
  // «Configuración» aquí porque no hay ninguna configuración de usuario que
  // no esté ya dentro del perfil; inventarla para rellenar el menú sería
  // ofrecer una pantalla vacía.
  //
  // «Seguridad y accesos» NO está aquí: es gobierno del tenant —cuentas de
  // otros— y pertenece a Organización, para quien tenga permiso.
  //
  // Y «Catálogos del sistema» se eliminó del SPA (2026-08-18): era una
  // pantalla TÉCNICA —el volcado de `core/api/codigos.ts`, sin endpoint
  // detrás— que enseñaba al usuario los códigos de una letra del cable. Los
  // catálogos siguen existiendo en el dominio como fuente de verdad interna;
  // lo que desaparece es exponerlos como si fueran una capacidad del negocio.
  // No se ocultó del menú: se borraron entrada, ruta y componente, de modo que
  // tampoco se abre escribiendo la URL.
  // ------------------------------------------------------------------
  { ruta: 'perfil', etiqueta: 'Mi perfil', roles: 'TODOS', seccion: 'Cuenta' },
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
 * Módulos que se DIBUJAN en el menú para un rol: los que puede entrar y no
 * están marcados `enMenu: false`.
 */
export function modulosDelMenu(rol: RolSesion | undefined): Modulo[] {
  return modulosDe(rol).filter((modulo) => modulo.enMenu !== false);
}

/**
 * Módulos del menú agrupados por sección, saltando las que quedan vacías para
 * ese rol (a un AGENTE, por ejemplo, no le aparece Organización).
 */
export function menuDe(rol: RolSesion | undefined): { seccion: Seccion; modulos: Modulo[] }[] {
  const visibles = modulosDelMenu(rol);
  return SECCIONES.map((seccion) => ({
    seccion,
    modulos: visibles.filter((modulo) => modulo.seccion === seccion),
  })).filter((grupo) => grupo.modulos.length > 0);
}

/** La navegación del producto: lo que se dibuja en el lateral. */
export function cuerpoDelMenu(rol: RolSesion | undefined): { seccion: Seccion; modulos: Modulo[] }[] {
  return menuDe(rol).filter((grupo) => grupo.seccion !== SECCION_DE_CUENTA);
}

/**
 * La cuenta: lo que se dibuja en el menú de la identidad, arriba a la derecha.
 * «Cerrar sesión» no está aquí porque no es una ruta — es una acción, y la
 * pone el propio menú al final, separada.
 */
export function cuentaDe(rol: RolSesion | undefined): Modulo[] {
  return modulosDelMenu(rol).filter((modulo) => modulo.seccion === SECCION_DE_CUENTA);
}

/** Busca el módulo que cubre una ruta, para el guard. */
export function moduloDeRuta(ruta: string): Modulo | undefined {
  const limpia = ruta.replace(/^\/+/, '');
  return MODULOS.find((modulo) => modulo.ruta === limpia);
}

/**
 * Lo que `+ Registrar` ofrece, según el rol.
 *
 * <b>No duplica módulos.</b> El sidebar sirve para entrar al trabajo; esto
 * sirve para crear, y por eso lista ALTAS y no secciones. Cada opción apunta a
 * la pantalla de alta que ya existe; cuando el registro universal esté hecho,
 * «Propiedad» apuntará a él sin que cambie nada aquí.
 *
 * Las opciones salen de los mismos roles que el `data.roles` de su ruta, así
 * que el botón nunca ofrece un alta que va a terminar en acceso denegado.
 */
export interface Alta {
  readonly ruta: string;
  readonly etiqueta: string;
  readonly descripcion: string;
  readonly roles: readonly RolSesion[] | 'TODOS';
}

export const ALTAS: readonly Alta[] = [
  {
    ruta: 'propiedades/nueva',
    etiqueta: 'Propiedad',
    descripcion: 'Un inmueble para vender o alquilar',
    roles: ['AGENTE'],
  },
  {
    ruta: 'propietarios/nuevo',
    etiqueta: 'Propietario',
    descripcion: 'Quien es dueño de una propiedad',
    roles: ['AGENTE'],
  },
  {
    ruta: 'clientes/nuevo',
    etiqueta: 'Cliente',
    descripcion: 'Quien busca comprar o alquilar',
    roles: ['AGENTE'],
  },
  {
    ruta: 'agentes/nuevo',
    etiqueta: 'Agente',
    descripcion: 'Una cuenta de agente inmobiliario',
    roles: ['TENANT_ADMIN'],
  },
  {
    ruta: 'brokers/nuevo',
    etiqueta: 'Broker',
    descripcion: 'Una cuenta de broker',
    roles: ['TENANT_ADMIN'],
  },
];

/** Las altas que este rol puede hacer. Vacío = el botón no se dibuja. */
export function altasDe(rol: RolSesion | undefined): Alta[] {
  if (!rol) {
    return [];
  }
  return ALTAS.filter((alta) => alta.roles === 'TODOS' || alta.roles.includes(rol));
}
