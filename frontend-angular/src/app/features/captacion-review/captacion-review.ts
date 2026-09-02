import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  CandidatoAgente,
  Captacion,
  CaptacionesService,
} from '../../core/api/captaciones.service';
import { describir, ESTADO_CAPTACION, RESULTADO_PROPUESTA } from '../../core/api/codigos';
import { Local, LocalesService } from '../../core/api/locales.service';
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
  /**
   * **Los destinos que el Core ofrece para ESTE encargo** (D-P0-7 + D-P0-12).
   *
   * Hasta el 2026-09-01 esta pantalla pedía `GET /agentes` y se quedaba con
   * «todos menos el actual»: **ninguna** de las seis condiciones de
   * elegibilidad, así que el `<select>` ofrecía agentes suspendidos, de baja o
   * del equipo de otro bróker, y el rechazo llegaba después de escribir el
   * motivo. Ahora la lista llega resuelta y aquí no se depura nada.
   */
  protected readonly candidatos = signal<CandidatoAgente[]>([]);
  protected readonly cargandoCandidatos = signal(false);
  protected readonly errorCandidatos = signal<string | null>(null);
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
  /**
   * **Si el Core deja mover este encargo a otro agente** (D-P0-12).
   *
   * Viaja resuelta en la ficha individual —esta pantalla la pide por código— y
   * la produce **el mismo predicado que después deniega el comando**. Su
   * ausencia significa «no calculado aquí», y el defecto es **no ofrecer**: un
   * botón activo que el backend rechaza aparece cuando la persona ya escribió
   * el motivo.
   *
   * Convive con `revisable()` sin mezclarse: aquélla dice en qué **momento**
   * del flujo de revisión está esta pantalla —y sólo puede ofrecer de menos—,
   * ésta dice **quién puede**, y es la única de las dos que es autoridad.
   */
  protected readonly puedeReasignar = computed(
    () => this.captacion()?.capacidades?.puedeReasignar === true,
  );
  protected readonly agentesDestino = computed(() => this.candidatos());
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
    if (!this.revisable() || !this.puedeReasignar()) return;
    this.agenteNuevo.reset(0);
    this.motivoReasignacion.reset('');
    this.errorAccion.set(null);
    this.dialogoReasignar.set(true);
    // Los destinos se piden al ABRIR y no al cargar la pantalla: entre una cosa
    // y otra un agente puede quedar desactivado, y lo que tiene que estar al
    // día es la lista que se va a usar. El POST revalida igualmente.
    void this.cargarCandidatos();
  }

  /**
   * **A quién puede pasarle este encargo**, resuelto por el Core.
   *
   * Un **403** aquí no es «no hay candidatos» sino «no te corresponde», y se
   * muestra el mensaje del Core: una lista vacía dejaría a alguien buscando un
   * agente que no existe.
   */
  private async cargarCandidatos(): Promise<void> {
    const captacion = this.captacion();
    if (!captacion) return;
    this.cargandoCandidatos.set(true);
    this.errorCandidatos.set(null);
    try {
      const pagina = await this.api.candidatosReasignacion(captacion.id);
      this.candidatos.set(pagina.items);
    } catch (error) {
      this.candidatos.set([]);
      this.errorCandidatos.set(
        mensajeError(error, 'No se pudieron cargar los destinos de esta captación.'),
      );
    } finally {
      this.cargandoCandidatos.set(false);
    }
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

  /**
   * **Declara sobre qué agente se actúa** (D-P0-9).
   *
   * `idAgenteActual` es el agente que esta pantalla estaba **mostrando**. Si
   * otro bróker lo movió mientras tanto, el Core responde **409** y no escribe
   * nada; entonces se recarga el expediente para que la siguiente decisión
   * parta de quien lo lleva ahora, en vez de reintentar sobre un estado que ya
   * no existe.
   */
  protected async confirmarReasignacion(): Promise<void> {
    const captacion = this.captacion();
    const idAgente = this.agenteNuevo.value;
    const motivo = this.motivoReasignacion.value.trim();
    if (!captacion || this.procesando()) return;
    const observado = captacion.idAgente;
    if (idAgente <= 0 || !motivo || observado == null) {
      this.agenteNuevo.markAsTouched();
      this.motivoReasignacion.markAsTouched();
      return;
    }
    await this.ejecutar(
      () => this.api.reasignar(captacion.id, idAgente, motivo, observado),
      'Captación reasignada. La revisión sigue pendiente.',
    );
    if (!this.errorAccion()) {
      this.dialogoReasignar.set(false);
    } else if (this.ultimoFallo instanceof ApiError && this.ultimoFallo.conflicto) {
      // Las dos cosas, y en este orden: recargar deja ver quién lo lleva ahora,
      // y el mensaje se vuelve a poner DESPUÉS porque `cargar()` limpia los
      // errores de acción — sin esto la pantalla se refrescaba en silencio y
      // quien reasignó no llegaba a enterarse de por qué no se hizo.
      const porQue = this.errorAccion();
      this.dialogoReasignar.set(false);
      await this.cargar();
      this.errorAccion.set(porQue);
    }
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
      // Ya no se piden los agentes del tenant: los destinos válidos de ESTE
      // encargo los resuelve el Core cuando se abre la reasignación.
      const captacion = await this.api.obtenerPorCodigo(codigo);
      this.captacion.set(captacion);
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

  /**
   * El último error de una acción, **con su tipo**. El texto ya viaja en
   * `errorAccion`, pero un 409 hay que distinguirlo de un rechazo cualquiera:
   * no significa «no se pudo», significa «el estado que veías ya no es», y eso
   * obliga a recargar en vez de reintentar.
   */
  private ultimoFallo: unknown = null;

  private async ejecutar(operacion: () => Promise<Captacion>, mensaje: string): Promise<void> {
    this.procesando.set(true);
    this.errorAccion.set(null);
    this.mensaje.set(null);
    this.ultimoFallo = null;
    try {
      this.captacion.set(await operacion());
      this.mensaje.set(mensaje);
    } catch (error) {
      this.ultimoFallo = error;
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
