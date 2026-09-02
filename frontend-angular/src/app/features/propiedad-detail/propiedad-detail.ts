import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  AtributoPropiedad,
  FichaPropiedad,
  HechoDeActividad,
  ImporteFechado,
  motivoDeBloqueo,
  PropiedadesService,
  puedeEscribir,
} from '../../core/api/propiedades.service';
import { fechaCorta, monto, SIN_DATO, texto } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { BloqueEncargo } from './bloque-encargo';
import { TraspasoResponsable } from './traspaso-responsable';

/** Un proceso de la actividad, con su rótulo y el orden en que se lee. */
interface Carril {
  clave: string;
  rotulo: string;
  hechos: readonly HechoDeActividad[];
}

/**
 * **La ficha universal de una propiedad.**
 *
 * Sustituye a `local-detail`, que leía el modelo heredado (`GET /locales/{id}`:
 * un propietario, un precio, ninguna operación) y por eso no podía enseñar una
 * propiedad que se vende **y** se alquila sin elegir uno de los dos y llamarlo
 * «el precio».
 *
 * ## Los tres conceptos, y por qué no se vuelven a mezclar
 *
 * 1. **La cosa física** — tipo, ubicación, características, titulares. No tiene
 *    precio ni operación: no son suyos.
 * 2. **Su gestión comercial** — un bloque por encargo, cada uno con su importe,
 *    su estado, su agente y **su** histórico. Dos históricos nunca se funden.
 * 3. **Su actividad** — los hechos, cada uno con el encargo del que nace.
 *
 * ## La identidad del bloque es `idEncargo`, no `operacion`
 *
 * Es la regla que sostiene el modelo cuando aparece la historia. Una propiedad
 * puede haber tenido tres alquileres sucesivos —2024 cerrado, 2025 cerrado,
 * 2026 vigente—: lo que la base prohíbe es dos **vivos** de la misma operación,
 * no que hayan existido varios. Agrupados por operación serían un bloque con
 * tres precios dentro y una línea temporal que no significa nada.
 *
 * Aquí no hay ningún `groupBy`. La lista llega del backend, ordenada, y se
 * pinta tal cual, con `track encargo.idEncargo`.
 *
 * ## Esta pantalla no traduce nada
 *
 * Ni el tipo, ni el uso, ni el estado, ni la operación, ni el nombre del
 * importe. Todos los rótulos vienen del read model. Un ternario tan inocente
 * como `operacion === 'VENTA' ? 'Precio' : 'Renta'` es semántica inmobiliaria
 * escrita en la interfaz, y con dos interfaces —BROX Web y KAIROS— serían dos
 * que se separan (D-A-1 §5). Lo único que se compone aquí es el ORDEN de los
 * bloques y qué se pliega, que es presentación.
 */
@Component({
  selector: 'app-propiedad-detail',
  imports: [RouterLink, EstadoListado, BloqueEncargo, TraspasoResponsable],
  templateUrl: './propiedad-detail.html',
  styleUrl: './propiedad-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropiedadDetail implements OnInit {
  private readonly api = inject(PropiedadesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ficha = signal<FichaPropiedad | null>(null);

  /**
   * Qué encargo acota la actividad. `null` es «todos».
   *
   * Filtra por **encargo**, no por operación: con tres alquileres en la
   * historia, «Alquiler» juntaría la actividad de los tres y volvería a perder
   * la procedencia que el backend se molestó en poner.
   */
  protected readonly encargoElegido = signal<number | null>(null);

  protected readonly SIN_DATO = SIN_DATO;

  /**
   * **Si esta persona puede escribir esta propiedad** (P0).
   *
   * Lo dice el backend en la ficha. Antes lo decidía una sola línea, que
   * comparaba el rol de la sesión con «AGENTE»: era una copia del gate del
   * backend que dejó de ser cierta con V87, porque la autoridad ya no es «ser
   * agente», es **ser el responsable de esta
   * propiedad**. Con la copia vieja, todo agente del tenant veía el botón
   * «Editar» de toda propiedad y se llevaba un 403 al guardar.
   *
   * Mientras la ficha carga vale `false`: no se ofrece una acción que
   * todavía no se sabe si existe. Y ante un `responsabilidad` **ausente** vale
   * lo mismo, porque lo decide `puedeEscribir` — una sola función para esta
   * pantalla, el editor y la ficha del encargo. Antes cada una elegía su
   * defecto y el editor elegía el contrario.
   */
  protected readonly puedeEditar = computed(() =>
    puedeEscribir(this.ficha()?.responsabilidad),
  );

  /** Quién responde por el inmueble hoy, o `null` si está FALTANTE. */
  protected readonly responsable = computed(
    () => this.ficha()?.responsabilidad?.nombre ?? null,
  );

  /**
   * Por qué no se puede escribir, en las palabras del Core.
   *
   * **Se pinta.** Estuvo declarado y sin usar en la plantilla: el agente veía
   * desaparecer «Editar» sin ninguna explicación, que es justo lo que el propio
   * `exigirEdicion` evita en el backend cuando devuelve el motivo con el 403.
   */
  protected readonly motivoBloqueo = computed(() =>
    motivoDeBloqueo(this.ficha()?.responsabilidad),
  );

  protected readonly vivos = computed(
    () => this.ficha()?.encargos.filter((encargo) => encargo.vivo) ?? [],
  );

  protected readonly cerrados = computed(
    () => this.ficha()?.encargos.filter((encargo) => !encargo.vivo) ?? [],
  );

  /** La ubicación en una línea, saltándose lo que no se sabe. */
  protected readonly ubicacion = computed(() => {
    const donde = this.ficha()?.ubicacion;
    if (!donde) {
      return SIN_DATO;
    }
    return (
      [
        donde.nombreEdificioGaleria,
        donde.interiorUnidad && `Int. ${donde.interiorUnidad}`,
        donde.piso && `Piso ${donde.piso}`,
        donde.zonaUrbanizacion,
        donde.distrito,
      ]
        .filter((parte): parte is string => !!parte && parte.trim().length > 0)
        .join(' · ') || SIN_DATO
    );
  });

  /**
   * Los cinco procesos, con la actividad ya acotada al encargo elegido.
   *
   * El reparto por proceso lo hace el backend; aquí sólo se les pone orden y
   * nombre de sección, que es presentación.
   *
   * El bloque **puede no viajar** (D-P0-6). Sin él esto devuelve la lista
   * vacía y no lanza; quien decide que la sección entera no se pinte —en vez
   * de escribir «todavía no hay actividad», que sería afirmar un hecho— es la
   * plantilla, que sí distingue ausente de vacío.
   */
  protected readonly carriles = computed<Carril[]>(() => {
    const actividad = this.ficha()?.actividad;
    if (!actividad) {
      return [];
    }
    return [
      { clave: 'oportunidades', rotulo: 'Oportunidades', hechos: actividad.oportunidades },
      { clave: 'visitas', rotulo: 'Visitas', hechos: actividad.visitas },
      { clave: 'interacciones', rotulo: 'Contacto', hechos: actividad.interacciones },
      { clave: 'expedientes', rotulo: 'Expedientes', hechos: actividad.expedientes },
      { clave: 'contratos', rotulo: 'Contratos', hechos: actividad.contratos },
    ].map((carril) => ({ ...carril, hechos: this.acotar(carril.hechos) }));
  });

  protected readonly hayActividad = computed(() =>
    this.carriles().some((carril) => carril.hechos.length > 0),
  );

  /**
   * **La memoria del inmueble**, que es distinta de sus encargos.
   *
   * Los bloques de arriba contestan «qué dice este encargo». Esto contesta «qué
   * ha pasado con este inmueble»: cuántas veces estuvo en alquiler, a cuánto se
   * alquiló la última vez, cuál fue el último precio de cierre.
   *
   * Llega calculada del backend, con cada cifra apuntando a su `idEncargo`.
   * Aquí no se agrega nada: agregar sobre encargos es dominio, no presentación.
   */
  protected readonly historia = computed(() => this.ficha()?.historia?.porOperacion ?? []);

  /** Los movimientos del inmueble, cruzando encargos, del más reciente al más antiguo. */
  protected readonly linea = computed(() => this.ficha()?.historia?.linea ?? []);

  /** Cuántos hechos hay en total, para no prometer una sección vacía. */
  protected readonly totalHechos = computed(() =>
    this.carriles().reduce((suma, carril) => suma + carril.hechos.length, 0),
  );

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  protected async cargar(): Promise<void> {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.ficha.set(null);
      this.error.set('El identificador de la propiedad no es válido.');
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.ficha.set(await this.api.consultar(id));
    } catch (error) {
      this.ficha.set(null);
      this.error.set(
        error instanceof ApiError || error instanceof Error
          ? error.message
          : 'No se pudo cargar la propiedad.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  protected elegirEncargo(idEncargo: number | null): void {
    this.encargoElegido.set(idEncargo);
  }

  protected abrir(hecho: HechoDeActividad): void {
    if (hecho.ruta) {
      void this.router.navigateByUrl(`/${hecho.ruta}`);
    }
  }

  // ------------------------------------------------------------------
  // Formato. Nada de esto decide nada: pone comas y puntos.
  // ------------------------------------------------------------------

  protected fecha(valor: string | null | undefined): string {
    return fechaCorta(valor);
  }

  /**
   * Un importe de la historia: la cifra y cuándo fue.
   *
   * Sin dato devuelve el guion y **no un cero**: «no hubo cierre» y «se cerró a
   * cero» son cosas distintas, y la segunda no ha pasado nunca.
   */
  protected cifraFechada(importe: ImporteFechado | null | undefined): string {
    if (!importe || importe.monto === null || importe.monto === undefined) {
      return SIN_DATO;
    }
    return monto(importe.monto, importe.moneda) + " · " + fechaCorta(importe.fecha);
  }

  protected cifra(valor: number | null | undefined, moneda: string | null | undefined): string {
    return monto(valor, moneda);
  }

  /** «3 veces», «1 vez». */
  protected veces(cuantas: number): string {
    return cuantas === 1 ? "1 vez" : cuantas + " veces";
  }

  protected valor(valor: string | null | undefined): string {
    return texto(valor);
  }

  /**
   * El valor de una característica con su unidad: `85.5 m²`.
   *
   * Se pega la unidad que declara el catálogo; esta pantalla no sabe cuál lleva
   * cada clave ni tiene por qué saberlo.
   */
  protected caracteristica(atributo: AtributoPropiedad): string {
    const limpio = (atributo.valor ?? '').trim();
    if (!limpio) {
      return SIN_DATO;
    }
    // Un si/no se escribia «true», que es el valor del cable y no una
    // palabra. El TIPO lo declara el catálogo y viaja en `tipoDato`: leerlo
    // es representar la respuesta, no decidir qué se pregunta (D-A-1 §6).
    if (atributo.tipoDato === 'BOOLEANO') {
      return limpio.toLowerCase() === 'true' ? 'Sí' : 'No';
    }
    return atributo.unidad ? `${limpio} ${atributo.unidad}` : limpio;
  }

  /**
   * ¿Es un dato **histórico**? Lo dice el Core en `estadoDato`; aquí no se
   * decide, ni se mira la clave, ni se mantiene ninguna lista de campos no
   * editables.
   *
   * Se compara contra `'HISTORICO'` y no contra `!== 'VIGENTE'`: un valor que
   * no llegara —cliente contra una respuesta más vieja— se leería como
   * histórico, y marcar como historia un dato corregible es peor que no
   * marcarlo.
   */
  protected esHistorico(atributo: AtributoPropiedad): boolean {
    return atributo.estadoDato === 'HISTORICO';
  }

  /** El importe de un hecho de actividad, cuando lo tiene. */
  protected montoDelHecho(hecho: HechoDeActividad): string {
    return hecho.monto === null || hecho.monto === undefined ? '' : monto(hecho.monto, hecho.moneda);
  }

  /**
   * La cuota de un titular, **siempre**.
   *
   * Antes se ocultaba con un solo titular, por parecer ruido. Pero la cuota es
   * justo el dato que hace real la titularidad múltiple, y esconderla en el
   * caso simple hacía que la sección volviera a leerse como «el propietario».
   * Sin cuota registrada se dice que no la hay; no se supone 100.
   */
  protected cuota(cuota: number | null | undefined): string {
    return cuota === null || cuota === undefined ? SIN_DATO : `${cuota} %`;
  }

  private acotar(hechos: readonly HechoDeActividad[]): readonly HechoDeActividad[] {
    const elegido = this.encargoElegido();
    return elegido === null ? hechos : hechos.filter((hecho) => hecho.idEncargo === elegido);
  }
}
