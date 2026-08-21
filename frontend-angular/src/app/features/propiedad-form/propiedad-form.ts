import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { LowerCasePipe, NgTemplateOutlet } from '@angular/common';
import { Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  BloqueOperacion,
  CapturaService,
  DefinicionCaptura,
  EstadoCaptura,
  PreguntaCaptura,
  preguntasDe,
} from '../../core/api/captura.service';
import {
  DatosPropietario,
  Propietario,
  PropietariosService,
} from '../../core/api/propietarios.service';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

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
 * ## Nada se escribe hasta confirmar
 *
 * Abrir la pantalla abre un *borrador*, que anota lo que se va sabiendo y no
 * crea nada. Si se abandona, no queda una propiedad a medias. La confirmación
 * lleva `Idempotency-Key`: un doble clic devuelve la propiedad del primer
 * intento en vez de crear una segunda.
 */
@Component({
  selector: 'app-propiedad-form',
  imports: [EstadoListado, LowerCasePipe, NgTemplateOutlet],
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
  protected readonly valores = signal<Record<string, string>>({});
  protected readonly titulares = signal<readonly TitularElegido[]>([]);
  protected readonly revisando = signal(false);

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

  /** Hasta que el tipo y la operación estén, no hay plan que pintar. */
  protected readonly planListo = computed(
    () => !!this.tipoElegido() && !!this.operacionesElegidas() && !!this.definicion(),
  );

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
    () => this.borrador()?.listoParaEjecutar ?? false,
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
    const anotado = this.borrador()?.conocido ?? {};
    const definicion = this.definicion();
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
      const [preguntas, estado] = await Promise.all([
        this.captura.apertura(),
        this.captura.abrir(),
      ]);
      this.apertura.set(preguntas);
      this.borrador.set(estado);
      await this.cargarPropietarios(true);
    } catch (error) {
      this.errorCarga.set(mensajeDe(error, 'No se pudo abrir el registro de la propiedad.'));
    } finally {
      this.cargando.set(false);
    }
  }

  // ------------------------------------------------------------------
  // Responder
  // ------------------------------------------------------------------

  /** Un campo de texto, número o selector. */
  protected responder(clave: string, evento: Event): void {
    const destino = evento.target as HTMLInputElement | HTMLSelectElement;
    this.anotar(clave, destino.value ?? '');
  }

  protected responderInterruptor(clave: string, evento: Event): void {
    const destino = evento.target as HTMLInputElement;
    this.anotar(clave, destino.checked ? 'true' : 'false');
  }

  /**
   * Una opción de un selector múltiple. El valor viaja como lista separada por
   * comas —`VENTA,ALQUILER`— porque cada elemento es una operación de verdad;
   * lo que nunca viaja es un valor combinado.
   */
  protected alternarOpcion(clave: string, opcion: string, evento: Event): void {
    const marcada = (evento.target as HTMLInputElement).checked;
    const actuales = (this.valores()[clave] ?? '').split(',').filter((parte) => parte.length > 0);
    const siguientes = marcada
      ? [...actuales.filter((valor) => valor !== opcion), opcion]
      : actuales.filter((valor) => valor !== opcion);
    this.anotar(clave, siguientes.join(','));
  }

  protected estaMarcada(clave: string, opcion: string): boolean {
    return (this.valores()[clave] ?? '').split(',').includes(opcion);
  }

  protected valorDe(clave: string): string {
    return this.valores()[clave] ?? '';
  }

  protected estaEncendido(clave: string): boolean {
    return this.valores()[clave] === 'true';
  }

  /**
   * Anota una respuesta y, si cambió una de las dos que deciden el plan, vuelve
   * a pedir la definición.
   *
   * Al cambiar el tipo, **lo que ya no aplica se descarta**: dejarlo oculto con
   * su valor guardaría el rubro de un terreno.
   */
  private anotar(clave: string, valor: string): void {
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
    if (!tipo || !operaciones) {
      this.definicion.set(null);
      return;
    }
    this.cargandoDefinicion.set(true);
    this.errorCarga.set(null);
    try {
      const definicion = await this.captura.definicion(tipo, operaciones);
      this.definicion.set(definicion);
      this.descartarLoQueYaNoAplica(definicion);
    } catch (error) {
      this.definicion.set(null);
      this.errorCarga.set(
        mensajeDe(error, 'No se pudo saber qué hay que preguntar para esta propiedad.'),
      );
    } finally {
      this.cargandoDefinicion.set(false);
    }
  }

  /** Se quedan las respuestas que el plan nuevo sigue admitiendo; el resto se va. */
  private descartarLoQueYaNoAplica(definicion: DefinicionCaptura): void {
    const admitidas = new Set<string>([
      PropiedadForm.CLAVE_TIPO,
      PropiedadForm.CLAVE_OPERACIONES,
      ...preguntasDe(definicion).map((pregunta) => pregunta.clave),
    ]);
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
    this.titulares.update((actuales) => {
      const quedan = actuales.filter((titular) => titular.id !== id);
      // Siempre tiene que haber un representante: si se fue el que lo era, lo
      // hereda el primero que queda. Sin esto, la copropiedad se queda sin
      // interlocutor y la base lo rechaza al final del alta.
      if (quedan.length > 0 && !quedan.some((titular) => titular.representante)) {
        return quedan.map((titular, indice) =>
          indice === 0 ? { ...titular, representante: true } : titular,
        );
      }
      return quedan;
    });
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
    if (!estado) {
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const actualizado = await this.captura.avanzar(estado.idBorrador, this.cargaUtil(estado));
      this.borrador.set(actualizado);
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
    this.revisando.set(false);
  }

  /** Aquí, y sólo aquí, se escribe la propiedad. */
  protected async confirmar(): Promise<void> {
    const estado = this.borrador();
    if (!estado || !estado.listoParaEjecutar) {
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const creada = await this.captura.ejecutar(estado.idBorrador, this.claveIdempotencia);
      await this.router.navigate(['/propiedades', creada.idPropiedad]);
    } catch (error) {
      this.errorGuardado.set(mensajeDe(error, 'No se pudo registrar la propiedad.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected async cancelar(): Promise<void> {
    const estado = this.borrador();
    if (estado && estado.estado === 'E') {
      try {
        await this.captura.descartar(estado.idBorrador);
      } catch {
        // Que el borrador quede abierto no impide salir: no hay nada escrito
        // del negocio y la organización lo verá en sus capturas en curso.
      }
    }
    await this.router.navigate(['/propiedades']);
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
}

/** La clave del titular en el contrato del motor. */
const TITULARES = 'titulares';

function crearClave(): string {
  const cripto = globalThis.crypto;
  return cripto && 'randomUUID' in cripto
    ? cripto.randomUUID()
    : `alta-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : porDefecto;
}
