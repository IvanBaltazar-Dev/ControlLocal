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
  FilaSeguimiento,
  FiltrosSeguimiento,
  PaginaSeguimiento,
  SeguimientoService,
  TAMANO_SEGUIMIENTO,
} from '../../core/api/seguimiento.service';
import { fechaCorta, fechaHora, numero } from '../../core/formato';
import { NavegacionLegado } from '../../core/navegacion-legado';
import { BarraFiltros } from '../../shared/barra-filtros/barra-filtros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { FiltroSelect } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { TarjetaKpi, TonoKpi } from '../../shared/tarjeta-kpi/tarjeta-kpi';

/** Los cinco procesos, en el orden del flujo comercial. */
const PROCESOS = [
  { valor: 'Prospeccion', etiqueta: 'Prospección', clave: 'prospeccion' },
  { valor: 'Captacion', etiqueta: 'Captación', clave: 'captacion' },
  { valor: 'Oportunidad', etiqueta: 'Oportunidad', clave: 'oportunidad' },
  { valor: 'Solicitud', etiqueta: 'Solicitud', clave: 'solicitud' },
  { valor: 'Cierre', etiqueta: 'Cierre', clave: 'cierre' },
] as const;

const VACIA: PaginaSeguimiento = {
  items: [],
  totalRecords: 0,
  page: 1,
  pageSize: TAMANO_SEGUIMIENTO,
  counts: { todos: 0, prospeccion: 0, captacion: 0, oportunidad: 0, solicitud: 0, cierre: 0 },
  options: { agentes: [], propietarios: [], estados: [], distritos: [] },
};

/**
 * Seguimiento comercial: el proceso entero en una sola lista, de la prospección
 * al cierre. Es la pantalla que responde "¿en qué punto está cada cosa?" sin
 * obligar a recorrer cinco bandejas.
 *
 * Todo el trabajo lo hace el backend (contrato congelado E4) y la pantalla se
 * limita a no estorbarlo. Tres decisiones que salen de ahí:
 *
 * - **Los KPI son atajos de filtro y siguen siendo válidos con otros filtros
 *   puestos**, porque `counts` se calcula con todos los filtros MENOS el de
 *   proceso. Recalcularlos aquí los rompería.
 * - **Los selectores se llenan con `options`, que el backend calcula sobre el
 *   alcance completo y sin filtros.** Derivarlos de la página visible dejaría
 *   un selector que se vacía a sí mismo tras el primer filtro.
 * - **El tamaño de página es 8 y es un techo del recurso**, no una preferencia
 *   de la pantalla: pedir más devuelve 8 igual.
 *
 * Y una rareza que se muestra tal cual: **las filas sin fecha encabezan la
 * lista**. Es el comparador del cable, está verificado y reordenarlas aquí
 * sería divergir del sistema que todavía manda.
 */
@Component({
  selector: 'app-seguimiento-comercial',
  imports: [BarraFiltros, EstadoListado, FiltroSelect, Paginacion, TarjetaKpi],
  templateUrl: './seguimiento-comercial.html',
  styleUrl: './seguimiento-comercial.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SeguimientoComercial implements OnInit {
  private readonly api = inject(SeguimientoService);
  private readonly navegacion = inject(NavegacionLegado);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly procesos = PROCESOS;
  protected readonly tamano = TAMANO_SEGUIMIENTO;

  protected readonly datos = signal<PaginaSeguimiento>(VACIA);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly texto = signal('');
  protected readonly tipo = signal('');
  protected readonly agente = signal('');
  protected readonly propietario = signal('');
  protected readonly estado = signal('');
  protected readonly distrito = signal('');
  protected readonly pagina = signal(1);

  protected readonly hayFiltros = computed(
    () =>
      !!this.texto() ||
      !!this.tipo() ||
      !!this.agente() ||
      !!this.propietario() ||
      !!this.estado() ||
      !!this.distrito(),
  );

  protected readonly opciones = computed(() => this.datos().options);

  protected readonly kpis = computed(() => {
    const counts = this.datos().counts;
    const tonos: Record<string, TonoKpi> = {
      prospeccion: 'azul',
      captacion: 'info',
      oportunidad: 'azul',
      solicitud: 'ambar',
      cierre: 'verde',
    };
    return [
      { valor: '', etiqueta: 'Todos', total: counts.todos, tono: 'gris' as TonoKpi },
      ...PROCESOS.map((proceso) => ({
        valor: proceso.valor,
        etiqueta: proceso.etiqueta,
        total: counts[proceso.clave],
        tono: tonos[proceso.clave],
      })),
    ];
  });

  ngOnInit(): void {
    const parametros = this.route.snapshot.queryParamMap;
    this.tipo.set(this.procesoValido(parametros.get('tipo')));
    this.texto.set(parametros.get('q') ?? '');
    void this.cargar();
  }

  /** Un proceso inventado en la URL no viaja al backend: se ignora. */
  private procesoValido(valor: string | null): string {
    return PROCESOS.some((p) => p.valor === valor) ? (valor as string) : '';
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.datos.set(await this.api.pagina(this.filtros()));
    } catch (fallo) {
      this.datos.set(VACIA);
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo cargar el seguimiento.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  private filtros(): FiltrosSeguimiento {
    return {
      tipo: this.tipo() || undefined,
      q: this.texto() || undefined,
      agente: this.agente() || undefined,
      propietario: this.propietario() || undefined,
      estado: this.estado() || undefined,
      distrito: this.distrito() || undefined,
      pagina: this.pagina(),
      tamano: TAMANO_SEGUIMIENTO,
    };
  }

  /** Cualquier cambio de filtro vuelve a la página 1: la 3 ya no existe. */
  protected aplicar(): void {
    this.pagina.set(1);
    this.sincronizarUrl();
    void this.cargar();
  }

  protected cambiarPagina(valor: number): void {
    this.pagina.set(valor);
    void this.cargar();
  }

  protected filtrarPorProceso(valor: string): void {
    this.tipo.set(this.tipo() === valor ? '' : valor);
    this.aplicar();
  }

  protected limpiar(): void {
    this.texto.set('');
    this.tipo.set('');
    this.agente.set('');
    this.propietario.set('');
    this.estado.set('');
    this.distrito.set('');
    this.aplicar();
  }

  /** Solo viajan a la URL los dos filtros que vale la pena compartir. */
  private sincronizarUrl(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tipo: this.tipo() || null, q: this.texto() || null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  // --- Navegación ---------------------------------------------------------

  protected puedeAbrir(fila: FilaSeguimiento): boolean {
    return this.navegacion.puedeAbrir(fila.ruta);
  }

  protected puedeRevisar(fila: FilaSeguimiento): boolean {
    return !!fila.rutaRevision && this.navegacion.puedeAbrir(fila.rutaRevision);
  }

  protected async abrir(ruta: string): Promise<void> {
    if (!(await this.navegacion.abrir(ruta))) {
      this.aviso.set('Ese registro no tiene una pantalla a la que llevarte todavía.');
    }
  }

  // --- Presentación -------------------------------------------------------

  protected fecha(fila: FilaSeguimiento): string {
    return fila.fechaOrden ? fechaCorta(fila.fechaOrden) : 'sin fecha';
  }

  /**
   * El `ultimoHito` es texto del cable y **cambia de forma según el proceso**:
   * la prospección manda una frase, la captación otra, y oportunidad, solicitud
   * y cierre mandan una **fecha ISO pelada**. Se formatea solo esa: dejar un
   * `2026-07-16T14:00` en una columna que dice "último hito" obliga al lector a
   * traducir. Lo que no es fecha se muestra tal cual, sin tocarlo.
   */
  protected hito(fila: FilaSeguimiento): string {
    const valor = (fila.ultimoHito ?? '').trim();
    return /^\d{4}-\d{2}-\d{2}([T ]|$)/.test(valor) ? fechaHora(valor) : valor;
  }

  /** El monto viaja en texto plano; se agrupa cuando de verdad es un número. */
  protected importe(fila: FilaSeguimiento): string {
    const valor = (fila.monto ?? '').trim();
    if (!valor) {
      return '';
    }
    const numerico = Number(valor);
    return Number.isFinite(numerico) ? numero(numerico) : valor;
  }

  /**
   * El tono viaja del backend con los nombres del legado (`blue`, `green`,
   * `gray`, `info`) y aquí se traduce al vocabulario del badge. No se deriva
   * del proceso: es el cable el que decide.
   */
  protected tono(fila: FilaSeguimiento): string {
    switch (fila.tono) {
      case 'green':
        return 'bien';
      case 'red':
        return 'mal';
      case 'amber':
        return 'aviso';
      default:
        return '';
    }
  }
}
