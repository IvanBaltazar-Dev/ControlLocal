import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnDestroy,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiError } from '../../core/api/api.types';
import {
  CandidatoResponsable,
  PropiedadesService,
  Responsabilidad,
} from '../../core/api/propiedades.service';

/**
 * **La llave del P0, donde ya se ve la propiedad** (C3).
 *
 * ## Por qué vive aquí y no en una pantalla nueva
 *
 * El traspaso decide **quién puede escribir** un inmueble concreto. Esa decisión
 * se toma mirando el inmueble —quién responde hoy, qué encargos tiene, qué
 * estado— y todo eso ya está en la ficha. Una pantalla aparte obligaría a
 * llevarse el contexto a otro sitio para decidir, o a decidir sin él.
 *
 * Sin esto, el P0 entregaba la puerta cerrada **sin la llave**: la autoridad
 * sólo se movía por HTTP crudo, y con las 26 propiedades de `dev` en FALTANTE
 * eso significa que nadie podía editar ninguna desde la aplicación.
 *
 * ## Lo que esta pantalla NO decide
 *
 * - **Si se ofrece**: lo dice `responsabilidad.puedeTraspasar`, resuelto por el
 *   Core. Aquí no se compara ningún rol de sesión.
 * - **A quién se puede pasar**: la lista es
 *   `GET /propiedades/{id}/responsable/candidatos`, que llega **ya depurada**
 *   para esta propiedad y este actor —tenant, rol vigente, cuenta habilitada,
 *   relación organizacional, estado operativo, supervisión y sin el
 *   responsable actual (D-P0-7 + D-P0-12)—. **Aquí no se filtra ni un
 *   elemento**: se pinta lo que devolvió el Core, en su orden.
 * - **Si el motivo vale**: lo exige el Core con el mismo mínimo que la
 *   reasignación de un encargo. Aquí se avisa antes de enviar porque es mejor
 *   experiencia, no porque sea esta pantalla quien lo hace cumplir.
 *
 * ## Por qué la búsqueda va al servidor
 *
 * La lista de candidatos es del **tenant**, no del formulario. Antes se pedían
 * `agentes.pagina(1, 100)` y se depuraba aquí: con más de cien agentes la
 * primera página llegaba **truncada sin decirlo**, y acotar en el cliente sobre
 * ella devolvía «no hay nadie» teniendo a la persona buscada en la página dos.
 * Por eso cada búsqueda vuelve al Core con su `texto`, y cuando quedan
 * candidatos fuera de la página se dice, en vez de callarlo.
 *
 * El motivo queda en un expediente **append-only** que nadie corrige después.
 * Por eso el aviso dice para qué sirve y no sólo cuántas letras faltan.
 */
@Component({
  selector: 'cl-traspaso-responsable',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './traspaso-responsable.scss',
  template: `
    <section class="traspaso">
      <header>
        <h3>Quién responde por este inmueble</h3>
        @if (responsabilidad()?.nombre; as quien) {
          <p class="quien">{{ quien }}</p>
        } @else {
          <p class="faltante">
            No se sabe. Esta propiedad no tiene agente responsable, así que
            todavía no la edita nadie.
          </p>
        }
      </header>

      @if (!abierto()) {
        <button type="button" class="cl-btn" (click)="abrir()">
          {{ responsabilidad()?.idResponsable == null ? 'Asignar responsable' : 'Traspasar' }}
        </button>
      } @else {
        <div class="formulario">
          <label>
            <span>Buscar agente</span>
            <input
              type="search"
              name="busqueda"
              [(ngModel)]="busqueda"
              (ngModelChange)="alEscribir()"
              [disabled]="guardando()"
              placeholder="Nombre o código"
            />
          </label>

          <label>
            <span>Nuevo responsable</span>
            <select [(ngModel)]="idElegido" name="idAgente" [disabled]="guardando()">
              <option [ngValue]="null">Elige un agente…</option>
              <!-- Tal como los devolvió el Core, en su orden y sin descartar
                   ninguno: la elegibilidad ya está resuelta allí. -->
              @for (candidato of candidatos(); track candidato.idAgente) {
                <option [ngValue]="candidato.idAgente">{{ etiqueta(candidato) }}</option>
              }
            </select>
          </label>

          @if (cargandoCandidatos()) {
            <p class="cl-menudo">Cargando agentes…</p>
          } @else if (candidatos().length === 0) {
            <!-- Neutro a propósito. El Core ya descartó por tenant, rol,
                 cuenta, relación organizacional, disponibilidad y supervisión,
                 y no publica cuál de las seis fue: afirmar aquí «no supervisas
                 a nadie» sería inventarle una causa a una lista vacía. -->
            <p class="cl-vacio">No hay agentes que puedan recibirla hoy.</p>
          } @else if (hayMas()) {
            <!-- La página no es la lista. Callarlo es lo que convertía un
                 catálogo truncado en «ese agente no existe». -->
            <p class="cl-menudo">Hay más agentes: acota por nombre o código.</p>
          }

          <label>
            <span>Motivo del traspaso</span>
            <textarea
              [(ngModel)]="motivo"
              name="motivo"
              rows="3"
              [disabled]="guardando()"
              placeholder="Por qué cambia de manos. Queda en el expediente y no se puede corregir después."
            ></textarea>
          </label>
          @if (motivoCorto()) {
            <p class="cl-menudo">
              Faltan {{ MINIMO_MOTIVO - motivo.trim().length }} caracteres. Este texto
              queda en el expediente de la propiedad y tiene que entenderse dentro
              de unos meses.
            </p>
          }

          @if (error(); as fallo) {
            <p class="cl-aviso" role="alert">{{ fallo }}</p>
          }
          @if (desactualizada()) {
            <!-- El estado del que partía este traspaso ya no es el que hay, así
                 que no se ofrece reintentar: reintentar tal cual sería ejecutar
                 sobre un responsable que nadie miró (D-P0-9). Lo que se ofrece
                 es volver a mirar. -->
            <button type="button" class="cl-btn" (click)="volverACargar()">
              Volver a cargar la ficha
            </button>
          }

          <div class="acciones">
            <button
              type="button"
              class="cl-btn primario"
              [disabled]="!listo() || guardando()"
              (click)="traspasar()"
            >
              {{ guardando() ? 'Guardando…' : 'Confirmar traspaso' }}
            </button>
            <button type="button" class="cl-btn" [disabled]="guardando()" (click)="cerrar()">
              Cancelar
            </button>
          </div>
        </div>
      }
    </section>
  `,
})
export class TraspasoResponsable implements OnDestroy {
  private readonly propiedades = inject(PropiedadesService);

  readonly idPropiedad = input.required<number>();
  readonly responsabilidad = input<Responsabilidad | null | undefined>(null);

  /** La ficha se relee entera: el traspaso cambia quién puede editar. */
  readonly traspasado = output<void>();

  /**
   * **La ficha se relee sin que haya habido traspaso** (D-P0-9).
   *
   * Es una salida distinta de `traspasado` a propósito, porque el hecho es
   * distinto: aquí **no** ha cambiado nada: el Core respondió 409 porque el
   * responsable que se veía en pantalla ya no es el que hay. Emitir
   * `traspasado` sería decirle al resto de la pantalla que ocurrió algo que no
   * ocurrió; callar dejaría al usuario mirando un estado que sabemos falso.
   */
  readonly recargar = output<void>();

  /**
   * El mínimo que exige el Core (`PoliticaComercial.MOTIVO_REASIGNACION`).
   *
   * Está duplicado a sabiendas y con una asimetría deliberada: aquí sólo sirve
   * para **avisar antes** de enviar. Quien lo hace cumplir es el backend, y si
   * un día sube el mínimo, esta pantalla avisará de menos —nunca de más— y el
   * Core seguirá rechazando lo que tenga que rechazar.
   */
  protected readonly MINIMO_MOTIVO = 10;

  /** Cuántos candidatos se piden de una vez. El resto se acota buscando. */
  private readonly TAMANO_PAGINA = 50;

  /** Lo justo para no disparar una consulta por tecla. Sin librerías. */
  private readonly RETARDO_BUSQUEDA_MS = 250;

  protected readonly abierto = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly cargandoCandidatos = signal(false);

  /**
   * El Core rechazó por **409**: el responsable cambió desde que se cargó la
   * ficha. No es un fallo del formulario y no se arregla reintentando.
   */
  protected readonly desactualizada = signal(false);

  /** Lo que devolvió el Core para esta propiedad y este actor. Nada más. */
  protected readonly candidatos = signal<readonly CandidatoResponsable[]>([]);

  /** Cuántos hay en total, para no presentar una página como si fuera la lista. */
  protected readonly total = signal(0);

  protected idElegido: number | null = null;
  protected motivo = '';
  protected busqueda = '';

  private temporizador: ReturnType<typeof setTimeout> | null = null;

  /**
   * Contador de peticiones: sólo se pinta la respuesta de la **última**.
   *
   * Dos búsquedas en vuelo pueden volver al revés, y la vieja pintaría
   * resultados que ya no corresponden a lo escrito.
   */
  private peticion = 0;

  protected readonly hayMas = computed(() => this.total() > this.candidatos().length);

  protected readonly motivoCorto = computed(
    () => this.motivo.trim().length > 0 && this.motivo.trim().length < this.MINIMO_MOTIVO,
  );

  ngOnDestroy(): void {
    this.cancelarBusqueda();
  }

  /** Nombre, código y zona, unidos. Los tres los publica el Core. */
  protected etiqueta(candidato: CandidatoResponsable): string {
    return [candidato.nombre, candidato.codigoAgente, candidato.zonaAsignada]
      .filter((parte): parte is string => !!parte && parte.trim().length > 0)
      .join(' · ');
  }

  protected listo(): boolean {
    return this.idElegido !== null && this.motivo.trim().length >= this.MINIMO_MOTIVO;
  }

  protected abrir(): void {
    this.abierto.set(true);
    this.error.set(null);
    this.desactualizada.set(false);
    void this.consultar();
  }

  /**
   * Pide al padre que vuelva a leer la ficha entera y cierra el formulario.
   *
   * <p>Cierra a propósito: los datos con los que se rellenó —empezando por el
   * responsable que se vio— son justo los que el Core acaba de declarar
   * caducados. Dejarlo abierto invitaría a pulsar otra vez sobre lo mismo.
   */
  protected volverACargar(): void {
    this.cerrar();
    this.recargar.emit();
  }

  /** Cada tecla reinicia la espera; se consulta cuando se deja de escribir. */
  protected alEscribir(): void {
    this.cancelarBusqueda();
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      void this.consultar();
    }, this.RETARDO_BUSQUEDA_MS);
  }

  protected cerrar(): void {
    this.cancelarBusqueda();
    this.abierto.set(false);
    this.error.set(null);
    this.desactualizada.set(false);
    this.idElegido = null;
    this.motivo = '';
    this.busqueda = '';
    this.candidatos.set([]);
    this.total.set(0);
  }

  protected async traspasar(): Promise<void> {
    const destino = this.idElegido;
    if (destino === null || this.guardando()) {
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.desactualizada.set(false);
    try {
      // El responsable que se está viendo AHORA en la ficha viaja con el
      // comando (D-P0-9): el traspaso es «cambia este por aquel», no «pon a
      // aquel». `null` no es omitirlo — dice «la vi sin responsable», que es un
      // estado observado y no un hueco.
      await this.propiedades.asignarResponsable(
        this.idPropiedad(),
        destino,
        this.motivo.trim(),
        this.responsabilidad()?.idResponsable ?? null,
      );
      this.cerrar();
      this.traspasado.emit();
    } catch (fallo) {
      // El texto del rechazo lo escribe el Core: si no supervisas al agente, si
      // el motivo es corto o si ya responde por ella, el mensaje lo dice y esta
      // pantalla no lo reescribe.
      this.error.set(
        fallo instanceof ApiError
          ? fallo.message
          : 'No se pudo traspasar la propiedad. Vuelve a intentarlo.',
      );
      // Y el 409 no es «vuelve a intentarlo»: es «lo que veías ya no es». No se
      // emite `traspasado` —no hubo traspaso— y lo único que se ofrece es
      // volver a mirar. Reintentar en silencio sobre el estado nuevo sería la
      // reinterpretación que el Core acaba de negarse a hacer.
      this.desactualizada.set(fallo instanceof ApiError && fallo.status === 409);
    } finally {
      this.guardando.set(false);
    }
  }

  /**
   * Le pregunta al Core qué destinos puede elegir, con el texto tal cual se
   * escribió. La depuración ya viene hecha (D-P0-12).
   */
  private async consultar(): Promise<void> {
    const mia = ++this.peticion;
    const texto = this.busqueda.trim();
    this.cargandoCandidatos.set(true);
    this.error.set(null);
    try {
      const pagina = await this.propiedades.candidatos(
        this.idPropiedad(),
        texto.length > 0 ? texto : undefined,
        1,
        this.TAMANO_PAGINA,
      );
      if (mia !== this.peticion) {
        return;
      }
      this.candidatos.set(pagina.items);
      this.total.set(pagina.totalRecords);
      // Si el elegido ya no está en la lista, deja de estar elegido: mantenerlo
      // dejaría el botón activo apuntando a un agente que no se ve.
      if (!pagina.items.some((candidato) => candidato.idAgente === this.idElegido)) {
        this.idElegido = null;
      }
    } catch (fallo) {
      if (mia !== this.peticion) {
        return;
      }
      this.candidatos.set([]);
      this.total.set(0);
      this.error.set(
        fallo instanceof ApiError
          ? fallo.message
          : 'No se pudo leer la lista de agentes. Vuelve a intentarlo.',
      );
    } finally {
      if (mia === this.peticion) {
        this.cargandoCandidatos.set(false);
      }
    }
  }

  private cancelarBusqueda(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
