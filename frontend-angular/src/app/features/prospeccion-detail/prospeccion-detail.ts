import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { describir, ESTADO_PROSPECCION } from '../../core/api/codigos';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { fechaCorta, monto, numero, SIN_DATO, texto } from '../../core/formato';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const ACTIVAS = new Set(['P', 'C', 'R', 'E', 'S']);

type AccionProspeccion = 'contactar' | 'reunion' | 'propuesta' | 'seguimiento';

interface HitoProspeccion {
  codigo: string;
  etiqueta: string;
  fecha?: string;
}

@Component({
  selector: 'app-prospeccion-detail',
  imports: [DialogoConfirmacion, EstadoListado, ReactiveFormsModule],
  templateUrl: './prospeccion-detail.html',
  styleUrl: './prospeccion-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProspeccionDetail implements OnInit {
  private readonly api = inject(ProspeccionesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly cargando = signal(true);
  protected readonly procesando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly prospeccion = signal<Prospeccion | null>(null);
  protected readonly dialogoDescartar = signal(false);
  protected readonly motivo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(1000)],
  });

  protected readonly puedeOperar = computed(() => this.auth.sesion()?.rol === 'AGENTE');
  protected readonly activa = computed(() => ACTIVAS.has(this.prospeccion()?.estado ?? ''));
  protected readonly recontactoVencido = computed(() => {
    const p = this.prospeccion();
    if (!p?.fechaRecontacto || !['C', 'R', 'E', 'S'].includes(p.estado ?? '')) return false;
    const limite = fechaLocal(p.fechaRecontacto);
    if (!limite) return false;
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    return limite <= hoy;
  });
  protected readonly hitos = computed<HitoProspeccion[]>(() => {
    const p = this.prospeccion();
    if (!p) return [];
    return [
      { codigo: 'P', etiqueta: 'Prospecto registrado' },
      { codigo: 'C', etiqueta: 'Propietario contactado', fecha: p.fechaContacto },
      { codigo: 'R', etiqueta: 'Reunión registrada', fecha: p.fechaReunion },
      { codigo: 'S', etiqueta: 'Propuesta y seguimiento', fecha: p.fechaPropuesta },
      { codigo: 'T', etiqueta: 'Captación vinculada' },
    ];
  });

  ngOnInit(): void {
    void this.cargar();
  }

  protected volver(): void {
    void this.router.navigate(['/prospecciones']);
  }

  protected verLocal(): void {
    const id = this.prospeccion()?.localId;
    if (id) void this.router.navigate(['/propiedades', id]);
  }

  protected verResumen(): void {
    const codigo = this.prospeccion()?.captacionCodigo;
    if (codigo) void this.router.navigate(['/captaciones', codigo, 'ficha']);
  }

  protected crearCaptacion(): void {
    const id = this.prospeccion()?.id;
    if (id) {
      void this.router.navigate(['/captaciones/nueva'], { queryParams: { prospeccion: id } });
    }
  }

  protected reintentar(): void {
    void this.cargar();
  }

  protected abrirDescartar(): void {
    this.motivo.reset('');
    this.dialogoDescartar.set(true);
  }

  protected cerrarDescartar(): void {
    if (!this.procesando()) this.dialogoDescartar.set(false);
  }

  protected async avanzar(accion: AccionProspeccion): Promise<void> {
    const p = this.prospeccion();
    if (!p || this.procesando()) return;
    const operaciones: Record<AccionProspeccion, () => Promise<Prospeccion>> = {
      contactar: () => this.api.contactar(p.id),
      reunion: () => this.api.registrarReunion(p.id),
      propuesta: () => this.api.entregarPropuesta(p.id),
      seguimiento: () => this.api.registrarSeguimiento(p.id),
    };
    const mensajes: Record<AccionProspeccion, string> = {
      contactar: 'Contacto con el propietario registrado.',
      reunion: 'Reunión registrada.',
      propuesta: 'Propuesta entregada; la prospección quedó en seguimiento.',
      seguimiento: 'Seguimiento actualizado y reloj de recontacto reiniciado.',
    };
    await this.ejecutar(operaciones[accion], mensajes[accion]);
  }

  protected async confirmarDescartar(): Promise<void> {
    const p = this.prospeccion();
    const motivo = this.motivo.value.trim();
    if (!p || this.procesando()) return;
    if (!motivo) {
      this.motivo.markAsTouched();
      return;
    }
    const operacion = ['E', 'S'].includes(p.estado ?? '')
      ? () => this.api.rechazar(p.id, motivo)
      : () => this.api.descartar(p.id, motivo);
    await this.ejecutar(operacion, 'Prospección descartada.');
    if (!this.error()) this.dialogoDescartar.set(false);
  }

  protected etiquetaEstado(): string {
    return describir(ESTADO_PROSPECCION, this.prospeccion()?.estado) || SIN_DATO;
  }

  protected tonoEstado(): string {
    const estado = this.prospeccion()?.estado;
    if (estado === 'T') return 'bien';
    if (estado === 'D') return 'mal';
    return 'aviso';
  }

  protected fecha(valor: string | undefined): string {
    return fechaCorta(valor);
  }

  protected valor(valor: string | undefined): string {
    return texto(valor);
  }

  protected area(valor: number | undefined): string {
    return valor === undefined ? SIN_DATO : `${numero(valor)} m²`;
  }

  protected precio(valor: number | undefined, moneda: string | undefined): string {
    return monto(valor, moneda);
  }

  protected hitoCumplido(codigo: string): boolean {
    const estado = this.prospeccion()?.estado ?? '';
    if (estado === 'D') return codigo === 'P';
    return rangoEstado(estado) >= rangoEstado(codigo);
  }

  protected hitoActual(codigo: string): boolean {
    const estado = this.prospeccion()?.estado;
    if (estado === 'E') return codigo === 'S';
    return estado === codigo;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    this.mensaje.set(null);
    try {
      const id = Number(this.route.snapshot.paramMap.get('id'));
      if (!Number.isSafeInteger(id) || id <= 0) {
        throw new Error('El identificador de la prospección no es válido.');
      }
      this.prospeccion.set(await this.api.obtener(id));
    } catch (error) {
      this.prospeccion.set(null);
      this.error.set(mensajeError(error, 'No se pudo cargar la prospección.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private async ejecutar(
    operacion: () => Promise<Prospeccion>,
    mensaje: string,
  ): Promise<void> {
    this.procesando.set(true);
    this.error.set(null);
    this.mensaje.set(null);
    try {
      this.prospeccion.set(await operacion());
      this.mensaje.set(mensaje);
    } catch (error) {
      this.error.set(mensajeError(error, 'No se pudo actualizar la prospección.'));
    } finally {
      this.procesando.set(false);
    }
  }
}

export function rangoEstado(estado: string | undefined): number {
  return ({ P: 1, C: 2, R: 3, E: 4, S: 4, T: 5 } as Record<string, number>)[
    estado ?? ''
  ] ?? 0;
}

function fechaLocal(valor: string): Date | null {
  const fecha = new Date(`${valor.slice(0, 10)}T00:00:00`);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
