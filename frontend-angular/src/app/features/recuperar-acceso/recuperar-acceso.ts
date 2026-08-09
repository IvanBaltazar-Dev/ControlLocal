import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ContrasenasService } from '../../core/api/contrasenas.service';
import { LARGO_MINIMO_CONTRASENA } from '../../core/auth/politica-contrasena';

/** Los tres estados de la pantalla; el enlace en la URL entra directo en `canje`. */
type Paso = 'solicitar' | 'solicitada' | 'canje' | 'listo';

/**
 * Recuperación de acceso (Plan S0 §4.3). **Pública**: quien la usa no tiene
 * sesión — es exactamente lo que viene a recuperar.
 *
 * Un solo trámite en dos pasos: **pedir** el enlace y **canjearlo** definiendo
 * la contraseña nueva. Con `?token=…` en la URL arranca directamente en el
 * segundo.
 *
 * Dos cosas que parecen detalles y no lo son:
 *
 * - **Nunca se dice si la cuenta existe.** El backend responde 202 siempre y
 *   aquí se muestra el mismo mensaje pase lo que pase — incluso si la llamada
 *   falla. Un "ese usuario no existe" convertiría el formulario en un padrón.
 * - **Hoy el enlace no llega solo.** No hay transporte configurado (D-S0-11),
 *   así que la solicitud deja constancia y el enlace lo entrega el
 *   administrador. Se dice en pantalla en vez de prometer un correo que no sale.
 */
@Component({
  selector: 'app-recuperar-acceso',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './recuperar-acceso.html',
  styleUrl: './recuperar-acceso.scss',
})
export class RecuperarAcceso {
  private readonly fb = inject(FormBuilder);
  private readonly contrasenas = inject(ContrasenasService);
  private readonly router = inject(Router);

  private readonly tokenDeLaUrl = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';

  protected readonly largoMinimo = LARGO_MINIMO_CONTRASENA;
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly paso = signal<Paso>(this.tokenDeLaUrl ? 'canje' : 'solicitar');

  /** Con el token en la URL no se pide otra vez: ya lo trae el enlace. */
  protected readonly tokenEnLaUrl = !!this.tokenDeLaUrl;

  protected readonly formularioSolicitud = this.fb.nonNullable.group({
    usuario: ['', Validators.required],
  });

  protected readonly formularioCanje = this.fb.nonNullable.group({
    token: [this.tokenDeLaUrl, Validators.required],
    contrasenaNueva: ['', [Validators.required, Validators.minLength(LARGO_MINIMO_CONTRASENA)]],
    repetir: ['', Validators.required],
  });

  protected async solicitar(): Promise<void> {
    if (this.formularioSolicitud.invalid || this.cargando()) {
      this.formularioSolicitud.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      await this.contrasenas.solicitarRecuperacion(this.formularioSolicitud.getRawValue().usuario);
    } catch (e) {
      const http = e as HttpErrorResponse;
      // El 429 del bloqueo sí se muestra: no revela nada que el usuario no
      // sepa ya (que ha insistido) y evita que reintente en bucle.
      if (http.status === 429) {
        this.error.set(http.error?.error ?? 'Demasiados intentos. Espera un momento.');
        this.cargando.set(false);
        return;
      }
      // Cualquier otro fallo se traga a propósito: si un error de red se
      // distinguiera de un usuario inexistente, esto volvería a ser un padrón.
    }
    this.cargando.set(false);
    this.paso.set('solicitada');
  }

  protected async canjear(): Promise<void> {
    const valores = this.formularioCanje.getRawValue();
    if (this.formularioCanje.invalid || this.cargando()) {
      this.formularioCanje.markAllAsTouched();
      return;
    }
    if (valores.contrasenaNueva !== valores.repetir) {
      this.error.set('Las dos contraseñas no coinciden.');
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      await this.contrasenas.canjear(valores.token.trim(), valores.contrasenaNueva);
      this.paso.set('listo');
    } catch (e) {
      const http = e as HttpErrorResponse;
      this.error.set(http.error?.error ?? 'No se pudo completar la operación.');
    } finally {
      this.cargando.set(false);
    }
  }

  /** "Ya tengo el enlace": salta al paso 2 con el campo del código a la vista. */
  protected irAlCanje(): void {
    this.error.set(null);
    this.paso.set('canje');
  }

  protected irAlLogin(): void {
    void this.router.navigate(['/login']);
  }
}
