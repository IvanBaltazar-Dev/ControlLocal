import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Agente, AgentesService } from '../../core/api/agentes.service';
import { ApiError } from '../../core/api/api.types';
import { PropiedadesService, Responsabilidad } from '../../core/api/propiedades.service';

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
 * - **A quién se puede pasar**: la lista es `GET /agentes`, que ya viene
 *   acotada — el BROKER recibe a los que supervisa, el gobierno del tenant a
 *   todos los de su organización, y un agente de otra corredora no aparece
 *   nunca. La frontera de tenant no la dibuja este selector.
 * - **Si el motivo vale**: lo exige el Core con el mismo mínimo que la
 *   reasignación de un encargo. Aquí se avisa antes de enviar porque es mejor
 *   experiencia, no porque sea esta pantalla quien lo hace cumplir.
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
            <span>Nuevo responsable</span>
            <select [(ngModel)]="idElegido" name="idAgente" [disabled]="guardando()">
              <option [ngValue]="null">Elige un agente…</option>
              @for (agente of asignables(); track agente.id) {
                <option [ngValue]="agente.id">{{ agente.nombre }}</option>
              }
            </select>
          </label>

          @if (cargandoAgentes()) {
            <p class="cl-menudo">Cargando agentes…</p>
          } @else if (asignables().length === 0) {
            <p class="cl-vacio">
              No hay a quién traspasarla: no supervisas a ningún otro agente de
              esta organización.
            </p>
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
export class TraspasoResponsable {
  private readonly propiedades = inject(PropiedadesService);
  private readonly agentes = inject(AgentesService);

  readonly idPropiedad = input.required<number>();
  readonly responsabilidad = input<Responsabilidad | null | undefined>(null);

  /** La ficha se relee entera: el traspaso cambia quién puede editar. */
  readonly traspasado = output<void>();

  /**
   * El mínimo que exige el Core (`PoliticaComercial.MOTIVO_REASIGNACION`).
   *
   * Está duplicado a sabiendas y con una asimetría deliberada: aquí sólo sirve
   * para **avisar antes** de enviar. Quien lo hace cumplir es el backend, y si
   * un día sube el mínimo, esta pantalla avisará de menos —nunca de más— y el
   * Core seguirá rechazando lo que tenga que rechazar.
   */
  protected readonly MINIMO_MOTIVO = 10;

  protected readonly abierto = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly cargandoAgentes = signal(false);
  protected readonly agentesDelAlcance = signal<readonly Agente[]>([]);

  protected idElegido: number | null = null;
  protected motivo = '';

  /**
   * A quién se le puede pasar: los del alcance, menos quien ya responde.
   *
   * Quitar al actual no es cosmética — el Core responde 400 a un traspaso «de A
   * a A», porque una línea así en el expediente no cuenta ningún hecho.
   */
  protected readonly asignables = computed(() => {
    const actual = this.responsabilidad()?.idResponsable ?? null;
    return this.agentesDelAlcance().filter((agente) => agente.id !== actual);
  });

  protected readonly motivoCorto = computed(
    () => this.motivo.trim().length > 0 && this.motivo.trim().length < this.MINIMO_MOTIVO,
  );

  protected listo(): boolean {
    return this.idElegido !== null && this.motivo.trim().length >= this.MINIMO_MOTIVO;
  }

  protected async abrir(): Promise<void> {
    this.abierto.set(true);
    this.error.set(null);
    if (this.agentesDelAlcance().length > 0) {
      return;
    }
    this.cargandoAgentes.set(true);
    try {
      const pagina = await this.agentes.pagina(1, 100);
      this.agentesDelAlcance.set(pagina.items);
    } catch (fallo) {
      this.error.set(
        fallo instanceof ApiError
          ? fallo.message
          : 'No se pudo leer la lista de agentes. Vuelve a intentarlo.',
      );
    } finally {
      this.cargandoAgentes.set(false);
    }
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.error.set(null);
    this.idElegido = null;
    this.motivo = '';
  }

  protected async traspasar(): Promise<void> {
    const destino = this.idElegido;
    if (destino === null || this.guardando()) {
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    try {
      await this.propiedades.asignarResponsable(this.idPropiedad(), destino, this.motivo.trim());
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
    } finally {
      this.guardando.set(false);
    }
  }
}
