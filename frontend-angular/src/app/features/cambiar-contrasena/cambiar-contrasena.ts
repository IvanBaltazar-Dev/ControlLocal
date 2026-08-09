import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ContrasenasService } from '../../core/api/contrasenas.service';
import { AuthService } from '../../core/auth/auth.service';
import { LARGO_MINIMO_CONTRASENA } from '../../core/auth/politica-contrasena';

/**
 * Cambio de contraseña del propio usuario (Plan S0 §4.2). Cierra H-02: hasta
 * hoy **no existía ninguna forma** de cambiarla —el `PUT` de brokers y agentes
 * ignoraba el campo en silencio y la pantalla Blazor era un mock—.
 *
 * **Vive fuera del shell, y eso es lo que la hace funcionar en los dos casos**:
 *
 * - *voluntario*: se llega desde Perfil;
 * - *obligatorio*: la sesión viene **capada** por contraseña temporal y el
 *   backend responde 403 en todo lo demás. Dentro del shell no se podría
 *   pintar, porque el propio armazón (la campana, el menú) llama a endpoints
 *   que ese 403 bloquea.
 *
 * **Al guardar, la sesión muere.** No es un efecto secundario molesto: cambiar
 * la contraseña invalida todas las sesiones de la cuenta, incluida esta, y eso
 * es justo lo que hace que el cambio sirva de algo si alguien tenía la clave
 * anterior. La pantalla lo avisa antes y lleva al login después.
 */
@Component({
  selector: 'app-cambiar-contrasena',
  imports: [ReactiveFormsModule],
  templateUrl: './cambiar-contrasena.html',
  styleUrl: './cambiar-contrasena.scss',
})
export class CambiarContrasena {
  private readonly fb = inject(FormBuilder);
  private readonly contrasenas = inject(ContrasenasService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** `?obligatorio=1` lo pone el interceptor al toparse con la sesión capada. */
  protected readonly obligatorio =
    inject(ActivatedRoute).snapshot.queryParamMap.get('obligatorio') === '1';

  protected readonly largoMinimo = LARGO_MINIMO_CONTRASENA;
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly listo = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    contrasenaActual: ['', Validators.required],
    contrasenaNueva: ['', [Validators.required, Validators.minLength(LARGO_MINIMO_CONTRASENA)]],
    repetir: ['', Validators.required],
  });

  protected async guardar(): Promise<void> {
    const valores = this.formulario.getRawValue();
    if (this.formulario.invalid || this.cargando()) {
      this.formulario.markAllAsTouched();
      return;
    }
    if (valores.contrasenaNueva !== valores.repetir) {
      this.error.set('Las dos contraseñas no coinciden.');
      return;
    }
    if (valores.contrasenaNueva === valores.contrasenaActual) {
      this.error.set('La contraseña nueva debe ser distinta de la actual.');
      return;
    }

    this.cargando.set(true);
    this.error.set(null);
    try {
      await this.contrasenas.cambiar(valores.contrasenaActual, valores.contrasenaNueva);
      this.listo.set(true);
      // Limpieza local únicamente: el token ya está muerto en el servidor, así
      // que pedirle un logout devolvería 401 y realimentaría el mismo cierre.
      this.auth.olvidarSesionLocal();
    } catch (e) {
      const http = e as HttpErrorResponse;
      this.error.set(http.error?.error ?? 'No se pudo cambiar la contraseña.');
    } finally {
      this.cargando.set(false);
    }
  }

  protected irAlLogin(): void {
    void this.router.navigate(['/login'], { replaceUrl: true });
  }

  protected volver(): void {
    void this.router.navigate(['/perfil']);
  }
}
