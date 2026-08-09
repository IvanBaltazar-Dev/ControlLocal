import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/api/api.types';
import { Broker, BrokersService, DatosBroker } from '../../core/api/brokers.service';
import { AuthService } from '../../core/auth/auth.service';
import { texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const LARGO_DOCUMENTO: Readonly<Record<string, number>> = { D: 8, R: 11 };

/**
 * Alta y edición del broker supervisor. **Las dos son de ADMIN.**
 *
 * Igual que en agentes, `PUT /brokers/{id}` solo toca **nombre, teléfono,
 * correo, zona y estado**: documento, tipos, usuario, contraseña, código, fecha
 * de designación y —esto es lo importante— **`esAdministrador`** se descartan en
 * silencio. Quién administra se decide al dar de alta y no se edita después, así
 * que en edición ese control ni siquiera se ofrece.
 *
 * Regla que el formulario anticipa: **solo puede existir un broker administrador
 * por organización**. Si ya lo hay, marcar la casilla en el alta produce un 400
 * («Solo debe existir un broker administrador.»), no un error de validación
 * local, así que se avisa antes de enviar.
 */
@Component({
  selector: 'app-broker-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './broker-form.html',
  styleUrl: './broker-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrokerForm implements OnInit {
  private readonly api = inject(BrokersService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly idBroker = signal<number | null>(null);
  protected readonly original = signal<Broker | null>(null);
  protected readonly yaHayAdministrador = signal(false);

  protected readonly formulario = this.fb.nonNullable.group(
    {
      tipoDocumento: this.fb.nonNullable.control<'D' | 'R' | 'C' | 'P'>('D', Validators.required),
      numeroDocumento: this.fb.nonNullable.control('', Validators.required),
      nombre: this.fb.nonNullable.control('', Validators.required),
      telefono: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^\d{9}$/)]),
      correo: this.fb.nonNullable.control('', [Validators.required, Validators.email]),
      usuario: this.fb.nonNullable.control('', Validators.required),
      contrasena: this.fb.nonNullable.control('', [Validators.required, Validators.minLength(8)]),
      zona: this.fb.nonNullable.control(''),
      esAdministrador: this.fb.nonNullable.control(false),
    },
    { validators: [documentoValido] },
  );

  protected readonly esEdicion = computed(() => this.idBroker() !== null);
  protected readonly puedeGuardar = computed(() => this.auth.sesion()?.rol === 'TENANT_ADMIN');
  protected readonly titulo = computed(() =>
    this.esEdicion() ? `Editar ${textoDe(this.original()?.nombre)}` : 'Registrar broker',
  );

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isSafeInteger(id) && id > 0) {
      this.idBroker.set(id);
      void this.cargar(id);
    } else {
      void this.comprobarAdministrador();
    }
  }

  protected invalido(campo: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.dirty || control.touched);
  }

  protected get documentoIncompleto(): boolean {
    return (
      this.formulario.hasError('documento') &&
      (this.formulario.controls.numeroDocumento.dirty ||
        this.formulario.controls.numeroDocumento.touched)
    );
  }

  protected mensajeDocumento(): string {
    const largo = LARGO_DOCUMENTO[this.formulario.controls.tipoDocumento.value];
    return largo
      ? `El documento debe tener ${largo} dígitos.`
      : 'El número de documento es obligatorio.';
  }

  protected async guardar(): Promise<void> {
    if (this.guardando() || !this.puedeGuardar()) return;
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Revisa los campos obligatorios antes de guardar.');
      return;
    }

    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const id = this.idBroker();
      if (id) {
        await this.api.actualizar(id, this.datosParaGuardar());
      } else {
        await this.api.registrar(this.datosParaGuardar());
      }
      await this.router.navigate(['/brokers'], { replaceUrl: true });
    } catch (error) {
      this.errorGuardado.set(
        error instanceof ApiError ? error.message : 'No se pudo guardar el broker.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    void this.router.navigate(['/brokers']);
  }

  protected reintentar(): void {
    const id = this.idBroker();
    if (id) void this.cargar(id);
  }

  private async cargar(id: number): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const broker = await this.api.obtener(id);
      this.original.set(broker);
      this.formulario.patchValue({
        tipoDocumento: (broker.tipoDocumento as 'D' | 'R' | 'C' | 'P') ?? 'D',
        numeroDocumento: broker.numeroDocumento ?? '',
        nombre: broker.nombre ?? '',
        telefono: broker.telefono ?? '',
        correo: broker.correo ?? '',
        usuario: broker.usuario ?? '',
        zona: broker.zona ?? '',
        esAdministrador: broker.esAdministrador === true,
      });
      // Lo que el PUT descarta en silencio queda bloqueado.
      this.formulario.controls.tipoDocumento.disable();
      this.formulario.controls.numeroDocumento.disable();
      this.formulario.controls.usuario.disable();
      this.formulario.controls.contrasena.disable();
      this.formulario.controls.contrasena.clearValidators();
      this.formulario.controls.contrasena.updateValueAndValidity();
      this.formulario.controls.esAdministrador.disable();
    } catch (error) {
      this.errorCarga.set(
        error instanceof ApiError ? error.message : 'No se pudo cargar el broker.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  /** Para avisar antes de enviar, no para bloquear: la regla la impone el API. */
  private async comprobarAdministrador(): Promise<void> {
    try {
      const pagina = await this.api.pagina(1, 100);
      this.yaHayAdministrador.set((pagina.items ?? []).some((b) => b.esAdministrador === true));
    } catch {
      // Si no se puede comprobar, no se avisa. El backend sigue decidiendo.
      this.yaHayAdministrador.set(false);
    }
  }

  private datosParaGuardar(): DatosBroker {
    const v = this.formulario.getRawValue();
    if (this.esEdicion()) {
      return {
        nombre: v.nombre.trim(),
        telefono: v.telefono.trim(),
        correo: v.correo.trim(),
        zona: v.zona.trim(),
        estado: this.original()?.estadoAdministrativo,
      };
    }
    return {
      tipoPersona: 'N',
      tipoDocumento: v.tipoDocumento,
      numeroDocumento: v.numeroDocumento.trim(),
      nombre: v.nombre.trim(),
      telefono: v.telefono.trim(),
      correo: v.correo.trim(),
      usuario: v.usuario.trim(),
      contrasena: v.contrasena,
      zona: v.zona.trim(),
      esAdministrador: v.esAdministrador,
    };
  }
}

/** Espejo de `Personas.validar`: DNI 8 y RUC 11, numéricos; el resto pasa. */
function documentoValido(control: AbstractControl): ValidationErrors | null {
  const tipo = control.get('tipoDocumento')?.value as string | undefined;
  const numero = ((control.get('numeroDocumento')?.value as string | undefined) ?? '').trim();
  if (!numero) return null;
  const largo = tipo ? LARGO_DOCUMENTO[tipo] : undefined;
  if (!largo) return null;
  return /^\d+$/.test(numero) && numero.length === largo ? null : { documento: true };
}
