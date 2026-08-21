import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  AsignacionDeMeta,
  esPeriodo,
  IndicadoresResumen,
  IndicadoresService,
  MetaDeAgente,
  KpiCanonico as KpiCanonicoCable,
  PERIODO_POR_DEFECTO,
  PropuestaDeMeta,
  PERIODOS_INDICADORES,
} from '../../core/api/indicadores.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion } from '../../core/auth/sesion.model';
import { ConceptoSenal, NivelAtencion } from '../../core/politica-comercial';
import { fechaCorta, mesLargo } from '../../core/formato';
import {
  adelantoDe,
  cierreLegible,
  frescuraDe,
  metaDe,
  ritmoEsperadoDe,
  variacionDe,
  vozDelRitmo,
} from '../../core/rendimiento';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { GraficoSerie, SerieGrafico } from '../../shared/grafico-serie/grafico-serie';
import { Icono } from '../../shared/icono/icono';
import { Esfera } from './partes/esfera';

/**
 * La rampa de identidad del proceso: un tono por paso del embudo.
 *
 * El KPI de arriba y su salto en el embudo **comparten color**, y ese es todo
 * el trabajo que hace: enlazar la cifra con el tramo del que sale. El estado
 * —verde, ámbar, rojo— es el otro sistema y no se pisa con éste.
 */
const RAMPA = ['var(--p1)', 'var(--p2)', 'var(--p3)', 'var(--p4)'];

/** El color de cada estado de ritmo. El estado lo decide el dominio. */
const COLOR_RITMO: Record<string, string> = {
  EN_RITMO: 'var(--positivo)',
  ATENCION: 'var(--atencion)',
  FUERA_DE_RITMO: 'var(--riesgo)',
  SIN_BASE: 'var(--ink-3)',
};

/**
 * Cómo se llama cada concepto en pantalla, y qué cuenta exactamente.
 *
 * Los conceptos son **claves del dominio, no rótulos**: el texto se escribe
 * aquí, con las palabras que usaría alguien del rubro. Lo que no se decide aquí
 * es cuánto urge cada uno — eso llega en `nivelAtencion`.
 *
 * `unidad` importa: `DEMORA_DE_SEGUIMIENTO` vale **días**, no cosas, y sin
 * decirlo la lectura sumaría peras con manzanas — es el mismo error que hacía
 * decir «17 cosas» donde había 8 pendientes y 9 días de atraso.
 */
const SENAL: Record<ConceptoSenal, { rotulo: string; unidad?: string; pie: string }> = {
  SOLICITUD_POR_EVALUAR: {
    rotulo: 'Solicitudes por evaluar',
    pie: 'interesados esperando la respuesta del broker',
  },
  RECONTACTO_VENCIDO: {
    rotulo: 'Prospectos sin contactar a tiempo',
    pie: 'ya se pasaron de su fecha de recontacto',
  },
  CAPTACION_POR_REVISAR: {
    rotulo: 'Captaciones por revisar',
    pie: 'no se pueden ofrecer hasta que el broker las apruebe',
  },
  SOLICITUD_APROBADA_SIN_CIERRE: {
    rotulo: 'Aprobadas sin contrato',
    pie: 'ya se aprobaron y todavía nadie firma',
  },
  DEMORA_DE_SEGUIMIENTO: {
    rotulo: 'Demora de seguimiento',
    unidad: 'días',
    pie: 'promedio sin registrar actividad',
  },
  VISITA_PENDIENTE: {
    rotulo: 'Visitas pendientes',
    pie: 'por hacer, o hechas y sin registrar el resultado',
  },
  CIERRE_REGISTRADO: {
    rotulo: 'Alquileres firmados',
    pie: 'contratos cerrados en el periodo',
  },
  COBERTURA_DE_AGENTES: {
    rotulo: 'Agentes en operación',
    pie: 'con cartera asignada',
  },
};

/**
 * Las columnas de la lectura, en el orden en que se leen.
 *
 * Se abre por lo que reclama y se cierra por lo que informa: es el mismo orden
 * que el Inicio, donde primero va lo que depende de ti. Una columna sin señales
 * no se pinta.
 */
const GRUPOS: readonly { nivel: NivelAtencion; titulo: string; voz: string; marca: string }[] = [
  { nivel: 'ALTO', titulo: 'Atender ya', voz: 'var(--riesgo-voz)', marca: 'mk-alto' },
  { nivel: 'MEDIO', titulo: 'Vigilar', voz: 'var(--atencion-voz)', marca: 'mk-medio' },
  { nivel: 'SIN_PENDIENTES', titulo: 'Al día', voz: 'var(--positivo-voz)', marca: 'mk-bien' },
  { nivel: 'INFORMATIVO', titulo: 'Para mirar', voz: 'var(--accion)', marca: 'mk-mirar' },
];

/**
 * Qué cubos de salud son motivo de mirada.
 *
 * Los nombres los pone el backend; esto solo dice cuáles se tiñen. Un cubo que
 * no esté aquí sale en tinta neutra, que es lo correcto para un dato que no
 * reclama nada.
 */
const SALUD_OJO = new Set(['Por revisar', 'Observadas']);

/** Las métricas que la evolución sabe dibujar, con su serie y su unidad. */
type MetricaEvo = 'captaciones' | 'cierres' | 'conversion';

interface OpcionEvo {
  valor: MetricaEvo;
  etiqueta: string;
  color: string;
  sufijo: string;
}

const METRICAS_EVO: readonly OpcionEvo[] = [
  { valor: 'captaciones', etiqueta: 'Captaciones', color: 'var(--p2)', sufijo: '' },
  { valor: 'cierres', etiqueta: 'Contratos firmados', color: 'var(--p4)', sufijo: '' },
  { valor: 'conversion', etiqueta: 'Conversión', color: 'var(--p3)', sufijo: ' %' },
];

/**
 * **INDICADORES COMERCIALES** (D-E2-2).
 *
 * Cuatro KPI canónicos, **la misma definición para agente y broker**; lo único
 * que cambia es el alcance. Así un broker puede abrir el tablero de un agente y
 * entender de dónde sale el total del equipo.
 *
 * ## El semáforo mide RITMO, no consumo
 *
 * Es el cambio de fondo y no es un matiz: con 5 de 15 en el día 5 de 30, el
 * criterio de consumo dice rojo —33 % de la meta— y el de ritmo dice verde —a
 * este ritmo cierras en 30—. La pregunta que contesta el color es «¿voy camino
 * de llegar?», no «¿ya llegué?». **Lo decide el dominio**, en `estadoRitmo`;
 * aquí solo se elige con qué color se dibuja.
 *
 * ## El broker no es un agente con números más grandes
 *
 * Los cuatro KPI son resultado del **equipo**, no mérito suyo, y por eso su voz
 * cambia aunque los números no. Y ve al equipo **por excepción** —quién necesita
 * intervención—, nunca como tabla de posiciones: un agente con cartera recién
 * asignada y otro con expedientes maduros no compiten. Por eso esta pantalla ya
 * no lleva la tabla de desempeño que ordenaba a ocho personas por cierres:
 * la prohíbe la instrucción 13 de D-E2-2.
 *
 * ## Dos sistemas de color que no se pisan
 *
 * **Identidad**: la rampa `--p1..--p4`, un tono por paso del proceso — el KPI y
 * su salto en el embudo comparten tono. **Estado**: verde en ritmo, ámbar
 * atención, rojo fuera de ritmo, gris sin base. El azul es BROX y lo pulsable.
 *
 * ## Una cosa se dice una vez
 *
 * Rendimiento, diagnóstico, cartera y proceso son capas distintas; ninguna
 * tarjeta intenta resolver las cuatro. Y las frases del rendimiento salen de
 * `core/rendimiento.ts`, el mismo módulo que usa el pie del Inicio: por
 * construcción no pueden contradecirse.
 */
@Component({
  selector: 'app-indicadores',
  imports: [EstadoListado, Esfera, GraficoSerie, Icono, RouterLink],
  templateUrl: './indicadores.html',
  styleUrl: './indicadores.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Indicadores implements OnInit {
  private readonly api = inject(IndicadoresService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly periodos = PERIODOS_INDICADORES;
  protected readonly metricasEvo = METRICAS_EVO;

  protected readonly periodo = signal<string>(PERIODO_POR_DEFECTO);
  protected readonly datos = signal<IndicadoresResumen | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly metrica = signal<MetricaEvo>('captaciones');

  private readonly rol = computed<RolSesion | undefined>(() => this.auth.sesion()?.rol);
  protected readonly esAdmin = computed(() => this.rol() === 'TENANT_ADMIN');
  protected readonly esAgente = computed(() => this.rol() === 'AGENTE');
  protected readonly esBroker = computed(() => this.rol() === 'BROKER');

  // ==================================================================
  // RENDIMIENTO · los cuatro KPI canónicos
  // ==================================================================

  protected readonly rendimiento = computed(() => this.datos()?.rendimiento ?? null);

  /** Los cuatro en crudo. La tabla de metas los usa como cabecera de columna. */
  protected readonly kpisCanonicos = computed(() => this.rendimiento()?.kpis ?? []);

  /** Los cuatro, en el orden del embudo, con su tono de identidad. */
  protected readonly kpis = computed(() =>
    this.kpisCanonicos().map((kpi, i) => ({
      kpi,
      identidad: RAMPA[i] ?? RAMPA[RAMPA.length - 1],
      /** `null` cuando no hay referencia; **0 es un dato**, no una ausencia. */
      adelanto: adelantoDe(kpi),
    })),
  );

  protected readonly vozDelRitmo = vozDelRitmo;
  protected readonly variacionDe = variacionDe;
  protected readonly mesLargo = mesLargo;
  protected readonly adelantoDe = adelantoDe;

  protected metaDe(kpi: KpiCanonicoCable): string {
    return metaDe(kpi, this.esAgente());
  }

  protected ritmoEsperadoDe(kpi: KpiCanonicoCable): string {
    return ritmoEsperadoDe(kpi, this.esAgente());
  }

  protected colorDeRitmo(kpi: KpiCanonicoCable): string {
    return COLOR_RITMO[kpi.estadoRitmo] ?? 'var(--ink-3)';
  }

  /**
   * El calendario, **una vez para los cuatro KPI**.
   *
   * Dentro de cada tarjeta no cabía —«Meta del mes 24 · te faltan 2» partía las
   * cuatro en dos renglones— y aquí hace más trabajo: junto a la meta hasta hoy
   * explica de dónde sale esa cifra. Los días los cuenta el backend.
   */
  protected readonly corteDelMes = computed(() => {
    const periodo = this.rendimiento()?.periodo;
    if (!periodo) {
      return '';
    }
    const quedan = Math.max(0, periodo.diasTotales - periodo.diasTranscurridos);
    const cola = quedan === 1 ? 'queda 1 día' : `quedan ${quedan} días`;
    return `día ${periodo.diasTranscurridos} de ${periodo.diasTotales} · ${cola}`;
  });

  protected readonly calculadoHace = computed(() => frescuraDe(this.rendimiento()));

  /**
   * El pulso del equipo: la **distribución**, no el total (D-E2-2 §6.1).
   *
   * Vive pegado a los cuatro totales porque contesta lo que esos totales
   * esconden: un equipo puede firmar 21 con meta de 20 y estar perfectamente
   * verde mientras dos agentes hacen 17 y tres hacen cero. Resultado y reparto
   * no son lo mismo. Sin nombres — la instrucción 13 prohíbe el ranking.
   */
  protected readonly pulso = computed(() => {
    const pulso = this.rendimiento()?.pulso;
    if (!pulso) {
      return [];
    }
    return [
      { n: pulso.enRitmo, voz: vozDelRitmo('EN_RITMO'), color: COLOR_RITMO['EN_RITMO'] },
      { n: pulso.atencion, voz: vozDelRitmo('ATENCION'), color: COLOR_RITMO['ATENCION'] },
      {
        n: pulso.fueraDeRitmo,
        voz: vozDelRitmo('FUERA_DE_RITMO'),
        color: COLOR_RITMO['FUERA_DE_RITMO'],
      },
      { n: pulso.sinBase, voz: 'sin meta fijada', color: COLOR_RITMO['SIN_BASE'] },
    ].filter((grupo) => grupo.n > 0);
  });

  protected readonly agentesDelPulso = computed(() => this.rendimiento()?.pulso?.agentes ?? 0);

  // ==================================================================
  // LECTURA · el diagnóstico, agrupado por lo que urge
  // ==================================================================

  /**
   * Las señales repartidas en columnas por su nivel de atención.
   *
   * **La clasificación llega hecha.** El dominio dice de qué nivel es cada
   * concepto (R-07, E1) y aquí solo se agrupan y se les pone nombre. Una
   * columna sin señales no se dibuja: un rótulo sobre el vacío no informa.
   */
  protected readonly lectura = computed(() => {
    const senales = this.datos()?.senales ?? [];
    return GRUPOS.map((grupo) => ({
      ...grupo,
      senales: senales
        .filter((senal) => senal.nivelAtencion === grupo.nivel)
        .map((senal) => ({
          concepto: senal.concepto,
          valor: senal.valor,
          ...(SENAL[senal.concepto as ConceptoSenal] ?? {
            rotulo: senal.concepto,
            pie: '',
          }),
        })),
    })).filter((grupo) => grupo.senales.length > 0);
  });

  // ==================================================================
  // EN JUEGO · la cifra del mes, en su casa
  // ==================================================================
  //
  // El pie del Inicio la anticipa y enlaza aquí (D-E2-1 §6.2); ésta es la
  // pantalla donde vive con su detalle.

  protected readonly enJuego = computed(() => cierreLegible(this.rendimiento()));

  // ==================================================================
  // CARTERA · qué tienes, y en qué estado
  // ==================================================================

  protected readonly carteraTotal = computed(() => this.datos()?.captacionesTotales ?? 0);

  protected readonly salud = computed(() =>
    (this.datos()?.captacionesSalud ?? []).map((cubo) => ({
      ...cubo,
      ojo: SALUD_OJO.has(cubo.nombre),
    })),
  );

  private readonly totalEtapas = computed(() =>
    (this.datos()?.etapas ?? []).reduce((total, e) => total + e.valor, 0),
  );

  /**
   * El reparto de la cartera por etapa.
   *
   * **El riel es la cartera entera, no la fila más grande.** Normalizado contra
   * el máximo, la etapa mayor salía a barra llena y se leía «toda mi cartera
   * está en Activa» cuando eran 5 de 13. Así el hueco significa algo: es el
   * resto de la cartera.
   *
   * La etapa lleva rampa porque **es** un recorrido: más avanzada, más oscura.
   */
  protected readonly etapas = computed(() => {
    const total = this.totalEtapas() || 1;
    const rampa = ['#afc2de', '#7ba3e5', '#2563eb', '#0f7a85', '#168650'];
    return (this.datos()?.etapas ?? []).map((etapa, i) => ({
      ...etapa,
      color: rampa[i] ?? rampa[rampa.length - 1],
      ancho: (etapa.valor / total) * 100,
    }));
  });

  protected readonly hayEtapas = computed(() => this.totalEtapas() > 0);

  // ==================================================================
  // EMBUDO · cada salto nombra su origen y su destino
  // ==================================================================

  /**
   * Los saltos, derivados de las etapas consecutivas del embudo.
   *
   * El cable trae **niveles** (cuántas oportunidades hay en cada punto) y el
   * embudo se lee en **saltos**: de las 24 que entraron, 16 llegaron a visita.
   * La resta y el cociente son aritmética sobre dos conteos publicados, no una
   * clasificación — y así el porcentaje de cada tramo dice lo que retiene ese
   * tramo, en vez de repetir la proporción contra la primera fila.
   *
   * **No se señala el cuello.** Marcar cuál retiene menos exige un mínimo de
   * muestra —un 100 % sobre un caso no es un buen tramo, es un dato sin base—, y
   * ese umbral vive en `PoliticaComercial.MUESTRA_MINIMA` del dominio y no viaja
   * en el cable. Decidirlo aquí sería devolver al cliente el umbral que E1 le
   * quitó. Cuando `muestra.minima-para-concluir` viaje, este bloque lo usa.
   */
  protected readonly saltos = computed(() => {
    const filas = this.datos()?.embudo ?? [];
    return filas.slice(0, -1).map((fila, i) => {
      const destino = filas[i + 1];
      const pasan = destino.valor;
      const quedan = Math.max(0, fila.valor - pasan);
      return {
        de: fila.etapa,
        a: destino.etapa,
        entran: fila.valor,
        pasan,
        quedan,
        /** `null` cuando no entró nadie: 0/0 no es 0 %, es que no hay tasa. */
        porcentaje: fila.valor > 0 ? Math.round((pasan * 100) / fila.valor) : null,
        color: RAMPA[i + 1] ?? RAMPA[RAMPA.length - 1],
      };
    });
  });

  // ==================================================================
  // EVOLUCIÓN · una métrica por vez
  // ==================================================================

  protected readonly opcionEvo = computed(
    () => METRICAS_EVO.find((m) => m.valor === this.metrica()) ?? METRICAS_EVO[0],
  );

  protected readonly etiquetasSerie = computed(() => this.datos()?.mesesEtiquetas ?? []);

  /**
   * Una serie, no cuatro. Cuatro líneas simultáneas son ruido (D-E2-2 §10), y
   * mezclar conteos con un porcentaje obligaría a dos escalas en el mismo marco
   * — que es la manera más fácil de que un gráfico mienta.
   */
  protected readonly serie = computed<SerieGrafico[]>(() => {
    const i = this.datos();
    const opcion = this.opcionEvo();
    if (!i) {
      return [];
    }
    const valores =
      opcion.valor === 'captaciones'
        ? i.captacionesPorPeriodo
        : opcion.valor === 'cierres'
          ? i.cierresPorMes
          : i.conversionPorPeriodo;
    return [{ nombre: opcion.etiqueta, valores, color: opcion.color }];
  });

  /**
   * La meta del **mes en curso** para la métrica elegida, cuando existe.
   *
   * Se dice con palabras y no se dibuja como línea sobre los seis tramos: el
   * cable no publica metas históricas, así que una línea de lado a lado
   * afirmaría que ese objetivo rigió los seis periodos, y eso no lo sabemos.
   */
  protected readonly metaDeLaSerie = computed(() => {
    const codigo =
      this.metrica() === 'captaciones' ? 'P' : this.metrica() === 'cierres' ? 'F' : null;
    if (!codigo) {
      return null;
    }
    return this.rendimiento()?.kpis.find((k) => k.codigo === codigo)?.metaPeriodo ?? null;
  });

  protected cambiarMetrica(valor: string): void {
    if (METRICAS_EVO.some((m) => m.valor === valor)) {
      this.metrica.set(valor as MetricaEvo);
    }
  }

  // ==================================================================
  // GESTIÓN DE METAS · dentro de Indicadores, no en un módulo aparte
  // ==================================================================
  //
  // La meta pertenece a la pantalla donde se mide el rendimiento. Sacarla a un
  // módulo «Metas» obligaría a saltar de sitio para entender por qué el semáforo
  // dice lo que dice, y crearía una segunda superficie que compite con ésta.
  //
  // QUIÉN PUEDE QUÉ, y por qué:
  //   AGENTE  ve las suyas y PROPONE, con motivo. Si pudiera fijarlas, el
  //           indicador sería manipulable: voy al 60 %, bajo la meta, verde.
  //   BROKER  fija y decide. Es quien dirige comercialmente.
  //   ADMIN   lee. Administrar usuarios no es dirigir producción.
  //
  // La pantalla NO decide nada de eso: ofrece la acción que el rol permite y el
  // backend la vuelve a comprobar. Un 403 no debería llegar nunca, y si llega es
  // que la pantalla ofreció algo que no debía.

  protected readonly metas = signal<MetaDeAgente[]>([]);
  protected readonly propuestas = signal<PropuestaDeMeta[]>([]);
  protected readonly guardandoMetas = signal(false);
  protected readonly errorMetas = signal<string | null>(null);

  /** Qué KPI está editando el agente ahora mismo. `null` = ninguno. */
  protected readonly ajustando = signal<string | null>(null);
  protected readonly valorAjuste = signal<number | null>(null);
  protected readonly motivoAjuste = signal('');

  /** Qué agente está editando el broker. `null` = ninguno. */
  protected readonly fijandoA = signal<number | null>(null);
  protected readonly valoresFijados = signal<Record<string, number | null>>({});
  protected readonly motivoFijado = signal('');

  /** Qué propuesta está resolviendo el broker. */
  protected readonly resolviendo = signal<number | null>(null);
  protected readonly motivoDecision = signal('');

  /** Las metas del propio agente, en el orden canónico de los KPI. */
  protected readonly misObjetivos = computed(() => this.metas());

  /** Las del equipo, agrupadas por agente para poder fijarlas de una vez. */
  protected readonly equipo = computed(() => {
    const porAgente = new Map<number, { id: number; nombre: string; metas: MetaDeAgente[] }>();
    for (const meta of this.metas()) {
      const fila = porAgente.get(meta.idRolAgente) ?? {
        id: meta.idRolAgente,
        nombre: meta.agente,
        metas: [],
      };
      fila.metas.push(meta);
      porAgente.set(meta.idRolAgente, fila);
    }
    return [...porAgente.values()];
  });

  /**
   * Cuántos agentes tienen las cuatro metas fijadas.
   *
   * Es la cobertura: si falta una sola, el equipo entero se queda sin semáforo,
   * y el broker necesita ver **de quién** falta para poder arreglarlo. Es la
   * única excepción del equipo que el cable sostiene hoy, y no es un ranking:
   * es una configuración a medio hacer.
   */
  protected readonly cobertura = computed(() => {
    const filas = this.equipo();
    const completos = filas.filter((f) => f.metas.every((m) => m.valor != null)).length;
    return { completos, total: filas.length };
  });

  /** Quiénes se quedaron sin fijar. Por nombre, no por posición. */
  protected readonly sinMeta = computed(() =>
    this.equipo()
      .filter((f) => f.metas.some((m) => m.valor == null))
      .map((f) => f.nombre),
  );

  /** El mes que se está gestionando: el mismo que mide el rendimiento. */
  private mesVigente(): string {
    return this.rendimiento()?.periodo.codigo ?? '';
  }

  private async recargarMetas(): Promise<void> {
    this.errorMetas.set(null);
    try {
      this.metas.set(await this.api.metas(this.mesVigente()));
      if (!this.esAgente()) {
        this.propuestas.set(await this.api.propuestasDeMeta());
      }
    } catch (fallo) {
      this.errorMetas.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo completar la operacion.',
      );
    }
  }

  // --- El agente propone ---------------------------------------------

  protected abrirAjuste(meta: MetaDeAgente): void {
    this.ajustando.set(meta.kpi);
    this.valorAjuste.set(meta.propuesta?.valorPropuesto ?? meta.valor ?? null);
    this.motivoAjuste.set('');
    this.errorMetas.set(null);
  }

  protected cerrarAjuste(): void {
    this.ajustando.set(null);
    this.motivoAjuste.set('');
  }

  protected async enviarAjuste(kpi: string): Promise<void> {
    const valor = this.valorAjuste();
    if (valor == null || valor < 0) {
      this.errorMetas.set('Indica cuántos crees que son alcanzables este mes.');
      return;
    }
    this.guardandoMetas.set(true);
    this.errorMetas.set(null);
    try {
      this.metas.set(
        await this.api.proponerMeta(this.mesVigente(), kpi, valor, this.motivoAjuste()),
      );
      this.cerrarAjuste();
    } catch (fallo) {
      this.errorMetas.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo completar la operacion.',
      );
    } finally {
      this.guardandoMetas.set(false);
    }
  }

  // --- El broker fija -------------------------------------------------

  protected abrirFijar(fila: { id: number; metas: MetaDeAgente[] }): void {
    const valores: Record<string, number | null> = {};
    for (const meta of fila.metas) {
      valores[meta.kpi] = meta.valor ?? null;
    }
    this.valoresFijados.set(valores);
    this.motivoFijado.set('');
    this.fijandoA.set(fila.id);
    this.errorMetas.set(null);
  }

  protected cerrarFijar(): void {
    this.fijandoA.set(null);
    this.motivoFijado.set('');
  }

  protected valorFijado(kpi: string): number | null {
    return this.valoresFijados()[kpi] ?? null;
  }

  protected cambiarValorFijado(kpi: string, valor: string): void {
    const numero = valor === '' ? null : Number(valor);
    this.valoresFijados.set({ ...this.valoresFijados(), [kpi]: numero });
  }

  protected async guardarMetas(idRolAgente: number): Promise<void> {
    const valores = this.valoresFijados();
    // Solo viaja lo que tiene valor: lo que no viene NO se borra, y un campo en
    // blanco significa «no la fijo todavía», no «ponla a cero».
    const asignaciones: AsignacionDeMeta[] = Object.entries(valores)
      .filter(([, valor]) => valor != null && valor >= 0)
      .map(([kpi, valor]) => ({
        idRolAgente,
        kpi,
        valor: valor as number,
        motivo: this.motivoFijado(),
      }));

    if (asignaciones.length === 0) {
      this.errorMetas.set('No hay ninguna meta que fijar.');
      return;
    }
    this.guardandoMetas.set(true);
    this.errorMetas.set(null);
    try {
      this.metas.set(await this.api.fijarMetas(this.mesVigente(), asignaciones));
      this.cerrarFijar();
      await this.cargar();
    } catch (fallo) {
      this.errorMetas.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo completar la operacion.',
      );
    } finally {
      this.guardandoMetas.set(false);
    }
  }

  // --- El broker decide -----------------------------------------------

  protected abrirDecision(propuesta: PropuestaDeMeta): void {
    this.resolviendo.set(propuesta.idRevision);
    this.motivoDecision.set('');
    this.errorMetas.set(null);
  }

  protected cerrarDecision(): void {
    this.resolviendo.set(null);
    this.motivoDecision.set('');
  }

  protected async decidir(idRevision: number, acepta: boolean): Promise<void> {
    this.guardandoMetas.set(true);
    this.errorMetas.set(null);
    try {
      this.metas.set(await this.api.decidirPropuesta(idRevision, acepta, this.motivoDecision()));
      this.propuestas.set(await this.api.propuestasDeMeta());
      this.cerrarDecision();
      await this.cargar();
    } catch (fallo) {
      this.errorMetas.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo completar la operacion.',
      );
    } finally {
      this.guardandoMetas.set(false);
    }
  }

  // --- Lectura del historial ------------------------------------------

  /**
   * El historial dicho de corrido: «Meta inicial 8 · revisada a 6 el 18 de
   * agosto — agente incorporado tarde».
   *
   * Se redacta aquí y no en el backend porque es presentación pura; los hechos
   * —de cuánto a cuánto, quién y por qué— llegan enteros.
   */
  protected historialLegible(meta: MetaDeAgente): string[] {
    return meta.historial
      .filter((r) => r.estado !== 'E')
      .map((r) => {
        const salto =
          r.valorAnterior == null
            ? `Meta inicial ${r.valorPropuesto}`
            : `${r.valorAnterior} → ${r.valorPropuesto}`;
        const quien = r.estado === 'R' ? `${r.autor} lo pidió y no se aprobó` : r.autor;
        return `${salto} · ${fechaCorta(r.fecha)} · ${quien} — ${r.motivo}`;
      });
  }

  // ==================================================================
  // Carga
  // ==================================================================

  ngOnInit(): void {
    const pedido = this.route.snapshot.queryParamMap.get('periodo');
    this.periodo.set(esPeriodo(pedido) ? pedido : PERIODO_POR_DEFECTO);
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.datos.set(await this.api.resumen(this.periodo()));
      await this.recargarMetas();
    } catch (fallo) {
      this.datos.set(null);
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudieron cargar los indicadores.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  protected cambiarPeriodo(valor: string): void {
    if (!esPeriodo(valor) || valor === this.periodo()) {
      return;
    }
    this.periodo.set(valor);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { periodo: valor === PERIODO_POR_DEFECTO ? null : valor },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    void this.cargar();
  }

  protected etiquetaPeriodo(): string {
    return this.periodos.find((p) => p.valor === this.periodo())?.etiqueta ?? '';
  }
}
