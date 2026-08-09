import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { toSignal } from '@angular/core/rxjs-interop';

import { ApiError } from '../../core/api/api.types';
import { Cliente, ClientesService, DatosCliente } from '../../core/api/clientes.service';
import { AuthService } from '../../core/auth/auth.service';
import { texto as textoDe } from '../../core/formato';
import { rubrosCon } from '../../core/rubros';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

/** Largo exacto que el backend exige. Carné y pasaporte pasan tal cual. */
const LARGO_DOCUMENTO: Readonly<Record<string, number>> = { D: 8, R: 11 };

/**
 * Alta y edición del cliente interesado. Porta `ClienteForm.razor`.
 *
 * Lo que esta pantalla hace explícito y el legado dejaba a medias: en edición,
 * **tipo de persona, tipo de documento y número no se pueden cambiar**. No es
 * una decisión de la pantalla — `PUT /clientes/{id}` solo toca nombre,
 * contacto, rubro, consentimientos y estado, y descarta el resto **en
 * silencio**. Mostrar esos campos editables sería prometer un cambio que el
 * backend no hace, así que van bloqueados y con el motivo a la vista.
 *
 * Teléfono y correo son obligatorios **por regla de pantalla**, no del cable:
 * el backend los acepta vacíos. Se conserva la exigencia del Blazor porque un
 * cliente sin forma de contactarlo no sirve para la demanda.
 */
@Component({
  selector: 'app-cliente-form',
  imports: [EstadoListado, ReactiveFormsModule],
  templateUrl: './cliente-form.html',
  styleUrl: './cliente-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClienteForm implements OnInit {
  private readonly api = inject(ClientesService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly errorGuardado = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly idCliente = signal<number | null>(null);
  protected readonly original = signal<Cliente | null>(null);

  protected readonly formulario = this.fb.nonNullable.group(
    {
      tipoPersona: this.fb.nonNullable.control<'N' | 'J'>('N', Validators.required),
      tipoDocumento: this.fb.nonNullable.control<'D' | 'R' | 'C' | 'P'>('D', Validators.required),
      numeroDocumento: this.fb.nonNullable.control('', Validators.required),
      nombre: this.fb.nonNullable.control('', Validators.required),
      telefono: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^\d{9}$/)]),
      correo: this.fb.nonNullable.control('', [Validators.required, Validators.email]),
      rubroComercial: this.fb.nonNullable.control('', Validators.required),
      // D-27: en el alta esta es LA casilla de autorizacion, y lo UNICO que el
      // usuario aporta. Cubre los cinco ambitos, asi que no hay una segunda
      // casilla de comunicaciones ni un desplegable de canal.
      consentimientoUsoDato: this.fb.nonNullable.control(false),
    },
    { validators: [documentoValido] },
  );

  protected readonly esEdicion = computed(() => this.idCliente() !== null);
  protected readonly puedeEditar = computed(() => this.auth.sesion()?.rol === 'AGENTE');

  /**
   * El formulario refleja el valor con `toSignal(valueChanges)` y no con un
   * `computed()` sobre `FormControl.value`: **un computed que lee .value no es
   * reactivo**, y ese fallo ya costó un botón bloqueado con el motivo escrito
   * en F3.
   */
  private readonly valores = toSignal(this.formulario.valueChanges, {
    initialValue: this.formulario.getRawValue(),
  });

  /** En el alta, sin la casilla marcada no se puede guardar: el backend lo rechaza. */
  protected readonly faltaAutorizacion = computed(
    () => !this.esEdicion() && this.valores()?.consentimientoUsoDato !== true,
  );
  protected readonly titulo = computed(() =>
    this.esEdicion() ? `Editar ${textoDe(this.original()?.nombre)}` : 'Registrar cliente interesado',
  );
  protected readonly juridica = signal(false);
  /** El valor actual entra aunque no esté en la lista sugerida. */
  protected readonly rubros = computed(() => rubrosCon(this.original()?.rubroComercial));
  protected readonly etiquetaNombre = computed(() =>
    this.juridica() ? 'Razón social' : 'Nombre completo',
  );
  protected readonly etiquetaDocumento = computed(() => (this.juridica() ? 'RUC' : 'Número de documento'));

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isSafeInteger(id) && id > 0) {
      this.idCliente.set(id);
      void this.cargar(id);
    } else {
      this.formulario.controls.rubroComercial.setValue(this.rubros()[0]);
      this.sincronizarTipo('N');
    }
  }

  protected cambiarTipoPersona(tipo: 'N' | 'J'): void {
    if (this.esEdicion()) return;
    this.formulario.controls.tipoPersona.setValue(tipo);
    this.sincronizarTipo(tipo);
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
      ? `${this.etiquetaDocumento()} debe tener ${largo} dígitos.`
      : 'El número de documento es obligatorio.';
  }

  protected async guardar(): Promise<void> {
    if (this.guardando() || !this.puedeEditar()) return;
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.errorGuardado.set('Revisa los campos obligatorios antes de guardar.');
      return;
    }
    // D-27: se corta AQUI, no se manda para que el backend lo rechace. Sin
    // autorizacion no se guarda ningun dato identificable, y decirlo antes de
    // enviar es lo que evita que el usuario crea que fue un error del sistema.
    if (this.faltaAutorizacion()) {
      this.errorGuardado.set(
        'Sin la autorización de la persona no se puede registrar: no se guardará ningún dato.',
      );
      return;
    }

    this.guardando.set(true);
    this.errorGuardado.set(null);
    try {
      const id = this.idCliente();
      if (id) {
        await this.api.actualizar(id, this.datosParaGuardar());
      } else {
        await this.api.registrar(this.datosParaGuardar());
      }
      await this.router.navigate(['/clientes'], { replaceUrl: true });
    } catch (error) {
      this.errorGuardado.set(
        error instanceof ApiError ? error.message : 'No se pudo guardar el cliente.',
      );
    } finally {
      this.guardando.set(false);
    }
  }

  protected cancelar(): void {
    void this.router.navigate(['/clientes']);
  }

  protected reintentar(): void {
    const id = this.idCliente();
    if (id) void this.cargar(id);
  }

  protected valor(valor: string | undefined): string {
    return textoDe(valor);
  }

  private async cargar(id: number): Promise<void> {
    this.cargando.set(true);
    this.errorCarga.set(null);
    try {
      const cliente = await this.api.obtener(id);
      this.original.set(cliente);
      const tipo = cliente.tipoPersona === 'J' ? 'J' : 'N';
      this.formulario.patchValue({
        tipoPersona: tipo,
        tipoDocumento: (cliente.tipoDocumento as 'D' | 'R' | 'C' | 'P') ?? 'D',
        numeroDocumento: cliente.numeroDocumento ?? '',
        nombre: cliente.nombre ?? '',
        telefono: cliente.telefono ?? '',
        correo: cliente.correo ?? '',
        rubroComercial: cliente.rubroComercial || this.rubros()[0],
        consentimientoUsoDato: cliente.consentimientoUsoDato === true,
      });
      this.juridica.set(tipo === 'J');
      // La identidad queda bloqueada: el PUT no la toca (contrato F3 §2).
      this.formulario.controls.tipoPersona.disable();
      this.formulario.controls.tipoDocumento.disable();
      this.formulario.controls.numeroDocumento.disable();
    } catch (error) {
      this.errorCarga.set(
        error instanceof ApiError ? error.message : 'No se pudo cargar el cliente.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  /** Una persona jurídica se identifica con RUC; no hay otra opción. */
  private sincronizarTipo(tipo: 'N' | 'J'): void {
    this.juridica.set(tipo === 'J');
    const documento = this.formulario.controls.tipoDocumento;
    if (tipo === 'J') {
      documento.setValue('R');
      documento.disable();
    } else {
      if (documento.value === 'R') documento.setValue('D');
      documento.enable();
    }
  }

  private datosParaGuardar(): DatosCliente {
    // getRawValue incluye los controles deshabilitados: en edición viajan los
    // mismos valores que ya tenía, así el PUT no los ve cambiar.
    const v = this.formulario.getRawValue();
    return {
      tipoPersona: v.tipoPersona,
      tipoDocumento: v.tipoDocumento,
      numeroDocumento: v.numeroDocumento.trim(),
      nombre: v.nombre.trim(),
      telefono: v.telefono.trim(),
      correo: v.correo.trim(),
      rubroComercial: v.rubroComercial,
      // La autorizacion unica cubre tambien las comunicaciones (ambito 2 de
      // D-27), asi que el consentimiento de contacto NO se pide aparte: se
      // deriva de la misma casilla. En edicion se conserva el que ya tenia,
      // porque el PUT no es el sitio donde se revoca.
      consentimientoContacto: this.esEdicion()
        ? this.original()?.consentimientoContacto
        : v.consentimientoUsoDato,
      consentimientoUsoDato: v.consentimientoUsoDato,
      // El estado no se toca desde el formulario: se cambia con la baja o la
      // reactivación de la bandeja, que son decisiones con confirmación.
      estado: this.original()?.estado,
    };
  }
}

/**
 * Espejo de `Personas.validar` del backend: RUC 11 dígitos y DNI 8, ambos
 * numéricos; carné y pasaporte pasan tal cual. Se valida aquí para no
 * descubrirlo con un 400 después de escribir el formulario entero.
 */
function documentoValido(control: AbstractControl): ValidationErrors | null {
  const tipo = control.get('tipoDocumento')?.value as string | undefined;
  const numero = ((control.get('numeroDocumento')?.value as string | undefined) ?? '').trim();
  if (!numero) return null;
  const largo = tipo ? LARGO_DOCUMENTO[tipo] : undefined;
  if (!largo) return null;
  return /^\d+$/.test(numero) && numero.length === largo ? null : { documento: true };
}
