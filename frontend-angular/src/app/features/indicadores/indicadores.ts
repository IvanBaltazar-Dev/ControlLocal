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
  esPeriodo,
  IndicadoresResumen,
  IndicadoresService,
  PERIODO_POR_DEFECTO,
  PERIODOS_INDICADORES,
} from '../../core/api/indicadores.service';
import { AuthService } from '../../core/auth/auth.service';
import { RolSesion } from '../../core/auth/sesion.model';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { GraficoSerie, SerieGrafico } from '../../shared/grafico-serie/grafico-serie';
import { TarjetaKpi, TonoKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

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
  imports: [EstadoListado, GraficoSerie, TarjetaKpi],
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
  protected readonly conversion = computed(() => this.datos()?.conversionPropia ?? 0);

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

  protected readonly operativo = computed(() => this.datos()?.operativo ?? null);
}
