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
   * Las cinco entradas de abajo eran exactamente eso: el mismo listado con un
   * filtro, el mismo listado con otro alcance, o una acción sacada de su
   * objeto. La auditoría que originó el Corte 2 las encontró presentadas como
   * secciones, y conservarlas «temporalmente» habría conservado el problema.
   */
  it('no ofrece como entrada de menú ninguna cola, alcance ni acción', () => {
    const fueraDelMenu = [
      'Captaciones por revisar', // = Captaciones filtrada por estado
      'Solicitudes por revisar', // = Solicitudes filtrada por evaluar
      'Cartera del equipo', // = Propiedades con el alcance del broker
      'Mi equipo', // = Agentes con el alcance del broker
      'Reasignaciones', // = una acción sobre el encargo
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
    expect(moduloDeRuta('captaciones/pendientes')?.roles).toEqual(['BROKER', 'TENANT_ADMIN']);
    expect(moduloDeRuta('captaciones/reasignaciones')?.roles).toEqual(['BROKER', 'TENANT_ADMIN']);
    expect(moduloDeRuta('propiedades-equipo')?.roles).toEqual(['BROKER', 'TENANT_ADMIN']);
    expect(moduloDeRuta('mi-equipo')?.roles).toEqual(['BROKER']);
    expect(moduloDeRuta('solicitudes/revisar')?.roles).toEqual(['BROKER']);

    // Alcanzable por quien corresponde, invisible para el agente en los dos sentidos.
    expect(alcanzables('BROKER')).toContain('Captaciones por revisar');
    expect(alcanzables('AGENTE')).not.toContain('Captaciones por revisar');
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

  it('comparte la supervisión entre BROKER y TENANT_ADMIN', () => {
    for (const deSupervision of ['Captaciones por revisar', 'Reasignaciones', 'Cartera del equipo']) {
      expect(alcanzables('BROKER')).toContain(deSupervision);
      expect(alcanzables('TENANT_ADMIN')).toContain(deSupervision);
      expect(alcanzables('AGENTE')).not.toContain(deSupervision);
    }
    // Agentes sí es entrada de menú: es un padrón, no una cola.
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

  it('no restringe lo que el backend no restringe', () => {
    // Donde el backend no pone gate, lo que limita es el ALCANCE. Esconder esos
    // módulos sería inventar una restricción que el API no tiene.
    const sinGate = ['Propiedades', 'Propietarios', 'Clientes', 'Solicitudes', 'Indicadores'];
    for (const modulo of sinGate) {
      for (const rol of ROLES) {
        expect(enMenu(rol)).toContain(modulo);
      }
    }
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
    // Rutas no declaradas (detalles, formularios) pasan y las cubre el backend.
    expect(moduloDeRuta('/solicitudes/42/documentos')).toBeUndefined();
  });

  it('no declara dos módulos con la misma ruta', () => {
    const rutas = MODULOS.map((m) => m.ruta);
    expect(new Set(rutas).size).toBe(rutas.length);
  });
});
