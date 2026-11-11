import type { NombreIcono } from '../../shared/icono/icono';
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
 * C1 / CAT-E01: gobierno no concede consulta comercial. TENANT_ADMIN usa
 * los directorios y las actuaciones de gobierno; AGENTE y BROKER conservan
 * sus superficies comerciales dentro del alcance que Core autoriza.
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
 *   <li><b>La acción pertenece al objeto que modifica.</b> El broker reasigna
 *       desde el encargo; gobierno accede a su actuación mínima en Organización.</li>
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
   * El icono de la entrada. **Obligatorio**, también en las que no se dibujan:
   * antes había un punto de 6 px idéntico para las 26 entradas, y un menú así
   * no se lee de un vistazo ni sobrevive al modo estrecho (D-E3-1 §6.1).
   *
   * Se toman de `shared/icono`, que son los del prototipo. Que dos entradas de
   * personas compartan `persona` no es descuido: lo que las distingue es la
   * sección, y forzar un dibujo distinto por entrada inventaría símbolos.
   */
  readonly icono: NombreIcono;
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
  /** Excepción de presentación para un rol que no entra por el objeto comercial. */
  readonly enMenuPara?: readonly RolSesion[];
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

const ROLES_COMERCIALES: readonly RolSesion[] = ['AGENTE', 'BROKER'];

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
  { ruta: '', etiqueta: 'Inicio', roles: ROLES_COMERCIALES, seccion: 'Inicio', icono: 'pulso' },

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
  { ruta: 'propiedades', etiqueta: 'Propiedades', roles: ROLES_COMERCIALES, seccion: 'Oferta', icono: 'mapa' },
  {
    ruta: 'propietarios',
    etiqueta: 'Propietarios',
    roles: ROLES_COMERCIALES,
    seccion: 'Oferta',
    icono: 'persona',
  },
  {
    ruta: 'prospecciones',
    etiqueta: 'Prospecciones',
    roles: ROLES_COMERCIALES,
    seccion: 'Oferta',
    icono: 'lupa',
  },
  {
    ruta: 'captaciones',
    etiqueta: 'Captaciones',
    roles: ROLES_COMERCIALES,
    seccion: 'Oferta',
    icono: 'megafono',
  },

  // La cartera del equipo es ALCANCE, no un módulo: el mismo listado de
  // Propiedades resuelto con el alcance del broker. Se llega desde Propiedades.
  {
    ruta: 'propiedades-equipo',
    etiqueta: 'Cartera del equipo',
    roles: ['BROKER'],
    seccion: 'Oferta',
    icono: 'mapa',
    enMenu: false,
  },
  // La cola de revisión es Captaciones filtrada por estado. Se llega desde
  // Captaciones, con su contador a la vista para el broker.
  {
    ruta: 'captaciones/pendientes',
    etiqueta: 'Captaciones por revisar',
    roles: ['BROKER'],
    seccion: 'Oferta',
    icono: 'megafono',
    enMenu: false,
  },
  // El broker llega desde Captaciones. Gobierno sólo ve el caso mínimo de
  // reasignación: requiere una entrada propia sin abrir la cartera comercial.
  {
    ruta: 'captaciones/reasignaciones',
    etiqueta: 'Reasignaciones',
    roles: ['BROKER', 'TENANT_ADMIN'],
    seccion: 'Organización',
    icono: 'megafono',
    enMenu: false,
    enMenuPara: ['TENANT_ADMIN'],
  },

  // ------------------------------------------------------------------
  // DEMANDA — Cliente → Requerimiento → Oportunidad → Visita.
  // ------------------------------------------------------------------
  // Los contactos comerciales se consultan mediante el alcance autorizado en Core.
  { ruta: 'clientes', etiqueta: 'Clientes', roles: ROLES_COMERCIALES, seccion: 'Demanda', icono: 'persona' },
  {
    ruta: 'oportunidades',
    etiqueta: 'Oportunidades',
    roles: ROLES_COMERCIALES,
    seccion: 'Demanda',
    icono: 'diana',
  },
  { ruta: 'visitas', etiqueta: 'Visitas', roles: ROLES_COMERCIALES, seccion: 'Demanda', icono: 'cal' },
  // La conversación pertenece al EXPEDIENTE, no al menú (D-E3-1 §4.1, unión
  // ejecutada). Había dos superficies para lo mismo: esta bandeja y la
  // bitácora del cliente (`/clientes/:id/contacto`), a la que ya se llega
  // desde la ficha del cliente y desde su oportunidad.
  //
  // La bandeja **no se borra**: sigue respondiendo «qué contactos hice» y se
  // alcanza desde Seguimiento, que es la superficie transversal. Sus rutas
  // (`interacciones`, `interacciones/:id`, `interacciones/nueva`) se conservan
  // enteras para que ningún enlace guardado se rompa.
  {
    ruta: 'interacciones',
    etiqueta: 'Interacciones',
    roles: ROLES_COMERCIALES,
    seccion: 'Demanda',
    icono: 'chispa',
    enMenu: false,
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
    roles: ROLES_COMERCIALES,
    seccion: 'Cierre',
    icono: 'doc',
  },
  // La cola de evaluación es Solicitudes filtrada. Lleva gate no por su
  // listado —que es el mismo `GET /solicitudes` sin gate— sino por la decisión
  // a la que conduce: desde D-S0-17 `POST /evaluaciones` es de BROKER a secas.
  {
    ruta: 'solicitudes/revisar',
    etiqueta: 'Solicitudes por revisar',
    roles: ['BROKER'],
    seccion: 'Cierre',
    icono: 'doc',
    enMenu: false,
  },
  // «Contratos»: lo firmado, dentro del alcance comercial autorizado.
  {
    ruta: 'propiedades-alquiladas',
    etiqueta: 'Contratos',
    roles: ROLES_COMERCIALES,
    seccion: 'Cierre',
    icono: 'firma',
  },
  // La lectura comercial de liquidación conserva el alcance del agente/broker.
  {
    ruta: 'comisiones',
    etiqueta: 'Comisiones',
    roles: ROLES_COMERCIALES,
    seccion: 'Cierre',
    icono: 'moneda',
  },

  // ------------------------------------------------------------------
  // GESTIÓN — cómo va el negocio.
  // ------------------------------------------------------------------
  {
    ruta: 'indicadores',
    etiqueta: 'Indicadores',
    roles: ROLES_COMERCIALES,
    seccion: 'Gestión',
    icono: 'graf',
  },
  // RF-017, el avance por propiedad: es una PESTAÑA de Indicadores, no una
  // sección (D-E3-1 §4.3, unión ejecutada). Indicadores mide por actor y esto
  // por inmueble: la misma pregunta analítica por dos ejes.
  //
  // La ruta se conserva —cada pestaña mantiene su URL, para que un enlace
  // guardado siga abriendo la que se guardó— y lo que desaparece es la entrada
  // de menú.
  {
    ruta: 'reportes',
    etiqueta: 'Avance por propiedad',
    roles: ROLES_COMERCIALES,
    seccion: 'Gestión',
    icono: 'graf',
    enMenu: false,
  },
  {
    ruta: 'seguimiento-comercial',
    etiqueta: 'Seguimiento',
    roles: ROLES_COMERCIALES,
    seccion: 'Gestión',
    icono: 'reloj',
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
    icono: 'persona',
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
    icono: 'persona',
  },
  // «Mi equipo» es ALCANCE del broker sobre Agentes, no un módulo aparte.
  {
    ruta: 'mi-equipo',
    etiqueta: 'Mi equipo',
    roles: ['BROKER'],
    seccion: 'Organización',
    icono: 'persona',
    enMenu: false,
  },
  {
    ruta: 'asignaciones',
    etiqueta: 'Asignaciones',
    roles: ['TENANT_ADMIN'],
    seccion: 'Organización',
    icono: 'rombo',
  },
  // Gobierno de accesos: padrón de cuentas + aviso persistente de lo que se ha
  // hecho con ellos. `GET /accesos` y `GET /seguridad/avisos` son TENANT_ADMIN
  // en la matriz, así que el módulo lo es también.
  {
    ruta: 'seguridad',
    etiqueta: 'Seguridad y accesos',
    roles: ['TENANT_ADMIN'],
    seccion: 'Organización',
    icono: 'freno',
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
  { ruta: 'perfil', etiqueta: 'Mi perfil', roles: 'TODOS', seccion: 'Cuenta', icono: 'persona' },
];

/** ¿Puede este rol entrar al módulo? */
export function puedeEntrar(modulo: Modulo, rol: RolSesion | undefined): boolean {
  if (!rol || rol === 'IDENTIDAD') {
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
 * están marcados `enMenu: false`, salvo la presentación explícita para su rol.
 */
export function modulosDelMenu(rol: RolSesion | undefined): Modulo[] {
  return modulosDe(rol).filter((modulo) =>
    modulo.enMenu !== false || (!!rol && modulo.enMenuPara?.includes(rol)),
  );
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
    descripcion: 'Invitar a un agente a la organización',
    roles: ['TENANT_ADMIN'],
  },
  {
    ruta: 'brokers/nuevo',
    etiqueta: 'Broker',
    descripcion: 'Invitar a un broker a la organización',
    roles: ['TENANT_ADMIN'],
  },
];

/** Las altas que este rol puede hacer. Vacío = el botón no se dibuja. */
export function altasDe(rol: RolSesion | undefined): Alta[] {
  if (!rol || rol === 'IDENTIDAD') {
    return [];
  }
  return ALTAS.filter((alta) => alta.roles === 'TODOS' || alta.roles.includes(rol));
}
