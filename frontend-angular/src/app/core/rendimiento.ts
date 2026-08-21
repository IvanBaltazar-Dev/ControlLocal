import { EstadoRitmo, KpiCanonico, MotivoSinBase, Rendimiento } from './api/indicadores.service';

/**
 * Cómo se **dice** y cómo se **dibuja** un KPI canónico.
 *
 * ## Por qué está aquí y no en cada pantalla
 *
 * El pie del Inicio y la pantalla de Indicadores enseñan los mismos cuatro
 * indicadores, y D-E2-1 §6.2 lo exige explícitamente: *«por construcción no
 * puede contradecir a Indicadores»*. Si cada componente escribiera su propia
 * frase, la contradicción tardaría exactamente una tarde en aparecer — el pie
 * diría «A 5 de la meta» y el círculo «faltan 5 captaciones» sobre el mismo
 * número, y nadie sabría cuál mirar.
 *
 * ## Lo que este archivo NO hace
 *
 * No decide nada. Los estados, las metas, los prorrateos y el semáforo llegan
 * resueltos del dominio. Aquí solo se traducen a palabras y a porcentajes de
 * ancho: es presentación, y por eso vive en el SPA. El día que un umbral se
 * escriba en este archivo, E1 vuelve a estar roto.
 */

/**
 * Qué porción de la barra o del arco lleva recorrida un KPI.
 *
 * Se acota a 100 para poder dibujarla: pasarse de la meta es una buena noticia,
 * no un arco que se sale de la caja. El número real sigue al lado, sin acotar.
 */
export function avanceDe(kpi: KpiCanonico): number {
  if (kpi.porcentajeMeta == null) {
    return 0;
  }
  return Math.max(0, Math.min(100, kpi.porcentajeMeta));
}

/**
 * Dónde cae la marca del **ritmo esperado a hoy**, en porcentaje del recorrido.
 *
 * Sin ella, un 79 % no dice si vas por delante o por detrás — es la marca que
 * pide D-E2-2 §3. Devuelve `null` cuando la meta no tiene cadencia diaria: el
 * backend ya lo declara con `sinCadencia`, y repartir una meta de 2 contratos
 * por días inventaría un ritmo que el negocio no tiene.
 */
export function marcaEsperadaDe(kpi: KpiCanonico): number | null {
  if (kpi.metaEsperadaAHoy == null || kpi.metaPeriodo == null || kpi.metaPeriodo <= 0) {
    return null;
  }
  return Math.max(0, Math.min(100, Math.round((kpi.metaEsperadaAHoy / kpi.metaPeriodo) * 100)));
}

/**
 * El par `actual de meta`, o solo el actual cuando nadie fijó meta.
 *
 * **Sin meta no se pinta un cero.** «19 de 0» diría «tu objetivo era cero y lo
 * cumpliste», que es lo contrario de «nadie te fijó meta». Misma regla que E2.0
 * fijó para la conversión sin muestra.
 */
export function cifraDe(kpi: KpiCanonico): string {
  return kpi.metaPeriodo == null ? String(kpi.actual) : `${kpi.actual} de ${kpi.metaPeriodo}`;
}

/**
 * La línea de lectura: **primero a cuánto estás de la meta**, y después el
 * ritmo (criterio de aceptación de D-E2-1).
 *
 * @param esAgente cambia la voz, no los números. Al broker no se le dice «hoy
 * deberías ir por 21»: le atribuiría una producción personal que él no hace
 * (D-E2-2, instrucción 4). Su línea habla del equipo.
 */
export function lecturaDe(kpi: KpiCanonico, esAgente: boolean): string {
  if (kpi.estadoRitmo === 'SIN_BASE') {
    return porQueSinBase(kpi.motivoSinBase);
  }
  const falta =
    kpi.faltante == null || kpi.faltante === 0 ? 'Meta cumplida' : `A ${kpi.faltante} de la meta`;
  if (!esAgente) {
    return `${falta} · ${vozDelRitmoDelEquipo(kpi.estadoRitmo)}`;
  }
  if (kpi.metaEsperadaAHoy == null) {
    return falta;
  }
  return `${falta} · hoy deberías ir por ${kpi.metaEsperadaAHoy}`;
}

/**
 * **La meta y lo que falta**, en una línea.
 *
 * Es la primera mitad de `lecturaDe`, publicada aparte porque Indicadores la
 * enseña en su propio renglón mientras el pie del Inicio la encadena con el
 * ritmo. Las dos salen de aquí para que no puedan divergir: si mañana «te
 * faltan» pasa a decirse de otra manera, cambia en un sitio.
 *
 * Dice **la meta primero y la deuda después** — «Meta 24 · te faltan 5» —, no el
 * porcentaje: lo que mueve es lo cerca que estás, no un 79 %.
 *
 * @param esAgente cambia la voz, no los números. Al broker no se le atribuye la
 * producción del equipo como si fuera suya (D-E2-2, instrucción 4).
 */
export function metaDe(kpi: KpiCanonico, esAgente: boolean): string {
  if (kpi.metaPeriodo == null) {
    return porQueSinBase(kpi.motivoSinBase);
  }
  const cabeza = `Meta ${kpi.metaPeriodo}`;
  const falta = kpi.metaPeriodo - kpi.actual;
  if (falta === 0) {
    return `${cabeza} · alcanzada`;
  }
  if (falta < 0) {
    return `${cabeza} · superada por ${-falta}`;
  }
  return `${cabeza} · ${esAgente ? 'te faltan' : 'faltan'} ${falta}`;
}

/**
 * **Dónde tocaría estar hoy.** No es una segunda meta: es la del periodo medida
 * hasta hoy, y por eso no se rotula «meta» — dos líneas seguidas empezando por
 * esa palabra hacían leer que había dos objetivos y que el de hoy ya estaba
 * cumplido, que es lo contrario de lo que hay que entender.
 *
 * Devuelve cadena vacía cuando no hay nada que decir; el adelanto lo dice
 * `adelantoDe`, que es una marca y no una frase.
 */
export function ritmoEsperadoDe(kpi: KpiCanonico, esAgente: boolean): string {
  if (kpi.metaPeriodo == null) {
    return 'No hay ritmo que evaluar';
  }
  if (kpi.sinCadencia) {
    // Repartir una meta de dos contratos por días inventaría una cadencia que el
    // negocio no tiene. Lo declara el dominio, no un umbral de aquí.
    return 'Muy pocos para medir ritmo';
  }
  if (kpi.motivoSinBase === 'PERIODO_SIN_RECORRIDO') {
    return 'El periodo recién empieza';
  }
  if (kpi.metaEsperadaAHoy == null) {
    return '';
  }
  return esAgente
    ? `Hoy deberías ir por ${kpi.metaEsperadaAHoy}`
    : `Hoy el equipo debería ir por ${kpi.metaEsperadaAHoy}`;
}

/**
 * El adelanto o el retraso contra el calendario, con su signo.
 *
 * Contesta «¿y esto es bueno?» sin que el lector reste. **Convive con «te faltan
 * 5»**: una habla del calendario y la otra del objetivo, y las dos son ciertas a
 * la vez. `null` cuando no hay referencia contra la que compararse —sin meta,
 * sin cadencia o con el periodo recién empezado—, porque entonces la resta no
 * significa nada.
 *
 * Es aritmética sobre dos hechos que ya llegan decididos, no una clasificación:
 * el veredicto lo sigue dando `estadoRitmo`.
 */
export function adelantoDe(kpi: KpiCanonico): number | null {
  if (kpi.metaEsperadaAHoy == null || kpi.metaPeriodo == null || kpi.sinCadencia) {
    return null;
  }
  if (kpi.motivoSinBase === 'PERIODO_SIN_RECORRIDO') {
    return null;
  }
  return kpi.actual - kpi.metaEsperadaAHoy;
}

/**
 * Por qué no concluye.
 *
 * «Sin base» a secas obliga a adivinar si falta la meta propia, la de un
 * compañero o el mes entero — y las tres se arreglan de forma distinta.
 */
export function porQueSinBase(motivo: MotivoSinBase): string {
  switch (motivo) {
    case 'SIN_META':
      return 'Sin meta fijada para este mes';
    case 'COBERTURA_INCOMPLETA':
      return 'Falta la meta de algún agente del equipo';
    case 'PERIODO_SIN_RECORRIDO':
      return 'El mes todavía no ha empezado';
    default:
      return 'Sin base para concluir';
  }
}

/** El mismo estado, dicho del equipo y no de una persona. */
export function vozDelRitmoDelEquipo(estado: EstadoRitmo): string {
  switch (estado) {
    case 'EN_RITMO':
      return 'el equipo va en ritmo';
    case 'ATENCION':
      return 'el equipo va justo';
    case 'FUERA_DE_RITMO':
      return 'el equipo va por detrás';
    default:
      return 'sin base para concluir';
  }
}

/** El estado en palabras, para rotular sin repetir el código del dominio. */
export function vozDelRitmo(estado: EstadoRitmo): string {
  switch (estado) {
    case 'EN_RITMO':
      return 'En ritmo';
    case 'ATENCION':
      return 'Atención';
    case 'FUERA_DE_RITMO':
      return 'Fuera de ritmo';
    default:
      return 'Sin base suficiente';
  }
}

/**
 * La variación contra el mes anterior, ya con su signo.
 *
 * `null` cuando no hay con qué comparar: un «+0» sugiere que se midió y no
 * cambió nada, que es distinto de no haber medido.
 */
export function variacionDe(kpi: KpiCanonico): string | null {
  if (kpi.variacionComparable == null) {
    return null;
  }
  if (kpi.variacionComparable === 0) {
    return 'igual que el mes pasado';
  }
  const signo = kpi.variacionComparable > 0 ? '+' : '';
  return `${signo}${kpi.variacionComparable} frente al mes pasado`;
}

/**
 * «hace 2 min», contra el instante que **declara el backend**.
 *
 * `generadoEn` tiene un solo productor en todo el sistema. Mirar el reloj del
 * navegador diría «hace 0 min» incluso sobre una respuesta de hace un cuarto de
 * hora servida desde una caché.
 */
export function frescuraDe(rendimiento: Rendimiento | null, ahora = Date.now()): string | null {
  if (!rendimiento?.generadoEn) {
    return null;
  }
  const minutos = Math.max(0, Math.round((ahora - Date.parse(rendimiento.generadoEn)) / 60000));
  if (minutos < 1) {
    return 'hace un momento';
  }
  if (minutos < 60) {
    return `hace ${minutos} min`;
  }
  const horas = Math.round(minutos / 60);
  return horas < 24 ? `hace ${horas} h` : 'hace más de un día';
}

/**
 * La cifra en juego, ya redactada.
 *
 * Cero operaciones **no se esconde**: «Ninguna operación puede cerrarse este
 * mes» es información, y ocultarlo dejaría el hueco a que alguien lo leyera como
 * un fallo de carga.
 */
export interface CierreLegible {
  /** `null` cuando no hay ninguna operación: no hay cifra que dar. */
  importe: string | null;
  detalle: string;
  /** Con dos monedas se avisa; sumarlas necesitaría un tipo de cambio. */
  variasMonedas: boolean;
  esperanDecision: number;
}

export function cierreLegible(rendimiento: Rendimiento | null): CierreLegible | null {
  const cierre = rendimiento?.puedeCerrarse;
  if (!cierre) {
    return null;
  }
  if (cierre.operaciones === 0) {
    return {
      importe: null,
      detalle: 'Ninguna operación puede cerrarse este mes',
      variasMonedas: false,
      esperanDecision: cierre.esperanDecision,
    };
  }
  const unidad = cierre.operaciones === 1 ? 'operación' : 'operaciones';
  return {
    importe: `${cierre.moneda ?? ''} ${cierre.importe.toLocaleString('es-PE')}`.trim(),
    detalle: `${cierre.operaciones} ${unidad} · renta mensual`,
    variasMonedas: cierre.variasMonedas,
    esperanDecision: cierre.esperanDecision,
  };
}
