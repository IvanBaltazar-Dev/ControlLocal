import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import {
  describir,
  ESTADO_COMISION,
  ESTADO_CONTRATO,
  ESTADO_CONTRATO_AL_CERRAR,
  ESTADO_DOCUMENTO,
  ESTADO_SOLICITUD,
  FORMA_PAGO,
  opcionesDe,
  RESULTADO_EVALUACION,
  TIPO_DOCUMENTO_SOLICITUD,
  TIPO_EVALUACION,
} from '../../core/api/codigos';
import { Contrato, ContratosService } from '../../core/api/contratos.service';
import {
  DocumentoSolicitud,
  Evaluacion,
  Solicitud,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { AuthService } from '../../core/auth/auth.service';
import {
  calcularCondicionComision,
  comisionSobreRenta,
  descripcionCondicionComision,
  desembolsoInicial,
  Importe,
  importeTexto,
} from '../../core/comision';
import { fechaCorta, fechaHora, monto as montoDe, SIN_DATO, texto as textoDe } from '../../core/formato';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { OpcionFiltro } from '../../shared/filtro-select/filtro-select';
import { VisorDocumento } from '../../shared/visor-documento/visor-documento';

/** Las tres etapas visibles del circuito de la solicitud. */
interface Etapa {
  readonly titulo: string;
  readonly detalle: string;
}

const ETAPAS: readonly Etapa[] = [
  { titulo: 'Etapa 1', detalle: 'Registrada' },
  { titulo: 'Etapa 2', detalle: 'En revisión' },
  { titulo: 'Etapa 3', detalle: 'Resuelta' },
];

/**
 * Expediente de la solicitud: condiciones, propiedad, documentos, historial de
 * evaluaciones y —cuando corresponde— **el cierre del alquiler**.
 *
 * Cuatro cosas que conviene entender antes de tocarla:
 *
 * - **Solo la solicitud es fatal**: documentos, evaluaciones, captación y
 *   contrato son bloques complementarios y degradan con su propio aviso. Un
 *   fallo leyendo el historial no puede esconder el expediente entero.
 * - **El cierre vive aquí y no en una pantalla propia**, igual que en el
 *   legado: es la continuación natural de una solicitud aprobada, y sacarlo a
 *   un silo obligaría a volver a elegir la solicitud que ya se está mirando.
 * - **`POST /contratos` es una transacción con siete efectos**: crea contrato y
 *   comisión, cierra oportunidad, solicitud y captación, deja el local no
 *   disponible y resuelve tareas. Por eso el diálogo lo dice antes de confirmar
 *   y por eso no existe un botón de "cerrar oportunidad exitosa" en ninguna
 *   pantalla: ese cierre lo produce esto.
 * - **La comisión que se muestra antes de cerrar es una ESTIMACIÓN** calculada
 *   con la misma fórmula del backend (`renta × comisionPactada / 100`). La
 *   liquidación real la escribe el cierre; si la captación no se pudo leer, no
 *   se inventa un número.
 */
@Component({
  selector: 'app-solicitud-detail',
  imports: [DialogoConfirmacion, EstadoListado, ReactiveFormsModule, VisorDocumento],
  templateUrl: './solicitud-detail.html',
  styleUrl: './solicitud-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitudDetail implements OnInit {
  private readonly api = inject(SolicitudesService);
  private readonly contratosApi = inject(ContratosService);
  private readonly captacionesApi = inject(CaptacionesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly solicitud = signal<Solicitud | null>(null);

  protected readonly documentos = signal<readonly DocumentoSolicitud[]>([]);
  protected readonly errorDocumentos = signal<string | null>(null);
  protected readonly evaluaciones = signal<readonly Evaluacion[]>([]);
  protected readonly errorEvaluaciones = signal<string | null>(null);
  protected readonly captacion = signal<Captacion | null>(null);
  protected readonly errorCaptacion = signal<string | null>(null);
  protected readonly contrato = signal<Contrato | null>(null);
  protected readonly errorContrato = signal<string | null>(null);

  protected readonly documentoAbierto = signal<DocumentoSolicitud | null>(null);
  protected readonly confirmarCierre = signal(false);
  protected readonly cerrando = signal(false);
  protected readonly errorAccion = signal<string | null>(null);
  protected readonly cerrado = signal(false);

  protected readonly etapas = ETAPAS;
  protected readonly opcionesEstadoContrato: OpcionFiltro[] = opcionesDe(ESTADO_CONTRATO_AL_CERRAR);

  /** Formalización del cierre: lo único editable del formulario. */
  protected readonly formularioCierre = new FormGroup({
    fechaCierre: new FormControl(hoy(), { nonNullable: true, validators: [Validators.required] }),
    estadoContrato: new FormControl('V', { nonNullable: true, validators: [Validators.required] }),
    incidencias: new FormControl('', { nonNullable: true }),
  });

  /** Reflejo reactivo: un `computed()` sobre `.value` no se recalcularía. */
  private readonly valoresCierre = toSignal(this.formularioCierre.valueChanges, {
    initialValue: this.formularioCierre.getRawValue(),
  });

  protected readonly esAgente = computed(() => this.auth.sesion()?.rol === 'AGENTE');

  /**
   * Se puede cerrar el alquiler cuando la solicitud está APROBADA y todavía no
   * hay contrato. El resto de precondiciones —oportunidad abierta— las impone
   * el backend; aquí no se replican para no inventar reglas.
   */
  protected readonly puedeCerrar = computed(
    () => this.esAgente() && this.solicitud()?.estado === 'A' && this.contrato() === null,
  );

  protected readonly hoy = hoy();

  /** Etapa actual de la barra: registrada → en revisión → resuelta. */
  protected readonly etapaActual = computed(() => {
    const estado = this.solicitud()?.estado;
    if (estado === 'G') return 0;
    if (estado === 'E' || estado === 'O') return 1;
    return 2;
  });

  protected readonly renta = computed<Importe | null>(() => {
    const s = this.solicitud();
    return s?.montoPropuesto && s.moneda ? { valor: s.montoPropuesto, moneda: s.moneda } : null;
  });

  /**
   * Estimación de pantalla. Manda la **condición tipada** de la captación; el
   * `comisionPactada` histórico queda solo como respaldo, igual que en la ficha
   * de la propiedad. Null si falta el dato: no se inventa un número.
   */
  protected readonly comisionEstimada = computed<Importe | null>(
    () =>
      calcularCondicionComision(this.captacion()) ??
      comisionSobreRenta(this.captacion()?.comisionPactada, this.renta()),
  );

  /** "Un mes de alquiler" o, si no es redonda, el porcentaje sobre la renta. */
  protected readonly comisionEnPalabras = computed(() =>
    descripcionCondicionComision(this.captacion()),
  );

  /**
   * Lo que el inquilino desembolsa al entrar, **concepto por concepto**:
   * garantía y adelanto son del propietario, la comisión de la inmobiliaria.
   * Sumarlos en un solo número es lo que hace creer que todo va al propietario.
   */
  protected readonly desembolso = computed(() => {
    const renta = this.renta();
    const s = this.solicitud();
    if (!renta || !s) {
      return null;
    }
    return desembolsoInicial({
      renta,
      mesesGarantia: s.mesesGarantia,
      mesesAdelanto: s.mesesAdelanto,
      comisionPactada: this.captacion()?.comisionPactada,
    });
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
      await this.cargarComplementos(solicitud);
    } catch (error) {
      this.error.set(mensajeError(error, 'No se pudo cargar la solicitud.'));
    } finally {
      this.cargando.set(false);
    }
  }

  protected abrir(documento: DocumentoSolicitud): void {
    if (documento.rutaArchivo) {
      this.documentoAbierto.set(documento);
    }
  }

  protected cerrarVisor(): void {
    this.documentoAbierto.set(null);
  }

  protected gestionarDocumentos(): void {
    const codigo = this.solicitud()?.codigoSolicitud;
    if (codigo) void this.router.navigate(['/solicitudes', codigo, 'documentos']);
  }

  protected verOportunidad(): void {
    const id = this.solicitud()?.idOportunidad;
    if (id) void this.router.navigate(['/oportunidades', id]);
  }

  protected verCliente(): void {
    const id = this.solicitud()?.idCliente;
    if (id) void this.router.navigate(['/clientes', id]);
  }

  protected verPropiedad(): void {
    const codigo = this.solicitud()?.codigoCaptacion;
    if (codigo) void this.router.navigate(['/captaciones', codigo, 'ficha']);
  }

  protected verCierres(): void {
    void this.router.navigate(['/propiedades-alquiladas']);
  }

  protected volver(): void {
    void this.router.navigate(['/solicitudes']);
  }

  protected pedirCierre(): void {
    if (!this.puedeCerrar()) return;
    this.errorAccion.set(null);
    this.formularioCierre.reset({ fechaCierre: hoy(), estadoContrato: 'V', incidencias: '' });
    this.confirmarCierre.set(true);
  }

  protected cancelarCierre(): void {
    this.confirmarCierre.set(false);
  }

  /** Fecha de cierre futura: el backend la rechaza, así que se avisa antes. */
  protected readonly cierreInvalido = computed(() => {
    const valores = this.valoresCierre();
    const fecha = (valores.fechaCierre ?? '').trim();
    return !fecha || fecha > this.hoy || !valores.estadoContrato;
  });

  protected async cerrarAlquiler(): Promise<void> {
    const solicitud = this.solicitud();
    if (!solicitud || this.cerrando() || this.cierreInvalido()) return;
    this.cerrando.set(true);
    this.errorAccion.set(null);
    try {
      const datos = this.formularioCierre.getRawValue();
      const creado = await this.contratosApi.registrar({
        idSolicitud: solicitud.id,
        fechaCierre: datos.fechaCierre,
        estadoContrato: datos.estadoContrato,
        incidencias: datos.incidencias.trim() || undefined,
      });
      this.contrato.set(creado);
      this.confirmarCierre.set(false);
      this.cerrado.set(true);
      // La cascada movió la solicitud a CERRADA y cerró la oportunidad: se
      // recarga para que el expediente no siga mostrando el estado anterior.
      // Va en su propio try: el contrato YA está registrado, y fallar al
      // releerlo no puede presentarse como si el cierre no hubiera ocurrido.
      try {
        const codigo = solicitud.codigoSolicitud;
        if (codigo) {
          this.solicitud.set(await this.api.porCodigo(codigo));
        }
      } catch {
        // El aviso de éxito ya está puesto; el estado se verá al recargar.
      }
    } catch (error) {
      this.confirmarCierre.set(false);
      this.errorAccion.set(mensajeError(error, 'No se pudo registrar el contrato.'));
    } finally {
      this.cerrando.set(false);
    }
  }

  // ---------------- Presentación ----------------

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_SOLICITUD, codigo) || SIN_DATO;
  }

  protected tonoEstado(codigo: string | undefined): string {
    if (codigo === 'A' || codigo === 'C') return 'bien';
    if (codigo === 'R' || codigo === 'D') return 'mal';
    return 'aviso';
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

  protected etiquetaEstadoContrato(codigo: string | undefined): string {
    return describir(ESTADO_CONTRATO, codigo) || SIN_DATO;
  }

  protected etiquetaEstadoComision(codigo: string | undefined): string {
    return codigo ? describir(ESTADO_COMISION, codigo) || codigo : SIN_DATO;
  }

  protected importe(valor: Importe | null): string {
    return importeTexto(valor);
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

  protected checklist(s: Solicitud): string {
    return `${s.documentosEntregados ?? 0}/${s.documentosRequeridos ?? 0}`;
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

  /**
   * Los cuatro bloques complementarios, cada uno con su propio fallo. El
   * contrato merece un trato aparte: **su 404 es el caso normal** mientras la
   * operación sigue viva, así que no se muestra como error.
   */
  private async cargarComplementos(solicitud: Solicitud): Promise<void> {
    await Promise.all([
      this.cargarDocumentos(solicitud.id),
      this.cargarEvaluaciones(solicitud.id),
      this.cargarCaptacion(solicitud.idCaptacion),
      this.cargarContrato(solicitud.idOportunidad),
    ]);
  }

  private async cargarDocumentos(id: number): Promise<void> {
    try {
      this.documentos.set(await this.api.documentos(id));
      this.errorDocumentos.set(null);
    } catch (error) {
      this.documentos.set([]);
      this.errorDocumentos.set(mensajeError(error, 'No se pudieron leer los documentos.'));
    }
  }

  private async cargarEvaluaciones(id: number): Promise<void> {
    try {
      this.evaluaciones.set(await this.api.evaluaciones(id));
      this.errorEvaluaciones.set(null);
    } catch (error) {
      this.evaluaciones.set([]);
      this.errorEvaluaciones.set(mensajeError(error, 'No se pudo leer el historial.'));
    }
  }

  /** La captación aporta la comisión pactada, que es lo que da la estimación. */
  private async cargarCaptacion(idCaptacion: number | undefined): Promise<void> {
    if (!idCaptacion) {
      this.captacion.set(null);
      return;
    }
    try {
      this.captacion.set(await this.captacionesApi.obtener(idCaptacion));
      this.errorCaptacion.set(null);
    } catch (error) {
      this.captacion.set(null);
      this.errorCaptacion.set(
        mensajeError(error, 'No se pudo leer la captación: la comisión estimada no se muestra.'),
      );
    }
  }

  private async cargarContrato(idOportunidad: number | undefined): Promise<void> {
    if (!idOportunidad) {
      this.contrato.set(null);
      return;
    }
    try {
      this.contrato.set(await this.contratosApi.porOportunidad(idOportunidad));
      this.errorContrato.set(null);
    } catch (error) {
      this.contrato.set(null);
      // 404 = todavía no hay contrato, que es lo normal. Solo el resto es error.
      this.errorContrato.set(
        error instanceof ApiError && error.noEncontrado
          ? null
          : mensajeError(error, 'No se pudo comprobar si el alquiler ya está cerrado.'),
      );
    }
  }
}

function hoy(): string {
  return new Date().toISOString().slice(0, 10);
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
