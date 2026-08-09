import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError } from '../../core/api/api.types';
import { MfaService } from '../../core/api/mfa.service';
import {
  AvisoDeGobierno,
  CuentaDeGobierno,
  SeguridadService,
} from '../../core/api/seguridad.service';
import { AuthService } from '../../core/auth/auth.service';
import { MFA_CODIGO_REUTILIZADO } from '../../core/auth/codigos-mfa';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { EstadoListado } from '../../shared/estado-listado/estado-listado';

/** Lo que se enseña de cada tipo de evento, para no mostrar el enum crudo. */
const ETIQUETA_EVENTO: Readonly<Record<string, string>> = {
  MFA_ACTIVADO: 'Segundo factor activado',
  MFA_REVOCADO: 'Segundo factor revocado',
  MFA_CODIGOS_REGENERADOS: 'Códigos de respaldo regenerados',
  MFA_CODIGO_RESPALDO_USADO: 'Entró con un código de respaldo',
  ELEVACION_EMITIDA: 'Reautenticación reforzada',
  ELEVACION_FALLIDA: 'Reautenticación reforzada fallida',
  CUENTA_BLOQUEADA: 'Cuenta bloqueada por intentos',
  CUENTA_DESBLOQUEADA: 'Cuenta desbloqueada',
  CUENTA_ACTIVADA: 'Cuenta activada',
  CUENTA_DESACTIVADA: 'Cuenta desactivada',
  ROL_OTORGADO: 'Rol otorgado',
  ROL_REVOCADO: 'Rol revocado',
  PASSWORD_RESTABLECIDA: 'Contraseña restablecida',
  INVITACION_EMITIDA: 'Invitación de acceso emitida',
  ACCESO_TENANT_CONCEDIDO: 'Acceso a la organización concedido',
  ACCESO_TENANT_USADO: 'Acceso a la organización usado',
  BREAK_GLASS_ACTIVADO: 'Recuperación de emergencia activada',
};

/**
 * Gobierno de accesos del tenant. Solo `TENANT_ADMIN`.
 *
 * <h2>Por qué la revocación vive aquí y no en la ficha del agente</h2>
 * Porque la ficha del agente es un expediente **comercial** —captaciones,
 * oportunidades, dinero— y se identifica por `persona_rol.id`. La revocación
 * de un segundo factor habla de la **persona**, y el contrato congelado no
 * publica ese id en `AgenteResponse` ni en `BrokerResponse`. Meterlo ahí
 * habría exigido tocar dos DTO congelados; publicarlo en `GET /accesos`, que
 * es aditivo, no rompe nada. De paso queda donde corresponde: administrar
 * cuentas es gobierno, no supervisión.
 *
 * <h2>Lo que el administrador NO puede hacer, y se nota en la pantalla</h2>
 * No ve el secreto de nadie, no configura el factor de nadie y no elige la
 * contraseña de nadie. Revoca, y el titular vuelve a enrolar. Por eso el botón
 * dice «Revocar», no «Restablecer»: prometer lo segundo sería mentir sobre lo
 * que ocurre después.
 */
@Component({
  selector: 'cl-seguridad',
  imports: [EstadoListado, ReactiveFormsModule, DialogoConfirmacion],
  templateUrl: './seguridad.html',
  styleUrl: './seguridad.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Seguridad implements OnInit {
  private readonly api = inject(SeguridadService);
  private readonly mfa = inject(MfaService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly cuentas = signal<CuentaDeGobierno[]>([]);
  protected readonly avisos = signal<AvisoDeGobierno[]>([]);

  protected readonly objetivo = signal<CuentaDeGobierno | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly errorRevocacion = signal<string | null>(null);
  protected readonly exito = signal<string | null>(null);

  /** Motivo obligatorio + la reautenticación reforzada del propio administrador. */
  protected readonly formulario = this.fb.nonNullable.group({
    motivo: ['', [Validators.required, Validators.minLength(5)]],
    contrasena: ['', Validators.required],
    codigo: ['', Validators.required],
  });

  private readonly idPersonaPropia = computed(() => this.auth.sesion()?.idUsuario ?? -1);

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const [cuentas, avisos] = await Promise.all([this.api.cuentas(), this.api.avisos(1, 30)]);
      this.cuentas.set(cuentas);
      this.avisos.set(avisos.items);
    } catch (error) {
      this.error.set(
        error instanceof ApiError ? error.message : 'No se pudo cargar el gobierno de accesos.',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Nadie se revoca a sí mismo por esta vía: para el factor propio está el
   * perfil, que además exige la reautenticación del titular. El backend lo
   * rechaza igual; aquí solo se evita ofrecer un botón que va a fallar.
   */
  protected puedeRevocar(cuenta: CuentaDeGobierno): boolean {
    return cuenta.mfaActivo && cuenta.idPersona !== this.idPersonaPropia();
  }

  protected abrirRevocacion(cuenta: CuentaDeGobierno): void {
    this.objetivo.set(cuenta);
    this.errorRevocacion.set(null);
    this.exito.set(null);
    this.formulario.reset({ motivo: '', contrasena: '', codigo: '' });
  }

  protected cerrarRevocacion(): void {
    this.objetivo.set(null);
    this.errorRevocacion.set(null);
    this.formulario.reset({ motivo: '', contrasena: '', codigo: '' });
  }

  protected get incompleto(): boolean {
    return this.formulario.invalid;
  }

  /**
   * Dos llamadas y en este orden: primero la **elevación** —que es donde el
   * administrador prueba su propia contraseña y su propio código— y solo
   * después la revocación, que la consume. El token dura 5 minutos, sirve una
   * vez y solo para esta acción; existe porque el token de sesión está
   * congelado y no lleva cuándo se probó el segundo factor, así que dar por
   * buena una sesión abierta esta mañana dejaría pasar una sesión robada.
   */
  protected async revocar(): Promise<void> {
    const cuenta = this.objetivo();
    if (!cuenta || this.ocupado() || this.formulario.invalid) {
      return;
    }
    const datos = this.formulario.getRawValue();
    this.ocupado.set(true);
    this.errorRevocacion.set(null);
    try {
      const elevacion = await this.mfa.elevar({
        contrasena: datos.contrasena,
        codigo: datos.codigo.trim(),
      });
      await this.mfa.revocarAjeno(cuenta.idPersona, datos.motivo.trim(), elevacion.token);
      this.objetivo.set(null);
      this.exito.set(
        `Se revocó el segundo factor de ${cuenta.nombre}. Sus sesiones se cerraron y tendrá que ` +
          'volver a enrolarlo para poder operar.',
      );
      await this.cargar();
    } catch (error) {
      this.errorRevocacion.set(this.mensaje(error));
    } finally {
      this.ocupado.set(false);
    }
  }

  /** Por `codigo`, no por el texto en español. */
  private mensaje(error: unknown): string {
    if (!(error instanceof ApiError)) {
      return 'No se pudo completar la revocación.';
    }
    if (error.codigo === MFA_CODIGO_REUTILIZADO) {
      return 'Ese código ya se usó. Espera a que tu aplicación muestre el siguiente.';
    }
    return error.message;
  }

  protected etiqueta(tipo: string): string {
    return ETIQUETA_EVENTO[tipo] ?? tipo;
  }

  protected estadoDe(cuenta: CuentaDeGobierno): string {
    if (!cuenta.activa) {
      return 'Suspendida';
    }
    if (cuenta.debeCambiarContrasena) {
      return 'Debe cambiar contraseña';
    }
    if (cuenta.debeEnrolarMfa) {
      return 'Debe enrolar MFA';
    }
    return 'Operativa';
  }
}
