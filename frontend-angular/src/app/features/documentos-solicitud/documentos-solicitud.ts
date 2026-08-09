import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import {
  describir,
  ESTADO_DOCUMENTO,
  ESTADO_SOLICITUD,
  TIPO_DOCUMENTO_SOLICITUD,
  TIPOS_REQUERIDOS,
} from '../../core/api/codigos';
import {
  DocumentoSolicitud,
  Solicitud,
  SOLICITUD_REENVIABLE,
  SOLICITUD_RESUELTA,
  SolicitudesService,
} from '../../core/api/solicitudes.service';
import { ArchivoPreparado } from '../../core/archivos/archivos.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaHora, SIN_DATO, texto as textoDe } from '../../core/formato';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';
import { SubidaArchivo } from '../../shared/subida-archivo/subida-archivo';
import { VisorDocumento } from '../../shared/visor-documento/visor-documento';

/** Una fila del checklist: el tipo requerido y el documento real, si existe. */
export interface FilaDocumento {
  readonly tipo: string;
  readonly nombre: string;
  readonly documento: DocumentoSolicitud | null;
}

/**
 * Expediente documental de la solicitud, del lado del **agente**: cargar los
 * seis documentos requeridos, subsanar los que el broker observó y enviar a
 * evaluación.
 *
 * Cuatro reglas que la pantalla hace visibles en vez de dejar que las explique
 * un 400:
 *
 * - **El checklist tiene seis filas, no ocho.** Los otros dos tipos del cable
 *   —poder de representación y "otro"— se pueden subir pero no cuentan, así que
 *   pedirlos en una lista de "requeridos" sería mentir sobre el avance.
 * - **El estado del documento no lo escribe el agente**: nace `Registrado` y
 *   solo la revisión del broker lo deja `Validado` u `Observado`. Volver a
 *   subir uno observado lo devuelve a `Registrado`, que es exactamente lo que
 *   significa subsanar.
 * - **Solo se (re)envía desde REGISTRADA u OBSERVADA.** En revisión queda
 *   bloqueada hasta que el broker responda, y una resuelta ya no vuelve.
 * - **Se exige el expediente completo antes de reenviar**: si falta un
 *   documento o queda uno observado, el botón explica cuál es el bloqueo. El
 *   backend no lo comprueba —es una regla de la casa, heredada del Blazor— y por
 *   eso está dicho en pantalla.
 */
@Component({
  selector: 'app-documentos-solicitud',
  imports: [DialogoConfirmacion, EstadoListado, SubidaArchivo, VisorDocumento],
  templateUrl: './documentos-solicitud.html',
  styleUrl: './documentos-solicitud.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentosSolicitud implements OnInit {
  private readonly api = inject(SolicitudesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly solicitud = signal<Solicitud | null>(null);
  protected readonly documentos = signal<readonly DocumentoSolicitud[]>([]);

  protected readonly tipoSeleccionado = signal<string>(TIPOS_REQUERIDOS[0]);
  protected readonly subiendo = signal(false);
  protected readonly errorAccion = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly reenviando = signal(false);
  protected readonly confirmarReenvio = signal(false);
  protected readonly documentoAbierto = signal<DocumentoSolicitud | null>(null);

  protected readonly esAgente = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly tiposRequeridos = TIPOS_REQUERIDOS;

  /**
   * El checklist se arma sobre los SEIS tipos requeridos y se superpone el
   * documento real de cada uno. Si hay varios del mismo tipo —recargas—, **gana
   * el último**: es el que el broker verá.
   */
  protected readonly filas = computed<FilaDocumento[]>(() => {
    const cargados = this.documentos();
    return TIPOS_REQUERIDOS.map((tipo) => ({
      tipo,
      nombre: describir(TIPO_DOCUMENTO_SOLICITUD, tipo),
      documento: [...cargados].reverse().find((d) => d.tipoDocumento === tipo) ?? null,
    }));
  });

  /** Los que impiden reenviar: sin archivo, u observados por el broker. */
  protected readonly porResolver = computed(() =>
    this.filas().filter((fila) => !fila.documento || fila.documento.estado === 'O'),
  );

  protected readonly entregados = computed(
    () => TIPOS_REQUERIDOS.length - this.filas().filter((fila) => !fila.documento).length,
  );

  protected readonly estadoPermiteReenviar = computed(() => {
    const estado = this.solicitud()?.estado;
    return !!estado && SOLICITUD_REENVIABLE.has(estado);
  });

  /**
   * Cargar y reenviar **no son la misma condición**, y confundirlas deja un
   * hueco real: el broker puede observar UN documento sin observar la solicitud
   * entera —que sigue en `E`—, y si la carga dependiera de poder reenviar, el
   * agente no podría subsanarlo hasta que llegara la decisión completa. Se
   * carga mientras la solicitud no esté resuelta; se reenvía solo desde `G`/`O`.
   */
  protected readonly puedeCargar = computed(() => {
    const estado = this.solicitud()?.estado;
    return this.esAgente() && !!estado && !SOLICITUD_RESUELTA.has(estado);
  });

  protected readonly puedeReenviar = computed(
    () =>
      this.esAgente() &&
      this.estadoPermiteReenviar() &&
      this.porResolver().length === 0 &&
      !this.reenviando(),
  );

  /** Por qué NO se puede reenviar, dicho antes de pulsar. */
  protected readonly motivoBloqueo = computed(() => {
    if (!this.esAgente()) {
      return 'Solo el agente responsable envía la solicitud a evaluación.';
    }
    const estado = this.solicitud()?.estado;
    if (estado === 'E') {
      return 'La solicitud ya está en evaluación. Espera la respuesta del broker.';
    }
    if (!this.estadoPermiteReenviar()) {
      return `La solicitud está ${this.etiquetaEstado(estado).toLowerCase()}: ya no se reenvía a evaluación.`;
    }
    const pendientes = this.porResolver().length;
    return pendientes > 0
      ? `Faltan ${pendientes} documento(s) por cargar o subsanar.`
      : '';
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
      await this.recargarDocumentos(solicitud.id);
    } catch (error) {
      this.error.set(mensajeError(error, 'No se pudo cargar el expediente.'));
    } finally {
      this.cargando.set(false);
    }
  }

  protected seleccionarTipo(tipo: string): void {
    this.tipoSeleccionado.set(tipo);
    this.errorAccion.set(null);
  }

  /**
   * Sube el archivo ya validado por `cl-subida-archivo` (extensión, MIME,
   * firma y tamaño) como octet-stream. El backend vuelve a validarlo: la
   * validación del cliente evita el viaje, no sustituye a la del servidor.
   */
  protected async subir(preparados: readonly ArchivoPreparado[]): Promise<void> {
    const solicitud = this.solicitud();
    const preparado = preparados[0];
    if (!solicitud || !preparado || this.subiendo() || !this.puedeCargar()) {
      return;
    }
    this.subiendo.set(true);
    this.errorAccion.set(null);
    this.aviso.set(null);
    try {
      await this.api.subirDocumento(solicitud.id, this.tipoSeleccionado(), preparado.archivo);
      await this.recargarDocumentos(solicitud.id);
      this.aviso.set(
        `«${describir(TIPO_DOCUMENTO_SOLICITUD, this.tipoSeleccionado())}» cargado. Queda pendiente de revisión del broker.`,
      );
      // El siguiente tipo por resolver, para encadenar cargas sin volver arriba.
      const siguiente = this.porResolver()[0];
      if (siguiente) {
        this.tipoSeleccionado.set(siguiente.tipo);
      }
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudo subir el documento.'));
    } finally {
      this.subiendo.set(false);
    }
  }

  protected fallaSubida(mensaje: string): void {
    this.errorAccion.set(mensaje);
  }

  protected abrir(documento: DocumentoSolicitud | null): void {
    if (documento?.rutaArchivo) {
      this.documentoAbierto.set(documento);
    }
  }

  protected cerrarVisor(): void {
    this.documentoAbierto.set(null);
  }

  protected pedirReenvio(): void {
    if (this.puedeReenviar()) {
      this.confirmarReenvio.set(true);
    }
  }

  protected cancelarReenvio(): void {
    this.confirmarReenvio.set(false);
  }

  protected async reenviar(): Promise<void> {
    const solicitud = this.solicitud();
    if (!solicitud || this.reenviando()) return;
    this.reenviando.set(true);
    this.errorAccion.set(null);
    try {
      const actualizada = await this.api.reenviar(solicitud.id);
      this.solicitud.set(actualizada);
      this.confirmarReenvio.set(false);
      void this.router.navigate(['/solicitudes', actualizada.codigoSolicitud]);
    } catch (error) {
      this.confirmarReenvio.set(false);
      this.errorAccion.set(mensajeError(error, 'No se pudo enviar a evaluación.'));
    } finally {
      this.reenviando.set(false);
    }
  }

  protected volver(): void {
    const codigo = this.solicitud()?.codigoSolicitud;
    if (codigo) {
      void this.router.navigate(['/solicitudes', codigo]);
    } else {
      void this.router.navigate(['/solicitudes']);
    }
  }

  protected etiquetaEstado(codigo: string | undefined): string {
    return describir(ESTADO_SOLICITUD, codigo) || SIN_DATO;
  }

  /** Sin archivo no hay estado del cable que mostrar: la fila está en blanco. */
  protected etiquetaDocumento(documento: DocumentoSolicitud | null): string {
    if (!documento) {
      return 'Pendiente de carga';
    }
    return describir(ESTADO_DOCUMENTO, documento.estado) || SIN_DATO;
  }

  protected tonoDocumento(documento: DocumentoSolicitud | null): string {
    if (!documento) return '';
    if (documento.estado === 'V') return 'bien';
    if (documento.estado === 'O') return 'mal';
    return 'aviso';
  }

  protected fecha(valor: string | undefined): string {
    return valor ? fechaHora(valor) : SIN_DATO;
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private async recargarDocumentos(idSolicitud: number): Promise<void> {
    this.documentos.set(await this.api.documentos(idSolicitud));
  }
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
