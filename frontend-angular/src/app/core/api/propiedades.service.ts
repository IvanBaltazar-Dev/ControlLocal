import { inject, Injectable } from '@angular/core';
import { ApiClient } from './api.client';
import { Publicacion } from './encargos.service';
import { PageResponse } from './api.types';

/**
 * Un encargo tal como se ve en una lista: su operación y lo que se pide.
 *
 * **`importe` no es «el precio de la propiedad».** Es el de ESTE encargo. Una
 * propiedad en venta y en alquiler trae dos, y son dos números que no se pueden
 * comparar entre sí ni sumar.
 */
export interface EncargoEnLista {
  /** VENTA o ALQUILER. Nunca un valor combinado: `AMBAS` no existe. */
  operacion: string;
  /** P pendiente · O observada · A activa. */
  estado: string;
  importe?: number | null;
  moneda?: string | null;
}

/** Una fila del listado universal. */
export interface FilaPropiedad {
  id: number;
  codigo: string;
  /** El nombre del valor: `LOCAL`, `DEPARTAMENTO`… Es lo que viaja, no lo que se lee. */
  tipoPropiedad: string;
  /**
   * El rótulo, ya escrito para una persona: «Local comercial», «Almacén».
   *
   * Viene del backend a propósito. Un `switch` aquí que convirtiera `LOCAL` en
   * «Local comercial» sería la matriz «tipo → texto» viviendo en la interfaz, y
   * con dos interfaces habría dos (D-A-1 §6).
   */
  tipoRotulo: string;
  uso?: string | null;
  direccion: string;
  distrito: string;
  metraje?: number | null;
  /** D disponible · N no disponible · I inactiva. */
  estado: string;
  idPropietario?: number | null;
  /** El **representante** de la titularidad, no «el propietario». */
  propietarioNombre?: string | null;
  /** Cuántos titulares vigentes. Con más de uno, la ficha los detalla. */
  titulares: number;
  encargos: EncargoEnLista[];
  fechaRegistro?: string | null;
}

export interface FiltrosPropiedades {
  pagina?: number;
  tamano?: number;
  texto?: string;
  tipoPropiedad?: string;
  distrito?: string;
  estado?: string;
  /**
   * Las que la propiedad tiene **vivas**. Con `VENTA,ALQUILER` significa «tiene
   * las dos», no «tiene alguna»: es el filtro que sirve para encontrar
   * exactamente esas.
   */
  operaciones?: string;
}

/** Lo que el filtro puede ofrecer sin inventarse opciones que no existan. */
export interface OpcionesDeFiltro {
  distritos: string[];
}

// ====================================================================
// La ficha
// ====================================================================

/** Un titular con la parte que le corresponde. Las cuotas vigentes suman 100. */
export interface TitularPropiedad {
  idPropietario: number;
  nombre: string;
  /** Porcentaje. Con un solo titular es 100 y no hace falta enseñarlo. */
  cuota?: number | null;
  /** Con quién se habla. Si nadie está marcado, es el primero. */
  representante: boolean;
  desde?: string | null;
}

/**
 * Una característica, con su etiqueta ya escrita.
 *
 * `rotulo`, `unidad` y `tipoDato` vienen del catálogo. **Esta pantalla no sabe
 * qué características tiene cada tipo de propiedad** —eso lo decide el
 * catálogo— y por eso las pinta todas igual, sin conocer ninguna por su nombre.
 */
export interface AtributoPropiedad {
  clave: string;
  rotulo: string;
  tipoDato?: string | null;
  unidad?: string | null;
  /**
   * El texto **ya compuesto**: «PEN 350», «Cocina, Lavadora». Es lo que una
   * ficha pinta, y lo que un editor **no** puede partir de vuelta sin inferir.
   */
  valor?: string | null;
  /** La moneda de un IMPORTE, cruda. Para poder corregirlo (V77). */
  moneda?: string | null;
  /** Los elementos de un LISTA_MULTIPLE, crudos. Misma razón. */
  valores?: string[] | null;
  /**
   * `VIGENTE` si el valor forma parte de lo que **hoy** se pregunta;
   * `HISTORICO` si existe, se conserva y ya no.
   *
   * Un dato sale del contrato de dos maneras distintas —la clave se retiró del
   * catálogo, o la clave sigue viva pero ya no aplica a este tipo de
   * propiedad— y para quien lee la ficha son la misma cosa: está escrito y no
   * se puede corregir. Por eso el Core publica el **estado**, no cuál de las
   * dos ocurrió.
   *
   * **Esta pantalla no lo deduce.** No conoce ninguna clave por su nombre ni
   * lleva una lista de campos no editables: qué pertenece al contrato lo decide
   * el catálogo, y deducirlo aquí sería una segunda deducción que se separa de
   * la de KAIROS.
   */
  estadoDato?: 'VIGENTE' | 'HISTORICO';
  /**
   * Si el Core aceptaría corregirlo. Es exactamente lo que contestará el `PUT`,
   * que lo vuelve a comprobar: esta señal evita ofrecer lo imposible, **no**
   * sustituye a la validación del servidor.
   *
   * Viaja siempre —es un booleano primitivo y `NON_NULL` no omite `false`—,
   * pero se declara opcional porque un cliente compilado contra una respuesta
   * sin el campo lo leería como `undefined`: se compara con `=== false`.
   */
  editable?: boolean;
  /** Por qué no se puede corregir, **ya redactado**. Ausente si se puede. */
  motivoNoEditable?: string | null;
}

/** Un hito de la serie económica de un encargo. */
export interface HitoEncargo {
  /** E esperado · R recomendado · U autorizado · P publicado · O ofertado · A aceptado · C cerrado. */
  hito: string;
  /** «Autorizado», «Publicado»… Lo publica el backend; aquí no se traduce. */
  hitoRotulo: string;
  monto?: number | null;
  moneda?: string | null;
  fecha?: string | null;
}

/** Una clave obligatoria que falta, con su nombre legible. */
export interface AtributoQueFalta {
  clave: string;
  /** La palabra del catálogo. Es lo que se enseña: nunca la clave. */
  rotulo: string;
}

/**
 * **Un encargo: la relación comercial, con su precio y su histórico.**
 *
 * La identidad es `idEncargo`, **nunca `operacion`**. Una propiedad puede haber
 * tenido tres alquileres sucesivos —2024 cerrado, 2025 cerrado, 2026 vigente— y
 * agruparlos por operación fundiría tres series económicas que no tienen nada
 * que ver. Lo que la base prohíbe es dos **vivos** de la misma operación.
 *
 * Todos los rótulos vienen del backend. No hay ninguno que se componga aquí:
 * decidir que un importe de venta se llama «precio» y uno de alquiler «renta»
 * es semántica inmobiliaria, y vive en el Core (D-A-1 §5).
 */
export interface EncargoPropiedad {
  idEncargo: number;
  codigo: string;
  /** VENTA o ALQUILER. El valor, para comparar; nunca para escribir en pantalla. */
  operacion: string;
  /** «Venta», «Alquiler». Lo que se lee. */
  operacionRotulo: string;
  estado: string;
  estadoRotulo: string;
  /** Si sigue en juego. Los cerrados también llegan: su histórico vive aquí. */
  vivo: boolean;
  importe?: number | null;
  moneda?: string | null;
  /** «precio de venta» o «renta mensual», según la operación. Lo dice el Core. */
  importeRotulo: string;
  exclusividad?: boolean | null;
  idAgente?: number | null;
  agenteNombre?: string | null;
  inicio?: string | null;
  fin?: string | null;
  /**
   * **Sólo el suyo.** Dos encargos nunca comparten serie.
   *
   * **Puede no viajar** (D-P0-6). La serie económica de un encargo la leen su
   * propio agente y el bróker que lo alcanza; el TENANT_ADMIN no, porque
   * gobernar no es operar. Jackson va `NON_NULL`, así que ausente llega como
   * `undefined` y **la comparación correcta es `== null`**.
   *
   * **Ausente significa «no disponible para ti», nunca «vacío».** Una serie sin
   * movimientos y una serie que no corresponde son dos hechos distintos, y
   * pintarlas igual afirmaría que este encargo no ha tenido ninguno. El resto
   * del bloque —importe vigente, exclusividad, condiciones, anuncios y
   * `puedeEditar`— viaja siempre: no poder ver lo que se pidió en 2023 no es
   * que el encargo no exista.
   */
  historico?: HitoEncargo[] | null;
  /**
   * **Sus anuncios.** Igual que el histórico: son de este encargo, no de la
   * propiedad. El de la venta no aparece ni se modifica desde el del alquiler.
   */
  publicaciones: Publicacion[];
  /**
   * Si se puede gestionar la publicación de este encargo, y si no, por qué.
   *
   * Llega como **capacidad** para que esta pantalla no escriba
   * `encargo.estado === 'A'` ni tenga que saber que un encargo cerrado no se
   * publica: eso es una regla de negocio y su dueño es el Core (D-A-1 §5). El
   * backend la vuelve a imponer al escribir.
   */
  publicacionGestionable?: GestionDePublicacion | null;
  /**
   * **Lo pactado en ESTE encargo** (Corte 0C): garantía, adelanto, si acepta
   * mascotas… Son del encargo y no de la propiedad: dos alquileres sucesivos
   * del mismo inmueble pueden pactar cosas distintas. Mismo formato que los
   * atributos de la propiedad, clave **sin calificar**.
   */
  condiciones?: AtributoPropiedad[];
  /** Lo obligatorio para publicar este encargo que todavía no tiene valor. */
  faltanParaPublicar?: AtributoQueFalta[];
  /**
   * Si quien está mirando puede tocar **este** encargo (P0-4): su importe, su
   * exclusividad, su vigencia, sus condiciones y sus anuncios.
   *
   * Es la autoridad del **encargo**, no la de la propiedad, y son distintas a
   * propósito: el responsable del inmueble no manda sobre el encargo ajeno, y
   * el agente del alquiler no manda sobre la venta. Antes esto se deducía del
   * rol de la sesión, y por eso todos los agentes del tenant veían el botón.
   */
  puedeEditar?: boolean;
}

// ====================================================================
// La edición: `PUT /propiedades/{id}`
// ====================================================================

/**
 * **Lo que se manda a `PUT /propiedades/{id}`, y sólo lo que cambió.**
 *
 * La semántica entera, que es la invariante del Corte 0A:
 *
 * ```
 *   bloque ausente             = no tocar
 *   campo ausente              = no tocar
 *   campo con valor            = cambiar a ese valor
 *   clave en atributosABorrar  = retirar el valor
 * ```
 *
 * Por eso **todo es opcional**: el editor construye este objeto con lo que la
 * persona tocó y nada más. Mandar un bloque «para completar» es como se pierden
 * datos que nadie quería cambiar; y `''` no es una forma de borrar — el Core lo
 * rechaza, porque entre «no lo sé» y «quítalo» no se adivina.
 *
 * Lo que este cable **no** transporta, y el editor no ofrece: el tipo, el
 * código, el uso, el estado del registro, cerrar un encargo o cambiarle la
 * operación. Cada una de esas cosas es otro caso de uso con su propio endpoint
 * y su propio rol; reescribirlas desde aquí reescribiría historia.
 */
export interface EdicionPropiedad {
  descripcion?: string;
  /** Fusión campo a campo: dentro del bloque, lo que no viene tampoco se toca. */
  ubicacion?: UbicacionEnEdicion;
  /**
   * **El conjunto completo**, no un delta. Si viaja, el Core concilia la
   * titularidad entera contra él: cierra las vigencias que falten y abre las
   * nuevas. Una lista vacía deja la propiedad **sin titular** —legítimo desde
   * V76, pero es una intención, no un descuido—. Por eso sólo se manda cuando
   * la persona tocó el bloque.
   */
  titulares?: TitularEnEdicion[];
  atributos?: AtributoEnEdicion[];
  /**
   * Uno por **operación** del encargo vivo. Se dirige por operación y no por
   * id porque la base garantiza un solo encargo vivo por operación
   * (`uq_captacion_viva_por_operacion`); si no hay ninguno vivo de esa
   * operación, el Core rechaza en vez de crear. El importe y la moneda son
   * obligatorios en el bloque; si no cambiaron, no producen hito.
   */
  operaciones?: OperacionEnEdicion[];
  /**
   * Claves **lógicas** a retirar: `piso`, `descripcion`, `interiorUnidad`…
   * Quien pide retirar dice el nombre y el Core enruta por la autoridad del
   * dato. Una clave con valor y en esta lista a la vez es un error.
   */
  atributosABorrar?: string[];
  /** Un bloque por `idEncargo`: lo pactado en ese encargo y sólo en ese. */
  condiciones?: CondicionesDeEncargoEnEdicion[];
}

export interface UbicacionEnEdicion {
  direccion?: string;
  distrito?: string;
  zonaUrbanizacion?: string;
  latitud?: number;
  longitud?: number;
  interiorUnidad?: string;
  piso?: string;
  referenciaInterna?: string;
  nombreEdificioGaleria?: string;
}

/**
 * Los campos de la ubicación **tal como los nombra el cable**. No es el
 * catálogo —es la forma del `UbicacionRequest`— y por eso el editor puede
 * conocerlos: es lo mínimo para saber en qué hueco del cuerpo va cada
 * respuesta. Lo que no esté aquí ni sea `descripcion` viaja como atributo y
 * lo enruta el Core.
 */
export const CAMPOS_DE_UBICACION: readonly (keyof UbicacionEnEdicion)[] = [
  'direccion',
  'distrito',
  'zonaUrbanizacion',
  'latitud',
  'longitud',
  'interiorUnidad',
  'piso',
  'referenciaInterna',
  'nombreEdificioGaleria',
];

export interface TitularEnEdicion {
  idPropietario: number;
  /** Porcentaje. Un titular único puede no declararla: es el 100 %. */
  cuota?: number | null;
  representante: boolean;
}

export interface AtributoEnEdicion {
  clave: string;
  valor?: string;
  /** Obligatoria cuando la clave es un IMPORTE: el Core la exige. */
  moneda?: string;
  /** Los elementos de un multivalor. **Sustituyen** a los que hubiera. */
  valores?: string[];
}

export interface OperacionEnEdicion {
  /** VENTA o ALQUILER, con palabras. Nunca un valor combinado. */
  operacion: string;
  importe: number;
  moneda: string;
  exclusividad?: boolean;
  inicioEncargo?: string;
  finEncargo?: string;
}

/**
 * Lo que el cable del encargo transporta **además** de sus condiciones
 * gobernadas, con el nombre con que el motor de captura lo pregunta. Igual que
 * `CAMPOS_DE_UBICACION`: es la forma del `OperacionRequest`, no el catálogo.
 * El editor lo usa para separar, dentro del bloque de una operación, lo que va
 * en `operaciones[]` de lo que va en `condiciones[]`.
 */
export const CAMPOS_ECONOMICOS_DEL_ENCARGO: readonly string[] = ['importe', 'moneda', 'exclusividad'];

export interface CondicionesDeEncargoEnEdicion {
  idEncargo: number;
  atributos?: AtributoEnEdicion[];
  atributosABorrar?: string[];
}

/** Si se puede publicar este encargo, y el hecho —no el tono— de por qué no. */
export interface GestionDePublicacion {
  permitida: boolean;
  motivo?: string | null;
}

/**
 * Un hecho comercial **con la constancia de dónde viene**.
 *
 * `idEncargo` lo pone el backend. Es lo que impide que la actividad vuelva a
 * mezclar lo que los dos primeros bloques separaron: una visita de quien quiere
 * comprar y otra de quien quiere alquilar la misma propiedad se leen igual en
 * una lista plana.
 */
export interface HechoDeActividad {
  /** OPORTUNIDAD · VISITA · INTERACCION · EXPEDIENTE · CONTRATO. */
  proceso: string;
  id: number;
  codigo?: string | null;
  /** El hecho en una línea, ya escrito por el Core. */
  titulo: string;
  detalle?: string | null;
  estado?: string | null;
  estadoRotulo?: string | null;
  fecha?: string | null;
  /** El importe del hecho, cuando lo tiene. Llega como número: lo formatea la pantalla. */
  monto?: number | null;
  moneda?: string | null;
  /** De qué encargo nace. Nunca se pierde. */
  idEncargo?: number | null;
  /** VENTA o ALQUILER: el valor, para comparar. */
  operacion?: string | null;
  /** «Venta», «Alquiler»: lo que se lee. Tampoco esto se traduce aquí. */
  operacionRotulo?: string | null;
  /** Dónde se abre, en el vocabulario del SPA. */
  ruta?: string | null;
}

/** Un importe con su fecha y el encargo del que sale. */
export interface ImporteFechado {
  monto?: number | null;
  moneda?: string | null;
  fecha?: string | null;
  /** De qué episodio sale esta cifra. Viaja incluso en un dato agregado. */
  idEncargo?: number | null;
  codigoEncargo?: string | null;
}

/**
 * Qué ha pasado con esta propiedad en **una** operación a lo largo del tiempo.
 *
 * `ultimoPedido` y `ultimoCierre` **no son el mismo dato**: uno es lo que se
 * pidió y otro lo que se cerró de verdad. Cuando no hubo cierre llega `null`, y
 * la pantalla lo dice; no cae al precio pedido, que convertiría «lo que
 * pedíamos» en «lo que vale».
 */
export interface EpisodiosDeOperacion {
  operacion: string;
  operacionRotulo: string;
  /** Cuántos encargos de esta operación ha tenido, vivos y cerrados. */
  veces: number;
  desde?: string | null;
  /** Cuándo terminó el último. `null` mientras siga vivo. */
  hasta?: string | null;
  vivoAhora: boolean;
  ultimoPedido?: ImporteFechado | null;
  ultimoCierre?: ImporteFechado | null;
}

/** Un movimiento económico del inmueble, con su procedencia intacta. */
export interface HitoDeLaHistoria {
  fecha?: string | null;
  hito: string;
  hitoRotulo: string;
  monto?: number | null;
  moneda?: string | null;
  idEncargo?: number | null;
  codigoEncargo?: string | null;
  operacion?: string | null;
  operacionRotulo?: string | null;
}

/**
 * **La memoria del inmueble.** Un nivel de lectura distinto del encargo:
 *
 * - `idEncargo` — la identidad técnica de UN episodio comercial.
 * - `idPropiedad` — la continuidad histórica del inmueble.
 *
 * Los bloques de encargo sirven para auditar y negociar: este importe, este
 * agente, esta serie. Esto contesta otra pregunta —«¿a cuánto se alquiló la
 * última vez?», «¿cuántas veces estuvo en venta?»— que un CRM de operaciones
 * vivas no sabe responder.
 *
 * **No fusiona históricos: los agrega para leerlos.** Cada cifra sigue
 * apuntando a su `idEncargo`, así que de cualquier dato de la historia se puede
 * volver al episodio que lo produjo.
 */
export interface HistoriaComercial {
  porOperacion: EpisodiosDeOperacion[];
  /** Todos los movimientos, del más reciente al más antiguo, cruzando encargos. */
  linea: HitoDeLaHistoria[];
}

/** Lo que ha pasado con la propiedad, repartido por proceso. */
export interface ActividadPropiedad {
  oportunidades: HechoDeActividad[];
  visitas: HechoDeActividad[];
  interacciones: HechoDeActividad[];
  expedientes: HechoDeActividad[];
  contratos: HechoDeActividad[];
}

export interface UbicacionPropiedad {
  direccion?: string | null;
  distrito?: string | null;
  zonaUrbanizacion?: string | null;
  latitud?: number | null;
  longitud?: number | null;
  interiorUnidad?: string | null;
  piso?: string | null;
  referenciaInterna?: string | null;
  nombreEdificioGaleria?: string | null;
}

/**
 * **La propiedad entera, leída por el modelo universal.**
 *
 * Tres conceptos que no se vuelven a mezclar:
 *
 * 1. **la cosa física** — tipo, ubicación, características, titulares;
 * 2. **su gestión comercial** — un `encargo` por relación, con su precio, su
 *    estado, su agente y su histórico;
 * 3. **su actividad** — los hechos, cada uno con el encargo del que nace.
 *
 * **No hay `precio` ni `operacion` en este objeto**, y no es un olvido: no son
 * de la propiedad. Viven en el encargo, que es donde el negocio los pone.
 *
 * **El metraje aparece una sola vez**, entre `atributos`, con su clave lógica
 * `metraje_total`. No hay campo `metraje` suelto: publicarlo además por
 * separado obligaría a esta pantalla a excluirlo de la lista para no enseñarlo
 * dos veces, y esa exclusión es el primer eslabón de una colección de
 * excepciones por clave.
 */
export interface FichaPropiedad {
  id: number;
  codigo: string;
  /** `LOCAL`, `DEPARTAMENTO`… El valor, no lo que se lee. */
  tipoPropiedad: string;
  /** «Local comercial», «Departamento». Lo publica el backend (D-A-1 §6). */
  tipoRotulo: string;
  uso?: string | null;
  usoRotulo?: string | null;
  descripcion?: string | null;
  estadoRegistro?: string | null;
  estadoRegistroRotulo?: string | null;
  disponibilidadComercial?: string | null;
  disponibilidadRotulo?: string | null;
  ubicacion?: UbicacionPropiedad | null;
  titulares: TitularPropiedad[];
  atributos: AtributoPropiedad[];
  /** Todos: los vivos y los cerrados. Un cerrado guarda su histórico. */
  encargos: EncargoPropiedad[];
  /** Lo que impide el ALTA: las claves `ALT` que faltan. */
  atributosQueFaltan: AtributoQueFalta[];
  /**
   * Lo que impide PUBLICAR **esta propiedad**: las `ALT` y las `PUB` que
   * faltan, ya con su rótulo. Sale del mismo criterio de dominio que decide el
   * rechazo, así que la pantalla no interpreta exigencias: las muestra.
   *
   * Es distinto de `encargos[i].faltanParaPublicar`, que es la deuda del
   * ENCARGO. Cada sujeto reporta la suya bajo el mismo nombre.
   */
  faltanParaPublicar: AtributoQueFalta[];
  /**
   * La memoria del inmueble, agregada sobre los encargos que **este actor
   * puede ver**.
   *
   * **Puede no viajar** (D-P0-6): la lee el responsable de la propiedad y el
   * bróker que lo alcanza; el TENANT_ADMIN no, y un agente que no responde por
   * ella tampoco. Si no queda ningún encargo visible, el bloque entero se
   * queda fuera. Con `NON_NULL` llega como `undefined`, así que se compara con
   * **`== null`**.
   *
   * **Ausente significa «no disponible para ti», nunca «no ha pasado nada».**
   * Es la razón de que la pantalla no escriba «sin historia» cuando el bloque
   * no vino: sería afirmar un hecho comercial que nadie midió.
   */
  historia?: HistoriaComercial | null;
  /**
   * Los hechos de **esos mismos encargos visibles** (D-P0-6).
   *
   * Mismo contrato que `historia`: **puede no viajar**, ausente es `undefined`,
   * se compara con **`== null`** y significa «no disponible para ti», no «esta
   * propiedad no tiene actividad».
   */
  actividad?: ActividadPropiedad | null;
  fechaRegistro?: string | null;
  /**
   * **Quién responde por la propiedad, y qué puede hacer quien está mirando**
   * (P0).
   *
   * Llega **resuelto por el backend**, no en piezas para que la pantalla lo
   * componga. Es deliberado: si el SPA calculara «puedo editar» comparando el
   * rol de la sesión con el responsable, existirían dos copias de una regla de
   * autoridad —una aquí y otra en el Core— y divergirían hacia el lado peor,
   * el de pintar un botón que el backend va a rechazar. Aquí lo decide el
   * mismo método que después deniega el PUT.
   *
   * Por eso esta pantalla **no lleva ninguna lista de roles ni de claves**.
   */
  responsabilidad?: Responsabilidad | null;
}

/**
 * La autoridad de escritura sobre una propiedad, tal como el cable la cuenta.
 *
 * Jackson viaja `NON_NULL`, así que un campo nulo **no llega**: aquí es
 * `undefined` y no `null`. De ahí que todo sea opcional y que la comparación
 * correcta sea `== null` — `=== null` no ve el caso real.
 */
export interface Responsabilidad {
  /**
   * El rol del agente que responde hoy. Ausente = **FALTANTE**: no se sabe, y
   * eso no es lo mismo que «de todos». La propiedad se ve igual; lo que no se
   * puede es escribirla.
   */
  idResponsable?: number | null;
  nombre?: string | null;
  /** Si **este** usuario puede escribir hechos de la propiedad. */
  puedeEditar: boolean;
  /**
   * El código del rechazo: `FALTA_RESPONSABLE`, `OTRO_RESPONSABLE` o
   * `NO_OPERA`. Ausente cuando sí puede. **No se traduce en el cliente** —para
   * eso viene `motivoTexto`—; sirve para distinguir casos si alguna vista
   * necesita reaccionar distinto, no para redactar.
   */
  motivo?: string | null;
  /** El motivo **en palabras, escrito por el Core**. Es lo que se pinta. */
  motivoTexto?: string | null;
  /**
   * Si **este** usuario puede **iniciar ahora** el cambio de responsable de
   * **esta** propiedad, considerando su responsable actual (C7).
   *
   * Viaja resuelto por la misma razón que `puedeEditar`. Sin él, esta pantalla
   * tendría que llevar su propia copia de la regla de autoridad, que es
   * exactamente lo que este P0 vino a quitar.
   *
   * Lo resuelve el Core con las dos guardas del POST que la ficha **sí** puede
   * mirar: la **banda** —no ser AGENTE— y el alcance sobre el responsable
   * **saliente** (`alcanzaIncluidoSinDueno`), que es el **mismo** predicado que
   * pregunta el POST, y por eso la ficha no promete lo que el POST va a negar.
   * De ahí salen, sin regla nueva, los dos casos que sorprenden: TENANT_ADMIN
   * responde `true` por **autoridad de gobierno del tenant**, no como
   * super-broker —no gana edición de hechos—, y una propiedad **FALTANTE**
   * responde `true` a cualquier BROKER del tenant, porque no hay saliente a
   * quien supervisar.
   *
   * **No conoce el destino y no autoriza nada**: aquí todavía no hay destino
   * elegido, a qué agentes puede pasársela lo acota
   * `GET /propiedades/{id}/responsable/candidatos` —que ya llega depurado para
   * esta propiedad y este actor— y el POST sigue siendo la autoridad final,
   * donde se vuelven a comprobar la banda, el saliente y el destino.
   */
  puedeTraspasar?: boolean;
}

/**
 * **Si se puede escribir, cuando el bloque puede no venir.**
 *
 * Existe porque cada pantalla se estaba inventando su propio valor por defecto
 * y salían contrarios: la ficha caía a `false` y el editor a `true` ante la
 * **misma** respuesta. Y no es un caso teórico — Jackson viaja `NON_NULL`, así
 * que un `responsabilidad` ausente llega como `undefined` de verdad.
 *
 * El defecto es **`false`**, y se elige aquí y no en cada pantalla: si el Core
 * no ha dicho que se puede, no se ofrece. Fallar hacia «no puedes» muestra un
 * botón de menos; fallar hacia «sí puedes» promete una escritura que el backend
 * va a rechazar **después** de que la persona haya escrito.
 */
export function puedeEscribir(responsabilidad?: Responsabilidad | null): boolean {
  return responsabilidad?.puedeEditar ?? false;
}

/**
 * **Por qué no se puede, en las palabras del Core.**
 *
 * Cuando el Core lo dijo, se devuelve tal cual: la pantalla no traduce el
 * código ni redacta el rechazo, porque dos redacciones del mismo rechazo se
 * separan en el primer cambio.
 *
 * El único texto propio es el del caso en que **el Core no dijo nada**, y dice
 * exactamente eso —que no llegó— en vez de fingir un motivo. Es el precio de
 * que el defecto sea `false`: esconder el botón sin explicación obliga a
 * adivinar si falta un permiso o falta un dato.
 */
export function motivoDeBloqueo(responsabilidad?: Responsabilidad | null): string | null {
  if (responsabilidad?.puedeEditar) {
    return null;
  }
  return (
    responsabilidad?.motivoTexto ??
    'No llegó quién responde por esta propiedad, así que no se ofrece editarla. ' +
      'Vuelve a cargar la ficha; si sigue igual, avísale a un broker.'
  );
}

/**
 * **La cartera por el modelo universal.**
 *
 * Distinto de `LocalesService`, que sirve el listado heredado: allí cada fila
 * lleva un precio suelto que no dice de qué operación es, porque la proyección
 * nació cuando todo era alquiler. Aquí cada fila lleva **sus encargos**.
 */
@Injectable({ providedIn: 'root' })
export class PropiedadesService {
  private readonly api = inject(ApiClient);

  listar(filtros: FiltrosPropiedades = {}): Promise<PageResponse<FilaPropiedad>> {
    return this.api.get<PageResponse<FilaPropiedad>>('propiedades', { ...filtros });
  }

  /** Los valores del filtro, sacados de la cartera real. */
  filtros(): Promise<OpcionesDeFiltro> {
    return this.api.get<OpcionesDeFiltro>('propiedades/filtros');
  }

  /**
   * La ficha completa: física, comercial y actividad, en **una** llamada.
   *
   * Es una sola a propósito. Reunir la actividad desde aquí serían tres
   * llamadas por encargo, una más por cada oportunidad para sus visitas, y un
   * barrido de contratos filtrado a mano — y sobre todo obligaría a escribir
   * aquí la regla «la actividad de una propiedad es la de sus encargos», que es
   * dominio (D-A-1).
   */
  consultar(id: number): Promise<FichaPropiedad> {
    return this.api.get<FichaPropiedad>(`propiedades/${id}`);
  }

  /**
   * Edición parcial: lo que no va en `cambios` no se toca. Devuelve la ficha
   * completa ya releída, la misma que `consultar`.
   *
   * @param claveIdempotencia la misma en cada reintento del mismo guardado:
   *        un cambio de importe añade un hito, y un reintento no debe añadir
   *        dos.
   */
  editar(id: number, cambios: EdicionPropiedad, claveIdempotencia?: string): Promise<FichaPropiedad> {
    return this.api.put<FichaPropiedad>(
      `propiedades/${id}`,
      cambios,
      claveIdempotencia ? { 'Idempotency-Key': claveIdempotencia } : undefined,
    );
  }

  /**
   * **Traspasa quién responde por la propiedad** (P0-2).
   *
   * Es la única forma de mover la autoridad de escritura después del alta, y la
   * única de sacar a una propiedad de FALTANTE. Lo ejecuta un BROKER —dentro de
   * su equipo— o el gobierno del tenant; el agente nunca, ni sobre sí mismo.
   *
   * El `motivo` queda en un expediente **append-only** que no se corrige
   * después, y por eso el Core exige el mismo mínimo que la reasignación de un
   * encargo. La pantalla avisa antes de enviar porque es mejor experiencia, no
   * porque sea ella quien lo hace cumplir.
   *
   * **El comando declara sobre qué responsable actúa** (D-P0-9). Un traspaso no
   * es «pon a B», es «cambia A por B»: sin decirlo, dos traspasos que salieran
   * del mismo A —uno hacia B y otro hacia C— terminarían con la última
   * escritura ganando, y el segundo se habría reinterpretado en silencio como
   * «de B a C». Por eso `idResponsableActual` es **obligatorio** y `null` **no
   * es omitirlo**: viaja como `sinResponsableActual: true`, que dice «la vi
   * FALTANTE». Un cuerpo sin ninguna de las dos declaraciones es **400**.
   *
   * Si al ejecutarse el responsable ya no es ese, el Core responde **409** y
   * **no ha escrito nada**: hay que **volver a cargar la ficha** y decidir
   * sobre el estado actual. El traspaso no se reintenta tal cual — sería
   * ejecutar sobre un estado que nadie miró.
   *
   * @param idResponsableActual el responsable que el usuario **vio en la
   *        ficha**, o `null` si la vio sin responsable. No se deduce aquí ni se
   *        vuelve a leer: lo que importa es lo que se estaba mirando al decidir.
   */
  asignarResponsable(
    id: number,
    idAgente: number,
    motivo: string,
    idResponsableActual: number | null,
  ): Promise<Traspaso> {
    return this.api.post<Traspaso>(`propiedades/${id}/responsable`, {
      idAgente,
      motivo,
      idResponsableActual,
      sinResponsableActual: idResponsableActual == null,
    });
  }

  /**
   * **A quién puedo traspasarla**: los destinos ya elegibles para ESTA
   * propiedad y ESTE actor (D-P0-7 + D-P0-12).
   *
   * El Core devuelve la lista **depurada**: mismo tenant, rol AGENTE vigente,
   * cuenta habilitada, relación organizacional viva, estado operativo y —si
   * quien pregunta es un BROKER— supervisión vigente; y sin el responsable
   * actual, porque un traspaso «de A a A» no cuenta ningún hecho. **Aquí no se
   * filtra nada**: depurar en el cliente sería la lista de condiciones de
   * D-P0-7 escrita por segunda vez, y una copia de una regla de autoridad
   * diverge hacia el lado que ofrece lo que el POST va a rechazar.
   *
   * Se **pagina y se busca en el servidor** por la misma razón: la lista es
   * del tenant, no del formulario, así que acotar en el cliente sobre una
   * página devuelve resultados incompletos en cuanto haya más agentes que
   * sitio.
   *
   * **No autoriza nada.** `POST /propiedades/{id}/responsable` revalida banda,
   * tenant, saliente, destino y elegibilidad —entre pedir esta lista y usarla
   * una cuenta se puede suspender—, y por eso las dos preguntas comparten el
   * mismo predicado en el Core.
   *
   * Un actor que no puede iniciar el traspaso recibe **403** y no una lista
   * vacía: «no hay candidatos» y «no te corresponde» son dos respuestas
   * distintas. Una propiedad de otra corredora, **404**.
   */
  candidatos(
    idPropiedad: number,
    texto?: string,
    pagina = 1,
    tamano = 50,
  ): Promise<PageResponse<CandidatoResponsable>> {
    return this.api.get<PageResponse<CandidatoResponsable>>(
      `propiedades/${idPropiedad}/responsable/candidatos`,
      { texto, page: pagina, page_size: tamano },
    );
  }
}

/**
 * Una línea del expediente de traspasos.
 *
 * No la consume ninguna pantalla todavía: el expediente es superficie de
 * **gobierno** (C2) —lo leen BROKER y TENANT_ADMIN, nunca el agente, ni
 * siquiera el responsable vigente— y su pantalla no entra en este corte. Vive
 * aquí porque es lo que devuelve `asignarResponsable`, que sí entra.
 */
export interface Traspaso {
  id: number;
  idPropiedad: number;
  idResponsableAnterior?: number | null;
  responsableAnterior?: string | null;
  idResponsableNuevo: number;
  responsableNuevo?: string | null;
  idPersonaActor: number;
  rolActor: string;
  /** `ALTA` o `TRASPASO`. No se deduce de que falte el anterior (V88). */
  origen: string;
  motivo: string;
  fecha: string;
}

/**
 * **Un destino ya elegible para el traspaso** (D-P0-7 + D-P0-12).
 *
 * Lleva lo justo para elegir en una lista —quién es, su código y su zona— y
 * **ningún estado administrativo**, que no es un olvido: quien aparece cumple
 * las cinco condiciones de elegibilidad, y de quien no aparece no se publica el
 * motivo. Que una cuenta ajena esté suspendida no es dato de un selector de
 * traspaso.
 *
 * `idAgente` es el **`persona_rol.id` del rol AGENTE**, el mismo identificador
 * que espera `asignarResponsable`.
 */
export interface CandidatoResponsable {
  idAgente: number;
  nombre: string;
  codigoAgente?: string;
  zonaAsignada?: string | null;
}
