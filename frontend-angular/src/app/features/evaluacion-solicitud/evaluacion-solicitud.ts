import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  describir,
  ESTADO_DOCUMENTO,
  ESTADO_SOLICITUD,
  FORMA_PAGO,
  RESULTADO_EVALUACION,
  TIPO_DOCUMENTO_SOLICITUD,
  TIPO_EVALUACION,
} from '../../core/api/codigos';
import { EvaluacionesService } from '../../core/api/evaluaciones.service';
import {
  DocumentoSolicitud,
  Evaluacion,
  Solicitud,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, fechaHora, monto as montoDe, SIN_DATO, texto as textoDe } from '../../core/formato';
import { DialogoConfirmacion, TonoAccion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { VisorDocumento } from '../../shared/visor-documento/visor-documento';

/** Las tres decisiones del broker. `A` y `R` son finales; `O` devuelve. */
type Decision = 'A' | 'O' | 'R';

/**
 * Pantalla de decisión del broker sobre una solicitud: leer las condiciones,
 * revisar documento por documento y aprobar, observar o rechazar.
 *
 * Cinco reglas que la pantalla hace visibles antes de que el backend responda:
 *
 * - **El tipo de evaluación no se elige**: lo deriva el resultado (`O` ⇒
 *   observación, `A`/`R` ⇒ final). Por eso no hay selector de tipo.
 * - **Solo cabe una evaluación FINAL por solicitud.** Aprobar o rechazar cierra
 *   la decisión; el segundo intento responde 400.
 * - **La observación es obligatoria al observar y al rechazar**, y el diálogo lo
 *   bloquea en vez de dejar que lo diga el servidor.
 * - **No se aprueba con documentos observados sin resolver.** Es una regla de la
 *   casa —el backend no la impone—, y es la que evita aprobar una solicitud
 *   cuya propia revisión dijo que estaba mal. Se ofrece la salida: validarlos u
 *   observar la solicitud entera para devolverla.
 * - **La revisión de un documento comprueba el alcance del broker** (D-F4-5):
 *   fuera de su equipo responde 403, no 200 como la v1.
 */
@Component({
  selector: 'app-evaluacion-solicitud',
  imports: [DialogoConfirmacion, EstadoListado, ReactiveFormsModule, VisorDocumento],
  templateUrl: './evaluacion-solicitud.html',
  styleUrl: './evaluacion-solicitud.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EvaluacionSolicitud implements OnInit {
  private readonly api = inject(SolicitudesService);
  private readonly evaluacionesApi = inject(EvaluacionesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly solicitud = signal<Solicitud | null>(null);
  protected readonly documentos = signal<readonly DocumentoSolicitud[]>([]);
  protected readonly historial = signal<readonly Evaluacion[]>([]);

  protected readonly procesando = signal(false);
  protected readonly errorAccion = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly decision = signal<Decision | null>(null);
  protected readonly documentoAbierto = signal<DocumentoSolicitud | null>(null);
  protected readonly documentoAObservar = signal<DocumentoSolicitud | null>(null);

  protected readonly observaciones = new FormControl('', { nonNullable: true });
  protected readonly notaDocumento = new FormControl('', { nonNullable: true });

  /**
   * Reflejo reactivo de los dos textos. Un `computed()` que lea
   * `FormControl.value` **no** se recalcula al escribir: ese fallo dejó un botón
   * bloqueado con el motivo ya escrito en la agenda de visitas (F3).
   */
  private readonly textoObservaciones = toSignal(this.observaciones.valueChanges, {
    initialValue: '',
  });
  private readonly textoNota = toSignal(this.notaDocumento.valueChanges, { initialValue: '' });

  /**
    * D-S0-17 fila 13, la más sensible de las 18: la evaluación desemboca en
    * contrato y comisión, así que la firma el broker. El TENANT_ADMIN puede
    * auditar lo firmado, no firmarlo.
    */
  protected readonly puedeDecidir = computed(() => this.auth.sesion()?.rol === 'BROKER');

  /** Cargados y todavía sin revisar: lo que "validar todos" dejaría conformes. */
  protected readonly sinRevisar = computed(() =>
    this.documentos().filter((d) => !!d.rutaArchivo && d.estado === 'R'),
  );

  /** Hallazgos reales sin resolver. Bloquean la aprobación. */
  protected readonly observados = computed(() => this.documentos().filter((d) => d.estado === 'O'));

  protected readonly bloqueaAprobar = computed(() => this.observados().length > 0);

  /** Observar y rechazar exigen motivo; aprobar admite nota opcional. */
  protected readonly faltaMotivo = computed(() => {
    const decision = this.decision();
    return (decision === 'O' || decision === 'R') && !this.textoObservaciones().trim();
  });

  protected readonly confirmacionBloqueada = computed(
    () => this.faltaMotivo() || (this.decision() === 'A' && this.bloqueaAprobar()),
  );

  protected readonly tituloDecision = computed(() => {
    switch (this.decision()) {
      case 'A':
        return '¿Aprobar esta solicitud?';
      case 'O':
        return 'Observar y devolver al agente';
      case 'R':
        return 'Rechazar esta solicitud';
      default:
        return '';
    }
  });

  protected readonly descripcionDecision = computed(() => {
    switch (this.decision()) {
      case 'A':
        return 'Quedará aprobada y el agente podrá registrar el contrato de alquiler.';
      case 'O':
        return 'Vuelve al agente para que subsane. Indica qué debe corregir.';
      case 'R':
        return 'La decisión es final y queda registrada en la trazabilidad.';
      default:
        return '';
    }
  });

  protected readonly tonoDecision = computed<TonoAccion>(() => {
    switch (this.decision()) {
      case 'A':
        return 'verde';
      case 'O':
        return 'ambar';
      default:
        return 'rojo';
    }
  });

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    const codigo = (this.route.snapshot.paramMap.get('codigo') ?? '').trim();
    if (!codigo) {
      this.error.set('No se indicó la solicitud.');
      this.cargando.set(false);
      return;
    }
    try {
      const solicitud = await this.api.porCodigo(codigo);
      this.solicitud.set(solicitud);
      await this.recargarExpediente(solicitud.id);
    } catch (error) {
      this.error.set(mensajeError(error, 'No se pudo cargar la solicitud.'));
    } finally {
      this.cargando.set(false);
    }
  }

  // ---------------- Revisión documento a documento ----------------

  protected abrir(documento: DocumentoSolicitud): void {
    if (documento.rutaArchivo) {
      this.documentoAbierto.set(documento);
    }
  }

  protected cerrarVisor(): void {
    this.documentoAbierto.set(null);
  }

  /** Conforme: valida el documento y **borra** la observación previa. */
  protected async validar(documento: DocumentoSolicitud): Promise<void> {
    await this.revisar(documento, 'C');
  }

  protected pedirObservacionDocumento(documento: DocumentoSolicitud): void {
    this.notaDocumento.setValue('');
    this.errorAccion.set(null);
    this.documentoAObservar.set(documento);
  }

  protected cancelarObservacionDocumento(): void {
    this.documentoAObservar.set(null);
  }

  protected get notaVacia(): boolean {
    return !this.textoNota().trim();
  }

  protected async confirmarObservacionDocumento(): Promise<void> {
    const documento = this.documentoAObservar();
    if (!documento || this.notaVacia) return;
    await this.revisar(documento, 'O', this.notaDocumento.value.trim());
    this.documentoAObservar.set(null);
  }

  /** "Validar todos": deja conformes los pendientes, sin tocar los observados. */
  protected async validarTodos(): Promise<void> {
    const solicitud = this.solicitud();
    if (!solicitud || this.procesando() || !this.puedeDecidir()) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    try {
      this.documentos.set(await this.api.conformarDocumentos(solicitud.id));
      this.aviso.set('Documentos sin revisar validados.');
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudieron validar los documentos.'));
    } finally {
      this.procesando.set(false);
    }
  }

  // ---------------- Decisión sobre la solicitud ----------------

  protected pedirDecision(decision: Decision): void {
    if (!this.puedeDecidir()) return;
    this.observaciones.setValue('');
    this.errorAccion.set(null);
    this.decision.set(decision);
  }

  protected cancelarDecision(): void {
    this.decision.set(null);
  }

  protected async confirmarDecision(): Promise<void> {
    const solicitud = this.solicitud();
    const decision = this.decision();
    if (!solicitud || !decision || this.procesando() || this.confirmacionBloqueada()) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    try {
      await this.evaluacionesApi.registrar(
        solicitud.id,
        decision,
        this.observaciones.value.trim() || undefined,
      );
      this.decision.set(null);
      // La evaluación mueve la solicitud en la misma transacción, así que no
      // hay un segundo POST de estado: se vuelve a la cola ya actualizada.
      void this.router.navigate(['/solicitudes/revisar']);
    } catch (error) {
      this.decision.set(null);
      this.errorAccion.set(mensajeError(error, 'No se pudo registrar la evaluación.'));
    } finally {
      this.procesando.set(false);
    }
  }

  protected volver(): void {
    void this.router.navigate(['/solicitudes/revisar']);
  }

  protected verExpediente(): void {
    const codigo = this.solicitud()?.codigoSolicitud;
    if (codigo) void this.router.navigate(['/solicitudes', codigo]);
  }

  // ---------------- Presentación ----------------

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_SOLICITUD, codigo) || SIN_DATO;
  }

  protected etiquetaDocumento(documento: DocumentoSolicitud): string {
    return describir(ESTADO_DOCUMENTO, documento.estado) || SIN_DATO;
  }

  protected tonoDocumento(documento: DocumentoSolicitud): string {
    if (documento.estado === 'V') return 'bien';
    if (documento.estado === 'O') return 'mal';
    return 'aviso';
  }

  protected nombreTipo(documento: DocumentoSolicitud): string {
    return (
      documento.tipoNombre?.trim() ||
      describir(TIPO_DOCUMENTO_SOLICITUD, documento.tipoDocumento) ||
      SIN_DATO
    );
  }

  protected etiquetaResultado(codigo: string | undefined): string {
    return describir(RESULTADO_EVALUACION, codigo) || SIN_DATO;
  }

  protected etiquetaTipoEvaluacion(codigo: string | undefined): string {
    return describir(TIPO_EVALUACION, codigo) || SIN_DATO;
  }

  protected etiquetaFormaPago(codigo: string | undefined): string {
    return codigo ? describir(FORMA_PAGO, codigo) || codigo : SIN_DATO;
  }

  protected monto(valor: number | undefined, moneda: string | undefined): string {
    return montoDe(valor, moneda);
  }

  protected meses(valor: number | undefined): string {
    return valor === null || valor === undefined ? SIN_DATO : `${valor} mes(es)`;
  }

  protected plazo(s: Solicitud): string {
    if (s.plazoMeses && s.plazoMeses > 0) {
      return `${s.plazoMeses} meses`;
    }
    return textoDe(s.plazoTentativo);
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaCorta(valor) : SIN_DATO;
  }

  protected momento(valor: string | undefined): string {
    return valor ? fechaHora(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private async revisar(
    documento: DocumentoSolicitud,
    resultado: string,
    nota?: string,
  ): Promise<void> {
    const solicitud = this.solicitud();
    if (!solicitud || this.procesando() || !this.puedeDecidir()) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    try {
      await this.api.revisarDocumento(solicitud.id, documento.id, resultado, nota);
      await this.recargarExpediente(solicitud.id);
      this.aviso.set(
        resultado === 'C'
          ? `«${this.nombreTipo(documento)}» validado.`
          : `«${this.nombreTipo(documento)}» observado. El agente debe subsanarlo.`,
      );
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudo revisar el documento.'));
    } finally {
      this.procesando.set(false);
    }
  }

  /**
   * Documentos e historial se leen juntos. El historial es complementario: si
   * falla, la decisión sigue siendo posible, así que no tumba la pantalla.
   */
  private async recargarExpediente(idSolicitud: number): Promise<void> {
    this.documentos.set(await this.api.documentos(idSolicitud));
    try {
      this.historial.set(await this.api.evaluaciones(idSolicitud));
    } catch {
      this.historial.set([]);
    }
  }
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
