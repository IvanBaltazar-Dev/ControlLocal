import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  BloqueOperacion,
  CapturaService,
  DefinicionCaptura,
  PreguntaCaptura,
} from '../../core/api/captura.service';
import {
  AtributoEnEdicion,
  CAMPOS_DE_UBICACION,
  CAMPOS_ECONOMICOS_DEL_ENCARGO,
  CondicionesDeEncargoEnEdicion,
  EdicionPropiedad,
  EncargoPropiedad,
  FichaPropiedad,
  OperacionEnEdicion,
  motivoDeBloqueo,
  puedeEscribir,
  PropiedadesService,
  UbicacionEnEdicion,
} from '../../core/api/propiedades.service';
import { Propietario, PropietariosService } from '../../core/api/propietarios.service';
import { texto } from '../../core/formato';
import { CampoGobernado } from '../../shared/campo-gobernado/campo-gobernado';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const PROPIETARIOS_POR_PAGINA = 50;

/** Una pregunta de la propiedad, y si el cable de edición sabe transportarla. */
interface CampoDeLaPropiedad {
  readonly pregunta: PreguntaCaptura;
  readonly editable: boolean;
  /** Cuando no se edita, por qué. El hecho; el tono es de esta pantalla. */
  readonly motivo: string | null;
}

/** Un titular tal como se edita: quién, cuánto y si es con quien se habla. */
interface TitularEditado {
  readonly id: number;
  readonly nombre: string;
  readonly documento: string;
  cuota: number | null;
  representante: boolean;
}

/**
 * Un encargo vivo con lo que se le puede cambiar, ya separado en los dos
 * huecos del cable: lo económico va en `operaciones[]`, lo pactado en
 * `condiciones[]` por `idEncargo`.
 */
interface BloqueDeEncargo {
  readonly encargo: EncargoPropiedad;
  readonly economicos: readonly PreguntaCaptura[];
  /** Con la clave **sin calificar**, que es como la nombra el encargo y el PUT. */
  readonly pactables: readonly PreguntaCaptura[];
}

/**
 * Lo que se escribió en un campo. `valor` es el hueco principal; `moneda` y
 * `valores` son los otros dos huecos que el cable transporta, y sólo aparecen
 * cuando el control los tiene. Se guardan **separados** porque componer
 * «PEN 350» y volver a partirlo es exactamente lo que no se puede hacer sin
 * inferir (V77).
 */
interface Escrito {
  readonly valor?: string;
  readonly moneda?: string;
  readonly valores?: readonly string[];
}

type Tocado = Readonly<Record<string, Escrito>>;
type TocadoPorEncargo = Readonly<Record<number, Tocado>>;
type RetiradoPorEncargo = Readonly<Record<number, ReadonlySet<string>>>;

/**
 * **El editor universal. Uno, para los siete tipos, y sólo manda lo que cambió.**
 *
 * ## Qué es, y qué no
 *
 * Es la puerta para corregir **todo lo que BROX ya sabe** de una propiedad sin
 * perder ni inventar nada. No es el alta —eso es `propiedad-form`, que va por
 * el motor de captura— y no es un formulario por tipo: pregunta al Core qué
 * características tiene este tipo (`GET /captura/definicion`) y pinta lo que
 * recibe con el mismo renderizador que el alta. Aquí no hay ninguna matriz
 * «tipo → campos», y hay un gate que rompe el build si aparece.
 *
 * ## La regla del cuerpo
 *
 * `PUT /propiedades/{id}` es una edición parcial: **lo que no llega no se
 * toca**. Esta pantalla lo aprovecha entero. No construye un comando completo
 * cada vez: lleva la cuenta de lo que la persona tocó, bloque a bloque, y
 * `cambios()` produce un cuerpo que contiene eso y nada más.
 *
 * ```
 *   toca la ubicación      →  ubicacion
 *   toca características   →  atributos + atributosABorrar
 *   toca ENC-0016          →  operaciones con ENC-0016 solamente
 *   toca los titulares     →  titulares
 * ```
 *
 * Y tres cosas que **no** se confunden, porque confundirlas es como el editor
 * anterior inventaba `rubro_permitido` y convertía `uso` en `'C'`:
 *
 * ```
 *   no sé el valor     ≠  inventar un defecto   → un selector sin elegir es ''
 *   no toqué el valor  ≠  mandarlo vacío        → un campo vaciado NO viaja
 *   quiero eliminarlo  =  intención explícita    → «Quitar», y viaja en atributosABorrar
 * ```
 *
 * ## Un bloque por ENCARGO, no por operación
 *
 * `ENC-0016 · Venta` y `ENC-0032 · Alquiler` son dos bloques, y lo serían
 * aunque mañana hubiera otro de venta: el histórico pertenece al encargo. Y
 * un encargo **no cambia de operación** desde aquí —eso reescribiría su
 * historia—: el cambio de intención se expresa cerrando uno y abriendo otro.
 */
@Component({
  selector: 'app-propiedad-editor',
  imports: [CampoGobernado, EstadoListado, LowerCasePipe, RouterLink],
  templateUrl: './propiedad-editor.html',
  styleUrl: './propiedad-editor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropiedadEditor implements OnInit {
  private readonly api = inject(PropiedadesService);
  private readonly captura = inject(CapturaService);
  private readonly propietariosApi = inject(PropietariosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private static readonly CLAVE_DESCRIPCION = 'descripcion';

  /**
   * La misma en cada reintento del MISMO guardado. Se renueva en cuanto lo
   * tocado cambia: un cambio de importe añade un hito, y un reintento del
   * mismo cuerpo no debe añadir dos.
   */
  private claveIdempotencia: string | null = null;
  private paginaPropietarios = 0;

  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);

  protected readonly ficha = signal<FichaPropiedad | null>(null);
  protected readonly definicion = signal<DefinicionCaptura | null>(null);

  // ------------------------------------------------------------------
  // Lo tocado. Cada estructura guarda SOLO lo que la persona cambió.
  // ------------------------------------------------------------------

  /** Descripción, ubicación y características: clave lógica → valor nuevo. */
  protected readonly fisico = signal<Tocado>({});
  /** Claves de la propiedad que se quieren retirar. Intención declarada. */
  protected readonly retirados = signal<ReadonlySet<string>>(new Set());

  protected readonly titulares = signal<readonly TitularEditado[]>([]);
  protected readonly titularesTocados = signal(false);

  /** Por encargo: importe, moneda, exclusividad, inicio, fin. */
  protected readonly economico = signal<TocadoPorEncargo>({});
  /** Por encargo: lo pactado (clave sin calificar → valor). */
  protected readonly pactado = signal<TocadoPorEncargo>({});
  protected readonly pactadoRetirado = signal<RetiradoPorEncargo>({});

  protected readonly propietarios = signal<readonly Propietario[]>([]);
  protected readonly totalPropietarios = signal(0);
  protected readonly busquedaPropietario = signal('');
  protected readonly cargandoPropietarios = signal(false);
  protected readonly errorPropietario = signal<string | null>(null);

  // ------------------------------------------------------------------
  // Los cuatro bloques, derivados del contrato
  // ------------------------------------------------------------------

  /**
   * Lo común a toda propiedad, menos los titulares, que tienen bloque propio.
   * Se separan por **control**, no por clave: es el contrato quien dice que
   * ese dato se pide con un buscador de personas.
   */
  protected readonly camposComunes = computed<readonly CampoDeLaPropiedad[]>(() =>
    (this.definicion()?.comunes ?? [])
      .filter((pregunta) => pregunta.control !== 'TITULARES')
      .map((pregunta) => this.comoCampoComun(pregunta)),
  );

  protected readonly camposDelTipo = computed<readonly CampoDeLaPropiedad[]>(() =>
    (this.definicion()?.delTipo ?? []).map((pregunta) => this.comoCaracteristica(pregunta)),
  );

  protected readonly encargosVivos = computed<readonly BloqueDeEncargo[]>(() =>
    (this.ficha()?.encargos ?? [])
      .filter((encargo) => encargo.vivo)
      .map((encargo) => this.bloqueDe(encargo)),
  );

  protected readonly encargosCerrados = computed(
    () => this.ficha()?.encargos.filter((encargo) => !encargo.vivo) ?? [],
  );

  protected readonly cambios = computed<EdicionPropiedad | null>(() => this.construir());
  protected readonly hayCambios = computed(() => this.cambios() !== null);

  // ------------------------------------------------------------------
  // La autoridad, tal como llega del cable (P0)
  // ------------------------------------------------------------------

  /**
   * Si **este** usuario puede escribir hechos de esta propiedad.
   *
   * Sale del backend ya resuelto. Aquí no se compara ningún rol ni ningún id:
   * hacerlo pondría una segunda copia de la regla de autoridad en la pantalla,
   * y dos copias divergen — hacia el lado de habilitar un botón que el PUT va
   * a rechazar cuando la persona ya escribió.
   *
   * Cuando el bloque **no llega** —Jackson va `NON_NULL`, así que es un caso
   * real del cable— vale `false`. Valía `true`, y la ficha de al lado valía
   * `false` ante exactamente la misma respuesta: dos pantallas decidiendo lo
   * contrario sobre el mismo silencio. Ahora lo decide `puedeEscribir`, una
   * sola vez, para las tres.
   */
  protected readonly puedeEditar = computed(() =>
    puedeEscribir(this.ficha()?.responsabilidad),
  );

  /** El motivo del bloqueo **escrito por el Core**. La pantalla no redacta. */
  protected readonly motivoBloqueo = computed(() =>
    motivoDeBloqueo(this.ficha()?.responsabilidad),
  );

  /** Quién responde hoy, o `null` si está FALTANTE. */
  protected readonly responsable = computed(
    () => this.ficha()?.responsabilidad?.nombre ?? null,
  );

  /** Los bloques tocados, con nombre, para que se vea qué va a viajar. */
  protected readonly resumenDeCambios = computed<readonly string[]>(() => {
    const cuerpo = this.cambios();
    if (!cuerpo) {
      return [];
    }
    const lineas: string[] = [];
    if (cuerpo.descripcion !== undefined || cuerpo.ubicacion) {
      lineas.push('Propiedad y ubicación');
    }
    if (cuerpo.atributos || cuerpo.atributosABorrar) {
      lineas.push('Características');
    }
    if (cuerpo.titulares) {
      lineas.push('Titulares');
    }
    for (const bloque of this.encargosVivos()) {
      const economico = cuerpo.operaciones?.some(
        (operacion) => operacion.operacion === bloque.encargo.operacion,
      );
      const pactado = cuerpo.condiciones?.some(
        (condicion) => condicion.idEncargo === bloque.encargo.idEncargo,
      );
      if (economico || pactado) {
        lineas.push(`${bloque.encargo.codigo} · ${bloque.encargo.operacionRotulo}`);
      }
    }
    return lineas;
  });

  protected readonly propietariosVisibles = computed(() => {
    const buscado = normalizar(this.busquedaPropietario());
    const elegidos = new Set(this.titulares().map((titular) => titular.id));
    const disponibles = this.propietarios().filter((propietario) => !elegidos.has(propietario.id));
    if (!buscado) {
      return disponibles;
    }
    return disponibles.filter((propietario) =>
      normalizar(`${propietario.nombre} ${propietario.numeroDocumento}`).includes(buscado),
    );
  });

  protected readonly hayMasPropietarios = computed(
    () => this.propietarios().length < this.totalPropietarios(),
  );

  /**
   * Un titular solo no declara cuota: es el 100 %. Con dos o más, el reparto
   * tiene que estar completo, y se dice aquí antes de que lo diga la base.
   */
  protected readonly avisoDeCuotas = computed(() => {
    const elegidos = this.titulares();
    if (elegidos.length < 2) {
      return null;
    }
    if (elegidos.some((titular) => titular.cuota == null)) {
      return 'Con más de un titular hay que declarar la cuota de cada uno.';
    }
    const suma = elegidos.reduce((total, titular) => total + (titular.cuota ?? 0), 0);
    return suma === 100 ? null : `Las cuotas suman ${suma} %. Tienen que sumar 100 %.`;
  });

  ngOnInit(): void {
    void this.cargar();
  }

  // ------------------------------------------------------------------
  // Cargar: la ficha dice lo que hay; la definición, cómo se pregunta
  // ------------------------------------------------------------------

  private async cargar(): Promise<void> {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.errorCarga.set('El identificador de la propiedad no es válido.');
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const ficha = await this.api.consultar(id);
      // El plan de preguntas de ESTE tipo con las operaciones que tiene vivas:
      // el mismo que usó el alta. Sin encargos vivos no se piden bloques
      // económicos, porque no hay nada que editar ahí.
      const operaciones = ficha.encargos
        .filter((encargo) => encargo.vivo)
        .map((encargo) => encargo.operacion)
        .join(',');
      const definicion = await this.captura.definicion(ficha.tipoPropiedad, operaciones);
      this.ficha.set(ficha);
      this.definicion.set(definicion);
      this.titulares.set(
        ficha.titulares.map((titular) => ({
          id: titular.idPropietario,
          nombre: titular.nombre,
          documento: '',
          cuota: titular.cuota ?? null,
          representante: titular.representante,
        })),
      );
    } catch (error) {
      this.errorCarga.set(mensajeDe(error, 'No se pudo cargar la propiedad para editarla.'));
    } finally {
      this.cargando.set(false);
    }
  }

  // ------------------------------------------------------------------
  // Propiedad y características
  // ------------------------------------------------------------------

  /**
   * Lo que se ve en el campo: lo tocado si se tocó; si no, lo que la ficha
   * dice. Un campo retirado se ve vacío, y su botón dice «Conservar».
   */
  protected valorDe(clave: string): string {
    if (this.retirados().has(clave)) {
      return '';
    }
    const tocado = this.fisico()[clave]?.valor;
    return tocado !== undefined ? tocado : this.valorOriginalDe(clave);
  }

  /** La moneda de un IMPORTE: la tocada, o la que la ficha trae cruda. */
  protected monedaDe(clave: string): string {
    if (this.retirados().has(clave)) {
      return '';
    }
    const tocada = this.fisico()[clave]?.moneda;
    return tocada !== undefined ? tocada : this.atributoDe(clave)?.moneda ?? '';
  }

  /** Los elementos de un multivalor, crudos: nunca partidos de un texto. */
  protected valoresDe(clave: string): readonly string[] | null {
    if (this.retirados().has(clave)) {
      return [];
    }
    const tocados = this.fisico()[clave]?.valores;
    return tocados !== undefined ? tocados : this.atributoDe(clave)?.valores ?? null;
  }

  protected anotarMoneda(clave: string, moneda: string): void {
    this.escribir(clave, { moneda });
  }

  protected anotarValores(clave: string, valores: readonly string[]): void {
    this.escribir(clave, { valores });
  }

  protected tieneValorOriginal(clave: string): boolean {
    return this.valorOriginalDe(clave) !== '';
  }

  protected estaRetirado(clave: string): boolean {
    return this.retirados().has(clave);
  }

  /**
   * Anota lo escrito. **Un campo vaciado vuelve a «no tocado»**: no viaja ni
   * como valor ni como borrado. Retirar un valor es otra acción, con nombre.
   */
  protected anotar(clave: string, valor: string): void {
    this.escribir(clave, { valor });
  }

  /**
   * Anota uno de los huecos y deja los otros como estaban.
   *
   * **Un hueco vaciado vuelve a «no tocado»**, y si con eso el campo entero
   * queda sin nada escrito, desaparece de lo tocado: no viaja ni como valor ni
   * como borrado. Retirar es otra acción, con su botón.
   */
  private escribir(clave: string, cambio: Escrito): void {
    this.fisico.update((actuales) => {
      const siguientes = { ...actuales };
      const fundido: Escrito = { ...siguientes[clave], ...cambio };
      const vacio =
        (fundido.valor ?? '') === '' &&
        (fundido.moneda ?? '') === '' &&
        (fundido.valores?.length ?? 0) === 0;
      if (vacio) {
        delete siguientes[clave];
      } else {
        siguientes[clave] = fundido;
      }
      return siguientes;
    });
    this.retirados.update((actuales) => sin(actuales, clave));
    this.tocado();
  }

  private atributoDe(clave: string) {
    return this.ficha()?.atributos.find((candidato) => candidato.clave === clave);
  }

  /** «Quitar» y «Conservar»: la intención de retirar, declarada y reversible. */
  protected alternarRetiro(clave: string): void {
    const retirado = this.retirados().has(clave);
    this.retirados.update((actuales) => (retirado ? sin(actuales, clave) : con(actuales, clave)));
    if (!retirado) {
      this.fisico.update((actuales) => {
        const siguientes = { ...actuales };
        delete siguientes[clave];
        return siguientes;
      });
    }
    this.tocado();
  }

  protected esImporte(campo: CampoDeLaPropiedad): boolean {
    return campo.pregunta.control === 'IMPORTE';
  }

  private valorOriginalDe(clave: string): string {
    const ficha = this.ficha();
    if (!ficha) {
      return '';
    }
    if (clave === PropiedadEditor.CLAVE_DESCRIPCION) {
      return ficha.descripcion ?? '';
    }
    if (esCampoDeUbicacion(clave)) {
      const valor = ficha.ubicacion?.[clave];
      return valor == null ? '' : String(valor);
    }
    const atributo = this.atributoDe(clave);
    if (atributo) {
      // De un IMPORTE, la CIFRA sola: la moneda va por su hueco. `valor` trae
      // «PEN 350» compuesto para leer, y meterlo en el campo numerico lo
      // dejaria en blanco sin decir por que.
      if (atributo.moneda) {
        return (atributo.valor ?? '').replace(atributo.moneda, '').trim();
      }
      return atributo.valor ?? '';
    }
    // Lo que el cable no edita (el código, el uso) se enseña tal como la ficha
    // lo nombra, y con su rótulo si lo tiene: nunca una letra suelta.
    const plano = ficha as unknown as Record<string, unknown>;
    const legible = plano[`${clave}Rotulo`] ?? plano[clave];
    return legible == null ? '' : String(legible);
  }

  private comoCampoComun(pregunta: PreguntaCaptura): CampoDeLaPropiedad {
    const editable =
      pregunta.clave === PropiedadEditor.CLAVE_DESCRIPCION || esCampoDeUbicacion(pregunta.clave);
    return {
      pregunta,
      editable,
      motivo: editable ? null : 'No se cambia desde aquí.',
    };
  }

  /**
   * Toda característica del catálogo se edita. Hasta V77 el `IMPORTE` y el
   * `SELECTOR_MULTIPLE` se pintaban en sólo lectura porque `AtributoRequest`
   * llevaba un único texto por clave y no sabía transportar una moneda ni una
   * lista; el cable se ensanchó, así que ya no hay excusa.
   */
  private comoCaracteristica(pregunta: PreguntaCaptura): CampoDeLaPropiedad {
    return { pregunta, editable: true, motivo: null };
  }

  // ------------------------------------------------------------------
  // Titulares: el conjunto completo, sólo si se tocó
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
    this.titularesTocados.set(true);
    this.tocado();
  }

  protected quitarTitular(id: number): void {
    this.titulares.update((actuales) => {
      const quedan = actuales.filter((titular) => titular.id !== id);
      // Siempre tiene que haber un interlocutor mientras haya titulares.
      if (quedan.length > 0 && !quedan.some((titular) => titular.representante)) {
        return quedan.map((titular, indice) =>
          indice === 0 ? { ...titular, representante: true } : titular,
        );
      }
      return quedan;
    });
    this.titularesTocados.set(true);
    this.tocado();
  }

  protected cambiarCuota(id: number, evento: Event): void {
    const escrito = (evento.target as HTMLInputElement).value;
    const cuota = escrito.trim() === '' ? null : Number(escrito);
    this.titulares.update((actuales) =>
      actuales.map((titular) => (titular.id === id ? { ...titular, cuota } : titular)),
    );
    this.titularesTocados.set(true);
    this.tocado();
  }

  protected marcarRepresentante(id: number): void {
    this.titulares.update((actuales) =>
      actuales.map((titular) => ({ ...titular, representante: titular.id === id })),
    );
    this.titularesTocados.set(true);
    this.tocado();
  }

  protected buscarPropietario(evento: Event): void {
    this.busquedaPropietario.set((evento.target as HTMLInputElement).value);
    if (this.propietarios().length === 0 && !this.cargandoPropietarios()) {
      void this.cargarPropietarios(true);
    }
  }

  protected async cargarMasPropietarios(): Promise<void> {
    await this.cargarPropietarios(false);
  }

  private async cargarPropietarios(primera: boolean): Promise<void> {
    if (primera) {
      this.paginaPropietarios = 0;
    }
    this.cargandoPropietarios.set(true);
    this.errorPropietario.set(null);
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

  // ------------------------------------------------------------------
  // Gestión comercial: un bloque por encargo vivo
  // ------------------------------------------------------------------

  protected valorEconomico(encargo: EncargoPropiedad, clave: string): string {
    const tocado = this.economico()[encargo.idEncargo]?.[clave]?.valor;
    if (tocado !== undefined) {
      return tocado;
    }
    // El encargo nombra sus datos igual que la pregunta los nombra sin
    // calificar: `importe`, `moneda`, `exclusividad`, `inicio`, `fin`.
    const original = (encargo as unknown as Record<string, unknown>)[clave];
    return original == null ? '' : String(original);
  }

  protected anotarEconomico(encargo: EncargoPropiedad, clave: string, valor: string): void {
    this.economico.update((actuales) =>
      anotadoEn(actuales, encargo.idEncargo, clave, { valor }));
    this.tocado();
  }

  protected anotarEconomicoDesdeEvento(encargo: EncargoPropiedad, clave: string, evento: Event): void {
    this.anotarEconomico(encargo, clave, (evento.target as HTMLInputElement).value ?? '');
  }

  protected valorPactado(encargo: EncargoPropiedad, clave: string): string {
    if (this.pactadoRetirado()[encargo.idEncargo]?.has(clave)) {
      return '';
    }
    const tocado = this.pactado()[encargo.idEncargo]?.[clave]?.valor;
    if (tocado !== undefined) {
      return tocado;
    }
    return this.valorPactadoOriginal(encargo, clave);
  }

  /** La moneda de una condición IMPORTE — `precio_estacionamiento_adicional`. */
  protected monedaPactada(encargo: EncargoPropiedad, clave: string): string {
    if (this.estaPactadoRetirado(encargo, clave)) {
      return '';
    }
    const tocada = this.pactado()[encargo.idEncargo]?.[clave]?.moneda;
    return tocada !== undefined ? tocada : this.condicionDe(encargo, clave)?.moneda ?? '';
  }

  /** Los elementos de una condición multivalor — `equipamiento_incluido`. */
  protected valoresPactados(encargo: EncargoPropiedad, clave: string): readonly string[] | null {
    if (this.estaPactadoRetirado(encargo, clave)) {
      return [];
    }
    const tocados = this.pactado()[encargo.idEncargo]?.[clave]?.valores;
    return tocados !== undefined ? tocados : this.condicionDe(encargo, clave)?.valores ?? null;
  }

  protected anotarMonedaPactada(encargo: EncargoPropiedad, clave: string, moneda: string): void {
    this.escribirPactado(encargo, clave, { moneda });
  }

  protected anotarValoresPactados(
    encargo: EncargoPropiedad,
    clave: string,
    valores: readonly string[],
  ): void {
    this.escribirPactado(encargo, clave, { valores });
  }

  protected esImportePactado(pregunta: PreguntaCaptura): boolean {
    return pregunta.control === 'IMPORTE';
  }

  protected tienePactadoOriginal(encargo: EncargoPropiedad, clave: string): boolean {
    return this.valorPactadoOriginal(encargo, clave) !== '';
  }

  protected estaPactadoRetirado(encargo: EncargoPropiedad, clave: string): boolean {
    return this.pactadoRetirado()[encargo.idEncargo]?.has(clave) ?? false;
  }

  protected anotarPactado(encargo: EncargoPropiedad, clave: string, valor: string): void {
    this.escribirPactado(encargo, clave, { valor });
  }

  private escribirPactado(encargo: EncargoPropiedad, clave: string, cambio: Escrito): void {
    this.pactado.update((actuales) => anotadoEn(actuales, encargo.idEncargo, clave, cambio));
    this.pactadoRetirado.update((actuales) => ({
      ...actuales,
      [encargo.idEncargo]: sin(actuales[encargo.idEncargo] ?? new Set(), clave),
    }));
    this.tocado();
  }

  protected alternarRetiroPactado(encargo: EncargoPropiedad, clave: string): void {
    const id = encargo.idEncargo;
    const retirado = this.pactadoRetirado()[id]?.has(clave) ?? false;
    this.pactadoRetirado.update((actuales) => ({
      ...actuales,
      [id]: retirado ? sin(actuales[id] ?? new Set(), clave) : con(actuales[id] ?? new Set(), clave),
    }));
    if (!retirado) {
      this.pactado.update((actuales) => anotadoEn(actuales, id, clave, {}));
    }
    this.tocado();
  }

  private condicionDe(encargo: EncargoPropiedad, clave: string) {
    return encargo.condiciones?.find((condicion) => condicion.clave === clave);
  }

  private valorPactadoOriginal(encargo: EncargoPropiedad, clave: string): string {
    const condicion = this.condicionDe(encargo, clave);
    if (!condicion) {
      return '';
    }
    // De un IMPORTE, la cifra sola: la moneda va por su hueco.
    return condicion.moneda
      ? (condicion.valor ?? '').replace(condicion.moneda, '').trim()
      : condicion.valor ?? '';
  }

  /**
   * Del bloque que el Core publica para esa operación, separa lo que va en
   * `operaciones[]` de lo que va en `condiciones[]`. La frontera es la forma
   * del cable (`CAMPOS_ECONOMICOS_DEL_ENCARGO`), no el catálogo.
   */
  private bloqueDe(encargo: EncargoPropiedad): BloqueDeEncargo {
    const bloque: BloqueOperacion | undefined = this.definicion()?.deLaOperacion.find(
      (candidato) => candidato.operacion === encargo.operacion,
    );
    const preguntas = (bloque?.preguntas ?? []).map((pregunta) => ({
      ...pregunta,
      clave: sinCalificar(pregunta.clave),
    }));
    return {
      encargo,
      economicos: preguntas.filter((pregunta) =>
        CAMPOS_ECONOMICOS_DEL_ENCARGO.includes(pregunta.clave),
      ),
      pactables: preguntas.filter(
        (pregunta) => !CAMPOS_ECONOMICOS_DEL_ENCARGO.includes(pregunta.clave),
      ),
    };
  }

  // ------------------------------------------------------------------
  // El cuerpo: sólo lo tocado
  // ------------------------------------------------------------------

  private construir(): EdicionPropiedad | null {
    const ficha = this.ficha();
    if (!ficha) {
      return null;
    }
    const cuerpo: EdicionPropiedad = {};
    const fisico = this.fisico();

    const descripcion = fisico[PropiedadEditor.CLAVE_DESCRIPCION]?.valor;
    if (descripcion !== undefined) {
      cuerpo.descripcion = descripcion;
    }

    const ubicacion: UbicacionEnEdicion = {};
    const atributos: AtributoEnEdicion[] = [];
    for (const [clave, escrito] of Object.entries(fisico)) {
      if (clave === PropiedadEditor.CLAVE_DESCRIPCION) {
        continue;
      }
      if (esCampoDeUbicacion(clave)) {
        // La ubicación es de un solo hueco: no hay importes ni multivalores.
        if (escrito.valor !== undefined) {
          asignarUbicacion(ubicacion, clave, escrito.valor);
        }
      } else {
        atributos.push({
          clave,
          ...(escrito.valor === undefined ? {} : { valor: escrito.valor }),
          ...(escrito.moneda === undefined ? {} : { moneda: escrito.moneda }),
          ...(escrito.valores === undefined ? {} : { valores: [...escrito.valores] }),
        });
      }
    }
    if (Object.keys(ubicacion).length > 0) {
      cuerpo.ubicacion = ubicacion;
    }
    if (atributos.length > 0) {
      cuerpo.atributos = atributos;
    }
    const retirados = [...this.retirados()].sort();
    if (retirados.length > 0) {
      cuerpo.atributosABorrar = retirados;
    }

    if (this.titularesTocados()) {
      cuerpo.titulares = this.titulares().map((titular) => ({
        idPropietario: titular.id,
        ...(titular.cuota == null ? {} : { cuota: titular.cuota }),
        representante: titular.representante,
      }));
    }

    const operaciones: OperacionEnEdicion[] = [];
    const condiciones: CondicionesDeEncargoEnEdicion[] = [];
    for (const { encargo } of this.encargosVivos()) {
      const economico = this.economico()[encargo.idEncargo];
      if (economico && Object.keys(economico).length > 0) {
        operaciones.push(this.operacionDe(encargo, economico));
      }
      const pactado = this.pactado()[encargo.idEncargo] ?? {};
      const pactadoRetirado = [...(this.pactadoRetirado()[encargo.idEncargo] ?? [])].sort();
      const valores = Object.entries(pactado)
        .filter(([, escrito]) => !estaVacio(escrito))
        .map(([clave, escrito]) => ({
          clave,
          ...(escrito.valor === undefined ? {} : { valor: escrito.valor }),
          ...(escrito.moneda === undefined ? {} : { moneda: escrito.moneda }),
          ...(escrito.valores === undefined ? {} : { valores: [...escrito.valores] }),
        }));
      if (valores.length > 0 || pactadoRetirado.length > 0) {
        condiciones.push({
          idEncargo: encargo.idEncargo,
          ...(valores.length > 0 ? { atributos: valores } : {}),
          ...(pactadoRetirado.length > 0 ? { atributosABorrar: pactadoRetirado } : {}),
        });
      }
    }
    if (operaciones.length > 0) {
      cuerpo.operaciones = operaciones;
    }
    if (condiciones.length > 0) {
      cuerpo.condiciones = condiciones;
    }

    return Object.keys(cuerpo).length > 0 ? cuerpo : null;
  }

  /**
   * El bloque de una operación. El importe y la moneda van **siempre** —el
   * cable los exige— y si no cambiaron el Core no añade hito. El resto sólo si
   * se tocó. La operación es la del encargo: no se elige, no se cambia.
   */
  private operacionDe(encargo: EncargoPropiedad, tocado: Tocado): OperacionEnEdicion {
    const escrito = (clave: string) => tocado[clave]?.valor;
    const importe = escrito('importe') !== undefined ? Number(escrito('importe')) : encargo.importe;
    const operacion: OperacionEnEdicion = {
      operacion: encargo.operacion,
      importe: importe ?? Number.NaN,
      moneda: escrito('moneda') ?? encargo.moneda ?? '',
    };
    // La exclusividad tiene tres estados como cualquier booleano: sin declarar
    // no viaja, y por eso se mira `=== 'true'` sólo cuando hay algo escrito.
    if (escrito('exclusividad') !== undefined) {
      operacion.exclusividad = escrito('exclusividad') === 'true';
    }
    if (escrito('inicio') !== undefined) {
      operacion.inicioEncargo = escrito('inicio');
    }
    if (escrito('fin') !== undefined) {
      operacion.finEncargo = escrito('fin');
    }
    return operacion;
  }

  // ------------------------------------------------------------------
  // Guardar
  // ------------------------------------------------------------------

  protected async guardar(): Promise<void> {
    const ficha = this.ficha();
    const cuerpo = this.cambios();
    if (!ficha || !cuerpo || this.guardando()) {
      return;
    }
    if (this.avisoDeCuotas() && cuerpo.titulares) {
      this.errorGuardado.set(this.avisoDeCuotas());
      return;
    }
    this.claveIdempotencia ??= crearClave();
    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      await this.api.editar(ficha.id, cuerpo, this.claveIdempotencia);
      await this.router.navigate(['/propiedades', ficha.id]);
    } catch (error) {
      this.errorGuardado.set(mensajeDe(error, 'No se pudo guardar la propiedad.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected reintentar(): void {
    void this.cargar();
  }

  protected valor(valor: string | null | undefined): string {
    return texto(valor);
  }

  /** Cualquier cambio de lo tocado invalida la clave del guardado anterior. */
  private tocado(): void {
    this.claveIdempotencia = null;
    this.errorGuardado.set(null);
  }
}

// ====================================================================

function esCampoDeUbicacion(clave: string): clave is keyof UbicacionEnEdicion {
  return (CAMPOS_DE_UBICACION as readonly string[]).includes(clave);
}

function asignarUbicacion(ubicacion: UbicacionEnEdicion, clave: keyof UbicacionEnEdicion, valor: string): void {
  if (clave === 'latitud' || clave === 'longitud') {
    ubicacion[clave] = Number(valor);
  } else {
    ubicacion[clave] = valor;
  }
}

/** `garantia_meses:ALQUILER` → `garantia_meses`. Una clave sin calificar se devuelve igual. */
function sinCalificar(clave: string): string {
  const corte = clave.indexOf(':');
  return corte < 0 ? clave : clave.slice(0, corte);
}

/** Sin nada escrito en ninguno de sus tres huecos. */
function estaVacio(escrito: Escrito): boolean {
  return (
    (escrito.valor ?? '') === '' &&
    (escrito.moneda ?? '') === '' &&
    (escrito.valores?.length ?? 0) === 0
  );
}

function anotadoEn(
  actuales: TocadoPorEncargo,
  idEncargo: number,
  clave: string,
  cambio: Escrito,
): TocadoPorEncargo {
  const bloque = { ...(actuales[idEncargo] ?? {}) };
  const fundido: Escrito = { ...bloque[clave], ...cambio };
  if (estaVacio(fundido)) {
    delete bloque[clave];
  } else {
    bloque[clave] = fundido;
  }
  return { ...actuales, [idEncargo]: bloque };
}

function con(conjunto: ReadonlySet<string>, clave: string): ReadonlySet<string> {
  return new Set([...conjunto, clave]);
}

function sin(conjunto: ReadonlySet<string>, clave: string): ReadonlySet<string> {
  const siguiente = new Set(conjunto);
  siguiente.delete(clave);
  return siguiente;
}

function crearClave(): string {
  const cripto = globalThis.crypto;
  return cripto && 'randomUUID' in cripto
    ? cripto.randomUUID()
    : `edicion-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function normalizar(valor: string): string {
  return valor
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : porDefecto;
}
