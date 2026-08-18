import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { PerfilService } from '../core/api/perfil.service';
import { AuthService } from '../core/auth/auth.service';
import { RenovacionSesion } from '../core/auth/renovacion-sesion';
import { iniciales as inicialesDe } from '../core/formato';
import { ETIQUETA_ROL } from '../core/auth/sesion.model';
import { altasDe, cuentaDe, cuerpoDelMenu } from '../core/auth/acceso';
import { CampanaAlertas } from '../shared/campana-alertas/campana-alertas';
import { ImagenSegura } from '../shared/imagen-segura/imagen-segura';

/**
 * Cascarón de la aplicación: sidebar + topbar. El menú NO está escrito aquí:
 * sale de `core/auth/acceso.ts`, que deriva de `docs/ai/matriz-operacion-rol.md`
 * —la fuente de verdad del backend, cubierta por test—. Así el menú, el
 * `rolGuard` y los gates del API dicen lo mismo por construcción.
 *
 * Todo módulo del menú enlaza a una ruta: ya no quedan entradas deshabilitadas
 * "pendientes de migrar". La última, "Mis locales", pasó a ser un filtro dentro
 * de Locales.
 */
@Component({
  selector: 'app-shell',
  imports: [CampanaAlertas, ImagenSegura, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly perfil = inject(PerfilService);
  protected readonly etiquetaRol = ETIQUETA_ROL;

  /**
   * El trabajo: Oferta, Demanda, Cierre, Gestión y Organización. Las secciones
   * vacías para el rol no salen, y las pantallas que se alcanzan por filtro o
   * por acción —las colas de revisión, la cartera del equipo, mi equipo,
   * reasignaciones— no están aquí a propósito (D-E3-1: una cola no es una
   * sección, y el alcance no crea entradas).
   */
  protected readonly menu = computed(() => cuerpoDelMenu(this.auth.sesion()?.rol));

  /**
   * La cuenta: lo que abre el menú de la identidad, arriba a la derecha. Sale
   * del mismo mapa que el resto, así que no puede ofrecer una pantalla que el
   * rol no tenga.
   */
  protected readonly cuenta = computed(() => cuentaDe(this.auth.sesion()?.rol));

  protected readonly cuentaAbierta = signal(false);

  protected alternarCuenta(): void {
    this.cuentaAbierta.update((abierta) => !abierta);
  }

  protected cerrarCuenta(): void {
    this.cuentaAbierta.set(false);
  }

  /**
   * Lo que este rol puede dar de alta. Si está vacío el botón no se dibuja:
   * ofrecer «Registrar» a quien no puede registrar nada es prometer un 403.
   */
  protected readonly altas = computed(() => altasDe(this.auth.sesion()?.rol));

  protected readonly registroAbierto = signal(false);

  protected abrirRegistro(): void {
    this.registroAbierto.set(true);
  }

  protected cerrarRegistro(): void {
    this.registroAbierto.set(false);
  }

  /**
   * La foto del avatar sale del servicio, no de la sesión: el token congelado
   * no la lleva. Se pide una vez por sesión y se comparte con la pantalla de
   * perfil, para que subir una foto allí se vea aquí sin recargar.
   */
  protected readonly fotoPerfil = this.perfil.fotoClave;

  constructor() {
    // Se arranca aquí y no en `main.ts` porque el shell solo existe con sesión
    // abierta: vigilar la inactividad en la pantalla de login no tendría qué
    // renovar.
    inject(RenovacionSesion).iniciar();

    effect(() => {
      if (!this.auth.sesion()) {
        this.perfil.olvidarFoto();
        return;
      }
      // Silencioso a propósito: que no se pueda leer el perfil no es motivo
      // para romper el armazón. El avatar se queda en las iniciales.
      void this.perfil.obtener().catch(() => undefined);
    });
  }

  /**
   * Salida deliberada: avisa al servidor antes de limpiar (D-S0-12). El
   * `void` es intencionado — la navegación la resuelve el propio servicio y
   * la plantilla no tiene nada que esperar.
   */
  protected salir(): void {
    void this.auth.salir();
  }

  protected iniciales(nombre: string | undefined): string {
    return inicialesDe(nombre);
  }
}
