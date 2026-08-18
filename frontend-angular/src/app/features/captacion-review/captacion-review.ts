import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { describir, ESTADO_CAPTACION, RESULTADO_PROPUESTA } from '../../core/api/codigos';
import { Local, LocalesService } from '../../core/api/locales.service';
import { AgenteOpcion, PersonalService } from '../../core/api/personal.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { calcularCondicionComision, descripcionCondicionComision, importeTexto } from '../../core/comision';
import { fechaCorta, monto, numero, siNo, SIN_DATO, texto } from '../../core/formato';
import { DialogoConfirmacion, TonoAccion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

type Decision = 'A' | 'O' | 'R';

@Component({
  selector: 'app-captacion-review',
  imports: [DialogoConfirmacion, EstadoListado, ReactiveFormsModule],
  templateUrl: './captacion-review.html',
  styleUrl: './captacion-review.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaptacionReview implements OnInit {
  private readonly api = inject(CaptacionesService);
  private readonly locales = inject(LocalesService);
  private readonly prospecciones = inject(ProspeccionesService);
  private readonly personal = inject(PersonalService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly procesando = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorAccion = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly captacion = signal<Captacion | null>(null);
  protected readonly local = signal<Local | null>(null);
  protected readonly prospeccion = signal<Prospeccion | null>(null);
  protected readonly agentes = signal<AgenteOpcion[]>([]);
  protected readonly decision = signal<Decision | null>(null);
  protected readonly dialogoReasignar = signal(false);
  protected readonly observacion = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(1000)],
  });
  protected readonly agenteNuevo = new FormControl(0, {
    nonNullable: true,
    validators: [Validators.min(1)],
  });
  protected readonly motivoReasignacion = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(1000)],
  });

  protected readonly revisable = computed(() => ['P', 'O'].includes(this.captacion()?.estado ?? ''));
  protected readonly agentesDestino = computed(() =>
    this.agentes().filter((agente) => agente.id !== this.captacion()?.idAgente),
  );
  protected decisionBloqueada(): boolean {
    const decision = this.decision();
    return (decision === 'O' || decision === 'R') && !this.observacion.value.trim();
  }

  ngOnInit(): void {
    void this.cargar();
  }

  protected volver(): void {
    void this.router.navigate(['/captaciones/pendientes']);
  }

  protected expediente(): void {
    const codigo = this.captacion()?.codigoCaptacion;
    if (codigo) void this.router.navigate(['/captaciones', codigo]);
  }

  protected verLocal(): void {
    const id = this.captacion()?.idLocal;
    if (id) void this.router.navigate(['/propiedades', id]);
  }

  protected verProspeccion(): void {
    const id = this.prospeccion()?.id;
    if (id) void this.router.navigate(['/prospecciones', id]);
  }

  protected reintentar(): void {
    void this.cargar();
  }

  protected abrirDecision(decision: Decision): void {
    if (!this.revisable()) return;
    this.observacion.reset('');
    this.errorAccion.set(null);
    this.decision.set(decision);
  }

  protected cerrarDecision(): void {
    if (!this.procesando()) this.decision.set(null);
  }

  protected abrirReasignacion(): void {
    if (!this.revisable()) return;
    this.agenteNuevo.reset(0);
    this.motivoReasignacion.reset('');
    this.errorAccion.set(null);
    this.dialogoReasignar.set(true);
  }

  protected cerrarReasignacion(): void {
    if (!this.procesando()) this.dialogoReasignar.set(false);
  }

  protected async confirmarDecision(): Promise<void> {
    const captacion = this.captacion();
    const decision = this.decision();
    if (!captacion || !decision || this.procesando() || this.decisionBloqueada()) return;
    await this.ejecutar(
      () => this.api.decidir(captacion.id, decision, textoOpcional(this.observacion.value)),
      mensajeDecision(decision),
    );
    if (!this.errorAccion()) this.decision.set(null);
  }

  protected async confirmarReasignacion(): Promise<void> {
    const captacion = this.captacion();
    const idAgente = this.agenteNuevo.value;
    const motivo = this.motivoReasignacion.value.trim();
    if (!captacion || this.procesando()) return;
    if (idAgente <= 0 || !motivo) {
      this.agenteNuevo.markAsTouched();
      this.motivoReasignacion.markAsTouched();
      return;
    }
    await this.ejecutar(
      () => this.api.reasignar(captacion.id, idAgente, motivo),
      'Captación reasignada. La revisión sigue pendiente.',
    );
    if (!this.errorAccion()) this.dialogoReasignar.set(false);
  }

  protected tituloDecision(): string {
    return ({ A: 'Aprobar captación', O: 'Observar y devolver', R: 'Rechazar captación' } as Record<string, string>)[this.decision() ?? ''] ?? '';
  }

  protected descripcionDecision(): string {
    return ({
      A: 'La captación quedará activa y disponible para el proceso comercial.',
      O: 'El agente deberá corregir las observaciones antes de reenviar.',
      R: 'El expediente quedará rechazado y la causa se registrará en la trazabilidad.',
    } as Record<string, string>)[this.decision() ?? ''] ?? '';
  }

  protected tonoDecision(): TonoAccion {
    return ({ A: 'verde', O: 'ambar', R: 'rojo' } as Record<string, TonoAccion>)[this.decision() ?? ''] ?? 'azul';
  }

  protected etiquetaConfirmar(): string {
    return ({ A: 'Aprobar', O: 'Enviar observación', R: 'Rechazar' } as Record<string, string>)[this.decision() ?? ''] ?? 'Confirmar';
  }

  protected etiquetaEstado(): string {
    return describir(ESTADO_CAPTACION, this.captacion()?.estado) || SIN_DATO;
  }

  protected tonoEstado(): string {
    const estado = this.captacion()?.estado;
    if (estado === 'A') return 'bien';
    if (estado === 'R') return 'mal';
    return 'aviso';
  }

  protected valor(valor: string | undefined): string { return texto(valor); }
  protected fecha(valor: string | undefined): string { return fechaCorta(valor); }
  protected area(valor: number | undefined): string { return valor === undefined ? SIN_DATO : `${numero(valor)} m²`; }
  protected precio(valor: number | undefined, moneda: string | undefined): string { return monto(valor, moneda); }
  protected comision(): string { return descripcionCondicionComision(this.captacion()); }
  protected importeComision(): string {
    return importeTexto(calcularCondicionComision(this.captacion()));
  }
  protected exclusivo(valor: boolean | undefined): string { return siNo(valor); }
  protected resultadoPropuesta(valor: string | undefined): string {
    return describir(RESULTADO_PROPUESTA, valor) || SIN_DATO;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.errorAccion.set(null);
    this.mensaje.set(null);
    try {
      const codigo = this.route.snapshot.paramMap.get('codigo');
      if (!codigo) throw new Error('El código de la captación es obligatorio.');
      const [captacion, paginaAgentes] = await Promise.all([
        this.api.obtenerPorCodigo(codigo),
        firstValueFrom(this.personal.agentes$()),
      ]);
      this.captacion.set(captacion);
      this.agentes.set(paginaAgentes.items);
      const [local, paginaProspeccion] = await Promise.all([
        captacion.idLocal ? this.locales.obtener(captacion.idLocal) : Promise.resolve(null),
        this.prospecciones.pagina({ idCaptacion: captacion.id, pagina: 1, tamano: 1 }),
      ]);
      this.local.set(local);
      this.prospeccion.set(paginaProspeccion.items[0] ?? null);
    } catch (error) {
      this.captacion.set(null);
      this.errorCarga.set(mensajeError(error, 'No se pudo cargar el expediente para revisión.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private async ejecutar(operacion: () => Promise<Captacion>, mensaje: string): Promise<void> {
    this.procesando.set(true);
    this.errorAccion.set(null);
    this.mensaje.set(null);
    try {
      this.captacion.set(await operacion());
      this.mensaje.set(mensaje);
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudo registrar la decisión.'));
    } finally {
      this.procesando.set(false);
    }
  }
}

function mensajeDecision(decision: Decision): string {
  return ({
    A: 'Captación aprobada. Ya está activa.',
    O: 'Captación observada. El agente debe subsanarla y reenviarla.',
    R: 'Captación rechazada. La decisión quedó registrada.',
  } as Record<Decision, string>)[decision];
}

function textoOpcional(valor: string): string | null {
  const limpio = valor.trim();
  return limpio || null;
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
