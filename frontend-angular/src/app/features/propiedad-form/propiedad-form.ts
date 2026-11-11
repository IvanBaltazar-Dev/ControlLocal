import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  BloqueOperacion,
  CapturaService,
  DefinicionCaptura,
  EstadoCaptura,
  PreguntaCaptura,
  preguntasDe,
  RevisionCaptura,
  ResultadoActuacionCaptura,
  ReferenciaCapturaPendiente,
} from '../../core/api/captura.service';
import {
  DatosPropietario,
  Propietario,
  PropietariosService,
} from '../../core/api/propietarios.service';
import { CampoGobernado } from '../../shared/campo-gobernado/campo-gobernado';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { BroxPageHeader } from '../../shared/page-header/page-header';

const PROPIETARIOS_POR_PAGINA = 50;

/** Un titular elegido, con su cuota. Varias personas pueden serlo a la vez. */
interface TitularElegido {
  readonly id: number;
  readonly nombre: string;
  readonly documento: string;
  cuota: number | null;
  representante: boolean;
}

/**
 * **El alta universal de propiedades. Una sola, para los siete tipos.**
 *
 * ## Por qué no hay un formulario por tipo
 *
 * Habría siete formularios, siete validaciones, siete sitios donde una regla
 * puede divergir y siete que KAIROS tendría que reproducir. Esta pantalla no
 * sabe qué se le pregunta a una casa ni a un terreno: **se lo pregunta a BROX
 * Core** (`GET /captura/definicion`) y pinta lo que recibe.
 *
 * La consecuencia práctica es la que importa: añadir *Almacén* —o un atributo
 * nuevo a un tipo que ya existe— no toca este fichero. Es una fila del
 * catálogo.
 *
 * ## Qué decide esta pantalla y qué no
 *
 * Decide **presentación**: en qué orden van las secciones, cuál se pinta
 * plegada, qué control dibuja para un `SELECTOR` y qué para un `INTERRUPTOR`.
 *
 * No decide **negocio**: ni qué campos aplican, ni cuáles son obligatorios, ni
 * qué rango admite un número, ni cómo se llama el importe de una venta. Todo
 * eso llega en el contrato. El único sitio donde mira la clave de un campo es
 * para pasarle al motor los dos datos que la definición necesita como
 * parámetros —el tipo y las operaciones—, que es exactamente lo que
 * `GET /captura/apertura` devuelve (D-A-1 §4).
 *
 * ## Venta y alquiler no es un caso especial
 *
 * Elegir las dos operaciones no cambia nada de esta clase: la definición
 * devuelve **dos bloques económicos** en vez de uno y la plantilla los recorre.
 * Al confirmar sale **una propiedad con dos encargos**, cada uno con su precio
 * y su histórico. No existe `AMBAS`.
 *
 * ## Nada se ejecuta hasta confirmar
 *
 * Abrir la pantalla abre un *borrador*, que anota lo que se va sabiendo y no
 * crea nada del negocio. Cada avance queda disponible para retomarlo; volver
 * sólo sale de la pantalla. La confirmación identifica propuesta, contenido y
 * prueba de revisión con un evento humano estable. Si la respuesta se pierde,
 * la recuperación consulta esa actuación sin emitir otra confirmación.
 */
@Component({
  selector: 'app-propiedad-form',
  imports: [CampoGobernado, EstadoListado, LowerCasePipe, BroxPageHeader],
  templateUrl: './propiedad-form.html',
  styleUrl: './propiedad-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropiedadForm implements OnInit {
  private readonly captura = inject(CapturaService);
  private readonly propietariosApi = inject(PropietariosService);
  private readonly router = inject(Router);

  /**
   * Las claves que el motor pide como parámetros de la definición. No son una
   * lista de campos: son los dos nombres que hay que saber para poder
   * preguntar, y llegan declarados en `apertura`.
   */
  private static readonly CLAVE_TIPO = 'tipoPropiedad';
  private static readonly CLAVE_OPERACIONES = 'operaciones';

  private paginaPropietarios = 0;
  private secuenciaDefinicion = 0;
  /** Se genera una vez por alta: si se reintenta, tiene que ser LA MISMA. */
  private claveIdempotencia = crearClave();

  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly cargandoDefinicion = signal(false);

  protected readonly apertura = signal<readonly PreguntaCaptura[]>([]);
  protected readonly definicion = signal<DefinicionCaptura | null>(null);
  protected readonly borrador = signal<EstadoCaptura | null>(null);
  protected readonly capturasEnCurso = signal<readonly EstadoCaptura[]>([]);
  protected readonly valores = signal<Record<string, string>>({});
  protected readonly titulares = signal<readonly TitularElegido[]>([]);
  protected readonly revisando = signal(false);
  protected readonly revisionActuacion = signal<RevisionCaptura | null>(null);
  protected readonly resultadoActuacion = signal<ResultadoActuacionCaptura | null>(null);
  protected readonly referenciasPendientes = signal<ReferenciaCapturaPendiente[]>([]);
  protected readonly enVerificacion = computed(() => {
    const resultado = this.resultadoActuacion();
    return resultado?.estado === 'EN_VERIFICACION';
  });

  protected readonly propietarios = signal<readonly Propietario[]>([]);
  protected readonly totalPropietarios = signal(0);
  protected readonly busquedaPropietario = signal('');
  protected readonly cargandoPropietarios = signal(false);
  protected readonly altaPropietarioAbierta = signal(false);
  protected readonly errorPropietario = signal<string | null>(null);

  // ------------------------------------------------------------------
  // Lo que la plantilla necesita saber
  // ------------------------------------------------------------------

  protected readonly tipoElegido = computed(() => this.valores()[PropiedadForm.CLAVE_TIPO] ?? '');
  protected readonly operacionesElegidas = computed(
    () => this.valores()[PropiedadForm.CLAVE_OPERACIONES] ?? '',
  );

  /**
   * Hasta que el **tipo** esté, no hay plan que pintar.
   *
   * <p>La operación ya no hace falta: desde V75 una propiedad puede registrarse
   * sin encargo —para prospectarla— y el encargo nace cuando el propietario
   * acepta. Exigirla aquí dejaba la pantalla parada en la apertura, así que el
   * backend aceptaba `operaciones: []` y desde BROX Web no había forma de
   * mandarlo: la única puerta humana del alta se quedaba sin la salida.
   *
   * <p>Lo que sí sigue mandando el tipo: es él quien decide qué se pregunta.
   */
  protected readonly planListo = computed(
    () => !!this.tipoElegido() && !!this.definicion(),
  );

  /**
   * ¿Se está registrando sin encargo? La pantalla lo dice en voz alta para que
   * nadie crea que se le olvidó elegir: registrar no es encargar.
   */
  protected readonly sinOperaciones = computed(() => !this.operacionesElegidas());

  /**
   * El bloque de titularidad. Se separa por su **control**, no por su clave: es
   * el contrato el que declara que ese dato se pide con un buscador de
   * personas, no esta pantalla el que lo reconoce.
   */
  protected readonly preguntasDeTitularidad = computed(() =>
    (this.definicion()?.comunes ?? []).filter((pregunta) => pregunta.control === 'TITULARES'),
  );

  protected readonly preguntasDeUbicacion = computed(() =>
    (this.definicion()?.comunes ?? []).filter((pregunta) => pregunta.control !== 'TITULARES'),
  );

  protected readonly preguntasDelTipo = computed(() => this.definicion()?.delTipo ?? []);

  protected readonly bloquesEconomicos = computed(() => this.definicion()?.deLaOperacion ?? []);

  /** Lo que impide confirmar, dicho por BROX Core y no deducido aquí. */
  protected readonly faltante = computed(() => this.borrador()?.faltante ?? []);

  protected readonly listoParaConfirmar = computed(
    () => !!this.borrador()?.listoParaEjecutar && this.revisando()
      && !!this.revisionActuacion() && !this.enVerificacion(),
  );

  protected readonly propietariosVisibles = computed(() => {
    const texto = normalizar(this.busquedaPropietario());
    const elegidos = new Set(this.titulares().map((titular) => titular.id));
    const disponibles = this.propietarios().filter((propietario) => !elegidos.has(propietario.id));
    if (!texto) {
      return disponibles;
    }
    return disponibles.filter((propietario) =>
      normalizar(`${propietario.nombre} ${propietario.numeroDocumento}`).includes(texto),
    );
  });

  protected readonly hayMasPropietarios = computed(
    () => this.propietarios().length < this.totalPropietarios(),
  );

  /** La suma de cuotas declaradas. El reparto tiene que dar 100. */
  protected readonly cuotaRepartida = computed(() =>
    this.titulares().reduce((suma, titular) => suma + (titular.cuota ?? 0), 0),
  );

  /**
   * Un titular solo no declara cuota: es el 100 %. Con dos o más, el reparto
   * tiene que estar completo, y lo dice aquí antes de que lo diga el trigger
   * diferido de la base con un mensaje de PostgreSQL.
   */
  protected readonly avisoDeCuotas = computed(() => {
    const elegidos = this.titulares();
    if (elegidos.length < 2) {
      return null;
    }
    const declaradas = elegidos.filter((titular) => titular.cuota != null).length;
    if (declaradas !== elegidos.length) {
      return 'Con más de un titular hay que declarar la cuota de cada uno.';
    }
    const suma = this.cuotaRepartida();
    return suma === 100 ? null : `Las cuotas suman ${suma} %. Tienen que sumar 100 %.`;
  });

  /** Lo anotado, para el resumen. Se lee del borrador: es lo que BROX guardó. */
  protected readonly resumen = computed(() => {
    const propuesta = this.revisionActuacion()?.propuesta;
    const anotado = propuesta?.contenido.datos ?? this.borrador()?.conocido ?? {};
    const definicion = propuesta?.contenido.definicionCore ?? this.definicion();
    const rotulos = new Map<string, string>();
    for (const pregunta of this.apertura()) {
      rotulos.set(pregunta.clave, pregunta.rotulo);
    }
    for (const pregunta of definicion?.comunes ?? []) {
      rotulos.set(pregunta.clave, pregunta.rotulo);
    }
    for (const pregunta of definicion?.delTipo ?? []) {
      rotulos.set(pregunta.clave, pregunta.rotulo);
    }
    // En el resumen, las de un encargo llevan su bloque delante. Fuera de su
    // sección, «Moneda» dos veces seguidas no dice cuál es la de la venta y
    // cuál la del alquiler — y son dos condiciones distintas.
    for (const bloque of definicion?.deLaOperacion ?? []) {
      for (const pregunta of bloque.preguntas) {
        rotulos.set(pregunta.clave, `${bloque.rotulo} · ${pregunta.rotulo}`);
      }
    }
    return Object.entries(anotado)
      .filter(([, valor]) => valor != null && `${valor}`.length > 0)
      .map(([clave, valor]) => ({
        clave,
        rotulo: rotulos.get(clave) ?? clave,
        valor: this.textoDelResumen(clave, `${valor}`),
      }));
  });

  // ------------------------------------------------------------------
  // Arranque
  // ------------------------------------------------------------------

  async ngOnInit(): Promise<void> {
    await this.reintentar();
  }

  protected async reintentar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      await this.captura.cargarContextoReferencias();
      this.referenciasPendientes.set(this.captura.referenciasPendientes());
      const [preguntas, existentes] = await Promise.all([
        this.captura.apertura(),
        this.listarCapturasEnCurso(),
      ]);
      this.apertura.set(preguntas);
      this.capturasEnCurso.set(existentes);
      if (existentes.length === 0) {
        await this.iniciarNuevoBorrador();
      } else {
        this.borrador.set(null);
        this.definicion.set(null);
        this.valores.set({});
        this.titulares.set([]);
      }
      if (this.borrador()) {
        await this.cargarPropietarios(true);
      }
    } catch (error) {
      this.errorCarga.set(mensajeDe(error, 'No se pudo abrir el registro de la propiedad.'));
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * La version del servicio que existia antes de que el contrato publicara
   * `GET /captura` no tenia este metodo. El fallback mantiene testeables los
   * consumidores antiguos, pero el servicio real siempre lista antes de abrir
   * otro borrador para no esconder un avance que ya se puede retomar.
   */
  private async listarCapturasEnCurso(): Promise<EstadoCaptura[]> {
    const enCurso = this.captura.enCurso;
    return typeof enCurso === 'function' ? await enCurso.call(this.captura) : [];
  }

  protected async iniciarNuevo(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      await this.iniciarNuevoBorrador();
      await this.cargarPropietarios(true);
    } catch (error) {
      this.errorCarga.set(mensajeDe(error, 'No se pudo abrir el registro de la propiedad.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private async iniciarNuevoBorrador(): Promise<void> {
    const estado = await this.captura.abrir();
    this.capturasEnCurso.set([]);
    this.aplicarEstado(estado);
    this.definicion.set(null);
    this.claveIdempotencia = crearClave();
  }

  protected async retomar(entrada: EstadoCaptura): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const estado = await this.captura.consultar(entrada.idBorrador);
      this.aplicarEstado(estado);
      this.capturasEnCurso.set([]);
      await this.cargarPropietarios(true);
      await this.rehacerElPlan();
      const referencia = this.captura.referenciaPendiente(estado.idBorrador);
      if (referencia) {
        this.resultadoActuacion.set({ referencia, estado: 'EN_VERIFICACION', propuesta: null,
          resultado: null, motivo: null });
        this.revisando.set(true);
        await this.verificarActuacion();
      }
    } catch (error) {
      this.errorCarga.set(mensajeDe(error, 'No se pudo retomar el borrador.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private aplicarEstado(estado: EstadoCaptura): void {
    const conocido = Object.fromEntries(
      Object.entries(estado.conocido ?? {}).map(([clave, valor]) => [clave, textoDe(valor)]),
    );
    this.borrador.set(estado);
    this.valores.set(conocido);
    this.titularesDesde(conocido[TITULARES]);
    this.revisando.set(false);
  }

  // ------------------------------------------------------------------
  // Responder
  // ------------------------------------------------------------------

  protected valorDe(clave: string): string {
    return this.valores()[clave] ?? '';
  }

  /**
   * Anota una respuesta y, si cambió una de las dos que deciden el plan, vuelve
   * a pedir la definición.
   *
   * El campo lo dibuja `cl-campo-gobernado` y ya entrega el valor como texto
   * —`'true'`, `'VENTA,ALQUILER'`, la fecha ISO—; aquí sólo se anota.
   *
   * Al cambiar el tipo, **lo que ya no aplica se descarta**: dejarlo oculto con
   * su valor guardaría el rubro de un terreno.
   */
  protected anotar(clave: string, valor: string): void {
    this.valores.update((actuales) => ({ ...actuales, [clave]: valor }));
    this.errorGuardado.set(null);
    this.revisando.set(false);
    if (clave === PropiedadForm.CLAVE_TIPO || clave === PropiedadForm.CLAVE_OPERACIONES) {
      void this.rehacerElPlan();
    }
  }

  private async rehacerElPlan(): Promise<void> {
    const tipo = this.tipoElegido();
    const operaciones = this.operacionesElegidas();
    const secuencia = ++this.secuenciaDefinicion;
    if (!tipo) {
      this.definicion.set(null);
      this.cargandoDefinicion.set(false);
      return;
    }
    this.cargandoDefinicion.set(true);
    this.errorCarga.set(null);
    try {
      const definicion = await this.captura.definicion(tipo, operaciones);
      if (secuencia !== this.secuenciaDefinicion) {
        return;
      }
      this.definicion.set(definicion);
      this.descartarLoQueYaNoAplica(definicion);
    } catch (error) {
      if (secuencia !== this.secuenciaDefinicion) {
        return;
      }
      this.definicion.set(null);
      this.errorCarga.set(
        mensajeDe(error, 'No se pudo saber qué hay que preguntar para esta propiedad.'),
      );
    } finally {
      if (secuencia === this.secuenciaDefinicion) {
        this.cargandoDefinicion.set(false);
      }
    }
  }

  /** Se quedan las respuestas que el plan nuevo sigue admitiendo; el resto se va. */
  private descartarLoQueYaNoAplica(definicion: DefinicionCaptura): void {
    const preguntas = preguntasDe(definicion);
    const admitidas = new Set<string>([
      PropiedadForm.CLAVE_TIPO,
      PropiedadForm.CLAVE_OPERACIONES,
      ...preguntas.map((pregunta) => pregunta.clave),
    ]);
    for (const pregunta of preguntas) {
      if (pregunta.control === 'IMPORTE') {
        const claveMoneda = claveMonedaDe(pregunta.clave);
        if (claveMoneda != null) {
          admitidas.add(claveMoneda);
        }
      }
    }
    this.valores.update((actuales) =>
      Object.fromEntries(Object.entries(actuales).filter(([clave]) => admitidas.has(clave))),
    );
  }

  // ------------------------------------------------------------------
  // Titulares
  // ------------------------------------------------------------------

  protected elegirTitular(propietario: Propietario): void {
    if (this.titulares().some((titular) => titular.id === propietario.id)) {
      return;
    }
    this.titulares.update((actuales) => [
      ...actuales,
      {
        id: propietario.id,
        nombre: propietario.nombre,
        documento: propietario.numeroDocumento,
        cuota: null,
        representante: actuales.length === 0,
      },
    ]);
    this.busquedaPropietario.set('');
    this.revisando.set(false);
  }

  protected quitarTitular(id: number): void {
    const quedan = this.titulares().filter((titular) => titular.id !== id);
    // Siempre tiene que haber un representante: si se fue el que lo era, lo
    // hereda el primero que queda. Sin esto, la copropiedad se queda sin
    // interlocutor y la base lo rechaza al final del alta.
    const siguientes =
      quedan.length > 0 && !quedan.some((titular) => titular.representante)
        ? quedan.map((titular, indice) =>
            indice === 0 ? { ...titular, representante: true } : titular,
          )
        : quedan;
    this.titulares.set(siguientes);
    if (siguientes.length === 0) {
      // Al retomar, `valores` contiene la relación que Core devolvió. Dejarla
      // intacta haría que `cargaUtil` la reenviase aunque la persona quitara
      // al último titular en pantalla.
      this.valores.update((actuales) => {
        const { [TITULARES]: _titulares, ...sinTitulares } = actuales;
        return sinTitulares;
      });
    }
    this.revisando.set(false);
  }

  protected cambiarCuota(id: number, evento: Event): void {
    const texto = (evento.target as HTMLInputElement).value;
    const cuota = texto.trim() === '' ? null : Number(texto);
    this.titulares.update((actuales) =>
      actuales.map((titular) => (titular.id === id ? { ...titular, cuota } : titular)),
    );
    this.revisando.set(false);
  }

  protected marcarRepresentante(id: number): void {
    this.titulares.update((actuales) =>
      actuales.map((titular) => ({ ...titular, representante: titular.id === id })),
    );
    this.revisando.set(false);
  }

  protected buscarPropietario(evento: Event): void {
    this.busquedaPropietario.set((evento.target as HTMLInputElement).value);
  }

  protected async cargarMasPropietarios(): Promise<void> {
    await this.cargarPropietarios(false);
  }

  private async cargarPropietarios(primera: boolean): Promise<void> {
    if (primera) {
      this.paginaPropietarios = 0;
    }
    this.cargandoPropietarios.set(true);
    try {
      const pagina = await this.propietariosApi.pagina(
        this.paginaPropietarios + 1,
        PROPIETARIOS_POR_PAGINA,
      );
      this.paginaPropietarios += 1;
      this.totalPropietarios.set(pagina.totalRecords);
      this.propietarios.update((actuales) =>
        primera ? pagina.items : [...actuales, ...pagina.items],
      );
      if (TITULARES in this.valores()) {
        this.titularesDesde(this.valores()[TITULARES]);
      }
    } catch (error) {
      this.errorPropietario.set(mensajeDe(error, 'No se pudieron cargar los propietarios.'));
    } finally {
      this.cargandoPropietarios.set(false);
    }
  }

  protected abrirAltaDePropietario(): void {
    this.altaPropietarioAbierta.set(true);
    this.errorPropietario.set(null);
  }

  protected cerrarAltaDePropietario(): void {
    this.altaPropietarioAbierta.set(false);
  }

  /**
   * Buscar antes de registrar: el alta en contexto existe para el caso en que
   * la persona **no** esté, no como atajo para no buscar. Es lo que evita el
   * duplicado de personas, que es el error más caro de un CRM inmobiliario.
   */
  protected async registrarPropietario(formulario: HTMLFormElement): Promise<void> {
    const datos = new FormData(formulario);
    const nuevo: DatosPropietario = {
      tipoPersona: `${datos.get('tipoPersona') ?? 'N'}`,
      tipoDocumento: `${datos.get('tipoDocumento') ?? 'D'}`,
      numeroDocumento: `${datos.get('numeroDocumento') ?? ''}`.trim(),
      nombre: `${datos.get('nombre') ?? ''}`.trim(),
      telefono: `${datos.get('telefono') ?? ''}`.trim() || undefined,
      correo: `${datos.get('correo') ?? ''}`.trim() || undefined,
    };
    this.errorPropietario.set(null);
    try {
      const creado = await this.propietariosApi.registrar(nuevo);
      this.propietarios.update((actuales) => [creado, ...actuales]);
      this.totalPropietarios.update((total) => total + 1);
      this.elegirTitular(creado);
      this.altaPropietarioAbierta.set(false);
      formulario.reset();
    } catch (error) {
      this.errorPropietario.set(mensajeDe(error, 'No se pudo registrar el propietario.'));
    }
  }

  // ------------------------------------------------------------------
  // Revisar y confirmar
  // ------------------------------------------------------------------

  /**
   * Manda todo lo respondido al motor y pide el resumen.
   *
   * Lo que ya no está entre las respuestas viaja **en blanco** para que el
   * borrador lo olvide: cambiar de tipo tiene que borrar de verdad el rubro que
   * se había contestado, no dejarlo escondido.
   */
  protected async revisar(): Promise<void> {
    const estado = this.borrador();
    if (!estado || this.guardando() || this.enVerificacion()) {
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    this.revisionActuacion.set(null);
    this.resultadoActuacion.set(null);
    try {
      const actualizado = await this.captura.avanzar(estado.idBorrador, this.cargaUtil(estado));
      this.aplicarEstado(actualizado);
      if (actualizado.listoParaEjecutar) {
        const revision = await this.captura.prepararRevision(actualizado.idBorrador);
        if (!this.mismaFoto(revision, actualizado)) {
          throw new ApiError(409, 'El registro cambió. Revisa de nuevo antes de confirmar.');
        }
        this.revisionActuacion.set(revision);
      }
      this.revisando.set(true);
    } catch (error) {
      this.errorGuardado.set(mensajeDe(error, 'No se pudo anotar lo respondido.'));
    } finally {
      this.guardando.set(false);
    }
  }

  private cargaUtil(estado: EstadoCaptura): Record<string, string> {
    const respuestas: Record<string, string> = { ...this.valores() };
    const elegidos = this.titulares();
    if (elegidos.length > 0) {
      respuestas[TITULARES] = elegidos
        .map((titular) => (titular.cuota == null ? `${titular.id}` : `${titular.id}:${titular.cuota}`))
        .join(',');
    }
    // Lo que BROX tiene anotado y ya no está entre las respuestas se vacía.
    for (const clave of Object.keys(estado.conocido)) {
      if (!(clave in respuestas)) {
        respuestas[clave] = '';
      }
    }
    return respuestas;
  }

  protected volverAEditar(): void {
    if (this.enVerificacion()) return;
    this.revisionActuacion.set(null);
    this.revisando.set(false);
  }

  /** Aquí, y sólo aquí, se escribe la propiedad. */
  protected async confirmar(): Promise<void> {
    const estado = this.borrador();
    const revision = this.revisionActuacion();
    if (!estado || !revision || !this.listoParaConfirmar() || this.guardando()
        || !this.mismaFoto(revision, estado)) {
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const resultado = await this.captura.confirmarRevision(revision, this.claveIdempotencia);
      await this.recibirResultado(resultado);
    } catch (error) {
      this.revisionActuacion.set(null);
      this.revisando.set(false);
      this.errorGuardado.set(mensajeDe(error, 'No se pudo registrar la propiedad.'));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Tras una respuesta perdida sólo se consulta la misma referencia; nunca se repite el POST. */
  protected async verificarActuacion(): Promise<void> {
    const operacion = this.resultadoActuacion();
    if (!operacion) return;
    this.guardando.set(true);
    try {
      await this.recibirResultado(await this.captura.consultarActuacion(operacion.referencia));
    } catch {
      this.errorGuardado.set('El resultado sigue en verificación. Puedes consultarlo nuevamente.');
    } finally {
      this.guardando.set(false);
    }
  }

  /** Entrada explícita a recuperación; no consulta todos los registros ni recrea la captura. */
  protected async consultarReferencia(entrada: ReferenciaCapturaPendiente): Promise<void> {
    this.guardando.set(true);
    try {
      const resultado = await this.captura.consultarActuacion(entrada.referencia);
      if (resultado.estado === 'EXITO' && resultado.resultado) {
        this.captura.olvidarReferencia(entrada.idBorrador);
        this.referenciasPendientes.set(this.captura.referenciasPendientes());
        await this.router.navigate(['/propiedades', resultado.resultado.idPropiedad]);
      } else if (resultado.estado === 'RECHAZADO') {
        this.captura.olvidarReferencia(entrada.idBorrador);
        this.referenciasPendientes.set(this.captura.referenciasPendientes());
        this.errorGuardado.set('La actuación fue rechazada. Puedes retomar su registro para revisarlo.');
      } else if (resultado.estado === 'PREPARADA') {
        this.captura.olvidarReferencia(entrada.idBorrador);
        this.referenciasPendientes.set(this.captura.referenciasPendientes());
        this.errorGuardado.set('La propuesta está preparada y no tiene confirmación registrada. Retoma el borrador para revisarlo.');
      } else {
        this.errorGuardado.set('Ese registro continúa en verificación. Puedes consultarlo nuevamente.');
      }
    } catch (error) {
      this.errorGuardado.set(mensajeDe(error, 'No se pudo verificar el resultado.'));
    } finally { this.guardando.set(false); }
  }

  private async recibirResultado(resultado: ResultadoActuacionCaptura): Promise<void> {
    this.resultadoActuacion.set(resultado);
    if (resultado.estado !== 'EN_VERIFICACION') {
      const idBorrador = resultado.resultado?.idBorrador ?? resultado.propuesta?.contenido.idBorrador;
      if (idBorrador != null) this.captura.olvidarReferencia(idBorrador);
    }
    this.referenciasPendientes.set(this.captura.referenciasPendientes());
    if (resultado.estado === 'EXITO' && resultado.resultado) {
      await this.router.navigate(['/propiedades', resultado.resultado.idPropiedad]);
    } else if (resultado.estado === 'RECHAZADO') {
      this.revisionActuacion.set(null);
      this.revisando.set(false);
      this.errorGuardado.set('La actuación fue rechazada. Revisa los datos antes de prepararla nuevamente.');
    } else if (resultado.estado === 'PREPARADA') {
      this.revisionActuacion.set(null);
      this.revisando.set(false);
      this.errorGuardado.set('La propuesta está preparada y no tiene confirmación registrada. Revisa el borrador antes de continuar.');
    } else {
      this.revisando.set(true);
      this.errorGuardado.set('El resultado está en verificación. No vuelvas a registrar la propiedad.');
    }
  }

  private mismaFoto(revision: RevisionCaptura, estado: EstadoCaptura): boolean {
    return revision.propuesta.contenido.idBorrador === estado.idBorrador &&
      canonico(revision.propuesta.contenido.datos) === canonico(estado.conocido);
  }

  protected async cancelar(): Promise<void> {
    // Volver no es descartar. El borrador queda en curso para que la
    // organización lo vea en `GET /captura` y se pueda retomar más tarde.
    await this.router.navigate(['/propiedades']);
  }

  /** Descartar es explícito y conserva el hecho histórico en Core. */
  protected async descartarBorrador(): Promise<void> {
    if (this.enVerificacion()) return;
    const estado = this.borrador();
    if (!estado || estado.estado !== 'E') {
      await this.router.navigate(['/propiedades']);
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      await this.captura.descartar(estado.idBorrador);
      await this.router.navigate(['/propiedades']);
    } catch (error) {
      this.errorGuardado.set(mensajeDe(error, 'No se pudo descartar el borrador.'));
    } finally {
      this.guardando.set(false);
    }
  }

  // ------------------------------------------------------------------

  /** Los titulares se resumen por su nombre, no por la lista de ids del cable. */
  private textoDelResumen(clave: string, valor: string): string {
    if (clave !== TITULARES) {
      return valor;
    }
    return this.titulares()
      .map((titular) =>
        titular.cuota == null
          ? titular.nombre
          : `${titular.nombre} · ${titular.cuota} %${titular.representante ? ' · representante' : ''}`,
      )
      .join(', ');
  }

  protected rotuloDelBloque(bloque: BloqueOperacion): string {
    return bloque.rotulo;
  }

  /** Valor de moneda que acompaña a un importe, calificado por operación o atributo. */
  protected monedaDe(pregunta: PreguntaCaptura): string {
    const clave = pregunta.control === 'IMPORTE' ? claveMonedaDe(pregunta.clave) : null;
    return clave == null ? '' : this.valores()[clave] ?? '';
  }

  protected anotarMoneda(pregunta: PreguntaCaptura, moneda: string): void {
    const clave = claveMonedaDe(pregunta.clave);
    if (clave != null) {
      this.anotar(clave, moneda);
    }
  }

  private titularesDesde(valor: string | undefined): void {
    if (!valor) {
      this.titulares.set([]);
      return;
    }
    const conocidos = new Map(this.propietarios().map((propietario) => [propietario.id, propietario]));
    const titulares: TitularElegido[] = [];
    for (const parte of valor.split(',')) {
      const [idTexto, cuotaTexto] = parte.trim().split(':', 2);
      const id = Number(idTexto);
      if (!Number.isInteger(id) || id <= 0) {
        continue;
      }
      const propietario = conocidos.get(id);
      const cuota = cuotaTexto == null || cuotaTexto.trim() === '' ? null : Number(cuotaTexto);
      titulares.push({
        id,
        nombre: propietario?.nombre ?? `Titular #${id}`,
        documento: propietario?.numeroDocumento ?? '',
        cuota: Number.isFinite(cuota) ? cuota : null,
        representante: titulares.length === 0,
      });
    }
    this.titulares.set(titulares);
  }
}

/** La clave del titular en el contrato del motor. */
const TITULARES = 'titulares';

function crearClave(): string {
  const cripto = globalThis.crypto;
  if (typeof cripto.randomUUID === 'function') return cripto.randomUUID();
  const bytes = new Uint8Array(16);
  cripto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Compara JSON semántico sin convertir números, monedas o desconocidos. */
function canonico(valor: unknown): string {
  const ordenar = (dato: unknown): unknown => {
    if (Array.isArray(dato)) return dato.map(ordenar);
    if (dato && typeof dato === 'object') {
      return Object.fromEntries(Object.entries(dato).sort(([a], [b]) => a.localeCompare(b))
        .map(([clave, contenido]) => [clave, ordenar(contenido)]));
    }
    return dato;
  };
  return JSON.stringify(ordenar(valor)) ?? '';
}

function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

function textoDe(valor: unknown): string {
  if (Array.isArray(valor)) {
    return valor.join(',');
  }
  return valor == null ? '' : `${valor}`;
}

function claveMonedaDe(clave: string): string | null {
  if (!clave) {
    return null;
  }
  const separador = clave.indexOf(':');
  if (separador >= 0) {
    if (clave.slice(0, separador) !== 'importe' || separador === clave.length - 1) {
      return null;
    }
    return `moneda:${clave.slice(separador + 1)}`;
  }
  return `moneda:${clave}`;
}

function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : porDefecto;
}
