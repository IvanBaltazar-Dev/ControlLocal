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
 * - **Sin tope de filas.** El backend no pagina esto, así que la tabla puede
 *   ser larga; por eso la exportación recorre exactamente lo que se ve.
 *
 * **No lleva botón "Exportar PDF"**: los cinco endpoints Jasper quedaron fuera
 * del alcance de la migración (D-F5-1) y la impresión se decidirá cuando exista
 * la nueva funcionalidad de reportes. El CSV sí se ofrece —es dato, no
 * maquetación— y sale del mismo conjunto que la tabla.
 */
@Component({
  selector: 'app-reportes',
  imports: [EstadoListado, TarjetaKpi],
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

  /** Recorre lo que se ve: la lectura ya viene entera, no hay que paginarla. */
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
