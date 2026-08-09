import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiError } from '../../core/api/api.types';
import {
  AsignacionAgente,
  AsignacionBroker,
  AsignacionesService,
  ReasignacionAgente,
} from '../../core/api/asignaciones.service';
import { SIN_DATO, texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const ESTADO_OPERATIVO: Readonly<Record<string, string>> = {
  D: 'Disponible',
  O: 'Ocupado',
  V: 'Vacaciones',
  S: 'Suspendido',
};

type Panel = 'agentes' | 'brokers' | 'historial';

/**
 * Reasignación de agentes entre brokers. **Todo el recurso es de ADMIN.**
 *
 * No confundir con la reasignación de **captaciones** (`/captaciones/reasignaciones`,
 * del broker): aquella mueve un encargo concreto de un agente a otro; esta mueve
 * a la **persona** de equipo.
 *
 * Las cuatro reglas del cable se anticipan en el formulario en vez de dejar que
 * las explique un 400, porque las cuatro se pueden comprobar con lo que ya está
 * en pantalla:
 *
 * 1. agente y broker destino obligatorios;
 * 2. **motivo no vacío** obligatorio —queda en el evento histórico, así que es
 *    lo único que explicará el cambio dentro de un año—;
 * 3. **el broker administrador no puede ser destino**: no supervisa equipos, así
 *    que se excluye del selector;
 * 4. el agente tiene que estar administrativo **ACTIVO** y operativo
 *    **DISPONIBLE**, y no puede reasignarse al broker que ya lo supervisa.
 *
 * El historial NO es una inferencia sobre la supervisión vigente: es una
 * tabla-evento con broker anterior, nuevo, autorizador y motivo grabados cuando
 * ocurrió el cambio, así que no se reescribe al mover otra vez a la persona.
 */
@Component({
  selector: 'cl-asignaciones',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './asignaciones.html',
  styleUrl: './asignaciones.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Asignaciones implements OnInit {
  private readonly api = inject(AsignacionesService);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly agentes = signal<AsignacionAgente[]>([]);
  protected readonly brokers = signal<AsignacionBroker[]>([]);
  protected readonly historial = signal<ReasignacionAgente[]>([]);
  protected readonly panel = signal<Panel>('agentes');

  protected readonly guardando = signal(false);
  protected readonly errorReasignar = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    idAgente: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    idBrokerDestino: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    motivo: this.fb.nonNullable.control('', [Validators.required, Validators.minLength(5)]),
  });

  /** Solo los reasignables: activos y disponibles (regla 4). */
  protected readonly agentesReasignables = computed(() =>
    this.agentes().filter(
      (a) => a.estadoAdministrativo === 'A' && a.estadoOperativo === 'D',
    ),
  );

  /** El administrador queda fuera del selector: no supervisa equipos (regla 3). */
  protected readonly brokersDestino = computed(() =>
    this.brokers().filter((b) => !b.esAdministrador && b.estadoAdministrativo === 'A'),
  );

  protected readonly bloqueados = computed(() =>
    this.agentes().length - this.agentesReasignables().length,
  );

  /** El broker que hoy supervisa al agente elegido; no puede ser el destino. */
  protected readonly supervisorActual = computed(() => {
    const id = this.formulario.controls.idAgente.value;
    return this.agentes().find((a) => a.idAgente === id)?.brokerActual ?? '';
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    forkJoin({
      agentes: this.api.agentes$(),
      brokers: this.api.brokers$(),
      historial: this.api.historial$(),
    }).subscribe({
      next: ({ agentes, brokers, historial }) => {
        this.agentes.set(agentes ?? []);
        this.brokers.set(brokers ?? []);
        this.historial.set(historial ?? []);
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.error.set(
          error instanceof ApiError ? error.message : 'No se pudieron cargar las asignaciones.',
        );
        this.cargando.set(false);
      },
    });
  }

  protected abrir(panel: Panel): void {
    this.panel.set(panel);
  }

  protected async reasignar(): Promise<void> {
    if (this.guardando()) return;
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.errorReasignar.set('Elige agente, broker destino y escribe el motivo.');
      return;
    }
    this.guardando.set(true);
    this.errorReasignar.set(null);
    this.exito.set(null);
    try {
      const v = this.formulario.getRawValue();
      const evento = await this.api.reasignar({
        idAgente: v.idAgente,
        idBrokerDestino: v.idBrokerDestino,
        motivo: v.motivo.trim(),
      });
      this.exito.set(
        `${textoDe(evento.agenteNombre)} pasa a ${textoDe(evento.brokerNuevoNombre)}.`,
      );
      this.formulario.reset({ idAgente: 0, idBrokerDestino: 0, motivo: '' });
      // Se recarga entero: la reasignación cambia las tres listas a la vez
      // (supervisor del agente, carga de dos brokers y el historial).
      this.cargar();
    } catch (error) {
      this.errorReasignar.set(
        error instanceof ApiError ? error.message : 'No se pudo reasignar al agente.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  protected invalido(campo: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.dirty || control.touched);
  }

  // -- presentación --------------------------------------------------------

  protected texto(valor: string | undefined | null): string {
    return textoDe(valor);
  }

  protected operativo(agente: AsignacionAgente): string {
    return ESTADO_OPERATIVO[agente.estadoOperativo ?? ''] ?? SIN_DATO;
  }

  protected reasignable(agente: AsignacionAgente): boolean {
    return agente.estadoAdministrativo === 'A' && agente.estadoOperativo === 'D';
  }

  /** Por qué un agente no aparece en el selector. */
  protected motivoBloqueo(agente: AsignacionAgente): string {
    if (agente.estadoAdministrativo !== 'A') return 'Inactivo administrativamente';
    if (agente.estadoOperativo !== 'D') return `No disponible: ${this.operativo(agente)}`;
    return '';
  }
}
