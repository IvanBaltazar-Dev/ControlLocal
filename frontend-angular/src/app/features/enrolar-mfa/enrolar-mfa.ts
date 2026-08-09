import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { toDataURL } from 'qrcode';

import { ApiError } from '../../core/api/api.types';
import { MfaService } from '../../core/api/mfa.service';
import { AVISO_MFA_CONFIGURADO } from '../../core/auth/avisos-acceso';
import { AuthService } from '../../core/auth/auth.service';
import {
  MFA_CODIGO_REUTILIZADO,
  MFA_ENROLAMIENTO_INVALIDO,
} from '../../core/auth/codigos-mfa';
import { CodigosRespaldo } from '../../shared/codigos-respaldo/codigos-respaldo';

/**
 * Marca de "esta pestaña ya generó un enrolamiento". **Es un booleano y nada
 * más**: aquí no se guarda el secreto, ni la URI, ni parte de ellos. Sirve solo
 * para saber si hay que avisar de que la configuración anterior quedó muerta.
 */
const MARCA_ENROLAMIENTO = 'controllocal.mfa.enrolamiento-iniciado';

const LARGO_CODIGO = 6;

/**
 * Enrolamiento del segundo factor (V37, D-S0-25).
 *
 * **Vive fuera del shell**, por lo mismo que el cambio de contraseña
 * obligatorio: cuando el backend capa la sesión (403
 * `ENROLAMIENTO_MFA_REQUERIDO`) solo deja pasar el perfil, este flujo y el
 * logout — dentro del armazón, la campana y el menú chocarían contra ese 403 y
 * la pantalla no llegaría a pintarse.
 *
 * <h2>Tres decisiones que explican todo lo demás</h2>
 *
 * 1. **Cada carga empieza un enrolamiento nuevo y el anterior muere.** El
 *    secreto sale del servidor una sola vez y no hay endpoint que lo relea;
 *    conservarlo en el navegador para sobrevivir a una recarga dejaría una
 *    copia permanente del segundo factor justo en el sitio del que el factor
 *    viene a protegernos. Así que se pide otro y **se dice en pantalla**, para
 *    que quien ya había escaneado borre la entrada vieja de su aplicación en
 *    vez de descubrirlo cuando el código no le sirva.
 *
 * 2. **Al confirmar se sale.** El código que confirma queda consumido (el
 *    servidor sella su paso) y la sesión, invalidada — nació sin segundo
 *    factor. No se intenta renovar nada por detrás: el ingreso siguiente es
 *    contraseña + un código **nuevo**, y esperar hasta 30 segundos al
 *    siguiente paso es la consecuencia aceptada de que un código valga una
 *    sola vez.
 *
 * 3. **Se decide por `codigo`, nunca por el texto del error.** El mensaje está
 *    en español y es traducible; el código es el contrato.
 */
@Component({
  selector: 'cl-enrolar-mfa',
  imports: [ReactiveFormsModule, CodigosRespaldo],
  templateUrl: './enrolar-mfa.html',
  styleUrl: './enrolar-mfa.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnrolarMfa implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly mfa = inject(MfaService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** `?obligatorio=1` lo pone el interceptor al toparse con la sesión capada. */
  protected readonly obligatorio =
    inject(ActivatedRoute).snapshot.queryParamMap.get('obligatorio') === '1';

  protected readonly fase = signal<'generando' | 'escanear' | 'respaldo' | 'ya-activo'>(
    'generando',
  );
  protected readonly errorInicio = signal<string | null>(null);
  protected readonly errorCodigo = signal<string | null>(null);
  protected readonly confirmando = signal(false);

  /** El enrolamiento de esta carga sustituye a uno anterior de la misma pestaña. */
  protected readonly reemplaza = signal(false);

  protected readonly secreto = signal<string>('');
  protected readonly qr = signal<string | null>(null);
  protected readonly codigos = signal<string[]>([]);
  protected readonly copiado = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    codigo: [
      '',
      [Validators.required, Validators.minLength(LARGO_CODIGO), Validators.maxLength(LARGO_CODIGO)],
    ],
  });

  ngOnInit(): void {
    void this.generar();
  }

  /**
   * Pide un secreto nuevo. Se llama al entrar —siempre— y desde el botón de
   * "generar otra configuración".
   */
  protected async generar(): Promise<void> {
    this.fase.set('generando');
    this.errorInicio.set(null);
    this.errorCodigo.set(null);
    this.qr.set(null);
    this.formulario.reset({ codigo: '' });

    try {
      // Se pregunta ANTES de pedir un secreto. Si el factor ya está activo,
      // pedirlo falla con un mensaje sobre revocarlo primero que aquí no
      // explica nada — y es el caso de quien recarga justo después de
      // activarlo, mirando sus códigos de respaldo.
      if ((await this.mfa.estado()).activo) {
        olvidarEnrolamiento();
        this.fase.set('ya-activo');
        return;
      }
      this.reemplaza.set(yaHuboEnrolamiento());
      marcarEnrolamiento();
      const enrolamiento = await this.mfa.iniciar();
      this.secreto.set(enrolamiento.secreto);
      await this.pintarQr(enrolamiento.uri);
      this.fase.set('escanear');
    } catch (error) {
      this.errorInicio.set(
        error instanceof ApiError
          ? error.message
          : 'No se pudo preparar el segundo factor. Vuelve a intentarlo.',
      );
    }
  }

  /**
   * El QR se dibuja **aquí**, a partir de la `uri` que manda el backend. La
   * librería es sólo el codificador: no toca la red, así que la URI —que
   * contiene el secreto— no sale de esta pestaña. Un servicio externo de QR
   * habría sido una línea de código y una filtración del segundo factor a un
   * tercero.
   */
  private async pintarQr(uri: string): Promise<void> {
    try {
      this.qr.set(
        await toDataURL(uri, { errorCorrectionLevel: 'M', margin: 2, width: 232 }),
      );
    } catch {
      // Sin imagen se sigue pudiendo enrolar: la clave manual es el mismo
      // secreto y está siempre en pantalla, no escondida tras un "¿problemas?".
      this.qr.set(null);
    }
  }

  protected async confirmar(): Promise<void> {
    if (this.confirmando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.confirmando.set(true);
    this.errorCodigo.set(null);
    try {
      const respuesta = await this.mfa.confirmar(this.formulario.controls.codigo.value.trim());
      this.codigos.set(respuesta.codigos);
      // El factor ya está activo: si volviera a entrarse a esta pantalla sería
      // un enrolamiento distinto, no la continuación de este.
      olvidarEnrolamiento();
      this.fase.set('respaldo');
    } catch (error) {
      this.errorCodigo.set(this.mensajeDeFallo(error));
    } finally {
      this.confirmando.set(false);
    }
  }

  /**
   * El texto lo elige el SPA a partir del `codigo`. Importa sobre todo separar
   * "lo escribiste mal" de "ese código ya se usó": el segundo **no se corrige
   * reescribiéndolo**, y sin decirlo el usuario insiste con el mismo hasta
   * gastar sus intentos.
   */
  private mensajeDeFallo(error: unknown): string {
    if (!(error instanceof ApiError)) {
      return 'No se pudo confirmar el segundo factor. Vuelve a intentarlo.';
    }
    if (error.codigo === MFA_CODIGO_REUTILIZADO) {
      return 'Ese código ya se usó. Espera a que tu aplicación muestre el siguiente.';
    }
    if (error.codigo === MFA_ENROLAMIENTO_INVALIDO) {
      return 'La configuración caducó. Genera una nueva y vuelve a escanearla.';
    }
    return error.message;
  }

  /** La clave manual, en grupos de cuatro: se teclea a mano y así se lee. */
  protected get secretoLegible(): string {
    return (this.secreto().match(/.{1,4}/g) ?? []).join(' ');
  }

  protected async copiarClave(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.secreto());
      this.copiado.set(true);
    } catch {
      // Sin portapapeles (contexto no seguro o permiso denegado) queda
      // seleccionar a mano; no vale la pena un mensaje de error por esto.
      this.copiado.set(false);
    }
  }

  /**
   * Cierre y vuelta al login. **Solo limpieza local**: activar el factor ya
   * invalidó todas las sesiones de la cuenta en el servidor, así que pedir un
   * logout con este token devolvería 401 y realimentaría el mismo cierre.
   */
  protected async terminar(): Promise<void> {
    this.auth.olvidarSesionLocal();
    await this.router.navigate(['/login'], {
      queryParams: { aviso: AVISO_MFA_CONFIGURADO },
      replaceUrl: true,
    });
  }

  protected volver(): void {
    void this.router.navigate(['/perfil']);
  }
}

// `sessionStorage` puede estar bloqueado (modo privado, políticas del
// navegador). Se degrada a "no aviso" en vez de tumbar el enrolamiento: el
// aviso es una cortesía, el enrolamiento no.

function yaHuboEnrolamiento(): boolean {
  try {
    return sessionStorage.getItem(MARCA_ENROLAMIENTO) === '1';
  } catch {
    return false;
  }
}

function marcarEnrolamiento(): void {
  try {
    sessionStorage.setItem(MARCA_ENROLAMIENTO, '1');
  } catch {
    // Ver arriba.
  }
}

function olvidarEnrolamiento(): void {
  try {
    sessionStorage.removeItem(MARCA_ENROLAMIENTO);
  } catch {
    // Ver arriba.
  }
}
