import { menuDe, modulosDe, moduloDeRuta, MODULOS, puedeEntrar } from './acceso';
import { RolSesion } from './sesion.model';

/**
 * Fija el mapa de módulos contra `docs/ai/matriz-operacion-rol.md`.
 *
 * No comprueba autorización —eso lo impone el backend, y allí lo vigila
 * `MatrizOperacionRolTest`—: comprueba que el SPA no ofrezca al usuario
 * pantallas que el API le va a negar, ni le esconda las que sí puede usar.
 * Lo segundo es el error silencioso: un módulo mal restringido no falla, solo
 * desaparece.
 */
describe('acceso (mapa módulo→rol)', () => {
  const etiquetas = (rol: RolSesion) => modulosDe(rol).map((m) => m.etiqueta);

  it('da a cada rol un menú distinto y no vacío', () => {
    const roles: RolSesion[] = ['TENANT_ADMIN', 'BROKER', 'AGENTE'];
    for (const rol of roles) {
      expect(etiquetas(rol).length).toBeGreaterThan(0);
    }
    expect(etiquetas('AGENTE')).not.toEqual(etiquetas('BROKER'));
    expect(etiquetas('BROKER')).not.toEqual(etiquetas('TENANT_ADMIN'));
  });

  it('no reserva NINGÚN módulo al AGENTE: su reserva la impone el backend', () => {
    // Ya no queda ninguna entrada exclusiva del agente, y es deliberado. Los
    // dos recursos que el backend sí le reserva viven dentro de otra pantalla:
    // `/tareas` en la bandeja del dashboard y `/locales/mis-locales` en el
    // filtro "Solo mis captaciones" de Locales. Un módulo propio para cada uno
    // sería una pantalla-silo sobre un listado que ya existe.
    //
    // La consecuencia comprobable es que el menú del agente es exactamente el
    // conjunto sin gate de rol, y por tanto un subconjunto del de los otros dos.
    const delAgente = etiquetas('AGENTE');
    expect(delAgente.length).toBeGreaterThan(0);
    for (const etiqueta of delAgente) {
      expect(etiquetas('BROKER')).toContain(etiqueta);
      expect(etiquetas('TENANT_ADMIN')).toContain(etiqueta);
    }
    expect(delAgente).toContain('Locales');
  });

  it('comparte la supervisión entre BROKER y TENANT_ADMIN', () => {
    // Ver la cola y el rastro del equipo no produce ningún hecho del negocio,
    // así que el administrador las conserva (D-S0-17 filas 1, 8 y 14-16).
    for (const deSupervision of [
      'Captaciones por revisar',
      'Reasignaciones',
      'Cartera del equipo',
      'Agentes',
    ]) {
      expect(etiquetas('BROKER')).toContain(deSupervision);
      expect(etiquetas('TENANT_ADMIN')).toContain(deSupervision);
      expect(etiquetas('AGENTE')).not.toContain(deSupervision);
    }
  });

  /**
   * El corazón de D-S0-17: *gobernar no es operar*. La cola de revisión de
   * solicitudes conduce a `POST /evaluaciones` —la decisión que desemboca en
   * contrato y comisión— y esa es del broker. Ofrecérsela al administrador
   * sería prometerle un 403 al final del camino.
   */
  it('no ofrece al TENANT_ADMIN las pantallas que terminan en una decisión comercial', () => {
    expect(etiquetas('BROKER')).toContain('Solicitudes por revisar');
    expect(etiquetas('TENANT_ADMIN')).not.toContain('Solicitudes por revisar');
    expect(etiquetas('AGENTE')).not.toContain('Solicitudes por revisar');
  });

  it('deja Asignaciones solo al TENANT_ADMIN', () => {
    expect(etiquetas('TENANT_ADMIN')).toContain('Asignaciones');
    expect(etiquetas('BROKER')).not.toContain('Asignaciones');
    expect(etiquetas('AGENTE')).not.toContain('Asignaciones');
  });

  it('no restringe lo que el backend no restringe', () => {
    // 62 de las 146 operaciones del backend NO llevan gate de rol: ahí lo que
    // limita es el ALCANCE, no el acceso. Esconder esos módulos sería inventar
    // una restricción que el API no tiene. `Solicitudes` es el caso de F4: el
    // BROKER alcanza por agente supervisado, no por captación.
    const sinGate = ['Locales', 'Propietarios', 'Clientes', 'Solicitudes', 'Brokers', 'Indicadores'];
    const roles: RolSesion[] = ['TENANT_ADMIN', 'BROKER', 'AGENTE'];
    for (const modulo of sinGate) {
      for (const rol of roles) {
        expect(etiquetas(rol)).toContain(modulo);
      }
    }
  });

  it('no muestra nada sin sesión', () => {
    expect(modulosDe(undefined)).toEqual([]);
    expect(MODULOS.every((m) => !puedeEntrar(m, undefined))).toBeTrue();
  });

  it('no deja secciones vacías en el menú', () => {
    const roles: RolSesion[] = ['TENANT_ADMIN', 'BROKER', 'AGENTE'];
    for (const rol of roles) {
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
