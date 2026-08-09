import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ETIQUETA_ROL } from '../../core/auth/sesion.model';

/**
 * Destino del `rolGuard` cuando el rol no alcanza a la pantalla pedida.
 *
 * No es un error: es la respuesta correcta a navegar a mano a una URL que el
 * menú no ofrece. Se dice con qué rol se entró, porque la causa casi siempre
 * es esa (un enlace copiado entre un broker y un agente).
 */
@Component({
  selector: 'app-acceso-denegado',
  imports: [RouterLink],
  templateUrl: './acceso-denegado.html',
  styleUrl: './acceso-denegado.scss',
})
export class AccesoDenegado {
  private readonly auth = inject(AuthService);

  protected readonly rol = computed(() => {
    const sesion = this.auth.sesion();
    return sesion ? ETIQUETA_ROL[sesion.rol] : null;
  });
}
