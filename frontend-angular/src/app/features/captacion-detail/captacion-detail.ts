import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Captacion, CaptacionesService } from '../../core/api/captaciones.service';
import { describir, ESTADO_CAPTACION, RESULTADO_PROPUESTA } from '../../core/api/codigos';
import { Local, LocalesService } from '../../core/api/locales.service';
import { Prospeccion, ProspeccionesService } from '../../core/api/prospecciones.service';
import { AuthService } from '../../core/auth/auth.service';
import { calcularCondicionComision, descripcionCondicionComision, importeTexto } from '../../core/comision';
import { fechaCorta, monto, numero, siNo, SIN_DATO, texto } from '../../core/formato';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

@Component({
  selector: 'app-captacion-detail',
  imports: [DialogoConfirmacion, EstadoListado, ReactiveFormsModule],
  templateUrl: './captacion-detail.html',
  styleUrl: './captacion-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaptacionDetail implements OnInit {
  private readonly api = inject(CaptacionesService);
  private readonly locales = inject(LocalesService);
  private readonly prospecciones = inject(ProspeccionesService);
  private readonly auth = inject(AuthService);
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
  protected readonly dialogoCierre = signal(false);
  protected readonly motivoCierre = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(1000)],
  });

  protected readonly rol = computed(() => this.auth.sesion()?.rol);
  protected readonly puedeEditar = computed(
    () => this.rol() === 'AGENTE' && ['P', 'O'].includes(this.captacion()?.estado ?? ''),
  );
  protected readonly puedeRevisar = computed(
    () => this.rol() !== 'AGENTE' && ['P', 'O'].includes(this.captacion()?.estado ?? ''),
  );
  protected readonly puedeCerrar = computed(
    () => this.rol() !== 'AGENTE' && this.captacion()?.estado === 'A',
  );

  ngOnInit(): void {
    void this.cargar();
  }

  protected volver(): void {
    void this.router.navigate(['/captaciones']);
  }

  protected editar(): void {
    const codigo = this.captacion()?.codigoCaptacion;
    if (codigo) void this.router.navigate(['/captaciones', codigo, 'editar']);
  }

  protected revisar(): void {
    const codigo = this.captacion()?.codigoCaptacion;
    if (codigo) void this.router.navigate(['/captaciones', codigo, 'revisar']);
  }

  protected resumenComercial(): void {
    const codigo = this.captacion()?.codigoCaptacion;
    if (codigo) void this.router.navigate(['/captaciones', codigo, 'ficha']);
  }

  protected verLocal(): void {
    const id = this.captacion()?.idLocal;
    if (id) void this.router.navigate(['/locales', id]);
  }

  protected verProspeccion(): void {
    const id = this.prospeccion()?.id;
    if (id) void this.router.navigate(['/prospecciones', id]);
  }

  protected abrirCierre(): void {
    this.motivoCierre.reset('');
    this.errorAccion.set(null);
    this.dialogoCierre.set(true);
  }

  protected cerrarDialogo(): void {
    if (!this.procesando()) this.dialogoCierre.set(false);
  }

  protected async confirmarCierre(): Promise<void> {
    const captacion = this.captacion();
    const motivo = this.motivoCierre.value.trim();
    if (!captacion || this.procesando() || !motivo) return;
    this.procesando.set(true);
    this.errorAccion.set(null);
    this.mensaje.set(null);
    try {
      this.captacion.set(await this.api.cerrar(captacion.id, motivo));
      this.mensaje.set('Captación cerrada. El motivo quedó registrado en la trazabilidad.');
      this.dialogoCierre.set(false);
    } catch (error) {
      this.errorAccion.set(mensajeError(error, 'No se pudo cerrar la captación.'));
    } finally {
      this.procesando.set(false);
    }
  }

  protected reintentar(): void { void this.cargar(); }
  protected valor(valor: string | undefined): string { return texto(valor); }
  protected fecha(valor: string | undefined): string { return fechaCorta(valor); }
  protected area(valor: number | undefined): string { return valor === undefined ? SIN_DATO : `${numero(valor)} m²`; }
  protected precio(valor: number | undefined, moneda: string | undefined): string { return monto(valor, moneda); }
  protected comision(): string { return descripcionCondicionComision(this.captacion()); }
  protected importeComision(): string {
    return importeTexto(calcularCondicionComision(this.captacion()));
  }
  protected exclusivo(valor: boolean | undefined): string { return siNo(valor); }
  protected estado(): string { return describir(ESTADO_CAPTACION, this.captacion()?.estado) || SIN_DATO; }
  protected resultadoPropuesta(): string {
    return describir(RESULTADO_PROPUESTA, this.prospeccion()?.resultadoPropuesta) || SIN_DATO;
  }
  protected tonoEstado(): string {
    const estado = this.captacion()?.estado;
    if (estado === 'A' || estado === 'C') return 'bien';
    if (estado === 'R' || estado === 'V') return 'mal';
    return 'aviso';
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.errorAccion.set(null);
    this.mensaje.set(null);
    try {
      const codigo = this.route.snapshot.paramMap.get('codigo');
      if (!codigo) throw new Error('El código de la captación es obligatorio.');
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
      this.errorCarga.set(mensajeError(error, 'No se pudo cargar el expediente de captación.'));
    } finally {
      this.cargando.set(false);
    }
  }
}

function mensajeError(error: unknown, alterno: string): string {
  return error instanceof ApiError || error instanceof Error ? error.message : alterno;
}
