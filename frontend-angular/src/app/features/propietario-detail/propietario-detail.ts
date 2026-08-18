import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { describir, TIPO_DOCUMENTO, TIPO_PERSONA } from '../../core/api/codigos';
import {
  esPendiente,
  FichaComercialService,
  FichaPropietario,
  FilaFicha,
  FILAS_POR_SECCION,
  SeccionFicha,
  SECCIONES_PROPIETARIO,
} from '../../core/api/ficha-comercial.service';
import { Propietario, PropietariosService } from '../../core/api/propietarios.service';
import { AuthService } from '../../core/auth/auth.service';
import { ConstanciaAutorizacion } from '../../core/autorizacion';
import { fechaCorta, SIN_DATO, texto as textoDe } from '../../core/formato';
import { ConstanciaAutorizacionPanel } from '../../shared/constancia-autorizacion/constancia-autorizacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { Paginacion } from '../../shared/paginacion/paginacion';

const ETIQUETAS: Readonly<Record<string, string>> = {
  locales: 'Locales',
  prospecciones: 'Prospecciones',
  captaciones: 'Captaciones',
  oportunidades: 'Oportunidades',
  solicitudes: 'Solicitudes',
  cierres: 'Cierres',
  agentes: 'Agentes',
};

/**
 * Rutas del cable que YA tienen pantalla en el SPA. Misma convención que
 * `ClienteDetail`: una `ruta` que apunta a una pantalla sin migrar se muestra
 * como texto, no como enlace roto.
 */
const RUTAS_MIGRADAS: Readonly<Record<string, (id: string) => string[]>> = {
  'local-detail': (id) => ['/propiedades', id],
  'cliente-detail': (id) => ['/clientes', id],
  'prospeccion-detail': (id) => ['/prospecciones', id],
  'captacion-detail': (id) => ['/captaciones', id],
};

/**
 * Ficha comercial del propietario (E3). Hermana de `ClienteDetail` y con el
 * mismo patrón de secciones, pero **mira la oferta**: locales, prospecciones y
 * captaciones donde aquella mira requerimientos, interacciones y visitas.
 *
 * Tres cosas del contrato que esta pantalla tiene que respetar y que son fáciles
 * de "arreglar" por error:
 *
 * 1. **`cantidadLocales` de la cabecera viaja en 0** aunque la sección
 *    `locales` traiga registros. Es una rareza legacy conservada a propósito, así
 *    que el número que se muestra sale del `totalRecords` de la sección, no del
 *    contador.
 * 2. **`prospecciones` y `captaciones` llegan con el total calculado pero
 *    `items` vacío.** No son marcadores pendientes —su `totalRecords` no es
 *    negativo— así que `esPendiente` las da por resueltas; hay que llevar la
 *    cuenta aparte de cuáles se han pedido de verdad o esas dos pestañas se
 *    verían vacías para siempre. Es la única asimetría con la ficha de cliente.
 * 3. **El tope real son 8 filas por sección**: pedir más no trae más.
 *
 * El alcance lo impone el backend y no se replica aquí: el AGENTE ve solo su
 * historia y sin nombre de agente, el BROKER su equipo o las captaciones que
 * revisa, el ADMIN el tenant.
 */
@Component({
  selector: 'app-propietario-detail',
  imports: [ConstanciaAutorizacionPanel, EstadoListado, Paginacion],
  templateUrl: './propietario-detail.html',
  styleUrl: './propietario-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropietarioDetail implements OnInit {
  private readonly api = inject(FichaComercialService);
  private readonly propietariosApi = inject(PropietariosService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ficha = signal<FichaPropietario | null>(null);
  protected readonly secciones = signal<Record<string, SeccionFicha>>({});
  protected readonly activa = signal<string>('locales');
  protected readonly cargandoSeccion = signal(false);
  protected readonly errorSeccion = signal<string | null>(null);
  protected readonly idPropietario = signal<number>(0);

  /**
   * Secciones realmente traídas del backend. No se puede deducir de
   * `esPendiente` por el punto 2 de la cabecera del componente.
   */
  private readonly resueltas = signal<ReadonlySet<string>>(new Set(['locales']));

  // --- Autorización de datos (D-27): constancia de la PERSONA ---
  protected readonly autorizacion = signal<ConstanciaAutorizacion | null>(null);
  protected readonly cargandoAutorizacion = signal(false);
  protected readonly errorAutorizacion = signal<string | null>(null);

  protected readonly porPagina = FILAS_POR_SECCION;
  protected readonly esAgente = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly supervisa = computed(() => !this.esAgente());
  protected readonly propietario = computed<Propietario | null>(
    () => this.ficha()?.propietario ?? null,
  );

  protected readonly pestanas = computed(() =>
    SECCIONES_PROPIETARIO.filter((s) => s !== 'agentes' || this.supervisa()).map((clave) => ({
      clave,
      etiqueta: ETIQUETAS[clave] ?? clave,
      total: this.totalDe(clave),
      /** Nunca pedida: se dibuja el total como guion, no como cero. */
      incognita: !this.resueltas().has(clave) && esPendiente(this.secciones()[clave]),
    })),
  );

  protected readonly seccionActiva = computed<SeccionFicha | undefined>(
    () => this.secciones()[this.activa()],
  );
  protected readonly filas = computed<FilaFicha[]>(() => this.seccionActiva()?.items ?? []);
  protected readonly totalActiva = computed(() =>
    Math.max(0, this.seccionActiva()?.totalRecords ?? 0),
  );
  protected readonly paginaActiva = computed(() => Math.max(1, this.seccionActiva()?.page ?? 1));

  /**
   * El número de locales bueno es el de la SECCIÓN, no el
   * `propietario.cantidadLocales` de la cabecera, que el cable manda en 0.
   */
  protected readonly locales = computed(() =>
    this.resueltas().has('locales') ? this.totalDe('locales') : null,
  );

  protected readonly muestraLocal = computed(() => this.columnaConDatos((f) => f.local));
  protected readonly muestraDistrito = computed(() => this.columnaConDatos((f) => f.distrito));
  protected readonly muestraCliente = computed(() => this.columnaConDatos((f) => f.cliente));
  protected readonly muestraAgente = computed(
    () => this.supervisa() && this.columnaConDatos((f) => f.agente),
  );

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('El propietario indicado no es válido.');
      this.cargando.set(false);
      return;
    }
    this.idPropietario.set(id);
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    // En paralelo y sin bloquear la ficha: si el endpoint de la autorización
    // falla, la historia comercial se sigue viendo y el panel muestra su error.
    void this.cargarAutorizacion();
    this.api.fichaPropietario$(this.idPropietario()).subscribe({
      next: (ficha) => {
        this.ficha.set(ficha);
        this.secciones.set({ ...ficha.sections });
        this.resueltas.set(new Set(['locales']));
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.error.set(
          error instanceof ApiError ? error.message : 'No se pudo cargar la ficha del propietario.',
        );
        this.cargando.set(false);
      },
    });
  }

  private async cargarAutorizacion(): Promise<void> {
    this.cargandoAutorizacion.set(true);
    this.errorAutorizacion.set(null);
    try {
      this.autorizacion.set(await this.propietariosApi.autorizacion(this.idPropietario()));
    } catch (error) {
      this.autorizacion.set(null);
      this.errorAutorizacion.set(
        error instanceof ApiError ? error.message : 'No se pudo consultar la autorización de datos.',
      );
    } finally {
      this.cargandoAutorizacion.set(false);
    }
  }

  protected abrir(seccion: string): void {
    this.activa.set(seccion);
    this.errorSeccion.set(null);
    // Se pide si NUNCA se trajo de verdad. Volver a una pestaña ya vista no
    // vuelve a llamar al backend, y `prospecciones`/`captaciones` se piden
    // aunque su total venga resuelto, porque sus `items` llegan vacíos.
    if (!this.resueltas().has(seccion)) {
      this.pedirSeccion(seccion, 1);
    }
  }

  protected cambiarPagina(pagina: number): void {
    this.pedirSeccion(this.activa(), pagina);
  }

  protected reintentarSeccion(): void {
    this.pedirSeccion(this.activa(), this.paginaActiva());
  }

  private pedirSeccion(seccion: string, pagina: number): void {
    this.cargandoSeccion.set(true);
    this.errorSeccion.set(null);
    this.api.seccionPropietario$(this.idPropietario(), seccion, pagina).subscribe({
      next: (datos) => {
        this.secciones.update((actuales) => ({ ...actuales, [seccion]: datos }));
        this.resueltas.update((actuales) => new Set([...actuales, seccion]));
        this.cargandoSeccion.set(false);
      },
      error: (error: unknown) => {
        this.errorSeccion.set(
          error instanceof ApiError
            ? error.message
            : `No se pudo cargar la sección ${this.etiquetaSeccion(seccion)}.`,
        );
        this.cargandoSeccion.set(false);
      },
    });
  }

  protected editar(): void {
    void this.router.navigate(['/propietarios', this.idPropietario(), 'editar']);
  }

  protected volver(): void {
    void this.router.navigate(['/propietarios']);
  }

  protected navegar(fila: FilaFicha): void {
    const destino = this.destinoDe(fila);
    if (destino) void this.router.navigate(destino);
  }

  protected tieneDestino(fila: FilaFicha): boolean {
    return this.destinoDe(fila) !== null;
  }

  // -- presentación --------------------------------------------------------

  protected etiquetaSeccion(clave: string): string {
    return ETIQUETAS[clave] ?? clave;
  }

  /** La fecha en formato corto; el ISO crudo no es para leerse. */
  protected fechaLegible(valor: string | undefined | null): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected tipoPersona(codigo: string | undefined): string {
    return describir(TIPO_PERSONA, codigo) || SIN_DATO;
  }

  protected documento(): string {
    const propietario = this.propietario();
    if (!propietario) return SIN_DATO;
    const tipo = describir(TIPO_DOCUMENTO, propietario.tipoDocumento);
    const numero = textoDe(propietario.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected activo(): boolean {
    return this.propietario()?.estado === 'A';
  }

  private totalDe(seccion: string): number {
    return Math.max(0, this.secciones()[seccion]?.totalRecords ?? 0);
  }

  private columnaConDatos(dato: (fila: FilaFicha) => string | undefined): boolean {
    return this.filas().some((fila) => {
      const valor = (dato(fila) ?? '').trim();
      return valor !== '' && valor !== '-' && valor !== SIN_DATO;
    });
  }

  private destinoDe(fila: FilaFicha): string[] | null {
    const ruta = (fila.ruta ?? '').trim();
    if (!ruta) return null;
    const [pantalla, ...resto] = ruta.split('/');
    const id = resto.join('/');
    const destino: ((valor: string) => string[]) | undefined = RUTAS_MIGRADAS[pantalla];
    return destino !== undefined && id ? destino(id) : null;
  }
}
