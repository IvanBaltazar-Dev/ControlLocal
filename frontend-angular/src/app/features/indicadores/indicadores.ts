import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

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
import { TonoKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';
import { fechaCorta, mesLargo } from '../../core/formato';
import {
  avanceDe,
  cierreLegible,
  cifraDe,
  frescuraDe,
  lecturaDe,
  marcaEsperadaDe,
  variacionDe,
  vozDelRitmo,
} from '../../core/rendimiento';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { GraficoSerie, SerieGrafico } from '../../shared/grafico-serie/grafico-serie';

/**
 * Par categórico de las dos series de conteo. **Validado**, no elegido a ojo:
 * pasa las seis comprobaciones de paleta (banda de luminosidad, croma mínimo,
 * separación bajo daltonismo —ΔE 21 protan / 23 tritan—, separación en visión
 * normal y contraste ≥ 3:1 contra la superficie clara).
 *
 * El petróleo y el dorado de marca no pasaban: el primero lee como gris
 * (croma 0,079) y el segundo se queda en 2,19:1 de contraste.
 */
const COLOR_CAPTACIONES = '#0b74a8';
const COLOR_CIERRES = '#b8791a';

/** La conversión es una sola magnitud: un tono, sin identidad que distinguir. */
const COLOR_CONVERSION = '#0b74a8';

/** Rampa secuencial del embudo: más abajo en el embudo, más oscuro. */
const RAMPA_EMBUDO = ['#9dbecb', '#6ba0b4', '#3d829a', '#0e3a4c'];

interface Kpi {
  etiqueta: string;
  valor: string;
  tono: TonoKpi;
  pie: string;
}

/**
 * Lectura analítica del negocio: el `GET /indicadores/resumen` del contrato
 * congelado E4, entero y en una pantalla.
 *
 * Porta la parte analítica de `Reportes.razor` **sin su botón "Exportar PDF"**:
 * los cinco endpoints Jasper quedaron fuera del alcance de la migración
 * (D-F5-1) y la nueva funcionalidad de reportes se diseñará desde cero. Un
 * botón que promete un PDF que no existe es peor que su ausencia.
 *
 * Cuatro rarezas del cable que se muestran tal cual —replicarlas es la regla
 * mientras el contrato siga congelado (D-E4-3)—, pero **rotuladas**, para que
 * quien lea la pantalla no las tome por un error de cálculo:
 *
 * - La primera fila del embudo lleva **100 % fijo**, aunque su valor sea 0.
 * - *"Con visita realizada"* **no mira el estado de la visita**: cuenta
 *   oportunidades con visita de cualquier estado, incluida una cancelada.
 * - El donut de etapas **no depende del periodo**; la salud de captaciones sí.
 * - Si el periodo no tuvo prospecciones, el bloque operativo **cae a todas las
 *   del alcance**, así que su tasa no es la del periodo.
 *
 * Y una regla de forma que no se negocia: conteos y porcentajes van en
 * **gráficos separados**. Meterlos en el mismo marco obligaría a dos escalas,
 * que es la manera más fácil de que un gráfico mienta.
 */
@Component({
  selector: 'app-indicadores',
  imports: [EstadoListado, GraficoSerie],
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

  protected readonly periodo = signal<string>(PERIODO_POR_DEFECTO);
  protected readonly datos = signal<IndicadoresResumen | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  private readonly rol = computed<RolSesion | undefined>(() => this.auth.sesion()?.rol);
  protected readonly esAdmin = computed(() => this.rol() === 'TENANT_ADMIN');
  protected readonly esAgente = computed(() => this.rol() === 'AGENTE');

  // ==================================================================
  // RENDIMIENTO · los cuatro KPI canónicos (E2.6)
  // ==================================================================
  //
  // Las frases salen de `core/rendimiento.ts`, el MISMO módulo que usa el pie
  // del Inicio. Es lo que hace imposible que las dos pantallas se contradigan,
  // que es lo que D-E2-1 §6.2 exige: si aquí cambia una definición, allí cambia
  // sola porque no hay dos definiciones.

  /** El bloque de rendimiento, o `null` mientras no haya carga. */
  protected readonly rendimiento = computed(() => this.datos()?.rendimiento ?? null);

  /** Los cuatro, en el orden del embudo. */
  protected readonly kpisCanonicos = computed(() => this.rendimiento()?.kpis ?? []);

  protected readonly cifraDe = cifraDe;
  protected readonly marcaEsperadaDe = marcaEsperadaDe;
  protected readonly vozDelRitmo = vozDelRitmo;
  protected readonly variacionDe = variacionDe;
  protected readonly mesLargo = mesLargo;

  protected readonly cierreDelMes = computed(() => cierreLegible(this.rendimiento()));
  protected readonly calculadoHace = computed(() => frescuraDe(this.rendimiento()));

  /** La voz cambia por rol; los números no. */
  protected lecturaDe(kpi: KpiCanonicoCable): string {
    return lecturaDe(kpi, this.esAgente());
  }

  /**
   * El perímetro del arco, para dibujarlo con `stroke-dasharray`.
   *
   * `2πr` con r=52. Es geometría, no negocio: el porcentaje que representa lo
   * decide el dominio y aquí solo se convierte en longitud de trazo.
   */
  protected readonly perimetro = 2 * Math.PI * 52;

  /** Cuánto del arco queda SIN pintar. Es lo que consume `stroke-dashoffset`. */
  protected restoDelArco(kpi: KpiCanonicoCable): number {
    return this.perimetro * (1 - avanceDe(kpi) / 100);
  }


  ngOnInit(): void {
    const pedido = this.route.snapshot.queryParamMap.get('periodo');
    this.periodo.set(esPeriodo(pedido) ? pedido : PERIODO_POR_DEFECTO);
    void this.cargar();
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
   * y el broker necesita ver **de quién** falta para poder arreglarlo.
   */
  protected readonly cobertura = computed(() => {
    const filas = this.equipo();
    const completos = filas.filter((f) => f.metas.every((m) => m.valor != null)).length;
    return { completos, total: filas.length };
  });

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
      this.metas.set(
        await this.api.decidirPropuesta(idRevision, acepta, this.motivoDecision()),
      );
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

  // --- Cabecera y KPIs ----------------------------------------------------

  /**
   * Conversión por COHORTE: de las captaciones nacidas en el periodo, cuántas
   * ya cerraron. Por eso nunca pasa de 100 — y por eso no coincide con
   * "cierres del periodo", que incluye cierres de captaciones anteriores.
   */
  protected readonly conversion = computed(() => this.datos()?.conversionPropia ?? null);

  /** Ancho del medidor. Sin muestra no hay barra que llenar. */
  protected readonly conversionAncho = computed(() => this.conversion() ?? 0);

  protected readonly kpis = computed<Kpi[]>(() => {
    const i = this.datos();
    if (!i) {
      return [];
    }
    const periodo = this.etiquetaPeriodo().toLowerCase();
    const base: Kpi[] = [
      {
        etiqueta: 'Captaciones',
        valor: String(i.captacionesTotales),
        tono: 'azul',
        pie: periodo,
      },
      { etiqueta: 'Cierres', valor: String(i.cierres), tono: 'verde', pie: periodo },
      {
        etiqueta: 'Visitas',
        valor: String(i.visitas),
        tono: 'azul',
        pie: periodo,
      },
      {
        etiqueta: 'Interacciones',
        valor: String(i.interacciones),
        tono: 'azul',
        pie: periodo,
      },
      {
        etiqueta: 'Operaciones abiertas',
        valor: String(i.oportunidadesActivas),
        tono: 'info',
        pie: 'acumulado, sin periodo',
      },
      {
        etiqueta: 'Prospección → captación',
        valor: `${i.operativo.conversionProspeccionCaptacion}%`,
        tono: 'info',
        pie: 'disciplina de captación',
      },
    ];
    if (this.esAdmin()) {
      base.push({
        etiqueta: 'Equipo activo',
        valor: String(i.agentesActivos),
        tono: 'azul',
        pie: `${i.brokersActivos} brokers`,
      });
    }
    return base;
  });

  // --- Embudo -------------------------------------------------------------

  protected readonly embudo = computed(() =>
    (this.datos()?.embudo ?? []).map((fila, indice) => ({
      ...fila,
      color: RAMPA_EMBUDO[indice] ?? RAMPA_EMBUDO[RAMPA_EMBUDO.length - 1],
      /** El 100 de la primera fila es del cable, no un cálculo de la pantalla. */
      fijo: indice === 0,
    })),
  );

  // --- Series -------------------------------------------------------------

  protected readonly etiquetasSerie = computed(() => this.datos()?.mesesEtiquetas ?? []);

  /** Dos conteos, misma unidad: pueden compartir eje. */
  protected readonly seriesConteo = computed<SerieGrafico[]>(() => {
    const i = this.datos();
    if (!i) {
      return [];
    }
    return [
      { nombre: 'Captaciones', valores: i.captacionesPorPeriodo, color: COLOR_CAPTACIONES },
      { nombre: 'Cierres', valores: i.cierresPorMes, color: COLOR_CIERRES },
    ];
  });

  /** El porcentaje va aparte: otra unidad, otro gráfico. */
  protected readonly serieConversion = computed<SerieGrafico[]>(() => {
    const i = this.datos();
    if (!i) {
      return [];
    }
    return [{ nombre: 'Conversión', valores: i.conversionPorPeriodo, color: COLOR_CONVERSION }];
  });

  // --- Etapas y desempeño -------------------------------------------------

  protected readonly totalEtapas = computed(() =>
    (this.datos()?.etapas ?? []).reduce((total, e) => total + e.valor, 0),
  );

  protected readonly etapas = computed(() => {
    const total = this.totalEtapas();
    const rampa = ['#9dbecb', '#6ba0b4', '#3d829a', '#1d6180', '#0e3a4c'];
    return (this.datos()?.etapas ?? []).map((etapa, indice) => ({
      ...etapa,
      color: rampa[indice] ?? rampa[rampa.length - 1],
      porcentaje: total > 0 ? Math.round((etapa.valor * 100) / total) : 0,
    }));
  });

  protected readonly desempeno = computed(() => this.datos()?.desempeno ?? []);

  protected readonly tituloDesempeno = computed(() =>
    this.esAdmin() ? 'Desempeño por broker' : this.esAgente() ? 'Mi desempeño' : 'Desempeño por agente',
  );

}
