import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { mensajeDeAviso } from '../../core/auth/avisos-acceso';
import { destinoSeguro, PARAM_DESTINO } from '../../core/auth/destino-tras-login';
import { exigeReiniciarLogin, MFA_CODIGO_REUTILIZADO } from '../../core/auth/codigos-mfa';

/**
 * Puerta de entrada. **Dos pasos, y el segundo solo aparece si hace falta**
 * (D-S0-22): la pantalla manda usuario y contraseña, y es el servidor quien
 * responde con la sesión (200) o con un desafío (202). El SPA no puede
 * adivinar quién tiene segundo factor —preguntarlo sería un padrón de
 * cuentas—, así que no lo intenta.
 *
 * Tampoco se piden los tres datos de una vez: obligaría a tener el código
 * listo antes de saber si hace falta y **quemaría códigos legítimos contra
 * contraseñas mal escritas**.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly parametros = inject(ActivatedRoute).snapshot.queryParamMap;

  /** Mensaje traído por `?aviso=`, del catálogo cerrado de `avisos-acceso`. */
  protected readonly aviso = signal<string | null>(
    mensajeDeAviso(this.parametros.get('aviso')),
  );

  /**
   * A dónde volver tras entrar, si se llegó aquí por una sesión caducada o por
   * un enlace directo. Se filtra en `destinoSeguro` porque viene de la URL.
   */
  private readonly destino = destinoSeguro(this.parametros.get(PARAM_DESTINO));

  /** El desafío vivo. Mientras exista, la pantalla pide el código. */
  protected readonly desafio = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    usuario: ['', Validators.required],
    contrasena: ['', Validators.required],
  });

  protected readonly formularioCodigo = this.fb.nonNullable.group({
    codigo: ['', Validators.required],
  });

  protected async ingresar(): Promise<void> {
    if (this.formulario.invalid || this.cargando()) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.aviso.set(null);
    try {
      const resultado = await this.auth.login(this.formulario.getRawValue());
      if (resultado.tipo === 'desafio') {
        this.desafio.set(resultado.desafio);
        this.formularioCodigo.reset({ codigo: '' });
        return;
      }
      await this.irADestino();
    } catch (e) {
      const http = e as HttpErrorResponse;
      this.error.set(http.error?.error ?? 'No se pudo iniciar sesión. Verifica tu conexión.');
    } finally {
      this.cargando.set(false);
    }
  }

  protected async verificar(): Promise<void> {
    const desafio = this.desafio();
    if (!desafio || this.formularioCodigo.invalid || this.cargando()) {
      this.formularioCodigo.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      await this.auth.verificarSegundoFactor(
        desafio,
        this.formularioCodigo.controls.codigo.value.trim(),
      );
      await this.irADestino();
    } catch (e) {
      const cuerpo = (e as HttpErrorResponse).error as
        | { error?: string; codigo?: string }
        | undefined;
      // **Por `codigo`, nunca por el texto.** Lo que cambia no es solo el
      // mensaje: cuando el desafío ya no sirve hay que volver al primer paso,
      // y dejar al usuario tecleando códigos contra un desafío muerto le gasta
      // los intentos de la cuenta sin que nada de lo que escriba pueda
      // funcionar.
      if (exigeReiniciarLogin(cuerpo?.codigo)) {
        this.desafio.set(null);
        this.formulario.patchValue({ contrasena: '' });
        this.error.set(cuerpo?.error ?? 'Vuelve a iniciar sesión.');
        return;
      }
      if (cuerpo?.codigo === MFA_CODIGO_REUTILIZADO) {
        this.error.set('Ese código ya se usó. Espera a que tu aplicación muestre el siguiente.');
        return;
      }
      this.error.set(cuerpo?.error ?? 'No se pudo verificar el código. Verifica tu conexión.');
    } finally {
      this.cargando.set(false);
    }
  }

  /** Vuelve al primer paso por decisión del usuario, no por un error. */
  protected cancelarCodigo(): void {
    this.desafio.set(null);
    this.error.set(null);
    this.formulario.patchValue({ contrasena: '' });
  }

  /**
   * Una cuenta obligada a enrolar entra con la sesión **capada**: el panel
   * respondería 403 entero. Se va directo al enrolamiento en vez de dejar que
   * rebote, que es lo mismo pero con parpadeo.
   */
  private async irADestino(): Promise<void> {
    if (this.auth.debeEnrolarMfa()) {
      await this.router.navigate(['/enrolar-mfa'], {
        queryParams: { obligatorio: '1' },
        replaceUrl: true,
      });
      return;
    }
    // `navigateByUrl` y no `navigate`: el destino ya es una URL completa con
    // sus query params (los filtros de una bandeja, por ejemplo), y trocearla
    // para pasarla por `navigate` los perdería.
    await this.router.navigateByUrl(this.destino ?? '/');
  }
}
