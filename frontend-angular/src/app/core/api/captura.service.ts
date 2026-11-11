import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';
import { ApiError } from './api.types';
import { AuthService } from '../auth/auth.service';

/**
 * Los límites de un valor, **declarados por el catálogo**.
 *
 * Todo es opcional: un campo sin restricciones declaradas viaja con `null` y el
 * cliente sólo valida lo que el contrato afirme. Lo que NO puede hacer es
 * deducirlas del nombre del campo ni llevar su propia copia — eso sería una
 * regla con dos dueños, que es la misma clase de problema que D-E4-3 cerró para
 * los valores, aplicada a las reglas.
 */
export interface RestriccionesCampo {
  minimo?: number | null;
  maximo?: number | null;
  longitudMaxima?: number | null;
  /** `0` = no admite decimales. `null` = el catálogo no lo declara. */
  decimales?: number | null;
}

/**
 * Un campo con todo lo que hace falta para pintarlo **sin conocerlo**.
 *
 * `control` es la única cosa que esta pantalla mira para decidir qué dibujar.
 * Nunca la clave: si un componente acabara escribiendo `if (clave === 'piso')`,
 * la matriz «tipo → campos» no se habría eliminado, se habría mudado — y hay un
 * gate que rompe el build por eso (`FronteraDeAutoridadEnElSpaTest`).
 */
/**
 * Un valor admitido con su nombre. La pantalla pinta `rotulo` y devuelve
 * `valor`; decidir que `CZ` se lee «Comercio zonal» es del catálogo.
 *
 * Llegan como objetos desde el Corte 0B. Hasta entonces eran `string[]` y
 * este tipo se quedó atrás: el selector del alta pintaba el objeto entero.
 */
export interface OpcionCaptura {
  valor: string;
  rotulo: string;
}

export interface PreguntaCaptura {
  clave: string;
  rotulo: string;
  /**
   * A qué sección del guion pertenece: APERTURA, COMUN, TIPO u OPERACION. Es
   * lo que decide qué se conserva al cambiar la selección.
   */
  seccion?: string | null;
  /**
   * La agrupación temática que declara el **catálogo** («edificio»,
   * «instalaciones»). Se llamó así también la sección hasta el Corte 0B; ahora
   * son dos cosas y esta es la del catálogo. Puede venir vacía.
   */
  familia?: string | null;
  /**
   * SELECTOR, SELECTOR_MULTIPLE, TITULARES, INTERRUPTOR, MONEDA, FECHA,
   * IMPORTE, ENTERO, DECIMAL, TEXTO. Lo único que se mira para dibujar.
   */
  control: string;
  tipoDato: string;
  unidad?: string | null;
  opciones?: OpcionCaptura[] | null;
  /** ALT bloquea el alta, PUB bloquea publicar, OPC no bloquea nada. */
  exigencia?: string | null;
  obligatoria: boolean;
  ayuda?: string | null;
  orden: number;
  restricciones?: RestriccionesCampo | null;
}

/**
 * La condición económica de **un** encargo, con su título ya puesto.
 *
 * Una propiedad que se ofrece para venta y para alquiler devuelve dos: misma
 * forma, distinto rótulo. La pantalla pinta una sección por bloque y no
 * necesita saber cuántos hay ni partir ninguna clave.
 */
export interface BloqueOperacion {
  /** VENTA o ALQUILER. Nunca un valor combinado: `AMBAS` no existe. */
  operacion: string;
  /** «Condición de venta», «Condición de alquiler». */
  rotulo: string;
  preguntas: PreguntaCaptura[];
}

export interface DefinicionCaptura {
  intencion: string;
  tipoPropiedad: string;
  operaciones: string[];
  comunes: PreguntaCaptura[];
  delTipo: PreguntaCaptura[];
  deLaOperacion: BloqueOperacion[];
}

/** Dónde se ha quedado una captura. */
export interface EstadoCaptura {
  idBorrador: number;
  codigo: string;
  intencion: string;
  /** E en curso · J ejecutado · D descartado. */
  estado: string;
  canal?: string | null;
  conocido: Record<string, unknown>;
  /** Lo que falta, **en el orden en que se preguntará**. */
  faltante: string[];
  siguiente?: PreguntaCaptura | null;
  listoParaEjecutar: boolean;
  entidadTipo?: string | null;
  idEntidad?: number | null;
  actualizadoEn?: string | null;
}

/** Lo que produjo una captura confirmada. */
export interface EjecucionCaptura {
  idBorrador: number;
  idPropiedad: number;
  codigoPropiedad: string;
  /** Uno por operación declarada: dos cuando la propiedad se vende Y se alquila. */
  idsEncargos: number[];
  /** `true` cuando la confirmación era un reintento y no creó nada nuevo. */
  reintento: boolean;
}

/** UC-K-06: contenido canónico preparado por Core, sin prueba humana para el modelo. */
export interface PropuestaCaptura {
  referencia: string;
  capacidad: string;
  version: number;
  huella: string;
  caducaEnEpochSeconds: number;
  contenido: {
    idBorrador: number;
    versionCaptura: number;
    codigoCaptura: string;
    intencion: string;
    efecto: string;
    datos: Record<string, unknown>;
    definicionCore: DefinicionCaptura;
  };
}

/** Sólo la superficie humana recibe esta prueba opaca; nunca una herramienta conversacional. */
export interface RevisionCaptura {
  propuesta: PropuestaCaptura;
  pruebaRevision: string;
}

export interface ResultadoActuacionCaptura {
  referencia: string;
  estado: 'PREPARADA' | 'EXITO' | 'RECHAZADO' | 'EN_VERIFICACION';
  propuesta: PropuestaCaptura | null;
  resultado: EjecucionCaptura | null;
  motivo: string | null;
}

export interface ReferenciaCapturaPendiente { idBorrador: number; referencia: string; }

/**
 * **El motor de captura de BROX Core, por el cable.**
 *
 * BROX Web no decide qué se pregunta para cada tipo de propiedad: lo pregunta.
 * Sin este servicio, Angular necesitaría una tabla «tipo → campos» y KAIROS
 * otra, y las dos empezarían a divergir del catálogo —que es el real— desde el
 * primer atributo que alguien añada (D-A-1 §6).
 *
 * Abrir y responder conservan el borrador. El alta se solicita con la revisión
 * humana exacta; una respuesta perdida se consulta mediante la referencia
 * original, sin volver a emitir la confirmación.
 */
@Injectable({ providedIn: 'root' })
export class CapturaService {
  private readonly api = inject(ApiClient);
  private readonly auth = inject(AuthService);
  private espacioReferencias: { operador: string; prefijo: string } | null = null;

  /** El contexto procede de Core; nunca del texto, un tenant fijo o la decodificación del JWT. */
  async cargarContextoReferencias(): Promise<void> {
    this.espacioReferencias = null;
    const actual = this.auth.sesion();
    if (!actual || actual.contextoRequerido || !Number.isSafeInteger(actual.idOrganizacion) || actual.idOrganizacion <= 0) return;
    const catalogo = await this.api.get<{ capacidades: { idCore: string;
      contexto: { organizacionId: number; personaId: number; rol: string } }[] }>(
      'capacidades', { idCore: 'consultar_actuacion' });
    const contexto = catalogo.capacidades.find((c) => c.idCore === 'consultar_actuacion')?.contexto;
    if (!contexto || !Number.isSafeInteger(contexto.organizacionId) || contexto.organizacionId <= 0 ||
        contexto.organizacionId !== actual.idOrganizacion ||
        contexto.personaId !== actual.idUsuario || contexto.rol !== actual.rol) {
      throw new ApiError(403, 'El contexto de la sesión cambió. Vuelve a ingresar.');
    }
    this.espacioReferencias = { operador: `${actual.idOrganizacion}:${actual.idUsuario}:${actual.idDominio}:${actual.rol}`,
      prefijo: `brox:actuacion:v1:${contexto.organizacionId}:${contexto.personaId}:${contexto.rol}:${actual.idDominio}:` };
  }

  private prefijoReferencias(): string | null {
    const actual = this.auth.sesion();
    if (!actual || !this.espacioReferencias ||
        `${actual.idOrganizacion}:${actual.idUsuario}:${actual.idDominio}:${actual.rol}` !== this.espacioReferencias.operador) {
      this.espacioReferencias = null;
      return null;
    }
    return this.espacioReferencias.prefijo;
  }

  referenciasPendientes(): ReferenciaCapturaPendiente[] {
    const prefijo = this.prefijoReferencias();
    if (!prefijo) return [];
    try {
      const referencias: ReferenciaCapturaPendiente[] = [];
      for (let i = 0; i < sessionStorage.length; i++) {
        const clave = sessionStorage.key(i);
        if (!clave?.startsWith(prefijo)) continue;
        const id = Number(clave.slice(prefijo.length));
        const referencia = sessionStorage.getItem(clave);
        if (Number.isSafeInteger(id) && id > 0 && referencia) referencias.push({ idBorrador: id, referencia });
      }
      return referencias;
    } catch { return []; }
  }

  /**
   * Qué hay que decidir **antes** de que exista un plan de preguntas.
   *
   * Hoy son el tipo y la operación, pero la pantalla no lo sabe ni tiene por
   * qué: pinta lo que llega, en el orden en que llega.
   */
  apertura(intencion = 'REGISTRAR_PROPIEDAD'): Promise<PreguntaCaptura[]> {
    return this.api.get<PreguntaCaptura[]>('captura/apertura', { intencion });
  }

  /**
   * Qué se pregunta para este tipo y estas operaciones, con qué límites.
   *
   * @param operaciones una o varias: `VENTA`, `ALQUILER` o `VENTA,ALQUILER`.
   *        Con las dos, `deLaOperacion` trae dos bloques y la ficha física
   *        sigue viniendo una sola vez — una propiedad, dos encargos.
   */
  definicion(
    tipoPropiedad: string,
    operaciones: string,
    intencion = 'REGISTRAR_PROPIEDAD',
  ): Promise<DefinicionCaptura> {
    return this.api.get<DefinicionCaptura>('captura/definicion', {
      intencion,
      tipoPropiedad,
      operaciones,
    });
  }

  /** Abre una captura nueva. No escribe nada del negocio. */
  abrir(intencion = 'REGISTRAR_PROPIEDAD'): Promise<EstadoCaptura> {
    return this.api.post<EstadoCaptura>('captura', { intencion });
  }

  /** Capturas que la organizacion dejo en curso y se pueden retomar. */
  enCurso(): Promise<EstadoCaptura[]> {
    return this.api.get<EstadoCaptura[]>('captura');
  }

  /**
   * Anota lo que se acaba de saber y devuelve qué falta.
   *
   * Un valor vacío **borra** el dato en vez de guardarlo en blanco: el usuario
   * corrigiéndose es un caso normal, y un `""` guardado se parece demasiado a
   * un dato que sí se declaró.
   */
  avanzar(idBorrador: number, datos: Record<string, string>): Promise<EstadoCaptura> {
    return this.api.post<EstadoCaptura>('captura', { idBorrador, datos });
  }

  consultar(idBorrador: number): Promise<EstadoCaptura> {
    return this.api.get<EstadoCaptura>(`captura/${idBorrador}`);
  }

  /** Se prepara antes de mostrar la revisión, nunca después del click Confirmar. */
  async prepararRevision(idBorrador: number): Promise<RevisionCaptura> {
    const propuesta = await this.api.post<PropuestaCaptura>(
      `actuaciones/capturas/${idBorrador}/propuestas`,
    );
    if (!propuesta?.referencia) throw new ApiError(502, 'Core no devolvió una propuesta verificable.');
    const revision = await this.api.get<RevisionCaptura>(
      `actuaciones/${encodeURIComponent(propuesta.referencia)}/revision`,
    );
    if (!revision?.propuesta || !revision.pruebaRevision ||
        revision.propuesta.referencia !== propuesta.referencia ||
        revision.propuesta.version !== propuesta.version || revision.propuesta.huella !== propuesta.huella) {
      throw new ApiError(409, 'La propuesta cambió. Vuelve a revisar el contenido.');
    }
    return revision;
  }

  /** Consume la revisión exacta y registra un evento humano estable, sin reenviar una escritura incierta. */
  async confirmarRevision(revision: RevisionCaptura, eventoHumano: string): Promise<ResultadoActuacionCaptura> {
    const propuesta = revision.propuesta;
    this.recordarReferencia(propuesta.contenido.idBorrador, propuesta.referencia);
    try {
      const resultado = await this.api.post<ResultadoActuacionCaptura>(
        `actuaciones/${encodeURIComponent(propuesta.referencia)}/confirmacion`,
        { version: propuesta.version, huella: propuesta.huella,
          pruebaRevision: revision.pruebaRevision, eventoHumano },
      );
      return this.validarResultado(resultado, propuesta.referencia);
    } catch (error) {
      if (error instanceof ApiError && error.status > 0 && error.status < 500) {
        this.olvidarReferencia(propuesta.contenido.idBorrador);
        throw error;
      }
      try {
        return await this.consultarActuacion(propuesta.referencia);
      } catch {
        return { referencia: propuesta.referencia, estado: 'EN_VERIFICACION', propuesta,
          resultado: null, motivo: 'RESULTADO_EN_VERIFICACION' };
      }
    }
  }

  async consultarActuacion(referencia: string): Promise<ResultadoActuacionCaptura> {
    return this.validarResultado(await this.api.get<ResultadoActuacionCaptura>(
      `actuaciones/${encodeURIComponent(referencia)}`), referencia);
  }

  private validarResultado(resultado: ResultadoActuacionCaptura, referencia: string): ResultadoActuacionCaptura {
    if (!resultado || resultado.referencia !== referencia ||
        !['EXITO', 'RECHAZADO', 'EN_VERIFICACION', 'PREPARADA'].includes(resultado.estado) ||
        (resultado.estado === 'EXITO' && (!resultado.resultado ||
          !Number.isSafeInteger(resultado.resultado.idPropiedad) || resultado.resultado.idPropiedad <= 0))) {
      throw new ApiError(502, 'El resultado de Core no pudo verificarse.');
    }
    return resultado;
  }

  /** Conserva sólo la referencia técnica, sin contenido, nonce o credenciales. */
  referenciaPendiente(idBorrador: number): string | null {
    const prefijo = this.prefijoReferencias();
    if (!prefijo) return null;
    try { return sessionStorage.getItem(`${prefijo}${idBorrador}`); }
    catch { return null; }
  }

  olvidarReferencia(idBorrador: number): void {
    const prefijo = this.prefijoReferencias();
    if (!prefijo) return;
    try { sessionStorage.removeItem(`${prefijo}${idBorrador}`); }
    catch { /* El resultado Core ya existe; el almacenamiento local no lo revierte. */ }
  }

  private recordarReferencia(idBorrador: number, referencia: string): void {
    const prefijo = this.prefijoReferencias();
    if (!prefijo) return;
    try { sessionStorage.setItem(`${prefijo}${idBorrador}`, referencia); }
    catch { /* La referencia también se conserva en el estado de esta interacción. */ }
  }

  /** Abandonada a propósito. No se borra: que alguien la empezara también es un hecho. */
  descartar(idBorrador: number): Promise<EstadoCaptura> {
    return this.api.delete<EstadoCaptura>(`captura/${idBorrador}`);
  }

  /**
   * Las restricciones de cada clave, aplanadas por clave.
   *
   * Las tres familias van separadas en el contrato porque se comportan distinto
   * al cambiar la selección; para aplicar un mínimo esa distinción no importa,
   * así que se aplanan aquí y no en cada componente.
   */
  async restriccionesPorClave(
    tipoPropiedad: string,
    operaciones: string,
  ): Promise<Map<string, RestriccionesCampo>> {
    const definicion = await this.definicion(tipoPropiedad, operaciones);
    const porClave = new Map<string, RestriccionesCampo>();
    for (const pregunta of preguntasDe(definicion)) {
      if (pregunta.restricciones) {
        porClave.set(pregunta.clave, pregunta.restricciones);
      }
    }
    return porClave;
  }
}

/** Todas las preguntas de una definición, en el orden en que se presentan. */
export function preguntasDe(definicion: DefinicionCaptura): PreguntaCaptura[] {
  return [
    ...definicion.comunes,
    ...definicion.deLaOperacion.flatMap((bloque) => bloque.preguntas),
    ...definicion.delTipo,
  ];
}
