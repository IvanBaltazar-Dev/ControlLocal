import {
  altasDe,
  cuerpoDelMenu,
  menuDe,
  modulosDe,
  modulosDelMenu,
  moduloDeRuta,
  MODULOS,
  cuentaDe,
  puedeEntrar,
} from './acceso';
import { RolSesion } from './sesion.model';

/**
 * Fija el mapa de módulos contra `docs/ai/matriz-operacion-rol.md` y la
 * taxonomía de `docs/ai/decision-sidebar-brox.md` (D-E3-1).
 *
 * No comprueba autorización —eso lo impone el backend, y allí lo vigila
 * `MatrizOperacionRolTest`—: comprueba que el SPA no ofrezca al usuario
 * pantallas que el API le va a negar, ni le esconda las que sí puede usar.
 * Lo segundo es el error silencioso: un módulo mal restringido no falla, solo
 * desaparece.
 */
describe('acceso (mapa módulo→rol)', () => {
  const ROLES: RolSesion[] = ['TENANT_ADMIN', 'BROKER', 'AGENTE'];

  it('IDENTIDAD no recibe módulos ni altas comerciales o de gobierno', () => {
    expect(modulosDe('IDENTIDAD')).toEqual([]);
    expect(altasDe('IDENTIDAD')).toEqual([]);
    expect(menuDe('IDENTIDAD')).toEqual([]);
  });

  /** Todo lo que el rol puede ABRIR, se dibuje o no en el menú. */
  const alcanzables = (rol: RolSesion) => modulosDe(rol).map((m) => m.etiqueta);
  /** Solo lo que el rol VE en el menú. */
  const enMenu = (rol: RolSesion) => modulosDelMenu(rol).map((m) => m.etiqueta);

  it('da a cada rol un menú distinto y no vacío', () => {
    for (const rol of ROLES) {
      expect(enMenu(rol).length).toBeGreaterThan(0);
    }
    expect(enMenu('AGENTE')).not.toEqual(enMenu('BROKER'));
    expect(enMenu('BROKER')).not.toEqual(enMenu('TENANT_ADMIN'));
  });

  // ------------------------------------------------------------------
  // D-E3-1: las tres reglas que ordenan el menú
  // ------------------------------------------------------------------

  /**
   * <b>Una cola de trabajo no es una sección</b>, <b>el alcance no crea
   * entradas</b> y <b>la acción pertenece al objeto que modifica</b>.
   *
   * Las entradas de abajo eran exactamente eso: el mismo listado con un
   * filtro, el mismo listado con otro alcance, o una acción sacada de su
   * objeto. La auditoría que originó el Corte 2 las encontró presentadas como
   * secciones, y conservarlas «temporalmente» habría conservado el problema.
   */
  it('mantiene fuera del menú las colas y los alcances comerciales', () => {
    const fueraDelMenu = [
      'Captaciones por revisar', // = Captaciones filtrada por estado
      'Solicitudes por revisar', // = Solicitudes filtrada por evaluar
      'Cartera del equipo', // = Propiedades con el alcance del broker
      'Mi equipo', // = Agentes con el alcance del broker
    ];
    for (const etiqueta of fueraDelMenu) {
      for (const rol of ROLES) {
        expect(enMenu(rol)).not.toContain(etiqueta);
      }
    }
  });

  /**
   * <b>Y siguen protegidas.</b> Esta es la mitad que se olvida: quitar la fila
   * del mapa para que no salga en el menú dejaría la ruta sin módulo, y el
   * guard deja pasar lo que no reconoce («la autorización definitiva sigue en
   * el backend»). Es decir: se abriría a cualquier rol por URL.
   */
  it('mantiene el gate de rol de las pantallas que salieron del menú', () => {
    expect(moduloDeRuta('captaciones/pendientes')?.roles).toEqual(['BROKER']);
    expect(moduloDeRuta('propiedades-equipo')?.roles).toEqual(['BROKER']);
    expect(moduloDeRuta('mi-equipo')?.roles).toEqual(['BROKER']);
    expect(moduloDeRuta('solicitudes/revisar')?.roles).toEqual(['BROKER']);

    // Alcanzable por quien corresponde, invisible para el agente en los dos sentidos.
    expect(alcanzables('BROKER')).toContain('Captaciones por revisar');
    expect(alcanzables('AGENTE')).not.toContain('Captaciones por revisar');
    expect(alcanzables('TENANT_ADMIN')).not.toContain('Captaciones por revisar');
    expect(alcanzables('BROKER')).toContain('Cartera del equipo');
    expect(alcanzables('AGENTE')).not.toContain('Cartera del equipo');
    expect(alcanzables('TENANT_ADMIN')).not.toContain('Cartera del equipo');
  });

  it('conserva las reasignaciones como gobierno de broker y administrador', () => {
    expect(moduloDeRuta('captaciones/reasignaciones')?.roles).toEqual(['BROKER', 'TENANT_ADMIN']);
    expect(alcanzables('BROKER')).toContain('Reasignaciones');
    expect(alcanzables('TENANT_ADMIN')).toContain('Reasignaciones');
    expect(alcanzables('AGENTE')).not.toContain('Reasignaciones');
    expect(enMenu('BROKER')).not.toContain('Reasignaciones');
    expect(enMenu('TENANT_ADMIN')).toContain('Reasignaciones');
    expect(cuerpoDelMenu('TENANT_ADMIN').find((g) =>
      g.modulos.some((m) => m.etiqueta === 'Reasignaciones'))?.seccion).toBe('Organización');
  });

  /**
   * <b>Las dos uniones de D-E3-1 §4.</b> Eran las dos entradas que sobraban en
   * los tres roles y las que dejaban el menú en 15/17/19 en vez de 13/15/17:
   *
   * <ul>
   *   <li><b>Interacciones</b> pertenece al EXPEDIENTE (§4.1). Había dos
   *       superficies para lo mismo — esta bandeja y la bitácora del cliente—,
   *       y a la bandeja se llega desde Seguimiento, que es la transversal.</li>
   *   <li><b>El avance por propiedad</b> (RF-017) es una PESTAÑA de
   *       Indicadores (§4.3): una mide por actor y la otra por inmueble, pero
   *       es la misma pregunta analítica.</li>
   * </ul>
   *
   * Lo que esta prueba impide es la media unión: quitarlas del menú <b>y</b>
   * de las rutas dejaría muertos los enlaces guardados, que es lo contrario de
   * lo que se decidió.
   */
  it('une Interacciones al expediente y el avance a Indicadores sin perder sus rutas', () => {
    for (const rol of ['AGENTE', 'BROKER'] as const) {
      expect(enMenu(rol)).not.toContain('Interacciones');
      expect(enMenu(rol)).not.toContain('Avance por propiedad');
      expect(enMenu(rol)).not.toContain('Reportes');

      // Y siguen abriéndose: la ruta se conserva entera.
      expect(alcanzables(rol)).toContain('Interacciones');
      expect(alcanzables(rol)).toContain('Avance por propiedad');
    }
    expect(moduloDeRuta('interacciones')).toBeDefined();
    expect(moduloDeRuta('reportes')).toBeDefined();
  });

  /**
   * La cuenta de entradas que dejan las dos uniones, por rol. Es la medida que
   * `pendientes-brox.md` §4.2 usaba para saber si 4.8 estaba hecho, y aquí
   * queda fijada para que no se deshaga sin que nadie lo note.
   *
   * El agente ve las 13 comerciales; el broker suma Agentes y Brokers.
   * Gobierno usa cinco superficies organizacionales, sin cartera comercial.
   */
  it('conserva 13 y 15 entradas comerciales y ofrece cinco de gobierno', () => {
    const entradas = (rol: RolSesion) =>
      cuerpoDelMenu(rol).reduce((total, grupo) => total + grupo.modulos.length, 0);

    expect(entradas('AGENTE')).toBe(13);
    expect(entradas('BROKER')).toBe(15);
    expect(entradas('TENANT_ADMIN')).toBe(5);
  });

  /**
   * Cada entrada trae su icono. Antes eran 26 puntos de 6 px idénticos, que no
   * distinguen nada y desaparecen en el cajón del móvil (D-E3-1 §6.1). El tipo
   * ya lo obliga en compilación; esto lo vigila también en el mapa, que es
   * donde se añade una entrada nueva.
   */
  it('da icono a todas las entradas del mapa', () => {
    expect(MODULOS.every((m) => !!m.icono)).toBeTrue();
  });

  it('usa las cinco secciones de negocio y ninguna llamada «Proceso»', () => {
    const secciones = new Set(MODULOS.map((m) => m.seccion));
    expect(secciones.has('Oferta')).toBeTrue();
    expect(secciones.has('Demanda')).toBeTrue();
    expect(secciones.has('Cierre')).toBeTrue();
    expect(secciones.has('Gestión')).toBeTrue();
    expect(secciones.has('Organización')).toBeTrue();
    // «Proceso» mezclaba oferta con supervisión y era el único agrupador que no
    // nombraba una fase del negocio.
    expect([...secciones]).not.toContain('Proceso' as never);
  });

  // ------------------------------------------------------------------
  // Vocabulario
  // ------------------------------------------------------------------

  it('llama a las cosas por su nombre de negocio, no por su tabla', () => {
    const delAgente = enMenu('AGENTE');
    // Con el modelo universal lo que hay en cartera son propiedades de siete
    // tipos, no locales (D-E4-1).
    expect(delAgente).toContain('Propiedades');
    expect(delAgente).not.toContain('Locales');
    // Y la RUTA sigue al rótulo: la canónica es /propiedades. Dejar /locales
    // habría dejado el menú diciendo una cosa y la barra de direcciones otra.
    expect(moduloDeRuta('propiedades')?.etiqueta).toBe('Propiedades');
    expect(moduloDeRuta('locales')).toBeUndefined();

    expect(delAgente).toContain('Inicio');
    expect(delAgente).not.toContain('Dashboard');

    expect(delAgente).toContain('Contratos');
    expect(delAgente).not.toContain('Cierres exitosos');

    // «Solicitudes» y NO «Expedientes»: el expediente es la profundidad de
    // navegación de una solicitud (`/solicitudes/:codigo`), no el nombre del
    // objeto. El menú nombra la bandeja.
    expect(delAgente).toContain('Solicitudes');
    expect(delAgente).not.toContain('Expedientes');
  });

  // ------------------------------------------------------------------
  // Reparto por rol
  // ------------------------------------------------------------------

  it('comparte el padrón de agentes entre BROKER y TENANT_ADMIN', () => {
    // La supervisión y las reasignaciones se comprueban por separado; Agentes es el padrón.
    expect(enMenu('BROKER')).toContain('Agentes');
    expect(enMenu('TENANT_ADMIN')).toContain('Agentes');
    expect(enMenu('AGENTE')).not.toContain('Agentes');
  });

  /**
   * El corazón de D-S0-17: *gobernar no es operar*. La cola de revisión de
   * solicitudes conduce a `POST /evaluaciones` —la decisión que desemboca en
   * contrato y comisión— y esa es del broker.
   */
  it('no da al TENANT_ADMIN las pantallas que terminan en una decisión comercial', () => {
    expect(alcanzables('BROKER')).toContain('Solicitudes por revisar');
    expect(alcanzables('TENANT_ADMIN')).not.toContain('Solicitudes por revisar');
    expect(alcanzables('AGENTE')).not.toContain('Solicitudes por revisar');
  });

  it('deja Asignaciones solo al TENANT_ADMIN', () => {
    expect(enMenu('TENANT_ADMIN')).toContain('Asignaciones');
    expect(enMenu('BROKER')).not.toContain('Asignaciones');
    expect(enMenu('AGENTE')).not.toContain('Asignaciones');
  });

  /**
   * El único endurecimiento nuevo del Corte 2. El backend deja leer
   * `GET /brokers` a los tres roles y lo seguirá haciendo; lo que se retira es
   * ofrecérselo a quien no tiene ninguna operación que lo necesite.
   */
  it('retira Brokers al AGENTE aunque el backend se lo permita', () => {
    expect(enMenu('BROKER')).toContain('Brokers');
    expect(enMenu('TENANT_ADMIN')).toContain('Brokers');
    expect(enMenu('AGENTE')).not.toContain('Brokers');
    expect(alcanzables('AGENTE')).not.toContain('Brokers');
  });

  it('conserva los módulos comerciales para agente y broker dentro de su alcance', () => {
    const comerciales = ['Propiedades', 'Propietarios', 'Clientes', 'Solicitudes', 'Indicadores'];
    for (const modulo of comerciales) {
      for (const rol of ['AGENTE', 'BROKER'] as const) {
        expect(enMenu(rol)).toContain(modulo);
      }
    }
  });

  it('limita gobierno a sus superficies existentes y al perfil propio', () => {
    expect(alcanzables('TENANT_ADMIN')).toEqual([
      'Reasignaciones', 'Agentes', 'Brokers', 'Asignaciones', 'Seguridad y accesos', 'Mi perfil',
    ]);
    expect(menuDe('TENANT_ADMIN').map((g) => g.seccion)).toEqual(['Organización', 'Cuenta']);
  });

  // ------------------------------------------------------------------
  // El pie y el botón de registrar
  // ------------------------------------------------------------------

  /**
   * La cuenta vive arriba a la derecha, no en el lateral: el sidebar es
   * navegación del producto. Y son las dos cosas que existen de verdad —el
   * perfil, que ya contiene contraseña y MFA— más cerrar sesión, que no es una
   * ruta y por eso no está en el mapa.
   */
  it('saca la cuenta del lateral y la deja en el menú de la identidad', () => {
    for (const rol of ROLES) {
      expect(cuentaDe(rol).map((m) => m.etiqueta)).toEqual(['Mi perfil']);
      const cuerpo = cuerpoDelMenu(rol).flatMap((g) => g.modulos.map((m) => m.etiqueta));
      expect(cuerpo).not.toContain('Mi perfil');
    }
  });

  /**
   * «Catálogos del sistema» era una pantalla técnica —el volcado de los códigos
   * del cable— sin endpoint detrás. No se ocultó: se borró entrada, ruta y
   * componente, así que tampoco se abre escribiendo la URL.
   */
  it('no deja rastro de la pantalla de catálogos', () => {
    expect(moduloDeRuta('catalogos')).toBeUndefined();
    expect(MODULOS.some((m) => m.ruta.includes('catalogo'))).toBeFalse();
    for (const rol of ROLES) {
      expect(alcanzables(rol)).not.toContain('Catálogos');
      expect(alcanzables(rol)).not.toContain('Configuración');
    }
  });

  /**
   * `+ Registrar` ofrece ALTAS, no módulos: si duplicara el menú volveríamos a
   * las 25 entradas que la auditoría vino a quitar. Y solo ofrece las que el
   * rol puede hacer, para no prometer un 403.
   */
  it('ofrece a cada rol solo las altas que puede hacer', () => {
    const delAgente = altasDe('AGENTE').map((a) => a.etiqueta);
    expect(delAgente).toEqual(['Propiedad', 'Propietario', 'Cliente']);

    const delAdmin = altasDe('TENANT_ADMIN').map((a) => a.etiqueta);
    expect(delAdmin).toEqual(['Agente', 'Broker']);

    // El broker no da de alta: supervisa y decide.
    expect(altasDe('BROKER')).toEqual([]);
    expect(altasDe(undefined)).toEqual([]);

    // Ninguna alta repite un módulo del menú.
    const etiquetasDeMenu = new Set(ROLES.flatMap((rol) => enMenu(rol)));
    for (const alta of altasDe('AGENTE')) {
      expect(etiquetasDeMenu.has(alta.etiqueta)).toBeFalse();
    }
  });

  // ------------------------------------------------------------------
  // Invariantes del mapa
  // ------------------------------------------------------------------

  it('no muestra nada sin sesión', () => {
    expect(modulosDe(undefined)).toEqual([]);
    expect(MODULOS.every((m) => !puedeEntrar(m, undefined))).toBeTrue();
  });

  it('no deja secciones vacías en el menú', () => {
    for (const rol of ROLES) {
      expect(menuDe(rol).every((grupo) => grupo.modulos.length > 0)).toBeTrue();
    }
  });

  it('resuelve la ruta con o sin barra inicial, y solo las declaradas', () => {
    expect(moduloDeRuta('/solicitudes/revisar')?.etiqueta).toBe('Solicitudes por revisar');
    expect(moduloDeRuta('solicitudes/revisar')?.etiqueta).toBe('Solicitudes por revisar');
    // El guard trata los detalles según su operación y módulo; el mapa sigue siendo exacto.
    expect(moduloDeRuta('/solicitudes/42/documentos')).toBeUndefined();
  });

  it('no declara dos módulos con la misma ruta', () => {
    const rutas = MODULOS.map((m) => m.ruta);
    expect(new Set(rutas).size).toBe(rutas.length);
  });
});
