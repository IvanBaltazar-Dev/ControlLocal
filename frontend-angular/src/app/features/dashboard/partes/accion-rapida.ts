import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../../core/api/api.types';
import { CANAL_CONTACTO, opcionesDe, resultadosDe } from '../../../core/api/codigos';
import { InteraccionesService } from '../../../core/api/interacciones.service';
import { VisitasService } from '../../../core/api/visitas.service';
import { Icono } from '../../../shared/icono/icono';
import { AsuntoDelFoco } from '../asunto-del-foco';

/**
 * Qué se puede resolver aquí mismo, y con qué llamada.
 *
 * **La decide el tipo de entidad**, que es vocabulario del dominio, y no la
 * descripción de la tarea. D-E2-1 §7.0.h dice que la acción debería viajar en
 * el cable (`accion: ARCHIVO | FECHA | REGISTRO | DATO | EXPEDIENTE`); mientras
 * no viaje, esta tabla la deduce del tipo de entidad — que es el dato más
 * estable que sí llega. Cuando el cable la traiga, esta tabla se retira.
 */
type Forma = 'CERRAR_VISITA' | 'REGISTRAR_CONTACTO' | 'NINGUNA';

function formaDe(asunto: AsuntoDelFoco): Forma {
  const entidad = asunto.tarea?.entidadTipo;
  if (entidad === 'VISITA') {
    return 'CERRAR_VISITA';
  }
  if (entidad === 'PROSPECCION') {
    return 'REGISTRAR_CONTACTO';
  }
  return 'NINGUNA';
}

/**
 * **Resolver sin salir del Inicio.**
 *
 * El Radar identificaba el problema y después mandaba a otra pantalla a
 * arreglarlo. Para lo que se cierra con un dato —una visita que hay que marcar
 * como realizada, un contacto que hay que registrar— eso es un viaje de ida y
 * vuelta por algo que cabe en dos campos, y de paso pierde el sitio en la cola.
 *
 * ## Está atado al problema que se enuncia
 *
 * La forma sale de la entidad del asunto, así que lo que se ofrece es lo que
 * cierra **ese** hecho: si arriba dice «falta cerrar la visita con su
 * resultado», aquí hay «se realizó / no se realizó», no un formulario genérico.
 * Cuando no hay una acción que quepa, no se inventa una: se ofrece el
 * expediente.
 *
 * ## Lo que no se hace aquí
 *
 * Nada que necesite decisión larga —evaluar una solicitud, conformar
 * documentos— tiene ventana rápida: meter un expediente entero en una columna
 * de 340 px sería peor que el viaje. Esos siguen abriendo su pantalla.
 */
@Component({
  selector: 'cl-accion-rapida',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './accion-rapida.html',
  styleUrl: './accion-rapida.scss',
})
export class AccionRapida {
  private readonly visitas = inject(VisitasService);
  private readonly interacciones = inject(InteraccionesService);

  readonly asunto = input.required<AsuntoDelFoco>();

  /** Se resolvió: el padre recarga, porque el asunto deja de existir. */
  readonly resuelto = output<string>();
  /** No hay forma rápida: el padre ofrece el expediente. */
  readonly abrirExpediente = output<void>();

  protected readonly forma = computed<Forma>(() => formaDe(this.asunto()));

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** El paso abierto dentro de la ventana. `null` = todavía no eligió nada. */
  protected readonly paso = signal<'no-realizada' | 'contacto' | null>(null);

  protected readonly motivo = signal('');
  protected readonly canal = signal('');
  protected readonly resultado = signal('');
  protected readonly nota = signal('');

  protected readonly opcionesCanal = opcionesDe(CANAL_CONTACTO);
  protected readonly opcionesResultado = resultadosDe('PROSPECCION');

  private idEntidad(): number | null {
    return this.asunto().tarea?.entidadId ?? null;
  }

  protected volver(): void {
    this.paso.set(null);
    this.error.set(null);
  }

  /** «Sí, se realizó»: un botón, una llamada, y el asunto se cierra solo. */
  protected async marcarRealizada(): Promise<void> {
    const id = this.idEntidad();
    if (id == null) {
      return;
    }
    await this.ejecutar(
      () => this.visitas.realizar(id),
      'Visita marcada como realizada.',
    );
  }

  /**
   * «No se realizó» pide motivo, y lo pide **el backend**: una visita que se
   * cae sin explicación deja la oportunidad sin nada que leer dentro de un mes.
   */
  protected async marcarNoRealizada(): Promise<void> {
    const id = this.idEntidad();
    const motivo = this.motivo().trim();
    if (id == null) {
      return;
    }
    if (!motivo) {
      this.error.set('Escribe por qué no se realizó: sin eso no queda nada que leer después.');
      return;
    }
    await this.ejecutar(
      () => this.visitas.noRealizada(id, motivo),
      'Visita marcada como no realizada.',
    );
  }

  protected async registrarContacto(): Promise<void> {
    const id = this.idEntidad();
    if (id == null) {
      return;
    }
    if (!this.canal() || !this.resultado()) {
      this.error.set('Indica por dónde contactaste y cómo salió.');
      return;
    }
    await this.ejecutar(
      () =>
        this.interacciones.registrar({
          contexto: 'PROSPECCION',
          idProspeccion: id,
          canalContacto: this.canal(),
          resultado: this.resultado(),
          observaciones: this.nota().trim() || undefined,
        }),
      'Contacto registrado.',
    );
  }

  private async ejecutar(llamada: () => Promise<unknown>, hecho: string): Promise<void> {
    this.guardando.set(true);
    this.error.set(null);
    try {
      await llamada();
      this.resuelto.emit(hecho);
    } catch (fallo) {
      // El mensaje del backend se enseña tal cual: dice mejor qué pasó que
      // cualquier frase de repuesto («La visita ya tiene un resultado…»).
      this.error.set(
        fallo instanceof ApiError ? fallo.message : 'No se pudo completar la operación.',
      );
    } finally {
      this.guardando.set(false);
    }
  }
}
