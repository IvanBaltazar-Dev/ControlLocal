import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../core/api/api.types';
import { Cliente, ClientesService } from '../../core/api/clientes.service';
import {
  describir,
  ESTADO_REQUERIMIENTO,
  opcionesDe,
  TIPO_DOCUMENTO,
  TIPO_INMUEBLE_COMERCIAL,
  TIPO_PERSONA,
} from '../../core/api/codigos';
import { Coincidencia, CoincidenciasService } from '../../core/api/coincidencias.service';
import {
  esPendiente,
  FichaCliente,
  FichaComercialService,
  FILAS_POR_SECCION,
  FilaFicha,
  SeccionFicha,
  SECCIONES_CLIENTE,
} from '../../core/api/ficha-comercial.service';
import { Requerimiento, RequerimientosService } from '../../core/api/requerimientos.service';
import { AuthService } from '../../core/auth/auth.service';
import { ConstanciaAutorizacion } from '../../core/autorizacion';
import { fechaCorta, SIN_DATO, texto as textoDe } from '../../core/formato';
import { ConstanciaAutorizacionPanel } from '../../shared/constancia-autorizacion/constancia-autorizacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { Paginacion } from '../../shared/paginacion/paginacion';

const ETIQUETAS: Readonly<Record<string, string>> = {
  requerimientos: 'Requerimientos',
  propiedades: 'Propiedades',
  oportunidades: 'Oportunidades',
  interacciones: 'Interacciones',
  visitas: 'Visitas',
  solicitudes: 'Solicitudes',
  cierres: 'Cierres',
  agentes: 'Agentes',
};

/**
 * Rutas del cable que YA tienen pantalla en el SPA. El resto de las `ruta` que
 * devuelve la ficha apuntan a pantallas sin migrar, y por convención de la casa
 * no se ofrece un enlace que no lleva a ninguna parte (misma decisión que
 * `LocalDetail` con "Crear captación").
 */
const RUTAS_MIGRADAS: Readonly<Record<string, (id: string) => string[]>> = {
  'local-detail': (id) => ['/propiedades', id],
  'cliente-detail': (id) => ['/clientes', id],
  'prospeccion-detail': (id) => ['/prospecciones', id],
  'captacion-detail': (id) => ['/captaciones', id],
};

/**
 * Ficha comercial del cliente. **Primera pantalla del SPA que consume la ficha
 * por secciones de E3**, así que fija el patrón para la del propietario.
 *
 * Lo que hay que entender del cable antes de tocarla: la carga inicial es
 * **parcial**. Solo `requerimientos` viene resuelto; las demás secciones llegan
 * con `totalRecords: -1`, que es un **marcador de pendiente**, no un total.
 * Cada pestaña se pide al abrirla y se guarda, de modo que volver a ella no
 * vuelve a llamar al backend. Una sección legítimamente vacía trae `0` y
 * tampoco se vuelve a pedir: por eso la distinción se hace por el marcador y no
 * por `items.length`.
 */
@Component({
  selector: 'app-cliente-detail',
  imports: [ConstanciaAutorizacionPanel, EstadoListado, Paginacion, ReactiveFormsModule],
  templateUrl: './cliente-detail.html',
  styleUrl: './cliente-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClienteDetail implements OnInit {
  private readonly api = inject(FichaComercialService);
  private readonly clientesApi = inject(ClientesService);
  private readonly requerimientosApi = inject(RequerimientosService);
  private readonly coincidenciasApi = inject(CoincidenciasService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ficha = signal<FichaCliente | null>(null);
  protected readonly secciones = signal<Record<string, SeccionFicha>>({});
  protected readonly activa = signal<string>('requerimientos');
  protected readonly cargandoSeccion = signal(false);
  protected readonly errorSeccion = signal<string | null>(null);
  protected readonly idCliente = signal<number>(0);

  // --- Requerimientos: la búsqueda declarada del cliente ---
  // Se piden aparte de la ficha (`GET /requerimientos/cliente/{id}`) porque la
  // ficha solo trae filas descriptivas y aquí hacen falta los valores para
  // editarlos. Se cargan al abrir la pestaña, no al entrar.
  protected readonly requerimientos = signal<Requerimiento[]>([]);
  protected readonly cargandoRequerimientos = signal(false);
  protected readonly errorRequerimientos = signal<string | null>(null);
  protected readonly requerimientosPedidos = signal(false);
  /** null = editor cerrado; 0 = alta; >0 = edición de ese requerimiento. */
  protected readonly editando = signal<number | null>(null);
  protected readonly guardandoRequerimiento = signal(false);

  // --- Autorización de datos (D-27): constancia de la PERSONA ---
  protected readonly autorizacion = signal<ConstanciaAutorizacion | null>(null);
  protected readonly cargandoAutorizacion = signal(false);
  protected readonly errorAutorizacion = signal<string | null>(null);

  // --- Coincidencias: propiedades de cartera que encajan con su búsqueda ---
  protected readonly coincidencias = signal<Coincidencia[]>([]);
  protected readonly totalCoincidencias = signal(0);
  protected readonly cargandoCoincidencias = signal(false);
  protected readonly errorCoincidencias = signal<string | null>(null);
  protected readonly coincidenciasAbiertas = signal(false);

  protected readonly formulario = this.fb.group({
    rubro: this.fb.control('', [Validators.required, Validators.maxLength(120)]),
    tipoInmueble: this.fb.control('LOCAL_COMERCIAL'),
    moneda: this.fb.control('PEN'),
    rentaMin: this.fb.control<number | null>(null),
    rentaMax: this.fb.control<number | null>(null),
    metrajeMin: this.fb.control<number | null>(null),
    metrajeMax: this.fb.control<number | null>(null),
    frenteMinimo: this.fb.control<number | null>(null),
    estado: this.fb.control('A', [Validators.required]),
    distritos: this.fb.control(''),
    observaciones: this.fb.control('', [Validators.maxLength(1000)]),
  });

  protected readonly opcionesEstadoRequerimiento: OpcionFiltro[] = opcionesDe(ESTADO_REQUERIMIENTO);
  protected readonly opcionesTipoInmueble: OpcionFiltro[] = opcionesDe(TIPO_INMUEBLE_COMERCIAL);

  protected readonly porPagina = FILAS_POR_SECCION;
  protected readonly esAgente = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly supervisa = computed(() => !this.esAgente());
  protected readonly cliente = computed<Cliente | null>(() => this.ficha()?.cliente ?? null);
  protected readonly hayBusquedaActiva = computed(() => !!this.ficha()?.requerimientoActivo);

  /** El agente no ve la pestaña de agentes: en su ficha no aporta nada. */
  protected readonly pestanas = computed(() =>
    SECCIONES_CLIENTE.filter((s) => s !== 'agentes' || this.supervisa()).map((clave) => ({
      clave,
      etiqueta: ETIQUETAS[clave] ?? clave,
      total: this.totalDe(clave),
    })),
  );
  protected readonly seccionActiva = computed<SeccionFicha | undefined>(
    () => this.secciones()[this.activa()],
  );
  protected readonly filas = computed<FilaFicha[]>(() => this.seccionActiva()?.items ?? []);
  protected readonly totalActiva = computed(() => Math.max(0, this.seccionActiva()?.totalRecords ?? 0));
  protected readonly paginaActiva = computed(() => Math.max(1, this.seccionActiva()?.page ?? 1));

  /**
   * Métricas de la cabecera: totales de tres secciones del propio alcance.
   *
   * `valor: null` significa **todavía no pedida**, y se dibuja como guion. Un
   * cero ahí sería mentira: la carga inicial es parcial, así que "0
   * oportunidades" y "aún no lo sé" son cosas distintas y la pantalla no puede
   * confundirlas. Se resuelve solo cuando el usuario abre esa pestaña.
   */
  protected readonly metricas = computed(() =>
    ['oportunidades', 'solicitudes', 'cierres'].map((clave) => ({
      etiqueta: ETIQUETAS[clave],
      valor: esPendiente(this.secciones()[clave]) ? null : this.totalDe(clave),
    })),
  );

  // Columnas opcionales: se dibujan si la sección activa trae ese dato. La de
  // agente, además, solo para quien supervisa.
  protected readonly muestraLocal = computed(() => this.columnaConDatos((f) => f.local));
  protected readonly muestraDistrito = computed(() => this.columnaConDatos((f) => f.distrito));
  protected readonly muestraPropietario = computed(() => this.columnaConDatos((f) => f.propietario));
  protected readonly muestraAgente = computed(
    () => this.supervisa() && this.columnaConDatos((f) => f.agente),
  );

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.error.set('El cliente indicado no es válido.');
      this.cargando.set(false);
      return;
    }
    this.idCliente.set(id);
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    // La constancia va en paralelo y NO bloquea la ficha: es un dato de
    // cumplimiento, y si su endpoint falla la historia comercial se sigue
    // viendo. El panel muestra su propio error.
    void this.cargarAutorizacion();
    this.api.fichaCliente$(this.idCliente()).subscribe({
      next: (ficha) => {
        this.ficha.set(ficha);
        this.secciones.set({ ...ficha.sections });
        this.cargando.set(false);
        // `requerimientos` es la pestaña activa al entrar, así que su editor se
        // dibuja de inmediato: si no se piden aquí, se vería vacío hasta que
        // el usuario cambie de pestaña y vuelva.
        if (this.activa() === 'requerimientos' && !this.requerimientosPedidos()) {
          void this.cargarRequerimientos();
        }
      },
      error: (error: unknown) => {
        this.error.set(
          error instanceof ApiError ? error.message : 'No se pudo cargar la ficha del cliente.',
        );
        this.cargando.set(false);
      },
    });
  }

  private async cargarAutorizacion(): Promise<void> {
    this.cargandoAutorizacion.set(true);
    this.errorAutorizacion.set(null);
    try {
      this.autorizacion.set(await this.clientesApi.autorizacion(this.idCliente()));
    } catch (error) {
      this.autorizacion.set(null);
      this.errorAutorizacion.set(
        mensajeDe(error, 'No se pudo consultar la autorización de datos.'),
      );
    } finally {
      this.cargandoAutorizacion.set(false);
    }
  }

  protected abrir(seccion: string): void {
    this.activa.set(seccion);
    this.errorSeccion.set(null);
    // Solo se pide si nunca se resolvió: volver a una pestaña ya vista no
    // vuelve a llamar al backend.
    if (esPendiente(this.secciones()[seccion])) {
      this.pedirSeccion(seccion, 1);
    }
    // El editor necesita los valores del requerimiento, que la ficha no trae:
    // se piden la primera vez que se abre esa pestaña.
    if (seccion === 'requerimientos' && !this.requerimientosPedidos()) {
      void this.cargarRequerimientos();
    }
  }

  protected async cargarRequerimientos(): Promise<void> {
    this.cargandoRequerimientos.set(true);
    this.errorRequerimientos.set(null);
    try {
      this.requerimientos.set(await this.requerimientosApi.porCliente(this.idCliente()));
      this.requerimientosPedidos.set(true);
    } catch (error) {
      this.requerimientos.set([]);
      this.errorRequerimientos.set(
        mensajeDe(error, 'No se pudieron cargar los requerimientos del cliente.'),
      );
    } finally {
      this.cargandoRequerimientos.set(false);
    }
  }

  protected nuevoRequerimiento(): void {
    this.formulario.reset({
      rubro: this.cliente()?.rubroComercial ?? '',
      tipoInmueble: 'LOCAL_COMERCIAL',
      moneda: 'PEN',
      rentaMin: null,
      rentaMax: null,
      metrajeMin: null,
      metrajeMax: null,
      frenteMinimo: null,
      estado: 'A',
      distritos: '',
      observaciones: '',
    });
    this.errorRequerimientos.set(null);
    this.editando.set(0);
  }

  protected editarRequerimiento(requerimiento: Requerimiento): void {
    this.formulario.reset({
      rubro: requerimiento.rubro ?? '',
      tipoInmueble: requerimiento.tipoInmueble ?? 'LOCAL_COMERCIAL',
      moneda: requerimiento.moneda ?? 'PEN',
      rentaMin: requerimiento.rentaMin ?? null,
      rentaMax: requerimiento.rentaMax ?? null,
      metrajeMin: requerimiento.metrajeMin ?? null,
      metrajeMax: requerimiento.metrajeMax ?? null,
      frenteMinimo: requerimiento.frenteMinimo ?? null,
      estado: requerimiento.estado ?? 'A',
      distritos: (requerimiento.distritos ?? []).join(', '),
      observaciones: requerimiento.observaciones ?? '',
    });
    this.errorRequerimientos.set(null);
    this.editando.set(requerimiento.id);
  }

  protected cerrarEditor(): void {
    if (!this.guardandoRequerimiento()) this.editando.set(null);
  }

  /**
   * Alta y edición comparten formulario. Ninguno de los límites es
   * obligatorio: un requerimiento sin renta ni metraje es válido y el matching
   * cuenta esos criterios como NO_APLICA en vez de fallar. Exigirlos aquí sería
   * una regla inventada.
   */
  protected async guardarRequerimiento(): Promise<void> {
    const objetivo = this.editando();
    if (objetivo === null || this.guardandoRequerimiento()) return;
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardandoRequerimiento.set(true);
    this.errorRequerimientos.set(null);
    try {
      const datos = this.formulario.getRawValue();
      const cuerpo = {
        idCliente: this.idCliente(),
        rubro: datos.rubro.trim(),
        tipoInmueble: datos.tipoInmueble,
        moneda: datos.moneda,
        rentaMin: datos.rentaMin ?? undefined,
        rentaMax: datos.rentaMax ?? undefined,
        metrajeMin: datos.metrajeMin ?? undefined,
        metrajeMax: datos.metrajeMax ?? undefined,
        frenteMinimo: datos.frenteMinimo ?? undefined,
        estado: datos.estado,
        observaciones: datos.observaciones.trim() || undefined,
        // El cable espera NOMBRES de distrito, no ids del catálogo.
        distritos: datos.distritos
          .split(',')
          .map((d) => d.trim())
          .filter((d) => d.length > 0),
      };
      if (objetivo === 0) {
        await this.requerimientosApi.crear(cuerpo);
      } else {
        await this.requerimientosApi.actualizar(objetivo, cuerpo);
      }
      this.editando.set(null);
      await this.cargarRequerimientos();
      // La ficha deriva de esto: su sección y el aviso de búsqueda activa
      // quedarían desfasados si no se recarga.
      this.cargar();
      this.coincidenciasAbiertas.set(false);
    } catch (error) {
      this.errorRequerimientos.set(mensajeDe(error, 'No se pudo guardar el requerimiento.'));
    } finally {
      this.guardandoRequerimiento.set(false);
    }
  }

  /** Pausar, cerrar o reactivar: tiene endpoint propio, no es parte del PUT. */
  protected async cambiarEstadoRequerimiento(
    requerimiento: Requerimiento,
    estado: string,
  ): Promise<void> {
    if (this.guardandoRequerimiento()) return;
    this.guardandoRequerimiento.set(true);
    this.errorRequerimientos.set(null);
    try {
      await this.requerimientosApi.cambiarEstado(requerimiento.id, estado);
      await this.cargarRequerimientos();
      this.cargar();
    } catch (error) {
      this.errorRequerimientos.set(
        mensajeDe(error, 'No se pudo cambiar el estado del requerimiento.'),
      );
    } finally {
      this.guardandoRequerimiento.set(false);
    }
  }

  /**
   * Propiedades de cartera que encajan con la búsqueda declarada.
   *
   * Se pide **bajo demanda**, no al entrar: es una consulta que recorre las
   * captaciones activas y solo interesa cuando alguien va a proponer algo.
   *
   * Ojo con el sobre: en esta dirección el `id` de cada fila es el de la
   * **captación**, no el de la propiedad —la oferta viaja por su captación—, y
   * es justo lo que necesita el alta de oportunidad.
   */
  protected async verCoincidencias(): Promise<void> {
    this.coincidenciasAbiertas.set(true);
    this.cargandoCoincidencias.set(true);
    this.errorCoincidencias.set(null);
    try {
      const sobre = await firstValueFrom(
        this.coincidenciasApi.paraCliente$(this.idCliente(), 1, 6),
      );
      this.coincidencias.set(sobre.items ?? []);
      this.totalCoincidencias.set(sobre.total ?? 0);
    } catch (error) {
      this.coincidencias.set([]);
      this.totalCoincidencias.set(0);
      this.errorCoincidencias.set(
        mensajeDe(error, 'No se pudieron calcular las coincidencias de cartera.'),
      );
    } finally {
      this.cargandoCoincidencias.set(false);
    }
  }

  /** "Proponer" abre el alta de oportunidad con cliente y captación fijados. */
  protected proponer(coincidencia: Coincidencia): void {
    void this.router.navigate(['/oportunidades/nueva'], {
      queryParams: {
        cliente: this.idCliente(),
        captacion: coincidencia.captacionId ?? coincidencia.id,
      },
    });
  }

  protected verBitacora(): void {
    void this.router.navigate(['/clientes', this.idCliente(), 'contacto']);
  }

  protected etiquetaEstadoRequerimiento(codigo: string | undefined): string {
    return describir(ESTADO_REQUERIMIENTO, codigo) || SIN_DATO;
  }

  protected etiquetaTipoInmueble(codigo: string | undefined): string {
    return describir(TIPO_INMUEBLE_COMERCIAL, codigo) || SIN_DATO;
  }

  protected tonoRequerimiento(codigo: string | undefined): string {
    if (codigo === 'A') return 'bien';
    if (codigo === 'C') return 'mal';
    return 'aviso';
  }

  /** Rango legible: los dos extremos son opcionales por separado. */
  protected rango(minimo: number | undefined, maximo: number | undefined, sufijo = ''): string {
    if (minimo === undefined && maximo === undefined) return SIN_DATO;
    if (minimo !== undefined && maximo !== undefined) return `${minimo} – ${maximo}${sufijo}`;
    return minimo !== undefined ? `desde ${minimo}${sufijo}` : `hasta ${maximo}${sufijo}`;
  }

  protected cambiarPagina(pagina: number): void {
    this.pedirSeccion(this.activa(), pagina);
  }

  protected reintentarSeccion(): void {
    this.pedirSeccion(this.activa(), this.paginaActiva());
  }

  protected volver(): void {
    void this.router.navigate(['/clientes']);
  }

  protected editar(): void {
    void this.router.navigate(['/clientes', this.idCliente(), 'editar']);
  }

  /**
   * La ficha devuelve rutas del cable (`local-detail/9`). Solo se convierten en
   * enlace las que tienen pantalla; el resto se muestran como texto.
   */
  protected navegar(fila: FilaFicha): void {
    const destino = this.destinoDe(fila);
    if (destino) void this.router.navigate(destino);
  }

  protected tieneDestino(fila: FilaFicha): boolean {
    return this.destinoDe(fila) !== null;
  }

  protected etiquetaSeccion(clave: string): string {
    return ETIQUETAS[clave] ?? clave;
  }

  protected tipoPersona(): string {
    return describir(TIPO_PERSONA, this.cliente()?.tipoPersona) || SIN_DATO;
  }

  protected documento(): string {
    const cliente = this.cliente();
    const tipo = describir(TIPO_DOCUMENTO, cliente?.tipoDocumento);
    const numero = textoDe(cliente?.numeroDocumento);
    return tipo ? `${tipo} ${numero}` : numero;
  }

  protected activo(): boolean {
    return this.cliente()?.estado !== 'I';
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private pedirSeccion(seccion: string, pagina: number): void {
    this.cargandoSeccion.set(true);
    this.errorSeccion.set(null);
    this.api.seccionCliente$(this.idCliente(), seccion, pagina).subscribe({
      next: (datos) => {
        this.secciones.update((actuales) => ({ ...actuales, [seccion]: datos }));
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

  /** Un total negativo es el marcador de pendiente: se muestra como vacío. */
  private totalDe(seccion: string): number {
    return Math.max(0, this.secciones()[seccion]?.totalRecords ?? 0);
  }

  /**
   * Una columna se dibuja solo si la sección activa trae ese dato. Ojo con el
   * relleno: **el cable normaliza los nulos descriptivos a `-`** (guion), no al
   * guion largo que usa el SPA para lo ausente. Comparar solo contra `SIN_DATO`
   * dejaba columnas enteras de guiones.
   */
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
    // `constructor` es una propiedad de Object y TypeScript la da por siempre
    // definida; de ahí el nombre distinto y la comprobación explícita.
    const destino: ((valor: string) => string[]) | undefined = RUTAS_MIGRADAS[pantalla];
    return destino !== undefined && id ? destino(id) : null;
  }
}

function mensajeDe(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
