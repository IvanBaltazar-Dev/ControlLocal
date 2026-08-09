import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Agente, AgentesService, DatosAgente } from '../../core/api/agentes.service';
import { ApiError } from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { texto as textoDe } from '../../core/formato';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

const LARGO_DOCUMENTO: Readonly<Record<string, number>> = { D: 8, R: 11 };

/**
 * Alta y edición del agente inmobiliario.
 *
 * **La asimetría entre alta y edición es grande aquí, y no es un descuido.**
 * `POST /agentes` crea en una transacción persona, roles `USUARIO_INTERNO` y
 * `AGENTE`, credencial, detalle y la supervisión inicial. `PUT /agentes/{id}`
 * solo toca **nombre, teléfono, correo, zona, estado administrativo y estado
 * operativo**: documento, tipos, usuario, contraseña, código y fecha de ingreso
 * se descartan **en silencio**. Por eso en edición van bloqueados y con el
 * motivo a la vista, en vez de prometer un cambio que el backend no hace.
 *
 * Dos reglas más que se anticipan aquí en vez de dejar que las explique un 400:
 *
 * - **Nombre, usuario y contraseña son obligatorios en el alta** (mensaje exacto
 *   del cable: «Nombre, usuario y contrasena del agente son obligatorios.»).
 * - **El ADMIN no puede dar de alta agentes operativos**: la supervisión inicial
 *   la crea el broker en sesión, y el administrador no supervisa a nadie.
 */
@Component({
  selector: 'app-agente-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './agente-form.html',
  styleUrl: './agente-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgenteForm implements OnInit {
  private readonly api = inject(AgentesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly idAgente = signal<number | null>(null);
  protected readonly original = signal<Agente | null>(null);

  protected readonly formulario = this.fb.nonNullable.group(
    {
      tipoPersona: this.fb.nonNullable.control<'N' | 'J'>('N', Validators.required),
      tipoDocumento: this.fb.nonNullable.control<'D' | 'R' | 'C' | 'P'>('D', Validators.required),
      numeroDocumento: this.fb.nonNullable.control('', Validators.required),
      nombre: this.fb.nonNullable.control('', Validators.required),
      telefono: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^\d{9}$/)]),
      correo: this.fb.nonNullable.control('', [Validators.required, Validators.email]),
      usuario: this.fb.nonNullable.control('', Validators.required),
      contrasena: this.fb.nonNullable.control('', [Validators.required, Validators.minLength(8)]),
      zona: this.fb.nonNullable.control(''),
      estadoOperativo: this.fb.nonNullable.control<'D' | 'O' | 'V' | 'S'>('D'),
    },
    { validators: [documentoValido] },
  );

  protected readonly esEdicion = computed(() => this.idAgente() !== null);
  /** Filas 17 y 18: alta y edición de agentes son gobierno del tenant. */
  protected readonly gobierna = computed(() => this.auth.sesion()?.rol === 'TENANT_ADMIN');
  protected readonly puedeGuardar = computed(() => this.gobierna());
  protected readonly titulo = computed(() =>
    this.esEdicion() ? `Editar ${textoDe(this.original()?.nombre)}` : 'Registrar agente',
  );

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isSafeInteger(id) && id > 0) {
      this.idAgente.set(id);
      void this.cargar(id);
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
      const id = this.idAgente();
      if (id) {
        await this.api.actualizar(id, this.datosParaGuardar());
      } else {
        await this.api.registrar(this.datosParaGuardar());
      }
      await this.router.navigate(['/agentes'], { replaceUrl: true });
    } catch (error) {
      this.errorGuardado.set(
        error instanceof ApiError ? error.message : 'No se pudo guardar el agente.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    void this.router.navigate(['/agentes']);
  }

  protected reintentar(): void {
    const id = this.idAgente();
    if (id) void this.cargar(id);
  }

  /**
   * **No hay `GET /agentes/{id}`** en el contrato congelado, así que la ficha se
   * busca en el listado. Es una lectura de más, pero la alternativa —pasar el
   * agente por estado del router— se rompe al recargar la página con F5.
   */
  private async cargar(id: number): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const pagina = await this.api.pagina(1, 100);
      const agente = (pagina.items ?? []).find((a) => a.id === id);
      if (!agente) {
        this.errorCarga.set('No se encontró el agente en tu alcance.');
        return;
      }
      this.original.set(agente);
      this.formulario.patchValue({
        tipoPersona: agente.tipoPersona === 'J' ? 'J' : 'N',
        tipoDocumento: (agente.tipoDocumento as 'D' | 'R' | 'C' | 'P') ?? 'D',
        numeroDocumento: agente.numeroDocumento ?? '',
        nombre: agente.nombre ?? '',
        telefono: agente.telefono ?? '',
        correo: agente.correo ?? '',
        usuario: agente.usuario ?? '',
        zona: agente.zona ?? '',
        estadoOperativo: (agente.estadoOperativo as 'D' | 'O' | 'V' | 'S') ?? 'D',
      });
      // Lo que el PUT descarta en silencio queda bloqueado y sin exigirse.
      this.formulario.controls.tipoPersona.disable();
      this.formulario.controls.tipoDocumento.disable();
      this.formulario.controls.numeroDocumento.disable();
      this.formulario.controls.usuario.disable();
      this.formulario.controls.contrasena.disable();
      this.formulario.controls.contrasena.clearValidators();
      this.formulario.controls.contrasena.updateValueAndValidity();
    } catch (error) {
      this.errorCarga.set(
        error instanceof ApiError ? error.message : 'No se pudo cargar el agente.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  private datosParaGuardar(): DatosAgente {
    const v = this.formulario.getRawValue();
    if (this.esEdicion()) {
      // En edición se manda SOLO lo que el PUT mira. Enviar el resto no rompe
      // nada —el cable lo ignora— pero invita a creer que se puede cambiar.
      return {
        nombre: v.nombre.trim(),
        telefono: v.telefono.trim(),
        correo: v.correo.trim(),
        zona: v.zona.trim(),
        estadoOperativo: v.estadoOperativo,
        estado: this.original()?.estadoAdministrativo,
      };
    }
    return {
      tipoPersona: v.tipoPersona,
      tipoDocumento: v.tipoDocumento,
      numeroDocumento: v.numeroDocumento.trim(),
      nombre: v.nombre.trim(),
      telefono: v.telefono.trim(),
      correo: v.correo.trim(),
      usuario: v.usuario.trim(),
      contrasena: v.contrasena,
      zona: v.zona.trim(),
      estadoOperativo: v.estadoOperativo,
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
