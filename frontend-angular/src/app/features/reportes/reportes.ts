import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';

import { ApiError } from '../../core/api/api.types';
import { AvanceComercial, IndicadoresService } from '../../core/api/indicadores.service';
import { descargarCsv } from '../../core/csv';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { RESULTADOS_POR_PAGINA } from '../../shared/paginacion/tamano-pagina';
import { TarjetaKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

/**
 * Avance comercial por propiedad (RF-017): una fila por captación **activa**,
 * con todo lo que pasó sobre ese inmueble.
 *
 * Es la pantalla que el sistema viejo nunca tuvo. El endpoint
 * `GET /indicadores/avance` existe en la v1 pero **ningún `.razor` lo
 * consume**: se cortó con el resto de E4 y se queda sin cliente. Aquí es donde
 * encaja, porque responde la pregunta que ninguna bandeja responde —"de mis
 * inmuebles captados, ¿cuáles se están moviendo y cuáles no?"—.
 *
 * Tres cosas que hay que saber al leerla:
 *
 * - **Es acumulada, no del periodo.** No acepta `periodo` a propósito: mide el
 *   estado actual de cada captación, no una ventana temporal. Por eso esta
 *   pantalla no lleva selector.
 * - **`interesados` de la cabecera no es la suma de la columna**: son los
 *   clientes **distintos** a nivel global, así que un mismo cliente interesado
 *   en dos propiedades cuenta una vez arriba y dos abajo. No es un descuadre.
 * - **El backend no pagina esto**: devuelve el avance de toda la cartera de
 *   una vez, porque las tarjetas de arriba y la exportación necesitan el
 *   conjunto entero. La tabla se pagina en la VISTA para poder leerla; la
 *   exportación sigue llevando todas las filas.
 *
 * **No lleva botón "Exportar PDF"**: los cinco endpoints Jasper quedaron fuera
 * del alcance de la migración (D-F5-1) y la impresión se decidirá cuando exista
 * la nueva funcionalidad de reportes. El CSV sí se ofrece —es dato, no
 * maquetación— y sale del mismo conjunto que la tabla.
 */
@Component({
  selector: 'app-reportes',
  imports: [EstadoListado, Paginacion, TarjetaKpi],
  templateUrl: './reportes.html',
  styleUrl: './reportes.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Reportes implements OnInit {
  private readonly api = inject(IndicadoresService);

  protected readonly datos = signal<AvanceComercial | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly exportado = signal<string | null>(null);

  /** Orden del cable: más oportunidades abiertas primero, luego interacciones. */
  protected readonly filas = computed(() => this.datos()?.detalle ?? []);

  protected readonly sinMovimiento = computed(
    () => this.filas().filter((fila) => fila.oportunidadesTotales === 0).length,
  );

  /**
   * <b>Aquí la paginación es de la VISTA, no de la consulta</b>, y es la única
   * pantalla donde eso es lo correcto.
   *
   * `GET /indicadores/avance` es un agregado: devuelve el avance de toda la
   * cartera de una vez porque las tres tarjetas de arriba y el botón de
   * exportar necesitan el conjunto entero. Pedir páginas al backend obligaría
   * a recorrerlo entero igualmente para exportar, y a recalcular los
   * agregados en cada página.
   *
   * Lo que sí estaba mal es volcar las 40 filas de golpe: la tabla se leía
   * como un muro. Se pagina lo que se MUESTRA, con el mismo tamaño que el
   * resto de BROX, y se exporta todo.
   */
  protected readonly porPagina = RESULTADOS_POR_PAGINA;
  protected readonly pagina = signal(1);

  protected readonly filasVisibles = computed(() => {
    const desde = (this.pagina() - 1) * this.porPagina;
    return this.filas().slice(desde, desde + this.porPagina);
  });

  protected irAPagina(pagina: number): void {
    this.pagina.set(pagina);
  }

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.datos.set(await this.api.avance());
    } catch (fallo) {
      this.datos.set(null);
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cargar el avance comercial.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  /** Exporta TODO, no la página visible: quien descarga quiere el conjunto. */
  protected exportar(): void {
    const nombre = descargarCsv(
      'avance_comercial',
      [
        'Captacion',
        'Direccion',
        'Distrito',
        'Estado',
        'Oportunidades',
        'Abiertas',
        'Con visita',
        'Con solicitud',
        'Exitosas',
        'No favorables',
        'Sin continuidad',
        'Interesados',
        'Interacciones',
        'Visitas programadas',
        'Visitas concretadas',
        'Solicitudes',
        'Tasa visita %',
        'Tasa solicitud %',
        'Motivo principal de no continuidad',
      ],
      this.filas().map((fila) => [
        fila.codigoCaptacion,
        fila.direccion,
        fila.distrito,
        fila.estadoComercial,
        fila.oportunidadesTotales,
        fila.oportunidadesAbiertas,
        fila.oportunidadesConVisita,
        fila.oportunidadesConSolicitud,
        fila.cerradasExitosas,
        fila.cerradasNoFavorables,
        fila.cerradasNoContinuidad,
        fila.interesados,
        fila.interacciones,
        fila.visitasProgramadas,
        fila.visitasConcretadas,
        fila.solicitudesRecibidas,
        fila.tasaOportVisita,
        fila.tasaOportSolicitud,
        fila.motivoNoContinuidad,
      ]),
    );
    this.exportado.set(`Se descargó ${nombre} con ${this.filas().length} propiedades.`);
  }
}
