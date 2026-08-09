import { OpcionFiltro } from '../../shared/filtro-select/filtro-select';

/**
 * Catálogo de los códigos de una letra del contrato congelado. Equivale al
 * `EnumOption`/`Codigos` del Blazor y existe porque **el cable viaja con el
 * código, no con la descripción**: `'D'`, no `"Disponible"`.
 *
 * Dos reglas al usarlo:
 * - La descripción es **de presentación**; lo que se envía al API y lo que se
 *   compara siempre es el código.
 * - Un código desconocido se devuelve **tal cual**, no se convierte en "-".
 *   Si el backend añade un estado, la pantalla lo muestra en crudo en vez de
 *   esconderlo — que es como se detecta que falta actualizar esta tabla.
 *
 * Ojo con F3: `TipoInmuebleComercial` y parte de `ResultadoInteraccion`
 * **rompen la convención** y viajan con el nombre del enum, no con una letra.
 * `EstadoRequerimiento` sí la sigue (`A`/`P`/`C`) desde la normalización
 * V15–V20, aunque el §3 del contrato F3 —anterior— diga lo contrario: manda
 * `docs/ai/matriz-codigos-estado.md`.
 */

export type Catalogo = Readonly<Record<string, string>>;

/** Disponibilidad del local. `N` = alquilado o retirado del mercado. */
export const ESTADO_LOCAL: Catalogo = {
  D: 'Disponible',
  N: 'No disponible',
  I: 'Inactivo',
};

export const TIPO_PERSONA: Catalogo = {
  N: 'Persona natural',
  J: 'Persona jurídica',
};

/** Documento de identidad de una persona. También viaja como una letra. */
export const TIPO_DOCUMENTO: Catalogo = {
  D: 'DNI',
  R: 'RUC',
  C: 'Carnet de extranjería',
  P: 'Pasaporte',
};

export const TIPO_INMUEBLE: Catalogo = {
  L: 'Local',
  O: 'Oficina',
};

/** Solo comercial: ControlLocal no gestiona vivienda. */
export const USO_INMUEBLE: Catalogo = {
  C: 'Comercial',
};

export const ESTADO_PROSPECCION: Catalogo = {
  P: 'Prospecto',
  C: 'Contactado',
  R: 'Reunion',
  E: 'Propuesta entregada',
  S: 'En seguimiento',
  T: 'Captado',
  D: 'Descartado',
};

/**
 * Resultado de la propuesta al propietario. Va aparte de
 * `ESTADO_PROSPECCION` porque **no es un estado de la máquina**: convive con
 * él, y es la marca real de "propuesta entregada" (la v1 nunca emite el
 * estado `E`).
 */
export const RESULTADO_PROPUESTA: Catalogo = {
  P: 'Pendiente',
  A: 'Aceptada',
  R: 'Rechazada',
  S: 'Recontactar',
};

/** Estado de una publicación del local. `B` = existe pero no está difundida. */
export const ESTADO_PUBLICACION: Catalogo = {
  B: 'Sin publicar',
  P: 'Publicado',
  S: 'Pausado',
  C: 'Cerrado',
};

/**
 * Canales de difusión. Rompe la convención de una letra: el cable viaja con
 * el nombre en mayúsculas (`URBANIA`), no con un código.
 */
export const CANAL_PUBLICACION: Catalogo = {
  URBANIA: 'Urbania',
  ADONDEVIVIR: 'AdondeVivir',
  PROPERATI: 'Properati',
  NEXO_INMOBILIARIO: 'Nexo Inmobiliario',
  FACEBOOK: 'Facebook',
  MARKETPLACE: 'Marketplace',
  INSTAGRAM: 'Instagram',
  WHATSAPP: 'WhatsApp',
  WEB_PROPIA: 'Web propia',
  REFERIDO: 'Referido',
  OTRO: 'Otro',
};

/** Hito comercial de un precio del histórico. */
export const HITO_PRECIO: Catalogo = {
  E: 'Esperado',
  R: 'Recomendado',
  U: 'Autorizado',
  P: 'Publicado',
  O: 'Ofertado',
  A: 'Aceptado',
  C: 'Cerrado',
};

export const ESTADO_CAPTACION: Catalogo = {
  P: 'Pendiente de revision',
  O: 'Observada',
  R: 'Rechazada',
  A: 'Activa',
  C: 'Cerrada',
  V: 'Vencida',
};

export const ESTADO_OPORTUNIDAD: Catalogo = {
  A: 'Abierta',
  S: 'Solicitud creada',
  N: 'No continua',
  F: 'Finalizada exitosa',
  X: 'Finalizada no favorable',
};

export const ESTADO_SOLICITUD: Catalogo = {
  G: 'Registrada',
  E: 'En revision',
  O: 'Observada',
  A: 'Aprobada',
  R: 'Rechazada',
  D: 'Desistida',
  C: 'Cerrada',
};

/**
 * Estado del documento del expediente. Lo **deriva** la revisión del broker y
 * el agente no lo escribe: nace `R` al subirlo, y solo "conforme" lo deja `V`.
 */
export const ESTADO_DOCUMENTO: Catalogo = {
  R: 'Registrado',
  O: 'Observado',
  V: 'Validado',
};

/** Revisión del broker sobre un documento. `C` valida; cualquier otra observa. */
export const RESULTADO_REVISION: Catalogo = {
  P: 'Pendiente',
  C: 'Conforme',
  O: 'Observado',
};

export const RESULTADO_EVALUACION: Catalogo = {
  A: 'Aprobada',
  R: 'Rechazada',
  O: 'Observada',
};

/**
 * Tipo de la evaluación. **No se elige: lo deriva el resultado** (`O` ⇒
 * observación, `A`/`R` ⇒ final), así que este catálogo es solo de lectura del
 * historial. `P` preliminar existe en el vocabulario pero el cable nunca lo
 * produce.
 */
export const TIPO_EVALUACION: Catalogo = {
  P: 'Preliminar',
  O: 'Observacion',
  F: 'Final',
};

/**
 * Tipos de documento del expediente de alquiler. Los ocho se pueden subir, pero
 * **el checklist "X/6" solo cuenta seis**: poder de representación (`P`) y
 * "otro" (`O`) no suman aunque estén cargados. Ver {@link TIPOS_REQUERIDOS}.
 */
export const TIPO_DOCUMENTO_SOLICITUD: Catalogo = {
  I: 'Documento de identidad',
  R: 'Ficha o constancia RUC',
  V: 'Vigencia de poder',
  P: 'Poder de representacion',
  E: 'Sustento economico',
  G: 'Documento de garantia',
  D: 'Declaracion jurada',
  O: 'Otro',
};

/**
 * Los seis que cuentan para el indicador de la bandeja, **en el orden en que
 * se piden**. Es el mismo conjunto que el backend usa para calcular
 * `documentosEntregados`: si aquí se añade uno, el contador deja de cuadrar.
 */
export const TIPOS_REQUERIDOS: readonly string[] = ['I', 'R', 'V', 'E', 'G', 'D'];

/**
 * Forma de pago pactada. **Rompe la convención de una letra**: viaja con el
 * NOMBRE del enum, como `EstadoComision`.
 */
export const FORMA_PAGO: Catalogo = {
  TRANSFERENCIA: 'Transferencia',
  DEPOSITO_BANCARIO: 'Deposito bancario',
  EFECTIVO: 'Efectivo',
  CHEQUE: 'Cheque',
  OTRO: 'Otro',
};

export const ESTADO_CONTRATO: Catalogo = {
  P: 'En proceso',
  D: 'Firmado',
  V: 'Vigente',
  R: 'Renovado',
  F: 'Finalizado',
  S: 'Rescindido',
  A: 'Anulado',
};

/**
 * Los dos únicos estados con los que se puede **cerrar** un alquiler. El resto
 * del catálogo describe la vida posterior del contrato; el backend responde
 * _"El cierre solo admite los estados Firmado o Vigente."_ a cualquier otro.
 */
export const ESTADO_CONTRATO_AL_CERRAR: Catalogo = {
  V: 'Vigente',
  D: 'Firmado',
};

/** Estado persistido y expuesto como código unitario: P/R/C/A. */
export const ESTADO_COMISION: Catalogo = {
  P: 'Pendiente',
  R: 'Parcial',
  C: 'Cobrada',
  A: 'Anulada',
};

/**
 * Máquina de la visita. `G` no es un estado terminal: reprogramar desde `P` o
 * `G` vuelve a dejar la visita pendiente de realizarse.
 */
export const ESTADO_VISITA: Catalogo = {
  P: 'Programada',
  G: 'Reprogramada',
  R: 'Realizada',
  N: 'No realizada',
  C: 'Cancelada',
};

/** Canal por el que se produjo el contacto. Obligatorio al registrar. */
export const CANAL_CONTACTO: Catalogo = {
  L: 'Llamada',
  W: 'WhatsApp',
  E: 'Email',
  P: 'Presencial',
  R: 'Reunión',
  T: 'Portal',
  O: 'Otro',
};

/** Razón tipificada del cierre por no continuidad de una oportunidad. */
export const MOTIVO_NO_CONTINUIDAD: Catalogo = {
  P: 'Precio',
  U: 'Ubicación',
  C: 'Condiciones del contrato',
  L: 'Local no adecuado',
  N: 'Cliente no responde',
  E: 'Encontró otra opción',
  O: 'Otro',
};

/** Desenlace de una visita realizada. Los cuatro campos son opcionales. */
export const OBJECION_VISITA: Catalogo = {
  P: 'Precio',
  U: 'Ubicación',
  E: 'Estado del inmueble',
  C: 'Condiciones',
  O: 'Otra',
};

export const OPINION_PRECIO: Catalogo = {
  A: 'Alto',
  J: 'Justo',
  B: 'Bajo',
};

export const PROXIMA_ACCION_VISITA: Catalogo = {
  V: 'Nueva visita',
  O: 'Oferta',
  S: 'Seguimiento',
  D: 'Descartado',
};

/**
 * Estado de la búsqueda declarada. **Va con la convención de una letra**
 * (`A`/`P`/`C`), como el resto de estados del sistema: la fuente de verdad es
 * `docs/ai/matriz-codigos-estado.md`, no el §3 del contrato F3 —que se escribió
 * antes de la normalización V15–V20 y todavía habla de `ACTIVO`/`PAUSADO`/
 * `CERRADO`—.
 *
 * El código es lo que viaja; el texto natural es cosa de esta tabla. Solo los
 * `A` alimentan el matching de cartera.
 */
export const ESTADO_REQUERIMIENTO: Catalogo = {
  A: 'Activo',
  P: 'Pausado',
  C: 'Cerrado',
};

/**
 * `TipoInmuebleComercial`, también con el nombre del enum. Ojo: no es el mismo
 * catálogo que `TIPO_INMUEBLE` (`L`/`O`) del local, y solo tres de estos seis
 * tienen equivalencia 1:1 con él — de ahí que el criterio de tipo del matching
 * responda NO_APLICA para depósito, stand y otro.
 */
export const TIPO_INMUEBLE_COMERCIAL: Catalogo = {
  LOCAL_COMERCIAL: 'Local comercial',
  OFICINA: 'Oficina',
  DEPOSITO_ALMACEN: 'Depósito o almacén',
  STAND_MODULO: 'Stand o módulo',
  TERRENO_COMERCIAL: 'Terreno comercial',
  OTRO: 'Otro',
};

/** Entidad de la que cuelga una interacción (bitácora polimórfica). */
export const CONTEXTO_INTERACCION: Catalogo = {
  OPORTUNIDAD: 'Oportunidad',
  PROSPECCION: 'Prospección',
  CAPTACION: 'Captación',
  CLIENTE: 'Cliente',
};

/**
 * `ResultadoInteraccion` es el enum **mixto** de F3: conviven cinco códigos de
 * una letra heredados —los que usa el desenlace de la visita— con códigos-
 * palabra que dependen del contexto. Se describen todos juntos porque el cable
 * puede devolver cualquiera de ellos en `resultado`; para **ofrecer** opciones
 * en un formulario está `resultadosDe(contexto)`, que aplica la allow-list.
 */
export const RESULTADO_INTERACCION: Catalogo = {
  // Heredados (desenlace de visita y filas antiguas de interacción).
  P: 'Pendiente',
  I: 'Interesado',
  N: 'No interesado',
  S: 'Seguimiento',
  D: 'Descartado',
  // Contexto PROSPECCION.
  CONTACTADO: 'Contactado',
  REUNION_AGENDADA: 'Reunión agendada',
  PROPUESTA_ENVIADA: 'Propuesta enviada',
  ACEPTA_CAPTAR: 'Acepta captar',
  NO_ACEPTA: 'No acepta',
  RECONTACTAR: 'Recontactar',
  // Contexto CAPTACION.
  DOCS_SOLICITADOS: 'Documentos solicitados',
  CONDICIONES_AJUSTADAS: 'Condiciones ajustadas',
  PUBLICACION_COORDINADA: 'Publicación coordinada',
  PROPIETARIO_OBSERVA: 'Propietario observa',
  LISTO_PARA_PUBLICAR: 'Listo para publicar',
  PAUSAR_GESTION: 'Pausar gestión',
  // Contexto OPORTUNIDAD.
  INTERESADO: 'Interesado',
  VISITA_AGENDADA: 'Visita agendada',
  OFERTA_SOLICITADA: 'Oferta solicitada',
  NEGOCIANDO: 'Negociando',
  NO_INTERESADO: 'No interesado',
  // Contexto CLIENTE.
  BUSQUEDA_LEVANTADA: 'Búsqueda levantada',
  REQUIERE_OPCIONES: 'Requiere opciones',
  NO_RESPONDE: 'No responde',
  SEGUIMIENTO: 'Seguimiento',
  // Compartido por CLIENTE y OPORTUNIDAD, de ahí que vaya suelto al final.
  DESCARTADO: 'Descartado',
};

/** Desenlace de una visita realizada: los cinco códigos de una letra. */
export const RESULTADO_VISITA: Catalogo = {
  P: 'Pendiente',
  I: 'Interesado',
  N: 'No interesado',
  S: 'Seguimiento',
  D: 'Descartado',
};

/**
 * Allow-list de `resultado` por contexto, **réplica exacta del `Vocabulario`
 * del backend**. Enviar un código fuera de su contexto responde 400 con
 * _"Resultado no valido para {contexto}: {codigo}"_, así que el formulario solo
 * ofrece los que el backend acepta.
 *
 * Un contexto desconocido cae en OPORTUNIDAD, igual que el `default` del
 * `switch` del service.
 */
const RESULTADOS_POR_CONTEXTO: Readonly<Record<string, readonly string[]>> = {
  PROSPECCION: [
    'CONTACTADO',
    'REUNION_AGENDADA',
    'PROPUESTA_ENVIADA',
    'ACEPTA_CAPTAR',
    'NO_ACEPTA',
    'RECONTACTAR',
  ],
  CAPTACION: [
    'DOCS_SOLICITADOS',
    'CONDICIONES_AJUSTADAS',
    'PUBLICACION_COORDINADA',
    'PROPIETARIO_OBSERVA',
    'LISTO_PARA_PUBLICAR',
    'PAUSAR_GESTION',
  ],
  CLIENTE: [
    'BUSQUEDA_LEVANTADA',
    'PROPUESTA_ENVIADA',
    'REQUIERE_OPCIONES',
    'NO_RESPONDE',
    'SEGUIMIENTO',
    'DESCARTADO',
  ],
  OPORTUNIDAD: [
    'INTERESADO',
    'VISITA_AGENDADA',
    'OFERTA_SOLICITADA',
    'NEGOCIANDO',
    'NO_INTERESADO',
    'DESCARTADO',
  ],
};

/** Los resultados que el backend acepta para ese contexto, como opciones. */
export function resultadosDe(contexto: string | null | undefined): OpcionFiltro[] {
  const clave = (contexto ?? '').trim().toUpperCase();
  const codigos = RESULTADOS_POR_CONTEXTO[clave] ?? RESULTADOS_POR_CONTEXTO['OPORTUNIDAD'];
  return codigos.map((valor) => ({
    valor,
    etiqueta: describir(RESULTADO_INTERACCION, valor),
  }));
}

/**
 * Resultados que significan "el cliente no continúa". El backend los reconoce
 * en sus **dos formas** (la corta heredada y la palabra del contexto), y los
 * usa para cerrar la oportunidad cuando aparecen como desenlace de una visita:
 * la pantalla pide entonces la razón tipificada.
 */
const NO_CONTINUIDAD = new Set(['N', 'D', 'NO_INTERESADO', 'DESCARTADO']);

export function implicaNoContinuidad(resultado: string | null | undefined): boolean {
  return !!resultado && NO_CONTINUIDAD.has(resultado);
}

/** Descripción de un código. Desconocido o vacío: se devuelve tal cual. */
export function describir(catalogo: Catalogo, codigo: string | null | undefined): string {
  if (!codigo) {
    return '';
  }
  return catalogo[codigo] ?? codigo;
}

/**
 * Todas las opciones de un catálogo, **en el orden en que están declaradas**.
 *
 * Es lo contrario de `opcionesPresentes`: sirve para los formularios de alta,
 * donde hay que ofrecer el dominio entero aunque los datos actuales no lo
 * cubran. Para filtrar listados sigue mandando `opcionesPresentes`.
 */
export function opcionesDe(catalogo: Catalogo): OpcionFiltro[] {
  return Object.entries(catalogo).map(([valor, etiqueta]) => ({ valor, etiqueta }));
}

/**
 * Opciones para un `cl-filtro-select`, **acotadas a los códigos presentes en
 * los datos**. Es la convención data-driven del legado: ofrecer un filtro que
 * no devuelve nada es peor que no ofrecerlo.
 */
export function opcionesPresentes(
  catalogo: Catalogo,
  codigos: readonly (string | null | undefined)[],
): OpcionFiltro[] {
  const presentes = new Set(codigos.filter((c): c is string => !!c));
  return [...presentes]
    .map((valor) => ({ valor, etiqueta: describir(catalogo, valor) }))
    .sort((a, b) => a.etiqueta.localeCompare(b.etiqueta, 'es'));
}
