import { DestroyRef, inject, Injectable } from '@angular/core';

import { AuthService } from './auth.service';

/** Se intenta renovar cuando al token le quedan 5 minutos o menos. */
const MARGEN_RENOVACION_MS = 5 * 60 * 1000;

/** Cada cuánto se mira si toca renovar. */
const INTERVALO_COMPROBACION_MS = 60 * 1000;

/**
 * Lo que cuenta como "sigo aquí". Deliberadamente **no** incluye `mousemove`:
 * un ratón rozado por el gato mantendría la sesión viva toda la noche, que es
 * justo lo que el límite por inactividad viene a evitar.
 */
const EVENTOS_ACTIVIDAD = ['pointerdown', 'keydown', 'wheel'] as const;

/**
 * Convierte los 30 minutos del token en un límite de **inactividad**.
 *
 * Antes de esto la caducidad era absoluta: a los 30 minutos exactos llegaba un
 * 401 y el SPA te echaba, estuvieras escribiendo o no. La regla ahora es la que
 * espera cualquiera: <b>30 minutos sin tocar nada cierran la sesión; 30 minutos
 * trabajando no</b>.
 *
 * <h3>Por qué la condición es "actividad reciente" y no "hubo actividad"</h3>
 *
 * La tentación es renovar siempre que el usuario haya hecho algo desde la
 * última renovación. Eso **deriva**: si actúa en el minuto 1 y se va, en el
 * minuto 25 se renovaría igual —hubo actividad— y la sesión duraría 55 minutos
 * desde su último clic, luego 85, y así.
 *
 * Por eso se exige que la actividad sea de los últimos {@link
 * MARGEN_RENOVACION_MS}, la misma ventana en la que se renueva. Consecuencia
 * medible y honesta: <b>la sesión cae entre 25 y 30 minutos después de la
 * última acción</b>, nunca más.
 *
 * <h3>Qué NO hace</h3>
 *
 * No guarda ninguna credencial nueva ni alarga nada por su cuenta: pide un
 * token nuevo presentando el actual, y el backend solo lo emite si ese sigue
 * siendo válido y no fue revocado. Si la sesión murió —logout en otra pestaña,
 * cambio de contraseña, cuenta desactivada— la renovación recibe un 401 y el
 * interceptor cierra, que es exactamente lo que debe pasar.
 */
@Injectable({ providedIn: 'root' })
export class RenovacionSesion {
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  private ultimaActividad = Date.now();
  private renovando = false;
  private temporizador: number | null = null;

  /** Idempotente: llamarlo dos veces no duplica ni escuchas ni temporizador. */
  iniciar(): void {
    if (this.temporizador !== null) {
      return;
    }

    const anotar = () => {
      this.ultimaActividad = Date.now();
    };
    for (const evento of EVENTOS_ACTIVIDAD) {
      // `passive` porque solo se lee el reloj: no hay que darle al navegador
      // motivos para esperar a que este listener decida si cancela el gesto.
      document.addEventListener(evento, anotar, { passive: true, capture: true });
    }

    this.temporizador = window.setInterval(
      () => void this.comprobar(),
      INTERVALO_COMPROBACION_MS,
    );

    this.destroyRef.onDestroy(() => {
      for (const evento of EVENTOS_ACTIVIDAD) {
        document.removeEventListener(evento, anotar, { capture: true });
      }
      if (this.temporizador !== null) {
        window.clearInterval(this.temporizador);
        this.temporizador = null;
      }
    });
  }

  private async comprobar(): Promise<void> {
    const sesion = this.auth.sesion();
    if (!sesion || this.renovando) {
      return;
    }

    const ahora = Date.now();
    const restante = Date.parse(sesion.expiraEn) - ahora;
    if (!Number.isFinite(restante) || restante > MARGEN_RENOVACION_MS) {
      return;
    }
    // Sin actividad reciente NO se renueva. Esta línea es la que hace que la
    // sesión caduque sola cuando el usuario se fue: todo lo demás es fontanería.
    if (ahora - this.ultimaActividad > MARGEN_RENOVACION_MS) {
      return;
    }

    this.renovando = true;
    try {
      await this.auth.renovar();
    } catch {
      // Un 401 aquí ya lo convirtió el interceptor en cierre de sesión, y
      // cualquier otro fallo se reintenta en la comprobación siguiente: quedan
      // varios minutos de margen antes de que el token muera de verdad.
    } finally {
      this.renovando = false;
    }
  }
}
